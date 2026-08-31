package com.gymbuddy.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

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
    private var clip: Clip? = null
    private var outputBefore: String? = null
    private var a2dpBefore: Boolean? = null
    private var routedInput: NamedDevice? = null
    private var requestedRate: Int = 44100
    private var lastFocus: Boolean? = null
    private var lastError: String? = null
    private var lastMessage: String? = null
    private var scoState: String = "unknown"
    private var reportPath: String? = null
    private var refreshedAt: String? = null
    private var runningSequence: Boolean = false
    private var lastVerdict: HeadsetMicVerdict? = null
    private val sequenceLog = DiagnosticLog()
    private val scoWait = AtomicReference<CountDownLatch?>(null)
    private val callbackThread = HandlerThread("audio-devices").also { it.start() }
    private val callbackHandler = Handler(callbackThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executor { r -> callbackHandler.post(r) }
    private var communicationListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    private var modeListener: AudioManager.OnModeChangedListener? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
            log("DEVICES_ADDED ${added.orEmpty().joinToString { describeDevice(it) }}")
            notifyChanged()
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
            log("DEVICES_REMOVED ${removed.orEmpty().joinToString { describeDevice(it) }}")
            notifyChanged()
        }
    }

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return
            val st = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            scoState = scoName(st)
            log("SCO_STATE $scoState")
            if (st == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                scoWait.get()?.countDown()
            }
            notifyChanged()
        }
    }

    var captureMode: CaptureMode = CaptureMode.HEADSET_MIC
        set(value) {
            field = value
            log("MODE ${value.name}")
            lastMessage = "Capture mode ${value.name}"
            notifyChanged()
        }

    var listener: ((AudioSnapshot) -> Unit)? = null

    init {
        am.registerAudioDeviceCallback(deviceCallback, callbackHandler)
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(scoReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(scoReceiver, filter)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            communicationListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                log("COMMUNICATION_DEVICE ${device?.let { describeDevice(it) } ?: "null"}")
                notifyChanged()
            }
            modeListener = AudioManager.OnModeChangedListener { mode ->
                log("AUDIO_MODE ${modeName(mode)}")
                notifyChanged()
            }
            am.addOnCommunicationDeviceChangedListener(callbackExecutor, communicationListener!!)
            am.addOnModeChangedListener(callbackExecutor, modeListener!!)
        }
    }

    fun snapshot(): AudioSnapshot = synchronized(lock) {
        val flags = flags()
        val outs = deviceList(AudioManager.GET_DEVICES_OUTPUTS)
        val ins = deviceList(AudioManager.GET_DEVICES_INPUTS)
        val out = AudioRoutes.currentOutput(flags, outs)
        val rec = record
        val actual = rec?.let { routedOf(it) } ?: routedInput
        val inp = AudioRoutes.currentInput(flags, ins, actual)
        val c = clip
        AudioSnapshot(
            output = out,
            input = inp,
            micOpen = rec != null && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING,
            outputBeforeMic = outputBefore,
            outputAfterMic = if (outputBefore != null) out?.label else null,
            outputSwitchedOnMicOpen = if (outputBefore != null) outputBefore != out?.label else null,
            a2dpDroppedOnMicOpen = if (a2dpBefore != null) {
                AudioRoutes.a2dpDropped(a2dpBefore == true, flags.a2dpOn, flags.scoOn)
            } else {
                null
            },
            actualSampleRate = rec?.sampleRate ?: c?.sampleRate,
            actualInput = actual,
            actualInputKind = actual?.kind,
            requestedSampleRate = requestedRate,
            captureMode = captureMode,
            focusGranted = lastFocus,
            lastError = lastError,
            lastMessage = lastMessage,
            recording = rec != null,
            hasClip = c != null && c.pcm.isNotEmpty(),
            playing = track?.playState == AudioTrack.PLAYSTATE_PLAYING,
            clipDurationMs = c?.let { PcmWav.durationMs(it.pcm.size, it.sampleRate) },
            clipSampleRate = c?.sampleRate,
            clipMode = c?.mode,
            scoState = scoState,
            a2dpOn = flags.a2dpOn,
            scoOn = flags.scoOn,
            communicationDevice = flags.communicationDevice?.label,
            headsetMicGenuine = lastVerdict?.genuine,
            headsetMicReason = lastVerdict?.reason,
            availableOutputs = outs.joinToString { it.label }.ifBlank { "none" },
            availableInputs = ins.joinToString { it.label }.ifBlank { "none" },
            reportPath = reportPath,
            adbPull = AudioRoutes.ADB_PULL,
            refreshedAt = refreshedAt,
            runningSequence = runningSequence,
        )
    }

    fun refresh(): AudioSnapshot {
        refreshedAt = clock()
        val s = snapshot()
        lastError = null
        lastMessage = "Refreshed $refreshedAt · output=${s.output?.label ?: "none"} · " +
            "input=${s.input?.label ?: "none"} · sco=${s.scoOn} a2dp=${s.a2dpOn} " +
            "comm=${s.communicationDevice ?: "none"} · scoState=$scoState"
        log(lastMessage!!)
        return snapshot()
    }

    fun startMic(): AudioSnapshot {
        stopPlayback()
        stopMic(logClose = false)
        lastError = null
        lastVerdict = null
        routedInput = null
        val flagsBefore = flags()
        outputBefore = AudioRoutes.currentOutput(flagsBefore, deviceList(AudioManager.GET_DEVICES_OUTPUTS))?.label
        a2dpBefore = flagsBefore.a2dpOn
        log("OUTPUT_BEFORE $outputBefore a2dpOn=${flagsBefore.a2dpOn} scoOn=${flagsBefore.scoOn}")

        val prepared = prepareCapture()
        if (!prepared.ok) {
            lastError = prepared.error
            lastMessage = prepared.error
            log("MIC_OPEN_FAILED ${prepared.error}")
            revertHeadsetAudio()
            return snapshot()
        }
        requestedRate = prepared.rate
        val rec = try {
            buildRecord(prepared.source, prepared.rate, prepared.preferred, prepared.requirePreferred)
        } catch (e: SecurityException) {
            lastError = "Microphone permission denied"
            log("MIC_PERMISSION_DENIED")
            revertHeadsetAudio()
            return snapshot()
        } catch (e: Exception) {
            lastError = "AudioRecord threw ${e.javaClass.simpleName}: ${e.message}"
            log(lastError!!)
            revertHeadsetAudio()
            return snapshot()
        }
        if (rec == null) {
            lastError = "AudioRecord failed to initialize (source=${prepared.source} rate=${prepared.rate})"
            log(lastError!!)
            revertHeadsetAudio()
            return snapshot()
        }
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            lastError = "AudioRecord did not enter RECORDSTATE_RECORDING"
            log(lastError!!)
            revertHeadsetAudio()
            return snapshot()
        }
        record = rec
        Thread.sleep(120)
        val routed = routedOf(rec)
        routedInput = routed
        val flagsAfter = flags()
        val outAfter = AudioRoutes.currentOutput(flagsAfter, deviceList(AudioManager.GET_DEVICES_OUTPUTS))
        log(
            "MIC_OPEN mode=${captureMode.name} requestedRate=${prepared.rate} " +
                "actualRate=${rec.sampleRate} routed=${routed?.label ?: "unreported"} " +
                "OUTPUT_AFTER ${outAfter?.label} a2dpOn=${flagsAfter.a2dpOn} scoOn=${flagsAfter.scoOn}",
        )
        if (captureMode == CaptureMode.HEADSET_MIC) {
            lastVerdict = AudioRoutes.headsetMicVerdict(
                captureMode,
                routed,
                flagsAfter.scoOn || scoState == "CONNECTED",
                rec.sampleRate,
            )
            if (lastVerdict?.genuine != true) {
                val reason = lastVerdict?.reason ?: "headset mic not genuine"
                lastError = reason
                lastMessage = reason
                log("HEADSET_MIC_FALLBACK_REJECTED $reason")
                stopMic(logClose = false)
                return snapshot()
            }
        } else {
            val phone = AudioRoutes.headsetMicVerdict(captureMode, routed, false, rec.sampleRate)
            log("PHONE_MIC_VERDICT genuine=${phone.genuine} ${phone.reason}")
            if (!phone.genuine) {
                lastError = phone.reason
                lastMessage = phone.reason
            }
        }
        lastMessage = "Mic open · ${routed?.label ?: "unreported input"} · ${rec.sampleRate} Hz" +
            (lastVerdict?.reason?.let { " · $it" } ?: "")
        return snapshot()
    }

    fun stopMic(logClose: Boolean = true): AudioSnapshot {
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
        revertHeadsetAudio()
        if (logClose) {
            lastMessage = "Mic closed"
            log("MIC_CLOSED")
        }
        return snapshot()
    }

    fun recordClip(seconds: Int = 3): AudioSnapshot {
        val recState = startMic()
        val rec = record ?: return recState.copy(lastError = recState.lastError ?: "mic did not open")
        val rate = rec.sampleRate
        val n = rate * seconds
        val buf = ShortArray(n.coerceAtLeast(1))
        var filled = 0
        val tmp = ShortArray(2048)
        val deadline = System.currentTimeMillis() + seconds * 1000L + 800
        while (filled < n && System.currentTimeMillis() < deadline) {
            val r = rec.read(tmp, 0, minOf(tmp.size, n - filled))
            if (r > 0) {
                System.arraycopy(tmp, 0, buf, filled, r)
                filled += r
            } else if (r < 0) {
                lastError = "AudioRecord.read failed ($r)"
                break
            }
        }
        val captured = Clip(buf.copyOf(filled), rate, captureMode, routedInput?.label)
        clip = captured
        val dur = PcmWav.durationMs(filled, rate) / 1000.0
        log("RECORD samples=$filled rate=$rate duration=${"%.2f".format(dur)}s mode=${captureMode.name} routed=${captured.routed}")
        stopMic()
        if (filled == 0) {
            lastError = lastError ?: "Recorded 0 samples"
            lastMessage = lastError
        } else {
            lastMessage = "Recorded ${"%.2f".format(dur)}s at $rate Hz from ${captured.routed ?: "unreported"} (${captureMode.name})"
        }
        return snapshot()
    }

    fun playClip(): AudioSnapshot {
        val data = clip
        if (data == null || data.pcm.isEmpty()) {
            lastError = "Nothing recorded yet"
            lastMessage = lastError
            log("PLAY_FAILED nothing recorded")
            return snapshot()
        }
        stopPlayback()
        lastError = null
        try {
            @Suppress("DEPRECATION")
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
        val rate = data.sampleRate
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) {
            lastError = "AudioTrack buffer unavailable ($min) at $rate Hz"
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(min, data.pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            lastError = "AudioTrack.Builder failed: ${e.message}"
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            lastError = "AudioTrack failed to initialize (state=${t.state}) at $rate Hz"
            t.release()
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        requestPlaybackFocus()
        preferCurrentOutput(t)
        t.setVolume(1.0f)
        val written = t.write(data.pcm, 0, data.pcm.size)
        if (written < 0) {
            lastError = "AudioTrack.write failed ($written)"
            t.release()
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        if (written == 0) {
            lastError = "AudioTrack.write wrote 0 samples"
            t.release()
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        track = t
        try {
            t.play()
        } catch (e: Exception) {
            lastError = "AudioTrack.play() threw: ${e.message}"
            t.release()
            track = null
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
            lastError = "AudioTrack.play() did not start (playState=${t.playState})"
            t.release()
            track = null
            log("PLAY_FAILED $lastError")
            return snapshot()
        }
        val durationMs = PcmWav.durationMs(data.pcm.size, rate)
        lastMessage = "Playing ${data.mode.name} clip · ${"%.2f".format(durationMs / 1000.0)}s at $rate Hz through current output"
        log(lastMessage!!)
        val playingTrack = t
        thread(name = "clip-play") {
            try {
                val end = System.currentTimeMillis() + durationMs + 80
                while (System.currentTimeMillis() < end && track === playingTrack) {
                    if (playingTrack.playState != AudioTrack.PLAYSTATE_PLAYING) break
                    Thread.sleep(40)
                }
            } catch (e: Exception) {
                lastError = "Playback failed: ${e.message}"
                log("PLAY_FAILED $lastError")
            } finally {
                runCatching { playingTrack.stop() }
                runCatching { playingTrack.release() }
                synchronized(lock) {
                    if (track === playingTrack) track = null
                }
                lastMessage = "Playback stopped · ${data.mode.name} · ${"%.2f".format(durationMs / 1000.0)}s"
                log(lastMessage!!)
                notifyChanged()
            }
        }
        return snapshot()
    }

    fun requestDuckFocus(): AudioSnapshot {
        abandonFocus(logIt = false)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                log("FOCUS_CHANGE $change")
                notifyChanged()
            }
            .build()
        focusRequest = req
        val r = am.requestAudioFocus(req)
        lastFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        lastMessage = if (lastFocus == true) "Audio focus granted (duck)" else "Audio focus denied ($r)"
        log("FOCUS_REQUEST duck result=$r granted=${lastFocus}")
        return snapshot()
    }

    fun abandonFocus(logIt: Boolean = true): AudioSnapshot {
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
        lastFocus = null
        if (logIt) {
            lastMessage = "Audio focus abandoned"
            log("FOCUS_ABANDON")
        }
        return snapshot()
    }

    fun runFullSequence(seconds: Int = 3): AudioSnapshot {
        runningSequence = true
        sequenceLog.clear()
        lastError = null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = DiagnosticFiles.newRunDir(app.getExternalFilesDir(null) ?: app.filesDir, stamp)
        reportPath = dir.absolutePath
        log("SEQUENCE_START dir=$reportPath")
        log("PHONE ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
        val initial = refresh()
        log("INITIAL output=${initial.output?.label} input=${initial.input?.label} " +
            "a2dp=${initial.a2dpOn} sco=${initial.scoOn} comm=${initial.communicationDevice}")
        log("AVAILABLE_OUTPUTS ${initial.availableOutputs}")
        log("AVAILABLE_INPUTS ${initial.availableInputs}")

        captureMode = CaptureMode.HEADSET_MIC
        val headset = recordClip(seconds)
        val headsetClip = clip
        if (headsetClip != null && headsetClip.pcm.isNotEmpty()) {
            val wav = File(dir, "headset_mic.wav")
            PcmWav.write(wav, headsetClip.pcm, headsetClip.sampleRate)
            log("SAVED ${wav.name} ${headsetClip.pcm.size} samples ${headsetClip.sampleRate} Hz")
        } else {
            log("HEADSET_MIC_NO_CLIP ${headset.lastError ?: "unknown"}")
        }
        log("HEADSET_MIC_VERDICT genuine=${headset.headsetMicGenuine} ${headset.headsetMicReason}")

        captureMode = CaptureMode.PHONE_MIC_KEEP_A2DP
        val phone = recordClip(seconds)
        val phoneClip = clip
        if (phoneClip != null && phoneClip.pcm.isNotEmpty()) {
            val wav = File(dir, "phone_mic_keep_a2dp.wav")
            PcmWav.write(wav, phoneClip.pcm, phoneClip.sampleRate)
            log("SAVED ${wav.name} ${phoneClip.pcm.size} samples ${phoneClip.sampleRate} Hz")
        } else {
            log("PHONE_MIC_NO_CLIP ${phone.lastError ?: "unknown"}")
        }

        requestDuckFocus()
        Thread.sleep(400)
        abandonFocus()

        val header = linkedMapOf(
            "started" to stamp,
            "phone" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "sdk" to Build.VERSION.SDK_INT.toString(),
            "headset_genuine" to (headset.headsetMicGenuine?.toString() ?: "n/a"),
            "headset_rate" to (headsetClip?.sampleRate?.toString() ?: "n/a"),
            "headset_input" to (headset.actualInput?.label ?: headset.lastError ?: "n/a"),
            "phone_rate" to (phoneClip?.sampleRate?.toString() ?: "n/a"),
            "phone_input" to (phone.actualInput?.label ?: phone.lastError ?: "n/a"),
            "dir" to dir.absolutePath,
        )
        val report = DiagnosticFiles.writeReport(dir, header, sequenceLog)
        runningSequence = false
        log("SEQUENCE_DONE ${report.absolutePath}")
        reportPath = report.absolutePath
        lastMessage = "Report written: ${report.absolutePath}\n${AudioRoutes.ADB_PULL}"
        return snapshot()
    }

    fun release() {
        listener = null
        stopPlayback()
        stopMic(logClose = false)
        abandonFocus(logIt = false)
        runCatching { am.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { app.unregisterReceiver(scoReceiver) }
        if (Build.VERSION.SDK_INT >= 31) {
            communicationListener?.let { am.removeOnCommunicationDeviceChangedListener(it) }
            modeListener?.let { am.removeOnModeChangedListener(it) }
        }
        callbackThread.quitSafely()
    }

    private data class Clip(
        val pcm: ShortArray,
        val sampleRate: Int,
        val mode: CaptureMode,
        val routed: String?,
    )

    private data class PreparedCapture(
        val ok: Boolean,
        val error: String? = null,
        val source: Int = MediaRecorder.AudioSource.MIC,
        val rate: Int = 44100,
        val preferred: AudioDeviceInfo? = null,
        val requirePreferred: Boolean = false,
    )

    private fun prepareCapture(): PreparedCapture {
        return when (captureMode) {
            CaptureMode.HEADSET_MIC -> prepareHeadset()
            CaptureMode.PHONE_MIC_KEEP_A2DP -> preparePhoneMic()
        }
    }

    private fun prepareHeadset(): PreparedCapture {
        val wired = rawDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            AudioRoutes.kindOf(it.type) == DeviceKind.WIRED_HEADSET
        }
        val scoIn = rawDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || bleHeadset(it)
        }
        val scoOut = rawDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || bleHeadset(it)
        }
        val btPresent = scoIn != null || scoOut != null ||
            rawDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

        if (wired != null && !btPresent) {
            val named = toNamed(wired)
            val rate = AudioRoutes.pickCaptureRate(named, sco = false, fallback = 44100)
            log("HEADSET_PATH wired ${named.label} rate=$rate")
            @Suppress("DEPRECATION")
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false
            return PreparedCapture(
                true,
                source = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                rate = rate,
                preferred = wired,
                requirePreferred = true,
            )
        }
        if (!btPresent && wired == null) {
            return PreparedCapture(
                false,
                "No headset microphone available (no Bluetooth SCO/LE device and no wired headset). " +
                    "Not falling back to the phone mic.",
            )
        }
        @Suppress("DEPRECATION")
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = false
        val comm = setBluetoothCommunicationDevice(scoOut ?: scoIn)
        val scoOk = waitForSco()
        val connected = scoOk || scoState == "CONNECTED" || commDeviceIsBluetooth() || comm
        if (!connected) {
            return PreparedCapture(
                false,
                "Bluetooth SCO did not connect. Headset microphone is not available on this phone or these earbuds. " +
                    "Not falling back to the phone mic.",
            )
        }
        val preferred = rawDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || bleHeadset(it)
        } ?: scoIn
        if (preferred == null) {
            return PreparedCapture(
                false,
                "SCO connected but no Bluetooth input device is listed. Not falling back to the phone mic.",
            )
        }
        val named = toNamed(preferred)
        val rate = AudioRoutes.pickCaptureRate(named, sco = true, fallback = 16000)
        log("HEADSET_PATH bluetooth ${named.label} rate=$rate scoState=$scoState commSet=$comm")
        return PreparedCapture(
            true,
            source = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            rate = rate,
            preferred = preferred,
            requirePreferred = true,
        )
    }

    private fun preparePhoneMic(): PreparedCapture {
        revertHeadsetAudio()
        @Suppress("DEPRECATION")
        am.mode = AudioManager.MODE_NORMAL
        val builtin = rawDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        }
        val named = builtin?.let { toNamed(it) }
        val rate = AudioRoutes.pickCaptureRate(named, sco = false, fallback = 44100)
        log("PHONE_MIC_PATH ${named?.label ?: "default MIC"} rate=$rate (SCO off, MODE_NORMAL)")
        lastMessage = "Keeping high-quality output; capturing phone mic"
        return PreparedCapture(
            true,
            source = MediaRecorder.AudioSource.MIC,
            rate = rate,
            preferred = builtin,
            requirePreferred = false,
        )
    }

    private fun buildRecord(
        source: Int,
        rate: Int,
        preferred: AudioDeviceInfo?,
        requirePreferred: Boolean,
    ): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) {
            lastError = "AudioRecord buffer unavailable ($min) at $rate Hz"
            return null
        }
        val rec = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min * 2)
            .build()
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        if (preferred != null) {
            val ok = rec.setPreferredDevice(preferred)
            log("PREFERRED_INPUT ${describeDevice(preferred)} accepted=$ok")
            if (!ok && requirePreferred) {
                rec.release()
                lastError = "setPreferredDevice(${describeDevice(preferred)}) was rejected. Not falling back."
                return null
            }
        }
        return rec
    }

    private fun waitForSco(): Boolean {
        if (scoState == "CONNECTED" || commDeviceIsBluetooth()) return true
        val latch = CountDownLatch(1)
        scoWait.set(latch)
        try {
            @Suppress("DEPRECATION")
            am.startBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = true
            log("SCO_START waiting for CONNECTED (isBluetoothScoOn is not enough)")
            val ok = latch.await(4, TimeUnit.SECONDS)
            log("SCO_WAIT result=$ok state=$scoState scoOn=${flags().scoOn} comm=${flags().communicationDevice?.label}")
            return ok || scoState == "CONNECTED" || commDeviceIsBluetooth()
        } catch (e: Exception) {
            log("SCO_START failed: ${e.message}")
            return false
        } finally {
            scoWait.set(null)
        }
    }

    private fun commDeviceIsBluetooth(): Boolean {
        val comm = flags().communicationDevice ?: return false
        return comm.kind == DeviceKind.BLUETOOTH_SCO || comm.kind == DeviceKind.BLUETOOTH_LE
    }

    private fun setBluetoothCommunicationDevice(device: AudioDeviceInfo?): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val target = device
            ?: am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || bleHeadset(it)
            }
        if (target == null) {
            log("COMMUNICATION_DEVICE none available")
            return false
        }
        val ok = am.setCommunicationDevice(target)
        log("SET_COMMUNICATION_DEVICE ${describeDevice(target)} ok=$ok")
        return ok
    }

    private fun revertHeadsetAudio() {
        try {
            @Suppress("DEPRECATION")
            am.stopBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            am.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            }
        } catch (e: Exception) {
            log("REVERT_AUDIO ${e.message}")
        }
    }

    private fun requestPlaybackFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        val r = am.requestAudioFocus(req)
        lastFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        log("FOCUS_REQUEST playback result=$r")
        if (r != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            lastError = "Playback audio focus denied ($r) — trying anyway"
        }
    }

    private fun preferCurrentOutput(t: AudioTrack) {
        val flags = flags()
        val outs = rawDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = when {
            flags.a2dpOn -> outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            flags.wiredHeadsetOn -> outs.firstOrNull { AudioRoutes.kindOf(it.type) == DeviceKind.WIRED_HEADSET }
            else -> outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
        if (preferred != null) {
            val ok = t.setPreferredDevice(preferred)
            log("PREFERRED_OUTPUT ${describeDevice(preferred)} accepted=$ok")
        }
    }

    private fun stopPlayback() {
        track?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        track = null
    }

    private fun flags(): RouteFlags {
        val comm = if (Build.VERSION.SDK_INT >= 31) {
            am.communicationDevice?.let { toNamed(it) }
        } else {
            null
        }
        return RouteFlags(
            scoOn = @Suppress("DEPRECATION") am.isBluetoothScoOn,
            a2dpOn = a2dpOn(),
            speakerphoneOn = am.isSpeakerphoneOn,
            wiredHeadsetOn = wiredOn(),
            communicationDevice = comm,
        )
    }

    @Suppress("DEPRECATION")
    private fun a2dpOn(): Boolean = try {
        am.isBluetoothA2dpOn
    } catch (_: Exception) {
        rawDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }

    @Suppress("DEPRECATION")
    private fun wiredOn(): Boolean = try {
        am.isWiredHeadsetOn
    } catch (_: Exception) {
        rawDevices(AudioManager.GET_DEVICES_OUTPUTS).any { AudioRoutes.kindOf(it.type) == DeviceKind.WIRED_HEADSET }
    }

    private fun rawDevices(kind: Int): Array<AudioDeviceInfo> = am.getDevices(kind)

    private fun deviceList(kind: Int): List<NamedDevice> = rawDevices(kind).map { toNamed(it) }

    private fun toNamed(d: AudioDeviceInfo): NamedDevice {
        val product = try {
            d.productName?.toString()
        } catch (_: Exception) {
            null
        }
        val rates = try {
            d.sampleRates
        } catch (_: Exception) {
            null
        }
        return AudioRoutes.named(d.type, product, rates)
    }

    private fun routedOf(rec: AudioRecord): NamedDevice? = try {
        rec.routedDevice?.let { toNamed(it) }
    } catch (_: Exception) {
        null
    }

    private fun describeDevice(d: AudioDeviceInfo): String = toNamed(d).label

    private fun bleHeadset(d: AudioDeviceInfo): Boolean =
        Build.VERSION.SDK_INT >= 31 && (
            d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            )

    private fun scoName(st: Int): String = when (st) {
        AudioManager.SCO_AUDIO_STATE_CONNECTED -> "CONNECTED"
        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "DISCONNECTED"
        AudioManager.SCO_AUDIO_STATE_CONNECTING -> "CONNECTING"
        AudioManager.SCO_AUDIO_STATE_ERROR -> "ERROR"
        else -> "STATE_$st"
    }

    private fun modeName(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "MODE_NORMAL"
        AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
        AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
        else -> "MODE_$mode"
    }

    private fun clock(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    private fun log(message: String) {
        sequenceLog.add(message)
        lastMessage = message
    }

    private fun notifyChanged() {
        val snap = snapshot()
        val l = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            l.invoke(snap)
        } else {
            mainHandler.post { l.invoke(snap) }
        }
    }
}
