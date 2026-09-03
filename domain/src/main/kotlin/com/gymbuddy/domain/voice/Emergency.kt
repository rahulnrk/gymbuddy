package com.gymbuddy.domain.voice

import com.gymbuddy.domain.pain.PainInterview

enum class EmergencyRoute {
    PAIN_TRIAGE,
}

data class EmergencyOpen(
    val window: Window.UserInitiated,
    val route: EmergencyRoute,
    val interview: PainInterview,
)

/**
 * The only live user-initiated entry. Works mid-set. Goes to pain triage.
 */
fun openEmergency(): EmergencyOpen {
    val window = windowFor(EntryPoint.EMERGENCY)
    check(window is Window.UserInitiated)
    return EmergencyOpen(
        window = window,
        route = EmergencyRoute.PAIN_TRIAGE,
        interview = PainInterview(),
    )
}
