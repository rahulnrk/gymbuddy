---
id: 26-dashboard-todays-workout
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Dashboard / Today’s Workout

**Status:** specified  
**Core-loop role:** Supporting. Answers “what am I doing today?” and gets the user into the kernel with **START WORKOUT**. Must not become the product.

### User behavior

Opens app. Sees TODAY’S WORKOUT: name, target muscles, exercises, estimated duration, completion, **START WORKOUT**. Secondary: upcoming, continue/edit, plans, bank, progress, muscle recovery, profile. Not a statistics dump (product §6).

Continue if local session in progress.

### System state

Loads today’s planned workout + any resumable session. Does not start VAD until Gym Mode.

### Voice behavior

**Silent / not in Gym Mode.** Dashboard does not listen in MVP (avoids kitchen-table false logs). Voice starts after START WORKOUT.

### Structured event

CTA emits `SESSION_INITIALIZED` / `WORKOUT_STARTED` via lifecycle feature. Viewing dashboard: no events.

### Database change

None on view.

### UI state

English, calm, one-handed CTA. User is not required to configure anything to press Start if onboarding complete.

### Error / recovery

No plan today: generate or pick a plan, do not start empty kernel. Resume banner if `in_progress`.

### Tests

Start CTA path. Resume path. No mic on dashboard. Secondary links exist. Does not display fake recovery percents.

---
