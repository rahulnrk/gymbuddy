/* ============================================================
   20-engine.js : deterministic workout engine
   The single source of truth for workout state. The LLM never
   mutates this directly — it can only request allow-listed actions.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});
  const meta = () => VOS.exerciseMeta;

  const now = () => Date.now();
  const round = (n, step) => Math.round(n / step) * step;

  /* ---------- load increment rules (explicit, not LLM-invented) ---------- */
  function loadStep(name, weight) {
    const m = VOS.exerciseMeta(name);
    if (m.load === 'bodyweight') return 0;              // progress via reps
    if (m.load === 'dumbbell')   return weight >= 30 ? 4 : 2;   // per-hand pairs
    if (m.load === 'machine' || m.load === 'cable') return weight >= 60 ? 5 : 2.5;
    return m.kind === 'compound' ? (weight >= 100 ? 5 : 2.5) : 2.5;
  }

  function smallestPlate(name) {
    const m = VOS.exerciseMeta(name);
    if (m.load === 'dumbbell') return 2;
    if (m.load === 'machine' || m.load === 'cable') return 2.5;
    return 1.25;
  }

  /* ============================================================
     PROGRESSION ENGINE
     Compares planned vs actual for one exercise's last session and
     returns an explicit decision + a human-readable reason.
     ============================================================ */
  const Progression = {
    // sets: [{reps, weight, rpe}] from the PREVIOUS session for this exercise
    decide(exName, plan, sets, ctx) {
      ctx = ctx || {};
      const res = { action:'hold', weight: plan.weight, delta:0, reason:'', confidence:'normal' };
      if (!sets || !sets.length) {
        res.reason = 'No previous data — starting at the planned load.';
        res.action = 'start';
        return res;
      }

      const rpes = sets.map(s => (typeof s.rpe === 'number' ? s.rpe : null)).filter(x => x !== null);
      const avgRpe = rpes.length ? rpes.reduce((a,b)=>a+b,0) / rpes.length : null;
      const lastWeight = sets[sets.length - 1].weight;
      const allHitTop  = sets.every(s => s.reps >= plan.repHigh);
      const allHitLow  = sets.every(s => s.reps >= plan.repLow);
      const anyBelow   = sets.some(s => s.reps < plan.repLow);
      const badMiss    = sets.some(s => s.reps < plan.repLow - 2);
      const step       = loadStep(exName, lastWeight);
      const m          = VOS.exerciseMeta(exName);

      res.weight = lastWeight;

      // --- readiness gate: never add load on a clearly bad day ---
      const lowReadiness = (ctx.energy != null && ctx.energy <= 4) || ctx.consecutiveBadSessions >= 2;

      if (allHitTop && (avgRpe === null || avgRpe <= 8.5)) {
        if (lowReadiness) {
          res.action = 'hold';
          res.reason = 'You earned an increase, but you are reporting low readiness today — holding the load.';
          return res;
        }
        if (m.load === 'bodyweight') {
          res.action = 'add_reps';
          res.reason = `You hit the top of the range on every set. Add reps rather than load.`;
          return res;
        }
        res.action = 'increase';
        res.delta  = step;
        res.weight = lastWeight + step;
        res.reason = `All sets hit ${plan.repHigh}${avgRpe !== null ? ` at RPE ${avgRpe.toFixed(1)}` : ''}. Adding ${step} kilos.`;
        return res;
      }

      if (allHitTop && avgRpe !== null && avgRpe > 8.5) {
        res.action = 'hold';
        res.reason = `You hit the reps, but at RPE ${avgRpe.toFixed(1)}. Repeating this load to make it easier before adding.`;
        return res;
      }

      if (allHitLow && !anyBelow) {
        res.action = 'hold';
        res.reason = `You were inside the range but not at the top. Same load, chase ${plan.repHigh}.`;
        return res;
      }

      if (badMiss || (avgRpe !== null && avgRpe >= 9.7 && anyBelow)) {
        if (ctx.consecutiveBadSessions >= 1) {
          const deload = round(lastWeight * 0.9, smallestPlate(exName));
          res.action = 'deload';
          res.delta  = deload - lastWeight;
          res.weight = deload;
          res.reason = `Second session short of the range. Dropping about 10% to ${deload} and rebuilding.`;
          return res;
        }
        res.action = 'hold';
        res.reason = `You came up short last time. Same load — let's own it before moving on.`;
        return res;
      }

      res.action = 'hold';
      res.reason = 'Repeating the load.';
      return res;
    }
  };

  /* ============================================================
     SESSION
     phases: idle | checkin | warmup | ready | working | resting | transition | summary
     ============================================================ */
  function makeSessionExercise(planEx, prevSets, ctx) {
    const m = VOS.exerciseMeta(planEx.name);
    const dec = Progression.decide(planEx.name, planEx, prevSets, ctx);
    return {
      name: planEx.name,
      priority: planEx.priority,
      targetSets: planEx.sets,
      repLow: planEx.repLow,
      repHigh: planEx.repHigh,
      weight: dec.weight,
      plannedWeight: dec.weight,
      restSec: m.rest,
      kind: m.kind,
      technique: m.technique,
      /* A programme sheet's 80 kg squat is a suggestion for someone who
         already squats. For anyone starting out or coming back after a
         long time off, we ask what they are lifting rather than tell them. */
      needsWeight: (!!planEx.needsWeight || !!(ctx && ctx.askLoads)) && !prevSets,
      progression: dec,
      sets: [],          // {reps, weight, rpe, ts}
      status: 'pending', // pending | active | done | skipped
      startedAt: null,
      endedAt: null,
      note: null
    };
  }

  class Session {
    constructor(opts) {
      const { program, dayIndex, history, profile } = opts;
      this.program = program;
      this.dayIndex = dayIndex;
      this.day = program.days[dayIndex];
      this.profile = profile || {};
      this.history = history || [];
      this.phase = 'checkin';
      this.startedAt = null;
      this.endedAt = null;
      this.energy = null;
      this.soreness = null;
      this.endFatigue = null;
      this.timeBudgetMin = null;
      this.efficiencyApplied = false;
      this.cursor = { ex: 0, set: 0 };
      this.restEndsAt = null;
      this.restStartedAt = null;
      this.restPlannedSec = 0;
      this.restKind = 'passive'; // passive | active
      this.awaiting = null;      // null | 'reps' | 'rpe' | 'confirm_end'
      this.events = [];          // audit log
      this.notes = [];
      this.pendingSet = null;
      this.version = 0;          // bumped on every mutation, for stale-action rejection
      this.safety = null;        // set when the user reports pain — see 55-safety.js
      this.warmup = null;        // { items, cursor, startedAt, skipped } — see 19-warmup.js

      const ctx = { energy: null, consecutiveBadSessions: 0, askLoads: !!this.profile.askLoads };
      this.exercises = this.day.exercises
        .filter(px => !(this.profile.dislikes || []).includes(px.name))
        .map(px => makeSessionExercise(px, this.lastSetsFor(px.name), ctx));

      // apply substitutions for disliked movements rather than silently dropping
      (this.day.exercises || []).forEach((px, i) => {
        if ((this.profile.dislikes || []).includes(px.name)) {
          const sub = (VOS.SUBSTITUTES[px.name] || []).find(s => !(this.profile.dislikes || []).includes(s));
          if (sub) {
            const replaced = makeSessionExercise(Object.assign({}, px, { name: sub }), this.lastSetsFor(sub), ctx);
            replaced.note = `Substituted for ${px.name} (you've said you don't like it).`;
            this.exercises.splice(Math.min(i, this.exercises.length), 0, replaced);
          }
        }
      });
    }

    /* ---------- history lookups ---------- */
    lastSetsFor(name) {
      for (let i = this.history.length - 1; i >= 0; i--) {
        const ex = (this.history[i].exercises || []).find(x => x.name === name && x.sets && x.sets.length);
        if (ex) return ex.sets;
      }
      return null;
    }
    lastSessionFor(name) {
      for (let i = this.history.length - 1; i >= 0; i--) {
        const ex = (this.history[i].exercises || []).find(x => x.name === name && x.sets && x.sets.length);
        if (ex) return { session: this.history[i], exercise: ex };
      }
      return null;
    }

    /* ---------- accessors ---------- */
    get current()      { return this.exercises[this.cursor.ex] || null; }
    get setNumber()    { return this.cursor.set + 1; }
    get isLastSet()    { const c = this.current; return c ? this.cursor.set >= c.targetSets - 1 : false; }
    get elapsedSec()   { return this.startedAt ? Math.floor(((this.endedAt || now()) - this.startedAt) / 1000) : 0; }
    get restLeftSec()  { return this.restEndsAt ? Math.max(0, Math.ceil((this.restEndsAt - now()) / 1000)) : 0; }
    get totalVolume()  {
      return this.exercises.reduce((t, ex) =>
        t + ex.sets.reduce((s, st) => s + (st.weight || 0) * st.reps, 0), 0);
    }
    get setsDone()     { return this.exercises.reduce((t, ex) => t + ex.sets.length, 0); }
    get setsPlanned()  { return this.exercises.filter(e=>e.status!=='skipped').reduce((t, ex) => t + ex.targetSets, 0); }

    log(type, data) { this.version++; this.events.push({ t: now(), v: this.version, type, ...(data || {}) }); }

    /* ---------- lifecycle ---------- */
    checkin({ energy, soreness, timeBudgetMin }) {
      if (energy != null) this.energy = energy;
      if (soreness != null) this.soreness = soreness;
      if (timeBudgetMin != null) this.timeBudgetMin = timeBudgetMin;
      if (this.energy != null && this.energy <= 4) this.applyReadinessDamping();
      if (this.timeBudgetMin) this.applyEfficiencyMode(this.timeBudgetMin);
      this.log('checkin', { energy: this.energy, soreness: this.soreness, timeBudgetMin: this.timeBudgetMin });
      return this;
    }

    /* ------------------------------------------------------------
       WARM-UP
       Nothing starts cold. The plan comes from the first thing on
       today's list, so the ramp sets climb to the weight actually
       about to go on the bar.
       ------------------------------------------------------------ */
    planWarmup(opts) {
      opts = opts || {};
      if (!VOS.Warmup) return null;
      const plan = VOS.Warmup.plan({
        exercise: this.current,
        equipment: this.profile.equipment || 'full gym',
        soreGroups: opts.soreGroups || [],
        budgetMin: this.timeBudgetMin
      });
      this.warmup = {
        items: plan.items, cursor: 0, startedAt: null, skipped: false,
        pattern: plan.pattern, forExercise: plan.forExercise, plannedSec: plan.totalSec
      };
      return this.warmup;
    }

    beginWarmup() {
      if (!this.warmup) this.planWarmup();
      this.startedAt = now();
      this.phase = 'warmup';
      if (this.warmup) this.warmup.startedAt = now();
      this.log('warmup_start', { items: this.warmup ? this.warmup.items.length : 0 });
      return this.warmupItem;
    }

    get warmupItem() {
      const w = this.warmup;
      return w && !w.skipped ? (w.items[w.cursor] || null) : null;
    }
    get warmupIndex() { return this.warmup ? this.warmup.cursor : -1; }
    get warmupCount() { return this.warmup ? this.warmup.items.length : 0; }
    get warmupDone()  { return !this.warmup || this.warmup.skipped || this.warmup.cursor >= this.warmup.items.length; }
    get warmupLeftSec() {
      const w = this.warmup;
      if (!w || w.skipped) return 0;
      return w.items.slice(w.cursor).reduce((t, i) => t + (i.seconds || 0), 0);
    }

    /* Move to the next item. Returns the new item, or null when the
       warm-up is finished — the caller decides what to say about that. */
    advanceWarmup() {
      const w = this.warmup;
      if (!w || w.skipped) return null;
      w.items[w.cursor] && (w.items[w.cursor].done = true);
      w.cursor++;
      this.log('warmup_item', { index: w.cursor });
      return this.warmupItem;
    }

    /* Extra work bolted on mid-warm-up, when something is reported sore
       after the plan was already made. */
    addWarmupItem(item) {
      const w = this.warmup;
      if (!w || w.skipped || !item) return null;
      w.items.splice(Math.min(w.cursor + 1, w.items.length), 0, item);
      this.log('warmup_added', { id: item.id });
      return item;
    }

    /* Drop the general work still ahead of you, keep the ramp sets, and
       leave the cursor where it is. Returns how many items went. */
    trimWarmup() {
      const w = this.warmup;
      if (!w || w.skipped) return 0;
      const before = w.items.length;
      w.items = w.items.filter((it, i) => i <= w.cursor || it.ramp || it.forSoreness);
      const dropped = before - w.items.length;
      if (dropped) this.log('warmup_trimmed', { dropped });
      return dropped;
    }

    skipWarmup(reason) {
      if (this.warmup) { this.warmup.skipped = true; this.warmup.skipReason = reason || null; }
      this.log('warmup_skipped', { reason: reason || null });
      return this.start();
    }

    finishWarmup() {
      if (this.warmup) this.warmup.cursor = this.warmup.items.length;
      this.log('warmup_done', { sec: this.warmup && this.warmup.startedAt ? Math.round((now() - this.warmup.startedAt) / 1000) : null });
      return this.start();
    }

    start() {
      if (!this.startedAt) this.startedAt = now();
      this.phase = 'working';
      const c = this.current;
      if (c) { c.status = 'active'; c.startedAt = now(); }
      this.log('workout_start');
      return this;
    }

    /* ---------- adaptation ---------- */
    applyReadinessDamping() {
      // Low readiness: don't add load anywhere, trim priority-3 volume by one set.
      this.exercises.forEach(ex => {
        if (ex.progression.action === 'increase') {
          ex.weight = ex.progression.weight - ex.progression.delta;
          ex.progression = Object.assign({}, ex.progression, {
            action: 'hold', delta: 0, weight: ex.weight,
            reason: 'Low readiness today — holding load instead of increasing.'
          });
        }
        if (ex.priority === 3 && ex.targetSets > 1) ex.targetSets -= 1;
      });
      this.log('readiness_damping');
    }

    estimateMinutes() {
      let sec = 300; // warm-up allowance
      this.exercises.forEach(ex => {
        if (ex.status === 'skipped') return;
        const remaining = Math.max(0, ex.targetSets - ex.sets.length);
        sec += remaining * (40 + ex.restSec) + 60; // set + rest + transition
      });
      return Math.round(sec / 60);
    }

    // Efficiency mode: cut lowest-priority work until the session fits.
    applyEfficiencyMode(budgetMin) {
      this.timeBudgetMin = budgetMin;
      const cuts = [];
      let guard = 0;
      while (this.estimateMinutes() > budgetMin && guard++ < 40) {
        // 1. drop a set from the lowest-priority exercise that still has >1 set
        const p3 = this.exercises.filter(e => e.priority === 3 && e.status === 'pending' && e.targetSets > 1);
        if (p3.length) { const t = p3[p3.length-1]; t.targetSets -= 1; cuts.push(`${t.name}: one set less`); continue; }
        // 2. skip a whole priority-3 exercise
        const p3full = this.exercises.filter(e => e.priority === 3 && e.status === 'pending');
        if (p3full.length) { const t = p3full[p3full.length-1]; t.status = 'skipped'; cuts.push(`${t.name}: cut`); continue; }
        // 3. trim priority-2 sets
        const p2 = this.exercises.filter(e => e.priority === 2 && e.status === 'pending' && e.targetSets > 2);
        if (p2.length) { const t = p2[p2.length-1]; t.targetSets -= 1; cuts.push(`${t.name}: one set less`); continue; }
        // 4. shorten accessory rest (mains keep full rest — that's the point of a main)
        const trimmable = this.exercises.filter(e => e.priority > 1 && e.restSec > 45);
        if (trimmable.length) { trimmable.forEach(e => e.restSec = Math.max(45, e.restSec - 15)); cuts.push('shorter accessory rest'); continue; }
        break;
      }
      this.efficiencyApplied = cuts.length > 0;
      this.lastCuts = cuts;
      this.log('efficiency_mode', { budgetMin, cuts });
      return cuts;
    }

    /* ---------- the core loop ---------- */
    // Returns a structured result the conversation layer turns into speech.
    logSet({ reps, weight, rpe }) {
      const ex = this.current;
      if (!ex) return { ok:false, reason:'no_exercise' };
      const w = (weight != null ? weight : ex.weight);
      const entry = { reps, weight: w, rpe: (rpe != null ? rpe : null), ts: now() };
      ex.sets.push(entry);
      ex.weight = w;
      this.pendingSet = entry;
      this.log('set_logged', { exercise: ex.name, ...entry });

      const analysis = this.analyseSet(ex, entry);
      const needRpe = entry.rpe === null && this.shouldAskRpe(ex, analysis);
      this.awaiting = needRpe ? 'rpe' : null;
      return { ok:true, exercise: ex, set: entry, setNumber: ex.sets.length, analysis, needRpe };
    }

    setRpeOnPending(rpe) {
      if (!this.pendingSet) return { ok:false };
      this.pendingSet.rpe = rpe;
      this.awaiting = null;
      const ex = this.current;
      const analysis = this.analyseSet(ex, this.pendingSet);
      this.log('rpe_logged', { exercise: ex && ex.name, rpe });
      return { ok:true, analysis, exercise: ex, set: this.pendingSet };
    }

    shouldAskRpe(ex, analysis) {
      // Quiet by default: only ask when the answer will actually change a decision.
      if (ex.sets.length === 1) return true;                       // first set sets the tone
      if (ex.sets.length === ex.targetSets) return true;           // last set drives progression
      if (analysis.flag === 'underperform' || analysis.flag === 'overperform') return true;
      return false;
    }

    analyseSet(ex, entry) {
      const prev = this.lastSessionFor(ex.name);
      const prevSet = prev ? prev.exercise.sets[Math.min(ex.sets.length - 1, prev.exercise.sets.length - 1)] : null;
      const out = { flag:'normal', delta:null, prevSet, inRange: entry.reps >= ex.repLow && entry.reps <= ex.repHigh + 3 };
      if (entry.reps >= ex.repHigh) out.flag = 'top_of_range';
      if (entry.reps < ex.repLow) out.flag = 'below_range';
      if (entry.rpe != null && entry.rpe >= 9.5 && entry.reps < ex.repLow) out.flag = 'underperform';
      if (prevSet) {
        out.delta = entry.reps - prevSet.reps;
        const sameLoad = Math.abs((prevSet.weight||0) - (entry.weight||0)) < 0.01;
        if (sameLoad && out.delta <= -3) out.flag = 'underperform';
        if (sameLoad && out.delta >= 3)  out.flag = 'overperform';
      }
      return out;
    }

    startRest(seconds, kind) {
      const ex = this.current;
      const sec = seconds != null ? seconds : (ex ? ex.restSec : 90);
      this.restPlannedSec = sec;
      this.restStartedAt = now();
      this.restEndsAt = now() + sec * 1000;
      this.restKind = kind || 'passive';
      this.phase = 'resting';
      this.log('rest_start', { sec, kind: this.restKind });
      return sec;
    }

    extendRest(seconds) {
      if (!this.restEndsAt) return this.startRest(seconds || 60);
      this.restEndsAt += (seconds || 60) * 1000;
      this.restPlannedSec += (seconds || 60);
      this.log('rest_extend', { seconds });
      return this.restLeftSec;
    }

    endRest() {
      const actual = this.restStartedAt ? Math.round((now() - this.restStartedAt) / 1000) : 0;
      this.restEndsAt = null;
      this.restStartedAt = null;
      this.phase = 'working';
      this.log('rest_end', { actual });
      return actual;
    }

    // Move the cursor forward. Returns {type:'set'|'exercise'|'done', ...}
    advance() {
      const ex = this.current;
      if (!ex) return { type:'done' };
      if (ex.sets.length < ex.targetSets) {
        this.cursor.set = ex.sets.length;
        return { type:'set', exercise: ex, setNumber: this.cursor.set + 1 };
      }
      ex.status = 'done';
      ex.endedAt = now();
      return this.nextExercise();
    }

    nextExercise() {
      let i = this.cursor.ex + 1;
      while (i < this.exercises.length && this.exercises[i].status === 'skipped') i++;
      if (i >= this.exercises.length) { return this.finish(); }
      const prev = this.exercises[this.cursor.ex];
      if (prev && prev.status === 'active') { prev.status = prev.sets.length ? 'done' : 'skipped'; prev.endedAt = now(); }
      this.cursor.ex = i;
      this.cursor.set = 0;
      const ex = this.current;
      ex.status = 'active';
      ex.startedAt = now();
      this.phase = 'transition';
      this.log('exercise_start', { exercise: ex.name });
      return { type:'exercise', exercise: ex };
    }

    skipCurrentExercise(reason) {
      const ex = this.current;
      if (!ex) return { type:'done' };
      ex.status = 'skipped';
      ex.note = reason || 'Skipped by user.';
      this.log('exercise_skipped', { exercise: ex.name, reason });
      return this.nextExercise();
    }

    substituteCurrent(newName) {
      const ex = this.current;
      if (!ex) return null;
      const m = VOS.exerciseMeta(newName);
      const prevSets = this.lastSetsFor(newName);
      const dec = Progression.decide(newName, { weight: ex.weight, repLow: ex.repLow, repHigh: ex.repHigh, sets: ex.targetSets }, prevSets, {});
      ex.name = newName;
      ex.kind = m.kind;
      ex.restSec = m.rest;
      ex.technique = m.technique;
      ex.weight = dec.action === 'start' ? ex.weight : dec.weight;
      ex.progression = dec;
      this.log('substitute', { to: newName });
      return ex;
    }

    setWeight(w) {
      const ex = this.current;
      if (!ex) return null;
      ex.weight = w;
      ex.needsWeight = false;
      this.log('weight_set', { exercise: ex.name, weight: w });
      return ex;
    }

    undoLastSet() {
      for (let i = this.exercises.length - 1; i >= 0; i--) {
        const e = this.exercises[i];
        if (e.sets.length) {
          const removed = e.sets.pop();
          if (this.pendingSet === removed) this.pendingSet = null;
          if (e.status === 'done') { e.status = 'active'; e.endedAt = null; this.cursor.ex = i; }
          this.cursor.set = e.sets.length;
          this.log('undo_set', { exercise: e.name, reps: removed.reps, weight: removed.weight });
          return { exercise: e, set: removed };
        }
      }
      return null;
    }

    correctLast(field, value) {
      for (let i = this.exercises.length - 1; i >= 0; i--) {
        const e = this.exercises[i];
        if (!e.sets.length) continue;
        const set = e.sets[e.sets.length - 1];
        const was = set[field];
        set[field] = value;
        if (field === 'weight') e.weight = value;
        this.log('correct_set', { exercise: e.name, field, was, now: value });
        return { exercise: e, set, field, was };
      }
      return null;
    }

    addNote(text) { this.notes.push({ t: now(), text }); this.log('note', { text }); }

    finish(fatigue) {
      if (fatigue != null) this.endFatigue = fatigue;
      this.endedAt = this.endedAt || now();
      this.phase = 'summary';
      this.restEndsAt = null;
      this.log('workout_end');
      return { type:'done', summary: this.summary() };
    }

    summary() {
      const done = this.exercises.filter(e => e.sets.length);
      const prs = [];
      done.forEach(ex => {
        const best = ex.sets.reduce((b,s)=> (s.weight*s.reps > (b?b.weight*b.reps:0) ? s : b), null);
        const prevBest = (() => {
          let bb = null;
          this.history.forEach(h => (h.exercises||[]).forEach(he => {
            if (he.name !== ex.name) return;
            (he.sets||[]).forEach(s => { if (!bb || s.weight*s.reps > bb.weight*bb.reps) bb = s; });
          }));
          return bb;
        })();
        if (best && (!prevBest || best.weight * best.reps > prevBest.weight * prevBest.reps)) {
          prs.push({ exercise: ex.name, weight: best.weight, reps: best.reps });
        }
      });
      return {
        day: this.day.name,
        program: this.program.name,
        durationSec: this.elapsedSec,
        sets: this.setsDone,
        volume: Math.round(this.totalVolume),
        energy: this.energy,
        endFatigue: this.endFatigue,
        exercises: this.exercises.map(e => ({
          name: e.name, status: e.status, sets: e.sets, note: e.note,
          repLow: e.repLow, repHigh: e.repHigh, targetSets: e.targetSets
        })),
        prs,
        notes: this.notes,
        skipped: this.exercises.filter(e => e.status === 'skipped').map(e => e.name)
      };
    }

    // Compact snapshot handed to the LLM. Small on purpose.
    snapshot() {
      const ex = this.current;
      const prev = ex ? this.lastSessionFor(ex.name) : null;
      return {
        phase: this.phase,
        awaiting: this.awaiting,
        workout: this.day.name,
        program: this.program.name,
        elapsedMin: Math.round(this.elapsedSec / 60),
        timeBudgetMin: this.timeBudgetMin,
        energy: this.energy,
        soreness: this.soreness,
        setsDone: this.setsDone,
        setsPlanned: this.setsPlanned,
        restLeftSec: this.restLeftSec,
        currentExercise: ex ? {
          name: ex.name,
          setNumber: Math.min(ex.sets.length + 1, ex.targetSets),
          targetSets: ex.targetSets,
          targetReps: `${ex.repLow}-${ex.repHigh}`,
          weight: ex.weight,
          technique: ex.technique,
          setsThisSession: ex.sets.map(s => ({ reps:s.reps, kg:s.weight, rpe:s.rpe })),
          progressionReason: ex.progression && ex.progression.reason,
          lastTime: prev ? prev.exercise.sets.map(s => ({ reps:s.reps, kg:s.weight, rpe:s.rpe })) : null
        } : null,
        upcoming: this.exercises.slice(this.cursor.ex + 1).filter(e=>e.status!=='skipped')
          .slice(0,3).map(e => ({ name:e.name, sets:e.targetSets, reps:`${e.repLow}-${e.repHigh}`, kg:e.weight })),
        dislikes: this.profile.dislikes || [],
        goal: this.profile.goal,
        level: this.profile.level
      };
    }
  }

  VOS.Progression = Progression;
  VOS.Session = Session;
  VOS.loadStep = loadStep;
})(typeof globalThis !== 'undefined' ? globalThis : this);
