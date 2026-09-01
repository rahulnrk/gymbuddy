package com.gymbuddy.domain.intake

/**
 * Spoken to someone who may be frightened. Plain, calm, specific about the
 * next step. No implied diagnosis, no condition names, no probabilities.
 */
object ReferralCopy {

    fun forFlags(flags: List<RedFlag>): String {
        val unique = flags.distinct()
        val body = unique.joinToString("\n\n") { forFlag(it) }
        return if (unique.size == 1) {
            body
        } else {
            "A few of the things you marked need a doctor or qualified clinician " +
                "to look at before we start. I am holding any training plan until they " +
                "say you are clear to exercise.\n\n$body"
        }
    }

    fun forFlag(flag: RedFlag): String = when (flag) {
        RedFlag.CHEST_PAIN ->
            "You marked chest pain. I am not going to suggest any training until " +
                "a doctor or qualified clinician has checked this and told you it is " +
                "okay to exercise. Please get that looked at before we go further. " +
                "If they say you are clear, come back and record the date they told you — " +
                "I will store that as your report, not as a medical record."

        RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION ->
            "You marked that you get short of breath at rest or with light effort. " +
                "Please see a doctor or qualified clinician about that before we start " +
                "a training plan. When they say you are clear to exercise, tell me the " +
                "date, and we can pick this up."

        RedFlag.DIZZINESS_OR_FAINTING ->
            "You marked dizziness or fainting. That needs a doctor or qualified " +
                "clinician to check before you lift or train with us. Please get it " +
                "looked at. When they say you are clear to exercise, record that date " +
                "here as your report."

        RedFlag.PALPITATIONS_OR_IRREGULAR_HEARTBEAT ->
            "You marked palpitations or an irregular heartbeat. I will not suggest " +
                "training until a doctor or qualified clinician has checked this and " +
                "said it is okay to exercise. Please get that done, then come back and " +
                "record the date they told you."

        RedFlag.RECENT_SURGERY ->
            "You marked recent surgery. I am not going to suggest training until " +
                "the clinician looking after you says it is okay to start. Please ask " +
                "them, and when they give you the go-ahead, record that date here as " +
                "your report."

        RedFlag.UNEXPLAINED_NIGHT_PAIN ->
            "You marked pain at night that you cannot explain. Please have a doctor " +
                "or qualified clinician check that before we start training. When they " +
                "say you are clear to exercise, tell me the date they said so."

        RedFlag.RADIATING_NUMBNESS_OR_WEAKNESS ->
            "You marked numbness or weakness that travels. Please get that checked " +
                "by a doctor or qualified clinician before we add training. When they " +
                "say you are clear to exercise, record the date they told you."

        RedFlag.PREGNANCY_COMPLICATION ->
            "You marked yes on the question about a clinician-flagged complication. " +
                "This app will not suggest a training plan. Please follow the clinician " +
                "who is already looking after you. If they later say it is okay to train " +
                "here, record the date they told you — I will store that as your report."

        RedFlag.UNCONTROLLED_DIAGNOSED_CONDITION ->
            "You marked a diagnosed condition that is not under control. I am not " +
                "going to suggest training until a doctor or qualified clinician says " +
                "it is okay to exercise. Please talk to them. When they say you are " +
                "clear, record the date they told you as your report."

        RedFlag.NEW_UNEXPLAINED_SWELLING_OR_PAIN ->
            "You marked new swelling or pain that you cannot explain. Please have " +
                "a doctor or qualified clinician check that before we start a plan. " +
                "When they say you are clear to exercise, tell me the date they said so."
    }
}
