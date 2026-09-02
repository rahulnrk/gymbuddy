---
id: 03-voice-pipeline
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Voice pipeline (VAD, STT, language ID, barge-in, TTS, echo, turn detection)

**Status:** implementing (PR 5 ports + fakes + FakeClock barge-in). Real microphone / vendor adapters are later.  
**Core-loop role:** Kernel I/O. Converts sound to utterances and SpeakCommands to sound. Does not own workout truth.

Natural human-AI conversation is unrestricted. Voice ports transport speech; they do not constrain the user's vocabulary to commands.

### User behavior

User speaks unconstrained gym speech, including code-mix, overlapping AI. Interrupts: AI “Your next set is—” / user “Wait, what weight?” AI stops and answers (product §12). **No push-to-talk** in MVP Gym Mode (A5).

**Mic policy (single rule):** VAD is always-on for the whole Gym Mode session, including `SET_ACTIVE`. TTS is forbidden in `SET_ACTIVE` except `SAFETY_CONCERN` ≥ high. Wake word and cloud STT are **not** always-on: VAD gates the wake-word model, which gates the cloud (contract 35). Never run wake word on silence. Never open a cloud stream without a wake word or a coach-opened window.

**Voice-set commit route (closed; implements bystander golden):**

1. Read `audioRoute` from the platform audio session: `headset` (wired or BT headset / earpiece / near-field) vs `speakerphone` (built-in loudspeaker or bottom mic without a headset).
2. **Auto-commit** voice `SET_COMPLETED` only if `audioRoute === headset` **and** VAD saw a **single** talker cluster (no overlapping energy peaks within 300 ms that look like a second speaker).
3. If `audioRoute === speakerphone` → **do not auto-commit**. HUD confirm chips (or the user re-says it while looking at the phone). Neighbor “got 10” on speakerphone never becomes a set.
4. If headset **and** overlapping VAD clusters → drop the STT final; no commit. (Two-talker reject.)
5. Still apply duration ≥ **250 ms**, confidence 0.55 (except inherit path), current-exercise required, fabric-rustle drop. These gates are implemented in `DefaultCandidateValidator` (`durationBelow250msNotAutoCommit`, `overlappingVadNotAutoCommit`). PR 2 does not re-decide them.

Pocket + headset is valid near-field. Pocket + speakerphone is speakerphone (no auto-commit).

### System state

VoiceTurnState as specified. Echo: while `AI_SPEAKING`, STT either muted except barge-in detector, or echo-cancelled.

Preconditions: mic permission; audio session configured with voice + ducking.

### Voice behavior

- VAD speech_start → duck (user) + `USER_SPEAKING`.
- Endpoint → STT final → LID → conversation.
- TTS cancellable.
- Echo handling required.
- Turn detection: do not cut the user on a 200 ms pause mid-sentence; gym endpointing longer than IVR (configurable, start ~800–1200 ms).
- Language ID per utterance; mixed = true for Hinglish/Tanglish/Tenglish.

### Structured event

`LANGUAGE_DETECTED`, `BARGE_IN`, `TURN_ENDED`, `AUDIO_FOCUS_CHANGED`. These do not mutate sets.

### Database change

`conversation_turns` on finals. No `audio_blobs` by default.

### UI state

Voice status chip. User not required to tap mic each turn.

### Error / recovery

Mic loss → voice status error, session continues, manual HUD logging available. STT stream drop → restart stream without ending workout. TTS fail → skip speech, still persist events. Echo false barge-in → ignore sub-threshold; metrics.

**Offline:** if `supportsOffline`, STT continues. Else: **do not queue audio** (not even in RAM across process death); HUD numbers; one honesty line.

**Bystander / pocket:** first Gym Mode start shows mic-on notice. Apply the voice-set commit route above. Golden `03-voice.event.bystander-got-10-no-commit`: fixture is speakerphone **or** overlapping VAD + a clear “got 10” with current exercise → **zero** `SET_COMPLETED`. Golden `03-voice.event.headset-got-10-commits`: headset, single talker, current exercise, working weight → commit.

### Tests

- `03-voice.voice.barge-in-lt-200ms`
- `03-voice.event.echo-does-not-log-ai-words`
- `03-voice.event.multi-set-utterance-one-turn`
- `03-voice.event.bystander-got-10-no-commit` — speakerphone or overlapping VAD; current exercise; clear “got 10”; **no** SET_COMPLETED
- `03-voice.event.headset-got-10-commits` — headset, single talker
- `03-voice.event.pocket-rustle-no-commit`
- `03-voice.offline.no-audio-queue-when-cloud-stt`
- `03-voice.multilingual.lid-telugu-english`

---
