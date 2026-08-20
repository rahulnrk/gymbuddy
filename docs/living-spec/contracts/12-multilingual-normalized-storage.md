---
id: 12-multilingual-normalized-storage
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Multilingual / code-mixed conversation with English/normalized storage

**Status:** specified  
**Core-loop role:** Kernel language path. Conversation language ≠ storage language (product §15).

### User behavior

Speaks English, Hindi, Telugu, Tamil, Hinglish, Telugu-English, Hindi-English, mixed within a sentence. UI remains English. Example: “Nenu 60 kilos tho eight reps chesa.” Storage: Bench Press, 60 kg, 8 reps. AI may reply in Telugu: “Nice, last time 60 kilos tho 7 reps chesav. Ippudu oka rep extra vachindi.” only if last time 7 is **known**.

**Frozen correction fixture `kaadu-8`:** the utterance is `Kaadu, 8.` Telugu *kādu* (కాదు) means “no / not so.” Canonical interpretation: the same as English “No, 8.” — a `SET_CORRECTED` of the most recent eligible set to 8 reps, not a new set, not a question. This utterance is **not** in `PRODUCT.md`; it is a living-spec multilingual test of the product’s code-mix correction law (product §§15, 32). Do not change correction semantics to make the test pass.

### System state

`LANGUAGE_DETECTED` per turn. `user.primary_language` + `auto_language_detect`. Aliases table for exercise names in supported languages.

### Voice behavior

Reply in user’s language when LID confidence high; else primary_language. TTS lang matches. Barge-in unchanged. Gym Mode silence policy unchanged.

Planning Mode UI: English (unless future localization — not MVP).

### Structured event

Events always canonical: `exerciseId`, `weightKg`, `reps`. `language` field records utterance language. `USER_QUESTION.questionCanonical` English for retrieval; `questionOriginal` preserved.

### Database change

Sets/notes as specified. Do not store “Nenu 60 kilos…” as the weight field.

### UI state

HUD: “Bench Press · 60 kg · 8”. Not Telugu UI in MVP.

### Error / recovery

LID wrong → user can still log; correction works. If TTS lacks Telugu voice, speak English rather than fail the set.

### Tests

- `07-correction.multilingual.kaadu-8` (`llm_required`) — frozen Telugu “Kaadu, 8.” = “No, 8.”
- `12-multilingual.event.telugu-60x8-canonical-storage` (`llm_required`)
- `12-multilingual.event.aaj-kya-karna-hai-question-not-set` (`llm_required`)
- `12-multilingual.db.no-prose-in-weight-column`

---
