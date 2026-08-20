# Decisions and thresholds

Every heuristic constant introduced in PR 0–9, with provenance.

**Why this file exists.** These numbers were carried from `TECHNICAL_SPEC.md` into
code without recording where they came from. A future contributor cannot tell a
measured value from a guess. This register makes that explicit so nobody tunes a
safety requirement by accident, or defends a guess as if it were evidence.

## Classification

| Class | Meaning |
|---|---|
| `CONTRACTUAL` | Stated in the living spec / product law. Changing it changes a contract. |
| `SAFETY REQUIREMENT` | Exists to prevent harm or a false workout mutation. Conservative by design. |
| `PLATFORM CONSTRAINT` | Imposed by OS/hardware/physics. |
| `PRODUCT DECISION` | A deliberate product-feel choice. |
| `MEASURED` | Derived from real data. **Nothing currently qualifies.** |
| `MVP HEURISTIC` | Chosen to make the MVP work; expected to change. |
| `TEMPORARY TEST VALUE` | Exists only for deterministic fakes/tests. Not product behaviour. |
| `UNKNOWN — REQUIRES VALIDATION` | In code, not justified anywhere. |

> **No value in this repository is currently `MEASURED`.** Every threshold below is
> asserted by the spec or invented in code. The Voice Evidence MVP
> (`docs/living-spec/VOICE_EVIDENCE_MVP.md`) exists to convert the starred rows
> into `MEASURED`.

---

## Commit-gate thresholds (false-set prevention)

### `CONFIDENCE_FLOOR = 0.55` ★

- **Where:** `DefaultCandidateValidator.CONFIDENCE_FLOOR`
- **Purpose:** Block `SET_COMPLETED` when the extractor is unsure, except on the mandatory weight-inherit path.
- **Source:** `CONTRACTUAL` — `contracts/05` line 40; `TECHNICAL_SPEC.md` line 1245; `events/CATALOG.md` line 99.
- **Status:** Spec-asserted. No evidence the 0.55 point separates good from bad extractions.
- **Validation required:** Precision/recall of set-commit vs. human labels, swept over 0.3–0.9, on the real-utterance dataset.
- **Configurable:** No — compile-time `const`. Should become config for the sweep.
- **⚠ Known defect:** `EnglishLastSetFallback` hardcodes `overallConfidence = 0.9` on every regex match (`CandidateFactory.event(overall = 0.9)`). The floor therefore **cannot reject anything on the rules-fallback path**; it only bites on real LLM output. Tracked as P1.

### `sttDurationMs >= 250.0` ★

- **Where:** `DefaultCandidateValidator.voiceSetAutoCommitAllowed`
- **Purpose:** Drop pocket-fabric rustle and sub-word bursts before they become sets.
- **Source:** `CONTRACTUAL` + `SAFETY REQUIREMENT` — `contracts/03` line 27; `TECHNICAL_SPEC.md` lines 1248, 1793; `voice/PORTS.md`.
- **Status:** Spec-asserted. Plausible (250 ms is roughly one short syllable) but unmeasured.
- **Validation required:** Duration histogram of real "Got 10." finals vs. rustle/noise finals. Check the shortest legitimate utterance in the dataset is not being clipped.
- **Configurable:** No.

### `audioRoute == HEADSET && talkerCount == 1 && !overlappingVad`

- **Where:** `DefaultCandidateValidator.voiceSetAutoCommitAllowed`
- **Purpose:** A bystander's "got 10" must never become the user's set.
- **Source:** `CONTRACTUAL` + `SAFETY REQUIREMENT` — KD27 / A5; `contracts/03`; `voice/PORTS.md` "voice-set commit route (closed)".
- **Status:** Categorical rule, not a tunable number. Sound as a policy.
- **Validation required:** Confirm real STT/VAD can actually supply `talkerCount` and `overlappingVad`. **Today both are supplied by fakes only** — no real provider has been asked for them. This is the single most important unproven assumption in the commit gate.
- **Configurable:** No, and should not be.

### `DUPLICATE_WINDOW_MS = 10_000L` ★

- **Where:** `DefaultCandidateValidator.DUPLICATE_WINDOW_MS`
- **Purpose:** Same `sttFinalHash` within 10 s is a duplicate STT final, not a second set.
- **Source:** `CONTRACTUAL` — `contracts/05` line 43; `events/CATALOG.md` line 99; golden `05-extract.event.double-final-same-hash-10s`.
- **Status:** Spec-asserted. 10 s is long enough that a genuine fast repeat ("Got 10." … "Got 10." on a drop set) would be silently swallowed.
- **Validation required:** Measure real inter-final gaps for genuine repeats vs. duplicate finals. Check for false-negative sets in the dataset.
- **Configurable:** No.

### `weight delta > 0.20` (20 %) ★

- **Where:** `DefaultCandidateValidator.validateSetCompleted`
- **Purpose:** A spoken weight >20 % from working weight, with no drop/up language, is probably a misheard number → clarify.
- **Source:** `CONTRACTUAL` — `contracts/05` line 41 ("0.7 effective bar"); `TECHNICAL_SPEC.md` line 2229.
- **Status:** Spec-asserted. Directly trades false sets against clarification rate — a core UX dial.
- **Validation required:** Clarification rate on the real dataset; how often 20 % fires on a legitimate jump (e.g. 20 kg → 25 kg dumbbells = 25 %).
- **Configurable:** No.

---

## Correction and state thresholds

### Cross-exercise correction window `<= 120` seconds ★

- **Where:** `DefaultCandidateValidator.within120s`
- **Purpose:** Allow "no, 8" to correct the previous exercise's last set only if it was recent.
- **Source:** `CONTRACTUAL` — `contracts/07` lines 29, 64.
- **Status:** Spec-asserted, no rationale recorded.
- **Validation required:** Real gap between finishing an exercise and correcting it. Currently a guess about human memory.
- **Configurable:** No.

### `FIRST_SET_TIMER_SEC = 8` ★

- **Where:** `SessionStartContract.FIRST_SET_TIMER_SEC`
- **Purpose:** Enter `SET_ACTIVE` 8 s after `EXERCISE_STARTED` with no ack, so TTS suppression and grunt gating are live during the actual lift.
- **Source:** `CONTRACTUAL` — KD21; `state-machine/CONTRACT.md` line 84; `contracts/01` line 29. Spec states an allowed range of **5–12 s**.
- **Status:** Spec-asserted with a stated range, which implies it was chosen not measured.
- **Validation required:** Real time from "you're on bench" to the user starting the first rep.
- **Configurable:** Declared `const`; the 5–12 range should be enforced if it becomes settable.
- **⚠ Not implemented:** the constant exists; nothing reads it. There is no timer in the kernel today — `SET_ACTIVE` is only entered via explicit `beginSet()`. Tracked as P2.

### `restTargetSec = 90`

- **Where:** `PlannedExercise.restTargetSec` default; `WorkoutProjector` fallback `p.int("targetSec") ?: 90`.
- **Purpose:** Default rest target when a plan does not specify one.
- **Source:** `UNKNOWN — REQUIRES VALIDATION`. Appears in no spec document as a value.
- **Status:** Invented in code. Harmless today (rest is informational, and per `contracts/06` the timer does not force the user to lift), but it is an unsourced product default.
- **Validation required:** Should come from the plan template (PR 14/15), not a code default.
- **Configurable:** Per-exercise field, so yes — only the fallback is hardcoded.

---

## Coaching thresholds

### Useful delta: `|Δreps| >= 1` or `|ΔweightKg| >= 0.5` ★

- **Where:** `ShouldSpeak.usefulDelta`
- **Purpose:** Decide whether a completed set is worth speaking about.
- **Source:** `CONTRACTUAL` — `TECHNICAL_SPEC.md` line 809 (`≥ 0.5 kg`, or 1 lb if `units=lb`); `contracts/09`.
- **Status:** Spec-asserted. **Implementation deviates from the contract** in two ways:
  1. Spec compares Δweight against *this exercise's working weight*; code compares against the *previous set this session*.
  2. Spec also allows a delta vs. *last session's corresponding set* where `stance=known`. Code has no cross-session comparison at all. Tracked as P1 (see `Recommend` below).
  3. The `1 lb` variant for `units=lb` is not implemented; 0.5 kg is applied regardless of unit.
- **Validation required:** Speak-rate per session against user-reported "talked too much / too little".
- **Configurable:** No.

### `Recommend`: 3 sets, all `reps >= 10`, `last < first`, `±2.5 kg`

- **Where:** `Recommend.suggest`
- **Purpose:** Suggest a working-weight change for this session.
- **Source:** `UNKNOWN — REQUIRES VALIDATION`. The 10-rep bar, the exactly-3-sets requirement, and the 2.5 kg step appear in **no** spec document. `TECHNICAL_SPEC.md` §24's example ("12, 12 and 11 at 50 kg → try 52.5") implies a 2.5 kg step and a cross-**session** comparison, but states no rule.
- **Status:** Invented in code, and narrower than the contract: it reads only the current session (P1, see `docs/living-spec/contracts/09`).
- **Validation required:** Whether the suggestion matches what a coach would say, on real logged sessions.
- **Configurable:** No.

### Safety: pain collocate within a `6`-token window

- **Where:** `DefaultSafetyClassifier.painNearChest` (`abs(c - p) <= 6`)
- **Purpose:** "chest fly machine is occupied" must not fire chest-pain critical; "pain in my chest" must.
- **Source:** `CONTRACTUAL` + `SAFETY REQUIREMENT` — `contracts/11` line 74; `safety/DECISION_TABLE.md` line 13.
- **Status:** Spec-asserted. This is a **false-negative-sensitive** constant: too small a window misses a real report of chest pain.
- **Validation required:** Critical recall on real safety utterances. Per `contracts/11`, recall matters more than precision here — the classifier is required to fail conservative.
- **Configurable:** No, and any change requires a safety review, not a tuning pass.

---

## Voice / latency budgets

### Barge-in cancel p95 `< 200 ms` ★

- **Where:** asserted in `VoiceTurnMachineTest`; measured on `FakeClock`.
- **Purpose:** The AI must stop talking when the user starts.
- **Source:** `CONTRACTUAL` + `PLATFORM CONSTRAINT` — product §12; KD11; `TECHNICAL_SPEC.md` line 468; `voice/PORTS.md`.
- **Status:** **Currently vacuous.** `FakeClock` does not advance during `bargeIn()`, so the measured latency is 0 ms by construction. This proves the *call order* is right; it proves nothing about real cancellation latency.
- **Validation required:** Real TTS `cancel()` latency on a real Android device, per vendor. Belongs to PR 23/24.
- **Configurable:** N/A — a budget, not a setting.

### Endpointing `~800–1200 ms`

- **Where:** Spec only. **Not implemented anywhere.**
- **Purpose:** Do not cut the user off on a mid-sentence pause; gym endpointing is longer than IVR.
- **Source:** `CONTRACTUAL` — `contracts/03` line 43, explicitly marked "configurable, start ~800–1200 ms".
- **Status:** The spec itself flags this as a starting guess. No code reads it.
- **Validation required:** Directly measurable from the real dataset — pause-length distribution inside multi-set utterances such as "first was 12 … second was 10".
- **Configurable:** Must be, once implemented.

### `speech_end → HUD` p95 `< 800 ms` local / `< 1500 ms` cloud

- **Where:** Spec only. Not measured.
- **Source:** `CONTRACTUAL` — `TECHNICAL_SPEC.md` line 468.
- **Status:** Unmeasurable until a real STT provider exists.
- **Validation required:** Voice Evidence MVP records per-provider latency.

---

## LLM parameters

### Extraction `temperature <= 0.2`

- **Where:** `SchemaConstrainedFakeExtractor` (enforced by `require`); `LlmProvider.extractCandidates` default.
- **Purpose:** Deterministic, schema-obedient extraction.
- **Source:** `CONTRACTUAL` — `boundaries/LLM_VS_APPLICATION.md` line 19; `TECHNICAL_SPEC.md` line 1649.
- **Status:** Conventional and reasonable. Enforced at construction, which is good.
- **Configurable:** Constructor parameter, capped by `require`.

### Coaching `temperature ≈ 0.6`

- **Where:** `LlmProvider.phraseCoaching` default.
- **Purpose:** Natural phrasing.
- **Source:** `CONTRACTUAL` — `TECHNICAL_SPEC.md` line 1649.
- **Status:** Unused — `CoachingPhraser` is deterministic templates; no LLM is called.

---

## Test-only values (not product behaviour)

| Value | Where | Class |
|---|---|---|
| `FakeLid` confidences 0.7 / 0.8 / 0.9 | `voice/fake/FakeLid.kt` | `TEMPORARY TEST VALUE` |
| `FakeStt` final confidence 0.9, partial 0.4, duration 800 ms | `voice/fake/FakeStt.kt` | `TEMPORARY TEST VALUE` |
| `VoiceTurnMachine.onFinal` defaults 0.9 / 800 ms | `voice/VoiceTurnMachine.kt` | `TEMPORARY TEST VALUE` |
| `CandidateFactory.event(overall = 0.9)` | `extract/CandidateFactory.kt` | **Not test-only — ships in the fallback path. See the `CONFIDENCE_FLOOR` defect above.** |
| `LB_TO_KG = 0.45359237` | `DefaultCandidateValidator` | Definitional (exact, by international agreement). Not a heuristic. |

---

## Summary

| Class | Count |
|---|---|
| `CONTRACTUAL` (spec-asserted, unmeasured) | 12 |
| `SAFETY REQUIREMENT` (subset of above) | 4 |
| `UNKNOWN — REQUIRES VALIDATION` | 3 (`restTargetSec=90`, `Recommend` rule, `Recommend` 2.5 kg step) |
| `TEMPORARY TEST VALUE` | 4 |
| `MEASURED` | **0** |

★ = convertible to `MEASURED` by the Voice Evidence MVP.

Three constants are declared but **not wired to anything**: `FIRST_SET_TIMER_SEC`,
the endpointing range, and the coaching temperature. They describe intended
behaviour that does not exist yet. That is not drift — the contracts that own them
are still `specified` — but it should not be mistaken for implemented behaviour.
