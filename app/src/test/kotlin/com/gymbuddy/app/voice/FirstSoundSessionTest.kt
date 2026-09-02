package com.gymbuddy.app.voice

import com.gymbuddy.domain.voice.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FirstSoundSessionTest {
    @Test
    fun listenStartsAndRestoreAfterZeroHold() {
        val events = mutableListOf<String>()
        val lines = mutableListOf<String>()
        val listen = FakeDuckListen()
        val ducker = RecordingDucker(events)
        val tts = sessionTts(events, ducker)
        val session = FirstSoundSession(
            tts = tts,
            listen = listen,
            clock = ManualClock(),
            ducker = ducker,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            holdMs = 0L,
        )
        session.observer = SpeakObserver { lines += it }
        session.speak("x", "en")
        Thread.sleep(80)
        assertTrue(listen.startCalls > 0, "listen should start with the duck")
        assertTrue(ListenStatus.LISTENING in lines, lines.toString())
        assertTrue("ducker.restore" in events, events.toString())
        assertTrue(ListenStatus.DUCK_RESTORED in lines, lines.toString())
        assertTrue(ListenStatus.ARMED in lines, lines.toString())
        assertEquals(0, listen.stopCalls)
        session.release()
        tts.release()
    }

    @Test
    fun idleHotwordDucksWithoutSpeakTap() {
        val events = mutableListOf<String>()
        val lines = mutableListOf<String>()
        val listen = FakeDuckListen()
        val ducker = RecordingDucker(events)
        val tts = sessionTts(events, ducker)
        val session = FirstSoundSession(
            tts = tts,
            listen = listen,
            clock = ManualClock(),
            ducker = ducker,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            holdMs = 0L,
        )
        session.observer = SpeakObserver { lines += it }
        session.arm()
        assertTrue(listen.startCalls > 0)
        assertFalse("ducker.duck" in events, events.toString())
        listen.listener?.onFinal("hey coach")
        Thread.sleep(80)
        assertTrue("ducker.duck" in events, events.toString())
        assertTrue(lines.any { it == ListenStatus.hotword("hey coach") }, lines.toString())
        assertTrue(lines.any { it.startsWith("Reply:") }, lines.toString())
        session.release()
        tts.release()
    }

    @Test
    fun userSpeechDuringHoldKeepsDuck() {
        val events = mutableListOf<String>()
        val listen = FakeDuckListen()
        listen.userSpeaking = true
        val ducker = RecordingDucker(events)
        val tts = sessionTts(
            events,
            ducker,
            clock = WallClock(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val session = FirstSoundSession(
            tts = tts,
            listen = listen,
            clock = WallClock(),
            ducker = ducker,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            holdMs = 80L,
        )
        session.speak("x", "en")
        Thread.sleep(200)
        assertFalse("ducker.restore" in events, events.toString())
        listen.userSpeaking = false
        Thread.sleep(200)
        assertTrue("ducker.restore" in events, events.toString())
        session.release()
        tts.release()
    }

    @Test
    fun heyCoachFinalEmitsHotwordAndReply() {
        val events = mutableListOf<String>()
        val lines = mutableListOf<String>()
        val listen = FakeDuckListen()
        val ducker = RecordingDucker(events)
        val tts = sessionTts(events, ducker)
        val session = FirstSoundSession(
            tts = tts,
            listen = listen,
            clock = ManualClock(),
            ducker = ducker,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            holdMs = 0L,
        )
        session.observer = SpeakObserver { lines += it }
        session.speak("x", "en")
        Thread.sleep(50)
        listen.listener?.onFinal("hey coach that was ten")
        Thread.sleep(80)
        assertTrue(lines.any { it == ListenStatus.hotword("hey coach") }, lines.toString())
        assertTrue(lines.any { it.startsWith("Reply:") }, lines.toString())
        session.release()
        tts.release()
    }

    private fun sessionTts(
        events: MutableList<String>,
        ducker: TtsDucker,
        clock: Clock = ManualClock(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ) = ElevenLabsTts(
        credentials = ImmediateCredentials(),
        clock = clock,
        transport = AudioTransport(),
        playback = RecordingPlayback(events),
        ducker = ducker,
        duckLeadMs = 0L,
        restoreOnFinish = false,
        scope = scope,
    )
}

private class FakeDuckListen : DuckListen {
    override var userSpeaking: Boolean = false
    override var listener: DuckListenListener? = null
    var startCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set

    override fun start() {
        startCalls += 1
    }

    override fun stop() {
        stopCalls += 1
    }
}
