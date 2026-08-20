"""PR 0 artifact generator: schemas + golden fixtures. Does not implement PR 1 validators."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "docs" / "living-spec" / "schemas" / "v1"
GOLDEN_DIR = ROOT / "tests" / "golden"


def dump(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


EVENT_BASE_PROPS = {
    "eventId": {"type": "string", "format": "uuid"},
    "sessionId": {"type": ["string", "null"], "format": "uuid"},
    "userId": {"type": "string", "format": "uuid"},
    "occurredAt": {"type": "string", "format": "date-time"},
    "recordedAt": {"type": "string", "format": "date-time"},
    "source": {"enum": ["voice", "manual", "imported", "ai-generated", "system"]},
    "schemaVersion": {"type": "integer", "const": 1},
    "transcriptId": {"type": "string", "format": "uuid"},
    "language": {"type": "string", "minLength": 2},
    "confidence": {"type": "number", "minimum": 0, "maximum": 1},
    "sttFinalHash": {"type": "string", "minLength": 8},
    "batchId": {"type": "string", "format": "uuid"},
}

EVENT_BASE_REQUIRED = [
    "eventId",
    "sessionId",
    "userId",
    "occurredAt",
    "recordedAt",
    "source",
    "schemaVersion",
    "type",
    "payload",
]


def envelope(type_const: str, payload: dict, extra_props=None) -> dict:
    props = {
        **EVENT_BASE_PROPS,
        "type": {"const": type_const},
        "payload": payload,
    }
    if extra_props:
        props.update(extra_props)
    return {
        "type": "object",
        "additionalProperties": False,
        "required": EVENT_BASE_REQUIRED,
        "properties": props,
    }


def schemas() -> dict[str, dict]:
    candidate = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/candidate-event.schema.json",
        "title": "CandidateEventEnvelope",
        "description": "LLM extractors MUST emit this union. Unknown type values are dropped. This is not a committed catalog row. schemaVersion 1.",
        "type": "object",
        "required": ["schemaVersion", "candidates"],
        "additionalProperties": False,
        "properties": {
            "schemaVersion": {"type": "integer", "const": 1},
            "candidates": {
                "type": "array",
                "items": {"$ref": "#/$defs/CandidateEvent"},
            },
        },
        "$defs": {
            "CandidateEvent": {
                "allOf": [
                    {
                        "type": "object",
                        "required": [
                            "candidateId",
                            "type",
                            "overallConfidence",
                            "fieldConfidence",
                            "payload",
                        ],
                        "additionalProperties": False,
                        "properties": {
                            "candidateId": {"type": "string", "format": "uuid"},
                            "type": {
                                "enum": [
                                    "SET_COMPLETED",
                                    "SET_CORRECTED",
                                    "SET_DELETED",
                                    "WEIGHT_CHANGED",
                                    "REPS_CHANGED",
                                    "EXERCISE_CHANGED",
                                    "SUBSTITUTION_PROPOSE",
                                    "USER_NOTE",
                                    "USER_FEEDBACK",
                                    "USER_QUESTION",
                                    "COMMAND",
                                    "SAFETY",
                                ]
                            },
                            "overallConfidence": {
                                "type": "number",
                                "minimum": 0,
                                "maximum": 1,
                            },
                            "fieldConfidence": {
                                "type": "object",
                                "additionalProperties": {
                                    "type": "number",
                                    "minimum": 0,
                                    "maximum": 1,
                                },
                            },
                            "exerciseNameRaw": {"type": "string"},
                            "setNumberHint": {
                                "oneOf": [
                                    {"type": "integer", "minimum": 1},
                                    {
                                        "enum": [
                                            "first",
                                            "second",
                                            "third",
                                            "last",
                                            "this",
                                        ]
                                    },
                                ]
                            },
                            "speechSpan": {
                                "type": "object",
                                "required": ["startMs", "endMs"],
                                "additionalProperties": False,
                                "properties": {
                                    "startMs": {"type": "number"},
                                    "endMs": {"type": "number"},
                                },
                            },
                            "loggingMode": {"enum": ["live", "catch_up"]},
                            "payload": {"type": "object"},
                        },
                    },
                    {
                        "oneOf": [
                            {
                                "properties": {
                                    "type": {"const": "SET_COMPLETED"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "reps": {
                                                "type": "integer",
                                                "minimum": 0,
                                            },
                                            "weight": {
                                                "type": "number",
                                                "exclusiveMinimum": 0,
                                            },
                                            "weightUnit": {"enum": ["kg", "lb"]},
                                            "rpe": {
                                                "type": "number",
                                                "minimum": 1,
                                                "maximum": 10,
                                            },
                                            "rir": {
                                                "type": "integer",
                                                "minimum": 0,
                                                "maximum": 5,
                                            },
                                            "effortNote": {"type": "string"},
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "SET_CORRECTED"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "reps": {
                                                "type": "integer",
                                                "minimum": 0,
                                            },
                                            "weight": {
                                                "type": "number",
                                                "exclusiveMinimum": 0,
                                            },
                                            "weightUnit": {"enum": ["kg", "lb"]},
                                            "target": {"type": "string"},
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "SET_DELETED"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "reason": {"type": "string"}
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "WEIGHT_CHANGED"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "required": ["weight"],
                                        "properties": {
                                            "weight": {
                                                "type": "number",
                                                "exclusiveMinimum": 0,
                                            },
                                            "weightUnit": {"enum": ["kg", "lb"]},
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "REPS_CHANGED"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "reps": {
                                                "type": "integer",
                                                "minimum": 0,
                                            },
                                            "targetReps": {"type": "string"},
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "EXERCISE_CHANGED"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "fromExerciseId": {
                                                "type": "string",
                                                "format": "uuid",
                                            },
                                            "toExerciseId": {
                                                "type": "string",
                                                "format": "uuid",
                                            },
                                            "reason": {"type": "string"},
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "SUBSTITUTION_PROPOSE"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "fromExerciseId": {
                                                "type": "string",
                                                "format": "uuid",
                                            },
                                            "toExerciseId": {
                                                "type": "string",
                                                "format": "uuid",
                                            },
                                            "toExerciseNameRaw": {
                                                "type": "string"
                                            },
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "USER_NOTE"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "required": ["textOriginal"],
                                        "properties": {
                                            "textOriginal": {
                                                "type": "string",
                                                "minLength": 1,
                                            },
                                            "scope": {
                                                "enum": [
                                                    "set",
                                                    "exercise",
                                                    "session",
                                                    "user",
                                                ]
                                            },
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "USER_FEEDBACK"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "required": ["kind"],
                                        "properties": {
                                            "kind": {
                                                "enum": [
                                                    "easy",
                                                    "hard",
                                                    "dislike_exercise",
                                                    "fatigue",
                                                    "shoulder_discomfort",
                                                    "other",
                                                ]
                                            },
                                            "intensity": {"type": "number"},
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "USER_QUESTION"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "required": ["questionOriginal"],
                                        "properties": {
                                            "questionOriginal": {
                                                "type": "string",
                                                "minLength": 1,
                                            },
                                            "topic": {
                                                "enum": [
                                                    "technique",
                                                    "history",
                                                    "plan",
                                                    "nutrition",
                                                    "recovery",
                                                    "other",
                                                ]
                                            },
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "COMMAND"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "required": ["kind"],
                                        "properties": {
                                            "kind": {
                                                "enum": [
                                                    "pause",
                                                    "resume",
                                                    "skip",
                                                    "extra_set",
                                                    "end_workout",
                                                ]
                                            },
                                        },
                                    },
                                }
                            },
                            {
                                "properties": {
                                    "type": {"const": "SAFETY"},
                                    "payload": {
                                        "type": "object",
                                        "additionalProperties": False,
                                        "properties": {
                                            "severity": {
                                                "enum": [
                                                    "low",
                                                    "medium",
                                                    "high",
                                                    "critical",
                                                ]
                                            },
                                            "category": {"type": "string"},
                                        },
                                    },
                                }
                            },
                        ]
                    },
                ]
            }
        },
    }

    epistemic = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/epistemic-fact.schema.json",
        "title": "EpistemicFact",
        "type": "object",
        "additionalProperties": False,
        "required": ["id", "kind", "stance", "sourceRowIds", "speechAllowed"],
        "properties": {
            "id": {"type": "string", "minLength": 1},
            "kind": {
                "enum": [
                    "set",
                    "session_summary",
                    "pr",
                    "note",
                    "aggregate",
                    "knowledge",
                    "catalog_exercise",
                ]
            },
            "stance": {
                "enum": ["known", "estimated", "inferred", "uncertain", "none"]
            },
            "value": True,
            "sourceRowIds": {
                "type": "array",
                "items": {"type": "string"},
            },
            "speechAllowed": {"type": "boolean"},
        },
    }

    workout_ctx = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/workout-context.schema.json",
        "title": "WorkoutStateContext",
        "type": "object",
        "additionalProperties": False,
        "required": [
            "sessionId",
            "workoutId",
            "workoutName",
            "sessionState",
            "voiceState",
            "completedSets",
            "remainingSetsPlanned",
            "substitutions",
            "startedAt",
        ],
        "properties": {
            "sessionId": {"type": "string", "format": "uuid"},
            "workoutId": {"type": "string", "format": "uuid"},
            "workoutName": {"type": "string"},
            "sessionState": {
                "enum": [
                    "IDLE",
                    "WORKOUT_STARTING",
                    "READY_FOR_EXERCISE",
                    "EXERCISE_ACTIVE",
                    "SET_ACTIVE",
                    "SET_COMPLETED",
                    "RESTING",
                    "PAUSED",
                    "EXERCISE_COMPLETE",
                    "WORKOUT_COMPLETE",
                    "ERROR_RECOVERY",
                ]
            },
            "voiceState": {
                "enum": [
                    "SILENT",
                    "USER_SPEAKING",
                    "PROCESSING",
                    "AI_SPEAKING",
                ]
            },
            "currentExerciseId": {"type": ["string", "null"], "format": "uuid"},
            "currentExerciseName": {"type": ["string", "null"]},
            "currentSetNumber": {"type": ["integer", "null"], "minimum": 1},
            "completedSets": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["setNumber"],
                    "properties": {
                        "setNumber": {"type": "integer", "minimum": 1},
                        "weightKg": {"type": ["number", "null"]},
                        "reps": {"type": ["integer", "null"]},
                        "rpe": {"type": "number"},
                        "rir": {"type": "integer"},
                    },
                },
            },
            "remainingSetsPlanned": {"type": "integer", "minimum": 0},
            "targetReps": {"type": ["string", "null"]},
            "currentWeightKg": {"type": ["number", "null"]},
            "restStartedAt": {"type": ["string", "null"], "format": "date-time"},
            "restTargetSec": {"type": ["number", "null"]},
            "restRemainingSec": {"type": ["number", "null"]},
            "substitutions": {
                "type": "array",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["fromExerciseId", "toExerciseId"],
                    "properties": {
                        "fromExerciseId": {"type": "string", "format": "uuid"},
                        "toExerciseId": {"type": "string", "format": "uuid"},
                    },
                },
            },
            "startedAt": {"type": "string", "format": "date-time"},
        },
    }

    historical = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/historical-context.schema.json",
        "title": "HistoricalStateContext",
        "description": "ONLY rows that exist. Empty means coaching must not invent.",
        "type": "object",
        "additionalProperties": False,
        "required": [
            "previousSessionsForExercise",
            "lastWorkoutSummary",
            "knownPrs",
            "aggregates",
            "recentNotesForExercise",
        ],
        "properties": {
            "previousSessionsForExercise": {"type": "array"},
            "lastWorkoutSummary": {"type": ["object", "null"]},
            "knownPrs": {"type": "array"},
            "aggregates": {
                "type": "array",
                "items": {"$ref": "gymbuddy://schemas/v1/epistemic-fact.schema.json"},
            },
            "recentNotesForExercise": {"type": "array"},
        },
    }

    conversational = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/conversational-context.schema.json",
        "title": "ConversationalStateContext",
        "type": "object",
        "additionalProperties": False,
        "required": ["queuedSpeechActs", "recentCorrections", "languageDetected"],
        "properties": {
            "currentTopic": {"type": ["string", "null"]},
            "lastUserUtterance": {"type": ["object", "null"]},
            "lastAiUtterance": {"type": ["object", "null"]},
            "pendingClarification": {"type": ["object", "null"]},
            "pendingCommand": {"type": ["object", "null"]},
            "queuedSpeechActs": {"type": "array", "items": {"type": "string"}},
            "recentCorrections": {"type": "array"},
            "languageDetected": {"type": "string"},
        },
    }

    user_ctx = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/user-context.schema.json",
        "title": "UserStateContext",
        "type": "object",
        "additionalProperties": False,
        "required": [
            "userId",
            "goals",
            "experience",
            "equipment",
            "units",
            "primaryLanguage",
            "additionalLanguages",
            "preferences",
            "limitations",
        ],
        "properties": {
            "userId": {"type": "string", "format": "uuid"},
            "goals": {
                "type": "array",
                "items": {
                    "enum": [
                        "muscle_gain",
                        "strength",
                        "fat_loss",
                        "general_fitness",
                        "conditioning",
                        "recomposition",
                    ]
                },
            },
            "experience": {
                "enum": ["beginner", "intermediate", "advanced"]
            },
            "equipment": {"type": "array", "items": {"type": "string"}},
            "units": {"enum": ["kg", "lb"]},
            "primaryLanguage": {"type": "string"},
            "additionalLanguages": {"type": "array", "items": {"type": "string"}},
            "preferences": {"type": "array"},
            "limitations": {"type": "array", "items": {"type": "string"}},
        },
    }

    payloads = {
        "SetCompleted": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "setId",
                "workoutExerciseId",
                "exerciseId",
                "setNumber",
                "loggingMode",
            ],
            "properties": {
                "setId": {"type": "string", "format": "uuid"},
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "exerciseId": {"type": "string", "format": "uuid"},
                "setNumber": {"type": "integer", "minimum": 1},
                "weight": {"type": ["number", "null"]},
                "weightUnit": {"enum": ["kg", "lb"]},
                "weightKg": {"type": ["number", "null"]},
                "reps": {"type": ["integer", "null"], "minimum": 0},
                "rpe": {"type": ["number", "null"]},
                "rir": {"type": ["integer", "null"]},
                "effortNote": {"type": "string"},
                "loggingMode": {"enum": ["live", "catch_up"]},
                "batchId": {"type": "string", "format": "uuid"},
            },
        },
        "SetCorrected": {
            "type": "object",
            "additionalProperties": False,
            "required": ["setId", "fields", "previous"],
            "properties": {
                "setId": {"type": "string", "format": "uuid"},
                "fields": {"type": "object"},
                "previous": {"type": "object"},
            },
        },
        "ExerciseStarted": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "workoutExerciseId",
                "exerciseId",
                "plannedSets",
            ],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "exerciseId": {"type": "string", "format": "uuid"},
                "plannedSets": {"type": "integer", "minimum": 1},
                "targetReps": {"type": "string"},
                "restTargetSec": {"type": "integer", "minimum": 0},
            },
        },
        "WorkoutStarted": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutId", "startedAt"],
            "properties": {
                "workoutId": {"type": "string", "format": "uuid"},
                "startedAt": {"type": "string", "format": "date-time"},
            },
        },
        "WorkoutPaused": {
            "type": "object",
            "additionalProperties": False,
            "required": ["reason"],
            "properties": {
                "reason": {
                    "enum": ["user", "audio_error", "app_background", "safety"]
                }
            },
        },
        "WorkoutResumed": {
            "type": "object",
            "additionalProperties": False,
            "required": ["restoredState"],
            "properties": {
                "restoredState": {"type": "string"},
                "restRemainingSec": {"type": ["number", "null"]},
            },
        },
        "WorkoutCompleted": {
            "type": "object",
            "additionalProperties": False,
            "required": ["completedAt"],
            "properties": {
                "completedAt": {"type": "string", "format": "date-time"},
                "summaryRef": {"type": "string"},
            },
        },
        "UserQuestion": {
            "type": "object",
            "additionalProperties": False,
            "required": ["questionOriginal", "questionCanonical"],
            "properties": {
                "questionOriginal": {"type": "string", "minLength": 1},
                "questionCanonical": {"type": "string", "minLength": 1},
                "topic": {
                    "enum": [
                        "technique",
                        "history",
                        "plan",
                        "nutrition",
                        "recovery",
                        "other",
                    ]
                },
            },
        },
        "UserFeedback": {
            "type": "object",
            "additionalProperties": False,
            "required": ["kind"],
            "properties": {
                "kind": {
                    "enum": [
                        "easy",
                        "hard",
                        "dislike_exercise",
                        "fatigue",
                        "shoulder_discomfort",
                        "other",
                    ]
                },
                "intensity": {"type": "number"},
                "refId": {"type": "string"},
            },
        },
        "UserNote": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "noteId",
                "textCanonical",
                "textOriginal",
                "lang",
                "scope",
            ],
            "properties": {
                "noteId": {"type": "string", "format": "uuid"},
                "textCanonical": {"type": "string"},
                "textOriginal": {"type": "string", "minLength": 1},
                "lang": {"type": "string"},
                "scope": {"enum": ["set", "exercise", "session", "user"]},
                "refId": {"type": "string"},
            },
        },
        "SafetyConcern": {
            "type": "object",
            "additionalProperties": False,
            "required": ["severity", "category", "transcriptSnippet", "action"],
            "properties": {
                "severity": {"enum": ["low", "medium", "high", "critical"]},
                "category": {"type": "string"},
                "transcriptSnippet": {"type": "string"},
                "action": {"enum": ["advise", "pause", "stop_recommend"]},
            },
        },
    }

    individual = {
        "set-completed": envelope("SET_COMPLETED", payloads["SetCompleted"]),
        "set-corrected": envelope("SET_CORRECTED", payloads["SetCorrected"]),
        "exercise-started": envelope(
            "EXERCISE_STARTED", payloads["ExerciseStarted"]
        ),
        "workout-started": envelope("WORKOUT_STARTED", payloads["WorkoutStarted"]),
        "workout-paused": envelope("WORKOUT_PAUSED", payloads["WorkoutPaused"]),
        "workout-resumed": envelope("WORKOUT_RESUMED", payloads["WorkoutResumed"]),
        "workout-completed": envelope(
            "WORKOUT_COMPLETED", payloads["WorkoutCompleted"]
        ),
        "user-question": envelope("USER_QUESTION", payloads["UserQuestion"]),
        "user-feedback": envelope("USER_FEEDBACK", payloads["UserFeedback"]),
        "user-note": envelope("USER_NOTE_CREATED", payloads["UserNote"]),
        "safety-concern": envelope("SAFETY_CONCERN", payloads["SafetyConcern"]),
    }

    committed_types = [
        "SESSION_INITIALIZED",
        "WORKOUT_STARTED",
        "SESSION_REHYDRATED",
        "EXERCISE_STARTED",
        "SET_STARTED",
        "SET_COMPLETED",
        "SET_CORRECTED",
        "SET_DELETED",
        "WEIGHT_CHANGED",
        "REPS_CHANGED",
        "EXERCISE_CHANGED",
        "EXERCISE_SKIPPED",
        "PLANNED_SETS_CHANGED",
        "REST_STARTED",
        "REST_ENDED",
        "WORKOUT_PAUSED",
        "WORKOUT_RESUMED",
        "WORKOUT_COMPLETED",
        "USER_NOTE_CREATED",
        "USER_FEEDBACK",
        "USER_QUESTION",
        "COACHING_RESPONSE",
        "SAFETY_CONCERN",
        "CLARIFICATION_REQUESTED",
        "INTENT_REJECTED",
        "SUBSTITUTION_APPLIED",
        "LANGUAGE_DETECTED",
        "BARGE_IN",
        "TURN_ENDED",
        "AUDIO_FOCUS_CHANGED",
        "MEMORY_UPDATED",
        "SYNC_ACKNOWLEDGED",
    ]

    workout_event = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/workout-event.schema.json",
        "title": "WorkoutEvent",
        "description": "Committed append-only event log row. NEVER produced directly by the LLM. schemaVersion 1.",
        "type": "object",
        "required": EVENT_BASE_REQUIRED,
        "properties": {
            **EVENT_BASE_PROPS,
            "type": {"enum": committed_types},
            "payload": {"type": "object"},
        },
        "allOf": [
            {
                "if": {"properties": {"type": {"const": name}}},
                "then": individual.get(
                    {
                        "SET_COMPLETED": "set-completed",
                        "SET_CORRECTED": "set-corrected",
                        "EXERCISE_STARTED": "exercise-started",
                        "WORKOUT_STARTED": "workout-started",
                        "WORKOUT_PAUSED": "workout-paused",
                        "WORKOUT_RESUMED": "workout-resumed",
                        "WORKOUT_COMPLETED": "workout-completed",
                        "USER_QUESTION": "user-question",
                        "USER_FEEDBACK": "user-feedback",
                        "USER_NOTE_CREATED": "user-note",
                        "SAFETY_CONCERN": "safety-concern",
                    }.get(name, ""),
                    envelope(
                        name,
                        {"type": "object"},
                    ),
                )
                if name
                in {
                    "SET_COMPLETED",
                    "SET_CORRECTED",
                    "EXERCISE_STARTED",
                    "WORKOUT_STARTED",
                    "WORKOUT_PAUSED",
                    "WORKOUT_RESUMED",
                    "WORKOUT_COMPLETED",
                    "USER_QUESTION",
                    "USER_FEEDBACK",
                    "USER_NOTE_CREATED",
                    "SAFETY_CONCERN",
                }
                else envelope(name, {"type": "object"}),
            }
            for name in committed_types
        ],
    }

    # The allOf/if-then chain above is verbose and error-prone if envelope() is reused
    # with extra properties. Rebuild workout-event as oneOf of known payloads +
    # a fallback that still forbids extra EventBase keys.
    one_of = []
    mapping = {
        "SET_COMPLETED": payloads["SetCompleted"],
        "SET_CORRECTED": payloads["SetCorrected"],
        "EXERCISE_STARTED": payloads["ExerciseStarted"],
        "WORKOUT_STARTED": payloads["WorkoutStarted"],
        "WORKOUT_PAUSED": payloads["WorkoutPaused"],
        "WORKOUT_RESUMED": payloads["WorkoutResumed"],
        "WORKOUT_COMPLETED": payloads["WorkoutCompleted"],
        "USER_QUESTION": payloads["UserQuestion"],
        "USER_FEEDBACK": payloads["UserFeedback"],
        "USER_NOTE_CREATED": payloads["UserNote"],
        "SAFETY_CONCERN": payloads["SafetyConcern"],
        "SESSION_INITIALIZED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["sessionId", "workoutId"],
            "properties": {
                "sessionId": {"type": "string", "format": "uuid"},
                "workoutId": {"type": "string", "format": "uuid"},
            },
        },
        "SESSION_REHYDRATED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["sessionId"],
            "properties": {"sessionId": {"type": "string", "format": "uuid"}},
        },
        "SET_STARTED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId", "setNumber"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "setNumber": {"type": "integer", "minimum": 1},
            },
        },
        "SET_DELETED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["setId"],
            "properties": {
                "setId": {"type": "string", "format": "uuid"},
                "reason": {"type": "string"},
            },
        },
        "WEIGHT_CHANGED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId", "weight", "weightKg"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "weight": {"type": "number", "exclusiveMinimum": 0},
                "weightUnit": {"enum": ["kg", "lb"]},
                "weightKg": {"type": "number", "exclusiveMinimum": 0},
            },
        },
        "REPS_CHANGED": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "targetReps": {"type": "string"},
                "setId": {"type": "string", "format": "uuid"},
                "reps": {"type": "integer", "minimum": 0},
            },
        },
        "EXERCISE_CHANGED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId", "fromExerciseId", "toExerciseId"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "fromExerciseId": {"type": "string", "format": "uuid"},
                "toExerciseId": {"type": "string", "format": "uuid"},
                "reason": {"type": "string"},
            },
        },
        "EXERCISE_SKIPPED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"}
            },
        },
        "PLANNED_SETS_CHANGED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId", "plannedSets", "previousPlannedSets", "reason"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "plannedSets": {"type": "integer", "minimum": 1},
                "previousPlannedSets": {"type": "integer", "minimum": 1},
                "reason": {"enum": ["extra_set", "user_edit"]},
            },
        },
        "REST_STARTED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId", "afterSetId", "targetSec"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "afterSetId": {"type": "string", "format": "uuid"},
                "targetSec": {"type": "integer", "minimum": 0},
            },
        },
        "REST_ENDED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["workoutExerciseId", "reason"],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "reason": {"enum": ["timer", "user", "logged_through"]},
            },
        },
        "COACHING_RESPONSE": {
            "type": "object",
            "additionalProperties": False,
            "required": ["textSpoken", "lang", "silence", "citedFacts"],
            "properties": {
                "textSpoken": {"type": ["string", "null"]},
                "lang": {"type": "string"},
                "silence": {"type": "boolean"},
                "actId": {"type": "string"},
                "citedFacts": {"type": "array"},
            },
        },
        "CLARIFICATION_REQUESTED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["prompt"],
            "properties": {
                "prompt": {"type": "string"},
                "options": {"type": "array", "items": {"type": "string"}},
                "pendingCandidate": {"type": "object"},
            },
        },
        "INTENT_REJECTED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["reason", "candidateType"],
            "properties": {
                "reason": {"type": "string"},
                "candidateType": {"type": "string"},
            },
        },
        "SUBSTITUTION_APPLIED": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "workoutExerciseId",
                "fromExerciseId",
                "toExerciseId",
                "preserveObjective",
            ],
            "properties": {
                "workoutExerciseId": {"type": "string", "format": "uuid"},
                "fromExerciseId": {"type": "string", "format": "uuid"},
                "toExerciseId": {"type": "string", "format": "uuid"},
                "preserveObjective": {"const": True},
            },
        },
        "LANGUAGE_DETECTED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["lang", "confidence", "mixed"],
            "properties": {
                "lang": {"type": "string"},
                "confidence": {"type": "number"},
                "mixed": {"type": "boolean"},
            },
        },
        "BARGE_IN": {
            "type": "object",
            "additionalProperties": False,
            "required": ["cancelledTtsId"],
            "properties": {"cancelledTtsId": {"type": "string"}},
        },
        "TURN_ENDED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["turnId", "durationMs"],
            "properties": {
                "turnId": {"type": "string"},
                "durationMs": {"type": "number", "minimum": 0},
            },
        },
        "AUDIO_FOCUS_CHANGED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["phase"],
            "properties": {
                "phase": {
                    "enum": ["ducked", "restored", "denied", "unsupported"]
                },
                "reason": {"type": "string"},
            },
        },
        "MEMORY_UPDATED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["preferenceKey", "value", "userVisible"],
            "properties": {
                "preferenceKey": {"type": "string"},
                "value": True,
                "userVisible": {"const": True},
            },
        },
        "SYNC_ACKNOWLEDGED": {
            "type": "object",
            "additionalProperties": False,
            "required": ["eventId", "serverSeq"],
            "properties": {
                "eventId": {"type": "string", "format": "uuid"},
                "serverSeq": {"type": "integer", "minimum": 0},
            },
        },
    }
    for name, payload in mapping.items():
        one_of.append(envelope(name, payload))

    workout_event = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "gymbuddy://schemas/v1/workout-event.schema.json",
        "title": "WorkoutEvent",
        "description": "Committed append-only event. The LLM cannot emit this object as truth. schemaVersion 1.",
        "oneOf": one_of,
    }

    out = {
        "candidate-event.schema.json": candidate,
        "workout-event.schema.json": workout_event,
        "workout-context.schema.json": workout_ctx,
        "historical-context.schema.json": historical,
        "conversational-context.schema.json": conversational,
        "user-context.schema.json": user_ctx,
        "epistemic-fact.schema.json": epistemic,
        "set-completed.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/set-completed.schema.json",
            **individual["set-completed"],
        },
        "set-corrected.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/set-corrected.schema.json",
            **individual["set-corrected"],
        },
        "exercise-started.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/exercise-started.schema.json",
            **individual["exercise-started"],
        },
        "workout-started.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/workout-started.schema.json",
            **individual["workout-started"],
        },
        "workout-paused.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/workout-paused.schema.json",
            **individual["workout-paused"],
        },
        "workout-resumed.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/workout-resumed.schema.json",
            **individual["workout-resumed"],
        },
        "workout-completed.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/workout-completed.schema.json",
            **individual["workout-completed"],
        },
        "user-question.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/user-question.schema.json",
            **individual["user-question"],
        },
        "user-feedback.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/user-feedback.schema.json",
            **individual["user-feedback"],
        },
        "user-note.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/user-note.schema.json",
            **individual["user-note"],
        },
        "safety-concern.schema.json": {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "gymbuddy://schemas/v1/safety-concern.schema.json",
            **individual["safety-concern"],
        },
    }
    return out


UID = "11111111-1111-4111-8111-111111111111"
SID = "22222222-2222-4222-8222-222222222222"
WID = "33333333-3333-4333-8333-333333333333"
EID = "44444444-4444-4444-8444-444444444444"
WEID = "55555555-5555-4555-8555-555555555555"
SET1 = "66666666-6666-4666-8666-666666666666"
CID = "77777777-7777-4777-8777-777777777777"
EV1 = "88888888-8888-4888-8888-888888888888"
NOW = "2026-08-20T16:00:00Z"


def cand(typ, payload, **kw):
    base = {
        "candidateId": kw.get("candidateId", CID),
        "type": typ,
        "overallConfidence": kw.get("overallConfidence", 0.92),
        "fieldConfidence": kw.get("fieldConfidence", {"reps": 0.95}),
        "payload": payload,
    }
    for k in (
        "exerciseNameRaw",
        "setNumberHint",
        "speechSpan",
        "loggingMode",
    ):
        if k in kw:
            base[k] = kw[k]
    return {"schemaVersion": 1, "candidates": [base]}


def multi(cands):
    return {"schemaVersion": 1, "candidates": cands}


def golden(gid, **kwargs):
    return {"id": gid, "schemaVersion": 1, **kwargs}


def goldens():
    cases = []

    cases.append(
        golden(
            "04-nl-logging.event.got-10-inherit-weight",
            pr=["0", "1"],
            contract="04-conversational-workout-parsing",
            stage="event",
            utterance="Got 10.",
            given={
                "sessionState": "RESTING",
                "currentExerciseId": EID,
                "currentWeightKg": 60,
                "audioRoute": "headset",
                "talkers": 1,
            },
            input=cand(
                "SET_COMPLETED",
                {"reps": 10},
                loggingMode="live",
                fieldConfidence={"reps": 0.96},
            ),
            expect={
                "schemaValid": True,
                "commit": True,
                "eventType": "SET_COMPLETED",
                "reps": 10,
                "weightKg": 60,
                "inheritWeight": True,
                "clarify": False,
            },
        )
    )
    cases.append(
        golden(
            "04-nl-logging.event.did-10-no-working-weight-clarify",
            pr=["0", "1"],
            contract="04-conversational-workout-parsing",
            stage="event",
            utterance="Did 10.",
            given={
                "sessionState": "RESTING",
                "currentExerciseId": EID,
                "currentWeightKg": None,
            },
            input=cand("SET_COMPLETED", {"reps": 10}, loggingMode="live"),
            expect={
                "schemaValid": True,
                "commit": False,
                "clarify": True,
                "reason": "no_working_weight",
            },
        )
    )
    cases.append(
        golden(
            "04-nl-logging.event.40-for-10",
            pr=["0", "1"],
            contract="04-conversational-workout-parsing",
            stage="event",
            utterance="40 for 10.",
            given={"sessionState": "RESTING", "currentExerciseId": EID},
            input=cand(
                "SET_COMPLETED",
                {"reps": 10, "weight": 40, "weightUnit": "kg"},
                loggingMode="live",
                fieldConfidence={"reps": 0.9, "weight": 0.9},
            ),
            expect={"schemaValid": True, "commit": True, "reps": 10, "weightKg": 40},
        )
    )
    cands_ms = []
    for i, (hint, reps, cid) in enumerate(
        [
            ("first", 12, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1"),
            ("second", 10, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2"),
            ("this", 8, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3"),
        ]
    ):
        cands_ms.append(
            {
                "candidateId": cid,
                "type": "SET_COMPLETED",
                "overallConfidence": 0.9,
                "fieldConfidence": {"reps": 0.9, "weight": 0.9},
                "setNumberHint": hint,
                "loggingMode": "catch_up",
                "payload": {"reps": reps, "weight": 40, "weightUnit": "kg"}
                if i == 0
                else {"reps": reps},
            }
        )
    cases.append(
        golden(
            "04-nl-logging.event.multi-set-40x12-10-8",
            pr=["0", "1"],
            contract="04-conversational-workout-parsing",
            stage="event",
            utterance="First set was 12 at 40, second was 10, and I got 8 on this one.",
            llm_required=True,
            given={"sessionState": "RESTING", "completedSets": []},
            input=multi(cands_ms),
            expect={
                "schemaValid": True,
                "commitCount": 3,
                "setNumbers": [1, 2, 3],
                "weightsKg": [40, 40, 40],
                "reps": [12, 10, 8],
                "restStartedBetween": False,
                "restStartedAfterLastIfPlannedRemain": True,
            },
        )
    )
    cases.append(
        golden(
            "04-nl-logging.event.telugu-60x8",
            pr=["0", "1"],
            contract="12-multilingual-normalized-storage",
            stage="event",
            utterance="Nenu 60 kilos tho eight reps chesa.",
            llm_required=True,
            given={"currentExerciseName": "Bench Press", "units": "kg"},
            input=cand(
                "SET_COMPLETED",
                {"reps": 8, "weight": 60, "weightUnit": "kg"},
                loggingMode="live",
                exerciseNameRaw="bench",
            ),
            expect={
                "schemaValid": True,
                "canonicalExercise": "Bench Press",
                "weightKg": 60,
                "reps": 8,
                "storageLanguage": "en",
            },
        )
    )
    cases.append(
        golden(
            "04-nl-logging.event.drop-weight-not-a-set",
            pr=["0", "1"],
            contract="04-conversational-workout-parsing",
            stage="event",
            utterance="Let's drop the weight.",
            input=cand(
                "WEIGHT_CHANGED",
                {"weight": 55, "weightUnit": "kg"},
                fieldConfidence={"weight": 0.8},
            ),
            expect={
                "schemaValid": True,
                "eventType": "WEIGHT_CHANGED",
                "notEventType": "SET_COMPLETED",
            },
        )
    )
    cases.append(
        golden(
            "05-extract.event.reps-as-string-ten-fails-schema",
            pr=["0"],
            contract="05-structured-event-extraction",
            stage="event",
            utterance="ten",
            input={
                "schemaVersion": 1,
                "candidates": [
                    {
                        "candidateId": CID,
                        "type": "SET_COMPLETED",
                        "overallConfidence": 0.9,
                        "fieldConfidence": {"reps": 0.9},
                        "payload": {"reps": "ten"},
                    }
                ],
            },
            expect={"schemaValid": False, "reason": "reps_must_be_integer"},
        )
    )
    cases.append(
        golden(
            "05-extract.event.unknown-type-dropped",
            pr=["0", "1"],
            contract="05-structured-event-extraction",
            stage="event",
            input={
                "schemaVersion": 1,
                "candidates": [
                    {
                        "candidateId": CID,
                        "type": "HALLUCINATED_PR",
                        "overallConfidence": 0.99,
                        "fieldConfidence": {},
                        "payload": {"weightKg": 200},
                    }
                ],
            },
            expect={"schemaValid": False, "dropUnknownType": True, "commit": False},
        )
    )
    cases.append(
        golden(
            "05-extract.event.last-week-as-set-completed-rejected",
            pr=["1"],
            contract="05-structured-event-extraction",
            stage="event",
            utterance="Last week I did 55.",
            given={"intent": "question"},
            input=cand("USER_QUESTION", {"questionOriginal": "Last week I did 55?", "topic": "history"}),
            expect={
                "schemaValid": True,
                "commitSet": False,
                "notInventHistory": True,
            },
        )
    )
    cases.append(
        golden(
            "01-lifecycle.event.session-init-then-workout-started",
            pr=["0", "1", "2"],
            contract="01-gym-mode-session-lifecycle",
            stage="event",
            expect={
                "from": "IDLE",
                "sequence": [
                    "SESSION_INITIALIZED",
                    "WORKOUT_STARTED",
                ],
                "invalid": {"from": "IDLE", "event": "WORKOUT_STARTED"},
            },
        )
    )
    cases.append(
        golden(
            "01-lifecycle.event.resume-is-not-workout-started",
            pr=["0", "2"],
            contract="01-gym-mode-session-lifecycle",
            stage="event",
            expect={
                "crash": "SESSION_REHYDRATED",
                "fromPaused": "WORKOUT_RESUMED",
                "forbidden": "WORKOUT_STARTED",
            },
        )
    )
    cases.append(
        golden(
            "06-state-machine.state.set-started-required",
            pr=["0", "2"],
            contract="06-deterministic-workout-state-machine",
            stage="state",
            expect={
                "setActiveRequires": "SET_STARTED",
                "producer": "engine",
                "llmMayEmit": False,
            },
        )
    )
    cases.append(
        golden(
            "06-state-machine.event.catch-up-40x12-10-8",
            pr=["0", "2"],
            contract="06-deterministic-workout-state-machine",
            stage="event",
            expect={
                "rows": 3,
                "restBetween": 0,
                "restAfterLastIfRemaining": True,
            },
        )
    )
    cases.append(
        golden(
            "07-correction.event.no-8-one-row",
            pr=["0", "1"],
            contract="07-correction-reconciliation",
            stage="event",
            utterance="No, 8.",
            given={"lastSet": {"setId": SET1, "reps": 10, "weightKg": 60}},
            input=cand(
                "SET_CORRECTED",
                {"reps": 8, "target": "last"},
                fieldConfidence={"reps": 0.94},
            ),
            expect={
                "schemaValid": True,
                "newSetRow": False,
                "eventType": "SET_CORRECTED",
                "setId": SET1,
                "reps": 8,
            },
        )
    )
    cases.append(
        golden(
            "07-correction.event.that-was-second-set",
            pr=["1"],
            contract="07-correction-reconciliation",
            stage="event",
            utterance="No, that was the second set.",
            expect={"targetsSetNumber": 2, "newSetRow": False},
        )
    )
    cases.append(
        golden(
            "08-context.event.empty-history-no-invented-weight",
            pr=["0", "4"],
            contract="08-four-context-assembly",
            stage="event",
            given={"previousSessionsForExercise": []},
            expect={
                "stance": "none",
                "forbiddenSpeech": ["60 kilos", "last time"],
                "requiredSpeechClass": "i_dont_have_a_previous_number",
            },
        )
    )
    cases.append(
        golden(
            "09-coaching.voice.same-reps-must-not-speak",
            pr=["0", "9"],
            contract="09-coaching-response-generation",
            stage="voice",
            given={"previousReps": 10, "thisReps": 10, "previousWeightKg": 60, "thisWeightKg": 60, "notFirstSet": True, "sessionState": "RESTING"},
            expect={"speak": False, "reason": "no_useful_delta", "persistCoachingResponse": True, "silence": True},
        )
    )
    cases.append(
        golden(
            "09-coaching.voice.plus-one-rep-must-speak",
            pr=["0", "9"],
            contract="09-coaching-response-generation",
            stage="voice",
            given={"previousReps": 9, "thisReps": 10, "sessionState": "RESTING"},
            expect={"speak": True, "actId": "useful_delta"},
        )
    )
    cases.append(
        golden(
            "09-coaching.voice.question-during-set-deferred",
            pr=["0", "9"],
            contract="09-coaching-response-generation",
            stage="voice",
            given={"sessionState": "SET_ACTIVE", "userQuestion": True},
            expect={
                "speakNow": False,
                "reason": "set_active",
                "queue": ["answer_question"],
                "drainOn": ["RESTING", "SET_COMPLETED", "EXERCISE_COMPLETE"],
            },
        )
    )
    cases.append(
        golden(
            "11-safety.event.chest-pain-critical",
            pr=["0", "6"],
            contract="11-safety-layer",
            stage="event",
            utterance="my chest hurts",
            expect={
                "runsOn": "raw_transcript_before_extract",
                "category": "chest_pain",
                "severity": "critical",
                "action": "stop_recommend",
                "pause": True,
                "llmCannotOverride": True,
            },
        )
    )
    cases.append(
        golden(
            "11-safety.event.chest-fly-occupied-not-critical",
            pr=["0", "6"],
            contract="11-safety-layer",
            stage="event",
            utterance="That chest fly machine is occupied.",
            expect={"safetyConcern": False, "route": "substitution"},
        )
    )
    cases.append(
        golden(
            "03-voice.event.bystander-got-10-no-commit",
            pr=["0", "1"],
            contract="03-voice-pipeline",
            stage="event",
            utterance="got 10",
            given={"audioRoute": "speakerphone", "currentExerciseId": EID, "currentWeightKg": 60},
            expect={"autoCommit": False, "setCompleted": 0},
        )
    )
    cases.append(
        golden(
            "03-voice.event.headset-got-10-commits",
            pr=["0", "1"],
            contract="03-voice-pipeline",
            stage="event",
            utterance="Got 10.",
            given={"audioRoute": "headset", "talkers": 1, "currentExerciseId": EID, "currentWeightKg": 60},
            expect={"autoCommit": True, "reps": 10, "weightKg": 60},
        )
    )
    cases.append(
        golden(
            "03-voice.voice.barge-in-lt-200ms",
            pr=["0", "5"],
            contract="03-voice-pipeline",
            stage="voice",
            expect={"bargeInCancelP95Ms": 200, "ttsMustCancel": True},
        )
    )
    cases.append(
        golden(
            "19-substitution.event.occupied-machine-accept",
            pr=["0", "16"],
            contract="19-substitution-engine",
            stage="event",
            utterance="That machine is occupied.",
            expect={
                "proposeThen": "SUBSTITUTION_APPLIED",
                "forbiddenCommitted": ["EXERCISE_CHANGED"],
            },
        )
    )
    cases.append(
        golden(
            "17-plans.event.skip-this",
            pr=["0", "2"],
            contract="17-workout-plans-and-editing",
            stage="event",
            utterance="Skip this.",
            input=cand("COMMAND", {"kind": "skip"}, overallConfidence=0.85, fieldConfidence={"kind": 0.85}),
            expect={"eventType": "EXERCISE_SKIPPED", "foldRestores": True},
        )
    )
    cases.append(
        golden(
            "17-plans.event.one-more-set-after-yes",
            pr=["0", "2"],
            contract="17-workout-plans-and-editing",
            stage="event",
            utterance="Can we do one more? Yeah.",
            expect={
                "eventType": "PLANNED_SETS_CHANGED",
                "reason": "extra_set",
                "doesNotLogSet": True,
            },
        )
    )
    cases.append(
        golden(
            "10-local-first.offline.three-sets-process-kill",
            pr=["0", "3"],
            contract="10-local-first-session-reliability",
            stage="offline",
            expect={
                "afterProcessKill": "same three sets",
                "event": "SESSION_REHYDRATED",
                "not": "WORKOUT_STARTED",
            },
        )
    )
    cases.append(
        golden(
            "28-qa.event.protein-question-retrieval-only",
            pr=["0", "9"],
            contract="28-gym-mode-qa",
            stage="event",
            utterance="How much protein should I have?",
            input=cand(
                "USER_QUESTION",
                {"questionOriginal": "How much protein should I have?", "topic": "nutrition"},
            ),
            expect={
                "schemaValid": True,
                "retrievalOnly": True,
                "notChatTab": True,
                "noMedicalCertainty": True,
            },
        )
    )
    cases.append(
        golden(
            "06-state-machine.event.workout-started-from-idle-rejected",
            pr=["0", "2"],
            contract="06-deterministic-workout-state-machine",
            stage="event",
            expect={"from": "IDLE", "event": "WORKOUT_STARTED", "legal": False},
        )
    )
    cases.append(
        golden(
            "09-coaching.event.silence-row-always-persisted",
            pr=["0", "9"],
            contract="09-coaching-response-generation",
            stage="event",
            expect={"coachingResponseRequired": True, "silenceFieldRequired": True},
        )
    )
    return cases


def main() -> None:
    SCHEMA_DIR.mkdir(parents=True, exist_ok=True)
    GOLDEN_DIR.mkdir(parents=True, exist_ok=True)
    for name, obj in schemas().items():
        dump(SCHEMA_DIR / name, obj)
        print("schema", name)
    # stable aliases expected by the living spec
    alias_dir = ROOT / "docs" / "living-spec" / "schemas"
    dump(alias_dir / "candidate-event.schema.json", schemas()["candidate-event.schema.json"])
    dump(alias_dir / "committed-event.schema.json", schemas()["workout-event.schema.json"])
    for case in goldens():
        dump(GOLDEN_DIR / f"{case['id']}.json", case)
        print("golden", case["id"])
    print("done", "schemas", len(schemas()), "goldens", len(goldens()))


if __name__ == "__main__":
    main()
