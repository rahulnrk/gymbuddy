# Wireframe brief — gymbuddy

You are designing the screens for an Android app that is **mostly used without
looking at the screen.** Read that sentence twice; it inverts most of what
normally makes a fitness app good.

You do not have access to the codebase and do not need it. Everything you need
is here.

---

## 1. The product

gymbuddy is a voice coach for the gym. The user's phone is in a pocket, a
locker, or face-down on a bench. They have earphones in and their own music
playing. The coach talks to them between sets, the way a trainer walking the
floor would.

A session works like this: every few minutes, when the app calculates that a
set has probably just finished, the coach speaks. The music ducks into the
background — it never pauses — and the microphone opens at the same moment, so
the user can interrupt mid-sentence. They say whatever they say: "got eight",
"that felt heavy", "someone's on the bench", "how much protein do I need". The
coach logs it, answers, and adjusts the plan. Three seconds after the exchange
ends the music comes back and the mic closes.

The user never says a wake word. They cannot start a conversation by speaking.
The coach opens every window — except one: an emergency control on screen, for
pain.

The app knows 77 exercises, 30 warm-ups, 30 stretches and 12 kinds of cardio,
each with the equipment it needs, the muscles it works, and whether bad form is
dangerous. It builds a training week from the user's goal, experience,
equipment and injuries — or coaches them through a plan they bring themselves.

---

## 2. The design problem

Most fitness apps assume the phone is in the user's hand. This one assumes it
is not. So the screen has exactly four jobs, and everything else is secondary:

1. **Set the user up** — a long, one-time conversation before they ever train.
2. **Start and end a session.**
3. **Be glanceable** — when they do look, in the two seconds between sets, they
   must understand where they are without reading.
4. **Be reachable in an emergency** — something hurts and they need help now.

Everything else — history, progress, browsing exercises — is for the sofa, not
the gym floor. Design those calmly and separately. Do not let them leak into
the in-session screen.

---

## 3. Who is using it, and where

An adult training in a commercial or home gym, in India first, on a mid-range
Android phone in portrait. Assume:

- **One hand free.** The other is holding a bar, a dumbbell, or a towel.
- **Sweaty or chalky hands.** Small targets fail. Gloves are common.
- **Bad light** — glare near windows, dim corners in basement gyms.
- **Short attention.** They are between sets, breathing hard, and want to put
  the phone down again.
- **Sometimes in pain, sometimes frightened.** The emergency path has to work
  for someone who is not thinking clearly.

---

## 4. Screens, in priority order

Design 1–4 properly. Sketch 5–9. Note 10–12 as stubs.

### 1. Onboarding / intake — **the highest priority**

The app already has all the logic for this and no screen to run it. It asks
about: training goal, experience level, equipment available, injuries or
medical conditions, and how many days a week they can train.

Two things to get right:

- **Equipment must be simple.** Three choices — full gym, home gym with limited
  kit, bodyweight only. The app understands 43 individual pieces of equipment;
  the user must never see that list.
- **The injury and health questions are the sensitive part.** They decide
  whether the app refuses to give someone an exercise. Ask them so people
  answer honestly rather than skipping — no alarm, no medical-form coldness,
  clear about why it is being asked. Some answers lead to "please see a doctor
  before starting", which must not feel like a rejection.

This is long. Break it up, show progress, let people go back, and never lose
their answers.

### 2. In-session ("gym mode") — the glanceable one

The screen the user looks at for two seconds at a time, possibly from a bench
two feet away.

It must answer, without reading: **what am I doing, which set, how long is
left.** Big type. Very few words.

It must also show the coach's state, because trust in a voice product comes
from being able to check it: is it listening right now, is it speaking, is it
idle. And it must show what the coach last understood — if they said "got
eight" and it logged six, they need to catch that.

The emergency control lives here. Reachable in one thumb movement, impossible
to hit by accident.

### 3. Emergency / pain

Triggered mid-workout by someone who may be hurt. Opens a conversation
immediately.

The app asks what kind of pain and how bad on a scale. Above a certain level it
tells them to stop and see a doctor. Below it, it offers a different movement
or a different muscle group.

Design this to be calm and quick. Big targets, no cleverness, no jokes. It must
be usable one-handed by someone who is not okay. And "see a doctor" must read
as care, not as the app washing its hands.

### 4. Start / end a session

Before: what today's session is, roughly how long, anything they should know.
One large control to begin.

After: what they did, and — this matters — **one true thing worth hearing.**
"You lifted more than last time." "You covered every part of your chest."
"Your week has gone exactly to plan." Computed from their real numbers, never
flattery, and only when there is something real to say. Design for the case
where there is nothing worth saying, too.

**A rest day is a win, not a broken streak.** If you design any streak or
consistency display, it must congratulate planned rest rather than break on it.
Do not design anything that would push someone to train injured.

### 5–9 — sketch these

- **Home / today** — what is on today, one tap to start.
- **The week** — the plan laid out; what is done, what is coming.
- **Bring your own plan** — a user entering their own routine instead of the
  generated one.
- **History** — past sessions.
- **Progress** — lifts over time, muscle coverage.

### 10–12 — stubs only

Sign-in, profile and settings, browsing the exercise library.

---

## 5. Principles for this product specifically

- **The screen supports the voice; it does not compete with it.** If a screen
  makes voice unnecessary, the product has failed.
- **Do not design a chat interface as the main experience.** A transcript view
  is fine, tucked away, for checking what was said. It is not the home screen.
- **Logging a set is a voice job.** Manual entry should exist as a fallback,
  but it must not be the obvious path or people will stop talking to it.
- **Glanceable beats informative.** In session, one number beats five.
- **Say less.** The coach is brief; the screen should match.
- Assume a slow connection and a cheap phone.

## 6. What not to do

- No dense dashboards on the in-session screen.
- No streaks or badges that punish rest.
- No medical-looking forms for the injury questions.
- No tiny targets anywhere that gets touched in a gym.
- No dark-pattern nudges toward training more than planned.

## 7. Deliverable

Wireframes — structure, hierarchy and flow. Grey boxes are fine; this is not a
visual design pass. For each screen give the layout, what is on it, what the
user can do, and a sentence on why it is arranged that way. Show the onboarding
flow end to end, including the branch where someone is told to see a doctor.

Flag anything in this brief that seems wrong, missing, or in conflict. It was
written by someone who has been building the logic, not the interface.
