# Event catalog (schemaVersion 1)

Authoritative committed types. JSON: `docs/living-spec/schemas/v1/workout-event.schema.json`.
Candidates: `docs/living-spec/schemas/v1/candidate-event.schema.json`.

The LLM emits **CandidateEvent** only. The engine emits **WorkoutEvent**.

## EventBase (every committed event)

| Field | Required | Notes |
|---|---|---|
| eventId | yes | UUID, client-generated, idempotency key with userId |
| sessionId | yes, nullable | null only for non-session profile/memory events |
| userId | yes | |
| occurredAt | yes | display ISO-8601 |
| recordedAt | yes | queue time; duplicate window uses this, not occurredAt alone |
| source | yes | voice \| manual \| imported \| ai-generated \| system |
| schemaVersion | yes | **1** |
| type | yes | catalog enum |
| payload | yes | per-type object, additionalProperties false |
| transcriptId | no | short-lived |
| language | no | BCP-47 of utterance |
| confidence | no | extractor overallConfidence |
| sttFinalHash | no | sha256 of normalized STT final |
| batchId | no | catch-up / turn id |

**Idempotency:** `(userId, eventId)` unique. Re-delivery is a no-op.

**Set identity:** `setId` assigned at first `SET_COMPLETED`. Corrections reference `setId`.

**Persistence:** append-only `event_log`. Projectors fold into read models. Never UPDATE a log row.

---

## SESSION_INITIALIZED

- **Version:** 1
- **Purpose:** Open Gym Mode session from IDLE.
- **Required payload:** sessionId, workoutId
- **Optional:** none
- **Source:** manual (Start) or system
- **Validation:** from IDLE only; workout exists; user owns it; no other non-terminal session
- **Transition:** IDLE → WORKOUT_STARTING
- **Persistence:** sessions row
- **Idempotency:** duplicate Start while in-progress is a no-op (same sessionId)
- **Correction:** n/a
- **LLM:** must not emit as committed truth

## WORKOUT_STARTED

- **Version:** 1
- **Purpose:** Hydrate succeeded; companion is live.
- **Required payload:** workoutId, startedAt
- **Source:** system (engine after hydrate)
- **Validation:** from WORKOUT_STARTING **only**, never IDLE
- **Transition:** WORKOUT_STARTING → READY_FOR_EXERCISE
- **Persistence:** sessions.state, workouts.status=in_progress
- **LLM:** forbidden

## SESSION_REHYDRATED

- **Version:** 1
- **Purpose:** Restore after process death.
- **Required payload:** sessionId
- **Source:** system
- **Validation:** local non-terminal session exists
- **Transition:** restore snapshot; not a second WORKOUT_STARTED
- **LLM:** forbidden

## EXERCISE_STARTED

- **Version:** 1
- **Purpose:** Current movement is this exercise.
- **Required payload:** workoutExerciseId, exerciseId, plannedSets
- **Optional:** targetReps, restTargetSec
- **Source:** system
- **Validation:** exercise in this workout; from READY_FOR_EXERCISE or after EXERCISE_COMPLETE
- **Transition:** → EXERCISE_ACTIVE
- **LLM:** forbidden as committed (may name exercise in speech)

## SET_STARTED

- **Version:** 1
- **Purpose:** Enter SET_ACTIVE. Required. Engine only.
- **Required payload:** workoutExerciseId, setNumber
- **Source:** **system**
- **Validation:** setNumber = next engine number. Never LLM-emitted.
- **Transition:** EXERCISE_ACTIVE or RESTING → SET_ACTIVE
- **Persistence:** state only (no workout_sets row)
- **LLM:** forbidden

## SET_COMPLETED

- **Version:** 1
- **Purpose:** Record a performed set.
- **Required payload:** setId, workoutExerciseId, exerciseId, setNumber, loggingMode
- **Optional:** weight, weightUnit, weightKg, reps, rpe, rir, effortNote, batchId
- **Source:** voice or manual
- **Validation:** see living spec closed rules (inherit weight MUST; never inherit reps; confidence 0.55; duplicate hash 10s; bystander route; catch-up vs live)
- **Transition:** SET_ACTIVE or RESTING → transient SET_COMPLETED then RESTING or EXERCISE_COMPLETE
- **Persistence:** workout_sets insert; event_log append
- **Idempotency:** eventId; sttFinalHash+10s drop
- **Correction:** later SET_CORRECTED / SET_DELETED against setId; do not insert a parallel row. PR 1 emits `ValidatedFact.SetCorrected`/`SetDeleted` with `previous`; PR 2 appends the catalog event.
- **LLM:** candidate only

## SET_CORRECTED

- **Version:** 1
- **Purpose:** Fix an existing set. Product §32.
- **Required payload:** setId, fields, previous
- **Source:** voice or manual
- **Validation:** setId exists in session; must not create a new set; cross-exercise 120s rule
- **Transition:** does not jump exercises; rest timer not reset unless set was not actually completed
- **Persistence:** append event; projector overwrites read-model row; last_correction_event_id
- **Correction:** this IS the correction
- **LLM:** candidate only

## SET_DELETED

- **Version:** 1
- **Purpose:** Soft-delete a mistaken set.
- **Required payload:** setId
- **Optional:** reason
- **Validation:** setId exists; renumber remaining sets deterministically
- **Persistence:** deleted_at on row; log append
- **LLM:** candidate only

## WEIGHT_CHANGED

- **Version:** 1
- **Purpose:** This-session working weight. Not next-workout prescription.
- **Required payload:** workoutExerciseId, weight, weightKg
- **Optional:** weightUnit
- **Validation:** positive finite; kg|lb
- **Persistence:** workout_exercises working weight / session snapshot
- **LLM:** candidate only

## REPS_CHANGED

- **Version:** 1
- **Purpose:** Change planned target range or a specific set’s reps.
- **Optional payload:** workoutExerciseId, targetReps, setId, reps
- **Validation:** must target planned range OR an existing set
- **LLM:** candidate only

## EXERCISE_CHANGED

- **Version:** 1
- **Purpose:** Planning Mode editor only (KD28).
- **Required payload:** workoutExerciseId, fromExerciseId, toExerciseId
- **Optional:** reason
- **Validation:** **not** used for in-session swaps
- **Gym Mode:** forbidden as the swap commit

## EXERCISE_SKIPPED

- **Version:** 1
- **Purpose:** Skip current movement. fold() must restore.
- **Required payload:** workoutExerciseId
- **Optional:** reason (plain language; required when skip is the deferral cap)
- **Source:** system after validated COMMAND skip (confidence ≥ 0.7), confirmed question, or the N+1th `EXERCISE_DEFERRED` on the same movement
- **Validation:** active session; not already complete/skipped
- **Transition:** → EXERCISE_COMPLETE. Does not invent completed sets.
- **Persistence:** workout_exercises.skipped_at; reason retained
- **LLM:** COMMAND candidate only; never committed truth directly

## EXERCISE_DEFERRED

- **Version:** 1 (additive; `schemaVersion` stays 1)
- **Purpose:** Do this movement later in the same session. The missing “the rack is busy — we'll come back” event. Not a skip.
- **Required payload:** workoutExerciseId, exerciseId, fromPosition, reason
- **Source:** system after the user **accepts** a deferral proposal. Never automatic.
- **Validation:** legal from `EXERCISE_ACTIVE`, `READY_FOR_EXERCISE`, `RESTING`, `EXERCISE_COMPLETE`. **Illegal from `SET_ACTIVE`.** After `DEFER_CAP` (2) deferrals of the same movement, emit `EXERCISE_SKIPPED` with the same reason instead.
- **Transition:** → `READY_FOR_EXERCISE`. Projector moves the movement to after the next one and increments `deferralCount`. Engine then `EXERCISE_STARTED` the movement now first.
- **History:** deferred-then-completed is completed. Deferred-and-never-done is skipped, reason retained. Do **not** log skip-plus-readd.
- **Persistence:** event_log append; snapshot plan order
- **LLM:** forbidden as committed truth
- **Schema:** `docs/living-spec/schemas/v1/exercise-deferred.schema.json`

## PLANNED_SETS_CHANGED

- **Version:** 1
- **Purpose:** Extra set or plan edit. fold() must restore.
- **Required payload:** workoutExerciseId, plannedSets, previousPlannedSets, reason (extra_set|user_edit)
- **Validation:** plannedSets ≥ non-deleted completed sets; extra_set ⇒ previous+1
- **Persistence:** workout_exercises.planned_sets. Does **not** log a set.
- **LLM:** COMMAND candidate only

## REST_STARTED / REST_ENDED

- **REST_STARTED** required: workoutExerciseId, afterSetId, targetSec. After live SET_COMPLETED or last catch-up member if remaining planned > 0. Never between catch-up members.
- **REST_ENDED** required: workoutExerciseId, reason timer|user|logged_through. From RESTING.
- **Source:** system
- **LLM:** forbidden

## WORKOUT_PAUSED

- **Required payload:** reason user|audio_error|app_background|safety
- **Transition:** → PAUSED; store prePauseState + restRemainingSec
- **Validation:** not already complete

## WORKOUT_RESUMED

- **Required payload:** restoredState
- **Optional:** restRemainingSec
- **Validation:** from PAUSED only
- **LLM:** forbidden as committed (COMMAND resume is the candidate)

## WORKOUT_COMPLETED

- **Required payload:** completedAt
- **Optional:** summaryRef
- **Producer:** engine when remaining work is zero, or user “I’m done”
- **LLM:** must not declare complete without event

## USER_NOTE_CREATED

- **Required payload:** noteId, textCanonical, textOriginal, lang, scope
- **Optional:** refId
- **Persistence:** workout_notes, user-visible
- **Mutates workout truth:** no (memory)

## USER_FEEDBACK

- **Required payload:** kind enum
- **Optional:** intensity, refId
- **Not a set**

## USER_QUESTION

- **Required payload:** questionOriginal, questionCanonical
- **Optional:** topic
- **Mutates workout truth:** no
- **Feature 28:** retrieval-only answers

## COACHING_RESPONSE

- **Required payload:** textSpoken (nullable), lang, silence, citedFacts
- **Optional:** actId
- **Always persisted.** silence required.
- **Producer:** coaching policy, not free-form LLM write
- **citedFacts** must resolve (row or catalog id). stance=none cannot assert numbers.

## SAFETY_CONCERN

- **Required payload:** severity, category, transcriptSnippet, action
- **Producer:** safety layer on raw text
- **May pause**
- **Always persisted**
- **LLM cannot override** severity ≥ high

## CLARIFICATION_REQUESTED / INTENT_REJECTED

- Conversational only. Do not mutate sets.

## SUBSTITUTION_APPLIED

- **Required payload:** workoutExerciseId, fromExerciseId, toExerciseId, preserveObjective=true
- **Only** in-session swap commit. No EXERCISE_CHANGED in gym.

## LANGUAGE_DETECTED / BARGE_IN / TURN_ENDED / AUDIO_FOCUS_CHANGED

- Voice/conversational. Do not mutate sets.

## MEMORY_UPDATED

- preferenceKey, value, userVisible=true. Never silent high-impact.

## SYNC_ACKNOWLEDGED

- Reserved. **No sync server in MVP.** Do not implement a client against this in PR 0–21.

## Events the LLM may never emit as committed truth

WORKOUT_COMPLETED without user/engine · SET_COMPLETED with invented skipping setNumber · EXERCISE_SKIPPED / PLANNED_SETS_CHANGED without validated COMMAND · EXERCISE_DEFERRED (engine after accept only) · historical last-week numbers as events · SESSION_* · SET_STARTED · REST_* · SAFETY_CONCERN (safety layer owns it).
