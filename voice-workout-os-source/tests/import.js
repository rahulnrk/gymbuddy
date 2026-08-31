/* The import must accept what people actually type, never reject a line,
   and never make the user learn a format. */
const H = require('./_harness');
const V = H.load(), I = V.Import;

let pass = 0, fail = 0; const out = [];
const ok = (n, c, d) => { if (c) { pass++; out.push('  PASS  ' + n); } else { fail++; out.push('  FAIL  ' + n + '  <- ' + JSON.stringify(d)); } };
const sec = t => out.push('\n== ' + t + ' ==');

sec('Name matching — how people actually write exercises');
[
  ['Bench', 'Bench Press'], ['bench press', 'Bench Press'], ['BB Bench', 'Bench Press'],
  ['Inclined bench', 'Incline Bench Press'], ['incline bench press', 'Incline Bench Press'],
  ['Decline bench', 'Decline Bench Press'], ['Cable flyes', 'Cable Fly'], ['cable flies', 'Cable Fly'],
  ['DB bench', 'Dumbbell Bench Press'], ['OHP', 'Overhead Press'], ['military press', 'Overhead Press'],
  ['Squats', 'Back Squat'], ['squat', 'Back Squat'], ['RDL', 'Romanian Deadlift'], ['rdls', 'Romanian Deadlift'],
  ['Deads', 'Deadlift'], ['pullups', 'Pull-up'], ['Pull Ups', 'Pull-up'], ['chin ups', 'Chin-up'],
  ['lat pulldown', 'Lat Pulldown'], ['Lat Pull Down', 'Lat Pulldown'], ['bent over row', 'Barbell Row'],
  ['tricep pushdown', 'Triceps Pushdown'], ['Lateral raises', 'Lateral Raise'], ['side raise', 'Lateral Raise'],
  ['bicep curls', 'Barbell Curl'], ['hammer curls', 'Hammer Curl'], ['leg press', 'Leg Press'],
  ['hamstring curl', 'Leg Curl'], ['calves', 'Calf Raise'], ['skull crushers', 'Skullcrusher'],
  ['bulgarians', 'Bulgarian Split Squat'], ['hip thrusts', 'Hip Thrust'], ['face pulls', 'Face Pull'],
  ['seated cable row', 'Seated Cable Row'], ['dips', 'Dip'], ['pec deck', 'Cable Fly']
].forEach(([q, want]) => {
  const r = I.matchExercise(q);
  ok(`"${q}" -> ${want}`, r && r.name === want, r);
});

sec('Unknown movements are kept, never rejected');
const weird = I.matchExercise('Jefferson Curl');
ok('unknown movement survives as a custom exercise', weird && weird.confidence === 'custom' && weird.name === 'Jefferson Curl', weird);

sec('Line shapes — sets, reps and load in any notation');
[
  ['Bench 3 set',            { sets:3, repLow:null, weight:null }],
  ['Bench 3 sets',           { sets:3 }],
  ['Bench 3x8',              { sets:3, repLow:8, repHigh:8 }],
  ['Bench 3 x 8-10',         { sets:3, repLow:8, repHigh:10 }],
  ['Bench 4x6 100kg',        { sets:4, repLow:6, weight:100 }],
  ['Bench 3 sets of 8',      { sets:3, repLow:8 }],
  ['Bench 3 sets of 8-12',   { sets:3, repLow:8, repHigh:12 }],
  ['Bench press @ 60',       { weight:60 }],
  ['Squat 5x5 @100kg',       { sets:5, repLow:5, weight:100 }],
  ['- Leg press 3x12',       { sets:3, repLow:12 }],
  ['1. Deadlift 3x5',        { sets:3, repLow:5 }],
  ['Pull ups 3 sets bodyweight', { sets:3, weight:0 }],
  ['Bench | 3 | 8-10 | 60',  { sets:3, repLow:8, repHigh:10 }]
].forEach(([line, want]) => {
  const r = I.parseLine(line);
  const good = Object.keys(want).every(k => (want[k] === null ? r[k] == null : r[k] === want[k]));
  ok(`"${line}"`, good, r);
});

sec("The exact text that failed before");
const p1 = I.parseProgram(`#Chest
Bench 3 set
Inclined bench 3 set
Decline bench 3 set
Cable flyes 3 set`);
ok('all four lines imported', p1.stats.exercises === 4, p1.stats);
ok('one day named Chest', p1.days.length === 1 && p1.days[0].name === 'Chest', p1.days.map(d=>d.name));
ok('names resolved to the library', p1.days[0].exercises.map(e=>e.name).join(', ') ===
   'Bench Press, Incline Bench Press, Decline Bench Press, Cable Fly', p1.days[0].exercises.map(e=>e.name));
ok('missing sets/reps filled with defaults', p1.days[0].exercises.every(e => e.sets === 3 && e.repLow > 0), p1.days[0].exercises);
ok('missing weights flagged to ask in the gym', p1.stats.needWeight === 4, p1.stats);

sec('Headings without hashes');
const p2 = I.parseProgram(`Push
Bench 3x8
OHP 3x10

Pull:
Deadlift 3x5
Rows 3x10

Day 3 - Legs
Squat 4x6`);
ok('bare, colon and prefixed headings all work',
   p2.days.map(d => d.name).join('|') === 'Push|Pull|Day 3 - Legs', p2.days.map(d=>d.name));
ok('five exercises across three days', p2.stats.exercises === 5 && p2.stats.days === 3, p2.stats);

sec('Messy real-world paste');
const p3 = I.parseProgram(`  My push day!!

  1) Bench press — 4 sets x 6 reps @ 80 kg
  2) inclined DB press 3x10
  3) cable flyes  3 sets
  * lateral raises 4 x 15
  tricep rope pushdown 3x12 25kg
  Jefferson curl 2x8`);
ok('every line survived a messy paste', p3.stats.exercises === 6, p3.stats);
ok('lbs converted, kg preserved', p3.days[0].exercises[0].weight === 80, p3.days[0].exercises[0]);
ok('unknown movement kept rather than dropped',
   p3.days[0].exercises.some(e => e.name === 'Jefferson Curl'), p3.days[0].exercises.map(e=>e.name));
const lb = I.parseProgram('Bench 3x8 135lb');
ok('pounds converted to kg', Math.abs(lb.days[0].exercises[0].weight - 61) < 1, lb.days[0].exercises[0]);

sec('An imported programme actually runs');
const prog = I.parseProgram(`Chest
Bench 3 set
Cable flyes 3x12 20kg`);
const sess = new V.Session({ program: prog, dayIndex: 0, history: [], profile: {} });
sess.checkin({ energy: 7 });
const rig = H.makeCtrl(V, sess, { gate: 'open' });
(async () => {
  await rig.ctrl.beginWorkout();
  const said = rig.audio.said;
  ok('agent asks for the missing load', /what are you loading/i.test(said[said.length-1]), said[said.length-1]);
  ok('no phantom weight announced', !/bodyweight|0 kilos/i.test(said[said.length-1]), said[said.length-1]);
  ok('it is waiting for a weight', rig.dialogue.expecting('weight'), rig.dialogue.expectations);
  await rig.ctrl.onUtterance('60', ['60'], 0.95);
  ok('spoken weight is accepted', sess.current.weight === 60 && !sess.current.needsWeight, sess.current.weight);
  ok('and it hands straight back', /go when you'?re ready/i.test(said[said.length-1]), said[said.length-1]);
  await rig.ctrl.onUtterance('8', ['8'], 0.95);
  ok('the set logs against that weight', sess.current.sets[0] && sess.current.sets[0].weight === 60, sess.current.sets);
  const known = sess.exercises[1];
  ok('an exercise with a stated load is not asked about', known.needsWeight === false && known.weight === 20, known);

  console.log(out.join('\n'));
  console.log(`\n${pass} passed, ${fail} failed\n`);
  process.exit(fail ? 1 : 0);
})();
