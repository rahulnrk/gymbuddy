---
id: 05-structured-event-extraction
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Structured event extraction and validation

**Status:** implemented  

**Core-loop role:** Kernel. The only gate from language to truth.

`SafetyFirstTurn` always runs before extraction. Unknown types and `"reps": "ten"` fail the frozen schema. The extractor never writes Room or assigns official set numbers.

### User behavior

Invisible except when asked to clarify. User should rarely notice extraction; they notice only wrong logs (then Feature 7).

### System state

Receives frozen `CandidateEvent[]` + `FourContexts`. Intent is **multi-label** (see Intent detection). Safety classifier already ran on raw text before this commit gate.

Unknown `type` values dropped. `json` that is not `{ candidates: [...] }` is a schema fail.

### Voice behavior

On high ambiguity: one short clarification, then silence. Do not interview. On `INTENT_REJECTED` with casual speech: silence.

### Structured event

Output: validated catalog events **or** `CLARIFICATION_REQUESTED` **or** `INTENT_REJECTED`.

Rules:

- Unknown types dropped.
- `SET_COMPLETED` without current exercise and without identifiable `exerciseNameRaw` that resolves to catalog → clarify.
- Questions → `USER_QUESTION`, no set.
- Corrections → `SET_CORRECTED`/`SET_DELETED`, never a second `SET_COMPLETED` for the same slot.
- Field-level `fieldConfidence`; `overallConfidence < 0.55` blocks unless the mandatory weight-inherit path applies.
- If spoken weight differs >20% from working weight without drop/up language → clarify (0.7 effective bar).

Idempotency: committed `eventId` is new (not `candidateId`). Duplicate STT uses `sttFinalHash` + 10s `recordedAt`.

### Database change

No writes except via engine after validation. Validator itself is pure.

### UI state

If clarifying, HUD shows a small “waiting for confirm” voice caption. No modal that requires a tap (voice can resolve). Optional tap chips for 50 vs 55 as accessibility, not required.

### Error / recovery

Schema-invalid LLM JSON → retry once with stricter prompt → rules fallback (English matrix) → if still fail, `ERROR_RECOVERY` and optional “I missed that.”

### Tests

- `05-extract.event.unknown-type-dropped`
- `05-extract.event.extra-json-field-rejected-by-schema`
- `05-extract.event.double-final-same-hash-10s`
- `05-extract.event.last-week-as-set-completed-rejected`
- `05-extract.event.safety-not-a-set`

---
