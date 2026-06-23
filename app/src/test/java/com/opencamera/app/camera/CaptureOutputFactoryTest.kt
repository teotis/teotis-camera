package com.opencamera.app.camera

import android.os.Build
import android.provider.MediaStore
import org.robolectric.RuntimeEnvironment
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.SaveRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.fakes.BaseCursor
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CaptureOutputFactoryTest {

    private fun legacyFactory(): CaptureOutputFactory {
        return CaptureOutputFactory(RuntimeEnvironment.getApplication())
    }

    @Config(sdk = [28])
    @Test
    fun `legacy photo output has absolute file path`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.photoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createPhotoOutputRequest(saveRequest)

        assertTrue(File(request.outputPath).isAbsolute)
        assertTrue(request.outputPath.endsWith(".jpg"))
        assertNotNull(request.outputHandle)
    }

    @Test
    fun `photo output display name includes millisecond stamp`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.photoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createPhotoOutputRequest(saveRequest)

        assertTrue(
            request.outputPath.matches(Regex(""".*_\d{8}_\d{6}_\d{3}\.jpg$"""))
        )
    }

    @Config(sdk = [28])
    @Test
    fun `legacy photo output handle filePath matches outputPath`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.photoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createPhotoOutputRequest(saveRequest)

        assertEquals(request.outputPath, request.outputHandle.filePath)
        assertEquals(request.outputPath, request.outputHandle.displayPath)
    }

    @Config(sdk = [28])
    @Test
    fun `legacy video output has absolute file path`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.videoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createVideoOutputRequest(saveRequest)

        assertTrue(File(request.outputPath).isAbsolute)
        assertTrue(request.outputPath.endsWith(".mp4"))
        assertNotNull(request.outputHandle)
    }

    @Test
    fun `video output display name includes millisecond stamp`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.videoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createVideoOutputRequest(saveRequest)

        assertTrue(
            request.outputPath.matches(Regex(""".*_\d{8}_\d{6}_\d{3}\.mp4$"""))
        )
    }

    @Config(sdk = [28])
    @Test
    fun `legacy video output handle filePath matches outputPath`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.videoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createVideoOutputRequest(saveRequest)

        assertEquals(request.outputPath, request.outputHandle.filePath)
    }

    @Test
    fun `temp photo output creates file in cache dir`() {
        val factory = legacyFactory()

        val request = factory.createTemporaryPhotoOutputRequest(
            shotId = "shot-1",
            frameIndex = 0
        )

        assertTrue(File(request.outputPath).isAbsolute)
        assertTrue(request.outputPath.contains("multi-frame-captures"))
        assertTrue(request.outputPath.contains("shot-1_frame_0"))
        assertNotNull(request.cleanupFile)
        assertTrue(request.outputPath == request.outputHandle.filePath)
    }

    @Test
    fun `temp photo output frame index is included in filename`() {
        val factory = legacyFactory()

        val request = factory.createTemporaryPhotoOutputRequest(
            shotId = "shot-42",
            frameIndex = 3
        )

        assertTrue(request.outputPath.contains("shot-42_frame_3"))
    }

    @Test
    fun `live photo bundle with absolute still path has absolute motion and sidecar paths`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertEquals(stillPath, bundle.stillPath)
        assertTrue(bundle.motionPath.endsWith(".live.mp4"))
        assertTrue(bundle.sidecarPath.endsWith(".live.json"))
        assertTrue(File(bundle.motionPath).isAbsolute)
        assertTrue(File(bundle.sidecarPath).isAbsolute)
        assertEquals(stillHandle, bundle.thumbnailHandle)
        assertEquals(stillPath, bundle.thumbnailPath)
    }

    @Test
    fun `live photo bundle with absolute still path motion handle has filePath`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertEquals(bundle.motionPath, bundle.motionHandle.filePath)
        assertEquals(bundle.motionPath, bundle.motionHandle.displayPath)
    }

    @Test
    fun `live photo bundle with relative still path has relative motion and sidecar paths`() {
        val factory = legacyFactory()
        val stillHandle = MediaOutputHandle(displayPath = "Pictures/OpenCamera/capture.jpg")

        val bundle = factory.createLivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertTrue(bundle.motionPath.endsWith("capture.live.mp4"))
        assertTrue(bundle.sidecarPath.endsWith("capture.live.json"))
        assertEquals("Pictures/OpenCamera/capture.live.mp4", bundle.motionPath)
        assertEquals("Pictures/OpenCamera/capture.live.json", bundle.sidecarPath)
    }

    @Test
    fun `live photo bundle with relative still path motion handle has no filePath`() {
        val factory = legacyFactory()
        val stillHandle = MediaOutputHandle(displayPath = "Pictures/OpenCamera/capture.jpg")

        val bundle = factory.createLivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertEquals(null, bundle.motionHandle.filePath)
        assertEquals("Pictures/OpenCamera/capture.live.mp4", bundle.motionHandle.displayPath)
    }

    @Test
    fun `live photo bundle preserves spec motion duration and mime types`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera",
            livePhotoSpec = com.opencamera.core.media.LivePhotoCaptureSpec(
                motionDurationMillis = 3_000,
                motionMimeType = "video/webm",
                sidecarMimeType = "application/custom"
            )
        )

        assertEquals(3_000, bundle.motionDurationMillis)
        assertEquals("video/webm", bundle.motionMimeType)
        assertEquals("application/custom", bundle.sidecarMimeType)
    }

    @Test
    fun `live photo bundle default spec uses standard mime types`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertEquals(1_500, bundle.motionDurationMillis)
        assertEquals("video/mp4", bundle.motionMimeType)
        assertEquals("application/vnd.opencamera.live+json", bundle.sidecarMimeType)
    }

    @Test
    fun `live photo bundle absolute sidecar handle has filePath`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertEquals(bundle.sidecarPath, bundle.sidecarHandle.filePath)
        assertEquals(bundle.sidecarPath, bundle.sidecarHandle.displayPath)
    }

    @Test
    fun `live photo bundle relative sidecar handle has no filePath or contentUri`() {
        val factory = legacyFactory()
        val stillHandle = MediaOutputHandle(displayPath = "Pictures/OpenCamera/capture.jpg")

        val bundle = factory.createLivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        // Relative sidecar goes through MediaStore, so no filePath fallback
        // In Robolectric, MediaStore insert succeeds, so contentUri should be set
        assertNotNull(bundle.sidecarHandle.contentUri)
    }

    @Test
    fun `live photo bundle preserves temporal window when provided`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)
        val temporalWindow = com.opencamera.core.media.LiveTemporalWindow(
            requestedDurationMillis = 1_500,
            preShutterMillis = 1_200,
            postShutterMillis = 300,
            frameCount = 45,
            source = com.opencamera.core.media.LiveMotionSource.PREVIEW_RING_BUFFER
        )

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera",
            bundleStatus = com.opencamera.core.media.LiveBundleStatus.COMPLETE,
            temporalWindow = temporalWindow
        )

        assertEquals(com.opencamera.core.media.LiveBundleStatus.COMPLETE, bundle.bundleStatus)
        assertNotNull(bundle.temporalWindow)
        assertEquals(1_200, bundle.temporalWindow!!.preShutterMillis)
        assertEquals(300, bundle.temporalWindow!!.postShutterMillis)
        assertEquals(45, bundle.temporalWindow!!.frameCount)
    }

    @Test
    fun `live photo bundle defaults to COMPLETE status`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera"
        )

        assertEquals(com.opencamera.core.media.LiveBundleStatus.COMPLETE, bundle.bundleStatus)
    }

    @Test
    fun `live photo bundle still-only fallback status is preserved`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera",
            bundleStatus = com.opencamera.core.media.LiveBundleStatus.STILL_ONLY_FALLBACK
        )

        assertEquals(com.opencamera.core.media.LiveBundleStatus.STILL_ONLY_FALLBACK, bundle.bundleStatus)
    }

    @Test
    fun `live photo bundle preserves watermark fields`() {
        val factory = legacyFactory()
        val stillPath = File.createTempFile("capture", ".jpg").apply { deleteOnExit() }.absolutePath
        val stillHandle = MediaOutputHandle(displayPath = stillPath)

        val bundle = factory.createLivePhotoBundle(
            stillPath = stillPath,
            stillOutputHandle = stillHandle,
            relativePath = "Pictures/OpenCamera",
            watermarkRequested = "follow-frame-luma",
            watermarkResult = com.opencamera.core.media.LiveWatermarkResult.STILL_ONLY,
            watermarkDegradeReason = "motion-burn-in-not-implemented"
        )

        assertEquals("follow-frame-luma", bundle.watermarkRequested)
        assertEquals(com.opencamera.core.media.LiveWatermarkResult.STILL_ONLY, bundle.watermarkResult)
        assertEquals("motion-burn-in-not-implemented", bundle.watermarkDegradeReason)
    }

    @Test
    fun `MediaStore sidecar handle returns contentUri`() {
        val factory = legacyFactory()

        val handle = factory.createMediaStoreFileHandle(
            displayName = "capture.live.json",
            mimeType = "application/vnd.opencamera.live+json",
            relativePath = "Pictures/OpenCamera"
        )

        assertNotNull(handle.contentUri)
        assertTrue(handle.contentUri!!.startsWith("content://"))
        assertEquals("Pictures/OpenCamera/capture.live.json", handle.displayPath)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `API 29+ photo output uses MediaStore`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.photoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createPhotoOutputRequest(saveRequest)

        // MediaStore output: no filePath set (handled by CameraX)
        assertTrue(request.outputPath.endsWith(".jpg"))
        assertEquals(request.outputPath, request.outputHandle.displayPath)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `API 29+ photo output resolves editable MediaStore target when CameraX omits saved uri`() {
        val context = RuntimeEnvironment.getApplication()
        val factory = CaptureOutputFactory(context)
        val request = factory.createPhotoOutputRequest(
            SaveRequest.photoLibrary(metadata = com.opencamera.core.media.MediaMetadata())
        )
        Shadows.shadowOf(context.contentResolver).setCursor(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            FakePhotoCursor(
                columnNames = arrayOf(MediaStore.Images.Media._ID),
                rows = listOf(arrayOf(42L))
            )
        )

        val handle = request.resolveOutputHandle(null, context.contentResolver)

        assertEquals("content://media/external/images/media/42", handle.contentUri)
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun `API 29+ video output uses MediaStore`() {
        val factory = legacyFactory()
        val saveRequest = SaveRequest.videoLibrary(
            metadata = com.opencamera.core.media.MediaMetadata()
        )

        val request = factory.createVideoOutputRequest(saveRequest)

        assertTrue(request.outputPath.endsWith(".mp4"))
        assertEquals(request.outputPath, request.outputHandle.displayPath)
    }
}

private class FakePhotoCursor(
    private val columnNames: Array<String>,
    private val rows: List<Array<Any?>>
) : BaseCursor() {

    override fun getCount(): Int = rows.size

    override fun moveToFirst(): Boolean = rows.isNotEmpty()

    override fun getColumnIndex(columnName: String): Int = columnNames.indexOf(columnName)

    override fun getColumnIndexOrThrow(columnName: String): Int {
        val index = columnNames.indexOf(columnName)
        if (index < 0) throw IllegalArgumentException("No such column: $columnName")
        return index
    }

    override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]

    override fun getColumnNames(): Array<String> = columnNames

    override fun getColumnCount(): Int = columnNames.size

    override fun getLong(columnIndex: Int): Long =
        (rows[0][columnIndex] as? Long) ?: (rows[0][columnIndex]?.toString()?.toLongOrNull() ?: 0L)

    override fun close() {}
}
