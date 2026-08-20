# GymBuddy — Stage Assessment & Build Plan

**Verified:** 2026-08-20, 14:48 run · 228 files read · 220 tests, 0 failures · `check_living_spec.py` exit 0 · 57 goldens

---

## Headline

**The kernel is complete.** PR 0 through PR 9 all exist, with code, tests, and goldens. The spec calls PR 9 "first runnable companion (no pixels)" — you are past it. `CompanionLoop` runs the full turn end to end: transcript → safety → intent → candidates → validation → engine → event log → projector → shouldSpeak → phrase → persist.

Since my study an hour ago, PRs 6, 7, 8 and 9 landed. Something is writing to this folder continuously — goldens went 47 → 51 → 57 while I was reading.

---

## Stage map — every PR, with evidence

| PR | Scope | State | Evidence on disk |
|---|---|---|---|
| **0** | Living spec, product law, frozen schemas, 28 contracts, `AGENTS.md`, checker | ✅ **done** | 28 contracts, 18 schemas in `v1/`, `check_living_spec.py` (22 KB) exits 0 |
| **1** | `CandidateEvent` union, schema gate, validator, `ValidatedFact` | ✅ **done** | `DefaultCandidateValidator` (348 ln), 18 executable PR-1 goldens, `CandidateValidatorRulesTest` (12), `Pr1GoldenRunnerTest`, `ValidatedFactGuardTest` |
| **2** | State machine, projector, session lifecycle service | ✅ **done** | `WorkoutEngine` (549 ln), `WorkoutProjector` (244 ln), `WorkoutTransitionContract`, `WorkoutEngineTest` (16), `Pr2GoldenTest` (4) |
| **3** | Local persist, fixture user + workout | ✅ **done** | `RoomEventLog`, `WorkoutEventCodec`, `SchemaMigrator`, `PersistFixtures`; 42 persist tests incl. concurrency, recovery, equivalence |
| **4** | Four-context assembler, epistemic stance | ✅ **done** | `FourContextAssembler` (360 ln), `FourContextAssemblerTest` (15), `Pr4GoldenRunnerTest`, 5 × `08-context` goldens |
| **5** | Voice ports + fakes, `VoiceTurnState`, barge-in | ✅ **done** | `VoiceTurnMachine` (208 ln), 5 fakes, `VoiceTurnMachineTest` (22), `Pr5GoldenRunnerTest` (5), 4 × `27-music` goldens |
| **6** | Safety decision-table classifier + veto | ✅ **done** | `DefaultSafetyClassifier` (10.6 KB), `SafetyFirstTurn`, `SafetyResponsePolicy`, 6 × `11-safety` goldens, 3 test classes |
| **7** | Extraction + multi-label intent + fallback matrix | ⚠️ **done on fixtures** | `NaturalLanguageExtractor`, `RulesFirstIntent`, `EnglishLastSetFallback` (10.5 KB regex cascade), `SchemaConstrainedFakeExtractor` — **the "LLM" is a 2-entry hardcoded map** |
| **8** | Correction reconciliation wired through the loop | ✅ **done (thin)** | `Pr8CorrectionIntegrationTest` — 1 test; the real work was already in PR 1's validator |
| **9** | Headless loop, `shouldSpeak`, `recommend()` | ✅ **done** | `CompanionLoop` (14 KB), `ShouldSpeak`, `Recommend`, `CoachingPhraser`; 8 × `09-coaching` + 2 × `02-loop` goldens; `CompanionLoopTest`, `ShouldSpeakTest`, `Pr9GoldenRunnerTest` |
| **10** | Gym Mode HUD binder | ⬜ not started | `app/` has only a README; not in `settings.gradle.kts` |
| **11** | Conversation caption | ⬜ not started | — |
| **12** | Auth bind | ⬜ not started | — |
| **13** | Onboarding + profile | ⬜ not started | — |
| **14** | Exercise bank ≥80 + templates | ⬜ not started | **blocking** for 15, 16, 17, 28 |
| **15** | Plan generation + editor | ⬜ not started | — |
| **16** | Substitution engine | ⬜ not started | 1 thin golden exists; `SubstitutionPropose` fact exists; no engine |
| **17** | History awareness | ⬜ not started | — |
| **18** | Dashboard | ⬜ not started | correctly last |
| **19–21** | Notes, summary, progress/recovery | ⬜ not started | — |
| **22** | Backup sync | ❌ cut from MVP (Q11) | — |
| **23** | Android AudioFocus adapter | ⬜ not started | vendor-independent, could start now |
| **24** | Real STT/TTS/LLM adapters | ⬜ blocked | Q3–Q5 open |
| **25** | Observability + hard CI gate | ⬜ not started | — |
| **26** | Noise/code-mix/offline eval harness | ⬜ not started | — |

---

## What "kernel complete" actually buys you

You can run this today, headless, with zero vendors:

```
loop.start()                        → SESSION_INITIALIZED, WORKOUT_STARTED, EXERCISE_STARTED, greeting
loop.seedWorkingWeight(60.0)
loop.handleTurn("Got 10.")          → SET_COMPLETED 60×10, speaks "Nice."
loop.handleTurn("Got 11.")          → USEFUL_DELTA, speaks "Nice. That's one more than last time."
loop.handleTurn("No, 8.")           → SET_CORRECTED on the same setId, speaks "Got it."
loop.beginSet()
loop.handleTurn("How much did I do last week?")  → silence, ANSWER_QUESTION queued
loop.handleTurn("Got 9.")           → drains the queued answer in RESTING
loop.handleTurn("I got 10 but my chest hurts")   → commits the set, fires CRITICAL, pauses, speaks the frozen template
```

Every one of those is a passing test. That is a real companion loop. It is the thing the spec said to build first, and it got built.

---

## The five gaps that matter, in order

### 1. There is no version control. None.

`.gitignore` exists. `.git` does not. 228 files, ~6,000 lines of Kotlin, a 186 KB spec — in `Downloads`, with no history, no remote, no backup, and multiple AI agents writing concurrently.

Everything `AGENTS.md` says about "the same change", "PR blocking", "block merge when…" is currently unenforceable, because there are no changes and no PRs. The discipline system is a description of a process that isn't running.

One bad agent run overwrites work with no undo. This is the highest-severity item in the repo and it takes 90 seconds to fix.

### 2. The extractor's intelligence is two hardcoded strings

`SchemaConstrainedFakeExtractor.fixtures` is a `Map<String, String>` with exactly two keys:

- `"first set was 12 at 40, second was 10, and i got 8 on this one."`
- `"nenu 60 kilos tho eight reps chesa."`

Exact-match, lowercased. Everything else falls through to `EnglishLastSetFallback` — a regex cascade covering "got 10", "40 for 10", "no, 8", "scratch that", "skip", "one more", and a handful of question patterns.

This is legitimate for PR 7 as specified ("schema LLM **fake**"). But it means the two most impressive goldens — multilingual and multi-set catch-up — pass because their exact transcript is a dictionary key. Change "I got 8 on this one" to "and 8 on the last one" and the system produces nothing.

**You currently have zero evidence about real NLU.** That's not a criticism of the build; it's the honest read of where the risk sits.

### 3. `Recommend` can't do what §24 describes

The spec's example is cross-session: *"Last three sessions were 12, 12 and 11 at 50 kg. I'd try 52.5 today."*

`Recommend.suggest()` reads `snapshot.liveSetsOnCurrent()` — the last three sets **in the current session**, same exercise. It cannot see previous sessions at all. It also requires exactly: 3+ sets, identical weights, all reps ≥ 10, and last < first. Anything else returns `null`.

So the flagship progression feature is currently a narrow within-session heuristic, not the cross-session comparison the product promises.

### 4. Coaching text is fixed strings with no cite check

`CoachingPhraser` returns hardcoded sentences. `USEFUL_DELTA` always speaks **"Nice. That's one more than last time."** — even when the delta was one *fewer*, or a weight change rather than reps. It's asserted verbatim in `CompanionLoopTest.plusOneRepSpeaks`.

Separately, the spec's guardrail — *"if the cite check fails, strip the sentence"* — is not implemented. `persistCoaching` writes **every** `speechAllowed` fact id into `citedFacts` rather than the facts the sentence actually used. So the citation record is decorative right now; it can't catch an invented number because it isn't derived from the sentence.

The anti-hallucination *architecture* is real and excellent. The anti-hallucination *check* at the final step is not wired.

### 5. Queue drain discards the current turn's own act

In `CompanionLoop.handleTurn`:

```kotlin
} else if (queuedSpeechActs.isNotEmpty() && snap.phase in DRAIN_PHASES) {
    val drained = queuedSpeechActs.removeAt(0)
    speak = SpeakDecision.Speak(drained, ...)   // overwrites whatever this turn earned
}
```

If a question was queued during a set, and the next turn logs a set that earns `USEFUL_DELTA`, the drained `ANSWER_QUESTION` **replaces** it. The delta ack is not re-queued — it's dropped. `CompanionLoopTest.questionDuringLiftDeferred` confirms this is current behaviour (a set is logged on the drain turn and only the answer is spoken).

Probably fine as a product call — answering the human beats acknowledging a rep. But it's undocumented, and the alternative ("Nice, one more. And last week you did 60 for 10.") is arguably the better trainer.

---

## Smaller findings

| # | Finding | Where |
|---|---|---|
| 6 | **7 contracts have contradictory status** — frontmatter `specified`, body `implemented`: 01, 02, 04, 05, 06, 07, 09. The checker reads frontmatter, so your drift gate under-reports by seven. | `docs/living-spec/contracts/` |
| 7 | **Contract 12 (multilingual) still `specified`** although the Telugu path shipped and `kaadu-8` passes. Drift in the other direction. | `contracts/12-*.md` |
| 8 | **`check_living_spec.py` will fail the moment PR 10 starts** — it errors on any `.kt` under `app/` ("PR 0 must not start UI"). Must be updated in the same change as the first HUD file. | `scripts/check_living_spec.py` |
| 9 | **Golden runners use `when` with no `else`** — a golden whose `expect` shape matches no branch is silently skipped while still counting toward the total. | `Pr9GoldenRunnerTest`, others |
| 10 | **`WorkoutEngine.commit()` re-folds the whole event log up to 3× per event** — quadratic in session length. | `WorkoutEngine.kt` |
| 11 | **Room contract unverified by Room** — `@Dao`/`@Database` annotations exist but execution is a hand-written `JdbcWorkoutEventDao`; the Room compiler has never run. | `:persist` |
| 12 | **`answerHistory` reads `previousSessionsForExercise.firstOrNull()`** then `known.last()` — ordering isn't guaranteed most-recent-first. Risky for a product whose promise is accurate recall. | `CoachingPhraser.kt` |
| 13 | **`llmMayEmitAsCommittedTruth()` has a dead `when`** — every branch returns `false`. | `Enums.kt` |
| 14 | **Non-session events share one sequence space** (`sessionId ?: ""`). | `WorkoutEventCodec` |
| 15 | **No `gradlew` (unix), only `gradlew.bat`** — CI on Linux/macOS won't work as-is. | repo root |

---

## Recommended plan from here

### Right now, before anything else (30 minutes)

1. **`git init`, commit, push to a private remote.** Non-negotiable. Everything below assumes you can undo.
2. **Fix the 7 contract status fields**, and add a checker rule asserting frontmatter == body. Your drift gate should not be lying to you.
3. **Add `gradlew`** (unix wrapper script) so the project builds off Windows.

### Then — the decision fork

You are at the exact point where the spec's plan and the actual product risk diverge.

**The spec says next:** PR 10 (HUD binder) → PR 11 (caption) → PR 12/13 (auth, onboarding) → PR 14 (catalog).

**I'd argue for a different next step.** The kernel is proven on fakes. What is completely unproven is whether the voice half works at all — and every remaining PR assumes it does. Specifically:

> Can a real STT engine hear "sixty for ten", "twelve, ten, eight", "Nenu 60 kilos tho eight reps chesa", and "No, eight" — over a headset, in a loud gym, with music ducking — accurately enough that the kernel's guarantees mean anything?

Nothing in 220 passing tests answers that. Two hardcoded fixtures are standing in for the hardest problem in the product.

**Proposal — PR 7.5: the evidence spike (2–3 days).** Outside `:domain`, throwaway, no port violations:

- Record 60–100 real utterances: numbers, weights, exercise names, English + Telugu + code-mix, quiet room and gym-noise, with and without music.
- Run 2–3 candidate STT vendors over them (this also resolves Q3).
- Measure exactly what §49 says matters: number accuracy, weight accuracy, exercise-name accuracy, mixed-language accuracy.
- Feed the transcripts through the **existing** `CompanionLoop` and count how many turns produce the right event.

That last step is the whole point — you already have the machinery to score it. The output is a number that either de-risks the product or tells you to change the interaction model early, while changing it is still cheap.

It also resolves **Q3** and gives PR 24 a decided vendor instead of a blocked one.

### After the spike, in order

4. **PR 14 — the ≥80 exercise catalog + templates.** It's unglamorous, it's not an engineering problem, and it blocks PRs 15, 16, 17 and half of 28. Q9 (content licence) is still open. Start it in parallel with everything else — it's the classic thing that slips.
5. **PR 10 — HUD binder.** Now worth building, because you'll know what the voice layer can actually deliver. Remember to update `check_living_spec.py` in the same commit.
6. **PR 23 — Android AudioFocus.** Vendor-independent, so it isn't blocked by Q3–Q5, and music coexistence is a core product requirement that's currently only faked.
7. **PR 15 → 16 → 17** once the catalog lands.
8. **PR 18 dashboard, last**, exactly as the plan says.

### Fixes to fold into whatever you touch next

- Wire the real cite check in `persistCoaching` (facts used in the sentence, not all allowed facts).
- Give `Recommend` access to `HistoricalStore` so it can do the cross-session comparison §24 describes.
- Make `CoachingPhraser` phrase from the actual delta rather than a fixed string.
- Add an `else -> fail("unmatched golden shape")` to every golden runner.
- Thread one snapshot through `WorkoutEngine.commit()` instead of re-folding.

---

## What I'd want from prior agents (and what I don't need)

**I don't need a handoff to understand the code.** The repo is unusually self-documenting — `AGENTS.md`, `MODULE_BOUNDARIES.md`, `PR1_VS_PR2.md`, and the contracts explain not just what things do but why the boundaries exist. I've read all 228 files and I can work in it directly.

**Five things I genuinely cannot recover, and would save real time:**

1. **What was tried and abandoned.** With no git history, I can't tell whether e.g. the occupancy-clarify rule or the 20% weight-delta threshold replaced something that failed, or was right first time. I risk re-proposing a rejected idea.
2. **Where the magic numbers came from.** The 8-second first-set timer, 250 ms minimum utterance, 0.55 confidence floor, 120-second cross-exercise correction window, 20% weight-delta bar, 10-second duplicate window. Were these measured, borrowed, or invented? It changes whether I should tune them or defend them.
3. **Whether anything is running right now.** Files changed *while I was reading them.* If another agent is mid-PR, I need to know before I write anything, or we'll clobber each other.
4. **Any product decisions made in chat that never reached `PRODUCT.md`.** The spec says `PRODUCT.md` wins on product behaviour — if you settled something verbally, it's invisible to me and to every future agent.
5. **Q3–Q5 and Q9 context.** Have you already ruled vendors in or out on cost, latency, or Indic-language support? Same for the exercise-catalog licence — if you've found a source, that unblocks PR 14 immediately.

If you have transcripts or notes from the earlier sessions, points 1, 2 and 4 are the valuable ones. If you don't, that's fine — I'd just want us to start recording decisions in the repo from here, which is what `git init` plus a short `DECISIONS.md` gets you.

**One thing I'd ask you directly:** is another agent writing to this folder right now? I can see the timestamps moving, and I don't want to build on top of half-landed work.
