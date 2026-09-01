# Sourced programming numbers — research pass, 2026-09-01

Closes the gaps list from the programming-rules PR. Every row below either
upgrades an `MVP HEURISTIC` to `PUBLISHED GUIDELINE`, or confirms it must stay a
heuristic. Sources are named papers, not this folder's synthesized bundles.

**First, a correction.** `docs/research/README.md` flagged the "ACSM 2026
Position Stand" citation as unverified. **It is real**: Currier et al. (2026),
*ACSM Position Stand: Resistance Training Prescription for Muscle Function,
Hypertrophy, and Physical Performance in Healthy Adults — An Overview of
Reviews*, Medicine & Science in Sports & Exercise. 137 systematic reviews,
>30,000 participants, first update in 17 years. The research bundle described it
accurately.

---

## 1. The volume-counting model — change the mechanism, not the number

`PRIMARY_MUSCLE_CONTRIBUTION_MIN = 0.5` is a **threshold**: a muscle above 0.5
contribution gets credited a whole set, below 0.5 gets nothing. The literature
does not do it that way, and it has tested the alternatives directly.

Pelland et al. (2025) classified every contributing set as direct or indirect
and compared three counting methods across 67 studies / 2,058 participants:
indirect sets counted as **1.0 ('total')**, **0.5 ('fractional')**, or
**0 ('direct')**. The evidence was strongest for **fractional**. Two companion
meta-analyses (Hamaïde et al. 2025 for hypertrophy, Joachim et al. 2025 for
strength) found the same, with Bayes factors strongly favouring fractional.

**Recommended model — fractional counting:**

```
contribution >= 0.75   -> credit 1.0 sets   (primary force generator)
0.30 <= contribution < 0.75 -> credit 0.5 sets   (meaningfully trained, not primary)
contribution < 0.30    -> credit 0
```

Class: `PUBLISHED GUIDELINE` for the 0.5 fractional weight; the 0.75 / 0.30 band
edges remain `MVP HEURISTIC` (the papers classify direct/indirect by judgement,
not by a numeric cutoff — we need one because our map is weighted).

Effect on the worked example (one push day, 4 sets each of bench, incline DB
press, OHP, cable fly, lateral raise, pushdown): triceps currently tallies 12
whole sets under the threshold model. Under fractional counting it tallies ~8,
which is a far more defensible input to a 10–20 landmark.

---

## 2. Volume landmarks

| Constant | Was | Becomes | Class | Source |
|---|---|---|---|---|
| `HYPERTROPHY_INTERMEDIATE_HARD_SETS` | 10–20 | **10–20** (unchanged) | `PUBLISHED GUIDELINE` | ACSM 2026: "higher weekly volume (~10 sets per muscle group)". Baz-Valle 2022: 12–20 weekly sets optimum for trained young men. Schoenfeld 2017: graded dose–response. |
| `HYPERTROPHY_BEGINNER_HARD_SETS` | 6–12 | **4–10** | `PUBLISHED GUIDELINE` (low confidence) | Behm 2023 (narrative review): beginners gain with <3 sets per multi-joint exercise, one session/week, first 12 weeks. Peterson 2005: untrained optimum ~4 sets/muscle group. Uğurlu 2024: in untrained women moderate volume beat both low **and** high. |
| `STRENGTH_HARD_SETS` | 6–12 | **4–10** | `PUBLISHED GUIDELINE` | ACSM 2026: 2–3 sets per exercise at ≥80% 1RM, all major muscle groups ≥2×/week. Ralston 2017: medium/high weekly sets beat low. |
| `STRENGTH_BEGINNER_HARD_SETS` | 4–8 | **3–8** (near-unchanged) | `PUBLISHED GUIDELINE` (low confidence) | Rhea 2003 (140 studies, 1,433 ES): untrained maximal gains at 60% 1RM, 3 d/wk, ~4 sets per muscle group. Peterson 2005 confirms. |
| Fat-loss volume | copies hypertrophy | **copies hypertrophy** (unchanged) | `PUBLISHED GUIDELINE` | Roth 2022: high (5 sets/exercise) vs moderate (3) made no difference to lean-mass preservation in a deficit. Roth 2022 review: ≥10 weekly sets per muscle associated with little-to-no lean mass loss. The copy is now supported, not a guess. |
| `ENDURANCE_HARD_SETS`, `GENERAL_FITNESS_HARD_SETS` | 8–16 | **8–16** (unchanged) | `MVP HEURISTIC` | Still silent. ACSM 2026 gives frequency ("at least twice a week") but no endurance set landmark. |

**Ambiguity to record:** the Rhea/Peterson "4 sets per muscle group" is reported
without stating per-session or per-week. Both readings land inside the ranges
above, which is why the ranges are wide.

---

## 3. Effort — delete the beginner RIR band

`BEGINNER_RIR = 3–5` assumed novices should stay further from failure because
they judge effort less well. **The evidence contradicts the premise.**

- Bermúdez Droguett et al. (2025): no difference in RIR estimation accuracy
  between experienced (≥18 months) and novice lifters in the back squat.
- Zourdos et al. (2019): training age was **not** a significant predictor of RIR
  accuracy. What predicts it is proximity to failure and set length — error was
  2.05 reps at 1 RIR, 3.65 at 3 RIR, and **5.15 reps at 5 RIR**; more reps per
  set made estimates worse.
- Lovegrove et al. (2022): RIR was a reliable load-prescription tool in a young
  **novice** population (ICC 0.95–0.99).

**Recommended change.** Drop `BEGINNER_RIR`. Keep `MOST_WORK_RIR = 2–4` for
everyone (`PUBLISHED GUIDELINE`, ACSM 2026 / Guide §4.3). Add instead a
**confidence rule on reported effort**, which matters directly to a voice coach
that asks "how many did you have left?":

```
reported RIR <= 3 and set <= 12 reps  -> trust it
reported RIR >= 4, or set > 12 reps   -> low confidence, do not drive a load
                                         change off this alone
```

Class: `PUBLISHED GUIDELINE` (Zourdos 2019 gives the direction and rough
magnitude; the exact cutoffs are ours).

**One real modifier, and it is age, not training age.** Gómez-Redondo et al.
(2025): community-dwelling older adults (68 ± 4 yr) underestimated repetitions
at predicted RIR 2 and RIR 4, and their RPE ratings showed limited sensitivity.
If the app ever serves older adults, treat their RIR reports as low confidence
regardless of experience.

---

## 4. Repetition ranges

| Constant | Was | Becomes | Class | Source |
|---|---|---|---|---|
| `STRENGTH_REPS` | 3–6 | **4–8** | `PUBLISHED GUIDELINE` | ACSM 2026 anchors strength at **≥80% 1RM**, which is roughly an 8RM load, not a 3RM. 3–6 reps implies ~85–93% 1RM — heavier than the guideline, and heavier than is wise for a consumer app with no spotter and no form check. 4–8 sits on the guideline. |
| `HYPERTROPHY_REPS` | 6–12 | **6–12** (unchanged) | `PUBLISHED GUIDELINE` | NASM OPT. |
| `ENDURANCE_REPS` | 12–20 | **12–20** (unchanged) | `PUBLISHED GUIDELINE` | NASM OPT stabilization endurance. |
| `FAT_LOSS_REPS` | 8–15 | **delete — inherit hypertrophy** | `PUBLISHED GUIDELINE` | Carlson et al. (2022), randomised crossover, n=130: heavy (~80% 1RM) and light (~60% 1RM) load produced statistically equivalent fat loss. Load does not drive fat loss; keeping the muscle and the calorie deficit do. A separate fat-loss rep range implies a distinction the evidence does not support. |
| `GENERAL_FITNESS_REPS` | 8–15 | **8–15** (unchanged) | `MVP HEURISTIC` | ACSM 2026 gives no rep range for general health, only frequency. |

**Available if wanted:** ACSM 2026 gives power as **30–70% 1RM with fast
concentric intent, ≤24 reps × sets**. The `Goal` enum has no `POWER`; this is
enough to add one properly if the product wants it.

---

## 5. Rest — and a distinction we were missing

The published 3–5 min for strength is for **heavy compounds in trained
lifters**. Two modifiers are well supported and neither is currently modelled.

| Constant | Was | Becomes | Class | Source |
|---|---|---|---|---|
| `STRENGTH_REST_SEC` (compound) | 180–300 | **180–300** (unchanged) | `PUBLISHED GUIDELINE` | Senna 2016: 3–5 min needed for bench press at 3RM loads. Schoenfeld 2016: 3 min beat 1 min for strength and hypertrophy in trained men. |
| `STRENGTH_ISOLATION_REST_SEC` | 60–180 | **120–180** | `PUBLISHED GUIDELINE` | Senna 2016: for the single-joint machine chest fly, **2 min was sufficient**; the multi-joint bench press needed 3–5. The isolation modifier is now sourced, not invented. |
| **NEW** `STRENGTH_REST_SEC_BEGINNER` | — | **60–120** | `PUBLISHED GUIDELINE` | Grgic 2017 (systematic review, 23 studies): >2 min needed to maximise strength in **resistance-trained** individuals, but **60–120 s is sufficient for untrained**. Rest should depend on training age, not goal alone. |
| `HYPERTROPHY_REST_SEC` | 60–180 | **90–180** | `PUBLISHED GUIDELINE` | Singer 2024 (Bayesian MA, 9 studies): small hypertrophic benefit to resting >60 s, but **no appreciable further benefit beyond 90 s**. 90 s is the practical floor. |
| `ENDURANCE_REST_SEC` | 30–90 | **30–90** (unchanged) | `PUBLISHED GUIDELINE` | Guide §4.3. |
| `FAT_LOSS_REST_SEC` | 30–90 | **inherit hypertrophy (90–180)** | `PUBLISHED GUIDELINE` | Same reasoning as the rep range — fat loss is not a distinct prescription. Treating it as metabolic circuit work was a guess. |
| `GENERAL_FITNESS_REST_SEC` | 60–180 | **60–180** (unchanged) | `MVP HEURISTIC` | Silent. |

---

## 6. Confirmed to stay heuristics

These were flagged as gaps and the search closed none of them. Leave them
`MVP HEURISTIC` — that is the correct label, not a defect.

- `ORDER_SKILL_*`, `ORDER_FATIGUE_*`, `ORDER_COMPOUND_BONUS`,
  `ORDER_CORE_PENALTY`. The **rule** is published — ACSM 2026: strength work
  belongs "at the beginning of training sessions"; NSCA and the ACE exam outline
  say high-skill work goes while fresh. No source gives numeric sort weights,
  and none needs to; these are an implementation detail of a published ordering
  rule.
- `ENDURANCE_HARD_SETS`, `GENERAL_FITNESS_HARD_SETS`, `GENERAL_FITNESS_REPS`,
  `GENERAL_FITNESS_REST_SEC`.
- The 0.75 / 0.30 band edges in the new fractional counting model.

## 7. Out of scope but worth knowing

Murphy et al. (2021), meta-regression: an energy deficit of about
**500 kcal/day** is the point at which lean-mass gains stop. Nutrition is
outside the app's scope of practice and this must not become a calorie
recommendation — but it is the reason a "fat loss" goal cannot promise muscle
gain, and the coach should not imply otherwise.

---

## Sources

- Currier BS et al. (2026). *ACSM Position Stand: Resistance Training Prescription for Muscle Function, Hypertrophy, and Physical Performance in Healthy Adults: An Overview of Reviews.* Med Sci Sports Exerc.
- Pelland JC et al. (2025). *The Resistance Training Dose Response: Meta-Regressions Exploring the Effects of Weekly Volume and Frequency on Muscle Hypertrophy and Strength Gains.* Sports Med.
- Hamaïde AA et al. (2025); Joachim JM et al. (2025); Mikula SA et al. (2025). *The Resistance Training Dose-response* series. Med Sci Sports Exerc.
- Baz-Valle E et al. (2022). *A Systematic Review of The Effects of Different Resistance Training Volumes on Muscle Hypertrophy.* J Hum Kinet.
- Schoenfeld BJ et al. (2017). *Dose-response relationship between weekly resistance training volume and increases in muscle mass.* J Sports Sci.
- Rhea MR et al. (2003). *A meta-analysis to determine the dose response for strength development.* Med Sci Sports Exerc.
- Peterson MD et al. (2005). *Applications of the dose-response for muscular strength development.* J Strength Cond Res.
- Ralston GW et al. (2017). *The Effect of Weekly Set Volume on Strength Gain: A Meta-Analysis.* Sports Med.
- Behm DG et al. (2023). *Minimalist Training: Is Lower Dosage or Intensity Resistance Training Effective to Improve Physical Fitness?* Sports Med.
- Uğurlu D et al. (2024). *Dose-response effects of 8-week resistance training in untrained young women.* Medicine.
- Roth C et al. (2022). *Resistance training volume does not influence lean mass preservation during energy restriction in trained males.* Scand J Med Sci Sports. And *Lean mass sparing in resistance-trained athletes during caloric restriction.* Eur J Appl Physiol.
- Zourdos MC et al. (2019). *Proximity to Failure and Total Repetitions Performed in a Set Influences Accuracy of Intraset RIR-based RPE.* J Strength Cond Res.
- Bermúdez Droguett FA et al. (2025). *Objective Accuracy in Estimating Repetitions in Reserve in the Back Squat: Experienced vs. Novice.* J Hum Kinet.
- Lovegrove S et al. (2022). *Repetitions in Reserve Is a Reliable Tool for Prescribing Resistance Training Load.* J Strength Cond Res.
- Gómez-Redondo P et al. (2025). *Validity of repetitions in reserve for prescribing resistance exercise in older adults.* Exp Gerontol.
- Carlson L et al. (2022). *The Effects of Training Load During Dietary Intervention Upon Fat Loss.* Res Q Exerc Sport.
- Senna GW et al. (2016). *Effect of Different Interset Rest Intervals on Performance of Single and Multijoint Exercises With Near-Maximal Loads.* J Strength Cond Res.
- Grgic J et al. (2017). *Effects of Rest Interval Duration in Resistance Training on Measures of Muscular Strength: A Systematic Review.* Sports Med.
- Singer A et al. (2024). *Give it a rest: a systematic review with Bayesian meta-analysis on inter-set rest interval duration and muscle hypertrophy.* Front Sports Act Living.
- Schoenfeld BJ et al. (2016). *Longer Interset Rest Periods Enhance Muscle Strength and Hypertrophy in Resistance-Trained Men.* J Strength Cond Res.
- Murphy CA et al. (2021). *Energy deficiency impairs resistance training gains in lean mass but not strength.* Scand J Med Sci Sports.
