package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.device.BlueHourSceneMetrics
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
    private var consecutiveBlueHourCount = 0
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
        consecutiveBlueHourCount = 0
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
        val pixels = extractPixels(bitmap) ?: return
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels) ?: return
        val rawState = BlueHourSceneMetrics.classify(metrics)
        val lightState = classifyWithHysteresis(rawState)

        mutableSignals.emit(
            PhotoSceneSignal(
                lightState = lightState,
                brightnessScore = metrics.averageLuma,
                source = "preview-bitmap-metrics",
                averageLuma = metrics.averageLuma,
                blueCyanRatio = metrics.blueCyanRatio,
                highlightRatio = metrics.highlightRatio,
                confidence = metrics.confidence
            )
        )
    }

    private fun classifyWithHysteresis(rawState: SceneLightState): SceneLightState {
        return when (rawState) {
            SceneLightState.LOW_LIGHT -> {
                consecutiveLowCount++
                consecutiveNormalCount = 0
                consecutiveBlueHourCount = 0
                if (consecutiveLowCount >= 2) {
                    currentLightState = SceneLightState.LOW_LIGHT
                }
                currentLightState
            }
            SceneLightState.NORMAL -> {
                consecutiveNormalCount++
                consecutiveLowCount = 0
                consecutiveBlueHourCount = 0
                if (consecutiveNormalCount >= 2) {
                    currentLightState = SceneLightState.NORMAL
                }
                currentLightState
            }
            SceneLightState.BLUE_HOUR -> {
                consecutiveBlueHourCount++
                consecutiveLowCount = 0
                consecutiveNormalCount = 0
                if (consecutiveBlueHourCount >= 2) {
                    currentLightState = SceneLightState.BLUE_HOUR
                }
                currentLightState
            }
            SceneLightState.UNKNOWN -> currentLightState
        }
    }

    companion object {
        const val LOW_LIGHT_ENTER_THRESHOLD = 0.18f
        const val LOW_LIGHT_EXIT_THRESHOLD = 0.24f
        private const val SAMPLE_WIDTH = 32
        private const val SAMPLE_HEIGHT = 32

        private fun extractPixels(bitmap: Bitmap): IntArray? {
            val scaled = try {
                if (bitmap.width > SAMPLE_WIDTH || bitmap.height > SAMPLE_HEIGHT) {
                    Bitmap.createScaledBitmap(bitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, false)
                } else {
                    bitmap
                }
            } catch (_: Exception) {
                return null
            }
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            return pixels
        }

        fun computeAverageLuma(bitmap: Bitmap): Float {
            val pixels = extractPixels(bitmap) ?: return -1f
            return BlueHourSceneMetrics.analyzePixels(pixels)?.averageLuma ?: -1f
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
