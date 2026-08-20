"""Rewrite PR 1 goldens. Run from repo; does not implement the validator."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
G = ROOT / "tests" / "golden"

SET1 = "66666666-6666-4666-8666-666666666666"
SET2 = "66666666-6666-4666-8666-666666666667"
BENCH = "44444444-4444-4444-8444-444444444444"
INCLINE = "44444444-4444-4444-8444-444444444445"
CID = "77777777-7777-4777-8777-777777777777"
BATCH = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
T_NOW = "2026-08-20T16:00:00Z"
T_30S = "2026-08-20T15:59:30Z"
T_180S = "2026-08-20T15:57:00Z"


def dump(name: str, obj: dict) -> None:
    path = G / name
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")
    print("wrote", name)


def cand(typ, payload, **kw):
    body = {
        "candidateId": kw.pop("candidateId", CID),
        "type": typ,
        "overallConfidence": kw.pop("overallConfidence", 0.92),
        "fieldConfidence": kw.pop("fieldConfidence", {}),
        "payload": payload,
    }
    body.update(kw)
    return {"schemaVersion": 1, "candidates": [body]}


def main() -> None:
    dump(
        "03-voice.event.bystander-got-10-no-commit.json",
        {
            "id": "03-voice.event.bystander-got-10-no-commit",
            "schemaVersion": 1,
            "pr": ["0", "1"],
            "layer": "pr1_validator",
            "contract": "03-voice-pipeline",
            "stage": "event",
            "utterance": "got 10",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": 60,
                "audioRoute": "speakerphone",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
            },
            "input": cand(
                "SET_COMPLETED",
                {"reps": 10},
                loggingMode="live",
                fieldConfidence={"reps": 0.96},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "drop_or_no_auto_commit",
                "autoCommit": False,
                "acceptFactCount": 0,
                "newSetRow": False,
                "notFactType": "SetCompleted",
            },
        },
    )
    dump(
        "03-voice.event.headset-got-10-commits.json",
        {
            "id": "03-voice.event.headset-got-10-commits",
            "schemaVersion": 1,
            "pr": ["0", "1"],
            "layer": "pr1_validator",
            "contract": "03-voice-pipeline",
            "stage": "event",
            "utterance": "Got 10.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": 60,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [],
            },
            "input": cand(
                "SET_COMPLETED",
                {"reps": 10},
                loggingMode="live",
                fieldConfidence={"reps": 0.96},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCompleted",
                "reps": 10,
                "weightKg": 60,
                "inheritWeight": True,
                "loggingMode": "live",
                "mustNotContain": [
                    "setNumber",
                    "SET_STARTED",
                    "REST_STARTED",
                    "REST_ENDED",
                    "SESSION_INITIALIZED",
                    "WORKOUT_STARTED",
                ],
            },
        },
    )

    dump(
        "04-nl-logging.event.got-10-inherit-weight.json",
        json.loads(
            (G / "04-nl-logging.event.got-10-inherit-weight.json").read_text(encoding="utf-8")
        )
        | {
            "layer": "pr1_validator",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": 60,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [],
            },
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCompleted",
                "reps": 10,
                "weightKg": 60,
                "inheritWeight": True,
                "clarify": False,
                "mustNotContain": ["setNumber", "REST_STARTED", "SET_STARTED"],
            },
        },
    )

    dump(
        "04-nl-logging.event.did-10-no-working-weight-clarify.json",
        json.loads(
            (G / "04-nl-logging.event.did-10-no-working-weight-clarify.json").read_text(
                encoding="utf-8"
            )
        )
        | {
            "layer": "pr1_validator",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": None,
                "completedSets": [],
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
            },
            "expect": {
                "schemaValid": True,
                "decision": "clarify",
                "commit": False,
                "reason": "no_working_weight",
                "newSetRow": False,
            },
        },
    )

    dump(
        "04-nl-logging.event.40-for-10.json",
        json.loads((G / "04-nl-logging.event.40-for-10.json").read_text(encoding="utf-8"))
        | {
            "layer": "pr1_validator",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [],
            },
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCompleted",
                "reps": 10,
                "weightKg": 40,
                "inheritWeight": False,
                "mustNotContain": ["setNumber", "REST_STARTED"],
            },
        },
    )

    dump(
        "04-nl-logging.event.drop-weight-not-a-set.json",
        json.loads(
            (G / "04-nl-logging.event.drop-weight-not-a-set.json").read_text(encoding="utf-8")
        )
        | {
            "layer": "pr1_validator",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": 60,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 500,
                "overlappingVad": False,
            },
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "WeightChanged",
                "weightKg": 55,
                "notFactType": "SetCompleted",
            },
        },
    )

    dump(
        "04-nl-logging.event.multi-set-40x12-10-8.json",
        {
            "id": "04-nl-logging.event.multi-set-40x12-10-8",
            "schemaVersion": 1,
            "pr": ["0", "1"],
            "layer": "pr1_validator",
            "relatedPr2Golden": "06-state-machine.event.catch-up-40x12-10-8",
            "contract": "04-conversational-workout-parsing",
            "stage": "event",
            "utterance": "First set was 12 at 40, second was 10, and I got 8 on this one.",
            "llm_required": True,
            "notes": "Same gym utterance as 06-state-machine.event.catch-up-40x12-10-8. PR 1: facts only. PR 2: official numbers and rest.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": None,
                "completedSets": [],
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 2500,
                "overlappingVad": False,
            },
            "input": {
                "schemaVersion": 1,
                "candidates": [
                    {
                        "candidateId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                        "type": "SET_COMPLETED",
                        "overallConfidence": 0.9,
                        "fieldConfidence": {"reps": 0.9, "weight": 0.9},
                        "setNumberHint": "first",
                        "loggingMode": "catch_up",
                        "payload": {"reps": 12, "weight": 40, "weightUnit": "kg"},
                    },
                    {
                        "candidateId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2",
                        "type": "SET_COMPLETED",
                        "overallConfidence": 0.9,
                        "fieldConfidence": {"reps": 0.9},
                        "setNumberHint": "second",
                        "loggingMode": "catch_up",
                        "payload": {"reps": 10},
                    },
                    {
                        "candidateId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3",
                        "type": "SET_COMPLETED",
                        "overallConfidence": 0.9,
                        "fieldConfidence": {"reps": 0.9},
                        "setNumberHint": "this",
                        "loggingMode": "catch_up",
                        "payload": {"reps": 8},
                    },
                ],
            },
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "layer": "pr1_validator",
                "acceptFactCount": 3,
                "factType": "SetCompleted",
                "reps": [12, 10, 8],
                "weightsKg": [40, 40, 40],
                "inheritWeight": [False, True, True],
                "loggingMode": "catch_up",
                "setNumberHints": ["first", "second", "this"],
                "batchIdentity": "shared_or_same_turn",
                "mustNotContain": [
                    "setNumber",
                    "officialSetNumbers",
                    "REST_STARTED",
                    "REST_ENDED",
                    "SET_STARTED",
                    "WorkoutSessionState",
                    "remainingSetsPlanned",
                ],
            },
        },
    )

    dump(
        "06-state-machine.event.catch-up-40x12-10-8.json",
        {
            "id": "06-state-machine.event.catch-up-40x12-10-8",
            "schemaVersion": 1,
            "pr": ["0", "2"],
            "layer": "pr2_projector",
            "relatedPr1Golden": "04-nl-logging.event.multi-set-40x12-10-8",
            "contract": "06-deterministic-workout-state-machine",
            "stage": "event",
            "utterance": "First set was 12 at 40, second was 10, and I got 8 on this one.",
            "notes": "Same scenario as 04-nl-logging.event.multi-set-40x12-10-8, projector layer. Do not implement in PR 1.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "completedSets": [],
                "plannedSets": 4,
            },
            "expect": {
                "layer": "pr2_projector",
                "rows": 3,
                "officialSetNumbers": [1, 2, 3],
                "restStartedBetween": 0,
                "restStartedAfterLastIfPlannedRemain": True,
                "setStartedPerBackfill": False,
            },
        },
    )

    dump(
        "04-nl-logging.event.telugu-60x8.json",
        json.loads((G / "04-nl-logging.event.telugu-60x8.json").read_text(encoding="utf-8"))
        | {
            "layer": "pr1_validator",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentExerciseName": "Bench Press",
                "units": "kg",
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 800,
                "overlappingVad": False,
                "completedSets": [],
            },
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCompleted",
                "canonicalExercise": "Bench Press",
                "weightKg": 60,
                "reps": 8,
                "storageLanguage": "en",
                "mustNotContain": ["setNumber", "REST_STARTED"],
            },
        },
    )

    dump(
        "05-extract.event.last-week-as-set-completed-rejected.json",
        {
            "id": "05-extract.event.last-week-as-set-completed-rejected",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "05-structured-event-extraction",
            "stage": "event",
            "utterance": "Last week I did 55.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "intent": "question",
                "historical": {"previousSessionsForExercise": []},
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 700,
                "overlappingVad": False,
            },
            "input": cand(
                "USER_QUESTION",
                {
                    "questionOriginal": "Last week I did 55?",
                    "topic": "history",
                },
                fieldConfidence={"questionOriginal": 0.9},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "UserQuestion",
                "commitSet": False,
                "notFactType": "SetCompleted",
                "notInventHistory": True,
            },
        },
    )

    dump(
        "05-extract.event.unknown-type-dropped.json",
        json.loads(
            (G / "05-extract.event.unknown-type-dropped.json").read_text(encoding="utf-8")
        )
        | {
            "layer": "pr1_validator",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 300,
                "overlappingVad": False,
            },
        },
    )

    dump(
        "05-extract.event.reps-as-string-ten-fails-schema.json",
        json.loads(
            (G / "05-extract.event.reps-as-string-ten-fails-schema.json").read_text(
                encoding="utf-8"
            )
        )
        | {
            "layer": "pr0_schema",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
            },
        },
    )

    dump(
        "01-lifecycle.event.session-init-then-workout-started.json",
        json.loads(
            (G / "01-lifecycle.event.session-init-then-workout-started.json").read_text(
                encoding="utf-8"
            )
        )
        | {"pr": ["0", "2"], "layer": "pr2_state_machine"},
    )

    dump(
        "07-correction.event.no-8-one-row.json",
        {
            "id": "07-correction.event.no-8-one-row",
            "schemaVersion": 1,
            "pr": ["0", "1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "No, 8.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": 60,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [
                    {
                        "setId": SET1,
                        "setNumber": 1,
                        "reps": 10,
                        "weightKg": 60,
                        "occurredAt": T_NOW,
                        "exerciseId": BENCH,
                    }
                ],
                "lastSet": {
                    "setId": SET1,
                    "setNumber": 1,
                    "reps": 10,
                    "weightKg": 60,
                },
            },
            "input": cand(
                "SET_CORRECTED",
                {"reps": 8, "target": "last"},
                fieldConfidence={"reps": 0.94},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCorrected",
                "setId": SET1,
                "reps": 8,
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "previous": {"setId": SET1, "reps": 10, "weightKg": 60},
            },
        },
    )

    dump(
        "07-correction.event.that-was-second-set.json",
        {
            "id": "07-correction.event.that-was-second-set",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "No, that was the second set.",
            "notes": "Target resolution uses existing completedSets. Validator does not assign official setNumber; it selects setId for slot 'second'. Last uttered 8 was mis-attributed as a new set.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 700,
                "overlappingVad": False,
                "completedSets": [
                    {
                        "setId": SET1,
                        "setNumber": 1,
                        "reps": 12,
                        "weightKg": 40,
                        "occurredAt": "2026-08-20T15:59:00Z",
                        "exerciseId": BENCH,
                    },
                    {
                        "setId": SET2,
                        "setNumber": 2,
                        "reps": 10,
                        "weightKg": 40,
                        "occurredAt": "2026-08-20T15:59:40Z",
                        "exerciseId": BENCH,
                    },
                ],
                "lastAttemptedLog": {"reps": 8, "wouldCreateNewSet": True},
            },
            "input": cand(
                "SET_CORRECTED",
                {"reps": 8, "target": "second"},
                setNumberHint="second",
                fieldConfidence={"reps": 0.9, "target": 0.9},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCorrected",
                "setId": SET2,
                "resolvedFromContext": "completedSets.setNumber==2",
                "reps": 8,
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "previous": {"setId": SET2, "reps": 10, "weightKg": 40},
                "mustNotContain": ["officialSetNumberAssignment"],
            },
        },
    )

    dump(
        "07-correction.event.actually-9-after-no-8.json",
        {
            "id": "07-correction.event.actually-9-after-no-8",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "Actually make that 9.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 500,
                "overlappingVad": False,
                "completedSets": [
                    {
                        "setId": SET1,
                        "setNumber": 1,
                        "reps": 8,
                        "weightKg": 60,
                        "occurredAt": T_NOW,
                        "exerciseId": BENCH,
                    }
                ],
                "recentCorrections": [{"setId": SET1, "fields": ["reps"], "at": T_NOW}],
                "lastSet": {"setId": SET1, "reps": 8, "weightKg": 60},
            },
            "input": cand(
                "SET_CORRECTED",
                {"reps": 9, "target": "last"},
                fieldConfidence={"reps": 0.93},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCorrected",
                "setId": SET1,
                "reps": 9,
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "previous": {"setId": SET1, "reps": 8, "weightKg": 60},
            },
        },
    )

    dump(
        "07-correction.event.previous-exercise-within-120s.json",
        {
            "id": "07-correction.event.previous-exercise-within-120s",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "That was 9.",
            "given": {
                "sessionState": "EXERCISE_ACTIVE",
                "currentExerciseId": INCLINE,
                "currentExerciseName": "Incline Press",
                "currentExerciseStartedAt": T_NOW,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [],
                "conversationalTopic": "bench",
                "previousExerciseLastSet": {
                    "setId": SET1,
                    "setNumber": 1,
                    "reps": 10,
                    "weightKg": 60,
                    "occurredAt": T_30S,
                    "exerciseId": BENCH,
                },
            },
            "input": cand(
                "SET_CORRECTED",
                {"reps": 9, "target": "last"},
                fieldConfidence={"reps": 0.9},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCorrected",
                "setId": SET1,
                "reps": 9,
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "previous": {"setId": SET1, "reps": 10, "weightKg": 60},
                "rule": "previous_exercise_within_120s",
            },
        },
    )

    dump(
        "07-correction.event.previous-exercise-ask-when-ambiguous.json",
        {
            "id": "07-correction.event.previous-exercise-ask-when-ambiguous",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "That was 9.",
            "given": {
                "sessionState": "EXERCISE_ACTIVE",
                "currentExerciseId": INCLINE,
                "currentExerciseName": "Incline Press",
                "currentExerciseStartedAt": T_NOW,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [],
                "conversationalTopic": "incline",
                "previousExerciseLastSet": {
                    "setId": SET1,
                    "setNumber": 1,
                    "reps": 10,
                    "weightKg": 60,
                    "occurredAt": T_180S,
                    "exerciseId": BENCH,
                },
            },
            "input": cand(
                "SET_CORRECTED",
                {"reps": 9, "target": "last"},
                fieldConfidence={"reps": 0.8},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "clarify",
                "promptContains": "bench or incline",
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "acceptFactCount": 0,
                "doNotGuess": True,
            },
        },
    )

    dump(
        "07-correction.multilingual.kaadu-8.json",
        {
            "id": "07-correction.multilingual.kaadu-8",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "Kaadu, 8.",
            "llm_required": True,
            "notes": "Telugu kādu (కాదు) = no. Same as English 'No, 8.' Product multilingual correction; storage remains canonical.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "currentWeightKg": 60,
                "primaryLanguage": "te",
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 500,
                "overlappingVad": False,
                "completedSets": [
                    {
                        "setId": SET1,
                        "setNumber": 1,
                        "reps": 10,
                        "weightKg": 60,
                        "occurredAt": T_NOW,
                        "exerciseId": BENCH,
                    }
                ],
                "lastSet": {"setId": SET1, "reps": 10, "weightKg": 60},
            },
            "input": cand(
                "SET_CORRECTED",
                {"reps": 8, "target": "last"},
                fieldConfidence={"reps": 0.9},
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetCorrected",
                "setId": SET1,
                "reps": 8,
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "previous": {"setId": SET1, "reps": 10, "weightKg": 60},
                "storageLanguage": "en",
            },
        },
    )

    dump(
        "07-correction.event.scratch-that-deleted.json",
        {
            "id": "07-correction.event.scratch-that-deleted",
            "schemaVersion": 1,
            "pr": ["1"],
            "layer": "pr1_validator",
            "contract": "07-correction-reconciliation",
            "stage": "event",
            "utterance": "Scratch that.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "audioRoute": "headset",
                "talkerCount": 1,
                "sttDurationMs": 400,
                "overlappingVad": False,
                "completedSets": [
                    {
                        "setId": SET1,
                        "setNumber": 1,
                        "reps": 10,
                        "weightKg": 60,
                        "occurredAt": T_NOW,
                        "exerciseId": BENCH,
                    }
                ],
                "lastSet": {"setId": SET1, "reps": 10, "weightKg": 60},
            },
            "input": cand(
                "SET_DELETED",
                {"reason": "scratch that"},
                fieldConfidence={"reason": 0.9},
                overallConfidence=0.9,
            ),
            "expect": {
                "schemaValid": True,
                "decision": "accept",
                "factType": "SetDeleted",
                "setId": SET1,
                "newSetRow": False,
                "notFactType": "SetCompleted",
                "previous": {"setId": SET1, "reps": 10, "weightKg": 60},
                "replacementSetCompleted": False,
            },
        },
    )

    dump(
        "17-plans.event.skip-this.json",
        json.loads((G / "17-plans.event.skip-this.json").read_text(encoding="utf-8"))
        | {
            "layer": "pr2_projector",
            "pr": ["0", "2"],
            "notes": "PR 1 may accept ValidatedFact.Command(SKIP) only. fold/EXERCISE_COMPLETE is PR 2. This golden is the PR 2 layer.",
            "given": {
                "sessionState": "RESTING",
                "currentExerciseId": BENCH,
                "workoutExerciseId": "55555555-5555-4555-8555-555555555555",
            },
        },
    )


if __name__ == "__main__":
    main()
