---
id: 37-pain-triage
status: implemented
schemaVersion: 1
source: docs/prompts/pr-pain-triage.md
---

## Feature: In-session pain triage

**Status:** implemented  
**Core-loop role:** The missing conversation from "that hurt" on set two to a proposal the user accepts. Safety (contract 11) still owns raw-transcript chest/faint/SOB. Intake already has character. This package asks six questions, adaptively, and feeds the picker, sibling map, and `adjustForToday` machinery that already exist.

### Design decision

Do **not** triage on a 0–10 pain score. Intensity is a weak signal: severe soreness can be an 8, a significant tear can be a 4, a 3 that radiates is more serious than an 8 that is a cramp. Character and location (and the six questions) predict seriousness. There is no clinical standard that 7/10 means see a doctor; shipping that threshold would be a medical judgement.

**Keep the number for tracking, never for routing.** `triage` does not read `intensity`. "Was a six last week, now a three" is history. It is not a gate.

### The six questions

Asked in this order. Stop the moment the outcome is determined. Most cases resolve in two or three. Someone mid-workout will not sit through six.

| # | Id | Spoken | Separates |
|---|---|---|---|
| 1 | `SPOT` | Where is it — one spot you can point to? | Local vs diffuse |
| 2 | `ONSET` | Did it hit at a moment, or build up over the day? | Acute vs soreness |
| 3 | `SIDES` | Both sides, or just one? | Bilateral vs one-sided |
| 4 | `CHARACTER` | Sharp, or more of an ache? | Sharp vs ache |
| 5 | `TRAVEL` | Does it travel? Any pins and needles, or weakness? | **Red flag** |
| 6 | `AT_REST` | Worse when you move, or aching even at rest? | Mechanical vs constant |

Question 5 must always be reached before `LikelyTrainingSoreness`. If everything else looks benign but 5 is unanswered, the state is `NeedMore`, not soreness. Any unanswered question never produces the benign outcome.

### Outcomes (not diagnoses)

The type is `LikelyTrainingSoreness`, never `DOMS`. Type names leak.

- **Both sides + built up + dull/ache + question 5 clear + all six answered**, and the place is a muscle, joint, or spine → `LikelyTrainingSoreness`.
- **One side + a specific moment + sharp**, after question 5 is clear → `StopThisMovement`.
- **Travels, pins, weakness, radiating character, or wakes them at night** → `ReferBeforeContinuing` (`mayTrainOtherAreas` usually true). Spoken copy names what travels ("Pain that travels down the leg…"), not "that's travelling".
- **Chest pain, breathlessness, dizziness** → `UrgentReferral` with contract 30 flags. In-session chest has its own opening: "Stop. Pain in the chest isn't something we work through. Get this checked before we keep going." plus the shared urgency line (`ReferralCopy.RECENCY_URGENCY`). Contract 30 keeps "You marked chest pain" for intake. This is a second variant, not a rewrite of intake.
- Head or an unknown place never resolve to `LikelyTrainingSoreness`. Unknown is never fine.
- **Sudden severe headache during exertion** → `UrgentReferral` (`SUDDEN_SEVERE_HEADACHE`). Session over. Head + sudden onset, or the urgent flag, is the trigger — intensity is still not a gate. A built-up dull head ache is still `ReferBeforeContinuing`, not thunderclap.
- **Stroke signs, cauda-equina cluster, rhabdomyolysis cluster, hot red rapidly swelling joint** → `UrgentReferral` with their own contract 30 flags. Session over. Each has its own spoken line so the user can recognise the symptom. Do not collapse them into one generic warning. Do not append intake's recency line — they already told us they have it.

Two copy levels. **Only the wording differs.** Both are `UrgentReferral`; both empty the session. `EMERGENCY` is not a different outcome.

- **`EMERGENCY`** — hospital now, sit down, do not drive. Sudden severe headache, stroke signs, cauda equina cluster (time-critical in hours).
- **`SAME_DAY`** — hospital or doctor today. No ambulance framing. Rhabdomyolysis cluster, hot rapidly swelling joint.

**UNDECIDED — clinician sign-off pending.** The symptoms come from two reviews that agree. The wording does not have a clinician behind it. Structure is the decision (stop → sit → what to do → don't drive for `EMERGENCY`; stop → what to do → today for `SAME_DAY`). Sentences are provisional. See `docs/review/RESTRICTION-PLAN.md` Tier 3.

In-session lines (the whole spoken turn; not followed by `ReferralCopy.RECENCY_URGENCY`):

- Headache: "Stop and sit down. A headache that hits suddenly and hard during a lift needs emergency care, not rest. Get someone to take you to a hospital now. Don't drive yourself."
- Stroke signs: "Stop and sit down. Weakness on one side, a drooping face, slurred speech or sudden vision loss needs a hospital now. Get someone to take you. Don't drive yourself."
- Cauda-equina cluster: "Stop and sit down. Numbness around the groin, a new bladder or bowel problem, or both legs weakening fast needs a hospital now. Get someone to take you. Don't drive yourself."
- Rhabdomyolysis cluster: "Stop. Muscle pain far worse than this session should cause, swelling, or dark or tea-coloured urine needs checking at a hospital today."
- Hot joint: "Stop. A joint that's hot, red, and swelling fast isn't normal soreness. See a doctor today."

### What happens next (proposals)

Nothing mutates the plan until the user accepts.

| Outcome | Proposal |
|---|---|
| `LikelyTrainingSoreness` | Go lighter on that movement. "Start light and see how it feels — we can build up if it's fine." |
| `StopThisMovement` | Drop it for the session, offer a sibling-pattern alternative, exclude the slug from the picker for the rest of the session. |
| `ReferBeforeContinuing` | Stop that pattern. If `mayTrainOtherAreas`, the rest of the session stays. |
| `UrgentReferral` | Session over. Chest still uses the in-session chest opening plus the shared urgency line. Sudden severe headache, stroke signs, and cauda equina use `EMERGENCY` copy. Rhabdomyolysis and a hot red rapidly swelling joint use `SAME_DAY` copy. Breathlessness and dizziness still use contract 30 copy. |
| `NeedMore` | The next question. No adjustment. |

A `SessionPainRecord` is what next session's readiness reads: *"How's that shoulder today?"*

### User behavior

They twinge on set two. In v1 they **cannot** raise pain by speaking — the mic is not open unless the coach opened a window, and they cannot open one with their voice. Entry is the **on-screen emergency control** (contract 35). It opens a window immediately, out of schedule, and works whatever the session clock believes is happening, **mid-set included**. Someone in real pain is stopping anyway; reaching for the phone is acceptable.

Once that window is open, we ask until the outcome is determined. We propose. They accept. The six questions and the outcomes do not change.

### System state

`com.gymbuddy.domain.pain`. Pure. `PainInterview` holds answers (null = unanswered) and optional intensity. `triage` / `nextQuestion` / `propose`. `withExclusions` feeds `sharpPainSlugs` after accept.

### Voice behavior

**Emergency window, including mid-set.** Contract 35: the emergency control is the only user-initiated entry. It may speak during `SET_ACTIVE` because the user has already stopped to tap it. Safety ≥ high (contract 11) still wins if chest/faint/SOB is on the raw transcript. Same wording. No condition names. No `conditions.json` display names. In-session chest is `PainCopy.IN_SESSION_CHEST` plus the shared urgency line, never intake's "You marked chest pain". The five new red-flag lines are the whole turn — no recency suffix. `EMERGENCY` vs `SAME_DAY` is copy only.

Scheduled (non-emergency) pain talk still queues during `SET_ACTIVE` and drains on rest (contract 09). Test 10 describes that path. The v1 product path is emergency.

### Structured event

None until accept. Then skip / sets-changed / substitution as contract 32 / 19. No new event type.

### Database change

None in this PR. `SessionPainRecord` is the model for a later history writer.

### UI state

None. No `:app` change.

### Error / recovery behavior

Unknown / skipped question → `NeedMore`. Never `LikelyTrainingSoreness`. Intensity out of 0–10 dropped from history, route unchanged. Input session unchanged.

### Tests

Evidence is `:domain` Kotlin tests.

1. Intensity 9 with a benign profile does not refer.
2. Intensity 2 with radiating numbness does refer.
3. Question 5 unanswered never produces `LikelyTrainingSoreness`.
4. Any unanswered question never produces the benign outcome.
5. Chest / breathlessness / dizziness are `UrgentReferral` with contract 30 flags. In-session chest copy is not intake's "You marked chest pain"; the urgency line is kept. Sudden severe headache, stroke signs, cauda-equina cluster, rhabdomyolysis cluster, and a hot red rapidly swelling joint are the same **outcome** (session over) with two **copy** levels: `EMERGENCY` (sit, hospital now, don't drive) and `SAME_DAY` (hospital or doctor today). Word-for-word asserted. No intake recency suffix.
6. No output string contains a `conditions.json` name.
7. `StopThisMovement` adds a picker exclusion.
8. `ReferBeforeContinuing` with `mayTrainOtherAreas` still yields a legal reduced session.
9. Input session unchanged.
10. Queued during `SET_ACTIVE`, drained on rest.
11. A pain record appears in the next-session follow-up.
12. Spoken lines are plain language.
