# GymBuddy

Voice-first AI gym companion. The user operates the workout, not the app.

Not a workout tracker with a chatbot. **Do not start with the dashboard.**

## Read this first

1. [`PRODUCT.md`](PRODUCT.md) — product law
2. [`AGENTS.md`](AGENTS.md) — binding rules
3. [`docs/living-spec/PR0_DEFINITION_OF_DONE.md`](docs/living-spec/PR0_DEFINITION_OF_DONE.md)
4. [`docs/MODULE_BOUNDARIES.md`](docs/MODULE_BOUNDARIES.md)
5. [`docs/living-spec/TECHNICAL_SPEC.md`](docs/living-spec/TECHNICAL_SPEC.md)

Kernel:

**music playing → user talks naturally → AI understands context → structured workout event → AI responds naturally → history is updated → future coaching becomes better**

## Status

**PR 0–9 complete.** Headless companion loop. No UI. No real LLM/STT/TTS vendor. No cloud sync.

Stack: Android first, Kotlin `:domain`, SQLite/Room in `:persist`. Cloud STT/LLM allowed behind interfaces. No backup-sync server.

Conversation is first-class and unconstrained. Structured events are an internal reliability boundary.

## Checks

```
python scripts/check_living_spec.py
```

```
./gradlew.bat :domain:test
./gradlew.bat :persist:test
```

## What not to do next

Do not pick an AI vendor. PR 10 is the Gym Mode HUD binder (glanceable, not a chat app). Do not start the dashboard.
