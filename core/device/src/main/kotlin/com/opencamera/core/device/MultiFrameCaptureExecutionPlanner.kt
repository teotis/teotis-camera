package com.opencamera.core.device

import com.opencamera.core.media.ShotKind

enum class MultiFrameOutputRole {
    TEMPORARY,
    FINAL_OUTPUT
}

data class MultiFrameCaptureStep(
    val frameIndex: Int,
    val outputRole: MultiFrameOutputRole
)

data class MultiFrameCaptureExecutionPlan(
    val steps: List<MultiFrameCaptureStep>,
    val interFrameDelayMillis: Long
) {
    val totalFrameCount: Int
        get() = steps.size

    val temporaryFrameCount: Int
        get() = steps.count { it.outputRole == MultiFrameOutputRole.TEMPORARY }

    val finalFrameIndex: Int
        get() = steps.lastOrNull { it.outputRole == MultiFrameOutputRole.FINAL_OUTPUT }?.frameIndex ?: 1
}

class MultiFrameCaptureExecutionPlanner {
    fun plan(request: DeviceShotRequest): MultiFrameCaptureExecutionPlan {
        require(request.shotKind == ShotKind.MULTI_FRAME_CAPTURE) {
            "Multi-frame execution planner only supports MULTI_FRAME_CAPTURE requests"
        }

        val frameCount = request.frameCount.coerceAtLeast(1)
        return MultiFrameCaptureExecutionPlan(
            steps = (1..frameCount).map { frameIndex ->
                MultiFrameCaptureStep(
                    frameIndex = frameIndex,
                    outputRole = if (frameIndex == frameCount) {
                        MultiFrameOutputRole.FINAL_OUTPUT
                    } else {
                        MultiFrameOutputRole.TEMPORARY
                    }
                )
            },
            interFrameDelayMillis = request.interFrameDelayMillis.coerceAtLeast(0L)
        )
    }
}
