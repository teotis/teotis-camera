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
        val action = policy.map(GestureEvent.PinchZoom(1.5f, 200f, 300f, 1L), ModeId.PHOTO)
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
        policy.map(GestureEvent.PinchBegin(200f, 300f), ModeId.PHOTO)
        val action = policy.map(GestureEvent.PinchZoom(12f, 200f, 300f, 1L), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
        val ratio = (dispatch.intent as SessionIntent.ApplyZoomRatio).ratio
        assertEquals(10.0f, ratio)
    }

    @Test
    fun `pinchZoom with scale below minimum clamps to 0_5x`() {
        policy.resetZoomAccumulation()
        policy.map(GestureEvent.PinchBegin(200f, 300f), ModeId.PHOTO)
        val action = policy.map(GestureEvent.PinchZoom(0.1f, 200f, 300f, 1L), ModeId.PHOTO)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        assertTrue(dispatch.intent is SessionIntent.ApplyZoomRatio)
        val ratio = (dispatch.intent as SessionIntent.ApplyZoomRatio).ratio
        assertEquals(0.5f, ratio)
    }

    @Test
    fun `scaleEnd preserves zoom basis for next pinch`() {
        // Gesture 1: begin at 1.0, spanRatio 2.0 → targetZoom 2.0
        policy.map(GestureEvent.PinchBegin(200f, 300f), ModeId.PHOTO, 1.0f)
        val action1 = policy.map(GestureEvent.PinchZoom(2.0f, 200f, 300f, 1L), ModeId.PHOTO, 1.0f)
        assertTrue(action1 is GestureAction.DispatchSession)
        val ratio1 = (action1 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(2.0f, ratio1.ratio)

        val endAction = policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, endAction)

        // Gesture 2: PinchBegin snapshots currentZoom=2.0 as new baseZoom, spanRatio 1.5 → 3.0
        policy.map(GestureEvent.PinchBegin(200f, 300f), ModeId.PHOTO, 2.0f)
        val action2 = policy.map(GestureEvent.PinchZoom(1.5f, 200f, 300f, 2L), ModeId.PHOTO, 2.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio2 = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(3.0f, ratio2.ratio, 0.01f)
    }

    @Test
    fun `consecutive pinches separated by ScaleEnd maintain zoom continuity`() {
        // Gesture 1: begin at 1.0, spanRatio 3.0 → 3.0
        policy.map(GestureEvent.PinchBegin(100f, 100f), ModeId.PHOTO, 1.0f)
        policy.map(GestureEvent.PinchZoom(3.0f, 100f, 100f, 1L), ModeId.PHOTO, 1.0f)
        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)

        // Gesture 2: PinchBegin snapshots currentZoom=3.0, spanRatio 1.5 → 4.5
        policy.map(GestureEvent.PinchBegin(100f, 100f), ModeId.PHOTO, 3.0f)
        val action2 = policy.map(GestureEvent.PinchZoom(1.5f, 100f, 100f, 2L), ModeId.PHOTO, 3.0f)
        assertTrue(action2 is GestureAction.DispatchSession)
        val ratio2 = (action2 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(4.5f, ratio2.ratio, 0.01f)

        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)

        // Gesture 3: PinchBegin snapshots currentZoom=4.5, spanRatio 0.8 → 3.6
        policy.map(GestureEvent.PinchBegin(100f, 100f), ModeId.PHOTO, 4.5f)
        val action3 = policy.map(GestureEvent.PinchZoom(0.8f, 100f, 100f, 3L), ModeId.PHOTO, 4.5f)
        assertTrue(action3 is GestureAction.DispatchSession)
        val ratio3 = (action3 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(3.6f, ratio3.ratio, 0.01f)
    }

    @Test
    fun `pinchZoom accumulates within a single gesture`() {
        var now = 17L
        val policy = GesturePolicy { now }

        // PinchBegin snapshots baseZoom=1.0
        policy.map(GestureEvent.PinchBegin(100f, 100f), ModeId.PHOTO, 1.0f)
        // spanRatio 1.5 → targetZoom 1.5, dispatched
        policy.map(GestureEvent.PinchZoom(1.5f, 100f, 100f, 1L), ModeId.PHOTO, 1.0f)
        now += 8L
        // spanRatio 1.2 → targetZoom 1.2, throttled (< 16ms)
        val action2 = policy.map(GestureEvent.PinchZoom(1.2f, 100f, 100f, 1L), ModeId.PHOTO, 1.0f)
        assertEquals(GestureAction.Ignore, action2)

        now += 17L
        // spanRatio 1.0 → targetZoom 1.0, dispatched (>= 16ms)
        val action3 = policy.map(GestureEvent.PinchZoom(1.0f, 100f, 100f, 1L), ModeId.PHOTO, 1.0f)
        assertTrue(action3 is GestureAction.DispatchSession)
        val ratio = (action3 as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        // session-scoped: baseZoom(1.0) * spanRatio(1.0) = 1.0
        assertEquals(1.0f, ratio.ratio, 0.01f)
    }

    @Test
    fun `scaleEnd does not dispatch a session intent`() {
        val action = policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        assertEquals(GestureAction.Ignore, action)
    }

    @Test
    fun `pinchBegin snapshots currentZoom as baseZoom`() {
        // PinchBegin at zoom 3.0 should set baseZoom to 3.0
        policy.map(GestureEvent.PinchBegin(100f, 100f), ModeId.PHOTO, 3.0f)
        // spanRatio 2.0 → targetZoom = 3.0 * 2.0 = 6.0
        val action = policy.map(GestureEvent.PinchZoom(2.0f, 100f, 100f, 1L), ModeId.PHOTO, 3.0f)
        assertTrue(action is GestureAction.DispatchSession)
        val ratio = (action as GestureAction.DispatchSession).intent as SessionIntent.ApplyZoomRatio
        assertEquals(6.0f, ratio.ratio, 0.01f)
    }

    @Test
    fun `scaleEnd preserves zoom accumulation for next pinch`() {
        policy.resetZoomAccumulation()
        // Gesture 1: begin at 1.0, spanRatio 2.0 → 2.0
        policy.map(GestureEvent.PinchBegin(200f, 300f), ModeId.PHOTO)
        policy.map(GestureEvent.PinchZoom(2f, 200f, 300f, 1L), ModeId.PHOTO)
        policy.map(GestureEvent.ScaleEnd, ModeId.PHOTO)
        // Gesture 2: PinchBegin snapshots currentZoom=2.0, spanRatio 2.0 → 4.0
        policy.map(GestureEvent.PinchBegin(200f, 300f), ModeId.PHOTO, 2.0f)
        val action = policy.map(GestureEvent.PinchZoom(2f, 200f, 300f, 2L), ModeId.PHOTO, 2.0f)
        assertTrue(action is GestureAction.DispatchSession)
        val dispatch = action as GestureAction.DispatchSession
        val ratio = (dispatch.intent as SessionIntent.ApplyZoomRatio).ratio
        assertEquals(4.0f, ratio)
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
    fun `mapDragToRatio throttles with injected clock`() {
        var now = 1_000L
        val policy = GesturePolicy(nowMillis = { now })
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)

        policy.map(GestureEvent.DragStart(10f, 0f), ModeId.PHOTO)
        val first = policy.mapDragToRatio(50f, mapper, capability)
        now += 20
        val second = policy.mapDragToRatio(100f, mapper, capability)
        now += 31
        val third = policy.mapDragToRatio(100f, mapper, capability)

        assertTrue(first is GestureAction.DispatchSession)
        assertEquals(GestureAction.Ignore, second)
        assertTrue(third is GestureAction.DispatchSession)
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
