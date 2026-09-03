package com.gymbuddy.domain.voice

/**
 * Conversation-window and coach-clock numbers. Every `const val` has a row in
 * `docs/living-spec/DECISIONS_AND_THRESHOLDS.md`.
 */
object VoiceInteractionConstants {
    const val REVIEWED = "2026-09-02"

    /**
     * Reply budget after a STATEMENT (reminder, not a question).
     * Shortest of the set. UNDECIDED — not a measurement.
     */
    const val WINDOW_STATEMENT_MS = 5_000L
    const val WINDOW_CLOSED_ANSWER_MS = 8_000L
    const val WINDOW_SHORT_QUESTION_MS = 10_000L
    const val WINDOW_OPEN_QUESTION_MS = 40_000L
    const val WINDOW_USER_INITIATED_MS = 40_000L
    const val WINDOW_HARD_CAP_MS = 60_000L

    /** Longest published rest (strength trained compound). */
    const val CONFIDENCE_HORIZON_MS = 300_000L

    const val REST_DECAY_EXTENSION_SEC = 30

    /** Existing barge-in budget. Window policy must not add to it. */
    const val BARGE_IN_CANCEL_P95_MS = 200L

    /**
     * v1: the user cannot open a window by speaking.
     * Flip when LE Audio earbuds make an always-ready mic viable.
     */
    const val WAKE_WORD_PATH_LIVE = false

    /** After the user stops talking, music stays down this long. Not gym silence. */
    const val DUCK_HOLD_MS = 3_000L

    /** Observed rest intervals before the median is trusted over the plan. */
    const val OBSERVED_REST_SAMPLE_MIN = 3

    /** How many recent observed rests feed the median. */
    const val OBSERVED_REST_RECENT = 5

    /** Do not open another scheduled window inside this gap. */
    const val WINDOW_MIN_GAP_MS = 15_000L

    /** Open a window for a quiet non-lifting user after this long. */
    const val WINDOW_MAX_SILENCE_MS = 180_000L

    /** Rest that began this recently counts as "set probably ended." */
    const val WINDOW_SET_END_SLACK_MS = 10_000L
}
