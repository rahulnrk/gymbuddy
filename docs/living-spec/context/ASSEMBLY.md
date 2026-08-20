# Four-context assembly

FourContextAssembler reads authoritative structured state and historical data; it does not interpret natural language or mutate workout state.

```
Committed WorkoutEvents
  + WorkoutSessionSnapshot
  + stored workout/history rows
  + structured user data
  + structured conversational records
        ↓
FourContextAssembler.assemble()
        ↓
FourContexts
  workout / historical / conversational / user
  + FactRef[] (EpistemicFact)
        ↓
future conversational / AI layer
```

Natural conversation remains unrestricted. The assembler does not turn speech into commands. It prepares trustworthy context so a later AI layer can talk naturally without inventing numbers.

## Laws

- Read-only. No Room writes, no `EventLog.append`, no `WorkoutEvent` creation, no snapshot mutation.
- Deterministic. Same snapshot + events + stores → same `FourContexts`. No wall clock, no LLM, no random ids.
- Empty history stays empty (`stance=none`). Current working weight is never copied into historical context.
- Current vs historical stay distinct, even when both mention the same exercise and different weights.
- `FactRef` is `EpistemicFact`. Stance is per fact: `known | estimated | inferred | uncertain | none`.
- Provenance is `sourceRowIds` (existing PR 0 model): `set:{id}`, `session:{id}`, `history:{date}`, `note:{at}`, `pr:…`. No parallel provenance enum.
- Estimated aggregates require ≥2 source rows. Uncertain facts are not speech-citable until a later DB check.
- Conversational context is structured records (topic, pending clarification, recent corrections, last structured utterance). Not a transcript dump.

## Not in this layer

Speech parsing, STT/TTS/LLM, set-number assignment, workout transitions, HUD, dashboard, Room schema expansion.
