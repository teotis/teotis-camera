package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ContentUnderstandingFamily
import com.opencamera.core.media.ContentUnderstandingFamilyKeys
import com.opencamera.core.media.SceneMaskSupport
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MlKitSavedPhotoContentAnalyzerTest {

    @Test
    fun `ml kit backend declares shared content family keys`() {
        val analyzer = MlKitSavedPhotoContentAnalyzer(
            imageLabelClient = object : MlKitImageLabelClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitImageLabel> = emptyList()
            },
            objectClient = object : MlKitObjectDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitDetectedObject> = emptyList()
            },
            faceClient = object : MlKitFaceDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitFace> = emptyList()
            }
        )

        assertEquals(
            setOf(
                ContentUnderstandingFamily.SCENE_TAGS,
                ContentUnderstandingFamily.OBJECT_TAGS,
                ContentUnderstandingFamily.FACE_LANDMARKS
            ),
            analyzer.contentFamilies
        )
        assertEquals(
            setOf(
                ContentUnderstandingFamilyKeys.SCENE_TAGS,
                ContentUnderstandingFamilyKeys.OBJECT_TAGS,
                ContentUnderstandingFamilyKeys.FACE_LANDMARKS
            ),
            analyzer.contentFamilyKeys
        )
    }

    @Test
    fun `analyzer combines image labels objects and faces from injected clients`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = MlKitSavedPhotoContentAnalyzer(
            imageLabelClient = object : MlKitImageLabelClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitImageLabel> =
                    listOf(RawMlKitImageLabel("Sky", 0.9f, 1))
            },
            objectClient = object : MlKitObjectDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitDetectedObject> =
                    listOf(
                        RawMlKitDetectedObject(
                            objectIndex = 0,
                            bounds = RawMlKitBounds(100, 100, 600, 900),
                            labels = listOf(RawMlKitObjectLabel("Plant", 0.7f, 2))
                        )
                    )
            },
            faceClient = object : MlKitFaceDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitFace> =
                    listOf(RawMlKitFace(0, RawMlKitBounds(800, 200, 1400, 1000), 0.4f, null, null))
            }
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest("shot-30", "output-30", 30L)
        )

        assertTrue(snapshot.isAvailable)
        assertTrue(snapshot.hasTagFamily(com.opencamera.core.media.ContentTagFamily.SCENE))
        assertTrue(snapshot.hasRegion(com.opencamera.core.media.ContentRegionRole.OBJECT))
        assertTrue(snapshot.hasRegion(com.opencamera.core.media.ContentRegionRole.FACE))
    }

    @Test
    fun `analyzer degrades but keeps partial results when one client fails`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = MlKitSavedPhotoContentAnalyzer(
            imageLabelClient = object : MlKitImageLabelClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitImageLabel> =
                    listOf(RawMlKitImageLabel("Food", 0.8f, 3))
            },
            objectClient = object : MlKitObjectDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitDetectedObject> =
                    error("model-loading")
            },
            faceClient = object : MlKitFaceDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitFace> = emptyList()
            }
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest("shot-31", "output-31", 31L)
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(com.opencamera.core.media.SceneMaskQuality.DEGRADED, snapshot.quality)
        assertTrue(snapshot.diagnostics.contains("mlkit-content:object-tags=failed:IllegalStateException"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:object-tags=degraded"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=mlkit-content-partial-failure:object-tags"))
        assertEquals(SceneMaskSupport.DEGRADED, snapshot.capabilitySummary().objectTags)
        assertEquals(
            "mlkit-content-partial-failure:object-tags",
            snapshot.capabilitySummary().reason
        )
    }

    @Test
    fun `analyzer reports face family degradation with capability key when face client fails`() = runTest {
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(4000)
        Mockito.`when`(bitmap.height).thenReturn(3000)
        val analyzer = MlKitSavedPhotoContentAnalyzer(
            imageLabelClient = object : MlKitImageLabelClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitImageLabel> =
                    listOf(RawMlKitImageLabel("Sky", 0.86f, 2))
            },
            objectClient = object : MlKitObjectDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitDetectedObject> = emptyList()
            },
            faceClient = object : MlKitFaceDetectionClient {
                override suspend fun detect(bitmap: Bitmap): List<RawMlKitFace> =
                    error("face-model-loading")
            }
        )

        val snapshot = analyzer.analyze(
            bitmap = bitmap,
            request = SavedPhotoContentAnalysisRequest("shot-32", "output-32", 32L)
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(com.opencamera.core.media.SceneMaskQuality.DEGRADED, snapshot.quality)
        assertTrue(snapshot.diagnostics.contains("content-understanding:face-landmarks=degraded"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=mlkit-content-partial-failure:face-landmarks"))
        assertEquals(SceneMaskSupport.DEGRADED, snapshot.capabilitySummary().faceLandmarks)
    }
}
