/* ============================================================
   21-soreness.js : what the person actually feels

   The recovery model is an estimate built from volume and time. It
   is usually close and occasionally wrong. This module is how it
   finds out which.

   Three jobs:
     predict()   — what the model expects to be sore, so the coach can
                   ask a question worth answering instead of an open one
     parse()     — turn "yeah, quads are wrecked" into a reading
     merge()     — a reading from the person overrides the estimate for
                   that muscle, and decays from there on its own clock

   The interesting case is soreness the model did not predict. Sore
   in the wrong place after a movement usually means something —
   a lower back after squats is bracing, not legs.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* ------------------------------------------------------------
     WHAT PEOPLE ACTUALLY SAY
     Ordered longest-first so "lower back" wins over "back".
     ------------------------------------------------------------ */
  const ALIASES = [
    [/\blower\s*back\b|\blow\s*back\b|\blumbar\b/, ['back_lower']],
    [/\bupper\s*back\b|\bbetween (?:my )?(?:shoulder )?blades\b|\bmid\s*back\b/, ['trap_mid']],
    [/\bupper\s*traps?\b|\btraps?\b|\btrapezius\b/, ['trap_upper']],
    [/\bfront\s*delts?\b|\bfront (?:of (?:my )?)?shoulders?\b/, ['delt_front']],
    [/\brear\s*delts?\b|\bback (?:of (?:my )?)?shoulders?\b/, ['delt_rear']],
    [/\bside\s*delts?\b|\bmedial\s*delts?\b/, ['delt_side']],
    [/\bshoulders?\b|\bdelts?\b/, ['delt_front', 'delt_side', 'delt_rear']],
    [/\bupper\s*chest\b/, ['chest_upper']],
    [/\bchest\b|\bpecs?\b|\bpectorals?\b/, ['chest_mid', 'chest_upper']],
    [/\blats?\b|\blatissimus\b|\bwings?\b/, ['lat']],
    [/\bbiceps?\b|\bguns?\b/, ['biceps']],
    [/\btriceps?\b|\btris\b/, ['triceps']],
    [/\bforearms?\b|\bgrip\b|\bwrists?\b/, ['forearm']],
    [/\bobliques?\b|\bsides?\b|\blove handles\b/, ['oblique']],
    [/\babs?\b|\bcore\b|\bstomach\b|\bmidsection\b/, ['abs_upper', 'abs_lower']],
    [/\bserratus\b/, ['serratus']],
    [/\bneck\b/, ['neck']],
    [/\bquads?\b|\bthighs?\b|\bquadriceps\b|\bfront (?:of (?:my )?)?legs?\b/, ['quad']],
    [/\bhams?(?:trings?)?\b|\bhammies\b|\bback (?:of (?:my )?)?legs?\b/, ['hamstring']],
    [/\bglutes?\b|\bbum\b|\bbutt\b|\barse\b|\bass\b/, ['glute']],
    [/\bcalves\b|\bcalf\b/, ['calf']],
    [/\badductors?\b|\bgroin\b|\binner\s*thighs?\b/, ['adductor']],
    [/\babductors?\b|\bouter\s*(?:thighs?|hips?)\b|\bhips?\b/, ['abductor']],
    [/\bshins?\b|\btibialis\b/, ['tibialis']],
    [/\barms?\b/, ['biceps', 'triceps']],
    [/\bback\b/, ['lat', 'trap_mid']],
    [/\blegs?\b/, ['quad', 'hamstring', 'glute']],
    [/\beverything\b|\ball over\b|\bwhole body\b/, ['__all__']]
  ];

  /* How sore, on a 0–3 scale. Order matters: check the strong forms first. */
  const LEVELS = [
    [/\bnot (?:at all|really|especially|very)\b|\bnothing\b|\bfine\b|\bgood\b|\bno\b|\bnope\b|\ball good\b|\bfresh\b|\bnope\b/, 0],
    [/\bwrecked\b|\bdestroyed\b|\bcan(?:'t| ?not) (?:walk|move|straighten)\b|\bkilling me\b|\bagony\b|\breally sore\b|\bvery sore\b|\bproperly sore\b|\bbrutal\b/, 3],
    [/\bquite sore\b|\bpretty sore\b|\bdefinitely\b|\byeah\b|\byep\b|\bsore\b|\btender\b|\bstiff\b|\bfeeling (?:it|them)\b/, 2],
    [/\ba (?:bit|little)\b|\bslightly\b|\bmild(?:ly)?\b|\bbarely\b|\bjust a touch\b|\bnot much\b|\bmanageable\b/, 1]
  ];

  function levelFrom(text) {
    const t = ' ' + String(text || '').toLowerCase() + ' ';
    // a hedge in front of a strong word wins: "a bit sore" is 1, not 2
    if (/\b(?:a (?:bit|little)|slightly|mildly|barely|just a touch)\b/.test(t)) return 1;
    for (const [re, v] of LEVELS) if (re.test(t)) return v;
    return null;
  }

  /* ------------------------------------------------------------
     PARSE
     Returns { muscles, level, mentioned } — muscles empty means the
     person answered about whatever we asked, not about a new place.
     ------------------------------------------------------------ */
  function parse(text, opts) {
    const t = ' ' + String(text || '').toLowerCase().replace(/[^\w\s']/g, ' ').replace(/\s+/g, ' ') + ' ';
    const found = [];
    let rest = t;
    ALIASES.forEach(([re, ids]) => {
      if (re.test(rest)) {
        ids.forEach(id => { if (!found.includes(id)) found.push(id); });
        rest = rest.replace(new RegExp(re.source, 'g'), ' ');
      }
    });

    let muscles = found;
    if (muscles.includes('__all__')) muscles = Object.keys(VOS.MUSCLES);

    let level = levelFrom(t);
    // "my shoulder is sore" with no grading is a real report, not a zero
    if (level == null && muscles.length) level = 2;

    return {
      muscles,
      level,
      mentioned: muscles.length > 0,
      about: (opts && opts.about) || null
    };
  }

  /* ------------------------------------------------------------
     PREDICT — what the model thinks should be sore right now
     ------------------------------------------------------------ */
  function predict(history, opts) {
    if (!VOS.Recovery) return [];
    /* Deliberately blind to previous reports. The point of the question
       is to test what the model worked out on its own — feeding earlier
       answers back in would turn it into "you said you were sore, are
       you sore?" */
    const st = VOS.Recovery.state(history, null, Object.assign({ reports: [] }, opts || {}));
    return Object.keys(st)
      .map(id => ({ id, name: (VOS.MUSCLES[id] || {}).name || id, fatigue: st[id].fatigue,
                    lastHitAt: st[id].lastHitAt }))
      .filter(x => x.fatigue >= 0.35)
      .sort((a, b) => b.fatigue - a.fatigue);
  }

  /* Group the prediction so the coach names two areas, not nine muscles. */
  function predictedAreas(history, opts) {
    const rows = predict(history, opts);
    const seen = {}, out = [];
    rows.forEach(r => {
      const g = (VOS.MUSCLES[r.id] || {}).group || 'other';
      if (!seen[g]) { seen[g] = true; out.push({ group: g, lead: r }); }
    });
    return out;
  }

  /* ------------------------------------------------------------
     WHY IS THAT SORE?
     Some mismatches have a known cause worth saying out loud.
     ------------------------------------------------------------ */
  const EXPLAIN = {
    back_lower: { after: ['squat', 'hinge', 'lunge'],
                  say: 'A lower back that is sorer than the legs after squatting or pulling usually means the brace is giving way before the legs do, not that the back did the work.' },
    delt_front: { after: ['horizontal push', 'incline push'],
                  say: 'Front delts sore after pressing usually means the bar is drifting high, or the shoulder blades are not set.' },
    forearm:    { after: ['horizontal pull', 'vertical pull', 'hinge'],
                  say: 'Forearms going before the back does is a grip limit, not a back limit. Straps or a hook grip would let the back get the work.' },
    neck:       { after: ['squat', 'horizontal push', 'vertical push'],
                  say: 'A sore neck after lifting is usually tension rather than training — worth watching where your head is pointing under the bar.' },
    biceps:     { after: ['horizontal pull', 'vertical pull'],
                  say: 'Biceps sorer than the back after rowing means the arms are starting the pull. Think about leading with the elbows.' },
    trap_upper: { after: ['horizontal pull', 'vertical push'],
                  say: 'Upper traps taking over is common when the weight creeps up. Not a problem on its own, worth knowing.' }
  };

  /* Which recent movement most likely caused soreness in this muscle. */
  function blame(history, muscleId, sinceMs) {
    const cutoff = sinceMs || (Date.now() - 4 * 864e5);
    let best = null;
    (history || []).forEach(h => {
      const when = Date.parse(h.date || 0) || 0;
      if (when < cutoff) return;
      (h.exercises || []).forEach(ex => {
        if (!ex.sets || !ex.sets.length) return;
        const contrib = (VOS.muscles ? VOS.muscles(ex.name) : {})[muscleId] || 0;
        if (!contrib) return;
        const score = contrib * ex.sets.length;
        if (!best || score > best.score) best = { name: ex.name, score, when, contrib };
      });
    });
    return best;
  }

  /* An unexpected report, with a reason if we have one. */
  function explain(history, muscleId) {
    const cause = blame(history, muscleId);
    const e = EXPLAIN[muscleId];
    if (!e) return cause ? { cause, say: null } : null;
    if (!cause) return { cause: null, say: e.say };
    const meta = VOS.exerciseMeta ? VOS.exerciseMeta(cause.name) : null;
    const pat = meta && meta.pattern;
    return { cause, say: e.after.includes(pat) ? e.say : null };
  }

  /* ------------------------------------------------------------
     STORE
     ------------------------------------------------------------ */
  function record(memory, report) {
    const d = memory.data || memory;
    if (!d.soreness) d.soreness = [];
    d.soreness.push({
      t: report.at || Date.now(),
      muscle: report.muscle,
      level: report.level,           // 0–3
      expected: !!report.expected,
      note: report.note || null
    });
    if (d.soreness.length > 400) d.soreness = d.soreness.slice(-400);
    if (memory.save) memory.save();
    return d.soreness;
  }

  /* Only the most recent reading per muscle matters, and only if it is
     recent enough to still be about today. */
  function latest(reports, maxAgeH) {
    const cut = Date.now() - (maxAgeH || 96) * 3600000;
    const by = {};
    (reports || []).forEach(r => {
      if (r.t < cut) return;
      if (!by[r.muscle] || r.t > by[r.muscle].t) by[r.muscle] = r;
    });
    return by;
  }

  VOS.Soreness = {
    parse, levelFrom, predict, predictedAreas, record, latest, blame, explain,
    ALIASES, EXPLAIN
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
