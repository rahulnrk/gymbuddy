package com.gymbuddy.domain.voice

import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_CLOSED_ANSWER_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_HARD_CAP_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_OPEN_QUESTION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_SHORT_QUESTION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_SPEECH_EXTENSION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_USER_INITIATED_MS
import kotlin.math.max
import kotlin.math.min

enum class UtteranceKind {
    STATEMENT,
    CLOSED_ANSWER,
    SHORT_QUESTION,
    OPEN_QUESTION,
}

enum class EntryPoint {
    WAKE_WORD,
    EARBUD,
    ON_SCREEN,
}

sealed interface Window {
    val maxMs: Long

    data class CoachInitiated(
        val after: UtteranceKind,
        override val maxMs: Long,
    ) : Window

    data class UserInitiated(
        val via: EntryPoint,
        override val maxMs: Long,
    ) : Window

    data object None : Window {
        override val maxMs: Long = 0L
    }
}

fun windowFor(utterance: UtteranceKind): Window = when (utterance) {
    UtteranceKind.STATEMENT -> Window.None
    UtteranceKind.CLOSED_ANSWER ->
        Window.CoachInitiated(utterance, WINDOW_CLOSED_ANSWER_MS)
    UtteranceKind.SHORT_QUESTION ->
        Window.CoachInitiated(utterance, WINDOW_SHORT_QUESTION_MS)
    UtteranceKind.OPEN_QUESTION ->
        Window.CoachInitiated(utterance, WINDOW_OPEN_QUESTION_MS)
}

fun windowFor(entry: EntryPoint): Window =
    Window.UserInitiated(entry, WINDOW_USER_INITIATED_MS)

/**
 * Speech arrived before close. Extend while they talk, never past the hard cap.
 * A closed window stays closed.
 */
fun onSpeechDetected(window: Window, elapsedMs: Long): Window {
    if (window is Window.None) return window
    val extended = min(WINDOW_HARD_CAP_MS, max(window.maxMs, elapsedMs + WINDOW_SPEECH_EXTENSION_MS))
    return when (window) {
        is Window.CoachInitiated -> window.copy(maxMs = extended)
        is Window.UserInitiated -> window.copy(maxMs = extended)
        Window.None -> window
    }
}

fun onTimeout(window: Window, elapsedMs: Long): Window =
    if (window is Window.None || elapsedMs >= window.maxMs) Window.None else window

fun onCancel(window: Window): Window {
    if (window is Window.None) return window
    return Window.None
}

fun close(window: Window): Window {
    if (window is Window.None) return window
    return Window.None
}

fun shouldDuck(window: Window): Boolean = window !is Window.None
