/* ============================================================
   18-routines.js : the programme library
   Original programmes. Each is a set of named days the engine can
   run unchanged, plus enough metadata for the picker to filter by
   what the user actually has and can commit to.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  // name, sets, low, high, startKg, priority
  const e = (name, sets, lo, hi, kg, pr) => ({ name, sets, repLow: lo, repHigh: hi, weight: kg || 0, priority: pr || 2, needsWeight: kg == null });
  const day = (name, ...ex) => ({ name, exercises: ex });

  const LIB = [
    {
      id: 'groundwork', name: 'Groundwork', weeks: 8, daysPerWeek: 3,
      tagline: 'Learn the six patterns and turn up three times a week.',
      level: 'beginner', equipment: 'minimal', focus: 'general',
      about: 'Three full-body sessions built on one push, one pull and one leg pattern each. Loads start conservative on purpose — the job for eight weeks is turning up and owning the movement.',
      days: [
        day('Full Body A',
          e('Goblet Squat', 3, 8, 10, null, 1), e('Dumbbell Bench Press', 3, 8, 10, null, 1),
          e('Seated Cable Row', 3, 10, 12, null, 1), e('Leg Curl', 2, 10, 12, null, 2),
          e('Plank', 2, 30, 45, 0, 3)),
        day('Full Body B',
          e('Romanian Deadlift', 3, 8, 10, null, 1), e('Lat Pulldown', 3, 10, 12, null, 1),
          e('Overhead Press', 3, 8, 10, null, 1), e('Leg Press', 2, 10, 12, null, 2),
          e('Dumbbell Curl', 2, 10, 12, null, 3)),
        day('Full Body C',
          e('Leg Press', 3, 10, 12, null, 1), e('Push-up', 3, 8, 12, 0, 1),
          e('Chest Supported Row', 3, 10, 12, null, 1), e('Lateral Raise', 2, 12, 15, null, 3),
          e('Calf Raise', 2, 12, 15, null, 3))
      ]
    },

    {
      id: 'upper_lower_4', name: 'Upper / Lower Four', weeks: 12, daysPerWeek: 4,
      tagline: 'The default for anyone with four days and a full gym.',
      level: 'intermediate', equipment: 'full gym', focus: 'hypertrophy',
      about: 'Two upper and two lower days. Each half of the body gets a heavy day and a rep day, which is the simplest structure that still lets you add weight every week.',
      days: [
        day('Upper A',
          e('Bench Press', 3, 8, 10, 60, 1), e('Barbell Row', 3, 8, 10, 50, 1),
          e('Incline Dumbbell Press', 3, 10, 12, 20, 2), e('Lat Pulldown', 3, 10, 12, 45, 2),
          e('Lateral Raise', 3, 12, 15, 8, 3), e('Triceps Pushdown', 3, 12, 15, 25, 3)),
        day('Lower A',
          e('Back Squat', 3, 6, 8, 80, 1), e('Romanian Deadlift', 3, 8, 10, 70, 1),
          e('Leg Press', 3, 10, 12, 120, 2), e('Leg Curl', 3, 10, 12, 35, 2),
          e('Calf Raise', 3, 12, 15, 60, 3)),
        day('Upper B',
          e('Overhead Press', 3, 6, 8, 40, 1), e('Pull-up', 3, 6, 10, 0, 1),
          e('Dumbbell Bench Press', 3, 10, 12, 24, 2), e('Seated Cable Row', 3, 10, 12, 50, 2),
          e('Face Pull', 3, 12, 15, 20, 3), e('Barbell Curl', 3, 10, 12, 25, 3)),
        day('Lower B',
          e('Deadlift', 3, 4, 6, 100, 1), e('Bulgarian Split Squat', 3, 8, 10, 16, 2),
          e('Leg Extension', 3, 12, 15, 40, 2), e('Hip Thrust', 3, 10, 12, 60, 2),
          e('Hanging Leg Raise', 3, 8, 12, 0, 3))
      ]
    },

    {
      id: 'ppl_6', name: 'Push Pull Legs Six', weeks: 12, daysPerWeek: 6,
      tagline: 'High frequency for people who genuinely train six days.',
      level: 'advanced', equipment: 'full gym', focus: 'hypertrophy',
      about: 'Every pattern twice a week. Only worth running if six days is realistic — five days of this beats six days of it half-done.',
      days: [
        day('Push',
          e('Bench Press', 4, 6, 8, 70, 1), e('Overhead Press', 3, 8, 10, 42.5, 1),
          e('Incline Dumbbell Press', 3, 10, 12, 24, 2), e('Cable Fly', 3, 12, 15, 15, 3),
          e('Lateral Raise', 3, 12, 15, 10, 3), e('Triceps Pushdown', 3, 12, 15, 30, 3)),
        day('Pull',
          e('Deadlift', 3, 4, 6, 120, 1), e('Pull-up', 4, 6, 10, 0, 1),
          e('Barbell Row', 3, 8, 10, 60, 2), e('Seated Cable Row', 3, 10, 12, 55, 2),
          e('Face Pull', 3, 15, 20, 22.5, 3), e('Barbell Curl', 3, 10, 12, 30, 3)),
        day('Legs',
          e('Back Squat', 4, 6, 8, 100, 1), e('Romanian Deadlift', 3, 8, 10, 80, 1),
          e('Leg Press', 3, 12, 15, 140, 2), e('Leg Curl', 3, 12, 15, 40, 2),
          e('Calf Raise', 4, 12, 15, 70, 3))
      ]
    },

    {
      id: 'bodyweight_ladder', name: 'Bodyweight Ladder', weeks: 10, daysPerWeek: 4,
      tagline: 'No equipment. Progress by reps, then by leverage.',
      level: 'beginner', equipment: 'none', focus: 'general',
      about: 'Four short sessions with nothing but the floor and something to hang from. When a movement gets easy you add reps; when reps get silly you make the movement harder.',
      days: [
        day('Push Day',
          e('Push-up', 4, 8, 20, 0, 1), e('Dip', 3, 6, 12, 0, 1),
          e('Plank', 3, 30, 60, 0, 2), e('Ab Wheel', 2, 6, 12, 0, 3)),
        day('Pull Day',
          e('Pull-up', 4, 3, 10, 0, 1), e('Chin-up', 3, 3, 10, 0, 1),
          e('Back Extension', 3, 12, 20, 0, 2), e('Hanging Leg Raise', 3, 6, 15, 0, 3)),
        day('Leg Day',
          e('Goblet Squat', 4, 12, 20, 0, 1), e('Walking Lunge', 3, 10, 16, 0, 1),
          e('Nordic Curl', 3, 3, 8, 0, 2), e('Calf Raise', 3, 15, 25, 0, 3)),
        day('Full Body',
          e('Push-up', 3, 10, 20, 0, 1), e('Pull-up', 3, 3, 10, 0, 1),
          e('Step Up', 3, 10, 15, 0, 2), e('Plank', 3, 45, 90, 0, 3))
      ]
    },

    {
      id: 'thirty_split', name: 'The Thirty', weeks: 8, daysPerWeek: 4,
      tagline: 'Four sessions, thirty minutes each, nothing wasted.',
      level: 'intermediate', equipment: 'full gym', focus: 'hypertrophy',
      about: 'Built backwards from the clock. Two hard compounds and one accessory per session, short rests on the accessory only. Designed to survive a lunch break.',
      days: [
        day('Upper Push',
          e('Bench Press', 4, 6, 8, 60, 1), e('Overhead Press', 3, 8, 10, 35, 1),
          e('Triceps Pushdown', 2, 12, 15, 25, 3)),
        day('Upper Pull',
          e('Barbell Row', 4, 6, 8, 55, 1), e('Lat Pulldown', 3, 10, 12, 45, 1),
          e('Barbell Curl', 2, 10, 12, 25, 3)),
        day('Lower Push',
          e('Back Squat', 4, 5, 8, 80, 1), e('Leg Press', 3, 10, 12, 120, 1),
          e('Calf Raise', 2, 12, 15, 60, 3)),
        day('Lower Pull',
          e('Romanian Deadlift', 4, 6, 8, 70, 1), e('Leg Curl', 3, 10, 12, 35, 1),
          e('Hanging Leg Raise', 2, 10, 15, 0, 3))
      ]
    },

    {
      id: 'dumbbells_only', name: 'Two Dumbbells', weeks: 10, daysPerWeek: 3,
      tagline: 'One pair of dumbbells, a bench optional.',
      level: 'beginner', equipment: 'dumbbells', focus: 'general',
      about: 'Everything here works with a single adjustable pair. If you only ever buy one piece of equipment, this is the programme that justifies it.',
      days: [
        day('Push',
          e('Dumbbell Bench Press', 4, 8, 12, null, 1), e('Arnold Press', 3, 8, 12, null, 1),
          e('Incline Dumbbell Press', 3, 10, 12, null, 2), e('Lateral Raise', 3, 12, 15, null, 3),
          e('Overhead Triceps Extension', 3, 10, 15, null, 3)),
        day('Pull',
          e('Single Arm Dumbbell Row', 4, 8, 12, null, 1), e('Romanian Deadlift', 3, 8, 12, null, 1),
          e('Rear Delt Fly', 3, 12, 15, null, 2), e('Hammer Curl', 3, 10, 12, null, 3),
          e('Shrug', 3, 12, 15, null, 3)),
        day('Legs and Core',
          e('Goblet Squat', 4, 10, 15, null, 1), e('Bulgarian Split Squat', 3, 8, 12, null, 1),
          e('Romanian Deadlift', 3, 10, 12, null, 2), e('Calf Raise', 3, 15, 20, null, 3),
          e('Plank', 3, 45, 75, 0, 3))
      ]
    },

    {
      id: 'strength_five', name: 'Strength Five', weeks: 12, daysPerWeek: 3,
      tagline: 'Low reps, long rests, one number that matters.',
      level: 'intermediate', equipment: 'barbell', focus: 'strength',
      about: 'Three barbell sessions in the five-rep range with genuine rest between sets. Accessories exist only to keep the main lifts moving.',
      days: [
        day('Squat Day',
          e('Back Squat', 5, 4, 6, 90, 1), e('Overhead Press', 3, 5, 6, 40, 1),
          e('Barbell Row', 3, 6, 8, 55, 2), e('Back Extension', 2, 10, 15, 0, 3)),
        day('Bench Day',
          e('Bench Press', 5, 4, 6, 70, 1), e('Front Squat', 3, 5, 6, 60, 1),
          e('Pull-up', 3, 5, 8, 0, 2), e('Skullcrusher', 2, 8, 12, 25, 3)),
        day('Pull Day',
          e('Deadlift', 4, 3, 5, 110, 1), e('Incline Bench Press', 3, 5, 8, 55, 1),
          e('Pendlay Row', 3, 6, 8, 55, 2), e('Farmer Carry', 3, 30, 45, 24, 3))
      ]
    },

    {
      id: 'posterior_rebuild', name: 'Back to Front', weeks: 8, daysPerWeek: 3,
      tagline: 'For everything a desk quietly switches off.',
      level: 'beginner', equipment: 'minimal', focus: 'general',
      about: 'Glutes, hamstrings, mid-back and rear delts, three times a week, with the pressing kept deliberately light. Aimed squarely at people who sit for a living.',
      days: [
        day('Hinge',
          e('Romanian Deadlift', 4, 8, 12, null, 1), e('Hip Thrust', 3, 10, 15, null, 1),
          e('Face Pull', 3, 15, 20, null, 2), e('Back Extension', 3, 12, 20, 0, 2),
          e('Plank', 2, 40, 60, 0, 3)),
        day('Row',
          e('Chest Supported Row', 4, 10, 12, null, 1), e('Lat Pulldown', 3, 10, 12, null, 1),
          e('Rear Delt Fly', 3, 15, 20, null, 2), e('Shrug', 3, 12, 15, null, 3),
          e('Ab Wheel', 2, 6, 12, 0, 3)),
        day('Glute and Core',
          e('Bulgarian Split Squat', 3, 10, 12, null, 1), e('Hip Thrust', 4, 10, 15, null, 1),
          e('Leg Curl', 3, 12, 15, null, 2), e('Cable Crunch', 3, 12, 15, null, 3),
          e('Farmer Carry', 2, 30, 45, null, 3))
      ]
    },

    {
      id: 'circuit_conditioning', name: 'Circuit Work', weeks: 6, daysPerWeek: 3,
      tagline: 'Higher reps, shorter rests, breathing hard on purpose.',
      level: 'intermediate', equipment: 'minimal', focus: 'fatloss',
      about: 'Compound movements in the twelve to twenty range with rest cut to about a minute. Loads stay moderate — the stimulus here is density, not weight on the bar.',
      days: [
        day('Circuit A',
          e('Goblet Squat', 4, 12, 20, null, 1), e('Push-up', 4, 12, 20, 0, 1),
          e('Seated Cable Row', 4, 12, 15, null, 1), e('Kettlebell Swing', 3, 15, 25, null, 2),
          e('Plank', 3, 45, 75, 0, 3)),
        day('Circuit B',
          e('Walking Lunge', 4, 12, 20, null, 1), e('Dumbbell Bench Press', 4, 12, 15, null, 1),
          e('Lat Pulldown', 4, 12, 15, null, 1), e('Step Up', 3, 12, 18, null, 2),
          e('Hanging Leg Raise', 3, 10, 15, 0, 3)),
        day('Circuit C',
          e('Leg Press', 4, 15, 20, null, 1), e('Arnold Press', 3, 12, 15, null, 1),
          e('Single Arm Dumbbell Row', 3, 12, 15, null, 1), e('Kettlebell Swing', 3, 20, 30, null, 2),
          e('Farmer Carry', 3, 40, 60, null, 3))
      ]
    },

    {
      id: 'hypertrophy_five', name: 'Five Day Split', weeks: 12, daysPerWeek: 5,
      tagline: 'One region a day, enough volume to matter.',
      level: 'advanced', equipment: 'full gym', focus: 'hypertrophy',
      about: 'A classic bodypart split for people who already know how hard a hard set is. High per-session volume, so it only works if recovery is genuinely handled.',
      days: [
        day('Chest',
          e('Bench Press', 4, 6, 8, 70, 1), e('Incline Dumbbell Press', 4, 8, 12, 24, 1),
          e('Machine Chest Press', 3, 10, 12, 50, 2), e('Cable Fly', 3, 12, 15, 15, 3),
          e('Dip', 3, 8, 12, 0, 3)),
        day('Back',
          e('Deadlift', 3, 4, 6, 120, 1), e('Pull-up', 4, 6, 10, 0, 1),
          e('T-Bar Row', 3, 8, 12, 50, 2), e('Straight Arm Pulldown', 3, 12, 15, 30, 3),
          e('Shrug', 3, 12, 15, 60, 3)),
        day('Legs',
          e('Back Squat', 4, 6, 8, 100, 1), e('Romanian Deadlift', 3, 8, 10, 80, 1),
          e('Hack Squat', 3, 10, 12, 80, 2), e('Leg Curl', 3, 12, 15, 40, 2),
          e('Seated Calf Raise', 4, 12, 20, 50, 3)),
        day('Shoulders',
          e('Overhead Press', 4, 6, 8, 42.5, 1), e('Arnold Press', 3, 10, 12, 20, 1),
          e('Lateral Raise', 4, 12, 20, 10, 2), e('Face Pull', 3, 15, 20, 22.5, 2),
          e('Rear Delt Fly', 3, 15, 20, 10, 3)),
        day('Arms',
          e('Close Grip Bench Press', 4, 8, 10, 55, 1), e('Barbell Curl', 4, 8, 12, 30, 1),
          e('Skullcrusher', 3, 10, 12, 25, 2), e('Hammer Curl', 3, 10, 12, 14, 2),
          e('Triceps Pushdown', 3, 12, 15, 30, 3), e('Preacher Curl', 3, 12, 15, 20, 3))
      ]
    }
  ];

  const byId = {};
  LIB.forEach(p => { byId[p.id] = p; });

  const EQUIP_RANK = { none: 0, dumbbells: 1, barbell: 2, minimal: 2, 'full gym': 3 };

  /* Rank the library against what the user told us in onboarding. */
  function suggest(profile) {
    const days = profile.daysPerWeek || 3;
    const have = EQUIP_RANK[profile.equipment] != null ? EQUIP_RANK[profile.equipment] : 3;
    const level = profile.level || 'beginner';
    const levelRank = { beginner: 0, intermediate: 1, advanced: 2 };
    const mins = profile.sessionMin || 60;

    return LIB.map(p => {
      let score = 100;
      score -= Math.abs(p.daysPerWeek - days) * 14;                       // fits the week
      const need = EQUIP_RANK[p.equipment] != null ? EQUIP_RANK[p.equipment] : 3;
      if (need > have) score -= (need - have) * 30;                        // cannot be done
      else score += 4;
      const gap = levelRank[p.level] - levelRank[level];
      score -= Math.abs(gap) * 16;
      /* Someone with no training history, or years away from it, should
         not be handed a programme pitched above them because the goal
         happens to match. Being ready for it comes first. */
      if (profile.askLoads && gap > 0) score -= gap * 30;
      if (profile.goal && p.focus === profile.goal) score += 18;
      if (mins <= 35 && p.id === 'thirty_split') score += 25;
      if (mins <= 35 && p.daysPerWeek >= 5) score -= 10;
      return { program: p, score: Math.round(score),
               why: reasons(p, profile, need > have) };
    }).sort((a, b) => b.score - a.score);
  }

  function reasons(p, profile, tooMuchKit) {
    const r = [];
    if (p.daysPerWeek === (profile.daysPerWeek || 3)) r.push(`${p.daysPerWeek} days, same as you said`);
    else r.push(`${p.daysPerWeek} days a week`);
    if (tooMuchKit) r.push(`needs ${p.equipment}`);
    else if (p.equipment === 'none') r.push('no equipment at all');
    else r.push(p.equipment);
    if (profile.goal && p.focus === profile.goal) r.push('matches your goal');
    return r;
  }

  VOS.ROUTINES = LIB;
  VOS.ROUTINE_BY_ID = byId;
  VOS.suggestRoutines = suggest;

  // the picker and the engine both read from here
  LIB.forEach(p => { VOS.PROGRAMS[p.id] = p; });
})(typeof globalThis !== 'undefined' ? globalThis : this);
