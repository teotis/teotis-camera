package com.opencamera.app.camera.fixture

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.device.DeviceEvent
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.PreviewBrightnessResult
import com.opencamera.core.device.PreviewBrightnessResultStatus
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.PreviewMeteringPersistence
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal data class FixturePreviewFrame(
    val firstFrameLatencyMillis: Long = 48L,
    val snapshot: ThumbnailSource.PreviewSnapshot? = null
)

internal data class FixtureCaptureOutput(
    val outputPath: String = "/fixture/OpenCamera_fixture_001.jpg",
    val renderUri: String? = null,
    val metadata: MediaMetadata = MediaMetadata(
        customTags = mapOf("fixture" to "camera-pipeline")
    ),
    val pipelineNotes: List<String> = listOf("fixture:camera-pipeline=completed"),
    val imageProbe: FixtureImageProbe? = null,
    val imageExpectation: FixtureImageExpectation? = null,
    val elapsedTimestampMs: Long = 120L,
    val failReason: String? = null
) {
    fun thumbnailSource(): ThumbnailSource {
        return ThumbnailSource.SavedMedia(
            outputPath = outputPath,
            renderUri = renderUri
        )
    }

    fun resolvedPipelineNotes(): List<String> {
        val imageNotes = imageProbe?.let { probe ->
            listOf("fixture:image-signature=${probe.signature.receipt}")
        } ?: emptyList()
        val goldenNotes = imageExpectation?.let { expectation ->
            val signature = imageProbe?.signature
            if (signature != null && expectation.matches(signature)) {
                listOf("fixture:golden=passed")
            } else {
                emptyList()
            }
        } ?: emptyList()
        return pipelineNotes + imageNotes + goldenNotes
    }

    fun qualityFailureReason(): String? {
        val signature = imageProbe?.signature ?: return null
        val expectation = imageExpectation ?: return null
        return if (expectation.matches(signature)) {
            null
        } else {
            expectation.mismatchReason(signature)
        }
    }
}

internal data class FixtureImageProbe(
    val signature: FixtureImageSignature
) {
    companion object {
        fun fromArgbPixels(
            width: Int,
            height: Int,
            pixels: List<Int>
        ): FixtureImageProbe {
            return FixtureImageProbe(
                FixtureImageSignature.fromArgbPixels(
                    width = width,
                    height = height,
                    pixels = pixels
                )
            )
        }
    }
}

internal data class FixtureImageExpectation(
    val expectedSignature: FixtureImageSignature,
    val maxAverageLumaDelta: Int = 0
) {
    fun matches(actual: FixtureImageSignature): Boolean {
        return actual.width == expectedSignature.width &&
            actual.height == expectedSignature.height &&
            kotlin.math.abs(actual.averageLuma - expectedSignature.averageLuma) <= maxAverageLumaDelta &&
            actual.darkPixelCount == expectedSignature.darkPixelCount &&
            actual.brightPixelCount == expectedSignature.brightPixelCount &&
            actual.checksum == expectedSignature.checksum
    }

    fun mismatchReason(actual: FixtureImageSignature): String {
        return "fixture golden mismatch: expected ${expectedSignature.receipt}, actual ${actual.receipt}"
    }
}

internal data class FixtureImageSignature(
    val width: Int,
    val height: Int,
    val averageLuma: Int,
    val darkPixelCount: Int,
    val brightPixelCount: Int,
    val checksum: Long
) {
    val receipt: String
        get() = "${width}x${height}:avgLuma=$averageLuma:dark=$darkPixelCount:" +
            "bright=$brightPixelCount:checksum=$checksum"

    companion object {
        fun fromArgbPixels(
            width: Int,
            height: Int,
            pixels: List<Int>
        ): FixtureImageSignature {
            require(width > 0) { "width must be positive" }
            require(height > 0) { "height must be positive" }
            require(pixels.size == width * height) {
                "pixels size ${pixels.size} must match ${width}x$height"
            }

            var lumaTotal = 0
            var dark = 0
            var bright = 0
            var checksum = 1_125_899_906_842_597L
            pixels.forEach { argb ->
                val luma = lumaOf(argb)
                lumaTotal += luma
                if (luma < 32) dark += 1
                if (luma > 224) bright += 1
                checksum = checksum * 31 + (argb.toLong() and 0xffffffffL)
            }

            return FixtureImageSignature(
                width = width,
                height = height,
                averageLuma = lumaTotal / pixels.size,
                darkPixelCount = dark,
                brightPixelCount = bright,
                checksum = checksum
            )
        }

        private fun lumaOf(argb: Int): Int {
            val red = argb shr 16 and 0xff
            val green = argb shr 8 and 0xff
            val blue = argb and 0xff
            return (red * 299 + green * 587 + blue * 114) / 1000
        }
    }
}

internal data class FixtureCameraScenario(
    val capabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    val capabilitiesByLensFacing: Map<LensFacing, DeviceCapabilities> = emptyMap(),
    val previewFrame: FixturePreviewFrame = FixturePreviewFrame(),
    val captureOutputs: List<FixtureCaptureOutput> = listOf(FixtureCaptureOutput()),
    val bindFailureReason: String? = null
) {
    fun captureOutput(index: Int): FixtureCaptureOutput {
        return captureOutputs.getOrElse(index) {
            captureOutputs.lastOrNull() ?: FixtureCaptureOutput()
        }
    }
}

internal class FixtureCameraDeviceAdapter(
    private val scenario: FixtureCameraScenario = FixtureCameraScenario()
) : CameraDeviceAdapter {
    private val mutableEvents = MutableSharedFlow<DeviceEvent>(
        replay = 16,
        extraBufferCapacity = 16
    )
    private var boundGraph: DeviceGraphSpec? = null
    private var captureIndex = 0

    val bindRequests = mutableListOf<DeviceGraphSpec>()
    val recordedCommands = mutableListOf<DeviceCommand>()
    var releaseCount: Int = 0
        private set

    override val capabilities: DeviceCapabilities = scenario.capabilities
    override val events: Flow<DeviceEvent> = mutableEvents.asSharedFlow()

    override fun capabilitiesFor(deviceGraph: DeviceGraphSpec): DeviceCapabilities {
        return scenario.capabilitiesByLensFacing[deviceGraph.preferredLensFacing]
            ?: scenario.capabilities
    }

    override suspend fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec,
        manualCaptureParams: com.opencamera.core.settings.ManualCaptureParams?
    ) {
        bindRequests += deviceGraph
        scenario.bindFailureReason?.let { reason ->
            throw IllegalStateException(reason)
        }
        boundGraph = deviceGraph
        mutableEvents.emit(
            DeviceEvent.PreviewFirstFrameAvailable(
                scenario.previewFrame.firstFrameLatencyMillis
            )
        )
        scenario.previewFrame.snapshot?.let { snapshot ->
            mutableEvents.emit(DeviceEvent.PreviewSnapshotAvailable(snapshot))
        }
    }

    override suspend fun dispatch(command: DeviceCommand) {
        recordedCommands += command
        when (command) {
            is DeviceCommand.ExecuteShot -> executeShot(command.plan)
            is DeviceCommand.StopActiveShot -> Unit
            is DeviceCommand.UpdateZoomRatio -> Unit
            is DeviceCommand.SwitchLensNode -> Unit
            is DeviceCommand.UpdateOutputRotation -> Unit
            is DeviceCommand.ApplyPreviewMetering -> {
                mutableEvents.emit(
                    DeviceEvent.PreviewMeteringCompleted(
                        PreviewMeteringResult(
                            requestId = command.request.requestId,
                            point = command.request.point.clamped(),
                            status = when (command.request.persistence) {
                                PreviewMeteringPersistence.AUTO_CANCEL ->
                                    PreviewMeteringResultStatus.SUCCEEDED
                                PreviewMeteringPersistence.HOLD_UNTIL_CANCELLED ->
                                    PreviewMeteringResultStatus.LOCKED
                            }
                        )
                    )
                )
            }
            is DeviceCommand.CancelPreviewMetering -> Unit
            is DeviceCommand.ApplyPreviewBrightness -> {
                mutableEvents.emit(
                    DeviceEvent.PreviewBrightnessApplied(
                        PreviewBrightnessResult(
                            requestId = command.request.requestId,
                            exposureCompensationSteps = command.request.exposureCompensationSteps,
                            status = PreviewBrightnessResultStatus.APPLIED
                        )
                    )
                )
            }
        }
    }

    override suspend fun release() {
        releaseCount += 1
        boundGraph = null
    }

    override fun boundGraph(): DeviceGraphSpec? = boundGraph

    private suspend fun executeShot(plan: ShotPlan) {
        val output = scenario.captureOutput(captureIndex++)
        mutableEvents.emit(DeviceEvent.ShotStarted(plan.request))
        (output.failReason ?: output.qualityFailureReason())?.let { reason ->
            mutableEvents.emit(
                DeviceEvent.ShotFailed(
                    shotId = plan.request.shotId,
                    mediaType = plan.request.mediaType,
                    reason = reason
                )
            )
            return
        }
        mutableEvents.emit(
            DeviceEvent.CaptureCommitted(
                shotId = plan.request.shotId,
                mediaType = plan.request.mediaType,
                source = "FixtureCameraDeviceAdapter",
                elapsedTimestampMs = output.elapsedTimestampMs
            )
        )
        mutableEvents.emit(
            DeviceEvent.DataReceived(
                shotId = plan.request.shotId,
                mediaType = plan.request.mediaType
            )
        )
        mutableEvents.emit(
            DeviceEvent.ShotCompleted(
                ShotResult(
                    shotId = plan.request.shotId,
                    mediaType = plan.request.mediaType,
                    outputPath = output.outputPath,
                    outputHandle = MediaOutputHandle(
                        displayPath = output.outputPath,
                        filePath = output.outputPath
                    ),
                    saveRequest = plan.request.saveRequest,
                    thumbnailSource = output.thumbnailSource(),
                    captureProfile = plan.request.captureProfile,
                    metadata = output.metadata,
                    pipelineNotes = output.resolvedPipelineNotes(),
                    timing = ShotTiming(
                        deviceCaptureStartedAtElapsedMillis = 0L,
                        deviceCaptureCompletedAtElapsedMillis = output.elapsedTimestampMs,
                        postProcessCompletedAtElapsedMillis = output.elapsedTimestampMs
                    )
                )
            )
        )
    }
}
