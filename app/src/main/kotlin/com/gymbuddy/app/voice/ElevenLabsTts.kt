package com.gymbuddy.app.voice

import com.gymbuddy.app.voice.ElevenLabsConstants.RECONNECTS
import com.gymbuddy.app.voice.ElevenLabsConstants.SAMPLE_RATE_HZ
import com.gymbuddy.domain.voice.Clock
import com.gymbuddy.domain.voice.TtsHandle
import com.gymbuddy.domain.voice.TtsProvider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ElevenLabsTts(
    private val credentials: VoiceCredentials,
    private val clock: Clock,
    private val transport: TtsTransport,
    private val playback: TtsPlayback,
    private val ducker: TtsDucker = NoOpDucker,
    private val duckLeadMs: Long = ElevenLabsConstants.DUCK_LEAD_MS,
    private val restoreOnFinish: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TtsProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val seq = AtomicInteger(0)
    private var current: ElevenLabsTtsHandle? = null
    val diagnostics = SpeakDiagnostics()
    var observer: SpeakObserver? = null

    override val speaking: Boolean
        get() = playback.playing && current?.cancelled != true

    override fun speak(text: String, lang: String): TtsHandle {
        current?.cancel()
        val tapAt = clock.now()
        diagnostics.reset()
        diagnostics.keyPresent = credentials.keyPresent
        diagnostics.duckLeadMs = duckLeadMs
        diagnostics.duckHoldMs = ElevenLabsConstants.DUCK_HOLD_MS
        val handle = ElevenLabsTtsHandle(
            id = "el-${seq.incrementAndGet()}",
            startedAtMs = tapAt,
            onCancel = this::cancelCurrent,
        )
        current = handle
        if (!credentials.keyPresent) {
            fail(handle, tapAt, SpeakFailureKind.NO_API_KEY)
            return handle
        }
        emit(tapAt, SpeakStatus.CONNECTING)
        ducker.duck()
        handle.job = scope.launch {
            var attempt = 0
            while (attempt <= RECONNECTS && !handle.cancelled) {
                try {
                    streamOnce(handle, tapAt, text, lang)
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SpeakMappedException) {
                    if (handle.cancelled) return@launch
                    fail(handle, tapAt, e.kind, e.detail)
                    return@launch
                } catch (_: Exception) {
                    if (handle.cancelled) return@launch
                    attempt += 1
                    if (attempt > RECONNECTS) {
                        fail(handle, tapAt, SpeakFailureKind.DROPPED)
                        return@launch
                    }
                }
            }
        }
        return handle
    }

    override fun stop() {
        current?.cancel()
    }

    fun release() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun cancelCurrent(handle: ElevenLabsTtsHandle) {
        playback.stopAndFlush()
        ducker.restore()
        val elapsed = clock.now() - handle.startedAtMs
        handle.noteCancelled(clock.now())
        transport.close()
        emit(handle.startedAtMs, SpeakStatus.cancelled(elapsed.coerceAtLeast(0)))
    }

    private suspend fun streamOnce(
        handle: ElevenLabsTtsHandle,
        tapAt: Long,
        text: String,
        lang: String,
    ) {
        val token = credentials.issue(VoiceScope.TTS)
        if (handle.cancelled) return
        if (token.expiresAtMs <= clock.now()) {
            throw SpeakMappedException(SpeakFailureKind.KEY_REJECTED)
        }
        val opened = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val connected = AtomicBoolean(false)
        val gotAudio = AtomicBoolean(false)
        val mapped = AtomicReference<SpeakMappedException?>(null)
        val pending = ConcurrentLinkedQueue<ByteArray>()
        transport.open(
            url = ElevenLabsConstants.streamUrl(language = lang),
            headers = mapOf("xi-api-key" to token.value),
            listener = object : TtsTransport.Listener {
                override fun onOpen() {
                    connected.set(true)
                    emit(tapAt, SpeakStatus.CONNECTED)
                    opened.countDown()
                }

                override fun onMessage(text: String) {
                    if (handle.cancelled) return
                    classifyMessage(text)?.let { mapped.set(it) }
                    val audio = parseAudio(text)
                    if (audio != null && audio.isNotEmpty()) {
                        pending.add(audio)
                        gotAudio.set(true)
                    }
                    if (isFinal(text)) finished.countDown()
                }

                override fun onFailure(error: Throwable, httpCode: Int?) {
                    mapped.compareAndSet(null, mapHttp(httpCode, error, connected.get()))
                    opened.countDown()
                    finished.countDown()
                }

                override fun onClosed() {
                    finished.countDown()
                }
            },
        )
        if (!opened.await(15, TimeUnit.SECONDS)) {
            transport.close()
            throw SpeakMappedException(SpeakFailureKind.DROPPED)
        }
        mapped.get()?.let { throw it }
        if (handle.cancelled) return
        if (!connected.get()) {
            throw mapped.get() ?: SpeakMappedException(SpeakFailureKind.DROPPED)
        }
        transport.send(initMessage(token.value))
        transport.send(textMessage(text))
        transport.send(endMessage())
        waitDuckLead(handle, tapAt)
        if (handle.cancelled) return
        mapped.get()?.let { throw it }
        if (!gotAudio.get()) {
            awaitLatch(finished, handle, 30_000L)
        }
        if (handle.cancelled) return
        mapped.get()?.let { throw it }
        if (!gotAudio.get()) {
            throw SpeakMappedException(SpeakFailureKind.NO_AUDIO)
        }
        try {
            playback.start(SAMPLE_RATE_HZ)
        } catch (e: AudioOutputFailedException) {
            throw SpeakMappedException(SpeakFailureKind.AUDIO_OUTPUT, e.detail)
        }
        handle.speaking = true
        emit(tapAt, SpeakStatus.firstAudio(clock.now() - tapAt))
        pumpPending(handle, pending, finished, mapped)
        if (handle.cancelled) return
        mapped.get()?.let { throw it }
        transport.close()
        if (!handle.cancelled) playback.drain()
        if (handle.cancelled) return
        playback.stopAndFlush()
        handle.markFinished()
        if (restoreOnFinish) ducker.restore()
        emit(tapAt, SpeakStatus.finished(clock.now() - tapAt))
    }

    /** Wait remaining lead from tap. PCM is already buffering off this thread. */
    private suspend fun waitDuckLead(handle: ElevenLabsTtsHandle, tapAt: Long) {
        yield()
        if (duckLeadMs <= 0L) return
        while (!handle.cancelled) {
            val remaining = duckLeadMs - (clock.now() - tapAt)
            if (remaining <= 0L) return
            delay(minOf(50L, remaining))
        }
    }

    private suspend fun awaitLatch(
        latch: CountDownLatch,
        handle: ElevenLabsTtsHandle,
        timeoutMs: Long,
    ) {
        val startNs = System.nanoTime()
        while (!handle.cancelled && latch.count > 0L) {
            val waitedMs = (System.nanoTime() - startNs) / 1_000_000L
            if (waitedMs >= timeoutMs) return
            latch.await(10, TimeUnit.MILLISECONDS)
        }
    }

    private fun pumpPending(
        handle: ElevenLabsTtsHandle,
        pending: ConcurrentLinkedQueue<ByteArray>,
        finished: CountDownLatch,
        mapped: AtomicReference<SpeakMappedException?>,
    ) {
        while (!handle.cancelled) {
            if (mapped.get() != null) return
            val chunk = pending.poll()
            if (chunk != null) {
                playback.write(chunk)
                continue
            }
            if (latchDone(finished)) {
                val last = pending.poll()
                if (last != null) {
                    playback.write(last)
                    continue
                }
                return
            }
            finished.await(10, TimeUnit.MILLISECONDS)
        }
    }

    private fun latchDone(latch: CountDownLatch): Boolean = latch.count == 0L

    private fun emit(tapAt: Long, line: String) {
        diagnostics.record((clock.now() - tapAt).coerceAtLeast(0), line)
        observer?.onLine(line)
    }

    private fun fail(
        handle: ElevenLabsTtsHandle,
        tapAt: Long,
        kind: SpeakFailureKind,
        detail: String = "",
    ) {
        handle.markFailed()
        playback.stopAndFlush()
        ducker.restore()
        transport.close()
        emit(tapAt, kind.line((clock.now() - tapAt).coerceAtLeast(0), detail))
    }

    private fun mapHttp(code: Int?, error: Throwable, connected: Boolean): SpeakMappedException {
        when (code) {
            401, 403 -> return SpeakMappedException(SpeakFailureKind.KEY_REJECTED)
            429 -> return SpeakMappedException(SpeakFailureKind.RATE_LIMITED)
        }
        val msg = error.message.orEmpty().lowercase()
        if ("401" in msg || "unauthorized" in msg || "invalid api" in msg) {
            return SpeakMappedException(SpeakFailureKind.KEY_REJECTED)
        }
        if ("429" in msg || "rate" in msg || "quota" in msg) {
            return SpeakMappedException(SpeakFailureKind.RATE_LIMITED)
        }
        return if (connected) {
            SpeakMappedException(SpeakFailureKind.DROPPED)
        } else {
            SpeakMappedException(SpeakFailureKind.DROPPED)
        }
    }

    private fun classifyMessage(message: String): SpeakMappedException? {
        val obj = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return null
        val status = obj["status"]?.jsonPrimitive?.intOrNull
            ?: obj["code"]?.jsonPrimitive?.intOrNull
        when (status) {
            401, 403 -> return SpeakMappedException(SpeakFailureKind.KEY_REJECTED)
            429 -> return SpeakMappedException(SpeakFailureKind.RATE_LIMITED)
        }
        val err = obj["error"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
        if (err.contains("unauthorized") || err.contains("invalid api")) {
            return SpeakMappedException(SpeakFailureKind.KEY_REJECTED)
        }
        if (err.contains("quota") || err.contains("rate")) {
            return SpeakMappedException(SpeakFailureKind.RATE_LIMITED)
        }
        return null
    }

    private fun initMessage(apiKey: String): String = buildJsonObject {
        put("text", " ")
        put("xi_api_key", apiKey)
        put(
            "voice_settings",
            buildJsonObject {
                put("stability", 0.5)
                put("similarity_boost", 0.8)
                put("use_speaker_boost", false)
            },
        )
    }.toString()

    private fun textMessage(text: String): String = buildJsonObject {
        put("text", text)
        put("flush", true)
    }.toString()

    private fun endMessage(): String = buildJsonObject { put("text", "") }.toString()

    private fun parseAudio(message: String): ByteArray? {
        val audio = runCatching {
            json.parseToJsonElement(message).jsonObject["audio"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return null
        if (audio.isBlank()) return null
        return Base64.getDecoder().decode(audio)
    }

    private fun isFinal(message: String): Boolean =
        runCatching {
            json.parseToJsonElement(message).jsonObject["isFinal"]?.jsonPrimitive?.booleanOrNull
        }.getOrNull() == true
}

private class SpeakMappedException(
    val kind: SpeakFailureKind,
    val detail: String = "",
) : RuntimeException()

class ElevenLabsTtsHandle(
    override val id: String,
    override val startedAtMs: Long,
    private val onCancel: (ElevenLabsTtsHandle) -> Unit,
) : TtsHandle {
    override var cancelled: Boolean = false
        private set
    override var speaking: Boolean = false
        internal set
    override var cancelledAtMs: Long? = null
        private set
    var failed: Boolean = false
        private set
    var completed: Boolean = false
        private set
    internal var job: Job? = null

    override fun cancel() {
        if (cancelled || completed) return
        onCancel(this)
    }

    internal fun noteCancelled(atMs: Long) {
        cancelled = true
        completed = true
        cancelledAtMs = atMs
        speaking = false
        job?.cancel()
    }

    internal fun markFailed() {
        failed = true
        completed = true
        speaking = false
    }

    internal fun markFinished() {
        completed = true
        speaking = false
    }
}
