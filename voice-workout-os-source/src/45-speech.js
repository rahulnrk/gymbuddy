/* ============================================================
   45-speech.js : SPEECH GENERATOR
   Every line the user hears is written by the model — but never
   on the critical path. Lines are generated speculatively during
   dead time (rest, mid-set) and served from cache in 0ms.
   Static templates exist only as the cache-miss fallback.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* Moments the system can predict it will need. Slots are filled
     deterministically at play time, so a line can reference a number
     that isn't known yet when it's generated. */
  const MOMENTS = {
    set_ok: {
      n: 5, slots: ['reps', 'setNumber', 'targetSets'],
      desc: 'A routine set was just logged. Nothing notable. Acknowledge and get out of the way — usually one to three words. Vary it. Some of these should be almost nothing.'
    },
    set_top: {
      n: 3, slots: ['reps', 'repHigh'],
      desc: 'They just hit the top of the target rep range. One short sentence, plain, no cheerleading.'
    },
    set_low: {
      n: 3, slots: ['reps', 'repLow'],
      desc: 'They came in under the target range. Neutral and matter of fact, not disappointed.'
    },
    set_over: {
      n: 3, slots: ['reps', 'delta'],
      desc: 'Clearly better than the same set last session, by {delta} reps. One sentence stating the fact.'
    },
    set_under: {
      n: 3, slots: ['reps', 'delta'],
      desc: 'Clearly worse than the same set last session, down {delta} reps. Ask what happened, briefly and without alarm.'
    },
    set_last: {
      n: 3, slots: ['reps'],
      desc: 'That was the final set of this exercise. Short close-out.'
    },
    ask_rpe: {
      n: 5, slots: [],
      desc: 'Ask how hard that set felt. Very short, conversational, different every time. Never use the letters RPE.'
    },
    rest_cue: {
      n: 4, slots: ['rest'],
      desc: 'Tell them to rest. {rest} is already a spoken phrase such as "two minutes" — do not add the word minutes yourself. Keep it to a few words.'
    },
    rest_active: {
      n: 3, slots: ['rest'],
      desc: 'Rest after a heavy compound lift. Tell them to rest {rest} and to keep moving or walk it off.'
    },
    rest_over: {
      n: 4, slots: ['setNumber', 'targetSets', 'weight'],
      desc: 'Rest is finished, next set is set {setNumber} of {targetSets} at {weight}. Short. Do not shout.'
    },
    rest_transition: {
      n: 3, slots: ['rest'],
      desc: 'That was the last set of this exercise. Tell them to rest {rest} and that you will move on after. Short.'
    },
    rpe_easy: {
      n: 3, slots: [],
      desc: 'They reported the set was easy and there are sets left. Offer to add a little weight. End with a question.'
    },
    rpe_max: {
      n: 3, slots: [],
      desc: 'They reported the set was near maximal. Say you are holding the load. Calm, no drama.'
    },
    exercise_intro: {
      n: 1, slots: [], full: true,
      desc: 'Announce the next exercise using the facts in nextExercise. State the load and rep target — but if loadKnown is false, do NOT state a load, just the sets and rep target, because the agent is about to ask what they are using. If lastTime exists, reference it naturally. If progressionReason exists, give that reason in your own plain words — do not invent a different reason. Two or three short sentences maximum.'
    },
    workout_intro: {
      n: 1, slots: [], full: true,
      desc: 'Open the session. Name the first exercise from nextExercise with its load and rep target (omit the load entirely if loadKnown is false), reference lastTime if present, give the progression reason if present, and hand over. Three short sentences maximum.'
    }
  };

  /* Lines that only make sense for one exercise / set / load. Anything not
     listed is generic enough to survive a state change. Nothing safety-related
     is ever generated ahead of time — see 55-safety.js. */
  /* Only fully-written lines are context-scoped. Everything else carries
     slots that are filled from live state at play time, so it cannot go
     stale — that is the whole point of writing them with slots. */
  const CONTEXT_SCOPED = { exercise_intro: 1, workout_intro: 1 };

  const PERSONA = `You write the spoken lines for a workout agent. The user is in a gym with headphones in and their phone in their pocket. They cannot look at a screen.

Rules, all of them hard:
- This is speech. Short. Most lines are under eight words.
- No markdown, no lists, no emoji, no symbols. Write numbers as digits; the speech engine reads them correctly.
- Never say "great job", "you've got this", "keep pushing", "amazing", "crushing it", "let's go" or anything a motivational poster would say.
- Calm, competent, dry. A training partner who has done this a thousand times, not a hype man.
- Vary sentence shape and length across the variants you produce. Some should be a single word.
- Never invent facts, numbers, past sessions or personal records. Only use what you are given.
- Never claim to see the user or their form. You have no camera.`;

  function renderSlots(tpl, facts) {
    let missing = false;
    const out = tpl.replace(/\{(\w+)\}/g, (m, k) => {
      if (facts && facts[k] != null) return String(facts[k]);
      missing = true;
      return m;
    });
    return missing ? null : out;
  }

  class SpeechGen {
    constructor(opts) {
      opts = opts || {};
      this.llm = opts.llm || null;
      this.memory = opts.memory || (() => null);
      this.cache = new Map();     // moment -> [{tpl, key}]
      this.contextKey = null;
      this.recent = [];           // recently spoken, so the model can avoid them
      this.inflight = null;
      this.enabled = opts.enabled !== false;
      this.lastBatchAt = 0;
      this.cooldownMs = opts.cooldownMs != null ? opts.cooldownMs : 9000;
      this.stats = { batches: 0, generated: 0, fromCache: 0, fromFallback: 0, ms: [], errors: 0, skipped: 0, invalidated: 0 };
    }

    active() { return this.enabled && this.llm && this.llm.configured(); }

    /* Serve a line. Synchronous. Never waits on the network.
       A line written for a different state is not served — it is dropped. */
    line(moment, facts, contextKey) {
      if (!this.active()) { this.stats.fromFallback++; return null; }
      const q = this.cache.get(moment);
      if (!q || !q.length) { this.stats.fromFallback++; return null; }
      const scoped = CONTEXT_SCOPED[moment];
      for (let i = 0; i < q.length; i++) {
        const entry = q[i];
        if (scoped && contextKey && entry.key && entry.key !== contextKey) continue;   // stale
        const rendered = renderSlots(entry.tpl, facts);
        if (rendered) {
          q.splice(i, 1);
          this.stats.fromCache++;
          this.remember(rendered);
          return rendered;
        }
      }
      this.stats.fromFallback++;
      return null;
    }

    /* Called after every state change. Drops anything written for the
       world as it was, keeps the generic acknowledgements. */
    touch(contextKey) {
      if (contextKey === this.contextKey) return;
      this.contextKey = contextKey;
      let dropped = 0;
      Object.keys(CONTEXT_SCOPED).forEach(m => {
        const q = this.cache.get(m);
        if (!q) return;
        const keep = q.filter(e => !e.key || e.key === contextKey);
        dropped += q.length - keep.length;
        this.cache.set(m, keep);
      });
      this.stats.invalidated += dropped;
    }

    invalidateAll() {
      let n = 0;
      this.cache.forEach(q => { n += q.length; });
      this.cache.clear();
      this.recent = [];
      this.stats.invalidated += n;
    }

    remember(text) {
      this.recent.push(text);
      if (this.recent.length > 24) this.recent = this.recent.slice(-24);
    }

    need(moment) {
      const spec = MOMENTS[moment];
      if (!spec) return 0;
      const have = (this.cache.get(moment) || []).length;
      return Math.max(0, spec.n - have);
    }

    /* Only worth a round trip once a queue is actually running low.
       Topping up one line at a time would cost as much as generating live. */
    wants(moment) {
      const spec = MOMENTS[moment];
      if (!spec) return 0;
      const have = (this.cache.get(moment) || []).length;
      const floorLevel = spec.n <= 1 ? 0 : Math.floor(spec.n * 0.4);
      return have <= floorLevel ? spec.n - have : 0;
    }

    /* Which moments are worth having ready given where we are. */
    plan(session) {
      const ex = session.current;
      const want = [];
      if (!ex) return want;
      const setsLeft = ex.targetSets - ex.sets.length;
      if (setsLeft > 0) {
        want.push('set_ok', 'ask_rpe', 'rest_cue', 'rest_over');
        if (setsLeft === 1) want.push('rest_transition');
        if (ex.kind === 'compound' && ex.restSec >= 150) want.push('rest_active');
        want.push('set_top', 'set_low', 'set_over', 'set_under');
        if (setsLeft === 1) want.push('set_last');
        want.push('rpe_easy', 'rpe_max');
      } else {
        want.push('exercise_intro');
      }
      return want.filter(m => this.need(m) > 0);
    }

    /* Fire-and-forget top-up. Safe to call often; it self-throttles. */
    prefetch(session, extra, contextKey) {
      if (contextKey) this.touch(contextKey);
      if (!this.active() || this.inflight) return Promise.resolve();
      let moments = this.plan(session);
      if (extra) extra.forEach(m => { if (!moments.includes(m) && this.need(m) > 0) moments.push(m); });
      if (!moments.length) return Promise.resolve();
      // An empty queue is urgent; a merely thinning one can wait for the cooldown,
      // so a session costs a handful of batches rather than one call per line.
      const urgent = moments.some(m => (this.cache.get(m) || []).length === 0);
      if (!urgent && Date.now() - this.lastBatchAt < this.cooldownMs) { this.stats.skipped++; return Promise.resolve(); }
      this.lastBatchAt = Date.now();
      this.inflight = this.generate(session, moments)
        .catch(() => { this.stats.errors++; })
        .then(() => { this.inflight = null; });
      return this.inflight;
    }

    /* Force a specific moment to be ready before it is needed. */
    async ensure(session, moment, contextKey) {
      if (contextKey) this.touch(contextKey);
      if (!this.active() || this.need(moment) === 0) return;
      if (this.inflight) await this.inflight.catch(() => {});
      if (this.need(moment) === 0) return;
      try { await this.generate(session, [moment]); }
      catch (e) { this.stats.errors++; }
    }

    buildPrompt(session, moments) {
      const snap = session.snapshot();
      const nextEx = (() => {
        const ex = session.current;
        if (ex && ex.sets.length < ex.targetSets) return null;
        const rest = session.exercises.slice(session.cursor.ex + 1).filter(e => e.status !== 'skipped');
        return rest[0] || null;
      })();
      const target = nextEx || session.current;
      const prev = target ? session.lastSessionFor(target.name) : null;

      const ctx = {
        workout: snap.workout,
        level: snap.level,
        goal: snap.goal,
        setsDone: snap.setsDone,
        setsPlanned: snap.setsPlanned,
        elapsedMin: snap.elapsedMin,
        energyReported: snap.energy,
        currentExercise: snap.currentExercise,
        nextExercise: target ? {
          name: target.name,
          weight: target.needsWeight ? null : (target.weight === 0 ? 'bodyweight' : target.weight + ' kilos'),
          loadKnown: !target.needsWeight,
          repRange: `${target.repLow} to ${target.repHigh}`,
          sets: target.targetSets,
          progressionReason: target.progression && target.progression.reason,
          substitutionNote: target.note || null,
          lastTime: prev ? prev.exercise.sets.map(s => ({ reps: s.reps, kg: s.weight, rpe: s.rpe })) : null,
          isTechnical: target.technique === 'high'
        } : null
      };

      const spec = moments.map(m => {
        const s = MOMENTS[m];
        void 0;
        const slotTxt = s.slots.length ? ` Allowed slots: ${s.slots.map(x => '{' + x + '}').join(', ')} — use them where a number belongs, or write a line that needs none.`
                                       : ' Use no slots.';
        const want = Math.max(1, this.need(m));
        return `"${m}" (${want} variant${want > 1 ? 's' : ''}): ${s.desc}${s.full ? ' Write it fully, no slots — all the facts are in the context.' : slotTxt}`;
      }).join('\n');

      return [
        'CONTEXT (everything true, nothing else is):',
        JSON.stringify(ctx),
        '',
        'Recently spoken — do not reuse or closely echo these:',
        JSON.stringify(this.recent.slice(-14)),
        '',
        'Write spoken lines for these moments:',
        spec,
        '',
        'Return one JSON object, nothing else, mapping each moment name to an array of strings:',
        `{${moments.map(m => `"${m}":[...]`).join(',')}}`
      ].join('\n');
    }

    async generate(session, moments) {
      const t0 = Date.now();
      const res = await fetch(this.llm.endpoint('/v1/messages'), {
        method: 'POST',
        headers: this.llm.headers(),
        body: JSON.stringify({
          model: this.llm.cfg.model || undefined,
          max_tokens: 900,
          system: PERSONA,
          messages: [{ role: 'user', content: this.buildPrompt(session, moments) }]
        })
      });
      if (!res.ok) throw new Error('speechgen ' + res.status + ' ' + (await res.text()).slice(0, 160));
      const j = await res.json();
      let text = (j.content || []).filter(c => c.type === 'text').map(c => c.text).join('').trim();
      const fence = text.match(/```(?:json)?\s*([\s\S]*?)```/);
      if (fence) text = fence[1].trim();
      const a = text.indexOf('{'), b = text.lastIndexOf('}');
      if (a >= 0 && b > a) text = text.slice(a, b + 1);
      const obj = JSON.parse(text);

      let added = 0;
      Object.keys(obj).forEach(m => {
        if (!MOMENTS[m]) return;
        const lines = (Array.isArray(obj[m]) ? obj[m] : [obj[m]])
          .filter(x => typeof x === 'string' && x.trim())
          .map(x => x.trim())
          .filter(x => x.length < 320)
          .filter(x => !/\{(?!(?:reps|setNumber|targetSets|repHigh|repLow|delta|rest|weight)\})/.test(x));
        const q = this.cache.get(m) || [];
        lines.forEach(l => { if (!q.some(e => e.tpl === l)) { q.push({ tpl: l, key: this.contextKey }); added++; } });
        this.cache.set(m, q);
      });

      this.stats.batches++;
      this.stats.generated += added;
      this.stats.ms.push(Date.now() - t0);
      return added;
    }

    /* An exercise change invalidates anything that referenced the old one. */
    invalidateExerciseScoped() { Object.keys(CONTEXT_SCOPED).forEach(m => this.cache.delete(m)); }

    report() {
      const avg = this.stats.ms.length ? Math.round(this.stats.ms.reduce((a, b) => a + b, 0) / this.stats.ms.length) : 0;
      const total = this.stats.fromCache + this.stats.fromFallback;
      return {
        batches: this.stats.batches,
        linesGenerated: this.stats.generated,
        servedFromModel: this.stats.fromCache,
        servedFromTemplate: this.stats.fromFallback,
        modelShare: total ? Math.round(100 * this.stats.fromCache / total) + '%' : '—',
        avgBatchMs: avg,
        staleLinesDropped: this.stats.invalidated,
        batchesSkippedByCooldown: this.stats.skipped,
        errors: this.stats.errors
      };
    }
  }

  VOS.SpeechGen = SpeechGen;
  VOS.SPEECH_MOMENTS = MOMENTS;
  VOS.SPEECH_CONTEXT_SCOPED = CONTEXT_SCOPED;
  VOS.SPEECH_PERSONA = PERSONA;
})(typeof globalThis !== 'undefined' ? globalThis : this);
