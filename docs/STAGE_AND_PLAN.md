# GymBuddy — Stage Assessment

**Verified 2026-09-01** by reading the repository. Replaces the 2026-08-20
assessment, which was stale (it claimed PR 10 was not started and that there was
no version control; both were false).

Counts: 96 Kotlin sources in `:domain` / `:persist` / `:app`, 64 test files,
64 goldens, 33 contracts, 39 commits, 77 exercises.

---

## Part 1 — Done and solid

### The kernel (PR 0–10)
The voice loop runs end to end, headless, with no vendors:
transcript → safety → intent → candidate → validation → state machine →
append-only event log → projector → shouldSpeak → phrase → persist.

`implemented` contracts: 07 correction reconciliation, 08 four-context assembly,
09 coaching response generation, 11 safety layer, 27 music/audio focus.

Kotlin packages carrying it: `events`, `validation`, `state`, `engine`,
`context`, `loop`, `safety`, `voice`, `extract`, `persist`.

### The coach brain (built 2026-09-01)
Five contracts, all `implemented`:

| Contract | What it does |
|---|---|
| 29 programming rules | Sets, reps, rest, effort, frequency, deload, ordering, progression, plus a plan validator. Every number carries provenance. |
| 30 health screening and referral | Red-flag screen, referral copy, unknown-is-not-no, conditions declarable, contraindication path wired end to end. |
| 31 exercise selection | Filter → score → rank for one slot, with reasons. Safety exclusions non-overridable. |
| 32 in-session adaptation | Readiness, the busy-machine ladder, `EXERCISE_DEFERRED`. |
| 33 programme templates | Template schema and the four splits. |

Packages: `programming`, `intake`, `picker`, `adapt`, `plan`, `catalog`.

### The knowledge base
`knowledge/` holds 4 vocabularies (muscles, patterns, equipment, conditions),
2 schemas, 4 templates, and 77 exercises. Equipment is modelled as slots of
alternatives. Every field carries provenance and the build fails if a constant
has none.

### The provenance discipline
`DECISIONS_AND_THRESHOLDS.md` classifies every constant in the repo:
20 `PUBLISHED GUIDELINE`, 8 `GUIDELINE-DERIVED`, 29 `MVP HEURISTIC`,
10 `SAFETY REQUIREMENT`, 19 `CONTRACTUAL`, 3 `PRODUCT DECISION`. Nothing is
`MEASURED` for training science, correctly. This is the most unusual thing about
the codebase and the thing most worth protecting.

### What the app can actually do today
Given a person with no equipment and no idea: screen them for red flags, refer
them if needed, build a week that a coach would sign off, explain every choice,
prescribe sets/reps/rest/effort without inventing a load, warn when a week
overshoots, and adapt when the rack is busy or their legs are wrecked.

---

## Part 2 — Done but unproven

**Everything above runs headless against fakes.** The kernel has never heard a
real microphone. The STT, TTS and LLM ports exist (`voice/VoicePorts.kt`,
`ai/AiPorts.kt`) and every implementation behind them is a fake or a fixture.

`voice-workout-os-source/` is the only half of the product that has ever run
live with a real mic — and it is a separate, untracked, vanilla-JS codebase with
weaker state guarantees.

---

## Part 3 — Pending

### 3.1 Vendors — the immediate blocker
`Q3` (STT), `Q4` (TTS), `Q5` (LLM) are all **open**. Research is done:
`docs/research/voice-stack-costs-2026-09-01.md` and the `voice-vendor-notes`
memory. Decisions taken: cloud-first, no on-device, no RAG, two models (cheap
for extraction, major for open questions), off-topic answered between sets with
medical routed to referral.

**Not yet done: the measurement.** Code-switching accuracy on real gym audio is
the number that decides the architecture and the cost model, and no desk
research answers it.

### 3.2 The app — almost everything user-facing
`:app` has 14 sources: Gym Mode activity, HUD binder and screen, theme, Android
event log, and the audio diagnostic. That is the in-workout HUD and nothing else.

Never built (`specified`, no code):

| Contract | Feature |
|---|---|
| 13 | Authentication |
| 14 | Onboarding |
| 15 | Profile |
| 18 | Exercise bank and detail UI |
| 20 | Workout history |
| 21 | Progress and insights |
| 22 | Muscle map and recovery UI |
| 23 | Automatic notes and memory |
| 24 | Workout completion and post-workout |
| 25 | Conversation UI |
| 26 | Dashboard |

Note on 22: the recovery **model** exists in `voice-workout-os-source/` and the
muscle map **data** is in `knowledge/vocab/`. The UI and the port are what is
missing.

Partially done (`implementing`): 01–06 lifecycle/loop/voice/parsing/extraction/
state machine, 10 local-first, 12 multilingual, 16 plan generation (engine yes,
UI no), 17 plan editing (no editor), 19 substitution (ranking yes, proposal flow
partial), 28 gym-mode QA.

### 3.3 Content domains never started
The uploaded corpus treats these as first-class. The product has none of them:

- **Warm-ups.** Duration is modelled; content is not. No contract exists.
  `voice-workout-os-source/` has a warm-up planner that was never ported.
- **Cardio and conditioning.** The corpus carries 236 cardio records and the
  architecture doc makes it a first-class domain. The catalog has zero.
- **Stretching and mobility.** Same.
- **Nutrition.** In the corpus, in scope-of-practice terms only. Not started,
  and the boundaries need deciding before it is.

### 3.4 Known gaps, written down
- **No progression concept** (contract 31). Nothing moves a user from push-ups
  to dumbbells over eight weeks.
- **Bodyweight lifts cannot be loaded**, so a strength-goal rep prescription on
  them is not achievable as written (`Push-up 3×5–8` for an intermediate).
- **Catalog thin at the home and bodyweight presets** — several patterns still
  have a single legal option there.
- **Tier 1 safety data is AI-written and clinically unreviewed** — 37
  contraindication rows and the `consequenceOfFailure` column. See
  `docs/research/judgement-audit-2026-09-01.md`.
- `unilateral` unverified on all 77 (affects the volume ledger).
- The seven scoring weights are uncalibrated.
- `neck.group` is `"upper"`, a seventh group outside the six body groups.

### 3.5 The two codebases
`voice-workout-os-source/` is untracked and holds work gymbuddy does not: a
working live voice loop, a 24-region body map, the per-muscle recovery model, a
warm-up planner, and forgiving free-text programme import. Some of that needs
porting; some is superseded. That decision has not been made.

---

## Part 4 — What to do next, in order

1. **One hour with a physio** on the 37 contraindication rows and the
   `consequenceOfFailure` column. The only judgement in the system that can hurt
   someone, currently unreviewed.
2. **An afternoon in a gym** measuring speech accuracy on natural phrasing,
   code-mixed Telugu/English, out of breath, with music and plates.
3. **Pick the vendors** and build the first real adapter behind the existing
   ports. Closes Q3–Q5.
4. **Then the app**: onboarding and profile first (contracts 14, 15) — they feed
   the intake layer that is already built and currently has no way to be filled.

Items 1 and 2 cannot be delegated to a coding agent, are cheap, and have been
deferred repeatedly. Everything in Part 1 is worth very little until item 2 has
an answer.
