package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PostProcessInputGuardsTest {
    @Test
    fun `photo jpeg input is editable`() {
        assertEquals(PhotoJpegInput.EDITABLE, photoResult().photoJpegInput())
    }

    @Test
    fun `video input is not a photo`() {
        assertEquals(
            PhotoJpegInput.NOT_PHOTO,
            photoResult(
                mediaType = MediaType.VIDEO,
                saveRequest = SaveRequest.videoLibrary()
            ).photoJpegInput()
        )
    }

    @Test
    fun `non jpeg photo input is unsupported`() {
        assertEquals(
            PhotoJpegInput.UNSUPPORTED_MIME,
            photoResult(
                saveRequest = SaveRequest.photoLibrary().copy(mimeType = "image/heic")
            ).photoJpegInput()
        )
    }

    private fun photoResult(
        mediaType: MediaType = MediaType.PHOTO,
        saveRequest: SaveRequest = SaveRequest.photoLibrary()
    ): ShotResult {
        return ShotResult(
            shotId = "guard-test",
            mediaType = mediaType,
            outputPath = "/tmp/guard-test.jpg",
            outputHandle = MediaOutputHandle(
                displayPath = "/tmp/guard-test.jpg",
                filePath = "/tmp/guard-test.jpg"
            ),
            saveRequest = saveRequest,
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/guard-test.jpg"),
            metadata = MediaMetadata()
        )
    }
}
