package com.opencamera.app.camera.live

import com.opencamera.core.media.MotionPhotoContainerSpec
import com.opencamera.core.media.MotionPhotoJpegContainer
import java.io.File

class MotionPhotoFileMaterializer {

    fun materialize(
        stillPath: String,
        motionPath: String,
        outputPath: String,
        spec: MotionPhotoContainerSpec,
        cleanupTempMotion: Boolean = false
    ): Result<String> {
        return runCatching {
            val stillFile = File(stillPath)
            val motionFile = File(motionPath)
            val outputFile = File(outputPath)

            // Validate inputs
            if (!stillFile.exists()) {
                throw IllegalArgumentException("Still file does not exist: $stillPath")
            }
            if (!motionFile.exists()) {
                throw IllegalArgumentException("Motion file does not exist: $motionPath")
            }

            // Read input files
            val stillBytes = stillFile.readBytes()
            val motionBytes = motionFile.readBytes()

            // A still that is already a motion photo (re-materialization) must be stripped
            // so the XMP lengths and appended MP4 are written exactly once.
            val cleanStillBytes = MotionPhotoJpegContainer.stripMotionSegment(stillBytes)

            // Create combined Motion Photo
            val combinedBytes = MotionPhotoJpegContainer.write(
                jpegBytes = cleanStillBytes,
                motionBytes = motionBytes,
                spec = spec.copy(
                    motionLengthBytes = motionBytes.size.toLong(),
                    stillLengthBytes = cleanStillBytes.size.toLong()
                )
            )

            // Write to output file
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(combinedBytes)

            // Cleanup temp motion file if requested
            if (cleanupTempMotion) {
                motionFile.delete()
            }

            outputFile.absolutePath
        }
    }
}
