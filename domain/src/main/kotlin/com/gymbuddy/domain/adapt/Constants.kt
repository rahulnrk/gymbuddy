package com.gymbuddy.domain.adapt

object AdaptConstants {
    const val DEFER_CAP = 2
    const val MINUTES_PER_SLOT = 12
    const val EXTRA_RIR_WHEN_COOKED = 1
    const val ASK_HOW_LONG = AdaptCopy.ASK_HOW_LONG
}

/** Every string here is spoken. No field names, no bare numbers. */
object AdaptCopy {
    const val ASK_HOW_LONG = "How long do they look?"
    const val LOWER_EFFORT = "Same lifts, leave a couple more reps in the tank."
    const val LOWER_EFFORT_SHORT_NIGHT =
        "Same lifts, leave a couple more reps in the tank — short night."
    const val WORK_IN = "You can work in with them. Alternate sets. That's normal here."
    const val WAIT = "That's less than your rest anyway. We'll wait."
    const val SKIP_NO_ALTERNATIVE =
        "Nothing else here does this job with the equipment you've got. We'll skip it."
    const val SKIP_MISSING = "I don't have that exercise any more. We'll skip it."
    const val DROP_EXTRA = "I'd take this extra work off today and keep the main lifts."
    const val DROP_TIRED =
        "I'd take this one off today. Everything here still loads the same tired muscles."
    const val FRESH_ENOUGH = "This exercise looks fresh enough to do as planned."

    fun reduceSets(to: Int, from: Int): String =
        "Same work, just ${sets(to)} instead of ${sets(from)}."

    fun sets(n: Int): String = if (n == 1) "1 set" else "$n sets"

    fun shorten(minutes: Int): String =
        "You've got about $minutes minutes. I'll drop the last exercises so we finish on time."

    fun defer(nextName: String): String =
        "We'll do $nextName first and come back to this."

    fun substitute(name: String): String =
        "Let's use $name instead so we keep the same kind of work."

    fun swapToExercise(muscle: String, exerciseName: String): String =
        "Everything left for this loads your $muscle, and they're still cooked. " +
            "Let's switch this work to $exerciseName."

    fun cookedSlot(muscle: String): String =
        "Everything left for this loads your $muscle, and they're still cooked."

    fun mildFatigue(muscle: String): String =
        "Your $muscle still carry some fatigue, which is normal training."
}
