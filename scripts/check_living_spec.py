#!/usr/bin/env python3
"""PR 0 living-spec gate. PR 1 goldens must be executable, not a file count."""
from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = ROOT / "docs" / "living-spec" / "contracts"
SCHEMAS = ROOT / "docs" / "living-spec" / "schemas" / "v1"
GOLDENS = ROOT / "tests" / "golden"
VALIDATION_KT = (
    ROOT / "domain" / "src" / "main" / "kotlin" / "com" / "gymbuddy" / "domain" / "validation"
)
CONTEXT_KT = (
    ROOT / "domain" / "src" / "main" / "kotlin" / "com" / "gymbuddy" / "domain" / "context"
)
VOICE_KT = (
    ROOT / "domain" / "src" / "main" / "kotlin" / "com" / "gymbuddy" / "domain" / "voice"
)
SAFETY_KT = (
    ROOT / "domain" / "src" / "main" / "kotlin" / "com" / "gymbuddy" / "domain" / "safety"
)
EXTRACT_KT = (
    ROOT / "domain" / "src" / "main" / "kotlin" / "com" / "gymbuddy" / "domain" / "extract"
)
LOOP_KT = (
    ROOT / "domain" / "src" / "main" / "kotlin" / "com" / "gymbuddy" / "domain" / "loop"
)
DOMAIN_BUILD = ROOT / "domain" / "build.gradle.kts"

REQUIRED_CONTRACTS = [
    "01-gym-mode-session-lifecycle.md",
    "02-continuous-interaction-loop.md",
    "03-voice-pipeline.md",
    "04-conversational-workout-parsing.md",
    "05-structured-event-extraction.md",
    "06-deterministic-workout-state-machine.md",
    "07-correction-reconciliation.md",
    "08-four-context-assembly.md",
    "09-coaching-response-generation.md",
    "10-local-first-session-reliability.md",
    "11-safety-layer.md",
    "12-multilingual-normalized-storage.md",
    "13-authentication.md",
    "14-onboarding.md",
    "15-profile.md",
    "16-initial-workout-generation.md",
    "17-workout-plans-and-editing.md",
    "18-exercise-bank-and-detail.md",
    "19-substitution-engine.md",
    "20-workout-history.md",
    "21-progress-basics-and-insights.md",
    "22-muscle-map-and-recovery.md",
    "23-automatic-notes-and-memory.md",
    "24-workout-completion-and-post-workout.md",
    "25-conversation-ui.md",
    "26-dashboard-todays-workout.md",
    "27-music-coexistence-audio-focus.md",
    "28-gym-mode-qa.md",
]

REQUIRED_SCHEMAS = [
    "candidate-event.schema.json",
    "workout-event.schema.json",
    "workout-context.schema.json",
    "historical-context.schema.json",
    "conversational-context.schema.json",
    "user-context.schema.json",
    "epistemic-fact.schema.json",
    "set-completed.schema.json",
    "set-corrected.schema.json",
    "exercise-started.schema.json",
    "workout-started.schema.json",
    "workout-paused.schema.json",
    "workout-resumed.schema.json",
    "workout-completed.schema.json",
    "user-question.schema.json",
    "user-feedback.schema.json",
    "user-note.schema.json",
    "safety-concern.schema.json",
]

REQUIRED_PR1_GOLDENS = [
    "03-voice.event.bystander-got-10-no-commit.json",
    "03-voice.event.headset-got-10-commits.json",
    "04-nl-logging.event.got-10-inherit-weight.json",
    "04-nl-logging.event.did-10-no-working-weight-clarify.json",
    "04-nl-logging.event.40-for-10.json",
    "04-nl-logging.event.drop-weight-not-a-set.json",
    "04-nl-logging.event.multi-set-40x12-10-8.json",
    "04-nl-logging.event.telugu-60x8.json",
    "05-extract.event.last-week-as-set-completed-rejected.json",
    "05-extract.event.unknown-type-dropped.json",
    "07-correction.event.no-8-one-row.json",
    "07-correction.event.that-was-second-set.json",
    "07-correction.event.actually-9-after-no-8.json",
    "07-correction.event.previous-exercise-within-120s.json",
    "07-correction.event.previous-exercise-ask-when-ambiguous.json",
    "07-correction.multilingual.kaadu-8.json",
    "07-correction.event.scratch-that-deleted.json",
    "07-correction.event.occupancy-second-set-later-exists.json",
]

REQUIRED_PR4_GOLDENS = [
    "08-context.event.empty-history-no-invented-weight.json",
    "08-context.event.known-thursday-60x10-citable.json",
    "08-context.event.estimate-requires-two-rows.json",
    "08-context.event.uncertain-must-check-db.json",
    "08-context.offline.assemble-from-local.json",
]

REQUIRED_PR5_GOLDENS = [
    "03-voice.voice.barge-in-lt-200ms.json",
    "27-music.event.duck-before-tts.json",
    "27-music.event.restore-after-barge-in.json",
    "27-music.event.unsupported-still-logs.json",
    "27-music.event.engine-has-no-os-audio-import.json",
]

REQUIRED_PR9_GOLDENS = [
    "02-loop.event.gold-scene-got-10-plus-one-must-speak.json",
    "02-loop.event.duplicate-got-10-same-hash-10s-one-row.json",
    "09-coaching.voice.same-reps-must-not-speak.json",
    "09-coaching.voice.plus-one-rep-must-speak.json",
    "09-coaching.voice.set-active-must-not-speak.json",
    "09-coaching.voice.question-during-set-deferred.json",
    "09-coaching.event.silence-row-always-persisted.json",
    "09-coaching.event.empty-history-no-last-thursday.json",
    "09-coaching.event.recommend-talk-only-no-plan-write.json",
    "09-coaching.voice.banned-ivr-strings.json",
]

REQUIRED_PR6_GOLDENS = [
    "11-safety.event.chest-pain-critical.json",
    "11-safety.event.chest-fly-occupied-not-critical.json",
    "11-safety.event.got-10-shoulder-weird-commit-set-and-note.json",
    "11-safety.event.telugu-shoulder-medium-advise.json",
    "11-safety.event.why-shoulder-question-no-safety-concern.json",
    "11-safety.voice.no-diagnosis-no-push-through.json",
]

PR1_FORBIDDEN_EXPECT_KEYS = {
    "setNumbers",
    "officialSetNumbers",
    "restStartedBetween",
    "restStartedAfterLastIfPlannedRemain",
    "REST_STARTED",
    "REST_ENDED",
    "SET_STARTED",
    "foldRestores",
}

VALID_PR_TAGS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "16", "18"}

FORBIDDEN_VALIDATION_IMPORTS = (
    "androidx.room",
    "java.sql",
    "android.database",
    "com.gymbuddy.domain.persist",
    "com.gymbuddy.domain.ai",
    "com.gymbuddy.domain.voice",
    "androidx.compose",
)


NAMED_CASE_RE = re.compile(
    r"\b(\d{2}-[a-z-]+\.(?:event|state|voice|ui|db|offline|multilingual|correction)\.[a-z0-9-]+)"
)
VALID_STATUSES = ("proposed", "specified", "implementing", "implemented", "drifted")


def contract_status(text: str) -> tuple[str | None, str | None]:
    """Return (frontmatter_status, body_status). Either may be None."""
    fm = re.search(r"(?m)^status:\s*(\S+)", text)
    bd = re.search(r"(?m)^\*\*Status:\*\*\s*(\S+)", text)
    return (fm.group(1).strip() if fm else None, bd.group(1).strip() if bd else None)


def named_goldens(text: str) -> list[str]:
    """Test-case ids named in the contract's ### Tests section."""
    if "### Tests" not in text:
        return []
    return sorted(set(NAMED_CASE_RE.findall(text.split("### Tests")[-1])))


def check_status_evidence(name: str, text: str, present: set[str]) -> list[str]:
    """Status must agree with the evidence actually on disk.

    A manually maintained field is not the source of truth. The rule is:
      implemented  => every named golden exists (AGENTS.md forbidden #14)
      specified    => no named golden exists yet (otherwise it is understated)
      frontmatter  == body, always
    """
    out: list[str] = []
    fm, bd = contract_status(text)
    if fm is None:
        return [f"{name} missing frontmatter 'status:'"]
    if bd is None:
        return [f"{name} missing body '**Status:**'"]
    if fm not in VALID_STATUSES:
        out.append(f"{name} invalid status {fm!r}")
    if fm != bd:
        out.append(f"{name} status drift: frontmatter={fm!r} but body={bd!r}")
    if fm == "drifted":
        out.append(f"{name} is marked drifted; repair before further feature work")

    ids = named_goldens(text)
    missing = [i for i in ids if i not in present]
    have = [i for i in ids if i in present]

    if fm == "implemented" and missing:
        out.append(
            f"{name} claims implemented but {len(missing)} named golden(s) are absent: "
            + ", ".join(missing[:4])
            + ("…" if len(missing) > 4 else "")
        )
    if fm == "specified" and have:
        out.append(
            f"{name} claims specified but {len(have)} named golden(s) already exist "
            f"({', '.join(have[:3])}); use 'implementing'"
        )
    return out


def load_golden(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    errors: list[str] = []
    present_goldens = {p.stem for p in GOLDENS.glob("*.json")}

    for name in REQUIRED_CONTRACTS:
        path = CONTRACTS / name
        if not path.is_file():
            errors.append(f"missing contract {name}")
            continue
        text = path.read_text(encoding="utf-8")
        if name == "26-dashboard-todays-workout.md" and "Status:** implemented" in text:
            errors.append("dashboard contract must not be implemented in PR 0")
        errors.extend(check_status_evidence(name, text, present_goldens))
        for stage in (
            "User behavior",
            "System state",
            "Voice behavior",
            "Structured event",
            "Database change",
            "UI state",
            "Error",
            "Tests",
        ):
            if stage.lower() not in text.lower():
                errors.append(f"{name} missing stage '{stage}'")

    for name in REQUIRED_SCHEMAS:
        path = SCHEMAS / name
        if not path.is_file():
            errors.append(f"missing schema {name}")
        else:
            json.loads(path.read_text(encoding="utf-8"))

    goldens = list(GOLDENS.glob("*.json"))
    ids: list[str] = []
    pr1_stats = {
        "executable": 0,
        "invalid": 0,
        "missing_inputs": 0,
        "missing_context": 0,
        "missing_expectations": 0,
    }

    seen_files: set[str] = set()
    for path in goldens:
        try:
            data = load_golden(path)
        except json.JSONDecodeError as e:
            errors.append(f"malformed JSON {path.name}: {e}")
            pr1_stats["invalid"] += 1
            continue
        gid = data.get("id")
        if not gid:
            errors.append(f"{path.name} missing id")
        else:
            ids.append(str(gid))
            if path.stem != gid:
                errors.append(f"{path.name} id {gid!r} must match filename stem")
        pr = data.get("pr")
        if not isinstance(pr, list) or not pr:
            errors.append(f"{path.name} missing pr list")
            pr = []
        for tag in pr:
            if str(tag) not in VALID_PR_TAGS:
                errors.append(f"{path.name} invalid pr tag {tag!r}")
        if "1" in pr and "2" in pr:
            errors.append(f"{path.name} must not be tagged PR 1 and PR 2; split layers")

        contract = data.get("contract")
        if contract:
            cfile = CONTRACTS / f"{contract}.md"
            if not cfile.is_file():
                errors.append(f"{path.name} contract {contract} has no file")

        if "1" not in [str(x) for x in pr]:
            continue

        # PR 1 executable structural rules
        expect = data.get("expect")
        given = data.get("given")
        inp = data.get("input")
        ok = True
        if not isinstance(inp, dict):
            errors.append(f"PR1 {path.name} missing input")
            pr1_stats["missing_inputs"] += 1
            ok = False
        else:
            cands = inp.get("candidates")
            if not isinstance(cands, list) or not cands:
                errors.append(f"PR1 {path.name} input.candidates empty")
                pr1_stats["missing_inputs"] += 1
                ok = False
        if not isinstance(given, dict) or not given:
            errors.append(f"PR1 {path.name} missing given context")
            pr1_stats["missing_context"] += 1
            ok = False
        if not isinstance(expect, dict) or not expect:
            errors.append(f"PR1 {path.name} missing expect")
            pr1_stats["missing_expectations"] += 1
            ok = False
        else:
            forbidden = PR1_FORBIDDEN_EXPECT_KEYS.intersection(expect.keys())
            if forbidden:
                errors.append(
                    f"PR1 {path.name} expect contains PR 2 keys {sorted(forbidden)}"
                )
                ok = False
        if not data.get("contract"):
            errors.append(f"PR1 {path.name} missing contract")
            ok = False
        if ok:
            pr1_stats["executable"] += 1
        else:
            pr1_stats["invalid"] += 1

    dup = [i for i, n in Counter(ids).items() if n > 1]
    if dup:
        errors.append(f"duplicate golden ids: {dup}")

    for name in REQUIRED_PR1_GOLDENS:
        if not (GOLDENS / name).is_file():
            errors.append(f"missing required PR1 golden {name}")

    for name in REQUIRED_PR4_GOLDENS:
        if not (GOLDENS / name).is_file():
            errors.append(f"missing required PR4 golden {name}")

    for name in REQUIRED_PR5_GOLDENS:
        if not (GOLDENS / name).is_file():
            errors.append(f"missing required PR5 golden {name}")

    for name in REQUIRED_PR6_GOLDENS:
        if not (GOLDENS / name).is_file():
            errors.append(f"missing required PR6 golden {name}")

    for name in REQUIRED_PR9_GOLDENS:
        if not (GOLDENS / name).is_file():
            errors.append(f"missing required PR9 golden {name}")

    # jsonschema on known-good / known-bad
    try:
        from jsonschema import Draft202012Validator

        schema = json.loads((SCHEMAS / "candidate-event.schema.json").read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema)
        got = load_golden(GOLDENS / "04-nl-logging.event.got-10-inherit-weight.json")
        ten = load_golden(GOLDENS / "05-extract.event.reps-as-string-ten-fails-schema.json")
        if list(validator.iter_errors(got["input"])):
            errors.append("got-10 envelope should be schema-valid")
        if not list(validator.iter_errors(ten["input"])):
            errors.append('"ten" as reps must fail candidate schema')
        no8 = load_golden(GOLDENS / "07-correction.event.no-8-one-row.json")
        if list(validator.iter_errors(no8["input"])):
            errors.append("no-8 SET_CORRECTED envelope should be schema-valid")
    except ImportError:
        print("WARN: jsonschema not installed; skipped Draft 2020-12 validation")

    # Architectural: validation package
    if not VALIDATION_KT.is_dir():
        errors.append("missing domain validation package")
    else:
        kt_text = "\n".join(p.read_text(encoding="utf-8") for p in VALIDATION_KT.glob("*.kt"))
        for imp in FORBIDDEN_VALIDATION_IMPORTS:
            if re.search(rf"^import {re.escape(imp)}", kt_text, re.M):
                errors.append(f"validation package imports {imp}")
        if "interface CandidateValidator" not in kt_text:
            errors.append("CandidateValidator interface missing")
        if "class DefaultCandidateValidator" not in kt_text:
            errors.append("PR 1 DefaultCandidateValidator missing")
        if "data class CommittedDraft" in kt_text:
            errors.append("CommittedDraft JsonObject must not exist")
        if "sealed class ValidatedFact" not in kt_text:
            errors.append("ValidatedFact sealed type missing")
        if "var sessionState" in kt_text:
            errors.append("validation package mutates sessionState")

    if not CONTEXT_KT.is_dir():
        errors.append("missing domain context package")
    else:
        ctx_text = "\n".join(p.read_text(encoding="utf-8") for p in CONTEXT_KT.glob("*.kt"))
        if "object FourContextAssembler" not in ctx_text:
            errors.append("FourContextAssembler missing")
        if "typealias FactRef" not in ctx_text:
            errors.append("FactRef alias missing")
        for bad in (
            "androidx.room",
            "SttProvider",
            "TtsProvider",
            "LlmProvider",
            "VadProvider",
            "class WorkoutEngine",
            "DefaultCandidateValidator",
            "interface EventLog",
        ):
            if bad in ctx_text:
                errors.append(f"context package must not contain {bad}")
        assembly_doc = ROOT / "docs" / "living-spec" / "context" / "ASSEMBLY.md"
        if not assembly_doc.is_file():
            errors.append("missing docs/living-spec/context/ASSEMBLY.md")
        else:
            ad = assembly_doc.read_text(encoding="utf-8").lower()
            if "does not interpret natural language" not in ad:
                errors.append("ASSEMBLY.md must state assembler does not interpret natural language")

    if not VOICE_KT.is_dir():
        errors.append("missing domain voice package")
    else:
        voice_files = list(VOICE_KT.rglob("*.kt"))
        voice_text = "\n".join(p.read_text(encoding="utf-8") for p in voice_files)
        if "class VoiceTurnMachine" not in voice_text:
            errors.append("VoiceTurnMachine missing")
        if "class FakeClock" not in voice_text:
            errors.append("FakeClock missing")
        if "interface VadProvider" not in voice_text:
            errors.append("VadProvider missing")
        for bad in (
            "android.media",
            "AudioManager",
            "AudioTrack",
            "MediaPlayer",
            "openai",
            "androidx.compose",
            "WorkoutEngine",
            "DefaultCandidateValidator",
        ):
            if bad in voice_text:
                errors.append(f"voice package must not contain {bad}")
        ports = ROOT / "docs" / "living-spec" / "voice" / "PORTS.md"
        if ports.is_file():
            pt = ports.read_text(encoding="utf-8").lower()
            if "unrestricted" not in pt and "do not constrain" not in pt:
                errors.append("PORTS.md must preserve unrestricted conversation")

    if not SAFETY_KT.is_dir():
        errors.append("missing domain safety package")
    else:
        safety_text = "\n".join(p.read_text(encoding="utf-8") for p in SAFETY_KT.glob("*.kt"))
        if "class DefaultSafetyClassifier" not in safety_text:
            errors.append("DefaultSafetyClassifier missing")
        if "interface SafetyClassifier" not in safety_text:
            errors.append("SafetyClassifier interface missing")
        for bad in (
            "WorkoutEngine",
            "EventLog",
            "LlmProvider",
            "SttProvider",
            "androidx.compose",
            "androidx.room",
        ):
            if bad in safety_text:
                errors.append(f"safety package must not contain {bad}")
        dt = ROOT / "docs" / "living-spec" / "safety" / "DECISION_TABLE.md"
        if dt.is_file():
            dtt = dt.read_text(encoding="utf-8").lower()
            if "deterministic" not in dtt:
                errors.append("DECISION_TABLE.md must state classification is deterministic")
        else:
            errors.append("missing DECISION_TABLE.md")
        if not (ROOT / "docs" / "living-spec" / "SAFETY.md").is_file():
            errors.append("missing docs/living-spec/SAFETY.md")

    if not EXTRACT_KT.is_dir():
        errors.append("missing domain extract package")
    else:
        extract_text = "\n".join(p.read_text(encoding="utf-8") for p in EXTRACT_KT.glob("*.kt"))
        if "class NaturalLanguageExtractor" not in extract_text:
            errors.append("NaturalLanguageExtractor missing")
        if "SafetyFirstTurn" not in extract_text:
            errors.append("extractor must call SafetyFirstTurn")
        for bad in ("WorkoutEngine", "EventLog", "WorkoutEvent(", "androidx.room", "openai"):
            if bad in extract_text:
                errors.append(f"extract package must not contain {bad}")

    if not LOOP_KT.is_dir():
        errors.append("missing domain loop package")
    else:
        loop_text = "\n".join(p.read_text(encoding="utf-8") for p in LOOP_KT.glob("*.kt"))
        if "class CompanionLoop" not in loop_text:
            errors.append("CompanionLoop missing")
        if "object ShouldSpeak" not in loop_text:
            errors.append("ShouldSpeak missing")
        if "fun shouldSpeak" not in loop_text:
            errors.append("shouldSpeak() missing")
        if "object Recommend" not in loop_text:
            errors.append("Recommend missing")
        if "COACHING_RESPONSE" not in loop_text:
            errors.append("loop must persist COACHING_RESPONSE")
        speak_src = (LOOP_KT / "SpeakPolicy.kt").read_text(encoding="utf-8") if (LOOP_KT / "SpeakPolicy.kt").is_file() else ""
        if "EventLog" in speak_src or "FakeTts" in speak_src:
            errors.append("shouldSpeak must be pure (no EventLog / TTS)")
        for bad in ("androidx.compose", "openai", "AudioManager", "ElevenLabs", "azure.cognitiveservices"):
            if bad in loop_text:
                errors.append(f"loop package must not contain {bad}")

    build = DOMAIN_BUILD.read_text(encoding="utf-8") if DOMAIN_BUILD.is_file() else ""
    if re.search(r"room|sqlite-jdbc|androidx\.room", build, re.I):
        errors.append(":domain must not depend on Room/SQL")

    app_kt = list((ROOT / "app").rglob("*.kt")) if (ROOT / "app").exists() else []
    if app_kt:
        errors.append("app/ contains Kotlin; PR 0 must not start UI")

    persist_main = ROOT / "persist" / "src" / "main" / "kotlin"
    persist_kt = list(persist_main.rglob("*.kt")) if persist_main.is_dir() else []
    if not persist_kt:
        errors.append("PR 3 persist EventLog implementation missing")
    else:
        persist_text = "\n".join(p.read_text(encoding="utf-8") for p in persist_kt)
        for req in (
            "class RoomEventLog",
            "class WorkoutEventEntity",
            "object WorkoutEventCodec",
            "class GymBuddyDatabase",
        ):
            if req not in persist_text:
                errors.append(f"persist missing {req}")
        for bad in (
            "class DefaultCandidateValidator",
            "interface CandidateValidator",
            "LlmProvider",
            "SttProvider",
            "TtsProvider",
            "VadProvider",
            "androidx.compose",
            "WorkoutEngine",
            "WorkoutProjector",
        ):
            if bad in persist_text:
                errors.append(f"persist must not contain {bad}")
        if "never ValidatedFact" not in persist_text:
            errors.append("persist must state that ValidatedFact is never authoritative storage")

    persist_build = ROOT / "persist" / "build.gradle.kts"
    if persist_build.is_file():
        pb = persist_build.read_text(encoding="utf-8")
        if 'project(":domain")' not in pb.replace("'", '"'):
            errors.append(":persist must depend on :domain")
    if re.search(r'project\(":persist"\)', build):
        errors.append(":domain must not depend on :persist")

    natural = ROOT / "docs" / "living-spec" / "conversation" / "NATURAL_SPEECH.md"
    if not natural.is_file():
        errors.append("missing docs/living-spec/conversation/NATURAL_SPEECH.md")
    else:
        nt = natural.read_text(encoding="utf-8").lower()
        if "first-class" not in nt and "unrestricted" not in nt:
            errors.append("NATURAL_SPEECH.md must preserve unconstrained conversation")
        if "command" not in nt:
            errors.append("NATURAL_SPEECH.md must reject command-only interaction")

    domain_main = ROOT / "domain" / "src" / "main" / "kotlin"
    if domain_main.is_dir():
        for p in domain_main.rglob("*.kt"):
            text = p.read_text(encoding="utf-8")
            if "androidx.room" in text:
                errors.append(f"domain contains Room: {p.relative_to(ROOT)}")
            if "org.sqlite" in text:
                errors.append(f"domain contains sqlite: {p.relative_to(ROOT)}")
            for line in text.splitlines():
                if line.strip().startswith("import android.") or line.strip().startswith("import java.sql"):
                    errors.append(f"domain forbidden import in {p.relative_to(ROOT)}: {line.strip()}")

    print("PR1 goldens:")
    print(f"  executable: {pr1_stats['executable']}")
    print(f"  invalid: {pr1_stats['invalid']}")
    print(f"  missing inputs: {pr1_stats['missing_inputs']}")
    print(f"  missing context: {pr1_stats['missing_context']}")
    print(f"  missing expectations: {pr1_stats['missing_expectations']}")

    if errors:
        for e in errors:
            print("FAIL:", e)
        raise SystemExit(1)
    if pr1_stats["executable"] < 15:
        print("FAIL: expected ≥15 executable PR 1 goldens")
        raise SystemExit(1)
    print("PR0 living-spec check passed")
    print(f"  contracts={len(REQUIRED_CONTRACTS)}")
    print(f"  schemas={len(REQUIRED_SCHEMAS)}")
    print(f"  goldens={len(goldens)}")
    print(f"  pr1_executable={pr1_stats['executable']}")


if __name__ == "__main__":
    main()
