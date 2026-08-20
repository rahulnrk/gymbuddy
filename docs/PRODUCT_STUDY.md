# GymBuddy — End-to-End Product Study

**Analysis date:** 2026-08-20
**Scope:** `PRODUCT.md` (59 sections), `AGENTS.md`, `docs/living-spec/**` (3,554-line TECHNICAL_SPEC + 28 contracts + frozen schemas), all Kotlin in `:domain` and `:persist` (~5,900 lines), 51 golden fixtures, build/test state.

> This document is an outside-in study, not part of the living spec. It does not change any contract. Where it disagrees with `PRODUCT.md`, `PRODUCT.md` wins.

---

## 1. What the product actually is

GymBuddy is a **voice-first AI gym companion for Android**. The user puts in headphones, starts Spotify, opens the app, puts the phone in their pocket, and trains while an AI that already knows today's plan and last session's numbers talks to them like a trainer.

The identity is deliberately *not* "workout tracker with AI." The distinction is enforced structurally, not just stated:

- The dashboard is contract **26 of 28**, scheduled as **PR 18** — near the end.
- `AGENTS.md` forbidden-item #10 blocks dashboard work until the kernel (PR 0–9) exists.
- `app/` has **zero source files** and isn't even in `settings.gradle.kts` yet.

The single governing question, quoted in `PRODUCT.md` §59:

> *"Does this make the user feel like they have a great trainer beside them, without making them operate another app while they are working out?"*

And the central hypothesis being tested (§52):

> *"Does having a natural AI gym companion make people consistently complete and log better workouts?"*

### The one product law everything derives from

**"The user should operate their workout, not operate the app."** (§2)

No command grammar. Ever. `"Got 10."`, `"40 for 10."`, `"First one was 12, second was 10, and this one was 8."`, `"Actually make that 9."`, `"Kaadu, 8."`, `"Nenu 60 kilos tho eight reps chesa."` all have to work through context alone. `AGENTS.md` forbidden-item #11 is literally "Requiring command language."

---

## 2. The kernel loop

Everything in the repo hangs off one seven-step loop (`docs/living-spec/CORE_LOOP.md`):

```
music playing
  → user talks naturally
  → AI understands context (four contexts)
  → structured workout event
  → AI responds naturally (or stays silent)
  → history is updated
  → future coaching becomes better
```

Every one of the 28 feature contracts must declare its **"core-loop role."** Features that don't feed the loop must say so explicitly and are labelled secondary. This is the anti-sprawl mechanism — it's why there's no chat tab, no social feed, no form-check camera.

Outer product loop (§55): `PLAN → START → LISTEN → UNDERSTAND → RECORD → COACH → REMEMBER → ANALYZE → ADAPT → NEXT WORKOUT`. **ADAPT is cut in MVP** (KD24): `recommend()` is a pure function that produces talk-only suggestions; nothing writes next workout's `prescribed_weight_kg`.

### The per-utterance pipeline (fixed order, no exceptions)

```
raw transcript
  → assembleFourContexts()          read-only structured snapshot
  → safety classification            on RAW text, before extraction
  → intent (rules first, then LLM)   multi-label
  → CandidateEvent[]                 frozen JSON schema, LLM's only output
  → CandidateValidator               → ValidatedFact | Clarify | Reject | Drop
  → WorkoutEngine                    → WorkoutEvent (append-only)
  → EventLog.append                  Room / SQLite
  → WorkoutProjector.fold()          → WorkoutSessionSnapshot
  → shouldSpeak()                    deterministic; LLM only phrases
  → optional TTS                     never in SET_ACTIVE except safety ≥ high
```

Eight invariants guard it: no tapping to log; no commit without validation; no citing history absent from the snapshot; silence is persisted; barge-in always wins; dropped network never drops events; safety can veto speech; offline voice logging only if `SttProvider.supportsOffline`.

---

## 3. The central architectural bet

**Deterministic state machine owns workout truth. The LLM interprets language and speaks. Neither can do the other's job.**

`docs/living-spec/boundaries/LLM_VS_APPLICATION.md` turns this into a responsibility table. The critical rows:

| Concern | LLM does | Application does |
|---|---|---|
| Extraction | emits `CandidateEventEnvelope` only, temp ≤ 0.2, frozen schema | schema gate + validator → `ValidatedFact` |
| Set numbering | `setNumberHint` ("first"/"this"/int) only | engine assigns official `setNumber` and `setId` |
| Persistence | **never** | `EventLog.append`, idempotent on `(userId, eventId)` |
| History | must not recall from model memory | `FourContextAssembler` reads rows; empty → stance `none` |
| Session lifecycle | cannot emit `SESSION_*`, `WORKOUT_STARTED`, `SET_STARTED`, `REST_*` | engine only |
| Safety | cannot override severity ≥ high, cannot diagnose | dedicated classifier on raw text before extract |
| Coaching | phrases an *allowed* speech act, paraphrasing only from `citedFacts` | `shouldSpeak()` decides speak vs. silence |

The enforcement is **type-level, not documentation-level** — this is the most interesting engineering decision in the repo:

- `ValidatedFact` is a **sealed class with no branch** for `SET_STARTED`, `REST_*`, `SESSION_*`, or an official `setNumber`. The validator physically cannot express a state transition. `SetCompleted` has `setNumberHint`, not `setNumber`.
- `SetCorrected.setId` must be a `SetId` already present in `FourContexts.completedSets`, and `previous.setId == setId` is a `require()` in the constructor. The validator cannot mint a new set disguised as a correction.
- `WorkoutEvent`'s init block: `require(type != SET_STARTED || source == SYSTEM)`.
- `EpistemicFact`'s init block: `stance=known` requires ≥1 `sourceRowIds`; `stance=estimated` requires ≥2; `stance=none` cannot be `speechAllowed`.
- `FourContexts` is an immutable data class passed by value — reading `sessionState` is allowed, assigning is impossible.

`PR1_VS_PR2.md` states this explicitly: *"`CommittedDraft.payload: JsonObject` could hold `SET_STARTED`, `REST_STARTED`, or `setNumber: 3`. A sealed `ValidatedFact` cannot: those constructors do not exist."*

---

## 4. Module map

```
gymbuddy/
  PRODUCT.md              product law (verbatim master spec)
  AGENTS.md               binding rules; 14 forbidden items; PR gate
  docs/living-spec/       TECHNICAL_SPEC (186 KB) + 28 contracts + frozen schemas v1
  tests/golden/           51 language-agnostic JSON fixtures
  scripts/check_living_spec.py
  domain/                 :domain — portable Kotlin, no Android SDK, no vendors
  persist/                :persist — Room-shaped SQLite event log
  app/                    empty (README only); not a Gradle module yet
```

| Module | Contains | Must not contain |
|---|---|---|
| `:domain` | event contracts, `CandidateValidator`, `WorkoutEngine`, `WorkoutProjector`, `FourContextAssembler`, transition table, safety table, voice/AI/persist **ports**, deterministic fakes | Android SDK, Room, vendor SDKs, Compose, SQL |
| `:persist` | `event_log` entity/DAO, `RoomEventLog`, typed codec, schema v1 + migrator, fixtures | NLU, LLM, UI, validator, engine decisions |
| `:app` | (empty) | dashboard, chat tab, screens until PR 10 |

`:persist` depends on `:domain`. `:domain` depends on nothing. There is **no arrow from LLM to Room** anywhere in the design, and `ArchitecturalBoundaryTest` / `PersistArchitectureTest` / `ContextArchitectureTest` / `VoiceArchitectureTest` assert it in CI.

---

## 5. The event model

Event-sourced. `event_log` is append-only; corrections append `SET_CORRECTED`, never `UPDATE` a log row. `fold(event_log) = readModel`, pure and idempotent.

**EventBase** (every committed event): `eventId` (UUID, idempotency key with `userId`), `sessionId`, `userId`, `occurredAt`, `recordedAt`, `source` (`voice|manual|imported|ai-generated|system`), `schemaVersion=1`, `type`, `payload`, plus optional `transcriptId`, `language`, `confidence`, `sttFinalHash`, `batchId`.

**32 committed event types** in `CommittedEventType`, grouped:

- **Lifecycle (engine only):** `SESSION_INITIALIZED`, `WORKOUT_STARTED`, `SESSION_REHYDRATED`, `WORKOUT_PAUSED`, `WORKOUT_RESUMED`, `WORKOUT_COMPLETED`
- **Exercise/set (engine assigns numbers):** `EXERCISE_STARTED`, `SET_STARTED`, `SET_COMPLETED`, `SET_CORRECTED`, `SET_DELETED`, `REST_STARTED`, `REST_ENDED`, `EXERCISE_SKIPPED`, `PLANNED_SETS_CHANGED`, `SUBSTITUTION_APPLIED`, `EXERCISE_CHANGED` (Planning Mode only), `WEIGHT_CHANGED`, `REPS_CHANGED`
- **Conversation/memory:** `USER_NOTE_CREATED`, `USER_FEEDBACK`, `USER_QUESTION`, `COACHING_RESPONSE`, `SAFETY_CONCERN`, `CLARIFICATION_REQUESTED`, `INTENT_REJECTED`, `MEMORY_UPDATED`
- **Voice/orchestration:** `LANGUAGE_DETECTED`, `BARGE_IN`, `TURN_ENDED`, `AUDIO_FOCUS_CHANGED`
- **Reserved:** `SYNC_ACKNOWLEDGED` (no sync server in MVP)

**12 candidate types** in `CandidateType` — what the LLM may propose. Never the same thing as a committed event. `EXAMPLES.md` shows a `{eventId, type: SET_COMPLETED, setNumber: 7}` object and labels it illegal even though the field names look right.

### Key data-model laws

- Mass stored as `weight` + `weight_unit` + generated `weight_kg`. Never prose in a metric column.
- `source=ai-generated` is **planned** work only; never a claimed completed set.
- `setId` assigned at first `SET_COMPLETED`; corrections reference it forever.
- `workout_sets` unique safety net: `(session_id, workout_exercise_id, set_number) WHERE deleted_at IS NULL`.
- `event_log` unique: `(user_id, event_id)` and `(session_id, sequence)`; load `ORDER BY sequence ASC`.
- Duplicate STT finals dropped on `sttFinalHash` + 10 s `recordedAt` window.

---

## 6. Two orthogonal state machines

`WorkoutSessionState ⊥ VoiceTurnState`. Product §38 lists them in one list; the spec correctly splits them (KD4). `RESTING + USER_SPEAKING` is a legal pair.

### WorkoutSessionState

`IDLE · WORKOUT_STARTING · READY_FOR_EXERCISE · EXERCISE_ACTIVE · SET_ACTIVE · SET_COMPLETED (transient) · RESTING · PAUSED · EXERCISE_COMPLETE · WORKOUT_COMPLETE · ERROR_RECOVERY`

**Session start is a closed two-step sequence** (KD23):

```
IDLE --SESSION_INITIALIZED--> WORKOUT_STARTING --hydrate--> --WORKOUT_STARTED--> READY_FOR_EXERCISE
```

`WORKOUT_STARTED` from `IDLE` is **illegal** and has a dedicated golden. Crash restore is `SESSION_REHYDRATED`, never a second `WORKOUT_STARTED`. Resume is `WORKOUT_RESUMED` from `PAUSED` only, restoring `prePauseState` + `restRemainingSec`.

**`SET_ACTIVE` entry is closed** (KD21) — six numbered routes, all engine-emitted:

| # | From | Trigger | Engine emits |
|---|---|---|---|
| 1 | `EXERCISE_ACTIVE`, 0 sets | greeting ack **or** 8 s elapsed | `SET_STARTED` |
| 2 | `RESTING` | rest timer expiry | `REST_ENDED{timer}` → `SET_STARTED` |
| 3 | `RESTING` | "I'm going." | `REST_ENDED{user}` → `SET_STARTED` |
| 4 | `RESTING` | live `SET_COMPLETED` | `REST_ENDED{logged_through}`, **no** `SET_STARTED` |
| 5 | catch-up batch | ordinals / N≥2 | N × `SET_COMPLETED`, no `SET_STARTED`, no rest between |
| 6 | `PAUSED`, prePause=`SET_ACTIVE` | `WORKOUT_RESUMED` | nothing — restore without a second `SET_STARTED` |

**The overlay law:** `SET_ACTIVE + AI_SPEAKING` is **forbidden** except `SAFETY_CONCERN` severity ≥ high. VAD stays on; TTS does not. Nobody gets encouraged mid-rep.

**Catch-up vs live** (KD22) is the subtlest law. `"First set was 12 at 40, second was 10, and I got 8 on this one."` produces three `SET_COMPLETED` sharing one `batchId`, weight 40 inherited to sets 2 and 3, official numbers 1/2/3, **zero** rest between members, and exactly one `REST_STARTED` after the last **iff** planned sets remain. Same utterance is tested twice — `04-nl-logging.event.multi-set-40x12-10-8` at the validator layer (hints only, no `setNumber`) and `06-state-machine.event.catch-up-40x12-10-8` at the projector layer.

### VoiceTurnState

`SILENT → LISTENING → USER_SPEAKING → PROCESSING → AI_SPEAKING → INTERRUPTED → STOPPED`, with a legal-transition set in `VoiceTurnContract`. `VoiceTurnMachine` enforces it with `require(isLegal(...))` on every move and keeps a `trail` for tests. Barge-in cancels TTS on a `FakeClock` (never wall time), asserted p95 < 200 ms.

---

## 7. Four-context assembly — the anti-hallucination system

This is the machinery behind §33 "never invent workout history," and it's more rigorous than the product spec asked for.

`FourContextAssembler.assemble(AssemblyRequest) → FourContexts` is pure: same snapshot + events + stores → same output. No wall clock, no LLM, no random ids, no Room access.

Four contexts: **workout** (live session snapshot), **historical** (stored rows only), **conversational** (structured records — topic, pending clarification, recent corrections, recent STT hashes — *not* a transcript dump), **user** (goals, equipment, units, language, preferences).

The real innovation is **per-fact epistemic stance** (KD29), attached to every `FactRef`:

| Stance | Meaning | Allowed speech |
|---|---|---|
| `known` | direct row(s) in `sourceRowIds` | assert numbers — "Last Thursday you did 60 for 10." |
| `estimated` | aggregate from **≥2** rows | estimate language only — "averaging around 3 sets" |
| `inferred` | named rule over known rows | state the observation, never physiology |
| `uncertain` | user guess / low confidence | **must query DB this turn** before asserting |
| `none` | no rows | must not cite a number — "I don't have a previous number yet." |

`AGENTS.md` forbidden-item #9: treating `estimated`/`inferred`/`uncertain`/`none` as `known` is a rejectable PR. `COACHING_RESPONSE.citedFacts` must all resolve to a row id or catalog id; a sentence whose cite check fails is **stripped**, not softened.

Two more laws worth noting: current working weight is **never** copied into historical context (so "60 kg today" can't become "you did 60 last time"), and a DB read failure produces `stance=none` — it does **not** fall back to model memory.

---

## 8. Validation and the voice commit route

`DefaultCandidateValidator` is the only gate from language to truth. Pure — no `EventLog`, no Room, no vendors, no state writes.

**The voice-set auto-commit route is closed.** A voice `SET_COMPLETED` auto-commits only if *all* of:

1. `audioRoute == headset` (wired/BT/near-field)
2. `talkerCount == 1`
3. `overlappingVad == false`
4. `sttDurationMs ≥ 250`
5. a current exercise exists
6. `overallConfidence ≥ 0.55` — **unless** the mandatory weight-inherit path applies

Speakerphone → HUD confirm chips, never auto-commit. This is the answer to the bystander problem: your neighbour's "got 10" in a loud gym cannot become your set. Two goldens pin it (`bystander-got-10-no-commit`, `headset-got-10-commits`).

Other closed rules: weight **must** inherit from working weight when unspoken; reps are **never** inherited; a spoken weight differing >20% from working weight without drop/up language triggers clarification; duplicate STT finals drop on hash + 10 s.

**Product consequence worth naming:** the auto-commit rule makes headphones effectively mandatory for the hands-free promise. Speakerphone users get a tap-to-confirm product, which is a materially different experience.

---

## 9. Corrections — the hardest sub-problem

§32 says voice systems will make mistakes, so the system must be *designed around correction*. The contract handles seven distinct cases, and the interesting one is **occupancy**:

Sets 1 (60×10), 2 (60×8), 3 (60×7) all recorded. User: *"That was actually my second set — 9 reps."*

Slot 2 exists **and** a later set exists → the validator **clarifies** ("Should I replace set 2 or keep both?") and emits **zero** facts. Set 2 is not silently overwritten, set 3 is untouched, nothing is renumbered, nothing is appended until the user answers. Because `Clarify` produces no facts, `WorkoutEngine.applyFacts` receives an empty list and physically cannot rewrite set 2 — the safety is structural, not a check someone might forget.

Contrast: if the target slot exists and **no** later set exists, it's an ordinary correction on that `setId`.

Cross-exercise rule: if the current exercise has no sets, a correction may target the previous exercise's last set — but only within **120 s** or while the conversational topic is still on that exercise. Beyond that: clarify, don't guess.

Multilingual correction is frozen: `"Kaadu, 8."` (Telugu *kādu* = "no") is canonically identical to `"No, 8."` — same `setId`, `previous` preserved, `newSetRow=false`. The contract explicitly says: *"Do not change correction semantics to make the test pass."*

---

## 10. Safety layer

A dedicated deterministic layer that runs on the **raw transcript before extraction**, and **cannot be feature-flagged off in production Gym Mode**.

Categories: `sharp_pain`, `injury`, `dizziness`, `faintness`, `chest_pain`, `severe_sob`, `dangerous_condition`, `discomfort_mild`.
Severity: `low | medium | high | critical`. Action: `advise | pause | stop_recommend`.

The decision table is data (`SafetyDecisionTable.rows` in Kotlin), not prose. Highlights:

- "chest pain" / "I feel faint" / "I can't breathe" → **critical**, `stop_recommend`, pause, persist note. Template recommends stopping and seeking professional help. **The LLM cannot override this.**
- "sharp pain in my shoulder" / "I think I tore something" → **high**, pause.
- "My shoulder feels a little weird" → **medium**, advise, note, no pause.
- **"That chest fly machine is occupied."** → not safety. Exercise names containing "chest"/"fly" are excluded unless a pain collocate appears within a 6-token window.
- **"Why do I feel this in my shoulder?"** (no pain words) → not safety; it's a technique question (Feature 28), answered without diagnosis.

**Truth beats caution on the set itself:** *"I got 10 but my chest hurts"* commits the set (10 reps is a fact) **and** fires critical safety and pauses. The event isn't suppressed to make the safety response cleaner.

Fail-open conservative: pain/chest/dizzy/faint/SOB keywords fire even if the LLM says the user is fine. Refusal templates are fixed strings the model may not paraphrase away.

---

## 11. Coaching policy — silence as a first-class feature

KD10: **`shouldSpeak()` is deterministic. The LLM does not decide whether to talk, only how to phrase an act it's already been allowed.**

Precedence, evaluated in order:

1. `safety.severity ≥ high` → **MUST speak** safety. No queue.
2. `SET_ACTIVE` → **MUST NOT speak.** This wins over *every* other MUST-speak. Any matched act is **enqueued** on `conversational.queuedSpeechActs`, a `COACHING_RESPONSE` with `silence: true` is persisted, and **one** queued act is drained on entering `RESTING` / `SET_COMPLETED` / `EXERCISE_COMPLETE`.
3. Otherwise apply MUST speak, then MUST NOT.

**"Useful delta"** is a closed definition, not an LLM judgment: |Δreps| ≥ 1 vs the previous set this session; or ≥ 1 vs last session's corresponding set with `stance=known`; or |Δweight| ≥ 0.5 kg; or first set of this exercise.

**MUST NOT speak** cases include: same reps *and* same weight as the previous set mid-exercise (`no_useful_delta`); a bare ack like "Yeah." with no new fact (`ack_only`); system-only events. Banned strings: *"Command received."*, *"Your set has been logged."*, *"Proceed to the next exercise."*, recovery percentages, any number not in `citedFacts`.

**A `COACHING_RESPONSE` row is written on every turn, including silence** (`silence: boolean` is required). Silence is observable and testable — that's what makes "respect silence" enforceable rather than aspirational.

---

## 12. Multilingual

Conversation language ≠ storage language (§15, KD8). Speak English, Hindi, Telugu, Tamil, Hinglish, Tenglish, or mixed mid-sentence; the UI stays English and the database stores `exerciseId` + `weightKg` + `reps`.

`"Nenu 60 kilos tho eight reps chesa."` → Bench Press, 60 kg, 8 reps. The AI may reply in Telugu — but only citing numbers it actually has.

`USER_QUESTION` carries both `questionOriginal` (preserved) and `questionCanonical` (English, for retrieval). Notes store original text plus an English canonical summary. `exercise_aliases` carries per-language names for STT/NLU resolution. Golden: no prose in a weight column.

Rules-based fallback (LLM down) covers **English last-set patterns only** — "Got 10", "40 for 10", "No, 8", "scratch that". Multi-set ordinals, code-mix, substitution, and questions are all marked `llm_required` and skip the fallback suite. This is honest about what degradation actually means.

---

## 13. Persistence, offline, reliability

**Local-first, single-device.** KD25 (resolved 2026-08-20): **no sync server in MVP.** `sync_status` and `outbox` are reserved and unused. Local SQLite is session truth.

The split guarantee (KD26):

1. **Session truth never drops** — local `event_log` + projector + HUD manual number pad work with no network. Process kill restores the same sets via `load(sessionId)` → `fold()`. Snapshots are never stored as truth.
2. **Voice logging offline is conditional** — only if `SttProvider.supportsOffline`. Otherwise: **one** `offline_voice_unavailable` line per session ("I can't hear you until you're back online — tap the numbers if you want."), never every set, and **no raw audio queued** in RAM across process death.

`RoomEventLog.append` runs one transaction that reads `MAX(sequence)+1` and inserts under a connection lock; `UNIQUE(session_id, sequence)` rejects a race and the append **retries** (up to 8×) rather than overwriting. `UNIQUE(user_id, event_id)` + `PRIMARY KEY(event_id)` enforce idempotency in the database, not just in application code. Duplicate append returns `AppendAck.Duplicate` — a no-op, not an error.

`WorkoutEventCodec` fails loudly on unknown `event_type`, malformed `payload_json`, or wrong `schemaVersion` — no silent coercion. Migration is versioned from v1; downward and destructive migration both `error()`.

`EventLogEquivalenceTest` proves `InMemoryEventLog` and `RoomEventLog` produce the same types, count, and projected snapshot — so kernel tests running in memory are meaningful.

---

## 14. Voice and music

**Ports only in `:domain`; vendors unset (Q3–Q5); adapters in `:app`.** `VadProvider`, `SttProvider` (with `supportsOffline`), `TtsProvider`/`TtsHandle.cancel()`, `LidProvider`, `AudioFocusController`, `LlmProvider` (`extractCandidates` → envelope; `phraseCoaching` → string), `RetrievalProvider`, `EmbeddingProvider`.

`LlmProvider.extractCandidates` returns `CandidateEventEnvelope` — **not** `Any`, not `JsonObject`, not a chat-completions response. The type system prevents an extractor from returning something that could reach the DB.

**Mic policy (single rule, KD27):** always-on VAD for the whole Gym Mode session including `SET_ACTIVE`. No push-to-talk in MVP (A5). TTS is what's forbidden in `SET_ACTIVE`, not listening.

**Barge-in is mandatory.** VAD `speech_start` cancels TTS, p95 < 200 ms, measured on a fake clock in `VoiceTurnMachineTest`. Echo from TTS is distinguished from user speech (`echoFromTts`) so the AI doesn't log its own words.

**Music:** duck on user speech and on AI speech, restore after. Degrade gracefully if the platform can't duck — speak anyway, never fail the session. The spec is explicit about **not claiming control of Spotify/Apple Music/YouTube Music APIs**; only platform audio focus.

---

## 15. What is actually built (as of 2026-08-20)

**Test state: 100% green.** 23 test classes, ~145 tests, 0 failures, 0 errors. `check_living_spec.py` exits 0 (28 contracts, 18 schemas, 51 goldens, 18 executable PR-1 goldens).

| PR | Scope | State |
|---|---|---|
| 0 | Living spec, product law, frozen schemas v1, 28 contracts, `AGENTS.md`, checker | ✅ done |
| 1 | `CandidateEvent` union, schema gate, `DefaultCandidateValidator`, `ValidatedFact` | ✅ done |
| 2 | `WorkoutEngine`, `WorkoutProjector`, `WorkoutTransitionContract`, lifecycle service | ✅ done |
| 3 | `:persist` — Room-shaped `event_log`, `RoomEventLog`, codec, migrator, fixtures | ✅ done |
| 4 | `FourContextAssembler`, epistemic facts | ✅ done |
| 5 | Voice ports + fakes, `VoiceTurnMachine`, barge-in, fake duck | 🔄 **landing right now** (code + 30 tests + 4 `27-music` goldens written today, README not yet updated) |
| 6 | Safety classifier | ⬜ table exists as data; **no `SafetyClassifier` implementation** |
| 7 | Extraction + multi-label intent | ⬜ not started |
| 8 | Correction wiring into the loop | ⬜ validator side done; loop side not |
| 9 | Headless kernel loop + `shouldSpeak` + `recommend()` | ⬜ **not started — this is "first runnable companion"** |
| 10 | Gym Mode HUD binder | ⬜ `app/` still empty |
| 12–21 | Auth, onboarding, catalog ≥80, generation, substitution, history, dashboard, notes, summary, progress | ⬜ |
| 22 | Backup sync | ❌ cut from MVP (Q11) |
| 23–24 | Android audio focus; real STT/TTS/LLM adapters | ⬜ vendor-gated |

Two exercises' worth of reality check: there is **no UI, no vendor, no cloud, no LLM call, and no safety classifier yet**. What exists is a very well-defended kernel of state, validation, persistence, and context — plus, as of today, the voice turn-taking machine on fakes.

**Golden coverage:** 51 files for 100 named cases across contracts. The 49 missing ones belong almost entirely to PRs 6, 7, 9, 16, 17, 20, 28 — expected, not drift. The kernel-critical ones for built PRs all exist.

---

## 16. Findings — drift, smells, and things worth a decision

### Drift (small, fixable, worth fixing before the next PR)

1. **Contract frontmatter disagrees with contract body on five files.** Contracts 01, 04, 05, 06, 07 have YAML `status: specified` but a body line `**Status:** implemented`. Only 08 (`implemented`) and 10 (`implementing`) agree. Since `check_living_spec.py` reads the machine-readable field, the checker currently sees five implemented kernel features as merely specified — the gate is looking at the wrong number. Pick one source of truth (frontmatter) and make the body render from it, or make the checker assert they match.

2. **`README.md` says "PR 0–4 complete… PR 5 is voice ports and fakes"** while PR 5's code, 30 passing tests, and 4 new goldens are already on disk. It's mid-PR, so this is transient — but `AGENTS.md`'s same-change rule means the README update belongs in the same commit as the voice code, not after.

3. **Contract 03 (voice pipeline) is still `specified`** with PR 5 landing. Arguably correct, since 03 covers real vendors (PR 23–24) too — but if so, the contract should say which slice PR 5 satisfies, otherwise the next agent can't tell whether it's stale or deliberate.

### Code smells (none are bugs; all are future-pain)

4. **`WorkoutEngine.commit()` re-folds the entire event log up to three times per event.** `snapshot(sessionId, plan)` calls `log.sessionEvents()` then `WorkoutProjector.fold()` over everything, and `commit` calls it inside a `forEach` — twice in the guard, once at the end. A 200-event session makes this quadratic. Not a problem at gym scale today; it will be a problem the first time a session runs long or the log is shared. Fold once, thread the snapshot.

5. **The legality guard in `commit()` is written twice.** The outer `if` computes a large exemption expression and the inner `if` re-checks `isLegal` with the same inputs. It's behaviourally equivalent to "reject if illegal AND not exempt AND not the `SET_COMPLETED` special case" — the inner check is dead. Worth collapsing, because this is exactly the kind of function a later agent will misread.

6. **`CommittedEventType.llmMayEmitAsCommittedTruth()` has a dead branch.** The `when` lists ten types that return `false`, and `else -> false`. Every path returns `false`. The intent (nothing may be emitted) is right; the explicit list implies a distinction that doesn't exist and will mislead. Either make it a one-liner or make the list mean something.

7. **`SafetyDecisionTable`'s non-safety rows carry `allowSetCompletedIfNumbers = false`.** For `chest_fly_not_safety` and `technique_question_not_safety` the field is meaningless (no safety event fired), but `false` reads like "block the set." A `null`/`N/A` or a separate row type would say what's actually meant. This matters because PR 6's classifier will be written against this table.

8. **The Room contract is currently unverified by Room.** `:persist` uses `androidx.room:room-common` annotations, but the executable path is a hand-written `JdbcWorkoutEventDao` over SQLite JDBC — the Room compiler never runs, so `@Dao` queries are not validated and `@Database` never generates an implementation. Everything could pass today and still fail the first time Room's annotation processor sees it on Android. Adding a Room-compiler check on an Android variant (even one that only compiles) would close a real gap.

9. **Non-session events all share one sequence space.** `WorkoutEventCodec.toEntity` maps `sessionId ?: ""` to the empty string, so every profile/memory event with a null session contends on `UNIQUE("", sequence)`. Fine today (nothing emits them), a contention point later.

10. **`journal_mode = DELETE` plus a global `ReentrantLock`** serializes all writes. Correct and simple for single-device; worth revisiting (WAL) when the HUD and the loop are both touching the log.

### Behaviour worth a product decision

11. **A multi-set turn is all-or-nothing.** `validateTurn` returns on the first non-`Accept`, discarding facts already accepted in that turn. *"First set was 12 at 40, second was 10, and third was—"* (mumbled) currently yields one clarification and **zero** logged sets, losing two perfectly good ones. That may be intentional (never log a partial batch), but it isn't stated in contract 04 or 05, and in a gym it's the difference between "it got most of it" and "it got nothing." Worth an explicit rule either way.

12. **Occupancy clarification may be frequent in practice.** "That was actually my second set" is a natural thing to say, and any time a later set exists the system asks rather than acts. Safe, correct, and possibly annoying — worth watching in dogfood, and worth considering a "replace" default when the correction only touches reps and the later sets are untouched.

---

## 17. Strategic observations

**What this spec has de-risked extremely well:** state correctness. Duplicate sets, wrong set numbers, corrections spawning parallel rows, invented history, LLM writing the database, losing a workout to a process kill, talking over someone mid-rep — all of these are structurally prevented, with tests. That is a genuinely unusual amount of rigour for pre-vendor code, and it's the right rigour: reliability *is* the product for a trainer that remembers.

**What is not de-risked at all, and is scheduled last:** whether the voice half works. The hardest problem in this product is STT accuracy for **numbers, weights, exercise names, and Telugu-English code-mix, in a loud gym, over a headset, with music ducking** — and there is currently zero evidence about it, because vendors are Q3–Q5 (open) and real adapters are PR 24. The kernel can be declared "done on fakes and fixtures" (PR 26 says so explicitly) while the central product risk remains completely untested.

The mitigation the spec offers is honest (`llm_required` markers, rules-fallback matrix, an eval harness in PR 26) but it's late. **The highest-value schedule change available is probably to spike one real STT vendor against a recorded set of gym utterances — numbers, weights, code-mix, with music — well before PR 24, purely as evidence.** It costs little, doesn't violate the port boundary (a throwaway spike outside `:domain`), and it could invalidate or confirm the product thesis months earlier than the current plan does.

**Second-largest open risk: the 80-exercise catalog.** PR 14 is the dependency for generation (PR 15), substitution (PR 16), and half the Q&A answers, and Q9 (content source/license) is still open. It's unglamorous, blocking, and not an engineering problem — which is exactly the kind of thing that slips.

**Third: no sync means device loss = total history loss.** The product identity is "persistent training memory," and KD25 cut backup from MVP for good reasons (single device, no server, focus). But a user who loses their phone in month four loses everything the product exists to accumulate. Export exists in the API sketch; there's no user-facing backup story. Worth deciding whether a simple encrypted local export-to-file is MVP.

**Fourth: headphones are effectively required.** The auto-commit route means no headset → no hands-free voice logging → HUD confirm taps → a materially different product. That's the right safety call, but it should be stated as a product requirement ("bring headphones") rather than discovered by users on speakerphone.

**A note on the discipline itself.** `AGENTS.md`'s 14 forbidden items, the same-change rule, the PR gate, the `check_living_spec.py` checker, and the type-level enforcement in `ValidatedFact` together form the most effective anti-drift system I've seen in a spec this size. The repo is clearly designed to be built substantially by coding agents, and it's designed so that the *cheapest* path for an agent is also the correct one — an agent literally cannot express `setNumber: 3` in a validator output, so it won't. That's better than any amount of documentation saying "please don't."

---

## 18. Open questions (from the spec, still unresolved)

| ID | Question | Status |
|---|---|---|
| Q3 | STT vendor | **Open** — needs streaming, numbers, mixed language |
| Q4 | TTS vendor | **Open** — needs cancel/barge-in, te/hi/ta/en |
| Q5 | LLM vendor | **Open** — constrained JSON extraction + coaching; possibly two models |
| Q8 | Auth providers | **Open** |
| Q9 | Exercise catalog source/license | **Open** (size settled: ≥80 in-repo) |
| Q10 | Transcript TTL default | **Open** (not indefinite; number unset) |

Resolved 2026-08-20 and not to be reopened: Android first (Q1), native Kotlin (Q2), cloud STT/LLM allowed (Q6), SQLite/Room (Q7), no sync server in MVP (Q11).

---

## 19. The shortest path to knowing whether this works

The spec's own answer is PR 9 — "first runnable companion (no pixels)": the headless kernel loop with `shouldSpeak`, a fake extractor, and fake voice. Everything before it (PRs 6, 7, 8) is on that critical path; everything after it is surface.

From today that's: **safety classifier (6) → extraction + intent (7) → correction wiring (8) → headless loop (9)**. At PR 9 you can run the gold scene from §56 end to end — greeting, "Got 10.", "Nice. That's one more than your last set.", a correction, a question during a set deferred to rest, a completion summary — with zero UI and zero vendors, and assert every line of it. That's the moment the product stops being a specification and starts being testable as an experience.

Then the single highest-information thing after that is not PR 10's HUD. It's a real STT spike against real gym audio.

---

*Sources: `PRODUCT.md`; `AGENTS.md`; `docs/living-spec/TECHNICAL_SPEC.md`; `docs/living-spec/contracts/01`–`28`; `docs/living-spec/{CORE_LOOP,DATA_MODEL,PR0_DEFINITION_OF_DONE}.md`; `docs/living-spec/{boundaries,context,conversation,events,safety,state-machine,testing,voice}/*`; `docs/MODULE_BOUNDARIES.md`; `domain/src/**`; `persist/src/**`; `tests/golden/*`; `scripts/check_living_spec.py`; Gradle test-results XML from the 2026-08-20 13:42 run.*
