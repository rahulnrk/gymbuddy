# Clinical review of the exercise restriction list

Prepared 2026-09-03.

This is an evidence synthesis for product design. It is **not** medical advice, a diagnosis, or a substitute for a clinician. An app that cannot be overridden and has no clinician in the loop should be strict on under-restriction for surgery, uncontrolled disease, and red flags, and less absolute on chronic pain labels.

**How this was read.** Modern musculoskeletal rehab generally uses load, range, and symptom monitoring rather than lifetime bans. “Recent shoulder surgery” and “recent knee surgery” are not single conditions. ACL reconstruction, meniscus repair, TKA, rotator-cuff repair, and SLAP repair have incompatible timelines. A single static list cannot be correct. If the app only gets a label, the safe default is conservative.

---

## Question A — the four conditions that currently block nothing

These are the highest-risk gaps. The appendix suspicion is correct.

### Hypertension

Resistance training is **recommended** when blood pressure is controlled. It is not “block every lift.”

Evidence-based constraints:

- Uncontrolled BP (classically **>180/110 mmHg**) is treated as an absolute reason not to start heavy resistance work until medically managed (AHA).
- Controlled hypertension: moderate loads (about 40–60% 1RM, higher reps), **no Valsalva / breath-holding**, no near-max singles (ACSM / AHA programming).
- Peak arterial pressure during a 5RM leg press **with** Valsalva has been measured well above 300 mmHg. That is the actual hazard, not “squats exist.”

**If the app only knows “hypertension” and not control status:**

| Action | Exercises / rules |
|---|---|
| Do **not** blank-block the catalogue | Walking, machines, moderate dumbbell work are appropriate |
| **Limit** (better than ban) | Back squat, front squat, deadlift, bench, overhead press — cap to ≥8–12 reps, no 1–5RM work, explicit “breathe through the lift” |
| Treat as **session stop** if reported | Resting BP very high, sudden severe headache, chest pain, visual change |

Blocking *zero* lifts while still offering heavy compounds is the failure mode flagged in the brief.

### Pregnancy

ACOG’s position is the opposite of a total ban: uncomplicated pregnancy should keep exercising. The restrictions are positional and mechanistic, not “no gym.”

**Should block or hard-limit:**

- Prolonged **supine** work after the first trimester: flat bench press, skullcrushers, floor crunches, lying leg raises.
- High intra-abdominal pressure / Valsalva: heavy deadlift, heavy squat singles, ab wheel, loaded good morning.
- Fall / loss-of-balance risk and late-pregnancy overhead or floor-to-overhead lifts. NIOSH-derived pregnancy lifting guidance specifically advises against lifting from below mid-shin and overhead.
- Contact / impact / overheating (less relevant to a typical gym builder, but dips on unstable bars and box jumps belong off the list).

**Usually keep, with a load cap:** goblet squat, supported split squat, hip hinge with light load, rows, machines, walking.

Also add pregnancy-specific **session-stop** signs (ACOG warning list): vaginal bleeding, fluid leak, regular painful contractions, calf swelling, headache, chest pain, dizziness.

### Recent shoulder surgery

“Recent” without procedure and date is not a programmable medical state. Typical published timelines:

- Rotator-cuff repair: no active overhead for weeks; gym overhead press often **4–6 months**.
- Labral / Bankart / SLAP: no combined abduction–external rotation and limited biceps load early; overhead later than daily living.
- Arthroplasty: protected ER/IR depending on the approach; no early loaded press.

**Safe default if the user only ticked the box:**

Block all of: overhead press, Arnold press, pike push-up, dip, upright row, fly, incline press, pulldown / behind-neck work, heavy bench, loaded carry on that arm, and any dip / push-up that puts bodyweight through the operated shoulder.

Do **not** invent a “cleared at week 8” rule inside the app. The restriction should decay only with a date + procedure, or a clinician-clearance flag. Until then, an empty list means offering dips after a cuff repair.

### Recent knee surgery

Same problem. Consensus examples:

- Meniscus **repair** (not trim): deep loaded flexion often restricted **~4 months**; some protocols cap squat depth at about 60–70° for 12–16 weeks.
- ACL reconstruction: mini-squats appear early; deep, heavy, or high-speed single-leg work is criteria-based, often many months.
- TKA: load and impact are limited; running / deep sport loading is often never the goal.

**Safe default:**

Block or hard-limit to shallow range: back / front / goblet / hack squat, walking lunge, Bulgarian split squat, deep leg press, jump / step-up variations. Allow: quad sets, bridges, mini-squat to a high box, seated machines in a protected arc **only if** ROM can be encoded.

If “recent surgery” is already a **red-flag that stops the session**, that is correct for *today*. It does not replace a restriction list for the weeks after they come back and tap “build me a workout.”

---

## Section B — mark-up of the current lists

Rule used: **keep** if the movement necessarily loads the sensitised tissue in the provocative direction; **limit** if a shallower / lighter / supported version is standard practice; **remove** if the tag is biomechanically wrong.

### Flexion-intolerant back (15)

This label usually means discogenic / flexion-sensitive low back pain: symptoms worse with sitting, toe-touch, loaded rounding. Rehab is generally avoid end-range loaded flexion, train hip hinge and extension tolerance — not “no gym.”

| Exercise | Verdict | Why |
|---|---|---|
| Ab Wheel | **Keep** | End-range lumbar flexion under load |
| Cable Crunch | **Keep** | Same |
| Good Morning | **Keep** | Loaded hip hinge with a long lever; lumbar flexion + shear if form breaks |
| Dumbbell Good Morning | **Keep** | Same pattern |
| Deadlift | **Keep** as default | Not flexion by definition, but the most common place people lose neutral under load |
| Romanian Deadlift | **Limit** | Allow only if a neutral hinge and a load cap can be enforced; otherwise treat as deadlift |
| Kettlebell Swing | **Limit / keep if no coaching** | Fine as a hinge; ugly as a squat-swing with a rounded lumbar spine |
| Barbell Row | **Limit** | Prefer chest-supported row. Bent-over rows are a flexion-bias position |
| Pendlay Row | **Limit** | Same |
| T-Bar Row | **Limit** | Same unless chest-supported |
| Back Squat | **Limit** | Not a flexion exercise if the spine stays neutral. Depth / load cap, not deletion |
| Hack Squat | **Limit** | Bottom position often forces lumbar rounding as knees come to chest. Restrict depth |
| Leg Press | **Limit** | Same rounding-at-the-bottom problem |
| Back Extension | **Remove the block** | This is extension. Flexion-intolerant patients are often *treated* with extension-biased work |
| Bench Press | **Remove the block** | No spinal flexion. This tag is on the wrong condition |

Over-restriction here is real: blocking every squat and every press because someone is flexion-sensitive will produce a worse programme than a box squat plus a chest-supported row.

### Shoulder impingement / subacromial pain (11)

Provocation is typically elevation + internal rotation, or loaded end-range flexion / abduction. Rehab is activity modification plus cuff / scapular loading below the painful arc, not permanent exile from pressing.

| Exercise | Verdict |
|---|---|
| Overhead Press | **Keep** |
| Arnold Press | **Keep** |
| Pike Push-up | **Keep** |
| Dip | **Keep** — shoulder extension + internal rotation under load; commonly provocative |
| Upright Row | **Keep** — classic impingement-position exercise |
| Overhead Triceps Extension | **Keep** |
| Front Raise | **Keep** if it goes above ~90°; **limit** if height can be capped |
| Lateral Raise | **Limit** — scaption, thumb-up, stop at or below the painful height. A full ban is heavier than most protocols |
| Incline Bench Press | **Limit** — steep incline ≈ overhead. Low-moderate incline with tucked elbows is often kept |
| Incline Dumbbell Press | **Limit** — same |
| Dumbbell Fly | **Limit** — end-range horizontal abduction. Floor fly or shorter ROM is the usual workaround |

Bench press is **correctly not banned** if a narrower grip and tucked elbows can be enforced. Wide-grip bench and behind-neck work should be on this list and currently are not.

### Anterior knee pain (9)

Patellofemoral pain is defined as pain with loading a flexed knee. Squatting is a diagnostic test and also a treatment when depth and load are titrated. Patellofemoral joint stress rises with knee flexion and peaks roughly in the 60–90° region. Mini-squats and hip-dominant patterns are standard first-line, not elimination of every squat.

| Exercise | Verdict |
|---|---|
| Back Squat | **Limit** to ~45–60° or to a high box; progress depth as symptoms allow |
| Front Squat | **Limit** — same |
| Goblet Squat | **Limit** — same |
| Hack Squat | **Limit** ROM (do not let the knee travel into deep flexion under high load) |
| Leg Press | **Limit** ROM |
| Bulgarian Split Squat | **Limit** depth. Often *more* PFJ-stressful than a bilateral box squat |
| Walking Lunge | **Limit** depth |
| Step Up | **Limit** step height |
| Leg Extension | **Limit** the painful arc (open-chain 0–30° is often the worst). Do not treat as radioactive forever; it is used in later-stage PFPS programmes |

Blanket-blocking every squat pattern is the over-restriction case in the brief. It is also the one most likely to make people quit the app.

### Wrist pain (2)

Front squat and push-up are the right *examples*, but the list is too short.

Both hurt because they drive the wrist into loaded extension (front rack, palm-flat plank). The standard fix is position change, not exercise death: cross-arm / strap front squat, parallettes or fists for push-ups, neutral-grip press.

- If the builder **cannot** change equipment or grip: **keep the block** on front squat and push-up, and add flat-palm plank, burpee, and possibly barbell bench.
- If it **can** substitute: **limit instead of block**.

### Elbow tendinopathy (8)

The current list is “every curl + every isolation triceps move.” That is too crude. Tendinopathy is treated by **graded loading** of the affected tendon, while reducing the irritating combination of heavy grip + long lever.

| Exercise | Verdict |
|---|---|
| Barbell Curl | **Keep** as default in a flare — high grip + elbow flexion under load |
| Preacher Curl | **Keep** as default in a flare |
| Cable Curl | **Limit** — reduce load |
| Dumbbell Curl | **Limit** — reduce load |
| Hammer Curl | **Limit, do not automatically ban** — frequently the workaround |
| Skullcrusher | **Keep or limit hard** — long lever, often provocative |
| Overhead Triceps Extension | **Keep or limit hard** |
| Triceps Pushdown | **Limit** — often the better-tolerated triceps option, especially rope / neutral wrist |

Missing from the list and often more irritating than a pushdown: pull-ups, heavy mixed-grip deadlifts, fat-bar holds. Those are grip-dominant, not “elbow isolation.”

### Hypertension / Pregnancy / Recent shoulder surgery / Recent knee surgery

Currently 0 blocked. See Question A. Do not leave these empty.

---

## Section C — high-consequence / “get someone to watch you”

This list mixes two different risks: **you can get trapped under the bar** vs **the lift is technically demanding**.

**Should stay high-consequence** (trap or dump-on-you risk, or face / neck line of the bar):

- Back squat
- Front squat
- Bench press
- Close-grip bench press
- Decline bench press
- Incline bench press
- Overhead press
- Good morning
- Dip (if no step or assist)
- Skullcrusher

**Skill-high but not “pinned under a bar”:**

- Deadlift
- Romanian deadlift
- Barbell row
- Pendlay row

A missed deadlift is usually dropped on the floor. Still worth a warning for solo lifters, but the six-rep cap-plus-spotter logic of a squat is a different animal.

**Should probably come off “high consequence”:**

- **Hack squat** and **leg press** — skill demand is low and the machine holds the load. Listing them next to an unracked back squat trains users to ignore the warning.

**Missing and more deserving than leg press:**

- Any unracked barbell lift in a rack without safeties (the *situation*, not the name).
- Heavy hip thrust if the bar can roll onto the abdomen / hips and they have no stop.
- Behind-neck press, if that movement is ever added.

The six-rep cap for solo high-consequence work is a reasonable product rule even if it is not a clinical guideline.

---

## Section D

### D1. Bench press is blocked for a flexion-intolerant back and is not blocked for shoulder impingement. Is that the right way round?

**No.** There is no spinal flexion in a correctly set bench. That tag is a mis-label. Remove it from flexion-intolerant back.

For impingement, bench is often *kept with modification* (tucked elbows, floor press, narrower grip) rather than banned. So “not blocked for impingement” is defensible **if** the app can constrain technique. A wide-grip, flared-elbow bench is a different exercise and should be treated like an overhead pattern.

Right way round: **do not block bench for a flexion-sensitive back. Consider limiting, not banning, bench for impingement.**

### D2. Anterior knee pain currently blocks every squat pattern. Should any of these be limited by depth or range instead of removed entirely?

**Yes. Almost all of them.** That is how patellofemoral pain is actually trained: reduce knee flexion and load until symptoms settle, then restore depth. Eliminating every squat, lunge, step-up, hack squat, and leg press is the over-restriction case in the brief.

A workable rule: default cap at a high box / about 45–60° knee flexion, no walking lunge, no deep single-leg work, keep a bilateral squat pattern in the programme.

### D3. Should a sudden severe headache during heavy lifting be treated as urgent — stop and seek care the same day — or as stop-this-exercise?

**Urgent. Same day. Emergency-department territory, not “swap the lift.”**

A sudden severe headache that peaks in seconds during exertion or Valsalva is a thunderclap headache. The diagnosis you treat as true until imaging says otherwise is **subarachnoid haemorrhage** (and related vascular events such as reversible cerebral vasoconstriction syndrome or arterial dissection). Onset during exertion is one of the Ottawa SAH rule items. Primary exertional headache exists, but an app cannot tell them apart.

Current behaviour — treat as stop-this-exercise — is under-restricting the category the brief said it is actually worried about.

### D4. Anything missing from the red-flag list that should stop a workout?

The existing list is already close to a standard medical screen. Add these, because they are the ones gym contexts actually produce:

1. **Sudden severe / worst-ever / thunderclap headache**, especially during a lift or strain.
2. **Neurological stroke signs**: one-sided weakness, facial droop, speech change, sudden visual loss.
3. **Cauda equina cluster**: saddle numbness, new bowel or bladder change, rapidly worsening bilateral leg weakness. “Numbness that travels” is close but not specific enough.
4. **Pregnancy-specific stops**: bleeding, fluid leak, regular contractions, reduced fetal movement.
5. **Rhabdomyolysis cluster** after extreme sessions: severe muscle pain out of proportion, swelling, dark tea-coloured urine.
6. **Hot, red, rapidly swelling joint** (infection / crystal arthropathy), distinct from “new unexplained swelling.”
7. **Known aortic aneurysm / Marfan / similar**, with sudden chest / back / abdominal pain — rare, but a gym Valsalva is a bad place to discover it.

“Recent surgery” is already on the red-flag list. That makes the empty restriction lists for recent shoulder / knee surgery even harder to defend: the app already believes surgery is dangerous enough to halt a session, then builds the next session as if nothing is restricted.

---

## Risk ranking

| Failure | Severity | What to do first |
|---|---|---|
| Empty lists for recent surgery | Highest | Temporary near-total block on the operated region until date + procedure exist |
| Empty list for pregnancy | High | Supine + Valsalva + overhead / floor-lift rules |
| Empty list for hypertension | High | Intensity / Valsalva cap, not exercise deletion |
| Sudden severe headache treated as “skip this lift” | High | Move to red-flag / same-day care |
| Bench tagged to flexion-intolerant back | Medium | Delete the tag |
| All squat patterns banned for anterior knee pain | Medium (over-restrict) | Depth limits |
| Hack squat / leg press on the “you might die under the bar” list | Low | Demote |

**Single rule of thumb for the builder:** block the *direction of load* the tissue cannot currently tolerate; do not block the muscle group. Flexion-intolerant back ≠ no squat. Impingement ≠ no press. Anterior knee pain ≠ no knee bend. Recent repair ≠ “user can override later.” The last one is the only place the refusal should stay absolute.

---

## Sources

Grouped by topic. These are the primary references used for the mark-up above.

### Low back / flexion intolerance

1. Dynamic Chiropractic. *Flexion-Intolerant Lower Back Pain (Part 2): Exercise Rehab.* https://dynamicchiropractic.com/article/56894-flexion-intolerant-lower-back-pain-part-2-exercise-rehab
2. Rhodes RE, et al. Arthritis, osteoporosis, and low back pain: evidence-based clinical risk assessment for physical activity and exercise clearance. *Can Fam Physician* / PMC. https://pmc.ncbi.nlm.nih.gov/articles/PMC3264014/
3. Procter S. *Modified Gym Program for Flexion-Sensitive Low Back Pain (Disc Herniation pattern).* Physio Steve. https://physiosteve.com/articles/modified-gym-program-for-flexion-sensitive-low-back-pain-disc-herniation
4. Spine-Health / Cooper G. *Exercises to Avoid with Lumbar Spinal Stenosis.* https://www.spine-health.com/blog/exercises-avoid-lumbar-spinal-stenosis
5. Yashar J. *Herniated Disc Exercises to Avoid.* https://www.dryashar.com/article/herniated-disc-exercises-to-avoid
6. PhysioFit Bend. *Flexion-Intolerant Back Pain: How to Keep Training.* https://www.physiofitbend.com/physio-blog/flexion-intolerant-lower-back-pain-in-crossfitters/
7. McGill-derived recommendations summarised in: *Evidence-based risk assessment and recommendations for physical activity: arthritis, osteoporosis, and low back pain.* https://cdnsciencepub.com/doi/10.1139/h11-037
8. Warren E. *Exercises to Avoid With a Herniated Disc (And What to Do Instead).* https://mindfulmovementphysicaltherapies.com/exercises-to-avoid-with-herniated-disc/
9. Rusin J. *Barbell Good Mornings: Is The Risk Worth It?* https://drjohnrusin.com/barbell-good-mornings-is-the-risk-worth-it/

### Shoulder impingement

10. Muscle Technics. *Shoulder Impingement Exercises* (movements commonly removed vs modified). https://muscletechnics.com/en/blog/shoulder-impingement-exercises
11. Hospital for Special Surgery. *Shoulder Internal Impingement Non-operative Guidelines* (PDF). https://www.hss.edu/globalassets/files/rehab/guidelines/HSSRehabilitationClinicalGuidelines-Shoulder-InternalImpingement-Non-operative.pdf
12. Hospital for Special Surgery. *Shoulder Impingement Non-operative Guidelines* (PDF). https://www.hss.edu/files/HSSRehabilitationClinicalGuidelines-Shoulder-Impingement-Non-operative.pdf
13. HSS. *Exercises for Shoulder Impingement.* https://www.hss.edu/health-library/move-better/exercises-shoulder-impingement
14. Professional Physical Therapy. *Weight Room Exercise Modifications for the Post-Rehabilitated Athletic Shoulder.* https://www.professionalpt.com/weight-room-exercise-modifications-for-the-post-rehabilitated-athletic-shoulder/
15. Fitness Pain Free. *Getting Out of Shoulder Pain and Back to Bench Press, Overhead Press and Olympic Lifts.* https://fitnesspainfree.com/2021/07/the-ultimate-guide-to-getting-out-of-shoulder-pain-and-back-to-bench-press-overhead-press-and-olympic-lifts/
16. LiftStrong. *Shoulder Impingement: Exercises to Fix and Prevent It.* https://www.liftstrong.com/articles/shoulder-impingement-exercises-to-fix-and-prevent-it
17. Chicago Spine and Sports. *How to Exercise Safely with Shoulder Impingement Syndrome.* https://www.chicagospineandsports.com/blog/how-to-exercise-when-managing-shoulder-impingement-syndrome-a-guide-to-safe-and-effective-workouts
18. UpToDate. *Rehabilitation principles and practice for shoulder impingement and related problems.* https://www.uptodate.com/contents/rehabilitation-principles-and-practice-for-shoulder-impingement-and-related-problems

### Anterior knee pain / patellofemoral pain

19. Gaitonde DY, Ericksen A, Robbins RC. Patellofemoral Pain Syndrome. *Am Fam Physician.* https://www.aafp.org/afp/2019/0115/p88
20. Willy RW, et al. Patellofemoral Pain Clinical Practice Guidelines. *JOSPT* (summary PDF commonly circulated). https://hoosierpt.com/files/pdf/PatellofemoralPain.pdf
21. Squat University. *How to Fix Patellar Compressive Syndrome.* https://squatuniversity.com/2017/07/07/how-to-fix-patellar-compressive-syndrome/
22. PMC. *Optimizing Rehabilitation and Return to Sport in Athletes With Anterior Knee Pain Using a Biomechanical Perspective.* https://pmc.ncbi.nlm.nih.gov/articles/PMC8811520/
23. PMC. *Current Concepts and Treatment of Patellofemoral Compressive Issues.* https://pmc.ncbi.nlm.nih.gov/articles/PMC5095942/
24. E3 Rehab. *Patellofemoral Pain.* https://e3rehab.com/pfp/
25. UpToDate. *Patellofemoral pain.* https://www.uptodate.com/contents/patellofemoral-pain
26. Jenkins T. *Kneecap Pain: Why It Hurts and How to Fix It.* https://drtomjenkins.com/kneecap-pain-patellofemoral-syndrome/

### Hypertension and resistance training

27. Edwards JJ, et al. An evidence-based guide to the efficacy and safety of isometric resistance training in hypertension. PMC. https://pmc.ncbi.nlm.nih.gov/articles/PMC10015931/
28. NSCA. Resistance training programming for individuals with hypertension. *Strength Cond J.* https://journals.lww.com/nsca-scj/fulltext/2009/02000/resistance_training_programming_for_individuals.7.aspx
29. NSCA. Hypertension and resistance training. *Strength Cond J.* https://journals.lww.com/nsca-scj/fulltext/2009/02000/hypertension_and_resistance_training.6.aspx
30. ACSM / Pescatello et al. Updated FITT prescription for hypertension. *Med Sci Sports Exerc.* https://journals.lww.com/acsm-msse/fulltext/2025/02000/the_role_of_exercise_blood_pressure_in.20.aspx
31. EAPC / Niebauer et al. Recommendations for participation in competitive sports of athletes with arterial hypertension. *Eur Heart J.* https://academic.oup.com/eurheartj/article/39/40/3664/5079290
32. Hackett DA, Chow CM. The Valsalva maneuver: its effect on intra-abdominal pressure and safety considerations during resistance exercise. *J Strength Cond Res.* https://journals.lww.com/nsca-jscr/fulltext/2013/08000/the_valsalva_maneuver__its_effect_on.39.aspx
33. Medical News Today. *Exercises to avoid with hypertension.* https://www.medicalnewstoday.com/articles/what-exercises-should-be-avoided-with-high-blood-pressure

### Pregnancy

34. American College of Obstetricians and Gynecologists. *Exercise During Pregnancy* (FAQ). https://www.acog.org/womens-health/faqs/exercise-during-pregnancy
35. ACOG Committee Opinion. Physical Activity and Exercise During Pregnancy and the Postpartum Period. *Obstet Gynecol.* https://journals.lww.com/greenjournal/fulltext/10.1097/aog.0000000000003773~physical-activity-and-exercise-during-pregnancy-and-the
36. Waters TR, et al. Provisional Recommended Weight Limits for Manual Lifting During Pregnancy. PMC. https://pmc.ncbi.nlm.nih.gov/articles/PMC4606868/
37. UpToDate. *Exercise during pregnancy and the postpartum period.* https://www.uptodate.com/contents/exercise-during-pregnancy-and-the-postpartum-period
38. NSCA. Training the pregnant client. *Strength Cond J.* https://journals.lww.com/nsca-scj/fulltext/2017/08000/special_populations__training_the_pregnant_client.10.aspx
39. Perinatology.com / OBRx. *Exercise in High-Risk Pregnancy* (practical strength defaults). https://www.perinatology.com/Exercise%20in%20Pregnancy.htm

### Recent shoulder surgery

40. Doctors Clinic Dubai summary of typical timelines (cuff vs labrum). *Shoulder Surgery Recovery: Exercises & Timeline.* https://doctorsclinicdubai.ae/blog/shoulder-surgery-recovery-exercises
41. Kinesio Rehab. *Shoulder Labrum Surgery: Recovery Timeline.* https://kinesiorehab.com.my/blog/shoulder-labrum-surgery-recovery/
42. CQ Upper Limb patient protocols (arthroplasty, biceps tenodesis, clavicle ORIF) — example surgeon-specific phased restrictions. https://www.cqupperlimb.com/education/rehabilitation/total-shoulder-arthroplasty/
43. University Hospitals of Derby and Burton NHS. *Shoulder instability / dislocation* post-op pacing. https://www.uhdb.nhs.uk/shoulder-instabilitydislocation/

### Recent knee surgery

44. ESSKA–AOSSM–AASPT. The Formal EU-US Meniscus Rehabilitation 2024 Consensus, Part I. PMC. https://pmc.ncbi.nlm.nih.gov/articles/PMC12099113/
45. Current Rehabilitation Principles Following Meniscus Repairs. PMC. https://pmc.ncbi.nlm.nih.gov/articles/PMC12283527/
46. Nahas S. *ACL Recovery Timeline.* https://samknee.co.uk/resources/acl-recovery
47. Nahas S. *Return to Sport After Knee Surgery.* https://samknee.co.uk/resources/return-to-sport
48. University Hospitals of Derby and Burton NHS. Knee ligament reconstruction protocols. https://www.uhdb.nhs.uk/knee-protocols/
49. APTA. Clinical practice guideline for physical therapist management of total knee arthroplasty (revision). https://academic.oup.com/ptj/article/106/7/pzag058/8742282

### Wrist pain

50. E3 Rehab. *Wrist Pain Rehab.* https://e3rehab.com/wrist-pain-rehab/
51. Fortitude and Freedom. *Wrist pain in lifters — front rack, cleans, and push-ups.* https://www.fortitudeandfreedom.com/articles/sports-rehab/wrist-pain-in-lifters-and-crossfit-front-rack-cleans-and-push-ups
52. Fitbod. *7 Reasons You Get Wrist Pain Front Squatting.* https://fitbod.me/blog/front-squat-wrist-pain/
53. ePainAssist. *Wrist Pain in Weightlifters: Dorsal Impingement vs Tendinopathy.* https://www.epainassist.com/sports-injuries/wrist-injuries/when-the-bar-bites-back-dorsal-wrist-impingement-vs-tendinopathy-in-weightlifters

### Elbow tendinopathy

54. E3 Rehab. *Golfer’s Elbow Rehab.* https://e3rehab.com/golfers-elbow-rehab/
55. Mass General. Rehabilitation protocol for medial / lateral epicondylitis (PDF). https://www.massgeneral.org/assets/MGH/pdf/orthopaedics/sports-medicine/physical-therapy/rehabilitation-protocol-for-medial-lateral-epicondylitis.pdf
56. Barbell Medicine. *Lateral Elbow Pain for Lifters.* https://www.barbellmedicine.com/blog/lateral-elbow-pain-for-lifters/
57. Elite HP. *Weightlifting With Tennis Elbow.* https://www.elitehp.com.au/blog/weightlifting-with-tennis-elbow-how-to-train-safely-and-recover
58. Hand Therapy Experts. *Exercises to Avoid for Tennis Elbow.* https://www.handtherapyexperts.com.au/exercises-to-avoid-with-tennis-elbow/

### Red flags, thunderclap headache, exertional events

59. Mayo Clinic. *Exercise headaches — symptoms and causes.* https://www.mayoclinic.org/diseases-conditions/exercise-headaches/symptoms-causes/syc-20372276
60. Mayo Clinic. *Subarachnoid hemorrhage.* https://www.mayoclinic.org/diseases-conditions/subarachnoid-hemorrhage/symptoms-causes/syc-20361009
61. Rosenberg H, et al. Assessment and investigation of thunderclap headache. *BMJ.* https://www.bmj.com/content/389/bmj-2024-083247
62. StatPearls. *Thunderclap Headache.* https://www.ncbi.nlm.nih.gov/books/NBK560629/
63. EM Cases. *Subarachnoid Hemorrhage Recognition, Workup & Diagnosis* (Ottawa SAH Rule). https://emergencymedicinecases.com/subarachnoid-hemorrhage-recognition-workup-diagnosis/
64. Medicine Today. *Thunderclap headache: when the risk of doing nothing is too high.* https://medicinetoday.com.au/mt/supplements/feature-article/thunderclap-headache-when-risk-doing-nothing-too-high
65. PMC case report. Internal carotid artery dissection following heavy-weight squats. https://pmc.ncbi.nlm.nih.gov/articles/PMC10214480/
66. Harvard Health. *Safe exercise: know the warning signs of pushing too hard.* https://www.health.harvard.edu/pain/safe-exercise-know-the-warning-signs-of-pushing-too-hard
67. Cleveland Clinic. *What To Know About Chest Pain After Exercise.* https://health.clevelandclinic.org/chest-pain-after-exercise
68. SELF / HSS commentary. *8 Workout Pains You Should Never Ignore.* https://www.self.com/story/workout-pains-never-ignore

---

## Caveats

- Primary sources above include society guidelines (ACOG, ACSM, AHA, AHA/EAPC sports cardiology, ESSKA–AOSSM meniscus consensus, JOSPT PFPS CPG) and secondary clinical reviews. Blog / physio-site pieces were used only where they restated standard load-modification practice, not as standalone evidence of harm.
- Absolute contraindications in the literature are few. Most “do not do this exercise” lists in gym culture are irritation maps, not proof that the movement worsens tissue damage.
- True intent and surgical detail are unverifiable in an app. That is why empty restriction lists for surgery, pregnancy, and hypertension are the wrong shape of protection.
