---
id: 36-cardio-and-conditioning
status: implemented
schemaVersion: 1
source: docs/prompts/pr-cardio.md
---

## Feature: Cardio and conditioning

**Status:** implemented  
**Core-loop role:** Secondary. Strength days stay the kernel. Cardio is placed around lifting, spoken in coach language, never as a pace or a heart-rate zone. Gym Mode does not become a spin class.

### User behavior

They hear a modality, a session shape, a duration, and how hard it should feel in RPE and the talk test. *"Walk, easy steady, about 20 minutes. A conversation you could hold with a friend walking beside you."* They never hear a pace, a wattage, a speed, or a heart-rate zone. We have no sensor. A user on beta blockers has a blunted heart rate anyway. Inventing a load is forbidden for lifting; inventing a pace is the same crime here.

A beginner with no kit still gets a walk. Sprint intervals are never handed to a beginner. `requiresAerobicBase` is the field that enforces it — the Programming Guide is explicit that intervals are a spice after a base exists.

Someone with anterior knee pain is not offered running. Impact is a safety field, not a label.

### System state

**Compose, do not enumerate.** The corpus stored 236 rows as 12 modalities × 6 protocols written out. We store 12 modalities and 7 protocols and pair them at runtime.

`knowledge/catalog/cardio-modalities.json` and `knowledge/catalog/cardio-protocols.json`. `com.gymbuddy.domain.cardio`:

- `compatible(m, p, ctx)` is false when: the protocol type is not in the modality's `suitableProtocolTypes`; the user lacks the equipment (`none` in a slot is always satisfied); `requiresAerobicBase` and the user is a beginner or has no logged cardio history; the modality `impact` is too high for a declared condition (non-overridable); the modality lists that condition as a contraindication.
- `rank(goal, ctx, minutes)` returns legal `CardioPrescription`s, sorted deterministically. Every prescription carries plain-language `Reason`s. Exclusions are returned by `filterPairs`, not swallowed.
- `weeklyCardioTarget(goal, trainingAge)` is ACSM 150–300 minutes moderate (or 75–150 vigorous). Beginners start 75–150. Strength/hypertrophy stay in the 75–150 band so lifting stays primary.
- `placeOnSession` / `placeOnWeek` wire `cardioPlacement()`. Hard intervals do not go on a heavy lower-body day, and never before it. Easy aerobic is compatible with almost anything and is the default. **When in doubt, easy aerobic on a separate day.** That is the concurrent-training interference question, answered conservatively.
- `nextCardioSession` changes one variable at a time: duration, then frequency, then intensity. Never all three. Never an abrupt jump in intensity.

No enumerated modality × protocol rows anywhere.

### Voice behavior

**Not in Gym Mode as a cardio UI.** `speak(prescription)` is coach language so a later turn can read it. This PR does not call TTS. Effort copy is RPE and the talk test only.

### Structured event

None. Cardio is planned, not a `WorkoutEvent` in this PR.

### Database change

None. Model only.

### UI state

None. No `:app` change.

### Error / recovery behavior

No legal pairing → no prescription (do not invent a machine). Beginner + sprint request → exclude, explain the base. Knee pain + high impact → exclude by construction. Empty equipment → walking still legal. Heavy lower day + only-hard remaining → skip hard, keep easy. Weekly fill stops at five cardio sessions.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens.

1. Both schemas validate; every equipment, muscle and condition token resolves.
2. Composition works: a protocol pairs with every modality that lists its type, with no enumerated rows.
3. `sprint_intervals` is never prescribed to a beginner.
4. A high-impact modality never appears for a user with a declared condition that contraindicates it — by construction, not by score.
5. A user with no equipment still gets walking.
6. Hard intervals are never placed on the same day as heavy lower-body work.
7. Weekly totals land inside the ACSM range for the user's goal.
8. Progression changes one variable at a time.
9. No prescription contains a pace, speed, wattage or heart rate.
10. Every prescription carries at least one plain-language reason.
11. Determinism over 100 runs.
