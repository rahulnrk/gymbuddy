# Audit: what is our judgement, and was it the right call?

Every unsourced decision, ranked by **how much it changes what a user is
actually told to do**. This is the burn-down list.

Totals: **29 `MVP HEURISTIC` constants** in `DECISIONS_AND_THRESHOLDS.md`, and
**554 catalog fields marked `judgement`** across 77 exercises. Nothing is
`MEASURED` for training science, which is correct and honest.

---

## Tier 1 — changes safety outcomes. Review these.

### `contraindications` — 37 exercises
Decides what the validator **BLOCKs**. Assigned by an AI in a single pass, never
reviewed by anyone with clinical training. Wrong one way, a user is blocked from
a lift that is fine for them; wrong the other, they are handed one that hurts.

**Verdict: the single riskiest judgement in the system.** The uploaded corpus
says of itself that it "should undergo expert review before being used for
automated high-stakes coaching". This is exactly that case. **Get a qualified
human to read these 37 rows** — about an hour of a physio's time.

### `consequenceOfFailure` — 61 exercises
Drives `STRENGTH_UNSPOTTED_REP_FLOOR` and the "have a human watch this once"
promise. The prototype already had one wrong (leg press typed `normal`). Same
review pass.

### `unilateral` — 61 exercises
Silently doubles counted volume — one logged set counts per side. A wrong flag
makes the weekly ledger wrong and every warning built on it wrong. **Purely
factual and cheap to verify.** A checklist, not a judgement. Do it.

---

## Tier 2 — changes which exercise you are given

### The seven scoring weights
`WEIGHT_GOAL_FIT 2.0`, `WEIGHT_MUSCLE_COVERAGE 2.0`, `WEIGHT_SKILL_MISMATCH 1.5`,
`WEIGHT_VARIETY 1.5`, `WEIGHT_FRESHNESS 1.0`, `WEIGHT_ROLE_FIT 1.0`,
`WEIGHT_PREFERENCE 0.8`.

No literature exists for these and none will. **But we already have evidence
they are miscalibrated**, from reading printed weeks:

- The beginner hinge came out **Dumbbell Good Morning** (9.0) over **Dumbbell
  RDL** (7.0). Both moderate skill, moderate consequence. Most coaches would give
  a beginner the RDL. The scorer cannot separate them on anything that matters,
  so muscle-coverage arithmetic decided.
- Consequence-first tie-breaking then routed a beginner in a **full gym** to
  Machine Chest Press and Push-up, never a dumbbell press.

**Verdict: not wrong, but not what a coach would pick.** Calibrate against a set
of known-good expected outputs — "for this profile a coach would pick roughly
this" — rather than adjusting by feel again. Half a day, highest-leverage tuning
available.

### `FRACTIONAL_SET_PRIMARY_MIN = 0.75` / `FRACTIONAL_SET_INDIRECT_MIN = 0.30`
Pelland 2025 gives the **0.5 weight** for indirect sets and that is sourced. It
does **not** give the band edges — the papers classify direct/indirect by human
judgement. Ours are invented.

These decide what counts toward every volume landmark, so they drive every
warning in the product. Earlier measurement: moving the old single cutoff between
0.3 and 0.7 swung a push day's triceps tally from 16 sets to 4.

**Verdict: right shape, untested sensitivity.** Run the three profiles at
0.25/0.35, 0.30/0.75 and 0.40/0.80 and see whether any warning flips. If nothing
moves, stop worrying.

### `skillDemand` — 61 exercises
Feeds the beginner penalty. Less dangerous than `consequenceOfFailure`, same
review pass.

---

## Tier 3 — changes the numbers you are told

| Constant | Value | Assessment |
|---|---|---|
| `SESSION_SETS_*` | 2 beginner / 3 otherwise, every role | ACSM 2026 says 2-3 sets per exercise, so it sits inside guidance. But it is **flat** — a primary compound and a plank get the same. Crude, safe, fine for now. |
| `SECONDS_PER_WORKING_SET = 90` | was 40 | Changed 2.25x in one step, by feel, twice. Only affects the duration estimate and how many slots survive the clamp. Better scaled by `kind` or `fatigueCost` than held as one constant. Cheap, low risk. |
| `ENDURANCE_HARD_SETS 8-16`, `GENERAL_FITNESS_HARD_SETS 8-16` | | Sits sensibly between beginner hypertrophy (4-10) and hypertrophy (10-20). **Leave it.** |
| `GENERAL_FITNESS_REPS 8-15` | | ACSM's long-standing general-health guidance is 8-12; 8-15 is a wider, safe band. **Leave it.** |
| `GENERAL_FITNESS_REST 60-180` | | Fine. **Leave it.** |
| `repWindow` — 61 exercises | | Mostly conventional per lift. Low risk. |

---

## Tier 4 — product feel. Leave alone.

`ORDER_SKILL 300/200/100`, `ORDER_FATIGUE 30/20/10`, `ORDER_COMPOUND_BONUS 3`,
`ORDER_CORE_PENALTY 50` are sort keys, not physiological claims. What matters is
the **ratio** — skill outranks fatigue by 10x, matching NSCA's "high-skill and
heavy work while fresh". Correct by shape.

`DEFER_CAP 2`, `WAIT_VS_REST`, `FATIGUE_COOKED 0.7`, `EXTRA_RIR_WHEN_COOKED 1`,
`MINUTES_PER_SLOT 12`, `MIN_EXERCISES_PER_DAY 2`, `safeToSpeakMidSet`, `romNote`
— product feel, cheap to change, none can hurt anyone.

`requiredEquipment` — 77 exercises. Marked judgement but **factual and
self-correcting**: if wrong, a user finds out immediately and it is a one-row
fix. Not a research problem.

**Templates** are marked heuristic, but full body, upper/lower and PPL are the
standard splits in every textbook. Conventional, not invented.

---

## What to actually do, in order

1. **Get a physio or S&C coach to review the 37 contraindication rows and the
   `consequenceOfFailure` column.** One hour of qualified human time removes the
   only judgement in the system that can hurt someone — and it is exactly the
   expert review the source corpus says it needs.
2. **Verify `unilateral` on all 77.** Factual checklist, no expertise needed,
   fixes the volume ledger.
3. **Calibrate the seven scoring weights against expected outputs**, not by feel.
   We have concrete evidence of miscalibration to work from.
4. **Sensitivity-test the fractional band edges.** May be a non-issue.
5. Everything else: leave it. Labelled, cheap to change, not worth research time.

## The honest summary

The skeleton — sets, reps, rest, effort, frequency, deload, ordering, screening,
referral — is science-backed and cited. The flesh — which exercise, exactly how
risky, exactly how many degrees — is expert judgement, honestly marked.

That is how real coaching works. The difference here is you can see which is
which, and the build fails if a number appears without saying where it came from.

**One caveat, plainly: "our judgement" means an AI's judgement reviewed by one
non-specialist.** For Tiers 3 and 4 that is fine. For Tier 1 it is not, and item
1 above is the fix.
