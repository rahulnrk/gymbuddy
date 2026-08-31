/* The screen-level decisions: what you see first, what you can reach,
   and whether a mistake costs one tap to fix. */
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE ' + m.text()); });

  const res = [];
  const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));

  await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
  await page.waitForTimeout(400);
  await page.evaluate(() => {
    const M = window.VOSApp.M;
    M.profile.onboarded = true; M.profile.name = 'Rahul'; M.profile.sex = 'male';
    const H = (h, ex) => ({ date: new Date(Date.now() - h * 3600000).toISOString(), day: 'D', durationSec: 3000, sets: 12, volume: 9000, exercises: ex });
    const S = (w, r, rpe) => ({ reps: r, weight: w, rpe });
    M.history = [H(11, [{ name: 'Back Squat', sets: [S(100,6,9), S(100,6,9), S(100,5,10)] }])];
    window.VOS.Memory.save(); location.reload();
  });
  await page.waitForTimeout(600);

  /* ---- what you see first ---- */
  ok('a returning user lands on today, not a settings wall', await page.isVisible('#screenToday'));
  const startBox = await page.locator('#btnStartToday').boundingBox();
  ok('the start button is reachable without scrolling', startBox && startBox.y + startBox.height < 844, startBox);
  ok('the start button is a comfortable size', startBox && startBox.height >= 50, startBox && startBox.height);
  ok("today's session is named up front", (await page.textContent('#tdName')).length > 2, await page.textContent('#tdName'));
  ok('and its length is stated', /minutes/.test(await page.textContent('#tdMeta')), await page.textContent('#tdMeta'));

  /* ---- thumb zone ---- */
  const navBox = await page.locator('#nav').boundingBox();
  ok('navigation sits at the bottom of the screen', navBox && navBox.y > 844 - 110, navBox);
  const navBtn = await page.locator('.navbtn').first().boundingBox();
  ok('nav targets clear the 48px minimum', navBtn && navBtn.height >= 48, navBtn && navBtn.height);

  /* ---- the numbers you might change are steppers, not keyboards ---- */
  const before = await page.textContent('#qMins');
  await page.click('[data-q="mins"][data-d="-5"]');
  await page.click('[data-q="mins"][data-d="-5"]');
  await page.waitForTimeout(150);
  const after = await page.textContent('#qMins');
  ok('time adjusts without a keyboard', +after === +before - 10, { before, after });
  ok('and the consequence is spelled out immediately',
     /cut|Comfortable/.test(await page.textContent('#tdEstimate')), await page.textContent('#tdEstimate'));

  /* ---- recovery is surfaced before you commit to a session ---- */
  ok('recovery is visible on the home screen',
     (await page.$$('#tdRecBars .minirow')).length === 6);
  ok('and it names what is still sore', /coming back|recovered/i.test(await page.textContent('#tdRecNote')));

  /* ---- into the workout ---- */
  await page.evaluate(() => { const A = window.VOSApp.voice; A.say = A.speak = () => Promise.resolve(); });
  await page.click('#btnStartToday');
  await page.waitForTimeout(400);
  ok('starting hides the nav and shows the mic dock',
     !(await page.isVisible('#nav')) && await page.isVisible('#dock'));

  /* ---- the warm-up comes first, and it is not a wall of text ---- */
  ok('a session opens on the warm-up', await page.isVisible('#wuBox'));
  ok('which names one drill at a time', (await page.textContent('#wuName')).length > 2, await page.textContent('#wuName'));
  const wuDone = await page.locator('#wuDone').boundingBox();
  ok('with a thumb-sized way to move on', wuDone && wuDone.height >= 46, wuDone && wuDone.height);
  const steps = await page.locator('#wuSteps .wustep').count();
  ok('and a strip showing how much is left', steps >= 4, steps);
  ok('the working card stays out of the way until then', !(await page.isVisible('#wkBox')));

  await page.evaluate(async () => {
    let g = 0;
    while (window.VOSApp.session && window.VOSApp.session.phase === 'warmup' && g++ < 20) {
      await window.VOSApp.onUtterance('done', ['done'], 1, { source: 'ui' });
    }
  });
  await page.waitForTimeout(200);
  ok('finishing it hands the screen to the working card',
     await page.isVisible('#wkBox') && !(await page.isVisible('#wuBox')));

  await page.evaluate(t => window.VOSApp.onUtterance(t, [t], 0.95), '9');
  await page.waitForTimeout(250);

  /* ---- a wrong log costs one tap ---- */
  ok('an undo button appears after a set is logged', await page.isVisible('#btnUndo'));
  ok('and it says what it will undo', /9/.test(await page.textContent('#btnUndo')), await page.textContent('#btnUndo'));
  const setsBefore = await page.evaluate(() => window.VOSApp.session.setsDone);
  await page.click('#btnUndo');
  await page.waitForTimeout(300);
  const setsAfter = await page.evaluate(() => window.VOSApp.session.setsDone);
  ok('tapping it removes the set', setsAfter === setsBefore - 1, { setsBefore, setsAfter });

  /* ---- the thing you glance at stays on screen ---- */
  await page.evaluate(t => window.VOSApp.onUtterance(t, [t], 0.95), '9');
  await page.waitForTimeout(200);
  await page.evaluate(() => window.scrollTo(0, 500));
  await page.waitForTimeout(200);
  const nameBox = await page.locator('#wkName').boundingBox();
  ok('the current exercise stays pinned when you scroll', nameBox && nameBox.y < 300, nameBox);
  await page.screenshot({ path: 'shot-workout.png' });

  /* ---- contrast ---- */
  const contrast = await page.evaluate(() => {
    const lum = c => { const [r, g, b] = c.match(/\d+/g).map(n => { n = n / 255; return n <= .03928 ? n / 12.92 : Math.pow((n + .055) / 1.055, 2.4); }); return .2126 * r + .7152 * g + .0722 * b; };
    const bg = lum(getComputedStyle(document.body).backgroundColor.replace('rgba','rgb'));
    const probe = sel => { const e = document.querySelector(sel); if (!e) return null; const f = lum(getComputedStyle(e).color); return +(((Math.max(f, bg) + .05) / (Math.min(f, bg) + .05)).toFixed(2)); };
    return { note: probe('.note'), body: probe('.exname'), meta: probe('.metric .k') };
  });
  ok('body text is high contrast', contrast.body >= 7, contrast);
  ok('secondary text clears 4.5:1', contrast.note >= 4.5, contrast);
  ok('small labels clear 4.5:1', contrast.meta >= 4.5, contrast);

  console.log(res.join('\n'));
  const fails = res.filter(r => r.startsWith('FAIL')).length;
  console.log(`\n${res.length - fails} passed, ${fails} failed`);
  if (errors.length) console.log('page errors:', errors.slice(0, 4));
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
