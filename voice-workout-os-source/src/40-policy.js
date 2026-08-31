/* ============================================================
   40-policy.js : CONVERSATION POLICY
   Decides whether the agent speaks at all, and if so, how little
   it can get away with. Silence is the default.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const pick = a => a[Math.floor(Math.random() * a.length)];
  const kg = w => (w === 0 ? 'bodyweight' : `${(+w).toString().replace('.', ' point ')} kilos`);
  const kgShort = w => (w === 0 ? 'bodyweight' : `${w}`);

  function fmtTime(sec) {
    if (sec === 60) return 'a minute';
    if (sec === 90) return 'ninety seconds';
    if (sec % 60 === 0 && sec >= 120) return `${sec/60} minutes`;
    if (sec > 60) {
      const m = Math.floor(sec / 60), r = sec % 60;
      if (r === 30) return `${m} and a half minutes`;
      return `${m} minute${m > 1 ? 's' : ''} ${r} seconds`;
    }
    return `${sec} seconds`;
  }

  function fmtSets(sets) {
    if (!sets || !sets.length) return '';
    const sameW = sets.every(s => s.weight === sets[0].weight);
    const reps = sets.map(s => s.reps).join(', ');
    return sameW ? `${kgShort(sets[0].weight)} for ${reps}` : sets.map(s => `${kgShort(s.weight)} for ${s.reps}`).join(', ');
  }

  const Policy = {
    /* ---------- announcing an exercise ---------- */
    announceExercise(session, ex, opts) {
      opts = opts || {};
      const prev = session.lastSessionFor(ex.name);
      const parts = [];
      const lead = opts.first ? `Starting with ${ex.name}.` : `Next up, ${ex.name}.`;
      parts.push(lead);

      if (prev && prev.exercise.sets.length) {
        parts.push(`Last time ${fmtSets(prev.exercise.sets)}.`);
      }

      const p = ex.progression || {};
      if (ex.needsWeight && !ex.sets.length) {
        // load unknown — don't announce one, the agent is about to ask
        parts.push(`${ex.targetSets} sets, ${ex.repLow} to ${ex.repHigh}.`);
        if (ex.note) parts.push(ex.note);
        return parts.join(' ');
      }
      if (p.action === 'increase') parts.push(`Today ${kg(ex.weight)} — ${p.reason.replace(/^All sets/, 'you hit all your reps')}`);
      else if (p.action === 'deload') parts.push(`We're dropping to ${kg(ex.weight)}. ${p.reason}`);
      else if (p.action === 'add_reps') parts.push(`Same setup, chase more reps.`);
      else parts.push(`${kg(ex.weight)}, ${ex.repLow} to ${ex.repHigh}.`);

      if (ex.note) parts.push(ex.note);

      // Technique checkpoint — only for high-technique lifts, only when load went up,
      // and never as a claim about form the system cannot see.
      if (ex.technique === 'high' && p.action === 'increase' && (session.profile.level === 'beginner')) {
        parts.push(`That's a step up on a technical lift — if you're not sure about your setup, film a set or have someone check it.`);
      }

      if (opts.first) parts.push('Start when you\'re ready.');
      return parts.join(' ');
    },

    /* ---------- after a set is logged ---------- */
    afterSet(session, result) {
      const { exercise: ex, set, analysis, needRpe } = result;
      const last = ex.sets.length >= ex.targetSets;

      // 1. Something went wrong — this always earns speech.
      if (analysis.flag === 'underperform') {
        return {
          speak: true,
          moment: 'set_under',
          facts: { reps: set.reps, delta: analysis.delta != null ? Math.abs(analysis.delta) : null },
          text: `That dropped off a fair bit${analysis.delta != null ? ` — ${Math.abs(analysis.delta)} reps down on last time` : ''}. How did it feel?`,
          expect: 'rpe'
        };
      }

      // 2. We need an RPE to make a real decision later.
      if (needRpe) {
        return { speak: true, moment: 'ask_rpe', facts: {},
                 text: pick(['How did that feel?', 'How was that one?', 'How hard was that?']), expect: 'rpe' };
      }

      // 3. Notable overperformance — worth one sentence.
      if (analysis.flag === 'overperform') {
        return { speak: true, moment: 'set_over',
                 facts: { reps: set.reps, delta: analysis.delta != null ? Math.abs(analysis.delta) : null },
                 text: `${set.reps}. That's well up on last time.`, expect: null };
      }

      // 4. Routine set. Minimum viable speech.
      if (last) return { speak: true, moment: 'set_last', facts: { reps: set.reps },
                         text: pick(['Good. That\'s the exercise.', 'That\'s it for that one.']), expect: null };
      if (analysis.flag === 'top_of_range')
        return { speak: true, moment: 'set_top', facts: { reps: set.reps, repHigh: ex.repHigh },
                 text: pick(['Good.', 'That\'s the range.']), expect: null, terse: true };
      if (analysis.flag === 'below_range')
        return { speak: true, moment: 'set_low', facts: { reps: set.reps, repLow: ex.repLow },
                 text: 'Noted.', expect: null, terse: true };
      return { speak: true, moment: 'set_ok',
               facts: { reps: set.reps, setNumber: ex.sets.length, targetSets: ex.targetSets },
               text: pick(['Good.', 'Logged.', 'Nice.']), expect: null, terse: true };
    },

    /* ---------- reacting to an RPE ---------- */
    afterRpe(session, rpe, ex) {
      const setsLeft = ex.targetSets - ex.sets.length;
      if (rpe <= 6 && setsLeft > 0) {
        return { speak: true, moment: 'rpe_easy', facts: {},
                 text: `That's light. Want to add a little weight for the next set?`, expect: 'confirm_bump' };
      }
      if (rpe >= 9.5 && setsLeft > 0) {
        return { speak: true, moment: 'rpe_max', facts: {},
                 text: `That was close to your limit. I'll keep the load and we'll see how the next one goes.`, expect: null };
      }
      if (rpe >= 9.5 && setsLeft === 0) {
        return { speak: true, text: `Noted — that was a hard finish. I'll factor it in for next session.`, expect: null };
      }
      return { speak: false };
    },

    /* ---------- rest ---------- */
    restFacts(sec, kind) {
      return { moment: kind === 'active' ? 'rest_active' : 'rest_cue', facts: { rest: fmtTime(sec) } };
    },

    restCue(session, sec, kind) {
      if (kind === 'active') return `Take ${fmtTime(sec)}. Walk it off and loosen up.`;
      if (sec >= 150) return `${fmtTime(sec)}. Take it properly.`;
      if (sec <= 60) return `${fmtTime(sec)}.`;
      return fmtTime(sec) + '.';
    },

    restOver(session) {
      const ex = session.current;
      if (!ex) return { speak:true, text:'Time.' };
      const setNo = ex.sets.length + 1;
      if (setNo > ex.targetSets) return { speak: true, moment: null, text: 'Time. Ready to move on?' };
      return { speak: true, moment: 'rest_over',
               facts: { setNumber: setNo, targetSets: ex.targetSets, weight: kgShort(ex.weight) },
               text: `Set ${setNo}${ex.sets.length ? '' : ` of ${ex.targetSets}`}. ${kgShort(ex.weight)}. Go when you're ready.` };
    },

    /* ---------- deterministic answers to common questions ---------- */
    answer(session, intent) {
      const ex = session.current;
      switch (intent.intent) {
        case 'whats_next': {
          const rest = session.exercises.slice(session.cursor.ex + 1).filter(e => e.status !== 'skipped');
          if (ex && ex.sets.length < ex.targetSets)
            return `${ex.targetSets - ex.sets.length} more set${ex.targetSets - ex.sets.length > 1 ? 's' : ''} of ${ex.name}, then ${rest.length ? rest[0].name : 'you\'re done'}.`;
          return rest.length ? `${rest[0].name}, ${rest[0].targetSets} sets of ${rest[0].repLow} to ${rest[0].repHigh}.` : 'That\'s the last exercise.';
        }
        case 'where_am_i':
          return ex ? `${ex.name}, set ${Math.min(ex.sets.length + 1, ex.targetSets)} of ${ex.targetSets}, ${kgShort(ex.weight)} for ${ex.repLow} to ${ex.repHigh}.` : 'We haven\'t started yet.';
        case 'what_weight':
          return ex ? `${kg(ex.weight)}.` : 'No exercise loaded yet.';
        case 'time_left': {
          const l = session.restLeftSec;
          return l > 0 ? `${fmtTime(l)} left.` : 'Rest is done — go when you\'re ready.';
        }
        case 'elapsed': {
          const m = Math.round(session.elapsedSec / 60);
          return `${m} minute${m === 1 ? '' : 's'} in, ${session.setsDone} sets done.`;
        }
        default: return null;
      }
    },

    /* ---------- summary ---------- */
    summarise(summary) {
      const mins = Math.round(summary.durationSec / 60);
      const lines = [`Done. ${mins} minutes, ${summary.sets} sets, ${summary.volume.toLocaleString()} kilos of total volume.`];
      if (summary.prs.length) {
        lines.push(`Best set${summary.prs.length > 1 ? 's' : ''} you've done: ` +
          summary.prs.slice(0,2).map(p => `${p.exercise} ${kgShort(p.weight)} for ${p.reps}`).join(', ') + '.');
      }
      if (summary.skipped.length) lines.push(`We skipped ${summary.skipped.join(' and ')}.`);
      return lines.join(' ');
    },

    fmtTime, kg, kgShort, fmtSets
  };

  VOS.Policy = Policy;
})(typeof globalThis !== 'undefined' ? globalThis : this);
