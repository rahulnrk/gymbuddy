/* ============================================================
   52-actions.js : validation, idempotency and serialised execution
   Nothing touches workout state except through here. Every action
   carries the utterance that caused it and the state version it was
   decided against, so a stale or duplicated action cannot land.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const PHASE_OK = {
    log_set:       ['working', 'transition', 'resting'],
    set_rpe:       ['working', 'transition', 'resting'],
    set_weight:    ['working', 'transition', 'resting', 'checkin', 'warmup'],
    start_rest:    ['working', 'transition'],
    extend_rest:   ['resting'],
    end_rest:      ['resting', 'working', 'transition'],
    skip_exercise: ['working', 'transition', 'resting'],
    next_exercise: ['working', 'transition', 'resting'],
    substitute_exercise: ['working', 'transition', 'resting'],
    reduce_volume: ['working', 'transition', 'resting', 'checkin'],
    set_time_budget: ['working', 'transition', 'resting', 'checkin', 'warmup'],
    undo:          ['working', 'transition', 'resting'],
    correct_last:  ['working', 'transition', 'resting', 'summary'],
    end_workout:   ['working', 'transition', 'resting', 'paused', 'warmup'],
    warmup_next:   ['warmup'],
    warmup_skip:   ['warmup'],
    warmup_extra:  ['warmup'],
    add_note:      ['*'], none: ['*'], enter_safety: ['*']
  };

  const Validator = {
    /**
     * check(action, session, dialogue) ->
     *   { ok, reason, code, needsConfirm, confirmPrompt, fix }
     */
    check(action, session, dialogue) {
      const bad = (code, reason, extra) => Object.assign({ ok: false, code, reason }, extra || {});
      const ok  = (extra) => Object.assign({ ok: true }, extra || {});
      if (!action || !action.type) return bad('malformed', 'No action type.');

      const ex = session.current;
      const phases = PHASE_OK[action.type];
      if (!phases) return bad('unknown_action', `Unknown action ${action.type}.`);
      if (phases[0] !== '*' && !phases.includes(session.phase))
        return bad('wrong_phase', `${action.type} is not valid while ${session.phase}.`);

      // a workout that has ended accepts nothing but corrections
      if (session.phase === 'summary' && action.type !== 'correct_last' && action.type !== 'add_note')
        return bad('finished', 'The workout is already finished.');

      switch (action.type) {
        case 'log_set': {
          if (!ex) return bad('no_exercise', 'No exercise is active.');
          const r = action.reps;
          if (typeof r !== 'number' || !isFinite(r) || r < 1 || r > 100 || r % 1 !== 0)
            return bad('bad_reps', `${r} is not a believable rep count.`);
          if (ex.sets.length >= ex.targetSets)
            return bad('sets_complete', `${ex.name} already has all ${ex.targetSets} sets logged.`,
                       { needsConfirm: true, confirmPrompt: `That's an extra set on ${ex.name}. Add it?` });
          // wildly outside the target range is possible but worth checking
          if (r > ex.repHigh + 8 || r < Math.max(1, ex.repLow - 6))
            return ok({ needsConfirm: true, confirmPrompt: `${r} reps?` });
          if (action.weight != null) {
            const w = this.checkWeight(action.weight, ex);
            if (!w.ok) return w;
            if (w.needsConfirm) return ok(w);
          }
          return ok();
        }

        case 'set_rpe': {
          if (!session.pendingSet) return bad('no_pending_set', 'There is no set waiting for a rating.');
          const v = action.rpe;
          if (typeof v !== 'number' || v < 1 || v > 10) return bad('bad_rpe', `${v} is not an RPE.`);
          return ok();
        }

        case 'set_weight': {
          if (!ex) return bad('no_exercise', 'No exercise is active.');
          return this.checkWeight(action.weight, ex, true);
        }

        case 'correct_last': {
          const target = this.lastLoggedSet(session);
          if (!target) return bad('nothing_to_correct', 'Nothing has been logged yet.');
          if (action.field === 'reps' && (action.value < 1 || action.value > 100)) return bad('bad_reps', 'Not a believable rep count.');
          if (action.field === 'rpe' && (action.value < 1 || action.value > 10)) return bad('bad_rpe', 'Not an RPE.');
          if (action.field === 'weight') { const w = this.checkWeight(action.value, target.exercise); if (!w.ok) return w; }
          return ok();
        }

        case 'substitute_exercise': {
          if (!action.name) return bad('no_name', 'No exercise named.');
          if (!VOS.EXERCISES[action.name]) return bad('unknown_exercise', `${action.name} is not in the library.`);
          if (!ex) return bad('no_exercise', 'No exercise is active.');
          if (ex.sets.length) return bad('already_started', `You've already logged sets on ${ex.name}.`,
                                         { needsConfirm: true, confirmPrompt: `Swap ${ex.name} for ${action.name} even though you've started it?` });
          return ok();
        }

        case 'skip_exercise':
          if (!ex) return bad('no_exercise', 'Nothing to skip.');
          if (ex.sets.length) return ok({ needsConfirm: true, confirmPrompt: `You've done ${ex.sets.length} set${ex.sets.length > 1 ? 's' : ''} of ${ex.name}. Skip the rest?` });
          return ok();

        case 'extend_rest':
        case 'start_rest':
          if (typeof action.seconds !== 'number' || action.seconds < 5 || action.seconds > 900)
            return bad('bad_duration', 'That rest length is out of range.');
          return ok();

        case 'set_time_budget':
          if (typeof action.minutes !== 'number' || action.minutes < 5 || action.minutes > 240)
            return bad('bad_budget', 'That session length is out of range.');
          return ok();

        case 'end_workout':
          if (session.exercises.some(e => e.status === 'pending' || (e.status === 'active' && e.sets.length < e.targetSets)))
            return ok({ needsConfirm: true, confirmPrompt: 'There is still work left. End it here?' });
          return ok();

        default:
          return ok();
      }
    },

    checkWeight(w, ex, explicit) {
      if (typeof w !== 'number' || !isFinite(w) || w < 0 || w > 500)
        return { ok: false, code: 'bad_weight', reason: `${w} kilos is not believable.` };
      const meta = VOS.exerciseMeta(ex.name);
      if (meta.load === 'bodyweight' && w > 60)
        return { ok: false, code: 'bad_weight', reason: `${ex.name} is a bodyweight movement.` };
      const cur = ex.weight;
      if (explicit && cur > 0 && w > 0) {
        const ratio = w / cur;
        if (ratio > 1.6 || ratio < 0.5)
          return { ok: true, needsConfirm: true, confirmPrompt: `${w} kilos? That's a big jump from ${cur}.` };
      }
      return { ok: true };
    },

    lastLoggedSet(session) {
      for (let i = session.exercises.length - 1; i >= 0; i--) {
        const e = session.exercises[i];
        if (e.sets.length) return { exercise: e, set: e.sets[e.sets.length - 1], index: e.sets.length - 1 };
      }
      return null;
    }
  };

  /* ============================================================
     Serialised queue. One action in flight at a time, in order,
     with duplicate and stale rejection.
     ============================================================ */
  class ActionQueue {
    constructor(opts) {
      this.session = opts.session;
      this.dialogue = opts.dialogue;
      this.apply = opts.apply;                 // (action) => void, the only writer
      this.onReject = opts.onReject || (() => {});
      this.onConfirmNeeded = opts.onConfirmNeeded || (() => {});
      this.seen = new Set();                   // utteranceId:index, for idempotency
      this.q = [];
      this.running = false;
      this.stats = { submitted: 0, applied: 0, rejected: 0, duplicates: 0, stale: 0, confirmed: 0 };
    }

    /**
     * Submit a plan of one or more actions. All-or-nothing: if any step
     * fails validation the whole plan is rejected, so "skip this and give
     * me two minutes" cannot half-happen.
     */
    submit(actions, meta) {
      meta = meta || {};
      const list = Array.isArray(actions) ? actions : [actions];
      return new Promise(resolve => {
        this.q.push({ list, meta, resolve });
        this.drain();
      });
    }

    async drain() {
      if (this.running) return;
      this.running = true;
      try {
        while (this.q.length) {
          const job = this.q.shift();
          job.resolve(this.run(job));
        }
      } finally { this.running = false; }
    }

    run(job) {
      const { list, meta } = job;
      this.stats.submitted++;

      // idempotency — the recogniser can emit the same final twice
      const key = (meta.utteranceId || 'anon') + ':' + list.map(a => a.type).join(',');
      if (meta.utteranceId && this.seen.has(key)) {
        this.stats.duplicates++;
        return { ok: false, code: 'duplicate', applied: [] };
      }

      // staleness — the world moved between deciding and executing
      if (meta.stateVersion != null && meta.stateVersion !== this.session.version) {
        const revalidated = list.map(a => Validator.check(a, this.session, this.dialogue));
        if (revalidated.some(r => !r.ok)) {
          this.stats.stale++;
          const first = revalidated.find(r => !r.ok);
          this.onReject(first, list[revalidated.indexOf(first)], meta);
          return { ok: false, code: 'stale', reason: first.reason, applied: [] };
        }
      }

      // validate the whole plan before touching anything
      const checks = list.map(a => Validator.check(a, this.session, this.dialogue));
      const failed = checks.findIndex(c => !c.ok);
      if (failed >= 0) {
        this.stats.rejected++;
        this.onReject(checks[failed], list[failed], meta);
        return { ok: false, code: checks[failed].code, reason: checks[failed].reason, applied: [] };
      }

      const needing = checks.findIndex(c => c.needsConfirm);
      if (needing >= 0 && !meta.confirmed) {
        this.stats.confirmed++;
        this.onConfirmNeeded(checks[needing].confirmPrompt, list, meta);
        return { ok: false, code: 'needs_confirm', prompt: checks[needing].confirmPrompt, applied: [] };
      }

      if (meta.utteranceId) this.seen.add(key);
      const applied = [];
      list.forEach(a => { this.apply(a); applied.push(a.type); });
      this.stats.applied += applied.length;
      return { ok: true, applied, version: this.session.version };
    }
  }

  VOS.Validator = Validator;
  VOS.ActionQueue = ActionQueue;
  VOS.PHASE_OK = PHASE_OK;
})(typeof globalThis !== 'undefined' ? globalThis : this);
