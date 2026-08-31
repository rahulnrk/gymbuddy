/* The four things the user said were broken:
   buttons that did nothing, voice, chat, and a header that ate the page. */
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
    M.profile.onboarded = true; M.profile.name = 'Rahul';
    window.VOS.Memory.save(); location.reload();
  });
  await page.waitForTimeout(600);
  await page.evaluate(() => { const A = window.VOSApp.voice; A.say = A.speak = t => Promise.resolve(); });

  /* ---- 1. taps are explicit intent, never gated ---- */
  await page.click('#btnStartToday');
  await page.waitForTimeout(400);
  ok('start button opens the training screen', await page.isVisible('#screenWorkout'));

  const sayBtns = await page.locator('#dock [data-say], #screenWorkout [data-say]').count();
  ok('the training screen offers tappable phrases', sayBtns > 0, sayBtns);

  /* the session opens with a warm-up now — walk it before testing sets */
  await page.evaluate(async () => {
    let guard = 0;
    while (window.VOSApp.session && window.VOSApp.session.phase === 'warmup' && guard++ < 20) {
      await window.VOSApp.onUtterance('done', ['done'], 1, { source: 'ui' });
    }
  });
  await page.waitForTimeout(150);
  ok('the warm-up finishes into the working phase',
     await page.evaluate(() => window.VOSApp.session.phase) === 'working',
     await page.evaluate(() => window.VOSApp.session.phase));

  const before = await page.evaluate(() => window.VOSApp.session.setsDone);
  await page.evaluate(() => window.VOSApp.onUtterance('9', ['9'], 1, { source: 'ui' }));
  await page.waitForTimeout(300);
  const after = await page.evaluate(() => window.VOSApp.session.setsDone);
  ok('a tapped number logs a set instead of being ignored', after === before + 1, { before, after });

  /* the gate itself: same text, arriving from a tap, must not be refused */
  const gateVerdict = await page.evaluate(async () => {
    const r = await window.VOSApp.ctrl.onUtterance('ready', ['ready'], 1, { source: 'ui' });
    return r && r.reason;
  });
  ok('a tap is never answered with "not for me"', gateVerdict !== 'not-addressed', gateVerdict);

  /* ---- 2. chat: a real composer, not window.prompt ---- */
  await page.click('#btnType');
  await page.waitForTimeout(200);
  ok('the type button reveals a text field', await page.isVisible('#typeIn'));
  const setsB4 = await page.evaluate(() => window.VOSApp.session.setsDone);
  await page.fill('#typeIn', '8');
  await page.press('#typeIn', 'Enter');
  await page.waitForTimeout(350);
  const setsAfter = await page.evaluate(() => window.VOSApp.session.setsDone);
  ok('typed text is acted on', setsAfter === setsB4 + 1, { setsB4, setsAfter });
  ok('and the field clears itself for the next line', (await page.inputValue('#typeIn')) === '');

  /* ---- 3. voice denied: say so, and offer the way through ---- */
  await page.evaluate(() => window.VOSApp.voice.onError && window.VOSApp.voice.onError('not-allowed: permission denied'));
  await page.waitForTimeout(200);
  const helpShown = await page.isVisible('#micHelp');
  ok('a blocked microphone explains itself', helpShown);
  if (helpShown) {
    const txt = await page.textContent('#micHelpText');
    ok('and points at typing rather than leaving a dead end', /typ/i.test(txt), txt);
  }

  /* ---- 4. the sticky header must not sit under the topbar ---- */
  const boxes = await page.evaluate(() => {
    const t = document.querySelector('.topbar').getBoundingClientRect();
    const n = document.querySelector('.now.sticky:not(.hide)');   // warm-up card or working card, whichever is up
    return { topbarBottom: t.bottom, nowTop: n ? n.getBoundingClientRect().top : null,
             cssVar: getComputedStyle(document.documentElement).getPropertyValue('--topbar-h') };
  });
  ok('the topbar height is measured, not guessed', /\d/.test(boxes.cssVar || ''), boxes.cssVar);
  ok('the sticky card clears the topbar', boxes.nowTop == null || boxes.nowTop >= boxes.topbarBottom - 1, boxes);

  res.forEach(r => console.log(r));
  const failed = res.filter(r => r.startsWith('FAIL')).length;
  if (errors.length) { console.log('\nerrors:'); errors.forEach(e => console.log('  ' + e)); }
  console.log(`\n${res.length - failed}/${res.length} passed`);
  await browser.close();
  process.exit(failed || errors.length ? 1 : 0);
})();
