package com.opencamera.app.camera.live

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.opencamera.core.media.FrameDescriptor
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

internal data class CapturedPreviewYuvFrame(
    val descriptor: FrameDescriptor,
    val yPlane: ByteArray,
    val uPlane: ByteArray,
    val vPlane: ByteArray,
    val yRowStride: Int,
    val yPixelStride: Int,
    val uRowStride: Int,
    val uPixelStride: Int,
    val vRowStride: Int,
    val vPixelStride: Int
)

internal interface PreviewMotionSegmentEncoder {
    fun encode(
        frames: List<CapturedPreviewYuvFrame>,
        outputPath: String
    ): Result<String>
}

internal interface MotionSegmentFrameSource {
    fun materializeMotionSegment(
        selectedFrames: List<FrameDescriptor>,
        outputPath: String
    ): Result<String>
}

internal class AndroidPreviewMotionSegmentEncoder : PreviewMotionSegmentEncoder {

    override fun encode(
        frames: List<CapturedPreviewYuvFrame>,
        outputPath: String
    ): Result<String> = runCatching {
        val orderedFrames = frames
            .sortedBy { it.descriptor.timestampNanos }
            .filter { it.descriptor.width >= 2 && it.descriptor.height >= 2 }
        require(orderedFrames.isNotEmpty()) { "no preview frames with yuv payload" }

        val width = orderedFrames.first().descriptor.width and -2
        val height = orderedFrames.first().descriptor.height and -2
        val compatibleFrames = orderedFrames.filter {
            (it.descriptor.width and -2) == width && (it.descriptor.height and -2) == height
        }
        require(compatibleFrames.isNotEmpty()) { "no size-compatible preview frames" }

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val frameRate = estimateFrameRate(compatibleFrames)
        val codec = MediaCodec.createEncoderByType(VIDEO_MIME)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        try {
            val format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRateFor(width, height, frameRate))
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_ROTATION, compatibleFrames.first().descriptor.rotationDegrees)
                }
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            compatibleFrames.forEachIndexed { index, frame ->
                feedFrame(codec, frame, width, height, presentationTimeUs(index, frameRate))
                val drainResult = drain(codec, muxer, bufferInfo, muxerStarted, trackIndex)
                muxerStarted = drainResult.muxerStarted
                trackIndex = drainResult.trackIndex
            }
            signalEndOfStream(codec, presentationTimeUs(compatibleFrames.size, frameRate))
            while (true) {
                val drainResult = drain(codec, muxer, bufferInfo, muxerStarted, trackIndex)
                muxerStarted = drainResult.muxerStarted
                trackIndex = drainResult.trackIndex
                if (drainResult.sawEndOfStream) {
                    break
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching {
                if (muxerStarted) {
                    muxer.stop()
                }
            }
            runCatching { muxer.release() }
        }

        require(outputFile.exists() && outputFile.length() > 0L) {
            "motion segment encoder produced an empty file"
        }
        outputFile.absolutePath
    }

    private fun feedFrame(
        codec: MediaCodec,
        frame: CapturedPreviewYuvFrame,
        width: Int,
        height: Int,
        presentationTimeUs: Long
    ) {
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        require(inputIndex >= 0) { "timed out waiting for encoder input buffer" }
        val inputBuffer = codec.getInputBuffer(inputIndex)
            ?: error("encoder input buffer unavailable")
        inputBuffer.clear()
        inputBuffer.put(frame.toI420(width, height))
        codec.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, presentationTimeUs, 0)
    }

    private fun signalEndOfStream(codec: MediaCodec, presentationTimeUs: Long) {
        val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
    }

    private fun drain(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        muxerStarted: Boolean,
        trackIndex: Int
    ): DrainResult {
        var currentMuxerStarted = muxerStarted
        var currentTrackIndex = trackIndex
        var sawEnd = false
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!currentMuxerStarted) { "encoder output format changed after muxer start" }
                    currentTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    currentMuxerStarted = true
                }
                outputIndex >= 0 -> {
                    val encodedData: ByteBuffer = codec.getOutputBuffer(outputIndex)
                        ?: error("encoder output buffer unavailable")
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        check(currentMuxerStarted) { "muxer has not started" }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, encodedData, bufferInfo)
                    }
                    sawEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (sawEnd) {
                        break
                    }
                }
            }
        }
        return DrainResult(currentMuxerStarted, currentTrackIndex, sawEnd)
    }

    private data class DrainResult(
        val muxerStarted: Boolean,
        val trackIndex: Int,
        val sawEndOfStream: Boolean
    )

    private companion object {
        private const val VIDEO_MIME = "video/avc"
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val OUTPUT_TIMEOUT_US = 10_000L

        private fun estimateFrameRate(frames: List<CapturedPreviewYuvFrame>): Int {
            if (frames.size < 2) {
                return 15
            }
            val durationNanos = frames.last().descriptor.timestampNanos -
                frames.first().descriptor.timestampNanos
            if (durationNanos <= 0L) {
                return 15
            }
            return ((frames.size - 1) * 1_000_000_000.0 / durationNanos)
                .roundToInt()
                .coerceIn(10, 30)
        }

        private fun presentationTimeUs(index: Int, frameRate: Int): Long {
            return index * 1_000_000L / frameRate
        }

        private fun bitRateFor(width: Int, height: Int, frameRate: Int): Int {
            return (width * height * frameRate * 0.18f).roundToInt().coerceAtLeast(800_000)
        }
    }
}

private fun CapturedPreviewYuvFrame.toI420(width: Int, height: Int): ByteArray {
    val ySize = width * height
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val output = ByteArray(ySize + chromaWidth * chromaHeight * 2)

    copyPlane(
        src = yPlane,
        srcRowStride = yRowStride,
        srcPixelStride = yPixelStride,
        width = width,
        height = height,
        dst = output,
        dstOffset = 0,
        dstRowStride = width
    )
    copyPlane(
        src = uPlane,
        srcRowStride = uRowStride,
        srcPixelStride = uPixelStride,
        width = chromaWidth,
        height = chromaHeight,
        dst = output,
        dstOffset = ySize,
        dstRowStride = chromaWidth
    )
    copyPlane(
        src = vPlane,
        srcRowStride = vRowStride,
        srcPixelStride = vPixelStride,
        width = chromaWidth,
        height = chromaHeight,
        dst = output,
        dstOffset = ySize + chromaWidth * chromaHeight,
        dstRowStride = chromaWidth
    )

    return output
}

private fun copyPlane(
    src: ByteArray,
    srcRowStride: Int,
    srcPixelStride: Int,
    width: Int,
    height: Int,
    dst: ByteArray,
    dstOffset: Int,
    dstRowStride: Int
) {
    for (row in 0 until height) {
        for (col in 0 until width) {
            val srcIndex = row * srcRowStride + col * srcPixelStride
            val dstIndex = dstOffset + row * dstRowStride + col
            if (srcIndex < src.size && dstIndex < dst.size) {
                dst[dstIndex] = src[srcIndex]
            }
        }
    }
}
