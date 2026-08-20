---
id: 04-conversational-workout-parsing
status: implementing
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Conversational workout parsing / natural-language set logging

**Status:** implementing  

**Core-loop role:** Kernel NLU. Turns natural speech into candidate set facts using four contexts. Does not commit DB.

Natural language is unrestricted at the conversational boundary. `CandidateEventEnvelope` is an internal structured interpretation; it is not a restriction on the user's vocabulary. Extraction does not imply authorization to mutate workout state.

### User behavior

Examples that **must** work (product §§2, 9, 15):

| Utterance | Meaning in context |
|---|---|
| “Did 10.” / “Got 10.” / “That’s 10.” / “Ten reps.” | Current exercise, working weight, 10 reps, one set |
| “40 for 10.” / “40 kilos, eight.” | Weight + reps |
| “First one was 12, second was 10, and this one was 8.” | Three sets; inherit weight if given once (“at 40”) |
| “First set was 12 at 40, second was 10, and I got 8 on this one.” | Set1 40×12, set2 40×10, set3 40×8 |
| “I did 25 for 10, but the last two were really hard.” | 25×10 + feedback high effort |
| “Let’s drop the weight.” | `WEIGHT_CHANGED` down, not a set |
| “Can we do one more?” | question/command: extra set, not log |
| “What’s next?” | question |
| “Bro, this is heavy.” | feedback, not a set |
| “Nenu 60 kilos tho eight reps chesa.” | 60 kg × 8 |
| “Last time entha weight vesanu?” | question, historical |
| “Aaj kya karna hai?” | question, today’s plan |

Never require “Log set”.

### System state

Current exercise and working weight from workout context. If `READY_FOR_EXERCISE` and user logs, engine emits `EXERCISE_STARTED` then applies **live** or **catch-up** projector laws (`SET_STARTED` only for live `SET_ACTIVE` entry, not per backfilled set).

### Voice behavior

After parse+commit, `shouldSpeak` decides TTS (MUST/MUST-NOT). If parse ambiguous: “Was that 10 reps at 50 or 55?” only when both are plausible (`CLARIFICATION_REQUESTED`). Language matches user.

### Structured event

Candidates: `CandidateEvent[]` (frozen schema). One or more `SET_COMPLETED`, optional `USER_FEEDBACK`, optional `WEIGHT_CHANGED`. Multi-set → `loggingMode=catch_up` and shared `batchId`. Each candidate has `candidateId`; committed events get new `eventId`. `fieldConfidence` per field. `setNumberHint` for ordinals.

Validation is Feature 5–6. Parser attaches `weightUnit` from words (“kilos”) or profile.

### Database change

None directly. Source of eventual sets = `voice`.

### UI state

HUD updates after commit. User does not tap Save.

### Error / recovery

Garbage STT (“got hen”) → no event. Partial “forty for—” cut off → wait / ask, don’t log 40×null unless user confirms.

### Tests

- `04-nl-logging.event.got-10-inherit-weight` — MUST commit with working weight
- `04-nl-logging.event.did-10-no-working-weight-clarify` — MUST NOT commit
- `04-nl-logging.event.40-for-10`
- `04-nl-logging.event.multi-set-40x12-10-8` — PR 1 layer: three `SetCompleted` facts, inherit 40, hints only; **not** official set numbers or rest. Same utterance as `06-state-machine.event.catch-up-40x12-10-8` (PR 2). `llm_required`
- `04-nl-logging.event.drop-weight-not-a-set`
- `04-nl-logging.event.telugu-60x8` (`llm_required`)
- `04-nl-logging.offline.fallback-got-10-english`

---
