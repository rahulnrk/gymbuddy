---
id: 01-gym-mode-session-lifecycle
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Gym Mode session lifecycle (start, pause, resume, complete)

**Status:** implementing  
**Core-loop role:** Kernel. Enters and exits the continuous interaction loop. Without this, there is no companion session.

### User behavior

- From dashboard, tap **START WORKOUT** (primary CTA) or, if already in Gym Mode hydration, resume.
- Optional voice while session is starting: “Yeah.” / “Let’s go.” after AI greeting.
- Pause: “Pause.” / “Hold on.” / system pause on long background + explicit user pause control (large HUD control, not required each set).
- Resume: “I’m back.” / Resume control.
- Complete: last exercise finished, or “I’m done.” / “That’s it.”
- Phone may go in pocket after start. User is **not** required to keep the screen on.

### System state

- Preconditions: a `users.id` (local UUID allowed until auth binds); a planned workout row (fixture workout is valid); no other non-terminal session (or this is a resume of that row).
- **Start sequence (only):** `IDLE --SESSION_INITIALIZED--> WORKOUT_STARTING` (hydrate plan, first-exercise history, audio ports) `--WORKOUT_STARTED--> READY_FOR_EXERCISE`. `WORKOUT_STARTED` is **invalid** from `IDLE`.
- Pause → `PAUSED`, store `prePauseState` + `restRemainingSec`.
- Resume → `WORKOUT_RESUMED` from `PAUSED` only. Crash → `SESSION_REHYDRATED`, **not** a second `WORKOUT_STARTED`.
- Complete → `WORKOUT_COMPLETE` then close to `IDLE`.
- After greeting ack or 8s in `EXERCISE_ACTIVE` with 0 sets: `SET_STARTED` → `SET_ACTIVE`.
- Four contexts: workout snapshot empty→hydrated; historical for first exercise loaded; conversational topic = session start; user language/units/equipment loaded.

### Voice behavior

- On start, if history exists: *“You’re on chest and triceps today. Last time we started with 60 kilos on bench. Ready?”* (product §56).
- If no history: *“You’re on chest and triceps today. Starting with bench. Ready?”* — **do not invent** a last weight.
- After “Yeah.”: `shouldSpeak` → `ack_only` (no extra TTS). Engine may emit `SET_STARTED`. Then `SET_ACTIVE` silence.
- Pause: short *“Paused.”* only if useful; do not lecture.
- Resume: *“Back on bench, set 2.”* if that is true.
- Complete: see Feature 24.
- Barge-in: user can interrupt the greeting (“Wait, what weight?”).
- Language: respond in detected/primary conversation language; UI English.
- Do not narrate “Workout started in the database.”

### Structured event

- `SESSION_INITIALIZED` `{ sessionId, workoutId }` from `IDLE`.
- `WORKOUT_STARTED` `{ workoutId, startedAt }` from `WORKOUT_STARTING` only.
- `SESSION_REHYDRATED` `{ sessionId }` on crash restore.
- `WORKOUT_PAUSED` `{ reason }`.
- `WORKOUT_RESUMED` `{ restoredState, restRemainingSec }` from `PAUSED` only.
- `WORKOUT_COMPLETED` `{ completedAt }`.
- Validation: one non-terminal session per user; duplicate Start is a no-op.

### Database change

- `sessions` row: state, timestamps.
- `workouts.status` → `in_progress` / `completed`.
- `event_log` append. `source=manual` for CTA tap start; `source=voice` if start/end uttered; `source=system` for hydrate.

### UI state

- HUD: exercise name, set, target, previous performance (or “no previous”), rest, progress, voice status, optional music/connection.
- User is **not** required to tap through exercises to start listening.
- Conversation caption optional (Feature 25).
- **A11y (product §47):** every HUD control has a screen-reader label; focus order: Start/Pause → set numbers → rest; tap targets ≥ 44×44 dp; high contrast; Gym Mode usable without looking (voice + pocket). Test: `01-lifecycle.ui.a11y-labels-focus-order`.

### Error / recovery

- Hydrate fail → `ERROR_RECOVERY`, keep local plan if present, retry audio pipeline; do not lose a resumed session.
- Crash: on relaunch, if local session not complete, offer Continue (dashboard) and restore state from `event_log`.
- Audio permission denied: session can still run with manual logging; voice status shows unavailable; do not pretend to listen.

### Tests

Named cases:

- `01-lifecycle.event.session-init-then-workout-started` — Start emits `SESSION_INITIALIZED` then `WORKOUT_STARTED`; state never applies `WORKOUT_STARTED` from `IDLE`.
- `01-lifecycle.event.resume-is-not-workout-started` — process-kill restore emits `SESSION_REHYDRATED`; pause/resume emits `WORKOUT_RESUMED` only.
- `01-lifecycle.event.duplicate-start-noop`
- `01-lifecycle.state.pause-resting-restores-remaining-sec`
- `01-lifecycle.voice.greeting-cites-60-only-if-known`
- `01-lifecycle.db.kill-app-in-progress`
- `01-lifecycle.ui.a11y-labels-focus-order`
- `01-lifecycle.offline.start-with-fixture-workout`

---
