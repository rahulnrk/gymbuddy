---
id: 35-voice-interaction
status: implemented
schemaVersion: 1
source: docs/prompts/pr-voice-interaction-contract.md
---

## Feature: Voice interaction — the conversation window and the coach's clock

**Status:** implemented  
**Core-loop role:** Kernel companion. Music ducks only while a conversation window is open. The coach opens most windows after speaking. The user needs a wake word, earbud tap, or on-screen mic only when they interrupt unprompted. Past the confidence horizon the coach is silent. Silence is the common, correct outcome.

This PR is policy and state. No vendor SDK, no audio code, no `:app`. `VoiceTurnMachine` stays the transport. `VoiceTurnState` stays orthogonal to `WorkoutSessionState`.

### User behavior

Phone in a pocket, music playing. After a set, a rest, or a check-in, the coach has just said something. If that utterance invites a reply, a window opens and music ducks. The user talks naturally — *"that was fifteen at eighty"* — with no wake word. The window closes, music returns.

A statement that invites no reply ("Nice.") opens no window. Music does not duck for eight empty seconds after a closed answer.

If the user speaks first, they enter through a wake word, an earbud tap, or the on-screen mic. All three produce the same user-initiated window.

**The wake word is the least reliable of the three.** It will fail in a loud gym. The system must stay usable without it.

While music is playing — the normal case — an earbud tap is a music control (play, pause, skip), not a coach trigger. Do not send the user there as a fallback. The on-screen mic is the dedicated control that does not fight the music app. When the wake word fails under music, the fallback is the mic on the screen.

Earbud and on-screen still open the same user-initiated window if they arrive. The earbud path is not the recovery path for music. `voice-workout-os-source/` already has an addressee gate (speech aimed at the coach, no wake word). That is the better product answer and must be benched against a wake word before a vendor is picked. The adapter is blocked on measurement. No `:app` change in this PR.

If they go quiet for minutes — rest, mid-set, queue, toilet — the coach does not guess. One re-sync near the horizon ("Ready when you are?"). If unanswered, silence until they speak.

### System state

`com.gymbuddy.domain.voice` policy layer over `VoiceTurnMachine`, not a replacement.

**Window.** Adaptive length, not a fixed 40 seconds.

| What the coach just said | Window |
|---|---|
| Statement inviting no reply ("Nice.") | None |
| Closed answer ("Yes, stay at eighty.") | 8 s (`WINDOW_CLOSED_ANSWER_MS`) |
| Direct question, short answer ("How many?") | 10 s (`WINDOW_SHORT_QUESTION_MS`) |
| Open question ("How did that feel?") | 40 s (`WINDOW_OPEN_QUESTION_MS`) |
| User interrupted (wake word / earbud / on-screen) | 40 s (`WINDOW_USER_INITIATED_MS`) |

If speech is detected before close, the window extends while they talk, hard-capped at 60 s (`WINDOW_HARD_CAP_MS`). A user saying *"actually…"* must not be cut off.

`shouldDuck(window)` is true exactly while the window is not `None`. Un-duck on timeout, close, and cancellation. Contract 27 still owns audio focus; this contract only says how long the duck lasts.

**Silence rule.** The coach may speak proactively only when:

1. workout state permits speech (contract 09: never during `SET_ACTIVE` except safety ≥ high), **and**
2. time since the last anchoring event is within the confidence horizon (`CONFIDENCE_HORIZON_MS` = 300 s, equal to the longest published rest).

Past the horizon the coach is silent and waits. It does not guess. A missed prompt is a minor annoyance. Talking under a loaded bar is a safety failure and a product people uninstall.

**Re-sync.** The only utterance allowed at the horizon: *"Ready when you are?"* Once. Answered → clock re-anchors, proactive behaviour resumes. Unanswered → silent until the user speaks. Must not ask twice.

Every user utterance re-anchors the clock. So does any logged event.

**Coach clock.** `proactiveUtterance` returns at most a rest-complete prompt, the single re-sync, or null. Null is the common case. Same discipline as `ShouldSpeak`. Rest-complete fires **once** while `RESTING` and elapsed rest has reached the entry's published minimum. Unanswered, it does not ask again. Safety speech during `SET_ACTIVE` stays `ShouldSpeak`; this clock returns null there.

**Predictive rest.** Within-session only. `adjustForToday` handles between-session readiness. On a declining set, extend rest by 30 s. Never shorten below the goal's published minimum. Every adjustment carries a spoken reason with units.

**Listen gate.** VAD (is anyone speaking?) gates the wake-word model, which gates the cloud. Each stage exists to stop the next one running. Never run wake word detection on silence. Never open a cloud stream without a wake word or a coach-opened window (or an earbud / on-screen entry, which is the same user-initiated window).

`VadObservation` models two echoes. `echoFromTts` is brief and known — we are speaking. `echoFromMusic` is louder, constant, and the harder problem. `usable` is a boolean the adapter will set after measurement; `null` is unknown. Unknown-with-music is unusable. The policy then says the signal is unusable and falls back to the on-screen mic rather than pretending a wake word fired, and rather than an earbud tap (that tap is music controls). There is no SNR or wake-word confidence number in domain.

**Recognition session owner.** The listen gate owns the STT session. `applyListenDecision` starts and aborts it. `VoiceTurnMachine` transports what the gate opened: `start()` arms VAD only; `onSpeechStart` and `bargeIn` do not call `startRecognition`. `stop()` aborts if recognizing, because the voice turn is over. Always-on `LISTENING` is VAD, not cloud. Do not leave a second owner in the tree.

`VoiceTurnMachine` and `CompanionLoop` take ports (`Clock`, `VadProvider`, `SttProvider`, `TtsProvider`, `LidProvider`, `AudioFocusController`), not fakes. AEC measurement still blocks wiring barge-in-under-TTS through this gate.

Barge-in outside a coach-opened window is not reachable through this gate yet. `echoFromTts` returns `VadOnly`, which stops wake word and cloud. Echo detected is not "no user underneath." Separating the two needs AEC, the same measurement block as the wake word. Wiring `VoiceTurnMachine` to this gate must not silently drop barge-in on a rest countdown or a mid-set cue.

`applyListenDecision` starts cloud STT at most once per open window. `SttProvider.recognizing` is true from `startRecognition` until `abort`. A final does not end the session. One window is one recognition session; finals do not close it; the window closing does. Unguarded `startRecognition` or `abort` on every VAD observation would open or tear down a connection per frame.

### Voice behavior

- `STATEMENT` → silent window (none). Music stays up.
- Closed/short/open questions open the matching window. Music ducks for that window only.
- `SET_ACTIVE`: no proactive clock speech except the existing safety ≥ high path in contract 09.
- At the horizon: one re-sync, then silence.
- Four minutes of `SET_ACTIVE`: nothing. Four minutes after rest-complete went unanswered: nothing. The coach does not guess whether they are still resting, under the bar, queuing, or gone.
- Rest adjustment is spoken in coach language ("Take another thirty seconds — your last set dropped to six reps"), never field names, never a bare number without units.
- Unusable music: "The gym is too loud for a wake word. Use the mic on the screen." Session continues. Never "tap the earbud" — that button is music. No condition names, no field names.

### Structured event

None new. Windows, clock, and rest policy do not append events. `AUDIO_FOCUS_CHANGED` remains contract 27. `COACHING_RESPONSE` / silence rows remain contract 09. `BARGE_IN` remains the voice transport.

### Database change

None. Policy only.

### UI state

None. No `:app` change. Three entry points are named so a later HUD can bind them; this PR does not draw them. The wake-word adapter is blocked on measurement.

### Error / recovery behavior

Window times out → `None`, music restores. Cancel (barge-in / stop) → `None`, music restores. Barge-in cancel stays inside the existing 200 ms budget; the window layer must not add latency. Speech after close is a new user-initiated window, not an extension of a dead one. Horizon passed and re-sync unanswered → wait. Empty plan → no rest-complete. Music echo with unknown usability → `FallBackToOnScreen`, wake word off, cloud off. A pretended wake-word hit on that signal is discarded. Do not fall back to earbud tap: while music plays, that button belongs to the music app. Wake word unavailable → on-screen still works.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens.

1. A `STATEMENT` opens no window at all.
2. `CLOSED_ANSWER` opens a short window; `OPEN_QUESTION` a long one.
3. Speech before close extends the window; extension respects the hard cap.
4. Ducking is true exactly while a window is open — un-duck on timeout, on close, and on cancellation.
5. `maySpeakProactively` is false during `SET_ACTIVE` for anything below safety-high.
6. Past the confidence horizon, `proactiveUtterance` returns the re-sync **once** and null thereafter.
7. Any user utterance re-anchors the clock and restores proactive behaviour.
8. All three entry points produce an equivalent `UserInitiated` window.
9. Barge-in still cancels within the existing 200 ms budget — the window layer does not add latency.
10. Predictive rest extends on a declining set and never shortens below the goal minimum.
11. Every proactive utterance and rest adjustment carries a plain-language reason (no field names, no bare numbers without units).
12. `VoiceTurnState` and `WorkoutSessionState` remain independent.
13. Four minutes of `SET_ACTIVE` yields null at every tick, including with rest elapsed.
14. Unanswered rest-complete stays null until the horizon; it does not ask again.
15. Silence never runs the wake-word model and never opens the cloud.
16. A coach-opened window opens the cloud with no wake-word observation.
17. Earbud and on-screen open the cloud with no wake-word observation — the system stays usable without a wake word.
18. Music echo with unknown `usable` falls back to the on-screen mic, not the earbud; an injected wake-word hit on that signal does not open the cloud. The spoken line does not say "earbud".
19. `echoFromTts` closes the gate until AEC is measured. It does not claim no user is speaking underneath.
20. `OpenCloud` calls `startRecognition` once until `abort`; a further `OpenCloud` after abort starts it again.
21. `VadOnly` calls `abort` once if recognition was running, and not at all if it never started.
22. A final inside an open window does not start a second recognition session. The window closing does.
23. Same-reason duck is held; a second speech onset in one window does not restore-and-request.
24. Production `:domain` main sources outside `voice/fake/` do not import fakes.
