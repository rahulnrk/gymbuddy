package com.gymbuddy.domain.safety

import com.gymbuddy.domain.SafetyAction
import com.gymbuddy.domain.SafetySeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSafetyClassifierTest {
    private val c = DefaultSafetyClassifier()

    @Test
    fun chestPainIsCritical() {
        listOf("my chest hurts", "chest pain", "I have chest pain.", "pain in my chest").forEach { u ->
            val r = c.classify(u)
            assertTrue(r.fired, u)
            assertEquals(SafetySeverity.CRITICAL, r.severity, u)
            assertEquals("chest_pain", r.category, u)
            assertEquals(SafetyAction.STOP_RECOMMEND, r.action, u)
            assertTrue(r.pause, u)
            assertFalse(r.allowSetCompleted, u)
        }
    }

    @Test
    fun chestPainWithSetAllowsSetAndPauses() {
        val r = c.classify("I got 10 but my chest hurts")
        assertEquals(SafetySeverity.CRITICAL, r.severity)
        assertTrue(r.allowSetCompleted)
        assertTrue(r.pause)
    }

    @Test
    fun chestFlyOccupiedIsNotSafety() {
        val r = c.classify("That chest fly machine is occupied.")
        assertFalse(r.fired)
        assertNull(r.severity)
        assertTrue(r.allowSetCompleted)
    }

    @Test
    fun chestPressWithoutPainIsNotChestPain() {
        val r = c.classify("switch to chest press")
        assertFalse(r.fired)
    }

    @Test
    fun faintAndDizzyCritical() {
        assertEquals(SafetySeverity.CRITICAL, c.classify("I feel faint").severity)
        assertEquals(SafetySeverity.CRITICAL, c.classify("dizzy, going to pass out").severity)
        assertEquals("dizziness", c.classify("I feel dizzy").category)
    }

    @Test
    fun cannotBreatheIsCriticalNotHardBreathing() {
        val bad = c.classify("I can't breathe")
        assertEquals(SafetySeverity.CRITICAL, bad.severity)
        assertEquals("severe_sob", bad.category)
        val afterSet = c.classify("I'm just breathing hard after a set")
        assertFalse(afterSet.fired)
    }

    @Test
    fun sharpPainHigh() {
        val r = c.classify("sharp pain in my shoulder")
        assertEquals(SafetySeverity.HIGH, r.severity)
        assertEquals(SafetyAction.PAUSE, r.action)
        assertTrue(r.pause)
        val tore = c.classify("I think I tore something")
        assertEquals(SafetySeverity.HIGH, tore.severity)
        assertEquals("sharp_pain", tore.category)
        val injury = c.classify("I tore it")
        assertEquals("injury", injury.category)
    }

    @Test
    fun mediumDiscomfort() {
        val r = c.classify("My shoulder feels a little weird on this.")
        assertEquals(SafetySeverity.MEDIUM, r.severity)
        assertEquals(SafetyAction.ADVISE, r.action)
        assertFalse(r.pause)
        assertTrue(r.allowSetCompleted)
        val set = c.classify("I got 10 but my shoulder feels weird")
        assertEquals(SafetySeverity.MEDIUM, set.severity)
        assertTrue(set.allowSetCompleted)
    }

    @Test
    fun teluguShoulderFeelIsMediumNotChestPain() {
        val r = c.classify("Naku chest kanna shoulder lo ekkuva feel avutundi.")
        assertTrue(r.fired)
        assertEquals(SafetySeverity.MEDIUM, r.severity)
        assertEquals("discomfort_mild", r.category)
        assertNotEquals("chest_pain", r.category)
    }

    @Test
    fun multilingualDocumentedPhrases() {
        val sharp = c.classify("Naku shoulder lo sharp pain vastundi.")
        assertEquals(SafetySeverity.HIGH, sharp.severity)
        val chestTe = c.classify("Chest lo pain undi.")
        assertEquals(SafetySeverity.CRITICAL, chestTe.severity)
        val dizzy = c.classify("Thoda dizzy ga undi.")
        assertEquals(SafetySeverity.CRITICAL, dizzy.severity)
        val dard = c.classify("shoulder me dard hai")
        assertEquals(SafetySeverity.MEDIUM, dard.severity)
    }

    @Test
    fun negationNotSafety() {
        listOf("No chest pain.", "I'm not dizzy.", "My shoulder is fine.").forEach { u ->
            val r = c.classify(u)
            assertFalse(r.fired, u)
            assertNull(r.severity, u)
        }
    }

    @Test
    fun naturalSpeechNotCommands() {
        assertFalse(c.classify("Bro, this is heavy.").fired)
        assertFalse(c.classify("How much protein should I have?").fired)
        assertFalse(c.classify("Why do I feel this in my shoulder?").fired)
        val weird = c.classify("Bro my shoulder feels weird when I do this.")
        assertEquals(SafetySeverity.MEDIUM, weird.severity)
    }

    @Test
    fun deterministic() {
        val a = c.classify("I have chest pain.")
        val b = c.classify("I have chest pain.")
        assertEquals(a, b)
    }

    @Test
    fun noDiagnosisInTemplates() {
        val speech = SafetyResponsePolicy.directive(c.classify("chest pain"))
        val t = speech.text.orEmpty().lowercase()
        listOf("heart attack", "cardiac", "rotator cuff", "torn", "you are having", "you have a").forEach {
            assertFalse(it in t, it)
        }
        assertFalse("push through" in t)
        assertTrue("stop" in t)
        assertTrue("medical" in t || "professional" in t)
    }
}
