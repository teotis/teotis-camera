/**
 * 覆盖行为:
 * - plan() 对单帧请求生成 1 步计划
 * - plan() 对多帧请求生成正确步数
 * - 最后一帧标记为 FINAL_OUTPUT，其余为 TEMPORARY
 * - frameCount 被 coerceAtLeast(1)
 * - interFrameDelayMillis 被 coerceAtLeast(0)
 * - totalFrameCount / temporaryFrameCount / finalFrameIndex 计算正确
 * - 非 MULTI_FRAME_CAPTURE 请求抛出 IllegalArgumentException
 *
 * 不适合单测的行为: 无
 */
package com.opencamera.core.device

import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.FocusStackFrameRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MultiFrameCaptureExecutionPlannerTest {

    private val planner = MultiFrameCaptureExecutionPlanner()

    private fun makeRequest(
        shotKind: ShotKind = ShotKind.MULTI_FRAME_CAPTURE,
        frameCount: Int = 1,
        interFrameDelayMillis: Long = 0L,
        focusStackFrameRoles: List<FocusStackFrameRole> = emptyList()
    ) = DeviceShotRequest(
        shotId = "test",
        template = CaptureTemplate.STILL_CAPTURE,
        shotKind = shotKind,
        frameCount = frameCount,
        interFrameDelayMillis = interFrameDelayMillis,
        focusStackFrameRoles = focusStackFrameRoles
    )

    @Test
    fun `single frame request produces 1-step plan`() {
        val plan = planner.plan(makeRequest(frameCount = 1))
        assertEquals(1, plan.steps.size)
        assertEquals(MultiFrameOutputRole.FINAL_OUTPUT, plan.steps[0].outputRole)
    }

    @Test
    fun `multi frame request produces correct step count`() {
        val plan = planner.plan(makeRequest(frameCount = 5))
        assertEquals(5, plan.steps.size)
    }

    @Test
    fun `last frame is FINAL_OUTPUT, others are TEMPORARY`() {
        val plan = planner.plan(makeRequest(frameCount = 4))
        assertEquals(MultiFrameOutputRole.TEMPORARY, plan.steps[0].outputRole)
        assertEquals(MultiFrameOutputRole.TEMPORARY, plan.steps[1].outputRole)
        assertEquals(MultiFrameOutputRole.TEMPORARY, plan.steps[2].outputRole)
        assertEquals(MultiFrameOutputRole.FINAL_OUTPUT, plan.steps[3].outputRole)
    }

    @Test
    fun `frameCount coerced to at least 1`() {
        val plan = planner.plan(makeRequest(frameCount = 0))
        assertEquals(1, plan.steps.size)
        assertEquals(MultiFrameOutputRole.FINAL_OUTPUT, plan.steps[0].outputRole)
    }

    @Test
    fun `negative frameCount coerced to 1`() {
        val plan = planner.plan(makeRequest(frameCount = -3))
        assertEquals(1, plan.steps.size)
    }

    @Test
    fun `interFrameDelayMillis coerced to at least 0`() {
        val plan = planner.plan(makeRequest(frameCount = 2, interFrameDelayMillis = -100L))
        assertEquals(0L, plan.interFrameDelayMillis)
    }

    @Test
    fun `positive interFrameDelayMillis preserved`() {
        val plan = planner.plan(makeRequest(frameCount = 2, interFrameDelayMillis = 250L))
        assertEquals(250L, plan.interFrameDelayMillis)
    }

    @Test
    fun `totalFrameCount matches steps size`() {
        val plan = planner.plan(makeRequest(frameCount = 3))
        assertEquals(3, plan.totalFrameCount)
    }

    @Test
    fun `temporaryFrameCount excludes final frame`() {
        val plan = planner.plan(makeRequest(frameCount = 4))
        assertEquals(3, plan.temporaryFrameCount)
    }

    @Test
    fun `temporaryFrameCount is 0 for single frame`() {
        val plan = planner.plan(makeRequest(frameCount = 1))
        assertEquals(0, plan.temporaryFrameCount)
    }

    @Test
    fun `finalFrameIndex is last step index`() {
        val plan = planner.plan(makeRequest(frameCount = 5))
        assertEquals(5, plan.finalFrameIndex)
    }

    @Test
    fun `frame indices are 1-based sequential`() {
        val plan = planner.plan(makeRequest(frameCount = 3))
        assertEquals(1, plan.steps[0].frameIndex)
        assertEquals(2, plan.steps[1].frameIndex)
        assertEquals(3, plan.steps[2].frameIndex)
    }

    @Test
    fun `focus stack roles are assigned to matching frame steps`() {
        val plan = planner.plan(
            makeRequest(
                frameCount = 2,
                focusStackFrameRoles = listOf(FocusStackFrameRole.NEAR, FocusStackFrameRole.FAR)
            )
        )

        assertEquals(FocusStackFrameRole.NEAR, plan.steps[0].focusStackRole)
        assertEquals(FocusStackFrameRole.FAR, plan.steps[1].focusStackRole)
    }

    @Test
    fun `non MULTI_FRAME_CAPTURE request throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            planner.plan(makeRequest(shotKind = ShotKind.STILL_CAPTURE))
        }
    }

    @Test
    fun `VIDEO_RECORDING request throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            planner.plan(makeRequest(shotKind = ShotKind.VIDEO_RECORDING))
        }
    }
}
