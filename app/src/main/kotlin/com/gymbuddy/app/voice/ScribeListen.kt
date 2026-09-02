package com.gymbuddy.app.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.gymbuddy.app.voice.ElevenLabsConstants.STT_SAMPLE_RATE_HZ
import com.gymbuddy.domain.voice.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Always-on listen that does not take audio focus.
 * AudioRecord (phone mic) + ElevenLabs Scribe realtime. Not SpeechRecognizer.
 */
class ScribeListen(
    private val credentials: VoiceCredentials,
    private val clock: Clock,
    private val transport: TtsTransport,
    private val scope: CoroutineScope,
) : DuckListen {
    private val running = AtomicBoolean(false)
    private val socketOpen = AtomicBoolean(false)
    private var loopJob: Job? = null
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    override var userSpeaking: Boolean = false
        private set
    override var listener: DuckListenListener? = null

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        loopJob = scope.launch { connectLoop() }
    }

    override fun stop() {
        running.set(false)
        userSpeaking = false
        loopJob?.cancel()
        loopJob = null
        stopRecorder()
        transport.close()
        socketOpen.set(false)
    }

    private suspend fun connectLoop() {
        while (running.get()) {
            val token = runCatching { credentials.issue(VoiceScope.STT) }.getOrNull()
            if (token == null || token.value.isBlank() || token.expiresAtMs <= clock.now()) {
                listener?.onError(ListenStatus.error("No API key for listen."))
                running.set(false)
                return
            }
            val closed = AtomicBoolean(false)
            transport.open(
                url = ElevenLabsConstants.sttUrl(),
                headers = mapOf("xi-api-key" to token.value),
                listener = object : TtsTransport.Listener {
                    override fun onOpen() {
                        socketOpen.set(true)
                        startRecorder()
                    }

                    override fun onMessage(text: String) {
                        handleMessage(text)
                    }

                    override fun onFailure(error: Throwable, httpCode: Int?) {
                        socketOpen.set(false)
                        stopRecorder()
                        val named = when (httpCode) {
                            401, 403 -> "Key rejected by ElevenLabs."
                            429 -> "Out of credits or rate limited."
                            else -> error.message ?: "connection dropped"
                        }
                        listener?.onError(ListenStatus.error(named))
                        closed.set(true)
                    }

                    override fun onClosed() {
                        socketOpen.set(false)
                        stopRecorder()
                        closed.set(true)
                    }
                },
            )
            while (running.get() && !closed.get()) {
                delay(50)
            }
            stopRecorder()
            transport.close()
            if (running.get()) delay(400)
        }
    }

    private fun handleMessage(raw: String) {
        when (val event = ScribeMessages.parse(raw)) {
            is ScribeEvent.Partial -> {
                if (event.text.isBlank()) return
                userSpeaking = true
                listener?.onVad(true)
                listener?.onPartial(event.text)
            }
            is ScribeEvent.Committed -> {
                userSpeaking = false
                listener?.onVad(false)
                if (event.text.isNotBlank()) listener?.onFinal(event.text)
            }
            is ScribeEvent.Error -> {
                listener?.onError(ListenStatus.error(event.detail.ifBlank { event.kind }))
                if (event.kind == "session_time_limit_exceeded" || event.kind == "auth_error") {
                    transport.close()
                }
            }
            is ScribeEvent.Started, ScribeEvent.Other -> Unit
        }
    }

    private fun startRecorder() {
        stopRecorder()
        val min = AudioRecord.getMinBufferSize(
            STT_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min == AudioRecord.ERROR || min == AudioRecord.ERROR_BAD_VALUE) {
            listener?.onError(ListenStatus.error("mic buffer unavailable"))
            return
        }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                STT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min.coerceAtLeast(STT_SAMPLE_RATE_HZ),
            )
        } catch (_: SecurityException) {
            listener?.onError(ListenStatus.MIC_DENIED)
            running.set(false)
            return
        } catch (e: Exception) {
            listener?.onError(ListenStatus.error(e.javaClass.simpleName))
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            listener?.onError(ListenStatus.error("mic failed to start"))
            return
        }
        rec.startRecording()
        recorder = rec
        val chunkBytes = (STT_SAMPLE_RATE_HZ / 10) * 2
        recordThread = thread(name = "scribe-mic", isDaemon = true) {
            val buf = ByteArray(chunkBytes)
            while (running.get() && socketOpen.get()) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val b64 = Base64.getEncoder().encodeToString(buf.copyOf(n))
                transport.send(
                    buildJsonObject {
                        put("message_type", "input_audio_chunk")
                        put("audio_base_64", b64)
                        put("commit", false)
                        put("sample_rate", STT_SAMPLE_RATE_HZ)
                    }.toString(),
                )
            }
        }
    }

    private fun stopRecorder() {
        val rec = recorder
        recorder = null
        recordThread = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
    }
}
