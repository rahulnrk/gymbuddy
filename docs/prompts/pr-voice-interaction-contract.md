# PR — Voice interaction: the conversation window and the coach's clock

## Why this PR exists

The experience is settled: phone in a pocket, music playing, *"Hey Coach"* →
music ducks → coach listens → coach answers → a window stays open → music
returns. The coach also speaks on the workout clock, adjusting rests and asking
whether you are moving on.

**Write the rules down before anyone builds it.** Every problem caught in this
project today was caught because the rule existed and the output could be
checked against it. Voice is the part where getting it wrong is most obvious to
a user and hardest to walk back.

This PR is a contract plus the state machine that enforces it. **No vendors, no
audio code.**

## The tension this resolves

`AGENTS.md` forbidden item 11 is **"requiring command language"**, and set
logging must stay natural — *"that was fifteen at eighty"*, never
*"Hey Coach, that was fifteen at eighty."*

But an always-open cloud stream costs roughly six times more and drains the
battery.

**Resolution: the coach opens most windows.** After a set, after a rest expires,
after a check-in, the app knows a reply is coming. No wake word needed. The wake
word exists only for the user interrupting unprompted.

| Path | Trigger | Wake word |
|---|---|---|
| **Coach-initiated** | The coach just said something inviting a reply | No |
| **User-initiated** | User speaks first | Yes — wake word, earbud tap, or on-screen mic |

---

## Commit 1 — `docs: contract 35, voice interaction`

New `contracts/35-voice-interaction.md`. It must state all of the following as
law.

### 1. Window length is adaptive, not a fixed 40 seconds

| What the coach just said | Window |
|---|---|
| A statement inviting no reply ("Nice.") | **Do not open one** |
| A closed answer ("Yes, stay at eighty.") | 5–8 s |
| A direct question with a short answer ("How many?") | 8–10 s |
| An open question ("How did that feel?") | up to 40 s |
| Nothing — the user interrupted | up to 40 s |

**Extension rule:** if speech is detected before the window closes, extend it
while they are still talking, to a hard cap. A user saying *"actually…"* must not
be cut off.

Why this is not just a cost decision: **the music is ducked for the whole
window.** Forty seconds of quiet music after *"yes, stay at eighty"* happens
fifteen times a session and feels broken. The window closes, the music returns.

### 2. The silence rule — the most important rule in this contract

The coach's clock is only as good as the last thing the user told it. With the
phone in a pocket, a three-minute silence could mean resting, mid-set, queuing
for a machine, or gone to the toilet.

```
The coach may speak proactively ONLY when
  the workout state permits speech (contract 09: never during SET_ACTIVE
  except safety >= high)
AND
  time since the last anchoring event is within the confidence horizon.
```

Past the horizon the coach is **silent** and waits for the user. It does not
guess.

**Rationale to record: a coach that misses a prompt is a minor annoyance. A
coach that talks while you are under a loaded bar is a safety failure and a
product people uninstall.**

### 3. The re-sync is the only utterance allowed at the horizon

> *"Ready when you are?"*

Allowed **once** as the horizon approaches. If answered, the clock re-anchors and
normal proactive behaviour resumes. If unanswered, the coach goes silent until
the user speaks first. It must not ask twice.

Every user utterance re-anchors the clock. So does any logged event.

### 4. Music
Contract 27 already owns audio focus. This contract adds: **ducked exactly as
long as a window is open, and not one moment longer.** Un-duck on window close,
including on timeout and on cancellation.

### 5. Three ways in, all producing a user-initiated window
Wake word, earbud button/tap, on-screen mic. **The wake word will fail in a loud
gym**, and a coach you cannot reach is worse than no coach. The earbud tap is the
most reliable and the cheapest.

Note in the contract that `voice-workout-os-source/` already has an **addressee
gate** — deciding whether speech was aimed at the coach without any wake word.
It is the better product answer and should be benched against a wake word.

---

## Commit 2 — `feat(domain): the window state machine`

Package: extend `com.gymbuddy.domain.voice`. `VoiceTurnMachine` already exists;
this is a policy layer over it, not a replacement. `VoiceTurnState` stays
orthogonal to `WorkoutSessionState`.

```kotlin
enum class UtteranceKind { STATEMENT, CLOSED_ANSWER, SHORT_QUESTION, OPEN_QUESTION }

sealed interface Window {
  data class CoachInitiated(val after: UtteranceKind, val maxMs: Long) : Window
  data class UserInitiated(val via: EntryPoint, val maxMs: Long) : Window
  object None : Window
}

enum class EntryPoint { WAKE_WORD, EARBUD, ON_SCREEN }

fun windowFor(utterance: UtteranceKind): Window
fun onSpeechDetected(window: Window, elapsedMs: Long): Window   // extension
fun shouldDuck(window: Window): Boolean
```

All durations are constants with rows in `DECISIONS_AND_THRESHOLDS.md`, classed
`PRODUCT DECISION` except the barge-in cap, which stays a
`SAFETY REQUIREMENT`.

## Commit 3 — `feat(domain): the coach clock`

```kotlin
data class CoachClock(
  val workoutElapsedMs: Long,
  val lastAnchorMs: Long,          // last user utterance or logged event
  val anchorKind: AnchorKind
)

fun maySpeakProactively(clock: CoachClock, session: WorkoutSessionState): Boolean
fun proactiveUtterance(clock: CoachClock, plan: PlannedSession): CoachAct?
```

`proactiveUtterance` returns at most: a rest-complete prompt, the single re-sync
question, or null. **Null is the common case and must be comfortable** — this is
the same discipline as `ShouldSpeak`, which already exists and already prefers
silence.

## Commit 4 — `feat(domain): predictive rest`

Within-session, not between sessions. `adjustForToday` handles the latter;
nothing handles the former.

```kotlin
fun restAdjustment(setsThisExercise: List<SetPerformance>, target: IntRange2): RestAdjustment
```

> Set 1: 80×8. Set 2: 80×8. Set 3: 80×6.
> *"Take another thirty seconds — your last set dropped to six, so let's recover
> properly before we go again."*

Everything needed exists: the event log holds every set, `Deltas` computes the
change, `Recommend` reads history. Extend rest on decay; never shorten it below
the goal's published minimum. Every adjustment carries a spoken reason, per the
coach-voice rules.

## Commit 5 — `docs: thresholds and the checker`
Rows for every new constant. `scripts/check_living_spec.py` covers contract 35.

---

## Tests

1. A `STATEMENT` opens no window at all.
2. `CLOSED_ANSWER` opens a short window; `OPEN_QUESTION` a long one.
3. Speech before close extends the window; extension respects the hard cap.
4. Ducking is true exactly while a window is open — assert un-duck on timeout,
   on close, and on cancellation.
5. `maySpeakProactively` is false during `SET_ACTIVE` for anything below
   safety-high.
6. Past the confidence horizon, `proactiveUtterance` returns the re-sync **once**
   and null thereafter.
7. Any user utterance re-anchors the clock and restores proactive behaviour.
8. All three entry points produce an equivalent `UserInitiated` window.
9. Barge-in still cancels within the existing budget — the window layer must not
   have added latency.
10. Predictive rest extends on a declining set and never shortens below the
    goal minimum.
11. Every proactive utterance and rest adjustment carries a plain-language
    reason (no field names, no bare numbers without units).
12. `VoiceTurnState` and `WorkoutSessionState` remain independent.

## Definition of done

- `./gradlew :domain:test :persist:test` green; `check_living_spec.py` exits 0.
- No vendor SDK, no audio code, no `:app` change. Policy and state only.
- Still 77 exercises.

## Report back

1. The window duration table you settled on, and why.
2. The confidence horizon you chose, and what happens at 1×, 2× and 5× it.
3. **A transcript of one full session as the user would hear it** — including a
   stretch where they go silent for four minutes and the coach correctly says
   nothing.
4. Anything in the flow that could not be expressed without vendor behaviour.
5. Anything in this prompt that turned out to be wrong.
