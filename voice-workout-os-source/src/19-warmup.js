/* ============================================================
   19-warmup.js : nothing starts cold

   A warm-up is not a fixed routine bolted onto the front of the
   session. It is derived from the first thing you are about to do:
   a squat day gets ankles and hips, a press day gets thoracic spine
   and shoulders, and the ramp sets are computed from the weight
   actually on the bar today.

   Four stages, in this order, because each one needs the last:
     raise     — get warm. Blood, temperature, heart rate.
     mobilise  — the joints today's pattern asks for range from.
     activate  — wake the prime movers so the first set is not the
                 first time they fire.
     ramp      — the movement itself, climbing to the working load.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* ------------------------------------------------------------
     RAISE — whatever the room actually has in it
     ------------------------------------------------------------ */
  const RAISE = {
    'full gym':  { name: 'Easy bike',        how: 'Five minutes is not the goal. Three, at a pace you could hold a conversation through.' },
    minimal:     { name: 'Skipping or steps', how: 'Three minutes. Enough to break a light sweat, not enough to tire you.' },
    barbell:     { name: 'Brisk walk',        how: 'Three minutes. Get the temperature up before anything gets loaded.' },
    dumbbells:   { name: 'March on the spot', how: 'Three minutes, arms swinging. You want to feel warm, not worked.' },
    none:        { name: 'March on the spot', how: 'Three minutes, arms swinging, knees up. Warm, not tired.' }
  };

  /* ------------------------------------------------------------
     MOBILISE / ACTIVATE — keyed to the movement pattern, because
     that is what decides which joint needs range and which muscle
     needs waking.
     ------------------------------------------------------------ */
  const BY_PATTERN = {
    squat: {
      mobilise: { name: 'Ankles and hips', how: 'Rock the knee over the toe, ten each side. Then sit in a deep squat and breathe for thirty seconds.',
                  why: 'Squat depth is usually an ankle problem before it is a strength problem.' },
      activate: { name: 'Bodyweight squats', how: 'Ten, slow on the way down, and pause at the bottom.',
                  why: 'Grooves the pattern before it has any weight on it.' }
    },
    lunge: {
      mobilise: { name: 'Hip flexors', how: 'Half-kneeling, squeeze the back glute, thirty seconds each side.',
                  why: 'Split stance asks the trailing hip to open.' },
      activate: { name: 'Bodyweight split squats', how: 'Eight each leg, controlled.', why: 'Balance first, load second.' }
    },
    hinge: {
      mobilise: { name: 'Cat-cow and hamstring sweeps', how: 'Ten cat-cow, then ten sweeps each leg.',
                  why: 'A hinge needs a spine that can find neutral and hamstrings that will lengthen.' },
      activate: { name: 'Glute bridges', how: 'Fifteen, two-second squeeze at the top.',
                  why: 'If the glutes are not firing, the lower back takes the work.' }
    },
    hamstrings: {
      mobilise: { name: 'Hamstring sweeps', how: 'Ten each leg, long and slow.', why: 'Range before tension.' },
      activate: { name: 'Glute bridges', how: 'Fifteen, squeeze at the top.', why: 'Wakes the back of the chain.' }
    },
    'lower back': {
      mobilise: { name: 'Cat-cow', how: 'Fifteen, slow, all the way through the range.', why: 'Find neutral before you load it.' },
      activate: { name: 'Bird dogs', how: 'Eight each side, hold two seconds.', why: 'Bracing, not bending.' }
    },
    'horizontal push': {
      mobilise: { name: 'Chest and shoulders', how: 'Twenty arm circles each way, then thirty seconds in a doorway each side.',
                  why: 'Pressing from tight shoulders is where they start to complain.' },
      activate: { name: 'Push-ups', how: 'Ten, slow, full range.', why: 'Same pattern, no load.' }
    },
    'incline push': {
      mobilise: { name: 'Thoracic and shoulders', how: 'Ten thoracic rotations each side, then arm circles.',
                  why: 'Incline asks more of the shoulder than flat does.' },
      activate: { name: 'Band pull-aparts', how: 'Twenty. If there is no band, twenty reverse flyes with nothing.',
                  why: 'Balances the front-heavy work that is coming.' }
    },
    'vertical push': {
      mobilise: { name: 'Shoulder dislocates', how: 'Ten with a band, a broomstick or a towel. Wide grip, slow.',
                  why: 'Overhead needs range the desk takes away.' },
      activate: { name: 'Band pull-aparts', how: 'Twenty, squeeze the shoulder blades.',
                  why: 'Wakes the upper back that has to hold the press up.' }
    },
    'delt isolation': {
      mobilise: { name: 'Arm circles', how: 'Twenty each direction, gradually bigger.', why: 'Cheap range for a small joint.' },
      activate: { name: 'Empty lateral raises', how: 'Fifteen with nothing in your hands.', why: 'Side delts are small; they need waking.' }
    },
    'horizontal pull': {
      mobilise: { name: 'Thoracic rotations', how: 'Ten each side, on all fours, hand behind the head.',
                  why: 'Rowing from a stiff upper back turns into an arm exercise.' },
      activate: { name: 'Scap retractions', how: 'Fifteen. Hang or hold a band and move only the shoulder blades.',
                  why: 'Pull with the back, not the biceps.' }
    },
    'vertical pull': {
      mobilise: { name: 'Lat stretch', how: 'Hang, or hold a rack and sit back, thirty seconds each side.',
                  why: 'Overhead pulling needs the lats long first.' },
      activate: { name: 'Scapular pull-ups', how: 'Eight. Hang and shrug down without bending the elbows.',
                  why: 'Starts the pull where it should start.' }
    },
    lats: {
      mobilise: { name: 'Lat stretch', how: 'Thirty seconds each side.', why: 'Range before tension.' },
      activate: { name: 'Scap retractions', how: 'Fifteen, blades only.', why: 'Back first, arms second.' }
    },
    'rear delt': {
      mobilise: { name: 'Thoracic rotations', how: 'Ten each side.', why: 'Rear delts need a back that can move.' },
      activate: { name: 'Band pull-aparts', how: 'Twenty, slow.', why: 'The same movement, unloaded.' }
    },
    traps: {
      mobilise: { name: 'Neck and shoulder circles', how: 'Ten each way, gently.', why: 'Traps sit on a joint that seizes.' },
      activate: { name: 'Shrugs with nothing', how: 'Fifteen, hold at the top.', why: 'Feel it before you load it.' }
    },
    biceps: {
      mobilise: { name: 'Elbow circles', how: 'Fifteen each way, then open the forearms.', why: 'Small joints, small warm-up.' },
      activate: { name: 'Empty curls', how: 'Fifteen with nothing.', why: 'Blood into the arm.' }
    },
    triceps: {
      mobilise: { name: 'Overhead triceps stretch', how: 'Thirty seconds each side.', why: 'The long head crosses the shoulder.' },
      activate: { name: 'Bench dips or push-ups', how: 'Ten, easy.', why: 'Wakes the elbow extensors.' }
    },
    core: {
      mobilise: { name: 'Cat-cow', how: 'Fifteen, slow.', why: 'Move the spine before you brace it.' },
      activate: { name: 'Dead bugs', how: 'Ten each side, ribs down.', why: 'Bracing is a skill.' }
    },
    calves: {
      mobilise: { name: 'Ankle rocks', how: 'Fifteen each side.', why: 'The ankle is the joint doing the work.' },
      activate: { name: 'Bodyweight calf raises', how: 'Twenty, full range.', why: 'Blood into a stubborn muscle.' }
    },
    carry: {
      mobilise: { name: 'Shoulder circles and wrist rolls', how: 'Fifteen each.', why: 'Carries load the grip and the shoulder.' },
      activate: { name: 'Dead hangs', how: 'Twenty seconds.', why: 'Wakes the grip.' }
    },
    quads: {
      mobilise: { name: 'Ankles and hips', how: 'Ten knee rocks each side, then a deep squat hold.', why: 'Range at the knee starts at the ankle.' },
      activate: { name: 'Bodyweight squats', how: 'Ten, slow.', why: 'Same pattern, no load.' }
    },
    'chest isolation': {
      mobilise: { name: 'Doorway chest stretch', how: 'Thirty seconds each side.', why: 'Flyes need range.' },
      activate: { name: 'Push-ups', how: 'Ten, slow.', why: 'Warms the same tissue.' }
    },
    unknown: {
      mobilise: { name: 'Full body loosener', how: 'Ten arm circles, ten hip circles, ten cat-cow.', why: 'Everything gets a little.' },
      activate: { name: 'Bodyweight squats and push-ups', how: 'Ten of each.', why: 'Wakes both halves.' }
    }
  };

  /* Extra work when a specific area is reported sore. Sore does not
     mean skip — it usually means it needs longer to come good. */
  const SORE_EXTRA = {
    legs:      { name: 'Extra leg flush', how: 'Twenty bodyweight squats and thirty seconds of easy walking, twice.',
                 why: 'Sore legs warm up slowly. This is the difference between a bad first set and a good one.' },
    back:      { name: 'Extra spine work', how: 'Fifteen more cat-cow, then thirty seconds hanging if you can.',
                 why: 'A sore back wants movement and traction before load.' },
    shoulders: { name: 'Extra shoulder work', how: 'Twenty more band pull-aparts and ten slow dislocates.',
                 why: 'Sore shoulders need range restored before anything goes overhead.' },
    chest:     { name: 'Extra chest work', how: 'Thirty seconds each side in a doorway, then ten slow push-ups.',
                 why: 'Warms tissue that is still tender so the first press is not a shock.' },
    arms:      { name: 'Extra arm flush', how: 'Twenty empty curls and twenty empty extensions.',
                 why: 'Blood into a sore arm helps more than resting it does.' },
    core:      { name: 'Extra core work', how: 'Ten more dead bugs, slow.', why: 'A sore core still has to brace today.' }
  };

  const PLATE = { barbell: 2.5, dumbbell: 2, machine: 5, cable: 2.5, bodyweight: 0, band: 0 };
  function plateFor(name) {
    const m = VOS.exerciseMeta ? VOS.exerciseMeta(name) : null;
    const p = m && PLATE[m.load];
    return p == null ? 2.5 : p;
  }
  const roundTo = (v, step) => step ? Math.max(step, Math.round(v / step) * step) : Math.round(v);

  /* ------------------------------------------------------------
     RAMP SETS
     The last part of a warm-up is the movement itself. Climb to the
     working weight so the first working set is not also the first
     rep of the day.
     ------------------------------------------------------------ */
  function rampSets(exercise) {
    if (!exercise) return [];
    const w = exercise.weight || 0;
    const top = exercise.repHigh || 10;

    if (w === 0) {
      return [{ pct: 0, weight: 0, reps: Math.min(8, top), label: 'an easy set' }];
    }
    const step = plateFor(exercise.name);
    const mk = (pct, reps) => ({ pct, weight: roundTo(w * pct, step), reps, label: Math.round(pct * 100) + '%' });

    // light loads do not need three rungs to climb
    if (w < 30) return [mk(0.5, Math.min(8, top)), mk(0.8, 4)];
    return [mk(0.4, Math.min(8, top)), mk(0.6, 5), mk(0.8, 3)];
  }

  /* ------------------------------------------------------------
     THE PLAN
     ------------------------------------------------------------ */
  function plan(opts) {
    opts = opts || {};
    const exercise = opts.exercise || null;
    const equipment = opts.equipment || 'full gym';
    const soreGroups = opts.soreGroups || [];
    const budgetMin = opts.budgetMin || null;
    const meta = exercise && VOS.exerciseMeta ? VOS.exerciseMeta(exercise.name) : null;
    const pattern = (meta && meta.pattern) || 'unknown';
    const p = BY_PATTERN[pattern] || BY_PATTERN.unknown;

    const items = [];
    const raise = RAISE[equipment] || RAISE['full gym'];
    items.push({ id: 'raise', stage: 'raise', name: raise.name, how: raise.how,
                 why: 'Cold tissue tears. Warm tissue stretches.', seconds: 180 });

    items.push({ id: 'mobilise', stage: 'mobilise', name: p.mobilise.name, how: p.mobilise.how,
                 why: p.mobilise.why, seconds: 90 });

    // one targeted addition for the sorest area, if there is one
    const sore = soreGroups.find(g => SORE_EXTRA[g]);
    if (sore) {
      const x = SORE_EXTRA[sore];
      items.push({ id: 'sore_' + sore, stage: 'mobilise', name: x.name, how: x.how, why: x.why,
                   seconds: 90, forSoreness: sore });
    }

    items.push({ id: 'activate', stage: 'activate', name: p.activate.name, how: p.activate.how,
                 why: p.activate.why, seconds: 75 });

    rampSets(exercise).forEach((r, i) => {
      items.push({
        id: 'ramp' + i, stage: 'ramp',
        name: exercise ? exercise.name : 'Warm-up set',
        how: r.weight ? `${r.weight} kilos for ${r.reps}.` : `${r.reps} with just the bar or your bodyweight.`,
        why: i === 0 ? 'The movement itself now, climbing to today’s weight.' : null,
        seconds: 60, ramp: true, weight: r.weight, reps: r.reps, pct: r.pct
      });
    });

    /* A short session shortens the general work — it never removes the
       ramp sets, because those are the ones that protect the first
       working set. */
    let out = items;
    if (budgetMin && budgetMin <= 40) {
      out = items.filter(i => i.stage !== 'mobilise' || i.forSoreness);
      out[0] = Object.assign({}, out[0], { seconds: 120, how: 'Two minutes, brisk. We are short on time so this is the compressed version.' });
    }

    return {
      items: out,
      totalSec: out.reduce((t, i) => t + i.seconds, 0),
      pattern,
      forExercise: exercise ? exercise.name : null
    };
  }

  VOS.Warmup = { plan, rampSets, BY_PATTERN, RAISE, SORE_EXTRA };
})(typeof globalThis !== 'undefined' ? globalThis : this);
