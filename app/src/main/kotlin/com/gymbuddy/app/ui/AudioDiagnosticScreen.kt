package com.gymbuddy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymbuddy.app.audio.AudioSnapshot
import com.gymbuddy.app.audio.CaptureMode
import com.gymbuddy.app.audio.DeviceKind

@Composable
fun AudioDiagnosticScreen(
    state: AudioSnapshot,
    onBack: () -> Unit,
    onToggleMic: () -> Unit,
    onMode: (CaptureMode) -> Unit,
    onRecord: () -> Unit,
    onPlay: () -> Unit,
    onFocus: () -> Unit,
    onAbandonFocus: () -> Unit,
    onRefresh: () -> Unit,
    onRunSequence: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .defaultMinSize(minHeight = MinTap)
                .semantics { contentDescription = "Back to Gym Mode" },
        ) { Text("‹ Gym Mode", color = Accent, fontSize = 16.sp) }

        Text("Audio diagnostic", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "No speech recognition. Platform route, mic, and focus only.",
            color = Mute,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        RowLine("Refreshed", state.refreshedAt ?: "not yet — tap Refresh or wait for a route change")
        RowLine("Output (current route)", state.output?.label ?: "none")
        RowLine("Output kind", kindName(state.output?.kind))
        RowLine("A2DP on", yn(state.a2dpOn))
        RowLine("SCO on", yn(state.scoOn))
        RowLine("SCO state", state.scoState ?: "—")
        RowLine("Communication device", state.communicationDevice ?: "—")
        RowLine("Available outputs", state.availableOutputs ?: "—")
        RowLine("Available inputs", state.availableInputs ?: "—")
        RowLine("Likely input (not capturing)", if (state.micOpen) "mic is open — see routed capture" else state.input?.label ?: "none")
        RowLine("Routed capture input", state.actualInput?.label ?: if (state.micOpen) "unreported" else "mic closed")
        RowLine("Routed capture type", kindName(state.actualInputKind))
        RowLine("Mic", if (state.micOpen) "open" else "closed")
        RowLine("Before mic open", state.outputBeforeMic ?: "—")
        RowLine("After mic open", state.outputAfterMic ?: "—")
        RowLine(
            "Output switched when mic opened",
            when (state.outputSwitchedOnMicOpen) {
                null -> "not measured yet"
                true -> "YES — output route changed"
                false -> "no"
            },
        )
        RowLine(
            "High-quality Bluetooth dropped",
            when (state.a2dpDroppedOnMicOpen) {
                null -> "not measured yet"
                true -> "YES — left A2DP"
                false -> "no"
            },
        )
        RowLine("Requested sample rate", "${state.requestedSampleRate} Hz")
        RowLine("Actual capture sample rate", state.actualSampleRate?.let { "$it Hz" } ?: "—")
        RowLine(
            "Headset mic genuine",
            when (state.headsetMicGenuine) {
                true -> "YES"
                false -> "NO — not the Bluetooth microphone"
                null -> "not measured yet"
            },
        )
        RowLine("Headset mic detail", state.headsetMicReason ?: "—")
        RowLine(
            "Audio focus (duck)",
            when (state.focusGranted) {
                true -> "granted"
                false -> "denied"
                null -> "not requested"
            },
        )
        RowLine("Last clip", state.clipLengthLabel)
        RowLine("Playback", state.playbackLabel)

        Spacer(Modifier.height(16.dp))
        Text("Capture mode", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Headset mic waits for Bluetooth SCO/LE and refuses the phone mic if that route never engages. Phone mic keeps A2DP.",
            color = Mute,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ModeBtn(
            selected = state.captureMode == CaptureMode.HEADSET_MIC,
            label = "Headset microphone (SCO)",
            description = "Use the earbud microphone",
            onClick = { onMode(CaptureMode.HEADSET_MIC) },
        )
        Spacer(Modifier.height(8.dp))
        ModeBtn(
            selected = state.captureMode == CaptureMode.PHONE_MIC_KEEP_A2DP,
            label = "Phone mic, keep A2DP",
            description = "Keep high-quality music; capture from the phone microphone",
            onClick = { onMode(CaptureMode.PHONE_MIC_KEEP_A2DP) },
        )

        Spacer(Modifier.height(16.dp))
        Action("Start / stop microphone", onToggleMic)
        Action("Record 3 seconds", onRecord, enabled = !state.runningSequence)
        Action(
            if (state.playing) "Playing… tap to restart" else "Play last recording",
            onPlay,
        )
        Action("Request ducking audio focus", onFocus)
        Action("Abandon audio focus", onAbandonFocus)
        Action("Refresh devices", onRefresh)
        Action(
            if (state.runningSequence) "Running diagnostic…" else "Run full diagnostic (writes report)",
            onRunSequence,
            enabled = !state.runningSequence,
        )

        if (!state.reportPath.isNullOrBlank()) {
            Text("Report: ${state.reportPath}", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        if (!state.adbPull.isNullOrBlank()) {
            Text(state.adbPull!!, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (!state.lastMessage.isNullOrBlank()) {
            Text(state.lastMessage!!, color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        }
        if (!state.lastError.isNullOrBlank()) {
            Text(state.lastError!!, color = Danger, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RowLine(k: String, v: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, color = Mute, fontSize = 12.sp)
        Text(v, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModeBtn(selected: Boolean, label: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Card,
            contentColor = if (selected) AccentInk else Ink,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTap)
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Ink),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .defaultMinSize(minHeight = MinTap)
            .semantics { contentDescription = label },
    ) { Text(label) }
}

private fun kindName(kind: DeviceKind?): String = when (kind) {
    DeviceKind.BUILTIN -> "built-in"
    DeviceKind.WIRED_HEADSET -> "wired headset"
    DeviceKind.BLUETOOTH_A2DP -> "Bluetooth A2DP"
    DeviceKind.BLUETOOTH_SCO -> "Bluetooth SCO"
    DeviceKind.BLUETOOTH_LE -> "Bluetooth LE"
    DeviceKind.OTHER -> "other"
    null -> "—"
}

private fun yn(v: Boolean?): String = when (v) {
    true -> "yes"
    false -> "no"
    null -> "—"
}
