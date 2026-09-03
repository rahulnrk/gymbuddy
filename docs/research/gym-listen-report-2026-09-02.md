# Gym listen report — 2026-09-02

Floor tests of First Sound on a real phone, over music, in earbuds. Unit tests ran on the laptop after each code change. This is evidence, not a latency judgement.

**Tester / device:** Rahul. **Earbuds:** ZEB-BOOM, Bluetooth A2DP. **Music:** already playing from another app. **App:** debug APK from `gymbuddy`, First Sound screen. **TTS:** ElevenLabs Flash v2.5, voice Adam (`pNInz6obpgDQGcFmaJgB`), PCM 24 kHz. **Key:** present in debug only (`keyPresent: true`); never committed.

What this is not: full CompanionLoop, LLM coaching, set logging, or a vendor wake-word chip. Two-way today means hear the user and speak a short “I heard you” line.

---

## Verdict

The last floor run **passed**. Music ducks instead of pausing. The coach is intelligible. The sentence finishes. Ducking holds while the user talks. After music returns, **hey coach** / **coach** still works.

Earlier floor runs failed in specific, named ways. Those failures are below. They drove the code that is on git tonight.

---

## Setup that stayed true all day

| Check | Result |
|---|---|
| Output follows Bluetooth | **Pass.** Earphones when BT on; speaker when BT off. Route logged as `TYPE_BLUETOOTH_A2DP · ZEB-BOOM`. |
| Mic permission | **Pass** once granted. First Sound requests `RECORD_AUDIO` on open. |
| Debug key | **Pass.** `keyPresent: true`. Release guard still fails if the key is in `local.properties` (`assertReleaseVoiceGuard`). |
| Cancel | **Pass** across builds. Cancel is snappy; it does not drain leftover PCM. |

---

## Floor tests, in order

Each round is one installed debug APK. Failures are the reason the next round exists.

### Round 1 — First spoken line (no duck)

**What we did:** Open First Sound, music playing, tap Speak a line. Listen in the earbud. Cancel on a later tap. Toggle Bluetooth.

| Test | Result | Notes |
|---|---|---|
| One line in the earbud | **Pass** | Adam later; this round still used the first wired voice. |
| Time to first audio | **Pass (reported)** | ~680–815 ms. Example diagnostics: Connecting 0, Connected 318, First audio 815, Cancelled 2752. Other finishes ~2636 ms / ~3293 ms. Not judged as “too slow.” |
| Cancel before the line ends | **Pass** | lastError none. |
| Whole sentence | **Fail** | Cut off. Server `isFinal` flushed `AudioTrack` while buffer was still unplayed. |

**Change after fail:** `TtsPlayback.drain()` on the happy path; cancel still `stopAndFlush` immediately.

### Round 2 — Sentence completes; still no duck

| Test | Result | Notes |
|---|---|---|
| Whole sentence | **Pass** | Drain-before-stop. |
| Instruction + music together | **Fail** | Unintelligible. No duck yet. Docs-example Alice was too soft under gym-floor music. |

**Change after fail:** Duck with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` while the line plays. Voice Alice → Adam.

### Round 3 — Duck while the coach talks

| Test | Result | Notes |
|---|---|---|
| Music ducks when the coach speaks | **Pass** | “much better.” |
| Whole sentence | **Pass** | |
| Duck starts before the voice | **Fail** | Duck and first word were simultaneous. Ask: duck 3–4 s ahead. |

**Change after fail:** `DUCK_LEAD_MS = 3500`. Duck on tap, buffer PCM, start playback after remaining lead. Cancel during lead restores immediately.

### Round 4 — Retune lead/hold + first two-way attempt

Ask on the next floor pass: keep duck **1.8 s before** speak, **2.5 s after**, listen the whole duck window, prolong duck while the user talks, try hotword (`hey coach` / `coach`) in loud music, see if two-way works.

Shipped with Android `SpeechRecognizer` as VAD+STT, `DUCK_LEAD_MS = 1800`, `DUCK_HOLD_MS = 2500`, hard cap 60 s from the conversation-window constant.

| Test | Result | Notes |
|---|---|---|
| Music ducks (volume down, still playing) | **Fail** | Music **paused** as soon as listen started. SpeechRecognizer takes transient audio focus. |
| Duck 1.8 s before the coach | **Not verified** | Pause hid the duck. |
| Hold 2.5 s after, then restore | **Fail** | Duck died at **60 s** while still talking. Music came back under the coach. |
| Duck stays while the user talks | **Fail** | 60 s hard cap restored even when speaking. SpeechRecognizer also dropped `userSpeaking` between utterances. |
| Hotword during duck / while coach talks | **Pass (narrow)** | Only in the ducked window. |
| Hotword after music is back (`hey coach` / `coach`) | **Fail** | Listen was stopped on restore. Nothing happened. |
| Two-way | **Partial** | Heard the user only while ducked. |

**Change after fail:**
- Remove SpeechRecognizer. Capture with `AudioRecord` (`MIC`, 16 kHz) — no audio focus.
- Stream PCM to ElevenLabs Scribe realtime (`scribe_v2_realtime`, `filter_background_audio=true`, keyterms `coach` / `hey coach`).
- Arm the mic when First Sound opens. Music stays up. Hotword works idle.
- Duck only on Speak or a hotword hit. `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` only. Idempotent ducker (no abandon/re-request blip).
- No 60 s cut while speaking. Restore only after 2.5 s quiet. Do not restore while the coach is still playing.
- After restore, mic stays armed (`Music up. Say hey coach.`).

### Round 5 — Scribe listen, duck not pause, always-on hotword

**What we did:** Music playing. Open First Sound (mic armed, music still up). Say **hey coach** without tapping Speak. Tap Speak a line. Keep talking past a minute. After music returns, say **hey coach** again.

| Test | Result | Notes |
|---|---|---|
| Music stays up when the screen opens | **Pass** | Armed listen does not take focus. |
| Duck, not pause | **Pass** | |
| 1.8 s lead then coach | **Pass** | |
| Whole sentence | **Pass** | |
| Duck holds while talking (past 60 s) | **Pass** | |
| Coach and music do not overlap | **Pass** | |
| Hotword over full-volume music | **Pass** | `hey coach` / `coach`. |
| Two-way: hear user, short spoken reply | **Pass** | “I heard you. Coach is listening.” / “I heard: …” |
| After restore, hotword still works | **Pass** | |
| Tester summary | **Pass** | “this is perfect test it worked.” |

Copy diagnostics on this build include `duckLeadMs: 1800`, `duckHoldMs: 2500`, `sttModel: scribe_v2_realtime`, `lastHeard`, `hotword`, `vadSpeech`, `keyPresent` (boolean only).

---

## Unit tests (laptop)

Ran after the Scribe change, before the APK:

```
.\gradlew :app:testDebugUnitTest --tests com.gymbuddy.app.voice.ElevenLabsTtsTest --tests com.gymbuddy.app.voice.HotwordTest --tests com.gymbuddy.app.voice.DuckHoldTest --tests com.gymbuddy.app.voice.ScribeMessagesTest --tests com.gymbuddy.app.voice.FirstSoundSessionTest
```

**Result: BUILD SUCCESSFUL.**

| Suite | What it covers | Result |
|---|---|---|
| `ElevenLabsTtsTest` | Cancel stops playback before socket close; named failures (no key, 401, 429, drop, no audio, AudioTrack fail); first-audio/finished use the clock; drain before stop; cancel does not drain; duck before playback; duck lead delays start; cancel during lead restores without playing; `restoreOnFinish=false` leaves duck for the session; clipboard never contains a key | **Pass** |
| `HotwordTest` | `hey coach` and word-boundary `coach`; `coaching` does not match; echo of the coach line is ignored | **Pass** |
| `DuckHoldTest` | 2.5 s quiet after playback; speech prolongs; speech during playback does not shorten; speaking at 60 s still holds | **Pass** |
| `ScribeMessagesTest` | Partial / committed / auth_error parse | **Pass** |
| `FirstSoundSessionTest` | Listen starts with duck; restore after zero hold does **not** stop listen; user speech during hold keeps duck; idle hotword ducks without Speak; hey-coach final emits hotword + reply | **Pass** |

Domain listen-gate / VoiceTurnMachine tests were already green from the port-direction work (listeners on ports, fakes only in `voice/fake/`, no unguarded STT start). Not re-run as a full `:domain:test` in the last hour; they compiled as a dependency of `:app:testDebugUnitTest`.

---

## What we changed (code)

### First Sound / TTS (`:app`)

- `ElevenLabsTts` — streaming WebSocket Flash v2.5, PCM 24 kHz, handle returns immediately, cancel silences locally first.
- Drain before stop on the happy path.
- Duck on tap (`DUCK_LEAD_MS = 1800`). Buffer PCM off the OkHttp thread. Play after remaining lead.
- `restoreOnFinish = false` when First Sound owns the hold. Session restores after quiet.
- Voice Adam. Status lines are observed adapter state, not tap-time success. Copy diagnostics is boolean `keyPresent` plus timings — never a key prefix.
- Debug credentials from `local.properties`; release factory is backend placeholder; `assertReleaseVoiceGuard`.

### Listen / two-way (`:app`)

- `ScribeListen` — `AudioRecord` + Scribe realtime WebSocket. Not SpeechRecognizer.
- `FirstSoundSession` — arm on screen open; duck on Speak or hotword; hold while user or coach is speaking; restore after 2.5 s quiet; keep mic armed.
- `AudioFocusDucker` — duck once; restore once. No pause.
- Hotword is a transcript match (`hey coach`, `\bcoach\b`), not a wake-word vendor.

### Kernel (`:domain`) — same day, earlier

- Inbound events off production ports: `SttProvider.setListener` / `VadProvider.setListener`. Push lives on fakes.
- Listen gate owns STT start/abort. Finals do not end recognition.
- `FakeClock` / `FakeWakeWord` only under `voice/fake/`. Architecture test forbids Fake imports in production main.
- Conversation window policy at the time: a STATEMENT opened no window. **Withdrawn 2026-09-03** (contract 35): statements open the shortest window so a reminder can be answered. First Sound remains an experiment harness.

### Numbers (DECISIONS_AND_THRESHOLDS.md)

| Constant | Value | Why |
|---|---|---|
| `DUCK_LEAD_MS` | 1800 | 3.5 s felt long; 0 s was simultaneous. |
| `DUCK_HOLD_MS` | 2500 | After quiet, not after a 60 s cap. |
| `STT_MODEL_ID` | `scribe_v2_realtime` | STT without Android audio focus. |
| `STT_FORMAT` | `pcm_16000` | AudioRecord rate. |
| `VOICE_ID` | Adam `pNInz6obpgDQGcFmaJgB` | Alice too soft under music. |

---

## Still out of scope (not failed — not tested)

- Full workout conversation (sets, weight, rest, pain) through CompanionLoop / LLM.
- On-device wake-word vendor. Adapter still blocked on measurement; today is Scribe + keyword on the transcript.
- Barge-in under TTS with AEC. Echo of the coach line is filtered by string match.
- Telugu–English code-mix accuracy on the gym floor (not scored today).
- Release APK with a real backend token. Debug key only.
- STT over the earbud mic (HFP/SCO). Today is phone mic + A2DP playback. Switching to SCO would likely pause or degrade music.

---

## Tomorrow

Start from this commit and the last debug APK. Floor is green for: duck-not-pause, 1.8 s lead, hold while talking, always-on hey coach, short two-way reply.

Next product questions, if we pick them up:

1. Does “I heard: …” stay the right reply, or do we wire a real coaching turn?
2. Idle listen cost (Scribe session while the screen is open).
3. Whether phone-mic + A2DP is good enough in a louder gym than today’s test.

Do not re-open SpeechRecognizer for this path. It pauses music.