---
id: 09-coaching-response-generation
status: implemented
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Coaching response generation

**Status:** implemented  
**Core-loop role:** Kernel. Natural speech out. Not IVR. Silent when appropriate.

The conversational system may understand arbitrary natural speech, but the application owns the decision of whether speech is useful, safe, and timely. Silence is an intentional product outcome, not a system failure.

### User behavior

Hears a trainer, not a smart speaker. Examples (product §11): “Nice.” “That’s one more than last time.” “Let’s keep the weight there.” “You’re good. Take your rest.” “Yeah, I remember. You used 50 last Thursday.” After set vs last: “Nice. Same weight, one more rep than last time.”

Does **not** hear: “Command received.” “Your set has been logged.” “Proceed to the next exercise.” Motivational cliché spam. Questions after every set.

### System state

Inputs: FourContexts, committed events this turn, safety result. **`shouldSpeak` is the only gate** (closed function above). LLM phrases allowed acts; it does not decide speak vs silence.

`citedFacts` must be subset of snapshot facts (workout + historical + retrieval Hits with catalog ids). Each fact has `stance`.

### Voice behavior

- MUST/MUST NOT as in `shouldSpeak`. Same-reps same-weight mid-exercise **must not** speak. +1 rep vs last set **must** speak (`useful_delta`).
- Concise during rest; longer only for Feature 28 questions.
- Same language as user when LID confident; otherwise primary_language.
- Barge-in cancels in-progress coaching.
- Nutrition: Feature 28, secondary, non-medical.
- `SET_ACTIVE`: no TTS except safety ≥ high. **Precedence:** this MUST NOT wins over questions / weight-change / useful-delta. Queue those acts; drain one on RESTING (`09-coaching.voice.question-during-set-deferred`).

### Structured event

`COACHING_RESPONSE` **always**: `{ textSpoken: string | null, lang, silence: boolean, actId?, citedFacts }`. `USER_QUESTION` for questions.

### Database change

`conversation_turns` role=ai with `silence` boolean. No set mutations. `recommend()` suggestions never write `prescribed_weight_kg`.

### UI state

Caption of last AI line (Feature 25). Not a chat thread that the user must scroll.

### Error / recovery

LLM fail → templates for `correction_ack` / `session_greeting`. Never speak a number not in `citedFacts`. If cite check fails, strip the sentence.

### Tests

- `09-coaching.voice.same-reps-must-not-speak`
- `09-coaching.voice.plus-one-rep-must-speak`
- `09-coaching.voice.set-active-must-not-speak`
- `09-coaching.voice.question-during-set-deferred` — USER_QUESTION in SET_ACTIVE: silence now; `answer_question` drained on RESTING; no talk-over
- `09-coaching.event.empty-history-no-last-thursday`
- `09-coaching.event.silence-row-always-persisted` (silence is an intentional product outcome)
- `09-coaching.voice.banned-ivr-strings`
- `09-coaching.event.recommend-talk-only-no-plan-write`

---
