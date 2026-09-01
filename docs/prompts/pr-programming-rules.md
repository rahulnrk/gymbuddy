# PR — Programming rules (the coaching science, as code)

## Why this PR exists

The catalog is the vocabulary. This is the first piece of the reasoning.

Today nothing in the repo knows how many sets a hypertrophy session should
carry, how long to rest, when to add load, or when to back off. `Recommend.kt`
has one narrow heuristic ("3 sets, all reps >= 10, last < first, +/-2.5 kg") and
nothing behind it. This PR gives the app the programming knowledge a certified
coach is examined on, written as pure functions with the source of every number
recorded.

The product promise is a coach that can answer **"why three sets?"** with a real
answer rather than a confident guess. That is only possible if every number
carries its provenance.

## Scope guard — read this before writing anything

`contracts/16-initial-workout-generation.md` is product law and says: **one
engine, not a programming AI.** Plans come from in-repo templates, filtered by
equipment and clamped to duration. This PR does **not** change that.

This PR builds:

- the **rules** — constants and pure decision functions
- the **validator** — given a proposed session or week, which rules does it
  violate

This PR does **not** build:

- a plan generator (that is PR 15, template-based, and stays template-based)
- any UI, any `:app` change
- any LLM call
- exercise selection or scoring (that is the picker, a later PR)
- anything under `knowledge/catalog/` — the 61 exercises are untouched

If you find yourself writing code that produces a workout, stop. You are out of
scope.

## Non-negotiables

- `:domain` stays pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL.
- Living spec updates in the same change, per `AGENTS.md`.
- **Every number gets a provenance row** in
  `docs/living-spec/DECISIONS_AND_THRESHOLDS.md`. That file already classifies
  every constant in the repo. Extend it; do not start a second register.
- **Ranges, not invented precision.** The literature gives "about 10-20 hard sets
  per muscle per week", not 14. Model it as a range. A function that returns 14
  because it needed one number is lying about how well this is known.
- **Never invent a value.** `docs/research/Coach_and_AI_Programming_Guide.pdf`
  and the `Programming_Rules` block in
  `docs/research/exercise_knowledge_base_1265_v2.json` carry most of what you
  need. Where they are silent, use `MVP HEURISTIC`, say so, and put it in the
  gaps list. Do not dress a guess as a guideline.
- **Scope of practice.** These rules describe, adjust and refer. Nothing here
  diagnoses, and nothing here overrides the safety layer.

## New provenance class

`DECISIONS_AND_THRESHOLDS.md` has no class for "this comes from published
coaching guidance". Add one:

| Class | Meaning |
|---|---|
| `PUBLISHED GUIDELINE` | Stated in a named professional guideline or curriculum (ACSM, NSCA, NASM, ACE). Cite the source. Ranges, not point values. |

It sits above `MVP HEURISTIC` and below `MEASURED`. Nothing in this PR is
`MEASURED` — as the file already says, nothing in the repo is.

---

## Commit 1 — `feat(domain): training goals and effort`

Package `com.gymbuddy.domain.programming`.

```kotlin
enum class Goal { STRENGTH, HYPERTROPHY, ENDURANCE, GENERAL_FITNESS, FAT_LOSS }
enum class TrainingAge { BEGINNER, INTERMEDIATE, ADVANCED }

data class Effort(val rirMin: Int, val rirMax: Int)      // reps in reserve
data class IntRange2(val min: Int, val max: Int)         // inclusive
data class Landmark(val min: Int, val max: Int, val basis: Provenance)
```

Reuse the `Provenance` type from `com.gymbuddy.domain.catalog` — same four
fields, same meaning. Do not define a second one.

## Commit 2 — `feat(domain): the rules`

Pure functions. No state, no clock, no randomness.

### Volume

```kotlin
fun weeklyHardSets(goal: Goal, age: TrainingAge): Landmark
```

Hypertrophy, intermediate: 10-20 hard sets per muscle per week. Beginners grow
on less — model that, do not copy the intermediate range down. `PUBLISHED
GUIDELINE`, Coach & AI Programming Guide §4.3.

A **hard set** needs a definition in the contract: a working set taken close
enough to failure to drive adaptation. Warm-up and ramp sets do not count.

### Effort, reps and rest

```kotlin
fun effort(goal: Goal, age: TrainingAge): Effort          // strength: RIR 2-4
fun repRange(goal: Goal): IntRange2
fun restSeconds(goal: Goal, kind: ExerciseKind): IntRange2
```

Rest: strength 180-300 s, hypertrophy 60-180 s, metabolic/endurance 30-90 s.
Effort: most work at RPE 6-8, i.e. 2-4 reps in reserve; true 1RMs are rare and
expensive. Both `PUBLISHED GUIDELINE`.

The per-exercise `defaultRestSec` and `repWindow` already in the catalog **win**
over these when present — the catalog is more specific. Write that precedence
rule into the contract and test it.

### Frequency

```kotlin
fun weeklyFrequency(goal: Goal, age: TrainingAge): IntRange2
```

Each muscle or pattern 2-3 times per week beats one heroic session for most
people. `PUBLISHED GUIDELINE`.

### Ordering

```kotlin
fun orderScore(exercise: Exercise): Int
fun validateOrder(session: List<Exercise>): List<Violation>
```

High-skill, high-load and explosive work goes while fresh; compound before
isolation; core and isolation last. Use the catalog's `skillDemand`,
`fatigueCost` and `kind`. `PUBLISHED GUIDELINE` (NSCA; ACE exam outline).

### Progression

```kotlin
sealed interface ProgressionDecision {
  object Hold; data class AddReps(val to: Int); data class AddLoadKg(val delta: Double)
  data class AddSet(val to: Int); object Deload; object RegressExercise
}

fun nextSession(history: List<SetPerformance>, target: Prescription): ProgressionDecision
```

Double progression is the default: fill the rep range at the target effort, then
add load and drop back to the bottom of the range. Change **one or two variables
at a time**, never more. Load increments must come from the caller (plate
availability is equipment, not science) — do not hardcode 2.5 kg inside the rule.

Technique degradation outranks progression: if the recorded effort or the user's
own report says the pattern broke down, the answer is `Hold` or
`RegressExercise`, never more load. `PUBLISHED GUIDELINE` (NSCA; technique/safety
review).

### Deload

```kotlin
fun deloadDue(weeksAccumulated: Int, signals: RecoverySignals): Boolean
```

Every 4-8 weeks, or earlier when sleep, pain or motivation fall over. A deload
cuts volume roughly 40-60%, not intensity to zero. `PUBLISHED GUIDELINE`.

`RecoverySignals` carries what the app can actually observe today. Do not invent
inputs the app cannot supply — if a signal is not available, the function must
work without it and say so.

### Concurrent training

```kotlin
fun cardioPlacement(strengthDay: SessionSummary, goal: Goal): CardioAdvice
```

Enough to stop the obvious mistake: hard intervals stacked on a heavy lower-body
day. Keep it modest — a placement recommendation and a reason, not a full
endurance programme.

## Commit 3 — `feat(domain): plan validator`

```kotlin
data class Violation(
  val rule: String,          // stable id, e.g. "volume.weekly-hard-sets.low"
  val severity: Severity,    // INFO | WARN | BLOCK
  val message: String,       // plain language, speakable
  val provenance: Provenance
)

fun validateSession(session: PlannedSession, user: UserContext): List<Violation>
fun validateWeek(week: List<PlannedSession>, user: UserContext): List<Violation>
```

Checks: weekly volume inside the landmark per muscle (using the catalog's
`muscleOverrides`, and counting a unilateral set as one set per side); pattern
balance (no push-only week); ordering; frequency; rest and rep prescriptions
inside range or justified by the catalog; contraindicated exercise present for a
declared condition (**BLOCK**).

`message` is spoken to the user, so write it as a coach would speak it: *"That's
28 sets for chest this week — more than most people recover from. I'd cut it to
about 20."* Not *"volume.weekly-hard-sets.high violated."*

`BLOCK` is reserved for safety. Everything else is `WARN` or `INFO`. The
validator reports; it does not silently rewrite a plan. Automatic mutation of
the user's programme is forbidden by `AGENTS.md`.

## Commit 4 — `docs: provenance and contract`

- Extend `DECISIONS_AND_THRESHOLDS.md`: add the `PUBLISHED GUIDELINE` class and
  a row for every constant introduced here, each naming its source document and
  section.
- New `contracts/29-programming-rules.md`, status `implemented`, following the
  contract template. Record: the hard-set definition, the catalog-wins
  precedence rule, the one-or-two-variables progression rule, the severity
  meanings, and that the validator never mutates a plan.
- `scripts/check_living_spec.py` covers the new contract.

---

## Tests (`:domain`)

1. Every public rule function returns a `Landmark`/range carrying a
   `Provenance`, and no `Provenance.basis` is left at a default.
2. Every constant in the new package appears in `DECISIONS_AND_THRESHOLDS.md`.
   A constant with no provenance row fails the build. This is the test that
   makes the rest of the PR mean anything.
3. Beginner hypertrophy volume is strictly below intermediate.
4. Catalog `defaultRestSec` and `repWindow` win over the goal defaults.
5. Double progression: filling the rep range at target effort → `AddLoadKg`;
   short of it → `AddReps`; degraded technique → `Hold` or `RegressExercise`,
   never `AddLoadKg`.
6. Progression never changes more than two variables at once.
7. `deloadDue` fires by week 8 with no signals, and earlier with poor signals.
8. `validateWeek` flags a push-only week for pattern imbalance.
9. A contraindicated exercise for a declared condition produces `BLOCK`.
10. Unilateral sets count per side in the volume tally.
11. Every `Violation.message` is plain language: no rule ids, no snake_case, no
    numbers without units.
12. The validator returns violations and never returns a modified plan — assert
    the input is unchanged.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- `:app`, `knowledge/catalog/`, and the 61 exercises untouched.
- No workout is generated anywhere in this PR.
- Every number traceable to a named source or explicitly marked `MVP HEURISTIC`.

## Report back

1. The provenance table you added — every constant, its range, its class, its
   source.
2. **The gaps list**: every value you had to mark `MVP HEURISTIC` because the
   research folder was silent. This is the online-research queue.
3. Anywhere the research documents disagreed with each other, and which you
   took.
4. Any rule you could not express because the catalog lacks a field.
5. Anything in this prompt that turned out to be wrong.
