# Testing contract (before implementation)

Tests map to feature contracts. An agent cannot mark a contract `implemented` with empty files named to match.

PR 0 ships goldens and schema tests. PR 1 implements validator behavior against **executable** PR 1 goldens (`input` + `given` + `expect`). `scripts/check_living_spec.py` rejects PR 1 files that are labels only.

Catch-up product §9 is two goldens: PR 1 facts vs PR 2 projector. See `docs/living-spec/boundaries/PR1_VS_PR2.md`.

## Categories

| Category | What it proves | First PR |
|---|---|---|
| Unit | Pure Kotlin types, schema gate, transition table | 0 |
| Contract | Golden JSON ↔ schema ↔ contract id | 0 |
| Event validation | Candidate → accept/clarify/drop | 1 |
| Extraction | Transcript → CandidateEventEnvelope after SafetyFirstTurn; no set numbers | 7 |
| State transition | Table in CONTRACT.md | 2 |
| Correction | Same setId, no duplicate row | 1 / 8 |
| Idempotency | (userId, eventId) and sttFinalHash+10s | 1 / 3 |
| Multilingual extraction fixtures | Telugu/Hindi/Tamil/code-mix; storage English | 1 / 7 (`llm_required`) |
| Noisy gym speech fixtures | Music bleed, rustle, numbers | 5 / 26 |
| Interruption fixtures | Barge-in < 200 ms on FakeClock; echo does not barge-in | 5 |
| Ambiguity fixtures | Clarify 50 vs 55; no-weight “Did 10.” | 1 |
| Safety fixtures | Decision table classifier before extract; LLM cannot override HIGH/CRITICAL | 6 |
| Offline | Process-kill restore from `event_log` fold; no snapshot blob; no audio queue | 3 |
| Invented history | Empty previousSessions → stance `none`; current weight not copied into history | 4 |
| Headless loop | shouldSpeak silence / +1 rep / SET_ACTIVE defer / COACHING_RESPONSE always (including silence). Duplicate STT hash+10s. Safety ≥ high may speak during a lift. recommend() is talk-only. | 9 |

## Named goldens shipped in PR 0 (≥20)

See `tests/golden/` and `docs/living-spec/testing/CASES.md`.

Kernel naming: `<nn>-<slug>.<stage>.<case-id>.json`.

Cases marked `llm_required` skip the English rules-fallback suite.

Projector and state machine tests use **no** LLM. Persistence tests use **no** LLM, STT, TTS, or UI.
