/* ============================================================
   55-safety.js : pain as a state, and substitution that knows why
   Pain is not a request to be handled like "skip this". It changes
   the mode the system is in, and nothing about the response to it is
   ever served from a pre-generated cache.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  /* Deterministic detection only for the unmistakable cases. Everything
     softer is left to the semantic layer, which can flag pain:true. */
  const PAIN = /\b(?:hurt|hurts|hurting|pain|painful|sharp|pinch|pinching|tweak|tweaked|twinge|strain|strained|pulled (?:a|my)|popped|clicking|grinding|numb|tingl)\w*\b/i;
  const BODY = /\b(shoulder|elbow|wrist|neck|back|lower back|hip|knee|ankle|groin|hamstring|quad|chest|bicep|tricep|lat|glute|calf|foot|hand|rib|spine|disc)\b/i;
  /* Ordinary training discomfort — must not trigger the safety path. */
  const NOT_PAIN = /\b(?:burn|burning|pump|sore|soreness|doms|tired|fatigued|heavy|tough|brutal|dead|smoked|cooked|wrecked)\b/i;

  function detect(text) {
    const t = (text || '').toLowerCase();
    if (!PAIN.test(t)) return null;
    // "my legs are burning" or "that was brutal" is not an injury
    if (NOT_PAIN.test(t) && !/\b(sharp|pinch|pain|hurts?)\b/.test(t)) return null;
    const site = (t.match(BODY) || [])[1] || null;
    const sharp = /\b(sharp|pinch|pinching|popped|numb|tingl)\w*\b/.test(t);
    return { site, severity: sharp ? 'sharp' : 'ache', text, confidence: sharp ? 0.95 : 0.8 };
  }

  /* ---------- movement grouping, derived from the pattern field ---------- */
  const GROUP = {
    'horizontal push':'push', 'incline push':'push', 'decline push':'push',
    'vertical push':'push', 'chest isolation':'push', 'triceps':'push', 'delt isolation':'push',
    'horizontal pull':'pull', 'vertical pull':'pull', 'lats':'pull', 'rear delt':'pull',
    'biceps':'pull', 'traps':'pull',
    'squat':'quad', 'lunge':'quad', 'quads':'quad',
    'hinge':'posterior', 'hamstrings':'posterior', 'lower back':'posterior',
    'calves':'calves', 'core':'core', 'carry':'core'
  };
  const group = name => GROUP[VOS.exerciseMeta(name).pattern] || 'other';

  /* Joints a pattern loads hardest — used to steer away from a painful one. */
  const JOINT_LOAD = {
    'horizontal push': ['shoulder','elbow','chest','wrist'],
    'incline push':    ['shoulder','elbow','chest'],
    'decline push':    ['shoulder','elbow','chest'],
    'vertical push':   ['shoulder','elbow','neck','wrist'],
    'chest isolation': ['shoulder','chest'],
    'triceps':         ['elbow'],
    'delt isolation':  ['shoulder'],
    'horizontal pull': ['elbow','shoulder','lower back','back'],
    'vertical pull':   ['shoulder','elbow','lat'],
    'lats':            ['shoulder','lat'],
    'rear delt':       ['shoulder'],
    'biceps':          ['elbow','bicep'],
    'traps':           ['neck','shoulder'],
    'squat':           ['knee','hip','back','lower back'],
    'lunge':           ['knee','hip','ankle','groin'],
    'quads':           ['knee','quad'],
    'hinge':           ['lower back','back','hamstring','hip','spine','disc'],
    'hamstrings':      ['hamstring','knee'],
    'lower back':      ['lower back','back','spine'],
    'calves':          ['calf','ankle','foot'],
    'core':            ['back','rib','spine'],
    'carry':           ['hand','wrist','neck','back']
  };

  const EASIER_LOAD = { machine: 3, cable: 2, bodyweight: 2, dumbbell: 1, barbell: 0 };

  /**
   * Rank replacements for an exercise, given WHY it is being replaced.
   * reason: dislike | equipment_busy | no_equipment | pain | too_hard | time
   * -> [{ name, score, why }]
   */
  function candidates(exName, reason, opts) {
    opts = opts || {};
    const meta = VOS.exerciseMeta(exName);
    const g = group(exName);
    const painSite = (opts.painSite || '').toLowerCase();
    const loaded = JOINT_LOAD[meta.pattern] || [];
    const avoid = new Set([exName].concat(opts.exclude || []));

    const out = [];
    Object.keys(VOS.EXERCISES).forEach(name => {
      if (avoid.has(name)) return;
      const m = VOS.exerciseMeta(name);
      const samePattern = m.pattern === meta.pattern;
      const sameGroup = group(name) === g;
      if (!sameGroup) return;                                  // never cross training objectives

      let score = sameGroup ? 30 : 0;
      const why = [];
      if (samePattern) { score += 30; why.push('same movement pattern'); }
      else why.push('same muscle group');

      if (m.kind === meta.kind) score += 8;

      switch (reason) {
        case 'equipment_busy':
          if (m.load !== meta.load) { score += 25; why.push(`uses ${m.load} instead`); }
          else score -= 20;
          break;

        case 'no_equipment': {
          const have = opts.available || ['bodyweight', 'dumbbell', 'machine', 'cable'];
          if (!have.includes(m.load)) return;
          score += 15; why.push(`only needs ${m.load}`);
          break;
        }

        case 'pain': {
          // steer away from whatever loads the painful joint
          const nl = JOINT_LOAD[m.pattern] || [];
          if (painSite && nl.some(j => painSite.includes(j) || j.includes(painSite))) {
            score -= 45; why.push('still loads that area');
          } else if (painSite && loaded.some(j => painSite.includes(j))) {
            score += 25; why.push('takes the load off that joint');
          }
          if (m.kind === 'isolation') { score += 12; why.push('less demanding'); }
          if (m.technique === 'high') { score -= 20; why.push('technically demanding'); }
          score += (EASIER_LOAD[m.load] || 0) * 5;
          break;
        }

        case 'too_hard':
          score += (EASIER_LOAD[m.load] || 0) * 6;
          if (m.technique === 'high') score -= 15;
          if (EASIER_LOAD[m.load] > EASIER_LOAD[meta.load]) why.push('easier to control');
          break;

        case 'time':
          if (m.rest < meta.rest) { score += (meta.rest - m.rest) / 10; why.push('shorter rest'); }
          break;

        case 'dislike':
        default:
          if (m.load !== meta.load) { score += 6; why.push('different feel'); }
          break;
      }

      // a hand-curated pairing beats anything the scorer works out
      if ((VOS.SUBSTITUTES[exName] || []).includes(name)) { score += 18; why.push('common swap'); }

      out.push({ name, score: Math.round(score), why });
    });

    out.sort((a, b) => b.score - a.score);
    return out.slice(0, 5);
  }

  function best(exName, reason, opts) {
    const c = candidates(exName, reason, opts);
    return c.length ? c[0] : null;
  }

  /* ---------- the safety state itself ---------- */
  const Safety = {
    detect,
    candidates,
    best,
    group,

    /* Entering safety mode stops the current exercise and clears any
       pre-generated speech, which by definition predates the pain. */
    enter(session, info) {
      session.safety = {
        at: Date.now(),
        site: info.site || null,
        severity: info.severity || 'ache',
        text: info.text,
        exercise: session.current ? session.current.name : null,
        resolved: false
      };
      session.log('safety_enter', { site: info.site, severity: info.severity, exercise: session.safety.exercise });
      session.addNote(`Reported ${info.severity === 'sharp' ? 'sharp ' : ''}pain${info.site ? ' in the ' + info.site : ''} during ${session.safety.exercise || 'the session'}.`);
      return session.safety;
    },

    exit(session, how) {
      if (!session.safety) return;
      session.safety.resolved = true;
      session.safety.resolution = how;
      session.log('safety_exit', { how });
    },

    active(session) { return !!(session.safety && !session.safety.resolved); },

    /* Written live, never cached. Deliberately does not diagnose. */
    prompt(session) {
      const s = session.safety;
      const where = s.site ? `your ${s.site}` : 'that';
      if (s.severity === 'sharp') {
        return `Stop that set. Sharp pain in ${where} isn't something to push through, and I can't see what's going on — I'd get it looked at if it keeps up. Do you want to skip this exercise, swap it for something that leaves ${where} alone, or call it a day?`;
      }
      return `Let's not push through pain in ${where}. I can't assess it and I'm not going to guess. Skip this one, swap it for something easier on ${where}, or stop here?`;
    },

    options(session) {
      const ex = session.current;
      const alt = ex ? best(ex.name, 'pain', { painSite: session.safety && session.safety.site }) : null;
      return { skip: true, substitute: alt ? alt.name : null, substituteWhy: alt ? alt.why : null, end: true };
    }
  };

  VOS.Safety = Safety;
  VOS.MOVEMENT_GROUP = GROUP;
  VOS.JOINT_LOAD = JOINT_LOAD;
})(typeof globalThis !== 'undefined' ? globalThis : this);
