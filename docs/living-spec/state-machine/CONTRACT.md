# Workout and voice state-machine contract (PR 2)

Kotlin runtime: `WorkoutEngine` + `WorkoutProjector` (`com.gymbuddy.domain.engine`). `validateTurn` is PR 1 catch-up fact assembly only; PR 2 consumes `ValidatedFact`s and must not call it.

Kotlin table: `com.gymbuddy.domain.state.WorkoutTransitionContract`.

The LLM cannot set these states. Only validated `WorkoutEvent`s can.

`USER_SPEAKING` and `AI_SPEAKING` are **VoiceTurnState**, orthogonal to **WorkoutSessionState**.

## WorkoutSessionState

`IDLE` · `WORKOUT_STARTING` · `READY_FOR_EXERCISE` · `EXERCISE_ACTIVE` · `SET_ACTIVE` · `SET_COMPLETED` (transient) · `RESTING` · `PAUSED` · `EXERCISE_COMPLETE` · `WORKOUT_COMPLETE` · `ERROR_RECOVERY`

Product also lists `USER_SPEAKING` and `AI_SPEAKING`. Those are voice overlay states, not workout states.

## Session start (closed)

```
IDLE
  --SESSION_INITIALIZED { sessionId, workoutId }--> WORKOUT_STARTING
  --hydrate-->
  --WORKOUT_STARTED { workoutId, startedAt }--> READY_FOR_EXERCISE
  --hydrate fail--> ERROR_RECOVERY
```

| Event | Valid from | Next | Illegal |
|---|---|---|---|
| `SESSION_INITIALIZED` | `IDLE` only | `WORKOUT_STARTING` | any other state (duplicate Start is a no-op, same sessionId) |
| `WORKOUT_STARTED` | `WORKOUT_STARTING` only | `READY_FOR_EXERCISE` | **from IDLE** |
| `SESSION_REHYDRATED` | process launch with local non-terminal session | restore snapshot; if paused stay `PAUSED` | second `WORKOUT_STARTED` |
| `WORKOUT_RESUMED` | `PAUSED` only | `prePauseState`, restore `restRemainingSec` | from IDLE / RESTING |

## Valid workout transitions

| From | Event | To |
|---|---|---|
| IDLE | SESSION_INITIALIZED | WORKOUT_STARTING |
| WORKOUT_STARTING | WORKOUT_STARTED | READY_FOR_EXERCISE |
| READY_FOR_EXERCISE | EXERCISE_STARTED | EXERCISE_ACTIVE |
| READY_FOR_EXERCISE | WORKOUT_PAUSED | PAUSED |
| EXERCISE_ACTIVE | SET_STARTED | SET_ACTIVE |
| EXERCISE_ACTIVE | EXERCISE_SKIPPED | EXERCISE_COMPLETE |
| EXERCISE_ACTIVE | WORKOUT_PAUSED | PAUSED |
| SET_ACTIVE | SET_COMPLETED (live accepted) | SET_COMPLETED (transient) |
| SET_ACTIVE | EXERCISE_SKIPPED | EXERCISE_COMPLETE |
| SET_ACTIVE | WORKOUT_PAUSED | PAUSED |
| SET_COMPLETED | REST_STARTED | RESTING |
| SET_COMPLETED | (last planned set) | EXERCISE_COMPLETE |
| RESTING | REST_ENDED | RESTING (timer informational) |
| RESTING | SET_STARTED | SET_ACTIVE |
| RESTING | SET_COMPLETED live | SET_COMPLETED (`logged_through`, no SET_STARTED for that finished set) |
| EXERCISE_ACTIVE | SET_COMPLETED catch-up | SET_COMPLETED (no SET_STARTED per backfill) |
| SET_COMPLETED | SET_COMPLETED catch-up | SET_COMPLETED (batch continuation; no rest between) |
| RESTING | EXERCISE_SKIPPED | EXERCISE_COMPLETE |
| RESTING | WORKOUT_PAUSED | PAUSED |
| EXERCISE_COMPLETE | next EXERCISE_STARTED | READY_FOR_EXERCISE / EXERCISE_ACTIVE per engine |
| EXERCISE_COMPLETE | no exercises remain | WORKOUT_COMPLETE via WORKOUT_COMPLETED |
| PAUSED | WORKOUT_RESUMED | prePauseState |
| PAUSED | WORKOUT_COMPLETED | WORKOUT_COMPLETE |
| WORKOUT_COMPLETE | session closed | IDLE (engine-internal; not SESSION_INITIALIZED) |

Catch-up: N × `SET_COMPLETED` with one `batchId`. **No** `SET_STARTED` per backfilled set. **No** `REST_STARTED` between members. One `REST_STARTED` after the last iff planned sets remain.

## Invalid (must reject)

| From | Event | Why |
|---|---|---|
| IDLE | WORKOUT_STARTED | hydrate-in-progress state missing |
| IDLE | SET_COMPLETED | no session |
| READY_FOR_EXERCISE | WORKOUT_STARTED | already started |
| EXERCISE_ACTIVE | WORKOUT_STARTED | already started |
| SET_ACTIVE | SET_STARTED | already in set |
| WORKOUT_COMPLETE | SET_COMPLETED | session over |
| PAUSED | WORKOUT_STARTED | resume is WORKOUT_RESUMED |
| any | SET_STARTED from LLM | engine only |

## SET_ACTIVE entry (closed)

`SET_STARTED` is **required**. Producer = engine. LLM may not emit it.

| # | From | Trigger | Engine emits |
|---|---|---|---|
| 1 | EXERCISE_ACTIVE, 0 completed sets | greeting ack **or** 8s since EXERCISE_STARTED | SET_STARTED |
| 2 | RESTING | rest timer expiry | REST_ENDED reason=timer then SET_STARTED |
| 3 | RESTING | “I’m going.” | REST_ENDED reason=user then SET_STARTED |
| 4 | RESTING | live SET_COMPLETED | REST_ENDED reason=logged_through; **no** SET_STARTED for that finished set |
| 5 | catch-up batch | ordinal / N≥2 | N SET_COMPLETED; no SET_STARTED for backfills |
| 6 | PAUSED prePauseState=SET_ACTIVE | WORKOUT_RESUMED | restore SET_ACTIVE, no second SET_STARTED |

Questions/corrections/safety during the 8s window cancel the timer until the turn completes.

## VoiceTurnState

Orthogonal to WorkoutSessionState. SILENT is idle. Always-on VAD uses LISTENING.

```
SILENT → LISTENING (VAD start)
LISTENING → USER_SPEAKING (VAD speech_start)
SILENT → USER_SPEAKING (VAD speech_start)
SILENT | LISTENING → AI_SPEAKING (SpeakCommand)
USER_SPEAKING → PROCESSING (speech_end / STT final)
PROCESSING → SILENT | LISTENING | AI_SPEAKING | USER_SPEAKING
AI_SPEAKING → SILENT | LISTENING (TTS complete)
AI_SPEAKING → INTERRUPTED → USER_SPEAKING (barge-in cancels TTS, p95 < 200 ms on FakeClock)
* → STOPPED (voice session stop)
```

Echo while AI_SPEAKING does **not** barge-in. Overlap/talkerCount are transported, not decided here.

## Overlay law

`SET_ACTIVE` + `AI_SPEAKING` is **forbidden** except `SAFETY_CONCERN` severity ≥ high.

VAD stays on in SET_ACTIVE. TTS does not.

## Mic policy

Always-on VAD for the whole Gym Mode session. No push-to-talk in MVP.
