---
id: 20-workout-history
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Workout history and previous-workout awareness

**Status:** specified  
**Core-loop role:** Kernel memory of **facts**. “Never invent workout history” (product §33). Stage 2 personalization (product §54).

### User behavior

Asks “How many did I get last week?” / “How much did I do on incline last week?” / “I think I did 55 last time.” AI answers from records. “27.5 each hand for 9, 8 and 8.” only if stored. Planning Mode: history list of completed workouts.

If the user is guessing, the system **checks**: “I think you did 55 last time, but let me check.” then uses DB. If none: admit none.

### System state

Historical context from completed `workout_sets` only (`deleted_at` null). PRs computed, not guessed.

### Voice behavior

Known vs estimated vs uncertain language (product §33). Never invent. Gym Mode rest window is the primary time to answer.

### Structured event

`USER_QUESTION`. Answers are `COACHING_RESPONSE` with `citedFacts`. No writes to history from the question.

### Database change

Read-only for Q&A. History UI reads same tables.

### UI state

History screens in Planning Mode. HUD previous-performance strip in Gym Mode. User not required to open history to get last numbers.

### Error / recovery

Missing data → uncertain speech. Import not in MVP.

### Tests

- `20-history.event.incline-275-9-8-8-known`
- `20-history.event.unseeded-no-numbers`
- `20-history.event.user-guess-55-vs-db-50-known-wins`
- `20-history.event.uncertain-then-check`

---
