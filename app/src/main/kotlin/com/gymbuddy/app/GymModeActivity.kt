package com.gymbuddy.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.core.content.ContextCompat
import com.gymbuddy.app.audio.AudioProbe
import com.gymbuddy.app.audio.CaptureMode
import com.gymbuddy.app.hud.GymHudSession
import com.gymbuddy.app.hud.HudWriter
import com.gymbuddy.app.hud.LifecycleAction
import com.gymbuddy.app.persist.AndroidEventLog
import com.gymbuddy.app.ui.AudioDiagnosticScreen
import com.gymbuddy.app.ui.FirstSoundScreen
import com.gymbuddy.app.ui.GymModeScreen
import com.gymbuddy.app.ui.gymColors
import com.gymbuddy.app.voice.AndroidClock
import com.gymbuddy.app.voice.AudioFocusDucker
import com.gymbuddy.app.voice.AudioTrackPlayback
import com.gymbuddy.app.voice.ElevenLabsTts
import com.gymbuddy.app.voice.FirstSoundSession
import com.gymbuddy.app.voice.ListenStatus
import com.gymbuddy.app.voice.OkHttpTtsTransport
import com.gymbuddy.app.voice.ScribeListen
import com.gymbuddy.app.voice.SpeakObserver
import com.gymbuddy.app.voice.VoiceCredentialsFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.persist.PersistFixtures
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GymModeActivity : ComponentActivity() {

    private lateinit var log: AndroidEventLog
    private lateinit var engine: WorkoutEngine
    private lateinit var hud: GymHudSession
    private lateinit var probe: AudioProbe
    private lateinit var writer: HudWriter
    private val main = Handler(Looper.getMainLooper())
    private var tts: ElevenLabsTts? = null
    private var session: FirstSoundSession? = null
    private var ducker: AudioFocusDucker? = null

    private enum class Pane { GYM, AUDIO, SPEAK }

    private fun sessionOrCreate(): FirstSoundSession {
        val existing = session
        if (existing != null) return existing
        val clock = AndroidClock()
        val creds = VoiceCredentialsFactory.create(clock)
        val duck = AudioFocusDucker(probe)
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sttClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        val createdTts = ElevenLabsTts(
            credentials = creds,
            clock = clock,
            transport = OkHttpTtsTransport(),
            playback = AudioTrackPlayback(),
            ducker = duck,
            restoreOnFinish = false,
        )
        val created = FirstSoundSession(
            tts = createdTts,
            listen = ScribeListen(
                credentials = creds,
                clock = clock,
                transport = OkHttpTtsTransport(sttClient),
                scope = io,
            ),
            clock = clock,
            ducker = duck,
            scope = io,
        )
        ducker = duck
        tts = createdTts
        session = created
        return created
    }

    private fun micPermissions(): Array<String> {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        return needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = AndroidEventLog(this)
        engine = WorkoutEngine(log, now = { Instant.now().toString() })
        val plan = WorkoutPlan(
            PersistFixtures.WORKOUT_ID,
            listOf(
                PlannedExercise(
                    PersistFixtures.WORKOUT_EXERCISE_ID,
                    PersistFixtures.EXERCISE_ID,
                    plannedSets = 4,
                    restTargetSec = 90,
                ),
            ),
        )
        hud = GymHudSession(
            engine = engine,
            plan = plan,
            userId = PersistFixtures.USER_ID,
            sessionId = PersistFixtures.SESSION_ID,
            exerciseNames = mapOf(PersistFixtures.EXERCISE_ID to "Bench Press"),
            sessionName = "Chest / Triceps",
            voiceAvailable = false,
        )
        probe = AudioProbe(this)
        writer = HudWriter(main)
        writer.run({
            val snap = hud.snapshot()
            if (snap.phase != WorkoutSessionState.IDLE && snap.phase != WorkoutSessionState.WORKOUT_COMPLETE) {
                engine.recover(PersistFixtures.SESSION_ID, plan)
            }
            Triple(hud.ui(), hud.snapshot().workingWeightKg ?: 60.0, probe.snapshot())
        }) { (ui0, w0, audio0) ->
            if (!isDestroyed) bindScreen(ui0, w0, audio0)
        }
    }

    private fun bindScreen(
        ui0: com.gymbuddy.app.hud.HudUiState,
        w0: Double,
        audio0: com.gymbuddy.app.audio.AudioSnapshot,
    ) {
        setContent {
            var pane by remember { mutableStateOf(Pane.GYM) }
            var ui by remember { mutableStateOf(ui0) }
            var weight by remember { mutableStateOf(w0) }
            var reps by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var restLeft by remember { mutableStateOf(ui.restSec) }
            var audio by remember { mutableStateOf(audio0) }
            var speakStatus by remember { mutableStateOf("Idle. Play music from another app first.") }
            val lifecycleFocus = remember { FocusRequester() }
            val padFocus = remember { FocusRequester() }
            val restFocus = remember { FocusRequester() }
            val scope = rememberCoroutineScope()
            val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                audio = probe.refresh()
            }
            val activity = this@GymModeActivity

            fun refreshHud() {
                ui = hud.ui()
                restLeft = ui.restSec
                val w = hud.snapshot().workingWeightKg
                if (w != null) weight = w
            }

            LaunchedEffect(ui.rest.visible, ui.restSec) {
                if (!ui.rest.visible) return@LaunchedEffect
                var left = ui.restSec ?: return@LaunchedEffect
                restLeft = left
                while (left > 0) {
                    delay(1000)
                    left -= 1
                    restLeft = left
                }
            }

            LaunchedEffect(Unit) {
                probe.listener = { next -> main.post { audio = next } }
            }

            MaterialTheme(colorScheme = gymColors()) {
                when (pane) {
                    Pane.GYM -> GymModeScreen(
                        ui = ui,
                        weightKg = weight,
                        repsInput = reps,
                        restRemainingSec = restLeft,
                        error = error,
                        lifecycleFocus = lifecycleFocus,
                        padFocus = padFocus,
                        restFocus = restFocus,
                        onLifecycle = {
                            error = null
                            val action = ui.lifecycleAction
                            writer.run({
                                when (action) {
                                    LifecycleAction.START -> hud.start()
                                    LifecycleAction.PAUSE -> hud.pause()
                                    LifecycleAction.RESUME -> hud.resume()
                                    LifecycleAction.COMPLETE -> hud.complete()
                                }
                            }) { refreshHud() }
                        },
                        onDigit = { d -> if (reps.length < 3) reps += d },
                        onBackspace = { if (reps.isNotEmpty()) reps = reps.dropLast(1) },
                        onLogSet = {
                            val n = reps.toIntOrNull()
                            if (n == null || n <= 0) {
                                error = "Enter reps on the number pad"
                            } else {
                                val w = weight
                                writer.run({ hud.logManualSet(n, w) }) { r ->
                                    when (r) {
                                        is EngineResult.Rejected -> error = r.reason
                                        else -> {
                                            error = null
                                            reps = ""
                                        }
                                    }
                                    refreshHud()
                                }
                            }
                        },
                        onWeightDelta = { delta ->
                            weight = (weight + delta).coerceAtLeast(2.5)
                        },
                        onOpenSpeak = {
                            val missing = micPermissions()
                            if (missing.isNotEmpty()) {
                                perm.launch(missing)
                            } else {
                                val loop = sessionOrCreate()
                                loop.observer = SpeakObserver { line ->
                                    main.post { speakStatus = appendStatus(speakStatus, line) }
                                }
                                loop.arm()
                            }
                            pane = Pane.SPEAK
                        },
                        onOpenAudio = {
                            val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                needed += Manifest.permission.BLUETOOTH_CONNECT
                            }
                            val missing = needed.filter {
                                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isNotEmpty()) perm.launch(missing.toTypedArray())
                            audio = probe.refresh()
                            pane = Pane.AUDIO
                        },
                    )
                    Pane.SPEAK -> FirstSoundScreen(
                        status = speakStatus,
                        onSpeak = {
                            try {
                                val missing = micPermissions()
                                if (missing.isNotEmpty()) {
                                    perm.launch(missing)
                                    speakStatus = ListenStatus.MIC_DENIED
                                } else {
                                    val loop = sessionOrCreate()
                                    loop.observer = SpeakObserver { line ->
                                        main.post {
                                            speakStatus = appendStatus(speakStatus, line)
                                        }
                                    }
                                    tts?.diagnostics?.audioRoute =
                                        probe.refresh().output?.label ?: "unknown"
                                    speakStatus = "Idle."
                                    loop.speak()
                                }
                            } catch (_: Throwable) {
                                speakStatus = "Failed: unexpected error."
                            }
                        },
                        onCancel = {
                            try {
                                session?.cancel()
                            } catch (_: Throwable) {
                                speakStatus = "Failed: unexpected error."
                            }
                        },
                        onCopy = {
                            val text = tts?.diagnostics?.clipboardText()
                                ?: "keyPresent: false\nlastError: none"
                            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("first-sound", text))
                            speakStatus = appendStatus(speakStatus, "Copied.")
                        },
                        onBack = {
                            session?.release()
                            pane = Pane.GYM
                        },
                    )
                    Pane.AUDIO -> AudioDiagnosticScreen(
                        state = audio,
                        onBack = { pane = Pane.GYM },
                        onToggleMic = {
                            scope.launch {
                                val s = withContext(Dispatchers.IO) {
                                    if (probe.snapshot().micOpen) probe.stopMic() else probe.startMic()
                                }
                                audio = s
                            }
                        },
                        onMode = { mode: CaptureMode ->
                            probe.captureMode = mode
                            audio = probe.snapshot()
                        },
                        onRecord = {
                            scope.launch {
                                val s = withContext(Dispatchers.IO) { probe.recordClip() }
                                audio = s
                            }
                        },
                        onPlay = {
                            scope.launch {
                                val s = withContext(Dispatchers.IO) { probe.playClip() }
                                audio = s
                            }
                        },
                        onFocus = { audio = probe.requestDuckFocus() },
                        onAbandonFocus = { audio = probe.abandonFocus() },
                        onRefresh = { audio = probe.refresh() },
                        onRunSequence = {
                            scope.launch {
                                audio = probe.snapshot().copy(runningSequence = true, lastMessage = "Running diagnostic…")
                                val s = withContext(Dispatchers.IO) { probe.runFullSequence() }
                                audio = s
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::writer.isInitialized) writer.shutdown()
        if (::probe.isInitialized) probe.release()
        session?.release()
        tts?.release()
        if (::log.isInitialized) log.close()
        super.onDestroy()
    }
}

private fun appendStatus(current: String, line: String): String {
    val lines = (current.split('\n') + line).filter { it.isNotBlank() && it != "Idle." }
    return lines.takeLast(12).joinToString("\n")
}
