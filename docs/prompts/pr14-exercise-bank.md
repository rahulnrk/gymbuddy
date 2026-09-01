> **DO NOT RUN THIS AS-IS (2026-09-01).** Sections 2.2 and 2.4 are superseded by
> `pr14a-catalog-schema.md`, which fixes the single-valued `load` field, splits
> `technique` into two axes, and adds provenance. Run PR 14a first. What remains
> here — growth to >=80, templates, wiring a real workout into the app — becomes
> PR 14b once 14a has landed and the schema is frozen.

# PR 14 — Exercise bank ≥80, muscle map, aliases, substitutions, templates

## Why this PR exists

The app can hold a conversation but knows exactly one exercise: a hardcoded
`PlannedExercise` for "Bench Press" in `GymModeActivity`. Until a real catalog
exists, nothing downstream can be built — plan generation (PR 15), the
substitution engine (PR 16), history answers (PR 17) and the muscle map
(Feature 22) all block on it. `TECHNICAL_SPEC.md` line 2764 names the bar:
**≥80 in-repo exercises covering squat / hinge / horizontal-push /
horizontal-pull / vertical-push / vertical-pull / lunge / core, each with ≥1
dumbbell or bodyweight substitute, templates in-repo at
`knowledge/templates/*.json`.**

We are not inventing this from nothing. `voice-workout-os-source/` is our own
earlier prototype and it already contains, verified consistent (no dangling
references):

- `src/10-data.js` — 61 exercises with `kind`/`load`/`rest`/`technique`/`pattern`,
  118 spoken aliases, 12 substitution lists, 11 programme templates
- `src/12-muscles.js` — 24 muscle regions, 22 per-pattern contribution maps,
  per-exercise overrides where real emphasis differs from the pattern default
- `src/18-routines.js` — routines and a ranked picker (**out of scope here** —
  that is PR 15)

Harvest from those. Do not re-derive the numbers by guesswork.

## This also resolves Q9

Q9 ("exercise catalog extra license?") is open only because we never wrote down
where the data comes from. It comes from our own prototype in this repository.
Record that in `TECHNICAL_SPEC.md`: **Q9 resolved — catalog authored in-repo,
derived from `voice-workout-os-source/`, no third-party content or licence.**

## Non-negotiables (from AGENTS.md)

- `:domain` stays pure Kotlin. No Android SDK, no vendor SDK, no file IO in the
  parser itself.
- Living spec and contracts update **in the same change** as the code.
- No SQL or Room from LLM output or extractor code. This PR writes **no**
  events and adds **no** event types.
- A contract may not be marked `implemented` without mapped goldens.
- Do not start PR 15 (plan generation), PR 16 (substitution *engine*), PR 18
  (dashboard) or PR 11 (chat thread). This PR ships **data + lookup**, not
  behaviour that mutates a workout.
- `PersistFixtures` and the PR 3 fixture workout must remain valid. 21 golden
  files reference `44444444-4444-4444-8444-444444444444`. **Do not renumber
  them.** See TASK 2.5.

---

## Commit 1 — `fix(domain): stop overwriting the projected plan`

Unrelated to the catalog, already identified, ten lines. Do it first and alone.

`WorkoutEngine.applyOne` was fixed to seed the plan only when absent, but the
same unguarded `s.copy(plan = plan)` pattern remains in four more places:

- `applyFacts` — lines ~238/240 and ~257/259
- `maybeRestAfter` — ~468/471
- `maybeNextOrComplete` — ~495/498

Each looks like:

```kotlin
when (val r = WorkoutProjector.apply(s.copy(plan = plan), e)) {
    is ApplyResult.Ok -> { s = r.snapshot.copy(plan = plan); out += e }
```

The second `copy(plan = plan)` overwrites the plan the projector just evolved,
discarding extra sets, skips and substitutions — the exact bug
`SnapshotCacheTest` caught for `EXTRA_SET`. Replace every one with the existing
`withPlan(state, plan)` helper, which seeds only when `state.plan == null`.

Extend `SnapshotCacheTest` so the three-way equivalence (cached snapshot / full
fold / reload from disk) is asserted after a sequence that exercises **all four**
sites: `EXTRA_SET`, a skip, a substitution, and a rest that rolls into the next
exercise.

---

## Commit 2 — `feat(knowledge): exercise catalog, muscle map, aliases, substitutions`

### 2.1 Identity — deterministic UUIDs from readable slugs

The frozen schemas require `exerciseId` to be `format: uuid`
(`set-completed.schema.json`, `exercise-started.schema.json`). Hand-assigned
UUIDs would make the catalog undiffable and unmergeable. So derive them:

```
NAMESPACE = uuidv5(RFC4122 DNS namespace, "catalog.gymbuddy.app")
          = a6c06b9c-44ea-5dc4-a9d4-5636bb4b2dc6
id(slug)  = uuidv5(NAMESPACE, slug)
```

Slugs are lowercase ASCII, hyphen-separated, derived from the canonical name
("Romanian Deadlift" → `romanian-deadlift`).

The `id` field is written into the JSON (it is data, not something recomputed at
runtime), and a test **regenerates** it from the slug and asserts equality for
every entry — so a hand-edited id fails the build.

Pin these in the test as literals, independently computed:

| slug | id |
|---|---|
| `bench-press` | `3d3f7200-5c48-5274-8f63-763a7d095f6d` |
| `back-squat` | `36d57af1-8747-59c3-a38f-dddd80753ad4` |
| `romanian-deadlift` | `66174728-d7e1-5441-b0a8-1fdf9be4e61d` |
| `lat-pulldown` | `5712e8e9-a9c0-5345-8874-ee59822fa664` |
| `plank` | `1b431f65-a7d2-542b-a290-90ebc156de1f` |

If your generator disagrees with any of these, your generator is wrong.

### 2.2 `knowledge/catalog/exercises.json`

One array, one object per exercise, `additionalProperties: false`:

```json
{
  "slug": "romanian-deadlift",
  "id": "66174728-d7e1-5441-b0a8-1fdf9be4e61d",
  "name": "Romanian Deadlift",
  "kind": "compound",
  "load": "barbell",
  "pattern": "hinge",
  "technique": "high",
  "defaultRestSec": 180,
  "aliases": ["rdl", "rdls", "stiff leg deadlift", "sldl", "romanian"],
  "substitutes": ["dumbbell-romanian-deadlift", "leg-curl", "hip-thrust"],
  "muscleOverrides": { "hamstring": 1.0, "glute": 0.8, "back_lower": 0.7, "forearm": 0.4 },
  "legacyIds": []
}
```

Aliases live **on the exercise**, not in a separate file — a separate alias map
is how you get orphans. Substitutes are slugs, never names.

Carry over all 61 exercises from `10-data.js` with their existing
`kind`/`load`/`pattern`/`technique`/`rest` values unchanged, then author at
least 19 more to reach **≥80**. Prioritise filling real gaps rather than padding:
dumbbell and bodyweight variants of every barbell main lift (this is what makes
the substitute rule satisfiable), plus the machine variants a commercial gym
actually has. Every new exercise needs a pattern drawn from the existing set.

### 2.3 `knowledge/catalog/muscles.json` and `patterns.json`

- `muscles.json` — the 24 regions from `12-muscles.js` with `id`, `name`,
  `side` (`front`/`back`/`both`), `group` (one of chest, back, shoulders, arms,
  core, legs).
- `patterns.json` — the 22 `PATTERN_MUSCLES` maps verbatim.

Resolution rule, implemented once in Kotlin: **`muscleOverrides` if present,
otherwise the pattern map.** Do not pre-compute a flattened per-exercise map
into JSON — two sources of truth is how they drift.

### 2.4 Substitutions — the actual gap

12 of 61 exercises have a substitute list today. 49 have none. The spec requires
every exercise to have at least one, and at least one that is dumbbell or
bodyweight (the whole point: the barbell rack is occupied, or the user is
travelling).

Generate the missing ones with a stated, testable rule rather than by taste:

1. Same `pattern`.
2. `technique` no higher than the original (never substitute a high-technique
   lift *in*).
3. Order by load availability: `bodyweight` and `dumbbell` first, then `machine`
   and `cable`, then `barbell`.
4. At most 4 per exercise.

Keep all 12 hand-written lists from `10-data.js` as-is — they encode judgement
the rule cannot (`Deadlift → Romanian Deadlift, Hip Thrust, Leg Curl`). The rule
fills only the empty ones. Write the rule into
`contracts/19-substitution-engine.md` so it is reviewable.

### 2.5 The fixture must keep working

`PersistFixtures.EXERCISE_ID` is `44444444-4444-4444-8444-444444444444` and maps
to "Bench Press". 21 goldens depend on it. **Do not change it and do not
regenerate those goldens.** Instead put it in the catalog entry's `legacyIds`:

```json
{ "slug": "bench-press", "id": "3d3f7200-…", "legacyIds": ["44444444-4444-4444-8444-444444444444"] }
```

`byId` resolves through `legacyIds`. Add a test asserting
`catalog.byId(PersistFixtures.EXERCISE_ID)!!.slug == "bench-press"`.

### 2.6 Kotlin — `:domain`, package `com.gymbuddy.domain.catalog`

```kotlin
data class CatalogExercise(
    val slug: String, val id: String, val name: String,
    val kind: ExerciseKind, val load: LoadType, val pattern: String,
    val technique: TechniqueDemand, val defaultRestSec: Int,
    val aliases: List<String>, val substitutes: List<String>,
    val muscleOverrides: Map<String, Double>, val legacyIds: List<String>,
)

interface ExerciseCatalog {
    fun all(): List<CatalogExercise>
    fun byId(id: String): CatalogExercise?          // resolves legacyIds too
    fun bySlug(slug: String): CatalogExercise?
    fun resolveSpoken(text: String): CatalogExercise?
    fun musclesOf(id: String): Map<String, Double>
    fun substitutesFor(id: String, availableLoads: Set<LoadType>): List<CatalogExercise>
}
```

- `JsonExerciseCatalog(json: String, muscles: String, patterns: String)` — pure,
  parses with kotlinx.serialization, rejects unknown fields, takes **strings**.
  No file IO in `:domain`.
- A thin `ClasspathCatalog` object loads the resources. Put the JSON on the
  `:domain` resource path via a Gradle `processResources` copy from
  `knowledge/` — Java resources are packaged into the APK, so `:app` needs no
  assets directory and no duplicate copy.
- `resolveSpoken` is **exact match only**, in this order: canonical name →
  alias → normalized form of either (lowercase, strip punctuation, collapse
  whitespace). **No fuzzy or edit-distance matching in this PR.** A wrong
  confident match writes a wrong set into an append-only log; an unmatched
  phrase just asks the user. Unmatched returns `null`.

### 2.7 Tests (`:domain`)

- every `id` equals `uuidv5(NAMESPACE, slug)`; the five pinned values above match
- ≥80 exercises; slugs unique; ids unique; names unique
- all eight required patterns present: squat, hinge, horizontal push,
  horizontal pull, vertical push, vertical pull, lunge, core
- every substitute slug exists; every exercise has ≥1 substitute; every exercise
  has ≥1 substitute whose load is `dumbbell` or `bodyweight`
- no substitute has higher `technique` than its source
- every alias is unique across the whole catalog and no alias equals any
  canonical name
- every exercise resolves to ≥1 muscle region with weight ≥0.8, no weight >1.0,
  none <0.15, every region id exists in `muscles.json`
- `byId(PersistFixtures.EXERCISE_ID)?.slug == "bench-press"`
- `resolveSpoken("rdl")`, `"RDLs"`, `"  romanian  "` all resolve to
  `romanian-deadlift`; `resolveSpoken("bench pres")` returns `null`
- parsing JSON with an unknown field throws

---

## Commit 3 — `feat(knowledge): programme templates and a real plan in the app`

### 3.1 `knowledge/templates/*.json`

One file per programme, filename = programme id (`upper-lower-4.json`). Port all
11 from `PROGRAMS` in `10-data.js`:

```json
{
  "id": "upper_lower_4",
  "name": "Upper / Lower — 4 day",
  "goal": "hypertrophy",
  "level": "intermediate",
  "schedule": ["Mon", "Tue", "Thu", "Sat"],
  "days": [
    { "name": "Upper A", "exercises": [
      { "slug": "bench-press", "sets": 3, "repLow": 8, "repHigh": 10,
        "startWeightKg": 60, "priority": 1 }
    ]}
  ]
}
```

`priority`: 1 = main lift, never cut; 2 = secondary; 3 = optional accessory,
first to go. Exercises are referenced by **slug**, never by name.

Do **not** port `18-routines.js` or its ranked picker. That is PR 15.

### 3.2 Wire one real workout into the app

Replace the single hardcoded `PlannedExercise` in `GymModeActivity` with a plan
built from `upper-lower-4`, day "Upper A", via the catalog. Build
`exerciseNames` from the catalog rather than a literal map. Keep
`PersistFixtures.WORKOUT_ID` / `SESSION_ID` / `USER_ID`.

**Recovery hazard, handle it explicitly.** Phones already carry an event log
whose events reference the old fixture `workoutExerciseId`. When
`engine.recover(sessionId, plan)` folds events that reference exercises absent
from the current plan, it must not crash and must not silently attach those sets
to the wrong exercise. Decide the behaviour, write it into
`contracts/10-local-first-session-reliability.md`, and test it: **a session
whose stored events reference a `workoutExerciseId` not present in the supplied
plan recovers without throwing and reports the mismatch.**

### 3.3 Gate — `scripts/check_living_spec.py`

Add checks that fail the build, not a README:

- `knowledge/catalog/exercises.json` exists, parses, has ≥80 entries
- all eight required patterns present
- no duplicate slug, id, name or alias
- every substitute slug, every `muscleOverrides` region and every template slug
  resolves to a catalog entry
- every exercise has ≥1 dumbbell-or-bodyweight substitute
- `44444444-4444-4444-8444-444444444444` appears in exactly one `legacyIds`

---

## Spec and contracts (same change, per AGENTS.md)

- `18-exercise-bank-and-detail.md` → `implementing`, with the catalog file
  layout, the UUIDv5 derivation and the `resolveSpoken` matching order.
- `19-substitution-engine.md` → stays `implementing`; add the generation rule
  from 2.4 and state plainly that this PR ships **data only**, no
  `SUBSTITUTION_APPLIED` behaviour.
- `22-muscle-map-and-recovery.md` → add the override-else-pattern resolution rule.
- `10-local-first-session-reliability.md` → the recovery-mismatch behaviour.
- `TECHNICAL_SPEC.md` → mark **Q9 resolved** with the reason above; mark PR 14
  done in the PR plan.

## Definition of done

```
./gradlew :domain:test :persist:test :app:test
python scripts/check_living_spec.py
```

both green, no new Forbidden item, spec updated in the same commits, and three
commits with the messages above — not one.

## Report back

Number of exercises, how many substitutes were hand-kept vs rule-generated, any
place where the harvested prototype data was internally wrong, and anything in
this prompt you think is a mistake. If the plan-copy fix in Commit 1 turns out
to be already correct at any of the four sites, say so rather than editing it.
