package com.gymbuddy.domain.loop

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.SafetySeverity
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.AssemblyRequest
import com.gymbuddy.domain.context.ConversationalStore
import com.gymbuddy.domain.context.FourContextAssembler
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStore
import com.gymbuddy.domain.context.SttFinalRecord
import com.gymbuddy.domain.context.UserProfileStore
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.extract.ExtractionRequest
import com.gymbuddy.domain.extract.NaturalLanguageExtractor
import com.gymbuddy.domain.persist.EventLog
import com.gymbuddy.domain.safety.SafetyResult
import com.gymbuddy.domain.validation.CandidateValidator
import com.gymbuddy.domain.validation.DefaultCandidateValidator
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.domain.validation.ValidationDecision
import com.gymbuddy.domain.validation.VoiceCommitRoute
import com.gymbuddy.domain.voice.FakeClock
import com.gymbuddy.domain.voice.SttFinalHash
import com.gymbuddy.domain.voice.VoiceTurnEvidence
import com.gymbuddy.domain.voice.fake.FakeTts
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Headless companion loop. Coordinates existing kernel pieces.
 * Does not contain the workout state machine or a natural-language parser.
 *
 * The application owns whether speech is useful, safe, and timely.
 * Silence is an intentional product outcome, not a failure.
 */
class CompanionLoop(
    val engine: WorkoutEngine,
    val log: EventLog,
    val plan: WorkoutPlan,
    val userId: String,
    val sessionId: String,
    val extractor: NaturalLanguageExtractor = NaturalLanguageExtractor(),
    val validator: CandidateValidator = DefaultCandidateValidator(),
    var history: HistoricalStore = HistoricalStore(),
    var profile: UserProfileStore = UserProfileStore(userId = userId),
    val now: () -> String = { "2026-08-20T16:00:00Z" },
    val clock: FakeClock = FakeClock(),
    val tts: FakeTts? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val workoutName: String = "",
) {
    val queuedSpeechActs: MutableList<SpeechActId> = mutableListOf()

    /** Claim-verification failures seen this session. Must stay empty in tests. */
    val claimViolations: MutableList<String> = mutableListOf()
    private val recentHashes: MutableList<SttFinalRecord> = mutableListOf()

    fun start(): TurnResult {
        val r = engine.startSession(userId, sessionId, plan)
        val committed = (r as? EngineResult.Committed)?.events.orEmpty()
        val snap = engine.snapshot(sessionId, plan)
        val ctx = assemble(snap)
        val speak = ShouldSpeak.shouldSpeak(ctx, snap, committed, SafetyResult(false), false, false, null)
        val statement = CoachingPhraser.phrase(
            speak, com.gymbuddy.domain.safety.SafetySpeechDirective(null, null, false), ctx, null, snap,
        )
        val text = statement.text
        val coach = persistCoaching(speak, statement, hash = "start")
        val speechErr = try {
            maybeTts(speak, text)
            null
        } catch (e: Exception) {
            TurnError.Speech(e.message ?: "tts failed")
        }
        return TurnResult(
            transcript = "",
            safety = SafetyResult(false),
            safetySpeech = com.gymbuddy.domain.safety.SafetySpeechDirective(null, null, false),
            extraction = com.gymbuddy.domain.extract.ExtractionResult(
                SafetyResult(false),
                com.gymbuddy.domain.safety.SafetySpeechDirective(null, null, false),
                emptySet(),
                com.gymbuddy.domain.events.CandidateEventEnvelope(1, emptyList()),
            ),
            validation = null,
            committed = committed,
            snapshot = snap,
            contexts = ctx,
            recommendation = null,
            speak = speak,
            spokenText = text,
            coachingPersisted = coach,
            workoutPersisted = r is EngineResult.Committed,
            queuedSpeechActs = queuedSpeechActs.toList(),
            claims = statement.claims,
            error = (r as? EngineResult.Rejected)?.let { TurnError.Engine(it.reason) } ?: speechErr,
        )
    }

    fun handleTurn(
        transcript: String,
        evidence: VoiceTurnEvidence = VoiceTurnEvidence(transcript = transcript, isFinal = true, durationMs = 400.0),
    ): TurnResult {
        val before = engine.snapshot(sessionId, plan)
        val ctxBefore = assemble(before)
        val extraction = extractor.extract(
            ExtractionRequest(transcript = transcript, evidence = evidence, contexts = ctxBefore, language = evidence.language),
        )
        if (!extraction.classifiedBeforeExtract) {
            return failed(transcript, extraction, TurnError.Safety("safety did not run first"), before, ctxBefore)
        }
        val route = VoiceCommitRoute(
            audioRoute = evidence.audioRoute ?: AudioRoute.HEADSET,
            talkerCount = evidence.talkerCount,
            sttDurationMs = evidence.durationMs ?: 400.0,
            overlappingVad = evidence.overlappingVad,
            sttFinalHash = evidence.sttFinalHash ?: SttFinalHash.of(transcript),
            recordedAtEpochMs = evidence.recordedAtMs ?: clock.now(),
        )
        val hash = route.sttFinalHash
        val validation = validator.validateTurn(extraction.envelope.candidates, ctxBefore, route)
        var duplicate = false
        val facts: List<ValidatedFact> = when (validation) {
            is ValidationDecision.Accept -> {
                var fs = validation.facts
                if (!extraction.safety.allowSetCompleted) {
                    fs = fs.filter { it !is ValidatedFact.SetCompleted }
                }
                fs
            }
            is ValidationDecision.Drop -> {
                duplicate = validation.reason == "duplicate_stt_final"
                emptyList()
            }
            else -> emptyList()
        }
        var committed = emptyList<WorkoutEvent>()
        var workoutOk = true
        var error: TurnError? = null
        if (facts.isNotEmpty()) {
            try {
                when (val er = engine.applyFacts(sessionId, plan, facts)) {
                    is EngineResult.Committed -> committed = er.events
                    is EngineResult.Rejected -> {
                        workoutOk = false
                        error = TurnError.Engine(er.reason)
                    }
                }
            } catch (e: Exception) {
                workoutOk = false
                error = TurnError.Persistence(e.message ?: "append failed")
            }
        }
        val high = extraction.safety.severity == SafetySeverity.HIGH ||
            extraction.safety.severity == SafetySeverity.CRITICAL
        if (error == null && extraction.safety.pause && high) {
            try {
                val paused = engine.pause(sessionId, plan)
                if (paused is EngineResult.Committed) committed = committed + paused.events
            } catch (e: Exception) {
                error = TurnError.Persistence(e.message ?: "pause persist failed")
            }
        }
        hash?.let { recentHashes += SttFinalRecord(it, route.recordedAtEpochMs) }
        var snap = engine.snapshot(sessionId, plan)
        val ctx = try {
            assemble(snap)
        } catch (e: Exception) {
            error = error ?: TurnError.Context(e.message ?: "assemble failed")
            ctxBefore
        }
        val rec = try {
            Recommend.recommend(
                snap,
                ctx,
                feedbackHard = facts.any { it is ValidatedFact.UserFeedback && it.kind.name == "HARD" },
            )
        } catch (e: Exception) {
            error = error ?: TurnError.Recommendation(e.message ?: "recommend failed")
            null
        }
        val question = extraction.envelope.candidates.any { it.type.name == "USER_QUESTION" }
        val clarify = validation is ValidationDecision.Clarify
        var speak = ShouldSpeak.shouldSpeak(
            ctx, snap, committed, extraction.safety, question, clarify, rec,
            userAskedAdvice = transcript.lowercase().contains("how should i") ||
                transcript.lowercase().contains("approach this set"),
        )
        if (high) {
            speak = SpeakDecision.Speak(SpeechActId.SAFETY, "safety.critical")
        } else if (speak is SpeakDecision.Silence && speak.reason == SilenceReason.SET_ACTIVE) {
            queuedSpeechActs += speak.queued.filter { it !in queuedSpeechActs }
        } else if (queuedSpeechActs.isNotEmpty() && snap.phase in DRAIN_PHASES) {
            val drained = queuedSpeechActs.removeAt(0)
            speak = SpeakDecision.Speak(drained, "speech.${drained.name.lowercase()}")
        }
        var statement = if (high) {
            CoachingStatement.plain(extraction.speech.text)
        } else {
            CoachingPhraser.phrase(speak, extraction.speech, ctx, rec, snap)
        }
        // Anti-hallucination gate: a line whose own claims do not recompute is
        // stripped, not softened (TECHNICAL_SPEC four-context guardrail).
        val violations = ClaimVerifier.verify(statement, ctx)
        if (violations.isNotEmpty()) {
            claimViolations += violations
            statement = CoachingStatement.silent
            speak = SpeakDecision.Silence(SilenceReason.POLICY_SILENT_ACT)
        }
        val spoken = statement.text
        var coachingOk = false
        try {
            coachingOk = persistCoaching(speak, statement, hash ?: transcript)
        } catch (e: Exception) {
            error = error ?: TurnError.Persistence(e.message ?: "coaching persist failed")
        }
        try {
            maybeTts(speak, spoken)
        } catch (e: Exception) {
            error = error ?: TurnError.Speech(e.message ?: "tts failed")
        }
        snap = engine.snapshot(sessionId, plan)
        return TurnResult(
            transcript = transcript,
            safety = extraction.safety,
            safetySpeech = extraction.speech,
            extraction = extraction,
            validation = validation,
            committed = committed,
            snapshot = snap,
            contexts = assemble(snap),
            recommendation = rec,
            speak = speak,
            spokenText = if (speak.speak) spoken else null,
            coachingPersisted = coachingOk,
            workoutPersisted = workoutOk,
            duplicateSuppressed = duplicate,
            error = error,
            queuedSpeechActs = queuedSpeechActs.toList(),
            claims = statement.claims,
            claimViolations = violations,
        )
    }

    fun beginSet(): EngineResult = engine.beginSet(sessionId, plan)

    fun seedWorkingWeight(kg: Double) {
        engine.applyFacts(sessionId, plan, listOf(ValidatedFact.WeightChanged(kg)))
    }

    private fun assemble(snap: com.gymbuddy.domain.engine.WorkoutSessionSnapshot): FourContexts =
        FourContextAssembler.assemble(
            AssemblyRequest(
                snapshot = snap,
                sessionEvents = log.sessionEvents(SessionId(sessionId)),
                history = history,
                profile = profile.copy(userId = userId),
                conversation = ConversationalStore(
                    queuedSpeechActs = queuedSpeechActs.map { it.name.lowercase() },
                    languageDetected = "en",
                    recentSttFinals = recentHashes.toList(),
                    recentCorrections = snap.completedSets.mapNotNull { s ->
                        s.lastCorrectionEventId?.let {
                            com.gymbuddy.domain.context.CorrectionSnapshot(s.setId.value, listOf("reps"), now())
                        }
                    },
                ),
                exerciseNames = exerciseNames,
                workoutName = workoutName,
                voiceState = if (snap.phase == WorkoutSessionState.SET_ACTIVE) {
                    VoiceTurnState.USER_SPEAKING
                } else VoiceTurnState.SILENT,
            ),
        )

    private fun persistCoaching(
        speak: SpeakDecision,
        statement: CoachingStatement,
        hash: String,
    ): Boolean {
        val silence = !speak.speak
        val text = if (silence) null else statement.text
        val act = (speak as? SpeakDecision.Speak)?.actId?.name?.lowercase()
        val event = WorkoutEvent(
            eventId = UUID.nameUUIDFromBytes("coaching|$sessionId|$hash|$silence|$act".toByteArray()).toString(),
            sessionId = sessionId,
            userId = userId,
            occurredAt = now(),
            recordedAt = now(),
            source = EventSource.SYSTEM,
            type = CommittedEventType.COACHING_RESPONSE,
            payload = buildJsonObject {
                if (text == null) put("textSpoken", JsonNull) else put("textSpoken", text)
                put("lang", "en")
                put("silence", silence)
                if (!silence && act != null) put("actId", act)
                // Claim-derived, not "every allowed fact". A line that asserts
                // nothing cites nothing; a line that asserts a delta cites the
                // two rows the delta was computed from.
                put(
                    "citedFacts",
                    buildJsonArray {
                        if (!silence) statement.citedFactIds.forEach { add(JsonPrimitive(it)) }
                    },
                )
            },
        )
        log.append(event)
        return true
    }

    private fun maybeTts(speak: SpeakDecision, text: String?) {
        if (speak.speak && text != null) {
            tts?.speak(text, "en")
        }
    }

    private fun failed(
        transcript: String,
        extraction: com.gymbuddy.domain.extract.ExtractionResult,
        error: TurnError,
        snap: com.gymbuddy.domain.engine.WorkoutSessionSnapshot,
        ctx: FourContexts,
    ) = TurnResult(
        transcript, extraction.safety, extraction.speech, extraction, null, emptyList(), snap, ctx,
        null, SpeakDecision.Silence(SilenceReason.POLICY_SILENT_ACT), null, false, false, error = error,
    )

    private companion object {
        val DRAIN_PHASES = setOf(
            WorkoutSessionState.RESTING,
            WorkoutSessionState.SET_COMPLETED,
            WorkoutSessionState.EXERCISE_COMPLETE,
        )
    }
}
