---
id: 22-muscle-map-and-recovery
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Muscle map and estimated recovery

**Status:** specified  
**Core-loop role:** Supporting visualization of load so planning and “should I train chest again?” questions are grounded. **No fake precision** (product §§20–21).

### User behavior

Sees male/female map per profile visualization. After workout: Chest — High; Triceps — Moderate; Anterior deltoid — Moderate. Bands: Fresh → Recently trained → High fatigue → Recovering → Recovered. Copy: **“Estimated recovery”** never “Your chest is exactly 73% recovered.”

Guidance: “Chest was trained hard yesterday. Another heavy chest session may not be ideal today.”

### System state

`recovery_records.load_band` from volume, frequency, recent training, user feedback, RPE/RIR. Sleep inputs **future**, not MVP.

`Readiness` (contract 32) **consumes** this recovery model: `muscleFatigue` is the same map the picker already takes. Adaptation does not invent a second fatigue source. Sleep hours and soreness **modify** today's proposals; they never solo-trigger dropping a movement.

PR 14a records the recovery inputs that already live in the prototype:

- `damageMultiplier` on each catalog exercise. Values come from `DAMAGE_BY_EXERCISE` (re-keyed onto slugs) else `DAMAGE_BY_PATTERN` else `1.0` (`voice-workout-os-source/src/17-recovery.js`).
- **`DAMAGE_BY_EXERCISE` in `voice-workout-os-source/src/17-recovery.js` is still keyed on display-name strings** (`"Back Squat"`, not `back-squat`). It must be re-keyed on slug when that file is ported. Until then, catalog `damageMultiplier` is the slug-keyed source of truth.
- Muscle contribution: **`muscleOverrides` if present, otherwise the pattern map** in `knowledge/vocab/patterns.json`. Do not pre-compute a flattened per-exercise map into JSON.
- Region ids are the 24 keys in `knowledge/vocab/muscles.json` (verbatim from `12-muscles.js`, including `neck.group = upper`).

### Voice behavior

Gym/planning Q&A may use the same bands. Never speak a recovery percentage. Not in Gym Mode as a map the user must look at.

### Structured event

System recovery recompute after `WORKOUT_COMPLETED`. No user event required.

### Database change

`recovery_records`. `source=system`. Map variant from profile does not change bands.

### UI state

Body map in Planning Mode. Optional small muscle-status on dashboard secondary. HUD does not require the map.

### Error / recovery

No workouts → all fresh, labeled estimated. Do not interpolate fake physiology.

### Tests

No percent strings in UI copy tests. Hard chest yesterday → not “fresh.” Visualization config does not change load_band. Voice: “73%” banned.

---
