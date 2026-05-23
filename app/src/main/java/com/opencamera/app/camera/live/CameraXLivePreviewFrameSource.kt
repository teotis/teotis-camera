package com.opencamera.app.camera.live

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.opencamera.core.media.*
import java.util.concurrent.atomic.AtomicBoolean

class CameraXLivePreviewFrameSource : LivePreviewFrameSource {

    companion object {
        private const val TAG = "CameraXLivePreviewFS"
    }

    private val _isActive = AtomicBoolean(false)
    override val isActive: Boolean get() = _isActive.get()

    private var buffer: FrameRingBuffer? = null
    private var frameCounter = 0L

    override fun start(policy: FrameBufferPolicy) {
        if (_isActive.getAndSet(true)) {
            Log.w(TAG, "Already active, ignoring start")
            return
        }
        buffer = FrameRingBuffer(policy)
        frameCounter = 0
        Log.d(TAG, "Started with policy: fps=${policy.targetFps}, maxFrames=${policy.maxFrames}, retention=${policy.retentionWindowMillis}ms")
    }

    override fun stop(reason: String) {
        if (!_isActive.getAndSet(false)) {
            return
        }
        buffer?.clear()
        buffer = null
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
            image.close()
            return
        }

        val currentBuffer = buffer ?: run {
            image.close()
            return
        }

        frameCounter++
        val descriptor = FrameDescriptor(
            frameId = "preview-$frameCounter",
            source = FrameSourceKind.PREVIEW_ANALYSIS,
            timestampNanos = System.nanoTime(),
            width = image.width,
            height = image.height,
            rotationDegrees = rotationDegrees,
            payloadAccess = FramePayloadAccess.METADATA_ONLY,
            lensFacingTag = "BACK",
            zoomRatio = 1.0f
        )

        currentBuffer.append(descriptor)
        image.close()
    }
}
