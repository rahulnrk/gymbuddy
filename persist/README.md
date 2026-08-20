# :persist — SQLite / Room EventLog

PR 3 implements durable persistence for committed `WorkoutEvent` rows.

`:persist` depends on `:domain`. `:domain` does not depend on `:persist`.

## What this module is

- Room-shaped `event_log` (`WorkoutEventEntity`, `WorkoutEventDao`, `@Database` version 1)
- `RoomEventLog` implementing `com.gymbuddy.domain.persist.EventLog`
- Typed `WorkoutEventCodec` (event type + payload; malformed data fails)
- Fixture user / workout / exercise / session so Gym Mode can start without the dashboard
- Schema version 1 with non-destructive migration discipline

This repository does not have an Android SDK in CI. The executable EventLog uses the Room schema via SQLite JDBC. Android later binds the same `@Entity`/`@Dao` types with `Room.databaseBuilder`.

## What this module is not

- Not a command parser
- Not CandidateValidator / WorkoutEngine / WorkoutProjector
- Not STT / TTS / VAD / LLM
- Not UI / Compose / Gym Mode HUD
- Not a `ValidatedFact` store
- Not cloud sync / outbox

Natural conversation stays unrestricted. Only committed `WorkoutEvent` rows are persisted.

## Idempotency and sequencing

- Idempotency: `PRIMARY KEY(event_id)` and `UNIQUE(user_id, event_id)`
- Ordering: `UNIQUE(session_id, sequence)`; loads `ORDER BY sequence ASC`
- Append: one transaction assigns `MAX(sequence)+1`; unique conflicts do not overwrite

## Recovery

Process kill recovery is `load(sessionId)` → `WorkoutProjector.fold()`. Snapshots are not stored as truth.
