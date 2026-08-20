---
id: 27-music-coexistence-audio-focus
status: implemented
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Music coexistence / audio focus abstraction

**Status:** implemented (PR 5 `AudioFocusController` + `FakeAudioFocus`). Real OS adapters are PR 23.  
**Core-loop role:** Kernel companion condition. Music is a core requirement (product §13). The user trains **with** music, not instead of it.

### User behavior

Starts Spotify / Apple Music / YouTube Music / other. Starts GymBuddy. Music plays. When AI or user talks, music ducks; then restores. User does not manually pause/resume for every interaction.

### System state

`AudioFocusController` per OS. Capabilities: duck / pause_if_must / unsupported. Workout engine unaware of OS.

Sequence: music playing → duck → listen/speak → restore.

### Voice behavior

Do not speak “ducking music now.” If unsupported, still speak; volume may compete; degrade gracefully. Never fail the session because Spotify cannot be ducked.

### Structured event

`AUDIO_FOCUS_CHANGED` `{ ducked | restored | denied | unsupported }`. No set mutations.

### Database change

None required (telemetry only).

### UI state

Optional music/connection indication on HUD. User not required to pick a song inside GymBuddy (no in-app music marketplace).

### Error / recovery

Focus denied: log `denied`, continue. OS-specific implementations behind the interface. Desktop/web later can no-op duck.

### Tests

- `27-music.event.duck-before-tts`
- `27-music.event.restore-after-barge-in`
- `27-music.event.unsupported-still-logs`
- `27-music.event.engine-has-no-os-audio-import`

---
