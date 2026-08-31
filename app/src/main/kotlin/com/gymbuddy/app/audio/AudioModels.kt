package com.gymbuddy.app.audio

import java.util.Locale

enum class CaptureMode {
    HEADSET_MIC,
    PHONE_MIC_KEEP_A2DP,
}

enum class DeviceKind {
    BUILTIN,
    WIRED_HEADSET,
    BLUETOOTH_A2DP,
    BLUETOOTH_SCO,
    BLUETOOTH_LE,
    OTHER,
}

data class NamedDevice(
    val label: String,
    val kind: DeviceKind,
    val productName: String? = null,
    val typeName: String = "TYPE_UNKNOWN",
    val sampleRates: List<Int> = emptyList(),
)

data class RouteFlags(
    val scoOn: Boolean,
    val a2dpOn: Boolean,
    val speakerphoneOn: Boolean,
    val wiredHeadsetOn: Boolean,
    val communicationDevice: NamedDevice? = null,
)

data class HeadsetMicVerdict(
    val genuine: Boolean,
    val reason: String,
)

data class AudioSnapshot(
    val output: NamedDevice?,
    val input: NamedDevice?,
    val micOpen: Boolean,
    val outputBeforeMic: String? = null,
    val outputAfterMic: String? = null,
    val outputSwitchedOnMicOpen: Boolean? = null,
    val a2dpDroppedOnMicOpen: Boolean? = null,
    val actualSampleRate: Int? = null,
    val actualInput: NamedDevice? = null,
    val actualInputKind: DeviceKind? = null,
    val requestedSampleRate: Int = 44100,
    val captureMode: CaptureMode = CaptureMode.HEADSET_MIC,
    val focusGranted: Boolean? = null,
    val lastError: String? = null,
    val lastMessage: String? = null,
    val recording: Boolean = false,
    val hasClip: Boolean = false,
    val playing: Boolean = false,
    val clipDurationMs: Int? = null,
    val clipSampleRate: Int? = null,
    val clipMode: CaptureMode? = null,
    val scoState: String? = null,
    val a2dpOn: Boolean? = null,
    val scoOn: Boolean? = null,
    val communicationDevice: String? = null,
    val headsetMicGenuine: Boolean? = null,
    val headsetMicReason: String? = null,
    val availableOutputs: String? = null,
    val availableInputs: String? = null,
    val reportPath: String? = null,
    val adbPull: String? = null,
    val refreshedAt: String? = null,
    val runningSequence: Boolean = false,
) {
    val clipLengthLabel: String
        get() {
            val ms = clipDurationMs ?: return "none"
            val sec = ms / 1000.0
            val mode = clipMode?.name ?: "?"
            val rate = clipSampleRate?.let { "$it Hz" } ?: "?"
            return "${String.format(Locale.US, "%.2f", sec)}s · $mode · $rate"
        }

    val playbackLabel: String
        get() = when {
            playing -> "playing · $clipLengthLabel"
            hasClip -> "stopped · $clipLengthLabel"
            else -> "stopped · no clip"
        }
}
