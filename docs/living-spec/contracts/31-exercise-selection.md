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

`rank()` scores survivors only. Factors: goal fit, target muscle coverage (fractional credits), skill match (penalty, not exclusion), freshness (fatigue on primary muscles), variety (position in `recentSlugs`), preference (accepted substitutions), role fit. Weights are `MVP HEURISTIC` in `DECISIONS_AND_THRESHOLDS.md`. Ties break on slug, alphabetically. Same inputs, same order. No randomness, no clock.

Every `ScoredExercise` carries `Reason`s in coach language. "Why this exercise?" is answerable from the return value.

### Dose

`dose()` is per slot, not weekly. Precedence: rules-layer goal range → catalog `repWindow` / `defaultRestSec` (catalog rest is a **point**, so rest collapses to one number) → `STRENGTH_UNSPOTTED_REP_FLOOR` when the lift lists a catch and the user has neither catch nor partner. Each narrowing appends a plain-language `cappedBy` reason.

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
6. Ties break on slug.
7. Every score has a speakable reason.
8. Fatigued primary drops below the fresh equivalent.
9. Dislike excluded when alternatives exist; kept when it is the only survivor.
10. Empty set still returns exclusions.
11. Dose: catalog window then unspotted floor, both in `cappedBy`.
12. `rank()` returns exercises, never a session.
