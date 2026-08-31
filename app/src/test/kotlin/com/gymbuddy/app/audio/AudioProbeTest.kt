package com.gymbuddy.app.audio

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioProbeTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()

    private fun probe(): AudioProbe {
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.RECORD_AUDIO)
        return AudioProbe(ctx)
    }

    @Test
    fun refreshRewritesEveryFieldMessage() {
        val p = probe()
        val first = p.snapshot()
        assertTrue(first.refreshedAt == null)
        val second = p.refresh()
        assertNotNull(second.refreshedAt)
        assertTrue(second.lastMessage!!.startsWith("Refreshed"))
        assertTrue(second.lastMessage!!.contains("output="))
        assertTrue(second.lastMessage!!.contains("a2dp="))
        assertTrue(second.availableOutputs != null)
        assertTrue(second.availableInputs != null)
        p.release()
    }

    @Test
    fun playWithoutClipFailsOnScreen() {
        val p = probe()
        val s = p.playClip()
        assertFalse(s.hasClip)
        assertFalse(s.playing)
        assertTrue(s.lastError!!.contains("Nothing recorded"))
        p.release()
    }

    @Test
    fun snapshotPlaybackLabelShowsStoppedWithoutClip() {
        val p = probe()
        assertEqualsLabel(p.snapshot())
        p.release()
    }

    @Test
    fun runFullSequenceWritesReportEvenWhenCaptureFails() {
        val p = probe()
        val s = p.runFullSequence(seconds = 1)
        assertNotNull(s.reportPath)
        val report = File(s.reportPath!!)
        assertTrue(report.exists())
        val body = report.readText()
        assertTrue(body.contains("GymBuddy audio diagnostic"))
        assertTrue(body.contains("SEQUENCE_START") || body.contains("INITIAL"))
        assertTrue(body.contains("adb pull /sdcard/Android/data/com.gymbuddy.app/files/audio-diagnostic/"))
        assertTrue(s.lastMessage!!.contains("Report written") || s.lastMessage!!.contains("adb pull"))
        p.release()
    }

    @Test
    fun clipLengthLabelFormatsDuration() {
        val snap = AudioSnapshot(
            output = null,
            input = null,
            micOpen = false,
            hasClip = true,
            playing = false,
            clipDurationMs = 3000,
            clipSampleRate = 16000,
            clipMode = CaptureMode.HEADSET_MIC,
        )
        assertTrue(snap.clipLengthLabel.contains("3.00s"))
        assertTrue(snap.clipLengthLabel.contains("HEADSET_MIC"))
        assertTrue(snap.clipLengthLabel.contains("16000 Hz"))
        assertTrue(snap.playbackLabel.startsWith("stopped"))
    }

    private fun assertEqualsLabel(s: AudioSnapshot) {
        assertTrue(s.playbackLabel.contains("no clip"))
    }
}
