---
id: 31-exercise-selection
status: implemented
schemaVersion: 1
source: docs/prompts/pr-exercise-picker.md
---

## Feature: Exercise selection (the picker)

**Status:** implemented  
**Core-loop role:** Ranks catalog candidates for **one template slot**. Contract 16 stays product law: one engine, template-based. This function does not choose a split, a session, or a week.

### Ordered filter

1. Contraindicated for a declared condition — excluded. Never overridable.
2. Named in a sharp or radiating pain report — excluded. Never overridable.
3. Equipment unsatisfiable — excluded.
4. Wrong pattern — excluded.
5. Disliked — excluded **only if something else survives**. If dislike would empty the slot, keep the lift and mark `keptDespiteDislike`.

The unspotted rep floor does not exclude. It caps dose.

Every exclusion is returned on `CandidateSet`, so contract 19 can say "no alternative given your equipment" from this list rather than inventing a machine.

### Scoring

`rank()` scores survivors only. Factors: goal fit (`bestFitGoals` on the exercise, not a compound/isolation proxy), target muscle coverage (fractional credits), skill match (penalty, not exclusion), freshness (fatigue on primary muscles), variety (a hit in `recentSlugs` takes the full `WEIGHT_VARIETY` penalty — no 1/(index+1) decay), preference (accepted substitutions), role fit. Weights are `MVP HEURISTIC` in `DECISIONS_AND_THRESHOLDS.md`.

Score ties: if both lifts share an **implement class** (freeweight, machine, cable, bodyweight), break toward lower `consequenceOfFailure`, then lower `skillDemand`, then slug. A machine and a free weight are not equivalent — they train different things — so consequence must not pick Machine Chest Press over a dumbbell press. Across classes, slug is the remaining deterministic breaker. Same inputs, same order. No randomness, no clock.

**Known gap — no progression.** The picker has no concept of moving someone along a lift family over time. A beginner who can only push-up today will still be offered push-ups next month; nothing promotes them to dumbbells, then a barbell. Recency can swap among catalog survivors this week. It does not graduate a movement. That is a real product hole. It is not this PR.

`rank()` still returns `List<ScoredExercise>`. `rankSlot()` returns `RankedSlot` with `SlotReadiness`: when every survivor's primary is cooked, ranking inside the slot is the wrong question — contract 32 proposes an adjustment instead.

Every `ScoredExercise` carries `Reason`s in coach language. "Why this exercise?" is answerable from the return value.

### Dose

`dose()` is per slot, not weekly. Precedence: rules-layer goal range → catalog `repWindow` / `defaultRestSec` (catalog rest is a **point**, so rest collapses to one number) → `STRENGTH_UNSPOTTED_REP_FLOOR` when the lift lists a catch and the user has neither catch nor partner. If the goal∩catalog intersection would collapse to a single rep, use the catalog window. `PRIMARY` sets are never fewer than `SECONDARY` sets at the same training age. Each narrowing appends a plain-language `cappedBy` reason.

### User behavior

Does not surface as its own screen. Template generation (contract 16) and substitution (contract 19) call `rank()` for a slot. Contract 19 takes the **top** result as the one primary alternative.

### System state

`com.gymbuddy.domain.picker`. Pure functions. Inputs: `Slot`, `Catalog`, `PickContext` (intake `UserContext`, resolved equipment, fatigue map, recent slugs, dislikes). Output: `CandidateSet`, `List<ScoredExercise>`, or `Prescription` for that slot. Never a `PlannedSession`.

### Voice behavior

**Not in Gym Mode as a picker UI.** A later substitution turn may speak a `Reason.explanation`. This PR does not call TTS.

### Structured event

None. The picker does not append to the event log. Substitution still commits only `SUBSTITUTION_APPLIED` (contract 19).

### Database change

None.

### UI state

None. No `:app` change.

### Error / recovery behavior

Empty survivors: still return every exclusion with its reason. Do not invent an exercise. Dislike cannot empty a slot. Safety exclusions cannot be scored back in.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens.

1. Contraindicated exercise never in `rank()`.
2. Sharp-pain exercise never in `rank()`.
3. Preference cannot promote an excluded exercise (by construction).
4. Kettlebell squat slot → goblet squat, not back squat.
5. Same inputs, same order, 100 runs.
6. Ties break toward lower consequenceOfFailure, then lower skillDemand, only among the same implement class; across classes, slug. A machine does not beat a dumbbell press on consequence.
7. Every score has a speakable reason.
8. Fatigued primary drops below the fresh equivalent.
9. Dislike excluded when alternatives exist; kept when it is the only survivor.
10. Empty set still returns exclusions.
11. Dose: catalog window then unspotted floor, both in `cappedBy`.
12. `rank()` returns exercises, never a session.
