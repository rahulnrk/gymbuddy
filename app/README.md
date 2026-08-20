# :app — Android shell

**PR 0: this module has no source.**

First UI is the Gym Mode HUD (PR 10), a glanceable binder to the session snapshot. Conversation is a caption, not a chat app.

**Dashboard is PR 18.** Do not add `TodayScreen`, navigation graphs, or Compose material dashboards here until the kernel loop (PR 9) exists.

Deterministic voice fakes live in `:domain` (PR 5). Real vendor adapters (STT/TTS/LLM/AudioFocus) land in `:app` in PR 23–24. They must not live in `:domain`.
