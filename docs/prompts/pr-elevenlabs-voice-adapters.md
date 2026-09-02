# PR — ElevenLabs voice adapters (STT + TTS) in `:app`

## What this is

The first real vendor adapters. `:domain` already defines the ports
(`domain/.../voice/VoicePorts.kt`): `SttProvider`, `TtsProvider`, `TtsHandle`,
`VadProvider`, `WakeWordProvider`, `AudioFocusController`. Only fakes implement
them today. This PR adds ElevenLabs implementations in `:app`, behind those
ports, with nothing new in `:domain`.

## Hard rules

1. **No vendor SDK, no HTTP client, no ElevenLabs type ever enters `:domain`.**
   AGENTS.md forbidden item 3. If you find yourself editing a file under
   `domain/` other than to add a doc comment, stop and report.
2. **The API key must never ship inside the APK.** An Android app is
   decompilable; a key in `BuildConfig`, a resource, or an asset is a public
   key. See "Credentials" below — this is not negotiable and not a later task.
3. **No new numeric constant without a row in `DECISIONS_AND_THRESHOLDS.md`.**
   Timeouts, retry counts, buffer sizes, sample rates all count. Vendor-imposed
   values are `PLATFORM CONSTRAINT` and must cite the doc page. Values you chose
   are `MVP HEURISTIC`. Do not invent latency budgets.
4. **Unit tests do no network.** Both adapters take a transport interface;
   tests supply a scripted fake. If a test needs a live key, it does not belong
   in this PR.
5. Existing contracts 1–37 must still pass. Contract 35 (voice interaction)
   governs behaviour; this PR is plumbing under it, not a change to it.

## Credentials

Add to `:app` only:

```kotlin
interface VoiceCredentials {
    /** Short-lived token for a single STT or TTS connection. */
    suspend fun issue(scope: VoiceScope): VoiceToken
}
enum class VoiceScope { STT, TTS }
data class VoiceToken(val value: String, val expiresAtMs: Long)
```

Two implementations:

- `BackendVoiceCredentials` — calls gymbuddy's own backend, which holds the
  real ElevenLabs key server-side and returns a short-lived token. The backend
  does not exist yet. Write this class against the interface, with the base URL
  read from config, and leave a single `TODO(backend)` marker at the call site.
  This is the production path.
- `DebugVoiceCredentials` — reads a key from `local.properties` (gitignored),
  compiled into the **debug** variant only. Add a Gradle assertion that fails
  the **release** build if this class is on the release classpath or if the key
  property is present in a release build. Prove the guard works: the PR report
  must show the failing build output when the guard trips.

`local.properties` must already be in `.gitignore`. Verify; do not assume.
No key value appears in any committed file, test fixture, or log line. Redact
tokens in every log statement.

## TTS adapter

`ElevenLabsTts : TtsProvider`

- Streaming synthesis over the WebSocket endpoint, **Flash v2.5** as the model.
  Rationale: this is the conversational path, where time-to-first-audio decides
  whether the coach feels present. Higher-quality models are a config value, not
  a code change — put the model id behind a single constant with a DECISIONS row.
- `speak(text, lang)` returns a `TtsHandle` immediately; audio starts when the
  first chunk arrives. Never block the caller.
- **`TtsHandle.cancel()` must silence audio locally first.** Stop and flush the
  `AudioTrack`, set `cancelled`, record `cancelledAtMs` — *then* close the
  socket. Closing the network first and waiting for it means the coach keeps
  talking over the user, which is the exact failure contract 35 forbids. Write a
  test that asserts the playback stop happens before any transport close call.
- Chunk text at sentence boundaries when the caller passes multi-sentence
  copy, so a cancel lands cleanly rather than mid-word.
- Vendor failure, socket drop, or token expiry surfaces as
  `VoiceError.TtsUnavailable`. One reconnect attempt, then fail; the coach
  falling silent is better than the coach speaking late.
- `speaking` reflects actual playback, not "we sent a request".

## STT adapter

`ElevenLabsStt : SttProvider`

- **Scribe v2 Realtime** over WebSocket. `supportsStreaming = true`,
  `supportsLanguageId = true`, `supportsOffline = false`.
- `startRecognition(languages)` opens the socket and streams mic PCM. The
  socket opens only when the listening window opens — never held open across a
  set. Cost model in `docs/research/voice-stack-costs-2026-09-01.md` assumes
  ~4.5 minutes streamed per session; a leaked open socket breaks it. Add a test
  that a completed window closes the transport.
- `abort()` closes without waiting for a final.
- Emit `SttChunk` for both partials and the final. Populate `lang` and set
  `mixed` when the vendor reports more than one language in the utterance —
  the code-switching case is the whole reason `mixed` exists. If the vendor
  does not report it, leave `mixed = false` and say so in the report rather
  than guessing from the text.
- Compute `sttFinalHash` with the existing `SttFinalHash.of()` on finals only.
- Set `recordedAtMs` from the mic capture clock, not from response arrival.
- Malformed frames become `VoiceError.MalformedSttResponse` with the field
  name — never a silent empty transcript.

## Bench harness (this is the point of the PR, not a nice-to-have)

Add a debug-only screen alongside `AudioDiagnosticScreen` that:

- plays a recorded WAV from app storage through the STT adapter,
- writes transcript, per-chunk arrival times, end-of-turn latency, and reported
  language into the existing `DiagnosticReport` format,
- does the same for a second, swappable `SttProvider` implementation so a rival
  vendor can be dropped in without touching the screen.

The unresolved question is **Telugu/English code-switching accuracy**, and no
published benchmark answers it for our users. This harness is how we answer it
with our own gym recording. Do not add a second vendor in this PR — just leave
the seam.

## Not in this PR

Wake word, VAD, `AudioFocusController`, Text to Dialogue, voice cloning, any
`:domain` change, any UI outside the debug screen.

## Report back

- Files added, per module.
- The release-build guard output when the debug key is present.
- Every new constant with its DECISIONS class and, for vendor-imposed values,
  the doc URL.
- The cancel-ordering test and the socket-close test, quoted.
- Anything the ports could not express — say so plainly rather than widening a
  domain interface to fit the vendor.
