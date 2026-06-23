package com.opencamera.app.camera.live

import com.opencamera.core.media.*

class FakeLivePreviewFrameSource : LivePreviewFrameSource {

    private var buffer: FrameRingBuffer? = null
    override val isActive: Boolean get() = buffer != null

    override var lastStartReason: String? = null
        private set
    override var lastStopReason: String? = null
        private set

    override fun start(policy: FrameBufferPolicy) {
        lastStartReason = "policy=target-fps=${policy.targetFps},maxFrames=${policy.maxFrames}"
        buffer = FrameRingBuffer(policy)
    }

    override fun stop(reason: String) {
        lastStopReason = reason
        buffer?.clear()
        buffer = null
    }

    override fun selectForLive(shutterTimestampNanos: Long, spec: LivePhotoCaptureSpec): SelectedFrameSet {
        val currentBuffer = buffer
        if (currentBuffer == null) {
            return SelectedFrameSet(
                frames = emptyList(),
                preShutterCount = 0,
                postShutterCount = 0,
                coveredPreShutterMillis = 0,
                coveredPostShutterMillis = 0,
                diagnostics = listOf("frame-source:not-active")
            )
        }

        val preShutterMillis = (spec.motionDurationMillis * 0.8).toLong()
        val postShutterMillis = spec.motionDurationMillis - preShutterMillis

        return currentBuffer.select(
            FrameSelectionWindow(
                shutterTimestampNanos = shutterTimestampNanos,
                preShutterMillis = preShutterMillis,
                postShutterMillis = postShutterMillis
            )
        )
    }

    fun addFrame(descriptor: FrameDescriptor) {
        buffer?.append(descriptor)
    }
}
