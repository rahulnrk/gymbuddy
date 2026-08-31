/* Onboarding earns its screens: five of them, and the first answer
   changes what happens on the gym floor. */
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const res = [];
  const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));
  const errors = [];

  async function walk(startLabel, goalLabel) {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    page.on('pageerror', e => errors.push(e.message));
    await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
    await page.waitForTimeout(400);
    await page.evaluate(() => { window.VOS.Memory.reset(); location.reload(); });
    await page.waitForTimeout(500);
    const pick = async (label) => {
      for (const b of await page.$$('#obBody .obopt')) {
        if ((await b.textContent()).includes(label)) { await b.click(); await page.waitForTimeout(80); return; }
      }
      throw new Error('no option ' + label);
    };
    await pick(startLabel);
    await page.click('#obNext'); await page.waitForTimeout(80);   // accept time defaults
    await pick('Full gym');
    await pick(goalLabel || 'Build muscle');
    await page.click('#obSkip');                                   // skip the personal block
    await page.waitForTimeout(250);
    return page;
  }

  /* ---- length and shape ---- */
  let page = await walk('Training regularly');
  ok('the whole flow is five screens', await page.evaluate(() => window.VOS.ONBOARD_STEPS.length) === 5,
     await page.evaluate(() => window.VOS.ONBOARD_STEPS.length));
  ok('skipping the personal screen still finishes', (await page.$$('#obBody .obcard')).length >= 3);
  let p = await page.evaluate(() => window.VOSApp.M.profile);
  ok('sensible defaults survive an untouched screen', p.daysPerWeek === 3 && p.sessionMin === 45, p);
  ok('a regular trainee is not treated as a novice', p.askLoads === false && p.level === 'intermediate', p);
  await page.close();

  /* ---- the first-timer ---- */
  page = await walk('This is my first time');
  p = await page.evaluate(() => window.VOSApp.M.profile);
  ok('a first-timer is recorded as one', p.startingPoint === 'new' && p.level === 'beginner', p);
  ok('and the app knows not to hand them numbers', p.askLoads === true, p.askLoads);
  const note = await page.textContent('#obBody');
  ok('which it says out loud before they pick a programme', /ask what you'?re lifting/i.test(note));
  const top = await page.textContent('#obBody .obcard .nm');
  ok('the best fit is a beginner programme', /groundwork|bodyweight|foundation/i.test(top), top);

  await page.click('#obBody .obcard');
  await page.waitForTimeout(300);
  await page.evaluate(() => { const A = window.VOSApp.voice; A.say = A.speak = () => Promise.resolve(); window.VOSApp.startWorkout(); });
  await page.waitForTimeout(400);
  const first = await page.evaluate(() => {
    const e = window.VOSApp.session.exercises[0];
    return { name: e.name, needsWeight: e.needsWeight, weight: e.weight };
  });
  ok('so the first exercise asks the load instead of asserting one', first.needsWeight === true, first);
  await page.close();

  /* A beginner asking for strength is still a beginner. */
  page = await walk('This is my first time', 'Get stronger');
  const strengthTop = await page.textContent('#obBody .obcard .nm');
  ok('a matching goal does not promote a programme above your level',
     !/strength five|five day|push pull/i.test(strengthTop), strengthTop);
  await page.close();

  /* ---- coming back after years ---- */
  page = await walk("I've trained before, but not for a long time");
  p = await page.evaluate(() => window.VOSApp.M.profile);
  ok('a long return is its own answer, not lumped in with beginners', p.startingPoint === 'returning', p.startingPoint);
  ok('but it starts just as carefully', p.askLoads === true && p.level === 'beginner', p);
  ok('and it is told differently', /rebuild|under where you left off/i.test(await page.textContent('#obBody')));
  await page.close();

  res.forEach(r => console.log(r));
  const failed = res.filter(r => r.startsWith('FAIL')).length;
  if (errors.length) { console.log('\nerrors:'); errors.forEach(e => console.log('  ' + e)); }
  console.log(`\n${res.length - failed}/${res.length} passed`);
  await browser.close();
  process.exit(failed || errors.length ? 1 : 0);
})();
