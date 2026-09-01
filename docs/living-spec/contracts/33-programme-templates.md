---
id: 33-programme-templates
status: implemented
schemaVersion: 1
source: docs/prompts/pr15-session-builder.md
---

## Feature: Programme templates and session assembly

**Status:** implemented  
**Core-loop role:** Lays out the slots that the picker, dose, validator, and adaptation already know how to handle. This is contract 16's algorithm. It does not invent exercises.

### User behavior

After intake, the app proposes a week from an in-repo template. The user accepts, edits, or replaces it (contract 17). They never feel locked in. A generated plan is a proposal: building it writes no events.

### System state

`knowledge/templates/*.json` plus `knowledge/schema/template.schema.json` (`additionalProperties: false`). Four minimum templates: beginner Full Body 3-day, Full Body 3-day, Upper/Lower 4-day, PPL 6-day. Keyed by `goal × daysPerWeek × trainingAge`. No exact match → nearest lower days, named in notes.

`com.gymbuddy.domain.plan`:

1. Choose template.
2. For each slot, `rank()`; take the top. If the written pattern is empty, try each sibling in `PatternSiblings.MAP` once, skip a sibling already filled that day, keep the original role, and name the swap. Then drop and name the slot. Each kept slug is prepended to `recentSlugs` before the next slot, and the day's kept slugs are prepended before the next day in the same `buildWeek` call. Later days in the week therefore see earlier picks as recent.
3. `dose()` for sets / reps / rest / effort. `prescribedWeightKg` is always null.
4. Clamp: estimate minutes from sets × (working time + rest midpoint), cut descending `priority`, never a `PRIMARY`. Then reduce sets if still over. Then say so. Clamp never adds work; a 4-slot strength lower with a moderate hinge can sit under the −15 floor.
5. `validateWeek` — warnings returned, never auto-fixed.
6. Optional `adjustForToday` on the first day — proposals, not applied.

Sibling map (one hop, explicit, not inferred from `patternGroup`):

- vertical pull ↔ horizontal pull
- vertical push → incline push, then horizontal push
- horizontal push → incline push, then vertical push
- incline push → horizontal push
- decline push → horizontal push
- squat ↔ lunge
- lats → vertical pull, then horizontal pull
- hinge does **not** swap with squat

`CannotBuild` is still reachable if a day has fewer than two legal lifts. Bodyweight-only **does** fill a 3-day beginner week from the expanded catalog (air squat, glute bridge, reverse lunge, inverted/towel row, pike push-up, plank, standing calf raise). A pull uses a sturdy table or a door frame — tokens with `tier: none`, so the bodyweight preset includes them. A pull-up bar is not required. A missing vertical pull becomes a row via the sibling map.

Beginner templates put one lower pattern per day (squat / hinge / lunge), hinge at most twice a week, never after a squat in the same session. Core is last on every day that has it. A `PRIMARY` slot never receives fewer sets than a `SECONDARY` slot.

Warm-up **content** is planned in contract 34. `estimateMinutes` uses the real `WarmupPlan.estimatedMinutes`, not placeholder minutes. Cooldown stretches are after the session and are not counted. If a week overshoots the ±15 window with the real warm-up, that is reported; the week is not quietly thinned to hide it.

**Order warnings** (`order.freshness`) fire only on class inversion: a compound after isolation, or isolation/compound after core. Adjacent compounds of different skill (chin-up after a moderate hinge) are picker noise, not a coaching fail — the old consecutive-`orderScore` rule fired on nearly every generated week. Templates still order patterns heavy-first and put core last; they do not pin a specific exercise. Volume `WARN` on an in-repo template is a bug. An `order.freshness` WARN on an in-repo template is also a bug: it means a main lift is sitting after accessory or core work.

### Voice behavior

**Not in Gym Mode.** Notes are coach language so a later turn can read them. This PR does not call TTS.

### Structured event

None on build. Accepting a plan is a later write (workout origin). Planned rows are not completed sets.

### Database change

None in this PR. Model only.

### UI state

None. No `:app` change.

### Error / recovery behavior

Try the listed sibling before dropping a slot. Then drop empty slots. `CannotBuild` names unlocking equipment. Time clamp will not gut mains. Validator stays an independent check. Warm-up minutes come from the real plan (contract 34).

### Tests

Evidence is `:domain` Kotlin tests, not event goldens — this PR emits no workout events.

1. Every template validates; every pattern and role exists.
2. Same profile, same week, 100 runs.
3. Empty slot tries the listed sibling, then is dropped and named. Home Upper A with no bar still gets a row.
4. `prescribedWeightKg` null on every entry.
5. Clamp never cuts a `PRIMARY`.
6. 30 vs 90 minutes: same mains, different accessories.
7. Beginner vs intermediate: dose differs, load does not.
8. Contraindication never appears.
9. Bodyweight-only → `CannotBuild` with named equipment.
10. `buildWeek` emits no events.
11. Validator warnings returned, not fixed.
12. Every note is plain language.
