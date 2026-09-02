# PR — Cardio and conditioning

## Why this PR exists

The uploaded corpus treats cardio as a first-class domain — 236 records — and
the catalog has **zero**. The rules layer already has `cardioPlacement(...)`
from the programming-rules PR and nothing to place. `equipment.json` already
carries `exercise-bike`, `rower` and `jump-rope`, added during the warm-up work.

## The design decision, made up front — do not copy the corpus's shape

The corpus's 236 records are **12 modalities × 6 protocols enumerated out**:
walking-easy, walking-intervals, cycling-easy, cycling-intervals, and so on.
Almost every field repeats.

**Store the modalities and the protocols separately, and compose them.**

- Adding rowing then costs one row, not six.
- The coach can say *"same intervals, on the bike today"* without a row existing
  for that pair.
- The protocol's science lives in one place instead of being copy-pasted twelve
  times.

Not every pairing is legal — sprint intervals on a stair climber is a bad idea,
and swimming intervals need skill most users do not have. **Express that as a
compatibility rule, not as a matrix of rows.**

`docs/research/` stays reference only. Same quality bar as the warm-ups: if you
find yourself writing the same sentence with a different machine name, stop.

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL.
- Living spec updates in the same change. The 77 exercises, 30 warm-ups and
  30 stretches are untouched.
- **No invented paces, speeds, watts or heart-rate zones.** This is the same rule
  as "no invented loads". We do not know anyone's fitness, we have no sensor, and
  a user on beta blockers has a blunted heart rate anyway. **Effort is prescribed
  by RPE and the talk test only.**
- Deterministic.

---

## Commit 1 — `feat(knowledge): cardio modality and protocol schemas`

### `knowledge/schema/cardio-modality.json`

```json
{
  "slug": "incline-walking",
  "name": "Incline Walking",
  "requiredEquipment": [["treadmill"]],
  "impact": "low",
  "primaryDemand": ["calf", "quad", "glute", "hamstring"],
  "skillDemand": "low",
  "setting": "indoor",
  "suitableProtocolTypes": ["steady_state", "tempo"],
  "contraindications": [],
  "provenanceDefault": { … }
}
```

`impact`: `low` | `moderate` | `high` — this is the field that keeps someone
with anterior knee pain off running, so it is a safety field, not a label.

Cover roughly 12: walking, incline walking, running, cycling, rowing, elliptical,
stair climber, skipping, swimming, ski erg, assault/air bike, sled push. Add
equipment tokens where missing (`treadmill`, `elliptical`, `stair-climber`,
`ski-erg`, `air-bike`, `sled` already exists) — **vocabulary only, no exercise
rows.**

### `knowledge/schema/cardio-protocol.json`

```json
{
  "slug": "long-intervals",
  "name": "Long Intervals",
  "type": "intervals",
  "intensity": "hard",
  "effortGuidance": "RPE 7–8. You can speak a few words, not a sentence.",
  "workSec": 180, "recoverSec": 180, "rounds": [4, 6],
  "typicalSessionMin": [25, 40],
  "suitableGoals": ["CARDIO_FITNESS", "FAT_LOSS", "GENERAL_FITNESS"],
  "requiresAerobicBase": true,
  "provenanceDefault": { … }
}
```

`type`: `recovery` | `steady_state` | `tempo` | `threshold` | `intervals` |
`sprint_intervals`.

Roughly 6–8 protocols. **`requiresAerobicBase` is the important field** — the
Programming Guide is explicit that *"intervals are a spice after a base exists"*,
and the app must not hand sprint intervals to a beginner.

---

## Commit 2 — `feat(domain): compose and filter`

Package `com.gymbuddy.domain.cardio`.

```kotlin
data class CardioPrescription(
  val modality: CardioModality, val protocol: CardioProtocol,
  val durationMin: Int, val effort: String, val why: List<Reason>
)

fun compatible(m: CardioModality, p: CardioProtocol, ctx: PickContext): Boolean
fun rank(goal: Goal, ctx: PickContext, minutes: Int): List<CardioPrescription>
```

`compatible` is false when:
- the protocol type is not in the modality's `suitableProtocolTypes`
- the user lacks the equipment
- `requiresAerobicBase` is true and the user is a beginner or has no logged
  cardio history
- the modality's `impact` is too high for a declared condition —
  **non-overridable, exactly like the picker's safety exclusions**
- the modality is contraindicated for a declared condition

Same reasons discipline as the picker: every prescription carries plain-language
reasons, and every exclusion is returned rather than swallowed.

---

## Commit 3 — `feat(domain): weekly dose and placement`

### Weekly target
ACSM, already `PUBLISHED GUIDELINE` in `DECISIONS_AND_THRESHOLDS.md` via the
Programming Guide: **150–300 minutes moderate, or 75–150 vigorous, per week**,
individualised from the user's current base. Add
`fun weeklyCardioTarget(goal, trainingAge): IntRange2` and cite it.

### Placement — the rule that already exists and has been waiting
`cardioPlacement(strengthDay, goal)` is already in `programming`. Wire it:

- Hard intervals do **not** go on the same day as heavy lower-body work, and
  never before it.
- When a strength goal is primary, endurance work is scheduled around it, not
  through it.
- Easy aerobic work is compatible with almost anything and is the default.

Record in the contract that this is the concurrent-training interference
question, and that our stance is conservative: **when in doubt, easy aerobic on a
separate day.**

### Progression
One variable at a time — duration, then frequency, then intensity. Never all
three. Never an abrupt jump in intensity. This is the same
`nextSession`-style discipline as resistance progression, and it cites ACSM/NSCA.

---

## Commit 4 — `docs: contract 36`

New `contracts/36-cardio-and-conditioning.md`: the compose-don't-enumerate model,
the compatibility rules, the RPE-and-talk-test-only rule, the weekly target, the
placement rule, and progression.

`DECISIONS_AND_THRESHOLDS.md`: every constant. Expect the weekly target to be
`PUBLISHED GUIDELINE` and most protocol structures to be `MVP HEURISTIC` — say
so honestly rather than dressing them up.

`scripts/check_living_spec.py` covers contract 36 and the new knowledge paths.

---

## Tests

1. Both schemas validate; every equipment, muscle and condition token resolves.
2. Composition works: a protocol pairs with every modality that lists its type,
   with no enumerated rows.
3. `sprint_intervals` is never prescribed to a beginner.
4. A high-impact modality never appears for a user with a declared condition
   that contraindicates it — assert by construction, not by score.
5. A user with no equipment still gets a legal cardio option (walking).
6. Hard intervals are never placed on the same day as heavy lower-body work.
7. Weekly totals land inside the ACSM range for the user's goal.
8. Progression changes one variable at a time.
9. No prescription contains a pace, speed, wattage or heart rate — assert on
   the text.
10. Every prescription carries at least one plain-language reason.
11. Determinism over 100 runs.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` change. Exercises, warm-ups and stretches untouched.
- No enumerated modality × protocol rows anywhere.

## Report back

1. **Print three cardio prescriptions as a user would hear them**: a beginner
   with nothing, an intermediate with a gym on a lifting day, and someone whose
   goal is cardio fitness on a non-lifting day.
2. How many modalities and protocols you wrote, and how many legal combinations
   that yields. Compare with the corpus's 236 rows.
3. Which pairings you ruled out and why.
4. How placement interacts with the existing lifting weeks — print one week that
   has both.
5. Anything in this prompt that turned out to be wrong.
