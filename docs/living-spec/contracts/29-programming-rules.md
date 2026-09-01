---
id: 29-programming-rules
status: implemented
schemaVersion: 1
source: docs/prompts/pr-programming-rules.md
---

## Feature: Programming rules

**Status:** implemented  
**Core-loop role:** Supporting knowledge for later coaching and for the template engine (contract 16). These functions describe, adjust, and refer. They do not generate a workout, do not speak in Gym Mode, and do not mutate a programme. Contract 16 remains product law: one engine, template-based.

### Hard-set definition

A **hard set** is a working set taken close enough to failure to drive adaptation. Warm-up and ramp sets do not count toward weekly volume.

### Catalog-wins precedence

When an exercise is known, catalog `defaultRestSec` and `repWindow` win over the goal-level `restSeconds` / `repRange` defaults. The catalog is more specific. Goal defaults apply only when there is no exercise, or as the range the validator compares a non-catalog prescription against. A prescription that equals the catalog value is justified even when it sits outside the goal range.

### One-or-two-variables progression

Double progression is the default: fill the rep range at the target effort, then add load (caller-supplied increment) and drop back to the bottom of the range. Change **one or two variables at a time**, never more. `nextSession` returns a single `ProgressionDecision` variant, so one call changes at most one variable. Technique degradation outranks progression: broken pattern → `Hold` or `RegressExercise`, never `AddLoadKg`. Load increments are not a science constant; they come from the caller.

### Severity

| Severity | Meaning |
|---|---|
| `INFO` | Coach-worthy observation. Volume on the light side; frequency below 2–3. |
| `WARN` | The plan fights the landmarks (volume high, push-only week, order class inversion, rest/reps unjustified). |
| `BLOCK` | Safety only. A contraindicated exercise is present for a declared condition. |

The validator **reports**; it does not rewrite a plan. Automatic programme mutation is forbidden by `AGENTS.md`.

### User behavior

Does not surface as its own screen. A later generator (contract 16 templates) and the coaching loop may ask these functions “how many hard sets?” and “does this week violate a rule?”. The user hears the `Violation.message`, written as a coach would speak it, never a rule id.

### System state

Pure functions in `com.gymbuddy.domain.programming`. No clock, no randomness, no file IO, no SQL, no Android. Inputs are `Goal`, `TrainingAge`, catalog `Exercise`, `PlannedSession` / week, and `UserContext` (programming-time; not the frozen `UserStateContext` snapshot).

`weeklyHardSets` returns a `Landmark` (a range, never a point invented for convenience). Hypertrophy, intermediate: 10–20 (`PUBLISHED GUIDELINE`, Currier et al. 2026; Baz-Valle 2022; Schoenfeld 2017). Beginners: 4–10 (`GUIDELINE-DERIVED` — Behm 2023 / Peterson 2005 state a low dose works; 4–10 is ours). Strength, intermediate: 4–10 (`GUIDELINE-DERIVED` — ACSM 2026 states about 4–6 weekly sets; the 10 is ours). Fat-loss volume copies hypertrophy (`PUBLISHED GUIDELINE`, Roth 2022). Fat-loss *reps* inherit hypertrophy (`PUBLISHED GUIDELINE`, Carlson 2022 — about load). Fat-loss *rest* inherits hypertrophy as a `PRODUCT DECISION` (Carlson is not a rest paper).

**Fractional set counting** (Pelland et al. 2025; Hamaïde 2025; Joachim 2025): contribution ≥ 0.75 credits 1.0 sets, 0.30–0.75 credits 0.5, below 0.30 credits 0. The 0.5 indirect weight is `PUBLISHED GUIDELINE`; the 0.75 / 0.30 band edges are `MVP HEURISTIC`. Unilateral: one logged set counts as one set per side. It is not halved.

**Effort.** Most work at RIR 2–4 for every training age (`PUBLISHED GUIDELINE`, ACSM 2026 / Guide §4.3). There is no beginner RIR band — novices estimate RIR as well as trained lifters (Bermúdez Droguett 2025; Zourdos 2019; Lovegrove 2022). `reportedEffortConfidence`: trust a spoken RIR only when it is ≤ 3 and the set is ≤ 12 reps; otherwise low confidence and it must not drive a load change on its own (`GUIDELINE-DERIVED`, Zourdos 2019 — paper states error grows; cutoffs are ours). Chronological older adults would be low-confidence regardless (Gómez-Redondo 2025); the app has no older-adult input today.

**Unspotted floor.** Strength default is 5–8 reps (`PUBLISHED GUIDELINE`, ACSM 2026 ≥80% 1RM). Prescribing below 6 reps requires a catch in the user's equipment **only when the exercise lists a rack/cage/Smith** in required or optional gear. High `consequenceOfFailure` alone is not enough — a deadlift miss is a drop. A human spotter is not equipment; it lives on intake as `trainsWithPartner` (unknown is not yes). The validator warns; it does not rewrite the plan.

This catch-listing rule must be **re-examined when the catalog grows past 61**. A future lift could be high-consequence, not droppable, and list no catch, and the current predicate would fail to floor it.

**Rest.** Strength rest depends on training age: untrained 60–120 s (Grgic 2017); trained compounds 180–300 s; trained isolation 120–180 s (Senna 2016). Hypertrophy rest 90–180 s (Singer 2024).

**Order.** `order.freshness` warns only when the next lift is a higher class than the current one: compound (2) > isolation (1) > core (0). A chin-up after a moderate hinge is two compounds and is silent. Fly then bench still WARNs. Core last is still a fail if a calf raise follows a plank. `orderScore` remains a total-order helper; it is not the WARN trigger.

### Voice behavior

**Not in Gym Mode.** Messages are speakable so a later coaching PR can read them aloud. This PR does not call TTS.

### Structured event

None. Rules and validator do not append to the event log.

### Database change

None.

### UI state

None. No `:app` change.

### Error / recovery behavior

Unknown catalog muscle/condition ids fall back to a plain-language name, never a crash. Missing recovery signals (all null) make `deloadDue` calendar-only. Missing RIR on `SetPerformance` is treated as unobserved, not as “easy”. A contraindication is `BLOCK`; everything else is `WARN` or `INFO`. The validator never throws to “fix” a plan.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens — this PR emits no workout events and must not be asked for `29-*.event.*` fixtures.

1. Every public rule function returns a `Landmark` / range carrying a `Provenance`, and no `Provenance.basis` is left blank.
2. Every `const val` in the programming package appears in `DECISIONS_AND_THRESHOLDS.md`. A constant with no provenance row fails the build.
3. Beginner hypertrophy volume is strictly below intermediate.
4. Catalog `defaultRestSec` and `repWindow` win over the goal defaults.
5. Double progression: filling the rep range at target effort → `AddLoadKg`; short of it → `AddReps`; degraded technique → `Hold` or `RegressExercise`, never `AddLoadKg`. Load step comes from the caller.
6. Progression never changes more than two variables at once (in practice one, because the decision is a single variant).
7. `deloadDue` fires by week 8 with no signals, and earlier with poor signals (from week 4).
8. `validateWeek` flags a push-only week for pattern imbalance.
9. A contraindicated exercise for a declared condition produces `BLOCK`.
10. Unilateral sets count per side in the volume tally (not halved). Indirect muscle contribution credits 0.5 sets, not 0 or 1.
11. Every `Violation.message` is plain language: no rule ids, no snake_case, no numbers without units.
12. The validator returns violations and never returns a modified plan — input is unchanged.
13. Beginner effort is RIR 2–4, same as trained. A reported RIR ≥ 4 or a set > 12 reps is low confidence.
14. Fat-loss reps equal hypertrophy (`PUBLISHED GUIDELINE`). Fat-loss rest equals hypertrophy (`PRODUCT DECISION`). Strength rest for beginners is 60–120 seconds.
15. Strength default is 5–8 reps. Below 6 reps on a high-consequence lift that lists a rack, with no rack in user equipment → `WARN` and `effectiveRepRange` floors at 6. A deadlift is not floored. The input plan is unchanged.
