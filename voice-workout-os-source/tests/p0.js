/* ============================================================
   P0 behaviours: contextual resolution, corrections, multi-intent,
   confidence tiers, races, idempotency, safety, cache staleness.
   ============================================================ */
const { load, makeSession, makeCtrl } = require('./_harness');
const VOS = load();

let pass = 0, fail = 0; const out = [];
const ok = (n, c, d) => { if (c) { pass++; out.push('  PASS  ' + n); } else { fail++; out.push('  FAIL  ' + n + '  <- ' + JSON.stringify(d)); } };
const sec = t => out.push('\n== ' + t + ' ==');
const hist = [{ date:'2026-08-10', day:'Upper A', exercises:[
  { name:'Bench Press', sets:[{reps:10,weight:62.5,rpe:7},{reps:10,weight:62.5,rpe:8},{reps:10,weight:62.5,rpe:8}] }
]}];

/* ============================================================ */
sec('Numbers in context — the same word, different meanings');
{
  const s = makeSession(VOS, { history: hist });
  const { ctrl, dialogue } = makeCtrl(VOS, s);
  const anchors = VOS.Resolver.anchorsFor(s);

  dialogue.expect('reps');
  let r = VOS.Resolver.resolve({ text: 'eight', dialogue, session: s });
  ok('"eight" while expecting reps -> reps', r.plan[0].intent === 'reps' && r.plan[0].args.reps === 8, r.plan[0]);

  dialogue.clear(); dialogue.expect('rpe');
  r = VOS.Resolver.resolve({ text: 'eight', dialogue, session: s });
  ok('"eight" while expecting RPE -> rpe', r.plan[0].intent === 'rpe' && r.plan[0].args.rpe === 8, r.plan[0]);

  dialogue.clear(); dialogue.expect('weight');
  r = VOS.Resolver.resolve({ text: 'sixty five', dialogue, session: s });
  ok('"sixty five" while expecting a load -> weight', r.plan[0].intent === 'set_weight' && r.plan[0].args.weight === 65, r.plan[0]);

  const n = VOS.Numbers.resolve('ate', ['reps'], anchors);
  ok('"ate" is read as 8', n && n.value === 8, n);
  const n2 = VOS.Numbers.resolve('one twenty', ['weight'], { weight: 100 });
  ok('a plausible load beats an implausible rep count', n2 && n2.kind === 'weight', n2);
  const n3 = VOS.Numbers.rescoreAlternatives(['hate', 'eight', 'ate'], ['reps'], anchors);
  ok('the best of several ASR guesses is chosen', n3 && n3.value === 8, n3);
  ok('a rep count far from the target is treated as less likely',
     VOS.Numbers.fit(9, 'reps', 9) > VOS.Numbers.fit(43, 'reps', 9), null);
  ok('a load that is not a plate multiple scores lower',
     VOS.Numbers.fit(62.5, 'weight', 62.5) > VOS.Numbers.fit(63, 'weight', 62.5), null);
}

/* ============================================================ */
sec('Self-correction — the last thing said wins');
{
  const s = makeSession(VOS, { history: hist });
  const { dialogue } = makeCtrl(VOS, s);
  let r = VOS.Resolver.resolve({ text: 'set it to 70, actually no, make it 65', dialogue, session: s });
  ok('"70 — actually make it 65" resolves to 65',
     r.plan[0].intent === 'set_weight' && r.plan[0].args.weight === 65, r.plan[0]);
  ok('the correction is recorded, not silently applied', !!r.correction, r.correction);

  r = VOS.Resolver.resolve({ text: 'nine, I mean ten', dialogue, session: s });
  ok('"nine, I mean ten" resolves to ten', (r.plan[0].args.reps === 10 || r.plan[0].args.rpe === 10), r.plan[0]);

  dialogue.expect('reps');
  r = VOS.Resolver.resolve({ text: 'no I did 9', dialogue, session: s });
  ok('"no I did 9" is a correction of the last set',
     r.plan[0].intent === 'correct_last' && r.plan[0].args.value === 9, r.plan[0]);
}

/* ============================================================ */
sec('Multiple intents in one breath');
{
  const s = makeSession(VOS, { history: hist });
  const { dialogue } = makeCtrl(VOS, s);
  const r = VOS.Resolver.resolve({ text: 'skip this exercise and give me two minutes', dialogue, session: s });
  ok('two commands produce two steps', r.plan.length === 2, r.plan.map(p => p.intent));
  ok('both steps understood',
     /^skip_exercise,(extend_rest|set_rest)$/.test(r.plan.map(p => p.intent).join(',')), r.plan.map(p => p.intent));
  const one = VOS.Resolver.resolve({ text: 'eight and a half', dialogue, session: s });
  ok('a phrase containing "and" is not split into commands', one.plan.length === 1, one.plan);
}

/* ============================================================ */
sec('Confidence decides: execute, confirm or clarify');
{
  ok('a read-only question executes on weak confidence', VOS.Resolver.tierFor('whats_next', 0.4) === 'execute');
  ok('logging reps at 0.8 executes', VOS.Resolver.tierFor('reps', 0.8) === 'execute');
  ok('logging reps at 0.5 asks first', VOS.Resolver.tierFor('reps', 0.5) === 'confirm');
  ok('ending the workout at 0.8 asks first', VOS.Resolver.tierFor('end_workout', 0.8) === 'confirm');
  ok('a weight change at 0.5 asks first', VOS.Resolver.tierFor('set_weight', 0.5) === 'confirm');
  ok('anything destructive at 0.3 clarifies', VOS.Resolver.tierFor('skip_exercise', 0.3) === 'clarify');
}

/* ============================================================ */
sec('Nothing uncertain is executed silently');
{
  const s = makeSession(VOS, { history: hist });
  const h = makeCtrl(VOS, s);
  (async () => {
    await h.ctrl.beginWorkout();
    // a huge, implausible rep count should be questioned, not logged
    const before = s.current.sets.length;
    await h.ctrl.onUtterance('forty three', ['forty three'], 0.9);
    ok('an implausible rep count is queried rather than logged',
       s.current.sets.length === before || /43/.test(h.audio.last || ''), { sets: s.current.sets, said: h.audio.last });
  })();
}

/* ============================================================ */
sec('Validator — the engine cannot be corrupted');
{
  const s = makeSession(VOS, { history: hist });
  const { ctrl } = makeCtrl(VOS, s);
  s.start();
  const V = VOS.Validator;
  ok('500 reps rejected', !V.check({ type:'log_set', reps:500 }, s).ok);
  ok('negative weight rejected', !V.check({ type:'set_weight', weight:-5 }, s).ok);
  ok('9999 kg rejected', !V.check({ type:'set_weight', weight:9999 }, s).ok);
  ok('unknown exercise rejected', !V.check({ type:'substitute_exercise', name:'Unicorn Press' }, s).ok);
  ok('unknown action type rejected', !V.check({ type:'drop_database' }, s).ok);
  ok('a big jump in load needs confirming',
     V.check({ type:'set_weight', weight: s.current.weight * 3 }, s).needsConfirm === true, null);
  ok('extending rest while not resting is rejected',
     !V.check({ type:'extend_rest', seconds:60 }, s).ok, s.phase);
  ok('correcting with nothing logged is rejected', !V.check({ type:'correct_last', field:'reps', value:8 }, s).ok);
  s.logSet({ reps: 9 });
  ok('correcting after a set is allowed', V.check({ type:'correct_last', field:'reps', value:8 }, s).ok);
  s.logSet({ reps: 9 }); s.logSet({ reps: 9 });
  ok('a fourth set on a three-set exercise needs confirming',
     V.check({ type:'log_set', reps:9 }, s).needsConfirm === true, s.current.sets.length);
}

/* ============================================================ */
sec('Races, duplicates and stale actions');
{
  const s = makeSession(VOS, { history: hist });
  const { ctrl } = makeCtrl(VOS, s);
  s.start();
  const q = ctrl.queue;
  (async () => {
    const a = await q.submit([{ type:'log_set', reps:9 }], { utteranceId:'u1', stateVersion:s.version });
    ok('a valid set is applied', a.ok && s.current.sets.length === 1, a);

    const dup = await q.submit([{ type:'log_set', reps:9 }], { utteranceId:'u1', stateVersion:s.version });
    ok('the same utterance twice logs one set', dup.code === 'duplicate' && s.current.sets.length === 1, { dup, sets:s.current.sets.length });

    // decided against an old world, and no longer valid in the new one
    const staleVersion = s.version;
    s.logSet({ reps: 9 }); s.logSet({ reps: 9 });      // exercise now complete
    const stale = await q.submit([{ type:'log_set', reps:9 }], { utteranceId:'u2', stateVersion: staleVersion });
    ok('a stale action is caught by revalidation', !stale.ok, stale);

    // interleaved submissions still apply in order, one at a time
    const s2 = makeSession(VOS, { history: hist });
    const h2 = makeCtrl(VOS, s2); s2.start();
    const results = await Promise.all([
      h2.ctrl.queue.submit([{ type:'log_set', reps:8 }], { utteranceId:'a', stateVersion:s2.version }),
      h2.ctrl.queue.submit([{ type:'log_set', reps:9 }], { utteranceId:'b', stateVersion:s2.version }),
      h2.ctrl.queue.submit([{ type:'log_set', reps:7 }], { utteranceId:'c', stateVersion:s2.version })
    ]);
    ok('three simultaneous sets land in order, exactly once each',
       s2.current.sets.map(x => x.reps).join(',') === '8,9,7', s2.current.sets.map(x => x.reps));
    ok('all three reported applied', results.every(r => r.ok), results);
  })();
}

/* ============================================================ */
sec('Multi-action is all-or-nothing');
{
  const s = makeSession(VOS, { history: hist });
  const { ctrl } = makeCtrl(VOS, s);
  s.start();
  (async () => {
    const before = s.current.sets.length;
    const r = await ctrl.queue.submit(
      [{ type:'log_set', reps:9 }, { type:'set_weight', weight: 9999 }],
      { utteranceId:'multi1', stateVersion:s.version });
    ok('an invalid step rejects the whole plan', !r.ok, r);
    ok('and nothing from that plan was applied', s.current.sets.length === before, s.current.sets.length);
  })();
}

/* ============================================================ */
sec('Pain is a state, not a request');
{
  ok('"my shoulder hurts" is pain', !!VOS.Safety.detect('my shoulder hurts'), null);
  ok('"sharp pain in my knee" is sharp', (VOS.Safety.detect('sharp pain in my knee') || {}).severity === 'sharp', null);
  ok('the site is picked up', /back/.test((VOS.Safety.detect('my lower back is hurting') || {}).site || ''), VOS.Safety.detect('my lower back is hurting'));
  ok('"my legs are burning" is NOT pain', !VOS.Safety.detect('my legs are burning'), VOS.Safety.detect('my legs are burning'));
  ok('"that was brutal" is NOT pain', !VOS.Safety.detect('that was brutal'), null);
  ok('"I am really sore today" is NOT pain', !VOS.Safety.detect('I am really sore today'), null);

  const s = makeSession(VOS, { history: hist });
  const h = makeCtrl(VOS, s);
  (async () => {
    await h.ctrl.beginWorkout();
    const said0 = h.audio.said.length;
    await h.ctrl.onUtterance('my shoulder is really hurting on this', null, 0.9);
    ok('pain enters the safety state', VOS.Safety.active(s), s.safety);
    const line = h.audio.said[h.audio.said.length - 1];
    ok('it stops the set and offers options', /skip|swap|stop/i.test(line), line);
    ok('it does not diagnose', !/(strain|tear|impingement|tendonitis|you have)/i.test(line), line);
    ok('it points at a professional', /looked at|assess|can'?t (see|assess)/i.test(line), line);
    ok('a note is written to memory', s.notes.some(n => /pain/i.test(n.text)), s.notes);

    await h.ctrl.onUtterance('swap it', null, 0.9);
    ok('the swap happens and safety is resolved', !VOS.Safety.active(s), s.safety);
    ok('the replacement is not the same movement', s.current.name !== 'Bench Press', s.current.name);
  })();
}

/* ============================================================ */
sec('Substitution knows why');
{
  const painSub = VOS.Safety.best('Bench Press', 'pain', { painSite: 'shoulder' });
  ok('pain in the shoulder avoids another shoulder-loading press',
     painSub && !/Overhead|Incline Bench|Decline Bench/.test(painSub.name), painSub);
  const busy = VOS.Safety.best('Bench Press', 'equipment_busy');
  ok('a busy bench gives a different piece of equipment',
     busy && VOS.exerciseMeta(busy.name).load !== 'barbell', busy);
  const none = VOS.Safety.best('Back Squat', 'no_equipment', { available: ['bodyweight', 'dumbbell'] });
  ok('no barbell means no barbell',
     none && ['bodyweight','dumbbell'].includes(VOS.exerciseMeta(none.name).load), none);
  const hard = VOS.Safety.best('Back Squat', 'too_hard');
  ok('too hard gives something easier to control',
     hard && VOS.exerciseMeta(hard.name).load !== 'barbell', hard);
  const dis = VOS.Safety.best('Bulgarian Split Squat', 'dislike');
  ok('a dislike keeps the training objective', dis && VOS.Safety.group(dis.name) === VOS.Safety.group('Bulgarian Split Squat'), dis);
  ok('deadlift is never swapped for a leg curl on dislike alone',
     VOS.Safety.best('Deadlift', 'dislike').name !== 'Leg Curl', VOS.Safety.candidates('Deadlift', 'dislike').slice(0,3));
}

/* ============================================================ */
sec('Addressee gate');
{
  const d = new VOS.Dialogue();
  const g = new VOS.Gate({ strategy: 'hybrid' });
  const s = makeSession(VOS, {});
  ok('a wake word is always accepted', g.check({ text:'hey coach what is next', dialogue:d, session:s }).accept);
  ok('the wake word is stripped before parsing',
     g.check({ text:'coach, eight', dialogue:d, session:s }).text === 'eight', g.check({ text:'coach, eight', dialogue:d, session:s }));
  ok('a bare number with no window open is still accepted as a command',
     g.check({ text:'eight', dialogue:d, session:s }).accept);
  ok('chat aimed at a person is ignored',
     !g.check({ text:'hey man you good?', dialogue:d, session:s }).accept,
     g.check({ text:'hey man you good?', dialogue:d, session:s }));
  ok('"are you using this?" is ignored',
     !g.check({ text:'are you using this rack', dialogue:d, session:s }).accept);
  ok('open conversation is ignored outside a window',
     !g.check({ text:'so then he told me it was fine', dialogue:d, session:s }).accept);
  d.expect('rpe', { question: 'how was that' });
  ok('the same sentence is accepted inside a window',
     g.check({ text:'that was pretty hard', dialogue:d, session:s }).accept);
  const w = new VOS.Gate({ strategy: 'wake' });
  ok('wake-only mode ignores a bare number', !w.check({ text:'eight', dialogue:new VOS.Dialogue(), session:s }).accept);
  const o = new VOS.Gate({ strategy: 'open' });
  ok('open mode accepts everything', o.check({ text:'anything at all', dialogue:d, session:s }).accept);
  ok('every decision is logged for comparison', g.log.length >= 7, g.log.length);
}

/* ============================================================ */
sec('Speech cache goes stale when the world moves');
{
  const s = makeSession(VOS, { history: hist });
  const gen = new VOS.SpeechGen({ llm: { configured: () => true, cfg: {} } });
  gen.contextKey = 'A';
  gen.cache.set('exercise_intro', [{ tpl: 'Bench press next.', key: 'A' }]);
  gen.cache.set('set_ok', [{ tpl: 'That is {reps}.', key: 'A' }]);
  ok('a line written for this state is served', gen.line('exercise_intro', {}, 'A') === 'Bench press next.');
  gen.cache.set('exercise_intro', [{ tpl: 'Bench press next.', key: 'A' }]);
  ok('the same line is refused once the state moved', gen.line('exercise_intro', {}, 'B') === null);
  gen.touch('B');
  ok('stale context-scoped lines are dropped', (gen.cache.get('exercise_intro') || []).length === 0);
  ok('slotted lines survive, because their numbers are filled live',
     (gen.cache.get('set_ok') || []).length === 1);
  ok('and they still render against the new state',
     gen.line('set_ok', { reps: 9 }, 'B') === 'That is 9.');
  gen.invalidateAll();
  ok('safety wipes everything', gen.cache.size === 0);
}

/* ============================================================ */
setTimeout(() => {
  console.log(out.join('\n'));
  console.log(`\n${pass} passed, ${fail} failed\n`);
  process.exit(fail ? 1 : 0);
}, 300);
