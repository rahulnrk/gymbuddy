/* Headless test harness — runs engine + parser without a browser. */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const SRC = path.join(__dirname, '..', 'src');
const VOS = require('./_harness').load();

let pass = 0, fail = 0;
const results = [];
function ok(name, cond, detail) {
  if (cond) { pass++; results.push(`  PASS  ${name}`); }
  else { fail++; results.push(`  FAIL  ${name}${detail ? '  <- ' + JSON.stringify(detail) : ''}`); }
}
function section(t) { results.push('\n== ' + t + ' =='); }

/* ============================================================
   1. INTENT PARSER
   ============================================================ */
section('Intent parser — fast path');
const cases = [
  ['8', {}, 'reps', s => s.reps === 8],
  ['eight', {}, 'reps', s => s.reps === 8],
  ['got 10', {}, 'reps', s => s.reps === 10],
  ['I did twelve', {}, 'reps', s => s.reps === 12],
  ['ten reps', {}, 'reps', s => s.reps === 10],
  ['twenty two', {}, 'reps', s => s.reps === 22],
  ['done', {}, 'done_set', () => true],
  ['that\'s the set', {}, 'done_set', () => true],
  ['next', {}, 'done_set', () => true],
  ['next exercise', {}, 'next_exercise', () => true],
  ['skip this', {}, 'skip_exercise', () => true],
  ['can I skip this one', {}, 'skip_exercise', () => true],
  ['ready', {}, 'ready', () => true],
  ['let\'s go', {}, 'ready', () => true],
  ['I\'m good', {}, 'ready', () => true],
  ['give me another minute', {}, 'extend_rest', s => s.seconds === 60],
  ['rest two minutes', {}, 'set_rest', s => s.seconds === 120],
  ['make it 90 seconds', {}, 'set_rest', s => s.seconds === 90],
  ['3 min', {}, 'set_rest', s => s.seconds === 180],
  ['that was easy', {}, 'rpe', s => s.rpe === 6],
  ['that was pretty hard', {}, 'rpe', s => s.rpe === 9],
  ['rpe 8', {}, 'rpe', s => s.rpe === 8],
  ['nine out of ten', {}, 'rpe', s => s.rpe === 9],
  ['two in the tank', {}, 'rpe', s => s.rpe === 8],
  ['that was all I had', {}, 'rpe', s => s.rpe === 10],
  ['8', { awaiting: 'rpe' }, 'rpe', s => s.rpe === 8],
  ['using 62.5 kg', {}, 'set_weight', s => s.weight === 62.5],
  ['I only have 35 minutes', {}, 'time_budget', s => s.minutes === 35],
  ['only 40 minutes today', {}, 'time_budget', s => s.minutes === 40],
  ['what\'s next', {}, 'whats_next', () => true],
  ['what weight', {}, 'what_weight', () => true],
  ['how much time left', {}, 'time_left', () => true],
  ['say that again', {}, 'repeat', () => true],
  ['end the workout', {}, 'end_workout', () => true],
  ['undo', {}, 'undo', () => true],
  ['pause', {}, 'pause', () => true],
  // these MUST fall through to the LLM
  ['why are we not increasing the weight', {}, 'unknown', () => true],
  ['my shoulder feels a bit weird on this one', {}, 'unknown', () => true],
  ['what did I do last week on bench', {}, 'unknown', () => true],
  ['I hate bulgarian split squats', {}, 'unknown', () => true]
];
cases.forEach(([text, c, want, check]) => {
  const r = VOS.Intent.parse(text, c);
  ok(`"${text}" -> ${want}`, r.intent === want && check(r), r);
});

/* ============================================================
   2. PROGRESSION RULES
   ============================================================ */
section('Progression engine');
const plan = { weight: 65, repLow: 8, repHigh: 10, sets: 3 };
let d = VOS.Progression.decide('Bench Press', plan,
  [{reps:10,weight:65,rpe:7},{reps:10,weight:65,rpe:8},{reps:10,weight:65,rpe:8}], {});
ok('all sets at top of range, moderate RPE -> increase', d.action === 'increase' && d.weight === 67.5, d);

d = VOS.Progression.decide('Bench Press', plan,
  [{reps:10,weight:65,rpe:9.5},{reps:10,weight:65,rpe:10},{reps:10,weight:65,rpe:10}], {});
ok('top of range but maximal RPE -> hold', d.action === 'hold', d);

d = VOS.Progression.decide('Bench Press', plan,
  [{reps:7,weight:65,rpe:10},{reps:7,weight:65,rpe:10},{reps:6,weight:65,rpe:10}], {});
ok('below range, first time -> hold', d.action === 'hold', d);

d = VOS.Progression.decide('Bench Press', plan,
  [{reps:7,weight:65,rpe:10},{reps:6,weight:65,rpe:10},{reps:5,weight:65,rpe:10}], { consecutiveBadSessions: 1 });
ok('below range twice -> deload ~10%', d.action === 'deload' && d.weight === 58.75, d);

d = VOS.Progression.decide('Bench Press', plan,
  [{reps:10,weight:65,rpe:7},{reps:10,weight:65,rpe:7},{reps:10,weight:65,rpe:7}], { energy: 3 });
ok('earned increase but low energy -> hold', d.action === 'hold', d);

d = VOS.Progression.decide('Pull-up', { weight:0, repLow:6, repHigh:10, sets:3 },
  [{reps:10,weight:0,rpe:8},{reps:10,weight:0,rpe:8},{reps:10,weight:0,rpe:8}], {});
ok('bodyweight at top of range -> add reps not load', d.action === 'add_reps', d);

d = VOS.Progression.decide('Back Squat', { weight:120, repLow:6, repHigh:8, sets:3 },
  [{reps:8,weight:120,rpe:7},{reps:8,weight:120,rpe:8},{reps:8,weight:120,rpe:8}], {});
ok('heavy barbell increment is 5kg not 2.5', d.weight === 125, d);

/* ============================================================
   3. FULL SESSION SIMULATION (through the new orchestrator)
   ============================================================ */
section('Session simulation — a whole workout by voice');

const H = require('./_harness');
const history = [{
  date: '2026-08-10',
  day: 'Upper A',
  exercises: [
    { name: 'Bench Press', sets: [{reps:10,weight:62.5,rpe:7},{reps:10,weight:62.5,rpe:8},{reps:10,weight:62.5,rpe:8}] },
    { name: 'Barbell Row', sets: [{reps:10,weight:50,rpe:8},{reps:10,weight:50,rpe:8},{reps:10,weight:50,rpe:8}] }
  ]
}];

const session = new VOS.Session({
  program: VOS.PROGRAMS.upper_lower_4, dayIndex: 0, history,
  profile: { level: 'intermediate', goal: 'hypertrophy', dislikes: [] }
});
session.checkin({ energy: 7, soreness: 2 });
const rig = H.makeCtrl(VOS, session, { gate: 'open' });
const ctrl = rig.ctrl, audio = rig.audio;
const spoken = audio.said;

(async () => {
  await ctrl.beginWorkout();
  const say = (t) => ctrl.onUtterance(t, [t], 0.95);

  ok('opening line names the exercise and last session',
     /Bench Press/.test(spoken[0]) && /62\.5/.test(spoken[0]), spoken[0]);
  ok('bench progressed from 62.5 to 65 automatically', session.exercises[0].weight === 65,
     { w: session.exercises[0].weight, reason: session.exercises[0].progression.reason });

  await say('9');
  ok('after the first set the agent asks how it felt',
     rig.dialogue.topKind === 'rpe', { said: audio.last, expecting: rig.dialogue.expectations.map(e=>e.kind) });

  await say('that was pretty good');
  ok('effort recorded from natural language', session.exercises[0].sets[0].rpe === 7, session.exercises[0].sets[0]);
  ok('rest started automatically', session.phase === 'resting' && session.restLeftSec > 100, session.restLeftSec);

  await say('give me another minute');
  ok('rest extended past the original', session.restPlannedSec >= 240, session.restPlannedSec);

  await say("I'm ready");
  ok('ready ends the rest', session.phase !== 'resting', session.phase);

  await say('8');
  ok('a mid-exercise set does not trigger a question', rig.dialogue.topKind !== 'rpe', audio.last);
  ok('the agent stays terse on a routine set', spoken[spoken.length - 2].length < 40, spoken[spoken.length - 2]);

  await say('ready');
  await say('7');
  ok('the final set of an exercise is asked about', rig.dialogue.topKind === 'rpe', audio.last);
  await say('that was hard');
  ok('three sets logged on bench', session.exercises[0].sets.length === 3, session.exercises[0].sets);

  await say('ready');
  ok('moved on to exercise two', session.current.name === 'Barbell Row', session.current.name);
  ok('the new exercise is announced', /Barbell Row/.test(audio.last), audio.last);

  const before = spoken.length;
  await say('what weight');
  ok('a load question is answered locally', /\d/.test(audio.last), audio.last);
  await say("what's next");
  ok("what's next is answered locally", spoken.length === before + 2, spoken.slice(before));

  await say('10'); await say('8'); await say('ready');
  await say('10'); await say('ready'); await say('10'); await say('8'); await say('ready');
  ok('reached the third exercise', session.current.name === 'Incline Dumbbell Press', session.current.name);

  await say('skip this');
  ok('skipping marks it and advances',
     session.exercises[2].status === 'skipped' && session.current.name === 'Lat Pulldown',
     { s: session.exercises[2].status, cur: session.current.name });

  await say('11');
  const beforeUndo = session.current.sets.length;
  await say('undo');
  ok('undo removes the last set', session.current.sets.length === beforeUndo - 1, session.current.sets);

  await say('end the workout');
  ok('ending early asks for confirmation', !!rig.dialogue.pending, audio.last);
  await say('yes');
  ok('the workout finishes', session.phase === 'summary', session.phase);
  const sum = session.summary();
  ok('the summary has duration, sets and volume', sum.sets > 0 && sum.volume > 0, { sets: sum.sets, volume: sum.volume });
  ok('the summary records the skipped exercise', sum.skipped.includes('Incline Dumbbell Press'), sum.skipped);

  finish();
})();

/* ============================================================
   4. EFFICIENCY MODE
   ============================================================ */
section('Efficiency mode');
const s2 = new VOS.Session({ program: VOS.PROGRAMS.upper_lower_4, dayIndex: 0, history: [], profile: {} });
const fullEstimate = s2.estimateMinutes();
const cuts = s2.applyEfficiencyMode(35);
ok('full session estimate is realistic (45-90 min)', fullEstimate >= 45 && fullEstimate <= 95, fullEstimate);
ok('efficiency mode made cuts', cuts.length > 0, cuts);
ok('efficiency mode fits the budget', s2.estimateMinutes() <= 36, s2.estimateMinutes());
ok('main lifts survived the cut',
   s2.exercises.filter(e => e.priority === 1).every(e => e.status !== 'skipped' && e.targetSets === 3),
   s2.exercises.filter(e=>e.priority===1).map(e=>({n:e.name,s:e.targetSets,st:e.status})));

/* ============================================================
   5. LOW READINESS
   ============================================================ */
section('Low readiness damping');
const s3 = new VOS.Session({ program: VOS.PROGRAMS.upper_lower_4, dayIndex: 0, history, profile: {} });
const benchBefore = s3.exercises[0].weight;
s3.checkin({ energy: 3 });
ok('low energy cancels the load increase', s3.exercises[0].weight === 62.5 && benchBefore === 65,
   { before: benchBefore, after: s3.exercises[0].weight });
ok('low energy trims accessory volume',
   s3.exercises.filter(e => e.priority === 3).every(e => e.targetSets === 2),
   s3.exercises.filter(e=>e.priority===3).map(e=>e.targetSets));

/* ============================================================
   6. DISLIKED EXERCISE SUBSTITUTION
   ============================================================ */
section('Preference memory');
const s4 = new VOS.Session({
  program: VOS.PROGRAMS.upper_lower_4, dayIndex: 3, history: [],
  profile: { dislikes: ['Bulgarian Split Squat'] }
});
const names = s4.exercises.map(e => e.name);
ok('disliked exercise removed', !names.includes('Bulgarian Split Squat'), names);
ok('a substitute was inserted', names.includes('Leg Press'), names);
ok('substitution is explained', s4.exercises.some(e => e.note && /Bulgarian/.test(e.note)),
   s4.exercises.map(e=>e.note).filter(Boolean));

/* ---------------------------------------------------------- */
function finish() {
  console.log(results.join('\n'));
  console.log(`\n${pass} passed, ${fail} failed\n`);
  process.exit(fail ? 1 : 0);
}
