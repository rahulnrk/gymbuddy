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
| `PUBLISHED GUIDELINE` | Stated in a named professional guideline or curriculum (ACSM, NSCA, NASM, ACE). Cite the source. Ranges, not point values. Sits above `MVP HEURISTIC` and below `MEASURED`. Nothing in the programming-rules PR is `MEASURED`. |
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

## Programming rules (contract 29)

Package `com.gymbuddy.domain.programming`. Every `const val` in that package is named below. These are ranges the validator checks against; they are not a plan generator. Catalog `defaultRestSec` / `repWindow` win over the goal defaults when an exercise is known.

Review date for this block: `REVIEWED = 2026-09-01`.

### Volume — weekly hard sets per muscle

A hard set is a working set taken close enough to failure to drive adaptation. Warm-up and ramp sets do not count. Unilateral: one logged set = one set per side, not halved.

### `HYPERTROPHY_INTERMEDIATE_HARD_SETS_MIN = 10` / `HYPERTROPHY_INTERMEDIATE_HARD_SETS_MAX = 20`

- **Where:** `weeklyHardSets(HYPERTROPHY, INTERMEDIATE|ADVANCED)`
- **Purpose:** Landmark weekly hard-set volume per muscle for hypertrophy.
- **Source:** `PUBLISHED GUIDELINE` — Coach & AI Programming Guide §4.3: “About 10–20 hard sets per muscle per week for intermediates”. Advanced is not given a separate number; we reuse this range rather than invent a higher one.
- **Status:** Range, as published. Not a point value.
- **Configurable:** No.

### `HYPERTROPHY_BEGINNER_HARD_SETS_MIN = 6` / `HYPERTROPHY_BEGINNER_HARD_SETS_MAX = 12`

- **Where:** `weeklyHardSets(HYPERTROPHY, BEGINNER)`
- **Purpose:** Beginners grow on less than intermediates. Strictly below the 10–20 range (min and max both lower).
- **Source:** `MVP HEURISTIC` — Guide §4.3 says “beginners grow on less” and gives no number.
- **Status:** Overlaps the bottom of intermediate (10–12). That overlap is honest; the guide does not claim a disjoint band.
- **Configurable:** No.

### `STRENGTH_BEGINNER_HARD_SETS_MIN = 4` / `STRENGTH_BEGINNER_HARD_SETS_MAX = 8`

- **Where:** `weeklyHardSets(STRENGTH, BEGINNER)`
- **Purpose:** Strength-goal weekly hard sets for beginners.
- **Source:** `MVP HEURISTIC` — research folder silent on strength-goal set counts.
- **Configurable:** No.

### `STRENGTH_HARD_SETS_MIN = 6` / `STRENGTH_HARD_SETS_MAX = 12`

- **Where:** `weeklyHardSets(STRENGTH, INTERMEDIATE|ADVANCED)`
- **Purpose:** Strength-goal weekly hard sets.
- **Source:** `MVP HEURISTIC` — research folder silent on strength-goal set counts.
- **Configurable:** No.

### `ENDURANCE_HARD_SETS_MIN = 8` / `ENDURANCE_HARD_SETS_MAX = 16`

- **Where:** `weeklyHardSets(ENDURANCE, *)`
- **Purpose:** Endurance-goal weekly hard sets.
- **Source:** `MVP HEURISTIC` — research folder silent.
- **Configurable:** No.

### `GENERAL_FITNESS_HARD_SETS_MIN = 8` / `GENERAL_FITNESS_HARD_SETS_MAX = 16`

- **Where:** `weeklyHardSets(GENERAL_FITNESS, *)`
- **Purpose:** General-fitness weekly hard sets.
- **Source:** `MVP HEURISTIC` — research folder silent.
- **Configurable:** No.

Fat-loss volume copies the hypertrophy landmark for the same training age and is tagged `MVP HEURISTIC` (research folder silent on a fat-loss set count; “fat loss while keeping muscle” is the nearest published job).

### Effort (reps in reserve)

### `MOST_WORK_RIR_MIN = 2` / `MOST_WORK_RIR_MAX = 4`

- **Where:** `effort(*, INTERMEDIATE|ADVANCED)`
- **Purpose:** Target effort for most working sets. RPE 6–8.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “Most work at RPE 6–8 (2–4 reps in reserve). True 1RMs are rare and expensive.” Applied across goals; the guide does not split RIR by goal.
- **Configurable:** No.

### `BEGINNER_RIR_MIN = 3` / `BEGINNER_RIR_MAX = 5`

- **Where:** `effort(*, BEGINNER)`
- **Purpose:** Extra reserve while the pattern is still being learned.
- **Source:** `MVP HEURISTIC` — research folder silent on a beginner RIR split.
- **Configurable:** No.

### Reps

### `STRENGTH_REPS_MIN = 3` / `STRENGTH_REPS_MAX = 6`

- **Where:** `repRange(STRENGTH)`
- **Purpose:** Strength-goal rep window.
- **Source:** `PUBLISHED GUIDELINE` — NASM OPT §4.2 Maximal Strength is 1–5; Guide §4.3 says true 1RMs are rare. Taken as **3–6** so the default is heavy work without programming a 1RM. Documented disagreement; 3–6 is the resolution.
- **Configurable:** No. Catalog `repWindow` wins per exercise.

### `HYPERTROPHY_REPS_MIN = 6` / `HYPERTROPHY_REPS_MAX = 12`

- **Where:** `repRange(HYPERTROPHY)`
- **Purpose:** Hypertrophy-goal rep window.
- **Source:** `PUBLISHED GUIDELINE` — NASM OPT §4.2 Hypertrophy / muscular development 6–12.
- **Configurable:** No. Catalog wins.

### `ENDURANCE_REPS_MIN = 12` / `ENDURANCE_REPS_MAX = 20`

- **Where:** `repRange(ENDURANCE)`
- **Purpose:** Endurance-goal rep window.
- **Source:** `PUBLISHED GUIDELINE` — NASM OPT §4.2 Stabilization Endurance 12–20.
- **Configurable:** No. Catalog wins.

### `FAT_LOSS_REPS_MIN = 8` / `FAT_LOSS_REPS_MAX = 15`

- **Where:** `repRange(FAT_LOSS)`
- **Purpose:** Fat-loss-goal rep window.
- **Source:** `MVP HEURISTIC` — research folder silent.
- **Configurable:** No. Catalog wins.

### `GENERAL_FITNESS_REPS_MIN = 8` / `GENERAL_FITNESS_REPS_MAX = 15`

- **Where:** `repRange(GENERAL_FITNESS)`
- **Purpose:** General-fitness rep window.
- **Source:** `MVP HEURISTIC` — research folder silent.
- **Configurable:** No. Catalog wins.

### Rest (seconds)

### `STRENGTH_REST_SEC_MIN = 180` / `STRENGTH_REST_SEC_MAX = 300`

- **Where:** `restSeconds(STRENGTH, COMPOUND)`
- **Purpose:** Rest between heavy strength sets.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “Strength 3–5 min”.
- **Configurable:** No. Catalog `defaultRestSec` wins.

### `HYPERTROPHY_REST_SEC_MIN = 60` / `HYPERTROPHY_REST_SEC_MAX = 180`

- **Where:** `restSeconds(HYPERTROPHY, *)`
- **Purpose:** Rest between hypertrophy sets.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “hypertrophy 1–3 min”.
- **Configurable:** No. Catalog wins.

### `ENDURANCE_REST_SEC_MIN = 30` / `ENDURANCE_REST_SEC_MAX = 90`

- **Where:** `restSeconds(ENDURANCE, *)`
- **Purpose:** Rest between metabolic / endurance sets.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “metabolic work 30–90 s”.
- **Configurable:** No. Catalog wins.

### `STRENGTH_ISOLATION_REST_SEC_MIN = 60` / `STRENGTH_ISOLATION_REST_SEC_MAX = 180`

- **Where:** `restSeconds(STRENGTH, ISOLATION)`
- **Purpose:** Isolation rest on a strength goal. The published 3–5 min is for heavy compound work.
- **Source:** `MVP HEURISTIC` — research folder silent on isolation rest under a strength goal.
- **Configurable:** No. Catalog wins.

### `FAT_LOSS_REST_SEC_MIN = 30` / `FAT_LOSS_REST_SEC_MAX = 90`

- **Where:** `restSeconds(FAT_LOSS, *)`
- **Purpose:** Treat fat-loss rest as metabolic.
- **Source:** `MVP HEURISTIC` — research folder silent.
- **Configurable:** No. Catalog wins.

### `GENERAL_FITNESS_REST_SEC_MIN = 60` / `GENERAL_FITNESS_REST_SEC_MAX = 180`

- **Where:** `restSeconds(GENERAL_FITNESS, *)`
- **Purpose:** General-fitness rest, hypertrophy-like.
- **Source:** `MVP HEURISTIC` — research folder silent.
- **Configurable:** No. Catalog wins.

### Frequency

### `WEEKLY_FREQUENCY_MIN = 2` / `WEEKLY_FREQUENCY_MAX = 3`

- **Where:** `weeklyFrequency(*, *)`
- **Purpose:** Times per week a muscle or pattern should appear.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “Each muscle / pattern 2–3 times per week beats one heroic session for most humans.” Same range for every goal and training age; the guide does not split.
- **Configurable:** No.

### Deload

### `DELOAD_EARLIEST_WEEK = 4` / `DELOAD_LATEST_WEEK = 8`

- **Where:** `deloadDue`
- **Purpose:** Calendar trigger. Always due at week 8 with no signals. From week 4, any observed poor sleep / pain / motivation also fires. Null signals are unobserved, not “fine”.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “Every 4–8 weeks, or when sleep/pain/motivation fall over”.
- **Configurable:** No.

### `DELOAD_CUT_PERCENT_MIN = 40` / `DELOAD_CUT_PERCENT_MAX = 60`

- **Where:** `deloadCutPercent`
- **Purpose:** A deload cuts volume ~40–60%, not intensity to zero. Remaining volume is therefore also ~40–60%.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “cut volume ~40–60%”.
- **Configurable:** No.

### Ordering ranks (relative only)

These numbers are sort keys, not published seconds or sets. They exist so `orderScore` can be a total order. Changing them does not change the coaching claim (“compound and high-skill work first”).

### `ORDER_SKILL_HIGH = 300` / `ORDER_SKILL_MODERATE = 200` / `ORDER_SKILL_LOW = 100`

- **Where:** `orderScore`
- **Purpose:** Rank catalog `skillDemand`.
- **Source:** `MVP HEURISTIC` — NSCA/ACE say high-skill work goes first; they do not publish a numeric score.
- **Configurable:** No.

### `ORDER_FATIGUE_HIGH = 30` / `ORDER_FATIGUE_MODERATE = 20` / `ORDER_FATIGUE_LOW = 10`

- **Where:** `orderScore`
- **Purpose:** Rank catalog `fatigueCost` below skill.
- **Source:** `MVP HEURISTIC` — same as skill ranks.
- **Configurable:** No.

### `ORDER_COMPOUND_BONUS = 3`

- **Where:** `orderScore`
- **Purpose:** Tie-break compound before isolation at equal skill and fatigue.
- **Source:** `MVP HEURISTIC` — Guide §4 / NSCA “compound before isolation”; no numeric bonus.
- **Configurable:** No.

### `ORDER_CORE_PENALTY = 50`

- **Where:** `orderScore`
- **Purpose:** Core last.
- **Source:** `MVP HEURISTIC` — Guide / NSCA “core and isolation last”; no numeric penalty.
- **Configurable:** No.

### Volume tally

### `PRIMARY_MUSCLE_CONTRIBUTION_MIN = 0.5`

- **Where:** `hardSetsByMuscle` / `musclesTrained`
- **Purpose:** A hard set counts toward a muscle when that muscle's catalog contribution is at least 0.5. Stops serratus 0.2 on bench from eating a full hard set, still counts triceps 0.65.
- **Source:** `MVP HEURISTIC` — research folder silent on a contribution cutoff. Catalog has weights, not a primary flag.
- **Configurable:** No.

---

## Summary

| Class | Count |
|---|---|
| `CONTRACTUAL` (spec-asserted, unmeasured) | 12 |
| `SAFETY REQUIREMENT` (subset of above) | 4 |
| `PUBLISHED GUIDELINE` (programming rules, ranges) | 12 landmarks (volume 10–20, RIR 2–4, reps 3–6 / 6–12 / 12–20, rest 180–300 / 60–180 / 30–90, frequency 2–3, deload 4–8 weeks and 40–60% cut) |
| `MVP HEURISTIC` (programming rules, research silent) | beginner volume, strength/endurance/fat-loss/general volume, beginner RIR, fat-loss/general reps, isolation/fat-loss/general rest, order ranks, primary-muscle cutoff 0.5 |
| `UNKNOWN — REQUIRES VALIDATION` | 3 (`restTargetSec=90`, `Recommend` rule, `Recommend` 2.5 kg step) |
| `TEMPORARY TEST VALUE` | 4 |
| `MEASURED` | **0** |

★ = convertible to `MEASURED` by the Voice Evidence MVP.

Three constants are declared but **not wired to anything**: `FIRST_SET_TIMER_SEC`,
the endpointing range, and the coaching temperature. They describe intended
behaviour that does not exist yet. That is not drift — the contracts that own them
are still `specified` — but it should not be mistaken for implemented behaviour.
