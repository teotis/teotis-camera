package com.opencamera.core.media

data class LiveTemporalPlannerInput(
    val captureSpec: LivePhotoCaptureSpec,
    val availableSource: LiveMotionSource,
    val ringBufferDepthMillis: Long = 0,
    val postShutterBudgetMillis: Long = 0,
    val resourceBudgetBytes: Long = 256L * 1024 * 1024
)

data class LiveTemporalPlan(
    val temporalWindow: LiveTemporalWindow,
    val expectedBundleStatus: LiveBundleStatus,
    val sidecarPlacement: SidecarPlacement,
    val notes: List<String>
)

enum class SidecarPlacement {
    APP_PRIVATE,
    MEDIA_STORE_COMPANION
}

fun planLiveTemporalAssembly(input: LiveTemporalPlannerInput): LiveTemporalPlan {
    val requestedMillis = input.captureSpec.motionDurationMillis

    return when (input.availableSource) {
        LiveMotionSource.PREVIEW_RING_BUFFER -> {
            if (input.ringBufferDepthMillis <= 0) {
                metadataOnlyFallback(requestedMillis)
            } else {
                val preShutter = minOf(
                    input.ringBufferDepthMillis,
                    (requestedMillis * 0.8).toLong()
                )
                val postShutter = requestedMillis - preShutter
                val frameCount = ((preShutter + postShutter) / 33).toInt().coerceAtLeast(1)
                LiveTemporalPlan(
                    temporalWindow = LiveTemporalWindow(
                        requestedDurationMillis = requestedMillis,
                        preShutterMillis = preShutter,
                        postShutterMillis = postShutter,
                        frameCount = frameCount,
                        source = LiveMotionSource.PREVIEW_RING_BUFFER
                    ),
                    expectedBundleStatus = LiveBundleStatus.COMPLETE,
                    sidecarPlacement = SidecarPlacement.APP_PRIVATE,
                    notes = listOf("live:source=preview-ring-buffer")
                )
            }
        }

        LiveMotionSource.POST_SHUTTER_FRAMES -> {
            if (input.postShutterBudgetMillis <= 0) {
                metadataOnlyFallback(requestedMillis)
            } else {
                val postShutter = minOf(input.postShutterBudgetMillis, requestedMillis)
                val frameCount = (postShutter / 33).toInt().coerceAtLeast(1)
                LiveTemporalPlan(
                    temporalWindow = LiveTemporalWindow(
                        requestedDurationMillis = requestedMillis,
                        preShutterMillis = 0,
                        postShutterMillis = postShutter,
                        frameCount = frameCount,
                        source = LiveMotionSource.POST_SHUTTER_FRAMES
                    ),
                    expectedBundleStatus = LiveBundleStatus.DEGRADED_MOTION,
                    sidecarPlacement = SidecarPlacement.APP_PRIVATE,
                    notes = listOf("live:source=post-shutter-frames")
                )
            }
        }

        LiveMotionSource.VIDEO_RECORDER_SEGMENT -> {
            val postShutter = minOf(input.postShutterBudgetMillis, requestedMillis)
            val frameCount = if (postShutter > 0) (postShutter / 33).toInt().coerceAtLeast(1) else 0
            LiveTemporalPlan(
                temporalWindow = LiveTemporalWindow(
                    requestedDurationMillis = requestedMillis,
                    preShutterMillis = 0,
                    postShutterMillis = postShutter,
                    frameCount = frameCount,
                    source = LiveMotionSource.VIDEO_RECORDER_SEGMENT
                ),
                expectedBundleStatus = LiveBundleStatus.DEGRADED_MOTION,
                sidecarPlacement = SidecarPlacement.APP_PRIVATE,
                notes = listOf("live:source=video-recorder-segment")
            )
        }

        LiveMotionSource.METADATA_ONLY -> {
            metadataOnlyFallback(requestedMillis)
        }
    }
}

private fun metadataOnlyFallback(requestedMillis: Long): LiveTemporalPlan {
    return LiveTemporalPlan(
        temporalWindow = LiveTemporalWindow(
            requestedDurationMillis = requestedMillis,
            preShutterMillis = 0,
            postShutterMillis = 0,
            frameCount = 0,
            source = LiveMotionSource.METADATA_ONLY
        ),
        expectedBundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK,
        sidecarPlacement = SidecarPlacement.APP_PRIVATE,
        notes = listOf("live:degraded=metadata-only")
    )
}
