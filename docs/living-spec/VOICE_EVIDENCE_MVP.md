# Voice Evidence MVP

**This is engineering validation tooling, not the consumer MVP.** It exists to
answer one question with a number:

> Can a person in a real gym speak naturally and have GymBuddy arrive at the
> correct structured workout event?

It is deliberately not PR 10. There is no HUD, no dashboard, no onboarding, no
production LLM, no backend.

## System under test

The PR 0-9 kernel, unchanged:

```
real audio -> real STT -> SafetyFirstTurn -> NaturalLanguageExtractor
           -> CandidateValidator -> WorkoutEngine -> WorkoutEvent
```

Only the STT is swapped. Everything downstream is the shipping kernel, so a
result here is a statement about the product, not about a test harness.

## Status

| Item | State |
|---|---|
| Label schema | `evidence/schema/utterance.schema.json` - done |
| Scorecard | `scripts/score_evidence.py` - done, self-tested |
| STT adapter boundary | **not built** |
| Evidence runner | **not built** |
| Real utterances collected | **0** |
| Providers compared | **0** |
| Measured end-to-end accuracy | **not measured** |

`scripts/score_evidence.py` exits 1 and refuses to print an accuracy figure
until real labels and real runs exist. Do not quote a number it has not printed.

## Dataset target (~100 utterances)

Do **not** fabricate these. Collect them from real sessions.

| Category | Target | Examples |
|---|---|---|
| Set logging | 20 | "Got 10." / "Did 8." / "40 for 10." |
| Numbers and weights | 15 | "Fifty five for ten." / "Thirty seven point five for eight." / "Twenty each hand for twelve." |
| Corrections | 10 | "No, 8." / "Actually make that 9." / "That was the second set." / "Scratch that." |
| Questions | 10 | "What did I do last week?" / "Should I rest longer?" |
| Casual feedback | 10 | "Bro this is heavy." / "That felt easy." / "I'm tired." |
| Commands | 10 | "Let's skip this." / "Can we do one more?" / "That machine is occupied." |
| Safety / discomfort | 10 | "My shoulder feels weird." / "That hurts." |
| Multilingual / code-mixed | 15 | Telugu, Hindi, Telugu-English, Hindi-English |

**Safety category rule.** Collect these from people describing discomfort they
actually had, or reading a phrase back in a normal voice between sets. Never ask
anyone to train into pain, keep lifting while dizzy, or manufacture a real
incident. The contract in `contracts/11` is about recognising a report, and a
calmly spoken report is a valid sample.

## Conditions to cover

Quiet gym, normal gym noise, loud gym; music on and off; headset and
speakerphone; bystanders nearby; heavy breathing after a set; male and female
voices; a range of accents; varying mic distance; English, Telugu, Hindi, and
code-mixed speech.

Ordinary workouts are sufficient. No unsafe setups.

## Labelling rule

The `expected` block is **human-assigned**, before or independently of seeing
what the system produced. Labelling an utterance from the model's own output
measures nothing. Ambiguous utterances are labelled `CLARIFY`, which is a
correct outcome, not a failure.

Privacy (PRODUCT.md 35): audio lives under `evidence/audio/`, which is
git-ignored. Only manifests and labels are committed. Speaker ids are
pseudonymous. Delete audio once transcripts and labels exist.

## Running it

```
python3 scripts/score_evidence.py
```

Reads `evidence/labels/*.jsonl` and `evidence/runs/<provider>.jsonl` and prints,
per provider: WER, intent accuracy, weight/rep/exercise accuracy, and the
headline set - end-to-end correct %, false workout mutation %, missed event %,
clarification rate, safety recall.

It reports providers side by side and picks no winner. That is a human decision
made on the numbers, and Q3 stays open until then.

## What still has to be built

1. **`SttProvider` adapters** behind the existing port for 2-3 real vendors.
   `:domain` must not gain a vendor import; adapters live outside it, exactly as
   `voice/PORTS.md` requires.
2. **Evidence runner** - feed each audio file through each provider, then push
   the transcript through the real kernel and record the actual
   `CandidateEvent`, `ValidationDecision` and `WorkoutEvent` to
   `evidence/runs/<provider>.jsonl`.
3. **A gym, a phone, headphones, and real workouts.**

## Success criterion

Not "the architecture works" - that is already true and proves nothing about
speech. Success is a measured end-to-end number on real gym utterances, with the
false-mutation rate low enough that a user can trust the log without checking it.

Set the bar before looking at the results.
