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

- `compatible(m, p, ctx)` is false when: the protocol type is not in the modality's `suitableProtocolTypes`; the protocol's `workSec` falls outside the modality's `minWorkSec`–`maxWorkSec` (sled work is 15–45 seconds, not 3-minute efforts); the user lacks the equipment (`none` in a slot is always satisfied); `requiresAerobicBase` and the user is a beginner or has no logged cardio history; the modality `impact` is too high for a declared condition (non-overridable); the modality lists that condition as a contraindication. Continuous protocols (no `workSec`) skip the bout check.
- `rank(goal, ctx, minutes)` returns legal `CardioPrescription`s, sorted deterministically. Every prescription carries plain-language `Reason`s. Exclusions are returned by `filterPairs`, not swallowed.
- `weeklyCardioTarget` is ACSM **150–300 minutes moderate-equivalent**. 75–150 vigorous is the same statement, not a second smaller target. Easy work (RPE 2–4, full sentences) is light activity: **credit 0**. Tempo counts 1:1 as moderate. Hard work counts as vigorous × 2 toward moderate-equivalent. Concurrent training changes *placement*, not the health target. Do not credit recovery minutes against the vigorous band.
- `placeOnSession` / `placeOnWeek` wire `cardioPlacement()`. Hard intervals do not go on a heavy lower-body day, and never before it. Easy aerobic on those days is recovery, not the ACSM target. **Variety:** a new modality before a new protocol. Cardio sessions stay inside the days the user said they train (`daysPerWeek`). The ACSM target does not invent extra days. When credit falls short: *"You're at 0 of 150. A fourth day would close it."* That is an offer, not a silent add. When every lifting day is heavy lower, the week also says so.
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

No legal pairing → no prescription (do not invent a machine). Work bout outside the modality range → exclude. Beginner + sprint request → exclude, explain the base. Knee pain + high impact → exclude by construction. Empty equipment → walking still legal. Heavy lower day → easy recovery only, and if that is every lifting day, say there is no room for intervals. ACSM shortfall → offer an extra day as a choice. Never grow the week past `daysPerWeek`.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens.

1. Both schemas validate; every equipment, muscle and condition token resolves.
2. Composition works: a protocol pairs with every modality that lists its type, with no enumerated rows.
3. `sprint_intervals` is never prescribed to a beginner.
4. A high-impact modality never appears for a user with a declared condition that contraindicates it — by construction, not by score.
5. A user with no equipment still gets walking.
6. Hard intervals are never placed on the same day as heavy lower-body work.
7. Weekly ACSM credit uses intensity: easy = 0, moderate = 1×, vigorous = 2×. Recovery minutes must not satisfy the 75–150 vigorous band.
8. Progression changes one variable at a time.
9. No prescription contains a pace, speed, wattage or heart rate.
10. Every prescription carries at least one plain-language reason.
11. Determinism over 100 runs.
12. A week does not repeat the same modality across consecutive cardio slots until other legal modalities are used.
13. When every lifting day is heavy lower, the week carries the no-room-for-intervals note.
14. A protocol whose `workSec` sits outside the modality's `minWorkSec`–`maxWorkSec` is incompatible. Sled push never gets 3-minute efforts.
15. `placeOnWeek` never adds days beyond `daysPerWeek`. A shortfall is a spoken offer, not a silent extra session.
