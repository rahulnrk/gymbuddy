# Safety layer (PR 6)

Authoritative table: [`safety/DECISION_TABLE.md`](safety/DECISION_TABLE.md).

Safety classification is deterministic and independent of generative model behavior.

Natural human conversation remains unrestricted; the safety layer interprets that conversation internally without requiring safety commands.

```
raw/final transcript
  → DefaultSafetyClassifier.classify
  → SafetyResult + SafetyResponsePolicy
  → extraction (SafetyFirstTurn)
```

The LLM cannot override HIGH/CRITICAL. Classifier does not diagnose, mutate workout state, or write Room.

`fired=false` is the existing representation of no safety concern (the `SafetySeverity` enum has no `NONE` value).

## Templates (no diagnosis)

| Id | Text |
|---|---|
| `safety.critical.stop_seek_help` | Stop the exercise and seek appropriate medical attention. |
| `safety.high.pause_no_push` | Let's pause this exercise. Don't push through. |
| `safety.medium.advise_reduce` | I hear you. We can reduce the load, switch this movement, or stop this exercise. |
