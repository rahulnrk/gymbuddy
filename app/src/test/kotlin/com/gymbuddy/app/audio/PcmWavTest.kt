package com.gymbuddy.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWavTest {
    @Test
    fun durationMs() {
        assertEquals(1000, PcmWav.durationMs(16000, 16000))
        assertEquals(3000, PcmWav.durationMs(44100 * 3, 44100))
        assertEquals(0, PcmWav.durationMs(100, 0))
    }

    @Test
    fun writesRiffHeaderAndPcm() {
        val dir = File.createTempFile("wav", "dir").apply { delete(); mkdirs() }
        val file = File(dir, "clip.wav")
        val pcm = shortArrayOf(1, -2, 3)
        PcmWav.write(file, pcm, 16000)
        val bytes = file.readBytes()
        assertEquals(44 + 6, bytes.size)
        assertEquals("RIFF", bytes.decodeToString(0, 4))
        assertEquals("WAVE", bytes.decodeToString(8, 12))
        val le = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        le.position(24)
        assertEquals(16000, le.int)
        le.position(40)
        assertEquals(6, le.int)
        assertTrue(file.length() > 44)
    }
}
