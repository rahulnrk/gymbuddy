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
| `WARN` | The plan fights the landmarks (volume high, push-only week, order, rest/reps unjustified). |
| `BLOCK` | Safety only. A contraindicated exercise is present for a declared condition. |

The validator **reports**; it does not rewrite a plan. Automatic programme mutation is forbidden by `AGENTS.md`.

### User behavior

Does not surface as its own screen. A later generator (contract 16 templates) and the coaching loop may ask these functions “how many hard sets?” and “does this week violate a rule?”. The user hears the `Violation.message`, written as a coach would speak it, never a rule id.

### System state

Pure functions in `com.gymbuddy.domain.programming`. No clock, no randomness, no file IO, no SQL, no Android. Inputs are `Goal`, `TrainingAge`, catalog `Exercise`, `PlannedSession` / week, and `UserContext` (programming-time; not the frozen `UserStateContext` snapshot).

`weeklyHardSets` returns a `Landmark` (a range, never a point invented for convenience). Hypertrophy, intermediate: 10–20 hard sets per muscle per week (`PUBLISHED GUIDELINE`, Coach & AI Programming Guide §4.3). Beginners grow on less — a strictly lower range, marked `MVP HEURISTIC` because the guide gives no beginner number.

Unilateral: one logged set counts as one set per side. It is not halved.

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
10. Unilateral sets count per side in the volume tally (not halved).
11. Every `Violation.message` is plain language: no rule ids, no snake_case, no numbers without units.
12. The validator returns violations and never returns a modified plan — input is unchanged.
