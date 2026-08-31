/* ============================================================
   17-recovery.js : how sore is each muscle right now?
   Redness on the map means "still recovering", not "volume done".
   Small muscles come back in a day and a half; hamstrings and the
   lower back take three or four. A heavier dose takes longer than a
   light one, and eccentric-heavy work takes longer than either.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* Hours to full recovery from a solid dose. These are working
     estimates, not measured truth — they live in one table on purpose
     so they are easy to argue with and easy to change. */
  const RECOVERY_H = {
    forearm: 30, tibialis: 30, neck: 34,
    abs_upper: 36, abs_lower: 36, delt_rear: 38,
    delt_side: 40, serratus: 40, oblique: 40, calf: 42,
    biceps: 42, delt_front: 44, triceps: 44,
    trap_upper: 48, abductor: 48,
    chest_upper: 48, trap_mid: 50,
    chest_mid: 52, lat: 52, adductor: 50,
    glute: 54, quad: 58, hamstring: 62, back_lower: 60
  };
  const MAX_WINDOW_H = 96;   // nothing sensible keeps a muscle down for four days

  /* Movements that load the stretched position hardest leave the most
     damage, so they hold a muscle down longer for the same set count. */
  const DAMAGE_BY_PATTERN = {
    'hinge': 1.3, 'hamstrings': 1.25, 'lunge': 1.2, 'squat': 1.15,
    'chest isolation': 1.15, 'lats': 1.1, 'vertical pull': 1.05,
    'core': 1.1, 'lower back': 1.25, 'carry': 1.1
  };
  const DAMAGE_BY_EXERCISE = {
    'Romanian Deadlift': 1.35, 'Nordic Curl': 1.5, 'Deadlift': 1.3,
    'Bulgarian Split Squat': 1.3, 'Walking Lunge': 1.25, 'Good Morning': 1.3,
    'Back Squat': 1.2, 'Front Squat': 1.15, 'Dumbbell Fly': 1.2, 'Cable Fly': 1.15,
    'Ab Wheel': 1.25, 'Pull-up': 1.1, 'Dip': 1.1
  };

  const DOSE_TARGET = 6;      // effective sets that count as a full dose
  const CURVE = 1.35;         // >1 holds the fatigue then drops away

  function damageOf(exName) {
    if (DAMAGE_BY_EXERCISE[exName]) return DAMAGE_BY_EXERCISE[exName];
    const m = VOS.exerciseMeta(exName);
    return DAMAGE_BY_PATTERN[m.pattern] || 1;
  }

  /* How long this dose keeps this muscle down. */
  function windowHours(muscleId, effSets, damage) {
    const base = RECOVERY_H[muscleId] || 48;
    const dose = Math.min(1.25, (effSets || 0) / DOSE_TARGET);
    return Math.min(MAX_WINDOW_H, base * (0.5 + 0.62 * dose) * (damage || 1));
  }

  /* Fraction of the hit still outstanding after `hours`. */
  function remaining(hours, windowH) {
    if (windowH <= 0) return 0;
    const t = Math.max(0, hours) / windowH;
    if (t >= 1) return 0;
    return Math.max(0, Math.min(1, 1 - Math.pow(t, CURVE)));
  }

  /* Independent hits saturate rather than add past 1. */
  function combine(a, b) { return 1 - (1 - a) * (1 - b); }

  /* ============================================================
     state(history, session, opts) -> { muscleId: {...} }
     ============================================================ */
  function state(history, session, opts) {
    opts = opts || {};
    const now = opts.now || Date.now();
    const out = {};
    Object.keys(VOS.MUSCLES).forEach(m => {
      out[m] = { fatigue: 0, readyInH: 0, lastHitH: null, effSets7d: 0, volume7d: 0, sources: [] };
    });

    const apply = (exName, sets, whenMs) => {
      const contrib = VOS.muscles(exName);
      const ageH = Math.max(0, (now - whenMs) / 3600000);
      const dmg = damageOf(exName);
      Object.keys(contrib).forEach(mid => {
        const c = contrib[mid];
        if (c < 0.15 || !out[mid]) return;
        let eff = 0;
        sets.forEach(s => { eff += c * VOS.Heat.setQuality(s, exName); });
        if (eff <= 0) return;
        const win = windowHours(mid, eff, dmg);
        const rem = remaining(ageH, win);
        const t = out[mid];
        t.effSets7d += eff;
        sets.forEach(s => { t.volume7d += c * (s.reps || 0) * ((s.weight || 0) > 0 ? s.weight : 30); });
        if (t.lastHitH === null || ageH < t.lastHitH) t.lastHitH = ageH;
        if (rem > 0) {
          t.fatigue = combine(t.fatigue, rem);
          const left = Math.max(0, win - ageH);
          if (left > t.readyInH) t.readyInH = left;
          t.sources.push({ exercise: exName, agoH: Math.round(ageH), effSets: Math.round(eff * 10) / 10, windowH: Math.round(win) });
        }
      });
    };

    (history || []).forEach(h => {
      const when = Date.parse(h.date || 0) || 0;
      if (!when || (now - when) / 3600000 > 24 * 10) return;
      (h.exercises || []).forEach(ex => { if (ex.sets && ex.sets.length) apply(ex.name, ex.sets, when); });
    });

    // work done in the last few minutes is maximally fatiguing
    if (session) {
      (session.exercises || []).forEach(ex => {
        if (ex.sets && ex.sets.length) apply(ex.name, ex.sets, now);
      });
    }

    /* ------------------------------------------------------------
       WHAT THE PERSON SAID BEATS WHAT WE COMPUTED
       Everything above is an estimate from volume and elapsed time.
       A reading from the person is evidence. It replaces the estimate
       for that muscle and decays from the moment they said it, on that
       muscle's own clock — "my quads are wrecked" is true when it is
       said and less true two days later.

       Callers may pass reports explicitly; otherwise we read whatever
       the person has told us. Passing an empty array opts out.
       ------------------------------------------------------------ */
    const reports = opts.reports !== undefined ? opts.reports
      : (opts.memory && opts.memory.soreness)
        || (VOS.Memory && VOS.Memory.data && VOS.Memory.data.soreness)
        || null;

    if (reports && reports.length) {
      const byMuscle = {};
      reports.forEach(r => {
        if (!out[r.muscle]) return;
        if (!byMuscle[r.muscle] || r.t > byMuscle[r.muscle].t) byMuscle[r.muscle] = r;
      });
      Object.keys(byMuscle).forEach(mid => {
        const r = byMuscle[mid];
        const t = out[mid];
        const ageH = Math.max(0, (now - r.t) / 3600000);
        const win = windowHours(mid, Math.max(t.effSets7d, 4), 1);
        if (ageH > win) return;                        // the reading has expired
        const said = Math.max(0, Math.min(1, r.level / 3));
        t.fatigue = said * remaining(ageH, win);
        t.readyInH = said > 0 ? Math.max(0, win - ageH) : 0;
        t.reported = { level: r.level, agoH: Math.round(ageH), expected: !!r.expected, at: r.t };
      });
    }

    Object.keys(out).forEach(m => {
      const t = out[m];
      t.fatigue = Math.round(t.fatigue * 100) / 100;
      t.readyInH = Math.round(t.readyInH * 10) / 10;
      t.effSets7d = Math.round(t.effSets7d * 10) / 10;
      t.volume7d = Math.round(t.volume7d);
      t.status = t.fatigue >= 0.65 ? 'sore' : t.fatigue >= 0.25 ? 'recovering' : 'ready';
      // the map reads fatigue, so the existing painter works unchanged
      t.heat = t.fatigue;
      t.effSets = t.effSets7d;
      t.volume = t.volume7d;
      t.top = Array.from(new Set(t.sources.map(s => s.exercise))).slice(0, 4);
      t.live = !!(session && t.lastHitH !== null && t.lastHitH < 0.05);
    });
    return out;
  }

  const fmtH  = h => h <= 0 ? 'ready' : h < 1 ? 'under an hour' : h < 24 ? `${Math.round(h)}h` : `${Math.round(h / 24 * 10) / 10}d`;
  const fmtAgo = h => h == null ? 'not recently' : h < 0.5 ? 'just now' : h < 24 ? `${Math.round(h)}h ago` : `${Math.round(h / 24 * 10) / 10}d ago`;

  /* Muscles fresh enough to train hard today. */
  function readyNow(st) {
    return Object.keys(st).filter(m => st[m].status === 'ready')
      .map(m => ({ id: m, name: VOS.MUSCLES[m].name, group: VOS.MUSCLES[m].group,
                   staleH: st[m].lastHitH == null ? null : st[m].lastHitH }))
      .sort((a, b) => (b.staleH == null ? 1e9 : b.staleH) - (a.staleH == null ? 1e9 : a.staleH));
  }

  function stillSore(st) {
    return Object.keys(st).filter(m => st[m].fatigue > 0.25)
      .map(m => ({ id: m, name: VOS.MUSCLES[m].name, fatigue: st[m].fatigue, readyInH: st[m].readyInH }))
      .sort((a, b) => b.fatigue - a.fatigue);
  }

  /* Group-level readiness, for "what should I train today". */
  function byGroup(st) {
    const g = {};
    VOS.MUSCLE_GROUPS.forEach(k => { g[k] = { fatigue: 0, readyInH: 0, n: 0 }; });
    Object.keys(st).forEach(m => {
      const grp = VOS.MUSCLES[m].group;
      if (!g[grp]) return;
      g[grp].fatigue = Math.max(g[grp].fatigue, st[m].fatigue);
      g[grp].readyInH = Math.max(g[grp].readyInH, st[m].readyInH);
      g[grp].n++;
    });
    Object.keys(g).forEach(k => { g[k].label = fmtH(g[k].readyInH); });
    return g;
  }

  /* A day-by-day strip for one muscle: what the next few days look like. */
  function forecast(st, muscleId, days) {
    const d = days || 4;
    const f = st[muscleId];
    if (!f) return [];
    const win = f.readyInH || 0;
    const out = [];
    for (let i = 0; i < d; i++) {
      const hrs = i * 24;
      const left = Math.max(0, win - hrs);
      out.push({ day: i, fatigue: win > 0 ? Math.max(0, f.fatigue * (left / win)) : 0, ready: left <= 0 });
    }
    return out;
  }

  VOS.Recovery = {
    state, readyNow, stillSore, byGroup, forecast, windowHours, remaining,
    damageOf, fmtH, fmtAgo, RECOVERY_H, DAMAGE_BY_EXERCISE, DAMAGE_BY_PATTERN, DOSE_TARGET
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
