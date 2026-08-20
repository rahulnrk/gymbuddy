---
id: 06-deterministic-workout-state-machine
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Deterministic workout state machine

**Status:** implementing  

**Core-loop role:** Kernel source of truth for session.

### User behavior

Indirect: the companion “knows where we are” (exercise, set index, rest). User never sets state by saying “enter RESTING.”

### System state

WorkoutSessionState transitions **only** from validated events (see state diagram). `setNumber` assigned here:

- Next number = max existing non-deleted set_number on this `workout_exercise` + 1, unless correction reassigns.
- Planned sets do not create completed `workout_sets` rows (`ai-generated` planned data lives on `workout_exercises`, not as fake completed sets).

Cannot skip `SESSION_INITIALIZED` / `WORKOUT_STARTED`. `WORKOUT_STARTED` is valid only from `WORKOUT_STARTING`. Cannot complete from `IDLE`. Catch-up does not emit `SET_STARTED` per backfilled set.

### Voice behavior

**Explicit none.** The state machine does not speak. Coaching reads state after transitions. `SET_ACTIVE` + `AI_SPEAKING` is forbidden except safety ≥ high.

### Structured event

Consumes catalog events including `EXERCISE_SKIPPED` and `PLANNED_SETS_CHANGED`. Emits follow-on: `EXERCISE_STARTED`, `SET_STARTED` (required for `SET_ACTIVE`), `REST_STARTED` (not between catch-up members), `REST_ENDED` `{ reason: timer|user|logged_through }`, `WORKOUT_COMPLETED` when remaining work is zero after last `SET_COMPLETED` or skip of the last exercise.

### Database change

Projector: `sessions.state`, `workout_sets`, `workout_exercises` progress. `source` preserved from inbound event.

### UI state

HUD bound to projected snapshot. Rest timer is informational **and** emits `REST_ENDED` on expiry (does not force the user to lift). User may say “next” or live-log from `RESTING` (`logged_through`). Pause freezes `restRemainingSec`.

### Error / recovery

Illegal event for state → `INTENT_REJECTED` or `ERROR_RECOVERY`, not a crash. Restore from event log fold after process kill via `SESSION_REHYDRATED`.

### Tests

- `06-state-machine.state.set-started-required` — no `SET_ACTIVE` without `SET_STARTED`
- `06-state-machine.event.catch-up-40x12-10-8` — **PR 2 layer** of the same §9 utterance as `04-nl-logging.event.multi-set-40x12-10-8`. Official numbers 1,2,3; **one** `REST_STARTED` after last if planned_sets>3; zero rest between; no `SET_STARTED` per backfill. Do not implement in PR 1.
- `06-state-machine.event.live-set-then-rest-started`
- `06-state-machine.event.workout-started-from-idle-rejected`
- `06-state-machine.event.fold-100-idempotent`
- `06-state-machine.state.pause-resting-keeps-remaining-sec`
- `06-state-machine.event.skip-this-fold-restores` — `EXERCISE_SKIPPED` in the log; fold lands on next exercise
- `06-state-machine.event.extra-set-fold-restores` — `PLANNED_SETS_CHANGED` in the log; remaining sets include the extra

---
