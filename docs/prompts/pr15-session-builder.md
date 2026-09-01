# PR 15 — The session builder

## Why this PR exists

Everything needed to produce a workout now exists and nothing produces one.

- Intake knows who the person is, what they have, and what would hurt them.
- The picker ranks candidates for a slot, safely, with reasons.
- The rules layer doses sets, reps, rest and effort.
- The validator checks a week.
- The adaptation layer changes a session when the day changes.

There is no code that lays out the slots. `knowledge/templates/` does not exist.
This is the last missing piece of the chain, and it is small because the hard
parts are already built.

## Scope guard — read `contracts/16-initial-workout-generation.md` first

It is product law and it is specific: **one engine, not a programming AI.**
Sessions come from **in-repo templates**, filtered to the user's equipment and
clamped to their time. This PR implements exactly that algorithm and invents
nothing beyond it.

In particular, from contract 16:

- Templates keyed by `goal × days_per_week`. Minimum set: Full Body 3-day,
  Upper/Lower 4-day, PPL 6-day, beginner Full Body 3-day.
- Filter every template slot to the user's equipment. **If a slot has no legal
  exercise, drop the slot** — do not invent one.
- Clamp accessories until estimated duration is within **±15 minutes** of the
  user's typical session length (default 60 if unset).
- Experience maps to intensity **only** through `planned_sets` / `target_reps`,
  never through invented loads.
- **`prescribed_weight_kg` stays null until the user logs.** The app does not
  guess what someone can lift. This is the same rule as "no invented history".

Not in scope: an LLM swap path (contract 16 step 4 — later, and it may only pick
from the catalog), plan editing (contract 17), any `:app` UI, any new exercises.

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL, no
  LLM call, no network.
- Deterministic. Same profile, same day, same plan. Any variety comes from the
  picker's recency term.
- Living spec updates in the same change. Still 61 exercises.
- A generated plan is a **proposal**. Contract 16 says the user must never feel
  locked in, and `AGENTS.md` forbids automatic programme mutation. Building a
  plan writes no events; accepting one does.

---

## Commit 1 — `feat(knowledge): programme templates`

`knowledge/templates/*.json`, plus `knowledge/schema/template.schema.json`,
`additionalProperties: false`.

```json
{
  "id": "full-body-3d-beginner",
  "name": "Full Body, 3 days",
  "goals": ["HYPERTROPHY", "GENERAL_FITNESS", "FAT_LOSS"],
  "trainingAges": ["BEGINNER"],
  "daysPerWeek": 3,
  "days": [
    {
      "name": "Day A",
      "slots": [
        { "pattern": "squat",           "role": "PRIMARY",   "priority": 1 },
        { "pattern": "horizontal push", "role": "PRIMARY",   "priority": 1 },
        { "pattern": "horizontal pull", "role": "PRIMARY",   "priority": 1 },
        { "pattern": "hinge",           "role": "SECONDARY", "priority": 2 },
        { "pattern": "core",            "role": "CORE",      "priority": 3 }
      ]
    }
  ]
}
```

`priority` is what the time clamp cuts by: highest number goes first. A
`PRIMARY` slot is never cut by the clamp — if the session still does not fit,
reduce sets, and if it still does not fit, say so rather than gutting the
session silently.

Author the four minimum templates. Balance them by pattern, not by muscle names,
and keep them boring — the Programming Guide's own advice is that a repeatable
full-body session has produced more useful humans than any novelty circuit.

Every template needs a provenance block like everything else: which body of
knowledge the split comes from, and `MVP HEURISTIC` where it is our choice.

---

## Commit 2 — `feat(domain): assemble a session`

Package `com.gymbuddy.domain.plan`.

```kotlin
data class PlannedSession(val day: String, val entries: List<PlannedEntry>, val notes: List<String>)
data class PlannedEntry(
  val exercise: Exercise, val slot: Slot,
  val sets: Int, val repRange: IntRange2, val restSec: IntRange2, val effort: Effort,
  val prescribedWeightKg: Double? = null,   // stays null. always.
  val why: List<Reason>                     // straight from the picker
)

sealed interface PlanResult {
  data class Built(val week: List<PlannedSession>, val warnings: List<Violation>) : PlanResult
  data class CannotBuild(val reasons: List<String>) : PlanResult
}

fun buildWeek(template: Template, ctx: PickContext, readiness: Readiness?): PlanResult
```

Order of operations, and each step must be individually testable:

1. Choose the template by `goal × daysPerWeek × trainingAge`. No match → the
   nearest lower `daysPerWeek`, and say so in `notes`.
2. For each slot, `rank()` from the picker; take the top candidate. A slot with
   no legal candidate is **dropped**, and the drop is recorded in `notes` in
   plain language.
3. Dose each entry from the rules layer, narrowed by the catalog, capped by the
   unspotted floor.
4. Clamp to time: estimate duration from sets × (working time + rest midpoint),
   then cut by descending `priority` until within ±15 minutes. Never cut a
   `PRIMARY`. Record every cut in `notes`.
5. Run `validateWeek` over the result. **Return the violations as warnings; do
   not auto-fix.** If the builder could silently rewrite itself to satisfy the
   validator, the validator would stop being an independent check.
6. If `readiness` is supplied, run `adjustForToday` on today's session and
   return the adjustments as proposals — do not apply them.

`CannotBuild` is a real outcome and must be reachable: bodyweight-only resolves
to 3 of the 61 exercises, so a 3-day full-body template will drop most of its
slots. Saying "I can't build a useful week from what you have" is the honest
answer, and better than four sessions of push-ups. Name what equipment would
unlock it.

---

## Commit 3 — `feat(domain): duration estimate`

```kotlin
fun estimateMinutes(session: PlannedSession): Int
```

Working time per set and the rest midpoint are `MVP HEURISTIC` constants and get
rows in `DECISIONS_AND_THRESHOLDS.md`. Warm-up time is **not** modelled in this
PR — note that in the contract as a known omission rather than inventing a
number for it.

---

## Commit 4 — `docs: contracts`

- `contracts/16-initial-workout-generation.md`: move status to `implementing`,
  record what this PR delivers and what remains (the LLM swap path, editing).
- New `contracts/33-programme-templates.md` for the template schema and the
  priority/clamp rules.
- `DECISIONS_AND_THRESHOLDS.md`: the duration constants, the ±15 minute window
  (`CONTRACTUAL`, it comes from contract 16), the template choices.
- `scripts/check_living_spec.py` covers contract 33 and `knowledge/templates/`.

---

## Tests (`:domain`)

1. Every template validates against the schema; every pattern and role exists in
   the vocabularies.
2. Determinism: same profile, same week, over 100 runs.
3. A slot with no legal exercise is dropped and named in `notes`.
4. `prescribedWeightKg` is null on every entry, always. Assert it across every
   template and profile — this is the "no invented loads" rule.
5. The time clamp never cuts a `PRIMARY` slot.
6. A 30-minute session and a 90-minute session from the same template differ
   only by cut accessories, not by different main lifts.
7. Beginner vs intermediate differ in sets and reps, never in prescribed load.
8. A declared contraindication never appears in a built week.
9. Bodyweight-only produces `CannotBuild` with a named list of what equipment
   would unlock a workable week — not a thin plan.
10. `buildWeek` emits no events and mutates nothing.
11. Validator warnings are returned, not silently fixed — construct a template
    that overshoots weekly volume and assert the warning survives.
12. Every dropped slot, every cut and every substitution carries a plain-language
    note (no field names, no underscores, no bare numbers without units).

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` change, still 61 exercises, no new exercises invented.
- Building a plan writes no events.

## Report back

1. **Print a full week, as text, for three profiles**: (a) beginner,
   hypertrophy, 3 days, full gym; (b) intermediate, strength, 4 days, home with
   dumbbells and a bench; (c) beginner, general fitness, 3 days,
   bodyweight-only. This is the first time the whole chain is visible end to
   end — show it, do not summarise it.
2. Which slots got dropped in each, and what equipment would have saved them.
3. Every validator warning the built weeks produced. If a template we authored
   trips our own validator, say so plainly rather than tuning the template until
   it passes.
4. The duration constants you chose and how far off you think they are.
5. Anything in this prompt that turned out to be wrong.
