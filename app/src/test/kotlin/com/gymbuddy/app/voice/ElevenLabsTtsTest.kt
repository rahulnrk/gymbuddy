package com.gymbuddy.app.voice

import com.gymbuddy.domain.voice.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Base64

class ElevenLabsTtsTest {
    @Test
    fun cancelStopsPlaybackBeforeClosingTransport() {
        val events = mutableListOf<String>()
        val tts = engine(
            RecordingTransport(events),
            RecordingPlayback(events),
            duckLeadMs = 400L,
        )
        val handle = tts.speak("Nice. That's one more than last time.", "en")
        Thread.sleep(40)
        handle.cancel()
        val stop = events.indexOf("playback.stop")
        val close = events.indexOf("transport.close")
        assertTrue(stop >= 0, events.toString())
        assertTrue(close >= 0, events.toString())
        assertTrue(stop < close, events.toString())
        assertTrue(handle.cancelled)
        tts.release()
    }

    @Test
    fun missingKeyIsNamedWithoutConnecting() {
        val lines = mutableListOf<String>()
        val events = mutableListOf<String>()
        val tts = engine(
            RecordingTransport(events),
            RecordingPlayback(events),
            creds = EmptyCredentials(),
            ducker = RecordingDucker(events),
        )
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        assertEquals(listOf(SpeakStatus.NO_API_KEY), lines)
        assertFalse("ducker.duck" in events, events.toString())
        tts.release()
    }

    @Test
    fun http401IsKeyRejected() {
        val lines = mutableListOf<String>()
        val tts = engine(FailingTransport(401), RecordingPlayback(mutableListOf()))
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue(SpeakStatus.CONNECTING in lines, lines.toString())
        assertTrue(SpeakStatus.KEY_REJECTED in lines, lines.toString())
        tts.release()
    }

    @Test
    fun http429IsRateLimited() {
        val lines = mutableListOf<String>()
        val tts = engine(FailingTransport(429), RecordingPlayback(mutableListOf()))
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue(SpeakStatus.RATE_LIMITED in lines, lines.toString())
        tts.release()
    }

    @Test
    fun dropAfterOpenIsNamed() {
        val lines = mutableListOf<String>()
        val tts = engine(DropAfterOpenTransport(), RecordingPlayback(mutableListOf()))
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue(SpeakStatus.CONNECTED in lines, lines.toString())
        assertTrue(lines.any { it.startsWith("Failed: Connection dropped after") }, lines.toString())
        tts.release()
    }

    @Test
    fun connectedWithoutAudioIsNamed() {
        val lines = mutableListOf<String>()
        val tts = engine(SilentCloseTransport(), RecordingPlayback(mutableListOf()))
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue(SpeakStatus.NO_AUDIO in lines, lines.toString())
        tts.release()
    }

    @Test
    fun audioTrackFailureIsNamed() {
        val lines = mutableListOf<String>()
        val tts = engine(AudioTransport(), FailingPlayback())
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue(lines.any { it.startsWith("Failed: Audio output failed:") }, lines.toString())
        tts.release()
    }

    @Test
    fun firstAudioAndFinishedUseClock() {
        val clock = ManualClock()
        val lines = mutableListOf<String>()
        val tts = engine(AudioTransport(clock), RecordingPlayback(mutableListOf()), clock = clock)
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue(SpeakStatus.firstAudio(40) in lines, lines.toString())
        assertTrue(SpeakStatus.finished(40) in lines, lines.toString())
        tts.release()
    }

    @Test
    fun finishedDrainsPlaybackBeforeStop() {
        val events = mutableListOf<String>()
        val tts = engine(AudioTransport(), RecordingPlayback(events))
        tts.speak("x", "en")
        Thread.sleep(50)
        val drain = events.indexOf("playback.drain")
        val stop = events.indexOf("playback.stop")
        assertTrue(drain >= 0 && stop >= 0 && drain < stop, events.toString())
        tts.release()
    }

    @Test
    fun cancelDoesNotDrain() {
        val events = mutableListOf<String>()
        val tts = engine(RecordingTransport(events), RecordingPlayback(events))
        val handle = tts.speak("x", "en")
        handle.cancel()
        assertFalse("playback.drain" in events, events.toString())
        tts.release()
    }

    @Test
    fun diagnosticsNeverContainAKey() {
        val tts = engine(AudioTransport(), RecordingPlayback(mutableListOf()))
        tts.diagnostics.keyPresent = true
        tts.diagnostics.record(0, SpeakStatus.CONNECTING)
        val text = tts.diagnostics.clipboardText()
        assertTrue("keyPresent: true" in text)
        assertFalse("tok_test" in text)
        assertFalse("sk_" in text)
        tts.release()
    }

    @Test
    fun duckHappensBeforePlaybackStarts() {
        val events = mutableListOf<String>()
        val tts = engine(
            AudioTransport(),
            RecordingPlayback(events),
            ducker = RecordingDucker(events),
        )
        tts.speak("x", "en")
        Thread.sleep(50)
        val duck = events.indexOf("ducker.duck")
        val start = events.indexOf("playback.start")
        assertTrue(duck >= 0 && start >= 0 && duck < start, events.toString())
        assertTrue("ducker.restore" in events, events.toString())
        tts.release()
    }

    @Test
    fun duckLeadDelaysPlaybackUntilLeadElapsed() {
        val clock = WallClock()
        val events = mutableListOf<String>()
        val lines = mutableListOf<String>()
        val tts = engine(
            AudioTransport(),
            RecordingPlayback(events),
            clock = clock,
            ducker = RecordingDucker(events),
            duckLeadMs = 80L,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        tts.observer = SpeakObserver { lines += it }
        tts.speak("x", "en")
        Thread.sleep(400)
        val duck = events.indexOf("ducker.duck")
        val start = events.indexOf("playback.start")
        assertTrue(duck >= 0 && start > duck, "events=$events lines=$lines")
        val first = lines.firstOrNull { it.startsWith("First audio") }
        assertTrue(first != null, "events=$events lines=$lines")
        val n = first.substringAfter("at ").substringBefore(" ms").toLong()
        assertTrue(n >= 80, "events=$events lines=$lines")
        tts.release()
    }

    @Test
    fun restoreOnFinishFalseLeavesDuckUntilSessionRestores() {
        val events = mutableListOf<String>()
        val tts = engine(
            AudioTransport(),
            RecordingPlayback(events),
            ducker = RecordingDucker(events),
            restoreOnFinish = false,
        )
        tts.speak("x", "en")
        Thread.sleep(50)
        assertTrue("ducker.duck" in events, events.toString())
        assertTrue("playback.start" in events, events.toString())
        assertFalse("ducker.restore" in events, events.toString())
        tts.release()
    }

    @Test
    fun cancelDuringDuckLeadRestoresWithoutPlaying() {
        val events = mutableListOf<String>()
        val tts = engine(
            RecordingTransport(events),
            RecordingPlayback(events),
            ducker = RecordingDucker(events),
            duckLeadMs = 400L,
        )
        val handle = tts.speak("x", "en")
        Thread.sleep(40)
        handle.cancel()
        Thread.sleep(40)
        assertTrue("ducker.duck" in events, events.toString())
        assertTrue("ducker.restore" in events, events.toString())
        assertFalse("playback.start" in events, events.toString())
        assertFalse("playback.drain" in events, events.toString())
        tts.release()
    }

    private fun engine(
        transport: TtsTransport,
        playback: TtsPlayback,
        creds: VoiceCredentials = ImmediateCredentials(),
        clock: Clock = ManualClock(),
        ducker: TtsDucker = NoOpDucker,
        duckLeadMs: Long = 0L,
        restoreOnFinish: Boolean = true,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ) = ElevenLabsTts(
        credentials = creds,
        clock = clock,
        transport = transport,
        playback = playback,
        ducker = ducker,
        duckLeadMs = duckLeadMs,
        restoreOnFinish = restoreOnFinish,
        scope = scope,
    )
}

internal class ManualClock(var t: Long = 0) : Clock {
    override fun now(): Long = t
    fun advance(ms: Long) {
        t += ms
    }
}

internal class WallClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

internal class RecordingDucker(
    private val events: MutableList<String>,
) : TtsDucker {
    override fun duck() {
        events += "ducker.duck"
    }

    override fun restore() {
        events += "ducker.restore"
    }
}

internal class ImmediateCredentials : VoiceCredentials {
    override val keyPresent: Boolean = true
    override suspend fun issue(scope: VoiceScope): VoiceToken =
        VoiceToken(value = "tok_test", expiresAtMs = Long.MAX_VALUE)
}

private class EmptyCredentials : VoiceCredentials {
    override val keyPresent: Boolean = false
    override suspend fun issue(scope: VoiceScope): VoiceToken =
        error("must not issue")
}

private class RecordingTransport(
    private val events: MutableList<String>,
) : TtsTransport {
    override fun open(url: String, headers: Map<String, String>, listener: TtsTransport.Listener) {
        events += "transport.open"
        listener.onOpen()
    }

    override fun send(text: String) {
        events += "transport.send"
    }

    override fun close() {
        events += "transport.close"
    }
}

internal class RecordingPlayback(
    private val events: MutableList<String>,
) : TtsPlayback {
    override var playing: Boolean = false
        private set

    override fun start(sampleRateHz: Int) {
        playing = true
        events += "playback.start"
    }

    override fun write(pcm: ByteArray) {
        events += "playback.write"
    }

    override fun drain() {
        events += "playback.drain"
    }

    override fun stopAndFlush() {
        playing = false
        events += "playback.stop"
    }
}

private class FailingTransport(private val code: Int) : TtsTransport {
    override fun open(url: String, headers: Map<String, String>, listener: TtsTransport.Listener) {
        listener.onFailure(RuntimeException("http $code"), code)
    }

    override fun send(text: String) = Unit
    override fun close() = Unit
}

private class DropAfterOpenTransport : TtsTransport {
    override fun open(url: String, headers: Map<String, String>, listener: TtsTransport.Listener) {
        listener.onOpen()
        listener.onFailure(RuntimeException("dropped"), null)
    }

    override fun send(text: String) = Unit
    override fun close() = Unit
}

private class SilentCloseTransport : TtsTransport {
    override fun open(url: String, headers: Map<String, String>, listener: TtsTransport.Listener) {
        listener.onOpen()
        listener.onClosed()
    }

    override fun send(text: String) = Unit
    override fun close() = Unit
}

internal class AudioTransport(
    private val clock: ManualClock? = null,
) : TtsTransport {
    override fun open(url: String, headers: Map<String, String>, listener: TtsTransport.Listener) {
        listener.onOpen()
        clock?.advance(40)
        val pcm = Base64.getEncoder().encodeToString(byteArrayOf(0, 1, 0, 1))
        listener.onMessage("""{"audio":"$pcm"}""")
        listener.onMessage("""{"isFinal":true}""")
    }

    override fun send(text: String) = Unit
    override fun close() = Unit
}

private class FailingPlayback : TtsPlayback {
    override val playing: Boolean = false
    override fun start(sampleRateHz: Int) {
        throw AudioOutputFailedException("$sampleRateHz Hz PCM 16-bit mono")
    }

    override fun write(pcm: ByteArray) = Unit
    override fun drain() = Unit
    override fun stopAndFlush() = Unit
}
