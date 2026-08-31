package com.gymbuddy.app.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcmWav {
    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val dataBytes = pcm.size * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(1) // mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // byte rate
            header.putShort(2) // block align
            header.putShort(16) // bits
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes)
            raf.write(header.array())
            val samples = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { samples.putShort(it) }
            raf.write(samples.array())
        }
    }

    fun durationMs(samples: Int, sampleRate: Int): Int {
        if (sampleRate <= 0) return 0
        return ((samples * 1000L) / sampleRate).toInt()
    }
}
