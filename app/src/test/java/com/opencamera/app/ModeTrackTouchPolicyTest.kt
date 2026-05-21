package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModeTrackTouchPolicyTest {
    private val slop = 20f

    @Test
    fun `tap below slop dispatches click`() {
        val decision = modeTrackTouchDecision(
            downX = 100f, downY = 200f,
            upX = 105f, upY = 202f,
            touchSlop = slop
        )
        assertTrue(decision.shouldDispatchClick)
        assertFalse(decision.shouldTreatAsScroll)
    }

    @Test
    fun `horizontal movement above slop suppresses click`() {
        val decision = modeTrackTouchDecision(
            downX = 100f, downY = 200f,
            upX = 160f, upY = 202f,
            touchSlop = slop
        )
        assertFalse(decision.shouldDispatchClick)
        assertTrue(decision.shouldTreatAsScroll)
    }

    @Test
    fun `vertical movement above slop suppresses click`() {
        val decision = modeTrackTouchDecision(
            downX = 100f, downY = 200f,
            upX = 102f, upY = 260f,
            touchSlop = slop
        )
        assertFalse(decision.shouldDispatchClick)
        assertTrue(decision.shouldTreatAsScroll)
    }

    @Test
    fun `movement exactly at slop boundary dispatches click`() {
        val decision = modeTrackTouchDecision(
            downX = 100f, downY = 200f,
            upX = 120f, upY = 200f,
            touchSlop = slop
        )
        assertTrue(decision.shouldDispatchClick)
        assertFalse(decision.shouldTreatAsScroll)
    }

    @Test
    fun `zero movement is a tap`() {
        val decision = modeTrackTouchDecision(
            downX = 50f, downY = 50f,
            upX = 50f, upY = 50f,
            touchSlop = slop
        )
        assertTrue(decision.shouldDispatchClick)
        assertFalse(decision.shouldTreatAsScroll)
    }

    @Test
    fun `diagonal movement above slop suppresses click`() {
        val decision = modeTrackTouchDecision(
            downX = 100f, downY = 200f,
            upX = 115f, upY = 225f,
            touchSlop = slop
        )
        assertFalse(decision.shouldDispatchClick)
        assertTrue(decision.shouldTreatAsScroll)
    }
}
