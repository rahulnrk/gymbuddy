/* ============================================================
   36-resolve.js : contextual intent resolution
   Deterministic patterns handle the handful of utterances that are
   genuinely unambiguous. Everything else is resolved by context —
   what the system is expecting, what the numbers could plausibly be,
   which of the recogniser's guesses fits — and anything still unclear
   is handed to the semantic layer as a *proposal*, never an action.

   Rule: no new regex for open-ended language. Regex only for
   high-confidence fixed commands.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* How much damage a wrong guess does. Drives the confidence tier. */
  const RISK = {
    // read-only — a wrong guess costs nothing
    whats_next:'safe', where_am_i:'safe', what_weight:'safe', time_left:'safe',
    elapsed:'safe', repeat:'safe', pause:'safe', resume:'safe', ready:'safe',
    extend_rest:'safe', set_rest:'safe', music_next:'safe', music_prev:'safe',
    music_pause:'safe', music_resume:'safe', music_shuffle:'safe', music_volume:'safe',
    agent_volume:'safe', affirm:'safe', deny:'safe', interrupt:'safe',
    // writes state, but cheap to undo
    reps:'logging', rpe:'logging', done_set:'logging', undo:'logging',
    energy:'logging', correct_last:'logging',
    // hard to undo or changes the plan
    set_weight:'destructive', skip_exercise:'destructive', next_exercise:'destructive',
    end_workout:'destructive', substitute_exercise:'destructive',
    reduce_volume:'destructive', time_budget:'destructive', pain:'destructive'
  };

  const CORRECTION_MARKER = /\b(?:actually|no wait|wait no|i mean|i meant|sorry|scratch that|rather|make it|no,?\s+make it|let'?s say|change that to)\b/i;
  const NEGATION_START = /^\s*(?:no|nope|nah)\b[,\s]*/i;
  const INTERRUPT = /^\s*(?:wait|hold on|hang on|stop|shh|shut up|quiet|one sec|one second|hold up|pause a sec)\b/i;

  /* Splitting on connectives is only safe when both halves stand alone. */
  const CONNECTIVE = /\s+(?:and then|,?\s*then|,?\s*and also|,?\s*also|;|\.\s+)\s+|\s+and\s+/i;

  function isReal(intent) { return intent && intent !== 'unknown'; }

  /* ---------- self-correction ----------
     "Set it to 70 — actually no, make it 65"  ->  the 65 wins.
     A trailing fragment with no verb inherits the intent of what it corrects. */
  function splitCorrection(text) {
    const m = text.match(CORRECTION_MARKER);
    if (!m || m.index === 0) return null;
    const head = text.slice(0, m.index).trim();
    const tail = text.slice(m.index + m[0].length).replace(/^[,\s]+/, '').trim();
    if (!head || !tail) return null;
    return { head, tail, marker: m[0] };
  }

  /* ---------- multi-intent ---------- */
  function segment(text, ctx) {
    if (CORRECTION_MARKER.test(text)) return [text];           // corrections are not two commands
    const parts = text.split(CONNECTIVE).map(s => s.trim()).filter(Boolean);
    if (parts.length < 2 || parts.length > 3) return [text];
    const each = parts.map(p => VOS.Intent.parse(p, ctx));
    // only a genuine multi-command if every part means something on its own
    if (each.every(r => isReal(r.intent))) return parts;
    return [text];
  }

  /* ---------- which numeric kinds are plausible right now ---------- */
  function expectedKinds(dialogue, session) {
    const top = dialogue.topKind;
    const ex = session && session.current;
    const phase = session ? session.phase : null;

    if (top === 'weight')  return ['weight', 'reps'];
    if (top === 'rpe')     return ['rpe', 'reps'];
    if (top === 'energy')  return ['energy'];
    if (top === 'fatigue') return ['fatigue'];
    if (top === 'reps')    return ['reps', 'rpe'];

    if (phase === 'resting') return ['seconds', 'reps', 'weight'];
    if (ex && ex.needsWeight) return ['weight', 'reps'];
    return ['reps', 'rpe', 'weight'];
  }

  function anchorsFor(session) {
    const ex = session && session.current;
    if (!ex) return {};
    const target = Math.round((ex.repLow + ex.repHigh) / 2);
    const lastSet = ex.sets[ex.sets.length - 1];
    return {
      reps: lastSet ? lastSet.reps : target,
      weight: ex.weight || null,
      rpe: lastSet && lastSet.rpe ? lastSet.rpe : 8,
      energy: 7, fatigue: 7,
      seconds: ex.restSec || 120
    };
  }

  /* ---------- confidence -> what to do about it ---------- */
  function tierFor(intent, confidence) {
    const risk = RISK[intent] || 'destructive';
    if (risk === 'safe')       return confidence >= 0.35 ? 'execute' : 'clarify';
    if (risk === 'logging')    return confidence >= 0.72 ? 'execute' : (confidence >= 0.4 ? 'confirm' : 'clarify');
    return confidence >= 0.85 ? 'execute' : (confidence >= 0.45 ? 'confirm' : 'clarify');
  }

  /* ---------- resolving one clause ---------- */
  function resolveClause(text, alts, dialogue, session, asrConfidence) {
    const ctx = {
      awaiting: dialogue.topKind === 'rpe' ? 'rpe'
              : dialogue.topKind === 'weight' ? 'weight'
              : dialogue.topKind === 'energy' ? 'energy' : null,
      phase: session ? session.phase : null,
      hasCurrentExercise: !!(session && session.current)
    };
    const asr = asrConfidence == null || asrConfidence === 0 ? 1 : Math.max(0.5, asrConfidence);

    if (INTERRUPT.test(text)) return { intent: 'interrupt', confidence: 0.97, source: 'pattern', text };

    // 1. deterministic patterns — unambiguous fixed commands only
    const pat = VOS.Intent.parse(text, ctx);
    if (isReal(pat.intent)) {
      // a bare number matched as reps is really a numeric question; let stage 2 judge it
      const bareNumeric = (pat.intent === 'reps' || pat.intent === 'rpe' || pat.intent === 'set_weight' || pat.intent === 'energy')
        && /^[\s\w.-]{0,18}$/.test(text) && VOS.Numbers.candidates(text).length === 1
        && !/kg|kilo|rpe|reps?|out of|tank/i.test(text);
      if (!bareNumeric) {
        return { intent: pat.intent, args: pat, confidence: +(0.93 * asr).toFixed(3), source: 'pattern', text };
      }
    }

    /* 2. numeric under an expectation, rescored across the recogniser's guesses.
       Only for short utterances. A number buried in a sentence — "I'm short on
       time, thirty five minutes" — is a sentence, and sentences go to the
       semantic layer. Guessing at them with pattern rules is how you end up
       logging "I hate Bulgarian split squats" as eight reps. */
    const words = text.trim().split(/\s+/).filter(Boolean);
    const shortEnough = words.length <= 4;
    const kinds = expectedKinds(dialogue, session);
    const anchors = anchorsFor(session);
    const num = shortEnough
      ? VOS.Numbers.rescoreAlternatives(alts && alts.length ? alts : [text], kinds, anchors)
      : null;
    if (num && num.confidence >= 0.42) {
      const intent = num.kind === 'weight' ? 'set_weight'
                   : num.kind === 'seconds' ? 'set_rest'
                   : num.kind;                                  // reps | rpe | energy | fatigue
      const args = { reps: num.value, rpe: num.value, weight: num.value,
                     energy: num.value, fatigue: num.value, seconds: num.value };
      // the expectation being explicitly open is strong evidence
      const expected = dialogue.topKind === num.kind;
      const conf = Math.min(0.97, num.confidence * asr * (expected ? 1.25 : 0.95));
      return {
        intent, args, confidence: +conf.toFixed(3), source: 'context', text,
        numeric: num, ambiguous: num.ambiguous && !expected,
        alternatives: num.alternatives
      };
    }

    // 3. pattern matched but we could not corroborate it numerically
    if (isReal(pat.intent)) return { intent: pat.intent, args: pat, confidence: +(0.7 * asr).toFixed(3), source: 'pattern', text };

    // 4. over to the semantic layer
    return { intent: 'unknown', confidence: 0, source: 'none', text };
  }

  /**
   * resolve({ text, alts, confidence, dialogue, session })
   * -> { plan: [clause...], tier, needsSemantic, correction, interrupted }
   */
  function resolve(input) {
    const { text, alts, dialogue, session } = input;
    const asrConf = input.confidence;
    const out = { raw: text, plan: [], needsSemantic: false, correction: null, tier: 'execute' };

    if (!text || !text.trim()) { out.needsSemantic = false; out.tier = 'reject'; return out; }

    // interruption short-circuits everything
    if (INTERRUPT.test(text)) {
      out.plan = [{ intent: 'interrupt', confidence: 0.97, source: 'pattern', text }];
      out.tier = 'execute';
      return out;
    }

    // "no, I did 9" — a denial carrying a replacement value
    let working = text;
    let negated = false;
    if (NEGATION_START.test(text) && VOS.Numbers.candidates(text).length) {
      negated = true;
      working = text.replace(NEGATION_START, '');
    }

    // self-correction: keep the tail, inherit the head's intent if the tail is bare
    const corr = splitCorrection(working);
    if (corr) {
      const headR = resolveClause(corr.head, null, dialogue, session, asrConf);
      let tailR = resolveClause(corr.tail, null, dialogue, session, asrConf);
      if (!isReal(tailR.intent) || (tailR.source === 'context' && isReal(headR.intent) && headR.intent !== tailR.intent)) {
        const n = VOS.Numbers.candidates(corr.tail);
        if (n.length && isReal(headR.intent)) {
          tailR = Object.assign({}, headR, {
            args: Object.assign({}, headR.args, {
              reps: n[0].value, weight: n[0].value, rpe: n[0].value, seconds: n[0].value
            }),
            text: corr.tail, source: 'correction',
            confidence: Math.min(0.95, headR.confidence)
          });
        }
      }
      out.correction = { from: corr.head, to: corr.tail, marker: corr.marker };
      out.plan = [tailR];
      out.tier = tierFor(tailR.intent, tailR.confidence);
      out.needsSemantic = !isReal(tailR.intent);
      return out;
    }

    // a denial with a number replaces what was just logged
    if (negated) {
      const r = resolveClause(working, alts, dialogue, session, asrConf);
      if (isReal(r.intent) && (r.intent === 'reps' || r.intent === 'rpe' || r.intent === 'set_weight')) {
        out.plan = [{
          intent: 'correct_last', source: 'correction', text,
          args: { field: r.intent === 'set_weight' ? 'weight' : r.intent, value: (r.args && (r.args.reps ?? r.args.rpe ?? r.args.weight)) },
          confidence: Math.min(0.9, r.confidence)
        }];
        out.tier = tierFor('correct_last', out.plan[0].confidence);
        return out;
      }
    }

    // multi-intent
    const ctx = { awaiting: dialogue.topKind, phase: session && session.phase };
    const clauses = segment(working, ctx);
    clauses.forEach(c => out.plan.push(resolveClause(c, clauses.length === 1 ? alts : null, dialogue, session, asrConf)));

    out.needsSemantic = out.plan.every(p => !isReal(p.intent));
    if (out.needsSemantic) { out.tier = 'semantic'; return out; }

    // the plan is only as safe as its least certain step
    const tiers = out.plan.filter(p => isReal(p.intent)).map(p => tierFor(p.intent, p.confidence));
    out.tier = tiers.includes('clarify') ? 'clarify' : tiers.includes('confirm') ? 'confirm' : 'execute';
    if (out.plan.some(p => p.ambiguous)) out.tier = 'clarify';
    return out;
  }

  VOS.Resolver = { resolve, resolveClause, segment, splitCorrection, tierFor, expectedKinds, anchorsFor, RISK, INTERRUPT };
})(typeof globalThis !== 'undefined' ? globalThis : this);
