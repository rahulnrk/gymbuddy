# Safety layer

**Status:** implemented (PR 6 `DefaultSafetyClassifier`). Dedicated layer. Not the generative model. Runs on **raw transcript before extraction**.

Safety classification is deterministic and independent of generative model behavior.

Natural human conversation remains unrestricted; the safety layer interprets that conversation internally without requiring safety commands.

Cannot be feature-flagged off in production Gym Mode.

Classifier fail-open conservative on pain / chest / dizzy / faint / SOB keywords even if the LLM says the user is fine.

False-positive policy: exercise names containing “chest” / “fly” are excluded unless a pain collocate is within a 6-token window.

No diagnosis. No “push through.”

Kotlin: `com.gymbuddy.domain.safety.SafetyDecisionTable`.

## Decision table

| Utterance | category | severity | action | persist note? | pause? | allow SET_COMPLETED? |
|---|---|---|---|---|---|---|
| “my chest hurts” / “chest pain” | chest_pain | critical | stop_recommend | yes | yes | no if no numbers; if “I got 10 but my chest hurts” → yes (truth) + pause |
| “I feel faint” / “dizzy, going to pass out” | faintness / dizziness | critical | stop_recommend | yes | yes | same |
| “I can’t breathe” (not “breathing hard after a set”) | severe_sob | critical | stop_recommend | yes | yes | same |
| “sharp pain in my shoulder” / “I think I tore something” | sharp_pain / injury | high | pause | yes | yes | yes if numbers present |
| “My shoulder feels a little weird on this.” | discomfort_mild | medium | advise | yes | no | yes |
| “Naku chest kanna shoulder lo ekkuva feel avutundi.” | discomfort_mild | medium | advise | yes | no | yes if also a set |
| “I got 10 but my shoulder feels weird” | discomfort_mild | medium | advise | yes | no | **yes** (10) |
| “That chest fly machine is occupied.” | — | — | none | no | no | Feature 19 |
| “Why do I feel this in my shoulder?” (no pain words) | — | — | none | no | no | Feature 28; **no** SAFETY_CONCERN |
| “How much protein should I have?” | — | — | none | no | no | Feature 28 |
| “Bro, this is heavy.” | — | — | none | no | no | USER_FEEDBACK hard |

Critical template (LLM cannot override): recommend **stop activity** and seek professional help.

Goldens: `11-safety.event.chest-pain-critical`, `11-safety.event.chest-fly-occupied-not-critical`.
