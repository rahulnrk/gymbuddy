package com.gymbuddy.app.hud

/**
 * Gym Mode HUD binder, aligned to the working Voice Workout OS prototype
 * (`06-body.html` `#screenWorkout` + `#pocket` + `#dock`).
 *
 * Not the Today dashboard. Not the body map. Not a chat app.
 * Caption is a stub until PR 11 (Feature 25).
 */
enum class VoiceIndicator {
    LISTENING,
    THINKING,
    SPEAKING,
    SILENT,
    UNAVAILABLE,
}

enum class LifecycleAction {
    START,
    PAUSE,
    RESUME,
    COMPLETE,
}

data class CaptionStub(
    val userLine: String? = null,
    val aiLine: String? = null,
    val minimized: Boolean = true,
    val isChatThread: Boolean = false,
    val bubbleCount: Int = 0,
)

data class HudControl(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val minTapTargetDp: Int = 44,
)

data class SetPill(
    val text: String,
    val todo: Boolean,
    val justLogged: Boolean = false,
)

data class RestHud(
    val visible: Boolean,
    val secondsLabel: String,
    val kindLabel: String,
    val message: String,
)

data class PocketHud(
    val label: String,
    val big: String,
    val exerciseLine: String,
)

data class UndoHud(
    val visible: Boolean,
    val label: String,
)

data class HudUiState(
    val kicker: String,
    val exerciseName: String,
    val weightLabel: String,
    val setLabel: String,
    val targetRepsLabel: String,
    val setPills: List<SetPill>,
    val whyLine: String,
    val undo: UndoHud,
    val rest: RestHud,
    val pocket: PocketHud,
    val micLabel: String,
    val setNumber: Int,
    val targetSets: Int,
    val previousPerformance: String,
    val restSec: Int?,
    val progress: String,
    val voice: VoiceIndicator,
    val caption: CaptionStub,
    val lifecycleAction: LifecycleAction,
    val numberPadEnabled: Boolean,
    val highContrast: Boolean = true,
    val minTapTargetDp: Int = 44,
    val controls: List<HudControl>,
    val focusOrder: List<String>,
)
