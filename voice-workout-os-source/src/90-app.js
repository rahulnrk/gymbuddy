/* ============================================================
   90-app.js : UI glue
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = root.VOS;
  const $  = s => document.querySelector(s);
  const $$ = s => Array.from(document.querySelectorAll(s));
  const el = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html != null) n.innerHTML = html; return n; };

  const M = VOS.Memory.load();
  let voice, llm, ctrl, session, speech, field, dialogue, gate, bodyMap, onboarding;
  let playlist = [], playIdx = 0, shuffleOn = false, baseVol = 1.0;
  const metrics = { taps: 0, voiceTurns: 0, fast: 0, ai: 0, fastMs: [], aiMs: [], misheard: 0 };

  /* ============ tap counting: the number the experiment cares about ============ */
  document.addEventListener('click', (e) => {
    if (e.target.closest('button, .chip, input, select, textarea, audio')) metrics.taps++;
    paintPills();
  }, true);

  /* ============================================================
     ROUTER — one place decides what is visible, so the nav, the dock
     and the screens can never disagree with each other.
     ============================================================ */
  const SCREENS = ['screenOnboard', 'screenToday', 'screenSetup', 'screenWorkout', 'screenBody', 'screenSummary'];
  let current = 'screenToday';

  function showScreen(id, opts) {
    opts = opts || {};
    current = id;
    SCREENS.forEach(s2 => { const n = $('#' + s2); if (n) n.classList.toggle('hide', s2 !== id); });
    const training = id === 'screenWorkout';
    const chrome = id !== 'screenOnboard' && id !== 'screenSummary';
    $('#nav').classList.toggle('hide', !chrome || training);
    $('#dock').classList.toggle('hide', !training);
    $('#btnSettings').classList.toggle('hide', id === 'screenOnboard');
    document.querySelectorAll('.navbtn').forEach(b =>
      b.classList.toggle('on', b.dataset.nav === (id === 'screenBody' ? 'body' : 'today')));
    if (!opts.keepScroll) window.scrollTo({ top: 0, behavior: 'instant' in window ? 'instant' : 'auto' });
    if (id === 'screenToday') paintToday();
  }

  /* ============================================================
     TODAY — everything you need before training, above the fold
     ============================================================ */
  function todayIndex() {
    const prog = VOS.Memory.program();
    return Math.min(M.profile.nextDayIndex || 0, prog.days.length - 1);
  }

  function paintToday() {
    const prog = VOS.Memory.program();
    const idx = todayIndex();
    const day = prog.days[idx];
    if (!day) return;

    const sess = new VOS.Session({ program: prog, dayIndex: idx, history: M.history, profile: M.profile });
    const full = sess.estimateMinutes();

    $('#tdEyebrow').textContent = M.profile.name ? `Today, ${M.profile.name}` : 'Today';
    $('#tdName').textContent = day.name;
    $('#tdMeta').textContent = `${day.exercises.length} exercises · about ${full} minutes`;

    const pv = $('#tdPreview'); pv.innerHTML = '';
    day.exercises.slice(0, 6).forEach(e => pv.appendChild(el('span', null, e.name)));
    if (day.exercises.length > 6) pv.appendChild(el('span', null, `+${day.exercises.length - 6} more`));

    $('#qEnergy').textContent = +$('#energyIn').value || 7;
    $('#qSore').textContent   = +$('#soreIn').value || 2;
    $('#qMins').textContent   = +$('#budgetIn').value || M.profile.sessionMin || 60;

    // what the numbers above will actually change
    const mins = +$('#qMins').textContent;
    const energy = +$('#qEnergy').textContent;
    const s2 = new VOS.Session({ program: prog, dayIndex: idx, history: M.history, profile: M.profile });
    let line = '';
    if (mins < full) {
      const cuts = s2.applyEfficiencyMode(mins);
      line = cuts.length ? `In ${mins} minutes I'd cut ${cuts.length} thing${cuts.length > 1 ? 's' : ''} and keep the main lifts.` : '';
    } else line = `Comfortable in ${mins} minutes.`;
    if (energy <= 4) line += ' Low energy, so no load increases today.';
    $('#tdEstimate').textContent = line;

    // recovery at a glance, so the decision to train legs is informed
    const st = VOS.Recovery.state(M.history, null);
    const g = VOS.Recovery.byGroup(st);
    const rb = $('#tdRecBars'); rb.innerHTML = '';
    VOS.MUSCLE_GROUPS.forEach(k => {
      const row = el('div', 'minirow');
      row.appendChild(el('span', 'mn', k));
      const bar = el('span', 'mb'), fill = el('span', 'mf');
      fill.style.width = Math.round(g[k].fatigue * 100) + '%';
      fill.style.background = VOS.Heat.colorFor(g[k].fatigue);
      bar.appendChild(fill); row.appendChild(bar);
      rb.appendChild(row);
    });
    const sore = VOS.Recovery.stillSore(st).slice(0, 3);
    $('#tdRecNote').textContent = sore.length
      ? 'Still coming back: ' + sore.map(x => `${x.name} (${VOS.Recovery.fmtH(x.readyInH)})`).join(', ')
      : 'Everything has recovered.';

    $('#tdPlanName').textContent = prog.name;
    $('#tdPlanMeta').textContent = `Day ${idx + 1} of ${prog.days.length}` +
      (prog.weeks ? ` · ${prog.weeks}-week programme` : '');
  }

  function bindToday() {
    document.querySelectorAll('.stepper button').forEach(b => {
      b.onclick = () => {
        const map = { energy: ['#energyIn', 1, 10], sore: ['#soreIn', 1, 10], mins: ['#budgetIn', 15, 180] };
        const [sel, lo, hi] = map[b.dataset.q];
        const cur = +$(sel).value || (b.dataset.q === 'mins' ? (M.profile.sessionMin || 60) : (b.dataset.q === 'energy' ? 7 : 2));
        $(sel).value = Math.max(lo, Math.min(hi, cur + (+b.dataset.d)));
        paintToday();
      };
    });
    $('#btnStartToday').onclick = () => startWorkout();
    $('#tdRecovery').onclick = () => bodyMap.open(session) || showScreen('screenBody');
    $('#tdPlan').onclick = () => { showScreen('screenSetup'); setTimeout(() => $('#progChips').scrollIntoView({ behavior: 'smooth', block: 'center' }), 60); };
    $('#tdSetup').onclick = () => showScreen('screenSetup');
    $('#setupBack').onclick = () => showScreen('screenToday');
    $('#tdChangeDay').onclick = () => {
      const prog = VOS.Memory.program();
      openSheet('<h3 style="margin-bottom:12px">Which session today?</h3>' +
        prog.days.map((d, i) => `<button class="obcard" data-day="${i}" style="width:100%;margin-bottom:8px">
          <div class="nm">${d.name}${i === todayIndex() ? ' <span class="pill good">next up</span>' : ''}</div>
          <div class="meta">${d.exercises.map(e => e.name).join(' · ')}</div></button>`).join(''));
      document.querySelectorAll('[data-day]').forEach(b => b.onclick = () => {
        M.profile.nextDayIndex = +b.dataset.day; VOS.Memory.save();
        $('#sheet').classList.add('hide'); paintToday();
      });
    };
    document.querySelectorAll('.navbtn').forEach(b => b.onclick = () => {
      if (b.dataset.nav === 'today') showScreen('screenToday');
      else if (b.dataset.nav === 'body') { bodyMap.open(session); showScreen('screenBody'); }
      else $('#btnHistory').click();
    });
  }

  /* ============================================================
     SETUP SCREEN
     ============================================================ */
  function initSetup() {
    // --- voice support ---
    const sup = $('#voiceSupport');
    if (VOS.speechSupported) {
      sup.innerHTML = 'Speech recognition available. Chrome on Android or desktop works best; iOS Safari is unreliable and you may need the type button.';
    } else {
      sup.innerHTML = '<span style="color:var(--bad)">This browser has no SpeechRecognition API.</span> Everything still works via the keyboard button in the dock. For real voice use Chrome.';
    }

    // --- voices ---
    function fillVoices() {
      const vs = (root.speechSynthesis && root.speechSynthesis.getVoices()) || [];
      const sel = $('#voiceSel');
      sel.innerHTML = '';
      vs.filter(v => /^en/i.test(v.lang)).forEach(v => {
        const o = document.createElement('option');
        o.value = v.name; o.textContent = `${v.name} (${v.lang})`;
        sel.appendChild(o);
      });
      if (M.settings.voiceName) sel.value = M.settings.voiceName;
    }
    fillVoices();
    if (root.speechSynthesis) root.speechSynthesis.onvoiceschanged = fillVoices;
    $('#voiceSel').onchange = e => { M.settings.voiceName = e.target.value; VOS.Memory.save(); voice.setVoiceByName(e.target.value); };

    $('#btnTestVoice').onclick = () => voice.speak("Bench press. Last time 62 point 5 for 10, 9 and 8. Today, 65 kilos, 8 to 10. Start when you're ready.");
    $('#btnTestMic').onclick = () => {
      $('#micResult').textContent = 'say a number…';
      const once = (t) => { $('#micResult').textContent = `heard "${t}" → ${JSON.stringify(VOS.Intent.parse(t, {}))}`; voice.opts.onFinal = onUtterance; voice.stop(); };
      voice.opts.onFinal = once;
      voice.start();
    };

    // --- LLM ---
    $('#apiKey').value = M.settings.apiKey || '';
    $('#apiKey').onchange = e => { M.settings.apiKey = e.target.value.trim(); llm.cfg.apiKey = M.settings.apiKey; VOS.Memory.save(); };
    if (M.settings.model) {
      $('#modelSel').innerHTML = `<option value="${M.settings.model}">${M.settings.model}</option>`;
      $('#modelSel').value = M.settings.model;
    }
    $('#modelSel').onchange = e => { M.settings.model = e.target.value; llm.cfg.model = e.target.value; VOS.Memory.save(); };

    $('#btnLoadModels').onclick = async (ev) => {
      const b = ev.currentTarget; b.classList.add('busy');
      M.settings.apiKey = $('#apiKey').value.trim(); llm.cfg.apiKey = M.settings.apiKey; VOS.Memory.save();
      $('#llmResult').textContent = 'loading…';
      try {
        const ids = await llm.listModels();
        const sel = $('#modelSel'); sel.innerHTML = '';
        ids.forEach(id => { const o = document.createElement('option'); o.value = id; o.textContent = id; sel.appendChild(o); });
        const pref = ids.find(i => /haiku/.test(i)) || ids[0];
        sel.value = M.settings.model && ids.includes(M.settings.model) ? M.settings.model : pref;
        M.settings.model = sel.value; llm.cfg.model = sel.value; VOS.Memory.save();
        $('#llmResult').textContent = `${ids.length} models. Haiku is the fastest for gym latency.`;
      } catch (err) { $('#llmResult').innerHTML = `<span style="color:var(--bad)">${String(err).slice(0,180)}</span>`; }
      finally { b.classList.remove('busy'); }
    };

    $('#btnTestLLM').onclick = async (ev) => {
      const b = ev.currentTarget; b.classList.add('busy');
      M.settings.apiKey = $('#apiKey').value.trim();
      llm.cfg.apiKey = M.settings.apiKey; llm.cfg.model = $('#modelSel').value;
      VOS.Memory.save();
      $('#llmResult').textContent = 'testing…';
      try {
        const fake = { phase:'working', workout:'Upper A', currentExercise:{ name:'Bench Press', setNumber:2, targetSets:3, targetReps:'8-10', weight:65, setsThisSession:[{reps:9,kg:65,rpe:8}], progressionReason:'All sets hit 10 at RPE 7.7. Adding 2.5 kilos.', lastTime:[{reps:10,kg:62.5,rpe:7}] } };
        const r = await llm.ask(fake, 'why did we go up today', VOS.Memory.forLLM());
        llm.reset();
        $('#llmResult').innerHTML = `<span style="color:var(--accent)">${llm.lastLatency}ms:</span> "${r.say}"`;
        voice.speak(r.say);
      } catch (err) { $('#llmResult').innerHTML = `<span style="color:var(--bad)">${String(err).slice(0,200)}</span>`; }
      finally { b.classList.remove('busy'); }
    };

    // --- profile chips ---
    function chipGroup(sel, key) {
      $$(sel + ' .chip').forEach(c => {
        c.classList.toggle('on', M.profile[key] === c.dataset.v);
        c.onclick = () => { M.profile[key] = c.dataset.v; VOS.Memory.save(); chipGroup(sel, key); };
      });
    }
    chipGroup('#goalChips', 'goal');
    chipGroup('#levelChips', 'level');
    $('#daysWk').value = M.profile.daysPerWeek;
    $('#daysWk').onchange = e => { M.profile.daysPerWeek = +e.target.value; VOS.Memory.save(); };
    $('#sessMin').value = M.profile.sessionMin;
    $('#sessMin').onchange = e => { M.profile.sessionMin = +e.target.value; VOS.Memory.save(); };

    // --- dislikes ---
    const dp = $('#dislikePick');
    Object.keys(VOS.EXERCISES).sort().forEach(n => { const o = document.createElement('option'); o.value = n; o.textContent = n; dp.appendChild(o); });
    function paintDislikes() {
      const w = $('#dislikeChips'); w.innerHTML = '';
      if (!M.profile.dislikes.length) w.appendChild(el('span', 'note', 'none yet — you can also just say "I hate leg extensions" mid-workout'));
      M.profile.dislikes.forEach(n => {
        const c = el('button', 'chip x', n + ' ✕');
        c.onclick = () => { M.profile.dislikes = M.profile.dislikes.filter(x => x !== n); VOS.Memory.save(); paintDislikes(); paintProgram(); };
        w.appendChild(c);
      });
    }
    $('#btnAddDislike').onclick = () => { VOS.Memory.addDislike(dp.value); paintDislikes(); paintProgram(); };
    paintDislikes();

    // --- programme ---
    function paintProgram() {
      const w = $('#progChips'); w.innerHTML = '';
      const opts = Object.values(VOS.PROGRAMS).concat(M.profile.customProgram ? [M.profile.customProgram] : []);
      opts.forEach(p => {
        const c = el('button', 'chip' + (M.profile.programId === p.id ? ' on' : ''), p.name);
        c.onclick = () => { M.profile.programId = p.id; M.profile.nextDayIndex = 0; VOS.Memory.save(); paintProgram(); };
        w.appendChild(c);
      });
      const prog = VOS.Memory.program();
      const pv = $('#progPreview'); pv.innerHTML = '';
      prog.days.forEach((d, i) => {
        const r = el('div', 'planrow');
        r.appendChild(el('span', 'dot'));
        r.appendChild(el('span', null, `<b>${d.name}</b>`));
        r.appendChild(el('span', 'meta', d.exercises.map(e => e.name).join(' · ')));
        pv.appendChild(r);
      });
      const ds = $('#daySel'); ds.innerHTML = '';
      prog.days.forEach((d, i) => { const o = document.createElement('option'); o.value = i; o.textContent = d.name; ds.appendChild(o); });
      ds.value = Math.min(M.profile.nextDayIndex || 0, prog.days.length - 1);
      ds.onchange = updateEstimate;
      updateEstimate();
    }
    // ---------- forgiving import with a live preview ----------
    let draft = null, lastTxt = null, debounce = null;

    function renderImport() {
      const wrap = $('#importPreview');
      wrap.innerHTML = '';
      if (!draft || !draft.days.length) {
        if (($('#customTxt').value || '').trim())
          wrap.appendChild(el('div', 'note', 'Nothing recognised yet — keep typing.'));
        return;
      }
      const st = draft.stats;
      const bits = [`${st.exercises} exercise${st.exercises === 1 ? '' : 's'} across ${st.days} day${st.days === 1 ? '' : 's'}`];
      if (st.guessed) bits.push(`${st.guessed} interpreted`);
      if (st.custom) bits.push(`${st.custom} not in the library`);
      if (st.needWeight) bits.push(`${st.needWeight} will be asked about in the gym`);
      wrap.appendChild(el('div', 'note ok', bits.join(' · ')));

      draft.days.forEach((d, di) => {
        const box = el('div', 'ipday');
        const h = el('h4'); h.textContent = d.name;
        box.appendChild(h);
        box.appendChild(el('div', 'ipcols', '<span>sets</span><span>reps</span><span>to</span><span>kg</span>'));

        d.exercises.forEach((ex, ei) => {
          const row = el('div', 'iprow');
          const nm = el('div', 'nm');
          nm.textContent = ex.name;
          if (ex.typed && VOS.Import.normalise(ex.typed) !== VOS.Import.normalise(ex.name))
            nm.appendChild(el('small', null, 'you wrote: ' + ex.typed));
          row.appendChild(nm);

          const num = (val, min, max, step, ph, set, wide) => {
            const i = document.createElement('input');
            i.type = 'number'; i.className = 'num' + (wide ? ' w' : ''); i.min = min; i.max = max; i.step = step || 1;
            if (val != null && !(ph && !val)) i.value = val; else i.placeholder = ph || '';
            i.oninput = () => { set(i.value === '' ? null : parseFloat(i.value)); };
            return i;
          };
          const ctrls = el('div', 'ctrls');
          ctrls.appendChild(num(ex.sets, 1, 12, 1, null, v => { ex.sets = v || 1; }));
          ctrls.appendChild(num(ex.repLow, 1, 60, 1, null, v => { ex.repLow = v || 1; }));
          ctrls.appendChild(num(ex.repHigh, 1, 60, 1, null, v => { ex.repHigh = v || ex.repLow; }));
          ctrls.appendChild(num(ex.weight, 0, 500, 1.25, ex.needsWeight ? 'ask' : null,
            v => { if (v == null) { ex.weight = 0; ex.needsWeight = true; } else { ex.weight = v; ex.needsWeight = false; } }, true));
          const rm = el('button', 'rm', '✕');
          rm.title = 'remove';
          rm.onclick = () => { d.exercises.splice(ei, 1); recount(); renderImport(); };
          ctrls.appendChild(rm);
          row.appendChild(ctrls);

          if (ex.notes && ex.notes.length) {
            const n = el('div', 'ipnote' + (ex.confidence === 'exact' || ex.confidence === 'alias' ? ' ok' : ''));
            n.textContent = ex.notes.join(' · ');
            row.appendChild(n);
          }
          box.appendChild(row);
        });

        const add = el('div', 'iphead');
        add.appendChild(el('span', 'lbl', 'add to ' + d.name));
        const sel = document.createElement('select');
        sel.style.flex = '1'; sel.style.minWidth = '150px';
        Object.keys(VOS.EXERCISES).sort().forEach(n => {
          const o = document.createElement('option'); o.value = n; o.textContent = n; sel.appendChild(o);
        });
        const btn = el('button', 'btn sm', 'Add');
        btn.onclick = () => {
          const meta = VOS.exerciseMeta(sel.value);
          const def = (VOS.Import.DEFAULT_REPS[meta.load] || VOS.Import.DEFAULT_REPS.barbell)[meta.kind];
          d.exercises.push({
            name: sel.value, typed: sel.value, sets: 3, repLow: def[0], repHigh: def[1],
            weight: 0, needsWeight: meta.load !== 'bodyweight', priority: d.exercises.length < 2 ? 1 : 3,
            confidence: 'exact', notes: meta.load !== 'bodyweight' ? ["weight not given — I'll ask you on the first set"] : []
          });
          recount(); renderImport();
        };
        add.appendChild(sel); add.appendChild(btn);
        box.appendChild(add);
        wrap.appendChild(box);
      });
    }

    function recount() {
      if (!draft) return;
      draft.days = draft.days.filter(d => d.exercises.length);
      draft.days.forEach(d => d.exercises.forEach((e, i) => { e.priority = i < 2 ? 1 : (i < 4 ? 2 : 3); }));
      const all = draft.days.flatMap(d => d.exercises);
      draft.stats = {
        days: draft.days.length, exercises: all.length,
        guessed: all.filter(e => e.confidence === 'guess').length,
        custom: all.filter(e => e.confidence === 'custom').length,
        needWeight: all.filter(e => e.needsWeight).length
      };
    }

    function reparse() {
      const txt = $('#customTxt').value;
      if (txt === lastTxt) return;
      lastTxt = txt;
      draft = VOS.Import.parseProgram(txt, { goal: M.profile.goal, level: M.profile.level });
      renderImport();
    }

    $('#customTxt').oninput = () => { clearTimeout(debounce); debounce = setTimeout(reparse, 220); };

    $('#btnImport').onclick = () => {
      reparse();
      if (!draft || !draft.days.length) {
        $('#importResult').innerHTML = '<span style="color:var(--bad)">Nothing to import yet.</span>';
        return;
      }
      recount();
      M.profile.customProgram = JSON.parse(JSON.stringify(draft));
      M.profile.programId = 'custom';
      M.profile.nextDayIndex = 0;
      VOS.Memory.save();
      paintProgram();
      $('#importResult').innerHTML = `<span style="color:var(--accent)">Saved.</span>`;
      $('#progNote').innerHTML = `<span style="color:var(--accent)">Using your programme — ${draft.stats.exercises} exercises across ${draft.stats.days} day${draft.stats.days === 1 ? '' : 's'}.</span>`;
      $('#ownProg').open = false;
      $('#progChips').scrollIntoView({ behavior: 'smooth', block: 'center' });
    };

    if (M.profile.customProgram) {
      // rebuild an editable draft from what was saved last time
      draft = JSON.parse(JSON.stringify(M.profile.customProgram));
      recount();
    }

    paintProgram();

    ['#energyIn','#soreIn','#budgetIn'].forEach(s => $(s).onchange = updateEstimate);
    $('#btnStart').onclick = startWorkout;

    // --- music ---
    const audio = $('#music');
    $('#musicFiles').onchange = (e) => {
      playlist = Array.from(e.target.files);
      if (!playlist.length) return;
      playIdx = 0;
      audio.onended = () => musicControl('next');
      loadTrack();
    };
    voice.attachMusic(audio);
    voice.baseVolume = baseVol;
  }

  /* ---------- demo music player ----------
     V1 ships duck-only: the OS lowers whatever app the user is actually
     using and we never touch it. This player exists so the ducking is
     demonstrable and so the "next" vs "next track" grammar gets tested. */
  function loadTrack() {
    const audio = $('#music');
    if (!playlist.length) return;
    audio.src = URL.createObjectURL(playlist[playIdx % playlist.length]);
    audio.volume = baseVol;
    audio.play().catch(() => {});
  }

  function musicControl(what, dir) {
    const audio = $('#music');
    if (!playlist.length) return false;
    switch (what) {
      case 'next':
        playIdx = shuffleOn ? Math.floor(Math.random() * playlist.length) : playIdx + 1;
        loadTrack(); return true;
      case 'prev':
        if (audio.currentTime > 3) { audio.currentTime = 0; return true; }
        playIdx = (playIdx - 1 + playlist.length) % playlist.length; loadTrack(); return true;
      case 'pause':   audio.pause(); return true;
      case 'resume':  audio.play().catch(()=>{}); return true;
      case 'shuffle': shuffleOn = !shuffleOn; return true;
      case 'volume':
        baseVol = Math.max(0, Math.min(1, baseVol + (dir || 1) * 0.15));
        voice.baseVolume = baseVol;
        if (!voice.speaking) audio.volume = baseVol;
        return true;
      default: return false;
    }
  }

  function agentVolume(dir) {
    voice.opts.rate = voice.opts.rate; // unchanged
    M.settings.ttsVolume = Math.max(0.2, Math.min(1, (M.settings.ttsVolume || 1) + dir * 0.2));
    voice.opts.volume = M.settings.ttsVolume;
    VOS.Memory.save();
  }

  function updateEstimate() {
    const prog = VOS.Memory.program();
    const idx = +$('#daySel').value || 0;
    const s = new VOS.Session({ program: prog, dayIndex: idx, history: M.history, profile: M.profile });
    const full = s.estimateMinutes();
    const budget = +$('#budgetIn').value;
    let txt = `Estimated ${full} minutes as written.`;
    if (budget && budget < full) {
      const cuts = s.applyEfficiencyMode(budget);
      txt += ` With ${budget} minutes I'd cut: ${cuts.slice(0,4).join('; ')}${cuts.length>4?'…':''} — mains untouched.`;
    }
    const energy = +$('#energyIn').value;
    if (energy && energy <= 4) txt += ' Energy is low, so no load increases today and accessories get trimmed.';
    $('#estimate').textContent = txt;
  }

  /* ---------- custom programme parser ---------- */
  function parseCustom(text) {
    const lines = text.split('\n').map(l => l.trim()).filter(Boolean);
    const days = [];
    let cur = null;
    lines.forEach(line => {
      if (line.startsWith('#')) { cur = { name: line.replace(/^#+\s*/, ''), exercises: [] }; days.push(cur); return; }
      const p = line.split('|').map(x => x.trim());
      if (p.length < 3) throw new Error(`Can't read: "${line}"`);
      if (!cur) { cur = { name: 'Day 1', exercises: [] }; days.push(cur); }
      const reps = (p[2] || '8-10').split('-').map(Number);
      const idx = cur.exercises.length;
      cur.exercises.push({
        name: p[0], sets: parseInt(p[1], 10) || 3,
        repLow: reps[0] || 8, repHigh: reps[1] || reps[0] || 10,
        weight: parseFloat(p[3]) || 0,
        priority: idx < 2 ? 1 : (idx < 4 ? 2 : 3)
      });
    });
    if (!days.length) throw new Error('Nothing to import.');
    return { id: 'custom', name: 'My programme', goal: M.profile.goal, level: M.profile.level, schedule: [], days };
  }

  /* ============================================================
     WORKOUT
     ============================================================ */
  function startWorkout() {
    const prog = VOS.Memory.program();
    const dayIndex = +$('#daySel').value || 0;
    session = new VOS.Session({ program: prog, dayIndex, history: M.history, profile: M.profile });
    session.checkin({
      energy: +$('#energyIn').value || null,
      soreness: +$('#soreIn').value || null,
      timeBudgetMin: +$('#budgetIn').value || null
    });

    speech = new VOS.SpeechGen({ llm, enabled: M.settings.speechGen !== false });
    field = new VOS.FieldLog().begin();
    dialogue = new VOS.Dialogue();
    gate = new VOS.Gate({ strategy: M.settings.gate || 'hybrid' });
    if (M.settings.recordAudio) {
      field.startRecording().then(ok => {
        addMsg('sys', ok ? 'recording session audio for the field study' : 'could not start recording: ' + field.recordError);
      });
    }

    ctrl = new VOS.Controller({
      session, dialogue, gate, field, speech,
      audio: voice,
      log: (e) => {
        if (e.who === 'user') addMsg('user', e.text, null, null, e.gated);
        else addMsg('agent', e.text, null, e.source);
      },
      onState: paint,
      onFinish: showSummary,
      memory: () => VOS.Memory.forLLM(),
      llm: (snap, utter, mem) => llm.ask(snap, utter, mem),
      music: musicControl,
      agentVolume: agentVolume,
      memoryStore: VOS.Memory,
      questionDelayMs: () => (VOS.Demo && VOS.Demo.running) ? Math.round(1100 / (VOS.Demo.speed || 1)) : 3500
    });

    showScreen('screenWorkout');
    const sx = M.profile.sex === 'female' ? 'female' : 'male';
    $('#wkFront').innerHTML = VOS.Figure.svg({ sex: sx, idPrefix: 'wf-' });
    $('#wkBack').innerHTML  = VOS.Figure.svg({ sex: sx, back: true, idPrefix: 'wb-' });
    // the mini map is the obvious way into the full one mid-session
    $('#wkBodyLink').onclick = () => { bodyMap.open(session); showScreen('screenBody'); };
    $('#dock').classList.remove('hide');
    $('#subline').textContent = session.day.name;

    // honest fatigue observation from long-term memory
    const tr = VOS.Memory.fatigueTrend();
    if (tr && tr.falling) {
      addMsg('sys', `memory: energy has fallen across your last three sessions (${tr.energies.join(' → ')})`);
    }

    const started = ctrl.beginWorkout();
    field.event('workout_start', { day: session.day.name, energy: session.energy });
    if (M.settings.handsFree) startListening();
    paint();
    if (!clockTimer) clockTimer = setInterval(clock, 250);
    return started;
  }

  let clockTimer = null;
  function clock() {
    if (!ctrl) return;
    ctrl.tick();
    paintRest();
    paintPills();
    if (!$('#pocket').classList.contains('hide')) paintPocket();
  }

  /* ---------- painting ---------- */
  /* The warm-up owns the top of the screen until it is finished. */
  function paintWarmup() {
    const inWarmup = session && session.phase === 'warmup' && !session.warmupDone;
    $('#wuBox').classList.toggle('hide', !inWarmup);
    $('#wkBox').classList.toggle('hide', !!inWarmup);
    if (inWarmup) $('#restBox').classList.add('hide');   // no rest timer during a warm-up
    if (!inWarmup) return;

    const w = session.warmup, item = session.warmupItem;
    const mins = Math.max(1, Math.round(session.warmupLeftSec / 60));
    $('#wuKicker').textContent = `Warm-up · ${session.warmupIndex + 1} of ${session.warmupCount} · about ${mins} min left`;
    $('#wuName').textContent = item ? item.name : '—';
    $('#wuHow').textContent = item ? item.how : '';

    const strip = $('#wuSteps'); strip.innerHTML = '';
    w.items.forEach((it, i) => {
      const cls = 'wustep' + (it.ramp ? ' ramp' : '') +
        (i < w.cursor ? ' done' : i === w.cursor ? ' here' : '');
      const seg = el('div', cls);
      seg.title = it.name;
      strip.appendChild(seg);
    });
    $('#wuWhyLine').textContent = '';
  }

  function paint() {
    if (!session) return;
    paintWarmup();
    const ex = session.current;
    const P = VOS.Policy;

    if (ex) {
      $('#wkKicker').textContent = `${session.day.name} · exercise ${session.cursor.ex + 1} of ${session.exercises.filter(e=>e.status!=='skipped').length}`;
      $('#wkName').textContent = ex.name;
      $('#mWeight').innerHTML = ex.weight === 0 ? 'BW' : `${ex.weight}<small> kg</small>`;
      const complete = ex.sets.length >= ex.targetSets;
      $('#mSet').innerHTML = complete
        ? `<span style="color:var(--accent)">done</span>`
        : `${ex.sets.length + 1}<small>/${ex.targetSets}</small>`;
      $('#mReps').textContent = `${ex.repLow}–${ex.repHigh}`;

      const g = $('#setGrid'); g.innerHTML = '';
      ex.sets.forEach((s, i) => {
        const pill = el('div', 'setpill', `<b>${s.reps}</b> × ${s.weight === 0 ? 'BW' : s.weight}${s.rpe ? `<span class="rpe">RPE ${s.rpe}</span>` : ''}`);
        if (s.ts && Date.now() - s.ts < 1200 && i === ex.sets.length - 1) pill.classList.add('justlogged');
        g.appendChild(pill);
      });
      showUndoFor(ex);
      for (let i = ex.sets.length; i < ex.targetSets; i++) g.appendChild(el('div', 'setpill todo', `— × ${ex.weight === 0 ? 'BW' : ex.weight}`));

      const why = ex.progression && ex.progression.reason;
      $('#whyLine').textContent = why && ex.sets.length === 0 ? why : (ex.note || '');
    }

    const pl = $('#planList'); pl.innerHTML = '';
    session.exercises.forEach((e, i) => {
      const cls = e.status === 'skipped' ? 'skipped' : (i === session.cursor.ex ? 'active' : (e.sets.length >= e.targetSets ? 'done' : ''));
      const r = el('div', 'planrow ' + cls);
      r.appendChild(el('span', 'dot'));
      r.appendChild(el('span', null, e.name));
      r.appendChild(el('span', 'meta', `${e.sets.length}/${e.targetSets} · ${e.weight === 0 ? 'BW' : e.weight + 'kg'}`));
      pl.appendChild(r);
    });

    if (bodyMap && !$('#screenBody').classList.contains('hide')) bodyMap.render();
    if (session && $('#wkFront').firstChild) {
      const hm = VOS.Heat.forSession(session);
      VOS.Heat.paint($('#wkFront'), hm);
      VOS.Heat.paint($('#wkBack'), hm);
      const lit = Object.keys(hm).filter(m => hm[m].heat > 0.08);
      $('#wkHeatNote').textContent = lit.length
        ? lit.length + ' regions worked this session'
        : 'nothing logged yet';
    }
    $('#micLbl').textContent = ctrl && ctrl.busy ? 'thinking…'
      : (voice.wantListening ? (voice.speaking ? 'speaking — say anything to cut in' : 'listening') : 'Start listening');
    const mb = $('#micBtn');
    mb.className = 'mic' + (ctrl && ctrl.busy ? ' busy' : (voice.speaking ? ' hot' : (voice.wantListening ? ' on' : '')));
    paintRest(); paintPills();
  }

  /* A mistaken log should cost one tap to fix, not a spoken sentence. */
  let undoTimer = null;
  function showUndoFor(ex) {
    const last = ex.sets[ex.sets.length - 1];
    const btn = $('#btnUndo');
    const fresh = last && last.ts && Date.now() - last.ts < 12000;
    btn.classList.toggle('hide', !fresh);
    if (!fresh) return;
    btn.textContent = `Undo ${last.reps}`;
    btn.onclick = () => { onUtterance('undo', ['undo'], 1); btn.classList.add('hide'); };
    clearTimeout(undoTimer);
    undoTimer = setTimeout(() => btn.classList.add('hide'), 12000 - (Date.now() - last.ts));
  }

  function paintRest() {
    if (!session) return;
    const box = $('#restBox');
    if (session.phase === 'resting' && session.restLeftSec > 0) {
      box.classList.remove('hide');
      const left = session.restLeftSec;
      $('#restNum').textContent = left >= 60 ? `${Math.floor(left/60)}:${String(left%60).padStart(2,'0')}` : left;
      $('#restLbl').textContent = session.restKind === 'active' ? 'Active recovery' : 'Recovery';
      const cur = session.current;
      const more = cur && cur.sets.length < cur.targetSets;
      $('#restMsg').textContent = session.restKind === 'active'
        ? 'Walk around, stay loose.'
        : (more ? `Next: set ${cur.sets.length + 1} of ${cur.targetSets}` : 'Next: new exercise');
      const frac = 1 - left / Math.max(1, session.restPlannedSec);
      $('#ringArc').setAttribute('stroke-dashoffset', String(201 * frac));
    } else box.classList.add('hide');
  }

  function paintPills() {
    if (!session) return;
    const s = session.elapsedSec;
    $('#pElapsed').textContent = `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;
    $('#pSets').textContent = `${session.setsDone} sets`;
    $('#pVol').textContent = `${Math.round(session.totalVolume).toLocaleString()} kg`;
    $('#pTouch').textContent = `${metrics.taps} taps`;
    $('#pVoice').textContent = `${metrics.voiceTurns} voice`;
    const avgF = metrics.fastMs.length ? Math.round(metrics.fastMs.reduce((a,b)=>a+b,0)/metrics.fastMs.length) : null;
    const avgA = metrics.aiMs.length ? Math.round(metrics.aiMs.reduce((a,b)=>a+b,0)/metrics.aiMs.length) : null;
    $('#pLat').textContent = `${metrics.fast} fast${avgF!=null?` ${avgF}ms`:''} · ${metrics.ai} ai${avgA!=null?` ${avgA}ms`:''}`;
  }

  function addMsg(who, text, tag, source, gated) {
    const f = $('#feed');
    const m = el('div', 'msg ' + who + (gated ? ' gated' : ''));
    if (source) m.dataset.source = source;
    if (gated) m.title = 'not addressed to the agent: ' + gated;
    if (tag) m.appendChild(el('span', 'tag ' + tag, tag === 'llm' ? 'ai path' : 'fast path'));
    m.appendChild(document.createTextNode(text));
    f.appendChild(m);
    while (f.children.length > 60) f.removeChild(f.firstChild);
    f.scrollTop = f.scrollHeight;
  }

  /* ---------- utterance routing ---------- */
  async function onUtterance(text, alts, asrConfidence, opts) {
    if (!ctrl || !session) return;
    metrics.voiceTurns++;
    $('#partial').textContent = '';

    // the end-of-session fatigue answer must not be logged as reps
    if (dialogue.expecting('fatigue') && session.phase === 'summary') {
      const n = VOS.Intent.firstNumber(String(text).toLowerCase());
      if (n != null && n >= 1 && n <= 10) {
        session.endFatigue = n; dialogue.clear('fatigue');
        addMsg('user', text);
        ctrl.say(n >= 8 ? 'Noted. Eat and sleep.' : 'Good. Logged.');
        showSummary(session.summary());
        return;
      }
    }

    const r = await ctrl.onUtterance(text, alts, asrConfidence, opts);
    if (r.route === 'fast')       { metrics.fast++; if (r.ms) metrics.fastMs.push(r.ms); tagLast('fast'); }
    else if (r.route === 'llm')   { metrics.ai++; if (r.ms) metrics.aiMs.push(r.ms); tagLast('llm'); }
    else if (r.route === 'gated') { metrics.gated = (metrics.gated || 0) + 1; }
    paint();
    return r;
  }

  function tagLast(kind) {
    const f = $('#feed');
    for (let i = f.children.length - 1; i >= 0; i--) {
      const c = f.children[i];
      if (c.classList.contains('agent') && !c.querySelector('.tag')) {
        const src = c.dataset.source === 'model' ? ' · written' : ' · template';
        c.insertBefore(el('span', 'tag ' + kind, (kind === 'llm' ? 'ai path' : 'fast path') + src), c.firstChild);
        break;
      }
    }
  }

  /* A denied microphone must not end the session — say what happened,
     and put the typed alternative one tap away. */
  function showMicHelp(msg) {
    /* During a hands-free playback the app is visibly working, so a
       banner saying the microphone is blocked reads as a bug. */
    if (VOS.Demo && VOS.Demo.running) return;
    const box = $('#micHelp');
    box.classList.remove('hide');
    $('#micHelpText').textContent = /no speech recognition/i.test(msg)
      ? "This browser has no speech recognition. Everything still works by typing."
      : "The microphone was blocked here. Open the file in Chrome to talk, or type instead — everything works either way.";
    $('#micHelpType').onclick = () => {
      box.classList.add('hide');
      $('#composer').classList.remove('hide');
      setTimeout(() => $('#typeIn').focus(), 40);
    };
    $('#micHelpDismiss').onclick = () => box.classList.add('hide');
  }

  /* ---------- mic control ---------- */
  function startListening() {
    if (!voice.start()) addMsg('sys', 'no speech recognition here — use the keyboard button');
    paint();
  }

  /* ---------- summary ---------- */
  function showSummary(summary) {
    summary = summary || session.summary();
    voice.stop();
    $('#pocket').classList.add('hide');
    showScreen('screenSummary');
    const mins = Math.round(summary.durationSec / 60);
    $('#sumTitle').textContent = `${summary.day} · ${mins} min`;

    const st = $('#sumStats'); st.innerHTML = '';
    const stats = [
      ['Sets', summary.sets], ['Volume', Math.round(summary.volume).toLocaleString() + ' kg'],
      ['Energy at start', summary.energy ?? '—'], ['Fatigue at end', summary.endFatigue ?? '—']
    ];
    stats.forEach(([k, v]) => { const d = el('div', 'metric'); d.appendChild(el('div','k',k)); d.appendChild(el('div','v',String(v))); st.appendChild(d); });

    const ex = $('#sumEx'); ex.innerHTML = '';
    summary.exercises.forEach(e => {
      const line = e.sets.length
        ? e.sets.map(s => `${s.weight === 0 ? 'BW' : s.weight} × ${s.reps}${s.rpe ? ` @${s.rpe}` : ''}`).join('  ·  ')
        : (e.status === 'skipped' ? 'skipped' : 'not started');
      const d = el('div', 'kv');
      d.appendChild(el('span', 'k', e.name));
      d.appendChild(el('span', 'v', line));
      ex.appendChild(d);
    });
    if (summary.prs.length) {
      const d = el('div', 'note'); d.style.marginTop = '10px';
      d.textContent = 'Best sets you have on record: ' + summary.prs.map(p => `${p.exercise} ${p.weight}×${p.reps}`).join(', ');
      ex.appendChild(d);
    }

    /* What the check-in actually taught the model. Worth showing,
       because it is the part the person supplied. */
    const ci = ctrl && ctrl.checkin && ctrl.checkin.report();
    if (ci && (ci.surprises.length || ci.learned.length)) {
      const d = el('div', 'note reported'); d.style.marginTop = '12px';
      const bits = [];
      ci.surprises.forEach(s => {
        const name = ((VOS.MUSCLES[s.muscle] || {}).name || s.muscle).toLowerCase();
        bits.push(`You reported your ${name} sore, which the model had not predicted` +
                  (s.why && s.why.cause ? ` — most likely from ${s.why.cause.name}` : '') + '.');
      });
      if (ci.learned.includes('recovered faster than estimated'))
        bits.push('You came back faster than the estimate had you down for, so the map has been corrected.');
      if (ci.learned.includes('less sore than estimated'))
        bits.push('Less sore than estimated — the map has been corrected.');
      d.innerHTML = bits.join(' ');
      ex.appendChild(d);
    }
    if (session && session.warmup && session.warmup.skipped) {
      const d = el('div', 'note'); d.style.marginTop = '8px';
      d.textContent = 'You skipped the warm-up today.';
      ex.appendChild(d);
    }

    const mm = $('#sumMetrics'); mm.innerHTML = '';
    const avgF = metrics.fastMs.length ? Math.round(metrics.fastMs.reduce((a,b)=>a+b,0)/metrics.fastMs.length) : 0;
    const avgA = metrics.aiMs.length ? Math.round(metrics.aiMs.reduce((a,b)=>a+b,0)/metrics.aiMs.length) : 0;
    [['Screen taps', metrics.taps],
     ['Voice turns', metrics.voiceTurns],
     ['Handled locally', `${metrics.fast} (${avgF} ms avg)`],
     ['Sent to the model', `${metrics.ai} (${avgA} ms avg)`],
     ['Local share', metrics.fast + metrics.ai ? Math.round(100 * metrics.fast / (metrics.fast + metrics.ai)) + '%' : '—']
    ].forEach(([k, v]) => { const d = el('div','kv'); d.appendChild(el('span','k',k)); d.appendChild(el('span','v',String(v))); mm.appendChild(d); });

    // ---- field study ----
    const fs = field ? field.summary() : null;
    const sp = speech ? speech.report() : null;
    if (fs) {
      const card = el('div', 'card');
      card.appendChild(el('h3', null, 'Field study'));
      const add = (k, v) => { const d = el('div','kv'); d.appendChild(el('span','k',k)); d.appendChild(el('span','v',String(v))); card.appendChild(d); };
      add('Utterances heard', fs.utterances);
      add('Understood', fs.matched);
      add('Not understood', fs.unmatched);
      add('One utterance every', fs.secondsPerUtterance != null ? fs.secondsPerUtterance + ' s' : '—');
      add('Music commands', fs.musicRequests);
      const cr = ctrl ? ctrl.report() : null;
      if (cr) {
        add('Turns', cr.turns);
        add('Acted on directly', cr.executed);
        add('Confirmed before acting', cr.confirmations);
        add('Asked to clarify', cr.clarifications);
        add('Ignored as not addressed', cr.gatedOut);
        add('Actions rejected by the validator', cr.rejectedActions);
        add('Duplicate utterances dropped', cr.queue.duplicates);
        add('Stale actions dropped', cr.queue.stale);
        add('Resolve time', (cr.avgResolveMs != null ? cr.avgResolveMs : 0) + ' ms avg');
        if (cr.audio) {
          add('Times you cut it off', cr.audio.interrupts);
          add('Stop-talking latency', (cr.audio.avgInterruptMs != null ? cr.audio.avgInterruptMs : 0) + ' ms');
          add('Ducked then resumed (false alarms)', cr.audio.falseDucks);
          add('Own voice filtered out', cr.audio.echoSuppressed);
        }
        add('Addressee strategy', cr.gate.strategy + ' — ' + cr.gate.rejectRate + ' ignored');
      }
      if (sp) {
        add('Lines written by the model', sp.servedFromModel);
        add('Lines from the fallback template', sp.servedFromTemplate);
        add('Model share of speech', sp.modelShare);
        add('Pre-generation batches', `${sp.batches} (${sp.avgBatchMs} ms avg, off the critical path)`);
        add('Stale lines dropped', sp.staleLinesDropped);
      }
      if (fs.unmatchedTexts.length) {
        card.appendChild(el('div', 'note', '<hr>Not understood — these are your candidates for third-party speech, self-talk, or a missing fast-path phrase:'));
        fs.unmatchedTexts.slice(0, 25).forEach(t => card.appendChild(el('div', 'note', '· ' + t)));
      }
      const row = el('div', 'row wrap'); row.style.marginTop = '14px';
      const bj = el('button', 'btn sm', 'Export log (JSON)');
      const bc = el('button', 'btn sm', 'Export log (CSV)');
      row.appendChild(bj); row.appendChild(bc);
      const dl = (name, blob) => { const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click(); };
      const stamp = new Date().toISOString().slice(0,16).replace(/[:T]/g,'-');
      bj.onclick = () => dl(`session-${stamp}.json`, new Blob([field.toJSON(summary)], {type:'application/json'}));
      bc.onclick = () => dl(`session-${stamp}.csv`, new Blob([field.toCSV()], {type:'text/csv'}));
      if (field.recording || field.audioBlob) {
        const ba = el('button', 'btn sm', 'Download audio');
        ba.onclick = async () => {
          ba.textContent = 'finalising…';
          const blob = field.audioBlob || await field.stopRecording();
          if (blob) dl(`session-${stamp}.webm`, blob);
          ba.textContent = 'Download audio';
        };
        row.appendChild(ba);
      } else if (field.recordError) {
        card.appendChild(el('div', 'note warn', 'Audio recording failed: ' + field.recordError));
      }
      card.appendChild(row);
      $('#screenSummary').insertBefore(card, $('#btnSaveSession'));
      if (field.recording) field.stopRecording();
    }

    $('#btnSaveSession').onclick = () => {
      summary.endFatigue = session.endFatigue;
      session.notes.forEach(n => VOS.Memory.addNote(n.text, 'session'));
      VOS.Memory.addSession(summary);
      location.reload();
    };
  }

  /* ---------- pocket mode ---------- */
  function paintPocket() {
    const s = session;
    if (!s) return;
    if (s.phase === 'resting' && s.restLeftSec > 0) {
      $('#pkLbl').textContent = s.restKind === 'active' ? 'Active recovery' : 'Recovery';
      const l = s.restLeftSec;
      $('#pkBig').textContent = l >= 60 ? `${Math.floor(l/60)}:${String(l%60).padStart(2,'0')}` : l;
    } else {
      $('#pkLbl').textContent = voice.speaking ? 'Speaking' : (voice.wantListening ? 'Listening' : 'Paused');
      $('#pkBig').textContent = s.current ? (s.current.weight === 0 ? 'BW' : s.current.weight) : '—';
    }
    $('#pkEx').textContent = s.current ? `${s.current.name} · set ${Math.min(s.current.sets.length + 1, s.current.targetSets)} of ${s.current.targetSets}` : '';
  }

  /* ============================================================
     SHEETS
     ============================================================ */
  function openSheet(html) { $('#sheetBody').innerHTML = html; $('#sheet').classList.remove('hide'); }
  $('#sheetClose').onclick = () => $('#sheet').classList.add('hide');
  $('#sheet').onclick = e => { if (e.target.id === 'sheet') $('#sheet').classList.add('hide'); };

  $('#btnHistory').onclick = () => {
    const h = M.history.slice().reverse();
    let html = '<h3 style="margin-bottom:12px">Training memory</h3>';
    html += `<p class="note">${M.history.length} session(s) stored ${VOS.Memory.usingLocalStorage ? 'in this browser' : '<span style="color:var(--warn)">in memory only — this preview blocks storage, so history dies on reload</span>'}.</p>`;
    if (M.notes.length) html += '<hr><h3>Things you told it</h3>' + M.notes.slice(-12).map(n => `<div class="kv"><span class="k">${new Date(n.t).toLocaleDateString()}</span><span class="v" style="font-weight:400;text-align:right">${n.text}</span></div>`).join('');
    html += '<hr>';
    if (!h.length) html += '<p class="note">Nothing yet.</p>';
    h.forEach(s => {
      html += `<div class="card" style="margin-top:10px"><b>${s.day}</b> <span class="note">${(s.date||'').slice(0,10)} · ${Math.round(s.durationSec/60)} min · ${s.sets} sets · ${Math.round(s.volume).toLocaleString()} kg</span>`;
      (s.exercises||[]).filter(e=>e.sets&&e.sets.length).forEach(e => {
        html += `<div class="kv"><span class="k">${e.name}</span><span class="v">${e.sets.map(x=>`${x.weight||'BW'}×${x.reps}`).join(' · ')}</span></div>`;
      });
      html += '</div>';
    });
    html += '<hr><button class="btn sm" id="btnExport">Export JSON</button> <button class="btn sm danger" id="btnWipe">Erase everything</button>';
    openSheet(html);
    $('#btnExport').onclick = () => {
      const blob = new Blob([VOS.Memory.exportJSON()], { type: 'application/json' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'workout-memory.json'; a.click();
    };
    $('#btnWipe').onclick = () => { if (confirm('Erase all training memory?')) { VOS.Memory.reset(); location.reload(); } };
  };

  /* ============================================================
     DEMO CONTROLS
     For showing the product in a room: ten weeks of history so
     nothing is empty, and a session that runs itself when the
     microphone is unavailable or the room is too loud.
     ============================================================ */
  function demoBanner(state) {
    let b = $('#demoBanner');
    if (!state || !state.running) { if (b) b.remove(); return; }
    if (!b) {
      b = document.createElement('div');
      b.id = 'demoBanner';
      b.innerHTML = '<span class="dot"></span><span id="demoSaid"></span>' +
                    '<button class="btn sm ghost" id="demoStop">Stop</button>';
      document.body.appendChild(b);
      $('#demoStop').onclick = () => VOS.Demo.stop();
    }
    $('#demoSaid').textContent = state.said ? '“' + state.said + '”' : 'playing';
  }
  VOS.Demo.onState = demoBanner;

  function openDemoSheet() {
    const n = M.history.length;
    openSheet(`<h3 style="margin-bottom:6px">Demo</h3>
      <p class="note">${n} session${n === 1 ? '' : 's'} in memory.</p>
      <div class="oblist" style="margin-top:16px">
        <button class="obcard top" id="dmPlay">
          <div class="nm">Run a session, hands free</div>
          <div class="tag">Logs sets, moves the timers, heats the body map. No microphone needed.</div>
        </button>
        <button class="obcard" id="dmSeed">
          <div class="nm">Load ten weeks of training</div>
          <div class="tag">A real block: the lifts climb, the press stalls, a holiday week is missing, one session ended early.</div>
        </button>
        <button class="obcard" id="dmFresh">
          <div class="nm">Start as a brand new user</div>
          <div class="tag">Wipes everything and opens on the first onboarding screen.</div>
        </button>
      </div>`);
    $('#dmPlay').onclick = () => {
      $('#sheet').classList.add('hide');
      $('#micHelp').classList.add('hide');
      VOS.Demo.play();
    };
    $('#dmSeed').onclick = () => { VOS.Demo.seed(); location.reload(); };
    $('#dmFresh').onclick = () => { VOS.Demo.fresh(); location.reload(); };
  }
  $('#btnDemo').onclick = openDemoSheet;

  $('#btnSettings').onclick = () => {
    openSheet(`<h3 style="margin-bottom:12px">Settings</h3>
      <label class="field"><span>Speech rate — ${M.settings.rate}</span><input type="range" id="sRate" min="0.7" max="1.5" step="0.02" value="${M.settings.rate}" style="width:100%"></label>
      <label class="field"><span>Duck music to — ${Math.round(M.settings.duckTo*100)}%</span><input type="range" id="sDuck" min="0" max="0.6" step="0.02" value="${M.settings.duckTo}" style="width:100%"></label>
      <label class="field"><span>Recognition language</span><input type="text" id="sLang" value="${M.settings.lang}"></label>
      <label class="field"><span><input type="checkbox" id="sHF" ${M.settings.handsFree?'checked':''}> Start listening automatically</span></label>
      <label class="field"><span><input type="checkbox" id="sGen" ${M.settings.speechGen!==false?'checked':''}> Model writes the agent's lines (pre-generated during rest, served instantly)</span></label>
      <label class="field"><span><input type="checkbox" id="sRec" ${M.settings.recordAudio?'checked':''}> Record session audio for the field study</span></label>
      <hr>
      <label class="field"><span>Who is it listening to — swap this between sessions and compare</span>
        <select id="sGate">
          <option value="hybrid">Hybrid — open window after it speaks, wake word otherwise</option>
          <option value="open">Open — accept everything (most false activations)</option>
          <option value="window">Window only — no wake word, deaf between prompts</option>
          <option value="wake">Wake word always — say "coach" first</option>
        </select></label>
      <label class="field"><span>Barge-in</span>
        <select id="sBarge">
          <option value="vad">Two stage — duck on any sound, stop on real speech</option>
          <option value="transcript">Transcript only — stop when words are recognised</option>
          <option value="off">Off — let it finish talking</option>
        </select></label>
      <hr>
      <label class="field"><span>Backend URL (keeps the API key off this device)</span>
        <input type="text" id="sProxy" placeholder="http://localhost:8787" value="${M.settings.proxyUrl || ''}"></label>
      <label class="field"><span>Neural TTS endpoint (optional — POST {text} returns audio)</span>
        <input type="text" id="sTts" placeholder="http://localhost:8788/speak" value="${M.settings.ttsUrl || ''}"></label>
      <p class="note">Recording stays on this device. Download it from the summary screen and listen back to label who each utterance was actually aimed at.</p>
      <hr>
      <p class="note">Fast path handles: numbers, done, next, skip, ready, another minute, rest durations, weights, RPE by feel, what's next, what weight, time left, undo, pause, end. Everything else goes to the model.</p>
      <p class="note" style="margin-top:8px">Storage: ${VOS.Memory.usingLocalStorage ? 'localStorage' : 'in-memory fallback'}.</p>`);
    $('#sRate').oninput = e => { M.settings.rate = +e.target.value; voice.opts.rate = +e.target.value; VOS.Memory.save(); };
    $('#sDuck').oninput = e => { M.settings.duckTo = +e.target.value; voice.opts.duckTo = +e.target.value; VOS.Memory.save(); };
    $('#sLang').onchange = e => { M.settings.lang = e.target.value; voice.opts.lang = e.target.value; VOS.Memory.save(); };
    $('#sHF').onchange = e => { M.settings.handsFree = e.target.checked; VOS.Memory.save(); };
    $('#sGen').onchange = e => { M.settings.speechGen = e.target.checked; if (speech) speech.enabled = e.target.checked; VOS.Memory.save(); };
    $('#sRec').onchange = e => { M.settings.recordAudio = e.target.checked; VOS.Memory.save(); };
    $('#sGate').value = M.settings.gate || 'hybrid';
    $('#sGate').onchange = e => { M.settings.gate = e.target.value; if (gate) gate.setStrategy(e.target.value); VOS.Memory.save(); };
    $('#sBarge').value = M.settings.bargeIn || 'vad';
    $('#sBarge').onchange = e => { M.settings.bargeIn = e.target.value; voice.opts.bargeIn = e.target.value; VOS.Memory.save(); };
    $('#sProxy').onchange = e => { M.settings.proxyUrl = e.target.value.trim(); llm.cfg.proxyUrl = M.settings.proxyUrl; VOS.Memory.save(); };
    $('#sTts').onchange = e => { M.settings.ttsUrl = e.target.value.trim(); voice.useRemoteTTS(M.settings.ttsUrl, M.settings.ttsVoice); VOS.Memory.save(); };
  };

  /* ============================================================
     DOCK
     ============================================================ */
  $('#micBtn').onclick = () => {
    if (voice.wantListening) { voice.stop(); voice.shutUp(); } else { startListening(); }
    paint();
  };
  /* Warm-up controls. Every one of these has a spoken equivalent —
     these exist so the warm-up is not the one part of the session that
     needs a working microphone. */
  $('#wuDone').onclick = () => onUtterance('done', ['done'], 1, { source: 'ui' });
  $('#wuSkip').onclick = () => onUtterance('skip the warm-up', ['skip the warm-up'], 1, { source: 'ui' });
  $('#wuWhy').onclick = () => {
    const it = session && session.warmupItem;
    $('#wuWhyLine').textContent = it ? (it.why || it.how) : '';
  };

  $('#btnPocket').onclick = () => { $('#pocket').classList.remove('hide'); paintPocket(); };
  $('#pocket').onclick = () => $('#pocket').classList.add('hide');
  function sendTyped() {
    const inp = $('#typeIn');
    const t = (inp.value || '').trim();
    if (!t) return;
    inp.value = '';
    onUtterance(t, [t], 1, { source: 'ui' });
  }
  $('#btnType').onclick = () => {
    const bar = $('#composer');
    const open = bar.classList.toggle('hide');
    if (!open) setTimeout(() => $('#typeIn').focus(), 40);
  };
  $('#typeSend').onclick = sendTyped;
  $('#typeIn').onkeydown = e => { if (e.key === 'Enter') { e.preventDefault(); sendTyped(); } };
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-say]');
    if (b) onUtterance(b.dataset.say, [b.dataset.say], 1, { source: 'ui' });
  });

  /* ============================================================
     BOOT
     ============================================================ */
  voice = new VOS.Audio({
    lang: M.settings.lang,
    rate: M.settings.rate,
    duckTo: M.settings.duckTo,
    voiceName: M.settings.voiceName,
    bargeIn: M.settings.bargeIn || 'vad',
    onFinal: onUtterance,
    onPartial: (t, info) => { $('#partial').textContent = info && info.echo ? '' : t; },
    onStatus: () => paint(),
    onError: msg => {
      addMsg('sys', msg);
      if (/permission|denied|no speech recognition/i.test(msg)) showMicHelp(msg);
    },
    onInterrupt: (text, line) => { if (ctrl) ctrl.onInterrupt(text, line); addMsg('sys', 'interrupted'); },
    onMetric: m => { if (field) field.event(m.type, m); }
  });
  if (M.settings.ttsUrl) voice.useRemoteTTS(M.settings.ttsUrl, M.settings.ttsVoice);
  llm = new VOS.LLM({ apiKey: M.settings.apiKey, model: M.settings.model, proxyUrl: M.settings.proxyUrl });

  bodyMap = new VOS.BodyMap(VOS.Memory);
  $('#bmClose').onclick = () =>
    showScreen(session && session.phase !== 'summary' ? 'screenWorkout' : 'screenToday');

  /* A URL you can paste into a browser in a meeting room and be
     certain of what comes up. ?demo seeds, ?demo=new starts clean. */
  const q = (root.location && (root.location.search + root.location.hash)) || '';
  if (/[?&#]demo\b/.test(q)) {
    if (/demo=new/.test(q)) VOS.Demo.fresh();
    else if (!M.history.length) VOS.Demo.seed();
  }

  if (!M.profile.onboarded) {
    onboarding = new VOS.Onboarding(VOS.Memory, (own) => {
      initSetup(); bindToday();
      if (own) { showScreen('screenSetup'); $('#ownProg').open = true; }
      else showScreen('screenToday');
    });
    showScreen('screenOnboard');
    onboarding.start();
  } else {
    initSetup(); bindToday();
    showScreen('screenToday');
  }

  /* The header line earns its space: who this is and what they are on,
     rather than the word "prototype". */
  (function nameHeader() {
    const p = M.profile;
    if (!p.onboarded) return;
    const prog = VOS.Memory.program();
    const bits = [];
    if (p.name) bits.push(p.name);
    if (prog && prog.name) bits.push(prog.name);
    if (bits.length) $('#subline').textContent = bits.join(' · ');
  })();

  function syncTopbar() {
    const h = $('.topbar').getBoundingClientRect().height;
    document.documentElement.style.setProperty('--topbar-h', Math.round(h) + 'px');
  }
  syncTopbar();
  root.addEventListener('resize', syncTopbar);
  root.VOSApp = { showScreen, paintToday, get bodyMap(){return bodyMap;}, get session(){return session;}, get ctrl(){return ctrl;}, get speech(){return speech;}, get field(){return field;}, get dialogue(){return dialogue;}, get gate(){return gate;}, metrics, voice, audio: voice, llm, M, onUtterance, startWorkout, musicControl, demo: VOS.Demo, openDemoSheet };
})(typeof globalThis !== 'undefined' ? globalThis : this);
