package com.opencamera.app.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.SceneLightState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PreviewSceneBrightnessMonitor(
    private val scope: CoroutineScope,
    private val sampleIntervalMillis: Long = 800L,
    private val bitmapProvider: () -> Bitmap? = { null }
) {
    private val mutableSignals = MutableSharedFlow<PhotoSceneSignal>(extraBufferCapacity = 2)
    val signals: Flow<PhotoSceneSignal> = mutableSignals.asSharedFlow()

    private var samplingJob: Job? = null
    private var isPreviewActive = false
    private var consecutiveLowCount = 0
    private var consecutiveNormalCount = 0
    private var currentLightState = SceneLightState.UNKNOWN

    fun onPreviewStarted() {
        isPreviewActive = true
        startSampling()
    }

    fun onPreviewStopped() {
        isPreviewActive = false
        stopSampling()
    }

    fun onPreviewHostDetached() {
        isPreviewActive = false
        stopSampling()
        currentLightState = SceneLightState.UNKNOWN
        consecutiveLowCount = 0
        consecutiveNormalCount = 0
    }

    private fun startSampling() {
        if (samplingJob?.isActive == true) return
        samplingJob = scope.launch {
            while (isActive && isPreviewActive) {
                sampleBrightness()
                delay(sampleIntervalMillis)
            }
        }
    }

    private fun stopSampling() {
        samplingJob?.cancel()
        samplingJob = null
    }

    private suspend fun sampleBrightness() {
        val bitmap = bitmapProvider() ?: return
        val score = computeAverageLuma(bitmap)
        if (score < 0) return

        val lightState = classifyLightState(score)
        mutableSignals.emit(
            PhotoSceneSignal(
                lightState = lightState,
                brightnessScore = score,
                source = "preview-bitmap-luma"
            )
        )
    }

    private fun classifyLightState(score: Float): SceneLightState {
        return when {
            score <= LOW_LIGHT_ENTER_THRESHOLD -> {
                consecutiveLowCount++
                consecutiveNormalCount = 0
                if (consecutiveLowCount >= 2) {
                    currentLightState = SceneLightState.LOW_LIGHT
                }
                currentLightState
            }
            score >= LOW_LIGHT_EXIT_THRESHOLD -> {
                consecutiveNormalCount++
                consecutiveLowCount = 0
                if (consecutiveNormalCount >= 2) {
                    currentLightState = SceneLightState.NORMAL
                }
                currentLightState
            }
            else -> currentLightState
        }
    }

    companion object {
        const val LOW_LIGHT_ENTER_THRESHOLD = 0.18f
        const val LOW_LIGHT_EXIT_THRESHOLD = 0.24f
        private const val SAMPLE_WIDTH = 32
        private const val SAMPLE_HEIGHT = 32

        fun computeAverageLuma(bitmap: Bitmap): Float {
            val scaled = if (bitmap.width > SAMPLE_WIDTH || bitmap.height > SAMPLE_HEIGHT) {
                Bitmap.createScaledBitmap(bitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, false)
            } else {
                bitmap
            }
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            var totalLuma = 0.0
            for (pixel in pixels) {
                val r = (pixel shr 16 and 0xFF) / 255.0
                val g = (pixel shr 8 and 0xFF) / 255.0
                val b = (pixel and 0xFF) / 255.0
                totalLuma += 0.2126 * r + 0.7152 * g + 0.0722 * b
            }
            return (totalLuma / pixels.size).toFloat()
        }
    }
}

interface SceneBrightnessSignalSource {
    val signals: Flow<PhotoSceneSignal>
    fun onPreviewStarted()
    fun onPreviewStopped()
    fun onPreviewHostDetached()
}

fun PreviewSceneBrightnessMonitor.toSignalSource(): SceneBrightnessSignalSource =
    object : SceneBrightnessSignalSource {
        override val signals: Flow<PhotoSceneSignal> = this@toSignalSource.signals
        override fun onPreviewStarted() = this@toSignalSource.onPreviewStarted()
        override fun onPreviewStopped() = this@toSignalSource.onPreviewStopped()
        override fun onPreviewHostDetached() = this@toSignalSource.onPreviewHostDetached()
    }
