/* ============================================================
   12-muscles.js : muscle taxonomy and per-exercise contribution
   Regions are deliberately coarser than an anatomy textbook and
   finer than "chest / back / legs" — the granularity a lifter
   actually thinks in.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  // side: which view the region is drawn on. group: for roll-ups.
  const MUSCLES = {
    neck:        { name: 'Neck',            side: 'both',  group: 'upper' },
    trap_upper:  { name: 'Upper traps',     side: 'both',  group: 'back' },
    trap_mid:    { name: 'Mid traps',       side: 'back',  group: 'back' },
    delt_front:  { name: 'Front delts',     side: 'front', group: 'shoulders' },
    delt_side:   { name: 'Side delts',      side: 'both',  group: 'shoulders' },
    delt_rear:   { name: 'Rear delts',      side: 'back',  group: 'shoulders' },
    chest_upper: { name: 'Upper chest',     side: 'front', group: 'chest' },
    chest_mid:   { name: 'Mid chest',       side: 'front', group: 'chest' },
    serratus:    { name: 'Serratus',        side: 'front', group: 'core' },
    biceps:      { name: 'Biceps',          side: 'front', group: 'arms' },
    triceps:     { name: 'Triceps',         side: 'back',  group: 'arms' },
    forearm:     { name: 'Forearms',        side: 'both',  group: 'arms' },
    abs_upper:   { name: 'Upper abs',       side: 'front', group: 'core' },
    abs_lower:   { name: 'Lower abs',       side: 'front', group: 'core' },
    oblique:     { name: 'Obliques',        side: 'front', group: 'core' },
    lat:         { name: 'Lats',            side: 'back',  group: 'back' },
    back_lower:  { name: 'Lower back',      side: 'back',  group: 'back' },
    glute:       { name: 'Glutes',          side: 'back',  group: 'legs' },
    quad:        { name: 'Quads',           side: 'front', group: 'legs' },
    hamstring:   { name: 'Hamstrings',      side: 'back',  group: 'legs' },
    adductor:    { name: 'Adductors',       side: 'front', group: 'legs' },
    abductor:    { name: 'Abductors',       side: 'back',  group: 'legs' },
    calf:        { name: 'Calves',          side: 'back',  group: 'legs' },
    tibialis:    { name: 'Shins',           side: 'front', group: 'legs' }
  };

  const GROUPS = ['chest', 'back', 'shoulders', 'arms', 'core', 'legs'];

  /* Base contribution by movement pattern. 1.0 = prime mover.
     Everything below 0.15 is noise and left out. */
  const PATTERN_MUSCLES = {
    'horizontal push': { chest_mid:1, chest_upper:.5, delt_front:.6, triceps:.6, serratus:.2 },
    'incline push':    { chest_upper:1, chest_mid:.4, delt_front:.75, triceps:.5 },
    'decline push':    { chest_mid:1, chest_upper:.2, delt_front:.35, triceps:.55 },
    'vertical push':   { delt_front:1, delt_side:.7, triceps:.65, trap_upper:.35, abs_upper:.2 },
    'chest isolation': { chest_mid:1, chest_upper:.45, delt_front:.3 },
    'delt isolation':  { delt_side:1, trap_upper:.25, delt_front:.2 },
    'rear delt':       { delt_rear:1, trap_mid:.6, trap_upper:.25 },
    'triceps':         { triceps:1, delt_front:.15 },
    'biceps':          { biceps:1, forearm:.5 },
    'horizontal pull': { lat:.8, trap_mid:1, delt_rear:.6, biceps:.5, forearm:.35, back_lower:.3 },
    'vertical pull':   { lat:1, biceps:.6, trap_mid:.5, delt_rear:.35, forearm:.35 },
    'lats':            { lat:1, triceps:.3, trap_mid:.25 },
    'traps':           { trap_upper:1, forearm:.4, trap_mid:.3 },
    'squat':           { quad:1, glute:.75, adductor:.4, back_lower:.35, hamstring:.3, calf:.2 },
    'lunge':           { quad:.9, glute:1, hamstring:.4, adductor:.35, calf:.25, abductor:.3 },
    'quads':           { quad:1 },
    'hinge':           { hamstring:1, glute:.9, back_lower:.8, trap_upper:.3, forearm:.4, lat:.3 },
    'hamstrings':      { hamstring:1, calf:.25 },
    'lower back':      { back_lower:1, glute:.5, hamstring:.4 },
    'calves':          { calf:1, tibialis:.15 },
    'core':            { abs_upper:1, abs_lower:.85, oblique:.5, serratus:.25 },
    'carry':           { forearm:1, trap_upper:.8, oblique:.6, abs_upper:.4, glute:.3 }
  };

  /* Exercises whose real emphasis differs from their pattern default. */
  const OVERRIDES = {
    'Deadlift':               { hamstring:1, glute:1, back_lower:1, trap_upper:.55, forearm:.65, lat:.5, quad:.45 },
    'Romanian Deadlift':      { hamstring:1, glute:.8, back_lower:.7, forearm:.4 },
    'Good Morning':           { hamstring:.9, back_lower:1, glute:.6 },
    'Back Squat':             { quad:1, glute:.8, adductor:.45, back_lower:.45, hamstring:.35, abs_upper:.25 },
    'Front Squat':            { quad:1, glute:.6, abs_upper:.45, back_lower:.4, adductor:.35 },
    'Hack Squat':             { quad:1, glute:.5, adductor:.3 },
    'Leg Press':              { quad:1, glute:.65, adductor:.35, hamstring:.25 },
    'Goblet Squat':           { quad:.9, glute:.7, adductor:.4, abs_upper:.35 },
    'Bulgarian Split Squat':  { quad:1, glute:1, adductor:.4, abductor:.4, hamstring:.35, calf:.25 },
    'Walking Lunge':          { glute:1, quad:.85, hamstring:.45, adductor:.35, calf:.3 },
    'Step Up':                { glute:1, quad:.85, calf:.3, abductor:.3 },
    'Hip Thrust':             { glute:1, hamstring:.55, quad:.2, abs_lower:.2 },
    'Kettlebell Swing':       { glute:1, hamstring:.85, back_lower:.6, forearm:.5, abs_upper:.35, trap_upper:.3 },
    'Pull-up':                { lat:1, biceps:.6, trap_mid:.5, delt_rear:.3, forearm:.4, abs_upper:.3 },
    'Chin-up':                { lat:.9, biceps:1, trap_mid:.4, forearm:.45, abs_upper:.3 },
    'Lat Pulldown':           { lat:1, biceps:.55, trap_mid:.45, delt_rear:.25 },
    'Straight Arm Pulldown':  { lat:1, triceps:.3, abs_upper:.25 },
    'Barbell Row':            { trap_mid:1, lat:.85, delt_rear:.6, biceps:.5, back_lower:.5, forearm:.4 },
    'Pendlay Row':            { trap_mid:1, lat:.85, delt_rear:.65, back_lower:.55, biceps:.45 },
    'T-Bar Row':              { lat:.9, trap_mid:1, biceps:.5, back_lower:.4 },
    'Seated Cable Row':       { trap_mid:1, lat:.8, delt_rear:.5, biceps:.45 },
    'Chest Supported Row':    { trap_mid:1, delt_rear:.7, lat:.7, biceps:.4 },
    'Single Arm Dumbbell Row':{ lat:1, trap_mid:.7, biceps:.45, oblique:.3, forearm:.35 },
    'Face Pull':              { delt_rear:1, trap_mid:.7, trap_upper:.3 },
    'Bench Press':            { chest_mid:1, chest_upper:.5, delt_front:.6, triceps:.65, serratus:.2 },
    'Close Grip Bench Press': { triceps:1, chest_mid:.7, delt_front:.45 },
    'Dip':                    { chest_mid:.85, triceps:1, delt_front:.5, serratus:.3 },
    'Push-up':                { chest_mid:1, delt_front:.55, triceps:.6, serratus:.35, abs_upper:.3 },
    'Overhead Press':         { delt_front:1, delt_side:.7, triceps:.6, trap_upper:.4, abs_upper:.3, back_lower:.2 },
    'Arnold Press':           { delt_front:1, delt_side:.85, triceps:.5, trap_upper:.3 },
    'Lateral Raise':          { delt_side:1, trap_upper:.25 },
    'Front Raise':            { delt_front:1, chest_upper:.25 },
    'Plank':                  { abs_upper:1, abs_lower:.8, oblique:.55, serratus:.35, delt_front:.25 },
    'Hanging Leg Raise':      { abs_lower:1, abs_upper:.6, oblique:.4, forearm:.4, lat:.25 },
    'Cable Crunch':           { abs_upper:1, abs_lower:.6, oblique:.35 },
    'Ab Wheel':               { abs_upper:1, abs_lower:.9, oblique:.4, lat:.35, serratus:.4 },
    'Farmer Carry':           { forearm:1, trap_upper:.85, oblique:.6, glute:.35, abs_upper:.4, calf:.3 },
    'Back Extension':         { back_lower:1, glute:.7, hamstring:.6 },
    'Nordic Curl':            { hamstring:1, calf:.3, glute:.3 },
    'Leg Curl':               { hamstring:1, calf:.25 },
    'Leg Extension':          { quad:1 },
    'Calf Raise':             { calf:1, tibialis:.15 },
    'Seated Calf Raise':      { calf:1 },
    'Shrug':                  { trap_upper:1, forearm:.5, trap_mid:.35 },
    'Skullcrusher':           { triceps:1 },
    'Overhead Triceps Extension': { triceps:1, delt_side:.15 },
    'Triceps Pushdown':       { triceps:1 },
    'Hammer Curl':            { biceps:.9, forearm:1 },
    'Preacher Curl':          { biceps:1, forearm:.35 },
    'Cable Curl':             { biceps:1, forearm:.4 },
    'Barbell Curl':           { biceps:1, forearm:.5 },
    'Dumbbell Curl':          { biceps:1, forearm:.45 },
    'Cable Fly':              { chest_mid:1, chest_upper:.4, delt_front:.25 },
    'Dumbbell Fly':           { chest_mid:1, chest_upper:.35, delt_front:.3 },
    'Machine Chest Press':    { chest_mid:1, delt_front:.45, triceps:.5 },
    'Incline Bench Press':    { chest_upper:1, chest_mid:.45, delt_front:.75, triceps:.5 },
    'Incline Dumbbell Press': { chest_upper:1, chest_mid:.4, delt_front:.8, triceps:.45 },
    'Decline Bench Press':    { chest_mid:1, triceps:.55, delt_front:.3 },
    'Dumbbell Bench Press':   { chest_mid:1, chest_upper:.45, delt_front:.6, triceps:.55 },
    'Assisted Pull-up':       { lat:1, biceps:.55, trap_mid:.45 },
    'Rear Delt Fly':          { delt_rear:1, trap_mid:.55 }
  };

  /* muscles(name) -> { muscleId: contribution 0..1 } */
  function muscles(name) {
    if (OVERRIDES[name]) return OVERRIDES[name];
    const meta = VOS.exerciseMeta(name);
    const base = PATTERN_MUSCLES[meta.pattern];
    if (base) return base;
    // an imported movement we have never seen — spread it thinly rather than
    // claiming knowledge we do not have
    return { abs_upper: 0.2 };
  }

  function primary(name) {
    const m = muscles(name);
    return Object.keys(m).filter(k => m[k] >= 0.85);
  }

  /* Which exercises in the library hit a given muscle, best first. */
  function exercisesFor(muscleId, minContribution) {
    const min = minContribution != null ? minContribution : 0.5;
    return Object.keys(VOS.EXERCISES)
      .map(n => ({ name: n, c: muscles(n)[muscleId] || 0 }))
      .filter(x => x.c >= min)
      .sort((a, b) => b.c - a.c);
  }

  VOS.MUSCLES = MUSCLES;
  VOS.MUSCLE_GROUPS = GROUPS;
  VOS.PATTERN_MUSCLES = PATTERN_MUSCLES;
  VOS.MUSCLE_OVERRIDES = OVERRIDES;
  VOS.muscles = muscles;
  VOS.musclePrimary = primary;
  VOS.exercisesForMuscle = exercisesFor;
})(typeof globalThis !== 'undefined' ? globalThis : this);
