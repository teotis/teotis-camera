package com.opencamera.app

import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.session.SavedMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GalleryOpenTargetTest {
    @Test
    fun `content uri saved photo opens content uri`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.SavedMedia(
                outputPath = "Pictures/OpenCamera/photo.jpg",
                renderUri = "content://media/external/images/media/42"
            ),
            savedMediaType = SavedMediaType.PHOTO
        )

        assertEquals(GalleryOpenUriKind.CONTENT_URI, target?.kind)
        assertEquals("content://media/external/images/media/42", target?.uri)
        assertEquals("image/*", target?.mimeType)
    }

    @Test
    fun `relative display path without render uri does not open`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.SavedMedia("Pictures/OpenCamera/photo.jpg"),
            savedMediaType = SavedMediaType.PHOTO
        )

        assertNull(target)
    }

    @Test
    fun `preview snapshot with absolute path opens as file`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.PreviewSnapshot("/tmp/preview.jpg"),
            savedMediaType = SavedMediaType.PHOTO
        )

        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, target?.kind)
        assertEquals("/tmp/preview.jpg", target?.uri)
        assertEquals("image/*", target?.mimeType)
    }

    @Test
    fun `saved video uses video mime type`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.SavedMedia(
                outputPath = "Movies/OpenCamera/video.mp4",
                renderUri = "content://media/external/video/media/99"
            ),
            savedMediaType = SavedMediaType.VIDEO
        )

        assertEquals(GalleryOpenUriKind.CONTENT_URI, target?.kind)
        assertEquals("video/*", target?.mimeType)
    }

    @Test
    fun `absolute file path without content uri opens as file`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.SavedMedia(
                outputPath = "/storage/emulated/0/Pictures/photo.jpg",
                renderUri = null
            ),
            savedMediaType = SavedMediaType.PHOTO
        )

        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, target?.kind)
        assertEquals("/storage/emulated/0/Pictures/photo.jpg", target?.uri)
        assertEquals("image/*", target?.mimeType)
    }

    @Test
    fun `file uri render uri extracts absolute path`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.SavedMedia(
                outputPath = "Pictures/photo.jpg",
                renderUri = "file:///storage/emulated/0/Pictures/photo.jpg"
            ),
            savedMediaType = SavedMediaType.PHOTO
        )

        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, target?.kind)
        assertEquals("/storage/emulated/0/Pictures/photo.jpg", target?.uri)
    }

    @Test
    fun `null source does not open`() {
        val target = galleryOpenTargetFor(
            source = null,
            savedMediaType = SavedMediaType.PHOTO
        )

        assertNull(target)
    }

    @Test
    fun `pending source does not open`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.Pending,
            savedMediaType = SavedMediaType.PHOTO
        )

        assertNull(target)
    }

    @Test
    fun `cached video frame thumbnail is not used as gallery target`() {
        val target = galleryOpenTargetFor(
            source = ThumbnailSource.SavedMedia(
                outputPath = "Movies/OpenCamera/video.mp4",
                renderUri = "content://media/external/video/media/99"
            ),
            savedMediaType = SavedMediaType.VIDEO
        )

        assertEquals("content://media/external/video/media/99", target?.uri)
        assertEquals("video/*", target?.mimeType)
        assertEquals(GalleryOpenUriKind.CONTENT_URI, target?.kind)
    }
}
