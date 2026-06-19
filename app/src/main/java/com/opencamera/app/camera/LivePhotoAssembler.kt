package com.opencamera.app.camera

import com.opencamera.core.media.FrameDescriptor
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.temporalNotes
import com.opencamera.core.settings.LiveSaveFormat
import kotlinx.coroutines.CancellationException
import java.io.File

internal data class CapturedPhotoResult(
    val outputPath: String,
    val outputHandle: MediaOutputHandle
)

internal data class LivePhotoOutcome(
    val livePhotoBundle: LivePhotoBundle?,
    val sidecarWriteSuccess: Boolean,
    val diagnostics: List<String>
)

internal data class MaterializationBundleResult(
    val bundle: LivePhotoBundle,
    val materializationSucceeded: Boolean,
    val extraDiagnostics: List<String>
)

internal object LivePhotoAssembler {

    fun assembleLivePhoto(
        capturedResult: CapturedPhotoResult,
        livePhotoBundle: LivePhotoBundle,
        saveFormat: LiveSaveFormat,
        motionSourceResult: LiveMotionSourceResult,
        prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String>,
        materializeContainer: (String) -> Result<String>,
        writeContentUriPayload: (String, String) -> Unit
    ): LivePhotoOutcome {
        // STILL_JPEG_ONLY: skip motion entirely, return still with diagnostics
        if (saveFormat == LiveSaveFormat.STILL_JPEG_ONLY) {
            return LivePhotoOutcome(
                livePhotoBundle = null,
                sidecarWriteSuccess = true,
                diagnostics = listOf(
                    "live-export:format=still-jpeg-only",
                    "live-export:share-target=still",
                    "live-export:fallback=disabled-by-format"
                )
            )
        }

        val materializationResult = when (saveFormat) {
            LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG -> materializeGoogleMotionPhoto(
                bundle = livePhotoBundle,
                motionSourceResult = motionSourceResult,
                prepareMotionSegment = prepareMotionSegment,
                materialize = materializeContainer
            )

            LiveSaveFormat.MOTION_MP4_SIDECAR -> materializeMp4Sidecar(
                bundle = livePhotoBundle,
                motionSourceResult = motionSourceResult,
                prepareMotionSegment = prepareMotionSegment
            )

            LiveSaveFormat.STILL_JPEG_ONLY -> error("unreachable: handled above")
        }

        val finalBundle = materializationResult.bundle

        // Sidecar write: non-fatal, but CancellationException must rethrow
        val sidecarResult = try {
            com.opencamera.app.camera.materializeLivePhotoSidecar(
                bundle = finalBundle,
                writeContentUriPayload = writeContentUriPayload
            )
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

        val diagnostics = buildList {
            // Format tag and bundle marker
            add("live-export:format=${saveFormat.storageKey}")
            add("device:live-photo=bundle")

            // Motion source diagnostics
            addAll(motionSourceResult.diagnostics)

            // Materialization per-format diagnostics (from materialization step)
            addAll(materializationResult.extraDiagnostics)

            // Format intended vs actual
            add("live-format:intended=${saveFormat.storageKey}")
            add("live-format:actual=${
                if (materializationResult.materializationSucceeded) saveFormat.storageKey else "still-jpeg-only"
            }")
            add("live-motion:status=${
                when {
                    !materializationResult.materializationSucceeded && motionSourceResult.selectedFrameSet.frames.isNotEmpty() -> "failed"
                    materializationResult.materializationSucceeded -> "materialized"
                    else -> "missing"
                }
            }")
            add("gallery-recognition=untested")

            // Share target per format
            when (saveFormat) {
                LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG -> {
                    add("live-export:share-target=motion-photo")
                    val mp4File = File(finalBundle.motionPath)
                    if (mp4File.exists()) {
                        add("motion-photo:appended-mp4-bytes=${mp4File.length()}")
                    }
                }

                LiveSaveFormat.MOTION_MP4_SIDECAR -> {
                    add("live-export:share-target=mp4")
                }

                LiveSaveFormat.STILL_JPEG_ONLY -> {
                    add("live-export:share-target=still")
                }
            }

            // Sidecar status
            if (sidecarResult.isSuccess) {
                add(
                    if (File(finalBundle.sidecarPath).isAbsolute) {
                        "device:live-sidecar=materialized"
                    } else {
                        "device:live-sidecar=planned"
                    }
                )
            } else {
                val sidecarError = sidecarResult.exceptionOrNull()?.message ?: "unknown"
                add("device:live-sidecar=failed:$sidecarError")
                add("device:live-photo=still-only-fallback")
            }

            // Watermark notes
            addAll(finalBundle.temporalNotes().filter { it.startsWith("live-watermark:") })
        }

        return LivePhotoOutcome(
            livePhotoBundle = if (sidecarResult.isSuccess) finalBundle else null,
            sidecarWriteSuccess = sidecarResult.isSuccess,
            diagnostics = diagnostics
        )
    }

    private fun materializeGoogleMotionPhoto(
        bundle: LivePhotoBundle,
        motionSourceResult: LiveMotionSourceResult,
        prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String>,
        materialize: (String) -> Result<String>
    ): MaterializationBundleResult {
        val hasSelectedFrames = motionSourceResult.source == LiveMotionSource.PREVIEW_RING_BUFFER &&
            motionSourceResult.selectedFrameSet.frames.isNotEmpty()
        if (!hasSelectedFrames) {
            return MaterializationBundleResult(
                bundle = bundle,
                materializationSucceeded = false,
                extraDiagnostics = emptyList()
            )
        }

        val motionSegmentResult = prepareMotionSegment(
            motionSourceResult.selectedFrameSet.frames,
            bundle.motionPath
        )
        if (motionSegmentResult.isFailure) {
            val reason = motionSegmentResult.exceptionOrNull()?.message ?: "unknown"
            return MaterializationBundleResult(
                bundle = bundle.copy(bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK),
                materializationSucceeded = false,
                extraDiagnostics = listOf("motion-photo:motion-segment=failed:$reason")
            )
        }

        val motionPath = motionSegmentResult.getOrDefault(bundle.motionPath)
        val materializeResult = materialize(motionPath)
        return if (materializeResult.isSuccess) {
            val outputPath = materializeResult.getOrDefault(bundle.stillPath)
            MaterializationBundleResult(
                bundle = bundle.copy(
                    stillPath = outputPath,
                    motionPath = motionPath,
                    thumbnailPath = outputPath,
                    thumbnailHandle = MediaOutputHandle(displayPath = outputPath)
                ),
                materializationSucceeded = true,
                extraDiagnostics = listOf(
                    "motion-photo:motion-segment=materialized",
                    "motion-photo:container=google-jpeg",
                    "motion-photo:xmp=present"
                )
            )
        } else {
            val reason = materializeResult.exceptionOrNull()?.message ?: "unknown"
            MaterializationBundleResult(
                bundle = bundle.copy(bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK),
                materializationSucceeded = false,
                extraDiagnostics = listOf("motion-photo:container=failed:$reason")
            )
        }
    }

    private fun materializeMp4Sidecar(
        bundle: LivePhotoBundle,
        motionSourceResult: LiveMotionSourceResult,
        prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String>
    ): MaterializationBundleResult {
        val selectedFrames = motionSourceResult.selectedFrameSet.frames
        if (selectedFrames.isEmpty()) {
            return MaterializationBundleResult(
                bundle = bundle.copy(bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK),
                materializationSucceeded = false,
                extraDiagnostics = listOf("motion-photo:motion-segment=unavailable")
            )
        }

        val motionSegmentResult = prepareMotionSegment(selectedFrames, bundle.motionPath)
        return motionSegmentResult.fold(
            onSuccess = { motionPath ->
                val mp4File = File(motionPath)
                MaterializationBundleResult(
                    bundle = bundle.copy(
                        motionPath = motionPath,
                        motionHandle = bundle.motionHandle.copy(
                            displayPath = motionPath,
                            filePath = motionPath.takeIf { File(it).isAbsolute }
                        )
                    ),
                    materializationSucceeded = true,
                    extraDiagnostics = buildList {
                        add("motion-photo:motion-segment=materialized")
                        if (mp4File.exists()) {
                            add("motion-photo:appended-mp4-bytes=${mp4File.length()}")
                        }
                    }
                )
            },
            onFailure = { throwable ->
                MaterializationBundleResult(
                    bundle = bundle.copy(bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK),
                    materializationSucceeded = false,
                    extraDiagnostics = listOf(
                        "motion-photo:motion-segment=failed:${throwable.message ?: "unknown"}"
                    )
                )
            }
        )
    }
}
