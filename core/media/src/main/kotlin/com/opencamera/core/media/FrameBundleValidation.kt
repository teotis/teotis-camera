package com.opencamera.core.media

import java.io.File

/**
 * Builder for constructing a valid [FrameBundle] with explicit validation at build time.
 * Fields that are left null become explicit unknown sentinels, never silently absent.
 */
class FrameBundleBuilder(private val shotId: String) {
    private val frames = mutableListOf<FrameBundleFrame>()
    private var shotNoiseModel: NoiseModel = NoiseModel.Unknown
    private var shotMotionScore: MotionScore = MotionScore.Unknown
    private var bundleTimestampNanos: Long? = null
    private val diagnostics = mutableListOf<String>()

    fun shotNoiseModel(model: NoiseModel) = apply { shotNoiseModel = model }
    fun shotMotionScore(score: MotionScore) = apply { shotMotionScore = score }
    fun bundleTimestampNanos(nanos: Long) = apply { bundleTimestampNanos = nanos }

    fun addFrame(frame: FrameBundleFrame) = apply {
        diagnostics += frame.degradationReasons
        frames += frame
    }

    fun addFrame(
        frameIndex: Int,
        pixelReference: PixelReference,
        frameRole: FrameRole = FrameRole.FUSION_SUPPLEMENT,
        exposureTimeNanos: Long? = null,
        isoSensitivity: Int? = null,
        timestampNanos: Long? = null,
        whiteBalance: WhiteBalance? = null,
        focalLengthMm: Float? = null,
        lensId: String? = null,
        noiseModel: NoiseModel = NoiseModel.Unknown,
        motionScore: MotionScore = MotionScore.Unknown,
        outputFormat: String = "image/jpeg"
    ) = addFrame(
        FrameBundleFrame(
            frameIndex = frameIndex,
            pixelReference = pixelReference,
            frameRole = frameRole,
            exposureTimeNanos = exposureTimeNanos,
            isoSensitivity = isoSensitivity,
            timestampNanos = timestampNanos,
            whiteBalance = whiteBalance,
            focalLengthMm = focalLengthMm,
            lensId = lensId,
            noiseModel = noiseModel,
            motionScore = motionScore,
            outputFormat = outputFormat
        )
    )

    fun build(): FrameBundle {
        require(frames.isNotEmpty()) { "FrameBundle must contain at least one frame" }
        val sorted = frames.sortedBy { it.frameIndex }.also { frames.clear(); frames.addAll(it) }
        val anchorCount = sorted.count { it.frameRole == FrameRole.FUSION_ANCHOR }
        if (anchorCount > 1) {
            diagnostics += "multiple-anchors=$anchorCount"
        }
        if (anchorCount == 0) {
            diagnostics += "no-anchor-frame"
        }
        return FrameBundle(
            shotId = shotId,
            frames = sorted.toList(),
            shotNoiseModel = shotNoiseModel,
            shotMotionScore = shotMotionScore,
            bundleTimestampNanos = bundleTimestampNanos,
            diagnostics = diagnostics.toList()
        )
    }
}

/**
 * Validation result for a [FrameBundle].
 */
data class FrameBundleValidation(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val status: FrameBundleStatus
) {
    fun summary(): String {
        val parts = mutableListOf("valid=$isValid", "status=$status")
        if (errors.isNotEmpty()) parts.add("errors=[${errors.joinToString("; ")}]")
        if (warnings.isNotEmpty()) parts.add("warnings=[${warnings.joinToString("; ")}]")
        return parts.joinToString("; ")
    }
}

/**
 * Validates a [FrameBundle] and returns a detailed [FrameBundleValidation].
 */
fun FrameBundle.validate(): FrameBundleValidation {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    if (frames.isEmpty()) {
        errors += "bundle-empty: no frames present"
        return FrameBundleValidation(
            isValid = false,
            errors = errors,
            warnings = warnings,
            status = FrameBundleStatus.ABSENT
        )
    }

    frames.forEach { frame ->
        when (frame.pixelReference) {
            is PixelReference.File -> {
                if (frame.pixelReference.path.isBlank() || frame.pixelReference.path.startsWith("unknown://")) {
                    errors += "frame-${frame.frameIndex}: pixel-reference-missing"
                }
            }
            is PixelReference.ContentUri -> {
                if (frame.pixelReference.uri.isBlank()) {
                    errors += "frame-${frame.frameIndex}: pixel-reference-empty-uri"
                }
            }
            is PixelReference.InMemory -> {
                if (frame.pixelReference.bytes.isEmpty()) {
                    errors += "frame-${frame.frameIndex}: pixel-reference-empty-bytes"
                }
            }
        }

        if (frame.isDegraded) {
            warnings += "frame-${frame.frameIndex}: degraded (${frame.degradationReasons.joinToString(", ")})"
        }
        if (frame.noiseModel is NoiseModel.Unknown) {
            warnings += "frame-${frame.frameIndex}: noise-model-unknown"
        }
        if (frame.motionScore is MotionScore.Unknown) {
            warnings += "frame-${frame.frameIndex}: motion-score-unknown"
        }
    }

    val anchorCount = frames.count { it.frameRole == FrameRole.FUSION_ANCHOR }
    if (anchorCount == 0) {
        warnings += "no-anchor-frame"
    }
    if (anchorCount > 1) {
        warnings += "multiple-anchor-frames=$anchorCount"
    }

    val status = computeBundleStatus(this)
    return FrameBundleValidation(
        isValid = errors.isEmpty(),
        errors = errors,
        warnings = warnings,
        status = status
    )
}

// ── Convenience factory functions ───────────────────────────────────

fun buildFrameBundle(shotId: String, block: FrameBundleBuilder.() -> Unit): FrameBundle {
    return FrameBundleBuilder(shotId).apply(block).build()
}

fun fileReference(path: String): PixelReference = PixelReference.File(path)
fun uriReference(uri: String): PixelReference = PixelReference.ContentUri(uri)
fun memoryReference(bytes: ByteArray, label: String = "in-memory"): PixelReference =
    PixelReference.InMemory(bytes, label)

// ── IntermediateOutputPaths compatibility ────────────────────────────

/**
 * Creates an [intermediateOutputPaths]-compatible list from a [FrameBundle].
 * Only file-based pixel references are included. This bridges the existing
 * cleanup/transaction path with the new bundle contract.
 */
fun FrameBundle.toIntermediateOutputPaths(): List<String> =
    frames.mapNotNull { frame ->
        (frame.pixelReference as? PixelReference.File)?.path
    }

/**
 * Returns true if this [FrameBundle]'s file paths are consistent with an
 * existing [intermediateOutputPaths] list — i.e. every file-based frame
 * reference appears in the paths list. Non-file references are ignored.
 */
fun FrameBundle.isCompatibleWith(intermediateOutputPaths: List<String>): Boolean {
    val bundlePaths = frames.mapNotNull { (it.pixelReference as? PixelReference.File)?.path }
    return bundlePaths.all { it in intermediateOutputPaths }
}
