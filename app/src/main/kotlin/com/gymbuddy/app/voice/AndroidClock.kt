package com.gymbuddy.app.voice

import android.os.SystemClock
import com.gymbuddy.domain.voice.Clock

class AndroidClock : Clock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}
