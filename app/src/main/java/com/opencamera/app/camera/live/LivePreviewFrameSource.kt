package com.opencamera.app.camera.live

import com.opencamera.core.media.*

/**
 * Source of live preview frames for Live Photo capture.
 *
 * Frame source is only active under the [com.opencamera.core.media.CaptureTemplate.STILL_CAPTURE]
 * template. Other templates (e.g. VIDEO_RECORDING, PREVIEW) do not start the frame source,
 * causing [com.opencamera.app.camera.LivePhotoMaterialization.resolveLiveMotionSource] to degrade
 * to METADATA_ONLY silently.
 */
interface LivePreviewFrameSource {
    val isActive: Boolean

    /** Reason passed to the most recent [start] call, or null if never started. */
    val lastStartReason: String?

    /** Reason passed to the most recent [stop] call, or null if never stopped. */
    val lastStopReason: String?

    fun start(policy: FrameBufferPolicy)
    fun stop(reason: String)
    fun selectForLive(shutterTimestampNanos: Long, spec: LivePhotoCaptureSpec): SelectedFrameSet
}
