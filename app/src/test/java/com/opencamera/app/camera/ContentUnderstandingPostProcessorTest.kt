package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ContentRegionBounds
import com.opencamera.core.media.ContentRegionDescriptor
import com.opencamera.core.media.ContentRegionRole
import com.opencamera.core.media.ContentTagDescriptor
import com.opencamera.core.media.ContentTagFamily
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskTransform
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentUnderstandingPostProcessorTest {

    @Test
    fun `saved photo content snapshot is attached to shot result`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = FakeSavedPhotoContentAnalyzer(
            ContentUnderstandingSnapshot(
                snapshotId = "content-shot-42",
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
                        transform = transform()
                    ),
                    ContentRegionDescriptor(
                        regionId = "person-0",
                        role = ContentRegionRole.PERSON_SUBJECT,
                        quality = SceneMaskQuality.SAVED_PHOTO,
                        backendId = "fake-content",
                        confidence = 0.82f,
                        transform = transform()
                    )
                ),
                tags = listOf(
                    ContentTagDescriptor(
                        tagId = "scene-food",
                        label = "Food",
                        family = ContentTagFamily.SCENE,
                        confidence = 0.91f,
                        backendId = "fake-content"
                    ),
                    ContentTagDescriptor(
                        tagId = "scene-sky",
                        label = "Sky",
                        family = ContentTagFamily.SCENE,
                        confidence = 0.86f,
                        backendId = "fake-content"
                    ),
                    ContentTagDescriptor(
                        tagId = "object-coffee-cup",
                        label = "Coffee cup",
                        family = ContentTagFamily.OBJECT,
                        confidence = 0.88f,
                        backendId = "fake-content"
                    )
                ),
                diagnostics = listOf("fake-content:scene-tags=1")
            )
        )
        val processor = ContentUnderstandingPostProcessor(
            analyzer = analyzer,
            bitmapSource = FakeSavedPhotoBitmapSource(bitmap),
            elapsedRealtimeMillis = { 42L }
        )

        val result = processor.process(photoResult())

        assertEquals(listOf("shot-42"), analyzer.requests.map { it.shotId })
        assertNotNull(result.contentUnderstanding)
        assertEquals("content-shot-42", result.contentUnderstanding?.snapshotId)
        assertTrue(result.contentUnderstanding?.hasTagFamily(ContentTagFamily.SCENE) == true)
        assertTrue(result.pipelineNotes.contains("content-understanding:available"))
        assertTrue(result.pipelineNotes.contains("content-understanding:quality=saved_photo"))
        assertTrue(result.pipelineNotes.contains("content-understanding:subject=applied"))
        assertTrue(result.pipelineNotes.contains("content-understanding:semantic-regions=unsupported"))
        assertTrue(result.pipelineNotes.contains("content-understanding:face-landmarks=applied"))
        assertTrue(result.pipelineNotes.contains("content-understanding:object-tags=applied"))
        assertTrue(result.pipelineNotes.contains("content-understanding:scene-tags=applied"))
        assertTrue(result.pipelineNotes.contains("content-understanding:primary-scene=food"))
        assertTrue(result.pipelineNotes.contains("content-understanding:primary-people=face"))
        assertTrue(result.pipelineNotes.contains("content-understanding:primary-object=object"))
        assertTrue(result.pipelineNotes.contains("content-understanding:primary-object-source=tag:object-coffee-cup"))
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-ready=true"))
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-style-scene=food"))
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-portrait-subject=face"))
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-checkin-scenario=people-place"))
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-checkin-source=region:face"))
        assertTrue(result.pipelineNotes.contains("fake-content:scene-tags=1"))
        assertEquals("saved_photo", result.metadata.customTags["contentQuality"])
        assertEquals("fake-content", result.metadata.customTags["contentBackend"])
        assertEquals("applied", result.metadata.customTags["contentSubjectRegions"])
        assertEquals("unsupported", result.metadata.customTags["contentSemanticRegions"])
        assertEquals("applied", result.metadata.customTags["contentFaceLandmarks"])
        assertEquals("applied", result.metadata.customTags["contentObjectTags"])
        assertEquals("applied", result.metadata.customTags["contentSceneTags"])
        assertEquals("food", result.metadata.customTags["contentPrimaryScene"])
        assertEquals("tag:scene-food", result.metadata.customTags["contentPrimarySceneSource"])
        assertEquals("0.91", result.metadata.customTags["contentPrimarySceneConfidence"])
        assertEquals("face", result.metadata.customTags["contentPrimaryPeople"])
        assertEquals("region:face", result.metadata.customTags["contentPrimaryPeopleSource"])
        assertEquals("object", result.metadata.customTags["contentPrimaryObject"])
        assertEquals("tag:object-coffee-cup", result.metadata.customTags["contentPrimaryObjectSource"])
        assertEquals("true", result.metadata.customTags["contentAdaptationReady"])
        assertEquals(
            "food@tag:scene-food:0.91,sky-water@tag:scene-sky:0.86",
            result.metadata.customTags["contentAdaptationSceneCandidates"]
        )
        assertEquals(
            "face@region:face:0.93,person-subject@region:person-subject:0.82",
            result.metadata.customTags["contentAdaptationPortraitSubjectCandidates"]
        )
        assertEquals(
            "people-place@region:face:0.93,object-place@tag:object-coffee-cup:0.88",
            result.metadata.customTags["contentAdaptationCheckInCandidates"]
        )
    }

    @Test
    fun `semantic region candidates are persisted into notes and metadata`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = FakeSavedPhotoContentAnalyzer(
            ContentUnderstandingSnapshot(
                snapshotId = "content-semantic",
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
                        transform = transform(),
                        bounds = ContentRegionBounds(
                            left = 0.10f,
                            top = 0.20f,
                            right = 0.60f,
                            bottom = 0.80f
                        )
                    ),
                    ContentRegionDescriptor(
                        regionId = "food-0",
                        role = ContentRegionRole.FOOD,
                        quality = SceneMaskQuality.SAVED_PHOTO,
                        backendId = "fake-content",
                        confidence = 0.86f,
                        transform = transform(),
                        bounds = ContentRegionBounds(
                            left = 0.20f,
                            top = 0.15f,
                            right = 0.50f,
                            bottom = 0.70f
                        )
                    )
                )
            )
        )
        val processor = ContentUnderstandingPostProcessor(
            analyzer = analyzer,
            bitmapSource = FakeSavedPhotoBitmapSource(bitmap),
            elapsedRealtimeMillis = { 42L }
        )

        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.contains("content-understanding:semantic-regions=applied"))
        assertTrue(
            result.pipelineNotes.contains(
                "content-understanding:semantic-region-candidates=document@region:document-0:0.92,food@region:food-0:0.86"
            )
        )
        assertEquals("applied", result.metadata.customTags["contentSemanticRegions"])
        assertEquals(
            "document@region:document-0:0.92,food@region:food-0:0.86",
            result.metadata.customTags["contentSemanticRegionCandidates"]
        )
        assertTrue(
            result.pipelineNotes.contains(
                "content-understanding:semantic-region-bounds=region:document-0=0.10,0.20,0.60,0.80;region:food-0=0.20,0.15,0.50,0.70"
            )
        )
        assertEquals(
            "region:document-0=0.10,0.20,0.60,0.80;region:food-0=0.20,0.15,0.50,0.70",
            result.metadata.customTags["contentSemanticRegionBounds"]
        )
        assertTrue(
            result.pipelineNotes.contains(
                "content-understanding:placement-avoidance=" +
                    "document@region:document-0:0.92=0.10,0.20,0.60,0.80;" +
                    "food@region:food-0:0.86=0.20,0.15,0.50,0.70"
            )
        )
        assertEquals(
            "document@region:document-0:0.92=0.10,0.20,0.60,0.80;" +
                "food@region:food-0:0.86=0.20,0.15,0.50,0.70",
            result.metadata.customTags["contentPlacementAvoidance"]
        )
    }

    @Test
    fun `available snapshot does not leak unavailable diagnostics into pipeline notes`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = FakeSavedPhotoContentAnalyzer(
            ContentUnderstandingSnapshot(
                snapshotId = "content-shot-42",
                timestampMillis = 42L,
                quality = SceneMaskQuality.DEGRADED,
                backendId = "fake-content",
                regions = emptyList(),
                tags = listOf(
                    ContentTagDescriptor(
                        tagId = "object-hand",
                        label = "Hand",
                        family = ContentTagFamily.OBJECT,
                        confidence = 0.91f,
                        backendId = "fake-content"
                    )
                ),
                diagnostics = listOf(
                    "content-understanding:unavailable",
                    "content-understanding:reason=no-person-detected",
                    "mlkit-content:objects=1"
                )
            )
        )
        val processor = ContentUnderstandingPostProcessor(
            analyzer = analyzer,
            bitmapSource = FakeSavedPhotoBitmapSource(bitmap),
            elapsedRealtimeMillis = { 42L }
        )

        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.contains("content-understanding:available"))
        assertFalse(result.pipelineNotes.contains("content-understanding:unavailable"))
        assertEquals(
            1,
            result.pipelineNotes.count { it == "content-understanding:reason=no-person-detected" }
        )
        assertTrue(result.pipelineNotes.contains("mlkit-content:objects=1"))
    }

    @Test
    fun `missing bitmap records diagnostic without replacing original result`() = runTest {
        val analyzer = FakeSavedPhotoContentAnalyzer(
            ContentUnderstandingSnapshot.unavailable(
                timestampMillis = 42L,
                backendId = "fake-content",
                reason = "should-not-run"
            )
        )
        val processor = ContentUnderstandingPostProcessor(
            analyzer = analyzer,
            bitmapSource = FakeSavedPhotoBitmapSource(null),
            elapsedRealtimeMillis = { 42L }
        )

        val input = photoResult()
        val result = processor.process(input)

        assertEquals(emptyList(), analyzer.requests)
        assertEquals(null, result.contentUnderstanding)
        assertTrue(result.pipelineNotes.contains("content-understanding:skipped:bitmap-unavailable"))
    }

    @Test
    fun `unavailable content records stable adaptation not ready metadata`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = FakeSavedPhotoContentAnalyzer(
            ContentUnderstandingSnapshot.unavailable(
                timestampMillis = 42L,
                backendId = "fake-content",
                reason = "no-recognition-backend"
            )
        )
        val processor = ContentUnderstandingPostProcessor(
            analyzer = analyzer,
            bitmapSource = FakeSavedPhotoBitmapSource(bitmap),
            elapsedRealtimeMillis = { 42L }
        )

        val result = processor.process(photoResult())

        assertEquals("false", result.metadata.customTags["contentAdaptationReady"])
        assertEquals("unavailable", result.metadata.customTags["contentAdaptationNotReadyQuality"])
        assertEquals("no-recognition-backend", result.metadata.customTags["contentAdaptationNotReadyReason"])
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-not-ready-quality=unavailable"))
        assertTrue(result.pipelineNotes.contains("content-understanding:adaptation-not-ready-reason=no-recognition-backend"))
    }

    private class FakeSavedPhotoContentAnalyzer(
        private val snapshot: ContentUnderstandingSnapshot
    ) : SavedPhotoContentAnalyzer {
        val requests = mutableListOf<SavedPhotoContentAnalysisRequest>()

        override suspend fun analyze(
            bitmap: Bitmap,
            request: SavedPhotoContentAnalysisRequest
        ): ContentUnderstandingSnapshot {
            requests += request
            return snapshot
        }
    }

    private class FakeSavedPhotoBitmapSource(
        private val bitmap: Bitmap?
    ) : SavedPhotoBitmapSource {
        override suspend fun decode(target: ProcessorTarget): Bitmap? {
            assertEquals(ProcessorTarget.FilePath("/tmp/content-aware.jpg"), target)
            return bitmap
        }
    }

    private fun transform(): SceneMaskTransform = SceneMaskTransform(
        sourceWidth = 4000,
        sourceHeight = 3000,
        maskWidth = 4000,
        maskHeight = 3000,
        rotationDegrees = 0
    )

    private fun photoResult(): ShotResult = ShotResult(
        shotId = "shot-42",
        mediaType = MediaType.PHOTO,
        outputPath = "/tmp/content-aware.jpg",
        outputHandle = MediaOutputHandle(
            displayPath = "/tmp/content-aware.jpg",
            filePath = "/tmp/content-aware.jpg"
        ),
        saveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata()
        ),
        thumbnailSource = ThumbnailSource.SavedMedia("/tmp/content-aware.jpg"),
        metadata = MediaMetadata()
    )
}
