package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ContentTagDescriptor
import com.opencamera.core.media.ContentTagFamily
import com.opencamera.core.media.ContentUnderstandingFamily
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskSupport
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedPhotoContentAnalyzerTest {

    @Test
    fun `composite analyzer merges subject mask labels and degraded backends`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(32)
        Mockito.`when`(bitmap.height).thenReturn(24)
        val subjectMask = SceneMaskTestUtils.createUniformMask(8, 6, 0.8f)
        val analyzer = CompositeSavedPhotoContentAnalyzer(
            backends = listOf(
                SceneMaskSavedPhotoContentAnalyzer(
                    provider = object : SavedPhotoSceneMaskProvider {
                        override suspend fun createSubjectMask(
                            bitmap: Bitmap,
                            request: SavedPhotoSceneMaskRequest
                        ): SceneMaskResult = SceneMaskResult.Available(subjectMask)
                    }
                ),
                FakeSavedPhotoContentBackend(
                    ContentUnderstandingSnapshot.fromTags(
                        snapshotId = "labels",
                        timestampMillis = 10L,
                        backendId = "fake-labels",
                        quality = SceneMaskQuality.SAVED_PHOTO,
                        tags = listOf(
                            ContentTagDescriptor(
                                tagId = "scene-city",
                                label = "City",
                                family = ContentTagFamily.SCENE,
                                confidence = 0.86f,
                                backendId = "fake-labels"
                            )
                        )
                    )
                ),
                FakeSavedPhotoContentBackend(
                    ContentUnderstandingSnapshot.unavailable(
                        timestampMillis = 10L,
                        backendId = "fake-objects",
                        reason = "object-model-loading"
                    )
                )
            )
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest(
                shotId = "shot-1",
                outputHandleTag = "output-1",
                timestampMillis = 10L
            )
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(SceneMaskQuality.DEGRADED, snapshot.quality)
        assertTrue(snapshot.hasTagFamily(ContentTagFamily.SCENE))
        assertTrue(snapshot.hasRegion(com.opencamera.core.media.ContentRegionRole.PERSON_SUBJECT))
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=object-model-loading"))
    }

    @Test
    fun `composite analyzer marks subject family degraded when subject mask backend fails`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(32)
        Mockito.`when`(bitmap.height).thenReturn(24)
        val analyzer = CompositeSavedPhotoContentAnalyzer(
            backends = listOf(
                SceneMaskSavedPhotoContentAnalyzer(
                    provider = object : SavedPhotoSceneMaskProvider {
                        override suspend fun createSubjectMask(
                            bitmap: Bitmap,
                            request: SavedPhotoSceneMaskRequest
                        ): SceneMaskResult = SceneMaskResult.Failed("segmentation-exception")
                    }
                ),
                FakeSavedPhotoContentBackend(
                    ContentUnderstandingSnapshot.fromTags(
                        snapshotId = "labels",
                        timestampMillis = 11L,
                        backendId = "fake-labels",
                        quality = SceneMaskQuality.SAVED_PHOTO,
                        tags = listOf(
                            ContentTagDescriptor(
                                tagId = "scene-sky",
                                label = "Sky",
                                family = ContentTagFamily.SCENE,
                                confidence = 0.86f,
                                backendId = "fake-labels"
                            )
                        )
                    )
                )
            )
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest(
                shotId = "shot-subject-failed",
                outputHandleTag = "output-subject-failed",
                timestampMillis = 11L
            )
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(SceneMaskQuality.DEGRADED, snapshot.quality)
        assertTrue(snapshot.diagnostics.contains("content-understanding:subject=degraded"))
        assertEquals(SceneMaskSupport.DEGRADED, snapshot.capabilitySummary().subjectRegions)
    }

    @Test
    fun `composite analyzer marks backend families degraded when a backend throws`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(32)
        Mockito.`when`(bitmap.height).thenReturn(24)
        val analyzer = CompositeSavedPhotoContentAnalyzer(
            backends = listOf(
                SceneMaskSavedPhotoContentAnalyzer(
                    provider = object : SavedPhotoSceneMaskProvider {
                        override suspend fun createSubjectMask(
                            bitmap: Bitmap,
                            request: SavedPhotoSceneMaskRequest
                        ): SceneMaskResult = error("provider-crash")
                    }
                ),
                FakeSavedPhotoContentBackend(
                    ContentUnderstandingSnapshot.fromTags(
                        snapshotId = "labels",
                        timestampMillis = 12L,
                        backendId = "fake-labels",
                        quality = SceneMaskQuality.SAVED_PHOTO,
                        tags = listOf(
                            ContentTagDescriptor(
                                tagId = "scene-food",
                                label = "Food",
                                family = ContentTagFamily.SCENE,
                                confidence = 0.88f,
                                backendId = "fake-labels"
                            )
                        )
                    )
                )
            )
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest(
                shotId = "shot-backend-throws",
                outputHandleTag = "output-backend-throws",
                timestampMillis = 12L
            )
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(SceneMaskQuality.DEGRADED, snapshot.quality)
        assertTrue(snapshot.diagnostics.contains("content-understanding:subject=degraded"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=backend-exception:IllegalStateException"))
        assertEquals(SceneMaskSupport.DEGRADED, snapshot.capabilitySummary().subjectRegions)
    }

    @Test
    fun `composite analyzer reports stable backend id when a backend throws`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(32)
        Mockito.`when`(bitmap.height).thenReturn(24)
        val analyzer = CompositeSavedPhotoContentAnalyzer(
            backends = listOf(
                object : SavedPhotoContentBackend {
                    override val backendId: String = "fake-crashing-content"
                    override val contentFamilies: Set<ContentUnderstandingFamily> =
                        setOf(ContentUnderstandingFamily.OBJECT_TAGS)

                    override suspend fun analyze(
                        bitmap: Bitmap,
                        request: SavedPhotoContentAnalysisRequest
                    ): ContentUnderstandingSnapshot = error("object-model-crash")
                }
            )
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest(
                shotId = "shot-stable-backend-id",
                outputHandleTag = "output-stable-backend-id",
                timestampMillis = 13L
            )
        )

        assertTrue(snapshot.diagnostics.contains("content-understanding:backend=fake-crashing-content"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:object-tags=degraded"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=backend-exception:IllegalStateException"))
    }

    private class FakeSavedPhotoContentBackend(
        private val snapshot: ContentUnderstandingSnapshot
    ) : SavedPhotoContentBackend {
        override suspend fun analyze(
            bitmap: Bitmap,
            request: SavedPhotoContentAnalysisRequest
        ): ContentUnderstandingSnapshot = snapshot
    }
}
