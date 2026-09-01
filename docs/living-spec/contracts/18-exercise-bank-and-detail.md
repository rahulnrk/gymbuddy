---
id: 18-exercise-bank-and-detail
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Exercise bank and exercise detail

**Status:** specified  
**Core-loop role:** Supporting knowledge. Names, aliases, muscles, substitutions, and STT vocabulary so the kernel can resolve “incline” and show a glanceable demo if the user looks.

PR 14a freezes the catalog **shape** on the existing prototype set (61 exercises in `knowledge/catalog/exercises.json`). Growth to ≥80 is PR 14b. Status stays `specified` until that bar.

### Catalog schema (PR 14a)

- Identity: `slug` + `id = uuidv5(NAMESPACE, slug)` with `NAMESPACE = uuidv5(RFC4122 DNS, "catalog.gymbuddy.app")`.
- Equipment is **not** a single `load` string. `requiredEquipment` / `optionalEquipment` are arrays of `knowledge/vocab/equipment.json` ids. Required is AND (the movement is impossible without every token). Optional makes it safer or better (a rack for a heavy barbell lift). `["none"]` means doable on a mat.
- Technique is two axes, not one flag: `skillDemand` (how hard to learn) and `consequenceOfFailure` (how bad a miss is under load). Both `low | moderate | high`. Only `consequenceOfFailure: high` earns “have a human watch this once.”
- `provenanceDefault` applies to every field; `provenance` overrides per field. `basis` is `measured | consensus | judgement | inherited`. Most migrated fields are `inherited` from the prototype.
- Aliases are `{text, locale, confidence}` objects. `resolveSpoken` returns a **ranked list**, not a single pick. When the top two are within `ASK_MARGIN` (0.1), the caller asks. This is what stops `'pec deck' → Cable Fly`.
- `unilateral: true` means one logged set counts as one set per side for volume. Arithmetic is PR 14b.
- Vocabularies live under `knowledge/vocab/` (`muscles.json`, `patterns.json`, `equipment.json`, `conditions.json`). Nothing else in the repo may use a bare string where one of those ids belongs.

`docs/research/` is consulted reference only. No rows are copied from it into `knowledge/`.

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
