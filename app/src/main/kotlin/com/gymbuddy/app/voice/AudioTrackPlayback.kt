package com.gymbuddy.app.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioTrackPlayback : TtsPlayback {
    private var track: AudioTrack? = null
    private var framesWritten: Int = 0
    override var playing: Boolean = false
        private set

    override fun start(sampleRateHz: Int) {
        stopAndFlush()
        val detail = "$sampleRateHz Hz PCM 16-bit mono"
        val min = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min == AudioTrack.ERROR || min == AudioTrack.ERROR_BAD_VALUE) {
            throw AudioOutputFailedException(detail)
        }
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min.coerceAtLeast(sampleRateHz))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            throw AudioOutputFailedException(detail)
        }
        created.play()
        track = created
        framesWritten = 0
        playing = true
    }

    override fun write(pcm: ByteArray) {
        val t = track ?: return
        t.write(pcm, 0, pcm.size)
        framesWritten += pcm.size / 2
    }

    override fun drain() {
        val t = track ?: return
        if (!playing || framesWritten <= 0) return
        while (playing && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val head = t.playbackHeadPosition
            if (head >= framesWritten - 1) break
            Thread.sleep(10)
        }
    }

    override fun stopAndFlush() {
        playing = false
        val t = track ?: return
        track = null
        framesWritten = 0
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.stop() }
        runCatching { t.release() }
    }
}
