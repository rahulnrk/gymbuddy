/* ============================================================
   88-screens.js : onboarding and the body map
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});
  const $ = s => document.querySelector(s);
  const el = (t, c, h) => { const n = document.createElement(t); if (c) n.className = c; if (h != null) n.innerHTML = h; return n; };


  /* ============================================================
     ONBOARDING
     Five screens. Each one earns its place by changing something:
     how careful the first sessions are, which programmes are even
     offered, and what the agent calls you. Everything is skippable
     and everything has a defensible default.
     ============================================================ */

  /* One question replaces "how long have you trained" and "how has
     the last month gone" — for the purpose of starting loads they
     were always the same question. */
  const START = {
    new:       { level: 'beginner',     habit: 'restarting', askLoads: true },
    returning: { level: 'beginner',     habit: 'restarting', askLoads: true },
    patchy:    { level: 'intermediate', habit: 'patchy',     askLoads: false },
    regular:   { level: 'intermediate', habit: 'consistent', askLoads: false },
    years:     { level: 'advanced',     habit: 'consistent', askLoads: false }
  };

  function ageFromDob(dob) {
    if (!dob) return null;
    const d = new Date(dob);
    if (isNaN(d)) return null;
    const now = new Date();
    let a = now.getFullYear() - d.getFullYear();
    const m = now.getMonth() - d.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < d.getDate())) a--;
    return a >= 10 && a <= 110 ? a : null;
  }
  function dobBounds() {
    const y = new Date().getFullYear();
    const iso = (yr) => `${yr}-01-01`;
    return { min: iso(y - 100), max: new Date(Date.now() - 13 * 365.25 * 864e5).toISOString().slice(0, 10) };
  }

  const STEPS = [
    {
      id: 'start', kind: 'choice',
      title: 'Where are you starting from?',
      hint: 'This decides how careful the first few weeks are.',
      options: [
        { v: 'new',       l: 'This is my first time', s: "we'll learn the movements before we load them" },
        { v: 'returning', l: "I've trained before, but not for a long time", s: 'months or years off — we rebuild, we do not resume' },
        { v: 'patchy',    l: 'On and off', s: 'some good months, some missed ones' },
        { v: 'regular',   l: 'Training regularly', s: 'a year or so, mostly consistent' },
        { v: 'years',     l: 'Years of it', s: 'you know what a hard set feels like' }
      ]
    },
    {
      id: 'time', kind: 'fields',
      title: 'How much time do you actually have?',
      hint: 'Be realistic. A three-day week you keep beats a five-day week you abandon.',
      fields: [
        { id: 'daysPerWeek', label: 'Days a week', kind: 'chips', dflt: 3,
          options: [{ v: 2, l: '2' }, { v: 3, l: '3' }, { v: 4, l: '4' }, { v: 5, l: '5' }, { v: 6, l: '6' }] },
        { id: 'sessionMin', label: 'Length of a session', kind: 'chips', dflt: 45,
          options: [{ v: 30, l: '30 min' }, { v: 45, l: '45 min' }, { v: 60, l: '1 hour' }, { v: 90, l: '90 min' }] }
      ],
      summary(a) {
        const d = a.daysPerWeek, m = a.sessionMin, tot = d * m;
        const h = Math.floor(tot / 60), r = tot % 60;
        return `${d} sessions of ${m} minutes — about ${h}h${r ? ' ' + r + 'm' : ''} a week.`;
      }
    },
    {
      id: 'equipment', kind: 'choice',
      title: 'What can you actually get to?',
      options: [
        { v: 'none',     l: 'Nothing', s: 'floor space and a doorway' },
        { v: 'dumbbells', l: 'Dumbbells', s: 'a pair or two at home' },
        { v: 'minimal',  l: 'Home gym', s: 'bench, bar, a few plates' },
        { v: 'full gym', l: 'Full gym', s: 'racks, machines, cables' }
      ]
    },
    {
      id: 'goal', kind: 'choice',
      title: 'What are you actually after?',
      options: [
        { v: 'hypertrophy', l: 'Build muscle' },
        { v: 'strength',    l: 'Get stronger' },
        { v: 'fatloss',     l: 'Lose fat' },
        { v: 'general',     l: 'Stay healthy' }
      ]
    },
    {
      id: 'about', kind: 'fields', optional: true,
      title: 'Last one — about you',
      hint: 'All optional. Your date of birth only shapes recovery advice; the figure only changes the drawing.',
      fields: [
        { id: 'name', kind: 'text', label: 'First name', placeholder: 'so I can say it out loud' },
        { id: 'dob',  kind: 'date', label: 'Date of birth' },
        { id: 'sex',  kind: 'chips', label: 'Body map figure', dflt: 'male',
          options: [{ v: 'male', l: 'Male' }, { v: 'female', l: 'Female' }] }
      ],
      summary(a) {
        const age = ageFromDob(a.dob);
        return age ? `${age} years old.` : '';
      }
    }
  ];

  class Onboarding {
    constructor(memory, onDone) {
      this.M = memory; this.onDone = onDone; this.i = 0;
      this.answers = {};
      STEPS.forEach(s => {
        if (s.kind !== 'fields') return;
        const o = this.answers[s.id] = {};
        s.fields.forEach(f => { if (f.dflt !== undefined) o[f.id] = f.dflt; });
      });
    }

    start() {
      $('#screenOnboard').classList.remove('hide');
      document.querySelectorAll('.screen').forEach(s => { if (s.id !== 'screenOnboard') s.classList.add('hide'); });
      this.render();
    }

    render() {
      const step = STEPS[this.i];
      const host = $('#obBody');
      host.innerHTML = '';
      $('#obProgress').style.width = Math.round((this.i / STEPS.length) * 100) + '%';
      $('#obCount').textContent = `${this.i + 1} of ${STEPS.length}`;

      host.appendChild(el('h2', 'obq', step.title));
      if (step.hint) host.appendChild(el('p', 'note', step.hint));

      if (step.kind === 'choice') this.renderChoice(step, host);
      else this.renderFields(step, host);

      $('#obSkip').classList.toggle('hide', !step.optional);
      $('#obSkip').onclick = () => this.next();
      $('#obBack').classList.toggle('hide', this.i === 0);
      $('#obBack').onclick = () => { this.i = Math.max(0, this.i - 1); this.render(); };
    }

    /* One question, one tap, straight on to the next screen. */
    renderChoice(step, host) {
      const wrap = el('div', 'obopts');
      step.options.forEach(o => {
        const b = el('button', 'obopt');
        b.dataset.v = o.v;
        b.appendChild(el('span', 'l', o.l));
        if (o.s) b.appendChild(el('span', 's', o.s));
        b.onclick = () => { this.answers[step.id] = o.v; this.next(); };
        wrap.appendChild(b);
      });
      host.appendChild(wrap);
    }

    /* Several small things that belong to one decision, on one screen. */
    renderFields(step, host) {
      const a = this.answers[step.id];
      const wrap = el('div', 'obfields');
      const sum = el('p', 'obsum');
      const redraw = () => { if (step.summary) sum.textContent = step.summary(a) || ''; };

      step.fields.forEach(f => {
        const row = el('div', 'obfield');
        row.appendChild(el('div', 'oblabel', f.label));

        if (f.kind === 'chips') {
          const chips = el('div', 'obchips');
          f.options.forEach(o => {
            const c = el('button', 'obchip' + (a[f.id] === o.v ? ' on' : ''), o.l);
            c.dataset.f = f.id; c.dataset.v = o.v;
            c.onclick = () => {
              a[f.id] = o.v;
              chips.querySelectorAll('.obchip').forEach(x => x.classList.toggle('on', x === c));
              redraw();
            };
            chips.appendChild(c);
          });
          row.appendChild(chips);
        } else {
          const input = document.createElement('input');
          input.className = 'obinput sm';
          input.id = 'obf_' + f.id;
          if (f.kind === 'date') {
            input.type = 'date';
            const b = dobBounds();
            input.min = b.min; input.max = b.max;
          } else {
            input.type = 'text';
            input.placeholder = f.placeholder || '';
            input.autocomplete = 'off';
          }
          if (a[f.id]) input.value = a[f.id];
          input.oninput = () => { a[f.id] = input.value.trim() || null; redraw(); };
          input.onkeydown = e => { if (e.key === 'Enter') { e.preventDefault(); this.next(); } };
          row.appendChild(input);
        }
        wrap.appendChild(row);
      });

      host.appendChild(wrap);
      host.appendChild(sum);
      redraw();

      const go = el('button', 'btn primary wide', this.i === STEPS.length - 1 ? 'Finish' : 'Continue');
      go.id = 'obNext';
      go.style.marginTop = '16px';
      go.onclick = () => this.next();
      host.appendChild(go);
    }

    next() {
      this.i++;
      if (this.i < STEPS.length) return this.render();
      this.finish();
    }

    finish() {
      const p = this.M.data.profile;
      const a = this.answers;
      const t = a.time || {};
      const me = a.about || {};

      const s = START[a.start];
      if (s) {
        p.level = s.level;
        p.frequencyHabit = s.habit;
        p.startingPoint = a.start;
        /* Someone new, or back after years, is not told to put 80 kg on
           the bar because a programme sheet says so. We ask instead. */
        p.askLoads = s.askLoads;
      }
      if (t.daysPerWeek) p.daysPerWeek = t.daysPerWeek;
      if (t.sessionMin) p.sessionMin = t.sessionMin;
      if (a.equipment) p.equipment = a.equipment;
      if (a.goal) p.goal = a.goal;
      if (me.name) p.name = me.name;
      if (me.sex) p.sex = me.sex;
      if (me.dob) { p.dob = me.dob; p.age = ageFromDob(me.dob); }
      p.onboarded = true;
      this.M.save();
      this.showSuggestions();
    }

    showSuggestions() {
      const host = $('#obBody');
      const p = this.M.data.profile;
      host.innerHTML = '';
      $('#obProgress').style.width = '100%';
      $('#obCount').textContent = 'Programme';
      $('#obSkip').classList.add('hide');
      $('#obBack').classList.add('hide');

      host.appendChild(el('h2', 'obq', p.name ? `Right, ${p.name}. Here's what fits.` : "Here's what fits."));
      host.appendChild(el('p', 'note', 'Ranked against your days, your kit and your goal. You can change this any time, or paste your own programme on the next screen.'));

      if (p.askLoads) {
        host.appendChild(el('p', 'obsum',
          p.startingPoint === 'new'
            ? "I won't hand you numbers on day one. I'll ask what you're lifting, then build from there."
            : "You've done this before, so we start under where you left off and climb back."));
      }

      const ranked = VOS.suggestRoutines(p);
      const list = el('div', 'oblist');
      ranked.slice(0, 5).forEach((r, i) => {
        const c = el('button', 'obcard' + (i === 0 ? ' top' : ''));
        c.appendChild(el('div', 'nm', r.program.name + (i === 0 ? ' <span class="pill good">best fit</span>' : '')));
        c.appendChild(el('div', 'tag', r.program.tagline));
        c.appendChild(el('div', 'meta', `${r.program.weeks} weeks · ${r.why.join(' · ')}`));
        c.onclick = () => {
          this.M.data.profile.programId = r.program.id;
          this.M.data.profile.nextDayIndex = 0;
          this.M.save();
          this.onDone();
        };
        list.appendChild(c);
      });
      host.appendChild(list);

      const skip = el('button', 'btn ghost wide', 'I have my own programme');
      skip.style.marginTop = '12px';
      skip.onclick = () => this.onDone(true);
      host.appendChild(skip);
    }
  }

  VOS.ageFromDob = ageFromDob;
  VOS.ONBOARD_STEPS = STEPS;

  /* ============================================================
     BODY MAP
     ============================================================ */
  class BodyMap {
    constructor(memory) {
      this.M = memory;
      this.back = false;
      this.range = 'recovery';    // recovery | week | session
      this.session = null;
      this.selected = null;
    }

    open(session) {
      this.session = session || null;
      document.querySelectorAll('.screen').forEach(s => s.classList.add('hide'));
      $('#screenBody').classList.remove('hide');
      this.render();
    }

    heat() {
      const hist = this.M.data.history;
      if (this.range === 'recovery') return VOS.Recovery.state(hist, this.session);
      if (this.range === 'session') {
        return this.session ? VOS.Heat.forSession(this.session) : VOS.Heat.forHistory(hist.slice(-1), { decay: false, target: VOS.Heat.SESSION_TARGET_SETS });
      }
      return VOS.Heat.combined(hist, this.session);
    }

    render() {
      const sex = this.M.data.profile.sex === 'female' ? 'female' : 'male';
      const h = this.heat();

      $('#bmFigure').innerHTML = VOS.Figure.svg({ sex, back: this.back });
      $('#bmMini').innerHTML = VOS.Figure.svg({ sex, back: !this.back, idPrefix: 'mini-' });
      VOS.Heat.paint($('#bmFigure'), h);
      VOS.Heat.paint($('#bmMini'), h);

      $('#bmFigure').querySelectorAll('.mz').forEach(z => {
        z.onclick = () => this.select(z.getAttribute('data-m'), h);
      });
      $('#bmMini').onclick = () => { this.back = !this.back; this.render(); };
      $('#bmFlip').onclick = () => { this.back = !this.back; this.render(); };
      $('#bmView').textContent = this.back ? 'Back' : 'Front';

      document.querySelectorAll('#bmRange .chip').forEach(c => {
        c.classList.toggle('on', c.dataset.v === this.range);
        c.onclick = () => { this.range = c.dataset.v; this.selected = null; this.render(); };
      });

      const rec = this.range === 'recovery';
      $('#bmGroupTitle').textContent = rec ? 'How recovered you are' : 'By region';
      $('#bmReadyCard').classList.toggle('hide', !rec);
      $('#bmLegendLo').textContent = rec ? 'recovered' : 'untouched';
      $('#bmLegendHi').textContent = rec ? 'still sore' : 'target met';

      const gw = $('#bmGroups'); gw.innerHTML = '';
      if (rec) {
        const g = VOS.Recovery.byGroup(h);
        VOS.MUSCLE_GROUPS.forEach(k => {
          const row = el('div', 'grow');
          row.appendChild(el('span', 'gn', k));
          const bar = el('span', 'gbar'), fill = el('span', 'gfill');
          fill.style.width = Math.round(g[k].fatigue * 100) + '%';
          fill.style.background = VOS.Heat.colorFor(g[k].fatigue);
          bar.appendChild(fill); row.appendChild(bar);
          row.appendChild(el('span', 'gv', g[k].readyInH > 0 ? 'in ' + g[k].label : 'ready'));
          gw.appendChild(row);
        });
        const sore = VOS.Recovery.stillSore(h);
        $('#bmGaps').innerHTML = sore.length
          ? `<b>${sore.length} region${sore.length > 1 ? 's' : ''} still recovering.</b> The map fades as they come back.`
          : 'Everything has recovered. Nothing is holding you back today.';
        this.renderReady(h);
      } else {
        const g = VOS.Heat.byGroup(h);
        VOS.MUSCLE_GROUPS.forEach(k => {
          const row = el('div', 'grow');
          row.appendChild(el('span', 'gn', k));
          const bar = el('span', 'gbar'), fill = el('span', 'gfill');
          fill.style.width = Math.round(g[k].heat * 100) + '%';
          fill.style.background = VOS.Heat.colorFor(g[k].heat);
          bar.appendChild(fill); row.appendChild(bar);
          row.appendChild(el('span', 'gv', g[k].effSets ? g[k].effSets + ' sets' : '—'));
          gw.appendChild(row);
        });
        const gaps = VOS.Heat.gaps(h, 0.22).slice(0, 6);
        $('#bmGaps').innerHTML = gaps.length
          ? '<b>Barely touched this week:</b> ' + gaps.map(x => x.name).join(', ')
          : 'Every region has had some work this week.';
      }

      this.renderDetail(h);
    }

    /* Who is fresh, and a day-by-day strip for whoever is not. */
    renderReady(h) {
      const ready = VOS.Recovery.readyNow(h);
      const rw = $('#bmReady'); rw.innerHTML = '';
      if (!ready.length) rw.appendChild(el('span', 'note', 'Nothing is fully fresh right now.'));
      ready.slice(0, 12).forEach(r => rw.appendChild(el('span', 'chip ready', r.name)));

      const sore = VOS.Recovery.stillSore(h).slice(0, 8);
      const sw = $('#bmSore'); sw.innerHTML = '';
      if (!sore.length) return;
      sw.appendChild(el('div', 'forecastkey', '<span>today</span><span>tomorrow</span><span>+2d</span><span>+3d</span>'));
      sore.forEach(x => {
        const row = el('div', 'sorerow');
        row.appendChild(el('span', 'nm', x.name));
        const strip = el('span', 'strip');
        VOS.Recovery.forecast(h, x.id, 4).forEach(f => {
          const c = el('span', 'cell');
          c.style.background = f.fatigue > 0.02 ? VOS.Heat.colorFor(f.fatigue) : 'var(--bg3)';
          c.title = f.ready ? 'recovered' : Math.round(f.fatigue * 100) + '% fatigued';
          strip.appendChild(c);
        });
        row.appendChild(strip);
        row.appendChild(el('span', 'eta', VOS.Recovery.fmtH(x.readyInH)));
        sw.appendChild(row);
      });
    }

    select(id, h) {
      this.selected = this.selected === id ? null : id;
      this.renderDetail(h || this.heat());
    }

    renderDetail(h) {
      const box = $('#bmDetail');
      const id = this.selected;
      if (!id || !VOS.MUSCLES[id]) {
        box.innerHTML = '<span class="note">Tap any muscle for its numbers and the movements that hit it.</span>';
        return;
      }
      const m = VOS.MUSCLES[id], d = h[id] || { effSets: 0, volume: 0, heat: 0, top: [] };
      box.innerHTML = '';
      const head = el('div', 'row');
      head.appendChild(el('h3', null, m.name));
      const dot = el('span', 'heatdot'); dot.style.background = VOS.Heat.colorFor(d.heat);
      head.appendChild(dot);
      box.appendChild(head);
      if (this.range === 'recovery') {
        box.appendChild(el('div', 'kv', `<span class="k">State</span><span class="v">${d.status === 'sore' ? 'still sore' : d.status === 'recovering' ? 'coming back' : 'ready'}</span>`));
        box.appendChild(el('div', 'kv', `<span class="k">Fatigue left</span><span class="v">${Math.round((d.fatigue || 0) * 100)}%</span>`));
        box.appendChild(el('div', 'kv', `<span class="k">Ready</span><span class="v">${d.readyInH > 0 ? 'in ' + VOS.Recovery.fmtH(d.readyInH) : 'now'}</span>`));
        box.appendChild(el('div', 'kv', `<span class="k">Last hit</span><span class="v">${VOS.Recovery.fmtAgo(d.lastHitH)}</span>`));
        box.appendChild(el('div', 'kv', `<span class="k">Typical recovery</span><span class="v">${VOS.Recovery.RECOVERY_H[id]}h from a full dose</span>`));
        /* Be honest about where the number came from. An estimate from
           volume and time is not the same thing as the person telling
           you their quads are wrecked. */
        if (d.reported) {
          const words = ['not sore', 'a little sore', 'sore', 'very sore'];
          box.appendChild(el('div', 'note reported',
            `You told me this was <b>${words[d.reported.level] || 'sore'}</b> ${VOS.Recovery.fmtAgo(d.reported.agoH)}` +
            (d.reported.expected ? '.' : ' — which is not where I expected it.')));
        } else {
          box.appendChild(el('div', 'note', 'Estimated from your training. Tell me how it actually feels and I will use that instead.'));
        }
      } else {
        box.appendChild(el('div', 'kv', `<span class="k">Effective sets</span><span class="v">${d.effSets}</span>`));
        box.appendChild(el('div', 'kv', `<span class="k">Volume</span><span class="v">${(d.volume || 0).toLocaleString()} kg</span>`));
        box.appendChild(el('div', 'kv', `<span class="k">Toward the target</span><span class="v">${Math.round(d.heat * 100)}%</span>`));
      }
      if (d.top && d.top.length) {
        box.appendChild(el('div', 'note', '<hr>Hit by: ' + d.top.join(', ')));
      } else {
        const best = VOS.exercisesForMuscle(id, 0.85).slice(0, 4).map(x => x.name);
        box.appendChild(el('div', 'note', '<hr>Nothing yet. Best movements for it: ' + best.join(', ')));
      }
    }
  }

  VOS.Onboarding = Onboarding;
  VOS.BodyMap = BodyMap;
  VOS.ONBOARD_STEPS = STEPS;
})(typeof globalThis !== 'undefined' ? globalThis : this);
