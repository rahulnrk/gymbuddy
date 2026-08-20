# PR 1 vs PR 2 boundary

> CandidateValidator may resolve references from FourContexts but must never transition WorkoutSessionState.

The same gym utterance can be tested twice, at two layers. Catch-up product §9 is the example:

| Layer | Golden | Asserts |
|---|---|---|
| PR 1 validator | `04-nl-logging.event.multi-set-40x12-10-8` | three `ValidatedFact.SetCompleted`; reps 12/10/8; inherited 40 kg; `setNumberHint` only; shared batch/turn |
| PR 2 projector | `06-state-machine.event.catch-up-40x12-10-8` | official set numbers 1,2,3; no `REST_STARTED` between; rest after last if planned remain; no `SET_STARTED` per backfill |

PR 1 must not assert official set numbers, rest events, remaining planned sets, or session transitions.

`WorkoutEngine.applyFacts(List<ValidatedFact>)` is the only PR 2 intake for sets/skip/extra-set. It must not call `validateTurn`. Occupancy `Clarify` produces no facts, so the engine cannot silently rewrite set 2 while set 3 exists.

## PR 1 owns

```
CandidateEvent
schema validation
candidate classification
context lookup
validation decision
candidate facts (ValidatedFact)
correction target resolution (existing setId)
voice route eligibility
```

Output type: `ValidationDecision` of `ValidatedFact`. Not `WorkoutEvent`. Not `JsonObject`.

Allowed facts: `SetCompleted` (no `setNumber`), `SetCorrected` (required `setId` + `previous`), `SetDeleted` (required `setId` + `previous`), `WeightChanged`, `UserQuestion`, `UserFeedback`, `UserNote`, `Command` (classification only), `SubstitutionPropose`.

## PR 2 owns

```
event sequencing
official set numbers
SET_STARTED
REST_STARTED
REST_ENDED
session lifecycle
WorkoutSessionState
projector/fold
skip transitions
extra-set transitions
pause/resume
```

## Why ValidatedFact blocks a mini state machine

`CommittedDraft.payload: JsonObject` could hold `SET_STARTED`, `REST_STARTED`, or `setNumber: 3`. A sealed `ValidatedFact` cannot: those constructors do not exist. `SetCompleted.setNumber` is not a property; only `setNumberHint`. `SetCorrected.setId` is a resolved reference to a row already in `FourContexts.completedSets` (or `previousExerciseLastSet`). The validator cannot mint a new physical set as a correction.

Reading `snapshot.workout.sessionState` is allowed. Assigning it is impossible: `FourContexts` is an immutable data class passed by value, and `ValidatedFact` has no session field.
