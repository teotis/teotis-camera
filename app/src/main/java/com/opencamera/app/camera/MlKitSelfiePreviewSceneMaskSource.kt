package com.opencamera.app.camera

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MlKitSelfiePreviewSceneMaskSource : PreviewSceneMaskSource {

    companion object {
        private const val TAG = "MlKitSelfieMask"
    }

    override val capability: SceneMaskCapability = SceneMaskCapability.READY

    private var segmenter: com.google.mlkit.vision.segmentation.Segmenter? = null
    private val isRunning = AtomicBoolean(false)
    private val inferenceInFlight = AtomicBoolean(false)
    private val latestMaskHolder = java.util.concurrent.atomic.AtomicReference<PreviewSceneMaskPayload?>()

    private val framesReceived = AtomicLong()
    private val framesProcessed = AtomicLong()
    private val framesDropped = AtomicLong()
    private val inferenceErrors = AtomicLong()

    override fun start(config: PreviewSceneMaskConfig) {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Already running, ignoring start")
            return
        }
        try {
            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .enableRawSizeMask()
                .build()
            segmenter = Segmentation.getClient(options)
            Log.d(TAG, "Started: backend=${config.backendId}, target=${config.targetWidth}x${config.targetHeight}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create segmenter, running degraded", e)
            isRunning.set(false)
        }
    }

    override fun stop(reason: String) {
        if (!isRunning.getAndSet(false)) return
        segmenter?.close()
        segmenter = null
        latestMaskHolder.set(null)
        inferenceInFlight.set(false)
        Log.d(TAG, "Stopped: reason=$reason, received=$framesReceived, processed=$framesProcessed, dropped=$framesDropped, errors=$inferenceErrors")
    }

    override fun latestMask(): PreviewSceneMaskPayload? = latestMaskHolder.get()

    override fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int) {
        if (!isRunning.get()) {
            image.close()
            return
        }

        framesReceived.incrementAndGet()

        if (inferenceInFlight.getAndSet(true)) {
            framesDropped.incrementAndGet()
            image.close()
            return
        }

        val currentSegmenter = segmenter
        if (currentSegmenter == null) {
            inferenceInFlight.set(false)
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            inferenceInFlight.set(false)
            framesDropped.incrementAndGet()
            image.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val captureTimestamp = System.currentTimeMillis()

        currentSegmenter.process(inputImage)
            .addOnSuccessListener { mask ->
                inferenceInFlight.set(false)
                framesProcessed.incrementAndGet()

                val buffer = mask.buffer
                val maskWidth = mask.width
                val maskHeight = mask.height
                val floatPixels = FloatArray(maskWidth * maskHeight)
                buffer.rewind()
                buffer.asFloatBuffer().get(floatPixels)

                val byteMask = ByteArray(floatPixels.size) { i ->
                    (floatPixels[i].coerceIn(0f, 1f) * 255f).toInt().toByte()
                }

                latestMaskHolder.set(
                    PreviewSceneMaskPayload(
                        width = maskWidth,
                        height = maskHeight,
                        confidenceMask = byteMask,
                        rotationDegrees = rotationDegrees,
                        timestampMillis = captureTimestamp,
                        diagnostics = listOf(
                            "backend=mlkit-selfie",
                            "mode=stream",
                            "received=$framesReceived",
                            "processed=$framesProcessed",
                            "dropped=$framesDropped"
                        )
                    )
                )
            }
            .addOnFailureListener { e ->
                inferenceInFlight.set(false)
                inferenceErrors.incrementAndGet()
                Log.w(TAG, "Segmentation failed", e)
            }

        image.close()
    }
}
