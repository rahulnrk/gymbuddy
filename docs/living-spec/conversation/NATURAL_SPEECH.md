# Natural conversation is first-class

Structured events are an **internal reliability boundary**, not a restriction on how the human talks to the AI.

The user operates the workout, not the app. They must never be trained to say “Log set”, “Enter reps”, “Set weight”, or “Next exercise” unless they naturally used those words.

## Two different concepts

### 1. Human ↔ AI conversation (unrestricted)

The user may speak arbitrary, natural, human-like, multilingual/code-mixed speech:

- “Bro this is heavy.”
- “Why am I feeling this more in my shoulder?”
- “What did I do last week?”
- “I don't feel like doing this exercise today.”
- “Can we do something different?”
- “Actually I think that was 9.”
- “Give me a second, I'm tired.”
- “Naku chest kanna shoulder lo ekkuva feel avutundi.”
- “How much protein should I have?”
- “Can we do one more?”
- “What should I do instead of this machine?”
- “Kaadu, 8.”

The AI may understand and reply naturally (“Yeah, let's drop it a little.”).

The conversational system may understand arbitrary natural speech, but the application owns the decision of whether speech is useful, safe, and timely. Silence is an intentional product outcome, not a system failure.

Casual talk, questions, coaching, corrections, logging, naturally expressed commands, uncertainty, feedback, preferences, code-mix, and safety concerns are all in scope.

### 2. Conversation → authoritative workout mutation (controlled)

Natural speech must never execute arbitrary database mutations.

```
Human speech
  → STT
  → conversational understanding
  → CandidateEvent
  → safety + CandidateValidator
  → ValidatedFact
  → WorkoutEngine
  → WorkoutEvent
  → EventLog (Room)
  → WorkoutProjector
  → WorkoutSessionSnapshot
```

Only **committed `WorkoutEvent`** rows are persisted. `ValidatedFact` is never a Room source of truth.

A utterance may produce **zero** workout events (pure conversation) and still be a successful turn.

## Persistence must not impose a command vocabulary

Room, EventLog, and the projector must not assume the user spoke a fixed grammar. Future voice/LLM work maps free-form speech onto this pipeline; it does not replace conversation with commands.

PR 7 maps free-form speech to `CandidateEventEnvelope`. That envelope is an internal interpretation, not a command language. Extraction does not authorize workout mutation.
