package com.opencamera.app

import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.session.SavedMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 覆盖行为:
// - SavedMedia + content:// renderUri -> CONTENT_URI 类型
// - SavedMedia + file:// renderUri -> ABSOLUTE_FILE 类型
// - SavedMedia + 绝对路径 outputPath -> ABSOLUTE_FILE 类型
// - SavedMedia + VIDEO -> mimeType video/*
// - SavedMedia + PHOTO -> mimeType image/*
// - PreviewSnapshot -> mimeType image/*, kind ABSOLUTE_FILE
// - null source -> null
// - 空 renderUri 回退到 outputPath
// - 非 content:// 且非 file:// 且非绝对路径 -> null
//
// 不适合单测的行为: 无
class GalleryOpenTargetLogicTest {

    @Test
    fun `saved media with content uri renderUri returns CONTENT_URI type`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = "content://media/external/images/1"
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals(GalleryOpenUriKind.CONTENT_URI, result.kind)
        assertEquals("content://media/external/images/1", result.uri)
    }

    @Test
    fun `saved media with file uri renderUri returns ABSOLUTE_FILE type`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = "file:///sdcard/photo.jpg"
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, result.kind)
        assertEquals("/sdcard/photo.jpg", result.uri)
    }

    @Test
    fun `saved media with absolute outputPath and no renderUri returns ABSOLUTE_FILE`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = null
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, result.kind)
        assertEquals("/sdcard/photo.jpg", result.uri)
    }

    @Test
    fun `saved media with video type returns video mime type`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/video.mp4",
            renderUri = "content://media/external/video/1"
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.VIDEO)
        assertNotNull(result)
        assertEquals("video/*", result.mimeType)
    }

    @Test
    fun `saved media with photo type returns image mime type`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = "content://media/external/images/1"
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals("image/*", result.mimeType)
    }

    @Test
    fun `saved media with null savedMediaType defaults to image mime type`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = "content://media/external/images/1"
        )
        val result = galleryOpenTargetFor(source, null)
        assertNotNull(result)
        assertEquals("image/*", result.mimeType)
    }

    @Test
    fun `preview snapshot returns image mime type and ABSOLUTE_FILE kind`() {
        val source = ThumbnailSource.PreviewSnapshot(outputPath = "/sdcard/preview.jpg")
        val result = galleryOpenTargetFor(source, null)
        assertNotNull(result)
        assertEquals("image/*", result.mimeType)
        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, result.kind)
        assertEquals("/sdcard/preview.jpg", result.uri)
    }

    @Test
    fun `null source returns null`() {
        assertNull(galleryOpenTargetFor(null, SavedMediaType.PHOTO))
        assertNull(galleryOpenTargetFor(null, null))
    }

    @Test
    fun `empty renderUri falls back to absolute outputPath`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = ""
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, result.kind)
        assertEquals("/sdcard/photo.jpg", result.uri)
    }

    @Test
    fun `blank renderUri falls back to absolute outputPath`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/photo.jpg",
            renderUri = "   "
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, result.kind)
        assertEquals("/sdcard/photo.jpg", result.uri)
    }

    @Test
    fun `non absolute outputPath with no renderUri returns null`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "relative/photo.jpg",
            renderUri = null
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNull(result)
    }

    @Test
    fun `file uri renderUri strips prefix correctly`() {
        val source = ThumbnailSource.SavedMedia(
            outputPath = "/sdcard/fallback.jpg",
            renderUri = "file:///storage/emulated/0/photo.jpg"
        )
        val result = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        assertNotNull(result)
        assertEquals(GalleryOpenUriKind.ABSOLUTE_FILE, result.kind)
        assertEquals("/storage/emulated/0/photo.jpg", result.uri)
    }

    @Test
    fun `preview snapshot ignores savedMediaType parameter`() {
        val source = ThumbnailSource.PreviewSnapshot(outputPath = "/sdcard/snap.jpg")
        val resultPhoto = galleryOpenTargetFor(source, SavedMediaType.PHOTO)
        val resultVideo = galleryOpenTargetFor(source, SavedMediaType.VIDEO)
        assertNotNull(resultPhoto)
        assertNotNull(resultVideo)
        // PreviewSnapshot always returns image/* regardless of savedMediaType
        assertEquals("image/*", resultPhoto.mimeType)
        assertEquals("image/*", resultVideo.mimeType)
    }

    @Test
    fun `none source returns null`() {
        assertNull(galleryOpenTargetFor(ThumbnailSource.None, SavedMediaType.PHOTO))
    }

    @Test
    fun `pending source returns null`() {
        assertNull(galleryOpenTargetFor(ThumbnailSource.Pending, SavedMediaType.PHOTO))
    }
}
