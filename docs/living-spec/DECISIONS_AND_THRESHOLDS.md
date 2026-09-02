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
| `PUBLISHED GUIDELINE` | Stated in a named professional guideline or curriculum (ACSM, NSCA, NASM, ACE). Cite the source. Ranges, not point values. Sits above `GUIDELINE-DERIVED`. Nothing in the programming-rules PR is `MEASURED`. |
| `GUIDELINE-DERIVED` | The direction is stated in a named guideline or study; the specific numbers are ours. Cite the source and say what it actually states. Sits above `MVP HEURISTIC` and below `PUBLISHED GUIDELINE`. |
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

Review date for this block: `REVIEWED = 2026-09-01`. Sourced replacements: `docs/research/programming-numbers-2026-09-01.md`. Citations below are the named papers, not that folder.

### Volume — weekly hard sets per muscle

A hard set is a working set taken close enough to failure to drive adaptation. Warm-up and ramp sets do not count. Unilateral: one logged set = one set per side, not halved. Counting is **fractional** (see `FRACTIONAL_SET_*`).

### `HYPERTROPHY_INTERMEDIATE_HARD_SETS_MIN = 10` / `HYPERTROPHY_INTERMEDIATE_HARD_SETS_MAX = 20`

- **Where:** `weeklyHardSets(HYPERTROPHY, INTERMEDIATE|ADVANCED)`
- **Purpose:** Landmark weekly hard-set volume per muscle for hypertrophy.
- **Source:** `PUBLISHED GUIDELINE` — Currier et al. (2026) ACSM Position Stand: “higher weekly volume (~10 sets per muscle group)”. Baz-Valle et al. (2022) J Hum Kinet: 12–20 weekly sets optimum for trained young men. Schoenfeld et al. (2017) J Sports Sci: graded dose–response. Advanced is not given a separate number; we reuse this range.
- **Configurable:** No.

### `HYPERTROPHY_BEGINNER_HARD_SETS_MIN = 4` / `HYPERTROPHY_BEGINNER_HARD_SETS_MAX = 10`

- **Where:** `weeklyHardSets(HYPERTROPHY, BEGINNER)`
- **Purpose:** Beginner hypertrophy volume. Strictly below intermediate (min and max both lower).
- **Source:** `GUIDELINE-DERIVED` — Behm et al. (2023) Sports Med is a *minimalist* review: beginners still gain with &lt;3 sets per multi-joint exercise, one session/week. That is a “still works” floor, not a 4–10 weekly landmark. Peterson et al. (2005): untrained optimum ~4 sets/muscle group, session vs week unstated. Uğurlu et al. (2024): moderate beat both low and high in untrained women. **What the papers state:** a low dose works. **What is ours:** 4–10.
- **Configurable:** No.

### `STRENGTH_BEGINNER_HARD_SETS_MIN = 3` / `STRENGTH_BEGINNER_HARD_SETS_MAX = 8`

- **Where:** `weeklyHardSets(STRENGTH, BEGINNER)`
- **Purpose:** Strength-goal weekly hard sets for beginners.
- **Source:** `GUIDELINE-DERIVED` — Rhea et al. (2003) Med Sci Sports Exerc (140 studies): untrained maximal gains at 60% 1RM, 3 d/wk, ~4 sets per muscle group. Peterson et al. (2005) confirms the ~4. **What the papers state:** a low dose around 4. They do not state per-session vs per-week. **What is ours:** 3–8.
- **Configurable:** No.

### `STRENGTH_HARD_SETS_MIN = 4` / `STRENGTH_HARD_SETS_MAX = 10`

- **Where:** `weeklyHardSets(STRENGTH, INTERMEDIATE|ADVANCED)`
- **Purpose:** Strength-goal weekly hard sets.
- **Source:** `GUIDELINE-DERIVED` — Currier et al. (2026) ACSM Position Stand: 2–3 sets per exercise at ≥80% 1RM, all major groups ≥2×/week. **What ACSM states:** about 4–6 weekly sets. Ralston et al. (2017) Sports Med: medium/high weekly sets beat low, no 10. **What is ours:** the 10.
- **Configurable:** No.

### `ENDURANCE_HARD_SETS_MIN = 8` / `ENDURANCE_HARD_SETS_MAX = 16`

- **Where:** `weeklyHardSets(ENDURANCE, *)`
- **Purpose:** Endurance-goal weekly hard sets.
- **Source:** `MVP HEURISTIC` — named papers silent. ACSM 2026 gives frequency, not an endurance set landmark.
- **Configurable:** No.

### `GENERAL_FITNESS_HARD_SETS_MIN = 8` / `GENERAL_FITNESS_HARD_SETS_MAX = 16`

- **Where:** `weeklyHardSets(GENERAL_FITNESS, *)`
- **Purpose:** General-fitness weekly hard sets.
- **Source:** `MVP HEURISTIC` — named papers silent.
- **Configurable:** No.

Fat-loss volume copies the hypertrophy landmark for the same training age. **Source:** `PUBLISHED GUIDELINE` — Roth et al. (2022) Scand J Med Sci Sports: high vs moderate sets made no difference to lean-mass preservation in a deficit; ≥10 weekly sets per muscle associated with little-to-no lean mass loss.

### Effort (reps in reserve)

### `MOST_WORK_RIR_MIN = 2` / `MOST_WORK_RIR_MAX = 4`

- **Where:** `effort(*, *)` — every training age.
- **Purpose:** Target effort for most working sets. RPE 6–8.
- **Source:** `PUBLISHED GUIDELINE` — Currier et al. (2026) ACSM Position Stand; Guide §4.3. Training age does not change the target (Bermúdez Droguett et al. 2025; Zourdos et al. 2019; Lovegrove et al. 2022).
- **Configurable:** No.

`BEGINNER_RIR` was deleted. Novices estimate RIR as well as trained lifters; a separate easier band contradicted the evidence.

### `RIR_TRUST_MAX = 3` / `RIR_TRUST_REPS_MAX = 12`

- **Where:** `reportedEffortConfidence`
- **Purpose:** Trust a spoken RIR only when it is ≤ 3 and the set is ≤ 12 reps. Otherwise low confidence — do not drive a load change off the report alone.
- **Source:** `GUIDELINE-DERIVED` — Zourdos et al. (2019) J Strength Cond Res: error was 2.05 reps at 1 RIR, 3.65 at 3 RIR, 5.15 at 5 RIR; longer sets made estimates worse. **What the paper states:** accuracy collapses as RIR and set length rise. **What is ours:** the cutoffs 3 and 12.
- **Configurable:** No.

### Reps

### `STRENGTH_REPS_MIN = 5` / `STRENGTH_REPS_MAX = 8`

- **Where:** `repRange(STRENGTH)`
- **Purpose:** Strength-goal rep window. 5 sits nearer ACSM's ≥80% 1RM (~8RM) anchor than 4 did.
- **Source:** `PUBLISHED GUIDELINE` — Currier et al. (2026) ACSM Position Stand: strength at ≥80% 1RM, roughly an 8RM load.
- **Configurable:** No. Catalog `repWindow` wins per exercise, then [STRENGTH_UNSPOTTED_REP_FLOOR](#strength_unspotted_rep_floor--6) may raise the min.

### `PAIN_MOVEMENT`

- **Where:** `validatePainMovements` / `IntakeProvenance.PAIN_MOVEMENT`
- **Purpose:** Sharp or radiating pain on a named movement is a SAFETY `BLOCK` for that movement. Dull ache is caution only. The app does not name the pain.
- **Source:** `SAFETY REQUIREMENT` — user report; stop and modify; do not diagnose.
- **Configurable:** No.

### `STRENGTH_UNSPOTTED_REP_FLOOR = 6`

- **Where:** `unspottedRepFloor` / `effectiveRepRange` / `validatePrescriptions`
- **Purpose:** Prescribing below 6 reps on a high-`consequenceOfFailure` lift that lists a rack/cage/Smith requires that catch in the user's equipment. Otherwise the rule returns a floor of 6. The validator **reports**; it does not rewrite the plan.
- **Source:** `SAFETY REQUIREMENT` — conservative product policy on top of the 5–8 strength window. Equipment vocab has no person-spotter id; catch implements are `squat-rack`, `power-rack`, `smith-machine`. A deadlift miss is a drop, so the floor does not apply to lifts that never list a catch.
- **Configurable:** No.

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

Fat-loss reps inherit hypertrophy (`HYPERTROPHY_REPS_*`). **Source:** `PUBLISHED GUIDELINE` — Carlson et al. (2022) Res Q Exerc Sport: heavy (~80% 1RM) and light (~60% 1RM) load produced statistically equivalent fat loss. A separate fat-loss rep range implies a distinction the evidence does not support. `FAT_LOSS_REPS` was deleted.

### `GENERAL_FITNESS_REPS_MIN = 8` / `GENERAL_FITNESS_REPS_MAX = 15`

- **Where:** `repRange(GENERAL_FITNESS)`
- **Purpose:** General-fitness rep window.
- **Source:** `MVP HEURISTIC` — ACSM 2026 gives no rep range for general health, only frequency.
- **Configurable:** No. Catalog wins.

### Rest (seconds)

### `STRENGTH_REST_SEC_MIN = 180` / `STRENGTH_REST_SEC_MAX = 300`

- **Where:** `restSeconds(STRENGTH, COMPOUND, INTERMEDIATE|ADVANCED)`
- **Purpose:** Rest between heavy strength sets for trained lifters.
- **Source:** `PUBLISHED GUIDELINE` — Senna et al. (2016) J Strength Cond Res: 3–5 min needed for bench press at 3RM. Schoenfeld et al. (2016) J Strength Cond Res: 3 min beat 1 min for strength and hypertrophy in trained men.
- **Configurable:** No. Catalog `defaultRestSec` wins.

### `STRENGTH_REST_SEC_BEGINNER_MIN = 60` / `STRENGTH_REST_SEC_BEGINNER_MAX = 120`

- **Where:** `restSeconds(STRENGTH, *, BEGINNER)`
- **Purpose:** Strength rest for untrained. Training age, not goal alone.
- **Source:** `PUBLISHED GUIDELINE` — Grgic et al. (2017) Sports Med (23 studies): >2 min needed to maximise strength in resistance-trained individuals; 60–120 s is sufficient for untrained.
- **Configurable:** No. Catalog wins.

### `HYPERTROPHY_REST_SEC_MIN = 90` / `HYPERTROPHY_REST_SEC_MAX = 180`

- **Where:** `restSeconds(HYPERTROPHY, *)`
- **Purpose:** Rest between hypertrophy sets. Floor raised from 60 s to 90 s.
- **Source:** `PUBLISHED GUIDELINE` — Singer et al. (2024) Front Sports Act Living (Bayesian MA, 9 studies): small hypertrophic benefit to resting >60 s, no appreciable further benefit beyond 90 s.
- **Configurable:** No. Catalog wins.

### `ENDURANCE_REST_SEC_MIN = 30` / `ENDURANCE_REST_SEC_MAX = 90`

- **Where:** `restSeconds(ENDURANCE, *)`
- **Purpose:** Rest between metabolic / endurance sets.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3: “metabolic work 30–90 s”.
- **Configurable:** No. Catalog wins.

### `STRENGTH_ISOLATION_REST_SEC_MIN = 120` / `STRENGTH_ISOLATION_REST_SEC_MAX = 180`

- **Where:** `restSeconds(STRENGTH, ISOLATION, INTERMEDIATE|ADVANCED)`
- **Purpose:** Isolation rest on a strength goal for trained lifters. Beginners use `STRENGTH_REST_SEC_BEGINNER_*` instead.
- **Source:** `PUBLISHED GUIDELINE` — Senna et al. (2016): for the single-joint machine chest fly, 2 min was sufficient; the multi-joint bench press needed 3–5.
- **Configurable:** No. Catalog wins.

Fat-loss rest inherits hypertrophy (`HYPERTROPHY_REST_SEC_*`, 90–180 s). **Source:** `PRODUCT DECISION` — Carlson et al. (2022) is about *load*, not rest. Inheriting hypertrophy rest is a product choice so fat-loss is not treated as a metabolic circuit. `FAT_LOSS_REST_SEC` was deleted. Fat-loss *reps* still inherit hypertrophy as `PUBLISHED GUIDELINE` (Carlson).

### `GENERAL_FITNESS_REST_SEC_MIN = 60` / `GENERAL_FITNESS_REST_SEC_MAX = 180`

- **Where:** `restSeconds(GENERAL_FITNESS, *)`
- **Purpose:** General-fitness rest.
- **Source:** `MVP HEURISTIC` — named papers silent.
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

These numbers are sort keys, not published seconds or sets. They exist so `orderScore` can be a total order for “where this would sit if we sorted.” `validateOrder` does **not** use that total order. It warns only on class inversion (compound after isolation, or work after core). Consecutive `orderScore` inversion fired on nearly every generated week (chin-up after a moderate hinge, split squat after goblet, Nordic after a good morning) and was too crude to be useful. Changing the ranks does not change the coaching claim (“compound before isolation before core”).

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

### Volume tally — fractional counting

`PRIMARY_MUSCLE_CONTRIBUTION_MIN` (threshold: ≥0.5 → 1 set, else 0) was deleted. Pelland et al. (2025) compared total (1.0), fractional (0.5), and direct (0) counting of indirect sets across 67 studies / 2,058 participants; evidence strongest for fractional. Hamaïde et al. (2025) and Joachim et al. (2025) found the same.

### `FRACTIONAL_SET_INDIRECT_WEIGHT = 0.5`

- **Where:** `setCredit` / `hardSetsByMuscle`
- **Purpose:** Indirect sets credit 0.5, not 1.0 or 0.
- **Source:** `PUBLISHED GUIDELINE` — Pelland et al. (2025) Sports Med; Hamaïde et al. (2025); Joachim et al. (2025).
- **Configurable:** No.

### `FRACTIONAL_SET_PRIMARY_MIN = 0.75` / `FRACTIONAL_SET_INDIRECT_MIN = 0.30`

- **Where:** `setCredit`
- **Purpose:** Map our 0–1 contribution weights onto direct / indirect / ignore. ≥0.75 → 1.0; 0.30–0.75 → 0.5; <0.30 → 0.
- **Source:** `MVP HEURISTIC` — the papers classify direct/indirect by judgement, not a numeric cutoff. We need one because the catalog is weighted.
- **Configurable:** No.

---

## Summary

| Class | Count |
|---|---|
| `CONTRACTUAL` (spec-asserted, unmeasured) | 12 |
| `SAFETY REQUIREMENT` (subset of above) | 4 |
| `PUBLISHED GUIDELINE` (programming rules, ranges) | hypertrophy 10–20; strength beginner 3–8; fat-loss volume copies hypertrophy (Roth 2022); fat-loss *reps* inherit hypertrophy (Carlson 2022, load); RIR 2–4 everyone; reps 5–8 / 6–12 / 12–20; rest 180–300 trained compound, 120–180 trained isolation, 60–120 untrained, hypertrophy 90–180; fractional indirect weight 0.5; frequency 2–3; deload 4–8 weeks and 40–60% cut |
| `GUIDELINE-DERIVED` | beginner hypertrophy 4–10 (Behm/Peterson state a low dose works); strength beginner 3–8 (Rhea/Peterson ~4, session vs week unstated); strength 4–10 (ACSM states ~4–6; 10 is ours); RIR trust ≤3 and ≤12 reps (Zourdos states error grows; cutoffs are ours) |
| `PRODUCT DECISION` (programming) | fat-loss rest inherits hypertrophy (Carlson is about load, not rest) |
| `SAFETY REQUIREMENT` (programming) | `STRENGTH_UNSPOTTED_REP_FLOOR = 6` |
| `MVP HEURISTIC` (programming rules, papers silent) | endurance/general-fitness volume, general-fitness reps and rest, order ranks, fractional band edges 0.75 / 0.30 |
| `UNKNOWN — REQUIRES VALIDATION` | 3 (`restTargetSec=90`, `Recommend` rule, `Recommend` 2.5 kg step) |
| `TEMPORARY TEST VALUE` | 4 |
| `MEASURED` | **0** |

## Intake and screening (contract 30)

Unknown is not No. Every screening answer is `YES | NO | UNKNOWN`. Skip never becomes a clean bill.

### `DAYS_PER_WEEK_MIN = 1` / `DAYS_PER_WEEK_MAX = 7`

- **Where:** `missingRequirements` / `IntakeConstants`
- **Purpose:** A week has 1–7 days. Outside that range is treated as unanswered, not as a plan.
- **Source:** `CONTRACTUAL` — calendar. Not a training-science landmark.
- **Configurable:** No.

### `CLEARANCE_DATE_PATTERN = YYYY-MM-DD`

- **Where:** `recordClearance` / `IntakeConstants.CLEARANCE_DATE_PATTERN`
- **Purpose:** Clearance after referral requires an explicit user-reported date. Evaluate never writes this.
- **Source:** `SAFETY REQUIREMENT` — clearance is the user's report, not a medical fact.
- **Configurable:** No.

### `REVIEWED = 2026-09-01` (intake)

Same review date as the programming block. Intake provenance rows use it.

Red-flag count is 10. That is the screen, not a tunable threshold.

## Exercise picker (contract 31)

Weights and per-slot set counts. All `MVP HEURISTIC`. The picker ranks one slot; it does not invent weekly volume. Score ties break toward lower `consequenceOfFailure`, then lower `skillDemand`, **only among the same implement class** (freeweight / machine / cable / bodyweight). A machine and a dumbbell press are not equivalent. Across classes, slug. The picker has no progression: it will not move a beginner from push-ups to dumbbells over time.

### `WEIGHT_GOAL_FIT = 2.0`

- **Where:** `rank` / `goalFit`
- **Purpose:** Reward a compound lift on a strength goal; smaller bump for hypertrophy / general.
- **Source:** `MVP HEURISTIC` — catalog has no Best-Fit Goal field, so `kind` vs `Goal` is the proxy.
- **Configurable:** No.

### `WEIGHT_MUSCLE_COVERAGE = 2.0`

- **Where:** `rank` / `muscleCoverage`
- **Purpose:** Scale by fractional set-credit on the slot's target muscles.
- **Source:** `MVP HEURISTIC` on the weight; the credits themselves are Pelland fractional counting.
- **Configurable:** No.

### `WEIGHT_SKILL_MISMATCH = 1.5`

- **Where:** `rank` / `skillMatch`
- **Purpose:** Penalise a high-skill lift for a beginner. Do not exclude.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `WEIGHT_FRESHNESS = 1.0`

- **Where:** `rank` / `freshness`
- **Purpose:** Penalty when a primary muscle is fatigued. Was 3.0. Same-pattern candidates usually share primaries, so a uniform hit does not reorder them — ranking cannot answer “don't squat today.” `SlotReadiness.allCandidatesFatigued` is that signal. The weight still earns its keep when primaries differ: back squat lists glute ≥ 0.75, goblet does not, so glute fatigue drops back squat below goblet. Close-grip bench cannot be the example — it is pattern `triceps`, so it never shares a horizontal-push slot with bench.
- **Worked example:** squat slot targeting quads, full gym, glute = 1.0. Fresh order has back squat above goblet (tie-break). Fatigued glutes: goblet ranks above back squat.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `FATIGUE_COOKED = 0.7` / `FATIGUE_MILD = 0.35`

- **Where:** `slotReadiness`, `adjustForToday`
- **Purpose:** Cooked = every survivor's primary is ≥ 0.7, or the slot's worst target is. Mild = ≥ 0.35 and is **not** a reason to fuss (`AsPlanned`).
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `DEFER_CAP = 2`

- **Where:** `WorkoutEngine.acceptDeferral`
- **Purpose:** A movement may be deferred twice. The third accept on that same movement becomes `EXERCISE_SKIPPED` with the deferred reason. Two later movements is a full shuffle for a 4–6 movement session; a third “still busy” means the kit never freed.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `WAIT_VS_REST` (wait seconds ≤ this movement's rest seconds)

- **Where:** `respondToUnavailable`
- **Purpose:** If the wait is no longer than the rest already prescribed, staying costs nothing. Rest comes from `prescribedRestSec` else catalog `defaultRestSec`. Wait is `expectedWaitMinutes * 60`.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `SECONDS_PER_WORKING_SET = 90`

- **Where:** `estimateMinutes`
- **Purpose:** Seconds of lifting **plus** walking to the station, loading plates and getting set, for one working set. Added to the rest midpoint. 40s was only the concentric+eccentric and produced 33-minute "hour" sessions.
- **Source:** `MVP HEURISTIC` — ~30–45s of lifting, ~45s of setup/loading. Still ignores a lost plate and a queue.
- **Configurable:** No.

### `WARMUP_PULSE_MIN` / `WARMUP_DRILL_MIN` / `WARMUP_RAMP_SETS` (removed)

- **Where:** were `warmupSeconds` in `Estimate.kt`
- **Purpose:** Placeholder minutes for a warm-up that did not exist. Replaced by `planWarmup` and `WarmupPlan.estimatedMinutes`.
- **Source:** retired. See `WARMUP_CAP_MIN` and friends.
- **Configurable:** No.

### `PULSE_MIN_SEC = 180` / `PULSE_MAX_SEC = 300`

- **Where:** `planWarmup` / pulse drills' `durationSec`
- **Purpose:** Pulse raise lasts 3 to 5 minutes.
- **Source:** `PUBLISHED GUIDELINE` — Coach & AI Programming Guide §4.3; NSCA performance preparation.
- **Configurable:** No.

### `DRILL_COUNT_MIN = 3` / `DRILL_COUNT_MAX = 6`

- **Where:** `planWarmup`
- **Purpose:** Mobility, activation and rehearsal together. Cap 6. The min is a target; the 12-minute cap may cut below it.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3.
- **Configurable:** No.

### `RAMP_SETS_MIN = 1` / `RAMP_SETS_MAX = 3` / `RAMP_SETS = 2`

- **Where:** `planWarmup` / `RampSet`
- **Purpose:** Specific warm-up sets on the first primary lift. Two is inside 1 to 3. Effort is relative ("about half your working weight"), never a kilogram.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3 / NSCA. The relative wording is `CONTRACTUAL` (no invented loads).
- **Configurable:** No.

### `RAMP_SET_SEC = 45`

- **Where:** `warmupMinutes`
- **Purpose:** Clock time for one ramp set. Shorter than a working set because the load is not the work.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `WARMUP_CAP_MIN = 12`

- **Where:** `planWarmup`
- **Purpose:** If the plan would exceed about 12 minutes, cut drills, not the pulse.
- **Source:** `PUBLISHED GUIDELINE` — Guide §4.3.
- **Configurable:** No.

### `STRETCH_COUNT = 3`

- **Where:** `suggestStretches`
- **Purpose:** Two or three post-session static stretches, not a routine.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `DURATION_TOLERANCE_MIN = 15`

- **Where:** `clampToTime` / `withinDurationWindow`
- **Purpose:** Keep estimated duration within typical session length ± 15 minutes.
- **Source:** `CONTRACTUAL` — `contracts/16` step 3.
- **Configurable:** No.

### `DEFAULT_DURATION_MIN = 60` / `DEFAULT_DAYS_PER_WEEK = 3`

- **Where:** `buildWeek` / `chooseTemplate`
- **Purpose:** Defaults when intake did not record them. Intake requires both before `Ready`; the defaults exist so `buildWeek` is total.
- **Source:** `CONTRACTUAL` for 60 (contract 16 “default 60 if unset”); `MVP HEURISTIC` for 3 days.
- **Configurable:** No.

### `MIN_EXERCISES_PER_DAY = 2` / `MIN_SETS = 1`

- **Where:** `buildWeek` / `clampToTime`
- **Purpose:** Fewer than two remaining exercises is `CannotBuild`, not a thin plan. Sets never drop below 1.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### Templates (full-body-3d-beginner, full-body-3d, upper-lower-4d, ppl-6d)

- **Where:** `knowledge/templates/`
- **Purpose:** The only source of slot layout. Balanced by pattern. Boring on purpose. Beginner full body is one lower pattern per day (squat / hinge / lunge), core last. Empty slots try `PatternSiblings.MAP` once before dropping.
- **Source:** split idea from the Programming Guide; slot lists and sibling map `MVP HEURISTIC`.
- **Configurable:** No.

### `MINUTES_PER_SLOT = 12` / `EXTRA_RIR_WHEN_COOKED = 1`

- **Where:** `adjustForToday`
- **Purpose:** Clock-based `ShortenSession` keeps `minutesAvailable / 12` movements (at least one) and drops from the end. Cooked work adds one extra rep in reserve; spoken as “leave a couple more reps in the tank,” not as a field name.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `WEIGHT_VARIETY = 2.0`

- **Where:** `rank` / `variety`
- **Purpose:** Penalise a lift that appears in `recentSlugs`. Full 2.0 for any hit. Was 1.5 divided by `(index + 1)`, which made Monday's pick a rounding error by Thursday, so Upper B copied Upper A. 1.5 also tied the goal-mismatch gap (2.0 vs 0.5), so slug re-picked the same dumbbell press. 2.0 lets an unused alternative win. `buildWeek` prepends each day's kept slugs before ranking the next day.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `WEIGHT_PREFERENCE = 0.8`

- **Where:** `rank` / `preference`
- **Purpose:** Soft bump for a previously accepted substitution. Cannot override safety.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `WEIGHT_ROLE_FIT = 1.0`

- **Where:** `rank` / `roleFit`
- **Purpose:** Compound for PRIMARY, isolation for ISOLATION, core pattern for CORE.
- **Source:** `MVP HEURISTIC`.
- **Configurable:** No.

### `SESSION_SETS_PRIMARY_BEGINNER = 2` / `SESSION_SETS_PRIMARY = 3` / `SESSION_SETS_SECONDARY_BEGINNER = 2` / `SESSION_SETS_SECONDARY = 3` / `SESSION_SETS_ACCESSORY = 2` / `SESSION_SETS_ISOLATION = 2` / `SESSION_SETS_CORE = 2`

- **Where:** `dose` / `sessionSets`
- **Purpose:** Per-slot set count. Invariant: PRIMARY ≥ SECONDARY at the same training age. Beginner secondary was 3 while primary was 2; that is inverted. Weekly hard-set landmarks stay with the validator.
- **Source:** `MVP HEURISTIC` — contract 16 says beginners get fewer sets via the template column; this is the picker-side analogue, not a measured dose.
- **Configurable:** No.

### `RECENCY_QUESTION` / `RECENCY_URGENCY`

- **Where:** `ReferralCopy.RECENCY_QUESTION`, `ReferralCopy.RECENCY_URGENCY`
- **Purpose:** Follow-up on acute-symptom flags. Urgency line is added only when the user answers YES to recency. UNKNOWN is not YES. No emergency number in domain copy.
- **Source:** `SAFETY REQUIREMENT` — conservative; the app does not judge severity. Recency is the user's statement. `UNKNOWN` skip includes the urgency line (same as `YES`); only `NO` omits it.
- **Configurable:** No. The question text is product copy, not a 7-day science constant.

## Voice interaction (contract 35)

Policy only. No vendor, no audio implementation. The numbers are how long music stays ducked and how long the coach still trusts the last thing it heard.

### `WINDOW_CLOSED_ANSWER_MS = 8000`

- **Where:** `windowFor(CLOSED_ANSWER)`
- **Purpose:** After a closed answer ("Yes, stay at eighty.") keep the window open long enough for "ok" / "wait", not long enough that music stays quiet for a whole extra breath. Upper end of the contract's 5–8 s range.
- **Source:** `PRODUCT DECISION` — ducking is felt every rest. Forty seconds of quiet music after a closed answer is the failure mode.
- **Configurable:** No.

### `WINDOW_SHORT_QUESTION_MS = 10000`

- **Where:** `windowFor(SHORT_QUESTION)`
- **Purpose:** A direct question with a short answer ("How many?"). Upper end of 8–10 s.
- **Source:** `PRODUCT DECISION`.
- **Configurable:** No.

### `WINDOW_OPEN_QUESTION_MS = 40000` / `WINDOW_USER_INITIATED_MS = 40000`

- **Where:** `windowFor(OPEN_QUESTION)`, `windowFor(EntryPoint)`
- **Purpose:** Open questions and unprompted user entry. Forty seconds is the ceiling from the prompt, not a default for every turn.
- **Source:** `PRODUCT DECISION`. Same cap for wake word, earbud, and on-screen so the three ways in are equivalent.
- **Configurable:** No.

### `WINDOW_HARD_CAP_MS = 60000` / `WINDOW_SPEECH_EXTENSION_MS = 5000`

- **Where:** `onSpeechDetected`
- **Purpose:** If they are still talking, do not cut them off. Pad five seconds from last detected speech; never past 60 s. Music is ducked for the whole window.
- **Source:** `PRODUCT DECISION`. Endpointing 800–1200 ms (contract 03) is a pause detector, not a turn cap. 60 s is a cost/feel ceiling, unmeasured.
- **Configurable:** No.

### `CONFIDENCE_HORIZON_MS = 300000`

- **Where:** `maySpeakProactively`, `proactiveUtterance`
- **Purpose:** After this long with no user utterance and no logged event, the coach no longer knows whether they are resting, mid-set, queuing, or gone. Equals `STRENGTH_REST_SEC_MAX` so a published strength rest can still get a rest-complete prompt. At 1×: one re-sync. At 2× and 5×: silence.
- **Source:** `PRODUCT DECISION`. A missed prompt is a minor annoyance. Talking under a loaded bar is a safety failure.
- **Configurable:** No.

### `REST_DECAY_EXTENSION_SEC = 30`

- **Where:** `restAdjustment`
- **Purpose:** Last set dropped reps → add thirty seconds. The prompt's example. Never shortens below the goal's published rest minimum.
- **Source:** `PRODUCT DECISION`. Senna/Grgic/Singer give rest *ranges*, not a within-session bump. The 30 is ours.
- **Configurable:** No.

### `BARGE_IN_CANCEL_P95_MS = 200`

- **Where:** `VoiceInteractionConstants`; asserted in `VoiceTurnMachineTest` and `VoiceInteractionTest`
- **Purpose:** The window layer must not add latency to barge-in cancel. Same budget as the existing voice/latency row.
- **Source:** `SAFETY REQUIREMENT` — product §12; KD11. Unchanged.
- **Configurable:** No.

### `REVIEWED = 2026-09-02` (voice interaction)

Review date for this block. Not a behaviour constant.

## Cardio and conditioning (contract 36)

Modalities and protocols are stored separately and composed. Protocol structures are `MVP HEURISTIC`. The weekly minute target is the only `PUBLISHED GUIDELINE` in this block.

### `WEEKLY_MODERATE_MIN = 150` / `WEEKLY_MODERATE_MAX = 300`

- **Where:** `weeklyCardioTarget`
- **Purpose:** Adult health cardio volume when endurance, fat-loss or general fitness is the goal and the user is not a beginner.
- **Source:** `PUBLISHED GUIDELINE` — ACSM: 150–300 minutes moderate-intensity aerobic activity per week, or 75–150 vigorous.
- **Configurable:** No.

### `WEEKLY_VIGOROUS_MIN = 75` / `WEEKLY_VIGOROUS_MAX = 150`

- **Where:** `weeklyCardioTarget` (cited; the function returns the moderate band or the beginner/strength band, not a separate vigorous clock)
- **Purpose:** The vigorous equivalent of the ACSM moderate band. Recorded so nobody invents 200 minutes of sprints.
- **Source:** `PUBLISHED GUIDELINE` — ACSM, same statement as the moderate pair.
- **Configurable:** No.

### `WEEKLY_BEGINNER_MIN = 75` / `WEEKLY_BEGINNER_MAX = 150`

- **Where:** `weeklyCardioTarget` for `TrainingAge.BEGINNER`
- **Purpose:** Start below the adult floor. ACSM states 150; we do not dump 150 minutes on week one.
- **Source:** `GUIDELINE-DERIVED` — ACSM floor is 150 moderate. The 75 is ours.
- **Configurable:** No.

### `WEEKLY_STRENGTH_MIN = 75` / `WEEKLY_STRENGTH_MAX = 150`

- **Where:** `weeklyCardioTarget` for `STRENGTH` / `HYPERTROPHY`
- **Purpose:** Concurrent training. Keep cardio in a lower band so lifting stays primary. Easy aerobic around the week, not through it.
- **Source:** `GUIDELINE-DERIVED` — ACSM health target still exists; the reduced band is our concurrent-training stance.
- **Configurable:** No.

### `DURATION_STEP_MIN = 5`

- **Where:** `nextCardioSession`
- **Purpose:** First progression lever. Five minutes longer, same effort, same days.
- **Source:** `MVP HEURISTIC`. ACSM/NSCA say one variable at a time, not a five-minute step.
- **Configurable:** No.

### `FREQUENCY_MIN = 2` / `FREQUENCY_MAX = 5`

- **Where:** `nextCardioSession`, `placeOnWeek`
- **Purpose:** Second progression lever, and a cap on how many cardio sessions a week will grow to.
- **Source:** `MVP HEURISTIC` on the cap. ACSM frequency is “most days”; 5 is ours.
- **Configurable:** No.

### `REVIEWED = 2026-09-02` (cardio)

Review date for this block. Protocol work/recover lengths (`workSec`, `recoverSec`, `rounds`) live on the protocol rows and are `MVP HEURISTIC` — starting shapes, not measured intervals.

---

★ = convertible to `MEASURED` by the Voice Evidence MVP.

Three constants are declared but **not wired to anything**: `FIRST_SET_TIMER_SEC`,
the endpointing range, and the coaching temperature. They describe intended
behaviour that does not exist yet. That is not drift — the contracts that own them
are still `specified` — but it should not be mistaken for implemented behaviour.
