# Voice and audio ports

`:domain` owns interfaces and **deterministic fakes** (PR 5). Real vendor/Android adapters belong in `:app` (PR 23–24).

Vendors unset (Q3–Q5). Cloud STT/LLM allowed. HUD number pad when offline. On-device STT not required in MVP.

Natural human-AI conversation is unrestricted. Voice ports transport speech; they do not constrain the user's vocabulary to commands.

SILENT is idle. LISTENING is VAD-armed. INTERRUPTED is barge-in in flight (then USER_SPEAKING). STOPPED ends the voice session. VoiceTurnState never writes WorkoutSessionState.

## Ports

- `Clock` (`now()` in monotonic milliseconds. Tests supply a fake; `:app` supplies wall time.)
- `VadProvider` (speech start/stop, `setListener(VadListener)` — the adapter pushes observations; the kernel implements the listener)
- `SttProvider` (`recognizing` is true from `startRecognition` until `abort`; a final does not end the session — one window is one recognition session; `setListener(SttListener)` — the adapter pushes chunks; `supportsOffline` gates voice logging with no network; `sttFinalHash` = sha256 of normalized final)
- `WakeWordProvider` (on-device wake word. **Dormant for v1.** Phone in a pocket or locker; only usable mic is earbuds; ordinary Bluetooth open-mic drops music to call quality. Types stay. Returns if LE Audio earbuds become common.)
- `TopicScope` (in-scope vs out-of-scope for the gym-coach brief. Real classification needs a model. Tests use `FakeTopicScope`. Production main must not keyword-classify.)
- `TtsProvider` / `TtsHandle.cancel()` for barge-in. Completion is `speaking` going false, not a `complete()` on the port.
- `LidProvider` (en/hi/te/ta + code-mixed; does not translate)
- `AudioFocusController` (duck / restore / release; degrade if unsupported)
- `LlmProvider.extractCandidates` → `CandidateEventEnvelope` only (not implemented in PR 5)
- `LlmProvider.phraseCoaching` → string phrasing of an allowed speech act (not implemented in PR 5)
- `RetrievalProvider` for Feature 28 (not a chat API)

Fakes live in `voice/fake/` and are test-only. `VoiceTurnMachine` and `CompanionLoop` take the ports, not the fakes. `listenDecision` / `applyListenDecision` is the gate: VAD → wake word → cloud. **The gate is dormant for v1** (same earbud-mic reason as `WakeWordProvider`). Do not delete it. The coach opens windows. The only live user-initiated entry is the emergency control.

Workout engine, projector, and validator import **zero** provider types. The 250 ms / headset / overlap auto-commit gate stays in `CandidateValidator`. The voice layer only transports duration, route, talker count, and overlap.

## Voice-set commit route (closed; PR 1)

Auto-commit voice `SET_COMPLETED` **only if all of**:

1. `audioRoute == headset`
2. `talkerCount == 1` (single talker)
3. `overlappingVad == false` (no overlapping energy cluster / second speaker)
4. `sttDurationMs >= 250` (drop pocket rustle / sub-250 ms bursts)
5. current exercise exists
6. confidence ≥ 0.55 unless the mandatory weight-inherit path

Speakerphone: HUD confirm, **no** auto-commit. Overlapping VAD: **drop**. Duration &lt; 250 ms: **drop**.

Tests: `03-voice.event.headset-got-10-commits`, `03-voice.event.bystander-got-10-no-commit`, plus unit tests `durationBelow250msNotAutoCommit` and `overlappingVadNotAutoCommit`.

PR 2 must not re-decide this gate. It only receives `ValidatedFact`s that already passed it.

## Barge-in

VAD `speech_start` cancels TTS. p95 < 200 ms. Golden: `03-voice.voice.barge-in-lt-200ms`.

## Music

Duck on user speech and AI speech. Restore after. Do not claim Spotify/Apple Music/YouTube Music APIs.

Music echo is modeled on `VadObservation.echoFromMusic`. It is louder, constant, and harder than TTS echo. If `usable` is not `true`, `listenDecision` falls back to the on-screen mic rather than pretending a wake word fired. An earbud tap is not the fallback: while music plays, that button is a music control.
