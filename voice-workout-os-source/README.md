# Voice Workout OS — working prototype

One self-contained HTML file. No build, no server, no install. Open it in a browser and train.

---

## Run it

1. Put `voice-workout-os.html` on your phone (AirDrop, Drive, email to yourself, or serve the folder over your LAN).
2. Open it in **Chrome** — Android or desktop. Allow the microphone.
3. Either run `ANTHROPIC_API_KEY=... node server/proxy.js` and put its URL in Settings → Backend (no key on the device), or paste a key directly for a quick throwaway run.
4. Tap **Start workout**, tap the mic, pocket the phone.

```
python3 -m http.server 8080
# then http://<laptop-ip>:8080/voice-workout-os.html on the phone
```

> **iOS Safari**: `SpeechRecognition` is unreliable to absent. The app detects this and everything still works through the ⌨ button. Real iOS needs a native build.

---

## Architecture

Four layers, cleanly separated. The engine is the source of truth and the model never writes to it.

```
        mic (never closed)
              |
        energy VAD  ──────── two-stage barge-in
              |
    Web Speech, n-best + confidence
              |
        addressee gate ....... open | window | wake | hybrid
              |
        dialogue state ....... what we asked, what we expect
              |
     ┌────────┴────────┐
 deterministic     semantic layer
   resolver          (proposes)
     └────────┬────────┘
              |
      action validator ....... phase, ranges, contradictions
              |
      serialised queue ....... ids, versions, idempotency
              |
      WORKOUT ENGINE ......... source of truth
              |
     response generator ...... pre-written, state-versioned
              |
       clause-chunked TTS
              |
        interruptible
```

| Layer | File | Job |
|---|---|---|
| Audio | `70-audio.js` | continuous listening, VAD, barge-in, echo suppression, TTS providers |
| Addressee | `72-gate.js` | four switchable strategies, every decision logged |
| Numbers | `32-numbers.js` | contextual numeric resolution, n-best rescoring |
| Dialogue | `34-dialogue.js` | expectation stack, turn history, pending questions |
| Resolver | `36-resolve.js` | patterns → context → semantic, with confidence tiers |
| Validator | `52-actions.js` | phase legality, ranges, idempotency, serialised queue |
| Safety | `55-safety.js` | pain as a state, reason-aware substitution |
| Engine | `20-engine.js` | the only source of truth |
| Speech | `45-speech.js` | pre-generated lines, state-versioned |

---

## P0, item by item

**1–3 · Continuous listening and barge-in.** The recogniser is never torn down, including while the agent talks. Interruption is two-stage: an energy onset on the mic yields the floor in under a millisecond of our own processing, and a transcript that isn't our own echo hard-stops the line. If nothing intelligible follows, the line **resumes** — a clang of plates does not silence the agent. Lines are spoken clause by clause, so a stop lands inside one clause instead of at the end of a paragraph. Echo is filtered by token overlap against what we are currently saying: our own voice scores ≥ 0.9, a genuine reply < 0.3. 17 assertions on this alone.

**4 · Addressee detection.** Four strategies, switchable in Settings between sessions. Every decision is logged with its reason, and `tools/gate-compare.js` replays a labelled session through all four and prints false accepts, false rejects and a weighted cost. On a synthetic set: hybrid 92.9%, window 85.7%, open 71.4%, wake 57.1%. Run it on real gym audio and the argument is settled with data.

**5 · Gym noise.** What software can do here is done: n-best rescoring against context, plausibility priors, an adaptive noise floor in the VAD, and short-utterance discipline. What software cannot do here is replace the browser recogniser — that needs a production streaming ASR behind the proxy, and it is the single biggest remaining risk.

**6–7 · Numbers.** "Eight" resolves against what the system is actually waiting for: reps here, RPE there, kilos when it just asked for a load. Homophones are scored, not guessed — `ate`, `hate`, `wait`, `weight` all read as 8, but **only in a short utterance**. In a sentence, "hate" is the word hate: "I hate Bulgarian split squats" used to log eight reps, and now goes to the semantic layer where it belongs. Plausibility priors know a load is usually a multiple of 2.5 and a rep count is usually near the target.

**8 · Contextual intent, no new regex.** Patterns handle only genuinely fixed commands. Anything longer than four words with no unambiguous pattern goes to the semantic layer, which returns **intent proposals** — never actions. Those proposals pass through the same validator and the same confidence tiers as anything else. Self-corrections resolve before execution: "set it to 70, actually no, make it 65" applies 65, and a bare trailing number inherits the intent it is correcting. "No, I did 9" is a correction of the last logged set, not a new one. Multi-intent works: "skip this and give me two minutes" is two steps, executed transactionally.

**9 · Ambiguity and confidence.** Three tiers by risk. Read-only questions execute on weak evidence. Logging a set executes above 0.72, confirms above 0.4, clarifies below. Anything destructive — weight, skip, substitute, end — needs 0.85 to execute and confirms down to 0.45. Ambiguous numbers ask: "eight reps or RPE eight?" and the answer is matched against the options offered, not re-guessed.

**10 · Races, duplicates, staleness.** Every action carries an utterance id and the state version it was decided against. One action executes at a time. Three simultaneous sets land in order, exactly once each. The same utterance twice logs one set. An action decided against an old world is re-validated and dropped if it no longer fits. Multi-action plans are all-or-nothing: an invalid second step rejects the first.

**11 · Validation.** 500 reps, negative weight, 9999 kg, a nonexistent exercise, an invented action type, extending rest while not resting, correcting with nothing logged — all rejected with a spoken reason. A tripled load or a fourth set on a three-set exercise asks first.

**12 · Pain is a state.** `my shoulder hurts` enters safety mode: the agent stops mid-sentence, **wipes the entire pre-generated cache** (every cached line predates the pain), speaks a line written live, and offers skip / swap / stop. It does not diagnose. Ordinary discomfort — burn, pump, sore, brutal — is explicitly not pain. Substitution then knows *why*: pain steers away from whatever loads that joint and prefers machines and isolation; a busy rack gives different equipment; no equipment restricts to what you have; too hard gives something easier to control; a dislike keeps the training objective. Deadlift is never swapped for a leg curl.

**13 · Stale speech.** Only fully-written lines are context-scoped — everything else carries slots filled from live state at play time, so it cannot go stale by construction. Fully-written intros are stamped with the exercise they name and dropped if it changes. Safety wipes everything.

**14 · Graceful degradation.** If the model is unreachable, the workout continues: numbers, rest, navigation and corrections are all local. Pre-generated speech falls back to templates silently. Tested by killing the API mid-session.

**15 · No key on the client.** `server/proxy.js` is a small backend the client talks to instead of Anthropic. It holds the key, **picks the model** so model IDs stop being a user problem, and caps `max_tokens`. Set the URL in Settings → Backend.

```
ANTHROPIC_API_KEY=sk-ant-... node server/proxy.js
```

**Neural TTS** has an interface and a working remote provider (`POST {text}` → audio). Point it at Cartesia, ElevenLabs or your own endpoint in Settings. Browser `speechSynthesis` remains the default and still sounds like a train station — that is the largest remaining gap between this and something that feels human.

---

## Measured

| | |
|---|---|
| Assertions across 9 suites | 315 |
| Resolve time, local path | ~1 ms |
| Yield-the-floor latency | < 1 ms of our processing |
| Hard-stop latency after confirmation | < 1 ms |
| Speech written by the model | 100% |
| Pre-generation batches per session | ~5, all off the critical path |
| Duplicate utterances logged twice | 0 |
| Races out of order | 0 |

The summary screen now reports, per session: turns, acted-on directly, confirmed, clarified, ignored as not-addressed, actions rejected, duplicates dropped, stale actions dropped, times you cut it off, stop-talking latency, false ducks, own-voice filtered, and the addressee strategy's ignore rate.

---

## Not done, deliberately

**P1** — richer coaching and progression, memory that separates durable facts from today's niggles, device and Bluetooth compatibility, background reliability. All real, none of them P0.

**P2** — music stays duck-only, as decided. No new exercises, templates, UI or analytics.

**The two P0 items a browser cannot finish** — production streaming ASR and streaming neural TTS. Both now have the interface and the backend they need; both need a real endpoint and real gym audio behind them.

---

## Screen design

The app is voice-first, so most mobile-UI advice applies sideways here — the ideal state is not touching the screen at all. Four principles earned their place; the rest were left out on purpose.

**Start is the screen, not the last thing on it.** The home screen used to be six configuration cards with "Start workout" buried under all of them. It is now a **Today** card: the session name, its exercises, its length, three steppers for energy, soreness and time, and a 58px start button — all above the fold on a 390×844 phone. Voice, AI, music and programme setup moved behind one link, because you configure them once and train a hundred times.

**Navigation moved to the thumb.** Body and History were ghost buttons in the top-right corner, the hardest place to reach one-handed, while the mic sat comfortably at the bottom. There is now a bottom nav — Train, Body, History — with 54px targets. During a workout it is replaced by the mic dock, so the two never fight; mid-session the way into the full body map is tapping the mini figures on the training screen.

**A wrong log costs one tap.** Voice `undo` already worked, but you had to say it. After any set is logged an **Undo 9** button appears in the pinned header for twelve seconds and says exactly what it will remove. The set that just landed pulses green so you can see it registered without reading anything.

**The thing you glance at stays on screen.** Exercise name, weight, set number and target reps are a sticky header, so scrolling to read the transcript never hides the one line you actually looked down for.

Smaller changes in the same spirit: the time and energy steppers explain their consequence as you press them ("In 45 minutes I'd cut 3 things and keep the main lifts"), every interactive target is at least 40px and most are 44–54px, async buttons show a spinner instead of only changing a label, and secondary text was lightened until small labels clear 4.5:1 against the background — verified in the test suite rather than eyeballed.

Deliberately **not** done: a full Material 3 restyle, which would have been churn against a dark visual language that already works; step bars in the workout, since the session list is already the progress indicator; and moving the mic, which was already exactly where a thumb lands.

---

## The body map

Twenty-four muscle regions, front and back, male and female. **Redness means "still recovering", not "volume done".** You train, the muscles you hit go red, and they fade back to grey over the next few days as they actually recover.

### The artwork

Every muscle is an **authored outline that follows its real origin, belly and insertion** — not a procedurally generated band. The earlier version built muscles by sweeping a width along a bone axis, which produced symmetric sausages that did not look like anatomy, and was rightly called out for it.

What the outlines now follow:

- **Pectoralis major** fans from a broad vertical origin on the sternum and *converges* to a narrow insertion on the humerus, clavicular and sternal heads separately — which is why the fibres visibly run upward and outward.
- **Deltoid**'s three heads cap the shoulder and meet at the deltoid tuberosity a third of the way down the arm, rather than running the arm's full length.
- **Latissimus** sweeps from the lumbar spine up to a narrow armpit insertion — the shape that gives a back its taper.
- **Trapezius** in three planes: the upper slope to the acromion, the middle, and the lower converging on the thoracic spine.
- **Quadriceps** as vastus lateralis high and outer, rectus femoris down the middle, vastus medialis as the low teardrop above the knee, with sartorius crossing them diagonally.
- **Gastrocnemius** as two heads sitting over the soleus, stopping above the Achilles.
- **Biceps** with brachialis showing lateral to it near the elbow; **triceps** as long head, lateral head and the common tendon.
- **Rectus abdominis** as a narrow column of eight blocks with the obliques as flank bands beside it, and **serratus** as interdigitating slips.
- **Erector spinae** as two lumbar columns, **glute medius** fanning above the max, two hamstring heads, flexor and extensor forearm masses.

Forty-odd shapes across twenty-four muscle ids. Coordinates are authored once against a reference male; the female figure is the same anatomy under a y-dependent horizontal warp — narrower shoulders and ribcage, wider hips.

It is a stylised anatomy chart, not a medical illustration. Every shape is a plain list of points in `src/14-figure.js` under the muscle it belongs to, so any shape you disagree with is a few numbers to move. `node /tmp/figdebug.js`-style colour-coded rendering is how I diagnosed them; the same trick makes it obvious which outline to edit.

### Recovery

Every muscle has its own recovery window, and the window stretches with the dose and the kind of damage:

| | Hours from a full dose |
|---|---|
| Forearms, shins, neck | 30–34 |
| Abs, rear delts, calves, biceps | 36–44 |
| Traps, upper chest, lats | 48–54 |
| Glutes, quads | 54–58 |
| Hamstrings, lower back | 60–62 |

Three sets keeps a muscle down for about 80% of its base window; nine sets stretches it past 110%. Eccentric-heavy work stretches it further still — Nordic curls carry a 1.5× multiplier, Romanian deadlifts 1.35×, back squats 1.2×. Nothing exceeds four days, because nothing sensibly should.

Fatigue decays on a slightly convex curve rather than a straight line, so soreness holds for the first day and then drops away. Separate sessions **saturate rather than add**: two hard hits on the same muscle can reach 100% fatigue but not 200%.

### What you see

Three views, recovery first:

- **Recovery** — the default. Red is sore, grey is ready. Underneath: how recovered each region is with an ETA, a **ready to train** list of everything that's fresh, and a **day-by-day strip** for each sore muscle showing the fade across today, tomorrow, +2 and +3 days.
- **This week** — effective sets and volume, for spotting what you're neglecting.
- **Session** — fills in live. A compact front-and-back pair also sits on the training screen so you watch it happen without leaving the workout.

Heat in the volume view is counted in **effective sets, not kilos**, so pull-ups and lat pulldowns land on the same scale. Each set is distributed across the muscles that movement actually works, weighted by contribution — bench gives mid chest 1.0, front delts 0.6, triceps 0.65 — then scaled by rep range and RPE.

Tap any muscle for its state, fatigue remaining, when it'll be ready, when it was last hit, its typical recovery time, and which movements got it there.

The agent knows all of this too: what's still recovering goes into its memory snapshot, so it can say "your hamstrings are still coming back from Tuesday" without inventing it.

**These numbers are working estimates, not measured truth.** They live in two tables — `RECOVERY_H` and `DAMAGE_BY_EXERCISE` in `src/17-recovery.js` — precisely so they're easy to argue with and change.

---

## Programmes

Ten original multi-week programmes, filterable by what you actually have:

| | Days | Level | Kit | For |
|---|---|---|---|---|
| Groundwork | 3 | beginner | minimal | learning the six patterns |
| Bodyweight Ladder | 4 | beginner | none | a floor and a doorway |
| Two Dumbbells | 3 | beginner | dumbbells | one adjustable pair |
| Back to Front | 3 | beginner | minimal | what a desk switches off |
| Upper / Lower Four | 4 | intermediate | full gym | the sensible default |
| The Thirty | 4 | intermediate | full gym | built backwards from the clock |
| Circuit Work | 3 | intermediate | minimal | density over load |
| Strength Five | 3 | intermediate | barbell | low reps, long rests |
| Five Day Split | 5 | advanced | full gym | one region a day |
| Push Pull Legs Six | 6 | advanced | full gym | genuine six-day training |

Onboarding ranks them against your days, your equipment, your experience and your goal, and shows why each one scored where it did. You can ignore all of it and paste your own.

---

## Onboarding

Five screens. Each one has to earn its place by changing something.

| # | Screen | What it changes |
|---|--------|-----------------|
| 1 | **Where are you starting from?** | how careful the first weeks are |
| 2 | Days a week **and** session length, together | which programmes are even offered |
| 3 | Equipment | filters out what you cannot do |
| 4 | Goal | breaks ties between programmes that fit |
| 5 | Name, full date of birth, figure — all optional, skippable as a block | what the agent calls you, and recovery advice |

The first question replaces two older ones — "how long have you trained" and "how has the last month gone" — because for the purpose of setting a starting load they were always the same question. Its five answers include **"This is my first time"** and **"I've trained before, but not for a long time"** as separate options, which matters:

- both set `askLoads`, so the agent **asks** what you are lifting instead of reading 80 kg off a programme sheet at someone who has never squatted
- both weight readiness above goal in the ranking, so asking for strength does not hand a novice an intermediate barbell programme

Date of birth is a real date picker, kept whole; the age is derived from it.

It ends on the ranked programme list rather than dumping you into a settings page.

---

## Adding your own programme

Type it however you write it. There is no format to learn.

```
Chest
Bench 3 set
Inclined bench 3 set
Cable flyes 3x12 20kg

Legs
Squat 4x5 100kg
RDL 3 sets of 8-10
```

- **Names are matched, not required to be exact.** `Bench` → Bench Press, `Inclined bench` → Incline Bench Press, `cable flyes` → Cable Fly, `RDL` → Romanian Deadlift, `OHP` → Overhead Press, `deads` → Deadlift. 60+ movements, abbreviations, plurals and misspellings.
- **A movement it doesn't know is kept, not rejected.** `Jefferson curl` becomes a custom exercise with sensible defaults.
- **Headings work however you write them** — `Chest`, `Push:`, `# Legs`, `Day 3 - Legs`, or nothing at all.
- **Any notation for sets and reps** — `3 set`, `3x8`, `4 x 6-8`, `3 sets of 8-12`, `5x5 @100kg`, `135lb` (converted). The old `Name | sets | low-high | kg` still works too.
- **Anything you leave out is filled in** — 3 sets by default, a rep range matched to the movement type.
- **A missing weight is not an error.** The agent just asks you on the first set: *"Bench press. 3 sets, 6 to 8. What are you loading?"* — say "sixty" and it carries on.
- **A live preview shows exactly what it understood** as you type, with every assumption labelled, every field editable inline, and an add-exercise picker per day.

## What else is real

**The fast path.** Reps, RPE, rest, skip, ready, undo, weight, "what's next", "I only have 35 minutes" — parsed locally, no network, **under 1 ms** average.

**The engine owns the state.** Progression is explicit rules, not model output:

| Last session | Decision |
|---|---|
| All sets at top of range, RPE ≤ 8.5 | Increase (+2.5 / +5 kg by lift and load) |
| All sets at top, RPE > 8.5 | Hold — earn it easier first |
| Inside range, not at top | Hold, chase the top |
| Below range, first time | Hold |
| Below range, second time | Deload ~10% |
| Bodyweight at top of range | Add reps, not load |
| Energy ≤ 4 at check-in | Cancel all increases, trim accessory volume |

**The model can't corrupt state.** It returns `{say, action}`; every action passes an allow-list with range checks. A 9999 kg `set_weight`, a 500-rep `log_set`, a nonexistent exercise and an invented `delete_all_history` are all silently rejected.

**Quiet by default.** RPE is only requested on the first set, the last set, or when performance deviates from last time.

**Efficiency mode.** "I only have 35 minutes" cuts priority-3 accessories, then priority-2 sets, then accessory rest — mains untouched — until it fits.

**Memory persists.** Programme, history, preferences, notes. Say "I hate Bulgarian split squats" → the model files a note → it's matched against the exercise library → the movement is substituted in future sessions, explained out loud.

---

## Say any of this

```
8 · eight · got 10 · I did twelve · ten reps
done · next · that's the set · racked
skip this · next exercise
ready · let's go · I'm good
another minute · rest two minutes · make it 90 seconds
that was easy / pretty good / hard / all I had
rpe 8 · nine out of ten · two in the tank
using 62.5 kg · going up to 70
I only have 35 minutes
what's next · what weight · how much time left · say that again
undo · pause · end the workout

next song · skip this track · previous track · shuffle the music
pause the music · turn the music up · volume down
you're too loud · speak up
```

Anything else goes to Claude with a compact snapshot of live state plus long-term memory.

---

## Files

| | |
|---|---|
| `voice-workout-os.html` | the whole app, open it and go |
| `src/*` | readable source, one file per layer |
| `src/45-speech.js` | the pre-generation speech engine |
| `src/85-fieldlog.js` | the field-study instrumentation |
| `src/12-muscles.js` | muscle taxonomy and per-exercise contribution |
| `src/14-figure.js` | the generated body figure |
| `src/16-heat.js` | effective-set accounting and the colour ramp |
| `src/17-recovery.js` | per-muscle recovery windows, damage factors, forecasts |
| `src/18-routines.js` | the programme library |
| `src/19-warmup.js` | the warm-up: raise, mobilise, activate, and ramp sets computed from today's weight |
| `src/21-soreness.js` | parsing what people say about their body, and what unexpected soreness usually means |
| `src/22-checkin.js` | the questions asked during the warm-up, and what each answer changes |
| `src/88-screens.js` | onboarding and the body map |
| `src/89-demo.js` | ten weeks of seeded training, and a session that runs itself without a microphone |
| `build.js` | concatenates `src/` into the single file |
| `tests/run.js` | 78 headless assertions on engine, parser, progression |
| `tests/browser.js` | Playwright smoke test, full session |
| `tests/llmpath.js` | AI path with a stubbed API — actions apply, guardrails hold |
| `tests/import.js` | 69 assertions on name matching, line shapes, headings and messy pastes |
| `tests/p0.js` | 74 assertions on context, corrections, races, validation, safety, staleness |
| `tests/bargein.js` | 17 assertions on interruption, echo suppression, false alarms |
| `tests/ui.js` | 19 assertions on layout, reach, undo, stickiness and contrast |
| `tests/bodymap.js` | 40 assertions on onboarding, the figure, recovery and the heat map |
| `tests/onboarding.js` | 13 assertions on flow length, defaults, first-timers and long returns |
| `tests/warmup.js` | 58 assertions on the warm-up plan, the check-in conversation, and reported soreness overriding the estimate |
| `tests/demo.js` | 26 assertions on seeded history, hands-free playback, populated screens and desktop framing |
| `tests/fixes.js` | 11 assertions that taps, typing, mic denial and the sticky header all behave |
| `tools/gate-compare.js` | replays a labelled session through all four addressee strategies |
| `tools/figure-debug.js` | colour-codes and labels every muscle so a wrong outline is obvious |
| `server/proxy.js` | the backend that keeps the API key off the client |
| `tests/speechgen.js` | 18 assertions on pre-generation, slots, no-repeat, batching, degradation, music grammar |

```
node build.js && for t in run p0 import bargein speechgen llmpath bodymap warmup ui onboarding fixes demo browser; do node tests/$t.js; done
```

---

## Next

1. **Ten gym sessions with audio recording on.** Label the CSV. That decides the mic gate and tells you whether anyone wants music control.
2. **Move every misrecognised phrase into the fast path.** Cheapest, highest-leverage work in the system.
3. **Then go native** — for real ducking, background audio, headset-mic routing and streaming neural TTS. Not before.


---

## Showing it to someone

Open the file with `?demo` on the end of the address. That seeds ten weeks of training — the lifts climb, the overhead press stalls and gets deloaded, a holiday week is missing, one session ended early — so no screen is ever empty and the body map opens with a real pattern of recovery on it.

The ▶ button in the header runs a session forward hands-free: sets logged, timers running, the map heating up, with a pill at the bottom showing what the app just heard. No microphone, no permission dialog. Every step goes through the same code path a spoken utterance does.

`?demo=new` starts clean, on the first onboarding screen.

See `DEMO.md` for how to walk someone through it.


---

## The warm-up, and the conversation inside it

Nothing starts cold, and the check-in happens while you are already moving.

Tapping start does not open a form. It plans a warm-up from the first lift on today's list and begins it immediately: **raise** (get warm), **mobilise** (whatever joint today's pattern asks range from), **activate** (wake the prime movers), then **ramp sets** on the movement itself, computed from the weight actually going on the bar — a 100 kg squat gets 40 × 8, 60 × 5, 80 × 3. A squat day warms ankles and hips; a press day warms thoracic spine and shoulders. A short session trims the general work and never the ramps.

The questions arrive one per warm-up item, each asked once, none of them blocking:

1. **Energy**, during the first drill.
2. **Soreness**, during the second — phrased as a prediction to check, not an open question. *"By my reckoning your glutes and lats should still be feeling yesterday. Is that where it is?"*
3. **Anything else**, only when the second answer surprised it.

The second question is the one that does the work, because it has three possible answers rather than one. You can **confirm** it, **correct** it ("not really" — and the map clears), or **contradict** it ("actually it's my lower back"). The third is the valuable case: soreness where the model did not put it usually means something about how the movement was done, and the app says so — *a lower back sorer than the legs after squatting means the brace is giving way before the legs do*. It names the likely source, writes a note, adds targeted work to the rest of the warm-up, and holds the loads.

**What you say beats what it computed.** A reported reading replaces the estimate for that muscle and decays from the moment you said it, on that muscle's own clock. The body map outlines the muscles you gave it readings for, and the detail panel says which is which — "you told me this was very sore 2h ago" versus "estimated from your training".

The coach tracks warm-up progress rather than just announcing it: it knows which drill you are on, "done" moves you along, and it answers "how much warm-up is left" and "why this one". You can skip it — and it will tell you plainly that your first working set is now the warm-up, and log that you did.
