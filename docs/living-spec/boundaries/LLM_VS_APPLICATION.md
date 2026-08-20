# Deterministic boundaries

The LLM interprets language and speaks. The application owns truth.

Conversation never writes `workout_sets`. Path:

```
speech → CandidateEvent → validator → append-only WorkoutEvent log → projector
```

If a later PR puts an `INSERT` in an LLM or conversation package, that PR is invalid.

## Responsibility table

| Concern | LLM responsibility | Application responsibility |
|---|---|---|
| Speech interpretation | Transcribe is STT. LLM maps unconstrained speech (including code-mix) into `CandidateEvent[]` using the frozen schema. | Owns STT port, VAD, LID, barge-in, echo. Drops unknown `type`. `"ten"` as reps is a schema failure, not a guess. |
| Intent detection | May label remaining intents after rules. | Safety classifier first on raw text. Rules overrides win (correction lexicon, question syntax, command phrases). Multi-label. |
| Event extraction | Emit `CandidateEventEnvelope` only. Temperature ≤ 0.2. Must use frozen JSON schema. | Schema gate + `CandidateValidator` → `ValidatedFact`. Engine (PR 2) assigns official `setNumber` and `SET_COMPLETED.setId`. |
| Conversational response | Phrase an allowed speech act. Paraphrase only using `citedFacts`. | `shouldSpeak` decides speak vs silence. Persist `COACHING_RESPONSE` always, including `silence: true`. Ban IVR strings. |
| Historical lookup | Must not recall numbers from model memory. May ask a question. | `FourContextAssembler.assembleFourContexts` reads snapshot + stored rows. Empty history → stance `none`. Invented last-week numbers are a failed test. |
| Set numbering | `setNumberHint` only (`first` / `this` / integer hint). | Engine assigns official `setNumber`. Catch-up ordinals resolved by projector laws. |
| Set persistence | Never. | `EventLog.append` after validation. Idempotent on `(userId, eventId)`. |
| Correction | Propose `SET_CORRECTED` / `SET_DELETED` candidates. | Same `setId`. No second conflicting row. Persist `previous` snapshot. |
| Workout state | Cannot set `WorkoutSessionState`. Cannot declare workout complete. | Transition table in `:domain`. `SET_ACTIVE` only via engine `SET_STARTED`. |
| Safety | Cannot override severity ≥ high. Cannot diagnose. | `DefaultSafetyClassifier` on raw transcript **before** extract (`SafetyFirstTurn`). Decision table in `safety/`. |
| Authorization | None. | User can only read/write own rows. Local UUID until auth binds (PR 12). |
| Session lifecycle | Cannot emit `SESSION_INITIALIZED`, `WORKOUT_STARTED`, `SESSION_REHYDRATED`, `SET_STARTED`. | `IDLE → SESSION_INITIALIZED → WORKOUT_STARTING → WORKOUT_STARTED → READY_FOR_EXERCISE`. Crash = `SESSION_REHYDRATED`. |
| Program mutation | May talk `recommend()` suggestions. | MVP: no write to next workout `prescribed_weight_kg`. `WEIGHT_CHANGED` is this session only. |
| Substitution | Propose `SUBSTITUTION_PROPOSE`. | Commit `SUBSTITUTION_APPLIED` only. Never `EXERCISE_CHANGED` in Gym Mode. |
| Skip / extra set | Propose `COMMAND` skip / extra_set. | Commit `EXERCISE_SKIPPED` / `PLANNED_SETS_CHANGED` so `fold(event_log)` restores them. |

## Forbidden LLM outputs treated as truth

- `WORKOUT_COMPLETED` without user/engine.
- `SET_COMPLETED` with invented `setNumber` that skips slots.
- `EXERCISE_SKIPPED` / `PLANNED_SETS_CHANGED` without a validated COMMAND.
- Any historical “last week you did X” as an event.
- Direct SQL / Room / table writes.
- Arbitrary JSON that is not `{ schemaVersion: 1, candidates: CandidateEvent[] }` for extraction.
