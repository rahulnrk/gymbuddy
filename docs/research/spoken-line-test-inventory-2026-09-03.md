# Spoken-line test inventory — 2026-09-03

Contract 35 / SESSION-DESIGN.md §5 and §11: once the model writes replies, a test cannot assert the sentence. Assert what was **logged** and **decided**. Cached coach-initiated lines can still be asserted word for word.

**No tests were changed in this PR.** This is the list.

Buckets:

- **(a)** asserts a model-generated reply (or a line that will be, including user-number lines generated fresh) — must change
- **(b)** asserts a cached / frozen coach-initiated line — can stay
- **(c)** unclear

User transcripts, slugs, event types, and "must not contain" bans are out of this inventory unless they pin a coach sentence.

---

## (a) Must change — 10

| File | Test |
|---|---|
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/CompanionLoopTest.kt` | `plusOneRepSpeaks` — exact `"Nice. One more rep than your last set."` (user numbers; design: generated fresh, not a recording) |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/CompanionLoopTest.kt` | `questionNoMutation` — spokenText contains `"don't have a previous number"` or `"Last time"` (answer to a user question → model) |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `plusOneRep` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `plusTwoRepsSaysTwoAndPluralises` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `minusOneRepDoesNotClaimAnIncrease` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `minusTwoReps` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `weightOnlyChange` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `weightDropIsNotCalledAnIncrease` (contains `"down 2.5 kilos"`) |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `weightAndRepChangeTogether` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/DeltaPhraseTest.kt` | `crossSessionBaselineSaysLastTime` |

`DeltaPhraseTest.equalRepsAndWeightSaysNothing` / `subThresholdWeightChangeIsIgnored` / `missingBaselineProducesNoSentence` assert **null**, not a sentence — they stay as decision tests, not in (a).

---

## (b) Can stay — 12

Cached or frozen policy copy. Not a reply to a free-form user turn.

| File | Test |
|---|---|
| `domain/src/test/kotlin/com/gymbuddy/domain/voice/VoiceInteractionTest.kt` | `pastHorizonResyncOnceThenNull` — `"Ready when you are?"` |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/CompanionLoopTest.kt` | `safetyCriticalCommitsSetAndSafetySpeech` — frozen `CRITICAL_TEXT` |
| `domain/src/test/kotlin/com/gymbuddy/domain/safety/SafetyFirstTurnTest.kt` | `llmContinueCannotOverrideCritical` — `CRITICAL_TEXT` |
| `domain/src/test/kotlin/com/gymbuddy/domain/safety/SafetyFirstTurnTest.kt` | `highSuppressesCoaching` — `HIGH_TEXT` |
| `domain/src/test/kotlin/com/gymbuddy/domain/adapt/AdaptTest.kt` | `nullWaitAsksHowLongAndNeverSubstitutes` — `AdaptCopy.ASK_HOW_LONG` |
| `domain/src/test/kotlin/com/gymbuddy/domain/adapt/AdaptTest.kt` | `waitShorterThanRestIsWait` — `AdaptCopy.WAIT` |
| `domain/src/test/kotlin/com/gymbuddy/domain/adapt/AdaptTest.kt` | copy-lock of `"How long do they look?"` / `"That's less than your rest anyway. We'll wait."` |
| `domain/src/test/kotlin/com/gymbuddy/domain/pain/PainTriageTest.kt` | `triageIsQueuedDuringSetActiveAndDrainedOnRest` — `PainCopy.question(SPOT)` |
| `domain/src/test/kotlin/com/gymbuddy/domain/pain/PainTriageTest.kt` | `painRecordAppearsInTheNextSessionFollowUp` — `"How's that shoulder today?"` / `"How's that left knee today?"` |
| `domain/src/test/kotlin/com/gymbuddy/domain/pain/PainTriageTest.kt` | `everySpokenLineIsPlainLanguage` — walks `PainCopy.*` (cached questions/outcomes) |
| `domain/src/test/kotlin/com/gymbuddy/domain/voice/SessionClockTest.kt` | `outOfScopeUsesTheExactLine` — `TopicCopy.OUT_OF_SCOPE` |
| `domain/src/test/kotlin/com/gymbuddy/domain/intake/IntakeTest.kt` | recency / clearance copy locks (`ReferralCopy.*`) |

---

## (c) Unclear — 6

| File | Test | Why |
|---|---|---|
| `domain/src/test/kotlin/com/gymbuddy/domain/safety/SafetyFirstTurnTest.kt` | `nonSafetyAllowsLlmCoaching` | Asserts the **LLM suggestion** `"Want to drop a little?"` passed through. That *is* a model line, but the test is about pass-through, not a canned coach job. |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/CompanionLoopTest.kt` | `persistenceFailureDoesNotReportSetLogged` | Asserts spokenText does **not** contain `"Set logged"`. Constraint on future model output, not an exact line. |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/Pr9GoldenRunnerTest.kt` | several goldens | Ban-lists on `spokenText` (`last Thursday you used`, field names). Decision/logging more than a sentence lock. |
| `domain/src/test/kotlin/com/gymbuddy/domain/voice/VoiceInteractionTest.kt` | `proactiveAndRestReasonsArePlainLanguage` | Asserts rest-complete / re-sync **text** is plain language, including `"That's your rest. Ready for the next set?"` via `assertPlainReason`. Could stay as (b) if those stay cached. |
| `domain/src/test/kotlin/com/gymbuddy/domain/adapt/AdaptTest.kt` | `copyHasNoBannedShapes` (and similar speakable walks) | Locks many `AdaptCopy` strings at once. Cached if adaptation stays recorded; model if those become replies. |
| `domain/src/test/kotlin/com/gymbuddy/domain/loop/CompanionLoopTest.kt` | `questionDuringLiftDeferred` | Asserts act id after drain, not the sentence. Probably already in the "logged/decided" world. Listed because it is a user-question reply path. |

---

## Counts

| Bucket | Count |
|---|---|
| (a) must change | **10** |
| (b) can stay | **12** |
| (c) unclear | **6** |

Bucket (a) is one CompanionLoop turn test plus the DeltaPhrase suite. Manageable: keep asserting `SpeakDecision.Speak` + `SpeechActId.USEFUL_DELTA` + claims, drop the sentence.

Not counted: user-utterance fixtures (`"Got 10."`, `"How much protein should I have?"`), slugs, transcripts on `SttChunk`.
