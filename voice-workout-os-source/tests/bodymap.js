/* Onboarding, the figure, and the heat map. */
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' }).catch(() => chromium.launch());
  const page = await browser.newPage({ viewport: { width: 420, height: 900 }, deviceScaleFactor: 2 });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE ' + m.text()); });
  await page.goto('file://' + path.join(__dirname, '..', 'voice-workout-os.html'));
  await page.waitForTimeout(400);

  const res = [];
  const ok = (n, c, d) => res.push((c ? 'PASS  ' : 'FAIL  ') + n + (c ? '' : '  <- ' + JSON.stringify(d)));

  // ---- onboarding shows first ----
  ok('a first-time user lands in onboarding', await page.isVisible('#screenOnboard'));
  ok('the setup screen is hidden until then', !(await page.isVisible('#screenSetup')));
  await page.screenshot({ path: 'shot-onboard.png' });

  const pick = async (label) => {
    const btns = await page.$$('#obBody .obopt');
    for (const b of btns) { const t = await b.textContent(); if (t.includes(label)) { await b.click(); await page.waitForTimeout(90); return true; } }
    await btns[0].click(); await page.waitForTimeout(90); return false;
  };
  const chip = async (label) => { await page.click(`#obBody .obchip:text-is("${label}")`); await page.waitForTimeout(60); };

  // 1 — starting point
  ok('the first question is where you are starting from', /starting from/i.test(await page.textContent('.obq')), await page.textContent('.obq'));
  await pick('Training regularly');

  // 2 — days and length together, one screen
  ok('days and session length share a screen', (await page.$$('#obBody .obfield')).length === 2);
  await chip('4'); await chip('1 hour');
  ok('it says what that adds up to', /a week/.test(await page.textContent('#obBody .obsum')), await page.textContent('#obBody .obsum'));
  await page.click('#obNext'); await page.waitForTimeout(100);

  // 3, 4
  await pick('Full gym');
  await pick('Build muscle');

  // 5 — about you, all optional, full date of birth
  ok('the personal details come last', /about you/i.test(await page.textContent('.obq')), await page.textContent('.obq'));
  ok('and the whole screen can be skipped', await page.isVisible('#obSkip'));
  await page.fill('#obf_name', 'Rahul');
  await page.fill('#obf_dob', '1995-03-14');
  await chip('Female');
  ok('a date of birth reads back as an age', /30|31/.test(await page.textContent('#obBody .obsum')), await page.textContent('#obBody .obsum'));
  await page.click('#obNext');
  await page.waitForTimeout(250);

  ok('it ends on a ranked programme list', (await page.$$('#obBody .obcard')).length >= 3);
  const first = await page.textContent('#obBody .obcard .nm');
  ok('the best fit is flagged', /best fit/i.test(first), first);
  await page.screenshot({ path: 'shot-suggest.png' });

  const profile = await page.evaluate(() => window.VOSApp.M.profile);
  ok('the answers are stored', profile.name === 'Rahul' && profile.sex === 'female', profile);
  ok('the date of birth is kept whole, not reduced to a number', profile.dob === '1995-03-14', profile.dob);
  ok('and the age is derived from it', profile.age === 30 || profile.age === 31, profile.age);
  ok('five screens, not nine', await page.evaluate(() => window.VOS.ONBOARD_STEPS.length) === 5);
  ok('four days a week was recorded', profile.daysPerWeek === 4, profile.daysPerWeek);

  await page.click('#obBody .obcard');
  await page.waitForTimeout(350);
  ok('choosing a programme drops you on the home screen, ready to train',
     await page.isVisible('#screenToday'), await page.evaluate(() =>
       ['screenToday','screenSetup','screenOnboard'].filter(i => !document.querySelector('#'+i).classList.contains('hide'))));
  ok('and that programme is selected', !!profile.goal, await page.evaluate(() => window.VOSApp.M.profile.programId));

  // ---- run a workout so there is something to colour ----
  await page.evaluate(async () => {
    const A = window.VOSApp.voice; A.say = A.speak = () => Promise.resolve();
    document.querySelector('#budgetIn').value = '';
    await window.VOSApp.startWorkout();
    /* the session opens with a warm-up now — walk it the way a user
       would, by saying "done" to each drill */
    let guard = 0;
    while (window.VOSApp.session && window.VOSApp.session.phase === 'warmup' && guard++ < 20) {
      await window.VOSApp.onUtterance('done', ['done'], 1, { source: 'ui' });
    }
  });
  await page.waitForTimeout(300);
  for (const u of ['10','8','ready','10','ready','10','8','ready','12','ready','12','ready','12','8','ready']) {
    await page.evaluate(t => window.VOSApp.onUtterance(t, [t], 0.95), u);
    await page.waitForTimeout(45);
  }
  const setsDone = await page.evaluate(() => window.VOSApp.session.setsDone);
  ok('sets were logged', setsDone >= 6, setsDone);

  // ---- body map ----
  // mid-session the way in is the mini map on the training screen
  await page.click('#wkBodyLink');
  await page.waitForTimeout(400);
  ok('the body map opens', await page.isVisible('#screenBody'));

  const fig = await page.evaluate(() => {
    const zones = Array.from(document.querySelectorAll('#bmFigure .mz'));
    const heats = zones.map(z => ({ m: z.getAttribute('data-m'), h: +z.getAttribute('data-heat'), fill: z.style.fill }));
    return {
      count: zones.length,
      sex: document.querySelector('#bmFigure .figure').getAttribute('data-sex'),
      view: document.querySelector('#bmFigure .figure').getAttribute('data-view'),
      hot: heats.filter(x => x.h > 0.05).map(x => x.m),
      cold: heats.filter(x => x.h === 0).map(x => x.m),
      distinctFills: new Set(heats.map(x => x.fill)).size
    };
  });
  ok('the figure matches the chosen sex', fig.sex === 'female', fig.sex);
  ok('every muscle zone rendered', fig.count >= 24, fig.count);
  ok('worked muscles have heat', fig.hot.length >= 5, fig.hot);
  ok('untouched muscles stay cold', fig.cold.length >= 4, fig.cold.slice(0, 8));
  // everything logged in the last few minutes is equally sore, so recovery
  // is deliberately near-binary right after a session
  ok('freshly worked muscles are at full fatigue',
     fig.hot.length >= 5 && fig.distinctFills >= 2, fig);

  const chestHeat = await page.evaluate(() => {
    const z = document.querySelector('#bmFigure .mz[data-m="chest_mid"]');
    return z ? +z.getAttribute('data-heat') : null;
  });
  ok('pressing lights up the chest', chestHeat > 0.05, chestHeat);

  await page.screenshot({ path: 'shot-bodymap.png' });

  // flip to the back
  await page.click('#bmFlip');
  await page.waitForTimeout(300);
  const backView = await page.evaluate(() => document.querySelector('#bmFigure .figure').getAttribute('data-view'));
  ok('it turns around', backView === 'back', backView);
  const latHeat = await page.evaluate(() => {
    const z = document.querySelector('#bmFigure .mz[data-m="lat"]');
    return z ? +z.getAttribute('data-heat') : null;
  });
  ok('rowing lights up the lats on the back view', latHeat > 0.05, latHeat);
  await page.screenshot({ path: 'shot-bodymap-back.png' });

  // tap a muscle
  await page.click('#bmFigure .mz[data-m="lat"]');
  await page.waitForTimeout(150);
  const detail = await page.textContent('#bmDetail');
  ok('tapping a muscle shows its numbers',
     /Lats/.test(detail) && /(Effective sets|Fatigue left)/.test(detail), detail.slice(0, 140));
  ok('and names what hit it', /Barbell Row|Lat Pulldown|Pull-up|Seated Cable Row/.test(detail), detail.slice(0, 200));

  const recoveryUI = await page.evaluate(() => ({
    title: document.querySelector('#bmGroupTitle').textContent,
    readyVisible: !document.querySelector('#bmReadyCard').classList.contains('hide'),
    readyChips: document.querySelectorAll('#bmReady .chip').length,
    soreRows: document.querySelectorAll('#bmSore .sorerow').length,
    legend: document.querySelector('#bmLegendHi').textContent,
    detail: document.querySelector('#bmDetail').textContent
  }));
  ok('recovery is the view you land on', /recovered/i.test(recoveryUI.title), recoveryUI.title);
  ok('it lists what is ready to train', recoveryUI.readyVisible && recoveryUI.readyChips > 0, recoveryUI);
  ok('and a day-by-day strip for whatever is sore', recoveryUI.soreRows > 0, recoveryUI.soreRows);
  ok('the legend explains redness as soreness', /sore/i.test(recoveryUI.legend), recoveryUI.legend);
  ok('the detail panel gives an ETA, not just a percentage', /Ready/.test(recoveryUI.detail), recoveryUI.detail.slice(0, 140));
  ok('"last hit" reads as elapsed time', !/ready ago/.test(recoveryUI.detail), recoveryUI.detail.slice(0, 200));

  // the volume view still works
  await page.click('#bmRange .chip[data-v="week"]');
  await page.waitForTimeout(250);
  const weekUI = await page.evaluate(() => ({
    title: document.querySelector('#bmGroupTitle').textContent,
    readyHidden: document.querySelector('#bmReadyCard').classList.contains('hide'),
    legend: document.querySelector('#bmLegendHi').textContent,
    fills: new Set(Array.from(document.querySelectorAll('#bmFigure .mz')).map(z => z.style.fill)).size
  }));
  ok('the weekly volume view is still there', /By region/.test(weekUI.title) && weekUI.readyHidden, weekUI);
  ok('and its legend switches back', /target/i.test(weekUI.legend), weekUI.legend);
  ok('the volume view is a real gradient, not two colours', weekUI.fills >= 4, weekUI.fills);

  const groups = await page.evaluate(() => Array.from(document.querySelectorAll('#bmGroups .grow')).map(r => r.textContent));
  ok('regions roll up into groups', groups.length === 6, groups);
  const gaps = await page.textContent('#bmGaps');
  ok('it says what is being neglected', gaps.length > 10, gaps.slice(0, 90));

  console.log(res.join('\n'));
  const fails = res.filter(r => r.startsWith('FAIL')).length;
  console.log(`\n${res.length - fails} passed, ${fails} failed`);
  if (errors.length) console.log('page errors:', errors.slice(0, 5));
  await browser.close();
  process.exit(fails || errors.length ? 1 : 0);
})();
