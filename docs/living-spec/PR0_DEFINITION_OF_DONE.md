# PR 0 Definition of Done

A reviewer can mark PR 0 complete **only if every box is objectively true**.

## Product discipline

- [ ] `PRODUCT.md` is the master product spec, not a paraphrase.
- [ ] `AGENTS.md` forbids LLM table writes, invented history, dashboard-before-kernel, silent schema edits, treating estimated/inferred as known.
- [ ] No dashboard / Compose / Activity / Fragment sources exist under `app/`.
- [ ] No vendor STT/TTS/LLM SDK is imported from `:domain`.
- [ ] No Room `@Dao` writes from conversation packages (none should exist yet).
- [ ] Contract 26 (dashboard) status is `specified`, not `implementing` or `implemented`.

## Living spec materialized

- [ ] 28 files in `docs/living-spec/contracts/` with all eight pipeline stages.
- [ ] `docs/living-spec/events/CATALOG.md` lists every committed event with version, fields, validation, transitions, persistence, idempotency.
- [ ] `docs/living-spec/state-machine/CONTRACT.md` lists legal and illegal transitions including IDLE ↛ WORKOUT_STARTED.
- [ ] `docs/living-spec/boundaries/LLM_VS_APPLICATION.md` exists.
- [ ] `docs/living-spec/safety/DECISION_TABLE.md` exists.
- [ ] `docs/living-spec/voice/PORTS.md` exists.
- [ ] `docs/living-spec/testing/CASES.md` has ≥ 20 named cases.
- [ ] `TECHNICAL_SPEC.md` remains the complete spec; split files do not contradict it.

## Frozen schemas

- [ ] `docs/living-spec/schemas/v1/candidate-event.schema.json` schemaVersion const 1, additionalProperties false, per-type payload oneOf, reps integer.
- [ ] `workout-event.schema.json` oneOf committed types; LLM cannot satisfy this as extract output.
- [ ] Context schemas: workout, historical, conversational, user.
- [ ] `epistemic-fact.schema.json` stance enum known|estimated|inferred|uncertain|none.
- [ ] Individual schemas for SetCompleted, SetCorrected, ExerciseStarted, WorkoutStarted, WorkoutPaused, WorkoutResumed, WorkoutCompleted, UserQuestion, UserFeedback, UserNote, SafetyConcern.

## Kotlin :domain

- [ ] `CandidateEventEnvelope` parses valid JSON and rejects `"reps": "ten"`.
- [ ] `CandidateValidator` is an interface. No session-invariant implementation claiming PR 1 done.
- [ ] Validator output is sealed `ValidatedFact`, not `CommittedDraft`/`JsonObject`. No official `setNumber` on `SetCompleted`.
- [ ] `EventLog` is a persist-port interface. Validation package does not import it.
- [ ] `WorkoutTransitionContract` encodes IDLE + WORKOUT_STARTED as illegal.
- [ ] Every PR 1 golden has `input` + `given` + `expect`. Checker prints executable/invalid/missing counts.
- [ ] Provider ports exist (VAD, STT, TTS, LLM, VAD, audio focus, retrieval).
- [ ] Safety decision table exists as data, classifier is an interface.

## Tests / CI

- [ ] ≥ 20 JSON goldens in `tests/golden/`.
- [ ] `python scripts/check_living_spec.py` exits 0.
- [ ] `:domain` unit tests cover: valid Got 10 envelope, ten-as-string fail, unknown type fail, transition illegal from IDLE, SET_ACTIVE TTS overlay law, golden index ≥ 20.

## Explicitly not done (if any of these exist, PR 0 is **over** scope)

- SET_COMPLETED session validator implementation (PR 1)
- Full state machine / projector (PR 2)
- Room event_log (PR 3)
- Fake or real STT/TTS (PR 5 / 24)
- Gym Mode HUD (PR 10)
- Dashboard Today’s Workout UI (PR 18)
- Auth screens (PR 12)
- Chosen STT/TTS/LLM vendor
