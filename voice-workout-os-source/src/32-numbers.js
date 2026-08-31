/* ============================================================
   32-numbers.js : contextual numeric resolution
   Numbers are the failure point. "Eight" can be reps, RPE, kilos,
   minutes or a fatigue score, and the recogniser routinely returns
   "ate", "hate", "a", "eat". This scores every candidate reading
   against what the workout expects right now, instead of guessing.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  // Homophones and recogniser slips, mapped to what they probably were.
  const SOUNDALIKE = {
    eight:8, ate:8, eat:8, hate:8, late:8, wait:8, weight:8, eighth:8, ait:8,
    to:2, too:2, tu:2, two:2, tue:2,
    for:4, fore:4, four:4, ford:4,
    free:3, tree:3, three:3, thee:3,
    won:1, one:1, wan:1, an:1, a:1,
    sex:6, six:6, sicks:6, sick:6,
    seven:7, heaven:7, sudden:7,
    nine:9, nain:9, dine:9, mine:9, line:9,
    ten:10, tan:10, then:10, than:10,
    five:5, hive:5, fife:5,
    zero:0, sero:0, nil:0,
    eleven:11, twelve:12, thirteen:13, fourteen:14, fifteen:15,
    sixteen:16, seventeen:17, eighteen:18, nineteen:19, twenty:20,
    thirty:30, forty:40, fourty:40, fifty:50, sixty:60, seventy:70,
    eighty:80, ninety:90, hundred:100
  };

  // What a number probably means, given what the system is waiting for.
  // Each entry is a plausible range plus how far from an anchor is normal.
  const EXPECT = {
    reps:    { lo: 1,  hi: 50,  anchorSpread: 6,   name: 'reps' },
    rpe:     { lo: 4,  hi: 10,  anchorSpread: 2,   name: 'RPE' },
    weight:  { lo: 0,  hi: 400, anchorSpread: 25,  name: 'kilos' },
    energy:  { lo: 1,  hi: 10,  anchorSpread: 3,   name: 'energy' },
    fatigue: { lo: 1,  hi: 10,  anchorSpread: 3,   name: 'fatigue' },
    seconds: { lo: 10, hi: 600, anchorSpread: 90,  name: 'seconds' }
  };

  function wordNumber(word) {
    const w = (word || '').toLowerCase().replace(/[^a-z]/g, '');
    return SOUNDALIKE[w] != null ? SOUNDALIKE[w] : null;
  }

  /* Every number-ish reading of a phrase, with how sure we are it is a number
     at all (digits beat spelled words beat homophones). */
  /* Homophone readings are only plausible in a short, number-shaped
     utterance. "hate" in "I hate Bulgarian split squats" is the word hate. */
  function candidates(text, allowSoundalike) {
    const out = [];
    const t = (text || '').toLowerCase();
    const wordCount = t.trim().split(/\s+/).filter(Boolean).length;
    const soundalikeOk = allowSoundalike != null ? allowSoundalike : wordCount <= 3;

    const digits = t.match(/\d+(?:[.,]\d+)?/g) || [];
    digits.forEach(d => out.push({ value: parseFloat(d.replace(',', '.')), form: 'digit', prior: 1.0 }));

    const words = t.split(/[\s-]+/).filter(Boolean);
    let skipNext = -1;
    words.forEach((w, i) => {
      const clean = w.replace(/[^a-z]/g, '');
      const v = SOUNDALIKE[clean];
      if (v == null) return;
      // "twenty two"
      const nxt = (words[i + 1] || '').replace(/[^a-z]/g, '');
      if (v >= 20 && v % 10 === 0 && SOUNDALIKE[nxt] != null && SOUNDALIKE[nxt] < 10) {
        out.push({ value: v + SOUNDALIKE[nxt], form: 'compound', prior: 1.0 });
        skipNext = i + 1;
      }
      if (i === skipNext) return;
      // a true number word is far more likely than a homophone of one
      const isRealWord = /^(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fourty|fifty|sixty|seventy|eighty|ninety|hundred)$/.test(clean);
      if (!isRealWord && !soundalikeOk) return;
      out.push({ value: v, form: isRealWord ? 'word' : 'soundalike', prior: isRealWord ? 0.9 : 0.35 });
    });

    // dedupe, keeping the strongest reading of each value
    const best = new Map();
    out.forEach(c => { const p = best.get(c.value); if (!p || c.prior > p.prior) best.set(c.value, c); });
    return Array.from(best.values());
  }

  /* Score one numeric value against an expectation.
     anchor = what we'd expect it to be near (last weight, target reps, ...) */
  function fit(value, kind, anchor) {
    const e = EXPECT[kind];
    if (!e) return 0;
    if (value < e.lo || value > e.hi) return 0;
    let s = 0.55;
    if (anchor != null) {
      const d = Math.abs(value - anchor);
      s += 0.45 * Math.exp(-(d * d) / (2 * e.anchorSpread * e.anchorSpread));
    } else {
      s += 0.2;
    }
    // weights in a gym are overwhelmingly multiples of 2.5
    if (kind === 'weight' && value > 0) s += (Math.abs(value / 2.5 - Math.round(value / 2.5)) < 0.01) ? 0.12 : -0.12;
    if (kind === 'rpe' && (value % 0.5 !== 0)) s -= 0.2;
    return Math.max(0, Math.min(1, s));
  }

  /**
   * Resolve a spoken phrase to a number under a known expectation.
   * kinds: ordered list of plausible kinds, most likely first.
   * anchors: { reps: 8, weight: 60, rpe: 8, ... }
   * -> { value, kind, confidence, alternatives:[{value,kind,confidence}] }
   */
  function resolve(text, kinds, anchors) {
    anchors = anchors || {};
    const cands = candidates(text);
    if (!cands.length) return null;
    const scored = [];
    cands.forEach(c => {
      kinds.forEach((k, ki) => {
        const f = fit(c.value, k, anchors[k]);
        if (!f) return;
        const kindPrior = 1 - ki * 0.18;         // earlier kinds are likelier
        scored.push({ value: c.value, kind: k, form: c.form, confidence: +(f * c.prior * kindPrior).toFixed(3) });
      });
    });
    if (!scored.length) return null;
    scored.sort((a, b) => b.confidence - a.confidence);
    const top = scored[0];
    const runnerUp = scored.find(s => s.value !== top.value || s.kind !== top.kind);
    return {
      value: top.value,
      kind: top.kind,
      confidence: top.confidence,
      // if two readings are close, the caller should ask rather than guess
      ambiguous: !!(runnerUp && top.confidence - runnerUp.confidence < 0.12),
      alternatives: scored.slice(1, 4)
    };
  }

  /* Pick the best of several ASR transcriptions for a numeric answer. */
  function rescoreAlternatives(alts, kinds, anchors) {
    let best = null;
    (alts || []).forEach((a, i) => {
      const r = resolve(a, kinds, anchors);
      if (!r) return;
      // the recogniser's own ordering is weak evidence
      const adjusted = r.confidence * (1 - i * 0.05);
      if (!best || adjusted > best.confidence) best = Object.assign({}, r, { confidence: +adjusted.toFixed(3), text: a, altIndex: i });
    });
    return best;
  }

  VOS.Numbers = { candidates, resolve, fit, rescoreAlternatives, wordNumber, EXPECT, SOUNDALIKE };
})(typeof globalThis !== 'undefined' ? globalThis : this);
