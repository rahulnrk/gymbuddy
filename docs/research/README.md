# Research inputs — reference only

Added 2026-09-01. These are **reference material, not source data.** Nothing in
this folder is copied into `knowledge/`. We author our own catalog and check it
against these.

## Why "reference only"

`docs/prompts/pr14-exercise-bank.md` resolves Q9 by declaring the catalog
authored in-repo with no third-party content or licence. Copying rows out of
these files would make that statement false. `Complete_Coach_Fitness_Knowledge_Base.xlsx`
contains 816 rows from the public-domain Free Exercise DB and **1316 rows from
an unnamed "Exercise catalog A"** with no stated provenance.

Both bundles are AI-synthesized and say so. The v2 file's own note: *"curated/
synthesized v1 and should undergo expert review before being used for automated
high-stakes coaching."* Citations are framework-level, not claim-level.

## What is here

| File | What it is | What it is good for |
|---|---|---|
| `Coach_and_AI_Programming_Guide.pdf` | Decision-layer guide, 4 pages | **The best thing here.** Concrete programming landmarks — see below. |
| `exercise_knowledge_base_1265_v2.json` | 729 strength + 236 cardio + 150 stretch + 150 warm-up + 49 muscles | Family/variation model; cross-check for muscle and equipment fields |
| `Complete_Coach_Fitness_Knowledge_Base.xlsx` | 2162 movements, 15 sheets | Breadth check; `Minimum_gear` column is a usable required-equipment hint |
| `AI_Gym_Coach_Complete_Knowledge_Architecture.md` | 30-domain architecture essay | Scope checklist — what a coach must know |
| `ai_coach_knowledge.pdf` | 2-page framework summary | Overlaps the above |
| `exercise_curriculum.pdf` | 3-page biomechanics + per-exercise error tables | Form-error cross-check |

Deduplicated on the way in: two PDFs were uploaded twice, and the `1029_v1`
bundle is a strict subset of `1265_v2` (identical for muscles, strength,
stretches, warm-ups and programming rules; v2 only adds cardio). An xlsx twin of
the v2 JSON was also dropped — same data, re-derivable.

## Known quality limits (found by inspection, not stated by the docs)

- v2 `Stabilizers` is empty in all 729 rows.
- v2 `Evidence Level` is the constant string `"Moderate/biomechanical"` in all
  729 rows. It carries no information.
- Coaching text is templated, not per-exercise: 729 rows share 97 cue strings,
  24 safety notes, 24 error strings. The master catalog's 2162 rows share 20
  error strings. These are pattern-level defaults, which is why our schema marks
  such fields `basis: inherited`.
- The 150 warm-ups and 150 stretches are one-per-muscle generated text.
- `Evidence_Sources` cites an "ACSM 2026 Position Stand" — **verified real**
  on 2026-09-01: Currier et al. (2026), *ACSM Position Stand: Resistance
  Training Prescription for Muscle Function, Hypertrophy, and Physical
  Performance in Healthy Adults: An Overview of Reviews*, Med Sci Sports Exerc.
  137 systematic reviews, >30,000 participants. The bundle described it
  accurately.

## What it does answer well

`Coach_and_AI_Programming_Guide.pdf` gives defensible programming numbers:
~10–20 hard sets per muscle per week for intermediates; most work at RPE 6–8
(2–4 reps in reserve); each pattern 2–3× per week; rest 3–5 min strength /
1–3 min hypertrophy / 30–90 s metabolic; 150–300 min moderate aerobic per week;
deload every 4–8 weeks at −40–60% volume; compound before isolation. These
belong in the rules layer, each citing its source.

## What it cannot replace

`voice-workout-os-source/src/12-muscles.js` carries **numeric muscle
contribution weights** (`chest_mid:1, delt_front:.6, triceps:.6`). Neither
bundle has weights — only prose "primary / secondary". The prototype's weighted
model stays canonical.

Likewise `17-recovery.js` (per-muscle base hours × dose × damage multiplier) is
better than anything here. Keep it.

## If something is missing

Do not invent it and do not copy it from these files. Add it to the gaps list in
the PR report and we will research it properly.

## Primary-source research done from this folder

`programming-numbers-2026-09-01.md` — the sourced replacements for the
programming rules' guessed constants, from named papers rather than these
bundles. Read it before changing any number in
`docs/living-spec/DECISIONS_AND_THRESHOLDS.md`.
