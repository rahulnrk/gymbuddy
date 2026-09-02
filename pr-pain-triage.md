# PR — In-session pain triage

## Why this PR exists

Intake asks about pain at sign-up. **Nothing handles "my shoulder just twinged"
halfway through set two.** The safety classifier detects that pain was mentioned
and can fire a concern, but there is no structured conversation and no path from
"that hurt" to a changed session.

Everything downstream already exists: the picker excludes painful movements, the
sibling map swaps a pattern, `adjustForToday` drops a slot, and the adaptation
layer already works on proposals the user accepts. **This PR is the missing
conversation**, feeding machinery that is already built.

## The design decision that shapes it

**Do not triage on a 0–10 pain score.**

Intensity is a weak signal on its own. Severe training soreness can be an 8; a
significant tear can be a 4 at rest; a 3 that radiates with pins and needles is
more serious than an 8 that is a cramp. **Character and location predict
seriousness far better than intensity** — and the intake layer already models
character (`SHARP | DULL | ACHE`) and the red-flag list already covers radiating
numbness and unexplained night pain.

There is also no clinical standard that says 7/10 means see a doctor. Shipping
such a threshold would be the app making a medical judgement, which is the one
thing it must never do.

**Keep the number for tracking, never for routing.** "Was a six last week, now a
three" is useful history. It is not a gate.

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL.
- **Never name a condition.** Not "that's DOMS", not "sounds like tendonitis".
  The app describes a *pattern* and behaves accordingly. Add a build-failing test
  like the one on `ReferralCopy`: no output string may contain a display name
  from `conditions.json`.
- **Unknown is never "fine"** — same rule as the health screen. A skipped
  question does not resolve to the benign branch.
- **Nothing proceeds without consent.** The coach proposes; the user accepts.
  `AGENTS.md` forbids automatic programme mutation.
- Contract 09 timing: this conversation happens **during rest, never during a
  set**. Queue and drain exactly as questions already do.
- Still 77 exercises. No `:app` change.

---

## Commit 1 — `feat(domain): the triage questions`

Package `com.gymbuddy.domain.pain`.

Six questions, asked **adaptively** — stop the moment the answer is determined.
Someone mid-workout will not sit through six questions, and most cases resolve
in two or three.

| # | Question | What it separates |
|---|---|---|
| 1 | Where exactly — can you point to one spot? | Local vs diffuse |
| 2 | Did it come on at a moment, or build up over a day? | Acute injury vs soreness |
| 3 | Both sides, or one? | Bilateral soreness vs one-sided problem |
| 4 | Sharp, or more of an ache? | The character axis intake already has |
| 5 | Does it travel anywhere? Pins and needles, or weakness? | **Red flag** |
| 6 | Worse when you move it, or aching even at rest? | Mechanical vs constant |

Question 5 is the one that must always be reached before any "carry on" outcome.
If everything else looks benign but 5 is unanswered, the state is `NeedMore`,
not soreness.

---

## Commit 2 — `feat(domain): outcomes, not diagnoses`

```kotlin
sealed interface PainOutcome {
  data class LikelyTrainingSoreness(val why: String) : PainOutcome
  data class StopThisMovement(val slug: ExerciseSlug, val why: String) : PainOutcome
  data class ReferBeforeContinuing(val why: String, val mayTrainOtherAreas: Boolean) : PainOutcome
  data class UrgentReferral(val flags: List<RedFlag>) : PainOutcome
  data class NeedMore(val nextQuestion: QuestionId) : PainOutcome
}
```

Routing:

- **Both sides + built up + dull ache + a day or two after training, and
  question 5 clear** -> `LikelyTrainingSoreness`.
- **One side + a specific moment + sharp** -> `StopThisMovement`.
- **Travels, or numbness, or weakness, or wakes them at night** ->
  `ReferBeforeContinuing`.
- **Chest pain, breathlessness, dizziness** -> hand straight to the existing
  contract 30 red-flag path. Do not re-implement it here.

Note the naming: **`LikelyTrainingSoreness`, not `DOMS`.** The type name leaks
into logs and eventually into someone's screen.

---

## Commit 3 — `feat(domain): what happens next`

Each outcome produces a **proposal**, never an action.

| Outcome | Proposal |
|---|---|
| `LikelyTrainingSoreness` | **Go lighter on that movement this session** — not carry on as planned, and not skip. "Start light and see how it feels — we can build up if it's fine." Autoregulation, decided by feel. |
| `StopThisMovement` | Drop that exercise for the session and offer a sibling-pattern alternative via the existing map. It also becomes a picker exclusion for the rest of the session. |
| `ReferBeforeContinuing` | Stop that movement pattern. **`mayTrainOtherAreas` is usually true** — someone with a sore shoulder can still train legs, and saying so is better product than ending the session. Offer that explicitly. |
| `UrgentReferral` | Contract 30's path. Session over. |

**Persist it.** A pain report from one session must surface at the next
session's readiness check — *"how's that shoulder today?"* That single follow-up
is most of what makes this feel like a coach rather than a form.

---

## Commit 4 — `docs: contract 37`

New `contracts/37-pain-triage.md`. Record: the six questions and their order,
the adaptive stopping rule, **why intensity is recorded but never routed on**,
the never-name-a-condition rule, unknown-is-not-fine, the go-lighter default,
`mayTrainOtherAreas`, and the next-session follow-up.

`DECISIONS_AND_THRESHOLDS.md` gets a row for anything numeric. Expect most to be
`SAFETY REQUIREMENT` — conservative by design.

`scripts/check_living_spec.py` covers contract 37.

---

## Tests

1. A reported intensity of 9 with a benign character profile does **not** route
   to referral — intensity is not a gate.
2. A reported intensity of 2 with radiating numbness **does** route to referral.
3. Question 5 unanswered can never produce `LikelyTrainingSoreness`.
4. Any unanswered question produces `NeedMore`, never a benign outcome.
5. Chest pain, breathlessness or dizziness hand off to contract 30 and never
   resolve inside this package.
6. No output string contains a condition display name from `conditions.json` —
   assert across every outcome and every phrasing.
7. `StopThisMovement` adds a picker exclusion for the remainder of the session.
8. `ReferBeforeContinuing` with `mayTrainOtherAreas` true still yields a legal
   reduced session.
9. Nothing mutates the plan until a proposal is accepted — assert the input is
   unchanged.
10. Triage is queued during `SET_ACTIVE` and drained on rest.
11. A pain report persists and appears in the next session's readiness input.
12. Every spoken line is plain language: no field names, no bare numbers without
    units, no condition names.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` change, no catalog change, still 77 exercises.
- Nothing in this package can produce a diagnosis.

## Report back

1. **The exact wording of every question and every outcome**, as the user would
   hear it, mid-workout, slightly out of breath. Second only to the referral copy
   in how carefully it needs reading.
2. The decision table you implemented — which answer combinations reach which
   outcome.
3. Any combination you found ambiguous, and which way you resolved it.
4. What the next-session follow-up sounds like.
5. Anything in this prompt that turned out to be wrong.
