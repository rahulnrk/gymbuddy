---
id: 19-substitution-engine
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Substitution engine

**Status:** implementing  
**Core-loop role:** Keeps the loop going when the plan collides with reality (occupied machine, missing equipment) without the user operating a catalog.

PR 14a ships the 12 hand-written substitute lists from the prototype as `substitutes` slugs. The substitution **rule** (muscle + pattern + equipment + `trainableWith`) is PR 14b. Named goldens already exist, so the living-spec gate keeps this `implementing` rather than rolling it back to `specified`.

### User behavior

**Behaviour change (contract 32).** Substitution is the **fourth** response to occupied equipment, not the first. The documented reflex used to be: occupied machine → offer a swap, and wait only when nothing is available to swap to. That is backwards. A trainer glances, asks how long, waits if that is rest, works in, or does the next movement first. They substitute after that.

The ladder, in order, is in `respondToUnavailable` (contract 32): `AskHowLong` when the wait is unknown → `Wait` when the wait is no longer than this movement's rest → `WorkIn` or `Defer` for protected roles (`PRIMARY` / `SECONDARY` / `CORE`) → `Substitute` (this contract) → `Skip`. Isolation and accessory substitute without the work-in / defer ceremony.

The swap itself is unchanged: one primary alternative, user accepts, `SUBSTITUTION_APPLIED` only.

“That machine is occupied.” AI, first: “How long do they look?” If they then accept a swap: “Let’s use dumbbell fly instead so we keep the same kind of work.” Suggests based on target muscle, movement pattern, goal, available equipment, history, preference. User accepts or rejects.

### System state

Current `workout_exercise`, user equipment, dislikes. Preserve training objective (`preserveObjective: true`).

### Voice behavior

Propose one primary alternative, not a list of ten. Wait for acceptance unless user already named the swap (“do dumbbell fly”). Barge-in. If safety discomfort, substitute more conservatively (reduce load / machine to dumbbell only if appropriate) without diagnosing.

The substitution engine consumes `rank()` from contract 31 and takes the **top** result. The picker returning a full ranking does not violate “one primary alternative”; that is a caller-side rule. Empty `CandidateSet.exclusions` is how the app says “no alternative given your equipment” without inventing a machine.

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
