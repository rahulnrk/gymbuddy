---
id: 32-in-session-adaptation
status: implemented
schemaVersion: 1
source: docs/prompts/pr-adapt-in-session.md
---

## Feature: In-session adaptation

**Status:** implemented  
**Core-loop role:** When something changes today — fatigue, a short window, a busy rack — the coach proposes one next step. The user accepts. The kernel does not rewrite the programme on its own.

This feature modifies an existing planned session. It never generates one. Contract 16 stands.

### User behavior

Before the first lift, if today's readiness is quiet (`UNKNOWN`, or no signal), the coach says nothing and the session is as planned. If a target is cooked, the coach offers cheaper changes first: leave more reps in the tank, then fewer sets, then a named other exercise, then dropping extra work. Isolation and accessory go before a main lift. A short clock drops exercises from the end.

When a machine is taken, the coach does not open a swap menu. They ask how long, wait if that is just rest, work in, or do the next movement first. Substitution is fourth. Skip is last.

The user accepts a proposal before anything is written.

### System state

`com.gymbuddy.domain.adapt`. Pure functions.

- `adjustForToday(session, readiness, ctx, catalog)` → `List<Adjustment>`. Never mutates `session`.
- `respondToUnavailable(slot, session, ctx, catalog, expectedWaitMinutes)` → one `UnavailableResponse`. Not a menu.
- `SlotReadiness` on the picker tells the adapter when ranking inside the slot is the wrong question (`allCandidatesFatigued`).
- `EXERCISE_DEFERRED` reorders the current movement after the next one. Cap: `DEFER_CAP = 2` (the third attempt on the same movement is `EXERCISE_SKIPPED`, reason kept). Illegal from `SET_ACTIVE`.
- History: deferred-then-completed is completed. Deferred-and-never-done is skipped, reason retained.

`schemaVersion` stays **1**. `EXERCISE_DEFERRED` is additive.

### Voice behavior

Contract 09: no TTS during `SET_ACTIVE` except safety ≥ high. Adaptation talk happens while resting or between movements. `SpokenProposal` queues on `DrainOn.RESTING`. `maySpeakNow` is false in `SET_ACTIVE`.

The first question when equipment is occupied: **“How long do they look?”** That is the substitute for a glance. Do not guess a wait. Do not substitute before asking.

Wait, when it is just rest: **“That's less than your rest anyway. We'll wait.”** No mixed units, no arithmetic.

Every `why` and `question` is coach language: no field names, no underscores, no bare numbers, no two units of time in one sentence, no pattern ids. Name the exercise.

### Structured event

Nothing is committed until the user accepts.

- Accept deferral → `EXERCISE_DEFERRED { workoutExerciseId, exerciseId, fromPosition, reason }`. Engine may then `EXERCISE_STARTED` the movement now first.
- The N+1th deferral of the same movement → `EXERCISE_SKIPPED` with the deferred reason. Not a silent skip.
- Accept substitute → `SUBSTITUTION_APPLIED` (contract 19). Not `EXERCISE_CHANGED`.
- Accept fewer sets → `PLANNED_SETS_CHANGED`.
- Do **not** encode deferral as `EXERCISE_SKIPPED` plus a re-add.

### Database change

None beyond the event log. Projector fold of `EXERCISE_DEFERRED` reorders `workout_exercises` in the snapshot and increments `deferralCount`. `EXERCISE_SKIPPED.reason` is optional and retained.

### UI state

None. No `:app` change. HUD follows the projected current movement after a deferral is accepted.

### Error / recovery behavior

- `expectedWaitMinutes == null` → ask, never guess, never substitute.
- Deferral during `SET_ACTIVE` → reject. Finish the set or rest first.
- Empty alternative set after occupying the only equipment that fits an isolation slot → skip, and say there is nothing else that does this job with the equipment they have.
- `UNKNOWN` readiness with no other signal → `AsPlanned`, stay quiet.

### Tests

Evidence is `:domain` Kotlin tests plus one event golden.

- `32-adaptation.event.exercise-deferred-round-trip` — codec + projector; queue after the next movement; `schemaVersion` 1
- Cooked quads, squat slot → `SlotReadiness.allCandidatesFatigued` and an adjustment
- Mild fatigue → `AsPlanned`
- `UNKNOWN` → `AsPlanned`, no invented fatigue
- `LowerEffort` / `ReduceSets` before `DropSlot`; primary never dropped while isolation could be
- One bad night never `DropSlot`
- Input session unchanged
- Null wait → `AskHowLong`, never `Substitute`
- Wait ≤ rest → `Wait`
- `PRIMARY` + 6-minute wait + later different-kit movement → `Defer`, not `Substitute`
- `ISOLATION` + 6-minute wait → `Substitute`
- Deferral refused mid-set
- Third deferral → `EXERCISE_SKIPPED` with reason
- Deferred-then-completed reports completed
- Every `why` / `question` is plain language
