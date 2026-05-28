package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PipelineNotesOrderTest {

    private fun resultWith(
        captureProfile: CaptureProfile = CaptureProfile(),
        metadata: MediaMetadata = MediaMetadata(),
        livePhotoBundle: LivePhotoBundle? = null
    ): ShotResult {
        return ShotResult(
            shotId = "shot-order-test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/order.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = captureProfile,
            metadata = metadata,
            livePhotoBundle = livePhotoBundle
        )
    }

    private suspend fun processNotes(
        captureProfile: CaptureProfile = CaptureProfile(),
        metadata: MediaMetadata = MediaMetadata(),
        livePhotoBundle: LivePhotoBundle? = null
    ): List<String> {
        return PipelineMetadataPostProcessor().process(
            resultWith(captureProfile, metadata, livePhotoBundle)
        ).pipelineNotes
    }

    @Test
    fun `device diagnostics notes appear before algorithm notes`() = runTest {
        val notes = processNotes(
            captureProfile = CaptureProfile(flashMode = FlashMode.ON),
            metadata = MediaMetadata(algorithmProfile = "photo-vivid")
        )

        val flashIndex = notes.indexOf("flash:on")
        val algorithmIndex = notes.indexOf("algorithm:photo-vivid")

        assertTrue(flashIndex >= 0, "flash note should exist")
        assertTrue(algorithmIndex >= 0, "algorithm note should exist")
        assertTrue(flashIndex < algorithmIndex, "device notes should come before algorithm notes")
    }

    @Test
    fun `algorithm notes appear before transaction notes`() = runTest {
        val notes = processNotes(
            metadata = MediaMetadata(
                algorithmProfile = "photo-vivid",
                customTags = mapOf(
                    "manualDraftState" to "metadata-draft",
                    "manualDraftRaw" to "off",
                    "manualDraftIso" to "auto",
                    "manualDraftShutterSpeedMillis" to "auto",
                    "manualDraftWhiteBalanceKelvin" to "auto"
                )
            )
        )

        val algorithmIndex = notes.indexOf("algorithm:photo-vivid")
        val draftIndex = notes.indexOfFirst { it.startsWith("manual-draft:") }

        assertTrue(algorithmIndex >= 0, "algorithm note should exist")
        assertTrue(draftIndex >= 0, "manual-draft note should exist")
        assertTrue(algorithmIndex < draftIndex, "algorithm notes should come before transaction notes")
    }

    @Test
    fun `empty categories produce no gaps`() = runTest {
        val notes = processNotes(
            captureProfile = CaptureProfile(frameCount = 4)
        )

        assertTrue(notes.none { it.isEmpty() }, "no empty strings in notes")
        assertTrue(notes.contains("frames:4"))
    }

    @Test
    fun `notes order is deterministic across repeated invocations`() = runTest {
        val captureProfile = CaptureProfile(
            frameCount = 3,
            flashMode = FlashMode.AUTO,
            longExposureMillis = 500
        )
        val metadata = MediaMetadata(
            algorithmProfile = "night-multiframe",
            watermarkText = "Test",
            customTags = mapOf(
                "livePhotoDefault" to "on",
                "manualDraftState" to "auto",
                "manualDraftRaw" to "off",
                "manualDraftIso" to "auto",
                "manualDraftShutterSpeedMillis" to "auto",
                "manualDraftWhiteBalanceKelvin" to "auto"
            )
        )

        val runs = (1..10).map {
            processNotes(captureProfile, metadata)
        }

        runs.forEach { notes ->
            assertEquals(runs.first(), notes, "notes should be identical across invocations")
        }
    }
}
