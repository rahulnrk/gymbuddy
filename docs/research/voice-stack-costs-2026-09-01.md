# Voice and AI stack: what we actually need, what vendors offer, what it costs

Researched 2026-09-01. **Prices move — re-check before committing.**
Supersedes the first draft of this file, which under-researched Google and
ignored general Q&A entirely.

---

## 1. Understand the use case first

The app has **four different kinds of user speech**, and they have almost
nothing in common. Treating them as one "voice AI" problem is what makes these
products expensive.

| # | Kind | Example | Volume | Latency need | Works offline? |
|---|---|---|---|---|---|
| 1 | **Set logging** | "got ten", "sixty for eight" | Very high | Sub-second | **Must** |
| 2 | **Session control** | "machine's busy", "skip this", "how much last week?" | Medium | Sub-second | Should |
| 3 | **Coaching questions** | "why this exercise?", "is three sets enough?" | Low | A second or two | Nice to have |
| 4 | **Off-topic** | "is creatine worth it?", "what should I eat?" | Rare | Seconds is fine | No |

Kinds 1 and 2 are ~95% of everything said in a session, need no model at all,
and must survive a gym basement with no signal. Kind 4 is rare and is the only
one that ever needs the internet.

**The cost structure follows directly.** The expensive thing is not intelligence
— it is having the microphone open for an hour.

---

## 2. Speech-to-text

| Provider | Streaming | Notes |
|---|---|---|
| **Android `SpeechRecognizer`** | **free** | On-device, streaming, offline on modern handsets. Quality on Indian English and Telugu code-mixing is **unverified — must be tested**. |
| **Sarvam** | ₹30/h (≈ $0.36/h) | Built for Indian languages. Closest fit to our Telugu/English goldens. Confirm it has a true streaming endpoint. |
| **Deepgram Nova-3** | $0.462/h ($0.0077/min) | Best streaming latency; the default pick for voice agents. |
| **Google Cloud STT v2** | **$0.96/h** ($0.016/min) | Twice Deepgram, nearly three times Sarvam. Volume tiers only start at 500,000 min/month — irrelevant to us. **Poor value for the live path.** |
| Google STT v2 dynamic batch | **$0.18/h** ($0.003/min) | Cheapest batch anywhere. Worth using for anything with no latency budget. |
| Azure Speech | $1.00/h | Reported latency issues. |
| OpenAI Whisper API | $0.36/h | **Batch, not streaming.** Wrong shape for the live loop. |

**Findings:** Google is out for the live path on price and in for batch. Whisper
is out on architecture, not price. The real contest is Android on-device vs
Sarvam vs Deepgram, and the deciding factor is code-mixed accuracy, not cost.

Billing detail that matters: Google bills **silence and empty responses** if the
request succeeded, and retries can double-bill. Any always-on stream punishes you
twice.

## 3. Text-to-speech — cheaper than assumed

The coach speaks maybe **2,400 characters per session**, so **~29,000 characters
per user per month**.

| Google voice tier | Per 1M chars | Free per month | Users covered by the free tier alone |
|---|---|---|---|
| Standard | $4 | 4M | ~138 |
| **WaveNet** | **$4** | 1M | ~34 |
| Neural2 / Polyglot | $16 | 1M | ~34 |
| Chirp 3: HD | $30 | 1M | ~34 |
| Studio | $160 | 100K | ~3 |

WaveNet dropped to $4/1M in early 2026 and is noticeably better than Standard at
the same price — it is the obvious pick. Free tiers stack.

Others for reference: Amazon Polly Standard $4, Deepgram Aura-2 $30, Sarvam
Bulbul v3 ≈ $36 (Indian-accented voices), ElevenLabs $50–100.

**Conclusion: TTS is free until real scale, and about ₹10 per user per month
after.** Rahul was right to push on this. Do not spend engineering effort
optimising it.

**Cache the coach's fixed phrases anyway — for latency, not cost.** A cloud round
trip is 200–500 ms; between sets that reads as broken. A cached line is instant.

## 4. LLM — and whether we need RAG

`LLM_VS_APPLICATION.md` already says the model is interpreter and speaker, never
source of truth. Short in, short out.

| Model class | Input / output per 1M tokens |
|---|---|
| Llama 3.1 8B (DeepInfra) | $0.02 / $0.03 |
| gpt-oss-120b | $0.037 / $0.17 |
| DeepSeek V4 Flash | $0.09 / $0.18 |
| Sarvam 105B | ₹29 / ₹73 |
| Gemini Flash class | ~$0.30 / $2.50 |
| Claude Haiku 4.5 | $1.00 / $5.00 |

### We probably do not need RAG

Our knowledge base is **structured, not prose**: 61 exercises with typed fields,
~40 programming rules with provenance, templates, and an event log. For
"why am I doing this?" the answer is a **row lookup**, not a similarity search —
and the picker already returns `Reason.explanation` strings written for exactly
this.

Vector retrieval would be worse here: slower, fuzzier, and unable to *guarantee*
it returns the right exercise. Structured lookup is faster, free, offline,
deterministic and testable — the same argument that made the catalog
structured-first in the first place.

The research corpus in this folder is unstructured, but it is reference material
for **us**, not something users query.

**Recommendation: no vector database, no embedding pipeline, for now.** Revisit
only when there is unstructured content users actually ask about. If that day
comes, a corpus that fits in ~50K tokens is better prompt-stuffed or
keyword-searched than embedded.

## 5. Answering anything — the router, not a quota

Rahul's requirement: the coach should be able to answer general questions like
any assistant, but that use must be limited.

**Cost is the least of the reasons to limit it.** The real ones are safety
(a medical question must be referred, never answered), scope of practice, product
law (`AGENTS.md` forbids unsupported features — a general chatbot in Gym Mode is
a different product), and contract 09 (no talking during a set at all).

So limit by **routing**, not by a usage cap. Cost control falls out of it.

| Tier | Example | Handled by | Cost |
|---|---|---|---|
| 0 | "got ten" | on-device STT + `RulesFirstIntent` | free |
| 1 | "how much did I do last week?" | event-log query | free |
| 2 | "why this exercise?" | catalog lookup + stored `Reason` | free |
| 3 | "is three sets enough?" | rules layer + small LLM to phrase it | ~free |
| 4 | "is creatine worth it?" | small LLM, scope-of-practice guardrails | tiny |
| 5 | "what's the score?" | web search + LLM | the only real cost |
| — | "why does my knee click?" | **refer. never answer.** | free |

Two rules that make this work in a gym:

1. **Tiers 4 and 5 are deferred to the end of the session.** Mid-workout the
   coach says it will look that up after, and does. That is what a real trainer
   does, it respects contract 09, and it caps cost as a side effect.
2. **Tier 5 needs an explicit allowlist of what it will not touch** — anything
   medical routes to the referral path built in contract 30, never to a search.

### Web search prices, if it happens at all

| Provider | Per search | Per 1,000 | Free tier |
|---|---|---|---|
| **Serper** | $0.001 | **$1** | 2,500 |
| DataForSEO | $0.002 | $2 | $1 credit |
| Exa | $0.005 | $5 | 1,000/month |
| Tavily | $0.008 | $8 | 1,000/month |
| SerpAPI | $0.025 | $25 | 100/month |

Five searches per user per month at Serper is **half a paisa**. Web search is a
scope and safety problem, not a cost problem.

---

## 6. What it actually costs

Assuming 12 sessions/month, 60 minutes each.

| Line | Naive build | Properly built |
|---|---|---|
| STT | stream 12 h/month to Deepgram → **$5.54** | gated to real speech (~4 min/session), on-device first → **$0.04** |
| TTS | $0.72 | free tier, then ~$0.12 |
| LLM | pennies | pennies |
| Web search | — | ~$0.005 |
| **Total** | **≈ $6.30 (₹550)** | **under $0.20 (₹18)** |

₹550 per user per month is more than most Indian fitness apps charge in total.
₹18 is a business.

**The entire difference is one decision: do not stream the gym to the cloud.**
Send only what someone actually said to the coach, and let the phone handle the
easy 90%. Every other optimisation here is rounding.

## 7. Recommended stack

- **STT:** Android on-device as primary. Cloud escalation behind the existing
  port, chosen by a head-to-head on **our own Telugu/English goldens** — Sarvam
  vs Deepgram. Google v2 only for batch/post-session work at $0.18/h.
- **TTS:** Android on-device + cached fixed phrases for speed; **Google WaveNet**
  ($4/1M, 1M free) for anything dynamic.
- **LLM:** a small model (Llama 8B class) for extraction and phrasing. No
  frontier model. No fine-tuning.
- **Retrieval:** structured lookup over our own catalog. **No vector DB.**
- **Web search:** Serper, deferred to end of session, behind the tier router,
  with medical questions routed to referral instead.

## 8. Still the first thing to do

Measure, do not integrate. One spike in a real gym: end-to-end latency and word
error rate on actual set-logging phrases including code-mixed ones, on-device
versus each cloud candidate. `audio_routing_finding.md` is the precedent.

The vendor choice is cheap to change — vendors are behind ports and vendor SDKs
are banned from `:domain`. **On-device accuracy on Indian English is the open
question that decides the whole cost model**, and no amount of desk research
answers it.

---

## 9. CORRECTION (same day): this is free-form conversation, not commands

Section 1 above framed set logging as short fixed phrases ("got ten"). **That is
wrong and it contradicts our own product law.** `AGENTS.md`: *"Conversation is
first-class and unconstrained. Structured events are an internal reliability
boundary, not a command language the user must learn."* Forbidden item 11 is
"requiring command language".

The real product is a conversation: a greeting that knows it is Monday and a
pull day, "how were your legs after Thursday?", "I did fifteen minutes on the
cycle", chat about protein between sets, and a set logged as *"yeah that was
fifteen at eighty"* or *"eighty kilos, fifteen reps, felt easy"* — any phrasing
at all.

### What that changes

**The variety is in the phrasing, not in the meaning.** There are perhaps fifteen
things a person actually means during a session: logging a set, how they feel,
something hurts, a kit problem, a question, small talk. Infinite ways to say
each. So:

- **Speech-to-text is unaffected in principle** — it always had to handle natural
  speech. But free-form speech is *harder to transcribe* than short phrases, so
  the risk that on-device accuracy is not good enough goes **up**. That is now
  the single most important thing to measure.
- **Understanding needs a model on more turns.** `EnglishLastSetFallback` (a
  10.5 KB regex cascade) plus `RulesFirstIntent` will still catch the common
  shapes for free. Novel phrasing escalates to a model. This is the
  rules-first-with-fallback design the spec already describes — it just runs the
  fallback more often than section 5 assumed.
- **People talk more** in a conversational product. Assume ~10 minutes of speech
  per session rather than 4.

### Revised cost, being honest

Assume 40 model turns per session, small context (current exercise, last set, a
few facts) — about 1,500 input and 100 output tokens per turn, with the system
prompt prompt-cached.

| Line | Assumption | Per user per month |
|---|---|---|
| STT, cloud only | ~10 min speech/session × 12 = 2 h at Sarvam ₹30/h | ₹60 (≈ $0.72) |
| STT, on-device handles most capture | ~10% escalates | ₹6 (≈ $0.07) |
| LLM on Llama 8B class | 480 turns/month | ~$0.02 |
| LLM on Gemini Flash class | 480 turns/month | ~$0.34 |
| LLM on Claude Haiku class | 480 turns/month | ~$0.96 |
| TTS | free tier, then ~₹10 | ~₹10 |

**Fully conversational, cloud STT, mid-tier model: roughly ₹90–130 per user per
month.**
**Fully conversational, on-device STT carrying most capture, small model:
roughly ₹25–35.**

Both are workable. Neither is the ₹550 of streaming the whole session. The
earlier ₹18 figure assumed command-style logging and should be ignored.

### What does not change

- **Do not stream the gym.** Silence is still the majority of the hour and still
  free to ignore. The gate is the same; the amount of real speech behind it is
  larger.
- **Rules first is still right** — not to avoid conversation, but because the
  common shapes are free to catch and instant.
- **Keep the context small and cache the system prompt.** Model cost here is
  driven almost entirely by how much context is resent each turn, not by how
  clever the model is.
- **Conversational is not constant.** `ShouldSpeak` and its silence rules exist
  because a coach who talks after every set is exhausting. Free-form input does
  not mean chatty output.

### The measurement this makes urgent

The gym spike now has to test **natural phrasing**, not fixed phrases: the same
set logged five different ways, mid-sentence corrections, code-mixed
Telugu/English, and speech while breathing hard. If on-device transcription
cannot handle that, cloud STT moves from optional to required and the cost model
moves with it. That one result decides the architecture.

---

## 10. Cloud-only decision: who is actually good at Indian + global speech

On-device is dropped. The question is which cloud provider handles Indian
English and **Telugu mixed into English** without falling over, while still
being credible globally.

### The number that should worry us

From independent benchmark summaries: *"a multilingual model that posts 5% WER on
monolingual benchmarks routinely posts **15–20% WER on Spanish-English or
Hindi-English code-switched calls**."*

One word in five or six wrong is not a working set-logger. **Code-switching, not
accent and not noise, is the thing most likely to kill this product.** Every
vendor choice below is really a bet on this one metric.

### Speech-to-text shortlist

| Provider | Languages | Code-switching | Streaming latency | Price |
|---|---|---|---|---|
| **Speechmatics Ursa 2** | 55 | **Claims 35% better than nearest competitor**; 18% WER reduction over prior version | — | check |
| **Sarvam** | Indic focus | Built for Indian code-mixing | verify streaming | ₹30/h (≈$0.36) |
| **ElevenLabs Scribe v2 Realtime** | 90+ (93.5% across 30) | good | **sub-150 ms**, fastest | check |
| **Deepgram Flux Multilingual** | 10 with code-switching (Indic added to Nova-3's 30+) | yes | sub-300 ms | $0.462/h |
| Google Chirp | **125+**, widest coverage | not specifically claimed | streaming | $0.96/h |
| AssemblyAI Universal-3 Pro | 6 with code-switching (99 without) | narrow set | — | $0.37/h async |
| OpenAI Whisper | 50+ | not claimed | **batch only** | $0.36/h |

**Speechmatics was not on my earlier list and should have been** — its explicit
code-switching claim targets exactly our failure mode.

**Krisp VIVA** is worth knowing as a separate noise-suppression front end:
10–30% WER reduction on noisy audio. A gym is noisy audio.

### Who does what — an important clarification

- **Claude (Anthropic) has no speech-to-text product.** Text only. It can be the
  brain; it cannot be the ears.
- **Grok/xAI voice** is priced around $0.08/min ≈ $4.80/h — an order of magnitude
  above the specialists. Not a serious candidate for an always-listening app.
- **OpenAI** offers Whisper (batch) and a separate Realtime API for streaming.
- **Gemini can take audio natively**, collapsing STT and understanding into one
  call: Gemini Flash Live is ~$0.023/min combined (≈$1.38/h). Simpler
  architecture, roughly 3× the cost of a separate STT, and it couples us to one
  vendor for two jobs. Worth a bench, not an obvious default.

### The brain

Any of the majors is fine — this is short-in, short-out interpretation, not
frontier reasoning. Gemini 3.5 Flash-Lite ($0.30 / $2.50), Claude Haiku
($1 / $5), GPT mini class. Pick on cost, latency and safety behaviour, and keep
it behind the port so it can be swapped.

### The voice

Google WaveNet at $4/1M with 1M free per month still wins on value. Sarvam
Bulbul if Indian-accented voice tests better with users. ElevenLabs only if the
voice becomes part of the brand.

## 11. Off-topic questions: answered between sets, with a safety layer

Revised: rest between sets is 1–3 minutes of dead time and is the natural place
for this — contract 09 forbids talking *during a set*, not during rest. Do not
defer to end of session.

Two constraints:

1. **Hand back cleanly** — the answer must not quietly stretch a 90-second rest
   into four minutes. Answer, then "ready when you are."
2. **Guardrails.** Nothing abusive, nothing harmful, nothing that helps someone
   hurt themselves or anyone else. Three layers, in order:
   - **Our own routing first**: anything medical goes to the contract 30 referral
     path and never to a model or a search. `DefaultSafetyClassifier` already
     exists for the in-session case.
   - **The provider's safety layer**: Anthropic, OpenAI and Google all ship
     strong refusal behaviour by default. This is a real reason to prefer a major
     provider over the cheapest open model for anything user-facing and open-ended.
   - **Our own refusal copy**, so a refusal sounds like our coach and not a
     generic model error.

Note the tension worth watching: the cheapest models (Llama 8B class at
$0.02/$0.03) are fine for *extraction*, where the input is a gym utterance and
the output is structured. They are the wrong choice for *open-ended user
questions*, where safety behaviour matters. **Two different models for two
different jobs is both cheaper and safer than one compromise model.**
