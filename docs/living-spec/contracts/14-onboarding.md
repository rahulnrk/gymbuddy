---
id: 14-onboarding
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Onboarding

**Status:** specified  
**Core-loop role:** Secondary. Feeds **user context** (goals, equipment, language, units, body-map visualization only) so the first generated workout and Gym Mode greeting are useful. Short and progressive (product §4). Do not overwhelm.

### User behavior

Account + name; age, sex, height, weight, units, experience; goals (muscle gain, strength, fat loss, general fitness, conditioning, recomposition; multiples later); days/week, duration, preferred time, style; equipment list (barbell, dumbbells, bench, squat rack, Smith, cables, machines, leg press, pull-up bar, kettlebells, bands, cardio, full gym); body visualization male/female **map only**; language primary + optional additional + auto detect.

### System state

Profile incomplete flags. Equipment becomes constraints for generation/substitution. Body map variant **must not** alter training ability, strength, or exercise recommendations (product §4).

### Voice behavior

**Silent / not in Gym Mode.** Onboarding is visual. No IVR onboarding.

### Structured event

None of the workout catalog. Profile writes. Later `MEMORY_UPDATED` if they change prefs.

### Database change

`users.*`, `user_preferences` as needed. `source=manual`.

### UI state

Short steps, skip where possible, English. User is not required to fill RPE philosophy etc.

### Error / recovery

Skip equipment → assume full gym only if they chose “full gym”; else constrain to selected; empty equipment → bodyweight-capable generation.

### Tests

Body map female does not change prescribed loads. Equipment without cable → generated plan has no cable-only moves (or substitutes). Language stored. Voice: none.

---
