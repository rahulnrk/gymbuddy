-- GymBuddy persist schema version 1.
-- Destructive migration is forbidden. Subsequent versions add a migrator step.
-- This file is the only event_log / fixture DDL. JDBC and Android SQLite both execute it.
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
CREATE TABLE IF NOT EXISTS fixture_user (
  id TEXT PRIMARY KEY NOT NULL,
  display_name TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS fixture_exercise (
  id TEXT PRIMARY KEY NOT NULL,
  canonical_name TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS fixture_workout (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL,
  name TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS fixture_session (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL,
  workout_id TEXT NOT NULL
);
