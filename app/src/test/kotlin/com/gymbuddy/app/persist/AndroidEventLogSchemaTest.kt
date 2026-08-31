package com.gymbuddy.app.persist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFails
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidEventLogSchemaTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun upgradeIsRejectedNotDropped() {
        val name = "up-${System.nanoTime()}.db"
        // Constructor does not open the file; force onCreate at v1 so reopen can upgrade.
        AndroidEventLog(ctx, name, version = 1).apply { sqlite(); close() }
        val thrown = assertFails { AndroidEventLog(ctx, name, version = 2).sqlite() }
        val msg = thrown.message.orEmpty() + (thrown.cause?.message ?: "")
        assertTrue(
            "destructive" in msg || "no migration" in msg,
            thrown.message,
        )
    }

    @Test
    fun downwardOpenIsRejected() {
        val name = "down-${System.nanoTime()}.db"
        AndroidEventLog(ctx, name, version = 2).apply { sqlite(); close() }
        val thrown = assertFails { AndroidEventLog(ctx, name, version = 1).sqlite() }
        val msg = thrown.message.orEmpty() + (thrown.cause?.message ?: "")
        assertTrue("downward" in msg, thrown.message)
    }
}
