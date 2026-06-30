package com.opencamera.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

private val logger = Logger.getLogger("MediaPostProcessors")

class CompositeMediaPostProcessor(
    private val processors: List<MediaPostProcessor>,
    private val onProcessorTimed: ((name: String, elapsedMs: Long) -> Unit)? = null,
    private val resourceBudget: CameraResourceBudget = defaultBudgetFor(CameraPerformanceClass.UNKNOWN)
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return processWithLiveness(
            PostProcessRequest(result = result),
            PostProcessTimeSource { System.nanoTime() / 1_000_000L }
        )
    }

    /**
     * Liveness-aware entry point. When [PostProcessRequest.liveness] is non-null the
     * composite enforces a per-processor deadline and stops the pipeline with a typed
     * [PostProcessFailureCause.TIMEOUT] failure on expiry.
     *
     * Cooperative cancel ([CancellationException]) from outside the watchdog is always
     * propagated — it is never misclassified as a timeout.
     */
    suspend fun processWithLiveness(
        request: PostProcessRequest,
        timeSource: PostProcessTimeSource
    ): ShotResult {
        val liveness = request.liveness
        if (liveness == null) return processBody(request.result, timeSource, liveness)

        // Entry deadline check
        if (liveness.isExpired(timeSource.elapsedMillis())) {
            val failure = PostProcessFailure(
                stage = PostProcessFailureStage.COMPOSITE,
                cause = PostProcessFailureCause.TIMEOUT,
                integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                disposition = PostProcessFailureDisposition.RECOVERABLE,
                processorName = "entry"
            )
            return request.result
                .addPipelineNotes("liveness:postprocess-timeout=entry")
                .addStructuredPostProcessFailure(failure)
        }

        return processBody(request.result, timeSource, liveness)
    }

    private suspend fun processBody(
        initial: ShotResult,
        timeSource: PostProcessTimeSource,
        liveness: PostProcessLivenessDeadline?
    ): ShotResult {
        var current = initial
        val processorTimings = mutableListOf<Pair<String, Long>>()
        var stopped = false
        for (processor in processors) {
            if (!processor.isApplicable(current)) {
                continue
            }
            val jobClass = processor.jobClass(current)
            val name = processor.diagnosticName()

            // Check deadline before starting this processor
            if (liveness != null && liveness.isExpired(timeSource.elapsedMillis())) {
                val failure = PostProcessFailure(
                    stage = PostProcessFailureStage.COMPOSITE,
                    cause = PostProcessFailureCause.TIMEOUT,
                    integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                    disposition = PostProcessFailureDisposition.RECOVERABLE,
                    processorName = name
                )
                return current
                    .addPipelineNotes("liveness:postprocess-timeout=$name")
                    .addStructuredPostProcessFailure(failure)
            }

            val startNanos = System.nanoTime()

            // Execute processor with remaining budget as deadline
            val processorTimeoutMs = if (liveness != null) {
                val remaining = liveness.deadlineElapsedMillis - timeSource.elapsedMillis()
                timeoutForProcessor(jobClass, remaining.coerceAtLeast(1L))
            } else {
                timeoutForProcessor(jobClass, Long.MAX_VALUE)
            }

            current = try {
                val result = withTimeoutOrNull(processorTimeoutMs) {
                    processor.process(current)
                }
                if (result == null) {
                    // Deadline fired — our watchdog, not cooperative cancel
                    val failure = PostProcessFailure(
                        stage = PostProcessFailureStage.COMPOSITE,
                        cause = PostProcessFailureCause.TIMEOUT,
                        integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                        disposition = PostProcessFailureDisposition.RECOVERABLE,
                        processorName = name
                    )
                    val timedOut = current
                        .addPipelineNotes("liveness:postprocess-timeout=$name")
                        .addStructuredPostProcessFailure(failure)
                    if (jobClass == AlgorithmJobClass.CAPTURE_OPTIONAL || jobClass == AlgorithmJobClass.REALTIME_PREVIEW) {
                        timedOut.addPipelineNotes("resource:optional-timeout=$name")
                    } else {
                        return timedOut
                    }
                } else {
                    result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Error) {
                throw e
            } catch (e: Throwable) {
                val failure = classifyExceptionForRecovery(e, name)
                val action = evaluateRecoveryPolicy(failure)
                logger.log(Level.SEVERE, "PostProcessor '$name' failed [${failure.cause}] action=$action", e)
                current = current.addStructuredPostProcessFailure(failure)
                if (jobClass == AlgorithmJobClass.CAPTURE_OPTIONAL && action != RecoveryAction.PROPAGATE) {
                    current.addPipelineNotes("resource:optional-degraded:$name")
                } else {
                    when (action) {
                        RecoveryAction.STOP_POSTPROCESS -> {
                            val errorMessage = (e.message ?: e.javaClass.simpleName).take(120)
                            stopped = true
                            current.addPipelineNotes("postprocess:failed:$name:$errorMessage")
                        }
                        RecoveryAction.TERMINATE -> {
                            val errorMessage = (e.message ?: e.javaClass.simpleName).take(120)
                            current.addPipelineNotes("postprocess:failed:$name:$errorMessage")
                            return current
                        }
                        RecoveryAction.PROPAGATE -> throw e
                        RecoveryAction.CONTINUE -> {
                            val errorMessage = (e.message ?: e.javaClass.simpleName).take(120)
                            current.addPipelineNotes("postprocess:failed:$name:$errorMessage")
                        }
                        RecoveryAction.RECOVER_RELEASE -> {
                            val errorMessage = (e.message ?: e.javaClass.simpleName).take(120)
                            current.addPipelineNotes("postprocess:failed:$name:$errorMessage")
                            return current
                        }
                    }
                }
            }
            if (stopped) break
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            processorTimings.add(name to elapsedMs)
            onProcessorTimed?.invoke(name, elapsedMs)
        }
        val timingNotes = processorTimings
            .filter { it.second > 2 }
            .map { "timing:postprocess:${it.first}=${it.second}ms" }
        return if (timingNotes.isNotEmpty()) {
            current.addPipelineNotes(*timingNotes.toTypedArray())
        } else {
            current
        }
    }

    private fun timeoutForProcessor(
        jobClass: AlgorithmJobClass,
        remainingLivenessMillis: Long
    ): Long {
        if (jobClass != AlgorithmJobClass.CAPTURE_OPTIONAL) {
            return remainingLivenessMillis
        }
        return minOf(remainingLivenessMillis, timeoutForJobClass(jobClass, resourceBudget))
            .coerceAtLeast(1L)
    }
}
private fun MediaPostProcessor.diagnosticName(): String {
    val raw = this::class.java.simpleName
    return raw.removeSuffix("PostProcessor").ifEmpty { raw }
}

class PipelineMetadataPostProcessor : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        val deviceNotes = buildList {
            if (result.captureProfile.frameCount > 1) {
                add("frames:${result.captureProfile.frameCount}")
            }
            result.captureProfile.longExposureMillis?.let { add("exposure:${it}ms") }
            if (result.captureProfile.requiresTripod) {
                add("stability:tripod")
            }
            if (result.captureProfile.flashMode != FlashMode.OFF) {
                add("flash:${result.captureProfile.flashMode.name.lowercase()}")
            }
            if (result.captureProfile.torchEnabled) {
                add("torch:on")
            }
            result.captureProfile.stillCaptureQuality?.let { add("stillQuality:${it.tagValue}") }
            result.captureProfile.stillCaptureResolutionPreset
                ?.let { add("stillResolution:${it.tagValue}") }
        }

        val algorithmNotes = buildList {
            result.metadata.algorithmProfile?.let { add("algorithm:$it") }
            result.metadata.watermarkText?.let { add("watermark:$it") }
            result.metadata.customTags["livePhotoDefault"]?.let { add("live-default:$it") }
            result.metadata.customTags["liveWatermarkBehavior"]
                ?.let { behavior -> add("live-watermark:$behavior") }
        }

        val transactionNotes = buildList {
            result.livePhotoBundle?.let { bundle ->
                add("live-photo:bundle")
                add("live-photo:motion=${bundle.motionMimeType}")
                add("live-photo:sidecar=${bundle.sidecarMimeType}")
                addAll(bundle.temporalNotes())
                add("live:sidecar=${if (bundle.sidecarHandle.contentUri != null) "media-store" else "app-private"}")
            }
            result.metadata.customTags["shutterSoundEnabled"]
                ?.let { enabled -> add("shutter-sound:$enabled") }
            if (result.metadata.customTags["selfieMirrorApply"].toBoolean()) {
                add("selfie-mirror:requested")
            }
            result.metadata.customTags["manualDraftState"]?.let { state ->
                val raw = result.metadata.customTags["manualDraftRaw"] ?: "unknown"
                val iso = result.metadata.customTags["manualDraftIso"] ?: "unknown"
                val shutter = result.metadata.customTags["manualDraftShutterSpeedMillis"] ?: "unknown"
                val whiteBalance = result.metadata.customTags["manualDraftWhiteBalanceKelvin"]
                    ?: "unknown"
                add("manual-draft:$state:raw-$raw:iso-$iso:s-$shutter:wb-$whiteBalance")
            }
            if (result.metadata.exifOverrides.isNotEmpty()) {
                add("exif:${result.metadata.exifOverrides.keys.sorted().joinToString(",")}")
            }
        }

        val degradedNotes = buildList {
            val hasDegradedMerge = (result.pipelineNotes + transactionNotes).any {
                it.startsWith("merge:degraded=") || it == "merge:applied=false"
            }
            if (hasDegradedMerge) {
                add("degraded:multi-frame-fusion")
            }
            if ((result.pipelineNotes + transactionNotes).any { it == "live:degraded=metadata-only" }) {
                add("degraded:live-still-only")
            }
        }

        val allNotes = deviceNotes + algorithmNotes + transactionNotes + degradedNotes
        if (allNotes.isEmpty()) {
            return result
        }
        return result.copy(pipelineNotes = result.pipelineNotes + allNotes)
    }
}
