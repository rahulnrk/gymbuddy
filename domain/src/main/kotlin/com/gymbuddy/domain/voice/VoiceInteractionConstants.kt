package com.gymbuddy.domain.voice

/**
 * Conversation-window and coach-clock numbers. Every `const val` has a row in
 * `docs/living-spec/DECISIONS_AND_THRESHOLDS.md`.
 */
object VoiceInteractionConstants {
    const val REVIEWED = "2026-09-02"

    const val WINDOW_CLOSED_ANSWER_MS = 8_000L
    const val WINDOW_SHORT_QUESTION_MS = 10_000L
    const val WINDOW_OPEN_QUESTION_MS = 40_000L
    const val WINDOW_USER_INITIATED_MS = 40_000L
    const val WINDOW_HARD_CAP_MS = 60_000L
    const val WINDOW_SPEECH_EXTENSION_MS = 5_000L

    /** Longest published rest (strength trained compound). */
    const val CONFIDENCE_HORIZON_MS = 300_000L

    const val REST_DECAY_EXTENSION_SEC = 30

    /** Existing barge-in budget. Window policy must not add to it. */
    const val BARGE_IN_CANCEL_P95_MS = 200L
}
