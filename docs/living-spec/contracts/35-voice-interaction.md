---
id: 35-voice-interaction
status: specified
schemaVersion: 1
source: docs/living-spec/SESSION-DESIGN.md
---

## Feature: Voice interaction — the coach runs on a clock and opens its own windows

**Status:** specified  
**Core-loop role:** Kernel companion. The coach opens listening windows. The user cannot open one by speaking. The phone is in a pocket or locker, so the only usable mic is in the earbuds; on ordinary Bluetooth an open earbud mic drops the music to call quality. The mic therefore cannot stay open across a workout. The coach decides when it opens. Facts arrive only when the user speaks in a window.

This is policy. No vendor SDK in this contract. `VoiceTurnMachine` stays the transport. `VoiceTurnState` stays orthogonal to `WorkoutSessionState`. Design of record: `docs/living-spec/SESSION-DESIGN.md` (agreed 2026-09-03).

### User behavior

Phone in a pocket or locker, music in the earbuds. They lift. Nothing is listening. When the clock decides a set has probably ended, music ducks (it never pauses), the coach starts speaking, and **the mic opens at the same moment** so they can cut in mid-sentence. They say whatever they say about the training — not a wake word, not a command. The coach logs what was said, answers what was asked, adjusts the plan if needed.

The ducked period and the listening period are the same interval. It is elastic: it extends while either party is talking. The exchange ends when the user stops **or** the coach finishes answering, whichever is later. Then a quiet hold. Quiet means the user stopped speaking, not that the room went silent. A gym is never silent. Music returns to full. Mic closes. Nothing listens until the next check-in.

The user cannot start a conversation by speaking. They never say "hey coach." They never tap an earbud to talk to the coach — that button is music (play, pause, skip) while A2DP is playing.

If something hurts, they hit one on-screen emergency control. That opens a window immediately, even mid-set, and goes into pain triage (contract 37). That is the only user-initiated entry in v1.

Android ducks; the app cannot set the duck level. The music app decides how far the volume falls.

### System state

`com.gymbuddy.domain.voice` policy layer over `VoiceTurnMachine`, not a replacement.

**Opening.** The clock aims at the gap after a set, never mid-set. A window that lands mid-set is the failure this design exists to avoid. **Every coach utterance opens a window**, including `STATEMENT` reminders. The old rule "a STATEMENT opens no window" is wrong under this design: the coach is the only party who can open a window, so a reminder with no window leaves the user mute. `WINDOW_STATEMENT_MS` is the shortest budget (UNDECIDED).

**Reply budget.** `maxMs` is time to begin speaking **after the coach stops**, not from window open. The mic still opens when the coach *starts* (barge-in). `onCoachFinished` starts the clock. Cached variants of different lengths must not change how long the user has to answer.

**Holding.** The window extends while either party is still talking. There is **no hard cap** that cuts a live conversation. The 60 s cap that fired in the 2026-09-02 floor test was a defect, not a policy. `WINDOW_HARD_CAP_MS` remains in the tree from the old model; it is not this contract's rule.

**Closing.** A quiet hold after the exchange ends. Quiet = the user stopped speaking.

**Numbers.** Check-in interval, its min and max, and the quiet hold are unset on purpose. See `DECISIONS_AND_THRESHOLDS.md` rows sourced `UNDECIDED — SEE SESSION-DESIGN.md`. Do not treat a floor-test millisecond as fact.

**Mic and duck are the same interval.** Music ducks when the window opens. The mic opens when the coach starts speaking. Both last until the quiet hold after the exchange. If the conversation runs long, the duck runs long with it. Never pause.

**How the coach speaks.**

- Coach-initiated lines (check-ins, rest calls, reminders, transitions, encouragement) are **pre-recorded**, many variants per job, chosen at random, **never repeated within one session**. The user is not responding to these, so they do not need to be generated live.
- Replies to the user **always come from the model**. Answering a person with a stock line is an IVR. "That felt heavy" always gets a real answer.
- Lines carrying the user's own numbers are **generated fresh** and never stitched from recorded fragments. "Eight at sixty — that's up" lands; "set logged" does not.

**Emergency.** On-screen control. Opens a window immediately, out of schedule, mid-set included. Routes to contract 37. The only user-initiated entry.

**Wake word, earbud tap, generic on-screen talk — dormant.** `WakeWordProvider`, `ListenDecision`, `EntryPoint.WAKE_WORD` / `EARBUD` / `ON_SCREEN`, and the listen-gate functions stay in the tree. They are not the v1 path. Reason: the user's phone is in a pocket or locker, so the only usable mic is in their earbuds, and on ordinary Bluetooth an open earbud mic drops the music to call quality. They return if LE Audio earbuds become common. Delete nothing.

**Listen gate.** Still in the tree. Dormant for v1. A coach-opened window or emergency is what opens cloud STT in the new model. Production constructors still take ports, not fakes.

**Topic scope.** In scope: training, technique, programming, nutrition, calories, protein, muscles and anatomy, injuries, recovery, equipment. Everything else gets one polite line:

> I'm your gym coach — I can help with training, food and injuries.

Classification needs the model. Do not keyword-match — it will misfire on "my shoulder is killing me."

**Silence / horizon.** The coach may speak a scheduled line only when workout state permits (contract 09: never during `SET_ACTIVE` except safety ≥ high). Talking under a loaded bar is a safety failure. A missed check-in is a minor annoyance.

**Coach clock / rest-complete / predictive rest.** Existing `proactiveUtterance` and `restAdjustment` remain in the tree. They are not the session loop in §2 of the design document. Do not treat them as the scheduler.

### Voice behavior

- The coach opens the window. The user cannot open one by speaking.
- Mic opens the moment the coach starts speaking. The user can cut in.
- Duck and listen are the same elastic interval. Music ducks, never pauses. The app does not set the duck level.
- No hard cap on a conversation in progress.
- Coach-initiated: recorded variants, no repeat in session. Replies: model. User numbers: generated fresh, never stitched.
- Emergency: mid-set allowed. Pain triage. Only user-initiated entry.
- Out of scope: the line above, once.
- `SET_ACTIVE`: no scheduled coach speech except safety ≥ high (contract 09 / 11).

### Structured event

None new. `AUDIO_FOCUS_CHANGED` remains contract 27. `COACHING_RESPONSE` / silence rows remain contract 09. `BARGE_IN` remains the voice transport. Pain accept remains contract 37.

### Database change

None in this contract update.

### UI state

None in this contract update. The emergency control is named so a later HUD can bind it. Wake-word and earbud are not drawn.

**First Sound** (`:app` `FirstSoundScreen`) is an experiment harness. It does not implement this window policy. It arms Scribe for the whole screen (always-on listening), which costs roughly nineteen times the windowed design. When the real session runner exists, that screen does not become it.

### Error / recovery behavior

Exchange ends → quiet hold → music restores, mic closes. Cancel → restore. Barge-in cancel stays inside the existing 200 ms budget. Speech after close is not an extension of a dead window; the next listen is the next scheduled check-in or emergency. Predicted lifting → no scheduled window. Emergency ignores the clock. Dormant wake-word hits are not the product path. If Android denies duck, speak anyway (contract 27); never pause the music app as a fallback.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens. Cached coach-initiated lines may be asserted word for word. Model-generated replies must not be; assert what was **logged** and **decided**. Inventory of existing exact-line tests: `docs/research/spoken-line-test-inventory-2026-09-03.md`.

1. The user cannot open a window by speaking. Scheduled windows are coach-initiated. A `STATEMENT` opens the shortest window, not none. Two coach lines of different lengths leave the same reply budget after `onCoachFinished`. Barge-in during the coach's line still works.
2. Duck interval equals listen interval. It extends while either party is talking.
3. Mic is defined as open from the moment the coach starts speaking.
4. The window closes only after a quiet hold past the later of (user stopped, coach finished answering).
5. Quiet is user-speech ending, not gym silence.
6. No hard cap cuts a live conversation.
7. Music ducks, never pauses.
8. Emergency opens during predicted lifting and routes to pain triage.
9. Wake-word / earbud types remain; the architecture test still forbids fakes in production main.
10. Out-of-scope copy is exactly the line in this contract.
11. `VoiceTurnState` and `WorkoutSessionState` remain independent.
12. Barge-in cancel stays inside the existing 200 ms budget.
13. Four minutes of `SET_ACTIVE`: no scheduled window. Emergency still works.
