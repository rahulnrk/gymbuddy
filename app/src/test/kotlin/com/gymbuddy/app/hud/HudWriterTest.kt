package com.gymbuddy.app.hud

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HudWriterTest {
    @Test
    fun workRunsOffMainThenPublishesOnMain() {
        val writer = HudWriter()
        try {
            val workThread = AtomicReference<String>()
            val workOnMain = AtomicReference<Boolean>()
            val thenOnMain = AtomicReference<Boolean>()
            val done = CountDownLatch(1)
            writer.run(
                {
                    workThread.set(Thread.currentThread().name)
                    workOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                },
                {
                    thenOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                    done.countDown()
                },
            )
            assertTrue(waitMain(done))
            assertEquals(HudWriter.THREAD_NAME, workThread.get())
            assertFalse(workOnMain.get())
            assertTrue(thenOnMain.get())
        } finally {
            writer.shutdown()
        }
    }

    private fun waitMain(done: CountDownLatch): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (done.count > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return done.count == 0L
    }
}
