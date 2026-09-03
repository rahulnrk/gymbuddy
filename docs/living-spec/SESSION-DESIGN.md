# Session design — the coach on a clock

**Status:** agreed with Rahul, 2026-09-03. Supersedes the wake-word-first
model in contract 35.

**Partly built.** This document was written from a conversation, not from the
code, and the first version claimed nothing was built. That was wrong.
`SessionPrediction`, `nextScheduledWindow`, `openEmergency()`, `TopicScope`
and a quiet-hold constant already exist in `:domain`. §14 lists every place
the tree and this document currently disagree. **The documents are ahead of
the code, which is the most dangerous state this project has been in — a
contract that reads as settled while the running behaviour is the old
design.** Close §14 before building anything new.

This is the design of record for what happens between "user starts a session"
and "user finishes". Contracts get updated from this document, not the other
way round.

---

## 1. What changed and why

The old model: the user says "hey coach", the phone hears it, a window opens.

That model is dead for v1, for a reason that is not about code. The user's
phone is in a pocket or a locker, so the only usable microphone is the one in
their earbuds. On ordinary Bluetooth, opening the earbud mic drops the music to
call quality. So the mic **cannot** stay open across a workout — not because of
cost, but because the music would sound broken the whole time.

Which means the mic can only be open in short bursts. Which means something
other than the user has to decide when those bursts happen. Which means the
coach opens the windows.

Everything below follows from that one constraint.

**Consequences to hold onto:**

- No wake word in v1. `WakeWordProvider`, `ListenDecision` and the listen gate
  stay in the tree, dormant, with this reason recorded. They return if LE Audio
  earbuds become common.
- The always-on cloud listening that passed the 2026-09-02 floor test is a test
  harness, not the product. It costs roughly 19× what this design costs.
- The user cannot start a conversation by speaking. The emergency control
  (§7) is the only user-initiated entry.

---

## 2. The session loop

One cycle:

1. The clock decides a set has probably ended and it is time to speak.
2. Music ducks. It never pauses.
3. The coach begins speaking. **The mic opens at the same moment**, so the user
   can cut in mid-sentence.
4. The user says whatever they say. Not fixed phrases — anything about the
   training or around it.
5. The coach logs what was said, answers what was asked, adjusts the plan if
   needed.
6. The exchange ends — the user stopped, or the coach finished answering,
   whichever came last.
7. A short quiet hold passes. Music returns to full. Mic closes.
8. Nothing listens and nothing costs money until the next check-in.

The ducked period and the listening period are the same interval. If the
conversation runs long, the duck runs long with it.

---

## 3. Window rules

**Opening.** The clock aims at the gap after a set, never mid-set. A window
landing mid-set is the failure this whole design exists to avoid.

**Holding.** The window extends while either party is still talking. There is
no hard cap that cuts a live conversation — the 60 s cap that fired during the
floor test was a defect, not a policy.

**Closing.** A quiet hold after the exchange ends. "Quiet" means *the user
stopped speaking*, not that the room went silent. A gym is never silent.

**Numbers.** This document sets none of them. The code has values that were
never argued for — `WINDOW_MIN_GAP_MS`, `WINDOW_MAX_SILENCE_MS`,
`WINDOW_STATEMENT_MS`, and `DUCK_HOLD_MS`, which is now the single number
closing both the duck and the mic. Every one is an `UNDECIDED` row in
`DECISIONS_AND_THRESHOLDS.md`. Having one number is not the same as having
argued for it.

**The reply budget is measured from when the coach stops speaking**, not from
when the window opens. Otherwise a longer line silently shortens the user's
time to answer — and once coach lines are drawn at random from a catalogue of
variants, the same question would carry a different deadline each time.

---

## 4. The clock

Tracks where the user *should* be: which exercise, which set, lifting or
resting, how long the current phase has run. It is a **prediction**, not a
fact, and the type should say so. Facts arrive only when the user speaks.

**Scheduling learns, simply.** First sessions have no history, so the rest
prescriptions already in the plan are used. After that, use the **median** of
the user's recent observed rests — median, not mean, so one interrupted rest
does not move it.

That reasoning only holds with enough samples: at two observations a median is
just their mean. So the guard counts **rest observations, not completed sets**,
and needs at least three. A three-set exercise yields two rests, so it does not
earn its own estimate — its rests still feed the per-user pool, which is what
gets used until the exercise has three of its own. Per exercise where there is
enough data, per user otherwise, the plan when neither qualifies.

No model, no weights, nothing that cannot be explained to a user who asks why
the coach spoke when it did.

---

## 5. How the coach speaks

Two kinds of speech, and the split is what keeps it both natural and cheap.

**Coach-initiated lines are pre-recorded.** Check-ins, rest calls, reminders,
transitions, encouragement. The user is not responding to these, so they do not
need to be responsive — only varied. Roughly twenty jobs, a dozen written ways
to say each, chosen at random, **never repeated within one session**. Recorded
once, free thereafter.

**Replies to the user come from the model, always.** Answering a person with a
stock line is precisely what makes something feel like an IVR, and it is the
one place where being wrong is obvious. "That felt heavy" always gets a real
answer.

**Lines containing the user's own numbers are generated fresh.** "Eight at
sixty — that's up" lands; "set logged" does not. These are short and cost
almost nothing. **Never stitch recorded fragments to speak a number** — that is
the one thing that always sounds robotic.

**Do not re-explain what has already been explained.** The familiarity levels
built for warm-ups apply to the whole session.

---

## 6. What the coach knows and will discuss

In scope: training, technique, programming, nutrition, calories, protein,
muscles and anatomy, injuries, recovery, equipment.

Out of scope: everything else, answered with one polite line to the effect of
*"I'm your gym coach — I can help with training, food and injuries."* The exact
wording belongs in the contract; it will be said often.

Classification needs the model. Do not attempt keyword matching — it will
misfire on "my shoulder is killing me".

---

## 7. Pain and the emergency control

An on-screen control opens a window immediately, out of schedule, and routes
into the existing pain triage (contract 37). It must work whatever the clock
believes is happening, mid-set included.

This is the only user-initiated entry in v1. It means reaching for the phone —
acceptable, because someone in real pain is stopping anyway.

---

## 8. Insights

Occasionally the coach says something true and specific from the user's own
data: lifted more than last time, covered every head of the chest, week went to
plan, good rest taken.

Four rules, all load-bearing:

1. **Computed, never invented.** The insight comes from logged numbers and the
   catalog's muscle mappings. The model may phrase it; the model may never
   produce the fact. One invented encouraging number and the whole feature
   stops being trustworthy — and users check.
2. **Rare.** One or two a session. Praise on every set is noise.
3. **Suppressed when the day is going badly.** No streak celebration right
   after a pain report or on a session where loads are dropping.
4. **A rest day is a win, not a broken streak.** Streak mechanics are the
   easiest way to push someone into training hurt. The coach congratulates the
   rest day; it does not forgive it.

---

## 9. Plans

The coach builds a week from intake, **or** the user brings their own plan and
is coached through theirs. Both paths reach the same session loop. Bring-your-
own does not skip screening — the safety checks in contract 30 still apply.

---

## 10. Cost, as a design constraint

Approximate, at the shape above: coach speech is the largest line, listening
second, the model third. Roughly ₹135 per user per month.

Three rules keep it there, and each is also a quality rule:

- Cache what the coach initiates. Free, and variety comes from having many
  variants rather than generating each time.
- Wake the model only when the user actually says something. Most check-ins
  are a question and a short answer that the rules already handle.
- Keep lines short. A real coach between sets is brief. Brevity is the
  cheapest item here and the most natural.

If a change makes the coach talk more, it costs more. That is the lever.

---

## 11. What this contradicts

To be reconciled when contracts are updated:

- **Contract 35** describes user-initiated windows via wake word, earbud and
  on-screen as first-class. In v1 only the emergency control is
  user-initiated.
- **Contract 37** (pain triage) assumes the user can raise pain by speaking.
  Its entry point becomes the emergency control.
- **`Listen.kt` / the listen gate** is not in the path. It becomes dormant,
  not deleted.
- Existing contract tests assert **exact spoken lines**. Model-generated
  replies cannot be tested that way. Those tests move to asserting what was
  **logged** and **decided**, not what was said. Cached coach-initiated lines
  can still be asserted.

---

## 12. Build order

0. **Close the gap in §14.** The code still runs the wake-word design the
   contracts now call dormant. Nothing below is safe until it does not.
1. ~~Contract 35 rewritten from this document. Contract 37 entry updated.~~ Done.
2. Session clock and window scheduler — **started**: `SessionPrediction` and
   `nextScheduledWindow` exist. Not finished, not wired.
3. Emergency control — **half done**: `openEmergency()` exists in domain,
   there is no on-screen control in `:app`.
4. The cached-line catalogue — jobs, variants, no-repeat-in-session.
5. Onboarding and profile screens. **Intake has been built for weeks with no
   way to fill it in**; nothing else here is usable until this exists.
6. The model: replies, topic scope, phrasing of computed insights.
7. Insights computation (domain, deterministic).
8. Bring-your-own-plan.

---

## 14. Where the tree disagrees with this document

Found by the agent on 2026-09-03, after the contracts were rewritten. Docs now
describe v1; code still runs v0. Every line here is a contradiction a reader
of the contracts would not detect.

**Closed (code now matches v1 contracts):**

1. ~~Listen gate still runs.~~ `WAKE_WORD_PATH_LIVE = false`. `listenDecision`
   returns `VadOnly` on speech with no window. `applyListenDecision` will not
   start the wake-word model.
2. ~~60 s cap cuts a live conversation.~~ `onSpeechDetected` no longer caps.
   `onTimeout(..., userSpeaking = true)` does not close. The leftover
   `WINDOW_HARD_CAP_MS` only closes a window open 60 s **with nobody talking**.
3. ~~`windowFor(WAKE_WORD)` builds a window.~~ Dormant entries return
   `Window.None` while `WAKE_WORD_PATH_LIVE` is false.
5. ~~Quiet hold 3000 vs 2500.~~ `:app` reads `VoiceInteractionConstants.DUCK_HOLD_MS`
   (3000). The number stays `UNDECIDED` — one place is not a decision.
6. ~~Unargued min/max gaps treated as fact.~~ Rows remain `UNDECIDED`. Values
   in code are leftovers, not promoted.
7. ~~Three-set exercise never learns.~~ Two rests after three sets enter the
   **user pool**. Per-exercise median waits until there are enough rest
   observations (not completed-set count) so the median is actually a median.
8. ~~Contract 09 vs emergency.~~ Written into 09: not an exception. Emergency
   is user-initiated; they have already stopped.
12. ~~Window close at 5 s, duck at 3 s.~~ Both end on `DUCK_HOLD_MS`.
   `WINDOW_SPEECH_EXTENSION_MS` is gone. Hold stays `UNDECIDED`.
13. ~~Per-exercise median of one or two rests.~~ Guard is rest-observation
   count. Below that, per-user pool, then the plan.

**Open by design (harness / later steps):**

4. `:app` First Sound still arms Scribe on screen open — always-on listening.
   Labeled a harness in `FirstSoundScreen` and contract 35. Behaviour left
   alone. Still matches "hey coach" on the transcript; that is the harness,
   not `windowFor(WAKE_WORD)`.
9. Replies still come from `DeltaPhrase` / `CoachingPhraser` templates, not
   the model. Test bucket (a).
10. Cached coach-line catalogue does not exist.
11. No emergency control in `:app`.

**Closed in the statement / reply-budget step:**

14. ~~`windowFor(STATEMENT)` returned `Window.None`.~~ Opens `WINDOW_STATEMENT_MS` (UNDECIDED, 5 s guess, shortest of the set).
15. ~~Reply budget ran from window open, so a long coach line ate the user's time.~~ `onCoachFinished` starts `maxMs`. `onSpeechDetected` extends that same clock, not elapsed-from-open. Mic still opens at coach start; barge-in unchanged.

---

## 13. Still open

- No physio has reviewed the injury data. `docs/review/PHYSIO-REVIEW.md` is
  ready and unused.
- The phone-away promise has not been tested in a genuinely loud gym.
- Telugu/English code-switching accuracy is unmeasured.
