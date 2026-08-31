package com.gymbuddy.app.hud

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One writer for Gym Mode. Session loads and EventLog appends run here,
 * never on the UI thread. The HUD updates only after [work] returns — the
 * write is acknowledged before the user sees the set.
 */
class HudWriter(
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, THREAD_NAME).apply { isDaemon = true }
    },
) {
    fun run(work: () -> Unit, then: () -> Unit = {}) {
        worker.execute {
            work()
            mainHandler.post(then)
        }
    }

    fun <T> run(work: () -> T, then: (T) -> Unit) {
        worker.execute {
            val result = work()
            mainHandler.post { then(result) }
        }
    }

    fun shutdown() {
        worker.shutdown()
        worker.awaitTermination(2, TimeUnit.SECONDS)
    }

    companion object {
        const val THREAD_NAME = "hud-session"
    }
}
