# PR — Warm-ups and stretches

## Why this PR exists

The session builder already **counts** warm-up time — 5 minutes of pulse raise,
5 of drills, 3 ramp sets — against a warm-up that does not exist. Every printed
week reserves ten minutes for nothing. Meanwhile
`voice-workout-os-source/` has a warm-up planner that was never ported, and the
uploaded corpus treats warm-up, mobility and stretching as first-class domains
the product does not have at all.

## The design decision, made up front

**These are not exercises and must not be forced into the exercise schema.**

An exercise has sets, reps, load and a rep window. A warm-up drill has a
duration and a purpose. A stretch has a hold and a timing. Three record types,
three schemas — sharing the same L0 vocabularies (`muscles`, `patterns`,
`equipment`, `conditions`) and the same provenance block.

**Quality over count.** The corpus contains 150 warm-ups and 150 stretches, and
they are one-per-muscle generated boilerplate — *"Prepare {muscle} via easy
rhythmic movement"*. That is 300 rows carrying almost no information. Do not
reproduce that shape. **Roughly 30 real drills and 30 real stretches, each worth
reading, beats 300 templated ones.** If you find yourself writing the same
sentence with a different muscle name, stop.

`docs/research/` stays reference only. Do not copy rows from it.

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL.
- Living spec updates in the same change.
- **No invented loads**, in warm-ups too. A ramp set is expressed relative to the
  working weight ("about half", "a little under"), never as a number — the app
  does not know what anyone lifts until they log it.
- Deterministic. No randomness.
- The 77 exercises are untouched.

---

## Commit 1 — `feat(knowledge): warm-up and stretch schemas`

### `knowledge/schema/warmup.schema.json`

```json
{
  "slug": "world-greatest-stretch",
  "id": "<uuidv5 from slug, same namespace rule as exercises>",
  "name": "World's Greatest Stretch",
  "method": "mobility",
  "targetPatterns": ["lunge", "hinge", "squat"],
  "targetMuscles": ["glute", "hamstring", "quad", "oblique"],
  "durationSec": 60,
  "reps": 5,
  "perSide": true,
  "requiredEquipment": [["none"]],
  "howTo": "…",
  "commonErrors": "…",
  "effortGuidance": "Easy to moderate. This should not fatigue you.",
  "contraindications": [],
  "provenanceDefault": { … }
}
```

`method` is one of:
- `pulse` — general temperature raise (easy cycling, walking, rowing, skipping)
- `mobility` — joint range work
- `activation` — low-load work for muscles that are supposed to fire and often
  do not (glute medius, lower trap, serratus, deep neck flexors)
- `rehearsal` — the day's pattern, unloaded or very light

### `knowledge/schema/stretch.schema.json`

```json
{
  "slug": "doorway-pec-stretch",
  "name": "Doorway Pec Stretch",
  "type": "static",
  "targetMuscles": ["chest_mid", "chest_upper"],
  "timing": "post-session",
  "holdSec": 30,
  "repeats": 2,
  "perSide": true,
  "requiredEquipment": [["none"]],
  "howTo": "…",
  "intensityGuidance": "A five-out-of-ten stretch. Tension, not pain.",
  "contraindications": [],
  "provenanceDefault": { … }
}
```

`type`: `static` | `dynamic` | `active` | `pnf`.
`timing`: `warmup` | `post-session` | `standalone`.

**The timing rule is not cosmetic and belongs in the contract:** dynamic work
belongs in the warm-up; static holds belong after training or in a separate
session. Both the Programming Guide and the stretching meta-analyses in
`programming-numbers-2026-09-01.md` say so.

---

## Commit 2 — `feat(knowledge): the content`

### Warm-ups — target ~30, not 150

Cover, at minimum:
- **Pulse**: 3–4 modalities (walking/incline walking, easy cycling, rowing,
  skipping) so the choice can match the room.
- **Mobility**: ankle, hip, thoracic spine, shoulder, wrist — the joints that
  actually limit the patterns in our catalog.
- **Activation**: glute medius, lower trap, serratus, deep neck flexors, and the
  rotator cuff. These are the ones the corpus and NASM both single out.
- **Rehearsal**: one per movement pattern in `patterns.json` — the pattern
  itself, unloaded (bodyweight squat, hip hinge with a dowel, scapular push-up,
  band pull-apart, dead-bug).

### Stretches — target ~30, not 150

One or two genuinely different stretches for each of the 24 muscle regions that
can usefully be stretched. Some regions do not need one; say so rather than
inventing filler.

**Record the rule that stops stretching being a universal answer**, from the
corpus: *"Feeling tight does not automatically mean a muscle needs to be
stretched"* and *"do not stretch a muscle that is already long and weak."* This
goes in the contract, and the planner must never prescribe a stretch as a fix
for weakness.

---

## Commit 3 — `feat(domain): the warm-up planner`

Package `com.gymbuddy.domain.warmup`.

```kotlin
data class WarmupPlan(
  val pulse: WarmupEntry?,             // 3–5 min
  val drills: List<WarmupEntry>,       // 3–6, matched to today's patterns
  val rampSets: List<RampSet>,         // 1–3 on the first primary lift
  val estimatedMinutes: Int,
  val notes: List<String>
)

data class RampSet(val reps: Int, val effortDescription: String) // "about half your working weight"

fun planWarmup(session: PlannedSession, ctx: PickContext, library: WarmupLibrary): WarmupPlan
```

The structure is the Programming Guide's, and it is published:
**general preparation → mobility where useful → activation where useful →
movement rehearsal → specific ramp-up sets → working sets.**

Rules:
- Drills are selected by `targetPatterns` ∩ the session's patterns. A warm-up for
  a squat day is not a warm-up for a pull day.
- Equipment-aware, like everything else. No pulse-raise on a bike the user does
  not have.
- Contraindication-aware. A drill contraindicated for a declared condition is
  excluded, non-overridably, exactly as in the picker.
- Ramp sets attach to the **first primary lift only**, and are relative.
- Short. The guide is explicit that a warm-up should improve readiness without
  creating fatigue. If the plan exceeds ~12 minutes, cut drills.

### Stretch suggestion

```kotlin
fun suggestStretches(session: PlannedSession, library: StretchLibrary): List<Stretch>
```

Post-session, `static`, targeting the muscles that actually did work in that
session (use the fractional contribution model). **Two or three, not a routine.**
Never offered as a remedy for weakness.

---

## Commit 4 — `feat(domain): wire into the builder`

- `PlannedSession` gains a `warmup: WarmupPlan` and optional `cooldown`.
- The duration model uses the **real** warm-up estimate instead of the current
  placeholder constants. Remove the placeholder; keep a row in
  `DECISIONS_AND_THRESHOLDS.md` explaining what replaced it.
- Re-check that the printed weeks still land inside the ±15 minute window. If
  they now overshoot, that is real information — report it, do not quietly cut.

## Commit 5 — `docs: contracts`

- New `contracts/34-warmup-and-mobility.md`: the five-stage structure, the
  selection rules, the stretch timing rule, the no-stretching-for-weakness rule,
  and the relative-ramp-set rule.
- `DECISIONS_AND_THRESHOLDS.md`: every new constant, with provenance. The
  five-stage structure and the 3–5 min / 3–6 drills / 1–3 ramp figures are
  `PUBLISHED GUIDELINE` (Coach & AI Programming Guide §4.3, NSCA performance
  preparation).
- `scripts/check_living_spec.py` covers contract 34 and the new knowledge paths.

## Tests

1. Both new schemas validate; every id regenerates from its slug.
2. Every muscle, pattern, equipment and condition token resolves.
3. A squat-day warm-up contains squat-pattern rehearsal and not a pull drill.
4. Equipment: a bodyweight-only user gets no bike-based pulse raise.
5. A contraindicated drill never appears, at any weight.
6. Ramp sets attach only to the first primary lift and contain **no numeric
   load** — assert no digits-plus-kg anywhere in the text.
7. Warm-up estimate never exceeds ~12 minutes; drills are cut, not the pulse.
8. Static stretches are never scheduled pre-session; dynamic never post- only.
9. Suggested stretches target muscles that actually worked in that session.
10. Every printed week still lands inside its duration window with the real
    warm-up in place.
11. Every drill and stretch carries provenance; a row without one fails the build.
12. No two warm-ups share a `howTo` string — the anti-boilerplate test.

## Report back

1. **Print one full session with its warm-up and cooldown**, as a user would
   hear it. This is the first time the session is complete end to end.
2. How many warm-ups and stretches you wrote, and which muscle regions you
   decided did not need a stretch.
3. What the real warm-up did to the duration estimates.
4. Anything in the five-stage structure you could not express with the data
   available.
5. Anything in this prompt that turned out to be wrong.
