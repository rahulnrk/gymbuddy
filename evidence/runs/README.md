# Provider run output

One file per STT provider: `<provider>.jsonl`, one line per utterance.

```json
{"utteranceId":"U001","transcript":"got ten","sttConfidence":0.91,"sttDurationMs":620,
 "language":"en","sttFinalHash":"...","latencyMs":740,
 "actual":{"intent":["SET_COMPLETED"],"exercise":"Bench Press","weightKg":60,"reps":10,
           "mutated":true,"clarified":false,"safetySeverity":null,
           "validationDecision":"Accept","workoutEventType":"SET_COMPLETED"}}
```

`actual` is whatever the real PR 0-9 kernel produced. Never hand-edit it to
match the label - that destroys the measurement.

Empty until a real run happens. `scripts/score_evidence.py` exits 1 until then.
