/* ============================================================
   80-memory.js : persistent training memory
   localStorage when available, in-memory fallback otherwise
   (so the page still works inside sandboxed preview frames).
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});
  const KEY = 'vos.v1';

  let backing = null;
  let usingLocal = false;
  try {
    root.localStorage.setItem('vos.probe', '1');
    root.localStorage.removeItem('vos.probe');
    usingLocal = true;
  } catch (e) { usingLocal = false; }

  const DEFAULTS = {
    profile: {
      name: '', sex: 'male', age: null, dob: null, onboarded: false, frequencyHabit: null,
      startingPoint: null,   // new | returning | patchy | regular | years
      askLoads: false,       // never trained, or back after a long break
      goal: 'hypertrophy',
      level: 'intermediate',
      daysPerWeek: 4,
      sessionMin: 60,
      equipment: 'full gym',
      dislikes: [],
      programId: 'upper_lower_4',
      customProgram: null,
      nextDayIndex: 0
    },
    history: [],     // completed session summaries
    notes: [],       // durable things the user told the agent
    soreness: [],    // what the person actually reported feeling — see 21-soreness.js
    settings: {
      apiKey: '',
      model: '',
      voiceName: '',
      rate: 1.06,
      handsFree: true,
      duckTo: 0.18,
      lang: 'en-GB',
      units: 'kg'
    }
  };

  function clone(o) { return JSON.parse(JSON.stringify(o)); }

  const Memory = {
    usingLocalStorage: usingLocal,
    data: null,

    load() {
      if (this.data) return this.data;
      let raw = null;
      try { raw = usingLocal ? root.localStorage.getItem(KEY) : backing; } catch (e) {}
      let parsed = null;
      try { parsed = raw ? JSON.parse(raw) : null; } catch (e) { parsed = null; }
      this.data = Object.assign(clone(DEFAULTS), parsed || {});
      this.data.profile  = Object.assign(clone(DEFAULTS.profile),  (parsed && parsed.profile)  || {});
      this.data.settings = Object.assign(clone(DEFAULTS.settings), (parsed && parsed.settings) || {});
      this.data.history  = (parsed && parsed.history) || [];
      this.data.notes    = (parsed && parsed.notes) || [];
      this.data.soreness = (parsed && parsed.soreness) || [];
      return this.data;
    },

    save() {
      const raw = JSON.stringify(this.data);
      try { usingLocal ? root.localStorage.setItem(KEY, raw) : (backing = raw); }
      catch (e) { backing = raw; }
      return this;
    },

    reset() {
      this.data = clone(DEFAULTS);
      this.save();
    },

    program() {
      const p = this.data.profile;
      if (p.programId === 'custom' && p.customProgram) return p.customProgram;
      return VOS.PROGRAMS[p.programId] || VOS.PROGRAMS.upper_lower_4;
    },

    addSession(summary) {
      summary.date = new Date().toISOString();
      this.data.history.push(summary);
      if (this.data.history.length > 200) this.data.history = this.data.history.slice(-200);
      const prog = this.program();
      this.data.profile.nextDayIndex = ((this.data.profile.nextDayIndex || 0) + 1) % prog.days.length;
      this.save();
    },

    addNote(text, kind) {
      this.data.notes.push({ t: Date.now(), text, kind: kind || 'general' });
      if (this.data.notes.length > 120) this.data.notes = this.data.notes.slice(-120);
      // A stated dislike should change the programme, not just sit in a note.
      if (/\b(?:dislikes?|hates?|doesn'?t like|don'?t like|avoid)\b/i.test(text)) {
        Object.keys(VOS.EXERCISES).forEach(n => {
          if (new RegExp(n.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&'), 'i').test(text)) this.addDislike(n);
        });
      }
      this.save();
    },

    addDislike(name) {
      if (!this.data.profile.dislikes.includes(name)) {
        this.data.profile.dislikes.push(name);
        this.save();
      }
    },

    /* What the LLM gets as "longer-term memory" — deliberately small. */
    forLLM() {
      const d = this.data;
      const recent = d.history.slice(-6).map(h => ({
        date: (h.date || '').slice(0, 10),
        day: h.day,
        min: Math.round((h.durationSec || 0) / 60),
        energy: h.energy,
        endFatigue: h.endFatigue,
        top: (h.exercises || []).filter(e => e.sets && e.sets.length).slice(0, 4).map(e =>
          `${e.name} ${e.sets.map(s => `${s.weight}x${s.reps}${s.rpe ? '@' + s.rpe : ''}`).join(' ')}`)
      }));
      return {
        goal: d.profile.goal,
        level: d.profile.level,
        trainsPerWeek: d.profile.daysPerWeek,
        typicalSessionMin: d.profile.sessionMin,
        equipment: d.profile.equipment,
        dislikes: d.profile.dislikes,
        notes: d.notes.slice(-12).map(n => n.text),
        recentSessions: recent,
        sessionsLogged: d.history.length,
        // so the agent can say "your hamstrings are still recovering" without inventing it
        /* What they actually told us, kept separate from what we worked
           out — the model should know which is which before it opines. */
        reportedSoreness: (function () {
          if (!VOS.Soreness) return null;
          const by = VOS.Soreness.latest(d.soreness, 72);
          const words = ['not sore', 'slightly sore', 'sore', 'very sore'];
          return Object.keys(by).map(id => {
            const r = by[id];
            const h = Math.round((Date.now() - r.t) / 3600000);
            return `${(VOS.MUSCLES[id] || {}).name || id}: ${words[r.level] || r.level} (said ${h}h ago${r.expected ? '' : ', not where we expected'})`;
          }).slice(0, 8);
        })(),
        recovery: (function () {
          if (!VOS.Recovery) return null;
          const st = VOS.Recovery.state(d.history, null, { reports: d.soreness });
          const sore = VOS.Recovery.stillSore(st).slice(0, 5)
            .map(x => `${x.name} ${Math.round(x.fatigue * 100)}% (ready in ${VOS.Recovery.fmtH(x.readyInH)})`);
          return { stillRecovering: sore, freshest: VOS.Recovery.readyNow(st).slice(0, 5).map(x => x.name) };
        })()
      };
    },

    /* Fatigue trend across the last few sessions — used for honest observations. */
    fatigueTrend() {
      const h = this.data.history.slice(-3).filter(x => x.energy != null);
      if (h.length < 3) return null;
      const e = h.map(x => x.energy);
      const falling = e[0] > e[1] && e[1] > e[2];
      return { energies: e, falling };
    },

    exportJSON() { return JSON.stringify(this.data, null, 2); },

    importJSON(text) {
      const parsed = JSON.parse(text);
      this.data = Object.assign(clone(DEFAULTS), parsed);
      this.save();
    }
  };

  VOS.Memory = Memory;
})(typeof globalThis !== 'undefined' ? globalThis : this);
