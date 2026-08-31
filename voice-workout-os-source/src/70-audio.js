/* ============================================================
   70-audio.js : continuous listening, two-stage barge-in, TTS
   The mic never stops, including while the agent is talking.
   Barge-in happens in two stages:
     1. energy onset on the mic  -> duck our own speech in ~50ms
     2. a transcript that isn't our own echo -> hard stop
   If stage 2 never arrives, we un-duck and carry on, so a clang of
   plates does not silence the agent.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});
  const SR = root.SpeechRecognition || root.webkitSpeechRecognition;
  const now = () => (root.performance && performance.now ? performance.now() : Date.now());

  /* ---------- how much of this transcript is just us talking? ---------- */
  function echoOverlap(transcript, spokenWindow) {
    if (!spokenWindow) return 0;
    const norm = s => s.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').split(/\s+/).filter(w => w.length > 1);
    const t = norm(transcript), w = new Set(norm(spokenWindow));
    if (!t.length) return 0;
    return t.filter(x => w.has(x)).length / t.length;
  }

  /* ============================================================
     TTS providers. Both must be able to stop mid-word.
     ============================================================ */
  class BrowserTTS {
    constructor(opts) { this.opts = opts || {}; this.voice = null; this.speaking = false; }
    setVoice(v) { this.voice = v; }
    supported() { return !!root.speechSynthesis; }

    speak(text, hooks) {
      return new Promise(resolve => {
        if (!root.speechSynthesis) return resolve('unsupported');
        const u = new SpeechSynthesisUtterance(text);
        if (this.voice) u.voice = this.voice;
        u.lang = (this.voice && this.voice.lang) || this.opts.lang || 'en-GB';
        u.rate = this.opts.rate || 1.06;
        u.pitch = this.opts.pitch || 1;
        u.volume = this.opts.volume != null ? this.opts.volume : 1;
        let done = false;
        const finish = (why) => { if (done) return; done = true; this.speaking = false; resolve(why); };
        u.onstart = () => { this.speaking = true; hooks && hooks.onStart && hooks.onStart(); };
        u.onend = () => finish('end');
        u.onerror = () => finish('error');
        this._u = u;
        root.speechSynthesis.speak(u);
        this._guard = setTimeout(() => finish('timeout'), 1500 + text.length * 95);
      });
    }

    stop() {
      clearTimeout(this._guard);
      try { root.speechSynthesis.cancel(); } catch (e) {}
      this.speaking = false;
    }
  }

  /* Any endpoint that takes {text} and returns audio bytes. Swap in a
     neural streaming voice here without touching anything else. */
  class RemoteTTS {
    constructor(opts) { this.opts = opts || {}; this.speaking = false; this.el = new Audio(); this.el.preload = 'auto'; }
    supported() { return !!this.opts.url; }
    setVoice() {}
    async speak(text, hooks) {
      try {
        const res = await fetch(this.opts.url, {
          method: 'POST', headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ text, voice: this.opts.voice })
        });
        if (!res.ok) throw new Error('tts ' + res.status);
        const blob = await res.blob();
        if (this._cancelled) { this._cancelled = false; return 'cancelled'; }
        this.el.src = URL.createObjectURL(blob);
        this.el.volume = this.opts.volume != null ? this.opts.volume : 1;
        this.speaking = true;
        hooks && hooks.onStart && hooks.onStart();
        await this.el.play();
        return await new Promise(r => {
          this.el.onended = () => { this.speaking = false; r('end'); };
          this.el.onerror = () => { this.speaking = false; r('error'); };
        });
      } catch (e) { this.speaking = false; return 'error'; }
    }
    stop() { this._cancelled = true; try { this.el.pause(); this.el.currentTime = 0; } catch (e) {} this.speaking = false; }
  }

  /* ============================================================
     Energy VAD — a parallel mic tap purely for speech-onset timing.
     SpeechRecognition will not give us audio, so we open our own.
     ============================================================ */
  class EnergyVAD {
    constructor(opts) {
      this.opts = Object.assign({ onOnset: () => {}, onOffset: () => {}, k: 3.2, frames: 3 }, opts || {});
      this.running = false; this.floor = 0.005; this.hot = 0; this.cold = 0; this.speaking = false;
      this.lastLevel = 0;
    }
    async start(stream) {
      if (this.running) return true;
      try {
        this.stream = stream || await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
        });
        const AC = root.AudioContext || root.webkitAudioContext;
        this.ctx = new AC();
        if (this.ctx.state === 'suspended') await this.ctx.resume();
        this.src = this.ctx.createMediaStreamSource(this.stream);
        this.an = this.ctx.createAnalyser();
        this.an.fftSize = 512; this.an.smoothingTimeConstant = 0.15;
        this.src.connect(this.an);
        this.buf = new Float32Array(this.an.fftSize);
        this.running = true;
        this.loop();
        return true;
      } catch (e) { this.error = String(e); return false; }
    }
    loop() {
      if (!this.running) return;
      this.an.getFloatTimeDomainData(this.buf);
      let sum = 0;
      for (let i = 0; i < this.buf.length; i++) sum += this.buf[i] * this.buf[i];
      const rms = Math.sqrt(sum / this.buf.length);
      this.lastLevel = rms;
      // slow-moving noise floor so a loud gym raises the bar instead of firing constantly
      this.floor = rms < this.floor ? this.floor * 0.95 + rms * 0.05 : this.floor * 0.999 + rms * 0.001;
      const threshold = Math.max(0.008, this.floor * this.opts.k);
      if (rms > threshold) {
        this.hot++; this.cold = 0;
        if (!this.speaking && this.hot >= this.opts.frames) { this.speaking = true; this.opts.onOnset(rms, threshold); }
      } else {
        this.cold++; this.hot = 0;
        if (this.speaking && this.cold >= 12) { this.speaking = false; this.opts.onOffset(); }
      }
      this._raf = setTimeout(() => this.loop(), 16);
    }
    stop() { this.running = false; clearTimeout(this._raf); try { this.ctx && this.ctx.close(); } catch (e) {} }
  }

  /* ============================================================
     The audio layer proper
     ============================================================ */
  class Audio_ {
    constructor(opts) {
      this.opts = Object.assign({
        lang: 'en-GB', rate: 1.06, duckTo: 0.18, bargeIn: 'vad',   // vad | transcript | off
        echoThreshold: 0.45, confirmWindowMs: 1400,
        onFinal: () => {}, onPartial: () => {}, onStatus: () => {},
        onError: () => {}, onInterrupt: () => {}, onMetric: () => {}
      }, opts || {});

      this.supported = !!SR;
      this.listening = false; this.wantListening = false;
      this.recognition = null;
      this.music = null; this.baseVolume = 1;
      this.wakeLock = null;

      this.tts = new BrowserTTS(this.opts);
      this.remoteTTS = null;
      this.speaking = false;
      this.spokenWindow = '';        // what we have said in the last few seconds
      this.queue = [];               // clauses still to speak
      this.currentLine = null;
      this.softDuckedAt = 0;
      this.pendingBarge = null;
      this.echoHits = 0;
      this.stats = { interrupts: 0, softDucks: 0, falseDucks: 0, echoSuppressed: 0, bargeMs: [], softMs: [] };

      this.vad = new EnergyVAD({
        onOnset: () => this.onEnergyOnset(),
        onOffset: () => {}
      });
      this._loadVoices();
    }

    /* ---------- voices ---------- */
    _loadVoices() {
      if (!root.speechSynthesis) return;
      const pick = () => {
        const vs = root.speechSynthesis.getVoices();
        if (!vs.length) return;
        this.voices = vs;
        const want = this.opts.voiceName;
        const v = (want && vs.find(x => x.name === want)) ||
                  vs.find(x => /en-GB/i.test(x.lang) && /(Daniel|Google UK English Male|Arthur)/i.test(x.name)) ||
                  vs.find(x => /en-GB/i.test(x.lang)) || vs.find(x => /^en/i.test(x.lang)) || vs[0];
        this.tts.setVoice(v);
      };
      pick();
      root.speechSynthesis.onvoiceschanged = pick;
    }
    setVoiceByName(name) {
      this.opts.voiceName = name;
      const vs = (root.speechSynthesis && root.speechSynthesis.getVoices()) || [];
      const v = vs.find(x => x.name === name); if (v) this.tts.setVoice(v);
    }
    useRemoteTTS(url, voice) {
      this.remoteTTS = url ? new RemoteTTS({ url, voice, volume: this.opts.volume }) : null;
    }
    get provider() { return (this.remoteTTS && this.remoteTTS.supported()) ? this.remoteTTS : this.tts; }

    /* ---------- ducking ---------- */
    duck(level) {
      if (!this.music) return;
      const target = this.baseVolume * level;
      const from = this.music.volume, steps = 6;
      let i = 0;
      clearInterval(this._duckTimer);
      this._duckTimer = setInterval(() => {
        i++; this.music.volume = Math.max(0, Math.min(1, from + (target - from) * (i / steps)));
        if (i >= steps) clearInterval(this._duckTimer);
      }, 20);
    }
    attachMusic(el) { this.music = el; }

    /* ============================================================
       SPEAKING — clause by clause, so a stop lands almost immediately
       ============================================================ */
    say(text, meta) {
      if (!text) return Promise.resolve();
      const clauses = String(text).split(/(?<=[.!?;])\s+|(?<=,)\s+(?=(?:and|but|then|so)\b)/)
        .map(s => s.trim()).filter(Boolean);
      this.currentLine = { full: text, clauses, spoken: [], meta: meta || {}, startedAt: now(), interrupted: false };
      this.queue = clauses.slice();
      return this._drain();
    }

    async _drain() {
      if (this._draining) return this._drainPromise;
      this._draining = true;
      this._drainPromise = (async () => {
        this.speaking = true;
        this.duck(this.opts.duckTo);
        this.opts.onStatus('speaking');
        while (this.queue.length) {
          const clause = this.queue.shift();
          this.spokenWindow = (this.spokenWindow + ' ' + clause).split(/\s+/).slice(-45).join(' ');
          const why = await this.provider.speak(clause, {});
          if (this.currentLine) this.currentLine.spoken.push(clause);
          if (why === 'cancelled' || this._stopRequested) break;
        }
        this._stopRequested = false;
        this.speaking = false;
        this.duck(1);
        // keep the echo window alive briefly — the recogniser lags
        setTimeout(() => { this.spokenWindow = ''; }, 2500);
        this.opts.onStatus(this.wantListening ? 'listening' : 'idle');
        this._draining = false;
      })();
      return this._drainPromise;
    }

    speak(text, opts) { return this.say(text, opts); }
    shutUp() { return this.stopSpeaking('manual'); }

    /* Hard stop: kill the rest of the line immediately. */
    stopSpeaking(reason) {
      if (!this.speaking && !this.queue.length) return null;
      const line = this.currentLine;
      this._stopRequested = true;
      this.queue = [];
      this.provider.stop();
      this.speaking = false;
      this.duck(1);
      if (line) {
        line.interrupted = true;
        line.remaining = line.clauses.slice(line.spoken.length).join(' ');
      }
      this.opts.onStatus(this.wantListening ? 'listening' : 'idle');
      return line;
    }

    /* ============================================================
       BARGE-IN
       ============================================================ */
    onEnergyOnset() {
      if (this.opts.bargeIn === 'off') return;
      if (!this.speaking) return;
      // stage 1: get out of the way instantly, before we know what was said
      this.softDuckedAt = now();
      this.stats.softDucks++;
      if (this.opts.bargeIn === 'vad') {
        this.provider.stop();          // pause our own voice
        this.speaking = false;         // clauses stop advancing
        this.opts.onStatus('yielding');
        this.opts.onMetric({ type: 'soft_duck', at: this.softDuckedAt });
        this.pendingBarge = setTimeout(() => this.resumeAfterFalseAlarm(), this.opts.confirmWindowMs);
      }
    }

    /* Nothing intelligible followed the noise, so pick the line back up. */
    resumeAfterFalseAlarm() {
      this.pendingBarge = null;
      if (!this.currentLine || this.currentLine.interrupted) return;
      const left = this.currentLine.clauses.slice(this.currentLine.spoken.length);
      if (!left.length) return;
      this.stats.falseDucks++;
      this.stats.softMs.push(Math.round(now() - this.softDuckedAt));
      this.queue = left;
      this._draining = false;
      this._drain();
    }

    /* A real utterance arrived while we were talking. */
    confirmBarge(text) {
      clearTimeout(this.pendingBarge); this.pendingBarge = null;
      const line = this.stopSpeaking('barge-in');
      const ms = this.softDuckedAt ? Math.round(now() - this.softDuckedAt) : 0;
      this.stats.interrupts++;
      this.stats.bargeMs.push(ms);
      this.opts.onMetric({ type: 'barge_in', softToHardMs: ms, spoken: line && line.spoken.length, remaining: line && line.remaining });
      this.opts.onInterrupt(text, line);
      this.softDuckedAt = 0;
    }

    /* ============================================================
       LISTENING — never stops
       ============================================================ */
    _build() {
      const r = new SR();
      r.lang = this.opts.lang;
      r.continuous = true;
      r.interimResults = true;
      r.maxAlternatives = 5;

      r.onresult = (ev) => {
        for (let i = ev.resultIndex; i < ev.results.length; i++) {
          const res = ev.results[i];
          const txt = (res[0].transcript || '').trim();
          if (!txt) continue;

          const overlap = (this.speaking || this.softDuckedAt) ? echoOverlap(txt, this.spokenWindow) : 0;
          const isEcho = overlap >= this.opts.echoThreshold;

          if (!res.isFinal) {
            this.opts.onPartial(txt, { echo: isEcho });
            // an interim that is not our own voice, while we are talking, is a barge-in
            if ((this.speaking || this.softDuckedAt) && !isEcho && txt.split(/\s+/).length >= 1) {
              this.confirmBarge(txt);
            }
            continue;
          }

          if (isEcho) { this.stats.echoSuppressed++; this.echoHits++; continue; }
          if (Date.now() - (this._lastFinalAt || 0) < 200 && txt === this._lastFinalText) continue;
          this._lastFinalAt = Date.now(); this._lastFinalText = txt;

          if (this.softDuckedAt && !this.currentLine?.interrupted) this.confirmBarge(txt);

          const alts = [];
          for (let k = 0; k < res.length; k++) if (res[k].transcript) alts.push(res[k].transcript.trim());
          this.opts.onFinal(txt, alts, res[0].confidence);
        }
      };

      r.onerror = (ev) => {
        if (ev.error === 'no-speech' || ev.error === 'aborted') return;
        if (ev.error === 'not-allowed' || ev.error === 'service-not-allowed') {
          this.wantListening = false;
          this.opts.onError('Microphone permission denied. Allow the mic and reload.');
          this.opts.onStatus('blocked'); return;
        }
        if (ev.error === 'network') { this.opts.onError('Speech recognition lost the network.'); return; }
        this.opts.onError('Speech error: ' + ev.error);
      };

      r.onend = () => {
        this.listening = false;
        // the browser stops recognition every so often; restart at once
        if (this.wantListening) {
          clearTimeout(this._restart);
          this._restart = setTimeout(() => this._start(), 120);
        } else this.opts.onStatus('idle');
      };
      r.onstart = () => { this.listening = true; this.opts.onStatus(this.speaking ? 'speaking' : 'listening'); };
      return r;
    }

    _start() {
      if (!this.supported || this.listening) return;
      if (!this.recognition) this.recognition = this._build();
      try { this.recognition.start(); } catch (e) {}
    }

    async start() {
      if (!this.supported) { this.opts.onError('This browser has no speech recognition. Use Chrome on Android or desktop.'); return false; }
      this.wantListening = true;
      this._start();
      if (this.opts.bargeIn === 'vad') {
        const okv = await this.vad.start();
        if (!okv) this.opts.onError('Barge-in needs mic access for level detection; falling back to transcript-only.');
      }
      this.requestWakeLock();
      return true;
    }

    stop() {
      this.wantListening = false;
      clearTimeout(this._restart);
      try { this.recognition && this.recognition.stop(); } catch (e) {}
      this.listening = false;
      this.vad.stop();
      this.releaseWakeLock();
    }

    report() {
      const avg = a => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
      return {
        interrupts: this.stats.interrupts,
        avgInterruptMs: avg(this.stats.bargeMs),
        softDucks: this.stats.softDucks,
        falseDucks: this.stats.falseDucks,
        echoSuppressed: this.stats.echoSuppressed,
        provider: this.provider === this.tts ? 'browser' : 'remote'
      };
    }

    async requestWakeLock() {
      try { if ('wakeLock' in navigator) { this.wakeLock = await navigator.wakeLock.request('screen'); } } catch (e) {}
    }
    releaseWakeLock() { try { this.wakeLock && this.wakeLock.release(); } catch (e) {} this.wakeLock = null; }
  }

  VOS.Audio = Audio_;
  VOS.BrowserTTS = BrowserTTS;
  VOS.RemoteTTS = RemoteTTS;
  VOS.EnergyVAD = EnergyVAD;
  VOS.echoOverlap = echoOverlap;
  VOS.speechSupported = !!SR;
})(typeof globalThis !== 'undefined' ? globalThis : this);
