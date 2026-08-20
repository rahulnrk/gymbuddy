---
id: 10-local-first-session-reliability
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Local-first session reliability and idempotent sync

**Status:** implementing — PR 3 local `event_log` is implemented. Outbox / cloud sync remains specified and is not built.  
**Core-loop role:** Kernel reliability. Dropped network must not erase the workout (product §31).

### User behavior

Walks into a dead zone. **Keeps training.** Logs by voice **if** on-device STT is present; otherwise taps HUD numbers. Later, the same sets are there — no duplicates after reconnect. Does not see a blocking “can’t log while offline” that erases the session.

### System state

Local `event_log` + projector is authoritative during the session. `outbox` pending. Session `local_epoch` increments.

### Voice behavior

Do not narrate sync. If cloud STT is unavailable and `supportsOffline=false`: **one** `offline_voice_unavailable` line per session (“I can’t hear you until you’re back online — tap the numbers if you want.”). Never every set. Never claim the user can keep talking.

### Structured event

`SYNC_ACKNOWLEDGED`. All workout events created locally first.

Idempotency key: `eventId`.

### Database change

Local insert always into Room `event_log` (schema version 1). Unique `(user_id, event_id)` and `(session_id, sequence)`. Only committed `WorkoutEvent` is stored. Server insert / `sync_status` pending→acked is **not** implemented (no sync client).

### UI state

Subtle offline indicator on HUD (optional). Numbers remain. User not required to tap Retry.

### Error / recovery

Optional backup: duplicate `eventId` = ack. 5xx retry with backoff. **No multi-device merge in MVP.** App kill: log durable before TTS even. No raw audio in the outbox.

### Tests

- `10-local-first.offline.three-sets-process-kill`
- Room DAO / EventLog / recovery / InMemory-vs-Room equivalence (`:persist:test`)
- `10-local-first.offline.hud-manual-without-stt` (later HUD)
- `10-local-first.event.no-audio-in-outbox`
- `10-local-first.event.push-replay-idempotent` (if backup enabled)

Natural conversation remains unrestricted. Persistence does not impose a command vocabulary. See `docs/living-spec/conversation/NATURAL_SPEECH.md`.

---
