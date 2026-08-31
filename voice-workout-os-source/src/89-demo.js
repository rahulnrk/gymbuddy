/* ============================================================
   89-demo.js : the demo harness
   Two jobs, both aimed at a room with an audience in it.

   1. seed()  — ten weeks of believable training, so no screen is
                ever empty and the body map already tells a story.
   2. play()  — runs a session forward without a microphone, for
                rooms that are too loud or won't grant permission.

   Everything here is deterministic. The same seed produces the
   same history every time, so the demo never surprises you.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* A tiny fixed PRNG. Variation without unpredictability. */
  function rng(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const H = 3600000;

  /* ------------------------------------------------------------
     WHERE EACH LIFT STARTED, AND HOW FAST IT MOVED
     Ten weeks of a real training block: the big lifts climb, the
     press stalls and gets deloaded, the accessories creep.
     ------------------------------------------------------------ */
  const ARC = {
    'Bench Press':              { start: 52.5, step: 2.5, every: 1.4 },
    'Back Squat':               { start: 70,   step: 5,   every: 1.6 },
    'Deadlift':                 { start: 90,   step: 5,   every: 2.0 },
    'Overhead Press':           { start: 35,   step: 2.5, every: 2.4, stallAt: 6 },
    'Barbell Row':              { start: 45,   step: 2.5, every: 1.8 },
    'Romanian Deadlift':        { start: 60,   step: 5,   every: 2.2 },
    'Incline Dumbbell Press':   { start: 18,   step: 2,   every: 2.5 },
    'Dumbbell Bench Press':     { start: 20,   step: 2,   every: 2.5 },
    'Lat Pulldown':             { start: 40,   step: 2.5, every: 2.0 },
    'Seated Cable Row':         { start: 45,   step: 2.5, every: 2.2 },
    'Leg Press':                { start: 100,  step: 10,  every: 2.0 },
    'Leg Curl':                 { start: 30,   step: 2.5, every: 2.6 },
    'Leg Extension':            { start: 35,   step: 2.5, every: 2.6 },
    'Hip Thrust':               { start: 50,   step: 5,   every: 2.2 },
    'Bulgarian Split Squat':    { start: 12,   step: 2,   every: 3.0 },
    'Calf Raise':               { start: 50,   step: 5,   every: 2.4 },
    'Lateral Raise':            { start: 7,    step: 1,   every: 3.2 },
    'Face Pull':                { start: 17.5, step: 2.5, every: 3.0 },
    'Triceps Pushdown':         { start: 22.5, step: 2.5, every: 2.8 },
    'Barbell Curl':             { start: 20,   step: 2.5, every: 3.0 },
    'Pull-up':                  { start: 0,    step: 0,   every: 99 },
    'Hanging Leg Raise':        { start: 0,    step: 0,   every: 99 }
  };

  function weightFor(name, occurrence, fallback) {
    const a = ARC[name];
    if (!a) return fallback || 0;
    let n = Math.floor(occurrence / a.every);
    // the press stalls, gets deloaded, and climbs back
    if (a.stallAt != null && occurrence >= a.stallAt) {
      n = Math.floor(a.stallAt / a.every) + Math.floor((occurrence - a.stallAt) / (a.every + 1.2)) - 1;
    }
    return Math.max(0, Math.round((a.start + Math.max(0, n) * a.step) * 2) / 2);
  }

  /* Which sessions never happened. A holiday and a couple of write-offs —
     a history with no gaps in it reads as fabricated. */
  const MISSED = new Set([9, 20, 21, 22, 23, 31]);   // indexes into the 40-session grid
  const CUT_SHORT = 34;                              // one session that ended early

  /* Hours between sessions, cycling — four a week, with the weekend off. */
  const GAP = [24, 48, 24, 72];

  function buildHistory(nowMs) {
    const prog = VOS.PROGRAMS.upper_lower_4;
    const rand = rng(20250820);
    const out = [];
    const seen = {};                 // exercise name -> how many times it has come up

    /* Walk forwards through a 40-session grid, then place it in time so
       the most recent session landed 20 hours ago — recent enough that
       the body map opens hot. */
    /* Lay all forty slots out in time first, then drop the ones that
       never happened — so a missed week leaves an actual hole in the
       calendar rather than the sessions closing ranks around it. */
    const slotTime = [];
    let t = nowMs - 20 * H;
    for (let i = 39; i >= 0; i--) {
      slotTime[i] = t;
      t -= GAP[i % GAP.length] * H;
    }

    const kept = [];
    for (let i = 0; i < 40; i++) if (!MISSED.has(i)) kept.push(i);

    const times = kept.map(i => slotTime[i]);

    kept.forEach((gridIndex, k) => {
      const day = prog.days[gridIndex % prog.days.length];
      const when = times[k];
      const cut = gridIndex === CUT_SHORT;
      const energy = cut ? 4 : 6 + Math.floor(rand() * 4);

      const exercises = [];
      let sets = 0, volume = 0;

      day.exercises.forEach((px, xi) => {
        seen[px.name] = (seen[px.name] || 0) + 1;
        const occ = seen[px.name] - 1;
        const w = weightFor(px.name, occ, px.weight);

        // the session that ended early only got through the first three
        if (cut && xi >= 3) {
          exercises.push({ name: px.name, status: 'skipped', sets: [],
                           repLow: px.repLow, repHigh: px.repHigh, targetSets: px.sets });
          return;
        }

        /* Reps: mostly at the top of the range, sometimes not. The
           session right after a jump in weight is usually a grind. */
        const jumped = occ > 0 && w > weightFor(px.name, occ - 1, px.weight);
        const s = [];
        for (let n = 0; n < px.sets; n++) {
          let reps;
          if (jumped)          reps = px.repLow + (n === 0 ? 1 : 0);
          else if (rand() < .68) reps = px.repHigh - (n === px.sets - 1 ? 1 : 0);
          else                 reps = px.repLow + Math.floor(rand() * 2);
          if (cut) reps = Math.max(px.repLow - 1, reps - 1);
          const rpe = Math.min(10, (jumped || cut ? 8.5 : 7.5) + (n * 0.5) + (rand() < .3 ? 0.5 : 0));
          s.push({ reps, weight: w, rpe: Math.round(rpe * 2) / 2, ts: when + n * 200000 });
          sets++; volume += reps * (w || 0);
        }
        exercises.push({ name: px.name, status: 'done', sets: s,
                         repLow: px.repLow, repHigh: px.repHigh, targetSets: px.sets });
      });

      out.push({
        date: new Date(when).toISOString(),
        day: day.name,
        program: prog.name,
        durationSec: cut ? 1980 : 3000 + Math.floor(rand() * 900),
        sets, volume: Math.round(volume),
        energy,
        endFatigue: cut ? 9 : 5 + Math.floor(rand() * 3),
        exercises,
        prs: [],
        notes: cut ? ['Called it after the third exercise. Nothing left in the tank.'] : [],
        skipped: exercises.filter(e => e.status === 'skipped').map(e => e.name)
      });
    });

    return out;
  }

  /* ------------------------------------------------------------
     SEED
     ------------------------------------------------------------ */
  function seed(opts) {
    const M = VOS.Memory;
    M.load();
    const hist = buildHistory(Date.now());
    const p = M.data.profile;

    p.name = (opts && opts.name) || 'Rahul';
    p.sex = 'male';
    p.dob = '1995-03-14';
    p.age = VOS.ageFromDob ? VOS.ageFromDob(p.dob) : 31;
    p.onboarded = true;
    p.level = 'intermediate';
    p.startingPoint = 'regular';
    p.frequencyHabit = 'consistent';
    p.askLoads = false;
    p.goal = 'hypertrophy';
    p.daysPerWeek = 4;
    p.sessionMin = 60;
    p.equipment = 'full gym';
    p.programId = 'upper_lower_4';
    p.nextDayIndex = hist.length % VOS.PROGRAMS.upper_lower_4.days.length;

    M.data.history = hist;
    M.data.notes = [
      { t: Date.now() - 40 * 24 * H, text: 'Left shoulder complains on flat barbell pressing when I go below about 8 reps.', kind: 'body' },
      { t: Date.now() - 12 * 24 * H, text: 'Trains before work, so sessions have a hard stop at an hour.', kind: 'schedule' }
    ];
    M.save();
    return hist.length;
  }

  /* ------------------------------------------------------------
     PLAYBACK
     A real session, driven the way a user would drive it — the same
     entry point a spoken utterance uses, so nothing here is faked.
     ------------------------------------------------------------ */
  const SCRIPT = [
    /* the warm-up, and the check-in that happens during it */
    { wait: 1000, say: 'seven',                            as: 'how is the energy today' },
    { wait: 2600, say: 'done' },
    { wait: 2600, say: 'my quads are still wrecked',       as: 'the soreness question' },
    { wait: 2400, say: 'done' },
    { wait: 2000, say: 'done' },
    { wait: 1900, say: 'done' },
    { wait: 1900, say: 'done' },
    { wait: 1900, say: 'done' },
    { wait: 1900, say: 'done' },
    /* working sets */
    { wait: 2600, say: 'ten' },
    { wait: 2200, say: 'that felt good' },
    { wait: 1800, say: 'ready' },
    { wait: 2600, say: 'ten' },
    { wait: 1800, say: 'ready' },
    { wait: 2600, say: 'nine' },
    { wait: 2000, say: 'that was hard' },
    { wait: 1800, say: 'ready' },
    { wait: 2400, say: 'add two and a half kilos' },
    { wait: 2400, say: 'ten' },
    { wait: 1600, say: 'undo that' },
    { wait: 2200, say: 'nine' },
    { wait: 1800, say: 'ready' },
    { wait: 2600, say: "what's next" },
    { wait: 2600, say: 'my shoulder hurts' },
    { wait: 3000, say: 'end the workout' },
    { wait: 2200, say: 'yes' },
    { wait: 2000, say: 'seven' }
  ];

  let timer = null, step = 0, running = false, speed = 1;

  function stop() {
    running = false;
    if (timer) { clearTimeout(timer); timer = null; }
    if (VOS.Demo.onState) VOS.Demo.onState({ running: false, step, total: SCRIPT.length });
  }

  function play(opts) {
    const App = root.VOSApp;
    if (!App) return;
    stop();
    running = true;
    step = 0;
    speed = (opts && opts.speed) || 1;

    if (!App.session || App.session.phase === 'summary') App.startWorkout();

    const tick = () => {
      if (!running) return;
      if (step >= SCRIPT.length) { stop(); return; }
      const s = SCRIPT[step++];
      if (VOS.Demo.onState) VOS.Demo.onState({ running: true, step, total: SCRIPT.length, said: s.say });
      App.onUtterance(s.say, [s.say], 1, { source: 'ui', demo: true });
      const next = SCRIPT[step];
      timer = setTimeout(tick, ((next && next.wait) || 1800) / speed);
    };
    timer = setTimeout(tick, (SCRIPT[0].wait || 900) / speed);
  }

  VOS.Demo = {
    seed,
    play,
    stop,
    get running() { return running; },
    /* the controller paces its check-in questions off this, so a
       sped-up demo does not answer a question before it is asked */
    get speed() { return speed; },
    script: SCRIPT,
    onState: null,
    /* wipe everything and land on the very first screen a new user sees */
    fresh() { VOS.Memory.reset(); }
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
