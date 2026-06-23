package com.opencamera.app.permissions

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PermissionRequestHistoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var history: PermissionRequestHistory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("test_permission_history", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        history = PermissionRequestHistory(prefs)
    }

    @Test
    fun `defaults to never requested for camera`() {
        assertFalse(history.hasRequestedCamera())
    }

    @Test
    fun `defaults to never requested for microphone`() {
        assertFalse(history.hasRequestedMicrophone())
    }

    @Test
    fun `markCameraRequested persists and roundtrips`() {
        history.markCameraRequested()
        assertTrue(history.hasRequestedCamera())
        assertFalse(history.hasRequestedMicrophone())
    }

    @Test
    fun `markMicrophoneRequested persists and roundtrips`() {
        history.markMicrophoneRequested()
        assertTrue(history.hasRequestedMicrophone())
        assertFalse(history.hasRequestedCamera())
    }

    @Test
    fun `marking both requests records both`() {
        history.markCameraRequested()
        history.markMicrophoneRequested()
        assertTrue(history.hasRequestedCamera())
        assertTrue(history.hasRequestedMicrophone())
    }
}
