# :app — Gym Mode HUD binder (PR 10)

Glanceable HUD bound to `WorkoutEngine` session snapshot. Layout matches the
working Voice Workout OS prototype (`06-body.html` `#screenWorkout` + `#pocket`
+ `#dock`): now-card (weight / set / target, set grid, undo), rest ring,
pocket mode, type-instead dock.

Conversation is a caption stub, not a chat app. `#feed` as a thread is PR 11.

## Two MVPs of the same product

Do not mix them in one change.

| Tree | What it is |
|---|---|
| `C:\Users\nande\Downloads\GYM` | Browser prototype. Real screens, mic, pocket mode. Newer (Grok voice, check-in/prep). |
| `C:\Users\nande\Downloads\gymbuddy` | Android-first Kotlin kernel + living spec. This module. |
| `gymbuddy/voice-workout-os-source` | Older copy of the prototype (warmup box). Not `:app`. |

- `GymHudBinder` maps snapshot + voice turn → HUD fields, a11y labels, focus order, pocket.
- `GymHudSession` binds Start/Pause/Resume and type-instead / number pad to the existing session service. It does not invent lifecycle event types.
- CTA / typed set log are `source=manual`. Hydrate (`SESSION_INITIALIZED`, `EXERCISE_STARTED`) stays `source=system`.
- Caption stub is Feature 25 / PR 11: two optional lines, minimized, never a chat thread.

**Dashboard is PR 18.** Do not add `TodayScreen`, bottom nav, or the body map here.

Deterministic voice fakes live in `:domain` (PR 5). Real vendor adapters (STT/TTS/LLM/AudioFocus) land in `:app` in PR 23–24. They must not live in `:domain`.
