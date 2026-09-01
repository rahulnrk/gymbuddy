# PR — Intake and health screening (what the coach must know before it speaks)

## Why this PR exists

The catalog knows which exercises are contraindicated for a
flexion-intolerant back. The validator will `BLOCK` a plan containing one. But
**nothing in the app can record that the user has a flexion-intolerant back.**
`knowledge/vocab/conditions.json` has no writer. The safety gate is wired to a
sensor that does not exist.

`contracts/14-onboarding.md` captures name, age, sex, height, weight, units,
experience, goals, days, duration, equipment, body-map variant and language. It
captures **nothing about pain, injury, medication or medical history**, and has
no referral path. For a product that tells people to lift heavy things, that is
the single largest hole in the specification.

The programming rules also need inputs that have no source: `TrainingAge`,
`RecoverySignals`, and now whether a spotter is available.

This PR defines the intake layer: what must be known, what blocks, what refers,
and what merely shapes the plan.

## Two carry-overs first

Small, do them in commit 1:

1. `STRENGTH_BEGINNER_HARD_SETS = 3–8` is still `PUBLISHED GUIDELINE (low
   confidence)`. It rests on the same Peterson session-vs-week ambiguity as the
   beginner hypertrophy row. Move it to `GUIDELINE-DERIVED`. You were right to
   flag it.
2. In `contracts/29-programming-rules.md`, record that the unspotted-rep-floor
   rule keys off the exercise listing a catch implement, **not** off
   `consequenceOfFailure` alone — and that this must be re-examined when the
   catalog grows past 61, since a future lift could be high-consequence,
   non-droppable and list no catch.

## Scope guard

This PR builds the **model and the rules**. It does not build screens.

Builds:
- the intake data model in `:domain`
- the completeness gate: may we suggest anything yet?
- the health screen and its referral outcomes
- `conditions.json` as a declarable thing
- the readiness/recovery inputs the rules layer already expects

Does **not** build:
- any `:app` UI, any onboarding screens
- a plan generator (still PR 15, still template-based)
- voice onboarding. `contracts/14-onboarding.md` is explicit: onboarding is
  **visual and silent, not in Gym Mode**. Do not add an IVR flow.
- anything that diagnoses

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL.
- Living spec updates in the same change.
- **Unknown is not No.** `AGENTS.md` forbidden item 9 already says
  `estimated` / `inferred` / `uncertain` / `none` must never be treated as
  `known`. A skipped screening question is `Unknown`, and `Unknown` must never
  silently behave like a clean answer. This is the central rule of this PR.
- **The app never diagnoses.** It records what the user reported, applies
  conservative rules, and refers. "You have a disc problem" is forbidden output.
  "Pain during that movement is a reason to stop and get it looked at" is fine.
- Still 61 exercises. No catalog changes. No generator.

---

## Commit 1 — `feat(domain): intake model`

Package `com.gymbuddy.domain.intake`.

### What must be known, in three tiers

```kotlin
enum class Tier { REQUIRED_TO_SUGGEST, SAFETY, SHAPING }
```

**REQUIRED_TO_SUGGEST** — without these there is no plan, only questions:
- `goal: Goal` (one primary; the enum already exists in `programming`)
- `trainingAge: TrainingAge`
- `daysPerWeek: Int`, `typicalSessionMinutes: Int`
- `equipment: EquipmentAvailability` (see below)
- `healthScreen: HealthScreen` — **completed**, even if entirely clear

**SAFETY** — these block or modify:
- red-flag answers
- `declaredConditions: Set<ConditionId>` from `knowledge/vocab/conditions.json`
- `painReports: List<PainReport>`

**SHAPING** — improve the plan, never block it:
- `recovery: RecoverySignals` (sleep, stress, soreness — the rules layer already
  takes this type; give it a source)
- `dislikes: Set<ExerciseSlug>`, `preferences`
- `trainsWithPartner: Boolean?` — closes the gap found in the unspotted-floor
  work: a human spotter cannot be expressed in the equipment vocabulary, and
  should not be, because a person is not equipment. It belongs here.
- `priorInjuriesResolved: List<String>`

### Equipment availability — presets, not 38 checkboxes

Rahul's rule: detailed in the data, simple for the user.

```kotlin
enum class EquipmentPreset { FULL_GYM, HOME_LIMITED, BODYWEIGHT_ONLY }

data class EquipmentAvailability(
  val preset: EquipmentPreset,
  val alsoHave: Set<EquipmentId> = emptySet(),
  val doNotHave: Set<EquipmentId> = emptySet()
) { fun resolve(vocab: EquipmentVocab): Set<EquipmentId> }
```

Presets expand via the `tier` field already on every equipment token
(`none` | `minimal` | `home` | `commercial`). `alsoHave` / `doNotHave` are the
adjustment layer — "home gym, but I do have a pull-up bar" must be expressible
without dropping the user into the full token list.

**Report the coverage.** Measured against the current 61: bodyweight-only
resolves to 3 exercises, one kettlebell 9, dumbbells 15, a good home kit 25. A
preset that cannot fill a week is a real limitation, and the intake layer should
be able to say so rather than silently generating a thin plan.

### Unknown is a value

```kotlin
enum class Answer { YES, NO, UNKNOWN }
```

Every screening question returns `Answer`, not `Boolean?`. `UNKNOWN` is
explicit, is never coerced, and appears in output.

---

## Commit 2 — `feat(domain): health screen and referral`

A PAR-Q+ style screen. Conservative by design; it is a filter, not a diagnosis.

### Red flags → refer, do not intensify

```kotlin
enum class RedFlag {
  CHEST_PAIN, BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION,
  DIZZINESS_OR_FAINTING, PALPITATIONS_OR_IRREGULAR_HEARTBEAT,
  RECENT_SURGERY, UNEXPLAINED_NIGHT_PAIN,
  RADIATING_NUMBNESS_OR_WEAKNESS, PREGNANCY_COMPLICATION,
  UNCONTROLLED_DIAGNOSED_CONDITION, NEW_UNEXPLAINED_SWELLING_OR_PAIN
}
```

```kotlin
sealed interface ScreenOutcome {
  object Clear : ScreenOutcome
  data class ProceedWithCaution(val notes: List<String>) : ScreenOutcome
  data class ReferBeforeTraining(val flags: List<RedFlag>, val message: String) : ScreenOutcome
  data class Incomplete(val unanswered: List<QuestionId>) : ScreenOutcome
}
```

Rules:
- Any red flag `YES` → `ReferBeforeTraining`. The app does not produce a
  training plan behind a live red flag. It says what it saw, in the user's own
  terms, and says to get it checked.
- Any red flag `UNKNOWN` → `Incomplete`, never `Clear`.
- `ReferBeforeTraining.message` is spoken to a person who may be frightened.
  Write it that way: plain, calm, specific about what to do next, and free of
  any implied diagnosis. No condition names. No probabilities.
- A referral is **not** permanent. The model must support the user recording
  that they have been cleared, with a date. Record it as the user's report, not
  as a medical fact.

### Pain is separate from red flags

```kotlin
data class PainReport(
  val where: String, val whenItHappens: String,
  val movements: Set<ExerciseSlug>, val character: PainCharacter, // SHARP | DULL | ACHE
  val since: String?
)
```

Sharp or radiating pain during a movement → stop-and-modify for that movement,
and a `SAFETY` violation from the validator for any plan containing it.
Dull ache → `ProceedWithCaution` with a note. The app regresses and refers; it
never explains what the pain is.

### Conditions become declarable

Map the intake's declared conditions onto `knowledge/vocab/conditions.json`
ids, so the contraindication links in the catalog finally have a source. If a
user reports something with no matching id, store the free text and mark it
`Unknown` — do **not** guess the nearest id.

---

## Commit 3 — `feat(domain): completeness gate`

```kotlin
sealed interface IntakeResult {
  data class Ready(val context: UserContext) : IntakeResult
  data class NeedMore(val missing: List<Requirement>, val nextQuestion: QuestionId) : IntakeResult
  data class Refer(val outcome: ScreenOutcome.ReferBeforeTraining) : IntakeResult
}

fun evaluate(intake: Intake, vocab: EquipmentVocab): IntakeResult
```

- `Refer` outranks everything.
- `NeedMore` returns **one** next question, not a list to render. The product is
  "short and progressive" (contract 14); the model should support asking one
  thing at a time.
- `Ready` produces the `UserContext` the programming rules already consume.

---

## Commit 4 — `docs: contracts`

- New `contracts/30-health-screening-and-referral.md`. This is a new feature and
  `AGENTS.md` requires a contract before code. Record the red-flag list, the
  referral rule, the unknown-is-not-no rule, the no-diagnosis boundary, and the
  clearance-is-user-reported rule.
- Amend `contracts/14-onboarding.md`: it must now reference the health screen and
  the three tiers. Note in the contract that the screen is **required** before a
  first plan, and that it is visual and silent, not voice.
- Amend `contracts/15-profile.md`: declared conditions, pain reports, spotter
  availability and equipment preset are editable profile state. Clearing a
  referral is a deliberate user action, never inferred.
- `DECISIONS_AND_THRESHOLDS.md`: any constant introduced here gets a row.
  Expect most to be `SAFETY REQUIREMENT` — conservative by design, and that is
  the correct class, not a weakness.
- `scripts/check_living_spec.py` covers contract 30.

---

## Tests (`:domain`)

1. A red flag answered `YES` produces `Refer`; no plan-shaped output is
   reachable from that state.
2. A red flag answered `UNKNOWN` produces `Incomplete`, never `Clear`.
3. Skipping every screening question does **not** produce `Ready`.
4. `Ready` is unreachable while any `REQUIRED_TO_SUGGEST` field is absent.
5. Missing `SHAPING` fields never block `Ready`.
6. `EquipmentPreset.BODYWEIGHT_ONLY` resolves to a token set that yields exactly
   the bodyweight-capable subset of the 61.
7. `alsoHave` adds and `doNotHave` removes, and `doNotHave` wins on conflict.
8. A declared condition reaches the validator and produces the expected `BLOCK`
   for a contraindicated exercise — the end-to-end wire that does not exist today.
9. Sharp pain on a movement makes any plan containing it a `SAFETY` violation.
10. A free-text condition with no vocabulary match is stored as `Unknown` and
    never silently mapped to a near-miss id.
11. `NeedMore` returns exactly one `nextQuestion`.
12. Clearance after referral requires an explicit user action with a date;
    no code path clears a referral implicitly.
13. No `ReferBeforeTraining.message` contains a condition name from
    `conditions.json` — assert it, so no future edit turns a referral into a
    diagnosis.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` changes, no catalog changes, still 61 exercises, no generator.
- The contraindication path is wired end to end for the first time: a user can
  declare a condition and the validator blocks on it.

## Report back

1. The final question list, in the order you would ask it, with each question's
   tier.
2. Any red flag you added or removed from the list above, and why.
3. The exact wording of every `ReferBeforeTraining` message. This is the most
   sensitive text in the product — it goes to a person who may be scared, and it
   must not diagnose. Expect it to be reviewed word by word.
4. Coverage numbers per equipment preset against the current 61.
5. Anything in this prompt that turned out to be wrong.
