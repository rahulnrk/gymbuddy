---
id: 23-automatic-notes-and-memory
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Automatic notes / structured memory

**Status:** specified  
**Core-loop role:** Remembers user-stated facts for future coaching. Structured and user-editable (product §§27–28). Never silent high-impact assumptions.

### User behavior

“My left shoulder feels slightly uncomfortable when I go heavy.” → note on exercise/session. “That weight felt easy today.” → feedback easy. “Don’t make me do this exercise next time.” → dislike preference. User can review, edit, delete in profile/memory UI.

### System state

Notes scoped set/exercise/session/user. High-impact (`dislike_exercise`, injuries) **user-visible** immediately. Do not infer “hates bench” from one hard set.

### Voice behavior

May say “I’ll remember that” **once** for high-impact, not every note. Safety-related notes follow Feature 11. Language original stored; canonical English summary for retrieval.

### Structured event

`USER_NOTE_CREATED`, `USER_FEEDBACK`, `MEMORY_UPDATED`.

### Database change

`workout_notes`, `user_preferences`. `source=voice`. `user_visible=true` for dislikes/limitations.

### UI state

Notes list editable. Gym: no required confirmation tap; optional caption. User can undo in memory UI.

### Error / recovery

If extraction is unsure whether it was a note, **do not** write high-impact preference; ask. Offline notes local.

### Tests

Dislike utterance creates editable preference, not a hidden embedding. Easy set → feedback not a weight change. User deletes note → coaching must not cite it. No opaque blob.

---
