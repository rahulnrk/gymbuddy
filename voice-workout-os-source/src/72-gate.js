/* ============================================================
   72-gate.js : addressee detection
   Four strategies, switchable at runtime, every decision logged.
   The point is not to pick one now — it is to be able to compare
   them on real gym audio and then pick one.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const WAKE = /^\s*(?:hey\s+|ok\s+|yo\s+|hi\s+)?(?:coach|trainer|buddy|partner)\b[,:\s]*/i;

  /* Things people say to other people, not to a machine. Weak evidence,
     used to lower confidence rather than to hard-reject. */
  const THIRD_PARTY = [
    /\b(?:hey|hi|hello|yo)\s+(?:man|mate|bro|dude|buddy|guys?|there)\b/i,
    /\b(?:you good|you alright|how'?s it going|how are you|what'?s up|how many (?:you|have you) got|you done|you finished)\b/i,
    /\b(?:no worries|cheers|thanks mate|excuse me|sorry mate|all yours|go ahead|after you|be my guest)\b/i,
    /\b(?:are you using|you using this|can i (?:jump|work) in|mind if i)\b/i,
    /\b(?:see you|later man|have a good one|take care)\b/i
  ];

  /* Utterances that are only ever aimed at the system. */
  const COMMAND_WORD = /^(?:done|next|ready|skip|undo|pause|resume|repeat|stop|wait|go|yes|no)$/i;
  function selfEvident(t) {
    const one = t.trim();
    if (COMMAND_WORD.test(one)) return true;
    if (/^\d{1,3}$/.test(one)) return true;
    // a spoken number on its own is only ever an answer to this system
    if (/^[a-z\s-]{2,14}$/i.test(one) && VOS.Numbers) {
      const c = VOS.Numbers.candidates(one);
      if (c.length && one.split(/\s+/).length <= 2) return true;
    }
    return false;
  }

  class Gate {
    constructor(opts) {
      opts = opts || {};
      this.strategy = opts.strategy || 'hybrid';   // open | window | wake | hybrid
      this.windowMs = opts.windowMs != null ? opts.windowMs : 20000;
      this.log = [];
      this.stats = { accepted: 0, rejected: 0, byReason: {} };
    }

    setStrategy(s) { this.strategy = s; return this; }

    /**
     * check({ text, dialogue, session, resolvedConfidence })
     * -> { accept, text, reason, confidence, wake }
     */
    check(input) {
      const { text, dialogue } = input;
      const raw = (text || '').trim();
      const wake = WAKE.test(raw);
      const stripped = wake ? raw.replace(WAKE, '').trim() : raw;
      const inWindow = dialogue ? dialogue.windowOpen(this.windowMs) : false;
      const isSelfEvident = selfEvident(stripped);
      const thirdParty = THIRD_PARTY.some(re => re.test(raw));

      let accept = false, reason = '', confidence = 0.5;

      switch (this.strategy) {
        case 'open':
          accept = true; reason = 'open strategy'; confidence = thirdParty ? 0.4 : 0.8;
          break;

        case 'wake':
          accept = wake || (inWindow && !!dialogue.pending);
          reason = wake ? 'wake word' : (accept ? 'answering a direct question' : 'no wake word');
          confidence = wake ? 0.97 : (accept ? 0.85 : 0.05);
          break;

        case 'window':
          accept = inWindow || isSelfEvident;
          reason = inWindow ? 'inside an expectation window' : (isSelfEvident ? 'command-only phrase' : 'outside any window');
          confidence = inWindow ? 0.85 : (isSelfEvident ? 0.7 : 0.15);
          break;

        case 'hybrid':
        default:
          if (wake) { accept = true; reason = 'wake word'; confidence = 0.97; }
          else if (inWindow) { accept = true; reason = 'inside an expectation window'; confidence = 0.85; }
          else if (isSelfEvident) { accept = true; reason = 'command-only phrase'; confidence = 0.7; }
          else { accept = false; reason = 'unaddressed and outside any window'; confidence = 0.2; }
          break;
      }

      // conversational-with-a-human markers pull it down, and can flip a
      // borderline accept, but never override an explicit wake word
      if (thirdParty && !wake) {
        confidence = Math.max(0, confidence - 0.45);
        if (confidence < 0.45) { accept = false; reason = 'sounds like talking to a person'; }
      }

      const decision = { accept, text: stripped, raw, reason, confidence: +confidence.toFixed(2), wake, inWindow, thirdParty, strategy: this.strategy, t: Date.now() };
      this.log.push(decision);
      if (this.log.length > 500) this.log = this.log.slice(-500);
      this.stats[accept ? 'accepted' : 'rejected']++;
      this.stats.byReason[reason] = (this.stats.byReason[reason] || 0) + 1;
      return decision;
    }

    report() {
      return {
        strategy: this.strategy,
        accepted: this.stats.accepted,
        rejected: this.stats.rejected,
        rejectRate: this.stats.accepted + this.stats.rejected
          ? Math.round(100 * this.stats.rejected / (this.stats.accepted + this.stats.rejected)) + '%' : '—',
        byReason: this.stats.byReason,
        // what a different strategy would have done, for offline comparison
        rejectedTexts: this.log.filter(d => !d.accept).slice(-25).map(d => d.raw)
      };
    }
  }

  VOS.Gate = Gate;
  VOS.WAKE_RE = WAKE;
})(typeof globalThis !== 'undefined' ? globalThis : this);
