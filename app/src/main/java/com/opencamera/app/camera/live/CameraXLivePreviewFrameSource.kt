package com.opencamera.app.camera.live

import android.util.Log
import androidx.camera.core.ImageProxy
import com.opencamera.core.media.*
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class CameraXLivePreviewFrameSource(
    private val motionSegmentEncoder: PreviewMotionSegmentEncoder = AndroidPreviewMotionSegmentEncoder()
) : LivePreviewFrameSource, MotionSegmentFrameSource {

    companion object {
        private const val TAG = "CameraXLivePreviewFS"
    }

    private val _isActive = AtomicBoolean(false)
    override val isActive: Boolean get() = _isActive.get()

    @Volatile override var lastStartReason: String? = null
        private set
    @Volatile override var lastStopReason: String? = null
        private set

    private var buffer: FrameRingBuffer? = null
    private val capturedFrames = LinkedHashMap<String, CapturedPreviewYuvFrame>()
    private var frameCounter = 0L

    override fun start(policy: FrameBufferPolicy) {
        if (_isActive.getAndSet(true)) {
            Log.w(TAG, "Already active, ignoring start")
            return
        }
        lastStartReason = "policy=target-fps=${policy.targetFps},maxFrames=${policy.maxFrames}"
        buffer = FrameRingBuffer(policy)
        synchronized(capturedFrames) {
            capturedFrames.clear()
        }
        frameCounter = 0
        Log.d(TAG, "Started with policy: fps=${policy.targetFps}, maxFrames=${policy.maxFrames}, retention=${policy.retentionWindowMillis}ms")
    }

    override fun stop(reason: String) {
        if (!_isActive.getAndSet(false)) {
            return
        }
        lastStopReason = reason
        buffer?.clear()
        buffer = null
        synchronized(capturedFrames) {
            capturedFrames.clear()
        }
        Log.d(TAG, "Stopped: reason=$reason")
    }

    override fun selectForLive(shutterTimestampNanos: Long, spec: LivePhotoCaptureSpec): SelectedFrameSet {
        val currentBuffer = buffer
        if (currentBuffer == null || !_isActive.get()) {
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

    fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int) {
        if (!_isActive.get()) {
            return
        }

        if (buffer == null) return

        try {
            frameCounter++
            val descriptor = FrameDescriptor(
                frameId = "preview-$frameCounter",
                source = FrameSourceKind.PREVIEW_ANALYSIS,
                timestampNanos = System.nanoTime(),
                width = image.width,
                height = image.height,
                rotationDegrees = rotationDegrees,
                payloadAccess = FramePayloadAccess.CPU_YUV,
                lensFacingTag = "BACK",
                zoomRatio = 1.0f
            )
            appendCapturedFrame(image.toCapturedPreviewYuvFrame(descriptor))
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to capture live preview frame", throwable)
        }
    }

    internal fun appendCapturedFrame(frame: CapturedPreviewYuvFrame) {
        val currentBuffer = buffer ?: return
        currentBuffer.append(frame.descriptor)
        synchronized(capturedFrames) {
            capturedFrames[frame.descriptor.frameId] = frame
            val activeFrameIds = currentBuffer.snapshot().map { it.frameId }.toSet()
            val iterator = capturedFrames.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().key !in activeFrameIds) {
                    iterator.remove()
                }
            }
        }
    }

    override fun materializeMotionSegment(
        selectedFrames: List<FrameDescriptor>,
        outputPath: String
    ): Result<String> {
        val frames = synchronized(capturedFrames) {
            selectedFrames.mapNotNull { descriptor -> capturedFrames[descriptor.frameId] }
        }
        return if (frames.isEmpty()) {
            Result.failure(IllegalStateException("no selected frames with yuv payload"))
        } else {
            motionSegmentEncoder.encode(frames, outputPath)
        }
    }
}

private fun ImageProxy.toCapturedPreviewYuvFrame(
    descriptor: FrameDescriptor
): CapturedPreviewYuvFrame {
    val imagePlanes = this.planes
    require(imagePlanes.size >= 3) { "ImageProxy does not expose YUV planes" }
    return CapturedPreviewYuvFrame(
        descriptor = descriptor,
        yPlane = imagePlanes[0].buffer.copyRemainingBytes(),
        uPlane = imagePlanes[1].buffer.copyRemainingBytes(),
        vPlane = imagePlanes[2].buffer.copyRemainingBytes(),
        yRowStride = imagePlanes[0].rowStride,
        yPixelStride = imagePlanes[0].pixelStride,
        uRowStride = imagePlanes[1].rowStride,
        uPixelStride = imagePlanes[1].pixelStride,
        vRowStride = imagePlanes[2].rowStride,
        vPixelStride = imagePlanes[2].pixelStride
    )
}

private fun java.nio.ByteBuffer.copyRemainingBytes(): ByteArray {
    val duplicate = duplicate()
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}
