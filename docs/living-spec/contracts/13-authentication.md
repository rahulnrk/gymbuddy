---
id: 13-authentication
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Authentication

**Status:** specified  
**Core-loop role:** Secondary. Enables identity for history/memory/sync so the kernel can remember. Not part of in-set speech.

### User behavior

Sign up / log in (email or OAuth as implemented). Provide name. Log out. Delete account (privacy). Session tokens refresh without interrupting Gym Mode (pre-refresh).

### System state

A local `users.id` UUID is created in the persist PR so `user_id` is never null (fixture user). Cloud auth **binds** that id later. Gym Mode does not require network identity. Optional backup requires auth.

### Voice behavior

**Explicit none in Gym Mode.** No voice login. Auth screens are Planning Mode. If token expires mid-workout: continue **local** session; do not interrupt the lift with a login modal; queue reauth after `WORKOUT_COMPLETE` or pause.

### Structured event

None in workout catalog. Auth audit outside event_log.

### Database change

`users` row. Tokens in secure storage. `source` N/A.

### UI state

Standard auth screens, English. User is not required to authenticate between sets.

### Error / recovery

Network fail on login: error, retry. Mid-workout 401: local continue. Account deletion: wipe local + server per privacy spec.

### Tests

Local UUID exists before auth. Mid-session 401 does not drop sets. Delete account removes event_log. Voice: silent / not in Gym Mode.

---
