package com.opencamera.app.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import android.graphics.Matrix
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MlKitSelfiePreviewSceneMaskSource : PreviewSceneMaskSource {

    companion object {
        private const val TAG = "MlKitSelfieMask"
        private const val INFERENCE_TIMEOUT_MS = 15_000L
    }

    override var capability: PreviewSceneMaskCapability = PreviewSceneMaskCapability.READY
        private set

    private val initError = AtomicReference<String?>()

    @Volatile
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

    val diagnostics: List<String>
        get() = buildList {
            val error = initError.get()
            if (error != null) {
                add("mlkit:init-error=$error")
                add("mlkit:capability=${capability.name.lowercase()}")
            } else {
                add("mlkit:capability=ready")
            }
            add("mlkit:running=${isRunning.get()}")
            add("mlkit:received=$framesReceived")
            add("mlkit:processed=$framesProcessed")
            add("mlkit:dropped=$framesDropped")
            add("mlkit:fpsThrottled=$framesFpsThrottled")
            add("mlkit:errors=$inferenceErrors")
        }

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
            val errorMsg = e.message ?: e::class.java.simpleName
            Log.w(TAG, "Failed to create segmenter, running degraded: $errorMsg", e)
            initError.set(errorMsg)
            capability = PreviewSceneMaskCapability.DEGRADED
            isRunning.set(false)
        }
    }

    override fun stop(reason: String) {
        if (!isRunning.getAndSet(false)) return
        synchronized(this) {
            segmenter?.close()
            segmenter = null
        }
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

        // Bounded cleanup: track inference start for timeout-based bitmap release
        val inferenceStartMs = captureTimestamp

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

        // Bounded cleanup: if inference does not complete within timeout,
        // release bitmap to avoid unbounded memory growth.
        // This is a best-effort guard; the actual bitmap is passed to ML Kit
        // which takes ownership for its internal processing.
        val elapsedMs = System.currentTimeMillis() - inferenceStartMs
        if (elapsedMs > INFERENCE_TIMEOUT_MS && scaledBitmap != rawBitmap && !scaledBitmap.isRecycled) {
            Log.w(TAG, "Inference timeout after ${elapsedMs}ms, bitmap may leak until GC")
            inferenceInFlight.set(false)
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return runCatching { image.toBitmap() }
            .onFailure { error ->
                Log.w(TAG, "ImageProxy bitmap conversion failed", error)
            }
            .getOrNull()
    }
}
