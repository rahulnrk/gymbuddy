package com.gymbuddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymbuddy.app.hud.HudUiState
import com.gymbuddy.app.hud.LifecycleAction
import com.gymbuddy.app.hud.VoiceIndicator

@Composable
fun GymModeScreen(
    ui: HudUiState,
    weightKg: Double,
    repsInput: String,
    restRemainingSec: Int?,
    error: String?,
    lifecycleFocus: FocusRequester,
    padFocus: FocusRequester,
    restFocus: FocusRequester,
    onLifecycle: () -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onLogSet: () -> Unit,
    onWeightDelta: (Double) -> Unit,
    onOpenAudio: () -> Unit,
) {
    val lifecycle = ui.controls.first { it.id == "lifecycle" }
    val pad = ui.controls.first { it.id == "numberPad" }
    val rest = ui.controls.first { it.id == "rest" }
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(ui.kicker, color = Mute, fontSize = 14.sp)
        Text(
            ui.exerciseName,
            color = Ink,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Weight", ui.weightLabel, Modifier.weight(1f))
            Metric("Set", ui.setLabel, Modifier.weight(1f))
            Metric("Target", if (ui.targetRepsLabel == "—") "${ui.targetSets} sets" else ui.targetRepsLabel, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("Previous: ${ui.previousPerformance}", color = Ink, fontSize = 16.sp)
        Text(ui.progress, color = Mute, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Voice: ${voiceLabel(ui.voice)}",
            color = Ink,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (ui.micLabel.isNotBlank()) {
            Text(ui.micLabel, color = Mute, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLifecycle,
            enabled = lifecycle.enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentInk),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTap)
                .focusRequester(lifecycleFocus)
                .focusProperties { next = padFocus }
                .semantics { contentDescription = lifecycle.label },
        ) {
            Text(lifecycleCta(ui.lifecycleAction), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusRequester(padFocus)
                .focusProperties { next = restFocus; previous = lifecycleFocus }
                .semantics { contentDescription = pad.label },
        ) {
            Text("Log a set", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Weight ${trimKg(weightKg)} kg", color = Ink, fontSize = 16.sp)
                Row {
                    MiniBtn("− 2.5", enabled = pad.enabled) { onWeightDelta(-2.5) }
                    Spacer(Modifier.padding(6.dp))
                    MiniBtn("+ 2.5", enabled = pad.enabled) { onWeightDelta(2.5) }
                }
            }
            Text(
                if (repsInput.isEmpty()) "Reps" else repsInput,
                color = Ink,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("⌫", "0", "LOG"),
            )
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        val log = key == "LOG"
                        Button(
                            onClick = {
                                when (key) {
                                    "⌫" -> onBackspace()
                                    "LOG" -> onLogSet()
                                    else -> onDigit(key)
                                }
                            },
                            enabled = pad.enabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (log) Accent else Card,
                                contentColor = if (log) AccentInk else Ink,
                                disabledContainerColor = Card.copy(alpha = 0.4f),
                                disabledContentColor = Mute,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = MinTap)
                                .semantics {
                                    contentDescription = when (key) {
                                        "⌫" -> "Delete digit"
                                        "LOG" -> "Log set with number pad"
                                        else -> "Digit $key"
                                    }
                                },
                        ) { Text(key, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        val restSec = restRemainingSec ?: ui.restSec
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, if (ui.rest.visible) Accent else Card, RoundedCornerShape(12.dp))
                .background(Card, RoundedCornerShape(12.dp))
                .defaultMinSize(minHeight = MinTap)
                .padding(14.dp)
                .focusRequester(restFocus)
                .focusProperties { previous = padFocus }
                .semantics { contentDescription = rest.label },
        ) {
            Column {
                Text(ui.rest.kindLabel, color = Mute, fontSize = 13.sp)
                Text(
                    if (ui.rest.visible) formatRest(restSec ?: 0) else "No rest",
                    color = Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (ui.rest.message.isNotBlank()) {
                    Text(ui.rest.message, color = Ink, fontSize = 14.sp)
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Text(error, color = Danger, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = onOpenAudio,
            modifier = Modifier
                .defaultMinSize(minHeight = MinTap)
                .semantics { contentDescription = "Open audio diagnostic" },
        ) {
            Text("Audio diagnostic", color = Accent, fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Metric(k: String, v: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Card, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(k, color = Mute, fontSize = 12.sp)
        Text(v, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Ink),
        modifier = Modifier.defaultMinSize(minWidth = MinTap, minHeight = MinTap),
    ) { Text(label) }
}

private fun lifecycleCta(action: LifecycleAction) = when (action) {
    LifecycleAction.START -> "Start workout"
    LifecycleAction.PAUSE -> "Pause"
    LifecycleAction.RESUME -> "Resume"
    LifecycleAction.COMPLETE -> "Finish"
}

private fun voiceLabel(v: VoiceIndicator) = when (v) {
    VoiceIndicator.LISTENING -> "listening"
    VoiceIndicator.THINKING -> "thinking"
    VoiceIndicator.SPEAKING -> "speaking"
    VoiceIndicator.SILENT -> "silent"
    VoiceIndicator.UNAVAILABLE -> "unavailable (this build is silent)"
}

private fun trimKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

private fun formatRest(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    return if (s >= 60) "${s / 60}:${(s % 60).toString().padStart(2, '0')}" else s.toString()
}
