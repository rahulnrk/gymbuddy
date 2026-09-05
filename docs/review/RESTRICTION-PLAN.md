# Restriction plan — what to do with the clinical review

Written 2026-09-03 from `PHYSIO-REVIEW-EVIDENCE.md` and
`Clinical Exercise Restriction Review.md`, checked against
`knowledge/catalog/exercises.json` as it stands today.

## What we have, and what it is not

Two independent AI reviews answering `PHYSIO-REVIEW.md`. They agree far more
than they disagree, and the evidence one cites real society guidance (ACOG 804,
ACSM/AHA resistance-training guidance, JOSPT patellofemoral CPG,
ESSKA–AOSSM meniscus consensus).

**Neither is a clinician.** Under this project's own provenance rule — grade by
the weakest link — the physio review is still open. What these reviews *do*
discharge is the part that never needed a clinician: our own factual errors and
internal contradictions. Those are in Tier 1 below and can be fixed today.

Everything that **loosens** a restriction is a safety change and belongs in
Tier 3, no matter how confident the reviews sound.

---

## The structural finding

`contraindications` is a flat array of condition slugs. It can say **blocked**
or **not blocked**. Nothing else.

Across the 45 tags we have today, the two reviews return roughly:

| Verdict | Count |
|---|---|
| Both say block | ~12 |
| Both say **limit** — shallower, lighter, different grip or support | ~24 |
| Both say remove | 1 (Bench Press / flexion-intolerant back) |
| They disagree | ~7 |

**More than half of our restriction data is the wrong shape, not just the wrong
value.** Acting on this review without a model change forces every "limit" into
either a ban (over-restricts, produces a worse programme, and is what makes
people quit) or an allow (unsafe). Both are wrong answers.

Hypertension does not fit the per-exercise shape at all. Its real rule is about
*how*, not *what*: no breath-holding, no near-maximal singles, keep reps at
8–12. There is nowhere in the model to put a rule like that.

### Proposed restriction kinds

- `BLOCK` — not offered at all.
- `LIMIT_ROM` — depth or elevation cap (knee flexion angle, shoulder elevation,
  squat depth, step height).
- `LIMIT_LOAD` — intensity cap, expressed as a rep floor rather than %1RM,
  because we never measure 1RM.
- `REQUIRE_VARIANT` — chest-supported, neutral grip, EZ bar, rope, cross-arm
  front rack, parallettes, incline rather than flat.
- `SESSION_RULE` — applies to the whole session, not one exercise: no Valsalva,
  no maximal singles, no supine work, no floor-to-overhead lifting.

### The rule that keeps this honest

**A limit we cannot enforce is a cue, not a guarantee.** The app cannot see
squat depth. It can say "stop at parallel, use a box" and it can ask, but it
cannot verify.

So the collapse rule is:

- Where the consequence of the user ignoring the cue is **high** — anything
  post-surgical, pregnancy supine/Valsalva, uncontrolled hypertension — an
  unenforceable limit collapses to `BLOCK`.
- Where the condition is a **chronic pain label** — flexion-intolerant back,
  anterior knee pain, impingement, tendinopathy — a spoken cue plus a
  substitute offer is proportionate, and blanket banning is its own harm.

Record that rule in `DECISIONS_AND_THRESHOLDS.md` as a `PRODUCT DECISION`, with
the reasoning, because it is the thing that decides most of the table below.

---

## Tier 1 — fix now, no clinician needed

These are our errors or internal contradictions. None of them require a
judgement call a layperson cannot check.

1. **Delete `flexion-intolerant-back` from Bench Press.** There is no spinal
   flexion in a bench press. Both reviews say remove; this is a mis-tag, not a
   clinical opinion.
2. **Sudden severe headache during lifting becomes a red flag** — stop, seek
   care the same day. The review brief assumed we treat it as "skip this
   exercise"; checking the tree, **the word "headache" does not appear in the
   domain at all**, so today it does nothing. That is worse than the brief
   described, and it is the highest-severity item that is purely ours to fix.
   Both reviews call a sudden severe headache during exertion a
   same-day-care event, not an exercise swap.
3. **Add the missing red flags** both reviews name: stroke signs (one-sided
   weakness, facial droop, speech change, sudden visual loss); the cauda equina
   cluster (saddle numbness, new bowel or bladder change, rapidly worsening
   bilateral leg weakness); rhabdomyolysis cluster (severe pain out of
   proportion, swelling, dark urine); hot rapidly swelling joint.
4. **Record the contradiction we already have in writing**: "recent surgery" is
   a red flag that stops today's session, and also blocks nothing when the app
   builds next week's. Until Tier 3 lands, that is a known hole and it should
   be stated in the contract, not left implicit.

---

## Tier 2 — needs the model change first

The full mark-up. **E** = `PHYSIO-REVIEW-EVIDENCE.md`, **G** = Gemini review.
Rows where they disagree are Tier 3, marked ⚠.

### flexion-intolerant-back (15 today)

| Exercise | E | G | Proposed |
|---|---|---|---|
| Ab Wheel | block | block | `BLOCK` |
| Cable Crunch | block | block | `BLOCK` |
| Good Morning | block | block | `BLOCK` |
| Dumbbell Good Morning | block | block | `BLOCK` |
| Romanian Deadlift | limit | limit | `LIMIT_ROM` — stop above the knee |
| Barbell Row | limit | limit | `REQUIRE_VARIANT` — chest-supported |
| T-Bar Row | limit | limit | `REQUIRE_VARIANT` — chest-supported |
| Kettlebell Swing | limit | limit | `LIMIT_LOAD` + hinge cue |
| Back Squat | limit | limit | `LIMIT_ROM` — to parallel |
| Hack Squat | limit | limit | `LIMIT_ROM` |
| Leg Press | limit | limit | `LIMIT_ROM` — stop before pelvic tilt |
| Bench Press | remove | remove | **remove the tag** (Tier 1) |
| Deadlift ⚠ | block by default | limit (rack pull) | Tier 3 |
| Pendlay Row ⚠ | limit | block | Tier 3 |
| Back Extension ⚠ | remove | limit ROM | Tier 3 |

### shoulder-impingement (11 today)

| Exercise | E | G | Proposed |
|---|---|---|---|
| Overhead Press | block | block | `BLOCK` |
| Arnold Press | block | block | `BLOCK` |
| Pike Push-up | block | block | `BLOCK` |
| Dip | block | block | `BLOCK` |
| Upright Row | block | block | `BLOCK` |
| Overhead Triceps Extension | block | block | `BLOCK` |
| Lateral Raise | limit | limit | `LIMIT_ROM` — scapular plane, below the painful arc |
| Incline Bench Press | limit | limit | `LIMIT_ROM` — low incline only |
| Incline Dumbbell Press | limit | limit | `LIMIT_ROM` + neutral grip |
| Dumbbell Fly | limit | limit | `LIMIT_ROM` — floor fly / short range |
| Front Raise ⚠ | block above 90° | limit elevation | Tier 3 (same rule, different default) |

**Both say the list is missing**: wide-grip bench, behind-neck work. Adding a
restriction is Tier 2, not Tier 3 — it errs safe.

### anterior-knee-pain (9 today)

**Unanimous: every one of the nine is a limit, not a block.** This is the
clearest over-restriction in the catalogue, and the reviews agree it is the
change most likely to stop people abandoning the app.

| Exercise | Proposed |
|---|---|
| Back Squat, Front Squat, Goblet Squat | `LIMIT_ROM` — high box / ~45–60° |
| Hack Squat, Leg Press | `LIMIT_ROM` |
| Bulgarian Split Squat, Walking Lunge | `LIMIT_ROM` — depth capped |
| Step Up | `LIMIT_ROM` — step height |
| Leg Extension | `LIMIT_ROM` — avoid the painful arc |

Keep a bilateral squat pattern in the programme. Do not empty the day.

### elbow-tendinopathy (8 today)

| Exercise | E | G | Proposed |
|---|---|---|---|
| Barbell Curl | block | block | `BLOCK` in a flare |
| Preacher Curl | block | block | `BLOCK` in a flare |
| Cable Curl | limit | limit | `LIMIT_LOAD` + rope/EZ |
| Dumbbell Curl | limit | limit | `LIMIT_LOAD` |
| Triceps Pushdown | limit | limit | `REQUIRE_VARIANT` — rope, neutral wrist |
| Hammer Curl ⚠ | limit | remove entirely | Tier 3 — both loosen, G more |
| Skullcrusher ⚠ | block or hard limit | limit | Tier 3 |
| Overhead Triceps Extension ⚠ | block or hard limit | limit | Tier 3 |

**Both say missing**: pull-ups, heavy mixed-grip deadlifts, fat-bar holds —
grip-dominant work is often more provocative than a pushdown.

### wrist-pain (2 today)

Both: the fix is grip and equipment, not deletion. `REQUIRE_VARIANT` —
cross-arm or strap front rack; parallettes, fists or dumbbells for push-ups.
Collapses to `BLOCK` when the equipment preset cannot supply the variant.

**Both say the list is too short**: add flat-palm plank and burpee.

---

## Tier 3 — needs a human

Take exactly this list to a physio. It is a much cheaper conversation than the
original brief.

1. **The four empty conditions.** Hypertension, pregnancy, recent shoulder
   surgery, recent knee surgery. Both reviews call these the highest-severity
   gap. The reviews propose conservative defaults; a clinician should sign them
   off, because "recent surgery" without a procedure and a date is not a
   programmable state and the reviews say so themselves.
2. **The seven rows marked ⚠** where the two reviews disagree.
3. **Everything that loosens a restriction.** Anterior knee pain is the big
   one: nine bans becoming nine depth limits is the right direction on the
   evidence, and it is still a change that makes the app offer squats to
   someone with knee pain. That needs a name against it.
4. **The collapse rule** — whether a spoken, unverifiable cue is acceptable for
   chronic pain labels. That is the judgement the whole design rests on.

## Also worth fixing, low severity

Section C of the evidence review: **hack squat and leg press should come off the
high-consequence list.** The machine holds the load; listing them beside an
unracked back squat teaches users to ignore the warning. Deadlift and the rows
are skill-high but not trap-you-under-the-bar, and could be a separate class.

---

## Order

1. Tier 1 — today. No model change, no clinician.
2. The model change — restriction kinds, the collapse rule, its DECISIONS row.
   Validator and picker must both understand the new kinds before any data
   moves.
3. Tier 2 data, condition by condition, tightenings before loosenings.
4. Tier 3 after a clinician signs it.

Nothing in Tier 2 or 3 ships to a user before a clinician has seen Tier 3.
