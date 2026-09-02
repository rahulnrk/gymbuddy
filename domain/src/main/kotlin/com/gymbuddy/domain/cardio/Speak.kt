package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.plan.PlannedSession

private val BANNED = listOf(
    "bpm", "watt", "watts", "km/h", "mph", "min/km", "m/s",
    "heart rate", "heartrate", "zone 2", "zone2", "pace",
)

fun speakWeek(week: List<PlannedSession>): String =
    week.joinToString("\n") { day ->
        val cardio = day.cardio?.let { speak(it) } ?: "no cardio"
        val note = day.notes.lastOrNull()?.let { "  ($it)" } ?: ""
        "${day.day}: $cardio$note"
    }

fun speak(p: CardioPrescription): String {
    val chunks = mutableListOf<String>()
    chunks += p.modality.name
    chunks += p.protocol.name
    chunks += "about ${p.durationMin} minutes"
    val structure = intervalShape(p.protocol)
    if (structure != null) chunks += structure
    chunks += p.effort.trim().trimEnd('.')
    val text = chunks.joinToString(". ") + "."
    assertNoInventedIntensity(text)
    p.why.forEach { assertNoInventedIntensity(it.explanation) }
    return text
}

private fun intervalShape(p: CardioProtocol): String? {
    val work = p.workSec ?: return null
    val recover = p.recoverSec ?: return null
    val rounds = p.rounds.firstOrNull() ?: return null
    return "$rounds rounds of ${spokenDuration(work)} work and ${spokenDuration(recover)} easy"
}

private fun spokenDuration(sec: Int): String = when {
    sec % 60 == 0 && sec / 60 == 1 -> "one minute"
    sec % 60 == 0 -> "${sec / 60} minutes"
    else -> "$sec seconds"
}

fun assertNoInventedIntensity(text: String) {
    val lower = text.lowercase()
    BANNED.forEach { token ->
        check(token !in lower) { "invented intensity '$token' in: $text" }
    }
}
