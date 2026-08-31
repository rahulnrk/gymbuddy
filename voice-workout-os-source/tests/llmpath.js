/* Verifies the AI path end to end with a stubbed Anthropic endpoint. */
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const page = await browser.newPage({ viewport: { width: 420, height: 900 } });
  const errors = [];
  page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));

  // intercept the real API and return canned replies
  await page.route('https://api.anthropic.com/**', async (route) => {
    const body = JSON.parse(route.request().postData() || '{}');
    const full = body.messages[body.messages.length - 1].content;
    // only look at the utterance itself; the prompt also carries recent turns
    const m = full.match(/mid-workout:\s*\n?"((?:[^"\\]|\\.)*)"\s*$/);
    const last = m ? m[1] : full;
    if (/Write spoken lines for these moments/.test(full)) {
      // the pre-generation batch — answer it so the opening line is not delayed
      return route.fulfill({ status: 200, contentType: 'application/json',
        body: JSON.stringify({ content: [{ type: 'text', text: '{"workout_intro":["Bench press. 60 kilos, 8 to 10."]}' }] }) });
    }
    let reply;
    if (/rack is taken|someone.s on the bench|occupied/i.test(last)) {
      reply = { say: "Bench is taken — dumbbells will do the same job.", pain: false,
                intents: [{ intent: 'substitute_exercise', name: 'Dumbbell Bench Press', reason: 'equipment_busy', confidence: 0.95 }] };
    } else if (/hate/i.test(last)) {
      reply = { say: "Noted, I'll leave it out.", pain: false, intents: [] };
    } else if (/why/i.test(last)) {
      reply = { say: "You hit ten on all three sets last time at RPE seven, so the engine added two and a half kilos.", pain: false, intents: [] };
    } else if (/tired|wrecked|nothing left/i.test(last)) {
      reply = { say: "Then we cut the optional work and keep the main lifts.", pain: false,
                intents: [{ intent: 'reduce_volume', confidence: 0.95 }] };
    } else if (/twinge|shoulder|hurts/i.test(last)) {
      reply = { say: '', pain: true, painSite: 'shoulder', painSevere: false, intents: [] };
    } else if (/thirty five minutes|short on time/i.test(last)) {
      reply = { say: "Right, trimming to fit.", pain: false,
                intents: [{ intent: 'time_budget', minutes: 35, confidence: 0.95 }] };
    } else {
      reply = { say: "I don't have that.", pain: false, intents: [] };
    }
    await route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ content: [{ type: 'text', text: '```json\n' + JSON.stringify(reply) + '\n```' }] })
    });
  });

  await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
  await page.waitForTimeout(400);
  await page.evaluate(() => {
    window.__spoken = [];
    const A = window.VOSApp.voice; A.say = A.speak = t => { window.__spoken.push(t); return Promise.resolve(); };
    window.VOSApp.llm.cfg.apiKey = 'test-key';
    window.VOSApp.llm.cfg.model = 'claude-test';
    return window.VOSApp.startWorkout();
  });
  /* walk the warm-up before testing the working phase */
  await page.evaluate(async () => {
    let guard = 0;
    while (window.VOSApp.session && window.VOSApp.session.phase === 'warmup' && guard++ < 20) {
      await window.VOSApp.onUtterance('done', ['done'], 1, { source: 'ui' });
    }
  });
  await page.waitForFunction(() => window.__spoken.length > 0, null, { timeout: 8000 });
  await page.waitForTimeout(150);

  /* Count only what the five test utterances routed — the "done"s that
     walked the warm-up are fast-path by design. */
  const base = await page.evaluate(() => ({ fast: window.VOSApp.metrics.fast, ai: window.VOSApp.metrics.ai }));

  const out = [];
  for (const utt of [
    'why are we going up today',
    'the rack is taken by someone else',
    "I've got nothing left in the tank today",
    'I hate bulgarian split squats',
    "I'm short on time, thirty five minutes"
  ]) {
    const before = await page.evaluate(() => window.VOSApp.metrics.ai);
    const rr = await page.evaluate(t => window.VOSApp.onUtterance(t, [t], 0.9), utt);
    if (process.env.DEBUG) console.error('DBG', utt, JSON.stringify(rr && rr.route), before);
    await page.waitForFunction(n => window.VOSApp.metrics.ai > n, before, { timeout: 5000 });
    out.push({
      said: utt,
      reply: await page.evaluate(() => window.__spoken[window.__spoken.length - 1]),
      exercise: await page.evaluate(() => window.VOSApp.session.current.name),
      accessorySets: await page.evaluate(() => window.VOSApp.session.exercises.filter(e => e.priority === 3).map(e => e.targetSets)),
      budget: await page.evaluate(() => window.VOSApp.session.timeBudgetMin)
    });
  }

  const routed = await page.evaluate(() => ({ fast: window.VOSApp.metrics.fast, ai: window.VOSApp.metrics.ai }));
  const st = await page.evaluate(() => ({
    exercise: window.VOSApp.session.current.name,
    budget: window.VOSApp.session.timeBudgetMin,
    accessorySets: window.VOSApp.session.exercises.filter(e => e.priority === 3).map(e => e.targetSets),
    safety: window.VOSApp.session.safety,
    queue: window.VOSApp.ctrl.queue.stats
  }));

  const res = [];
  const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));
  const byUtt = t => out.find(o => o.said === t) || {};

  ok('an open question is answered without touching state',
     /hit ten on all three/.test(byUtt('why are we going up today').reply || ''), byUtt('why are we going up today'));
  ok('a busy rack becomes a real substitution',
     st.exercise === 'Dumbbell Bench Press', st.exercise);
  ok('"nothing left in the tank" trims optional volume',
     st.accessorySets.every(n => n <= 2), st.accessorySets);
  ok('a stated time limit reaches the engine', st.budget === 35, st.budget);
  ok('every one of these went to the model, none guessed locally',
     routed.ai - base.ai === 5 && routed.fast - base.fast === 0, { routed, base });
  ok('no proposal was rejected by the validator', st.queue.rejected === 0, st.queue);

  // the model claiming pain must route through the safety path, not an action
  await page.evaluate(t => window.VOSApp.onUtterance(t, [t], 0.9), 'something feels off in my shoulder joint');
  await page.waitForTimeout(300);
  const afterPain = await page.evaluate(() => ({ safety: window.VOSApp.session.safety, said: window.__spoken.slice(-1)[0] }));
  ok('a model-flagged pain report enters the safety state', !!afterPain.safety, afterPain);
  ok('and the safety line is written live, not cached',
     /skip|swap|stop/i.test(afterPain.said || ''), afterPain.said);

  console.log(res.join('\n'));
  const fails = res.filter(r => r.startsWith('FAIL')).length;
  console.log(`\n${res.length - fails} passed, ${fails} failed`);
  if (process.env.DUMP) console.log(JSON.stringify({ out, routed, st }, null, 2));
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
