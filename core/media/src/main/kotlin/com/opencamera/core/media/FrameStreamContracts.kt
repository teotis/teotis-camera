package com.opencamera.core.media

enum class FrameSourceKind {
    PREVIEW_ANALYSIS,
    STILL_CAPTURE_FEEDBACK,
    PREVIEW_SNAPSHOT,
    VIDEO_MOTION,
    METADATA_ONLY
}

enum class FramePayloadAccess {
    METADATA_ONLY,
    FILE_HANDLE,
    CPU_YUV,
    CPU_RGBA,
    GPU_TEXTURE
}

data class FrameDescriptor(
    val frameId: String,
    val source: FrameSourceKind,
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val payloadAccess: FramePayloadAccess,
    val lensFacingTag: String,
    val zoomRatio: Float,
    val metadata: Map<String, String> = emptyMap()
)

enum class FrameDropPolicy {
    KEEP_LATEST,
    KEEP_KEYFRAMES_AROUND_SHUTTER,
    BLOCK_PRODUCER_NEVER
}

data class FrameBufferPolicy(
    val targetFps: Int,
    val maxFrames: Int,
    val retentionWindowMillis: Long,
    val maxBytes: Long,
    val dropPolicy: FrameDropPolicy
) {
    companion object {
        val PREVIEW_DEFAULT = FrameBufferPolicy(
            targetFps = 10, maxFrames = 12, retentionWindowMillis = 1500,
            maxBytes = 64L * 1024 * 1024, dropPolicy = FrameDropPolicy.KEEP_LATEST
        )
        val LIVE_PREVIEW_DEFAULT = FrameBufferPolicy(
            targetFps = 15, maxFrames = 24, retentionWindowMillis = 2000,
            maxBytes = 128L * 1024 * 1024, dropPolicy = FrameDropPolicy.KEEP_LATEST
        )
        val VIDEO_MOTION_DEFAULT = FrameBufferPolicy(
            targetFps = 30, maxFrames = 90, retentionWindowMillis = 3000,
            maxBytes = 256L * 1024 * 1024, dropPolicy = FrameDropPolicy.KEEP_LATEST
        )
    }
}

sealed interface FramePayload {
    data class FileRef(
        val descriptor: FrameDescriptor,
        val filePath: String,
        val mimeType: String
    ) : FramePayload

    data class YuvPlanesRef(
        val descriptor: FrameDescriptor,
        val yPlane: ByteArray,
        val uPlane: ByteArray,
        val vPlane: ByteArray,
        val rowStride: Int,
        val pixelStride: Int
    ) : FramePayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is YuvPlanesRef) return false
            return descriptor == other.descriptor &&
                yPlane.contentEquals(other.yPlane) &&
                uPlane.contentEquals(other.uPlane) &&
                vPlane.contentEquals(other.vPlane) &&
                rowStride == other.rowStride &&
                pixelStride == other.pixelStride
        }
        override fun hashCode(): Int {
            var result = descriptor.hashCode()
            result = 31 * result + yPlane.contentHashCode()
            result = 31 * result + uPlane.contentHashCode()
            result = 31 * result + vPlane.contentHashCode()
            result = 31 * result + rowStride
            result = 31 * result + pixelStride
            return result
        }
    }

    data class RgbaBufferRef(
        val descriptor: FrameDescriptor,
        val buffer: ByteArray,
        val width: Int,
        val height: Int
    ) : FramePayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RgbaBufferRef) return false
            return descriptor == other.descriptor &&
                buffer.contentEquals(other.buffer) &&
                width == other.width &&
                height == other.height
        }
        override fun hashCode(): Int {
            var result = descriptor.hashCode()
            result = 31 * result + buffer.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }
}

interface FrameLease : AutoCloseable {
    val descriptor: FrameDescriptor
    fun payload(): FramePayload?
}
