---
id: 02-continuous-interaction-loop
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Continuous interaction loop

**Status:** implemented  
**Core-loop role:** **The product kernel.** All other features must declare how they feed this loop.

Headless `CompanionLoop` coordinates existing ports. No pixels. No vendor LLM.

### User behavior

Music playing (Spotify / Apple Music / YouTube Music / other). User talks naturally: “Got 10.” AI understands, records, replies naturally if useful, history updates. User does not pause music by hand, does not say “log set.”

Canonical scene (product §8, §56): last time 10, 9, 8 at 60 kg → “Got 10.” → “Nice. That’s one more than your last set. Want to stay at 60?” → “Yeah.” Stored: Bench Press set 1, 60 kg, 10 reps.

### System state

- Gym Mode active (`READY_FOR_EXERCISE` … `WORKOUT_COMPLETE` excluding `IDLE`).
- VoiceTurnState cycles SILENT → USER_SPEAKING → PROCESSING → AI_SPEAKING/SILENT.
- Four contexts assembled **every** final utterance before extraction.

### Voice behavior

- Duck on user speech and on AI speech; restore after.
- Barge-in required.
- Silence when not useful.
- Language-flexible; storage canonical.
- Never IVR: no “Command received.”

### Structured event

The loop may emit any catalog event appropriate to the utterance. Orchestration events: `LANGUAGE_DETECTED`, `BARGE_IN`, `TURN_ENDED`, `AUDIO_FOCUS_CHANGED`. `COACHING_RESPONSE` is **always** persisted (`silence` required).

Idempotency: each turn has `turnId`; extractor `eventId`s unique.

### Database change

Committed events only after validation. `conversation_turns` written with TTL. `workout_sets` from `SET_COMPLETED`. History read model updates immediately locally.

### UI state

HUD numbers update without taps. Voice indicator: listening / thinking / speaking / silent. User not required to confirm each log unless clarification.

### Error / recovery

STT fail → stay silent or “I didn’t catch that” only after repeated failure; do not fake a set. LLM down → rules fallback **English last-set numbers only** (coverage matrix). Audio focus denied → still listen/speak best-effort.

**Offline:** session truth + HUD always. Voice logging only if `supportsOffline`. Otherwise one `offline_voice_unavailable` line, then manual numbers. No audio buffer across process death.

### Tests

- `02-loop.event.gold-scene-got-10-plus-one-must-speak`
- `02-loop.event.duplicate-got-10-same-hash-10s-one-row`
- `02-loop.voice.duck-stt-event-tts-restore`
- `02-loop.event.empty-history-no-60kg`
- `02-loop.offline.hud-manual-when-no-offline-stt`
- `02-loop.multilingual.telugu-60x8` (`llm_required`)
- `02-loop.correction.no-8-inside-loop`

---
