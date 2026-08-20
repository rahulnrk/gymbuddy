---
id: 08-four-context-assembly
status: implemented
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Four-context assembly for reasoning

**Status:** implemented  
**Core-loop role:** Kernel. Every extraction and coaching call receives the same snapshot.

FourContextAssembler reads authoritative structured state and historical data; it does not interpret natural language or mutate workout state.

### User behavior

Indirect: AI remembers last Thursday’s 50 kg because it is in historical context, not because the model “just knows.”

### System state

`assembleFourContexts` as specified. Called on every final utterance and on proactive greeting.

Historical query: last 1–3 sessions of **this exercise id** (or substitution family), last session summary, notes. Cap token size; **prefer facts over prose**.

### Voice behavior

Not a speaker. Stance is per `FactRef`. `none` → must not cite numbers. `estimated` must use estimate language and ≥2 `sourceRowIds`. `uncertain` must check DB this turn.

### Structured event

None. Read-only.

### Database change

None.

### UI state

HUD “Previous: 10, 9, 8 @ 60 kg” binds to the same historical snapshot.

### Error / recovery

DB read fail → facts with `stance=none`, **do not** use stale LLM memory. Never invent.

### Tests

- `08-context.event.empty-history-no-invented-weight`
- `08-context.event.known-thursday-60x10-citable`
- `08-context.event.estimate-requires-two-rows`
- `08-context.event.uncertain-must-check-db`
- `08-context.offline.assemble-from-local`

Natural conversation remains unrestricted. The assembler does not impose a command vocabulary.

---
