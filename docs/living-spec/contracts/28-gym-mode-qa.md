---
id: 28-gym-mode-qa
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Gym Mode Q&A (questions + secondary nutrition)

**Status:** specified  
**Core-loop role:** Kernel-adjacent. User questions during rest keep the companion useful without turning Gym Mode into a chat tab. Nutrition is **secondary and non-medical** (product §§25–26). Silent / **not a chat tab**.

### User behavior

User may ask at any time in Gym Mode (typically `RESTING`):

Product §25: “Why do I feel this in my shoulder?” “Should I use a wider grip?” “How much protein should I have?” “When should I eat after training?” “Is this enough volume?” “Why am I not progressing?” “What should I do instead of this exercise?” “Should I rest longer?” “What’s next?” “How much did I do on incline last week?”

Product §26 (nutrition, secondary): protein, calories, meal timing, general nutrition, pre/post-workout food, hydration.

User is **not** required to open a chat screen or type.

### System state

Intent label `question` (multi-label allowed). Retrieval via `RetrievalProvider` indexes `exercises | fitness_knowledge | nutrition_general`. Workout/history questions use FourContexts facts, not retrieval fiction.

Medical/pain: if Feature 11 table fires, that wins. Technique questions **without** pain language do **not** emit `SAFETY_CONCERN` and **must not diagnose**.

### Voice behavior

`shouldSpeak` → `answer_question` (MUST speak **unless** `SET_ACTIVE`, in which case enqueue until rest — Feature 9 precedence). Longer than set confirmations. Same language as user when LID confident.

**Refusal templates (must-not paraphrase away):**

- Diagnosis: “I can’t diagnose that. If it hurts sharply or you’re worried, stop and talk to a professional.”
- Medical nutrition / disease / medication: “I can only give general fitness nutrition, not medical diet advice.”
- Missing history: stance `none` — “I don’t have a previous number for that yet.”

Do not open a chat thread. Do not keep asking follow-up questions unless ambiguity is high.

### Structured event

`USER_QUESTION` `{ questionOriginal, questionCanonical, topic: "technique"|"history"|"plan"|"nutrition"|"recovery"|"other" }`.  
`COACHING_RESPONSE` with `citedFacts` that are either workout FactRefs **or** `kind: knowledge | catalog_exercise` with `sourceRowIds` = catalog/snippet ids from `Hit.id`. Inventing a knowledge claim with empty `sourceRowIds` is a test failure.

No set mutations unless the answer is also a command the user confirmed (e.g. “what should I do instead” + “yeah, swap it” → Feature 19).

### Database change

`conversation_turns` only. No opaque memory write from a nutrition answer.

### UI state

Last Q and A as HUD caption (Feature 25). **Not** a chat app. User is not required to tap Send.

### Error / recovery

Retrieval empty → say you don’t have a reliable answer; do not invent. LLM down → template “I can’t pull that up right now” + HUD still logs sets. Offline knowledge: local catalog can answer “what’s next” and exercise substitutions; nutrition snippets may be unavailable — honesty, not hallucination.

### Tests

- `28-qa.event.whats-next-from-plan`
- `28-qa.event.incline-last-week-from-history`
- `28-qa.event.protein-question-retrieval-only`
- `28-qa.event.medical-nutrition-refusal`
- `28-qa.event.why-shoulder-no-diagnosis`
- `28-qa.event.citedfacts-require-hit-or-row`
- `28-qa.ui.not-a-chat-tab`

---
