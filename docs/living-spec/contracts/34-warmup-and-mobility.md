---
id: 34-warmup-and-mobility
status: implemented
schemaVersion: 1
source: docs/prompts/pr-warmups-and-stretches.md
---

## Feature: Warm-ups and stretches

**Status:** implemented  
**Core-loop role:** Completes today's session before the first working set. The user hears a short, equipment-aware warm-up and a couple of post-session stretches. These are not exercises and do not go through the picker.

### User behavior

Before the first lift, the app tells them how they will get warm: a pulse raise, a handful of drills that match today's patterns, then two relative ramp sets on the first main lift. The first time a drill appears for this user, they hear the full how-to. After that: name, dose, reason — "Open book, six each side — frees up your pressing range." Seven fully-narrated drills before the first squat is too much by session three.

After the work, two or three static stretches for muscles that actually worked. The "stretching is not a fix for feeling weak" line is said **once**, on a user's first ever cooldown, and again only if they ask whether stretching fixes something. It does not repeat on every stretch.

### System state

Three record types, three schemas, shared L0 vocabularies (`muscles`, `patterns`, `equipment`, `conditions`) and the same provenance block:

- Exercises stay in `knowledge/catalog/exercises.json` (77, untouched).
- Warm-up drills in `knowledge/catalog/warmups.json` plus `knowledge/schema/warmup.schema.json`.
- Stretches in `knowledge/catalog/stretches.json` plus `knowledge/schema/stretch.schema.json`.

`com.gymbuddy.domain.warmup`:

1. `planWarmup(session, ctx, catalog, library, history)` — pulse, 3–6 drills matched to today's patterns, 1–3 ramp sets on the first `PRIMARY` lift, estimated minutes. If the plan would exceed 12 minutes, drills are cut, not the pulse. Each `WarmupEntry` carries `timesSeen` from `MobilityHistory` (on the plan, not the catalog). `buildWeek` increments history across days in the same week.
2. `suggestStretches(...)` — two or three **static**, **post-session** stretches whose target muscles actually worked (fractional contribution). Never offered as a remedy for weakness. The disclaimer is a separate field on the session, not a clause on every stretch.

**Familiarity.** `MobilityHistory.drillTimesSeen` / `heardStretchDisclaimer`. First appearance (`timesSeen == 0`): full how-to. Later: name plus reason. `answerIfAskedWhetherStretchingFixesSomething()` is the Q&A form of the disclaimer.

**Balance.** At most one drill per region (lower / push / pull / core / neck). A squat-and-press day does not spend two of six drills on rear delt and mid trap.

**Five-stage structure** (Coach & AI Programming Guide §4.3, NSCA performance preparation): general preparation → mobility where useful → activation where useful → movement rehearsal → specific ramp-up sets → working sets.

**Selection.** Drills by `targetPatterns` ∩ the session's patterns. Equipment-aware. Contraindicated drills excluded, non-overridably, as in the picker. A squat-only session rehearses a squat, not a pull.

**Ramp sets.** Attach to the first primary lift only. Effort is relative: "about half your working weight", "a little under your working weight". Never a kilogram. The app does not know what anyone lifts until they log it.

**Stretch timing.** Dynamic work belongs in the warm-up (as drills, or as stretches tagged `timing=warmup`). Static holds belong after training or in a standalone session. The planner never puts a static stretch before the first working set, and never puts a dynamic stretch in the cooldown.

**No stretching for weakness.** Feeling tight does not automatically mean a muscle needs to be stretched. Do not stretch a muscle that is already long and weak. Regions we do **not** stretch as a default: mid traps, rear delts, serratus, side delts, upper abs, lower abs, abductors (glute med), tibialis. Those are more often weak or short-range stabilizers in this catalog than tissues that need length.

`PlannedSession` carries `warmup: WarmupPlan` and `cooldown: List<StretchPick>`. The duration model uses the real warm-up estimate. Cooldown is after the session and is not counted. Building a plan writes no events.

### Voice behavior

**Not in Gym Mode as a warm-up UI.** Notes and `speakSession` are coach language so a later turn can read them. This PR does not call TTS.

### Structured event

None. Warm-up and cooldown are part of the planned session, not completed sets.

### Database change

None. Model only.

### UI state

None. No `:app` change.

### Error / recovery behavior

No usable pulse raise → start with drills and say so. No matching drill → skip that stage. Warm-up over ~12 minutes → cut drills, keep the pulse. Empty stretch list is allowed. A contraindicated drill or stretch never appears. No invented loads.

### Tests

Evidence is `:domain` Kotlin tests, not event goldens.

1. Both new schemas validate; every id regenerates from its slug.
2. Every muscle, pattern, equipment and condition token resolves.
3. A squat-day warm-up contains squat-pattern rehearsal and not a pull drill.
4. Equipment: a bodyweight-only user gets no bike-based pulse raise.
5. A contraindicated drill never appears, at any weight.
6. Ramp sets attach only to the first primary lift and contain no numeric load.
7. Warm-up estimate never exceeds ~12 minutes; drills are cut, not the pulse.
8. Static stretches are never scheduled pre-session; dynamic never post.
9. Suggested stretches target muscles that actually worked in that session.
10. Printed weeks use the real warm-up in the duration estimate. Overshoot is reported, not hidden by thinning the week.
11. Every drill and stretch carries provenance.
12. No two warm-ups share a `howTo` string.
13. First time through, how-to is spoken; later times are name plus reason. `timesSeen` is on the plan.
14. Stretch disclaimer once per user, not on every stretch line.
15. A squat-and-press day does not stack two upper-back activation drills.
