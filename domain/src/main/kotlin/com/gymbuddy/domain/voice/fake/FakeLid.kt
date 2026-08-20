package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.LidProvider
import com.gymbuddy.domain.voice.LidResult
import com.gymbuddy.domain.voice.VoiceError

/**
 * Deterministic LID for tests. Does not translate. Does not interpret meaning.
 */
class FakeLid(
    var unavailable: Boolean = false,
) : LidProvider {
    override fun detect(text: String): LidResult {
        if (unavailable) throw VoiceError.LidUnavailable()
        val t = text.lowercase()
        if (t.isBlank()) return LidResult("und", mixed = false, confidence = 0.0)
        val te = listOf("nenu", "naku", "chesa", "kaadu", "kanna", "ekkuva", "avutundi", "tho")
        val hi = listOf("hai", "kya", "nahi", "kitna", "karo")
        val ta = listOf("naan", "illa", "enna", "irukku")
        val hasTe = te.any { it in t }
        val hasHi = hi.any { it in t }
        val hasTa = ta.any { it in t }
        val hasEn = t.split(Regex("[^a-z]+")).any { it.length > 2 && it in ENGLISH }
        return when {
            hasTe && hasEn -> LidResult("te", mixed = true, confidence = 0.8)
            hasHi && hasEn -> LidResult("hi", mixed = true, confidence = 0.8)
            hasTa && hasEn -> LidResult("ta", mixed = true, confidence = 0.8)
            hasTe -> LidResult("te", mixed = false, confidence = 0.9)
            hasHi -> LidResult("hi", mixed = false, confidence = 0.9)
            hasTa -> LidResult("ta", mixed = false, confidence = 0.9)
            else -> LidResult("en", mixed = false, confidence = 0.7)
        }
    }

    private companion object {
        val ENGLISH = setOf(
            "the", "got", "this", "heavy", "what", "weight", "actually", "that",
            "was", "reps", "kilos", "eight", "bro", "wait", "last", "week",
            "chest", "shoulder", "feel", "more", "than",
        )
    }
}
