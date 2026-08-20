---
id: 24-workout-completion-and-post-workout
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Workout completion summary and post-workout intelligence

**Status:** specified  
**Core-loop role:** Closes the loop: remember + analyze so next time is better. Not a wall of AI text (product §§44–45).

### User behavior

End of session, AI: “That’s it. You got through all six exercises. Your total volume was up from last week.” / “Done. You had a stronger session than last Thursday. Bench volume was up, and you hit a new rep best.” — only with facts.

Screen: duration, exercises, sets, volume, PRs, muscles trained, notes, estimated recovery, few progress highlights.

User may add a final voice note: “Honestly felt pretty easy today.” → workout feedback.

### System state

`WORKOUT_COMPLETE`. Recovery recompute. Progress metrics refresh. Session `ended_at`.

### Voice behavior

One short spoken summary. Then stop. User can barge-in with a note. Language-flexible. No IVR “workout has been saved successfully.”

### Structured event

`WORKOUT_COMPLETED`, optional `USER_NOTE_CREATED` / `USER_FEEDBACK`, `COACHING_RESPONSE` with citedFacts, system recovery/progress.

### Database change

Session ended, workout status completed, metrics, recovery. `source=voice|system`.

### UI state

Summary screen after Gym Mode. Primary path still voice; looking is optional. User not required to rate 12 cards.

### Error / recovery

If last week volume unknown, do not say volume was up. Offline: summary from local fold; sync later.

### Tests

Seed last Thursday volume lower → may say up. Equal volume → do not claim up. Final note stored. Invented PR forbidden. Offline complete.

---
