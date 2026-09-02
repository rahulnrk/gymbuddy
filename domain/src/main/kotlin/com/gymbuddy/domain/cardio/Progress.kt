package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.programming.Goal

private val INTENSITY_LADDER = listOf(
    ProtocolType.RECOVERY,
    ProtocolType.STEADY_STATE,
    ProtocolType.TEMPO,
    ProtocolType.THRESHOLD,
    ProtocolType.INTERVALS,
    ProtocolType.SPRINT_INTERVALS,
)

/**
 * One variable at a time: duration, then frequency, then intensity.
 * Never an abrupt jump. Same discipline as resistance [com.gymbuddy.domain.programming.nextSession].
 */
fun nextCardioSession(
    last: CardioPrescription,
    sessionsThisWeek: Int,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
): CardioProgression {
    val nextDuration = last.durationMin + CardioConstants.DURATION_STEP_MIN
    if (nextDuration <= last.protocol.durationMax) {
        return CardioProgression(
            prescription = last.copy(durationMin = nextDuration),
            changed = ProgressVariable.DURATION,
            reason = "A little longer next time — about $nextDuration minutes. Same effort.",
        )
    }
    if (sessionsThisWeek < CardioConstants.FREQUENCY_MAX) {
        return CardioProgression(
            prescription = last,
            changed = ProgressVariable.FREQUENCY,
            reason = "Keep this session the same. Add a day this week rather than making it harder.",
        )
    }
    val nextType = nextIntensity(last.protocol.type)
    val proto = nextType?.let { t -> library.protocols.firstOrNull { it.type == t } }
    val inRange = proto != null &&
        last.durationMin in proto.durationMin..proto.durationMax &&
        compatible(last.modality, proto, ctx)
    if (inRange && proto != null) {
        val candidate = prescribe(last.modality, proto, goal, last.durationMin, ctx)
        return CardioProgression(
            prescription = candidate,
            changed = ProgressVariable.INTENSITY,
            reason = "Same machine, one step harder. Duration and days stay put.",
        )
    }
    return CardioProgression(
        prescription = last,
        changed = ProgressVariable.HOLD,
        reason = "Hold here. Duration, days and effort stay the same.",
    )
}

private fun nextIntensity(current: ProtocolType): ProtocolType? {
    val i = INTENSITY_LADDER.indexOf(current)
    if (i < 0 || i >= INTENSITY_LADDER.lastIndex) return null
    return INTENSITY_LADDER[i + 1]
}
