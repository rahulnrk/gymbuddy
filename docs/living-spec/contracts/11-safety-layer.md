---
id: 11-safety-layer
status: implemented
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Safety layer

**Status:** implemented  
**Core-loop role:** Kernel guard. Dedicated layer, not the generative model (product §34).

Safety classification is deterministic and independent of generative model behavior. Natural conversation remains unrestricted; users never speak safety commands.

### User behavior

User: “My shoulder feels a little weird on this.” / “I’m feeling this more in my shoulder.” / “Naku chest kanna shoulder lo ekkuva feel avutundi.” / sharp pain, dizziness, faintness, chest pain, severe shortness of breath.

Expect conservative, non-diagnostic response. Not “that’s your rotator cuff tear.” Not “push through.”

### System state

Runs on **raw transcript before extraction commit** and before any TTS. May `WORKOUT_PAUSED` on high/critical. Categories: `sharp_pain`, `injury`, `dizziness`, `faintness`, `chest_pain`, `severe_sob`, `dangerous_condition`, `discomfort_mild`. Severity: `low | medium | high | critical`. Action: `advise | pause | stop_recommend`.

**Keyword/pattern seeds (not exhaustive; classifier + these MUST fire even if LLM disagrees):**

- critical: `chest pain`, `pain in my chest`, `can't breathe`, `can't breath`, `severe shortness of breath`, `passing out`, `about to faint`, `faint`, `dizzy and` + fall, `heart`
- high: `sharp pain`, `sudden pain`, `popped`, `tore`, `injury`, `I think I tore`
- medium: `hurts`, `pain` (not “chest fly”), `weird in my shoulder` + heavy, Telugu/Hindi pain glosses (`noppi`, `dard`)
- not-safety: `chest fly`, `chest press`, `why do I feel this in my shoulder?` **without** pain/hurt/injury words (Feature 28 technique Q), `occupied`

Same-utterance set + safety: if they reported a set, **commit the set** (truth) unless the utterance is *only* the safety phrase with no numbers.

Medical technique questions without pain language: **no** `SAFETY_CONCERN`.

### Voice behavior

Closed table (copy also lives in `SAFETY.md`):

| Utterance | category | severity | action | persist note? | pause? | allow SET_COMPLETED? |
|---|---|---|---|---|---|---|
| “my chest hurts” / “chest pain” | chest_pain | critical | stop_recommend | yes | yes | no if no numbers; if “I got 10 but my chest hurts” → yes (truth) + pause |
| “I feel faint” / “dizzy, going to pass out” | faintness / dizziness | critical | stop_recommend | yes | yes | same |
| “I can’t breathe” (not “breathing hard after a set”) | severe_sob | critical | stop_recommend | yes | yes | same |
| “sharp pain in my shoulder” / “I think I tore something” | sharp_pain / injury | high | pause | yes | yes | yes if numbers present |
| “My shoulder feels a little weird on this.” / “I’m feeling this more in my shoulder.” | discomfort_mild | medium | advise | yes | no | yes |
| “Naku chest kanna shoulder lo ekkuva feel avutundi.” | discomfort_mild | medium | advise | yes | no | yes if also a set; else note only |
| “I got 10 but my shoulder feels weird” | discomfort_mild | medium | advise | yes | no | **yes** (10) |
| “That chest fly machine is occupied.” | — | — | none | no | no | no set; Feature 19 |
| “Why do I feel this in my shoulder?” (no pain words) | — | — | none | no | no | Feature 28; **no diagnosis** |
| “How much protein should I have?” | — | — | none | no | no | Feature 28 |
| “Bro, this is heavy.” | — | — | none | no | no | `USER_FEEDBACK` hard |

Critical template (must-not be overridden by LLM): recommend **stop activity** and seek professional help. No “push through.” No diagnosis.

Medium: acknowledge, suggest reducing load / substituting / stopping **that** exercise; store note.

Barge-in still works. Language-flexible templates.

### Structured event

`SAFETY_CONCERN` always when the table says so. Optional `USER_NOTE_CREATED`, `USER_FEEDBACK`, `WORKOUT_PAUSED` reason=safety. Block `suggest_weight_this_session` this turn.

### Database change

Persist concern + optional note. User-visible. `source=voice` or `system`.

### UI state

HUD can show a calm safety banner. User not forced through a medical questionnaire.

### Error / recovery

Classifier **fail-open conservative** on pain/chest/dizzy/faint/SOB keywords even if LLM says “you’re fine.” Cannot disable in production Gym Mode. False-positive policy: exercise names containing “chest” / “fly” are excluded unless a pain collocate is present within a 6-token window.

### Tests

- `11-safety.event.chest-pain-critical`
- `11-safety.event.chest-fly-occupied-not-critical`
- `11-safety.event.got-10-shoulder-weird-commit-set-and-note`
- `11-safety.event.telugu-shoulder-medium-advise`
- `11-safety.event.why-shoulder-question-no-safety-concern`
- `11-safety.voice.no-diagnosis-no-push-through`

---
