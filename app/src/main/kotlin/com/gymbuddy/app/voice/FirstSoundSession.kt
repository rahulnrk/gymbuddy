package com.gymbuddy.app.voice

import com.gymbuddy.domain.voice.Clock
import com.gymbuddy.domain.voice.VoiceInteractionConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * First Sound gym-listen loop.
 * Mic stays on while the screen is open (no audio focus).
 * Music ducks only for coach speak / a hotword hit, and stays down while the user talks.
 *
 * Experiment harness, not the contract-35 session runner. Always-on Scribe
 * costs roughly 19× the windowed design. Do not promote this screen into
 * the product path.
 */
class FirstSoundSession(
    private val tts: ElevenLabsTts,
    private val listen: DuckListen,
    private val clock: Clock,
    private val ducker: TtsDucker,
    private val scope: CoroutineScope,
    private val holdMs: Long = VoiceInteractionConstants.DUCK_HOLD_MS,
) {
    var observer: SpeakObserver? = null
    private val leaving = AtomicBoolean(false)
    private val ducked = AtomicBoolean(false)
    private val originMs = AtomicReference(0L)
    private val playbackEndedAt = AtomicReference(0L)
    private val lastSpeechAt = AtomicReference<Long?>(null)
    private val lastSpoken = AtomicReference(ElevenLabsConstants.FIRST_SOUND_LINE)
    private val pendingHeard = AtomicReference<String?>(null)
    private val pendingHotword = AtomicReference<String?>(null)
    private val replies = AtomicInteger(0)
    private var holdJob: Job? = null

    init {
        tts.observer = SpeakObserver { line ->
            observer?.onLine(line)
            when {
                line.startsWith("Finished after") -> onPlaybackFinished()
                line.startsWith("Cancelled after") || line.startsWith("Failed:") -> onAborted()
            }
        }
        listen.listener = object : DuckListenListener {
            override fun onVad(speech: Boolean) {
                tts.diagnostics.vadSpeech = speech
                if (speech) {
                    emit(ListenStatus.SPEECH)
                } else {
                    lastSpeechAt.set(clock.now())
                    emit(ListenStatus.SILENCE)
                }
            }

            override fun onPartial(text: String) {
                noteHeard(text, final = false)
            }

            override fun onFinal(text: String) {
                noteHeard(text, final = true)
            }

            override fun onError(message: String) {
                emit(message)
            }
        }
    }

    fun arm() {
        leaving.set(false)
        tts.diagnostics.duckHoldMs = holdMs
        listen.start()
        emit(ListenStatus.ARMED)
    }

    fun speak(text: String = ElevenLabsConstants.FIRST_SOUND_LINE, lang: String = "en") {
        leaving.set(false)
        replies.set(0)
        lastSpeechAt.set(null)
        playbackEndedAt.set(0L)
        pendingHeard.set(null)
        pendingHotword.set(null)
        lastSpoken.set(text)
        originMs.set(clock.now())
        holdJob?.cancel()
        tts.diagnostics.duckHoldMs = holdMs
        listen.start()
        ducked.set(true)
        emit(ListenStatus.LISTENING)
        tts.speak(text, lang)
    }

    fun cancel() {
        holdJob?.cancel()
        tts.stop()
        restoreDuckKeepListen()
    }

    fun release() {
        leaving.set(true)
        holdJob?.cancel()
        tts.stop()
        ducker.restore()
        ducked.set(false)
        listen.stop()
    }

    private fun noteHeard(text: String, final: Boolean) {
        val hit = Hotword.match(text)
        tts.diagnostics.lastHeard = text
        if (hit != null) {
            tts.diagnostics.hotword = hit
            emit(ListenStatus.hotword(hit))
        }
        emit(ListenStatus.heard(text))
        if (!final) return
        if (isCoachEcho(text, lastSpoken.get().orEmpty())) return
        if (!ducked.get()) {
            if (hit != null) openFromHotword(text, hit)
            return
        }
        if (tts.speaking) {
            pendingHeard.set(text)
            pendingHotword.set(hit)
            return
        }
        maybeReply(text, hit)
    }

    private fun openFromHotword(heard: String, hit: String) {
        if (leaving.get()) return
        ducked.set(true)
        originMs.set(clock.now())
        playbackEndedAt.set(0L)
        lastSpeechAt.set(clock.now())
        replies.set(0)
        holdJob?.cancel()
        ducker.duck()
        emit(ListenStatus.LISTENING)
        maybeReply(heard, hit)
    }

    private fun maybeReply(heard: String, hotword: String?) {
        if (leaving.get()) return
        if (replies.get() >= 8) return
        replies.incrementAndGet()
        val reply = if (hotword != null) {
            "I heard you. Coach is listening."
        } else {
            val clipped = heard.trim().take(80)
            "I heard: $clipped"
        }
        lastSpoken.set(reply)
        emit(ListenStatus.reply(reply))
        tts.speak(reply, "en")
    }

    private fun onPlaybackFinished() {
        if (leaving.get()) return
        playbackEndedAt.set(clock.now())
        val queued = pendingHeard.getAndSet(null)
        if (queued != null && replies.get() < 8) {
            maybeReply(queued, pendingHotword.getAndSet(null))
            return
        }
        emit(ListenStatus.DUCK_HOLDING)
        holdJob?.cancel()
        holdJob = scope.launch { waitHoldThenRestore() }
    }

    private fun onAborted() {
        if (leaving.get()) return
        holdJob?.cancel()
        restoreDuckKeepListen()
    }

    private suspend fun waitHoldThenRestore() {
        while (!leaving.get()) {
            val ended = playbackEndedAt.get()
            val hold = DuckHold.shouldHold(
                nowMs = clock.now(),
                holdMs = holdMs,
                userSpeaking = listen.userSpeaking || tts.speaking,
                lastSpeechAtMs = lastSpeechAt.get(),
                playbackEndedAtMs = ended,
            )
            if (!hold) break
            delay(50)
        }
        if (leaving.get()) return
        restoreDuckKeepListen()
    }

    private fun restoreDuckKeepListen() {
        ducker.restore()
        ducked.set(false)
        emit(ListenStatus.DUCK_RESTORED)
        emit(ListenStatus.ARMED)
    }

    private fun emit(line: String) {
        tts.diagnostics.record((clock.now() - originMs.get()).coerceAtLeast(0), line)
        observer?.onLine(line)
    }
}
