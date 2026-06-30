package com.opencamera.app

import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchSmokeTest {
    @Test
    fun `main activity can launch through first resumed frame`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
    }

    @Test
    fun `first dev console entry renders existing records immediately`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        activity.findViewById<android.view.View>(R.id.buttonDevEntry).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val events = activity.findViewById<RecyclerView>(R.id.devConsoleContent)
        assertTrue(
            events.adapter?.itemCount ?: 0 > 0,
            "First dev console entry must render existing records without waiting for another session emission"
        )
    }
}
