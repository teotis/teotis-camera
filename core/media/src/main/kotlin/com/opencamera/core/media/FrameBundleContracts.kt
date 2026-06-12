package com.opencamera.core.media

import java.io.File

/**
 * Pixel reference for a single captured frame. Kept as file path or URI string
 * in the JVM contract; platform-specific in-memory/YUV references are adapted
 * at the device boundary, not here.
 */
sealed interface PixelReference {
    data class File(val path: String) : PixelReference {
        fun toFile(): java.io.File = java.io.File(path)
    }
    data class ContentUri(val uri: String) : PixelReference
    data class InMemory(val bytes: ByteArray, val label: String = "in-memory") : PixelReference {
        override fun equals(other: Any?): Boolean =
            other is InMemory && bytes.contentEquals(other.bytes) && label == other.label

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + label.hashCode()
    }
}

/**
 * Describes how a frame participates in the fusion pipeline.
 */
enum class FrameRole {
    /** Primary reference frame used for alignment and exposure reference. */
    FUSION_ANCHOR,
    /** Supplementary frame contributing detail or noise reduction. */
    FUSION_SUPPLEMENT,
    /** Diagnostic or debug frame not used in final fusion. */
    DIAGNOSTIC
}

/**
 * Noise model metadata for a frame. UNKNOWN indicates the device or algorithm
 * could not determine a reliable model; consumers must treat the frame conservatively.
 */
sealed interface NoiseModel {
    data class Known(
        val profileId: String,
        val varianceScale: Float = 1.0f,
        val notes: String? = null
    ) : NoiseModel

    data object Unknown : NoiseModel
}

/**
 * Motion score metadata for a frame. UNKNOWN indicates no reliable motion
 * estimate was available; consumers should assume moderate motion.
 */
sealed interface MotionScore {
    data class Known(val score: Float, val source: String = "device") : MotionScore {
        init {
            require(score in 0.0f..1.0f) { "Motion score must be in [0,1], got $score" }
        }
    }

    data object Unknown : MotionScore
}

/**
 * White balance metadata for a frame.
 */
data class WhiteBalance(
    val temperature: Int? = null,
    val tint: Float? = null,
    val isAuto: Boolean = true,
    val presetLabel: String? = null
)

/**
 * Per-frame capture metadata. All fields except pixelReference and frameRole are
 * optional with conservative defaults to support partial metadata scenarios.
 */
data class FrameBundleFrame(
    val frameIndex: Int,
    val pixelReference: PixelReference,
    val frameRole: FrameRole = FrameRole.FUSION_SUPPLEMENT,
    val exposureTimeNanos: Long? = null,
    val isoSensitivity: Int? = null,
    val timestampNanos: Long? = null,
    val whiteBalance: WhiteBalance? = null,
    val focalLengthMm: Float? = null,
    val lensId: String? = null,
    val noiseModel: NoiseModel = NoiseModel.Unknown,
    val motionScore: MotionScore = MotionScore.Unknown,
    val outputFormat: String = "image/jpeg",
    val isDegraded: Boolean = false,
    val degradationReasons: List<String> = emptyList()
)

/**
 * Overall diagnostic status for a frame bundle.
 */
enum class FrameBundleStatus {
    /** No bundle is present for this shot. */
    ABSENT,
    /** All frames are available and non-degraded. */
    PRESENT,
    /** Bundle is present but at least one frame has missing required fields or is marked degraded. */
    DEGRADED
}

/**
 * A complete multi-frame fusion input bundle. Carries per-frame pixel references and
 * capture metadata, shot-level aggregates, and diagnostic information.
 *
 * Consumers downstream must handle all three [FrameBundleStatus] states. When the bundle
 * is absent (e.g. single-frame shot), no frame data is available. When degraded, the
 * processor may still produce output but with reduced quality guarantees.
 */
data class FrameBundle(
    val shotId: String,
    val frames: List<FrameBundleFrame>,
    val shotNoiseModel: NoiseModel = NoiseModel.Unknown,
    val shotMotionScore: MotionScore = MotionScore.Unknown,
    val bundleTimestampNanos: Long? = null,
    val diagnostics: List<String> = emptyList()
) {
    val frameCount: Int get() = frames.size

    val validFrames: List<FrameBundleFrame>
        get() = frames.filter { !it.isDegraded }

    val anchorFrame: FrameBundleFrame?
        get() = frames.firstOrNull { it.frameRole == FrameRole.FUSION_ANCHOR }

    val isDegraded: Boolean
        get() = frames.any { it.isDegraded } ||
            frames.any { it.noiseModel is NoiseModel.Unknown } ||
            frames.any { it.motionScore is MotionScore.Unknown }

    fun status(): FrameBundleStatus = computeBundleStatus(this)

    fun diagnosticsSummary(): String {
        val parts = mutableListOf("frames=${frames.size}")
        val degradedCount = frames.count { it.isDegraded }
        if (degradedCount > 0) parts.add("degraded=$degradedCount")
        val unknownNoise = frames.count { it.noiseModel is NoiseModel.Unknown }
        if (unknownNoise > 0) parts.add("unknown-noise=$unknownNoise")
        val unknownMotion = frames.count { it.motionScore is MotionScore.Unknown }
        if (unknownMotion > 0) parts.add("unknown-motion=$unknownMotion")
        if (diagnostics.isNotEmpty()) parts.addAll(diagnostics)
        return parts.joinToString("; ")
    }
}

// ── Sentinel helpers ────────────────────────────────────────────────

fun unknownNoiseModel(): NoiseModel = NoiseModel.Unknown

fun unknownMotionScore(): MotionScore = MotionScore.Unknown

fun unknownPixelReference(): PixelReference =
    PixelReference.File(path = "unknown://missing-pixel-reference")

// ── Status computation ──────────────────────────────────────────────

fun computeBundleStatus(bundle: FrameBundle?): FrameBundleStatus {
    if (bundle == null || bundle.frames.isEmpty()) return FrameBundleStatus.ABSENT
    return if (bundle.isDegraded) FrameBundleStatus.DEGRADED else FrameBundleStatus.PRESENT
}

// ── ShotResult integration ─────────────────────────────────────────

/**
 * Carry a [FrameBundle] on a [ShotResult]. When null, the shot has no multi-frame
 * fusion input and [FrameBundleStatus] is [FrameBundleStatus.ABSENT].
 *
 * Adding this field does not change the meaning or behavior of [ShotResult.intermediateOutputPaths],
 * which continues to track temporary file paths for cleanup regardless of bundle presence.
 */
fun ShotResult.frameBundleStatus(): FrameBundleStatus = computeBundleStatus(frameBundle)

fun ShotResult.hasFrameBundle(): Boolean = frameBundle != null

fun ShotResult.isFusionDegraded(): Boolean =
    frameBundle?.isDegraded == true
