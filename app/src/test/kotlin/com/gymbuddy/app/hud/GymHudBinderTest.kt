package com.gymbuddy.app.hud

import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GymHudBinderTest {
    @Test
    fun idleHudIsGlanceableWithStartAndA11y() {
        val ui = GymHudBinder.bind(WorkoutSessionSnapshot())
        assertEquals("Ready", ui.exerciseName)
        assertEquals("no previous", ui.previousPerformance)
        assertEquals("—", ui.weightLabel)
        assertEquals("Listening", ui.pocket.label)
        assertEquals(LifecycleAction.START, ui.lifecycleAction)
        assertFalse(ui.numberPadEnabled)
        assertEquals(listOf("lifecycle", "numberPad", "rest"), ui.focusOrder)
        assertEquals("Type what you'd say", ui.controls.first { it.id == "numberPad" }.label)
        assertTrue(ui.controls.all { it.label.isNotBlank() && it.minTapTargetDp >= 44 })
        assertEquals(44, ui.minTapTargetDp)
        assertTrue(ui.highContrast)
        assertFalse(ui.caption.isChatThread)
        assertEquals(0, ui.caption.bubbleCount)
        assertTrue(ui.caption.minimized)
    }

    @Test
    fun voiceMapsToIndicatorAndUnavailableDoesNotPretend() {
        assertEquals(
            VoiceIndicator.LISTENING,
            GymHudBinder.voiceIndicator(VoiceTurnState.LISTENING, true),
        )
        assertEquals(
            VoiceIndicator.THINKING,
            GymHudBinder.voiceIndicator(VoiceTurnState.PROCESSING, true),
        )
        assertEquals(
            VoiceIndicator.SPEAKING,
            GymHudBinder.voiceIndicator(VoiceTurnState.AI_SPEAKING, true),
        )
        assertEquals(
            VoiceIndicator.SILENT,
            GymHudBinder.voiceIndicator(VoiceTurnState.SILENT, true),
        )
        assertEquals(
            VoiceIndicator.UNAVAILABLE,
            GymHudBinder.voiceIndicator(VoiceTurnState.LISTENING, false),
        )
    }

    @Test
    fun captionStubCannotBecomeAChatThread() {
        val ui = GymHudBinder.bind(
            WorkoutSessionSnapshot(),
            caption = CaptionStub(userLine = "Got 10.", aiLine = "Nice.", isChatThread = true, bubbleCount = 200),
        )
        assertFalse(ui.caption.isChatThread)
        assertEquals(0, ui.caption.bubbleCount)
        assertEquals("Got 10.", ui.caption.userLine)
    }
}
