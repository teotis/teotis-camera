package com.opencamera.app.camera

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.opencamera.app.camera.live.LivePhotoMediaStoreWriter
import com.opencamera.app.camera.live.LivePreviewFrameSource
import com.opencamera.app.camera.live.MotionSegmentFrameSource
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.MultiFrameCaptureExecutionPlan
import com.opencamera.core.device.MultiFrameCaptureExecutionPlanner
import com.opencamera.core.device.MultiFrameOutputRole
import com.opencamera.core.device.MultiFrameCaptureStep
import com.opencamera.core.device.MultiFrameTemporaryOutputTracker
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameBundle
import com.opencamera.core.media.FrameBundleFrame
import com.opencamera.core.media.FrameRole
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MotionPhotoContainerSpec
import com.opencamera.core.media.MotionScore
import com.opencamera.core.media.NoiseModel
import com.opencamera.core.media.PixelReference
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.planLiveTemporalAssembly
import com.opencamera.core.media.LiveTemporalPlannerInput
import com.opencamera.core.settings.LiveSaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "StillCaptureExecutor"

/**
 * Request-scoped still capture executor.
 *
 * Owns only single-request mutable data: temporary output tracker, frame bundle
 * accumulation, capture timestamps and Live assembly outcome. Does not retain
 * provider, graph, lifecycle, preview view, bound camera, or current config.
 *
 * Adapter retains DeviceEvent projection, async post-process scheduling,
 * and capture-commit armed state shared with Camera2 session callback.
 */
internal class StillCaptureExecutor(
    private val context: Context,
    private val captureOutputFactory: CaptureOutputFactory,
    private val multiFrameExecutionPlanner: MultiFrameCaptureExecutionPlanner,
) {

    /**
     * Execute a single-frame photo capture via CameraX ImageCapture.
     */
    suspend fun captureSinglePhoto(
        capture: ImageCapture,
        request: PhotoOutputRequest
    ): PhotoCaptureOutcome {
        return suspendCancellableCoroutine { continuation ->
            val deviceCaptureStartedAt = SystemClock.elapsedRealtime()
            capture.takePicture(
                request.outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val deviceCaptureCompletedAt = SystemClock.elapsedRealtime()
                        continuation.resume(
                            PhotoCaptureOutcome.Success(
                                outputPath = request.outputPath,
                                outputHandle = request.resolveOutputHandle(
                                    outputFileResults.savedUri,
                                    context.contentResolver
                                ),
                                deviceCaptureStartedAtElapsedMillis = deviceCaptureStartedAt,
                                deviceCaptureCompletedAtElapsedMillis = deviceCaptureCompletedAt
                            )
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(
                            TAG,
                            "Image capture failed: output=${request.outputPath}",
                            exception
                        )
                        continuation.resume(
                            PhotoCaptureOutcome.Failure(
                                reason = exception.message ?: "Unknown image capture error",
                                cleanupPaths = request.cleanupPaths()
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Execute a multi-frame burst capture, accumulating frames and producing
     * a final fused output. Cleans up temporary outputs on any failure.
     */
    suspend fun captureMultiFrame(
        capture: ImageCapture,
        plan: ShotPlan,
        deviceRequest: DeviceShotRequest,
        beforeFrameCapture: suspend (MultiFrameCaptureStep) -> List<String> = { emptyList() }
    ): PhotoCaptureOutcome {
        val executionPlan = multiFrameExecutionPlanner.plan(deviceRequest)
        val temporaryOutputs = MultiFrameTemporaryOutputTracker()
        val bundleFrames = mutableListOf<FrameBundleFrame>()
        var finalOutputPath: String? = null
        var finalOutputHandle: MediaOutputHandle? = null
        var firstFrameDeviceCaptureStartedAt: Long = 0L
        var lastFrameDeviceCaptureCompletedAt: Long = 0L
        val framePreparationDiagnostics = mutableListOf<String>()

        try {
            executionPlan.steps.forEachIndexed { stepIndex, step ->
                framePreparationDiagnostics += beforeFrameCapture(step)
                val request = when (step.outputRole) {
                    MultiFrameOutputRole.TEMPORARY -> captureOutputFactory.createTemporaryPhotoOutputRequest(
                        shotId = plan.request.shotId,
                        frameIndex = step.frameIndex
                    )

                    MultiFrameOutputRole.FINAL_OUTPUT -> captureOutputFactory.createPhotoOutputRequest(plan.saveTask.saveRequest)
                }
                temporaryOutputs.register(request.cleanupFile)

                when (val result = captureSinglePhoto(capture, request)) {
                    is PhotoCaptureOutcome.Failure -> {
                        temporaryOutputs.cleanup()
                        return result
                    }
                    is PhotoCaptureOutcome.Success -> {
                        if (stepIndex == 0) {
                            firstFrameDeviceCaptureStartedAt = result.deviceCaptureStartedAtElapsedMillis
                        }
                        lastFrameDeviceCaptureCompletedAt = result.deviceCaptureCompletedAtElapsedMillis
                        if (step.outputRole == MultiFrameOutputRole.FINAL_OUTPUT) {
                            finalOutputPath = result.outputPath
                            finalOutputHandle = result.outputHandle
                        }
                        bundleFrames += FrameBundleFrame(
                            frameIndex = step.frameIndex,
                            pixelReference = PixelReference.File(result.outputPath),
                            frameRole = when (step.outputRole) {
                                MultiFrameOutputRole.FINAL_OUTPUT -> FrameRole.FUSION_ANCHOR
                                MultiFrameOutputRole.TEMPORARY -> FrameRole.FUSION_SUPPLEMENT
                            },
                            focusStackRole = step.focusStackRole,
                            noiseModel = NoiseModel.Unknown,
                            motionScore = MotionScore.Unknown,
                            isDegraded = true,
                            degradationReasons = listOf("camera-x:no-per-frame-metadata")
                        )
                    }
                }

                val hasNextStep = stepIndex < executionPlan.steps.lastIndex
                if (hasNextStep && executionPlan.interFrameDelayMillis > 0L) {
                    delay(executionPlan.interFrameDelayMillis)
                }
            }
        } catch (throwable: Throwable) {
            temporaryOutputs.cleanup()
            throw throwable
        }

        val resolvedFinalOutputPath = finalOutputPath
            ?: run {
                temporaryOutputs.cleanup()
                return PhotoCaptureOutcome.Failure("Multi-frame capture did not produce a final frame")
            }
        val resolvedFinalOutputHandle = finalOutputHandle
            ?: MediaOutputHandle(displayPath = resolvedFinalOutputPath)

        val bundle = FrameBundle(
            shotId = plan.request.shotId,
            frames = bundleFrames,
            diagnostics = listOf(
                "device:burst-bundle-frames=${bundleFrames.size}",
                "device:burst-metadata=unknown",
                "device:burst-final-frame=${executionPlan.finalFrameIndex}"
            )
        )

        return PhotoCaptureOutcome.Success(
            outputPath = resolvedFinalOutputPath,
            outputHandle = resolvedFinalOutputHandle,
            diagnostics = executionPlan.toExecutionDiagnostics() +
                framePreparationDiagnostics +
                bundle.diagnostics,
            intermediateOutputPaths = temporaryOutputs.outputPaths(),
            frameBundle = bundle,
            deviceCaptureStartedAtElapsedMillis = firstFrameDeviceCaptureStartedAt,
            deviceCaptureCompletedAtElapsedMillis = lastFrameDeviceCaptureCompletedAt
        )
    }

    /**
     * Execute a Live Photo capture: still photo followed by live motion assembly.
     */
    suspend fun captureLivePhoto(
        capture: ImageCapture,
        plan: ShotPlan,
        livePreviewFrameSource: LivePreviewFrameSource?,
        writeContentUriPayload: (String, String) -> Unit,
        deleteContentUri: (String) -> Unit,
    ): PhotoCaptureOutcome {
        val request = captureOutputFactory.createPhotoOutputRequest(plan.saveTask.saveRequest)
        return when (val result = captureSinglePhoto(capture, request)) {
            is PhotoCaptureOutcome.Failure -> result
            is PhotoCaptureOutcome.Success -> {
                val resolvedSpec = plan.saveTask.livePhotoSpec
                    ?: LivePhotoCaptureSpec()
                val saveFormat = resolvedSpec.saveFormat

                val frameSource = livePreviewFrameSource
                val motionSourceResult = if (frameSource != null) {
                    resolveLiveMotionSource(
                        frameSource = frameSource,
                        shutterTimestampNanos = System.nanoTime(),
                        spec = resolvedSpec
                    )
                } else {
                    LiveMotionSourceResult(
                        source = com.opencamera.core.media.LiveMotionSource.METADATA_ONLY,
                        selectedFrameSet = com.opencamera.core.media.SelectedFrameSet(
                            frames = emptyList(),
                            preShutterCount = 0,
                            postShutterCount = 0,
                            coveredPreShutterMillis = 0,
                            coveredPostShutterMillis = 0,
                            diagnostics = listOf("frame-source:not-configured")
                        ),
                        ringBufferDepthMillis = 0,
                        postShutterBudgetMillis = 0,
                        diagnostics = listOf("live:source=metadata-only")
                    )
                }

                val temporalPlan = planLiveTemporalAssembly(
                    LiveTemporalPlannerInput(
                        captureSpec = resolvedSpec,
                        availableSource = motionSourceResult.source,
                        ringBufferDepthMillis = motionSourceResult.ringBufferDepthMillis,
                        postShutterBudgetMillis = motionSourceResult.postShutterBudgetMillis
                    )
                )
                val resolvedWatermark = resolveLiveWatermarkOutcome(plan)
                val livePhotoBundle = captureOutputFactory.createLivePhotoBundle(
                    stillPath = result.outputPath,
                    stillOutputHandle = result.outputHandle,
                    relativePath = plan.saveTask.saveRequest.relativePath,
                    livePhotoSpec = plan.saveTask.livePhotoSpec,
                    bundleStatus = temporalPlan.expectedBundleStatus,
                    temporalWindow = temporalPlan.temporalWindow,
                    watermarkRequested = resolvedWatermark.requested,
                    watermarkResult = resolvedWatermark.result,
                    watermarkDegradeReason = resolvedWatermark.degradeReason
                )

                val motionFrameSource = frameSource as? MotionSegmentFrameSource
                val mediaStoreWriter = LivePhotoMediaStoreWriter(context)
                val outcome = LivePhotoAssembler.assembleLivePhoto(
                    capturedResult = CapturedPhotoResult(
                        outputPath = result.outputPath,
                        outputHandle = result.outputHandle
                    ),
                    livePhotoBundle = livePhotoBundle,
                    saveFormat = saveFormat,
                    motionSourceResult = motionSourceResult,
                    prepareMotionSegment = { frames, outputPath ->
                        motionFrameSource?.materializeMotionSegment(frames, outputPath)
                            ?: Result.failure(
                                IllegalStateException("motion segment source unavailable")
                            )
                    },
                    materializeContainer = { motionPath ->
                        val savedUri = result.outputHandle.contentUri?.let(Uri::parse)
                        if (savedUri != null) {
                            val combinedResult = mediaStoreWriter.createMotionPhotoBytes(
                                savedUri = savedUri,
                                motionPath = motionPath,
                                spec = MotionPhotoContainerSpec(
                                    motionLengthBytes = File(motionPath).length()
                                )
                            )
                            combinedResult.mapCatching { combinedBytes ->
                                mediaStoreWriter.overwriteMotionPhotoJpeg(savedUri, combinedBytes)
                                    .getOrThrow()
                            }.map {
                                MotionPhotoMaterializationResult(outputUri = savedUri.toString())
                            }
                        } else {
                            Result.failure(
                                IllegalStateException("MediaStore content URI not available for motion photo overwrite")
                            )
                        }
                    },
                    writeContentUriPayload = writeContentUriPayload,
                    mediaStoreWriter = mediaStoreWriter
                )

                // Content URI cleanup on sidecar write failure
                if (!outcome.sidecarWriteSuccess) {
                    livePhotoBundle.sidecarHandle.contentUri?.let { deleteContentUri(it) }
                }

                result.copy(
                    livePhotoBundle = outcome.livePhotoBundle,
                    diagnostics = result.diagnostics + outcome.diagnostics
                )
            }
        }
    }

    /**
     * Apply flash mode to ImageCapture before capture.
     */
    suspend fun applyFlashMode(capture: ImageCapture, flashMode: FlashMode) {
        withContext(Dispatchers.Main.immediate) {
            capture.flashMode = when (flashMode) {
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            }
        }
    }
}

internal fun MultiFrameCaptureExecutionPlan.toExecutionDiagnostics(): List<String> {
    return listOf(
        "device:burst-executed=$totalFrameCount",
        "device:burst-temp-frames=$temporaryFrameCount",
        "device:burst-final-frame=$finalFrameIndex"
    )
}
