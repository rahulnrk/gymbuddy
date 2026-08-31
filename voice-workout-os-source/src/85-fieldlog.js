/* ============================================================
   85-fieldlog.js : FIELD STUDY INSTRUMENTATION
   Answers the question that decides the mic-gating design:
   how often does someone else speak, and how often is the user
   talking to a person rather than to the agent?

   Records every recognised utterance with full context, and
   optionally the raw session audio so you can label ground truth
   afterwards instead of guessing.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  class FieldLog {
    constructor() {
      this.entries = [];
      this.startedAt = null;
      this.recorder = null;
      this.chunks = [];
      this.stream = null;
      this.audioBlob = null;
      this.recording = false;
      this.recordError = null;
    }

    begin() { this.startedAt = Date.now(); this.entries = []; return this; }

    at() { return this.startedAt ? Math.round((Date.now() - this.startedAt) / 1000) : 0; }

    /* Every final transcript, matched or not. */
    utterance(e) {
      this.entries.push({
        t: Date.now(),
        sec: this.at(),
        kind: 'user',
        text: e.text,
        alternatives: e.alts || null,
        phase: e.phase || null,
        exercise: e.exercise || null,
        awaiting: e.awaiting || null,
        intent: e.intent || null,
        route: e.route || null,
        matched: !!e.matched,
        ms: e.ms != null ? e.ms : null,
        // to be filled in by a human afterwards while listening back
        label: null
      });
      return this.entries[this.entries.length - 1];
    }

    agent(text, source) {
      this.entries.push({ t: Date.now(), sec: this.at(), kind: 'agent', text, source: source || null });
    }

    event(name, data) {
      this.entries.push({ t: Date.now(), sec: this.at(), kind: 'event', name, data: data || null });
    }

    /* ---------- raw audio ---------- */
    async startRecording() {
      if (this.recording) return true;
      try {
        this.stream = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: false, autoGainControl: true }
        });
        const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']
          .find(m => root.MediaRecorder && MediaRecorder.isTypeSupported(m));
        this.recorder = new MediaRecorder(this.stream, mime ? { mimeType: mime, audioBitsPerSecond: 64000 } : undefined);
        this.chunks = [];
        this.recorder.ondataavailable = ev => { if (ev.data && ev.data.size) this.chunks.push(ev.data); };
        this.recorder.start(5000);
        this.recording = true;
        this.event('recording_started', { mime: mime || 'default' });
        return true;
      } catch (err) {
        this.recordError = String(err);
        return false;
      }
    }

    stopRecording() {
      return new Promise(resolve => {
        if (!this.recorder || !this.recording) return resolve(null);
        this.recorder.onstop = () => {
          this.audioBlob = new Blob(this.chunks, { type: this.recorder.mimeType || 'audio/webm' });
          if (this.stream) this.stream.getTracks().forEach(t => t.stop());
          this.recording = false;
          resolve(this.audioBlob);
        };
        try { this.recorder.stop(); } catch (e) { this.recording = false; resolve(null); }
      });
    }

    /* ---------- analysis ---------- */
    summary() {
      const u = this.entries.filter(e => e.kind === 'user');
      const unmatched = u.filter(e => !e.matched);
      const byIntent = {};
      u.forEach(e => { const k = e.intent || 'unmatched'; byIntent[k] = (byIntent[k] || 0) + 1; });
      const music = u.filter(e => /^music_|^agent_volume$/.test(e.intent || ''));
      return {
        utterances: u.length,
        matched: u.length - unmatched.length,
        unmatched: unmatched.length,
        unmatchedTexts: unmatched.map(e => e.text),
        musicRequests: music.length,
        musicTexts: music.map(e => e.text),
        byIntent,
        durationSec: this.at(),
        // one utterance every N seconds — a proxy for how chatty the session was
        secondsPerUtterance: u.length ? Math.round(this.at() / u.length) : null,
        audioRecorded: !!this.audioBlob,
        audioBytes: this.audioBlob ? this.audioBlob.size : 0,
        recordError: this.recordError
      };
    }

    toJSON(extra) {
      return JSON.stringify({
        startedAt: this.startedAt ? new Date(this.startedAt).toISOString() : null,
        summary: this.summary(),
        note: 'label field is for you: fill in coach | third_party | self_talk | misrecognition while listening back to the audio',
        entries: this.entries,
        session: extra || null
      }, null, 2);
    }

    toCSV() {
      const rows = [['sec', 'kind', 'phase', 'exercise', 'awaiting', 'intent', 'route', 'matched', 'ms', 'text', 'alternatives', 'label']];
      this.entries.filter(e => e.kind !== 'event').forEach(e => {
        rows.push([
          e.sec, e.kind, e.phase || '', e.exercise || '', e.awaiting || '',
          e.intent || '', e.route || '', e.matched === true ? 'yes' : (e.kind === 'user' ? 'no' : ''),
          e.ms != null ? e.ms : '', e.text || '',
          (e.alternatives || []).join(' | '), e.label || ''
        ]);
      });
      return rows.map(r => r.map(c => {
        const s = String(c);
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
      }).join(',')).join('\n');
    }
  }

  VOS.FieldLog = FieldLog;
})(typeof globalThis !== 'undefined' ? globalThis : this);
