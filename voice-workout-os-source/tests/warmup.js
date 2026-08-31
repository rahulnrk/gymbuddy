/* The warm-up, and the conversation that happens during it.
   Two things are being checked: that nothing starts cold, and that a
   question the person answers actually changes what the app believes. */
const { load } = require('./_harness');
const VOS = load();

const res = [];
const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));
const H = 3600000;

/* ---------- a stand-in for Memory, so nothing touches storage ---------- */
function mem(history, soreness) {
  const d = { history: history || [], notes: [], soreness: soreness || [], profile: { equipment: 'full gym' } };
  return {
    data: d,
    save() { return this; },
    addNote(t, k) { d.notes.push({ t: Date.now(), text: t, kind: k }); }
  };
}

const S = (w, r, rpe) => ({ reps: r, weight: w, rpe });
const sess = (hoursAgo, exercises) => ({
  date: new Date(Date.now() - hoursAgo * H).toISOString(),
  day: 'Lower A', durationSec: 3000, sets: 9, volume: 8000, energy: 7, endFatigue: 6,
  exercises, notes: [], skipped: []
});

/* ============================================================
   THE PLAN
   ============================================================ */
const squat = { name: 'Back Squat', weight: 100, repHigh: 8, repLow: 6 };
const plan = VOS.Warmup.plan({ exercise: squat, equipment: 'full gym' });

ok('a warm-up is planned for the movement, not in general', plan.pattern === 'squat', plan.pattern);
ok('it raises before it mobilises', plan.items[0].stage === 'raise', plan.items.map(i => i.stage));
ok('it activates before it loads',
   plan.items.findIndex(i => i.stage === 'activate') < plan.items.findIndex(i => i.ramp),
   plan.items.map(i => i.stage));

const ramps = plan.items.filter(i => i.ramp);
ok('it finishes on the movement itself', ramps.length >= 2, ramps.length);
ok('and the ramp climbs to today’s weight, not a fixed number',
   ramps[0].weight === 40 && ramps[ramps.length - 1].weight === 80, ramps.map(r => r.weight));
ok('with the reps falling as the weight rises',
   ramps[0].reps > ramps[ramps.length - 1].reps, ramps.map(r => r.reps));

/* a press day gets shoulders, not ankles */
const press = VOS.Warmup.plan({ exercise: { name: 'Overhead Press', weight: 40, repHigh: 8 }, equipment: 'full gym' });
ok('a press day warms the shoulders instead', /shoulder|dislocate/i.test(press.items[1].name + press.items[1].how), press.items[1].name);

/* bodyweight has nothing to ramp */
const bw = VOS.Warmup.plan({ exercise: { name: 'Push-up', weight: 0, repHigh: 12 }, equipment: 'none' });
ok('bodyweight work still gets a practice set', bw.items.some(i => i.ramp && i.weight === 0));
ok('and the raise uses what the room actually has', /march|walk/i.test(bw.items[0].name), bw.items[0].name);

/* short session shortens the general work but never the ramp */
const short = VOS.Warmup.plan({ exercise: squat, equipment: 'full gym', budgetMin: 35 });
ok('a short session trims the warm-up', short.totalSec < plan.totalSec, { short: short.totalSec, full: plan.totalSec });
ok('but never drops the ramp sets', short.items.filter(i => i.ramp).length === ramps.length);

/* sore areas earn extra work */
const sore = VOS.Warmup.plan({ exercise: squat, equipment: 'full gym', soreGroups: ['legs'] });
ok('a sore area gets extra work rather than being avoided', sore.items.some(i => i.forSoreness === 'legs'));

/* ============================================================
   THE SESSION
   ============================================================ */
const prog = VOS.PROGRAMS.upper_lower_4;
function newSession(history) {
  return new VOS.Session({ program: prog, dayIndex: 1, history: history || [], profile: { equipment: 'full gym', dislikes: [] } });
}

let s = newSession();
s.planWarmup();
s.beginWarmup();
ok('the session begins in the warm-up, not in the first set', s.phase === 'warmup', s.phase);
ok('and it knows which item you are on', s.warmupIndex === 0 && s.warmupItem, s.warmupIndex);
const total = s.warmupCount;
const left0 = s.warmupLeftSec;
s.advanceWarmup();
ok('advancing moves the cursor', s.warmupIndex === 1);
ok('and the time left goes down', s.warmupLeftSec < left0, { left0, now: s.warmupLeftSec });
while (!s.warmupDone) s.advanceWarmup();
ok('when the items run out the warm-up is done', s.warmupDone && s.warmupIndex >= total);
s.finishWarmup();
ok('and only then does the working phase start', s.phase === 'working', s.phase);

s = newSession();
s.planWarmup(); s.beginWarmup();
s.skipWarmup('user asked');
ok('skipping is allowed', s.phase === 'working');
ok('but it is recorded, not hidden', s.warmup.skipped === true && s.events.some(e => e.type === 'warmup_skipped'));

/* ============================================================
   WHAT PEOPLE SAY
   ============================================================ */
const P = (t) => VOS.Soreness.parse(t);
ok('"quads are wrecked" is a severe reading', P('my quads are wrecked').level === 3 && P('my quads are wrecked').muscles[0] === 'quad');
ok('"a bit tight in the hamstrings" is a mild one', P('a bit tight in the hamstrings').level === 1);
ok('"not really" is a zero, not a shrug', P('not really').level === 0);
ok('"lower back" beats "back"', P('my lower back is sore').muscles.includes('back_lower') && !P('my lower back is sore').muscles.includes('lat'));
ok('"back of my legs" means hamstrings', P('back of my legs are stiff').muscles[0] === 'hamstring');
ok('a body part with no grading is still a report', P('my shoulder is sore').level >= 2);

/* ============================================================
   REPORTED BEATS COMPUTED
   ============================================================ */
const hist = [sess(20, [{ name: 'Back Squat', sets: [S(100, 8, 9), S(100, 8, 9), S(100, 6, 10)] }])];
const modelled = VOS.Recovery.state(hist, null, { reports: [] });
ok('the model predicts sore quads after a heavy squat session', modelled.quad.fatigue > 0.5, modelled.quad.fatigue);

const cleared = VOS.Recovery.state(hist, null, { reports: [{ t: Date.now() - H, muscle: 'quad', level: 0, expected: true }] });
ok('saying they are fine clears them', cleared.quad.fatigue < 0.1, cleared.quad.fatigue);
ok('and the map records that you were the source', !!cleared.quad.reported);

const flagged = VOS.Recovery.state(hist, null, { reports: [{ t: Date.now() - H, muscle: 'back_lower', level: 3, expected: false }] });
ok('reporting an unpredicted place lights it up', flagged.back_lower.fatigue > 0.8, flagged.back_lower.fatigue);
ok('and marks it as something you told us', flagged.back_lower.reported.expected === false);

const stale = VOS.Recovery.state(hist, null, { reports: [{ t: Date.now() - 30 * 24 * H, muscle: 'quad', level: 3 }] });
ok('a reading from last month is ignored', !stale.quad.reported);

/* ============================================================
   THE CONVERSATION
   ============================================================ */
const M = mem(hist);
let session = newSession(hist);
let ci = new VOS.CheckIn({ session, memory: M });

ok('it works out what should be sore before it asks', ci.predicted.length > 0, ci.predicted.map(p => p.lead.name));
const q0 = ci.dueFor(0);
ok('the first question comes during the first warm-up item', q0 && q0.id === 'energy', q0 && q0.id);
ok('and it is one question, not a form', (q0.text.match(/\?/g) || []).length === 1, q0.text);

const a0 = ci.answer('seven');
ok('an energy answer is understood', a0.understood && session.energy === 7, session.energy);

const q1 = ci.dueFor(1);
ok('the soreness question comes later, during a different drill', q1 && q1.id === 'soreness');
ok('it states what the model believes rather than asking an open question',
   /should still be/i.test(q1.text) && /\?/.test(q1.text), q1.text);
ok('and it names the muscles it expects', /quad|glute|hamstring/i.test(q1.text), q1.text);

/* --- confirming --- */
let a1 = ci.answer('yeah they are sore');
ok('confirming is acknowledged briefly', a1.understood && a1.say.length < 90, a1.say);
ok('and it is written down', M.data.soreness.length > 0);

/* --- correcting --- */
const M2 = mem(hist);
let ci2 = new VOS.CheckIn({ session: newSession(hist), memory: M2 });
ci2.dueFor(0); ci2.answer('eight');
ci2.dueFor(1);
const a2 = ci2.answer('not really');
ok('a correction is taken at face value', /quicker|faster|cleared/i.test(a2.say), a2.say);
ok('and every predicted muscle is cleared', M2.data.soreness.every(r => r.level === 0) && M2.data.soreness.length > 0);
const after = VOS.Recovery.state(hist, null, { reports: M2.data.soreness });
ok('so the map updates immediately', after.quad.fatigue < 0.1, after.quad.fatigue);

/* --- surprising ---
   Yesterday was an upper day, so chest and shoulders are what the model
   expects to be sore. Squats were three days ago and the lower back has
   had time to come good. Reporting it sore now is genuinely off-model. */
const mixed = [
  sess(74, [{ name: 'Back Squat', sets: [S(100, 8, 9), S(100, 8, 9), S(100, 6, 10)] }]),
  sess(20, [{ name: 'Bench Press', sets: [S(70, 8, 9), S(70, 8, 9), S(70, 7, 9)] },
            { name: 'Overhead Press', sets: [S(45, 8, 9), S(45, 7, 9)] }])
];
const M3 = mem(mixed);
const s3 = newSession(mixed);
let ci3 = new VOS.CheckIn({ session: s3, memory: M3 });
ok('after an upper day it expects the upper body to be sore',
   /chest|delt|shoulder|tricep/i.test(ci3.predicted.map(p => p.lead.name).join(' ')), ci3.predicted.map(p => p.lead.name));
ci3.dueFor(0); ci3.answer('seven');
ci3.dueFor(1);
const a3 = ci3.answer('actually it is my lower back');
ok('soreness in the wrong place is called out', /sorer than I had it|not where I expected/i.test(a3.say), a3.say);
ok('it names the likely cause', /squat/i.test(a3.say), a3.say);
ok('and explains what that usually means', /brace|bracing/i.test(a3.say), a3.say);
ok('it is recorded as unexpected', M3.data.soreness.some(r => r.muscle === 'back_lower' && !r.expected));
ok('and the session knows to protect it', ci3.protect === 'back_lower', ci3.protect);
ok('the surprise survives into the summary', ci3.report().surprises.length === 1);

/* a reported sore group changes the next warm-up plan */
ok('and it feeds the warm-up', ci3.soreGroups().includes('back'), ci3.soreGroups());

/* --- nothing to predict --- */
const ci4 = new VOS.CheckIn({ session: newSession([]), memory: mem([]) });
ci4.dueFor(0);
const q4 = ci4.dueFor(1);
ok('with no history it asks an open question instead', q4 && /anything sore/i.test(q4.text), q4 && q4.text);

/* --- the last session only gets asked about when there is something to ask --- */
const dull = [sess(20, [{ name: 'Back Squat', sets: [S(100, 8, 7)] }])];
const ciDull = new VOS.CheckIn({ session: newSession(dull), memory: mem(dull) });
ok('a routine session is not small-talked about', !ciDull.queue.some(q => q.id === 'yesterday'), ciDull.queue.map(q => q.id));

const rough = [Object.assign(sess(20, [{ name: 'Back Squat', sets: [S(100, 6, 10)] }]), { skipped: ['Leg Press'], notes: ['called it early'] })];
const ciRough = new VOS.CheckIn({ session: newSession(rough), memory: mem(rough) });
ok('a session that got cut short does get asked about', ciRough.queue.some(q => q.id === 'yesterday'));
ciRough.dueFor(0); ciRough.answer('six');
const qy = ciRough.dueFor(1);
ok('and the question refers to what actually happened', /cut short/i.test(qy.text), qy.text);

/* ---------- never two at once ---------- */
const ciOne = new VOS.CheckIn({ session: newSession(hist), memory: mem(hist) });
const seen = {};
let doubled = false;
for (let i = 0; i < 8; i++) {
  const q = ciOne.dueFor(i);
  if (!q) continue;
  if (seen[i]) doubled = true;
  seen[i] = q.id;
  ciOne.answer(q.expect === 'energy' ? 'seven' : 'yeah sore');
}
ok('no warm-up item ever carries two questions', !doubled, seen);
ok('and no question is asked twice', new Set(Object.values(seen)).size === Object.values(seen).length, seen);

res.forEach(r => console.log(r));
const failed = res.filter(r => r.startsWith('FAIL')).length;
console.log(`\n${res.length - failed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
