/* The demo has to survive a room. Seeded history everywhere, a session
   that runs itself, and no screen that opens empty. */
const { chromium } = require('playwright');
const path = require('path');
const FILE = 'file://' + path.join(__dirname, '..', 'voice-workout-os.html');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const res = [];
  const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));
  const errors = [];

  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE ' + m.text()); });

  /* ---- the URL you paste in the meeting room ---- */
  await page.goto(FILE + '?demo');
  await page.waitForTimeout(900);
  await page.evaluate(() => { const A = window.VOSApp.voice; A.say = A.speak = () => Promise.resolve(); });

  ok('?demo lands on today, not onboarding', await page.isVisible('#screenToday'));
  const hist = await page.evaluate(() => window.VOSApp.M.history);
  ok('it seeds a real block of training', hist.length >= 30, hist.length);
  ok('spanning about ten weeks',
     (Date.now() - Date.parse(hist[0].date)) / 864e5 > 55, Math.round((Date.now() - Date.parse(hist[0].date)) / 864e5));
  ok('the most recent session is recent enough to still be sore',
     (Date.now() - Date.parse(hist[hist.length - 1].date)) / 36e5 < 30);

  /* ---- the history has to look lived-in, not generated ---- */
  const bench = hist.flatMap(h => (h.exercises || []).filter(e => e.name === 'Bench Press' && e.sets.length))
                    .map(e => e.sets[0].weight);
  ok('the bench climbed over the block', bench[bench.length - 1] > bench[0], { from: bench[0], to: bench[bench.length - 1] });
  const press = hist.flatMap(h => (h.exercises || []).filter(e => e.name === 'Overhead Press' && e.sets.length))
                    .map(e => e.sets[0].weight);
  ok('but not everything went up in a straight line',
     new Set(press).size < press.length && press.some((w, i) => i && w === press[i - 1]), press);
  const gaps = hist.slice(1).map((h, i) => (Date.parse(h.date) - Date.parse(hist[i].date)) / 864e5);
  ok('there is a week missing, the way a real year has one', Math.max(...gaps) > 5, Math.max(...gaps).toFixed(1));
  ok('and one session that ended early', hist.some(h => (h.notes || []).length && h.skipped.length), null);

  /* ---- nothing opens empty ---- */
  const todayText = await page.textContent('#screenToday');
  ok('today names a session', /Upper|Lower/.test(todayText), todayText.slice(0, 80));
  ok('and reports what is still recovering', /recover|sore|ready/i.test(todayText));

  await page.click('[data-nav="body"]');
  await page.waitForTimeout(500);
  ok('the body map opens populated', await page.isVisible('#screenBody'));
  const heat = await page.evaluate(() => {
    const st = window.VOS.Recovery.state(window.VOSApp.M.history, null);
    const v = Object.values(st).map(x => x.fatigue);
    return { hot: v.filter(x => x > .5).length, warm: v.filter(x => x > .15 && x <= .5).length, fresh: v.filter(x => x <= .15).length };
  });
  ok('with muscles still hot from the last session', heat.hot >= 2, heat);
  ok('some part way back', heat.warm >= 2, heat);
  ok('and some fully recovered — a map, not a wash of red', heat.fresh >= 2, heat);
  await page.screenshot({ path: '/home/claude/vos/demo-body.png', fullPage: true });

  await page.click('[data-nav="today"]');
  await page.waitForTimeout(300);

  /* ---- the session that runs itself ---- */
  await page.click('#btnDemo');
  await page.waitForTimeout(300);
  ok('the demo controls are reachable in one tap', await page.isVisible('#dmPlay'));
  await page.evaluate(() => { document.querySelector('#sheet').classList.add('hide'); window.VOS.Demo.play({ speed: 3 }); });
  await page.waitForTimeout(1600);
  ok('playback starts a real session', await page.isVisible('#screenWorkout'));
  ok('and says what it just heard', await page.isVisible('#demoBanner'));
  ok('it opens on the warm-up, not the first working set',
     await page.evaluate(() => window.VOSApp.session.phase) === 'warmup',
     await page.evaluate(() => window.VOSApp.session.phase));

  await page.waitForTimeout(4000);
  const warm = await page.evaluate(() => ({
    idx: window.VOSApp.session.warmupIndex,
    energy: window.VOSApp.session.energy
  }));
  ok('the warm-up advances on its own', warm.idx > 0, warm);
  ok('and the check-in question gets answered along the way', warm.energy === 7, warm);
  await page.screenshot({ path: '/home/claude/vos/demo-warmup.png', fullPage: true });

  await page.waitForTimeout(14000);
  const mid = await page.evaluate(() => ({
    phase: window.VOSApp.session && window.VOSApp.session.phase,
    sets: window.VOSApp.session && window.VOSApp.session.setsDone,
    running: window.VOS.Demo.running
  }));
  ok('then working sets land without anyone touching the screen', mid.sets >= 2, mid);
  await page.screenshot({ path: '/home/claude/vos/demo-session.png', fullPage: true });

  await page.evaluate(() => window.VOS.Demo.stop());
  await page.waitForTimeout(300);
  ok('and it can be stopped', !(await page.isVisible('#demoBanner')));

  /* ---- nothing that looks unfinished ---- */
  const all = await page.evaluate(() => document.body.innerText);
  ok('no placeholder copy anywhere', !/lorem|coming soon|tbd|todo/i.test(all));
  ok('nothing chirpy', !/great job|awesome|congrats|🎉/i.test(all));

  /* ---- desktop framing ---- */
  const wide = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  wide.on('pageerror', e => errors.push('DESKTOP ' + e.message));
  await wide.goto(FILE + '?demo');
  await wide.waitForTimeout(800);
  const geom = await wide.evaluate(() => {
    document.querySelector('#dock').classList.remove('hide');   // it only shows mid-session
    const a = document.querySelector('#app').getBoundingClientRect();
    const d = document.querySelector('#dock').getBoundingClientRect();
    return { appW: Math.round(a.width), appL: Math.round(a.left), dockW: Math.round(d.width), dockL: Math.round(d.left) };
  });
  ok('on a laptop it is a device-width column, not a stretched page', geom.appW <= 460, geom);
  ok('and the dock lines up with it instead of drifting',
     Math.abs(geom.dockL - geom.appL) < 4 && Math.abs(geom.dockW - geom.appW) < 4, geom);
  await wide.screenshot({ path: '/home/claude/vos/demo-desktop.png' });

  res.forEach(r => console.log(r));
  const failed = res.filter(r => r.startsWith('FAIL')).length;
  if (errors.length) { console.log('\nerrors:'); errors.slice(0, 6).forEach(e => console.log('  ' + e)); }
  console.log(`\n${res.length - failed}/${res.length} passed`);
  await browser.close();
  process.exit(failed || errors.length ? 1 : 0);
})();
