/* ============================================================
   VOS — Voice Workout OS
   10-data.js : exercise library + program templates
   Pure data + pure functions. No DOM. Node-testable.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  // kind: 'compound' | 'isolation'
  // load: 'barbell' | 'dumbbell' | 'machine' | 'cable' | 'bodyweight'
  // technique: 'high' = deserves a form checkpoint before load increases
  const EXERCISES = {
    'Bench Press':            { kind:'compound',  load:'barbell',    rest:180, technique:'high',   pattern:'horizontal push' },
    'Incline Dumbbell Press': { kind:'compound',  load:'dumbbell',   rest:150, technique:'normal', pattern:'incline push' },
    'Overhead Press':         { kind:'compound',  load:'barbell',    rest:180, technique:'high',   pattern:'vertical push' },
    'Cable Fly':              { kind:'isolation', load:'cable',      rest:75,  technique:'normal', pattern:'chest isolation' },
    'Lateral Raise':          { kind:'isolation', load:'dumbbell',   rest:60,  technique:'normal', pattern:'delt isolation' },
    'Triceps Pushdown':       { kind:'isolation', load:'cable',      rest:60,  technique:'normal', pattern:'triceps' },
    'Barbell Row':            { kind:'compound',  load:'barbell',    rest:150, technique:'high',   pattern:'horizontal pull' },
    'Lat Pulldown':           { kind:'compound',  load:'machine',    rest:120, technique:'normal', pattern:'vertical pull' },
    'Pull-up':                { kind:'compound',  load:'bodyweight', rest:150, technique:'normal', pattern:'vertical pull' },
    'Seated Cable Row':       { kind:'compound',  load:'cable',      rest:120, technique:'normal', pattern:'horizontal pull' },
    'Face Pull':              { kind:'isolation', load:'cable',      rest:60,  technique:'normal', pattern:'rear delt' },
    'Barbell Curl':           { kind:'isolation', load:'barbell',    rest:75,  technique:'normal', pattern:'biceps' },
    'Dumbbell Curl':          { kind:'isolation', load:'dumbbell',   rest:60,  technique:'normal', pattern:'biceps' },
    'Back Squat':             { kind:'compound',  load:'barbell',    rest:210, technique:'high',   pattern:'squat' },
    'Front Squat':            { kind:'compound',  load:'barbell',    rest:180, technique:'high',   pattern:'squat' },
    'Deadlift':               { kind:'compound',  load:'barbell',    rest:240, technique:'high',   pattern:'hinge' },
    'Romanian Deadlift':      { kind:'compound',  load:'barbell',    rest:180, technique:'high',   pattern:'hinge' },
    'Leg Press':              { kind:'compound',  load:'machine',    rest:150, technique:'normal', pattern:'squat' },
    'Bulgarian Split Squat':  { kind:'compound',  load:'dumbbell',   rest:120, technique:'normal', pattern:'lunge' },
    'Walking Lunge':          { kind:'compound',  load:'dumbbell',   rest:120, technique:'normal', pattern:'lunge' },
    'Leg Extension':          { kind:'isolation', load:'machine',    rest:75,  technique:'normal', pattern:'quads' },
    'Leg Curl':               { kind:'isolation', load:'machine',    rest:75,  technique:'normal', pattern:'hamstrings' },
    'Hip Thrust':             { kind:'compound',  load:'barbell',    rest:150, technique:'normal', pattern:'hinge' },
    'Calf Raise':             { kind:'isolation', load:'machine',    rest:60,  technique:'normal', pattern:'calves' },
    'Plank':                  { kind:'isolation', load:'bodyweight', rest:60,  technique:'normal', pattern:'core' },
    'Hanging Leg Raise':      { kind:'isolation', load:'bodyweight', rest:60,  technique:'normal', pattern:'core' },
    'Push-up':                { kind:'compound',  load:'bodyweight', rest:90,  technique:'normal', pattern:'horizontal push' },
    'Goblet Squat':           { kind:'compound',  load:'dumbbell',   rest:120, technique:'normal', pattern:'squat' },
    'Dumbbell Bench Press':   { kind:'compound',  load:'dumbbell',   rest:150, technique:'normal', pattern:'horizontal push' },
    'Incline Bench Press':    { kind:'compound',  load:'barbell',    rest:180, technique:'high',   pattern:'incline push' },
    'Decline Bench Press':    { kind:'compound',  load:'barbell',    rest:180, technique:'normal', pattern:'decline push' },
    'Close Grip Bench Press': { kind:'compound',  load:'barbell',    rest:150, technique:'normal', pattern:'triceps' },
    'Machine Chest Press':    { kind:'compound',  load:'machine',    rest:120, technique:'normal', pattern:'horizontal push' },
    'Dumbbell Fly':           { kind:'isolation', load:'dumbbell',   rest:75,  technique:'normal', pattern:'chest isolation' },
    'Dip':                    { kind:'compound',  load:'bodyweight', rest:150, technique:'normal', pattern:'vertical push' },
    'Arnold Press':           { kind:'compound',  load:'dumbbell',   rest:150, technique:'normal', pattern:'vertical push' },
    'Front Raise':            { kind:'isolation', load:'dumbbell',   rest:60,  technique:'normal', pattern:'delt isolation' },
    'Rear Delt Fly':          { kind:'isolation', load:'dumbbell',   rest:60,  technique:'normal', pattern:'rear delt' },
    'Skullcrusher':           { kind:'isolation', load:'barbell',    rest:75,  technique:'normal', pattern:'triceps' },
    'Overhead Triceps Extension': { kind:'isolation', load:'dumbbell', rest:60, technique:'normal', pattern:'triceps' },
    'Chin-up':                { kind:'compound',  load:'bodyweight', rest:150, technique:'normal', pattern:'vertical pull' },
    'Assisted Pull-up':       { kind:'compound',  load:'machine',    rest:120, technique:'normal', pattern:'vertical pull' },
    'T-Bar Row':              { kind:'compound',  load:'barbell',    rest:150, technique:'normal', pattern:'horizontal pull' },
    'Pendlay Row':            { kind:'compound',  load:'barbell',    rest:150, technique:'high',   pattern:'horizontal pull' },
    'Single Arm Dumbbell Row':{ kind:'compound',  load:'dumbbell',   rest:120, technique:'normal', pattern:'horizontal pull' },
    'Straight Arm Pulldown':  { kind:'isolation', load:'cable',      rest:60,  technique:'normal', pattern:'lats' },
    'Shrug':                  { kind:'isolation', load:'barbell',    rest:75,  technique:'normal', pattern:'traps' },
    'Hammer Curl':            { kind:'isolation', load:'dumbbell',   rest:60,  technique:'normal', pattern:'biceps' },
    'Preacher Curl':          { kind:'isolation', load:'barbell',    rest:60,  technique:'normal', pattern:'biceps' },
    'Cable Curl':             { kind:'isolation', load:'cable',      rest:60,  technique:'normal', pattern:'biceps' },
    'Hack Squat':             { kind:'compound',  load:'machine',    rest:180, technique:'normal', pattern:'squat' },
    'Step Up':                { kind:'compound',  load:'dumbbell',   rest:120, technique:'normal', pattern:'lunge' },
    'Good Morning':           { kind:'compound',  load:'barbell',    rest:150, technique:'high',   pattern:'hinge' },
    'Back Extension':         { kind:'isolation', load:'bodyweight', rest:75,  technique:'normal', pattern:'lower back' },
    'Nordic Curl':            { kind:'isolation', load:'bodyweight', rest:90,  technique:'normal', pattern:'hamstrings' },
    'Seated Calf Raise':      { kind:'isolation', load:'machine',    rest:60,  technique:'normal', pattern:'calves' },
    'Kettlebell Swing':       { kind:'compound',  load:'dumbbell',   rest:90,  technique:'normal', pattern:'hinge' },
    'Cable Crunch':           { kind:'isolation', load:'cable',      rest:60,  technique:'normal', pattern:'core' },
    'Ab Wheel':               { kind:'isolation', load:'bodyweight', rest:60,  technique:'normal', pattern:'core' },
    'Farmer Carry':           { kind:'compound',  load:'dumbbell',   rest:90,  technique:'normal', pattern:'carry' },
    'Chest Supported Row':    { kind:'compound',  load:'dumbbell',   rest:120, technique:'normal', pattern:'horizontal pull' }
  };

  // Substitution graph — used when a user dislikes / can't do a movement.
  const SUBSTITUTES = {
    'Bulgarian Split Squat': ['Leg Press', 'Walking Lunge', 'Goblet Squat'],
    'Back Squat':            ['Leg Press', 'Front Squat', 'Goblet Squat'],
    'Deadlift':              ['Romanian Deadlift', 'Hip Thrust', 'Leg Curl'],
    'Bench Press':           ['Dumbbell Bench Press', 'Push-up', 'Incline Dumbbell Press'],
    'Overhead Press':        ['Incline Dumbbell Press', 'Lateral Raise'],
    'Pull-up':               ['Lat Pulldown', 'Seated Cable Row'],
    'Barbell Row':           ['Chest Supported Row', 'Seated Cable Row'],
    'Barbell Curl':          ['Dumbbell Curl'],
    'Walking Lunge':         ['Leg Press', 'Bulgarian Split Squat'],
    'Leg Press':             ['Goblet Squat', 'Back Squat'],
    'Cable Fly':             ['Incline Dumbbell Press', 'Push-up'],
    'Romanian Deadlift':     ['Leg Curl', 'Hip Thrust']
  };

  // priority: 1 = main lift (never cut), 2 = secondary, 3 = optional accessory (first to go)
  const e = (name, sets, repLow, repHigh, weight, priority) =>
    ({ name, sets, repLow, repHigh, weight, priority });

  const PROGRAMS = {
    upper_lower_4: {
      id: 'upper_lower_4',
      name: 'Upper / Lower — 4 day',
      goal: 'hypertrophy',
      level: 'intermediate',
      schedule: ['Mon', 'Tue', 'Thu', 'Sat'],
      days: [
        { name: 'Upper A', exercises: [
          e('Bench Press', 3, 8, 10, 60, 1),
          e('Barbell Row', 3, 8, 10, 50, 1),
          e('Incline Dumbbell Press', 3, 10, 12, 20, 2),
          e('Lat Pulldown', 3, 10, 12, 45, 2),
          e('Lateral Raise', 3, 12, 15, 8, 3),
          e('Triceps Pushdown', 3, 12, 15, 25, 3)
        ]},
        { name: 'Lower A', exercises: [
          e('Back Squat', 3, 6, 8, 80, 1),
          e('Romanian Deadlift', 3, 8, 10, 70, 1),
          e('Leg Press', 3, 10, 12, 120, 2),
          e('Leg Curl', 3, 10, 12, 35, 2),
          e('Calf Raise', 3, 12, 15, 60, 3)
        ]},
        { name: 'Upper B', exercises: [
          e('Overhead Press', 3, 6, 8, 40, 1),
          e('Pull-up', 3, 6, 10, 0, 1),
          e('Dumbbell Bench Press', 3, 10, 12, 24, 2),
          e('Seated Cable Row', 3, 10, 12, 50, 2),
          e('Face Pull', 3, 12, 15, 20, 3),
          e('Barbell Curl', 3, 10, 12, 25, 3)
        ]},
        { name: 'Lower B', exercises: [
          e('Deadlift', 3, 4, 6, 100, 1),
          e('Bulgarian Split Squat', 3, 8, 10, 16, 2),
          e('Leg Extension', 3, 12, 15, 40, 2),
          e('Hip Thrust', 3, 10, 12, 60, 2),
          e('Hanging Leg Raise', 3, 8, 12, 0, 3)
        ]}
      ]
    },

    full_body_3: {
      id: 'full_body_3',
      name: 'Full Body — 3 day (beginner)',
      goal: 'general',
      level: 'beginner',
      schedule: ['Mon', 'Wed', 'Fri'],
      days: [
        { name: 'Full Body A', exercises: [
          e('Goblet Squat', 3, 8, 10, 16, 1),
          e('Dumbbell Bench Press', 3, 8, 10, 14, 1),
          e('Seated Cable Row', 3, 10, 12, 35, 1),
          e('Leg Curl', 2, 10, 12, 25, 2),
          e('Plank', 2, 30, 45, 0, 3)
        ]},
        { name: 'Full Body B', exercises: [
          e('Romanian Deadlift', 3, 8, 10, 40, 1),
          e('Lat Pulldown', 3, 10, 12, 35, 1),
          e('Overhead Press', 3, 8, 10, 25, 1),
          e('Leg Press', 2, 10, 12, 80, 2),
          e('Dumbbell Curl', 2, 10, 12, 8, 3)
        ]},
        { name: 'Full Body C', exercises: [
          e('Leg Press', 3, 10, 12, 90, 1),
          e('Push-up', 3, 8, 12, 0, 1),
          e('Chest Supported Row', 3, 10, 12, 14, 1),
          e('Lateral Raise', 2, 12, 15, 6, 3),
          e('Calf Raise', 2, 12, 15, 40, 3)
        ]}
      ]
    },

    ppl_6: {
      id: 'ppl_6',
      name: 'Push / Pull / Legs — 6 day',
      goal: 'hypertrophy',
      level: 'advanced',
      schedule: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
      days: [
        { name: 'Push', exercises: [
          e('Bench Press', 4, 6, 8, 70, 1),
          e('Overhead Press', 3, 8, 10, 42.5, 1),
          e('Incline Dumbbell Press', 3, 10, 12, 24, 2),
          e('Cable Fly', 3, 12, 15, 15, 3),
          e('Lateral Raise', 3, 12, 15, 10, 3),
          e('Triceps Pushdown', 3, 12, 15, 30, 3)
        ]},
        { name: 'Pull', exercises: [
          e('Deadlift', 3, 4, 6, 120, 1),
          e('Pull-up', 4, 6, 10, 0, 1),
          e('Barbell Row', 3, 8, 10, 60, 2),
          e('Seated Cable Row', 3, 10, 12, 55, 2),
          e('Face Pull', 3, 15, 20, 22.5, 3),
          e('Barbell Curl', 3, 10, 12, 30, 3)
        ]},
        { name: 'Legs', exercises: [
          e('Back Squat', 4, 6, 8, 100, 1),
          e('Romanian Deadlift', 3, 8, 10, 80, 1),
          e('Leg Press', 3, 12, 15, 140, 2),
          e('Leg Curl', 3, 12, 15, 40, 2),
          e('Calf Raise', 4, 12, 15, 70, 3)
        ]}
      ]
    }
  };


  /* Written the way people actually write them. Everything here is
     normalised the same way the user's text is, so casing, plurals and
     punctuation do not matter. */
  const ALIASES = {
    'bench': 'Bench Press', 'flat bench': 'Bench Press', 'barbell bench': 'Bench Press',
    'bb bench': 'Bench Press', 'chest press': 'Machine Chest Press',
    'incline bench': 'Incline Bench Press', 'inclined bench': 'Incline Bench Press',
    'incline': 'Incline Bench Press', 'incline press': 'Incline Bench Press',
    'incline db bench': 'Incline Dumbbell Press', 'incline dumbbell bench': 'Incline Dumbbell Press',
    'decline bench': 'Decline Bench Press', 'declined bench': 'Decline Bench Press', 'decline': 'Decline Bench Press',
    'db bench': 'Dumbbell Bench Press', 'dumbbell bench': 'Dumbbell Bench Press',
    'cable fly': 'Cable Fly', 'cable flye': 'Cable Fly', 'pec deck': 'Cable Fly', 'pec fly': 'Cable Fly',
    'db fly': 'Dumbbell Fly', 'dumbbell flye': 'Dumbbell Fly',
    'ohp': 'Overhead Press', 'military press': 'Overhead Press', 'shoulder press': 'Overhead Press',
    'standing press': 'Overhead Press', 'press': 'Overhead Press',
    'lat raise': 'Lateral Raise', 'side raise': 'Lateral Raise', 'side lateral': 'Lateral Raise',
    'rear delt': 'Rear Delt Fly', 'reverse fly': 'Rear Delt Fly', 'reverse pec deck': 'Rear Delt Fly',
    'tricep pushdown': 'Triceps Pushdown', 'pushdown': 'Triceps Pushdown', 'rope pushdown': 'Triceps Pushdown',
    'tricep extension': 'Overhead Triceps Extension', 'skull crusher': 'Skullcrusher', 'lying tricep extension': 'Skullcrusher',
    'cgbp': 'Close Grip Bench Press', 'close grip bench': 'Close Grip Bench Press',
    'squat': 'Back Squat', 'squats': 'Back Squat', 'bb squat': 'Back Squat', 'barbell squat': 'Back Squat',
    'front squats': 'Front Squat', 'hacks': 'Hack Squat',
    'deads': 'Deadlift', 'dl': 'Deadlift', 'conventional deadlift': 'Deadlift',
    'rdl': 'Romanian Deadlift', 'rdls': 'Romanian Deadlift', 'stiff leg deadlift': 'Romanian Deadlift',
    'sldl': 'Romanian Deadlift', 'romanian': 'Romanian Deadlift',
    'bss': 'Bulgarian Split Squat', 'bulgarians': 'Bulgarian Split Squat', 'split squat': 'Bulgarian Split Squat',
    'lunges': 'Walking Lunge', 'lunge': 'Walking Lunge',
    'leg ext': 'Leg Extension', 'quad extension': 'Leg Extension',
    'hamstring curl': 'Leg Curl', 'lying leg curl': 'Leg Curl', 'seated leg curl': 'Leg Curl',
    'calves': 'Calf Raise', 'calf': 'Calf Raise', 'standing calf raise': 'Calf Raise',
    'thrusts': 'Hip Thrust', 'glute bridge': 'Hip Thrust', 'hip thrusts': 'Hip Thrust',
    'pullup': 'Pull-up', 'pull up': 'Pull-up', 'pull ups': 'Pull-up', 'pullups': 'Pull-up',
    'chinup': 'Chin-up', 'chin up': 'Chin-up', 'chins': 'Chin-up',
    'lat pull down': 'Lat Pulldown', 'pulldown': 'Lat Pulldown', 'lats': 'Lat Pulldown',
    'bb row': 'Barbell Row', 'bent over row': 'Barbell Row', 'bent row': 'Barbell Row', 'rows': 'Barbell Row',
    'row': 'Seated Cable Row', 'cable row': 'Seated Cable Row', 'seated row': 'Seated Cable Row',
    'db row': 'Single Arm Dumbbell Row', 'one arm row': 'Single Arm Dumbbell Row',
    'facepull': 'Face Pull', 'face pulls': 'Face Pull',
    'curl': 'Barbell Curl', 'curls': 'Barbell Curl', 'bicep curl': 'Barbell Curl', 'ez curl': 'Barbell Curl',
    'db curl': 'Dumbbell Curl', 'dumbbell curls': 'Dumbbell Curl',
    'pushup': 'Push-up', 'push up': 'Push-up', 'press up': 'Push-up',
    'dips': 'Dip', 'tricep dip': 'Dip', 'chest dip': 'Dip',
    'planks': 'Plank', 'leg raise': 'Hanging Leg Raise', 'hanging leg raises': 'Hanging Leg Raise',
    'crunches': 'Cable Crunch', 'ab rollout': 'Ab Wheel',
    'kb swing': 'Kettlebell Swing', 'swings': 'Kettlebell Swing',
    'traps': 'Shrug', 'shrugs': 'Shrug',
    'hyperextension': 'Back Extension', 'back ext': 'Back Extension',
    'goblet': 'Goblet Squat', 'farmers walk': 'Farmer Carry', 'farmers carry': 'Farmer Carry'
  };

  VOS.ALIASES = ALIASES;
  VOS.EXERCISES = EXERCISES;
  VOS.SUBSTITUTES = SUBSTITUTES;
  VOS.PROGRAMS = PROGRAMS;

  VOS.exerciseMeta = function (name) {
    return EXERCISES[name] || { kind:'compound', load:'barbell', rest:120, technique:'normal', pattern:'unknown' };
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
