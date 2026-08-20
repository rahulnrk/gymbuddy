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
