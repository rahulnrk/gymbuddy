/* ============================================================
   34-dialogue.js : explicit conversational state
   Separate from the LLM's message history. This is what the system
   *expects* to hear next, what it just asked, and what is pending —
   the thing that makes "eight" mean reps here and RPE there.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const now = () => Date.now();

  class Dialogue {
    constructor() {
      this.expectations = [];   // stack, most recent last
      this.turns = [];          // {role, text, t, intent, confidence}
      this.pending = null;      // {kind:'confirm'|'clarify', action|options, prompt, id}
      this.lastAgentText = '';
      this.lastAgentAt = 0;
      this.lastQuestionAt = 0;
      this.interruptions = 0;
    }

    /* ---------- expectations ---------- */
    // kind: reps | rpe | weight | energy | fatigue | confirm | clarify | ready
    expect(kind, opts) {
      opts = opts || {};
      this.expectations = this.expectations.filter(e => e.kind !== kind);
      this.expectations.push({
        kind,
        question: opts.question || null,
        openedAt: now(),
        ttlMs: opts.ttlMs != null ? opts.ttlMs : 45000,
        anchor: opts.anchor != null ? opts.anchor : null,
        meta: opts.meta || null
      });
      if (opts.question) this.lastQuestionAt = now();
      return this;
    }

    clear(kind) {
      this.expectations = kind ? this.expectations.filter(e => e.kind !== kind) : [];
      return this;
    }

    prune() {
      const t = now();
      this.expectations = this.expectations.filter(e => t - e.openedAt < e.ttlMs);
      return this;
    }

    get top() { this.prune(); return this.expectations[this.expectations.length - 1] || null; }
    get topKind() { const t = this.top; return t ? t.kind : null; }
    expecting(kind) { this.prune(); return this.expectations.some(e => e.kind === kind); }

    /* An open window means the agent just spoke or asked something, so an
       utterance arriving now is probably aimed at it. */
    windowOpen(ms) {
      const t = now(), w = ms || 20000;
      // we asked something
      if (this.lastQuestionAt && (t - this.lastQuestionAt) < w) return true;
      // we are waiting on something, but the window is shorter than the
      // expectation itself — the system expects reps for minutes, yet a
      // reply arriving four minutes later is probably not for us
      const top = this.top;
      if (top && (t - top.openedAt) < Math.min(top.ttlMs, w)) return true;
      // we just spoke, so a reply is likely aimed at us
      if (this.lastAgentAt && (t - this.lastAgentAt) < Math.min(w, 12000)) return true;
      return false;
    }

    /* ---------- turns ---------- */
    addUser(text, meta) {
      this.turns.push(Object.assign({ role: 'user', text, t: now() }, meta || {}));
      this.trim();
    }
    addAgent(text, meta) {
      this.lastAgentText = text;
      this.lastAgentAt = now();
      this.turns.push(Object.assign({ role: 'agent', text, t: now() }, meta || {}));
      this.trim();
    }
    trim() { if (this.turns.length > 40) this.turns = this.turns.slice(-40); }

    recent(n) { return this.turns.slice(-(n || 6)).map(t => `${t.role}: ${t.text}`); }

    /* ---------- pending confirmation / clarification ---------- */
    ask(kind, payload, prompt, id) {
      this.pending = { kind, ...payload, prompt, id, at: now() };
      this.expect(kind === 'confirm' ? 'confirm' : 'clarify', { question: prompt, ttlMs: 30000 });
      return this.pending;
    }
    resolvePending() { const p = this.pending; this.pending = null; this.clear('confirm'); this.clear('clarify'); return p; }

    /* Compact view for the resolver and the model. */
    snapshot() {
      this.prune();
      return {
        expecting: this.expectations.map(e => e.kind),
        topExpectation: this.topKind,
        lastQuestion: this.top && this.top.question,
        pending: this.pending ? { kind: this.pending.kind, prompt: this.pending.prompt } : null,
        lastAgentSaid: this.lastAgentText,
        recentTurns: this.recent(6)
      };
    }
  }

  VOS.Dialogue = Dialogue;
})(typeof globalThis !== 'undefined' ? globalThis : this);
