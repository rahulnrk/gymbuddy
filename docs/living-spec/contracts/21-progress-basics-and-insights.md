---
id: 21-progress-basics-and-insights
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Progress basics and progress insights

**Status:** specified  
**Core-loop role:** Supporting analyze/adapt. Distinguishes observation, interpretation, recommendation (product §23). Does not replace the gym loop.

### User behavior

Sees daily/weekly/monthly/long-term: completion, volume, duration, PRs, consistency, muscle-group frequency, strength changes. Insights like “You’ve increased your bench press from 50 to 65 kg over the last eight weeks.” — only if true.

### System state

`progress_metrics` computed from sets. Insights engine labels `{kind: observation|interpretation|recommendation, evidence: FactRef[]}`.

### Voice behavior

**Planning Mode screens: silent.** Gym Mode may cite a progress observation if useful (“your volume is up from last Thursday”) **only** with citedFacts. No wall of text at any time.

### Structured event

None required for viewing. Coaching may include facts. No fake PRs as events.

### Database change

`progress_metrics` derived. `source=system`.

### UI state

Simple charts/lists, not dashboard spam (product §6). Gym HUD does not become analytics.

### Error / recovery

Insufficient data → no insight, not a fabricated trend. “Stalled three weeks” only if three dated sessions exist.

### Tests

50→65 kg over 8 weeks observation when data matches. Empty user: no “you’re up 15 kg.” Voice citations guarded. Observation vs recommendation split in API/schema.

---
