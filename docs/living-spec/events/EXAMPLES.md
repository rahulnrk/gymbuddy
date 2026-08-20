# CandidateEvent examples

Valid envelopes pass `CandidateEventEnvelope.parse` and `CandidateSchemaGate`.
They are **not** database writes.

## Valid: “Got 10.” (inherit weight later, PR 1)

```json
{
  "schemaVersion": 1,
  "candidates": [
    {
      "candidateId": "77777777-7777-4777-8777-777777777777",
      "type": "SET_COMPLETED",
      "overallConfidence": 0.92,
      "fieldConfidence": { "reps": 0.96 },
      "loggingMode": "live",
      "payload": { "reps": 10 }
    }
  ]
}
```

## Valid: correction “No, 8.”

```json
{
  "schemaVersion": 1,
  "candidates": [
    {
      "candidateId": "77777777-7777-4777-8777-777777777777",
      "type": "SET_CORRECTED",
      "overallConfidence": 0.94,
      "fieldConfidence": { "reps": 0.94 },
      "payload": { "reps": 8, "target": "last" }
    }
  ]
}
```

## Valid: skip command (engine commits EXERCISE_SKIPPED)

```json
{
  "schemaVersion": 1,
  "candidates": [
    {
      "candidateId": "77777777-7777-4777-8777-777777777777",
      "type": "COMMAND",
      "overallConfidence": 0.85,
      "fieldConfidence": { "kind": 0.85 },
      "payload": { "kind": "skip" }
    }
  ]
}
```

## Invalid: reps as English word (schema)

```json
{
  "schemaVersion": 1,
  "candidates": [
    {
      "candidateId": "77777777-7777-4777-8777-777777777777",
      "type": "SET_COMPLETED",
      "overallConfidence": 0.9,
      "fieldConfidence": { "reps": 0.9 },
      "payload": { "reps": "ten" }
    }
  ]
}
```

Fails: `reps` must be integer. Golden: `05-extract.event.reps-as-string-ten-fails-schema`.

## Invalid: hallucinated type

```json
{
  "schemaVersion": 1,
  "candidates": [
    {
      "candidateId": "77777777-7777-4777-8777-777777777777",
      "type": "HALLUCINATED_PR",
      "overallConfidence": 0.99,
      "fieldConfidence": {},
      "payload": { "weightKg": 200 }
    }
  ]
}
```

Fails enum. Drop. Never write PRs into history.

## Invalid: extra envelope key that looks like a DB mutation

```json
{
  "schemaVersion": 1,
  "insertIntoSets": true,
  "candidates": []
}
```

Fails `additionalProperties: false`.

PR 1 maps a valid envelope to `ValidatedFact`, never to a `JsonObject` that could hold `SET_STARTED` or official `setNumber`.

## Invalid as committed truth

A CandidateEvent is never a `WorkoutEvent`. This is illegal even if field names look similar:

```json
{
  "eventId": "88888888-8888-4888-8888-888888888888",
  "type": "SET_COMPLETED",
  "setNumber": 7,
  "reps": 10
}
```

Missing EventBase, engine-assigned setNumber, setId, loggingMode. Extractor output must be the candidate envelope, not this.
