# Concrete test cases

Each row is a file under `tests/golden/`.

PR 1 goldens are **executable**: `input` + `given` + `expect`. Official set numbers and rest belong on PR 2 goldens only. See `boundaries/PR1_VS_PR2.md`.

## PR 1 validator (executable)

| ID | Given | Expect |
|---|---|---|
| `04-nl-logging.event.got-10-inherit-weight` | RESTING, working 60 kg, headset, `{reps:10}` | `SetCompleted` 10 @ 60 inherit. No `setNumber`. |
| `04-nl-logging.event.did-10-no-working-weight-clarify` | currentWeightKg=null | clarify `no_working_weight` |
| `04-nl-logging.event.40-for-10` | spoken 40 and 10 | `SetCompleted` 10 @ 40, inheritWeight=false |
| `04-nl-logging.event.multi-set-40x12-10-8` | empty completedSets, three catch-up candidates | three facts, reps 12/10/8, weights 40 inherit, **hints only**. Same utterance as PR 2 catch-up golden. |
| `04-nl-logging.event.telugu-60x8` | Bench Press | 60 kg × 8, storage English |
| `04-nl-logging.event.drop-weight-not-a-set` | working 60 | `WeightChanged` 55, not SetCompleted |
| `05-extract.event.unknown-type-dropped` | HALLUCINATED_PR | schema invalid / drop |
| `05-extract.event.last-week-as-set-completed-rejected` | empty history, question | `UserQuestion`, not a set |
| `03-voice.event.bystander-got-10-no-commit` | speakerphone + SET_COMPLETED candidate | no auto-commit |
| `03-voice.event.headset-got-10-commits` | headset, 1 talker, 60 kg | inherit commit |
| `07-correction.event.no-8-one-row` | last set SET1 60×10 | `SetCorrected` SET1 reps=8, previous 10, newSetRow=false |
| `07-correction.event.that-was-second-set` | SET1 and SET2 exist; target `second` | resolves SET2 `setId`; previous 10; not official setNumber assignment |
| `07-correction.event.actually-9-after-no-8` | SET1 already 8 | `SetCorrected` SET1 reps=9, previous 8 |
| `07-correction.event.previous-exercise-within-120s` | previous last set 30s ago | correct that `setId` |
| `07-correction.event.previous-exercise-ask-when-ambiguous` | previous last set 180s ago, topic incline | clarify; do not guess |
| `07-correction.multilingual.kaadu-8` | Telugu “Kaadu, 8.” | same as No, 8. `llm_required` |
| `07-correction.event.scratch-that-deleted` | last SET1 | `SetDeleted` SET1; previous snapshot; not a replacement SetCompleted |
| `07-correction.event.occupancy-second-set-later-exists` | Set1 60×10, Set2 60×8, Set3 60×7 | Clarify replace-set-2-or-keep-both. No silent overwrite. Later sets unchanged. |
| `07-correction.multilingual.kaadu-8` | Telugu `Kaadu, 8.` = “No, 8.” | Same as no-8-one-row |

## PR 0 schema

| ID | Expect |
|---|---|
| `05-extract.event.reps-as-string-ten-fails-schema` | `"ten"` fails schema |

## PR 2+ (not validator)

| ID | Layer | Expect |
|---|---|---|
| `06-state-machine.event.catch-up-40x12-10-8` | projector; same utterance as PR 1 multi-set | official 1,2,3; rest after last only |
| `06-state-machine.state.set-started-required` | state | SET_ACTIVE only via engine SET_STARTED |
| `06-state-machine.event.workout-started-from-idle-rejected` | state | illegal |
| `01-lifecycle.event.session-init-then-workout-started` | state | SESSION_INITIALIZED then WORKOUT_STARTED |
| `01-lifecycle.event.resume-is-not-workout-started` | state | SESSION_REHYDRATED |
| `17-plans.event.skip-this` | projector | EXERCISE_SKIPPED fold. PR 1 may only classify `Command(SKIP)`. |
| `17-plans.event.one-more-set-after-yes` | projector | PLANNED_SETS_CHANGED |
| `10-local-first.offline.three-sets-process-kill` | persist | fold(event_log) after process kill; not WORKOUT_STARTED |
| `08-context.event.empty-history-no-invented-weight` | assembler | stance none; no invented 60 kg |
| `08-context.event.known-thursday-60x10-citable` | assembler | known historical sets; current weight stays distinct |
| `08-context.event.estimate-requires-two-rows` | assembler | estimated mean; ≥2 sourceRowIds |
| `08-context.event.uncertain-must-check-db` | assembler | uncertain not known; speechAllowed false |
| `08-context.offline.assemble-from-local` | assembler | local rows only |
| `03-voice.voice.barge-in-lt-200ms` | voice | FakeClock barge-in < 200 ms; TTS cancelled |
| `03-voice.event.echo-does-not-log-ai-words` | voice | TTS echo does not barge-in |
| `03-voice.multilingual.lid-telugu-english` | voice | te + mixed; transcript unchanged |
| `27-music.event.duck-before-tts` | audio | duck → TTS → restore → release |
| `27-music.event.restore-after-barge-in` | audio | barge-in restores then user duck |
| `27-music.event.unsupported-still-logs` | audio | still speaks |
| `27-music.event.engine-has-no-os-audio-import` | architecture | engine has no AudioManager |
| `11-safety.event.chest-pain-critical` | safety | critical chest_pain; LLM cannot override |
| `11-safety.event.chest-fly-occupied-not-critical` | safety | not a medical concern |
| `11-safety.event.got-10-shoulder-weird-commit-set-and-note` | safety | medium; allow SET_COMPLETED |
| `11-safety.event.telugu-shoulder-medium-advise` | safety | medium, not chest_pain |
| `11-safety.event.why-shoulder-question-no-safety-concern` | safety | technique Q, no SAFETY_CONCERN |
| `11-safety.voice.no-diagnosis-no-push-through` | safety | critical template; no diagnosis |
| `04-nl-logging.event.got-10-inherit-weight` | extract+validate | reps 10; extractor does not copy working weight |
| `04-nl-logging.event.40-for-10` | extract | weight 40, reps 10 |
| `05-extract.event.last-week-as-set-completed-rejected` | extract | USER_QUESTION, not SET_COMPLETED |
| `09-coaching.*` / `19-*` / `28-*` | later PRs | as before |

## PR 9 headless companion loop

Headless `CompanionLoop` + `shouldSpeak()` + `recommend()`. Silence is an intentional outcome, not a failure. No UI. No vendor.

| ID | Expect |
|---|---|
| `02-loop.event.gold-scene-got-10-plus-one-must-speak` | Previous 9 then “Got 10.” → `useful_delta`, speech allowed |
| `02-loop.event.duplicate-got-10-same-hash-10s-one-row` | Same STT hash within 10s → one `SET_COMPLETED` |
| `09-coaching.voice.same-reps-must-not-speak` | Same weight/reps → `no_useful_delta` |
| `09-coaching.voice.plus-one-rep-must-speak` | +1 rep → `useful_delta` |
| `09-coaching.voice.set-active-must-not-speak` | `SET_ACTIVE` → silence (`set_active`); TTS forbidden except safety ≥ high |
| `09-coaching.voice.question-during-set-deferred` | Question while lifting queued; drained on rest |
| `09-coaching.event.silence-row-always-persisted` | `COACHING_RESPONSE.silence` always written |
| `09-coaching.event.empty-history-no-last-thursday` | Empty history → do not invent last Thursday’s weight |
| `09-coaching.event.recommend-talk-only-no-plan-write` | `recommend()` talk-only; no `prescribed_weight_kg` write |
| `09-coaching.voice.banned-ivr-strings` | Never “Command received.” / “Your set has been logged.” |

## PR 3 persistence (Room EventLog)

| Case | Expect |
|---|---|
| DAO insert / ordered load / session query / latest version | sequence ascending; other sessions excluded |
| Duplicate event id / UNIQUE(user_id, event_id) / UNIQUE(session_id, sequence) | one logical row; no overwrite |
| Typed codec | unknown type and malformed JSON fail; no silent coerce |
| InMemoryEventLog vs RoomEventLog | same types, count, projected snapshot |
| Process kill | `load` → `fold` restores workout/exercise/set/rest/pause/correction/delete/skip/extra-set/catch-up |
| Concurrent same event | one logical row |
| Concurrent different events | unique monotonic sequences |
| Occupancy clarify | empty facts write nothing; Room does not decide replace/keep-both |
| Architecture | no Room in `:domain`; no validator/engine/UI/vendors in `:persist` |
