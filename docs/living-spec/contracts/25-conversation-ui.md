---
id: 25-conversation-ui
status: specified
schemaVersion: 1
source: docs/living-spec/TECHNICAL_SPEC.md
---

## Feature: Conversation UI

**Status:** specified  
**Core-loop role:** Supporting visualization / debugging. **Not a chat app** (product §43). Voice is primary.

### User behavior

If they glance: latest user line and AI line. “User said: 40 for 10.” “AI: Nice. Let’s stay there.” Then minimize. Not a transcript they must manage.

### System state

Binds to last 1–2 `conversation_turns` in Gym Mode. Planning Mode may show a slightly longer recap after the workout, still not a messenger.

### Voice behavior

UI does not speak. Does not require the user to type. If they type in MVP, it is optional accessibility equivalent of an utterance into the **same** pipeline (same events). Typing is not the product path.

### Structured event

None unique. Optional typed utterance enters the same extractor.

### Database change

Turns TTL. No extra tables required.

### UI state

Caption overlay on HUD. High contrast, large type. User not required to open a chat thread. Minimize by default after a few seconds.

### Error / recovery

STT wrong: user can correct by voice (Feature 7), not by becoming a chat editor. Optional tap-to-edit last set numbers on HUD is editing the set (manual source), not chat.

### Tests

HUD not a `FlatList` of 200 bubbles in Gym Mode. Typed “got 10” if implemented produces same `SET_COMPLETED` as voice. Silence policy still applies (no AI bubble spam).

---
