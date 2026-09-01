---
id: 14-onboarding
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Onboarding

**Status:** specified  
**Core-loop role:** Secondary. Feeds **user context** (goals, equipment, language, units, body-map visualization only) so the first generated workout and Gym Mode greeting are useful. Short and progressive (product §4). Do not overwhelm.

A completed health screen (contract 30) is **required** before a first plan. The screen is visual and silent, not voice. Intake is three tiers: `REQUIRED_TO_SUGGEST` (including that completed screen), `SAFETY` (red flags, declared conditions, pain), `SHAPING` (recovery, dislikes, whether they train with a partner). Skip is unknown, not no.

### User behavior

Account + name; age, sex, height, weight, units, experience; goals (muscle gain, strength, fat loss, general fitness, conditioning, recomposition; multiples later); days/week, duration, preferred time, style; equipment **preset** (full gym / home limited / bodyweight) with also-have / do-not-have adjustments, not 38 checkboxes; body visualization male/female **map only**; language primary + optional additional + auto detect; health screen (contract 30) one question at a time.

### System state

Profile incomplete flags. Equipment becomes constraints for generation/substitution. Body map variant **must not** alter training ability, strength, or exercise recommendations (product §4).

### Voice behavior

**Silent / not in Gym Mode.** Onboarding is visual. No IVR onboarding. The health screen is not a Gym Mode conversation.

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
