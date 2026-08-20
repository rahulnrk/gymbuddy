---
id: 17-workout-plans-and-editing
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Workout plans and workout editing

**Status:** implementing  
**Core-loop role:** Supporting. Lets the user (and later the adapter) change what Gym Mode will run. AI plan is a recommendation, not a lock (product §29).

### User behavior

Edit order, sets, rep range, weight, rest, exercise, workout name, training days. During gym: voice “skip this,” “swap,” “add one more set” goes through events, not a hidden second editor.

### System state

If session active, in-gym swaps go through `SUBSTITUTION_APPLIED` (KD28). Extra set = `PLANNED_SETS_CHANGED`. Skip = `EXERCISE_SKIPPED`. Planning Mode editor uses `EXERCISE_CHANGED` and `PLANNED_SETS_CHANGED` reason=`user_edit`. Do not patch `planned_sets` except via those events.

### Voice behavior

Gym Mode: natural “skip this,” “let’s do rows instead,” “give me one more set.” Planning Mode: **silent** (visual editor).

### Structured event

Planning: `EXERCISE_CHANGED`, `PLANNED_SETS_CHANGED` reason=`user_edit`. Gym: `SUBSTITUTION_APPLIED` only for swaps; `EXERCISE_SKIPPED`; `PLANNED_SETS_CHANGED` reason=`extra_set`; `WEIGHT_CHANGED`; `REPS_CHANGED`; `SET_DELETED`. Validation: cannot edit another user’s workout. No hidden editor writes.

### Database change

`workouts`, `workout_exercises`. Live set changes via event log.

### UI state

Editor in Planning Mode. HUD does not require opening the editor to skip/swap (voice + optional HUD buttons). User not required to type to skip.

### Error / recovery

Edit conflict with in-flight set: wait until not `SET_ACTIVE`. Offline edits local.

### Tests

- `17-plans.event.skip-this` — COMMAND skip → one `EXERCISE_SKIPPED`; fold after kill does not return to that exercise; no invented sets
- `17-plans.event.one-more-set-after-yes` — “Can we do one more?” then “Yeah.” → one `PLANNED_SETS_CHANGED` (`previous+1`); fold restores `planned_sets`
- `17-plans.event.give-me-one-more-direct` — confidence ≥ 0.7, no second ask
- Reorder persists. Manual editor and voice produce same tables. Not a different engine.

---
