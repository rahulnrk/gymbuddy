---
id: 07-correction-reconciliation
status: implemented
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Correction / reconciliation

**Status:** implemented  

**Core-loop role:** Kernel. Voice systems will make mistakes (product §32). Designed around correction.

### User behavior

| Utterance | Result |
|---|---|
| AI: “You got 60 kilos for 10.” User: “No, 8.” | Same set, reps=8. AI: “Got it, 8.” |
| “Actually make that 9.” / “Actually I counted wrong. That was 9.” | Update most recent set reps=9, no new set |
| “No, that was the second set.” | Resolve `target` via context to the existing set whose engine-assigned slot is 2. `SetCorrected` on that `setId`. |
| Occupancy (sets 1,2,3 recorded; “That was actually my second set — 9 reps.”) | **Clarify.** Do not overwrite set 2, do not touch set 3, do not re-number. |
| “Delete that set.” / “Scratch that.” | `SET_DELETED` |
| “Wait, 55 not 50.” | weight correction on target set |

### System state

Uses conversational `recentCorrections` and workout `completedSets`. Default target: most recently completed set in current exercise. If user names “first set” / “second set”, target by `set_number`.

Target resolution (PR 1): ordinals (`first`/`second`/`third`/`last`/`this`) select an **existing** `setId` from `completedSets` (or previous-exercise last set under the 120s rule). The validator never assigns official `setNumber`.

**Occupancy (frozen):** recorded rows exist for the target slot **and** at least one later set on the same exercise (e.g. Set1 60×10, Set2 60×8, Set3 60×7; user: “That was actually my second set — 9 reps.”).

| Question | Rule |
|---|---|
| Target resolution | “second” → existing `setId` of slot 2 (context). |
| Is the existing target silently corrected? | **No.** |
| Do later sets remain unchanged? | **Yes** (validator emits nothing). |
| Clarification required? | **Yes.** Prompt: “Should I replace set 2 or keep both?” |
| Conflict rejected? | The candidate is **not accepted** (no `SetCorrected` / `SetDeleted` / `SetCompleted`). |
| Append-only history | Preserved: nothing is appended until the user answers. Re-numbering later sets is PR 2 and is not implied here. |

If the target slot exists and **no** later recorded set exists, this is not occupancy: apply `SetCorrected` to that `setId` (see `that-was-second-set`).

**`kaadu-8` (frozen):** utterance `Kaadu, 8.` (Telugu *kādu* / కాదు = “no”). Canonical interpretation is identical to English “No, 8.”: `SetCorrected` on the most recent eligible set, same `setId`, `previous` preserved, `newSetRow=false`. Storage remains canonical English numbers. Conversation language may stay Telugu.

### Voice behavior

Short confirm. Do not shame. Barge-in against the wrong confirmation. Language-flexible. Do not say “Record updated in database.”

### Structured event

`SET_CORRECTED` `{ setId, fields, previous }` or `SET_DELETED`. Idempotency: new `eventId` per correction. Never `SET_COMPLETED` for the same physical set.

### Database change

Read model set row updated. `last_correction_event_id`. Event log append-only. `source` remains original (`voice`) ; correction event `source=voice`.

### UI state

HUD numbers change in place. User does not tap a row to edit unless they want (Planning Mode / HUD optional edit is allowed but not required).

### Error / recovery

If no set to correct: “I don’t have a set to change yet.” No empty-row create. Offline corrections apply locally. Cross-exercise 120s rule (closed `SET_CORRECTED` validation).

### Tests (PR 1 executable goldens)

All of these have `input` + `given` (real `setId`s) + `expect.previous` where a set is changed. None assign official `setNumber`.

- `07-correction.event.no-8-one-row` — “No, 8.” → `SetCorrected` same `setId`, `newSetRow=false`, previous reps=10
- `07-correction.event.actually-9-after-no-8` — “Actually make that 9.” → same `setId` after the prior correction, previous reps=8
- `07-correction.event.that-was-second-set` — two existing sets; `target: "second"` resolves to SET2’s `setId`; not `targetsSetNumber=2` as authority
- `07-correction.event.previous-exercise-within-120s` — previous exercise last set 30s ago → correct that `setId`
- `07-correction.event.previous-exercise-ask-when-ambiguous` — 180s ago + topic moved → `clarify`, do not guess
- `07-correction.multilingual.kaadu-8` (`llm_required`) — Telugu kādu = no; same as “No, 8.”
- `07-correction.event.scratch-that-deleted` — `SetDeleted` same `setId`; not a replacement `SetCompleted`
- `07-correction.event.occupancy-second-set-later-exists` — three recorded sets; “second set, 9 reps” → clarify; no mutation

---
