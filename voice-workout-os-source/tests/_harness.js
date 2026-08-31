/* Shared loader + a fake audio layer, so engine tests run in Node. */
const fs = require('fs'), path = require('path'), vm = require('vm');

const FILES = ['10-data.js','12-muscles.js','14-figure.js','15-match.js','16-heat.js','17-recovery.js','18-routines.js','19-warmup.js','20-engine.js','21-soreness.js','22-checkin.js','30-intent.js','32-numbers.js','34-dialogue.js',
               '36-resolve.js','40-policy.js','45-speech.js','52-actions.js','55-safety.js',
               '58-controller.js','60-llm.js','72-gate.js'];

function load() {
  const ctx = vm.createContext({
    console, Date, Math, JSON, Promise,
    setTimeout, clearTimeout, setInterval, clearInterval,
    performance: { now: () => Date.now() }
  });
  FILES.forEach(f => vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'src', f), 'utf8'), ctx, { filename: f }));
  return ctx.VOS;
}

class FakeAudio {
  constructor() { this.said = []; this.stopped = 0; this.speaking = false; }
  say(t) { this.said.push(t); return Promise.resolve(); }
  speak(t) { return this.say(t); }
  stopSpeaking() { this.stopped++; this.speaking = false; return null; }
  report() { return { interrupts: 0, avgInterruptMs: null, softDucks: 0, falseDucks: 0, echoSuppressed: 0 }; }
  get last() { return this.said[this.said.length - 1]; }
}

/* A controller wired to a real engine and a fake mouth. */
function makeSession(VOS, opts) {
  opts = opts || {};
  const program = opts.program || VOS.PROGRAMS.upper_lower_4;
  const session = new VOS.Session({
    program, dayIndex: opts.dayIndex || 0,
    history: opts.history || [], profile: opts.profile || {}
  });
  session.checkin({ energy: opts.energy != null ? opts.energy : 7, soreness: 2, timeBudgetMin: opts.budget || null });
  return session;
}

function makeCtrl(VOS, session, opts) {
  opts = opts || {};
  const audio = new FakeAudio();
  const dialogue = new VOS.Dialogue();
  const gate = new VOS.Gate({ strategy: opts.gate || 'open' });
  const ctrl = new VOS.Controller({
    session, dialogue, gate, audio,
    speech: opts.speech || null,
    field: opts.field || null,
    llm: opts.llm || null,
    memory: () => null,
    log: () => {},
    onState: () => {},
    onFinish: opts.onFinish || (() => {}),
    music: opts.music || null,
    agentVolume: () => {},
    memoryStore: opts.memoryStore || { data: { history: [], notes: [], soreness: [] }, save() {}, addNote() {} },
    /* These suites test the working phase directly. The warm-up has its
       own suite — tests/warmup.js — which runs the real flow. */
    warmup: opts.warmup === undefined ? false : opts.warmup
  });
  return { ctrl, audio, dialogue, gate };
}

module.exports = { load, FakeAudio, makeSession, makeCtrl, FILES };
