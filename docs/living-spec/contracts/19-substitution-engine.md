---
id: 19-substitution-engine
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Substitution engine

**Status:** specified  
**Core-loop role:** Keeps the loop going when the plan collides with reality (occupied machine, missing equipment) without the user operating a catalog.

### User behavior

“That machine is occupied.” AI: “Let’s swap that cable fly for dumbbell fly today.” (product §30). Suggests based on target muscle, movement pattern, goal, available equipment, history, preference. User accepts or rejects.

### System state

Current `workout_exercise`, user equipment, dislikes. Preserve training objective (`preserveObjective: true`).

### Voice behavior

Propose one primary alternative, not a list of ten. Wait for acceptance unless user already named the swap (“do dumbbell fly”). Barge-in. If safety discomfort, substitute more conservatively (reduce load / machine to dumbbell only if appropriate) without diagnosing.

### Structured event

Candidates until accept (`SUBSTITUTION_PROPOSE` only). On accept: **`SUBSTITUTION_APPLIED` only**. Do **not** emit `EXERCISE_CHANGED` (KD28; Planning Mode editor only). Do not log a set for the old exercise.

### Database change

Projector of `SUBSTITUTION_APPLIED` updates `workout_exercises.exercise_id` and `substituted_from_exercise_id`. `source=voice` or `manual`. One mutation path.

### UI state

HUD exercise name switches. Optional “Swap” sheet, not required.

### Error / recovery

No alternative in catalog given equipment → say so, ask to skip or wait. Do not invent a machine they do not have.

### Tests

- `19-substitution.event.occupied-machine-accept` — `SUBSTITUTION_APPLIED` only; no `EXERCISE_CHANGED`
- `19-substitution.event.reject-keeps-original`
- `19-substitution.event.no-cable-not-suggested`
- `19-substitution.offline.local-catalog`

---
