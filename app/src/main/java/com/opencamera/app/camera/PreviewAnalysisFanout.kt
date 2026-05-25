package com.opencamera.app.camera

import android.util.Log
import androidx.camera.core.ImageProxy

class PreviewAnalysisFanout(
    private val sceneMaskConsumer: ((ImageProxy, Int) -> Unit)? = null,
    private val livePreviewConsumer: ((ImageProxy, Int) -> Unit)? = null
) {
    companion object {
        private const val TAG = "PreviewAnalysisFanout"
    }

    fun analyze(imageProxy: ImageProxy, rotationDegrees: Int) {
        // Live preview copies YUV data first; scene mask may close the
        // ImageProxy synchronously when it needs async ownership of the data.
        try {
            livePreviewConsumer?.invoke(imageProxy, rotationDegrees)
        } catch (e: Exception) {
            Log.w(TAG, "Live preview frame analysis failed", e)
        }
        try {
            sceneMaskConsumer?.invoke(imageProxy, rotationDegrees)
        } catch (e: Exception) {
            Log.w(TAG, "Scene mask analysis failed, live preview continues", e)
        }
        try {
            imageProxy.close()
        } catch (_: Exception) {}
    }
}
