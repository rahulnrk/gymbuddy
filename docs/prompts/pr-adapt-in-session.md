# PR — Adapting: today's readiness, the busy-machine ladder, and deferral

## Why this PR exists

Three problems, one idea: **something changed — what does the coach actually do?**

1. **Fatigue is inert.** It carries the heaviest weight in the picker (−3.0) and
   changes nothing, because it subtracts equally from every candidate in a slot.
   The worked example proves it: quads fully fatigued, and the picker still
   answers "goblet squat". Goblet won on the skill penalty, not on fatigue. The
   right answer to cooked quads is *not a different squat* — it is fewer sets, a
   different pattern, or not that slot today. None of those are decisions the
   picker can make.
2. **The busy-machine reflex is backwards.** `contracts/19` has the coach offer a
   swap when a machine is occupied, and mentions waiting only as what happens
   when nothing is available to swap to. A real trainer asks how long, suggests
   working in, or reorders the session. Substituting is the *fourth* thing they
   try, not the first.
3. **The session cannot be reordered.** `EXERCISE_SKIPPED`,
   `SUBSTITUTION_APPLIED` and `PLANNED_SETS_CHANGED` all exist. There is no way
   to say **"do that one later."** It is the most common real-gym response to a
   busy rack and the system cannot express it.

## The design rule behind all of it

Copy what a trainer **decides**. Where a trainer would **look**, ask a short
question instead — we cannot see the gym, the rack, or the person's face. And do
not copy what a trainer can risk because they are standing there: no diagnosis,
no "push through it", no invented maxes.

That is why "how long do they look?" is step one of the ladder. It is not
politeness; it is our substitute for a glance.

## Non-negotiables

- `:domain` pure Kotlin. No Android SDK, no vendor SDK, no file IO, no SQL, no
  LLM call. No `:app` changes.
- **Nothing mutates the plan on its own.** `AGENTS.md` forbids automatic
  programme mutation. Every adjustment in this PR is a *proposal* the user
  accepts. The functions return proposals; only an accepted proposal produces an
  event.
- **No generator.** Contract 16 stands: one engine, template-based. This PR
  modifies an existing planned session; it never creates one.
- Voice timing: contract 09 forbids TTS during `SET_ACTIVE` except safety ≥ high.
  Every conversation in this PR happens while resting or between exercises.
  Model it so a proposal can be queued and drained, exactly as questions are.
- Still 61 exercises, no catalog changes.

---

## Commit 1 — `fix(domain): make fatigue mean something`

Two changes to the picker.

1. **Lower the fatigue weight** to match its real discriminating power within a
   slot. Candidates sharing a pattern differ only slightly in muscle profile, so
   a −3.0 term is mis-calibrated. Pick a value, justify it in
   `DECISIONS_AND_THRESHOLDS.md`, and say in the report what it now changes.
2. **Add a slot-level signal** to the ranking result:

```kotlin
data class SlotReadiness(
  val allCandidatesFatigued: Boolean,
  val worstTargetFatigue: Double,
  val explanation: String   // "everything I can offer here loads your quads, and they're still cooked from Monday"
)
```

The picker still ranks. It now also says when ranking is the wrong question.

---

## Commit 2 — `feat(domain): today's readiness and adjustment proposals`

```kotlin
data class Readiness(
  val muscleFatigue: Map<MuscleId, Double>,   // from the recovery model
  val sleepHours: Double?, val soreness: Map<MuscleId, Double>,
  val minutesAvailable: Int?, val selfReported: SelfReport?  // GOOD | OK | ROUGH | UNKNOWN
)

sealed interface Adjustment {
  object AsPlanned : Adjustment
  data class ReduceSets(val slot: SlotIndex, val from: Int, val to: Int, val why: String) : Adjustment
  data class LowerEffort(val slot: SlotIndex, val extraRir: Int, val why: String) : Adjustment
  data class SwapPattern(val slot: SlotIndex, val to: PatternId, val why: String) : Adjustment
  data class DropSlot(val slot: SlotIndex, val why: String) : Adjustment
  data class ShortenSession(val dropSlots: List<SlotIndex>, val why: String) : Adjustment
}

fun adjustForToday(session: PlannedSession, readiness: Readiness, ctx: PickContext): List<Adjustment>
```

Order of preference — **keep the session the person came for**:

1. `AsPlanned`. The default. A slightly tired muscle is normal training, not a
   reason to intervene. Do not fuss.
2. `LowerEffort` — cheapest real change.
3. `ReduceSets`.
4. `SwapPattern` — only when `SlotReadiness.allCandidatesFatigued`.
5. `DropSlot` — isolation and accessory before secondary, secondary before
   primary. Never drop the primary lift while a cheaper adjustment exists.
6. `ShortenSession` — only for `minutesAvailable`, and it drops from the end.

Rules:
- `UNKNOWN` readiness is not "fine". With no signal, propose `AsPlanned` and say
  nothing — do not invent fatigue, and do not ask three questions before a
  workout the person is standing in the gym ready to do.
- Every `why` is spoken to the user. Plain language, no field names, no bare
  numbers without units — same rule as the validator.
- Sleep and soreness **modify**, they never solo-trigger a drop. One bad night
  is not a deload.

---

## Commit 3 — `feat(events): EXERCISE_DEFERRED`

The missing capability. A **schema change**, so `AGENTS.md`'s same-change rule
applies in full: schemaVersion decision recorded in the spec, updated goldens,
updated Kotlin types, updated `events/CATALOG.md` and the contract. Do not
shortcut it, and do not express deferral as `EXERCISE_SKIPPED` plus a re-add —
that would put a lie in the history.

- `EXERCISE_DEFERRED { exerciseId, fromPosition, reason }`
- The deferred exercise returns to the session queue after the next exercise.
- A cap: after N deferrals it becomes an `EXERCISE_SKIPPED`. Put N in
  `DECISIONS_AND_THRESHOLDS.md` as an `MVP HEURISTIC`.
- History honesty: deferred-then-completed is completed. Deferred-and-never-done
  is skipped, and the reason is retained.
- State machine: define exactly which states accept a deferral. A set in
  progress is not deferrable.

---

## Commit 4 — `feat(domain): the unavailable-equipment ladder`

```kotlin
sealed interface UnavailableResponse {
  data class AskHowLong(val question: String) : UnavailableResponse
  data class Wait(val why: String) : UnavailableResponse
  data class WorkIn(val why: String) : UnavailableResponse
  data class Defer(val untilAfter: SlotIndex, val why: String) : UnavailableResponse
  data class Substitute(val alternative: ScoredExercise, val why: String) : UnavailableResponse
  data class Skip(val why: String) : UnavailableResponse
}

fun respondToUnavailable(
  slot: SlotIndex, session: PlannedSession, ctx: PickContext,
  expectedWaitMinutes: Int?
): UnavailableResponse
```

The ladder, in order:

1. **`expectedWaitMinutes == null` → `AskHowLong`.** Do not guess, do not
   substitute. This is the design rule: a trainer glances, we ask. One short
   question.
2. **`Wait`** when the wait is no longer than the slot's own rest period. It
   costs nothing — the person was resting anyway. Say that.
3. **`WorkIn`** — propose alternating sets with whoever is on it. Normal gym
   etiquette that experienced lifters do automatically and beginners never think
   of, which is exactly what a coach is for.
4. **`Defer`** — a later slot in this session that does not need the same
   equipment. Uses commit 3.
5. **`Substitute`** — the picker's top candidate, objective preserved.
6. **`Skip`** — last.

**Slot role decides how hard to protect it.** `PRIMARY` is the point of the
session: wait, work in, or defer before substituting. `ISOLATION` and
`ACCESSORY`: substitute without ceremony. Use the `SlotRole` already in the
picker.

Return **one** response, not a menu — `contracts/19` requires one primary
alternative, and the same applies here.

---

## Commit 5 — `docs: contracts`

- `contracts/19-substitution-engine.md`: record the ladder and that substitution
  is the **fourth** response, not the first. This changes documented behaviour —
  say so explicitly rather than editing quietly.
- New `contracts/32-in-session-adaptation.md`: readiness, the adjustment
  preference order, deferral, the accept-before-mutate rule, and the
  ask-where-a-trainer-would-look rule.
- `contracts/22-muscle-map-and-recovery.md`: `Readiness` consumes the recovery
  model. Note that `DAMAGE_BY_EXERCISE` in
  `voice-workout-os-source/src/17-recovery.js` is still keyed on display-name
  strings and must be re-keyed on slug when it is ported.
- `events/CATALOG.md` + a v1 schema for `EXERCISE_DEFERRED`.
- `DECISIONS_AND_THRESHOLDS.md`: the new fatigue weight, the deferral cap, the
  wait-vs-rest threshold.
- `scripts/check_living_spec.py` covers contract 32.

---

## Tests (`:domain`)

1. Quads fully fatigued, squat slot, all candidates fatigued → `SlotReadiness.allCandidatesFatigued` is true and an adjustment is proposed. The old behaviour (rank a squat and say nothing) fails.
2. A mildly tired muscle produces `AsPlanned` — no fussing.
3. `UNKNOWN` readiness produces `AsPlanned` and no invented fatigue.
4. Adjustment order: `LowerEffort` and `ReduceSets` are proposed before any `DropSlot`; a primary slot is never dropped while an isolation slot could be.
5. One bad night's sleep alone never produces `DropSlot`.
6. No adjustment mutates the input session — assert the input is unchanged.
7. `expectedWaitMinutes == null` → `AskHowLong`, never `Substitute`.
8. Wait shorter than the slot's rest → `Wait`.
9. A `PRIMARY` slot with a 6-minute wait and a free later slot → `Defer`, not `Substitute`.
10. An `ISOLATION` slot with a 6-minute wait → `Substitute`.
11. `Defer` is refused mid-set.
12. Deferral cap: the N+1th deferral becomes `EXERCISE_SKIPPED`, and history reports it as skipped with its reason.
13. Deferred-then-completed reports as completed, not skipped.
14. Every `why` and every `question` is plain language: no field names, no underscores, no bare numbers without units.
15. `EXERCISE_DEFERRED` round-trips through the codec and the projector, and the golden matches.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` change, no catalog change, still 61 exercises, no generator.
- The schema addition is complete per the same-change rule: spec, goldens,
  types, catalog, contract.

## Report back

1. The new fatigue weight, and one worked example showing what it now changes
   that it did not before.
2. **The exact wording of every question and every `why` the coach speaks.**
   These are the product. "How long do they look?" is the first thing a user
   will hear when something goes wrong, and it sets whether this feels like a
   coach or a menu.
3. The deferral cap you chose and why.
4. Any rung of the ladder you could not implement with the data available.
5. Anything in this prompt that turned out to be wrong.
