package com.opencamera.app.camera

import com.opencamera.app.camera.live.LivePhotoMediaStoreWriter
import com.opencamera.core.media.FrameDescriptor
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MotionPhotoContainerSpec
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

data class MotionPhotoMaterializationResult(
    val outputUri: String,
    val diagnostics: List<String> = emptyList()
)

internal object LivePhotoAssembler {

    fun assembleLivePhoto(
        capturedResult: CapturedPhotoResult,
        livePhotoBundle: LivePhotoBundle,
        saveFormat: LiveSaveFormat,
        motionSourceResult: LiveMotionSourceResult,
        prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String>,
        materializeContainer: (String) -> Result<MotionPhotoMaterializationResult>,
        writeContentUriPayload: (String, String) -> Unit,
        mediaStoreWriter: LivePhotoMediaStoreWriter? = null
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
                prepareMotionSegment = prepareMotionSegment,
                mediaStoreWriter = mediaStoreWriter
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
                    materializationResult.materializationSucceeded && sidecarResult.isFailure -> "degraded"
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
                add("device:live-diagnostic-sidecar=failed:$sidecarError")
            }

            // Watermark notes
            addAll(finalBundle.temporalNotes().filter { it.startsWith("live-watermark:") })
        }

        return LivePhotoOutcome(
            livePhotoBundle = finalBundle,
            sidecarWriteSuccess = sidecarResult.isSuccess,
            diagnostics = diagnostics
        )
    }

    private fun materializeGoogleMotionPhoto(
        bundle: LivePhotoBundle,
        motionSourceResult: LiveMotionSourceResult,
        prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String>,
        materialize: (String) -> Result<MotionPhotoMaterializationResult>
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
            val outcome = materializeResult.getOrDefault(
                MotionPhotoMaterializationResult(outputUri = bundle.stillPath)
            )
            MaterializationBundleResult(
                bundle = bundle.copy(
                    stillPath = outcome.outputUri,
                    motionPath = motionPath,
                    thumbnailPath = outcome.outputUri,
                    thumbnailHandle = MediaOutputHandle(displayPath = outcome.outputUri)
                ),
                materializationSucceeded = true,
                extraDiagnostics = buildList {
                    add("motion-photo:motion-segment=materialized")
                    add("motion-photo:container=google-jpeg")
                    add("motion-photo:xmp=present")
                    addAll(outcome.diagnostics)
                }
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
        prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String>,
        mediaStoreWriter: LivePhotoMediaStoreWriter? = null
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
        if (motionSegmentResult.isFailure) {
            val reason = motionSegmentResult.exceptionOrNull()?.message ?: "unknown"
            return MaterializationBundleResult(
                bundle = bundle.copy(bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK),
                materializationSucceeded = false,
                extraDiagnostics = listOf("motion-photo:motion-segment=failed:$reason")
            )
        }

        val motionPath = motionSegmentResult.getOrDefault(bundle.motionPath)
        val mp4File = File(motionPath)
        val mp4Bytes = if (mp4File.exists()) mp4File.readBytes() else null

        if (mp4Bytes == null) {
            return MaterializationBundleResult(
                bundle = bundle.copy(
                    motionPath = motionPath,
                    bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
                ),
                materializationSucceeded = false,
                extraDiagnostics = listOf(
                    "motion-photo:motion-segment=unavailable"
                )
            )
        }

        val baseName = bundle.stillPath
            .substringAfterLast('/')
            .substringBeforeLast('.')

        val insertResult = if (mediaStoreWriter != null) {
            mediaStoreWriter.insertMotionMp4Sidecar(
                jpegRelativePath = bundle.stillPath,
                mp4DisplayNamePrefix = baseName,
                mp4Bytes = mp4Bytes
            )
        } else null

        if (insertResult == null) {
            return MaterializationBundleResult(
                bundle = bundle.copy(
                    motionPath = motionPath,
                    motionHandle = bundle.motionHandle.copy(
                        displayPath = motionPath,
                        filePath = motionPath.takeIf { File(it).isAbsolute }
                    ),
                    bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
                ),
                materializationSucceeded = false,
                extraDiagnostics = buildList {
                    add("motion-photo:motion-segment=materialized")
                    add("motion-photo:appended-mp4-bytes=${mp4Bytes.size}")
                    add("motion-photo:sidecar-mp4=mediastore-skipped")
                }
            )
        }

        if (insertResult.isFailure) {
            val reason = insertResult.exceptionOrNull()?.message ?: "unknown"
            return MaterializationBundleResult(
                bundle = bundle.copy(
                    motionPath = motionPath,
                    motionHandle = bundle.motionHandle.copy(
                        displayPath = motionPath,
                        filePath = motionPath.takeIf { File(it).isAbsolute }
                    ),
                    bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
                ),
                materializationSucceeded = false,
                extraDiagnostics = buildList {
                    add("motion-photo:motion-segment=materialized")
                    add("motion-photo:appended-mp4-bytes=${mp4Bytes.size}")
                    add("motion-photo:sidecar-mp4=mediastore-failed:$reason")
                }
            )
        }

        val insertedUri = insertResult.getOrThrow()
        val verifyResult = mediaStoreWriter!!.verifyMotionMp4Sidecar(insertedUri)
        if (verifyResult.isFailure) {
            val reason = verifyResult.exceptionOrNull()?.message ?: "unknown"
            return MaterializationBundleResult(
                bundle = bundle.copy(
                    motionPath = motionPath,
                    motionHandle = bundle.motionHandle.copy(
                        displayPath = motionPath,
                        filePath = motionPath.takeIf { File(it).isAbsolute },
                        contentUri = insertedUri.toString()
                    ),
                    bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
                ),
                materializationSucceeded = false,
                extraDiagnostics = buildList {
                    add("motion-photo:motion-segment=materialized")
                    add("motion-photo:appended-mp4-bytes=${mp4Bytes.size}")
                    add("motion-photo:sidecar-mp4=mediastore-failed:verify:$reason")
                }
            )
        }

        val record = verifyResult.getOrThrow()
        return MaterializationBundleResult(
            bundle = bundle.copy(
                motionPath = motionPath,
                motionHandle = bundle.motionHandle.copy(
                    displayPath = motionPath,
                    filePath = motionPath.takeIf { File(it).isAbsolute },
                    contentUri = insertedUri.toString()
                )
            ),
            materializationSucceeded = true,
            extraDiagnostics = buildList {
                add("motion-photo:motion-segment=materialized")
                add("motion-photo:appended-mp4-bytes=${mp4Bytes.size}")
                add("motion-photo:sidecar-mp4=mediastore-inserted")
                add(
                    "motion-photo:sidecar-mp4=verify:" +
                        "${record.displayName}|${record.relativePath}|${record.mimeType}|" +
                        "${record.size}|${record.duration}|${record.isPending}"
                )
            }
        )
    }
}
