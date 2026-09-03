package com.gymbuddy.domain.voice

import com.gymbuddy.domain.voice.fake.FakeClock
import com.gymbuddy.domain.voice.fake.FakeStt
import com.gymbuddy.domain.voice.fake.FakeWakeWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListenGateTest {
    private val silence = VadObservation(speech = false)
    private val speech = VadObservation(speech = true, usable = true)
    private val none: Window = Window.None

    @Test
    fun silenceNeverRunsWakeWordOrCloud() {
        val hit = WakeWordObservation(detected = true)
        val decision = listenDecision(silence, hit, none)
        assertIs<ListenDecision.VadOnly>(decision)
        assertFalse(mayRunWakeWord(decision))
        assertFalse(mayOpenCloud(decision))

        val wake = FakeWakeWord()
        val stt = FakeStt(FakeClock())
        applyListenDecision(decision, wake, stt)
        assertFalse(wake.started)
        assertFalse(stt.recognizing)
    }

    @Test
    fun speechWithoutWindowRunsWakeWordNotCloud() {
        val decision = listenDecision(speech, wake = null, none)
        assertIs<ListenDecision.VadOnly>(decision)
        assertFalse(mayRunWakeWord(decision))
        assertFalse(mayOpenCloud(decision))
        assertFalse(VoiceInteractionConstants.WAKE_WORD_PATH_LIVE)

        val wake = FakeWakeWord()
        val stt = FakeStt(FakeClock())
        applyListenDecision(decision, wake, stt)
        assertFalse(wake.started)
        assertFalse(stt.recognizing)
    }

    @Test
    fun wakeWordOnSpeechOpensCloud() {
        val decision = listenDecision(speech, WakeWordObservation(detected = true), none)
        assertIs<ListenDecision.VadOnly>(decision)
        assertFalse(mayOpenCloud(decision))
        assertFalse(mayRunWakeWord(decision))

        val wake = FakeWakeWord().also { it.start() }
        val stt = FakeStt(FakeClock())
        applyListenDecision(decision, wake, stt)
        assertFalse(stt.recognizing)
    }

    @Test
    fun coachWindowOpensCloudWithoutWakeWord() {
        val window = windowFor(UtteranceKind.SHORT_QUESTION)
        val decision = listenDecision(silence, wake = null, window)
        val open = assertIs<ListenDecision.OpenCloud>(decision)
        assertEquals(CloudReason.COACH_WINDOW, open.via)
        assertTrue(mayOpenCloud(decision))
        assertFalse(mayRunWakeWord(decision))

        val reminder = listenDecision(silence, wake = null, windowFor(UtteranceKind.STATEMENT))
        assertEquals(CloudReason.COACH_WINDOW, assertIs<ListenDecision.OpenCloud>(reminder).via)
    }

    @Test
    fun earbudAndOnScreenWorkWithoutWakeWord() {
        listOf(EntryPoint.WAKE_WORD, EntryPoint.EARBUD, EntryPoint.ON_SCREEN).forEach { entry ->
            assertIs<Window.None>(windowFor(entry))
            val decision = listenDecision(silence, wake = null, windowFor(entry))
            assertFalse(mayOpenCloud(decision), entry.name)
        }
        val emergency = listenDecision(silence, wake = null, windowFor(EntryPoint.EMERGENCY))
        val open = assertIs<ListenDecision.OpenCloud>(emergency)
        assertEquals(CloudReason.EMERGENCY, open.via)
        assertTrue(entryWorksWithoutWakeWord(EntryPoint.EMERGENCY))
        assertFalse(entryWorksWithoutWakeWord(EntryPoint.WAKE_WORD))
        assertTrue(entryWorksWithoutWakeWord(EntryPoint.EARBUD))
        assertTrue(entryWorksWithoutWakeWord(EntryPoint.ON_SCREEN))
    }

    @Test
    fun musicEchoUnknownUsableFallsBackToOnScreenNotEarbud() {
        val music = VadObservation(
            speech = true,
            echoFromMusic = true,
            usable = null,
        )
        val pretended = listenDecision(music, WakeWordObservation(detected = true), none)
        val fallback = assertIs<ListenDecision.FallBackToOnScreen>(pretended)
        assertEquals(FALLBACK_ON_SCREEN_WHY, fallback.why)
        assertEquals(EntryPoint.ON_SCREEN, fallbackWhenUnusable())
        assertFalse(mayRunWakeWord(pretended))
        assertFalse(mayOpenCloud(pretended))
        assertTrue("screen" in fallback.why.lowercase())
        assertFalse("earbud" in fallback.why.lowercase(), fallback.why)
        assertFalse('_' in fallback.why)

        val wake = FakeWakeWord().also { it.start() }
        val stt = FakeStt(FakeClock())
        stt.startRecognition()
        applyListenDecision(pretended, wake, stt)
        assertFalse(wake.started)
        assertFalse(stt.recognizing)
    }

    @Test
    fun musicEchoWithMeasuredUsableSpeechRunsWakeWord() {
        val musicButUsable = VadObservation(
            speech = true,
            echoFromMusic = true,
            usable = true,
        )
        val decision = listenDecision(musicButUsable, wake = null, none)
        assertIs<ListenDecision.VadOnly>(decision)
        assertFalse(mayRunWakeWord(decision))
    }

    @Test
    fun ttsEchoClosesTheGateUntilAecIsMeasured() {
        val tts = VadObservation(speech = true, echoFromTts = true)
        assertIs<ListenDecision.VadOnly>(listenDecision(tts, wake = null, none))
    }

    @Test
    fun openCloudStartsRecognitionOnceUntilAbort() {
        val wake = FakeWakeWord()
        val stt = FakeStt(FakeClock())
        val decision = ListenDecision.OpenCloud(CloudReason.COACH_WINDOW)
        repeat(8) { applyListenDecision(decision, wake, stt) }
        assertEquals(1, stt.startRecognitionCalls)
        assertTrue(stt.recognizing)
        stt.abort()
        assertFalse(stt.recognizing)
        applyListenDecision(decision, wake, stt)
        assertEquals(2, stt.startRecognitionCalls)
        assertTrue(stt.recognizing)
    }

    @Test
    fun vadOnlyAbortsOnceAndNotIfNeverStarted() {
        val wake = FakeWakeWord()
        val neverStarted = FakeStt(FakeClock())
        repeat(8) { applyListenDecision(ListenDecision.VadOnly, wake, neverStarted) }
        assertEquals(0, neverStarted.abortCalls)
        assertEquals(0, neverStarted.startRecognitionCalls)

        val stt = FakeStt(FakeClock())
        stt.startRecognition()
        repeat(8) { applyListenDecision(ListenDecision.VadOnly, wake, stt) }
        assertEquals(1, stt.abortCalls)
        assertFalse(stt.recognizing)
    }

    @Test
    fun oneWindowIsOneRecognitionSessionAcrossFinals() {
        val wake = FakeWakeWord()
        val stt = FakeStt(FakeClock())
        val decision = ListenDecision.OpenCloud(CloudReason.COACH_WINDOW)
        applyListenDecision(decision, wake, stt)
        assertEquals(1, stt.startRecognitionCalls)
        stt.emitFinal("got 10")
        assertTrue(stt.recognizing, "a final does not end the session")
        applyListenDecision(decision, wake, stt)
        assertEquals(1, stt.startRecognitionCalls)
        stt.abort()
        applyListenDecision(decision, wake, stt)
        assertEquals(2, stt.startRecognitionCalls)
    }

    @Test
    fun unusableMicFallsBackEvenWithoutMusic() {
        val plates = VadObservation(speech = true, usable = false)
        assertIs<ListenDecision.FallBackToOnScreen>(listenDecision(plates, wake = null, none))
    }

    @Test
    fun domainHasNoWakeWordVendor() {
        val cwd = java.io.File("").absoluteFile
        val voice = listOf(
            java.io.File(cwd, "src/main/kotlin/com/gymbuddy/domain/voice"),
            java.io.File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/voice"),
        ).first { it.isDirectory }
        val text = voice.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("porcupine", "openwakeword", "snowboy", "sensory", "picovoice").forEach { vendor ->
            assertFalse(vendor in text.lowercase(), vendor)
        }
    }
}
