package com.gymbuddy.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import kotlin.concurrent.thread

enum class CaptureMode {
    HEADSET_MIC,
    PHONE_MIC_KEEP_A2DP,
}

enum class DeviceKind {
    BUILTIN,
    WIRED_HEADSET,
    BLUETOOTH_A2DP,
    BLUETOOTH_SCO,
    OTHER,
}

data class NamedDevice(
    val label: String,
    val kind: DeviceKind,
)

data class AudioSnapshot(
    val output: NamedDevice?,
    val input: NamedDevice?,
    val micOpen: Boolean,
    val outputBeforeMic: String? = null,
    val outputAfterMic: String? = null,
    val outputSwitchedOnMicOpen: Boolean? = null,
    val actualSampleRate: Int? = null,
    val actualInputKind: DeviceKind? = null,
    val requestedSampleRate: Int = 44100,
    val captureMode: CaptureMode = CaptureMode.HEADSET_MIC,
    val focusGranted: Boolean? = null,
    val lastError: String? = null,
    val lastMessage: String? = null,
    val recording: Boolean = false,
    val hasClip: Boolean = false,
    val playing: Boolean = false,
)

/**
 * Platform audio diagnostic. No STT, no TTS, no network, no vendor SDKs.
 */
class AudioProbe(context: Context) {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock = Any()
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var clip: ShortArray? = null
    private var outputBefore: String? = null
    private val callbackThread = HandlerThread("audio-devices").also { it.start() }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = notifyChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = notifyChanged()
    }

    var captureMode: CaptureMode = CaptureMode.HEADSET_MIC
    var listener: ((AudioSnapshot) -> Unit)? = null

    init {
        am.registerAudioDeviceCallback(deviceCallback, Handler(callbackThread.looper))
    }

    fun snapshot(): AudioSnapshot = synchronized(lock) {
        val out = devices(AudioManager.GET_DEVICES_OUTPUTS).maxByOrNull { rank(it.kind) }
        val inp = devices(AudioManager.GET_DEVICES_INPUTS).maxByOrNull { rank(it.kind) }
        val rec = record
        AudioSnapshot(
            output = out,
            input = inp,
            micOpen = rec != null && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING,
            outputBeforeMic = outputBefore,
            outputAfterMic = if (outputBefore != null) describe(out) else null,
            outputSwitchedOnMicOpen = if (outputBefore != null) outputBefore != describe(out) else null,
            actualSampleRate = rec?.sampleRate,
            actualInputKind = inp?.kind,
            captureMode = captureMode,
            focusGranted = lastFocus,
            lastError = lastError,
            lastMessage = lastMessage,
            recording = rec != null,
            hasClip = clip != null,
            playing = track?.playState == AudioTrack.PLAYSTATE_PLAYING,
        )
    }

    fun startMic(): AudioSnapshot {
        stopPlayback()
        stopMic()
        outputBefore = describe(devices(AudioManager.GET_DEVICES_OUTPUTS).maxByOrNull { rank(it.kind) })
        applyCaptureMode(openingMic = true)
        val rate = 44100
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) {
            lastError = "AudioRecord buffer unavailable ($min)"
            return snapshot()
        }
        val source = when (captureMode) {
            CaptureMode.HEADSET_MIC -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            CaptureMode.PHONE_MIC_KEEP_A2DP -> MediaRecorder.AudioSource.MIC
        }
        val rec = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        } catch (e: SecurityException) {
            lastError = "Microphone permission denied"
            return snapshot()
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            lastError = "AudioRecord failed to initialize (source=$source)"
            return snapshot()
        }
        rec.startRecording()
        record = rec
        lastError = null
        lastMessage = "Mic open · reported ${rec.sampleRate} Hz"
        Thread.sleep(80)
        return snapshot()
    }

    fun stopMic(): AudioSnapshot {
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
        if (captureMode == CaptureMode.HEADSET_MIC) {
            am.stopBluetoothSco()
            am.isBluetoothScoOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
        lastMessage = "Mic closed"
        return snapshot()
    }

    fun recordClip(seconds: Int = 3): AudioSnapshot {
        val recState = startMic()
        val rec = record ?: return recState.copy(lastError = recState.lastError ?: "mic did not open")
        val n = rec.sampleRate * seconds
        val buf = ShortArray(n)
        var filled = 0
        val tmp = ShortArray(2048)
        val deadline = System.currentTimeMillis() + seconds * 1000L + 400
        while (filled < n && System.currentTimeMillis() < deadline) {
            val r = rec.read(tmp, 0, minOf(tmp.size, n - filled))
            if (r > 0) {
                System.arraycopy(tmp, 0, buf, filled, r)
                filled += r
            }
        }
        clip = buf.copyOf(filled)
        stopMic()
        lastMessage = "Recorded ${filled / 44100.0}s in ${captureMode.name}"
        return snapshot()
    }

    fun playClip(): AudioSnapshot {
        val data = clip
        if (data == null || data.isEmpty()) {
            lastError = "Nothing recorded yet"
            return snapshot()
        }
        stopPlayback()
        val min = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(min, data.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        thread(name = "clip-play") {
            t.write(data, 0, data.size)
            t.stop()
            t.release()
            synchronized(lock) {
                if (track === t) track = null
            }
            notifyChanged()
        }
        lastMessage = "Playing last clip"
        return snapshot()
    }

    fun requestDuckFocus(): AudioSnapshot {
        abandonFocus()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        val r = am.requestAudioFocus(req)
        lastFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        lastMessage = if (lastFocus == true) "Audio focus granted (duck)" else "Audio focus denied ($r)"
        return snapshot()
    }

    fun abandonFocus(): AudioSnapshot {
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
        lastFocus = null
        lastMessage = "Audio focus abandoned"
        return snapshot()
    }

    fun release() {
        stopPlayback()
        stopMic()
        abandonFocus()
        am.unregisterAudioDeviceCallback(deviceCallback)
        callbackThread.quitSafely()
    }

    private var lastFocus: Boolean? = null
    private var lastError: String? = null
    private var lastMessage: String? = null

    private fun applyCaptureMode(openingMic: Boolean) {
        when (captureMode) {
            CaptureMode.HEADSET_MIC -> {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = false
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
            }
            CaptureMode.PHONE_MIC_KEEP_A2DP -> {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
                am.mode = AudioManager.MODE_NORMAL
                if (openingMic) {
                    lastMessage = "Keeping high-quality output; capturing phone mic"
                }
            }
        }
    }

    private fun stopPlayback() {
        track?.run {
            runCatching { stop() }
            release()
        }
        track = null
    }

    private fun devices(kind: Int): List<NamedDevice> =
        am.getDevices(kind).map { d ->
            NamedDevice(label = label(d), kind = kindOf(d))
        }

    private fun label(d: AudioDeviceInfo): String {
        val type = kindOf(d).wireName()
        val name = if (Build.VERSION.SDK_INT >= 28) d.productName?.toString()?.takeIf { it.isNotBlank() } else null
        return if (name != null) "$type · $name" else type
    }

    private fun kindOf(d: AudioDeviceInfo): DeviceKind = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        -> DeviceKind.BUILTIN
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> DeviceKind.WIRED_HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> DeviceKind.BLUETOOTH_A2DP
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> DeviceKind.BLUETOOTH_SCO
        else -> DeviceKind.OTHER
    }

    private fun DeviceKind.wireName(): String = when (this) {
        DeviceKind.BUILTIN -> "built-in"
        DeviceKind.WIRED_HEADSET -> "wired headset"
        DeviceKind.BLUETOOTH_A2DP -> "Bluetooth A2DP"
        DeviceKind.BLUETOOTH_SCO -> "Bluetooth SCO"
        DeviceKind.OTHER -> "other"
    }

    private fun rank(kind: DeviceKind): Int = when (kind) {
        DeviceKind.BLUETOOTH_SCO -> 4
        DeviceKind.BLUETOOTH_A2DP -> 3
        DeviceKind.WIRED_HEADSET -> 2
        DeviceKind.BUILTIN -> 1
        DeviceKind.OTHER -> 0
    }

    private fun describe(d: NamedDevice?) = d?.label

    private fun notifyChanged() {
        listener?.invoke(snapshot())
    }
}
