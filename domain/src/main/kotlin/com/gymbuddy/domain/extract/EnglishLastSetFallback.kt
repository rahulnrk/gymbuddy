package com.gymbuddy.domain.extract

import com.gymbuddy.domain.CandidateType
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.FeedbackKind
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.QuestionTopic
import com.gymbuddy.domain.events.CandidateEvent
import com.gymbuddy.domain.events.CandidateEventEnvelope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * English last-set fallback matrix (TECHNICAL_SPEC). Deterministic.
 * Does not inherit working weight into SET_COMPLETED. Does not assign set numbers.
 */
object EnglishLastSetFallback {
    private val HISTORICAL = listOf("last week", "last time", "last thursday", "i think i used")

    fun extract(request: ExtractionRequest, intents: Set<IntentLabel>): CandidateEventEnvelope {
        val raw = request.transcript.trim()
        val n = raw.lowercase()
        val t = raw
        val out = mutableListOf<CandidateEvent>()
        var i = 0
        fun next() = i++

        if (HISTORICAL.any { it in n } && (intents.contains(IntentLabel.QUESTION) || "think" in n || "?" in n || "entha" in n)) {
            out += question(t, next(), n)
            return CandidateFactory.envelope(t, out)
        }

        if (isDelete(n)) {
            out += CandidateFactory.event(
                t, next(), CandidateType.SET_DELETED,
                buildJsonObject { put("reason", "scratch_that") },
                mapOf("reason" to 0.9),
            )
            return CandidateFactory.envelope(t, out)
        }

        correctionReps(n)?.let { reps ->
            out += CandidateFactory.event(
                t, next(), CandidateType.SET_CORRECTED,
                buildJsonObject {
                    put("reps", reps)
                    put("target", "last")
                },
                mapOf("reps" to 0.94),
                setNumberHint = lastHint(n),
            )
            return CandidateFactory.envelope(t, out)
        }

        if (isQuestion(n, intents)) {
            out += question(t, next(), n)
            extraSet(n)?.let { kind ->
                out += command(t, next(), kind)
            }
            skip(n)?.let { out += command(t, next(), it) }
            return CandidateFactory.envelope(t, out)
        }

        dropWeight(n, request)?.let { w ->
            out += CandidateFactory.event(
                t, next(), CandidateType.WEIGHT_CHANGED,
                buildJsonObject {
                    put("weight", w)
                    put("weightUnit", "kg")
                },
                mapOf("weight" to 0.8),
            )
            return CandidateFactory.envelope(t, out)
        }

        skip(n)?.let {
            out += command(t, next(), it)
            return CandidateFactory.envelope(t, out)
        }
        extraSet(n)?.let {
            out += command(t, next(), it)
            return CandidateFactory.envelope(t, out)
        }
        if (Regex("""\d+\s*seconds""").containsMatchIn(n)) {
            out += question(t, next(), n)
            return CandidateFactory.envelope(t, out)
        }
        pauseOrDone(n)?.let {
            out += command(t, next(), it)
            return CandidateFactory.envelope(t, out)
        }

        if ("occupied" in n || ("instead" in n) || "dumbbell" in n || "another exercise" in n || "something different" in n) {
            val name = when {
                "dumbbell" in n -> "dumbbells"
                else -> null
            }
            out += CandidateFactory.event(
                t, next(), CandidateType.SUBSTITUTION_PROPOSE,
                buildJsonObject {
                    if (name != null) put("toExerciseNameRaw", name)
                },
                mapOf("toExerciseNameRaw" to 0.8),
            )
            return CandidateFactory.envelope(t, out)
        }

        val sets = if (n.contains("first") && n.contains("second")) emptyList() else setLogs(n)
        if (sets.isNotEmpty() && IntentLabel.QUESTION !in intents && IntentLabel.CORRECTION !in intents) {
            val catchUp = sets.size >= 2
            sets.forEachIndexed { idx, s ->
                out += CandidateFactory.event(
                    t, next(), CandidateType.SET_COMPLETED,
                    buildJsonObject {
                        s.reps?.let { put("reps", it) }
                        s.weight?.let { put("weight", it) }
                        if (s.weight != null) put("weightUnit", s.unit)
                    },
                    buildMap {
                        s.reps?.let { put("reps", 0.95) }
                        s.weight?.let { put("weight", 0.9) }
                    },
                    loggingMode = if (catchUp) LoggingMode.CATCH_UP else LoggingMode.LIVE,
                    setNumberHint = s.hint?.let { CandidateFactory.hint(it) },
                )
            }
        }

        if ("heavy" in n) {
            out += CandidateFactory.event(
                t, next(), CandidateType.USER_FEEDBACK,
                buildJsonObject { put("kind", "hard") },
                mapOf("kind" to 0.85),
            )
        } else if ("weird" in n && "shoulder" in n) {
            out += CandidateFactory.event(
                t, next(), CandidateType.USER_FEEDBACK,
                buildJsonObject { put("kind", "shoulder_discomfort") },
                mapOf("kind" to 0.8),
            )
        }

        return CandidateFactory.envelope(t, out)
    }

    private fun question(t: String, index: Int, n: String): CandidateEvent {
        val topic = when {
            "protein" in n || "nutrition" in n -> QuestionTopic.NUTRITION
            "last week" in n || "last time" in n || "history" in n || "entha" in n -> QuestionTopic.HISTORY
            "shoulder" in n || "why" in n -> QuestionTopic.TECHNIQUE
            "rest" in n || "second" in n -> QuestionTopic.RECOVERY
            "next" in n || "aaj" in n || "karna" in n -> QuestionTopic.PLAN
            else -> QuestionTopic.OTHER
        }
        return CandidateFactory.event(
            t, index, CandidateType.USER_QUESTION,
            buildJsonObject {
                put("questionOriginal", t)
                put("topic", topic.name.lowercase())
            },
            mapOf("questionOriginal" to 0.9),
        )
    }

    private fun command(t: String, index: Int, kind: CommandKind): CandidateEvent =
        CandidateFactory.event(
            t, index, CandidateType.COMMAND,
            buildJsonObject { put("kind", kind.name.lowercase().let { if (it == "extra_set") "extra_set" else it }) },
            mapOf("kind" to 0.85),
        )

    private fun isDelete(n: String) =
        "scratch that" in n || "delete that" in n || "undo that" in n

    private fun isQuestion(n: String, intents: Set<IntentLabel>): Boolean {
        if (IntentLabel.QUESTION in intents) return true
        return n.contains("?") || n.startsWith("why") || n.startsWith("how") || n.startsWith("what")
    }

    private fun correctionReps(n: String): Int? {
        val m = Regex(
            """(?:no[, ]+|nope[, ]+|actually(?: make that)?\s+|kaadu[, ]+|that was\s+|wait,? that was\s+)(\d+|eight|nine|ten|six|seven)""",
        ).find(n)
        return m?.groupValues?.get(1)?.let { NumberWords.parse(it) }
    }

    private fun lastHint(n: String) = when {
        "this set" in n || "this one" in n -> CandidateFactory.hint("this")
        "last one" in n || "last set" in n || "the last" in n -> CandidateFactory.hint("last")
        else -> null
    }

    private fun dropWeight(n: String, request: ExtractionRequest): Double? {
        if ("drop the weight" !in n && "drop it" !in n && "lower the weight" !in n) return null
        val spoken = Regex("""(?:to|at)\s+(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull()
        if (spoken != null) return spoken
        val current = request.contexts?.workout?.currentWeightKg ?: return null
        return current - 5.0
    }

    private fun skip(n: String): CommandKind? =
        if (Regex("""\bskip\b""").containsMatchIn(n)) CommandKind.SKIP else null

    private fun extraSet(n: String): CommandKind? =
        if ("one more" in n || "another set" in n || "extra set" in n || "one more set" in n) {
            CommandKind.EXTRA_SET
        } else null

    private fun pauseOrDone(n: String): CommandKind? = when {
        Regex("""\bpause\b""").containsMatchIn(n) -> CommandKind.PAUSE
        "i'm done" in n || "im done" in n || "end workout" in n -> CommandKind.END_WORKOUT
        else -> null
    }

    private data class SetLog(val reps: Int?, val weight: Double?, val unit: String = "kg", val hint: String? = null)

    private fun setLogs(n: String): List<SetLog> {
        val multi = mutableListOf<SetLog>()
        // Single: "40 for 10" / "40 kilos for 8"
        Regex("""(\d+(?:\.\d+)?)\s*(kg|kilos|kilo|lb|lbs)?\s*(?:for|x|×)\s*(\d+|eight|ten|twelve|nine|six|seven)""")
            .find(n)?.let { m ->
                return listOf(
                    SetLog(
                        reps = NumberWords.parse(m.groupValues[3]),
                        weight = m.groupValues[1].toDouble(),
                        unit = unit(m.groupValues[2]),
                    ),
                )
            }
        Regex("""(\d+(?:\.\d+)?)\s*(kilos|kilo|kg|lb|lbs)\s*,\s*(\d+|eight|ten|twelve)""")
            .find(n)?.let { m ->
                return listOf(
                    SetLog(NumberWords.parse(m.groupValues[3]), m.groupValues[1].toDouble(), unit(m.groupValues[2])),
                )
            }
        Regex("""(\d+(?:\.\d+)?)\s*(kilos|kilo|kg).{0,24}(\d+|eight|ten|twelve|nine)\s*reps""")
            .find(n)?.let { m ->
                return listOf(
                    SetLog(NumberWords.parse(m.groupValues[3]), m.groupValues[1].toDouble(), unit(m.groupValues[2])),
                )
            }
        Regex("""\b(got|did|that's|thats|that is|that was)\s+(\d+|ten|eight|twelve|nine|six|seven)\b""")
            .find(n)?.let { m ->
                if (m.groupValues[1] == "that was" && "actually" in n) return emptyList()
                return listOf(SetLog(NumberWords.parse(m.groupValues[2]), null, hint = if ("this" in n) "this" else null))
            }
        Regex("""\b(\d+|ten|eight|twelve)\s+reps\b""").find(n)?.let { m ->
            return listOf(SetLog(NumberWords.parse(m.groupValues[1]), null))
        }
        return multi
    }

    private fun unit(raw: String): String =
        if (raw.contains("lb")) "lb" else "kg"
}
