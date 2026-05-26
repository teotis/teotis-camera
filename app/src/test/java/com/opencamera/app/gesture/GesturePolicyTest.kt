package com.opencamera.app.gesture

import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
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
        val action1 = policy.map(GestureEvent.PinchZoom(2.0f, 200f, 300f), ModeId.PHOTO, 1.0f)
        assertTrue(action1 is GestureAction.DispatchSession)
        val ratio1 = (action1 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(2.0f, ratio1.ratio)

        val endAction = policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, endAction)

        policy.syncZoomRatio(2.0f)
        val action2 = policy.map(GestureEvent.PinchZoom(1.1f, 200f, 300f), ModeId.PHOTO, 2.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio2 = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(2.2f, ratio2.ratio, 0.01f)
    }

    @Test
    fun `consecutive pinches separated by ScaleEnd maintain zoom continuity`() {
        policy.map(GestureEvent.PinchZoom(3.0f, 100f, 100f), ModeId.PHOTO, 1.0f)
        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        policy.syncZoomRatio(3.0f)

        val action2 = policy.map(GestureEvent.PinchZoom(1.5f, 100f, 100f), ModeId.PHOTO, 3.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio2 = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(4.5f, ratio2.ratio, 0.01f)

        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        policy.syncZoomRatio(4.5f)

        val action3 = policy.map(GestureEvent.PinchZoom(0.8f, 100f, 100f), ModeId.PHOTO, 4.5f)
        assertTrue(action3 is GestureAction.DispatchSession)
        val ratio3 = (action3 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(3.6f, ratio3.ratio, 0.01f)
    }

    @Test
    fun `pinchZoom accumulates within a single gesture`() {
        var now = 17L
        val policy = GesturePolicy { now }

        policy.map(GestureEvent.PinchZoom(1.5f, 100f, 100f), ModeId.PHOTO, 1.0f)
        now += 8L
        val action2 = policy.map(GestureEvent.PinchZoom(1.2f, 100f, 100f), ModeId.PHOTO, 1.0f)
        assertEquals(GestureAction.Ignore, action2)

        now += 17L
        val action3 = policy.map(GestureEvent.PinchZoom(1.0f, 100f, 100f), ModeId.PHOTO, 1.0f)
        assertTrue(action3 is GestureAction.DispatchSession)
        val ratio = (action3 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        // 1.0 * 1.5 * 1.2 * 1.0 = 1.8
        assertEquals(1.8f, ratio.ratio, 0.01f)
    }

    @Test
    fun `scaleEnd does not dispatch a session intent`() {
        val action = policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun `scaleEnd resets zoom accumulation`() {
        policy.resetZoomAccumulation()
        policy.map(GestureEvent.PinchZoom(2f, 200f, 300f), ModeId.PHOTO)
        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        val action = policy.map(GestureEvent.PinchZoom(2f, 200f, 300f), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        val ratio = (dispatch.intent as SessionIntent.ApplyZoomRatio).ratio
        assertEquals(2.0f, ratio)
    }

    @Test
    fun `dragStart returns Ignore`() {
        val action = policy.map(GestureEvent.DragStart(100f, 200f), ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun `dragMove dispatches zoom ratio when dragging`() {
        policy.map(GestureEvent.DragStart(100f, 200f), ModeId.PHOTO)
        val action = policy.map(GestureEvent.DragMove(150f, 200f), ModeId.PHOTO, 1.5f)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
    }

    @Test
    fun `dragMove ignores when not dragging`() {
        val action = policy.map(GestureEvent.DragMove(150f, 200f), ModeId.PHOTO, 1.5f)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun `dragEnd stops dragging`() {
        policy.map(GestureEvent.DragStart(100f, 200f), ModeId.PHOTO)
        policy.map(GestureEvent.DragEnd, ModeId.PHOTO)
        val action = policy.map(GestureEvent.DragMove(150f, 200f), ModeId.PHOTO, 1.5f)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun `dragCancel stops dragging`() {
        policy.map(GestureEvent.DragStart(100f, 200f), ModeId.PHOTO)
        policy.map(GestureEvent.DragCancel, ModeId.PHOTO)
        val action = policy.map(GestureEvent.DragMove(150f, 200f), ModeId.PHOTO, 1.5f)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun `mapDragToRatio dispatches ratio from mapper`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)
        policy.map(GestureEvent.DragStart(10f, 0f), ModeId.PHOTO)
        val action = policy.mapDragToRatio(50f, mapper, capability)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
    }

    @Test
    fun `mapDragToRatio ignores when not dragging`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)
        val action = policy.mapDragToRatio(50f, mapper, capability)
        assertEquals(GestureAction.Ignore, action)
    }
}
