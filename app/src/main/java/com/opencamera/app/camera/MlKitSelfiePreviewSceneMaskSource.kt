package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MlKitSelfiePreviewSceneMaskSource : PreviewSceneMaskSource {

    companion object {
        private const val TAG = "MlKitSelfieMask"
    }

    override val capability: PreviewSceneMaskCapability = PreviewSceneMaskCapability.READY

    private var segmenter: com.google.mlkit.vision.segmentation.Segmenter? = null
    private val isRunning = AtomicBoolean(false)
    private val inferenceInFlight = AtomicBoolean(false)
    private val latestMaskHolder = AtomicReference<PreviewSceneMaskPayload?>()
    private val activeConfig = AtomicReference<PreviewSceneMaskConfig?>()

    private val framesReceived = AtomicLong()
    private val framesProcessed = AtomicLong()
    private val framesDropped = AtomicLong()
    private val framesFpsThrottled = AtomicLong()
    private val inferenceErrors = AtomicLong()
    private val lastProcessedTimeMs = AtomicLong(0L)

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
            activeConfig.set(config)
            lastProcessedTimeMs.set(0L)
            Log.d(TAG, "Started: backend=${config.backendId}, target=${config.targetWidth}x${config.targetHeight}, maxFps=${config.maxFps}")
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
        activeConfig.set(null)
        inferenceInFlight.set(false)
        Log.d(TAG, "Stopped: reason=$reason, received=$framesReceived, processed=$framesProcessed, dropped=$framesDropped, fpsThrottled=$framesFpsThrottled, errors=$inferenceErrors")
    }

    override fun latestMask(): PreviewSceneMaskPayload? = latestMaskHolder.get()

    override fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int) {
        if (!isRunning.get()) return

        framesReceived.incrementAndGet()

        // maxFps throttle: drop frame before expensive conversion
        val config = activeConfig.get()
        if (config != null && config.maxFps > 0) {
            val minIntervalMs = 1000L / config.maxFps
            val now = System.currentTimeMillis()
            val lastProcessed = lastProcessedTimeMs.get()
            if (lastProcessed > 0 && (now - lastProcessed) < minIntervalMs) {
                framesFpsThrottled.incrementAndGet()
                return
            }
        }

        if (inferenceInFlight.getAndSet(true)) {
            framesDropped.incrementAndGet()
            return
        }

        val currentSegmenter = segmenter
        if (currentSegmenter == null) {
            inferenceInFlight.set(false)
            return
        }

        val rawBitmap = imageProxyToBitmap(image)
        if (rawBitmap == null) {
            inferenceInFlight.set(false)
            framesDropped.incrementAndGet()
            return
        }

        val sourceWidth = rawBitmap.width
        val sourceHeight = rawBitmap.height

        // Downscale to target size before ML Kit processing
        val targetW = config?.targetWidth ?: 256
        val targetH = config?.targetHeight ?: 256
        val scaledBitmap = if (sourceWidth != targetW || sourceHeight != targetH) {
            Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        val inputImage = InputImage.fromBitmap(scaledBitmap, rotationDegrees)
        val captureTimestamp = System.currentTimeMillis()
        lastProcessedTimeMs.set(captureTimestamp)

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
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight,
                        diagnostics = listOf(
                            "backend=mlkit-selfie",
                            "mode=stream",
                            "source=${sourceWidth}x${sourceHeight}",
                            "target=${targetW}x${targetH}",
                            "received=$framesReceived",
                            "processed=$framesProcessed",
                            "dropped=$framesDropped",
                            "fpsThrottled=$framesFpsThrottled"
                        )
                    )
                )
                scaledBitmap.recycle()
            }
            .addOnFailureListener { e ->
                inferenceInFlight.set(false)
                inferenceErrors.incrementAndGet()
                Log.w(TAG, "Segmentation failed", e)
                scaledBitmap.recycle()
            }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planes = image.planes
        if (planes.size < 3) return null

        val width = image.width
        val height = image.height

        if (width <= 0 || height <= 0) return null

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + vSize + uSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
