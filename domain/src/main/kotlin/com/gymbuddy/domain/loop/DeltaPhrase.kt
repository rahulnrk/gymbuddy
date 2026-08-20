package com.gymbuddy.domain.loop

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic phrasing for a useful delta.
 *
 * The sentence is generated from the [Claim]s, so it cannot disagree with them.
 * There is no fixed "one more than last time" string: +1, +2, -1 and a
 * weight-only change all produce different, accurate wording, and an absent
 * baseline produces no sentence at all.
 *
 * Replaces the previous hardcoded literal, which claimed "+1 rep" for every
 * useful delta including decreases (PRODUCT.md 33: never assert an
 * unsupported number).
 */
object DeltaPhrase {

    /** Below this a weight change is not worth mentioning (useful-delta rule). */
    const val MIN_KG = 0.5

    /** Null when there is nothing truthful to say. */
    fun phrase(reps: Claim.RepDelta?, weight: Claim.WeightDelta?): String? {
        val r = reps?.takeIf { it.delta != 0 }
        val w = weight?.takeIf { abs(it.deltaKg) >= MIN_KG }
        return when {
            r != null && w != null -> cap(repClause(r)) + ", and " + weightClause(w) + "."
            r != null -> "Nice. " + cap(repClause(r)) + "."
            w != null -> "Same reps, and " + weightClause(w) + "."
            else -> null
        }
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    private fun repClause(r: Claim.RepDelta): String {
        val n = abs(r.delta)
        val unit = if (n == 1) "rep" else "reps"
        val dir = if (r.delta > 0) "more" else "fewer"
        return count(n) + " " + dir + " " + unit + " than " + baseline(r.baseline)
    }

    private fun weightClause(w: Claim.WeightDelta): String {
        val dir = if (w.deltaKg > 0) "up" else "down"
        return "that's " + dir + " " + kg(abs(w.deltaKg)) + " on " + baseline(w.baseline)
    }

    private fun baseline(b: Baseline): String = when (b) {
        Baseline.PREVIOUS_SET_THIS_SESSION -> "your last set"
        Baseline.PREVIOUS_SESSION -> "last time"
    }

    private fun count(n: Int): String = when (n) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        10 -> "ten"
        else -> n.toString()
    }

    private fun kg(n: Double): String {
        val rounded = (n * 100).roundToInt() / 100.0
        val whole = rounded.roundToInt()
        val text = if (abs(rounded - whole) < 1e-9) whole.toString() else rounded.toString().trimEnd('0').trimEnd('.')
        return text + " " + (if (text == "1") "kilo" else "kilos")
    }
}
