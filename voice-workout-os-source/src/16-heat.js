/* ============================================================
   16-heat.js : what have you actually hit?
   Stimulus is counted in effective sets, not kilos, so bodyweight
   work and barbell work land on the same scale. Volume-load is
   tracked alongside it for the detail view.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  // Roughly a week of productive work for one muscle. Heat 1.0 = target met.
  const WEEKLY_TARGET_SETS = 12;
  const SESSION_TARGET_SETS = 6;
  const HALF_LIFE_DAYS = 3.2;          // how fast a session fades off the map

  /* A set is not a set: very low or very high reps stimulate less per set,
     and an easy set counts for less than a hard one. */
  function setQuality(set, ex) {
    let q = 1;
    const r = set.reps || 0;
    if (r < 5) q *= 0.9;
    else if (r > 20) q *= 0.8;
    else if (r > 15) q *= 0.92;
    if (typeof set.rpe === 'number') {
      if (set.rpe >= 9) q *= 1.15;
      else if (set.rpe >= 8) q *= 1.05;
      else if (set.rpe <= 6) q *= 0.75;
    }
    return q;
  }

  /* A recovered muscle should sink back into the body, not glow grey at
     you. Only heat is allowed to be bright — that way the map reads at a
     glance as "here is what is still sore", instead of as a diagram with
     some red on it. */
  const RAMP = [
    [0.00, [44, 49, 58]],
    [0.14, [66, 64, 68]],
    [0.32, [118, 88, 60]],
    [0.55, [178, 96, 52]],
    [0.78, [216, 70, 46]],
    [1.00, [240, 54, 40]]
  ];

  function colorFor(h) {
    const t = Math.max(0, Math.min(1, h || 0));
    for (let i = 1; i < RAMP.length; i++) {
      if (t <= RAMP[i][0]) {
        const [t0, c0] = RAMP[i - 1], [t1, c1] = RAMP[i];
        const k = (t - t0) / (t1 - t0 || 1);
        const c = c0.map((v, j) => Math.round(v + (c1[j] - v) * k));
        return `rgb(${c[0]},${c[1]},${c[2]})`;
      }
    }
    return 'rgb(240,54,40)';
  }

  /* Accumulate one exercise's sets into the tally. */
  function addExercise(tally, exName, sets, weight, opts) {
    const contrib = VOS.muscles(exName);
    const decay = opts && opts.decay != null ? opts.decay : 1;
    (sets || []).forEach(set => {
      const q = setQuality(set, exName) * decay;
      const load = (set.weight || 0) > 0 ? set.weight : (opts && opts.bodyweight ? opts.bodyweight * 0.45 : 30);
      Object.keys(contrib).forEach(m => {
        const c = contrib[m];
        if (c < 0.15) return;
        const t = tally[m] || (tally[m] = { sets: 0, effSets: 0, volume: 0, exercises: {} });
        t.sets += c >= 0.85 ? 1 : 0;
        t.effSets += c * q;
        t.volume += c * (set.reps || 0) * load;
        t.exercises[exName] = (t.exercises[exName] || 0) + c * q;
      });
    });
  }

  function blank() {
    const t = {};
    Object.keys(VOS.MUSCLES).forEach(m => { t[m] = { sets: 0, effSets: 0, volume: 0, exercises: {} }; });
    return t;
  }

  function finish(tally, target) {
    const out = {};
    Object.keys(tally).forEach(m => {
      const t = tally[m];
      out[m] = {
        sets: Math.round(t.sets * 10) / 10,
        effSets: Math.round(t.effSets * 10) / 10,
        volume: Math.round(t.volume),
        heat: Math.min(1, t.effSets / target),
        top: Object.keys(t.exercises).sort((a, b) => t.exercises[b] - t.exercises[a]).slice(0, 4)
      };
    });
    return out;
  }

  /* ---------- the two views ---------- */

  /* Live session, including sets logged a minute ago. */
  function forSession(session, opts) {
    const tally = blank();
    (session.exercises || []).forEach(ex => {
      if (!ex.sets || !ex.sets.length) return;
      addExercise(tally, ex.name, ex.sets, ex.weight, opts);
    });
    return finish(tally, (opts && opts.target) || SESSION_TARGET_SETS);
  }

  /* Rolling window across stored history, faded by how long ago it was. */
  function forHistory(history, opts) {
    opts = opts || {};
    const days = opts.days || 7;
    const now = opts.now || Date.now();
    const tally = blank();
    (history || []).forEach(h => {
      const t = Date.parse(h.date || 0) || 0;
      const ageDays = (now - t) / 86400000;
      if (ageDays > days || ageDays < -1) return;
      const decay = opts.decay === false ? 1 : Math.pow(0.5, Math.max(0, ageDays) / HALF_LIFE_DAYS);
      (h.exercises || []).forEach(ex => {
        if (!ex.sets || !ex.sets.length) return;
        addExercise(tally, ex.name, ex.sets, null, Object.assign({}, opts, { decay }));
      });
    });
    return finish(tally, opts.target || WEEKLY_TARGET_SETS);
  }

  /* Merge the finished week with the live session so the map moves as you lift. */
  function combined(history, session, opts) {
    const week = forHistory(history, opts);
    if (!session) return week;
    const live = forSession(session, Object.assign({}, opts, { target: WEEKLY_TARGET_SETS }));
    const out = {};
    Object.keys(week).forEach(m => {
      out[m] = {
        sets: week[m].sets + live[m].sets,
        effSets: Math.round((week[m].effSets + live[m].effSets) * 10) / 10,
        volume: week[m].volume + live[m].volume,
        heat: Math.min(1, week[m].heat + live[m].heat),
        top: Array.from(new Set(live[m].top.concat(week[m].top))).slice(0, 4),
        live: live[m].effSets > 0
      };
    });
    return out;
  }

  /* ---------- what is being neglected ---------- */
  function gaps(heatMap, threshold) {
    const th = threshold == null ? 0.25 : threshold;
    return Object.keys(heatMap)
      .filter(m => heatMap[m].heat < th)
      .map(m => ({ id: m, name: VOS.MUSCLES[m].name, heat: heatMap[m].heat, group: VOS.MUSCLES[m].group }))
      .sort((a, b) => a.heat - b.heat);
  }

  function byGroup(heatMap) {
    const g = {};
    VOS.MUSCLE_GROUPS.forEach(k => { g[k] = { effSets: 0, volume: 0, n: 0 }; });
    Object.keys(heatMap).forEach(m => {
      const grp = VOS.MUSCLES[m].group;
      if (!g[grp]) return;
      g[grp].effSets += heatMap[m].effSets;
      g[grp].volume += heatMap[m].volume;
      g[grp].n++;
    });
    Object.keys(g).forEach(k => {
      g[k].effSets = Math.round(g[k].effSets * 10) / 10;
      g[k].heat = g[k].n ? Math.min(1, g[k].effSets / (WEEKLY_TARGET_SETS * g[k].n * 0.55)) : 0;
    });
    return g;
  }

  /* ---------- painting an already-rendered figure ---------- */
  function paint(rootEl, heatMap) {
    if (!rootEl) return;
    const zones = rootEl.querySelectorAll('.mz');
    for (let i = 0; i < zones.length; i++) {
      const z = zones[i];
      const m = z.getAttribute('data-m');
      const h = (heatMap[m] && heatMap[m].heat) || 0;
      z.style.fill = colorFor(h);
      z.classList.toggle('hot', h >= 0.75);
      z.classList.toggle('live', !!(heatMap[m] && heatMap[m].live));
      /* A reading the person gave us gets an outline, so the map
         distinguishes what it was told from what it worked out. */
      z.classList.toggle('reported', !!(heatMap[m] && heatMap[m].reported));
      z.setAttribute('data-heat', h.toFixed(2));
    }
  }

  VOS.Heat = {
    forSession, forHistory, combined, gaps, byGroup, paint, colorFor,
    setQuality, WEEKLY_TARGET_SETS, SESSION_TARGET_SETS, HALF_LIFE_DAYS, RAMP
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
