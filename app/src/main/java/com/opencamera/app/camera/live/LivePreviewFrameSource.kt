package com.opencamera.app.camera.live

import com.opencamera.core.media.*

interface LivePreviewFrameSource {
    val isActive: Boolean

    fun start(policy: FrameBufferPolicy)
    fun stop(reason: String)
    fun selectForLive(shutterTimestampNanos: Long, spec: LivePhotoCaptureSpec): SelectedFrameSet
}
