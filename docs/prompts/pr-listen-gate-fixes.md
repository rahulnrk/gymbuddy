# PR — listen gate: STT restart guard and the TTS-echo gap

Domain only. No `:app`, no vendor, no audio. This is a follow-up to the
wake-word gating PR, found by reading `applyListenDecision` as behaviour
rather than by a failing test — all tests were green and stayed green.

## Fix 1 — STT restarts on every observation (real defect)

In `domain/.../voice/Listen.kt`:

```kotlin
ListenDecision.RunWakeWord -> {
    if (!wake.started) wake.start()      // guarded
}
is ListenDecision.OpenCloud -> {
    stt.startRecognition()               // NOT guarded
}
```

VAD emits observations continuously. While a window is open every one of them
returns `OpenCloud`, so `startRecognition()` is called once per observation for
the whole window. `FakeStt.startRecognition` only sets a boolean, so no test
notices. A streaming adapter opens a connection per call — that is a connection
per VAD frame, which breaks the cost model in
`docs/research/voice-stack-costs-2026-09-01.md` and fragments the transcript.

**Root cause is a port asymmetry.** `VadProvider` and `WakeWordProvider` expose
`started`. `TtsProvider` exposes `speaking`. `SttProvider` exposes no state at
all, so the caller cannot guard. `FakeStt` already has `recognizing` — the
existing `ListenGateTest` asserts on it — it was just never lifted onto the
interface.

Do:

1. Add `val recognizing: Boolean` to `SttProvider` in `VoicePorts.kt`. Document
   it as "true from `startRecognition` until `abort` or a final" so an adapter
   author knows what to report. This is the only `:domain` change permitted in
   this PR.
2. `FakeStt` overrides it (its existing `var recognizing` with `private set`
   satisfies a `val` on the interface — confirm it compiles rather than
   restructuring the fake).
3. Guard the call: `if (!stt.recognizing) stt.startRecognition()`.
4. Test: drive `applyListenDecision` with the same `OpenCloud` decision N times
   against a fake that **counts** `startRecognition` calls, and assert the count
   is 1. Add the counter to `FakeStt` if it is not there. Also assert that
   `abort()` then a further `OpenCloud` starts it again — the guard must not
   wedge a window closed after a legitimate abort.

While you are in there, answer in the report, do not fix: `applyListenDecision`
calls `startRecognition()` with **no language list**, so `languages` is always
empty. Say what the intended source of that list is. Do not invent one —
the Telugu/English decision is not made yet and is waiting on the bench harness.

## Fix 2 — TTS echo is recorded as certainty when it is a guess

`listenDecision` returns `VadOnly` when `vad.echoFromTts` is true, and the test
is named `ttsEchoIsNotUserSpeech`. But `echoFromTts = true` means echo was
*detected*, not that no user is speaking underneath it. Today the coach speaking
outside a window means the user cannot get in at all: wake word off, cloud off.

Contract 35 says the coach opens *most* windows after speaking. It is the other
ones — a rest countdown, a mid-set cue — where someone says "stop, my knee" and
nothing is listening. It does not bite yet only because `VoiceTurnMachine` is
not wired to this gate. It will bite the moment it is.

Separating user speech from our own returning voice needs echo cancellation,
which is the same measurement block as the wake word. So this is **not a code
fix**. Do:

1. Add a row to `DECISIONS_AND_THRESHOLDS.md`, sibling to the existing
   wake-word/SNR row: user speech during TTS is undetectable without AEC;
   `echoFromTts` gates the wake word closed; `UNKNOWN — REQUIRES VALIDATION`;
   adapter blocked on measurement.
2. Rename the test to say what is actually true — something like
   `ttsEchoClosesTheGateUntilAecIsMeasured`. A test name is a claim; this one
   currently claims more than we know.
3. Add a line to contract 35 recording that barge-in outside a coach-opened
   window is not reachable through this gate yet, and that wiring
   `VoiceTurnMachine` to it must not silently drop that path.

## Fix 3 — one flag, two different failures (note only)

`usable = false` covers gym noise and a dead or unpermitted mic, and both speak
`FALLBACK_ON_SCREEN_WHY`: *"The gym is too loud for a wake word. Use the mic on
the screen."* For a permission failure that is the wrong diagnosis pointing at a
control that will not work either.

No fix now — there is no permission UI yet. Add a `TODO(onboarding)` at the
`usable == false` branch naming the case, so whoever builds onboarding finds it.

## Rules

- `:domain` only. The single interface addition above is the whole surface.
- No new numeric constant. If you think you need one, stop and report.
- Contracts 1–37 stay green. `.\gradlew :domain:test :persist:test`.

## Report back

- The call-count test, quoted.
- Where the language list should come from.
- The two doc rows added.
- Anything else in `Listen.kt` that a fake makes look correct.
