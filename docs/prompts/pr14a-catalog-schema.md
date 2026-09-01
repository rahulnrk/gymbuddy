# PR 14a — Catalog schema and vocabularies (no catalog growth)

## Why this PR exists

`pr14-exercise-bank.md` would have the agent design the schema and author 80+
exercises in one pass. Two modelling errors are baked into its schema example,
and a third problem reopens a question that prompt closes. Fixing any of them
afterwards means rewriting every entry.

So it is split:

- **PR 14a (this document)** — freeze the shape. Vocabularies, schema, and the
  existing 62 exercises migrated onto it. **No new exercises.**
- **PR 14b (later)** — grow to ≥80, add `knowledge/templates/*.json`, wire a
  real workout into the app.

## Relationship to `pr14-exercise-bank.md`

- Section **2.1** (deterministic UUIDs from slugs) — **carries over unchanged.**
  Use the same namespace, the same slug rule, and the same five pinned literals.
- Section **2.3** (`muscles.json`, `patterns.json`) — carries over, moved under
  `knowledge/vocab/`.
- Sections **2.2** and **2.4** — **superseded by this document.**
- Sections 2.5, 2.6, 2.7 and Commit 3 — **deferred to PR 14b.**
- The Q9 resolution in that prompt — see "Third problem" below.

## The three problems being fixed

### 1. `load` is a single string

`"load": "barbell"` cannot express that a barbell bench press needs a bar, a
bench and (safely) a rack, or that a goblet squat takes a dumbbell *or* a
kettlebell. Substitution, "what can I train with what is here", and home-gym
users all break on it.

Confirmed wrong in `voice-workout-os-source/src/10-data.js`, and these must be
fixed during migration — do not carry them over:

| Entry | Currently | Problem |
|---|---|---|
| `pec deck` (alias) | → `Cable Fly` | A pec deck is its own machine. Different setup, different availability. |
| `reverse pec deck` (alias) | → `Rear Delt Fly`, typed `dumbbell` | Same. |
| `Lat Pulldown` | `load: machine` | It is a cable station. |
| `Kettlebell Swing` | `load: dumbbell` | A kettlebell is its own tool. |
| `Farmer Carry` | `load: dumbbell` | Needs handles, dumbbells *or* kettlebells. |
| `Chest Supported Row` | `load: dumbbell` | Usually a dedicated machine, or a bench plus dumbbells. |

Audit all 62 for the same class of error, not only these six.

### 2. `technique: "high" | "normal"` is one axis

Two different properties are collapsed into one flag:

- **`skillDemand`** — how hard the movement is to learn. A Turkish get-up is
  high skill; a snatch is high skill.
- **`consequenceOfFailure`** — how bad it is when it goes wrong under load. A
  Turkish get-up is low consequence; loaded lumbar flexion at the bottom of a
  leg press is high.

`Leg Press` is currently `technique: normal` and is exactly the low-skill /
high-consequence case. Only `consequenceOfFailure: high` earns the product's
"have a human watch this once" promise.

### 3. Third problem — provenance and Q9

`pr14-exercise-bank.md` resolves Q9 with: *"catalog authored in-repo, derived
from `voice-workout-os-source/`, no third-party content or licence."*

`docs/research/` now exists. It is **reference material only.** Copying rows out
of it would make that Q9 statement false — `Complete_Coach_Fitness_Knowledge_Base.xlsx`
contains 1316 rows from an unnamed source. Read `docs/research/README.md` before
opening anything in that folder.

Both research bundles are AI-synthesized, and their per-row coaching text is
templated (729 rows share 24 safety strings). That is why the schema records
*how* each field was arrived at rather than treating every field as equally
solid.

## Non-negotiables

From `AGENTS.md`, plus three specific to this PR:

- `:domain` stays pure Kotlin. No Android SDK, no vendor SDK, no file IO in the
  parser. No SQL, no Room, no `INSERT` anywhere in this PR.
- Living spec and contracts update **in the same change** as the code.
- **The exercise count stays at 62.** A diff that adds an exercise is rejected.
  Growth is PR 14b.
- **`docs/research/` is read-only reference.** Do not copy rows from it into
  `knowledge/`. Do not cite it as an authored source. Use it to *check* a value
  you derived from the prototype.
- **Never invent a value.** If the prototype is wrong or silent and the research
  folder does not settle it, mark the field `basis: "judgement"`, give it your
  best defensible value, and list it in the gaps section of your report. We will
  research those properly. Silent guessing is the failure mode this PR exists to
  prevent.

---

## Commit 1 — `feat(knowledge): controlled vocabularies`

Four files under `knowledge/vocab/`. These are L0: small, stable, and everything
else references them by id. Nothing else in the repo may use a bare string where
one of these ids belongs.

### `muscles.json`

The 24 regions from `voice-workout-os-source/src/12-muscles.js`, verbatim:
`id`, `name`, `side` (`front` | `back` | `both`), `group` (one of `chest`,
`back`, `shoulders`, `arms`, `core`, `legs`).

### `patterns.json`

The 22 `PATTERN_MUSCLES` maps verbatim. Keys:
`horizontal push, incline push, decline push, vertical push, chest isolation,
delt isolation, rear delt, triceps, biceps, horizontal pull, vertical pull,
lats, traps, squat, lunge, quads, hinge, hamstrings, lower back, calves, core,
carry`.

Resolution rule, implemented once in Kotlin: **`muscleOverrides` if present,
otherwise the pattern map.** Do not pre-compute a flattened per-exercise map
into JSON — two sources of truth is how they drift.

### `equipment.json` — new, and the point of this PR

```json
{
  "id": "squat-rack",
  "name": "Squat rack",
  "category": "support",
  "tier": "commercial",
  "aliases": ["rack", "power rack", "cage"]
}
```

- `category`: `bar` | `freeweight` | `machine` | `cable` | `bodyweight` |
  `accessory` | `support`
- `tier`: `none` | `minimal` | `home` | `commercial` — what a user must have
  access to. This is what answers "what can I do in this room".

Minimum token set (add more if migration needs them, never fewer):

```
none, mat, barbell, ez-bar, trap-bar, plate, dumbbell, kettlebell,
bench-flat, bench-adjustable, bench-decline, squat-rack, power-rack,
smith-machine, cable-station, lat-pulldown-station, seated-row-station,
pec-deck, chest-press-machine, shoulder-press-machine, leg-press-machine,
hack-squat-machine, leg-extension-machine, leg-curl-machine,
calf-raise-machine, assisted-pullup-machine, pull-up-bar, dip-bars,
resistance-band, medicine-ball, stability-ball, ab-wheel, box,
farmer-handles, sled, landmine
```

### `conditions.json` — small, for the safety layer

Starter set only; this is not a medical taxonomy:

```
flexion-intolerant-back, shoulder-impingement, anterior-knee-pain,
wrist-pain, elbow-tendinopathy, hypertension, pregnancy,
recent-shoulder-surgery, recent-knee-surgery
```

Each: `id`, `name`, `note` (one line, plain language, no diagnosis).

---

## Commit 2 — `feat(knowledge): exercise schema`

`knowledge/schema/exercise.schema.json`, `additionalProperties: false`.

```json
{
  "slug": "romanian-deadlift",
  "id": "66174728-d7e1-5441-b0a8-1fdf9be4e61d",
  "name": "Romanian Deadlift",
  "kind": "compound",
  "pattern": "hinge",

  "requiredEquipment": ["barbell", "plate"],
  "optionalEquipment": ["squat-rack"],

  "skillDemand": "high",
  "consequenceOfFailure": "high",
  "fatigueCost": "high",
  "damageMultiplier": 1.35,

  "unilateral": false,
  "defaultRestSec": 180,
  "repWindow": { "min": 5, "max": 15 },
  "romNote": "Full range is hip travel, not floor contact. Stop where the lower back would round.",

  "muscleOverrides": { "hamstring": 1.0, "glute": 0.8, "back_lower": 0.7, "forearm": 0.4 },
  "contraindications": ["flexion-intolerant-back"],

  "aliases": [
    { "text": "rdl",              "locale": "*",     "confidence": 0.95 },
    { "text": "romanian",         "locale": "*",     "confidence": 0.9  },
    { "text": "stiff leg deadlift","locale": "*",    "confidence": 0.7  }
  ],
  "spoken": { "short": "RDLs", "tts": "Romanian deadlift" },
  "safeToSpeakMidSet": false,

  "substitutes": ["dumbbell-romanian-deadlift", "leg-curl", "hip-thrust"],
  "legacyIds": [],

  "provenanceDefault": {
    "confidence": "moderate",
    "basis": "inherited",
    "source": "vos-prototype",
    "reviewed": "2026-09-01"
  },
  "provenance": {
    "requiredEquipment": { "confidence": "high", "basis": "judgement", "source": "pr14a-audit", "reviewed": "2026-09-01" }
  }
}
```

### Field notes

- **`requiredEquipment` / `optionalEquipment`** — arrays of `equipment.json`
  ids. Required means the exercise is impossible without it. Optional means it
  makes it safer or better (a rack for a heavy barbell lift). An exercise with
  `["none"]` required is doable on a mat.
- **`skillDemand` / `consequenceOfFailure`** — `low` | `moderate` | `high`,
  independent. Do not derive one from the other.
- **`damageMultiplier`** — feeds the existing recovery model in
  `voice-workout-os-source/src/17-recovery.js`. Carry the values from its
  `DAMAGE_BY_EXERCISE` table where present, otherwise `DAMAGE_BY_PATTERN`,
  otherwise `1.0`. **Re-key that table onto slugs**, not display names.
- **`unilateral`** — when true, one logged set counts as one set *per side* for
  volume. Note this in the contract; the arithmetic itself is PR 14b.
- **`repWindow`** — the range where the exercise makes sense. A cable fly is not
  a 3-rep lift; a heavy deadlift is not a 20-rep lift.
- **`aliases`** — objects, not bare strings. `locale` is `*` or a BCP-47 tag
  (`en-IN`, `en-GB`). `confidence` 0–1. Regional cases to cover during
  migration: "peck deck", "lat pull", "cycle" (stationary bike), "pressdown" vs
  "pushdown", "bent-over row" vs "barbell row".
- **`safeToSpeakMidSet`** — false for anything where the user is under a loaded
  bar and should not be answering a coach.
- **`provenanceDefault` / `provenance`** — the default applies to every field;
  the map overrides it per field. `basis` is one of:
  - `measured` — a real measurement or study backs this specific value
  - `consensus` — agreed across coaching sources
  - `judgement` — our call, defensible, not sourced
  - `inherited` — came from a pattern-level default, not observed for this
    exercise specifically

  Most migrated fields will be `inherited`. That is correct and expected. It
  lets the coach say "generally for hinges…" rather than implying precision it
  does not have, and it gives us a burn-down list.

---

## Commit 3 — `feat(knowledge): migrate the 62 prototype exercises`

`knowledge/catalog/exercises.json` — one array, 62 objects, schema-valid.

This migration **is** the equipment audit. Every wrong `load` value surfaces as
a row that will not validate against `equipment.json`.

Rules:

1. Fix the six known errors in the table above, plus anything else the audit
   turns up.
2. Where the prototype has separate barbell and dumbbell entries for the same
   movement, **keep them separate.** Do not merge them into one entry with
   variable equipment. Different setup, different exercise.
3. Keep the 12 hand-written substitute lists from `10-data.js` as-is — they
   encode judgement a rule cannot. Do not auto-generate the missing 50 in this
   PR; that is PR 14b, and it needs the substitution rule written into
   `contracts/19-substitution-engine.md` first.
4. Every `defaultRestSec`, `pattern`, `kind` and `muscleOverrides` value carries
   over unchanged unless the audit finds it wrong. If you change one, say so in
   the report.
5. `skillDemand` and `consequenceOfFailure`: derive the first from the old
   `technique` flag as a starting point, then set the second deliberately, one
   exercise at a time. Leg press, hack squat, behind-the-neck work, heavy
   hinges, overhead work and anything with a bar on the back deserve thought.

---

## Commit 4 — `feat(domain): catalog loader and validation`

Package `com.gymbuddy.domain.catalog`. Pure Kotlin, no file IO in the parser —
the loader takes a string, the caller supplies it.

Types: `Exercise`, `Equipment`, `Muscle`, `Pattern`, `Condition`, `Provenance`,
`Alias`, and a `Catalog` that owns lookup.

Lookup API this PR must provide:

- `bySlug(slug)`, `byId(uuid)`
- `resolveSpoken(text, locale)` → ranked candidates with confidence, **not a
  single answer**. When the top two are within a small margin, the caller must
  be able to ask the user. This is what stops the old
  `'pec deck' → Cable Fly` silent-wrong-answer bug.
- `musclesFor(exercise)` → applies the override-else-pattern rule
- `trainableWith(equipmentIds)` → every exercise whose `requiredEquipment` is a
  subset

### Tests (`:domain`)

1. Every entry validates against the schema.
2. Every `id` regenerates from its `slug` (PR 14 §2.1 rule); the five pinned
   literals are asserted independently.
3. Every equipment token in every exercise exists in `equipment.json`.
4. Every muscle key in every `muscleOverrides` exists in `muscles.json`.
5. Every `pattern` exists in `patterns.json`.
6. Every `contraindications` token exists in `conditions.json`.
7. Every `substitutes` slug resolves to an existing exercise.
8. No two exercises share a slug, an id, or an exact alias `text` + `locale`
   pair at confidence ≥ 0.9.
9. `resolveSpoken("pec deck", "en-IN")` does **not** return Cable Fly.
10. `resolveSpoken("lats", "*")` does not return an exercise at high confidence
    — it is a muscle name.
11. `trainableWith(["none"])` returns only bodyweight/mat exercises.
12. Exercise count is exactly 62. This test exists to stop scope creep and is
    deleted in PR 14b.

---

## Spec and contracts (same change, per `AGENTS.md`)

- `contracts/18-exercise-bank-and-detail.md` — record the schema, the two
  technique axes, the equipment model and the provenance model. Status stays
  `specified` until PR 14b delivers the ≥80 bar.
- `contracts/22-muscle-map-and-recovery.md` — record `damageMultiplier` and the
  override-else-pattern resolution rule.
- `contracts/19-substitution-engine.md` — currently says `implementing` with no
  engine behind it. Set it back to `specified`.
- `contracts/17-workout-plans-and-editing.md` — same drift; set to `specified`.
- `TECHNICAL_SPEC.md` — record **Q9 partially resolved**: catalog authored
  in-repo from `voice-workout-os-source/`; `docs/research/` is consulted
  reference and no rows are copied from it. Q9 fully closes at PR 14b.
- `scripts/check_living_spec.py` — extend the gate to cover `knowledge/vocab/`,
  `knowledge/schema/` and `knowledge/catalog/`.

## Definition of done

- `./gradlew :domain:test :persist:test` green.
- `scripts/check_living_spec.py` exits 0.
- `knowledge/catalog/exercises.json` holds exactly 62 schema-valid entries.
- No `load` field anywhere in the new catalog.
- No file under `docs/research/` is read at runtime or copied into `knowledge/`.
- Nothing in `:app` changed. No new UI.

## Report back

1. The full list of equipment corrections you made, old value → new value.
2. Every exercise where you set `consequenceOfFailure` higher than the old
   `technique` flag implied, and why.
3. **The gaps list.** Every field you marked `basis: "judgement"` because the
   prototype was silent or wrong and `docs/research/` did not settle it. This is
   the research queue — it is a deliverable, not an apology.
4. Any alias you found ambiguous enough that the resolver should ask rather than
   pick.
5. Anything in this prompt that turned out to be wrong.
