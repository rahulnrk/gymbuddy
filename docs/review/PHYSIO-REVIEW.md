# Clinical review request — exercise restriction list

Prepared 2026-09-01. Please read Sections A–C before Section D.

---

## What this is, and what we are asking

We are building a voice-based gym coaching app. It does **not** diagnose, treat, or give medical advice. When a user reports a red-flag symptom it stops and tells them to see a clinician.

One thing it does do: at sign-up a user can declare a condition from a fixed list. From then on, the app **refuses to include certain exercises** in any workout it builds for them. That refusal is absolute — it cannot be overridden by the user or by any other part of the system.

**That restriction list was written by an AI in a single pass and has never been read by anyone with clinical training.** That is what we are asking you to do.

Being wrong costs us in two directions:

- **Over-restricting** — a person with mild knee pain is currently blocked from every squat variation we have. They get a worse programme than they should, and may stop using the app.
- **Under-restricting** — someone is handed an exercise that hurts them. This is the one we are actually worried about.

You do not need to design anything. We need you to mark up a list.

---

## Section A — the conditions a user can declare

| Condition | Exercises currently blocked |
|---|---|
| Flexion-intolerant back | 15 |
| Shoulder impingement | 11 |
| Anterior knee pain | 9 |
| Wrist pain | 2 |
| Elbow tendinopathy | 8 |
| Hypertension | 0  ⚠️ **blocks nothing** |
| Pregnancy | 0  ⚠️ **blocks nothing** |
| Recent shoulder surgery | 0  ⚠️ **blocks nothing** |
| Recent knee surgery | 0  ⚠️ **blocks nothing** |

**Question A:** four conditions appear in the list a user can pick from and currently restrict nothing at all. Someone declaring recent shoulder surgery is offered overhead presses and dips. Which exercises, if any, should each of these block?

---

## Section B — the current restriction list

Please mark each line: **keep**, **remove**, or **limit instead of block** (and say how — depth, range, load).


### Flexion-intolerant back — 15 blocked

- [ ] Ab Wheel
- [ ] Back Extension
- [ ] Back Squat
- [ ] Barbell Row
- [ ] Bench Press
- [ ] Cable Crunch
- [ ] Deadlift
- [ ] Dumbbell Good Morning
- [ ] Good Morning
- [ ] Hack Squat
- [ ] Kettlebell Swing
- [ ] Leg Press
- [ ] Pendlay Row
- [ ] Romanian Deadlift
- [ ] T-Bar Row


### Shoulder impingement — 11 blocked

- [ ] Arnold Press
- [ ] Dip
- [ ] Dumbbell Fly
- [ ] Front Raise
- [ ] Incline Bench Press
- [ ] Incline Dumbbell Press
- [ ] Lateral Raise
- [ ] Overhead Press
- [ ] Overhead Triceps Extension
- [ ] Pike Push-up
- [ ] Upright Row


### Anterior knee pain — 9 blocked

- [ ] Back Squat
- [ ] Bulgarian Split Squat
- [ ] Front Squat
- [ ] Goblet Squat
- [ ] Hack Squat
- [ ] Leg Extension
- [ ] Leg Press
- [ ] Step Up
- [ ] Walking Lunge


### Wrist pain — 2 blocked

- [ ] Front Squat
- [ ] Push-up


### Elbow tendinopathy — 8 blocked

- [ ] Barbell Curl
- [ ] Cable Curl
- [ ] Dumbbell Curl
- [ ] Hammer Curl
- [ ] Overhead Triceps Extension
- [ ] Preacher Curl
- [ ] Skullcrusher
- [ ] Triceps Pushdown


### Hypertension — 0 blocked

_Nothing currently blocked._


### Pregnancy — 0 blocked

_Nothing currently blocked._


### Recent shoulder surgery — 0 blocked

_Nothing currently blocked._


### Recent knee surgery — 0 blocked

_Nothing currently blocked._

---

## Section C — 'get someone to watch you' list

Separately from the restriction list, each exercise carries a rating for how badly it can go wrong under load. Exercises rated **high** trigger a warning when someone is training alone without a rack or a spotter, and are capped at six repetitions in that situation.

Currently rated high consequence:

- [ ] Back Squat  _(skill demand: high)_
- [ ] Barbell Row  _(skill demand: high)_
- [ ] Bench Press  _(skill demand: high)_
- [ ] Close Grip Bench Press  _(skill demand: moderate)_
- [ ] Deadlift  _(skill demand: high)_
- [ ] Decline Bench Press  _(skill demand: moderate)_
- [ ] Dip  _(skill demand: moderate)_
- [ ] Front Squat  _(skill demand: high)_
- [ ] Good Morning  _(skill demand: high)_
- [ ] Hack Squat  _(skill demand: low)_
- [ ] Incline Bench Press  _(skill demand: high)_
- [ ] Leg Press  _(skill demand: low)_
- [ ] Overhead Press  _(skill demand: high)_
- [ ] Pendlay Row  _(skill demand: high)_
- [ ] Romanian Deadlift  _(skill demand: high)_
- [ ] Skullcrusher  _(skill demand: moderate)_

**Question C:** anything on this list that should not be, or missing from it that should be?

---

## Section D — four specific questions

Please answer these after reading the sections above.

**D1.** Bench press is currently blocked for a **flexion-intolerant back** and is *not* blocked for **shoulder impingement**. Is that the right way round?

**D2.** Anterior knee pain currently blocks every squat pattern we have (back squat, front squat, goblet, split squat, hack, leg press, leg extension, step-up, walking lunge). Should any of these be limited by depth or range instead of removed entirely?

**D3.** Should a **sudden severe headache during heavy lifting** be treated as urgent — stop and seek care the same day — or as stop-this-exercise? The app currently treats it as the second.

**D4.** Our red-flag list, which stops the workout and refers, is: chest pain; breathlessness at rest or on light effort; dizziness or fainting; palpitations; recent surgery; unexplained night pain; numbness or weakness that travels; a clinician-flagged complication; an uncontrolled diagnosed condition; new unexplained swelling or pain. **Anything missing that should stop a workout?**

---

## Notes






---

## Appendix — read only after Sections A–D

Three things we already suspect are wrong. Deliberately placed last so they did not shape your reading of the list.

1. **Bench press blocked for a flexion-intolerant back.** There is no spinal flexion in a bench press. We think this tag is simply on the wrong condition (see D1).

2. **Four conditions restrict nothing** — hypertension, pregnancy, recent shoulder surgery, recent knee surgery. This is the failure shape that worries us most: it looks like protection and is not.

3. **Anterior knee pain blocks all squatting.** We suspect real practice is range limitation rather than elimination.
