package com.gymbuddy.app.hud

import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.validation.ValidatedFact

/**
 * HUD controller. Binds Start/Pause/Resume and type-instead (manual set) to the existing
 * [WorkoutEngine] session service. Does not invent lifecycle event types.
 *
 * CTA taps are `source=manual`. Hydrate rows stay `source=system` inside the engine.
 */
class GymHudSession(
    val engine: WorkoutEngine,
    val plan: WorkoutPlan,
    val userId: String,
    val sessionId: String,
    val exerciseNames: Map<String, String> = emptyMap(),
    var voice: VoiceTurnState = VoiceTurnState.LISTENING,
    var voiceAvailable: Boolean = true,
    var caption: CaptionStub = CaptionStub(),
    var sessionName: String = "Workout",
    var lastLogAtMs: Long? = null,
    var nowMs: () -> Long = { System.currentTimeMillis() },
) {
    fun snapshot(): WorkoutSessionSnapshot = engine.snapshot(sessionId, plan)

    fun ui(): HudUiState = GymHudBinder.bind(
        snapshot(),
        voice = voice,
        voiceAvailable = voiceAvailable,
        caption = caption,
        exerciseNames = exerciseNames,
        sessionName = sessionName,
        lastLogAtMs = lastLogAtMs,
        nowMs = nowMs(),
    )

    fun start(): EngineResult = engine.startSession(userId, sessionId, plan, EventSource.MANUAL)

    fun pause(): EngineResult = engine.pause(sessionId, plan, source = EventSource.MANUAL)

    fun resume(): EngineResult = engine.resume(sessionId, plan, source = EventSource.MANUAL)

    fun complete(): EngineResult = engine.complete(sessionId, plan, source = EventSource.MANUAL)

    fun logManualSet(reps: Int, weightKg: Double? = null): EngineResult {
        val snap = snapshot()
        val weight = weightKg ?: snap.workingWeightKg
        if (weight == null) return EngineResult.Rejected("no_working_weight")
        val fact = ValidatedFact.SetCompleted(
            reps = reps,
            weightKg = weight,
            inheritWeight = weightKg == null,
            loggingMode = LoggingMode.LIVE,
        )
        val result = engine.applyFacts(sessionId, plan, listOf(fact), EventSource.MANUAL)
        if (result is EngineResult.Committed && result.events.isNotEmpty()) {
            lastLogAtMs = nowMs()
        }
        return result
    }
}
