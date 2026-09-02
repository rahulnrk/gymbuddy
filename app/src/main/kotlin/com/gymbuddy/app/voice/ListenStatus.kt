package com.gymbuddy.app.voice

/**
 * Exact listen lines on the First Sound screen. Timings are reported, never judged.
 */
object ListenStatus {
    const val LISTENING = "Listening."
    const val ARMED = "Music up. Say hey coach."
    const val SPEECH = "Speech detected."
    const val SILENCE = "User silent."
    const val MIC_DENIED =
        "Listen: mic permission denied. Grant it and tap Speak again."
    const val UNAVAILABLE = "Listen: speech recognizer unavailable on this device."
    const val DUCK_HOLDING = "Duck holding. Talk if you want."
    const val DUCK_RESTORED = "Duck restored."

    fun heard(text: String) = "Heard: $text"
    fun hotword(phrase: String) = "Hotword: $phrase"
    fun error(detail: String) = "Listen: $detail"
    fun reply(text: String) = "Reply: $text"
}

object Hotword {
    fun match(text: String): String? {
        val n = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return null
        if (n.contains("hey coach")) return "hey coach"
        if (Regex("\\bcoach\\b").containsMatchIn(n)) return "coach"
        return null
    }
}

object DuckHold {
    fun shouldHold(
        nowMs: Long,
        holdMs: Long,
        userSpeaking: Boolean,
        lastSpeechAtMs: Long?,
        playbackEndedAtMs: Long,
    ): Boolean {
        if (userSpeaking) return true
        val quietFrom = maxOf(playbackEndedAtMs, lastSpeechAtMs ?: playbackEndedAtMs)
        return nowMs - quietFrom < holdMs
    }
}

fun isCoachEcho(heard: String, spoken: String): Boolean {
    val h = heard.trim().lowercase().replace(Regex("\\s+"), " ")
    val s = spoken.trim().lowercase().replace(Regex("\\s+"), " ")
    if (h.isEmpty()) return true
    if (h == s) return true
    if (h.length >= 8 && s.contains(h)) return true
    if (s.length >= 8 && h.contains(s)) return true
    return false
}
