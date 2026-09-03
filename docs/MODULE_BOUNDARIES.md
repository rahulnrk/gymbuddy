# Repository structure and why each boundary exists

PR 0–2 created the modules, validator, engine, and projector. PR 3 implements Room-shaped EventLog persistence. It does not implement Gym Mode UI or vendors.

```
gymbuddy/
  PRODUCT.md                 product law
  AGENTS.md                  binding agent rules
  README.md
  settings.gradle.kts        :domain + :persist only
  domain/                    portable Kotlin kernel (no Android SDK)
  persist/                   Room-shaped SQLite EventLog (PR 3)
  app/                       Android UI + vendor adapters. NO sources in PR 0.
  docs/living-spec/          contracts, events, schemas, state machine, safety, voice, testing
  tests/golden/              language-agnostic JSON fixtures
  scripts/check_living_spec.py
```

## Modules

| Module | Contains | Must not contain | Why the boundary exists |
|---|---|---|---|
| **:domain** | Event contracts, CandidateEvent, WorkoutEvent, FourContextAssembler, epistemic facts, transition table, schema gate, validator, EventLog **port**, voice/AI/safety **ports** | Android SDK, Room, vendor STT/TTS/LLM SDKs, Compose, dashboard, SQL | Kernel must be portable and testable without a phone. Vendors will change; the engine must not. |
| **event contracts** (`domain/.../events`, `docs/living-spec/events`, `schemas/`) | Frozen JSON + Kotlin types | Table mutations | LLM output is typed. Arbitrary JSON cannot become SQL. |
| **workout state** (`domain/.../state`) | Transition contract only in PR 0 | Side effects, TTS, DB writes | PR 2 implements the machine against this table. |
| **application services** | None in PR 0. PR 2 session lifecycle service lives in `:domain` | UI click handlers that skip events | Lifecycle is engine, not HUD. |
| **:persist** | Room `event_log`, `RoomEventLog`, version-1 schema, fixture user/workout | NLU, LLM, UI, CandidateValidator, WorkoutEngine decisions | SQL is not allowed to interpret speech. Only committed `WorkoutEvent` is stored. |
| **voice ports** (`domain/.../voice`) | Clock/VAD/wake-word/STT/TTS/LID/AudioFocus/`TopicScope` ports, `SessionPrediction`, `WindowScheduler`, VoiceTurnMachine, listen gate | Vendor SDKs, Android audio APIs, WorkoutEngine, CandidateValidator, `voice.fake` types | Production constructors take ports, not fakes. Fakes live in `voice/fake/` for tests only. **Wake-word / listen gate dormant for v1:** phone in pocket or locker, only usable mic is earbuds, ordinary Bluetooth open-mic drops music to call quality. Types stay; they return if LE Audio earbuds become common. Coach opens windows. Live user entry is emergency. |
| **AI/provider ports** (`domain/.../ai`) | LlmProvider.extractCandidates → CandidateEventEnvelope | `json: Any`, chat completions API that writes DB | Extraction is schema-constrained. |
| **extract** (`domain/.../extract`) | NaturalLanguageExtractor, rules-first intent, schema fake | WorkoutEngine, EventLog, Room, real LLM vendor | Speech → CandidateEventEnvelope only. |
| **loop** (`domain/.../loop`) | CompanionLoop, ShouldSpeak, Recommend, CoachingPhraser | Android, Compose, vendor SDKs, Room DAOs | Headless companion. Application owns speak vs silence. |
| **safety** (`domain/.../safety` + `docs/living-spec/safety`) | Decision table, `DefaultSafetyClassifier`, `SafetyFirstTurn` | LLM override, diagnosis, WorkoutEngine, Room, UI | Product §34. Runs on raw transcript before extract. |
| **test fixtures** (`tests/golden`, `domain/src/test`) | Named goldens, schema tests | Live vendor calls in CI | Agents cannot mark implemented with empty files. |
| **:app** | Android application: Gym Mode HUD (`GymHudBinder` + Compose screen), `AndroidEventLog`, platform audio diagnostic | Dashboard, `TodayScreen`, chat thread, NavHost, STT/TTS/LLM vendors | HUD is PR 10. Audio adapters live here, never in `:domain`. Dashboard is PR 18. Caption thread is PR 11. |

## Data flow (enforced)

```
:app audio adapters (not in PR 9)
  → CompanionLoop (headless)
    → FourContextAssembler.assemble (read-only)
    → SafetyFirstTurn / classify(raw)
    → NaturalLanguageExtractor → CandidateEventEnvelope
    → CandidateValidator → ValidationDecision<ValidatedFact>
    → WorkoutEngine → EventLog.append (:persist implements; projector folds)
    → FourContextAssembler (authoritative snapshot)
    → recommend() (talk-only)
    → shouldSpeak() (application-owned; silence is intentional)
    → optional FakeTts
    → persist COACHING_RESPONSE (silence still a row)
```

No arrow from LLM to Room. The LLM does not decide whether to speak.
