package com.opencamera.app.camera

import com.opencamera.core.media.ContentRegionBounds
import com.opencamera.core.media.ContentRegionDescriptor
import com.opencamera.core.media.ContentRegionRole
import com.opencamera.core.media.ContentSceneHint
import com.opencamera.core.media.ContentTagDescriptor
import com.opencamera.core.media.ContentTagFamily
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskTransform
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInContentDecisionPostProcessorTest {
    @Test
    fun `face content marks check in as people place`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val result = processor.process(
            photoResult(
                mode = "check-in",
                checkInScenario = "clarity",
                contentUnderstanding = ContentUnderstandingSnapshot(
                    snapshotId = "content-face",
                    timestampMillis = 42L,
                    quality = SceneMaskQuality.SAVED_PHOTO,
                    backendId = "fake-content",
                    regions = listOf(
                        ContentRegionDescriptor(
                            regionId = "face-0",
                            role = ContentRegionRole.FACE,
                            quality = SceneMaskQuality.SAVED_PHOTO,
                            backendId = "fake-content",
                            confidence = 0.93f,
                            transform = transform(),
                            bounds = ContentRegionBounds(
                                left = 0.08f,
                                top = 0.18f,
                                right = 0.34f,
                                bottom = 0.68f
                            )
                        )
                    )
                )
            )
        )

        assertEquals("people-place", result.metadata.customTags["checkInContentScenario"])
        assertEquals("region:face", result.metadata.customTags["checkInContentSource"])
        assertEquals("0.93", result.metadata.customTags["checkInContentConfidence"])
        assertEquals("bottom-right", result.metadata.customTags["checkInContentWatermarkPlacement"])
        assertEquals("compact", result.metadata.customTags["checkInContentWatermarkDensity"])
        assertEquals("region:face-0", result.metadata.customTags["checkInContentPlacementSource"])
        assertEquals("face", result.metadata.customTags["checkInContentPlacementRole"])
        assertEquals("0.93", result.metadata.customTags["checkInContentPlacementConfidence"])
        assertEquals("0.21,0.43", result.metadata.customTags["checkInContentPlacementCenter"])
        assertEquals("0.13", result.metadata.customTags["checkInContentPlacementArea"])
        assertEquals("clarity", result.metadata.customTags["checkInOriginalScenario"])
        assertTrue(result.pipelineNotes.contains("checkin-content:scenario=people-place"))
        assertTrue(result.pipelineNotes.contains("checkin-content:source=region:face"))
        assertTrue(result.pipelineNotes.contains("checkin-content:watermark-placement=bottom-right"))
        assertTrue(result.pipelineNotes.contains("checkin-content:placement-source=region:face-0"))
        assertTrue(result.pipelineNotes.contains("checkin-content:placement-center=0.21,0.43"))
        assertTrue(result.pipelineNotes.contains("checkin-content:placement-area=0.13"))
        assertTrue(result.pipelineNotes.contains("checkin-content:watermark-density=compact"))
        assertTrue(result.pipelineNotes.contains("checkin-content:suggested=people-place:current=clarity"))
    }

    @Test
    fun `object tag marks check in as object place`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val result = processor.process(
            photoResult(
                mode = "check-in",
                checkInScenario = "portrait",
                contentUnderstanding = ContentUnderstandingSnapshot.fromTags(
                    snapshotId = "content-object",
                    timestampMillis = 42L,
                    backendId = "fake-content",
                    quality = SceneMaskQuality.SAVED_PHOTO,
                    tags = listOf(
                        ContentTagDescriptor(
                            tagId = "object-coffee-cup",
                            label = "Coffee cup",
                            family = ContentTagFamily.OBJECT,
                            confidence = 0.87f,
                            backendId = "fake-content"
                        )
                    )
                )
            )
        )

        assertEquals("object-place", result.metadata.customTags["checkInContentScenario"])
        assertEquals("tag:object-coffee-cup", result.metadata.customTags["checkInContentSource"])
        assertTrue(result.pipelineNotes.contains("checkin-content:scenario=object-place"))
    }

    @Test
    fun `object scene tag records check in content scene`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val result = processor.process(
            photoResult(
                mode = "check-in",
                checkInScenario = "portrait",
                contentUnderstanding = ContentUnderstandingSnapshot.fromTags(
                    snapshotId = "content-object-food",
                    timestampMillis = 42L,
                    backendId = "fake-content",
                    quality = SceneMaskQuality.SAVED_PHOTO,
                    tags = listOf(
                        ContentTagDescriptor(
                            tagId = "object-food",
                            label = "Food",
                            family = ContentTagFamily.OBJECT,
                            confidence = 0.88f,
                            backendId = "fake-content"
                        )
                    )
                )
            )
        )

        assertEquals("object-place", result.metadata.customTags["checkInContentScenario"])
        assertEquals(ContentSceneHint.FOOD.storageKey, result.metadata.customTags["checkInContentScene"])
        assertEquals("tag:object-food", result.metadata.customTags["checkInContentSceneSource"])
        assertEquals("0.88", result.metadata.customTags["checkInContentSceneConfidence"])
        assertTrue(result.pipelineNotes.contains("checkin-content:scene=food"))
        assertTrue(result.pipelineNotes.contains("checkin-content:scene-source=tag:object-food"))
    }

    @Test
    fun `document semantic region marks check in as clarity`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val result = processor.process(
            photoResult(
                mode = "check-in",
                checkInScenario = "object-place",
                contentUnderstanding = ContentUnderstandingSnapshot(
                    snapshotId = "content-document",
                    timestampMillis = 42L,
                    quality = SceneMaskQuality.SAVED_PHOTO,
                    backendId = "fake-content",
                    regions = listOf(
                        ContentRegionDescriptor(
                            regionId = "document-0",
                            role = ContentRegionRole.DOCUMENT,
                            quality = SceneMaskQuality.SAVED_PHOTO,
                            backendId = "fake-content",
                            confidence = 0.92f,
                            transform = transform()
                        )
                    )
                )
            )
        )

        assertEquals("clarity", result.metadata.customTags["checkInContentScenario"])
        assertEquals("region:document", result.metadata.customTags["checkInContentSource"])
        assertEquals("0.92", result.metadata.customTags["checkInContentConfidence"])
        assertEquals("object-place", result.metadata.customTags["checkInOriginalScenario"])
        assertTrue(result.pipelineNotes.contains("checkin-content:scenario=clarity"))
        assertTrue(result.pipelineNotes.contains("checkin-content:suggested=clarity:current=object-place"))
    }

    @Test
    fun `preview approximate content does not mark check in scenario`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val input = photoResult(
            mode = "check-in",
            checkInScenario = "clarity",
            contentUnderstanding = ContentUnderstandingSnapshot(
                snapshotId = "content-preview-face",
                timestampMillis = 42L,
                quality = SceneMaskQuality.PREVIEW_APPROXIMATE,
                backendId = "preview-content",
                regions = listOf(
                    ContentRegionDescriptor(
                        regionId = "face-0",
                        role = ContentRegionRole.FACE,
                        quality = SceneMaskQuality.PREVIEW_APPROXIMATE,
                        backendId = "preview-content",
                        confidence = 0.93f,
                        transform = transform()
                    )
                )
            )
        )

        val result = processor.process(input)

        assertFalse(result.metadata.customTags.containsKey("checkInContentScenario"))
        assertFalse(result.pipelineNotes.any { it.startsWith("checkin-content:scenario=") })
        assertTrue(result.pipelineNotes.contains("checkin-content:skipped:not-ready"))
        assertTrue(result.pipelineNotes.contains("checkin-content:quality=preview_approximate"))
    }

    @Test
    fun `not ready check in content records shared reason`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val input = photoResult(
            mode = "check-in",
            checkInScenario = "clarity",
            contentUnderstanding = ContentUnderstandingSnapshot.unavailable(
                timestampMillis = 42L,
                backendId = "fake-content",
                reason = "no-recognition-backend"
            )
        )

        val result = processor.process(input)

        assertFalse(result.metadata.customTags.containsKey("checkInContentScenario"))
        assertTrue(result.pipelineNotes.contains("checkin-content:skipped:not-ready"))
        assertTrue(result.pipelineNotes.contains("checkin-content:quality=unavailable"))
        assertTrue(result.pipelineNotes.contains("checkin-content:reason=no-recognition-backend"))
        assertEquals("not-ready", result.metadata.customTags["checkInContentSkipped"])
        assertEquals("unavailable", result.metadata.customTags["checkInContentSkippedQuality"])
        assertEquals("no-recognition-backend", result.metadata.customTags["checkInContentSkippedReason"])
    }

    @Test
    fun `missing content records check in content skip reason`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val input = photoResult(
            mode = "check-in",
            checkInScenario = "clarity",
            contentUnderstanding = null
        )

        val result = processor.process(input)

        assertFalse(result.metadata.customTags.containsKey("checkInContentScenario"))
        assertTrue(result.pipelineNotes.contains("checkin-content:skipped:missing-content"))
        assertEquals("missing-content", result.metadata.customTags["checkInContentSkipped"])
    }

    @Test
    fun `content without check in scenario records skip reason`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val input = photoResult(
            mode = "check-in",
            checkInScenario = "clarity",
            contentUnderstanding = ContentUnderstandingSnapshot.fromTags(
                snapshotId = "content-scene-only",
                timestampMillis = 42L,
                backendId = "fake-content",
                quality = SceneMaskQuality.SAVED_PHOTO,
                tags = listOf(
                    ContentTagDescriptor(
                        tagId = "scene-sky",
                        label = "Sky",
                        family = ContentTagFamily.SCENE,
                        confidence = 0.9f,
                        backendId = "fake-content"
                    )
                )
            )
        )

        val result = processor.process(input)

        assertFalse(result.metadata.customTags.containsKey("checkInContentScenario"))
        assertTrue(result.pipelineNotes.contains("checkin-content:skipped:no-scenario"))
        assertTrue(result.pipelineNotes.contains("checkin-content:subject=unsupported"))
        assertTrue(result.pipelineNotes.contains("checkin-content:object-tags=unsupported"))
        assertEquals("no-scenario", result.metadata.customTags["checkInContentSkipped"])
        assertEquals("unsupported", result.metadata.customTags["checkInContentSkippedSubjectRegions"])
        assertEquals("unsupported", result.metadata.customTags["checkInContentSkippedObjectTags"])
    }

    @Test
    fun `low confidence check in scenario records candidate reason`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val input = photoResult(
            mode = "check-in",
            checkInScenario = "clarity",
            contentUnderstanding = ContentUnderstandingSnapshot(
                snapshotId = "content-low-face",
                timestampMillis = 42L,
                quality = SceneMaskQuality.SAVED_PHOTO,
                backendId = "fake-content",
                regions = listOf(
                    ContentRegionDescriptor(
                        regionId = "face-0",
                        role = ContentRegionRole.FACE,
                        quality = SceneMaskQuality.SAVED_PHOTO,
                        backendId = "fake-content",
                        confidence = 0.62f,
                        transform = transform()
                    )
                )
            )
        )

        val result = processor.process(input)

        assertFalse(result.metadata.customTags.containsKey("checkInContentScenario"))
        assertTrue(result.pipelineNotes.contains("checkin-content:skipped:low-confidence-scenario"))
        assertTrue(result.pipelineNotes.contains("checkin-content:source=region:face"))
        assertTrue(result.pipelineNotes.contains("checkin-content:confidence=0.62"))
        assertEquals("low-confidence-scenario", result.metadata.customTags["checkInContentSkipped"])
        assertEquals("people-place", result.metadata.customTags["checkInContentSkippedScenario"])
        assertEquals("region:face", result.metadata.customTags["checkInContentSkippedSource"])
        assertEquals("0.62", result.metadata.customTags["checkInContentSkippedConfidence"])
    }

    @Test
    fun `non check in photo is left unchanged`() = runTest {
        val processor = CheckInContentDecisionPostProcessor()
        val input = photoResult(
            mode = "photo",
            checkInScenario = null,
            contentUnderstanding = ContentUnderstandingSnapshot.fromTags(
                snapshotId = "content-object",
                timestampMillis = 42L,
                backendId = "fake-content",
                quality = SceneMaskQuality.SAVED_PHOTO,
                tags = listOf(
                    ContentTagDescriptor(
                        tagId = "object-coffee-cup",
                        label = "Coffee cup",
                        family = ContentTagFamily.OBJECT,
                        confidence = 0.87f,
                        backendId = "fake-content"
                    )
                )
            )
        )

        val result = processor.process(input)

        assertEquals(input, result)
        assertFalse(result.metadata.customTags.containsKey("checkInContentScenario"))
    }

    private fun photoResult(
        mode: String,
        checkInScenario: String?,
        contentUnderstanding: ContentUnderstandingSnapshot?
    ): ShotResult {
        val metadata = MediaMetadata(
            customTags = buildMap {
                put("mode", mode)
                checkInScenario?.let { put("checkInScenario", it) }
            }
        )
        return ShotResult(
            shotId = "shot-checkin-content",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/checkin-content.jpg",
            outputHandle = MediaOutputHandle(
                displayPath = "/tmp/checkin-content.jpg",
                filePath = "/tmp/checkin-content.jpg"
            ),
            saveRequest = SaveRequest.photoLibrary(metadata = metadata),
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = "/tmp/checkin-content.jpg",
                renderUri = null
            ),
            metadata = metadata,
            contentUnderstanding = contentUnderstanding
        )
    }

    private fun transform(): SceneMaskTransform = SceneMaskTransform(
        sourceWidth = 4000,
        sourceHeight = 3000,
        maskWidth = 4000,
        maskHeight = 3000,
        rotationDegrees = 0
    )
}
