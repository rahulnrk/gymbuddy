package com.gymbuddy.app

import android.Manifest
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
import com.gymbuddy.app.hud.LifecycleAction
import com.gymbuddy.app.persist.AndroidEventLog
import com.gymbuddy.app.ui.AudioDiagnosticScreen
import com.gymbuddy.app.ui.GymModeScreen
import com.gymbuddy.app.ui.gymColors
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.persist.PersistFixtures
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GymModeActivity : ComponentActivity() {

    private lateinit var log: AndroidEventLog
    private lateinit var engine: WorkoutEngine
    private lateinit var hud: GymHudSession
    private lateinit var probe: AudioProbe
    private val main = Handler(Looper.getMainLooper())

    private enum class Pane { GYM, AUDIO }

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
        val snap = hud.snapshot()
        if (snap.phase != WorkoutSessionState.IDLE && snap.phase != WorkoutSessionState.WORKOUT_COMPLETE) {
            engine.recover(PersistFixtures.SESSION_ID, plan)
        }
        probe = AudioProbe(this)

        setContent {
            var pane by remember { mutableStateOf(Pane.GYM) }
            var ui by remember { mutableStateOf(hud.ui()) }
            var weight by remember { mutableStateOf(snap.workingWeightKg ?: 60.0) }
            var reps by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var restLeft by remember { mutableStateOf(ui.restSec) }
            var audio by remember { mutableStateOf(probe.snapshot()) }
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
                            when (ui.lifecycleAction) {
                                LifecycleAction.START -> hud.start()
                                LifecycleAction.PAUSE -> hud.pause()
                                LifecycleAction.RESUME -> hud.resume()
                                LifecycleAction.COMPLETE -> hud.complete()
                            }
                            refreshHud()
                        },
                        onDigit = { d -> if (reps.length < 3) reps += d },
                        onBackspace = { if (reps.isNotEmpty()) reps = reps.dropLast(1) },
                        onLogSet = {
                            val n = reps.toIntOrNull()
                            if (n == null || n <= 0) {
                                error = "Enter reps on the number pad"
                            } else {
                                when (val r = hud.logManualSet(n, weight)) {
                                    is EngineResult.Rejected -> error = r.reason
                                    else -> {
                                        error = null
                                        reps = ""
                                    }
                                }
                            }
                            refreshHud()
                        },
                        onWeightDelta = { delta ->
                            weight = (weight + delta).coerceAtLeast(2.5)
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
        if (::probe.isInitialized) probe.release()
        if (::log.isInitialized) log.close()
        super.onDestroy()
    }
}
