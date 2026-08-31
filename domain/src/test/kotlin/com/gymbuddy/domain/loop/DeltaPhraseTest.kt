package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.context.EpistemicFact
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Part 8: phrasing must follow the actual delta.
 * The old implementation spoke "one more than last time" for every useful
 * delta, including decreases.
 */
class DeltaPhraseTest {

    private fun setFact(id: String, reps: Int, kg: Double) = EpistemicFact(
        id = id,
        kind = "set",
        stance = EpistemicStance.KNOWN,
        value = buildJsonObject { put("reps", reps); put("weightKg", kg) },
        sourceRowIds = listOf(id),
        speechAllowed = true,
    )

    private fun reps(current: Int, baseline: Int, b: Baseline = Baseline.PREVIOUS_SET_THIS_SESSION) =
        Claim.RepDelta(current, baseline, b, listOf(setFact("a", current, 60.0), setFact("b", baseline, 60.0)))

    private fun weight(current: Double, baseline: Double, b: Baseline = Baseline.PREVIOUS_SET_THIS_SESSION) =
        Claim.WeightDelta(current, baseline, b, listOf(setFact("a", 10, current), setFact("b", 10, baseline)))

    @Test
    fun plusOneRep() {
        val t = DeltaPhrase.phrase(reps(10, 9), null)!!
        assertEquals("Nice. One more rep than your last set.", t)
    }

    @Test
    fun plusTwoRepsSaysTwoAndPluralises() {
        val t = DeltaPhrase.phrase(reps(12, 10), null)!!
        assertEquals("Nice. Two more reps than your last set.", t)
        assertTrue("one more" !in t)
    }

    @Test
    fun minusOneRepDoesNotClaimAnIncrease() {
        val t = DeltaPhrase.phrase(reps(8, 9), null)!!
        assertEquals("Nice. One fewer rep than your last set.", t)
        assertTrue("more" !in t)
    }

    @Test
    fun minusTwoReps() {
        assertEquals("Nice. Two fewer reps than your last set.", DeltaPhrase.phrase(reps(8, 10), null))
    }

    @Test
    fun equalRepsAndWeightSaysNothing() {
        assertNull(DeltaPhrase.phrase(reps(10, 10), weight(60.0, 60.0)))
    }

    @Test
    fun weightOnlyChange() {
        val t = DeltaPhrase.phrase(null, weight(62.5, 60.0))!!
        assertEquals("Same reps, and that's up 2.5 kilos on your last set.", t)
    }

    @Test
    fun weightDropIsNotCalledAnIncrease() {
        val t = DeltaPhrase.phrase(null, weight(57.5, 60.0))!!
        assertTrue("down 2.5 kilos" in t, t)
        assertTrue("up" !in t, t)
    }

    @Test
    fun weightAndRepChangeTogether() {
        val t = DeltaPhrase.phrase(reps(10, 9), weight(62.5, 60.0))!!
        assertTrue("One more rep" in t, t)
        assertTrue("up 2.5 kilos" in t, t)
    }

    @Test
    fun subThresholdWeightChangeIsIgnored() {
        assertNull(DeltaPhrase.phrase(null, weight(60.2, 60.0)))
    }

    @Test
    fun missingBaselineProducesNoSentence() {
        assertNull(DeltaPhrase.phrase(null, null))
    }

    @Test
    fun crossSessionBaselineSaysLastTime() {
        val t = DeltaPhrase.phrase(reps(10, 9, Baseline.PREVIOUS_SESSION), null)!!
        assertEquals("Nice. One more rep than last time.", t)
    }
}
