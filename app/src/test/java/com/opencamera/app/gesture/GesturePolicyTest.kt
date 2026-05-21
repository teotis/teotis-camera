package com.opencamera.app.gesture

import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePolicyTest {

    private val policy = GesturePolicy()

    @Test
    fun tap_mapsToFocusAt() {
        val action = policy.map(GestureEvent.Tap(100f, 200f), ModeId.PHOTO)
        assertTrue(action is GestureAction.FocusAt)
        val focus = action as GestureAction.FocusAt
        assertEquals(100f, focus.x)
        assertEquals(200f, focus.y)
    }

    @Test
    fun doubleTap_mapsToLensFacingToggled() {
        val action = policy.map(GestureEvent.DoubleTap(50f, 50f), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertEquals(SessionIntent.LensFacingToggled, dispatch.intent)
    }

    @Test
    fun doubleTap_mapsToLensFacingToggled_inVideoMode() {
        val action = policy.map(GestureEvent.DoubleTap(50f, 50f), ModeId.VIDEO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertEquals(SessionIntent.LensFacingToggled, dispatch.intent)
    }

    @Test
    fun pinchZoom_mapsToApplyZoomRatio() {
        val action = policy.map(GestureEvent.PinchZoom(1.5f, 200f, 300f), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
    }

    @Test
    fun verticalScroll_mapsToShowExposureHint() {
        val action = policy.map(GestureEvent.VerticalScroll(-50f, 100f, 200f), ModeId.PHOTO)
        assertTrue(action is GestureAction.ShowExposureHint)
        val hint = action as GestureAction.ShowExposureHint
        assertEquals(-50f, hint.deltaY)
    }

    @Test
    fun horizontalScroll_mapsToAssistModeSwitch() {
        val action = policy.map(GestureEvent.HorizontalScroll(80f, 100f, 200f), ModeId.PHOTO)
        assertTrue(action is GestureAction.AssistModeSwitch)
        val assist = action as GestureAction.AssistModeSwitch
        assertEquals(80f, assist.deltaX)
    }

    @Test
    fun longPress_mapsToIgnore() {
        val action = policy.map(GestureEvent.LongPress(100f, 200f), ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun dragCancel_mapsToIgnore() {
        val action = policy.map(GestureEvent.DragCancel, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, action)
    }
}
