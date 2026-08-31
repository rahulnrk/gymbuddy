/* ============================================================
   58-controller.js : the orchestrator
   Voice in -> gate -> safety -> resolver (or semantic layer)
   -> validator -> queue -> engine -> response -> voice out.
   The engine is the only source of truth; this file is the only
   thing allowed to call it, and it does so one action at a time.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});
  const nowMs = () => (root.performance && performance.now ? performance.now() : Date.now());

  class Controller {
    constructor(deps) {
      this.deps = deps;
      this.session = deps.session;
      this.dialogue = deps.dialogue || new VOS.Dialogue();
      this.audio = deps.audio;
      this.speech = deps.speech || null;
      this.gate = deps.gate || new VOS.Gate({ strategy: 'hybrid' });
      this.field = deps.field || null;
      this.utteranceSeq = 0;
      this.paused = false;
      this.busy = false;
      this.restAnnounced = false;
      this.lastSource = 'template';
      this.pendingPlan = null;
      this.metrics = { turns: 0, executed: 0, confirmed: 0, clarified: 0, semantic: 0, gated: 0, rejected: 0, stage: [] };

      this.queue = new VOS.ActionQueue({
        session: this.session,
        dialogue: this.dialogue,
        apply: (a) => this.applyAction(a),
        onReject: (check, action, meta) => this.onActionRejected(check, action, meta),
        onConfirmNeeded: (prompt, list, meta) => this.askConfirm(prompt, list, meta)
      });
    }

    /* ============================================================
       SPEAKING
       ============================================================ */
    say(text, opts) {
      if (!text) return;
      this.dialogue.addAgent(text, { source: this.lastSource });
      this.deps.log && this.deps.log({ who: 'agent', text, source: this.lastSource });
      this.field && this.field.agent(text, this.lastSource);
      this.lastSource = 'template';
      if (opts && opts.question) this.dialogue.lastQuestionAt = Date.now();
      return this.audio.say(text, opts);
    }

    /* A cached line is only usable if it was written for the state we are
       actually in. Anything safety-related is never cached at all. */
    line(moment, facts, fallback) {
      if (moment && this.speech && !VOS.Safety.active(this.session)) {
        const s = this.speech.line(moment, facts || {}, this.contextKey());
        if (s) { this.lastSource = 'model'; return s; }
      }
      this.lastSource = 'template';
      return typeof fallback === 'function' ? fallback() : fallback;
    }

    /* Coarse on purpose: a fully-written intro is only stale if the
       exercise it names changed, or if we are now in the safety path. */
    contextKey() {
      const ex = this.session.current;
      return [
        this.session.day && this.session.day.name,
        ex && ex.name,
        VOS.Safety.active(this.session) ? 'safety' : 'ok'
      ].join('|');
    }

    announce(ex, opts) {
      const moment = opts && opts.first ? 'workout_intro' : 'exercise_intro';
      let text = this.line(moment, {}, () => VOS.Policy.announceExercise(this.session, ex, opts));
      if (ex && ex.needsWeight && !ex.sets.length) {
        text += ' What are you loading?';
        this.dialogue.expect('weight', { question: 'what load', anchor: null });
      }
      this.prefetch();
      return text;
    }

    prefetch(extra) {
      if (this.speech && !VOS.Safety.active(this.session)) this.speech.prefetch(this.session, extra, this.contextKey());
    }

    /* ============================================================
       LIFECYCLE
       ============================================================ */
    async beginWorkout() {
      const s = this.session;

      /* Nothing starts cold. The warm-up is planned from the first
         lift on today's list and begins immediately — the check-in
         happens during it, not as a gate in front of it. */
      this.checkin = new VOS.CheckIn({ session: s, memory: this.deps.memoryStore || VOS.Memory });

      /* deps.warmup === false exists for tests that exercise the working
         phase directly. The app never passes it — a session that starts
         cold is not a session this thing is willing to run. */
      if (this.deps.warmup === false) return this.startWorkingPhase({ first: true });

      s.planWarmup({ soreGroups: this.checkin.soreGroups() });
      s.beginWarmup();

      const item = s.warmupItem;
      let intro = s.efficiencyApplied
        ? `You've got ${s.timeBudgetMin} minutes, so I've trimmed this to the work that matters. ` : '';
      const mins = Math.max(1, Math.round(s.warmupLeftSec / 60));
      this.say(`${intro}${s.day.name}. We warm up first — about ${mins} minutes. ${this.warmupLine(item, { first: true })}`);
      this.askCheckIn(0);
      this.deps.onState && this.deps.onState();
    }

    /* ============================================================
       WARM-UP
       ============================================================ */
    warmupLine(item, opts) {
      if (!item) return '';
      const lead = opts && opts.first ? 'Start with' : 'Next';
      if (item.ramp) {
        return `${lead}: ${item.name}, ${item.how}`;
      }
      return `${lead}: ${item.name}. ${item.how}`;
    }

    /* The questions live on the warm-up items so they arrive while the
       person is already moving, one at a time, never two at once. */
    askCheckIn(itemIndex) {
      if (!this.checkin) return;
      const q = this.checkin.dueFor(itemIndex);
      if (!q) return;
      // let the instruction land before the question arrives
      setTimeout(() => {
        if (this.session.phase !== 'warmup') return;
        this.say(q.text, { question: true });
        this.dialogue.expect(q.expect === 'energy' ? 'energy' : 'checkin',
                             { question: q.text, checkin: q.id, ttlMs: 240000 });
      }, this.questionDelay());
    }

    /* How long to let an instruction land before the question arrives.
       Long enough to not talk over itself; shorter when a demo is
       driving, because nobody wants to watch a pause. */
    questionDelay() {
      const d = this.deps.questionDelayMs;
      const v = typeof d === 'function' ? d() : d;
      return v != null ? v : 3500;
    }

    /* Everything said during the warm-up. Returns null to fall through
       to the ordinary path. */
    async warmupTurn(text, alts, conf, id) {
      const s = this.session;
      const R = (extra) => Object.assign({ route: 'fast', ok: true, id }, extra || {});
      const parsed = VOS.Intent.parse(text, { awaiting: this.dialogue.topKind });

      /* an answer to whatever we just asked */
      if (this.checkin && this.checkin.pending) {
        const q = this.checkin.pending;
        const a = this.checkin.answer(text, parsed);
        if (a && a.understood) {
          this.dialogue.clear && this.dialogue.clear('energy');
          this.dialogue.clear && this.dialogue.clear('checkin');
          if (a.say) this.say(a.say);
          this.applyCheckIn();
          return R({ intent: 'checkin_answer', checkin: q.id });
        }
        // not an answer — leave the question open and carry on
        this.checkin.pending = q;
      }

      switch (parsed.intent) {
        case 'warmup_skip':
          await this.skipWarmupNow();
          return R({ intent: 'warmup_skip' });
        case 'warmup_left': {
          const mins = Math.max(1, Math.round(s.warmupLeftSec / 60));
          const left = s.warmupCount - s.warmupIndex;
          this.say(`${left} to go, about ${mins} minute${mins === 1 ? '' : 's'}.`);
          return R({ intent: 'warmup_left' });
        }
        case 'warmup_why': {
          const it = s.warmupItem;
          this.say(it && it.why ? it.why : (it ? it.how : 'Almost through it.'));
          return R({ intent: 'warmup_why' });
        }
        case 'done_set':
        case 'ready':
          this.nextWarmupItem();
          return R({ intent: 'warmup_next' });
        case 'skip_exercise':
          this.nextWarmupItem({ skipped: true });
          return R({ intent: 'warmup_next' });
        case 'energy':
          s.checkin({ energy: parsed.energy });
          this.say(this.checkin ? this.checkin.energyReply(parsed.energy) : 'Noted.');
          return R({ intent: 'energy' });
        case 'time_budget': {
          s.applyEfficiencyMode(parsed.minutes);
          /* Trim what is left of the warm-up rather than re-planning it,
             which would send them back to the first drill. The ramp sets
             stay — those are the ones protecting the first working set. */
          const dropped = s.trimWarmup();
          this.say(`${parsed.minutes} minutes. ` +
            (dropped ? `I've cut ${dropped} drill${dropped === 1 ? '' : 's'} from the warm-up and trimmed the session to match. ` : "I've trimmed the session to match. ") +
            (s.warmupItem ? this.warmupLine(s.warmupItem) : ''));
          this.deps.onState && this.deps.onState();
          return R({ intent: 'time_budget' });
        }
        case 'reps':
          /* A number during a ramp set means "that's done" — it is not a
             working set and does not belong in the log. */
          if (s.warmupItem && s.warmupItem.ramp) {
            this.nextWarmupItem();
            return R({ intent: 'warmup_next' });
          }
          break;
      }

      /* Soreness volunteered without being asked. */
      if (VOS.Soreness && /\b(?:sore|stiff|tight|aching|tender|wrecked|doms)\b/.test(text.toLowerCase())) {
        const r = VOS.Soreness.parse(text);
        if (r.mentioned) {
          this.checkin.pending = { id: 'soreness', expect: 'soreness' };
          const a = this.checkin.answer(text, parsed);
          if (a && a.say) this.say(a.say);
          this.applyCheckIn();
          return R({ intent: 'soreness_report' });
        }
      }

      return null;
    }

    /* Something reported sore mid-warm-up earns extra work for it. */
    applyCheckIn() {
      const s = this.session;
      if (!this.checkin || !this.checkin.protect) return;
      const group = (VOS.MUSCLES[this.checkin.protect] || {}).group;
      const extra = group && VOS.Warmup.SORE_EXTRA[group];
      if (extra && !(s.warmup && s.warmup.items.some(i => i.id === 'sore_' + group))) {
        s.addWarmupItem(Object.assign({ id: 'sore_' + group, stage: 'mobilise', seconds: 90, forSoreness: group }, extra));
      }
    }

    /* You can skip it. It will not pretend that was nothing. */
    async skipWarmupNow() {
      const s = this.session;
      const left = s.warmupCount - s.warmupIndex;
      s.skipWarmup('user asked');
      s.addNote(`Warm-up skipped with ${left} item${left === 1 ? '' : 's'} left.`);
      const ex = s.current;
      return this.startWorkingPhase({
        lead: `Skipping it. Your first set of ${ex ? ex.name : 'the session'} is now the warm-up, so keep something in reserve. `
      });
    }

    nextWarmupItem(opts) {
      const s = this.session;
      const next = s.advanceWarmup();
      if (!next) return this.endWarmup();
      this.say(this.warmupLine(next));
      this.askCheckIn(s.warmupIndex);
      this.deps.onState && this.deps.onState();
      return next;
    }

    async endWarmup() {
      this.session.finishWarmup();
      return this.startWorkingPhase({ lead: 'Warm-up done. ' });
    }

    /* The one place the working phase is announced, so the warm-up path,
       the skip path and the test path all say it the same way. */
    async startWorkingPhase(opts) {
      const s = this.session;
      opts = opts || {};
      if (s.phase !== 'working') s.start();
      const ex = s.current;
      if (ex) { ex.status = 'active'; ex.startedAt = Date.now(); }
      const intro = opts.first && s.efficiencyApplied
        ? `You've got ${s.timeBudgetMin} minutes, so I've trimmed this to the work that matters. ` : '';
      if (this.speech) { try { await this.speech.ensure(s, 'workout_intro', this.contextKey()); } catch (e) {} }
      this.say((opts.lead || '') + intro + this.announce(ex, { first: true }),
               { question: !!(ex && ex.needsWeight) });
      if (ex && !ex.needsWeight) this.dialogue.expect('reps', { ttlMs: 300000 });
      this.deps.onState && this.deps.onState();
    }

    tick() {
      const s = this.session;
      if (!s || this.paused) return;
      if (s.phase === 'resting' && s.restLeftSec <= 0 && !this.restAnnounced) {
        this.restAnnounced = true;
        s.endRest();
        const out = VOS.Policy.restOver(s);
        if (out.speak) {
          this.say(this.line(out.moment, out.facts, out.text));
          this.dialogue.expect('reps', { anchor: null, ttlMs: 120000 });
        }
        this.deps.onState && this.deps.onState();
      }
    }

    /* ============================================================
       INTERRUPTION
       ============================================================ */
    onInterrupt(text, line) {
      this.dialogue.interruptions++;
      this.field && this.field.event('barge_in', { spokenClauses: line && line.spoken.length, cut: line && line.remaining });
      // whatever we were mid-way through saying is no longer the last thing said
      if (line && line.remaining) this.dialogue.lastAgentText = line.spoken.join(' ');
    }

    /* ============================================================
       MAIN ENTRY
       ============================================================ */
    async onUtterance(text, alts, asrConfidence, opts) {
      const s = this.session;
      if (!s) return { route: 'none' };
      const t0 = nowMs();
      const id = 'u' + (++this.utteranceSeq);
      this.metrics.turns++;

      /* 1. is this even for us?
         A button press is explicit intent — it never goes through the
         addressee gate, because the user literally pointed at it. */
      const gated = opts && opts.source === 'ui'
        ? { accept: true, text, reason: 'tapped', confidence: 1 }
        : this.gate.check({ text, dialogue: this.dialogue, session: s });
      if (!gated.accept) {
        this.metrics.gated++;
        this.field && this.field.utterance({ text, alts, phase: s.phase, exercise: s.current && s.current.name,
          awaiting: this.dialogue.topKind, intent: null, route: 'gated', matched: false, gate: gated });
        this.deps.log && this.deps.log({ who: 'user', text, gated: gated.reason });
        return { route: 'gated', reason: gated.reason, id };
      }
      const clean = gated.text || text;
      this.dialogue.addUser(clean);
      this.deps.log && this.deps.log({ who: 'user', text: clean });

      /* 2. pain overrides everything, including anything queued */
      const pain = VOS.Safety.detect(clean);
      if (pain) return this.enterSafety(pain, id, t0, alts);

      /* 3. the warm-up has its own vocabulary — a number here is an
         energy score, "done" moves to the next drill, and the check-in
         question we asked is answered before anything else. */
      if (s.phase === 'warmup') {
        const w = await this.warmupTurn(clean, alts, asrConfidence, id);
        if (w) { this.logTurn(id, clean, alts, w, t0); this.deps.onState && this.deps.onState(); return w; }
      }

      /* 4. if we asked something, answer that first */
      if (this.dialogue.pending) {
        const handled = await this.handlePending(clean, alts, asrConfidence, id);
        if (handled) { this.logTurn(id, clean, alts, handled, t0); return handled; }
      }

      /* 4. resolve against context */
      const res = VOS.Resolver.resolve({ text: clean, alts, confidence: asrConfidence, dialogue: this.dialogue, session: s });
      let out;
      if (res.tier === 'semantic') out = await this.semantic(clean, id);
      else if (res.tier === 'clarify') out = this.askClarify(res, id);
      else if (res.tier === 'confirm') out = this.askConfirm(this.confirmPrompt(res), this.planToActions(res.plan), { utteranceId: id, plan: res.plan });
      else out = await this.executePlan(res.plan, { utteranceId: id, stateVersion: s.version });

      out.resolved = res;
      out.ms = Math.round(nowMs() - t0);
      this.logTurn(id, clean, alts, out, t0);
      this.deps.onState && this.deps.onState();
      return out;
    }

    logTurn(id, text, alts, out, t0) {
      const ms = Math.round(nowMs() - t0);
      this.metrics.stage.push(ms);
      const s = this.session;
      this.field && this.field.utterance({
        text, alts, phase: s.phase, exercise: s.current && s.current.name,
        awaiting: this.dialogue.topKind,
        intent: (out.resolved && out.resolved.plan[0] && out.resolved.plan[0].intent) || out.intent || null,
        route: out.route, matched: out.route !== 'gated' && out.route !== 'reject', ms,
        confidence: out.resolved && out.resolved.plan[0] && out.resolved.plan[0].confidence,
        tier: out.resolved && out.resolved.tier
      });
    }

    /* ============================================================
       SAFETY
       ============================================================ */
    enterSafety(pain, id, t0, alts) {
      const s = this.session;
      this.audio.stopSpeaking('safety');
      if (this.speech) this.speech.invalidateAll();     // every cached line predates this
      VOS.Safety.enter(s, pain);
      const opts = VOS.Safety.options(s);
      let prompt = VOS.Safety.prompt(s);
      if (opts.substitute) prompt = prompt.replace('swap it for something', `swap it for ${opts.substitute}`);
      this.lastSource = 'safety';
      this.say(prompt, { question: true });
      this.dialogue.ask('clarify', { options: ['skip', 'substitute', 'end', 'continue'], safety: true, substitute: opts.substitute },
                        prompt, id);
      this.metrics.clarified++;
      const out = { route: 'safety', pain, options: opts, id };
      this.logTurn(id, pain.text, alts, out, t0);
      this.deps.onState && this.deps.onState();
      return out;
    }

    async resolveSafetyChoice(text, id) {
      const s = this.session;
      const p = this.dialogue.pending;
      const t = text.toLowerCase();
      const sub = p && p.substitute;
      this.dialogue.resolvePending();

      if (/\b(?:end|stop|done|finish|call it|go home)\b/.test(t)) {
        VOS.Safety.exit(s, 'ended session');
        this.lastSource = 'safety';
        this.say('Right, we stop here. Get it looked at if it does not settle.');
        await this.executePlan([{ intent: 'end_workout', confidence: 1 }], { utteranceId: id, confirmed: true });
        return { route: 'safety_resolved', how: 'end' };
      }
      if (sub && /\b(?:swap|substitute|change|different|alternative|other|yes|yeah|ok|sure|that)\b/.test(t)) {
        VOS.Safety.exit(s, 'substituted');
        return this.executePlan([{ intent: 'substitute_exercise', args: { name: sub, reason: 'pain' }, confidence: 1 }],
                                { utteranceId: id, confirmed: true });
      }
      if (/\b(?:skip|move on|next|leave it|drop it)\b/.test(t)) {
        VOS.Safety.exit(s, 'skipped');
        return this.executePlan([{ intent: 'skip_exercise', args: { reason: 'pain' }, confidence: 1 }],
                                { utteranceId: id, confirmed: true });
      }
      if (/\b(?:carry on|keep going|continue|it'?s fine|i'?m fine|no it'?s ok|push through)\b/.test(t)) {
        VOS.Safety.exit(s, 'user continued');
        this.lastSource = 'safety';
        this.say("Your call. Keep it well within what feels safe, and stop if it sharpens.");
        return { route: 'safety_resolved', how: 'continue' };
      }
      return null;   // not an answer to the safety question — fall through
    }

    /* ============================================================
       PENDING CONFIRM / CLARIFY
       ============================================================ */
    async handlePending(text, alts, asrConf, id) {
      const p = this.dialogue.pending;
      if (!p) return null;

      if (p.safety) {
        const r = await this.resolveSafetyChoice(text, id);
        if (r) return r;
        this.dialogue.ask('clarify', p, p.prompt, id);   // re-arm, they said something else
        return null;
      }

      const yes = /^(?:yes|yeah|yep|yup|sure|correct|right|do it|go on|ok|okay|please|that'?s right|aye)\b/i.test(text.trim());
      const no  = /^(?:no|nope|nah|don'?t|cancel|forget it|never ?mind|leave it)\b/i.test(text.trim());

      if (p.kind === 'confirm') {
        this.dialogue.resolvePending();
        if (yes) {
          this.metrics.confirmed++;
          return this.executePlan(p.plan, { utteranceId: id, confirmed: true, stateVersion: this.session.version });
        }
        if (no) { this.say('Forget it then.'); return { route: 'cancelled', id }; }
        return null;   // they said something else entirely; treat it fresh
      }

      if (p.kind === 'clarify') {
        // a clarification is answered by resolving the reply against the
        // options we offered, not by guessing again
        const pick = this.matchOption(text, p.options, alts);
        this.dialogue.resolvePending();
        if (pick) {
          this.metrics.clarified++;
          return this.executePlan([pick], { utteranceId: id, confirmed: true, stateVersion: this.session.version });
        }
        return null;
      }
      return null;
    }

    matchOption(text, options, alts) {
      if (!options || !options.length) return null;
      const t = text.toLowerCase();
      // "reps" / "rpe" / "kilos" style disambiguation
      for (const o of options) {
        if (!o || !o.intent) continue;
        const label = o.label || o.intent;
        if (new RegExp('\\b' + String(label).replace(/[^a-z0-9 ]/gi, '') + '\\b', 'i').test(t)) return o;
      }
      if (/\breps?\b/.test(t))   return options.find(o => o.intent === 'reps');
      if (/\brpe\b|\bhard\b|\beffort\b/.test(t)) return options.find(o => o.intent === 'rpe');
      if (/\bkilos?\b|\bkg\b|\bweight\b/.test(t)) return options.find(o => o.intent === 'set_weight');
      if (/\bfirst\b|\bformer\b/.test(t))  return options[0];
      if (/\bsecond\b|\blatter\b/.test(t)) return options[1];
      // a bare number re-answers the same question
      const n = VOS.Numbers.candidates(t);
      if (n.length === 1 && options.length) {
        const o = Object.assign({}, options[0]);
        o.args = Object.assign({}, o.args);
        ['reps','rpe','weight','seconds'].forEach(k => { if (o.args[k] != null) o.args[k] = n[0].value; });
        return o;
      }
      return null;
    }

    confirmPrompt(res) {
      const c = res.plan[0];
      const a = c.args || {};
      switch (c.intent) {
        case 'reps':        return `${a.reps} reps?`;
        case 'rpe':         return `RPE ${a.rpe}?`;
        case 'set_weight':  return `${a.weight} kilos?`;
        case 'skip_exercise': return `Skip ${this.session.current ? this.session.current.name : 'this'}?`;
        case 'end_workout': return 'End the workout here?';
        case 'correct_last':return `Change it to ${a.value}?`;
        default:            return `${String(c.intent).replace(/_/g, ' ')}?`;
      }
    }

    askConfirm(prompt, plan, meta) {
      this.lastSource = 'template';
      this.say(prompt, { question: true });
      this.dialogue.ask('confirm', { plan: Array.isArray(plan) ? plan : [plan] }, prompt, meta && meta.utteranceId);
      this.metrics.confirmed++;
      return { route: 'confirm', prompt, id: meta && meta.utteranceId };
    }

    askClarify(res, id) {
      const c = res.plan[0];
      const opts = [];
      let prompt;
      if (c && c.numeric) {
        const v = c.numeric.value;
        const kinds = [c.numeric.kind].concat((c.alternatives || []).map(a => a.kind)).filter((k, i, arr) => arr.indexOf(k) === i).slice(0, 2);
        const label = k => k === 'reps' ? 'reps' : k === 'rpe' ? 'RPE' : k === 'weight' ? 'kilos' : k;
        prompt = kinds.length > 1 ? `${v} ${label(kinds[0])} or ${v} ${label(kinds[1])}?` : `${v} ${label(kinds[0])}?`;
        kinds.forEach(k => opts.push({
          intent: k === 'weight' ? 'set_weight' : k, label: label(k), confidence: 1,
          args: { reps: v, rpe: v, weight: v, energy: v, seconds: v }
        }));
      } else {
        prompt = "Sorry, say that again?";
      }
      this.lastSource = 'template';
      this.say(prompt, { question: true });
      this.dialogue.ask('clarify', { options: opts }, prompt, id);
      this.metrics.clarified++;
      return { route: 'clarify', prompt, options: opts, id };
    }

    /* ============================================================
       SEMANTIC LAYER — proposes, never disposes
       ============================================================ */
    async semantic(text, id) {
      this.metrics.semantic++;
      if (!this.deps.llm) {
        this.say("I didn't catch that.");
        return { route: 'reject', id };
      }
      this.busy = true;
      this.deps.onState && this.deps.onState();
      const t0 = nowMs();
      try {
        const reply = await this.deps.llm(this.snapshotForLLM(), text, this.deps.memory ? this.deps.memory() : null);
        const ms = Math.round(nowMs() - t0);

        if (reply && reply.pain) {
          const p = { site: reply.painSite || null, severity: reply.painSevere ? 'sharp' : 'ache', text, confidence: 0.9 };
          return this.enterSafety(p, id, nowMs(), null);
        }
        if (reply && reply.say) { this.lastSource = 'model'; this.say(reply.say, { question: /\?\s*$/.test(reply.say) }); }

        const proposals = (reply && (reply.intents || (reply.action ? [this.legacyAction(reply.action)] : []))) || [];
        const plan = proposals.filter(Boolean).map(p => ({
          intent: p.intent || p.type, args: p, confidence: typeof p.confidence === 'number' ? p.confidence : 0.75, source: 'semantic'
        })).filter(p => p.intent && p.intent !== 'none');

        if (!plan.length) { this.busy = false; return { route: 'llm', ms, id, spoke: true }; }

        // the model's proposals go through exactly the same gauntlet
        const tiers = plan.map(p => VOS.Resolver.tierFor(p.intent, p.confidence));
        if (tiers.includes('clarify')) { this.busy = false; return { route: 'llm', ms, id, dropped: true }; }
        if (tiers.includes('confirm')) {
          this.busy = false;
          return this.askConfirm(this.confirmPrompt({ plan }), this.planToActions(plan), { utteranceId: id, plan });
        }
        const r = await this.executePlan(plan, { utteranceId: id, stateVersion: this.session.version, quiet: !!(reply && reply.say) });
        this.busy = false;
        return Object.assign({}, r, { route: 'llm', ms, id });
      } catch (err) {
        this.busy = false;
        // the workout must survive the model being unreachable
        this.say("Lost the connection for a second. Numbers and rest still work — what did you get?");
        return { route: 'llm_error', error: String(err), id };
      } finally {
        this.busy = false;
        this.deps.onState && this.deps.onState();
      }
    }

    legacyAction(a) { return Object.assign({ intent: a.type, confidence: 0.75 }, a); }

    snapshotForLLM() {
      const snap = this.session.snapshot();
      snap.dialogue = this.dialogue.snapshot();
      snap.safety = this.session.safety || null;
      return snap;
    }

    /* ============================================================
       PLAN -> ACTIONS -> ENGINE
       ============================================================ */
    planToActions(plan) {
      const out = [];
      plan.forEach(c => {
        // a confirmation stores actions, not clauses — pass those straight through
        if (c && c.type && !c.intent) { out.push(c); return; }
        const a = c.args || {};
        switch (c.intent) {
          case 'reps':        out.push({ type: 'log_set', reps: a.reps }); break;
          case 'rpe':         out.push({ type: 'set_rpe', rpe: a.rpe }); break;
          case 'set_weight':  out.push({ type: 'set_weight', weight: a.weight }); break;
          case 'correct_last':out.push({ type: 'correct_last', field: a.field, value: a.value }); break;
          case 'undo':        out.push({ type: 'undo' }); break;
          case 'extend_rest': out.push({ type: 'extend_rest', seconds: a.seconds || 60 }); break;
          case 'set_rest':    out.push({ type: this.session.phase === 'resting' ? 'extend_rest' : 'start_rest', seconds: a.seconds, absolute: true }); break;
          case 'skip_exercise': out.push({ type: 'skip_exercise', reason: a.reason || 'user asked' }); break;
          case 'next_exercise': out.push({ type: 'next_exercise' }); break;
          case 'substitute_exercise': out.push({ type: 'substitute_exercise', name: a.name, reason: a.reason }); break;
          case 'reduce_volume': out.push({ type: 'reduce_volume' }); break;
          case 'time_budget': out.push({ type: 'set_time_budget', minutes: a.minutes }); break;
          case 'end_workout': out.push({ type: 'end_workout' }); break;
          case 'energy':      out.push({ type: 'add_note', text: `Energy ${a.energy}/10` , energy: a.energy }); break;
          default: break;   // read-only intents are answered, not applied
        }
      });
      return out;
    }

    async executePlan(plan, meta) {
      const s = this.session;
      const answered = [];
      const speakOnly = [];

      // read-only intents never touch the queue
      plan.forEach(c => {
        const ans = VOS.Policy.answer(s, { intent: c.intent });
        if (ans) { answered.push(ans); return; }
        if (['interrupt','ready','done_set','repeat','pause','resume','affirm','deny',
             'music_next','music_prev','music_pause','music_resume','music_shuffle','music_volume',
             'agent_volume'].includes(c.intent)) speakOnly.push(c);
      });

      if (answered.length) { this.say(answered.join(' ')); }
      speakOnly.forEach(c => this.handleSideEffect(c));

      const actions = this.planToActions(plan);
      if (!actions.length) {
        this.metrics.executed += (answered.length + speakOnly.length) ? 1 : 0;
        return { route: 'fast', applied: [], answered: answered.length, id: meta && meta.utteranceId };
      }

      const result = await this.queue.submit(actions, meta);
      if (result.ok) {
        this.metrics.executed++;
        if (!meta.quiet) this.react(actions, plan);
      } else if (result.code === 'needs_confirm') {
        return { route: 'confirm', prompt: result.prompt, id: meta && meta.utteranceId };
      }
      return Object.assign({}, result, { route: 'fast', id: meta && meta.utteranceId });
    }

    /* Non-state side effects — music, transport, meta commands. */
    handleSideEffect(c) {
      const s = this.session, P = VOS.Policy;
      switch (c.intent) {
        case 'interrupt':
          this.audio.stopSpeaking('user');
          this.say('Go on.');
          break;
        case 'repeat':
          this.audio.say(this.dialogue.lastAgentText || 'Nothing to repeat.');
          break;
        case 'pause':
          this.paused = true; s.phase = 'paused'; this.say('Paused.');
          break;
        case 'resume':
          this.paused = false; s.phase = 'working'; this.say('Back on.');
          break;
        case 'ready':
        case 'done_set':
          this.readyNow();
          break;
        case 'agent_volume':
          this.deps.agentVolume && this.deps.agentVolume(c.args && c.args.dir);
          this.say((c.args && c.args.dir) > 0 ? 'Speaking up.' : 'Quieter.');
          break;
        default:
          if (/^music_/.test(c.intent)) {
            const handled = this.deps.music ? this.deps.music(c.intent.replace('music_', ''), c.args && c.args.dir) : false;
            s.log('music_request', { what: c.intent, handled });
            if (!handled) this.say("I can't reach your music app — that one's still on your phone.");
          }
      }
    }

    /* ============================================================
       THE ONLY WRITER
       ============================================================ */
    applyAction(a) {
      const s = this.session;
      switch (a.type) {
        case 'log_set': {
          if (s.phase === 'resting') s.endRest();
          this._lastSetResult = s.logSet({ reps: a.reps, weight: a.weight, rpe: a.rpe });
          break;
        }
        case 'set_rpe':      this._lastRpe = s.setRpeOnPending(a.rpe); break;
        case 'set_weight':   s.setWeight(a.weight); break;
        case 'correct_last': this._lastCorrection = s.correctLast(a.field, a.value); break;
        case 'undo':         this._lastUndo = s.undoLastSet(); break;
        case 'start_rest':   s.startRest(a.seconds); this.restAnnounced = false; break;
        case 'extend_rest':
          if (a.absolute && s.restStartedAt) { s.restEndsAt = s.restStartedAt + a.seconds * 1000; s.restPlannedSec = a.seconds; }
          else s.extendRest(a.seconds);
          this.restAnnounced = false;
          break;
        case 'end_rest':     s.endRest(); break;
        case 'skip_exercise':this._lastNav = s.skipCurrentExercise(a.reason); break;
        case 'next_exercise':this._lastNav = s.nextExercise(); break;
        case 'substitute_exercise': this._lastSub = s.substituteCurrent(a.name); break;
        case 'reduce_volume':
          s.exercises.filter(e => e.priority === 3 && e.status === 'pending')
                     .forEach(e => { if (e.targetSets > 1) e.targetSets -= 1; });
          s.log('reduce_volume');
          break;
        case 'set_time_budget': this._lastCuts = s.applyEfficiencyMode(a.minutes); break;
        case 'add_note':     if (a.energy != null) s.energy = a.energy; if (a.text) s.addNote(a.text); break;
        case 'end_workout':  this._lastFinish = s.finish(); break;
        case 'warmup_skip':  s.skipWarmup('user asked'); break;
        case 'warmup_next':  s.advanceWarmup(); break;
        default: break;
      }
      // any state change invalidates speech written for the old state
      if (this.speech) this.speech.touch(this.contextKey());
    }

    /* ============================================================
       RESPONSES
       ============================================================ */
    react(actions, plan) {
      const s = this.session, P = VOS.Policy;
      const types = actions.map(a => a.type);

      if (types.includes('log_set') && this._lastSetResult && this._lastSetResult.ok) {
        const res = this._lastSetResult; this._lastSetResult = null;
        const out = P.afterSet(s, res);
        const said = this.line(out.moment, out.facts, out.text);
        if (out.expect === 'rpe') {
          this.say(said, { question: true });
          this.dialogue.expect('rpe', { question: said, anchor: 8 });
          this.prefetch();
        } else {
          this.say(said);
          this.dialogue.clear('reps');
          this.afterSetComplete();
        }
      }

      if (types.includes('set_rpe') && this._lastRpe) {
        const r = this._lastRpe; this._lastRpe = null;
        this.dialogue.clear('rpe');
        const out = P.afterRpe(s, r.set.rpe, s.current);
        if (out.speak) {
          this.say(this.line(out.moment, out.facts, out.text), { question: !!out.expect });
          if (out.expect === 'confirm_bump') {
            this.dialogue.ask('confirm', {
              plan: [{ type: 'set_weight', weight: s.current.weight + VOS.loadStep(s.current.name, s.current.weight) }]
            }, out.text, 'rpe-bump');
            return;
          }
        }
        this.afterSetComplete();
      }

      if (types.includes('set_weight') && !types.includes('log_set')) {
        const ex = s.current;
        const wasAsking = this.dialogue.expecting('weight');
        this.dialogue.clear('weight');
        this.say(wasAsking
          ? `${ex.weight === 0 ? 'Bodyweight' : ex.weight}. ${ex.repLow} to ${ex.repHigh}. Go when you're ready.`
          : `${ex.weight === 0 ? 'Bodyweight' : ex.weight} it is.`);
      }

      if (types.includes('correct_last') && this._lastCorrection) {
        const c = this._lastCorrection; this._lastCorrection = null;
        this.say(`Changed to ${c.set[c.field]}.`);
      }

      if (types.includes('undo') && this._lastUndo) {
        const u = this._lastUndo; this._lastUndo = null;
        this.say(`Took off ${u.set.reps} at ${u.set.weight || 'bodyweight'}.`);
      }

      if (types.includes('extend_rest') || types.includes('start_rest')) {
        this.say(`${P.fmtTime(s.restLeftSec)}.`);
      }

      if (types.includes('substitute_exercise') && this._lastSub) {
        const e2 = this._lastSub; this._lastSub = null;
        const reason = (plan.find(p => p.intent === 'substitute_exercise') || {}).args;
        const why = reason && reason.reason === 'pain' ? ' — that should leave it alone' : '';
        this.say(`${e2.name} instead${why}. ${e2.weight || 'bodyweight'}, ${e2.repLow} to ${e2.repHigh}.`);
        if (this.speech) this.speech.invalidateAll();
      }

      if (types.includes('skip_exercise') || types.includes('next_exercise')) {
        const nav = this._lastNav; this._lastNav = null;
        if (nav && nav.type === 'done') this.finishUp(nav.summary);
        else if (nav && nav.exercise) {
          this.say(this.announce(nav.exercise, {}), { question: !!nav.exercise.needsWeight });
          if (!nav.exercise.needsWeight) this.dialogue.expect('reps', { ttlMs: 300000 });
        }
      }

      if (types.includes('set_time_budget')) {
        const cuts = this._lastCuts || []; this._lastCuts = null;
        this.say(cuts.length
          ? `Right. I've cut ${cuts.slice(0, 2).join(', ')} and kept the main work.`
          : `That's enough time for what's planned.`);
      }

      if (types.includes('reduce_volume')) this.say('Trimming the optional work.');

      if (types.includes('end_workout')) {
        const f = this._lastFinish; this._lastFinish = null;
        this.finishUp(f && f.summary);
      }
      this.deps.onState && this.deps.onState();
    }

    afterSetComplete() {
      const s = this.session, P = VOS.Policy, ex = s.current;
      if (!ex) return;
      if (ex.sets.length >= ex.targetSets) {
        const rest = s.startRest(Math.round(ex.restSec * 0.8 / 15) * 15, 'passive');
        this.restAnnounced = false;
        this.say(this.line('rest_transition', { rest: P.fmtTime(rest) }, () => `${P.fmtTime(rest)}, then we move on.`));
        this.prefetch(['exercise_intro']);
        return;
      }
      const active = ex.kind === 'compound' && ex.restSec >= 150;
      const rest = s.startRest(ex.restSec, active ? 'active' : 'passive');
      this.restAnnounced = false;
      const rf = P.restFacts(rest, active ? 'active' : 'passive');
      this.say(this.line(rf.moment, rf.facts, () => P.restCue(s, rest, active ? 'active' : 'passive')));
      this.prefetch();
    }

    readyNow() {
      const s = this.session, P = VOS.Policy;
      if (s.phase === 'resting') s.endRest();
      this.restAnnounced = true;
      const ex = s.current;
      if (!ex) { this.executePlan([{ intent: 'end_workout', confidence: 1 }], { confirmed: true }); return; }
      if (ex.sets.length >= ex.targetSets) {
        const r = s.nextExercise();
        if (r.type === 'done') { this.finishUp(r.summary); return; }
        this.say(this.announce(r.exercise, {}), { question: !!r.exercise.needsWeight });
        if (!r.exercise.needsWeight) this.dialogue.expect('reps', { ttlMs: 300000 });
        return;
      }
      s.cursor.set = ex.sets.length;
      const setNo = ex.sets.length + 1;
      this.say(this.line('rest_over', { setNumber: setNo, targetSets: ex.targetSets, weight: ex.weight === 0 ? 'bodyweight' : ex.weight },
        () => setNo === 1 ? `${ex.weight === 0 ? 'Bodyweight' : ex.weight}, ${ex.repLow} to ${ex.repHigh}. Go.` : `Set ${setNo}. Go when you're ready.`));
      this.dialogue.expect('reps', { ttlMs: 180000 });
      this.prefetch();
    }

    finishUp(summary) {
      const s = this.session;
      this.restAnnounced = true;
      if (s.phase !== 'summary') s.finish();
      this.say(VOS.Policy.summarise(summary || s.summary()) + ' How wrecked are you, one to ten?', { question: true });
      this.dialogue.expect('fatigue', { question: 'end fatigue', ttlMs: 300000 });
      this.deps.onFinish && this.deps.onFinish(summary || s.summary());
    }

    onActionRejected(check, action, meta) {
      this.metrics.rejected++;
      this.field && this.field.event('action_rejected', { code: check.code, reason: check.reason, action: action.type });
      // say something useful rather than swallowing it
      if (check.code === 'sets_complete' || check.code === 'nothing_to_correct' || check.code === 'no_pending_set'
          || check.code === 'bad_reps' || check.code === 'bad_weight' || check.code === 'bad_rpe'
          || check.code === 'unknown_exercise' || check.code === 'wrong_phase') {
        this.say(check.reason);
      }
    }

    report() {
      const avg = a => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
      return {
        turns: this.metrics.turns,
        executed: this.metrics.executed,
        confirmations: this.metrics.confirmed,
        clarifications: this.metrics.clarified,
        semanticCalls: this.metrics.semantic,
        gatedOut: this.metrics.gated,
        rejectedActions: this.metrics.rejected,
        avgResolveMs: avg(this.metrics.stage),
        queue: this.queue.stats,
        gate: this.gate.report(),
        audio: this.audio && this.audio.report ? this.audio.report() : null
      };
    }
  }

  VOS.Controller = Controller;
})(typeof globalThis !== 'undefined' ? globalThis : this);
