# Vendor requirements brief — STT, TTS, LLM, search

Written 2026-09-01 for Rahul to take to vendors. Everything a sales engineer or
a pricing page needs to answer, in one place.

---

## 1. What the product is

A voice-first AI gym companion for Android. The user starts a workout and then
just talks. The microphone is open for the whole session — roughly an hour. The
app greets them, asks how they are, logs their sets from natural speech, answers
questions between sets, and adapts the plan when something changes.

Two things make this unusual for a voice product:

- **It is not command-driven.** There is no wake word and no fixed phrasing. A
  set might be logged as *"got ten"*, *"that was fifteen at eighty"*,
  *"eighty kilos, fifteen reps, felt easy"*, or
  *"nenu 60 kilos tho eight reps chesa"*.
- **The users are Indian and code-switch mid-sentence.** Telugu and Hindi words
  inside English sentences, in a noisy gym, often while out of breath.

---

## 2. The four workloads — they have different requirements

| # | Workload | Share of traffic | Latency sensitivity |
|---|---|---|---|
| 1 | **Speech-to-text** | continuous while talking | Extreme |
| 2 | **Text-to-speech** | ~40 short lines/session | High |
| 3 | **LLM — understanding gym speech** | ~40 turns/session | High |
| 4 | **LLM — open questions + web search** | a few/session | Low |

---

## 3. Speech-to-text — the hard one

### Deal-breakers (disqualify immediately)
- **Batch-only.** Must be true streaming. Whisper's file-in/text-out API is the
  wrong shape regardless of price.
- **Cannot cancel a stream fast.** We need to stop and discard in-flight audio
  within ~200 ms when the user starts talking over the coach.
- **No Indian English support**, or English-only.
- **Cannot opt out of indefinite audio retention.** Our own rules forbid keeping
  raw audio; a vendor that requires it is out.

### Hard requirements
- Streaming with partial results.
- End-of-utterance detection in roughly 800–1200 ms, ideally tunable.
- Speech end to text available: **under 1500 ms** over cloud.
- **Code-switching within a single utterance** — Telugu/Hindi inside English.
  This is the single most important capability and the one most likely to fail.
- Natural spoken numbers: *"sixty for eight"*, *"one oh five"*,
  *"sixty-five kilos"*, *"a hundred and ten"*.
- Robust in noise: gym music, dropped plates, echo, other people talking nearby.
- Handles breathy, clipped speech from someone mid-effort.
- Android SDK or a plain WebSocket API we can call from Kotlin.

### Nice to have
- Word-level confidence scores (we gate commits on a confidence floor).
- Custom vocabulary or phrase hints (exercise names, "RDL", "pec deck").
- Speaker separation, so a bystander's voice does not log a set.
- India region / data residency.

### Shortlist to evaluate
Speechmatics (claims best-in-class code-switching), Sarvam (Indic specialist),
ElevenLabs Scribe (fastest end-of-turn), Deepgram (strong all-round), Google
Chirp (widest language coverage). Krisp or similar as a noise front-end.

---

## 4. Text-to-speech

### Deal-breakers
- **Cannot be cancelled mid-utterance.** Barge-in is core to the product; if the
  user speaks, the coach must stop immediately and cleanly.
- No streaming — waiting for a full audio file before playback starts.

### Hard requirements
- First audio byte in roughly 300 ms or less.
- Clean cancellation mid-sentence.
- An Indian English voice that does not sound foreign to the user.
- Licence permits **caching generated audio** — the coach repeats a bounded set
  of phrases and we want to store them, for speed rather than cost.

### Nice to have
- Telugu/Hindi voices, if the coach is ever to reply in kind.
- Control over pace and emphasis.

Note: TTS volume is small enough that price is close to irrelevant (see §6).
Choose on latency, cancellation and voice quality, not cost.

---

## 5. LLM — two different jobs, probably two different vendors

### 5a. Understanding gym speech (high volume, cheap)
- Input: a short transcript plus small context (current exercise, last set).
- Output: **constrained JSON** — a structured event or an intent label.
- Needs: strict schema adherence, low latency, low cost, tolerance of
  code-mixed input text.
- Does **not** need: frontier reasoning, long context, or web access.
- Prompt caching matters — the system prompt is identical every turn.

### 5b. Open questions (low volume, must be safe)
- "Is creatine worth it?", "what should I eat after this?", occasionally
  something entirely off-topic.
- Needs: strong built-in refusal behaviour, tool/web-search support, and
  sensible handling of health-adjacent questions.
- **Anything medical never reaches this path** — it routes to our referral
  flow. But the model must still behave well when something slips through.
- Latency is not critical; these are answered between sets.

### Web search
Only for 5b. A few queries per user per month. Serper, Exa, Tavily,
DataForSEO are all viable; this is not a cost problem.

---

## 6. Usage scale — the numbers to quote against

**Per user per month**, assuming 12 sessions of 60 minutes:

| Workload | Volume |
|---|---|
| STT — gated to actual speech (~10 min/session) | **120 minutes (2 hours)** |
| STT — if ungated, whole session streamed | 720 minutes (12 hours) |
| TTS | ~29,000 characters |
| LLM extraction | ~480 calls; ~720K input + 48K output tokens |
| LLM open questions | ~10 calls |
| Web search | ~5 queries |

**Ask every vendor for pricing at these three tiers:**

| | 100 users | 1,000 users | 10,000 users |
|---|---|---|---|
| **STT hours/month (gated)** | 200 | 2,000 | 20,000 |
| STT hours/month (ungated) | 1,200 | 12,000 | 120,000 |
| **TTS characters/month** | 2.9M | 29M | 290M |
| **LLM calls/month** | 48,000 | 480,000 | 4.8M |
| LLM input tokens/month | 72M | 720M | 7.2B |
| Web searches/month | 500 | 5,000 | 50,000 |

The gated STT row is the one that matters. Ungated is shown so you can see what
the architecture decision is worth — it is a 6× difference and it is the whole
cost story.

Reference points already gathered (list price, September 2026, re-check):
Sarvam ₹30/hour · Deepgram $0.462/hour · Google STT v2 $0.96/hour ·
Google STT batch $0.18/hour · Google TTS WaveNet $4/1M chars with 1M free/month ·
Serper $1/1,000 searches.

---

## 7. Questions to ask every vendor

1. Is this **true streaming**, and can I cancel an in-flight stream in under
   200 ms?
2. What is your word error rate on **Indian English**? And on **Hindi-English or
   Telugu-English code-switched** audio specifically? *(Expect the second number
   to be much worse — published benchmarks show 5% monolingual becoming 15–20%
   code-switched. A vendor who has not measured it has not solved it.)*
3. Do you **retain audio**? For how long? Can I opt out contractually?
4. Do you offer an **India region** or data residency?
5. What is the price at **200, 2,000 and 20,000 hours per month**? Where do
   volume tiers start?
6. Do you bill for **silence** or for failed/retried requests?
7. Is there an **Android SDK**, or is it a WebSocket I call myself?
8. What happens on a **network drop mid-utterance** — do I lose the whole
   utterance or the tail?
9. Free tier or startup credits?
10. For TTS: can I **cache and store** generated audio under your licence?

---

## 8. The test that actually decides it

Do not choose on marketing benchmarks — they are all monolingual and quiet.

**Record 20 minutes in a real gym.** Music on, plates dropping, people talking.
Log sets by speaking naturally: the same set five different ways, Telugu mixed
into English, mid-sentence self-corrections ("no, eight"), and at least a few
while genuinely out of breath.

Send that same file to every shortlisted vendor. Score two things:

- **Word error rate on the utterances that matter** — the numbers and the
  exercise names. Getting "felt easy" wrong costs nothing; getting "eighty" wrong
  costs everything.
- **End-to-end latency** from speech end to text.

Whoever gets your actual sentences right wins. That test costs almost nothing
and settles a question no amount of desk research can.

---

## 9. What is already decided

- **Cloud-first.** On-device is a later optimisation, not the starting
  architecture.
- **No RAG / no vector database.** Our knowledge base is structured rows, not
  prose; lookup beats retrieval.
- **Two models, not one** — a cheap one for extraction, a major one for open
  questions. Cheaper and safer than a single compromise.
- **Vendors sit behind ports** (`voice/VoicePorts.kt`, `ai/AiPorts.kt`) and
  vendor SDKs are banned from `:domain`. Switching later is genuinely cheap, so
  do not over-optimise this decision.
