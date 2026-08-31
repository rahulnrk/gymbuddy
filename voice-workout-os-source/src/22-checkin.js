/* ============================================================
   22-checkin.js : the conversation that happens during the warm-up

   The old shape was an interrogation before anything moved. This is
   the opposite: the warm-up starts the moment you tap go, and the
   questions arrive one at a time while you are already moving.

   Two rules keep it from feeling like a form:

     1. One question per warm-up item, never two at once, each asked
        once, all of them skippable by saying nothing.

     2. A question states what the model already believes and asks you
        to check it, rather than asking an open question about your
        body. "Your quads should still be sore from Friday — are they?"
        can be confirmed, corrected, or contradicted. "How do you feel?"
        can only be answered with a shrug.

   The third outcome is the valuable one. Soreness the model did not
   predict usually means something about how the movement was done.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const H = 3600000;
  const fmtAgo = (ms) => {
    const h = Math.round((Date.now() - ms) / H);
    if (h < 20) return 'yesterday';
    if (h < 44) return 'yesterday';
    const d = Math.round(h / 24);
    return d <= 1 ? 'yesterday' : `${d} days ago`;
  };

  function dayName(ms) {
    const d = new Date(ms);
    const diff = (Date.now() - ms) / 864e5;
    if (diff < 1.6) return 'yesterday';
    if (diff > 6.5) return 'your last session';
    return ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][d.getDay()] + "'s";
  }

  const list = (arr) => arr.length <= 1 ? (arr[0] || '')
    : arr.slice(0, -1).join(', ') + ' and ' + arr[arr.length - 1];

  class CheckIn {
    constructor(opts) {
      const o = opts || {};
      this.session = o.session;
      this.memory = o.memory;                       // VOS.Memory
      this.history = (this.memory && this.memory.data.history) || [];
      this.asked = {};
      this.answers = {};
      this.learned = [];        // things worth repeating back at the end
      this.surprises = [];      // sore where the model did not expect it

      this.predicted = VOS.Soreness ? VOS.Soreness.predictedAreas(this.history) : [];
      /* The full modelled picture, blind to anything previously reported.
         Answers are graded against this, so "unexpected" means sorer
         than the model had it — not merely absent from a shortlist. */
      this.modelState = VOS.Recovery ? VOS.Recovery.state(this.history, null, { reports: [] }) : {};
      this.last = this.history.length ? this.history[this.history.length - 1] : null;
      this.lastAt = this.last ? Date.parse(this.last.date) : null;

      this.queue = this.build();
    }

    /* ---------- which questions are worth asking today ---------- */
    build() {
      const q = [];

      q.push({ id: 'energy', item: 0, expect: 'energy' });

      /* Asking about the last session only earns its place when there
         is something to ask about — it was recent, and it either got
         cut short or was graded hard. Otherwise it is small talk. */
      const recent = this.lastAt && (Date.now() - this.lastAt) < 40 * H;
      const notable = this.last && (
        (this.last.notes && this.last.notes.length) ||
        (this.last.skipped && this.last.skipped.length) ||
        (this.last.endFatigue != null && this.last.endFatigue >= 9)
      );
      if (recent && notable) q.push({ id: 'yesterday', item: 1, expect: 'free' });

      q.push({ id: 'soreness', item: recent && notable ? 2 : 1, expect: 'soreness' });

      return q;
    }

    /* ---------- what to say when the warm-up reaches item n ---------- */
    dueFor(itemIndex) {
      const q = this.queue.find(x => x.item === itemIndex && !this.asked[x.id]);
      if (!q) return null;
      const text = this.compose(q);
      if (!text) { this.asked[q.id] = true; return this.dueFor(itemIndex); }
      this.asked[q.id] = true;
      this.pending = q;
      this.lastAskedId = q.id;
      return { id: q.id, expect: q.expect, text };
    }

    /* Anything still unasked, for when the warm-up is shorter than the
       question list. */
    remaining() { return this.queue.filter(x => !this.asked[x.id]); }

    compose(q) {
      if (q.id === 'energy') {
        return 'While you do that — how is the energy today, one to ten?';
      }

      if (q.id === 'yesterday') {
        const when = dayName(this.lastAt);
        const cut = this.last.skipped && this.last.skipped.length;
        if (cut) return `${when} session got cut short. Did that turn out to be the right call?`;
        return `${when} ${this.last.day} was a hard one. How did it land?`;
      }

      if (q.id === 'soreness') {
        if (!this.predicted.length) {
          return 'Anything sore from the last few days that I should know about?';
        }
        const top = this.predicted.slice(0, 2);
        this.askedMuscles = top.map(p => p.lead.id);      // what the question actually named
        const names = top.map(p => (p.lead.name || '').toLowerCase());
        const when = this.lastAt ? dayName(this.lastAt).replace(/'s$/, '') : 'the last session';
        return `By my reckoning your ${list(names)} should still be feeling ${when}. Is that where it is?`;
      }
      return null;
    }

    /* ---------- taking an answer ---------- */
    answer(text, parsed) {
      const q = this.pending;
      if (!q) return null;
      this.pending = null;

      if (q.id === 'energy') {
        const n = parsed && parsed.energy != null ? parsed.energy
                : (VOS.Intent ? VOS.Intent.firstNumber(text) : null);
        if (n == null || n < 1 || n > 10) return { say: null, understood: false };
        this.answers.energy = n;
        if (this.session) this.session.checkin({ energy: n });
        return { say: this.energyReply(n), understood: true };
      }

      if (q.id === 'yesterday') {
        this.answers.yesterday = text;
        if (this.memory) this.memory.addNote(`On ${dayName(this.lastAt)} session: ${text}`, 'session');
        return { say: 'Noted.', understood: true };
      }

      if (q.id === 'soreness') return this.soreness(text);
      return null;
    }

    energyReply(n) {
      if (n <= 3) return "That's low. I'll keep the volume down and we'll see how the first lift moves.";
      if (n <= 5) return "Alright. We'll start where we planned and I'll back it off if the bar says otherwise.";
      if (n >= 9) return 'Good. Today is a day to push the top set.';
      return 'Good.';
    }

    /* ------------------------------------------------------------
       The soreness answer, and what it changes
       ------------------------------------------------------------ */
    soreness(text) {
      const S = VOS.Soreness;
      if (!S) return { say: null, understood: false };
      const r = S.parse(text);
      const predictedIds = this.predicted.map(p => p.lead.id);
      const now = Date.now();
      const out = [];

      /* "Not really" — about the muscles we named. The model was high. */
      if (!r.mentioned && r.level === 0) {
        if (!predictedIds.length) {
          return { say: 'Good. Nothing to work around then.', understood: true };
        }
        predictedIds.forEach(id => S.record(this.memory, { muscle: id, level: 0, expected: true, at: now }));
        this.learned.push('recovered faster than estimated');
        this.followUp = null;
        return { say: "Good — you've come back quicker than I had you down for. I've cleared them on the map.", understood: true };
      }

      /* A bare grading with no muscle named answers the question we asked. */
      const targets = r.mentioned ? r.muscles : predictedIds;
      if (!targets.length) {
        this.followUp = 'elsewhere';
        return { say: null, understood: false };
      }

      const level = r.level == null ? 2 : r.level;

      /* Two ways an answer can be off-model, and both matter.

         The first is pragmatic: the question named two areas and the
         person named a different one. "Actually it's my lower back" is a
         correction whether or not they grade it, because they are
         telling you the work landed somewhere you did not ask about.

         The second is quantitative: they named an area the question did
         name, but far sorer than the model had it. Squats do load the
         lower back, so being sore there is not itself news — being much
         sorer there than expected is. */
      const asked = this.askedMuscles || predictedIds;
      const said = level / 3;
      const gap = id => said - ((this.modelState[id] && this.modelState[id].fatigue) || 0);
      const unexpected = targets
        .filter(id => level >= 2 && (!asked.includes(id) || gap(id) >= 0.35))
        .sort((a, b) => gap(b) - gap(a));

      /* "Expected" on a stored reading means the same thing it means in
         the reply: the model had this one right. The map reads it to
         decide whether to flag the muscle as a correction. */
      targets.forEach(id => S.record(this.memory, {
        muscle: id, level, expected: !unexpected.includes(id), at: now
      }));

      /* The interesting case: sore somewhere the model did not predict. */
      if (unexpected.length) {
        const id = unexpected[0];
        const name = ((VOS.MUSCLES[id] || {}).name || id).toLowerCase();
        const why = S.explain(this.history, id);
        this.surprises.push({ muscle: id, level, why });
        out.push(`Your ${name} is sorer than I had it — that is not where I expected the work to land.`);
        if (why && why.cause) out.push(`The most likely source is ${why.cause.name}.`);
        if (why && why.say) out.push(why.say);
        if (this.memory) {
          this.memory.addNote(`Reported unexpected soreness in ${name}${why && why.cause ? ` after ${why.cause.name}` : ''}.`, 'body');
        }
        const hit = this.hitsToday(id);
        if (hit) out.push(`${hit.name} is on today's list. I'll warm it up properly and we'll see how the first set feels before we commit.`);
        this.protect = id;
        return { say: out.join(' '), understood: true };
      }

      /* Confirming or grading the prediction. */
      if (level >= 3) {
        this.protect = targets[0];
        return { say: "That's a lot. I'll add some extra work for it in the warm-up and hold the loads where they are today.", understood: true };
      }
      if (level <= 1) {
        this.learned.push('less sore than estimated');
        return { say: "Lighter than I had it. Good — that means we can carry on as planned.", understood: true };
      }
      return { say: "That tracks. I'll factor it in.", understood: true };
    }

    /* Is a muscle worked by anything on today's list? */
    hitsToday(muscleId) {
      const s = this.session;
      if (!s || !VOS.muscles) return null;
      let best = null;
      (s.exercises || []).forEach(ex => {
        const c = VOS.muscles(ex.name)[muscleId] || 0;
        if (c >= 0.4 && (!best || c > best.c)) best = { name: ex.name, c };
      });
      return best;
    }

    /* Groups reported sore at 2+, for the warm-up to add work for. */
    soreGroups() {
      if (!VOS.Soreness || !this.memory) return [];
      const by = VOS.Soreness.latest(this.memory.data.soreness, 72);
      const groups = {};
      Object.keys(by).forEach(id => {
        if (by[id].level < 2) return;
        const g = (VOS.MUSCLES[id] || {}).group;
        if (g) groups[g] = Math.max(groups[g] || 0, by[id].level);
      });
      return Object.keys(groups).sort((a, b) => groups[b] - groups[a]);
    }

    /* What the check-in taught us, for the session summary. */
    report() {
      return {
        energy: this.answers.energy != null ? this.answers.energy : null,
        asked: Object.keys(this.asked),
        surprises: this.surprises,
        learned: this.learned,
        protecting: this.protect || null
      };
    }
  }

  VOS.CheckIn = CheckIn;
})(typeof globalThis !== 'undefined' ? globalThis : this);
