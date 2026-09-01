# PR — Fix what the first printed week exposed

Printing three real weeks found more in ten minutes than any amount of unit
testing. Everything below came from reading that output. The engine is fine —
the templates, the catalog and two constants are not.

## The acceptance test for the whole PR

**Our own templates must pass our own validator.** Today the beginner full-body
template trips it: quads 11, glutes 15, lower back 11, hamstrings 11.5, lats 12
against a beginner landmark of 4–10, plus ordering warnings. A programme we
authored should not fail the check we wrote.

Where a warning survives, it must be *deliberate and justified in the contract* —
not tolerated because fixing it is awkward.

---

## 1. The templates are wrong — fix them first

### Deadlift three times a week, for a beginner, after squats

Day A, B and C all contain a hinge, and in A and C the deadlift sits **fourth**,
after a heavy squat. That is the heaviest and most technically demanding lift in
the catalog, performed fatigued, three times a week, by someone who has never
done it. No coach would write it, and both the volume and ordering warnings are
the validator correctly telling us so.

Fix:

- A beginner full-body template gets a hinge **once or twice a week**, not three
  times.
- **Never a heavy hinge after a heavy squat in the same beginner session.** If
  both appear, the hinge is the lighter variant and goes first, or they go on
  different days.
- Prefer a Romanian deadlift over a conventional deadlift as the beginner's hinge.
  Lower skill demand, lower consequence, same pattern.

### Sessions come out at 33–37 minutes against a 60-minute target

Three separate causes; fix all three, and do **not** pad with invented work.

- `SECONDS_PER_WORKING_SET = 40` is optimistic. Walking to the rack, loading
  plates and getting set up is real time. Raise it, justify the new value in
  `DECISIONS_AND_THRESHOLDS.md`, and say in the report what you chose and why.
- **Warm-up is not modelled at all.** The programming guide gives the structure:
  3–5 min pulse raise, 3–6 pattern-specific drills, 1–3 ramp-up sets. Model it as
  a duration contribution at minimum, even if warm-up content is a later PR.
- The templates carry five slots. A 60-minute beginner session holds six or
  seven. Add slots — real ones, matched to patterns the week is missing.

### Ordering

The validator wants high-skill and heavy work while fresh. Where a template
violates that, fix the template rather than leaving a standing warning.

## 2. The sets rule is inverted

Beginner `PRIMARY` = 2 sets, `SECONDARY` = 3. So in week (a) the deadlift gets
**more** sets than the back squat purely because the template calls it secondary.
The less important lift gets more work.

Fix: a `PRIMARY` slot must never receive fewer sets than a `SECONDARY` slot in
the same session. Add that as an invariant with a test, not just a corrected
constant.

## 3. `2 × 8–8 reps` — a rep range that is not a range

Appears on Overhead Triceps Extension and Rear Delt Fly. Presumably the catalog
`repWindow` intersected with the goal range down to a single value. Decide the
intended behaviour — most likely the intersection should widen to the catalog
window when it would otherwise collapse — and test that no built entry ever has
`repRange.min == repRange.max` unless a single value is genuinely intended.

## 4. Grow the catalog — by outcome, not by count

**Do not aim for a number of exercises. Aim for these three things to be true.**

### Priority 1 — bodyweight-only must build a real week

Today `BODYWEIGHT_ONLY` resolves to three exercises (push-up, plank, nordic
curl) and `buildWeek` correctly returns `CannotBuild`. **Someone who downloads a
fitness app and owns nothing is likely our most common first user.** "I can't
help you" is the worst possible first impression.

Add enough no-equipment work that a 3-day full-body beginner week builds and
passes the validator: squat, hinge, lunge, horizontal push, horizontal pull,
vertical push, core, and something for calves. The hard ones are the pulls —
solve them honestly (inverted row under a table, towel row on a door frame) or
state plainly in the report that a pull-up bar is genuinely required and the
preset should say so up front.

### Priority 2 — the dumbbell hinge

A user with dumbbells and a bench is told *"I couldn't find a hinge you can do
with the equipment you've got."* **There is no dumbbell hinge in the 61.** The
dumbbell Romanian deadlift is one of the most common home exercises in the
world. Add it, plus single-leg RDL and dumbbell good morning.

### Priority 3 — the six full-gym orphans

Patterns with exactly one legal lift even in a full gym: carry, decline push,
lats, lower back, quads, traps. A substitution there cannot rank; it can only
keep or skip.

### Also — the missing field

Add `bestFitGoals` per exercise. The picker currently proxies goal fit with
`kind` (compound/isolation), which is weak. Every new and existing entry gets it.

**Every new exercise follows the PR 14a schema exactly**, including
`requiredEquipment` slots, both technique axes, `damageMultiplier`, aliases with
locale, and a provenance block. `docs/research/` stays reference only — do not
copy rows from it.

---

## Tests

1. All three profiles from the last report build. `BODYWEIGHT_ONLY` no longer
   returns `CannotBuild` for a 3-day beginner week.
2. **Every in-repo template passes `validateWeek` with no `WARN` volume
   violations** for its intended goal and training age.
3. No beginner template places a hinge in more than two sessions per week.
4. No beginner session places a heavy hinge after a heavy squat.
5. A `PRIMARY` slot never has fewer sets than a `SECONDARY` slot in the same
   session.
6. No built entry has a collapsed rep range unless intended.
7. Estimated duration for every template lands inside the ±15 minute window of
   its intended session length, warm-up included.
8. Every new exercise validates against the catalog schema; every equipment
   token exists; every substitute slug resolves.
9. Every exercise, new and existing, has `bestFitGoals`.
10. Deterministic: the same profile builds the same week over 100 runs.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No `:app` change. `prescribedWeightKg` still always null. Still no generator
  beyond the template engine. Building still writes no events.

## Report back

1. **Print the same three weeks again**, plus a bodyweight-only week. Side by
   side with the last report if you can.
2. The new `SECONDS_PER_WORKING_SET` and warm-up model, and how close the
   estimates now land.
3. Every validator warning that still survives, and the justification for each.
   If a template still trips our own check, say so — do not thin it until it
   passes.
4. How many exercises the catalog now holds, and which patterns are still
   singletons at each equipment preset.
5. Anything in this prompt that turned out to be wrong.
