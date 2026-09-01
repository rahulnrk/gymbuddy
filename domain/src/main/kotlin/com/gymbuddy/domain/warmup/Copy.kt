package com.gymbuddy.domain.warmup

object WarmupCopy {
    fun pulseWhy(): String =
        "Easy work to raise your temperature before the lifts."

    fun mobilityWhy(patterns: Collection<String>): String {
        val job = patterns.firstOrNull()?.let { jobFor(it) } ?: "today's lifts"
        return "This frees up range you'll use on $job."
    }

    fun activationWhy(muscles: Collection<String>, catalogName: (String) -> String): String {
        val muscle = muscles.firstOrNull()?.let { catalogName(it) } ?: "muscles that should fire"
        return "This wakes up your $muscle so they actually help."
    }

    fun rehearsalWhy(patterns: Collection<String>): String {
        val job = patterns.firstOrNull()?.let { jobFor(it) } ?: "the first lift"
        return "A light run-through of $job, no load."
    }

    fun stretchWhy(muscles: Collection<String>, catalogName: (String) -> String): String {
        val muscle = muscles.firstOrNull()?.let { catalogName(it) } ?: "what you just trained"
        return "This is for your $muscle after the work."
    }

    fun stretchDisclaimer(): String =
        "Stretching is not a fix for feeling weak. Feeling tight does not mean that muscle needs a stretch."

    fun answerIfAskedWhetherStretchingFixesSomething(): String = stretchDisclaimer()

    fun mobilityShort(patterns: Collection<String>): String {
        val range = rangeFor(patterns)
        return "frees up $range"
    }

    fun activationShort(muscles: Collection<String>, catalogName: (String) -> String): String {
        val muscle = muscles.firstOrNull()?.let { catalogName(it) } ?: "those muscles"
        return "wakes up your $muscle"
    }

    fun rehearsalShort(patterns: Collection<String>): String {
        val job = patterns.firstOrNull()?.let { jobFor(it) } ?: "today's lifts"
        return "$job, no load".replace("the ", "")
    }

    fun pulseShort(): String = "gets you warm"

    fun rangeFor(patterns: Collection<String>): String = when {
        patterns.any { it in setOf("horizontal push", "incline push", "decline push") } -> "your pressing range"
        patterns.any { it == "vertical push" } -> "your overhead range"
        patterns.any { it in setOf("horizontal pull", "vertical pull", "lats") } -> "your pulling range"
        patterns.any { it == "squat" } -> "your squat range"
        patterns.any { it == "hinge" } -> "your hinge range"
        patterns.any { it == "lunge" } -> "your lunge range"
        else -> "range you'll use today"
    }

    fun keptShort(): String =
        "I kept the warm-up short so it gets you ready without tiring you out."

    fun noPulse(): String =
        "I couldn't find a pulse raise you can do with the equipment you've got. We'll start with the drills."

    fun jobFor(pattern: String): String = when (pattern) {
        "squat" -> "the squats"
        "hinge" -> "the hinges"
        "lunge" -> "the lunges"
        "horizontal push", "incline push", "decline push" -> "the pressing"
        "vertical push" -> "the overhead work"
        "horizontal pull", "vertical pull", "lats" -> "the pulling"
        "core" -> "the core work"
        "carry" -> "the carries"
        else -> "today's lifts"
    }

    fun drillLine(entry: WarmupEntry): String {
        val d = entry.drill
        return if (entry.timesSeen <= 0) {
            val dose = when {
                d.method == "pulse" -> minutes(d.durationSec)
                d.perSide && d.reps > 0 -> "${d.reps} reps each side"
                d.reps > 0 -> "${d.reps} reps"
                else -> minutes(d.durationSec)
            }
            "${d.name} — $dose. ${entry.why} ${d.howTo}"
        } else {
            "${d.name}, ${familiarDose(d)} — ${entry.why}"
        }
    }

    fun familiarDose(d: WarmupDrill): String = when {
        d.method == "pulse" -> minutes(d.durationSec)
        d.perSide && d.reps > 0 -> "${spell(d.reps)} each side"
        d.reps > 0 -> "${spell(d.reps)} reps"
        else -> minutes(d.durationSec)
    }

    fun spell(n: Int): String = when (n) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        10 -> "ten"
        else -> n.toString()
    }

    fun rampLine(ramp: RampSet): String =
        "${ramp.reps} reps on ${ramp.liftName}, ${ramp.effortDescription}."

    fun stretchLine(pick: StretchPick): String {
        val s = pick.stretch
        val hold = if (s.perSide) {
            "${s.holdSec} seconds each side"
        } else {
            "${s.holdSec} seconds"
        }
        val times = if (s.repeats == 1) "once" else "${s.repeats} times"
        return "${s.name} — $hold, $times. ${pick.why} ${s.howTo}"
    }

    fun minutes(sec: Int): String {
        val m = (sec + 30) / 60
        return if (m <= 1) "about 1 minute" else "about $m minutes"
    }
}
