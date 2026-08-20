---
id: 18-exercise-bank-and-detail
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Exercise bank and exercise detail

**Status:** specified  
**Core-loop role:** Supporting knowledge. Names, aliases, muscles, substitutions, and STT vocabulary so the kernel can resolve “incline” and show a glanceable demo if the user looks.

### User behavior

Search naturally: “Chest exercises with dumbbells.” “Exercises for triceps using cable.” “Leg exercises without machines.” “Shoulder exercises at home.” Open detail: name, demo, primary/secondary muscles, equipment, how-to, mistakes, alternatives, add to workout.

### System state

Knowledge layer. Retrieval + structured filters (equipment, muscle, pattern).

### Voice behavior

**Planning Mode:** silent search UI. **Gym Mode:** user may ask “what’s a good alternative?” (Feature 19) or “should I use a wider grip?” — Q&A via knowledge retrieval + coaching, not a bank screen. Voice behavior for bank **screen** = not in Gym Mode.

### Structured event

`USER_QUESTION` when asked in gym (Feature 28). In-session swap: `SUBSTITUTION_APPLIED`. Planning add-to-workout is a row write `source=manual`, not a Gym Mode event. Browse-only is no event.

### Database change

Catalog is seeded (not user workout data). User “add to workout” writes `workout_exercises` `source=manual`.

### UI state

Visual, scannable, English. Muscle regions. Add to workout. User not required to visit bank during a session.

### Error / recovery

Unknown search: empty state, not hallucinated exercises. Gym Q&A must only recommend catalog ids.

### Tests

Search filters. Detail fields present. Voice Q&A returns catalog exercise. No invented exercise names in events.

---
