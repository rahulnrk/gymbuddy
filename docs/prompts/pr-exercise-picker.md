# PR — The picker: filter, score, order, dose

## Carry-over first (commit 1)

`4ed1a64` made the recency follow-up `UNKNOWN` on skip, and the urgency line is
added only on `YES`. That defaults an unanswered safety question to the calmer
branch, which is backwards and inconsistent with unknown-is-not-no.

**Fix: `UNKNOWN` recency includes the urgency line.** The line is already
conditional — *"If you are feeling this right now, or it came on recently…"* —
so the reader self-selects and a false positive costs nothing. A false negative
means someone with live symptoms is told to book an appointment. Add a test:
`UNKNOWN` recency produces the same message as `YES`.

---

## Why this PR exists

The rules layer knows how much to prescribe. The catalog knows what exists. The
intake layer knows who the person is and what would hurt them. Nothing connects
them: there is no code that answers **"which exercise, for this person, right
now?"**

## What the picker is — and is not

`contracts/16-initial-workout-generation.md` is product law: **one engine, not a
programming AI.** Sessions come from in-repo templates. `contracts/19` adds that
a substitution proposes **one** primary alternative, not a list of ten.

So the picker is **a ranking function over candidates for a single slot.** It
serves two callers that both already exist in the spec:

- contract 16 step 2 — filter a template's slot to what the user can actually do
- contract 19 — propose a swap when the plan meets reality

It does **not** build a session, choose a split, decide how many slots a day
has, or mutate the user's programme. If you find yourself writing a function
that returns a workout, you are out of scope.

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL, no
  LLM call.
- **Deterministic.** Same inputs, same ranking, every time. No randomness, no
  clock reads inside scoring. Variety comes from the recency term, which is
  computed from training history, not from a random number. A picker you cannot
  write a golden for is not shippable.
- **Every score carries its reasons.** "Why this exercise?" must be answerable
  from the return value alone. This is the same discipline as the claim verifier
  in the coaching loop: the coach cites what it computed from.
- Living spec updates in the same change. Still 61 exercises. No `:app` changes.
- No automatic programme mutation (`AGENTS.md`).

---

## Commit 2 — `feat(domain): slot and candidate filter`

Package `com.gymbuddy.domain.picker`.

```kotlin
enum class SlotRole { PRIMARY, SECONDARY, ACCESSORY, ISOLATION, CORE }

data class Slot(
  val pattern: PatternId,
  val role: SlotRole,
  val targetMuscles: Set<MuscleId> = emptySet()
)

data class PickContext(
  val user: UserContext,               // from intake, already exists
  val equipment: Set<EquipmentId>,     // resolved preset + adjustments
  val fatigue: Map<MuscleId, Double>,  // 0.0 recovered .. 1.0 fully fatigued
  val recentSlugs: List<ExerciseSlug>, // most recent first
  val dislikes: Set<ExerciseSlug>
)
```

### The filter is ordered, and safety is first

```kotlin
sealed interface Exclusion {
  data class Contraindicated(val condition: ConditionId) : Exclusion
  data class PainfulMovement(val report: PainReport) : Exclusion
  data class MissingEquipment(val unsatisfiedSlots: List<List<EquipmentId>>) : Exclusion
  data class WrongPattern(val expected: PatternId) : Exclusion
  data class Disliked(val slug: ExerciseSlug) : Exclusion
}

fun candidates(slot: Slot, catalog: Catalog, ctx: PickContext): CandidateSet
// CandidateSet carries both the survivors and every exclusion with its reason
```

Rules:
1. **Contraindicated for a declared condition → excluded. Never overridable.**
2. **Named in a sharp or radiating pain report → excluded. Never overridable.**
3. Equipment unsatisfiable → excluded.
4. Wrong pattern → excluded.
5. Disliked → excluded **only if something survives without it**. A dislike must
   never empty a slot; if it would, keep the exercise and mark it
   `keptDespiteDislike` so the coach can acknowledge that out loud.

The unspotted rep floor does **not** exclude. It caps the prescription — see
dose.

Every exclusion is returned, not swallowed. Contract 19 requires the app to say
"no alternative given your equipment" rather than inventing a machine; that
answer is built from this list.

---

## Commit 3 — `feat(domain): scoring`

```kotlin
data class Reason(val factor: String, val delta: Double, val explanation: String)
data class ScoredExercise(val exercise: Exercise, val score: Double, val reasons: List<Reason>)

fun rank(slot: Slot, catalog: Catalog, ctx: PickContext): List<ScoredExercise>
```

Factors. Weights are `MVP HEURISTIC` and belong in
`DECISIONS_AND_THRESHOLDS.md` like everything else — do not hide tuning
constants in the scorer.

| Factor | Source of truth |
|---|---|
| Goal fit | catalog `Best-Fit Goal` / `kind` vs `user.goal` |
| Target muscle coverage | fractional muscle weights, the model from `41da2d2` |
| Skill match | catalog `skillDemand` vs `user.trainingAge` — penalise a high-skill lift for a beginner, do not exclude it |
| Freshness | `ctx.fatigue` on the exercise's primary muscles; a fatigued primary is a strong penalty |
| Variety | position in `recentSlugs`; more recent, larger penalty |
| Preference | soft positive for previously accepted substitutions |
| Role fit | compound for `PRIMARY`, isolation for `ISOLATION` |

`explanation` is written as the coach would say it: *"your quads are still
fatigued from Monday"*, not `fatigue.primary=0.72`. Same rule as the validator's
messages.

Ranking is stable: ties break on slug, alphabetically, so the output is
reproducible.

---

## Commit 4 — `feat(domain): dose`

```kotlin
data class Prescription(
  val sets: Int, val repRange: IntRange2, val restSec: IntRange2,
  val effort: Effort, val cappedBy: List<String>
)

fun dose(exercise: Exercise, slot: Slot, ctx: PickContext): Prescription
```

Order of precedence, and this must be tested explicitly:

1. Start from the rules layer for `user.goal` and `user.trainingAge`.
2. Narrow by the catalog's `repWindow` and `defaultRestSec` where they are more
   specific. Note the known wart: `defaultRestSec` is a point value, so this
   currently collapses to a single number — record that in the contract rather
   than working around it.
3. Apply `STRENGTH_UNSPOTTED_REP_FLOOR` where the exercise lists a catch
   implement and the user has neither the catch nor a training partner.
4. Every narrowing appends to `cappedBy` with a plain-language reason.

`sets` is per-session for this slot. Weekly volume is the validator's job, not
the picker's — the picker must not try to balance a week.

---

## Commit 5 — `docs: contracts`

- New `contracts/31-exercise-selection.md`: the ordered filter, safety as
  non-overridable, the factor list, determinism, the reasons requirement, and
  the explicit statement that the picker ranks candidates for one slot and never
  returns a session.
- `contracts/19-substitution-engine.md`: record that the substitution engine
  consumes `rank()` and takes the top result — "one primary alternative, not a
  list of ten" is a caller-side rule, and the picker returning a full ranking
  does not violate it.
- `DECISIONS_AND_THRESHOLDS.md`: a row for every scoring weight.
- `scripts/check_living_spec.py` covers contract 31.

---

## Tests (`:domain`)

1. A contraindicated exercise never appears in `rank()` output, at any weight.
2. An exercise named in a sharp pain report never appears.
3. No amount of preference or goal fit promotes an excluded exercise — assert by
   construction, not by score comparison.
4. Equipment: `rank()` for a squat slot with only a kettlebell returns goblet
   squat and not back squat.
5. Determinism: same inputs, same order, over 100 runs.
6. Ties break alphabetically by slug.
7. Every `ScoredExercise` has at least one `Reason`, and no `explanation`
   contains a field name, an underscore or a bare number without units.
8. A fully fatigued primary muscle drops that exercise below an equivalent
   fresh one.
9. A disliked exercise is excluded when alternatives exist, and kept with
   `keptDespiteDislike` when it is the only survivor.
10. An empty `CandidateSet` still returns every exclusion with its reason.
11. Dose precedence: catalog `repWindow` narrows the goal range; the unspotted
    floor caps below 6 on a catch-listing lift with no rack and no partner; both
    appear in `cappedBy`.
12. `rank()` returns exercises, never a session or a week.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` change, no catalog change, still 61 exercises, no generator.
- The recency carry-over is fixed and tested.

## Report back

1. The final factor list with weights, and what each weight is worth in practice
   — show one worked ranking for a squat slot, beginner, home equipment, quads
   fatigued.
2. Any factor you dropped or added, and why.
3. Anywhere the 61-exercise catalog is too thin to rank meaningfully — a slot
   where there is only one legal candidate is a content gap for PR 14b, and this
   PR is the first thing able to find them. List them.
4. Any exclusion reason you found yourself wanting to override, and why you
   did not.
5. Anything in this prompt that turned out to be wrong.
