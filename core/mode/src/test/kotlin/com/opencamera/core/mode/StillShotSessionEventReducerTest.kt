package com.opencamera.core.mode

import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import kotlin.test.Test
import kotlin.test.assertEquals

class StillShotSessionEventReducerTest {

    private val text = StillShotSessionEventText(
        shotStartedHeadline = "Photo capture in progress",
        shotStartedDetail = "Unified shot pipeline accepted the photo save task.",
        shotCompletedHeadline = "Photo saved",
        shotFailedHeadline = "Photo capture failed"
    )

    @Test
    fun `photo shot started updates snapshot`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotStarted(photoShotRequest()),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        check(handled)
        val expected: List<Pair<String, String?>> = listOf(
            "Photo capture in progress" to
                "Unified shot pipeline accepted the photo save task."
        )
        assertEquals(expected, updates)
    }

    @Test
    fun `photo shot completed uses output path as detail`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotCompleted(photoShotResult()),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        check(handled)
        val expected: List<Pair<String, String?>> = listOf("Photo saved" to "/tmp/photo.jpg")
        assertEquals(expected, updates)
    }

    @Test
    fun `photo shot failed uses reason as detail`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotFailed(
                shotId = "photo-1",
                mediaType = MediaType.PHOTO,
                reason = "write failed"
            ),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        check(handled)
        val expected: List<Pair<String, String?>> = listOf("Photo capture failed" to "write failed")
        assertEquals(expected, updates)
    }

    @Test
    fun `video events are ignored`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotStarted(videoShotRequest()),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        check(!handled)
        check(updates.isEmpty())
    }

    private fun photoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "photo-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = null
        )

    private fun videoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "video-1",
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = LivePhotoCaptureSpec()
        )

    private fun photoShotResult(): ShotResult =
        ShotResult(
            shotId = "photo-1",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/photo.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/photo.jpg"),
            metadata = MediaMetadata()
        )
}
