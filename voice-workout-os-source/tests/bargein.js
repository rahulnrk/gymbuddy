/* ============================================================
   Interruption behaviour, measured.
   ============================================================ */
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
  await page.waitForTimeout(300);

  const r = await page.evaluate(async () => {
    const VOS = window.VOS;
    const out = {};

    /* a TTS provider we can control the timing of */
    function FakeProvider() {
      this.spoken = []; this.stopped = 0; this.speaking = false; this._resolve = null;
      this.speak = (text) => {
        this.spoken.push(text); this.speaking = true;
        return new Promise(res => { this._resolve = res; setTimeout(() => { if (this._resolve) { this._resolve('end'); this._resolve = null; } }, 400); });
      };
      this.stop = () => { this.stopped++; this.speaking = false; if (this._resolve) { this._resolve('cancelled'); this._resolve = null; } };
      this.supported = () => true; this.setVoice = () => {};
    }

    const mk = (opts) => {
      const a = new VOS.Audio(Object.assign({ bargeIn: 'vad' }, opts || {}));
      const fake = new FakeProvider();
      a.tts = fake; a.remoteTTS = null;
      return { a, fake };
    };

    /* ---- 1. a long line is split into clauses ---- */
    {
      const { a, fake } = mk();
      const line = 'Bench press. Last time 62.5 for 10, 9 and 8. Today 65 kilos, 8 to 10. Start when you are ready.';
      a.say(line);
      await new Promise(r => setTimeout(r, 50));
      out.clauses = a.currentLine.clauses.length;
      out.firstClause = fake.spoken[0];
      a.stopSpeaking('test');
    }

    /* ---- 2. energy onset ducks instantly, before any words ---- */
    {
      const { a, fake } = mk();
      a.say('One. Two. Three. Four. Five.');
      await new Promise(r => setTimeout(r, 60));
      const t0 = performance.now();
      a.onEnergyOnset();
      out.softDuckMs = +(performance.now() - t0).toFixed(2);
      out.stoppedOnOnset = fake.stopped >= 1;
      out.speakingAfterOnset = a.speaking;
      out.softDucks = a.stats.softDucks;
      a.stopSpeaking('test');
    }

    /* ---- 3. a real utterance confirms the barge-in ---- */
    {
      const { a, fake } = mk();
      let interrupted = null;
      a.opts.onInterrupt = (text, line) => { interrupted = { text, spoken: line.spoken.length, remaining: line.remaining }; };
      a.say('First clause here. Second clause here. Third clause here. Fourth clause here.');
      await new Promise(r => setTimeout(r, 60));
      a.onEnergyOnset();
      await new Promise(r => setTimeout(r, 30));
      const t0 = performance.now();
      a.confirmBarge('no I did nine');
      out.hardStopMs = +(performance.now() - t0).toFixed(2);
      out.interrupted = interrupted;
      out.queueEmptied = a.queue.length === 0;
      out.reportedInterrupts = a.stats.interrupts;
      out.measuredSoftToHard = a.stats.bargeMs[0];
    }

    /* ---- 4. a false alarm resumes the line instead of eating it ---- */
    {
      const { a, fake } = mk({ confirmWindowMs: 120 });
      a.say('Alpha. Bravo. Charlie. Delta. Echo.');
      await new Promise(r => setTimeout(r, 60));
      const before = fake.spoken.length;
      a.onEnergyOnset();
      await new Promise(r => setTimeout(r, 500));
      out.falseAlarmResumed = fake.spoken.length > before;
      out.falseDucks = a.stats.falseDucks;
      out.lineNotMarkedInterrupted = !a.currentLine.interrupted;
      a.stopSpeaking('test');
    }

    /* ---- 5. our own voice is not treated as the user ---- */
    {
      out.echoSelf = VOS.echoOverlap('take two minutes then we move on', 'okay take two minutes then we move on');
      out.echoUser = VOS.echoOverlap('no I did nine', 'okay take two minutes then we move on');
      out.echoPartial = VOS.echoOverlap('two minutes', 'take two minutes then we move on');
    }

    /* ---- 6. barge-in can be turned off ---- */
    {
      const { a, fake } = mk({ bargeIn: 'off' });
      a.say('One. Two. Three.');
      await new Promise(r => setTimeout(r, 60));
      a.onEnergyOnset();
      out.offIgnoresOnset = a.speaking === true && fake.stopped === 0;
      a.stopSpeaking('test');
    }

    /* ---- 7. recognition is not torn down while speaking ---- */
    {
      const { a } = mk();
      out.keepsListeningWhileSpeaking = (() => {
        a.wantListening = true; a.listening = true;
        a.say('Something long enough to still be going.');
        return a.listening === true && a.wantListening === true;
      })();
      a.stopSpeaking('test');
    }

    return out;
  });

  const res = [];
  const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));

  ok('a long line is broken into clauses so a stop lands fast', r.clauses >= 4, r);
  ok('only the first clause is spoken up front', /Bench press/.test(r.firstClause || ''), r.firstClause);
  ok('a noise onset yields the floor immediately', r.stoppedOnOnset === true, r);
  ok('yielding takes under 5ms of our own processing', r.softDuckMs < 5, r.softDuckMs);
  ok('the agent stops advancing clauses once it yields', r.speakingAfterOnset === false, r);
  ok('a confirmed utterance hard-stops the line', r.queueEmptied === true, r);
  ok('hard stop costs under 5ms', r.hardStopMs < 5, r.hardStopMs);
  ok('the interruption reports what was cut off', r.interrupted && r.interrupted.remaining && r.interrupted.remaining.length > 0, r.interrupted);
  ok('soft-to-hard latency is measured', typeof r.measuredSoftToHard === 'number', r.measuredSoftToHard);
  ok('a clang of plates does not silence the agent', r.falseAlarmResumed === true, r);
  ok('a false alarm is counted, not hidden', r.falseDucks >= 1, r.falseDucks);
  ok('a resumed line is not marked as interrupted', r.lineNotMarkedInterrupted === true, r);
  ok('the agent recognises its own voice as echo', r.echoSelf >= 0.9, r.echoSelf);
  ok('a genuine reply is not mistaken for echo', r.echoUser < 0.3, r.echoUser);
  ok('a partial echo is still caught', r.echoPartial >= 0.9, r.echoPartial);
  ok('barge-in can be switched off entirely', r.offIgnoresOnset === true, r);
  ok('the microphone stays open while the agent talks', r.keepsListeningWhileSpeaking === true, r);

  console.log(res.join('\n'));
  const fails = res.filter(x => x.startsWith('FAIL')).length;
  console.log(`\n${res.length - fails} passed, ${fails} failed`);
  if (errors.length) console.log('page errors:', errors);
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
