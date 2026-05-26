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

    @Test
    fun `pinchZoom with scale above maximum clamps to 10x`() {
        policy.resetZoomAccumulation()
        val action = policy.map(GestureEvent.PinchZoom(12f, 200f, 300f), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
        val ratio = (dispatch.intent as SessionIntent.ApplyZoomRatio).ratio
        assertEquals(10.0f, ratio)
    }

    @Test
    fun `pinchZoom with scale below minimum clamps to 0_5x`() {
        policy.resetZoomAccumulation()
        val action = policy.map(GestureEvent.PinchZoom(0.1f, 200f, 300f), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
        val ratio = (dispatch.intent as SessionIntent.ApplyZoomRatio).ratio
        assertEquals(0.5f, ratio)
    }

    @Test
    fun `scaleEnd preserves zoom basis for next pinch`() {
        // Pinch from 1.0x by 2.0x -> 2.0x
        val action1 = policy.map(GestureEvent.PinchZoom(2.0f, 200f, 300f), ModeId.PHOTO, 1.0f)
        assertTrue(action1 is GestureAction.DispatchSession)
        val ratio1 = (action1 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(2.0f, ratio1.ratio)

        // ScaleEnd should NOT reset localZoomRatio
        val endAction = policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, endAction)

        // Simulate what MainActivityActionBinder does: sync session zoom before next non-PinchZoom event
        // The session zoom is now 2.0x (from the last dispatch)
        // Then a new pinch starts: scale 1.1x -> should produce ~2.2x, NOT 1.1x (if reset to 1.0f)
        policy.syncZoomRatio(2.0f)
        val action2 = policy.map(GestureEvent.PinchZoom(1.1f, 200f, 300f), ModeId.PHOTO, 2.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio2 = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(2.2f, ratio2.ratio, 0.01f)
    }

    @Test
    fun `consecutive pinches separated by ScaleEnd maintain zoom continuity`() {
        // Pinch 1: from 1.0x, scale 3.0 -> 3.0x
        policy.map(GestureEvent.PinchZoom(3.0f, 100f, 100f), ModeId.PHOTO, 1.0f)
        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        policy.syncZoomRatio(3.0f)

        // Pinch 2: from 3.0x, scale 1.5 -> 4.5x
        val action2 = policy.map(GestureEvent.PinchZoom(1.5f, 100f, 100f), ModeId.PHOTO, 3.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio2 = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(4.5f, ratio2.ratio, 0.01f)

        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        policy.syncZoomRatio(4.5f)

        // Pinch 3: from 4.5x, scale 0.8 -> 3.6x
        val action3 = policy.map(GestureEvent.PinchZoom(0.8f, 100f, 100f), ModeId.PHOTO, 4.5f)
        assertTrue(action3 is GestureAction.DispatchSession)
        val ratio3 = (action3 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(3.6f, ratio3.ratio, 0.01f)
    }

    @Test
    fun `pinchZoom accumulates within a single gesture`() {
        var now = 17L
        val policy = GesturePolicy { now }

        // Two PinchZoom events in the same gesture (no ScaleEnd between)
        policy.map(GestureEvent.PinchZoom(1.5f, 100f, 100f), ModeId.PHOTO, 1.0f)
        now += 17L
        val action2 = policy.map(GestureEvent.PinchZoom(1.2f, 100f, 100f), ModeId.PHOTO, 1.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        // localZoomRatio: 1.0 * 1.5 * 1.2 = 1.8
        assertEquals(1.8f, ratio.ratio, 0.01f)
    }

    @Test
    fun `scaleEnd does not dispatch a session intent`() {
        val action = policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, action)
    }
}
