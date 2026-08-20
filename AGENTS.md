# GymBuddy — binding rules for humans and coding agents

This file is product law for implementation. It is not a style guide.

Canonical documents:

- Product law: `PRODUCT.md` (wins on product behavior)
- Living spec: `docs/living-spec/`
- Complete spec: `docs/living-spec/TECHNICAL_SPEC.md`
- Feature contracts: `docs/living-spec/contracts/`
- Frozen schemas: `docs/living-spec/schemas/v1/`
- Event catalog: `docs/living-spec/events/CATALOG.md`
- State machine contract: `docs/living-spec/state-machine/CONTRACT.md`
- LLM vs app: `docs/living-spec/boundaries/LLM_VS_APPLICATION.md`

**MVP stack:** Android first; Kotlin `:domain` (no Android SDK); Room `:persist` (PR 3); `:app` UI+adapters later. Cloud STT/LLM behind ports. No MVP sync server. Vendors unset.

## The product

GymBuddy is an **AI gym companion**, not a workout tracker with a chatbot.

The user operates the workout, not the app. Conversation is first-class and unconstrained. Structured events are an internal reliability boundary, not a command language the user must learn.

Kernel loop:

```
music playing
→ user talks naturally
→ AI understands context
→ structured workout event
→ AI responds naturally (or stays silent)
→ history is updated
→ future coaching becomes better
```

## Before writing any code

1. Read `PRODUCT.md`.
2. Read the matching file in `docs/living-spec/contracts/`. If none exists, **stop**.
3. If the change is dashboard, chat UI, social, wearables, CV, or vendor SDK in `:domain` — **stop**.
4. Confirm the change serves the kernel loop.

## Feature contract (required)

Every major feature: `user behavior → system state → voice behavior → structured event → database change → UI state → error/recovery → tests`.

Status: `proposed | specified | implementing | implemented | drifted`.

A feature is **not done** if the living spec and tests do not match the code.

## Same-change rule

Update the living spec **in the same change as the code**. Frozen schemas cannot change without:

1. bump or explicit schemaVersion decision in the spec
2. updated goldens
3. updated Kotlin types
4. updated `CATALOG.md` / contract

Silent schema edits are forbidden.

## Forbidden (reject the PR)

1. `INSERT` / Room / SQL from LLM output, conversation packages, or extractor code.
2. Changing workout state without a committed `WorkoutEvent`.
3. Inventing workout history (citing numbers not in `HistoricalStateContext`).
4. Bypassing `CandidateValidator` or the schema gate.
5. Bypassing the state-machine contract (e.g. `WORKOUT_STARTED` from `IDLE`, `SET_ACTIVE` without `SET_STARTED`).
6. Adding unsupported product features (social, marketplace, wearables, CV, gamification, avatars, chat-tab-as-product, notifications, automatic program mutation, multi-device merge).
7. Silently changing contracts.
8. Modifying frozen schemas without tests/spec in the same change.
9. Treating `estimated` / `inferred` / `uncertain` / `none` as `known`.
10. **Implementing the dashboard before the kernel is complete** (kernel = PR 0–9). Contract 26 stays `specified` until PR 18. No `app/` UI sources before PR 10 HUD.
11. Requiring command language.
12. Retaining raw audio indefinitely.
13. Vendor SDK in `:domain`.
14. Marking a contract `implemented` without mapped goldens.

## Architectural laws

- Deterministic workout state machine owns workout truth.
- LLM is interpreter + speaker, not source of truth.
- Speech → CandidateEvent → validation → append-only log → projector.
- Corrections are `SET_CORRECTED` / `SET_DELETED` on an existing `setId`, not duplicate set rows. Validator output is `ValidatedFact`, never `JsonObject` drafts or official `setNumber`.
- Local-first session. Process kill must not erase sets.
- VoiceTurnState ⊥ WorkoutSessionState.
- Safety is a dedicated layer on raw transcript before extract.
- Storage is canonical English / normalized values. Conversation may be code-mixed.
- History comes from structured data, never model memory.
- MVP must not automatically mutate the user's workout program.
- Gym Mode is voice-first, not a chat application.
- In-session swap commits only `SUBSTITUTION_APPLIED`.
- `SET_ACTIVE` + TTS forbidden except safety ≥ high.

## PR blocking

Block merge when any Forbidden item is true, when a major feature lacks a contract, when kernel files are stale, or when `scripts/check_living_spec.py` fails.

## Current PR gate

You are not allowed to start PR 1 work in a PR 0 change. You are not allowed to start dashboard work until PR 9 exists.
