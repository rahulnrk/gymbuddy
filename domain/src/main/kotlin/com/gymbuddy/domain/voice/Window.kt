package com.gymbuddy.domain.voice

import com.gymbuddy.domain.voice.VoiceInteractionConstants.DUCK_HOLD_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_CLOSED_ANSWER_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_HARD_CAP_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_OPEN_QUESTION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_SHORT_QUESTION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_STATEMENT_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_USER_INITIATED_MS
import kotlin.math.max

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
    EMERGENCY,
}

sealed interface Window {
    val maxMs: Long

    data class CoachInitiated(
        val after: UtteranceKind,
        /** Reply budget after the coach stops. Not measured from window open. */
        override val maxMs: Long,
        /** Elapsed ms when the coach finished. Null = still speaking; budget has not started. */
        val coachFinishedAtElapsedMs: Long? = null,
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
    UtteranceKind.STATEMENT ->
        Window.CoachInitiated(utterance, WINDOW_STATEMENT_MS)
    UtteranceKind.CLOSED_ANSWER ->
        Window.CoachInitiated(utterance, WINDOW_CLOSED_ANSWER_MS)
    UtteranceKind.SHORT_QUESTION ->
        Window.CoachInitiated(utterance, WINDOW_SHORT_QUESTION_MS)
    UtteranceKind.OPEN_QUESTION ->
        Window.CoachInitiated(utterance, WINDOW_OPEN_QUESTION_MS)
}

/**
 * Coach finished the line. Reply budget starts now. Mic was already open.
 */
fun onCoachFinished(window: Window, elapsedMs: Long): Window = when (window) {
    is Window.CoachInitiated -> window.copy(coachFinishedAtElapsedMs = elapsedMs)
    is Window.UserInitiated, Window.None -> window
}

fun windowFor(entry: EntryPoint): Window {
    if (!VoiceInteractionConstants.WAKE_WORD_PATH_LIVE && isDormantEntry(entry)) {
        return Window.None
    }
    return Window.UserInitiated(entry, WINDOW_USER_INITIATED_MS)
}

/**
 * Speech arrived before close. Extend while they talk.
 * No hard cap on a conversation in progress (contract 35).
 * A closed window stays closed.
 *
 * Coach-initiated [maxMs] is a duration after [Window.CoachInitiated.coachFinishedAtElapsedMs],
 * not from window open. Barge-in while the coach is still speaking does not
 * move that budget — [onTimeout] will not consume it until [onCoachFinished].
 */
fun onSpeechDetected(window: Window, elapsedMs: Long): Window {
    if (window is Window.None) return window
    return when (window) {
        is Window.CoachInitiated -> {
            val start = window.coachFinishedAtElapsedMs ?: return window
            val needed = elapsedMs + DUCK_HOLD_MS - start
            window.copy(maxMs = max(window.maxMs, needed))
        }
        is Window.UserInitiated ->
            window.copy(maxMs = max(window.maxMs, elapsedMs + DUCK_HOLD_MS))
        Window.None -> window
    }
}

/**
 * Close an idle window. Duck and mic end together: after last speech,
 * both last [DUCK_HOLD_MS]. While [userSpeaking] the window is not cut.
 * [WINDOW_HARD_CAP_MS] only closes a window open that long with nobody talking.
 * If they never spoke, the reply budget starts when the coach finished
 * ([onCoachFinished]). While the coach is still speaking the budget does not
 * run. Emergency / user-initiated windows have no coach line; budget is from open.
 */
fun onTimeout(
    window: Window,
    elapsedMs: Long,
    userSpeaking: Boolean = false,
    lastUserSpeechAtMs: Long? = null,
): Window {
    if (window is Window.None) return window
    if (userSpeaking) return window
    if (lastUserSpeechAtMs != null && elapsedMs >= lastUserSpeechAtMs + DUCK_HOLD_MS) {
        return Window.None
    }
    when (window) {
        is Window.CoachInitiated -> {
            val start = window.coachFinishedAtElapsedMs ?: return window
            if (elapsedMs >= start + window.maxMs) return Window.None
        }
        is Window.UserInitiated -> {
            if (elapsedMs >= window.maxMs) return Window.None
        }
        Window.None -> return window
    }
    if (elapsedMs >= WINDOW_HARD_CAP_MS) return Window.None
    return window
}

fun onCancel(window: Window): Window {
    if (window is Window.None) return window
    return Window.None
}

fun close(window: Window): Window {
    if (window is Window.None) return window
    return Window.None
}

fun shouldDuck(window: Window): Boolean = window !is Window.None

/**
 * Quiet is the user stopped talking, not that the gym went silent.
 * A window still ducks. After they stop, hold [DUCK_HOLD_MS].
 */
fun stillDucked(
    window: Window,
    userSpeaking: Boolean,
    lastUserSpeechAtMs: Long?,
    nowMs: Long,
): Boolean {
    if (shouldDuck(window)) return true
    if (userSpeaking) return true
    val last = lastUserSpeechAtMs ?: return false
    return nowMs - last < DUCK_HOLD_MS
}

fun isDormantEntry(entry: EntryPoint): Boolean = when (entry) {
    EntryPoint.EMERGENCY -> false
    EntryPoint.WAKE_WORD, EntryPoint.EARBUD, EntryPoint.ON_SCREEN -> true
}
