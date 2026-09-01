package com.gymbuddy.domain.intake

/**
 * Spoken to someone who may be frightened. Plain, calm, specific about the
 * next step. No implied diagnosis, no condition names, no probabilities.
 *
 * This app's limit is that it cannot write a plan until a clinician has
 * checked the things the user marked. That is not a refusal to help.
 * Urgency, when present, is the user's stated recency — never the app
 * judging how serious a symptom is. No emergency number lives here.
 */
object ReferralCopy {

    const val RECENCY_QUESTION =
        "Is this happening now, or has it happened in the last week?"

    const val RECENCY_URGENCY =
        "If you are feeling this right now, or it came on recently, please " +
            "don't wait for a routine appointment - get seen today."

    fun forFlags(flags: List<RedFlag>, recency: Map<RedFlag, Answer> = emptyMap()): String {
        val unique = flags.distinct()
        val body = unique.joinToString("\n\n") { flag ->
            forFlag(flag, recency[flag] == Answer.YES)
        }
        return if (unique.size == 1) {
            body
        } else {
            "A few of the things you marked need a doctor or qualified clinician " +
                "to look at before we start. A training plan from this app has to " +
                "wait until they say you are clear to exercise.\n\n$body"
        }
    }

    fun forFlag(flag: RedFlag, happeningNowOrThisWeek: Boolean = false): String {
        val base = when (flag) {
            RedFlag.CHEST_PAIN ->
                "You marked chest pain. A training plan from this app has to wait " +
                    "until a doctor or qualified clinician has checked this and told " +
                    "you it is okay to exercise. Please get that looked at before we " +
                    "go further. If they say you are clear, come back and record the " +
                    "date they told you."

            RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION ->
                "You marked that you get short of breath at rest or with light effort. " +
                    "Please see a doctor or qualified clinician about that before a " +
                    "training plan from this app can go further. When they say you are " +
                    "clear to exercise, tell me the date, and we can pick this up."

            RedFlag.DIZZINESS_OR_FAINTING ->
                "You marked dizziness or fainting. That needs a doctor or qualified " +
                    "clinician to check before you lift or train with us. Please get it " +
                    "looked at. When they say you are clear to exercise, record that date."

            RedFlag.PALPITATIONS_OR_IRREGULAR_HEARTBEAT ->
                "You marked palpitations or an irregular heartbeat. A training plan " +
                    "from this app has to wait until a doctor or qualified clinician has " +
                    "checked this and said it is okay to exercise. Please get that done, " +
                    "then come back and record the date they told you."

            RedFlag.RECENT_SURGERY ->
                "You marked recent surgery. A training plan from this app has to wait " +
                    "until the clinician looking after you says it is okay to start. " +
                    "Please ask them, and when they give you the go-ahead, record that date."

            RedFlag.UNEXPLAINED_NIGHT_PAIN ->
                "You marked pain at night that you cannot explain. Please have a doctor " +
                    "or qualified clinician check that before a training plan from this " +
                    "app can go further. When they say you are clear to exercise, tell me " +
                    "the date they said so."

            RedFlag.RADIATING_NUMBNESS_OR_WEAKNESS ->
                "You marked numbness or weakness that travels. Please get that checked " +
                    "by a doctor or qualified clinician before we add training. When they " +
                    "say you are clear to exercise, record the date they told you."

            RedFlag.PREGNANCY_COMPLICATION ->
                "You marked yes on the question about a clinician-flagged complication. " +
                    "A training plan from this app has to wait. Please follow the clinician " +
                    "who is already looking after you. If they later say it is okay to train " +
                    "here, record the date they told you."

            RedFlag.UNCONTROLLED_DIAGNOSED_CONDITION ->
                "You marked a diagnosed condition that is not under control. A training " +
                    "plan from this app has to wait until a doctor or qualified clinician " +
                    "says it is okay to exercise. Please talk to them. When they say you " +
                    "are clear, record the date they told you."

            RedFlag.NEW_UNEXPLAINED_SWELLING_OR_PAIN ->
                "You marked new swelling or pain that you cannot explain. Please have " +
                    "a doctor or qualified clinician check that before a training plan " +
                    "from this app can go further. When they say you are clear to exercise, " +
                    "tell me the date they said so."
        }
        return if (happeningNowOrThisWeek && flag in ACUTE_SYMPTOM_FLAGS) {
            "$base $RECENCY_URGENCY"
        } else {
            base
        }
    }

    fun recencyQuestion(flag: RedFlag): String {
        require(flag in ACUTE_SYMPTOM_FLAGS) { "$flag is not an acute-symptom flag" }
        return RECENCY_QUESTION
    }

    /** Spoken on the clearance path, not in the referral. */
    fun clearanceAcknowledgement(date: String): String =
        "You told us a clinician said you are clear to exercise on $date. " +
            "I'll store that as your report, not a medical record."
}
