package com.gymbuddy.app.audio

import android.media.AudioDeviceInfo
import android.os.Build

object AudioRoutes {
    const val ADB_PULL = "adb pull /sdcard/Android/data/com.gymbuddy.app/files/audio-diagnostic/"

    fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TYPE_BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "TYPE_BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "TYPE_BUILTIN_MIC"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "TYPE_WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "TYPE_WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "TYPE_USB_HEADSET"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "TYPE_USB_DEVICE"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "TYPE_BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "TYPE_BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TYPE_TELEPHONY"
        else -> when {
            Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "TYPE_BLE_HEADSET"
            Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> "TYPE_BLE_SPEAKER"
            Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_BROADCAST -> "TYPE_BLE_BROADCAST"
            else -> "TYPE_$type"
        }
    }

    fun kindOf(type: Int): DeviceKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        -> DeviceKind.BUILTIN
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        -> DeviceKind.WIRED_HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> DeviceKind.BLUETOOTH_A2DP
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceKind.BLUETOOTH_SCO
        else -> when {
            Build.VERSION.SDK_INT >= 31 && (
                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                ) -> DeviceKind.BLUETOOTH_LE
            else -> DeviceKind.OTHER
        }
    }

    fun label(typeName: String, productName: String?): String {
        val name = productName?.trim()?.takeIf { it.isNotEmpty() }
        return if (name != null) "$typeName · $name" else typeName
    }

    fun named(type: Int, productName: String?, sampleRates: IntArray? = null): NamedDevice {
        val tn = typeName(type)
        return NamedDevice(
            label = label(tn, productName),
            kind = kindOf(type),
            productName = productName?.trim()?.takeIf { it.isNotEmpty() },
            typeName = tn,
            sampleRates = sampleRates?.filter { it > 0 } ?: emptyList(),
        )
    }

    /**
     * Currently routed output, not "highest ranked device that happens to exist".
     * A connected SCO accessory is listed even while A2DP music is playing.
     */
    fun currentOutput(flags: RouteFlags, outputs: List<NamedDevice>): NamedDevice? {
        flags.communicationDevice?.let { comm ->
            if (flags.scoOn || comm.kind == DeviceKind.BLUETOOTH_SCO || comm.kind == DeviceKind.BLUETOOTH_LE) {
                return comm
            }
        }
        if (flags.scoOn) {
            return outputs.firstOrNull { it.kind == DeviceKind.BLUETOOTH_SCO }
                ?: outputs.firstOrNull { it.kind == DeviceKind.BLUETOOTH_LE }
                ?: flags.communicationDevice
        }
        if (flags.wiredHeadsetOn) {
            outputs.firstOrNull { it.kind == DeviceKind.WIRED_HEADSET }?.let { return it }
        }
        if (flags.a2dpOn) {
            outputs.firstOrNull { it.kind == DeviceKind.BLUETOOTH_A2DP }?.let { return it }
        }
        if (flags.speakerphoneOn) {
            outputs.firstOrNull { it.typeName == "TYPE_BUILTIN_SPEAKER" }?.let { return it }
        }
        return outputs.firstOrNull { it.typeName == "TYPE_BUILTIN_SPEAKER" }
            ?: outputs.firstOrNull { it.kind == DeviceKind.BUILTIN }
            ?: outputs.firstOrNull()
    }

    fun currentInput(flags: RouteFlags, inputs: List<NamedDevice>, routed: NamedDevice?): NamedDevice? {
        if (routed != null) return routed
        if (flags.scoOn) {
            return inputs.firstOrNull { it.kind == DeviceKind.BLUETOOTH_SCO }
                ?: inputs.firstOrNull { it.kind == DeviceKind.BLUETOOTH_LE }
        }
        if (flags.wiredHeadsetOn) {
            inputs.firstOrNull { it.kind == DeviceKind.WIRED_HEADSET }?.let { return it }
        }
        return inputs.firstOrNull { it.typeName == "TYPE_BUILTIN_MIC" }
            ?: inputs.firstOrNull { it.kind == DeviceKind.BUILTIN }
            ?: inputs.firstOrNull()
    }

    fun pickCaptureRate(device: NamedDevice?, sco: Boolean, fallback: Int): Int {
        val rates = device?.sampleRates.orEmpty()
        if (sco) {
            return rates.firstOrNull { it == 16000 }
                ?: rates.firstOrNull { it == 8000 }
                ?: rates.minOrNull()
                ?: 16000
        }
        return rates.firstOrNull { it == fallback } ?: fallback
    }

    fun headsetMicVerdict(
        mode: CaptureMode,
        routed: NamedDevice?,
        scoConnected: Boolean,
        sampleRate: Int?,
    ): HeadsetMicVerdict {
        if (mode != CaptureMode.HEADSET_MIC) {
            val phone = routed == null || routed.kind == DeviceKind.BUILTIN
            return if (phone) {
                HeadsetMicVerdict(true, "phone mic mode routed to ${routed?.label ?: "unknown (likely built-in)"}")
            } else {
                HeadsetMicVerdict(
                    false,
                    "phone mic mode was supposed to keep the handset mic; platform gave ${routed!!.label}",
                )
            }
        }
        if (routed == null) {
            return HeadsetMicVerdict(
                false,
                "headset mic mode opened, but the platform did not report a routed input device",
            )
        }
        if (routed.kind == DeviceKind.BLUETOOTH_SCO || routed.kind == DeviceKind.BLUETOOTH_LE) {
            val rateNote = sampleRate?.let { " at $it Hz" } ?: ""
            val scoNote = if (scoConnected) "SCO connected" else "SCO flag off (LE or OEM path)"
            val suspicious = sampleRate != null && sampleRate >= 44100 && routed.kind == DeviceKind.BLUETOOTH_SCO
            val extra = if (suspicious) {
                " — $sampleRate Hz is unusually high for classic Bluetooth SCO (expect 8 or 16 kHz)"
            } else {
                ""
            }
            return HeadsetMicVerdict(true, "routed to ${routed.label}$rateNote ($scoNote)$extra")
        }
        if (routed.kind == DeviceKind.WIRED_HEADSET) {
            return HeadsetMicVerdict(true, "routed to wired headset mic ${routed.label} (not Bluetooth)")
        }
        return HeadsetMicVerdict(
            false,
            "headset mic mode did not capture from the Bluetooth microphone. " +
                "Platform gave ${routed.label} at ${sampleRate ?: "?"} Hz. " +
                "This phone or these earbuds will not route capture to the headset mic.",
        )
    }

    fun a2dpDropped(a2dpBefore: Boolean, a2dpAfter: Boolean, scoAfter: Boolean): Boolean =
        a2dpBefore && (!a2dpAfter || scoAfter)
}
