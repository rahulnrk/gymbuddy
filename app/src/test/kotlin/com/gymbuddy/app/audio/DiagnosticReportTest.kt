package com.gymbuddy.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiagnosticReportTest {
    @Test
    fun logIsTimestampedAndOrdered() {
        var t = 1_000L
        val log = DiagnosticLog { t.also { t += 1 } }
        log.add("OUTPUT_BEFORE A2DP")
        log.add("MIC_OPEN 16000")
        val text = log.text()
        assertTrue(text.contains("OUTPUT_BEFORE A2DP"))
        assertTrue(text.contains("MIC_OPEN 16000"))
        val lines = log.snapshot()
        assertEquals(2, lines.size)
        assertTrue(lines[0] < lines[1] || lines[0].contains("OUTPUT_BEFORE"))
    }

    @Test
    fun writeReportIncludesHeaderDevicesAndAdb() {
        val dir = File.createTempFile("diag", "dir").apply { delete(); mkdirs() }
        val log = DiagnosticLog { 0L }
        log.add("FOCUS_REQUEST granted=true")
        val file = DiagnosticFiles.writeReport(
            dir,
            mapOf("phone" to "Pixel", "headset_rate" to "16000"),
            log,
        )
        val body = file.readText()
        assertTrue(body.contains("GymBuddy audio diagnostic"))
        assertTrue(body.contains("phone: Pixel"))
        assertTrue(body.contains("headset_rate: 16000"))
        assertTrue(body.contains("FOCUS_REQUEST granted=true"))
        assertTrue(body.contains(AudioRoutes.ADB_PULL))
        assertEquals("report.txt", file.name)
    }

    @Test
    fun adbPullPointsAtAppExternalFiles() {
        assertEquals(
            "adb pull /sdcard/Android/data/com.gymbuddy.app/files/audio-diagnostic/",
            AudioRoutes.ADB_PULL,
        )
    }
}
