# Data model (v1)

SQLite via Room in `:persist` (**PR 3**). PR 0 freezes names, sources, and units.

Canonical copy of columns: `TECHNICAL_SPEC.md` § Data Model.

## Laws

- Mass: `weight` + `weight_unit` (`kg`|`lb`) + generated `weight_kg`.
- Exercise identity: `exercise_id`, never free-text metrics.
- Completed work `source` is `voice` or `manual`. `ai-generated` is planned work only, never a claimed completed set.
- `event_log` is append-only. Corrections do not rewrite history; they append `SET_CORRECTED`.
- `users.id` is a local UUID before auth (PR 3 fixture). Never null `user_id`.
- No sync client. `sync_status` reserved unused.

## Tables

users · exercises · exercise_aliases · muscle_groups · workouts · workout_exercises · workout_sets · sessions · event_log · workout_notes · user_preferences · recovery_records · progress_metrics · conversation_turns · audio_blobs (default off) · outbox (reserved)

## workout_sets

id (setId) · workout_exercise_id · session_id · set_number (engine) · weight · weight_unit · weight_kg · reps · rpe · rir · source · occurred_at · deleted_at · last_correction_event_id · logging_mode · batch_id

Unique safety net: `(session_id, workout_exercise_id, set_number) WHERE deleted_at IS NULL`.

## event_log

event_id PK · user_id · session_id · event_type · payload_json · occurred_at · recorded_at · source · schema_version=1 · sequence (session-local monotonic version) · transcript_id · language · confidence · stt_final_hash · batch_id

Unique: `(user_id, event_id)`, `(session_id, sequence)`.

Load `ORDER BY sequence ASC`. Projector: `fold(event_log) = readModel`. Pure and idempotent.

Only committed `WorkoutEvent` is stored. `ValidatedFact` is never a Room source of truth.
