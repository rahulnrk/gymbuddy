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

If the user speaks first, they enter through a wake word, an earbud tap, or the on-screen mic. All three produce the same user-initiated window. The wake word will fail in a loud gym. The earbud tap is the most reliable and the cheapest. `voice-workout-os-source/` already has an addressee gate (speech aimed at the coach, no wake word). That is the better product answer and must be benched against a wake word before a vendor is picked.

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

**Coach clock.** `proactiveUtterance` returns at most a rest-complete prompt, the single re-sync, or null. Null is the common case. Same discipline as `ShouldSpeak`. Rest-complete fires while `RESTING` and elapsed rest has reached the entry's published minimum. Safety speech during `SET_ACTIVE` stays `ShouldSpeak`; this clock returns null there.

**Predictive rest.** Within-session only. `adjustForToday` handles between-session readiness. On a declining set, extend rest by 30 s. Never shorten below the goal's published minimum. Every adjustment carries a spoken reason with units.

### Voice behavior

- `STATEMENT` → silent window (none). Music stays up.
- Closed/short/open questions open the matching window. Music ducks for that window only.
- `SET_ACTIVE`: no proactive clock speech except the existing safety ≥ high path in contract 09.
- At the horizon: one re-sync, then silence.
- Four minutes of silence after an anchor: nothing. That is correct.
- Rest adjustment is spoken in coach language ("Take another thirty seconds — your last set dropped to six reps"), never field names, never a bare number without units.

### Structured event

None new. Windows, clock, and rest policy do not append events. `AUDIO_FOCUS_CHANGED` remains contract 27. `COACHING_RESPONSE` / silence rows remain contract 09. `BARGE_IN` remains the voice transport.

### Database change

None. Policy only.

### UI state

None. No `:app` change. Three entry points are named so a later HUD can bind them; this PR does not draw them.

### Error / recovery behavior

Window times out → `None`, music restores. Cancel (barge-in / stop) → `None`, music restores. Barge-in cancel stays inside the existing 200 ms budget; the window layer must not add latency. Speech after close is a new user-initiated window, not an extension of a dead one. Horizon passed and re-sync unanswered → wait. Empty plan → no rest-complete.

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
