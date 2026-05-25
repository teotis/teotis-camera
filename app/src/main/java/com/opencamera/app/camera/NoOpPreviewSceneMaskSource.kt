package com.opencamera.app.camera

import android.util.Log
import androidx.camera.core.ImageProxy

class NoOpPreviewSceneMaskSource : PreviewSceneMaskSource {

    companion object {
        private const val TAG = "NoOpSceneMask"
    }

    override val capability: PreviewSceneMaskCapability = PreviewSceneMaskCapability.UNSUPPORTED

    override fun start(config: PreviewSceneMaskConfig) {
        Log.d(TAG, "start: no-op, backend=${config.backendId}")
    }

    override fun stop(reason: String) {
        Log.d(TAG, "stop: $reason")
    }

    override fun latestMask(): PreviewSceneMaskPayload? = null

    override fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int) {
        // No-op: fanout owns ImageProxy lifecycle
    }
}
