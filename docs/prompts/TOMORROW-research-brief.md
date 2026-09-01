# Tomorrow — exercise science research, BEFORE any agent work

Decision taken 2026-08-31: **do not run `pr14-exercise-bank.md` yet.** That prompt
tells the agent to carry the 61 prototype exercises over "unchanged". Rahul is
right that the equipment data has errors, so that instruction is wrong and must
be replaced with researched data.

## What we already know is wrong

Spot-check of `voice-workout-os-source/src/10-data.js`:

- `pec deck` is an alias for **Cable Fly**. A pec deck is a machine. Different
  machine, different setup, different availability.
- `reverse pec deck` is an alias for **Rear Delt Fly**, which is typed dumbbell.
- `pulldown` maps to **Lat Pulldown**, typed `machine` — it is a cable station.
- **Kettlebell Swing** and **Farmer Carry** are typed `dumbbell`. A kettlebell is
  its own tool and many gyms have one and not the other.
- **Chest Supported Row** typed `dumbbell` — usually a dedicated machine or a
  bench plus dumbbells.

## The root modelling flaw

`load` is a **single value**. Real exercises need a *set* of equipment, and
"barbell bench" vs "dumbbell bench" are different exercises with different
setups, not one exercise with a variable load. Substitution, "what can I do with
what's here", and home-gym users all break on a single-valued field.

Likely fix: replace `load` with `requiredEquipment: [...]` plus
`optionalEquipment: [...]`, and split variants into their own catalog entries.
Settle this before the agent writes the JSON — it is the schema, and changing it
later means rewriting every entry.

## Research scope for tomorrow

1. **Which exercises hit which muscles** — verify the 22 pattern maps and the
   per-exercise overrides against real sources (EMG literature where it exists,
   established coaching consensus where it does not). Record which numbers are
   evidence-backed and which are judgement; the epistemic stance rule applies to
   our own data too.
2. **Equipment, properly** — required vs optional, bar type, bench, rack,
   attachment. Fix every error above and audit all 61.
3. **Warm-ups** — what actually warms up a given movement pattern, general vs
   specific, and how a warm-up should be represented (own catalog entries, or a
   field on the exercise).
4. **Stretches per muscle group** — same question about representation, plus
   when they belong (pre, post, rest day) since that affects what the coach says.
5. **The science of programming** — what certified coaching curricula (NSCA CSCS,
   NASM, ACSM and similar) actually teach about set/rep/rest prescription,
   progression, volume landmarks and exercise ordering. This is what makes our
   templates defensible rather than copied.
6. **Technique-sensitivity** — which lifts genuinely warrant "get a human to
   watch this once", which is a product promise Rahul made.

## Then

Rewrite `pr14-exercise-bank.md` sections 2.2 and 2.4 against the researched data
and the new equipment model, and only then hand it to the agent.
