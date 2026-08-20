package com.gymbuddy.domain.voice

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.voice.fake.FakeAudioFocus
import com.gymbuddy.domain.voice.fake.FakeLid
import com.gymbuddy.domain.voice.fake.FakeStt
import com.gymbuddy.domain.voice.fake.FakeTts
import com.gymbuddy.domain.voice.fake.FakeVad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceTurnMachineTest {
    private fun machine(
        cancelDelayMs: Long = 0,
        duckSupported: Boolean = true,
        offline: Boolean = false,
    ): VoiceTurnMachine {
        val clock = FakeClock()
        return VoiceTurnMachine(
            clock = clock,
            vad = FakeVad(),
            stt = FakeStt(clock, supportsOffline = offline),
            tts = FakeTts(clock, cancelDelayMs = cancelDelayMs),
            lid = FakeLid(),
            focus = FakeAudioFocus(duckSupported = duckSupported),
        )
    }

    @Test
    fun idleToListeningToUserSpeakingToProcessingToAiSpeaking() {
        val m = machine()
        assertEquals(VoiceTurnState.SILENT, m.state)
        m.start()
        assertEquals(VoiceTurnState.LISTENING, m.state)
        m.onSpeechStart()
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
        m.onSpeechEnd()
        assertEquals(VoiceTurnState.PROCESSING, m.state)
        m.speak("Nice work.")
        assertEquals(VoiceTurnState.AI_SPEAKING, m.state)
        m.onTtsEnded()
        assertEquals(VoiceTurnState.LISTENING, m.state)
    }

    @Test
    fun aiSpeakingInterruptedThenUserSpeaking() {
        val m = machine()
        m.start()
        m.speak("Your next set is")
        m.onSpeechStart()
        assertTrue(VoiceTurnState.INTERRUPTED in m.trail)
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
        assertFalse(m.tts.speaking)
        assertTrue(m.tts.lastHandle!!.cancelled)
    }

    @Test
    fun userSpeakingToStopped() {
        val m = machine()
        m.start()
        m.onSpeechStart()
        m.stop()
        assertEquals(VoiceTurnState.STOPPED, m.state)
        assertFalse(m.vad.started)
    }

    @Test
    fun bargeInUnder200msOnFakeClock() {
        val m = machine(cancelDelayMs = 40)
        m.start()
        m.speak("Your next set is seventy")
        m.clock.advance(80)
        m.onSpeechStart()
        val latency = m.lastBargeInLatencyMs
        assertNotNull(latency)
        assertTrue(latency < 200, "latency=$latency")
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
        assertFalse(m.tts.speaking)
    }

    @Test
    fun bargeInEarlyAndLateTts() {
        val early = machine(cancelDelayMs = 10)
        early.start()
        early.speak("Hi")
        early.clock.advance(5)
        early.onSpeechStart()
        assertTrue(early.lastBargeInLatencyMs!! < 200)

        val late = machine(cancelDelayMs = 10)
        late.start()
        late.speak("A long coaching sentence about rest")
        late.clock.advance(5000)
        late.onSpeechStart()
        assertTrue(late.lastBargeInLatencyMs!! < 200)
        assertTrue(late.tts.lastHandle!!.cancelled)
    }

    @Test
    fun repeatedInterruption() {
        val m = machine()
        m.start()
        m.speak("One")
        m.onSpeechStart()
        m.onSpeechEnd()
        m.speak("Two")
        m.onSpeechStart()
        assertEquals(2, m.trail.count { it == VoiceTurnState.INTERRUPTED })
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
    }

    @Test
    fun interruptionWhenTtsAlreadyStoppedStartsUserTurn() {
        val m = machine()
        m.start()
        m.speak("Done")
        m.onTtsEnded()
        assertEquals(VoiceTurnState.LISTENING, m.state)
        m.onSpeechStart()
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
        assertNull(m.lastBargeInLatencyMs)
    }

    @Test
    fun bargeInFailsIfCancelThrows() {
        val clock = FakeClock()
        val tts = FakeTts(clock, interruptFails = true)
        val m = VoiceTurnMachine(clock, FakeVad(), FakeStt(clock), tts, FakeLid(), FakeAudioFocus())
        m.start()
        m.speak("Keep going")
        assertFails { m.onSpeechStart() }
    }

    @Test
    fun echoDoesNotBargeIn() {
        val m = machine()
        m.start()
        m.speak("Nice. That’s one more.")
        m.onSpeechStart(echoFromTts = true)
        assertEquals(VoiceTurnState.AI_SPEAKING, m.state)
        assertTrue(m.tts.speaking)
        assertTrue(m.lastEvidence.echoSuspected)
    }

    @Test
    fun partialAndFinalAreDistinct() {
        val m = machine()
        m.start()
        m.onSpeechStart()
        val p = m.onPartial("got")
        assertFalse(p.isFinal)
        assertNull(p.sttFinalHash)
        val f = m.onFinal("Got 10.")
        assertTrue(f.isFinal)
        assertNotNull(f.sttFinalHash)
        assertEquals(SttFinalHash.of("Got 10."), f.sttFinalHash)
        assertTrue(m.lastEvidence.isFinal)
        assertEquals("Got 10.", m.lastEvidence.transcript)
        assertEquals(1, m.stt.chunks.count { it.isFinal })
    }

    @Test
    fun sttConfidenceDurationHashLanguage() {
        val m = machine()
        m.start()
        m.onSpeechStart()
        val f = m.onFinal("Bro this is heavy.", confidence = 0.81, durationMs = 920.0, lang = "en")
        assertEquals(0.81, f.confidence)
        assertEquals(920.0, f.durationMs)
        assertEquals("en", f.lang)
        assertEquals(SttFinalHash.of("Bro this is heavy."), f.sttFinalHash)
        assertEquals(m.clock.now(), f.recordedAtMs)
    }

    @Test
    fun supportsOfflineFlag() {
        assertFalse(machine(offline = false).stt.supportsOffline)
        assertTrue(machine(offline = true).stt.supportsOffline)
    }

    @Test
    fun malformedSttThrows() {
        val m = machine()
        m.start()
        val err = assertFails { m.stt.emitMalformed("not-json") }
        assertTrue(err is VoiceError.MalformedSttResponse)
    }

    @Test
    fun vadOnsetEndOverlapSingleSpeaker() {
        val m = machine()
        m.start()
        m.onSpeechStart(talkerCount = 1, overlapping = false)
        assertEquals(1, m.lastEvidence.talkerCount)
        assertFalse(m.lastEvidence.overlappingVad)
        m.onSpeechStart(talkerCount = 2, overlapping = true)
        assertTrue(m.lastEvidence.overlappingVad)
        assertEquals(2, m.lastEvidence.talkerCount)
        m.onSpeechEnd()
        assertFalse(m.vad.last.speech)
    }

    @Test
    fun duckRestoreReleaseAndUnavailable() {
        val m = machine()
        m.start()
        m.speak("Cue")
        assertTrue(m.focus.ducked)
        assertTrue(m.focus.log.any { it.startsWith("duck:ai_speech") })
        m.onTtsEnded()
        assertTrue("restore" in m.focus.log)
        m.stop()
        assertTrue("release" in m.focus.log)

        val unsupported = machine(duckSupported = false)
        unsupported.start()
        unsupported.speak("Still speak")
        assertEquals(VoiceTurnState.AI_SPEAKING, unsupported.state)
        assertTrue("unsupported" in unsupported.focus.log)
        assertFalse(unsupported.focus.ducked)
    }

    @Test
    fun lidEnglishHindiTeluguTamilCodeMixUnknown() {
        val lid = FakeLid()
        assertEquals("en", lid.detect("Got 10.").lang)
        assertFalse(lid.detect("Got 10.").mixed)
        assertEquals("hi", lid.detect("kya hai").lang)
        assertEquals("te", lid.detect("naku chest").lang)
        assertEquals("ta", lid.detect("naan illa").lang)
        val mix = lid.detect("Nenu 60 kilos tho eight reps chesa.")
        assertEquals("te", mix.lang)
        assertTrue(mix.mixed)
        val hinglish = lid.detect("kya weight hai bro")
        assertTrue(hinglish.mixed)
        assertEquals("und", lid.detect("").lang)
        lid.unavailable = true
        assertFails { lid.detect("hi") }
    }

    @Test
    fun codeMixedTranscriptNotTranslated() {
        val m = machine()
        m.start()
        m.onSpeechStart()
        val original = "Naku chest kanna shoulder lo ekkuva feel avutundi."
        val f = m.onFinal(original)
        assertEquals(original, f.text)
        assertEquals(original, m.lastEvidence.transcript)
        assertTrue(f.mixed)
        assertEquals("te", f.lang)
    }

    @Test
    fun anyTranscriptIsAccepted() {
        val m = machine()
        m.start()
        listOf(
            "Got 10.",
            "Bro this is heavy.",
            "How much did I do last week?",
            "Wait, what weight?",
            "Actually make that 9.",
            "Why does my shoulder feel weird?",
            "Can we take more rest?",
            "Naku chest kanna shoulder lo ekkuva feel avutundi.",
        ).forEach { u ->
            m.onSpeechStart()
            val f = m.onFinal(u)
            assertEquals(u, f.text)
            m.onSpeechEnd()
        }
    }

    @Test
    fun voiceStateDoesNotTouchWorkoutState() {
        val m = machine()
        m.start()
        val workout = WorkoutSessionState.SET_ACTIVE
        m.onSpeechStart()
        assertEquals(WorkoutSessionState.SET_ACTIVE, workout)
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
        m.onSpeechEnd()
        m.speak("x")
        assertEquals(WorkoutSessionState.SET_ACTIVE, workout)
        assertEquals(VoiceTurnState.AI_SPEAKING, m.state)
        val rest = WorkoutSessionState.RESTING
        m.onSpeechStart()
        assertEquals(WorkoutSessionState.RESTING, rest)
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
    }

    @Test
    fun transportsCommitEvidenceDoesNotDecide() {
        val m = machine()
        m.audioRoute = AudioRoute.SPEAKERPHONE
        m.start()
        m.onSpeechStart(overlapping = true, talkerCount = 2)
        m.onFinal("got 10", durationMs = 100.0)
        val e = m.lastEvidence
        assertEquals(AudioRoute.SPEAKERPHONE, e.audioRoute)
        assertTrue(e.overlappingVad)
        assertEquals(2, e.talkerCount)
        assertEquals(100.0, e.durationMs)
        assertEquals("got 10", e.transcript)
    }

    @Test
    fun providerUnavailableErrorsSurface() {
        val clock = FakeClock()
        val stt = FakeStt(clock, unavailable = true)
        assertFails { stt.startRecognition() }
        val tts = FakeTts(clock, unavailable = true)
        assertFails { tts.speak("x", "en") }
        val vad = FakeVad(unavailable = true)
        assertFails { vad.start() }
        val focus = FakeAudioFocus(unavailable = true)
        assertFails { focus.requestDuck("ai_speech") }
    }

    @Test
    fun illegalTransitionRejected() {
        val m = machine()
        m.stop()
        assertEquals(VoiceTurnState.STOPPED, m.state)
        assertFails { m.speak("no") }
    }
}
