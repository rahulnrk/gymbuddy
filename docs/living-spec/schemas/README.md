# Frozen schemas (v1)

JSON Schema draft 2020-12. `schemaVersion` is integer **1**.

| File | Role |
|---|---|
| `v1/candidate-event.schema.json` | LLM extract output only |
| `v1/workout-event.schema.json` | Committed event log (engine) |
| `v1/workout-context.schema.json` | WorkoutStateContext |
| `v1/historical-context.schema.json` | HistoricalStateContext |
| `v1/conversational-context.schema.json` | ConversationalStateContext |
| `v1/user-context.schema.json` | UserStateContext |
| `v1/epistemic-fact.schema.json` | FactRef / EpistemicFact |
| `v1/set-completed.schema.json` | SET_COMPLETED committed |
| `v1/set-corrected.schema.json` | SET_CORRECTED |
| `v1/exercise-started.schema.json` | EXERCISE_STARTED |
| `v1/workout-started.schema.json` | WORKOUT_STARTED |
| `v1/workout-paused.schema.json` | WORKOUT_PAUSED |
| `v1/workout-resumed.schema.json` | WORKOUT_RESUMED |
| `v1/workout-completed.schema.json` | WORKOUT_COMPLETED |
| `v1/user-question.schema.json` | USER_QUESTION |
| `v1/user-feedback.schema.json` | USER_FEEDBACK |
| `v1/user-note.schema.json` | USER_NOTE_CREATED |
| `v1/safety-concern.schema.json` | SAFETY_CONCERN |
| `v1/exercise-deferred.schema.json` | EXERCISE_DEFERRED (additive, schemaVersion stays 1) |

Aliases `candidate-event.schema.json` and `committed-event.schema.json` at this directory root copy v1.

Changing these files without goldens + Kotlin types + CATALOG.md in the same change is forbidden.
