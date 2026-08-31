package com.gymbuddy.app.hud

import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.ProjectedSet
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot

/**
 * Maps [WorkoutSessionSnapshot] onto the prototype Gym Mode HUD.
 *
 * Source of the layout: Voice Workout OS `06-body.html` `#screenWorkout`
 * (sticky now-card, set grid, rest ring, pocket, type-instead dock).
 * Does not emit events. Lifecycle belongs to [GymHudSession] → WorkoutEngine.
 */
object GymHudBinder {
    const val MIN_TAP_DP = 44
    const val UNDO_WINDOW_MS = 12_000L
    val FOCUS_ORDER = listOf("lifecycle", "numberPad", "rest")

    fun bind(
        snapshot: WorkoutSessionSnapshot,
        voice: VoiceTurnState = VoiceTurnState.LISTENING,
        voiceAvailable: Boolean = true,
        caption: CaptionStub = CaptionStub(),
        exerciseNames: Map<String, String> = emptyMap(),
        sessionName: String = "Workout",
        lastLogAtMs: Long? = null,
        nowMs: Long = 0L,
    ): HudUiState {
        val exercise = snapshot.currentExercise
        val name = when {
            exercise == null -> "Ready"
            else -> exerciseNames[exercise.exerciseId] ?: exercise.exerciseId
        }
        val live = snapshot.liveSetsOnCurrent()
        val target = exercise?.plannedSets ?: 0
        val setNumber = if (live.isEmpty()) snapshot.nextOfficialSetNumber() else live.maxOf { it.setNumber }
        val done = target > 0 && live.size >= target
        val nextSet = if (done) target else live.size + 1
        val weightKg = snapshot.workingWeightKg ?: live.lastOrNull()?.weightKg
        val weightLabel = formatWeight(weightKg)
        val previous = live.lastOrNull()?.let { formatSet(it.weightKg, it.reps) } ?: "no previous"
        val resting = snapshot.phase == WorkoutSessionState.RESTING
        val restSec = when {
            resting -> snapshot.restRemainingSec?.toInt() ?: snapshot.rest?.targetSec
            snapshot.phase == WorkoutSessionState.PAUSED -> snapshot.restRemainingSec?.toInt()
            else -> null
        }
        val exerciseOrdinal = if (exercise == null) 0 else snapshot.currentExerciseIndex + 1
        val exerciseCount = snapshot.plan?.exercises?.count { !it.skipped } ?: 0
        val action = lifecycleAction(snapshot.phase)
        val padOn = snapshot.phase != WorkoutSessionState.IDLE &&
            snapshot.phase != WorkoutSessionState.WORKOUT_COMPLETE
        val indicator = voiceIndicator(voice, voiceAvailable)
        val stub = caption.copy(isChatThread = false, bubbleCount = 0)
        val kicker = when {
            snapshot.phase == WorkoutSessionState.IDLE -> sessionName
            exerciseCount == 0 -> sessionName
            else -> "$sessionName · exercise $exerciseOrdinal of $exerciseCount"
        }
        val setLabel = when {
            exercise == null -> "—"
            done -> "done"
            target == 0 -> "$nextSet"
            else -> "$nextSet/$target"
        }
        val last = live.lastOrNull()
        val undoVisible = last != null && lastLogAtMs != null &&
            nowMs - lastLogAtMs in 0 until UNDO_WINDOW_MS
        val restHud = RestHud(
            visible = resting && (restSec ?: 0) > 0,
            secondsLabel = formatRest(restSec),
            kindLabel = "Recovery",
            message = when {
                !resting -> ""
                done -> "Next: new exercise"
                else -> "Next: set $nextSet of $target"
            },
        )
        val pocket = pocket(snapshot, name, weightLabel, nextSet, target, restSec, resting, indicator)
        val controls = listOf(
            HudControl("lifecycle", lifecycleLabel(action), enabled = true),
            HudControl("numberPad", "Type what you'd say", enabled = padOn),
            HudControl("rest", restA11y(restHud), enabled = restHud.visible),
        )
        return HudUiState(
            kicker = kicker,
            exerciseName = name,
            weightLabel = weightLabel,
            setLabel = setLabel,
            targetRepsLabel = "—",
            setPills = setPills(live, target, weightKg),
            whyLine = "",
            undo = UndoHud(
                visible = undoVisible,
                label = if (undoVisible && last?.reps != null) "Undo ${last.reps}" else "Undo",
            ),
            rest = restHud,
            pocket = pocket,
            micLabel = micLabel(indicator, voiceAvailable),
            setNumber = setNumber,
            targetSets = target,
            previousPerformance = previous,
            restSec = restSec,
            progress = "exercise $exerciseOrdinal/$exerciseCount · sets ${live.size}/$target",
            voice = indicator,
            caption = stub,
            lifecycleAction = action,
            numberPadEnabled = padOn,
            highContrast = true,
            minTapTargetDp = MIN_TAP_DP,
            controls = controls,
            focusOrder = FOCUS_ORDER,
        )
    }

    fun voiceIndicator(voice: VoiceTurnState, available: Boolean): VoiceIndicator {
        if (!available) return VoiceIndicator.UNAVAILABLE
        return when (voice) {
            VoiceTurnState.LISTENING, VoiceTurnState.USER_SPEAKING, VoiceTurnState.INTERRUPTED ->
                VoiceIndicator.LISTENING
            VoiceTurnState.PROCESSING -> VoiceIndicator.THINKING
            VoiceTurnState.AI_SPEAKING -> VoiceIndicator.SPEAKING
            VoiceTurnState.SILENT, VoiceTurnState.STOPPED -> VoiceIndicator.SILENT
        }
    }

    private fun pocket(
        snapshot: WorkoutSessionSnapshot,
        name: String,
        weightLabel: String,
        nextSet: Int,
        target: Int,
        restSec: Int?,
        resting: Boolean,
        voice: VoiceIndicator,
    ): PocketHud {
        if (resting && (restSec ?: 0) > 0) {
            return PocketHud(
                label = "Recovery",
                big = formatRest(restSec),
                exerciseLine = "$name · set $nextSet of $target",
            )
        }
        val label = when (voice) {
            VoiceIndicator.SPEAKING -> "Speaking"
            VoiceIndicator.LISTENING, VoiceIndicator.THINKING -> "Listening"
            VoiceIndicator.UNAVAILABLE -> "Voice unavailable"
            VoiceIndicator.SILENT -> "Paused"
        }
        val big = if (snapshot.phase == WorkoutSessionState.IDLE) "—" else weightLabel
        val line = if (snapshot.currentExercise == null) "" else "$name · set $nextSet of $target"
        return PocketHud(label, big, line)
    }

    private fun setPills(live: List<ProjectedSet>, target: Int, workingKg: Double?): List<SetPill> {
        val logged = live.mapIndexed { i, s ->
            SetPill(text = pillText(s.reps, s.weightKg), todo = false, justLogged = i == live.lastIndex)
        }
        val todos = (live.size until target).map {
            SetPill(text = "— × ${formatWeightBare(workingKg)}", todo = true)
        }
        return logged + todos
    }

    private fun lifecycleAction(phase: WorkoutSessionState): LifecycleAction = when (phase) {
        WorkoutSessionState.IDLE, WorkoutSessionState.WORKOUT_STARTING -> LifecycleAction.START
        WorkoutSessionState.PAUSED -> LifecycleAction.RESUME
        WorkoutSessionState.WORKOUT_COMPLETE -> LifecycleAction.START
        else -> LifecycleAction.PAUSE
    }

    private fun lifecycleLabel(action: LifecycleAction): String = when (action) {
        LifecycleAction.START -> "Start workout"
        LifecycleAction.PAUSE -> "Pause workout"
        LifecycleAction.RESUME -> "Resume workout"
        LifecycleAction.COMPLETE -> "Finish workout"
    }

    private fun micLabel(voice: VoiceIndicator, available: Boolean): String = when {
        !available -> "Voice unavailable — type instead"
        voice == VoiceIndicator.THINKING -> "thinking…"
        voice == VoiceIndicator.SPEAKING -> "speaking — say anything to cut in"
        voice == VoiceIndicator.LISTENING -> "listening"
        else -> "Start listening"
    }

    private fun restA11y(rest: RestHud): String =
        if (!rest.visible) "Rest timer" else "Rest timer, ${rest.secondsLabel} remaining"

    private fun formatRest(sec: Int?): String {
        if (sec == null || sec <= 0) return "0"
        return if (sec >= 60) "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" else sec.toString()
    }

    private fun formatWeight(kg: Double?): String {
        if (kg == null) return "—"
        return "${formatWeightBare(kg)} kg"
    }

    private fun formatWeightBare(kg: Double?): String {
        if (kg == null) return "—"
        return if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
    }

    private fun pillText(reps: Int?, weightKg: Double?): String {
        val w = formatWeightBare(weightKg)
        val r = reps?.toString() ?: "—"
        return "$r × $w"
    }

    private fun formatSet(weightKg: Double?, reps: Int?): String {
        val w = formatWeightBare(weightKg)
        val r = reps?.toString()
        return when {
            weightKg != null && r != null -> "$w × $r"
            r != null -> "$r reps"
            weightKg != null -> "$w kg"
            else -> "no previous"
        }
    }
}
