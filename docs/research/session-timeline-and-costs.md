# The 60-minute session, event by event, with the API cost that falls out

Written 2026-09-01 from the vision doc. Uses the real printed Day A
(beginner hypertrophy, full gym) rather than a hypothetical session.

---

## 1. The three-brain split is already product law

The vision proposes Workout Engine (deterministic) / AI Coach (LLM) / Voice
Engine. `AGENTS.md` already says exactly this:

> Deterministic workout state machine owns workout truth.
> LLM is interpreter + speaker, not source of truth.
> VoiceTurnState ⊥ WorkoutSessionState.

`WorkoutEngine`, `loop/`, and `voice/` are those three brains, already built and
already separated. **"The LLM is not the clock" is not a new decision — it is
enforced today**, and PR 1's forbidden list rejects any change that lets the
model write workout state.

Music ducking is contract 27, `implemented`, with four goldens.

Nothing in the vision's architecture section needs building. It needs
*recognising* — the hard part is done.

---

## 2. The one real conflict: "Hey Coach" vs no command language

`AGENTS.md` forbidden item 11 is **"requiring command language"**, and Rahul was
explicit: logging a set must be *"that was fifteen at eighty"*, not
*"Hey Coach, that was fifteen at eighty."*

A wake word before every set log breaks that. But always-streaming costs 6× more.

### The resolution — and it is already in the vision's own flow

> Coach: "How many?"
> User: "8."

**The coach opens most windows.** After a set ends, after a rest timer expires,
after a check-in question — the app knows a reply is coming and opens a short
listening window with no wake word required.

So there are two paths:

| Path | Trigger | Wake word? | Window |
|---|---|---|---|
| **Coach-initiated** | Set ends, rest ends, check-in | **No** | 8–10 s |
| **User-initiated** | User interrupts unprompted | **Yes** ("Hey Coach") or earbud tap | up to 40 s |

That keeps natural speech for the 90% case (logging sets), keeps a wake word only
for genuine interruptions, and keeps streamed audio to a few minutes per session.

VOS already has an **addressee gate** — deciding whether speech was aimed at the
coach — which is the more elegant version of the same idea and needs no wake
word at all. Worth benching both: the gate is better product, the wake word is
more reliable in gym noise.

---

## 3. Genuinely new, and worth specifying

### 3.1 Adaptive conversation window
Not a fixed 40 seconds. The window length depends on what the coach just said:

| Coach just said | Window |
|---|---|
| A closed answer ("Yes, stay at 80.") | 5–8 s |
| "How many?" | 8–10 s |
| An open question ("How did that feel?") | up to 40 s |
| Nothing — user interrupted | up to 40 s, extended while they keep talking |

This is a product improvement *and* the single biggest STT saving. It belongs in
`DECISIONS_AND_THRESHOLDS.md` as a table, not a constant.

### 3.2 Predictive rest — genuinely not built
The rules layer adapts **between** sessions (`adjustForToday`). Nothing adapts
**within** one.

> Set 1: 80 × 8. Set 2: 80 × 8. Set 3: 80 × 6, rest was only 75 s.
> "Take another thirty seconds. Your last set dropped to six, so let's recover
> properly before we go again."

Everything needed already exists — the event log has every set, `Deltas` computes
performance change, `Recommend` reads history. What is missing is a rule that
turns *within-session decay* into a rest adjustment. Small PR, high perceived
intelligence.

### 3.3 Three ways in
Wake word, earbud tap, on-screen mic. Redundancy matters because **the wake word
will fail in a loud gym**, and a coach you cannot reach is worse than no coach.
The earbud tap is the most reliable of the three and the cheapest to implement.

---

## 4. The 60-minute timeline

Real Day A: 12-minute warm-up, 6 exercises, 2 sets each, rests of 210/150/120/
60/60/60 seconds.

| Phase | Coach utterances | User speech | Streamed listening |
|---|---|---|---|
| Greeting + plan | 2 | 1 reply (~3 s) | 10 s |
| Warm-up (7 items + 2 ramps) | 9 | ~3 short replies | 30 s |
| 12 working sets — "How many?" + ack | 24 | 12 × ~2 s | 12 × 8 s = 96 s |
| Rest cues ("30 seconds", "10 seconds") | 12 | — | — |
| Exercise transitions | 6 | ~2 replies | 16 s |
| User questions | 5 responses | 5 × ~5 s | 5 × 25 s = 125 s |
| Corrections ("no, eight") | 2 | 2 × 2 s | included above |
| Closing summary | 2 | 1 reply | 10 s |
| **Total** | **~60 utterances** | **~85 s of actual speech** | **~4.5 min streamed** |

Two things to notice:

- **Actual speech is ~85 seconds in an hour.** Streamed audio is ~4.5 minutes,
  because a window stays open a little after the words stop. The gap between
  those two numbers is what the adaptive window shrinks.
- **~60 coach utterances**, of which roughly 60% are fixed strings — "How many?",
  "Next set", "Thirty seconds", "Ten seconds", "Nice", "Rest". Those are cached
  once and never synthesised again.

### Per user per month (12 sessions)

| Workload | Volume |
|---|---|
| **STT streamed** | 4.5 min × 12 = **54 minutes (0.9 hours)** |
| **TTS total** | ~3,300 chars × 12 = 40,000 chars |
| **TTS dynamic** (after caching fixed lines) | ~**16,000 characters** |
| **LLM extraction** | ~15 calls/session × 12 = **180 calls** |
| **LLM open questions** | ~10 calls |
| **Web search** | ~5 queries |

**This revises the earlier brief downward.** The previous estimate assumed
always-on gating at ~10 min/session. Windowed listening is ~4.5. Quote vendors
against the numbers below, not the earlier ones.

### Ask vendors for these

| | 100 users | 1,000 | 10,000 | 100,000 |
|---|---|---|---|---|
| **STT hours/month** | 90 | 900 | **9,000** | 90,000 |
| stress case (windows 2× longer) | 180 | 1,800 | 18,000 | 180,000 |
| **TTS chars/month (dynamic)** | 1.6M | 16M | 160M | 1.6B |
| **LLM calls/month** | 19,000 | 190,000 | 1.9M | 19M |
| Web searches/month | 500 | 5,000 | 50,000 | 500,000 |

Ask for a quote at **9,000 hours** and a **18,000-hour stress case** at
10,000 users. That is the number that decides whether the unit economics work.

### What it costs at 10,000 users, per user per month

| Line | At list price |
|---|---|
| STT at Sarvam ₹30/h | 0.9 h → **₹27** |
| STT at Deepgram $0.462/h | 0.9 h → ₹37 |
| TTS at Google WaveNet $4/1M | 16K chars → **₹0.6** |
| LLM extraction, small model | 190 calls → **~₹1** |
| Web search at Serper | 5 → negligible |
| **Total** | **≈ ₹30 per user per month** |

Down from the ₹90–130 in the earlier brief, and the difference is entirely the
windowed-listening decision.

---

## 5. What this changes about the build order

Nothing already built is wasted. What it adds:

1. **The conversation window state machine** — coach-initiated vs user-initiated,
   adaptive length. This is `VoiceTurnMachine` territory and it already exists;
   it needs the window policy on top.
2. **Wake word or addressee gate** — bench both. VOS has the gate.
3. **Predictive rest** — small, high impact, all the inputs exist.
4. **Earbud tap as a second way in** — platform work, high reliability payoff.

None of it changes the coach brain, the catalog, or the planning engine.
