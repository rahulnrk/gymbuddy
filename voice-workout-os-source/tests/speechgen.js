/* Verifies pre-generated speech: lines come from the model, are served
   from cache at zero latency, fill slots correctly, never repeat, and
   fall back cleanly when the model is unavailable. */
const { chromium } = require('playwright');
const path = require('path');

let batchCount = 0, lastPrompt = '', slowMode = false, failMode = false;

const VARIANTS = {
  set_ok:        ['Yep.', 'That\'s {reps}.', 'Down.', 'Got it.', 'Set {setNumber} away.'],
  set_top:       ['{reps}. Top of the range.', 'That\'s the {repHigh} we wanted.', 'Full range, good.'],
  set_low:       ['{reps} this time.', 'Bit short of {repLow}, noted.', 'Under the range, fine.'],
  set_over:      ['{reps}. {delta} up on last week.', 'Well clear of last time.', 'That\'s {delta} better.'],
  set_under:     ['{reps} — {delta} down. What happened?', 'That\'s off your usual. Everything alright?', 'Down {delta}. Talk to me.'],
  set_last:      ['That\'s the lot.', 'Done with that one.', 'Last one in the bank.'],
  ask_rpe:       ['How was it?', 'Hard?', 'What did that feel like?', 'Rate it.', 'How much left in there?'],
  rest_cue:      ['{rest}.', 'Take {rest}.', '{rest}, then again.', 'Breathe. {rest}.'],
  rest_active:   ['{rest}. Keep moving.', 'Walk it off, {rest}.', '{rest} — stay on your feet.'],
  rest_over:     ['Set {setNumber}.', '{weight}, when you\'re set.', 'Round {setNumber} of {targetSets}.', 'Up you get.'],
  rest_transition:['{rest}, then something new.', 'Rest {rest} and we move on.', '{rest}. New exercise after.'],
  rpe_easy:      ['Too easy. Add a bit?', 'Want more on there?', 'Shall we load it up?'],
  rpe_max:       ['Right at the edge. Holding.', 'That\'s enough. Same weight.', 'Noted, staying here.'],
  exercise_intro:['Barbell Row next. 50 kilos, 8 to 10.'],
  workout_intro: ['Bench press to open. 65 kilos, 8 to 10 — you cleared all three sets of 10 last week so we\'re up two and a half. Go when you want.']
};

(async () => {

  /* The session starts with a warm-up now — walk it the way a user
     would, by saying "done" to each drill, before testing the sets. */
  const walkWarmup = async (page) => {
    await page.evaluate(async () => {
      let guard = 0;
      while (window.VOSApp.session && window.VOSApp.session.phase === 'warmup' && guard++ < 20) {
        await window.VOSApp.onUtterance('done', ['done'], 1, { source: 'ui' });
      }
    });
    await page.waitForTimeout(150);
  };
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const page = await browser.newPage({ viewport: { width: 420, height: 900 } });
  const errors = [];
  page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));

  await page.route('https://api.anthropic.com/**', async (route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    const prompt = body.messages[body.messages.length - 1].content;
    if (failMode) return route.fulfill({ status: 500, contentType: 'text/plain', body: 'boom' });
    if (/Write spoken lines for these moments/.test(prompt)) {
      batchCount++; lastPrompt = prompt;
      const asked = Object.keys(VARIANTS).filter(m => new RegExp('"' + m + '"').test(prompt));
      const out = {};
      asked.forEach(m => { out[m] = VARIANTS[m].map((v, i) => batchCount > 1 ? v + '' : v); });
      if (slowMode) await new Promise(r => setTimeout(r, 1200));
      return route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: [{ type: 'text', text: JSON.stringify(out) }] }) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ content: [{ type: 'text', text: '{"say":"ok","action":null}' }] }) });
  });

  await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
  await page.waitForTimeout(400);
  await page.evaluate(() => {
    window.__spoken = [];
    const A = window.VOSApp.voice; A.say = A.speak = t => { window.__spoken.push(t); return Promise.resolve(); };
    window.VOSApp.llm.cfg.apiKey = 'test'; window.VOSApp.llm.cfg.model = 'test-model';
  });

  const results = [];
  const ok = (n, c, d) => results.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));

  // ---- opening line waits for the model, then everything is cached ----
  await page.evaluate(() => window.VOSApp.startWorkout());
  await walkWarmup(page);
  await page.waitForFunction(() => window.__spoken.length > 0, null, { timeout: 6000 });
  /* __spoken[0] is now the warm-up brief. The workout intro is the line
     that lands once the warm-up is finished. */
  const opener = await page.evaluate(() =>
    window.__spoken.find(t => /Warm-up done/.test(t)) || window.__spoken[window.__spoken.length - 1]);
  ok('the warm-up is briefed before anything else',
     /warm up first/i.test(await page.evaluate(() => window.__spoken[0])),
     await page.evaluate(() => window.__spoken[0]));
  ok('opening line is model-written, not the template', /to open/.test(opener), opener);

  // ---- every later line is served synchronously from cache ----
  await page.waitForTimeout(700);
  const timings = [];
  for (const utt of ['9', 'pretty hard', 'ready', '10', 'ready', '9', 'that was easy']) {
    const t = await page.evaluate(async (u) => {
      const before = performance.now();
      await window.VOSApp.onUtterance(u, [u]);
      return performance.now() - before;
    }, utt);
    timings.push(Math.round(t));
    await page.waitForTimeout(120);
  }
  const spoken = await page.evaluate(() => window.__spoken.slice(1));
  const worst = Math.max(...timings);
  ok('no turn blocked on the network (worst ' + worst + 'ms)', worst < 60, timings);

  const templatePool = ['Good.', 'Logged.', 'Nice.', 'How did that feel?', 'How was that one?', 'How hard was that?',
                        'Set 2. Go when you\'re ready.', 'Set 3. Go when you\'re ready.'];
  const fromTemplate = spoken.filter(s => templatePool.includes(s));
  ok('lines are model-written, not the canned pool', fromTemplate.length === 0, fromTemplate);

  const dupes = spoken.filter((s, i) => spoken.indexOf(s) !== i);
  ok('no line repeated within the session', dupes.length === 0, dupes);

  const slotLeak = spoken.filter(s => /\{\w+\}/.test(s));
  ok('every slot was filled before speaking', slotLeak.length === 0, slotLeak);

  const numeric = spoken.find(s => /\b9\b|\b10\b/.test(s));
  ok('slots filled with the real numbers', !!numeric, spoken);

  // ---- prompt hygiene ----
  ok('generator is told what was said recently', /do not reuse/i.test(lastPrompt));
  ok('generator only gets facts from the engine', /progressionReason/.test(lastPrompt));
  ok('generation happened in batches, not one call per line',
     batchCount > 0 && batchCount <= Math.ceil(spoken.length / 2), { batchCount, lines: spoken.length });

  const rep = await page.evaluate(() => window.VOSApp.speech.report());
  ok('most speech came from the model', parseInt(rep.modelShare) >= 80, rep);

  // ---- degradation: model dies mid-session ----
  failMode = true;
  await page.evaluate(() => { window.VOSApp.speech.cache.clear(); });
  await page.evaluate(u => window.VOSApp.onUtterance(u, [u]), 'ready');
  await page.evaluate(u => window.VOSApp.onUtterance(u, [u]), '8');
  await page.waitForTimeout(400);
  const afterFail = await page.evaluate(() => window.__spoken.slice(-2));
  ok('still speaks when the generator fails', afterFail.every(x => x && x.length > 0), afterFail);
  ok('no crash when the generator fails', errors.length === 0, errors);

  // ---- music grammar ----
  failMode = false;
  const musicProbe = await page.evaluate(() => {
    const p = t => window.VOS.Intent.parse(t, {}).intent;
    return { next: p('next'), nextSong: p('next song'), skip: p('skip this'),
             skipTrack: p('skip this track'), vol: p('turn the music down') };
  });
  ok('bare "next" still means the workout', musicProbe.next === 'done_set', musicProbe);
  ok('"next song" routes to music', musicProbe.nextSong === 'music_next', musicProbe);
  ok('bare "skip" still means the exercise', musicProbe.skip === 'skip_exercise', musicProbe);

  await page.evaluate(u => window.VOSApp.onUtterance(u, [u]), 'skip this track');
  await page.waitForTimeout(200);
  const fieldSum = await page.evaluate(() => window.VOSApp.field.summary());
  ok('music requests are counted as demand data', fieldSum.musicRequests >= 1, fieldSum);
  ok('unrecognised utterances are captured for labelling', Array.isArray(fieldSum.unmatchedTexts), fieldSum);
  const csv = await page.evaluate(() => window.VOSApp.field.toCSV().split('\n').slice(0, 2));
  ok('field log exports with a label column', /label/.test(csv[0]), csv);

  console.log(results.join('\n'));
  const fails = results.filter(r => r.startsWith('FAIL')).length;
  console.log(`\n${results.length - fails} passed, ${fails} failed`);
  console.log('sample of what it actually said:\n  ' + spoken.slice(0, 8).join('\n  '));
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
