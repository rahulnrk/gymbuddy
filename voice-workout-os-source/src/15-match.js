/* ============================================================
   15-match.js : FORGIVING PROGRAMME IMPORT
   The user should never have to learn a format. This takes whatever
   they typed and works out what they meant, filling in sensible
   defaults for anything they left out and flagging nothing as fatal.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const ABBREV = {
    bb: 'barbell', db: 'dumbbell', kb: 'kettlebell', ez: 'ez', sm: 'smith',
    ohp: 'overhead press', rdl: 'romanian deadlift', dl: 'deadlift',
    bss: 'bulgarian split squat', cgbp: 'close grip bench press',
    sldl: 'romanian deadlift', tri: 'triceps', bi: 'biceps', bis: 'biceps',
    ext: 'extension', ext_: 'extension', lat: 'lat', pulldowns: 'pulldown'
  };

  const SINGULAR = [
    [/\bflyes\b/g, 'fly'], [/\bflies\b/g, 'fly'], [/\bflys\b/g, 'fly'],
    [/\bpresses\b/g, 'press'], [/\braises\b/g, 'raise'], [/\bcurls\b/g, 'curl'],
    [/\bextensions\b/g, 'extension'], [/\brows\b/g, 'row'], [/\bsquats\b/g, 'squat'],
    [/\bdeadlifts\b/g, 'deadlift'], [/\bpushdowns\b/g, 'pushdown'], [/\bdips\b/g, 'dip'],
    [/\bthrusts\b/g, 'thrust'], [/\bshrugs\b/g, 'shrug'], [/\bplanks\b/g, 'plank'],
    [/\bswings\b/g, 'swing'], [/\bcrunches\b/g, 'crunch'], [/\blunges\b/g, 'lunge'],
    [/\bpull[\s-]?ups?\b/g, 'pull up'], [/\bchin[\s-]?ups?\b/g, 'chin up'], [/\bpush[\s-]?ups?\b/g, 'push up'],
    [/\bsit[\s-]?ups?\b/g, 'sit up'], [/\bstep[\s-]?ups?\b/g, 'step up'],
    [/\bcrushers\b/g, 'crusher'], [/\bskull ?crushers?\b/g, 'skullcrusher'],
    [/\binclined\b/g, 'incline'], [/\bdeclined\b/g, 'decline'], [/\bseated\b/g, 'seated'],
    [/\bcalves\b/g, 'calf'], [/\bhamstrings\b/g, 'hamstring'], [/\bquads\b/g, 'quad'],
    [/\btricep\b/g, 'triceps'], [/\bbicep\b/g, 'biceps'], [/\bdelts?\b/g, 'delt']
  ];

  function normalise(s) {
    let t = (s || '').toLowerCase()
      .replace(/[^\w\s-]/g, ' ')
      .replace(/[-_]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    t = t.split(' ').map(w => ABBREV[w] || w).join(' ');
    SINGULAR.forEach(([re, to]) => { t = t.replace(re, to); });
    return t.replace(/\s+/g, ' ').trim();
  }

  /* Match a free-text exercise name against the library.
     Returns { name, confidence: 'exact'|'alias'|'guess'|'custom', from } */
  function matchExercise(raw) {
    const q = normalise(raw);
    if (!q) return null;

    const names = Object.keys(VOS.EXERCISES);
    const byNorm = new Map();
    names.forEach(n => byNorm.set(normalise(n), n));

    if (byNorm.has(q)) return { name: byNorm.get(q), confidence: 'exact', from: raw };

    const aliasNorm = new Map();
    Object.keys(VOS.ALIASES || {}).forEach(k => aliasNorm.set(normalise(k), VOS.ALIASES[k]));
    if (aliasNorm.has(q)) return { name: aliasNorm.get(q), confidence: 'alias', from: raw };

    // token overlap, preferring the candidate with the least unmatched padding
    const qt = q.split(' ').filter(Boolean);
    let best = null;
    const consider = (candNorm, candName, penalty) => {
      const ct = candNorm.split(' ').filter(Boolean);
      const hit = qt.filter(w => ct.includes(w)).length;
      if (!hit) return;
      const coverage = hit / qt.length;             // how much of what they typed we used
      const extra = ct.length - hit;                // how much we invented
      if (coverage < 0.6) return;
      const score = coverage * 100 - extra * 6 - penalty;
      if (!best || score > best.score) best = { name: candName, score, coverage };
    };
    byNorm.forEach((name, norm) => consider(norm, name, 0));
    aliasNorm.forEach((name, norm) => consider(norm, name, 2));

    if (best && best.score >= 55) return { name: best.name, confidence: 'guess', from: raw };

    // one more go with a naive de-pluralisation, for words the table misses
    const depl = qt.map(w => (w.length > 3 && /s$/.test(w) && !/ss$/.test(w) ? w.slice(0, -1) : w)).join(' ');
    if (depl !== q) {
      if (byNorm.has(depl))   return { name: byNorm.get(depl), confidence: 'alias', from: raw };
      if (aliasNorm.has(depl)) return { name: aliasNorm.get(depl), confidence: 'alias', from: raw };
    }

    // Never reject. An unknown movement still works — it just gets defaults.
    return { name: titleCase(raw.trim()), confidence: 'custom', from: raw };
  }

  function titleCase(s) {
    return s.replace(/\s+/g, ' ').trim()
      .replace(/\b[a-z]/g, c => c.toUpperCase())
      .replace(/\bOf\b|\bAnd\b|\bThe\b/g, m => m.toLowerCase());
  }

  /* Is this line a day heading rather than an exercise? */
  const DAY_WORDS = /^(push|pull|legs?|upper|lower|chest|back|shoulders?|arms?|full body|day ?\d*|week ?\d*|a|b|c|d|glutes?|core|abs|cardio|rest)\b/i;
  const SPLIT_WORD = /\b(push|pull|legs?|upper|lower|chest|back|shoulders?|arms?|full ?body|glutes?|core|abs|cardio|day|week|session|workout)\b/i;

  function dayHeading(line) {
    const t = line.trim();
    if (/^#+\s*/.test(t)) return clean(t.replace(/^#+\s*/, '')) || 'Day';
    if (/^\[.+\]$/.test(t)) return clean(t.slice(1, -1));
    if (/[:：]\s*$/.test(t)) return clean(t.replace(/[:：]\s*$/, ''));
    // "Day 3 - Legs", "Week 2", "Session B"
    if (/^(day|week|session|workout)\s*\d*\b/i.test(t) && !/\d\s*[x×]/i.test(t)) return clean(t);
    // A line with no numbers that names a split and isn't a movement we know.
    if (!/\d/.test(t) && t.split(/\s+/).length <= 5 && SPLIT_WORD.test(t)) {
      const m = matchExercise(t);
      if (!m || (m.confidence !== 'exact' && m.confidence !== 'alias')) return clean(t);
    }
    return null;
  }

  function clean(s) { return s.replace(/[:\-–—!.]+\s*$/, '').replace(/\s+/g, ' ').trim(); }

  /* Pull sets / reps / weight out of a line in any order and any notation. */
  function parseLine(raw) {
    // The old strict format still works: Name | sets | low-high | kg
    const piped = raw.split('|').map(x => x.trim());
    if (piped.length >= 3 && piped[0] && /^\d/.test(piped[1] || '')) {
      const reps = (piped[2] || '').split(/[-–]|\s+to\s+/).map(x => parseInt(x, 10));
      return {
        name: piped[0],
        sets: parseInt(piped[1], 10) || null,
        repLow: isNaN(reps[0]) ? null : reps[0],
        repHigh: isNaN(reps[1]) ? (isNaN(reps[0]) ? null : reps[0]) : reps[1],
        weight: piped[3] != null && piped[3] !== '' ? (parseFloat(piped[3]) || 0) : null,
        unit: /lb|pound/i.test(piped[3] || '') ? 'lb' : null
      };
    }
    let t = ' ' + raw.trim().replace(/^[-*•·\d]+[.)]?\s+/, '') + ' ';
    const found = { sets: null, repLow: null, repHigh: null, weight: null, unit: null };
    const eat = (re, fn) => {
      const m = t.match(re);
      if (!m) return false;
      fn(m);
      t = t.slice(0, m.index) + ' ' + t.slice(m.index + m[0].length);
      return true;
    };

    // weight first — it is the least ambiguous
    eat(/(\d+(?:[.,]\d+)?)\s*(kgs?|kilos?|kilograms?|lbs?|pounds?)\b/i, m => {
      found.weight = parseFloat(m[1].replace(',', '.'));
      found.unit = /lb|pound/i.test(m[2]) ? 'lb' : 'kg';
    });
    if (found.weight == null) eat(/@\s*(\d+(?:[.,]\d+)?)/, m => { found.weight = parseFloat(m[1].replace(',', '.')); });
    if (found.weight == null) eat(/\bbody ?weight\b|\bbw\b/i, () => { found.weight = 0; });

    // sets x reps, with an optional rep range
    eat(/(\d+)\s*[x×*]\s*(\d+)\s*(?:[-–]|\s+to\s+)\s*(\d+)/i, m => {
      found.sets = +m[1]; found.repLow = +m[2]; found.repHigh = +m[3];
    });
    if (found.sets == null) eat(/(\d+)\s*[x×*]\s*(\d+)/i, m => { found.sets = +m[1]; found.repLow = found.repHigh = +m[2]; });
    if (found.sets == null) eat(/(\d+)\s*(?:sets?|sX|s)\b(?:\s*(?:of|x|×)\s*(\d+)(?:\s*(?:[-–]|to)\s*(\d+))?)?/i, m => {
      found.sets = +m[1];
      if (m[2]) { found.repLow = +m[2]; found.repHigh = m[3] ? +m[3] : +m[2]; }
    });
    if (found.repLow == null) eat(/(\d+)\s*(?:[-–]|\s+to\s+)\s*(\d+)\s*(?:reps?)?\b/i, m => { found.repLow = +m[1]; found.repHigh = +m[2]; });
    if (found.repLow == null) eat(/(\d+)\s*reps?\b/i, m => { found.repLow = found.repHigh = +m[1]; });
    if (found.sets == null) eat(/[x×]\s*(\d+)\b/i, m => { found.sets = +m[1]; });

    const name = t.replace(/\|/g, ' ')
      .replace(/\b(?:of|for|at|with|each|per side|ea)\b/gi, ' ')
      .replace(/\s+/g, ' ').trim();

    return { name, ...found };
  }

  const DEFAULT_REPS = {
    barbell:   { compound: [6, 8],  isolation: [10, 12] },
    dumbbell:  { compound: [8, 10], isolation: [10, 12] },
    machine:   { compound: [10, 12], isolation: [12, 15] },
    cable:     { compound: [10, 12], isolation: [12, 15] },
    bodyweight:{ compound: [6, 10], isolation: [10, 15] }
  };

  /* The whole import. Never throws, never rejects a line outright. */
  function parseProgram(text, opts) {
    opts = opts || {};
    const days = [];
    let cur = null;
    const lines = (text || '').split('\n');

    lines.forEach((rawLine, lineNo) => {
      const line = rawLine.trim();
      if (!line) return;
      if (/^(\/\/|;)/.test(line)) return;

      const heading = dayHeading(line);
      if (heading) { cur = { name: titleCase(heading), exercises: [] }; days.push(cur); return; }

      const bits = parseLine(line);
      if (!bits.name) return;

      const match = matchExercise(bits.name);
      if (!match) return;
      if (!cur) { cur = { name: 'Day 1', exercises: [] }; days.push(cur); }

      const meta = VOS.exerciseMeta(match.name);
      const def = (DEFAULT_REPS[meta.load] || DEFAULT_REPS.barbell)[meta.kind];
      const idx = cur.exercises.length;

      let weight = bits.weight;
      if (weight != null && bits.unit === 'lb') weight = Math.round(weight * 0.4536 * 2) / 2;
      const needsWeight = weight == null && meta.load !== 'bodyweight';

      const notes = [];
      if (match.confidence === 'custom') notes.push('new movement — added as you wrote it');
      const assumed = [];
      if (bits.sets == null)   assumed.push('3 sets');
      if (bits.repLow == null) assumed.push(`${def[0]}–${def[1]} reps`);
      if (assumed.length)      notes.push('assumed ' + assumed.join(', '));
      if (needsWeight)         notes.push("I'll ask the load in the gym");

      cur.exercises.push({
        name: match.name,
        typed: bits.name,
        sets: bits.sets != null ? Math.min(10, Math.max(1, bits.sets)) : 3,
        repLow: bits.repLow != null ? bits.repLow : def[0],
        repHigh: bits.repHigh != null ? bits.repHigh : (bits.repLow != null ? bits.repLow : def[1]),
        weight: weight != null ? weight : 0,
        needsWeight,
        priority: idx < 2 ? 1 : (idx < 4 ? 2 : 3),
        confidence: match.confidence,
        notes,
        line: lineNo
      });
    });

    const exCount = days.reduce((n, d) => n + d.exercises.length, 0);
    return {
      id: 'custom',
      name: opts.name || 'My programme',
      goal: opts.goal || 'hypertrophy',
      level: opts.level || 'intermediate',
      schedule: [],
      days,
      stats: {
        days: days.length,
        exercises: exCount,
        guessed: days.reduce((n, d) => n + d.exercises.filter(e => e.confidence === 'guess').length, 0),
        custom: days.reduce((n, d) => n + d.exercises.filter(e => e.confidence === 'custom').length, 0),
        needWeight: days.reduce((n, d) => n + d.exercises.filter(e => e.needsWeight).length, 0)
      }
    };
  }

  VOS.Import = { normalise, matchExercise, parseLine, parseProgram, dayHeading, titleCase, DEFAULT_REPS };
})(typeof globalThis !== 'undefined' ? globalThis : this);
