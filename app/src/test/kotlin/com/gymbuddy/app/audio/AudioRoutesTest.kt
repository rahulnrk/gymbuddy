package com.gymbuddy.app.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioRoutesTest {

    private val speaker = AudioRoutes.named(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker")
    private val a2dp = AudioRoutes.named(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Pixel Buds")
    private val sco = AudioRoutes.named(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Pixel Buds")
    private val builtinMic = AudioRoutes.named(AudioDeviceInfo.TYPE_BUILTIN_MIC, "Mic")
    private val scoMic = AudioRoutes.named(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Pixel Buds", intArrayOf(8000, 16000))
    private val outputs = listOf(speaker, a2dp, sco)

    @Test
    fun currentOutputDoesNotPickIdleScoWhileA2dpIsPlaying() {
        val flags = RouteFlags(scoOn = false, a2dpOn = true, speakerphoneOn = false, wiredHeadsetOn = false)
        val out = AudioRoutes.currentOutput(flags, outputs)
        assertEquals("TYPE_BLUETOOTH_A2DP · Pixel Buds", out?.label)
        assertEquals(DeviceKind.BLUETOOTH_A2DP, out?.kind)
    }

    @Test
    fun currentOutputPicksScoOnlyWhenScoIsActuallyOn() {
        val flags = RouteFlags(scoOn = true, a2dpOn = false, speakerphoneOn = false, wiredHeadsetOn = false)
        val out = AudioRoutes.currentOutput(flags, outputs)
        assertEquals(DeviceKind.BLUETOOTH_SCO, out?.kind)
    }

    @Test
    fun currentInputUsesRoutedDeviceWhenCapturing() {
        val flags = RouteFlags(scoOn = true, a2dpOn = false, speakerphoneOn = false, wiredHeadsetOn = false)
        val inp = AudioRoutes.currentInput(flags, listOf(builtinMic, scoMic), routed = scoMic)
        assertEquals(scoMic, inp)
    }

    @Test
    fun headsetMicAt44100OnBuiltinIsNotGenuine() {
        val v = AudioRoutes.headsetMicVerdict(
            CaptureMode.HEADSET_MIC,
            routed = builtinMic,
            scoConnected = false,
            sampleRate = 44100,
        )
        assertFalse(v.genuine)
        assertTrue(v.reason.contains("will not route", ignoreCase = true))
    }

    @Test
    fun headsetMicOnScoAt16kIsGenuine() {
        val v = AudioRoutes.headsetMicVerdict(
            CaptureMode.HEADSET_MIC,
            routed = scoMic,
            scoConnected = true,
            sampleRate = 16000,
        )
        assertTrue(v.genuine)
        assertTrue(v.reason.contains("TYPE_BLUETOOTH_SCO"))
    }

    @Test
    fun headsetMicOnScoAt44100IsGenuineButFlagged() {
        val v = AudioRoutes.headsetMicVerdict(
            CaptureMode.HEADSET_MIC,
            routed = sco,
            scoConnected = true,
            sampleRate = 44100,
        )
        assertTrue(v.genuine)
        assertTrue(v.reason.contains("unusually high"))
    }

    @Test
    fun missingRoutedDeviceIsNotGenuine() {
        val v = AudioRoutes.headsetMicVerdict(CaptureMode.HEADSET_MIC, null, false, 44100)
        assertFalse(v.genuine)
    }

    @Test
    fun a2dpDroppedWhenScoTakesOver() {
        assertTrue(AudioRoutes.a2dpDropped(a2dpBefore = true, a2dpAfter = false, scoAfter = true))
        assertFalse(AudioRoutes.a2dpDropped(a2dpBefore = true, a2dpAfter = true, scoAfter = false))
    }

    @Test
    fun scoCaptureRatePrefers16kThen8k() {
        assertEquals(16000, AudioRoutes.pickCaptureRate(scoMic, sco = true, fallback = 44100))
        assertEquals(8000, AudioRoutes.pickCaptureRate(sco.copy(sampleRates = listOf(8000)), sco = true, fallback = 44100))
        assertEquals(16000, AudioRoutes.pickCaptureRate(null, sco = true, fallback = 44100))
        assertEquals(44100, AudioRoutes.pickCaptureRate(builtinMic, sco = false, fallback = 44100))
    }

    @Test
    fun typeAndProductLabel() {
        assertEquals("TYPE_BLUETOOTH_SCO · Pixel Buds", AudioRoutes.label("TYPE_BLUETOOTH_SCO", "Pixel Buds"))
        assertEquals("TYPE_BUILTIN_MIC", AudioRoutes.label("TYPE_BUILTIN_MIC", "  "))
    }
}
