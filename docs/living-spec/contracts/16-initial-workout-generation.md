---
id: 16-initial-workout-generation
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Initial workout generation

**Status:** specified  
**Core-loop role:** Feeds the loop with **today’s plan**. One engine for AI-generated, predefined, and custom (product §5). User must never feel locked in.

### User behavior

After onboarding, get an initial routine. Accept, edit, replace, reorder, add/remove, substitute. May pick Full Body, Upper/Lower, PPL, strength, hypertrophy, beginner, or build custom.

Inputs: goal, experience, frequency, duration, equipment, preferences, limitations if provided, history if any.

### System state

Same `workouts` / `workout_exercises` model regardless of origin (`ai-generated` | `predefined` | `custom`).

**MVP generation algorithm (one engine, not a programming AI):**

1. In-repo **templates** keyed by `goal × days_per_week` (minimum templates: Full Body 3d, Upper/Lower 4d, PPL 6d, beginner full body 3d).
2. Filter every template exercise to `user.equipment` (treat `full_gym` as all `EquipmentId` except exclusive home-only). If a slot has no legal exercise, drop the slot.
3. Clamp accessories until `estimated_duration_min` is within **±15 min** of `typical_duration_min` (default 60 if unset). Experience maps to intensity **only** via template column `target_reps` / `planned_sets` (beginner: fewer sets), **not** via invented loads. `prescribed_weight_kg` stays null until the user logs.
4. LLM **may** swap a slot from the catalog with the same `movement_pattern` + muscle + equipment. It must not add unknown names.
5. User edits through Feature 17.

**Minimum catalog (Q9 partial):** PR 14a authors the in-repo catalog from `voice-workout-os-source/` (61 exercises). ≥80 + templates are PR 14b. `docs/research/` is reference only.

### Voice behavior

**Not in Gym Mode for the generator UI.** After generation, Gym Mode greeting uses the plan. User in gym “change this workout” is editing/substitution (Features 17, 19), not a second generator.

### Structured event

Plan creation is not a live session event. Planned rows are `origin` on workout; planned sets are **not** completed sets. `ai-generated` means plan origin, never claimed performance.

### Database change

Insert workout + workout_exercises. No `workout_sets` completed rows.

### UI state

Editable plan screen. Accept CTA. START WORKOUT still the gym entry.

### Error / recovery

LLM swap fail → keep the filtered template. Never output exercises not in bank. Never invent that the user “already did” this plan.

Gym Mode may start whenever a planned workout **row** exists (PR 3 fixture is enough). `SESSION_INITIALIZED` does **not** require the ≥80 catalog. Empty catalog fails **`generatePlan` / search / substitution extras**, not session start. Catalog ≥80 is PR 14 (generation/search).

### Tests

Three origins produce the same engine types. No cable exercises if no cable equipment. Duration clamp ±15 min. Empty history does not include fake PRs. Template-only path works with LLM down.

---
