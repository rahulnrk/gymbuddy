# Core loop

The product kernel. Everything else feeds, displays, or improves this.

**Conversation is unrestricted. Authoritative mutation is controlled.** See `conversation/NATURAL_SPEECH.md`. The user does not operate the app with command phrases. Structured events exist so natural speech can coexist with precise workout state.

```
music playing
  → user talks naturally
  → AI understands context (four contexts)
  → structured workout event
  → AI responds naturally (or stays silent)
  → history is updated
  → future coaching becomes better
```

Pipeline (every Gym Mode utterance):

```
raw transcript
  → assembleFourContexts (read-only structured snapshot)
  → safety classification (before extract; LLM cannot override HIGH/CRITICAL)
  → NaturalLanguageExtractor (rules-first, schema-constrained fake)
  → CandidateEventEnvelope
  → CandidateValidator
  → WorkoutEngine / EventLog
  → projector
  → FourContextAssembler
  → recommend() (talk-only)
  → shouldSpeak (application-owned; silence is intentional)
  → optional TTS (never in SET_ACTIVE except safety ≥ high)
  → persist COACHING_RESPONSE (silence still a row)
```

The conversational system may understand arbitrary natural speech, but the application owns the decision of whether speech is useful, safe, and timely. Silence is an intentional product outcome, not a system failure.

`assembleFourContexts` does not parse the transcript. It supplies Workout / Historical / Conversational / User context so later conversation can stay natural without inventing history.

Invariants:

1. User is not required to tap to log a set.
2. No structured event commits without validation against session state.
3. No coaching sentence may cite history that is not in the historical snapshot.
4. If coaching has nothing useful to say, stay silent. Persist silence.
5. Barge-in always wins over TTS.
6. A dropped network cannot drop un-synced local events.
7. Safety can veto speech and block progression suggestions.
8. Offline voice logging only if `SttProvider.supportsOffline`. Session truth never drops.

Full sequence diagram: `TECHNICAL_SPEC.md` § Product kernel.
