-- GymBuddy persist schema version 1.
-- Destructive migration is forbidden. Subsequent versions add a migrator step.
CREATE TABLE IF NOT EXISTS event_log (
  event_id TEXT PRIMARY KEY NOT NULL,
  session_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  schema_version INTEGER NOT NULL,
  sequence INTEGER NOT NULL,
  payload_json TEXT NOT NULL,
  occurred_at TEXT NOT NULL,
  recorded_at TEXT NOT NULL,
  source TEXT NOT NULL,
  transcript_id TEXT,
  language TEXT,
  confidence REAL,
  stt_final_hash TEXT,
  batch_id TEXT,
  UNIQUE(user_id, event_id),
  UNIQUE(session_id, sequence)
);
