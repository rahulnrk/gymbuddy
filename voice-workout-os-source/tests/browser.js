const { chromium } = require('playwright');
const path = require('path');

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
  const page = await browser.newPage({ viewport: { width: 420, height: 900 }, deviceScaleFactor: 2 });

  const errors = [];
  page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE: ' + m.text()); });

  await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
  await page.waitForTimeout(600);

  // silence real TTS so the run isn't paced by speech synthesis
  await page.evaluate(() => {
    window.__spoken = [];
    const A = window.VOSApp.voice; A.say = A.speak = (t) => { window.__spoken.push(t); return Promise.resolve(); };
  });

  await page.screenshot({ path: 'shot-setup.png', fullPage: true });

  // start a workout
  await page.evaluate(() => { document.querySelector('#budgetIn').value = ''; window.VOSApp.startWorkout(); });
  await walkWarmup(page);
  await page.waitForTimeout(300);

  const script = ['9','that was pretty good','ready','8','ready','7','that was hard','ready',
                  '10','8','ready','10','ready','10','what\'s next'];
  for (const s of script) {
    await page.evaluate(t => window.VOSApp.onUtterance(t, [t]), s);
    await page.waitForTimeout(90);
  }

  await page.screenshot({ path: 'shot-workout.png', fullPage: true });

  const state = await page.evaluate(() => ({
    exercise: window.VOSApp.session.current.name,
    sets: window.VOSApp.session.setsDone,
    volume: Math.round(window.VOSApp.session.totalVolume),
    spoken: window.__spoken.slice(-6),
    taps: window.VOSApp.metrics.taps,
    fast: window.VOSApp.metrics.fast,
    ai: window.VOSApp.metrics.ai,
    avgFastMs: window.VOSApp.metrics.fastMs.length
      ? +(window.VOSApp.metrics.fastMs.reduce((a,b)=>a+b,0)/window.VOSApp.metrics.fastMs.length).toFixed(2) : null,
    storage: window.VOS.Memory.usingLocalStorage
  }));

  // pocket mode render
  await page.click('#btnPocket');
  await page.waitForTimeout(200);
  await page.screenshot({ path: 'shot-pocket.png' });
  await page.click('#pocket');

  // end the workout and reach the summary
  for (const s of ['end the workout', 'yes', '8']) {
    await page.evaluate(t => window.VOSApp.onUtterance(t, [t]), s);
    await page.waitForTimeout(150);
  }
  await page.waitForTimeout(300);
  const summaryVisible = await page.isVisible('#screenSummary');
  await page.screenshot({ path: 'shot-summary.png', fullPage: true });

  console.log(JSON.stringify({ ...state, summaryVisible, errors }, null, 2));
  await browser.close();
  process.exit(errors.length ? 1 : 0);
})();
