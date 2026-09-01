---
id: 15-profile
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Profile

**Status:** specified  
**Core-loop role:** Secondary. Editable user context and structured memory surface.

### User behavior

View/edit profile fields, units, language, equipment **preset** (with also-have / do-not-have), goals, body-map variant, declared conditions, pain reports, whether they train with a partner, review/edit/delete preferences and notes (Features 23). Export/delete data. **No notification prefs UI** (notifications are out of MVP).

Clearing a referral (contract 30) is a deliberate user action with a date. It is never inferred from a skipped question, a later session, or a clean-looking screen.

### System state

Updates `UserStateContext` for subsequent sessions.

### Voice behavior

**Silent / not in Gym Mode** for editing. User may say in gym “I don’t have a bench today” → treated as session equipment constraint / substitution (Feature 19), not a profile form. Persistent equipment change is confirmed: do not silently rewrite profile on one occupied machine.

### Structured event

`MEMORY_UPDATED` only when user intends persistent preference. Occupied machine ≠ delete cable from profile.

### Database change

`users`, `user_preferences`. `source=manual` or `voice` if they said “never make me do skull crushers” (dislike) — still user-visible.

### UI state

English forms. Gym HUD does not open profile.

### Error / recovery

Validation on height/weight ranges; do not block workout on missing optional fields.

### Tests

Unit change kg↔lb does not rescale historical `weight_kg` incorrectly (store kg, display convert). Voice none in Gym Mode for the settings screen. Dislike preference appears in editable list.

---
