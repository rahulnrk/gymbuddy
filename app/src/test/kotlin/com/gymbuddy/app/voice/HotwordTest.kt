package com.gymbuddy.app.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotwordTest {
    @Test
    fun heyCoachAndCoachMatch() {
        assertEquals("hey coach", Hotword.match("Hey Coach"))
        assertEquals("hey coach", Hotword.match("  hey   coach, that was ten"))
        assertEquals("coach", Hotword.match("coach"))
        assertEquals("coach", Hotword.match("ok coach that was fifteen"))
    }

    @Test
    fun nonCoachDoesNotMatch() {
        assertNull(Hotword.match("that was fifteen at eighty"))
        assertNull(Hotword.match("coaching cues"))
        assertNull(Hotword.match(""))
    }

    @Test
    fun echoOfSpokenLineIsIgnored() {
        val line = ElevenLabsConstants.FIRST_SOUND_LINE
        assertTrue(isCoachEcho(line, line))
        assertTrue(isCoachEcho("nice. that's one more than last time.", line))
        assertFalse(isCoachEcho("that was fifteen", line))
    }
}

class DuckHoldTest {
    @Test
    fun holdForQuietWindowAfterPlayback() {
        assertTrue(
            DuckHold.shouldHold(
                nowMs = 1000,
                holdMs = 2500,
                userSpeaking = false,
                lastSpeechAtMs = null,
                playbackEndedAtMs = 0,
            ),
        )
        assertFalse(
            DuckHold.shouldHold(
                nowMs = 2500,
                holdMs = 2500,
                userSpeaking = false,
                lastSpeechAtMs = null,
                playbackEndedAtMs = 0,
            ),
        )
    }

    @Test
    fun userSpeechProlongsHold() {
        assertTrue(
            DuckHold.shouldHold(
                nowMs = 4000,
                holdMs = 2500,
                userSpeaking = true,
                lastSpeechAtMs = 3900,
                playbackEndedAtMs = 0,
            ),
        )
        assertTrue(
            DuckHold.shouldHold(
                nowMs = 5000,
                holdMs = 2500,
                userSpeaking = false,
                lastSpeechAtMs = 4000,
                playbackEndedAtMs = 0,
            ),
        )
        assertFalse(
            DuckHold.shouldHold(
                nowMs = 6500,
                holdMs = 2500,
                userSpeaking = false,
                lastSpeechAtMs = 4000,
                playbackEndedAtMs = 0,
            ),
        )
    }

    @Test
    fun speechDuringPlaybackDoesNotShortenHold() {
        assertTrue(
            DuckHold.shouldHold(
                nowMs = 1000,
                holdMs = 2500,
                userSpeaking = false,
                lastSpeechAtMs = 100,
                playbackEndedAtMs = 1000,
            ),
        )
    }

    @Test
    fun speakingPastSixtySecondsStillHolds() {
        assertTrue(
            DuckHold.shouldHold(
                nowMs = 60_000,
                holdMs = 2500,
                userSpeaking = true,
                lastSpeechAtMs = 59_000,
                playbackEndedAtMs = 1000,
            ),
        )
    }
}

class ScribeMessagesTest {
    @Test
    fun parsesPartialCommittedAndAuth() {
        val partial = ScribeMessages.parse("""{"message_type":"partial_transcript","text":"hey coa"}""")
        val committed = ScribeMessages.parse("""{"message_type":"committed_transcript","text":"hey coach"}""")
        val auth = ScribeMessages.parse("""{"message_type":"auth_error","error":"bad key"}""")
        assertTrue(partial is ScribeEvent.Partial && partial.text == "hey coa")
        assertTrue(committed is ScribeEvent.Committed && committed.text == "hey coach")
        assertTrue(auth is ScribeEvent.Error && auth.kind == "auth_error")
    }
}
