---
id: 30-health-screening-and-referral
status: implemented
schemaVersion: 1
source: docs/prompts/pr-intake-and-screening.md
---

## Feature: Health screening and referral

**Status:** implemented  
**Core-loop role:** Gate before the loop is allowed to suggest a first plan. The kernel still does not generate workouts here (contract 16 remains one engine, template-based). This feature records what the user reported, applies conservative rules, and refers. It does not diagnose.

### Red-flag list

`CHEST_PAIN`, `BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION`, `DIZZINESS_OR_FAINTING`, `PALPITATIONS_OR_IRREGULAR_HEARTBEAT`, `RECENT_SURGERY`, `UNEXPLAINED_NIGHT_PAIN`, `RADIATING_NUMBNESS_OR_WEAKNESS`, `PREGNANCY_COMPLICATION`, `UNCONTROLLED_DIAGNOSED_CONDITION`, `NEW_UNEXPLAINED_SWELLING_OR_PAIN`.

Each answer is `YES | NO | UNKNOWN`. There is no boolean skip.

### Referral rule

- Any red flag `YES` that is not covered by an explicit dated user-reported clearance → `ReferBeforeTraining`. No plan-shaped output (`IntakeResult.Ready` is unreachable).
- Refer outranks missing required fields, **except** an unanswered recency follow-up on an acute-symptom flag. That follow-up is asked first so urgency can come from the user's stated recency, not from the app judging severity.
- Acute-symptom flags (`CHEST_PAIN`, `BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION`, `DIZZINESS_OR_FAINTING`, `PALPITATIONS_OR_IRREGULAR_HEARTBEAT`, `RADIATING_NUMBNESS_OR_WEAKNESS`, `NEW_UNEXPLAINED_SWELLING_OR_PAIN`) each have the recency question: “Is this happening now, or has it happened in the last week?” Skip is `UNKNOWN`, not yes. Only `YES` adds: “If you are feeling this right now, or it came on recently, please don't wait for a routine appointment - get seen today.” No emergency number in domain copy.
- Referral copy states the app's limit (a plan from this app has to wait), not a refusal to help.
- `ReferBeforeTraining.message` is calm, specific about the next step, free of diagnosis, and must not contain a `conditions.json` name. The “report, not a medical record” line belongs on clearance, not on referral.

### Unknown is not No

A skipped screening question is `UNKNOWN`. `UNKNOWN` never becomes `Clear`, never becomes `Ready`, and never behaves like `NO`. `AGENTS.md` forbidden item 9 applied to screening.

`HealthScreen` is **completed** only when every red flag is `YES` or `NO`, the listed-condition question is `YES` or `NO`, and the pain question is `YES` or `NO`. Completed-and-clear is required before a first plan (contract 14).

### No-diagnosis boundary

The app records the user's words. It does not name a condition they did not declare as a vocab id. Free text with no exact id or exact display-name match is stored as `DeclaredCondition.Unknown` — never a nearest-id guess. Referral copy and caution notes do not explain what the pain is.

### Clearance is user-reported

A referral is not permanent. The only writer is `recordClearance(screen, date, flags)` with a `YYYY-MM-DD` date. Evaluate never invents clearance. Profile (contract 15) treats clearing a referral as a deliberate user action. The acknowledgement is: the date is stored as the user's report, not a medical record. That sentence is not in the referral messages.

### User behavior

Visual, short, one question at a time (`NeedMore.nextQuestion` is exactly one). Skip is `UNKNOWN`, not no. After a referral, the user can later record that a clinician said they are clear, with the date.

### System state

`com.gymbuddy.domain.intake`. Three tiers: `REQUIRED_TO_SUGGEST` (goal, training age, days, minutes, equipment, completed health screen), `SAFETY` (red flags, declared conditions, pain reports), `SHAPING` (recovery, dislikes, `trainsWithPartner`, prior injuries). Missing SHAPING never blocks `Ready`.

`evaluate` returns `Ready(UserContext)` | `NeedMore` | `Refer`. `Ready` is the programming `UserContext` (declared known condition ids, equipment tokens, optional partner, sharp/radiating movement slugs). It is not a workout.

Declared vocab ids feed `validateSession` contraindication `BLOCK`. Sharp or radiating pain on a movement is `safety.pain-movement` `BLOCK`.

### Voice behavior

**Silent / not in Gym Mode.** Onboarding and screening are visual. No IVR, no spoken intake flow.

### Structured event

None of the workout catalog. Profile writes later. Not a live session event.

### Database change

None in this PR. Model only. Future profile rows: declared conditions, pain reports, equipment preset, `trainsWithPartner`, clearance date. `source=manual`.

### UI state

None. No `:app` change. A later onboarding PR must ask one question at a time and must not add a voice flow.

### Error / recovery behavior

Unknown answers keep the user in `NeedMore`. Live red flags keep the user in `Refer` until `recordClearance`. A thin equipment preset is reported as coverage counts, not silently turned into a week of training.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens — this PR emits no workout events.

1. Red flag `YES` → `Refer`; `Ready` unreachable.
2. Red flag `UNKNOWN` → `Incomplete`, never `Clear`.
3. Skipping every screening question is not `Ready`.
4. `Ready` unreachable while any `REQUIRED_TO_SUGGEST` field is absent.
5. Missing SHAPING never blocks `Ready`.
6. `BODYWEIGHT_ONLY` matches the bodyweight-capable subset of the 61.
7. `alsoHave` adds, `doNotHave` removes, `doNotHave` wins on conflict.
8. Declared condition reaches the validator `BLOCK`.
9. Sharp pain on a movement is a SAFETY `BLOCK` for that movement.
10. Free-text with no vocab match is `Unknown`, never a near-miss id.
11. `NeedMore` returns exactly one `nextQuestion`.
12. Clearance requires an explicit dated action; evaluate does not infer it.
13. No `ReferBeforeTraining.message` contains a `conditions.json` name.
