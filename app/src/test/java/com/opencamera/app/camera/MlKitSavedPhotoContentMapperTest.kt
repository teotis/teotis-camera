package com.opencamera.app.camera

import com.opencamera.core.media.ContentCheckInScenarioHint
import com.opencamera.core.media.ContentRegionRole
import com.opencamera.core.media.ContentSceneHint
import com.opencamera.core.media.ContentSubjectHint
import com.opencamera.core.media.ContentTagFamily
import com.opencamera.core.media.ContentUnderstandingPipelineNotes
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MlKitSavedPhotoContentMapperTest {

    @Test
    fun `mapper converts labels objects and faces into one saved photo snapshot`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "shot-20",
                outputHandleTag = "output-20",
                timestampMillis = 20L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = listOf(
                RawMlKitImageLabel(label = "Sky", confidence = 0.91f, index = 10),
                RawMlKitImageLabel(label = "City", confidence = 0.84f, index = 12)
            ),
            objects = listOf(
                RawMlKitDetectedObject(
                    objectIndex = 0,
                    bounds = RawMlKitBounds(left = 400, top = 600, right = 2200, bottom = 2100),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Food", confidence = 0.77f, index = 4)
                    )
                )
            ),
            faces = listOf(
                RawMlKitFace(
                    faceIndex = 0,
                    bounds = RawMlKitBounds(left = 1000, top = 300, right = 1800, bottom = 1500),
                    smilingProbability = 0.62f,
                    leftEyeOpenProbability = 0.88f,
                    rightEyeOpenProbability = 0.86f
                )
            )
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(SceneMaskQuality.SAVED_PHOTO, snapshot.quality)
        assertTrue(snapshot.hasTagFamily(ContentTagFamily.SCENE))
        assertTrue(snapshot.hasTagFamily(ContentTagFamily.OBJECT))
        assertTrue(snapshot.hasTagFamily(ContentTagFamily.FACE_ATTRIBUTE))

        val objectRegion = snapshot.primaryRegion(ContentRegionRole.OBJECT)
        assertNotNull(objectRegion)
        assertEquals(0.10f, objectRegion.bounds?.left ?: 0f, 0.001f)
        assertEquals(0.70f, objectRegion.bounds?.bottom ?: 0f, 0.001f)

        val faceRegion = snapshot.primaryRegion(ContentRegionRole.FACE)
        assertNotNull(faceRegion)
        assertEquals(0.25f, faceRegion.bounds?.left ?: 0f, 0.001f)
        assertEquals(0.50f, faceRegion.bounds?.bottom ?: 0f, 0.001f)
        assertTrue(snapshot.diagnostics.contains("mlkit-content:image-labels=2"))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:objects=1"))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:faces=1"))
    }

    @Test
    fun `mapper reports unavailable when all recognition families are empty`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "empty",
                outputHandleTag = "output-empty",
                timestampMillis = 30L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = emptyList(),
            faces = emptyList()
        )

        assertEquals(SceneMaskQuality.UNAVAILABLE, snapshot.quality)
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=mlkit-content-empty"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:scene-tags=applied"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:object-tags=applied"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:face-landmarks=applied"))
        assertEquals(
            MlKitSavedPhotoContentFamilies.ALL,
            snapshot.diagnostics
                .mapNotNull { ContentUnderstandingPipelineNotes.parseFamilySupport(it) }
                .map { it.family }
        )
        assertEquals(SceneMaskSupport.SUPPORTED, snapshot.capabilitySummary().sceneTags)
        assertEquals(SceneMaskSupport.SUPPORTED, snapshot.capabilitySummary().objectTags)
        assertEquals(SceneMaskSupport.SUPPORTED, snapshot.capabilitySummary().faceLandmarks)
    }

    @Test
    fun `face detection without attribute probabilities still drives subject decisions`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "face-only",
                outputHandleTag = "output-face-only",
                timestampMillis = 40L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = emptyList(),
            faces = listOf(
                RawMlKitFace(
                    faceIndex = 0,
                    bounds = RawMlKitBounds(left = 1000, top = 300, right = 1800, bottom = 1500),
                    smilingProbability = null,
                    leftEyeOpenProbability = null,
                    rightEyeOpenProbability = null
                )
            )
        )

        val faceRegion = snapshot.primaryRegion(ContentRegionRole.FACE)
        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertNotNull(faceRegion)
        assertTrue(faceRegion.confidence >= 0.7f)
        assertEquals(ContentSubjectHint.FACE, decisions.portraitSubject?.hint)
        assertEquals(ContentCheckInScenarioHint.PEOPLE_PLACE, decisions.checkInScenario?.scenario)
        assertTrue(snapshot.diagnostics.contains("mlkit-content:faces=1"))
    }

    @Test
    fun `object detection without labels still drives object place decisions`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "object-only",
                outputHandleTag = "output-object-only",
                timestampMillis = 41L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = listOf(
                RawMlKitDetectedObject(
                    objectIndex = 0,
                    bounds = RawMlKitBounds(left = 600, top = 700, right = 2200, bottom = 2300),
                    labels = emptyList()
                )
            ),
            faces = emptyList()
        )

        val objectRegion = snapshot.primaryRegion(ContentRegionRole.OBJECT)
        val decisions = snapshot.savedPhotoAdaptationDecisions()
        val objectSubject = snapshot.primarySubjectHint(ContentSubjectHint.OBJECT)

        assertNotNull(objectRegion)
        assertTrue(objectRegion.confidence >= 0.7f)
        assertEquals(ContentSubjectHint.OBJECT, objectSubject?.hint)
        assertEquals(ContentCheckInScenarioHint.OBJECT_PLACE, decisions.checkInScenario?.scenario)
        assertTrue(snapshot.diagnostics.contains("mlkit-content:objects=1"))
    }

    @Test
    fun `object labels can drive saved photo style scene decisions`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "object-food",
                outputHandleTag = "output-object-food",
                timestampMillis = 42L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = listOf(
                RawMlKitDetectedObject(
                    objectIndex = 0,
                    bounds = RawMlKitBounds(left = 600, top = 700, right = 2200, bottom = 2300),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Food", confidence = 0.88f, index = 4)
                    )
                )
            ),
            faces = emptyList()
        )

        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertTrue(snapshot.hasTagFamily(ContentTagFamily.OBJECT))
        assertEquals(ContentSceneHint.FOOD, decisions.styleScene?.hint)
        assertEquals(0.88f, decisions.styleScene?.confidence)
    }

    @Test
    fun `object labels project bounded semantic regions for downstream consumers`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "object-semantics",
                outputHandleTag = "output-object-semantics",
                timestampMillis = 44L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = listOf(
                RawMlKitDetectedObject(
                    objectIndex = 0,
                    bounds = RawMlKitBounds(left = 400, top = 300, right = 2400, bottom = 2100),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Food", confidence = 0.88f, index = 0),
                        RawMlKitObjectLabel(label = "Tableware", confidence = 0.62f, index = 1)
                    )
                ),
                RawMlKitDetectedObject(
                    objectIndex = 1,
                    bounds = RawMlKitBounds(left = 1200, top = 600, right = 3000, bottom = 2400),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Receipt document", confidence = 0.84f, index = 0)
                    )
                )
            ),
            faces = emptyList()
        )

        val foodRegion = snapshot.primaryRegion(ContentRegionRole.FOOD)
        val documentRegion = snapshot.primaryRegion(ContentRegionRole.DOCUMENT)

        assertNotNull(foodRegion)
        assertEquals(0.10f, foodRegion.bounds?.left ?: 0f, 0.001f)
        assertEquals(0.70f, foodRegion.bounds?.bottom ?: 0f, 0.001f)
        assertEquals(0.88f, foodRegion.confidence)
        assertNotNull(documentRegion)
        assertEquals(0.84f, documentRegion.confidence)
        assertEquals(SceneMaskSupport.SUPPORTED, snapshot.capabilitySummary().semanticRegions)
        assertTrue(snapshot.diagnostics.contains("mlkit-content:semantic-regions=2"))
    }

    @Test
    fun `object labels ignore semantic tokens embedded inside unrelated words`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "object-token-fragments",
                outputHandleTag = "output-object-token-fragments",
                timestampMillis = 45L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = listOf(
                RawMlKitDetectedObject(
                    objectIndex = 0,
                    bounds = RawMlKitBounds(left = 400, top = 300, right = 1600, bottom = 1500),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Watermark sticker", confidence = 0.91f, index = 0)
                    )
                ),
                RawMlKitDetectedObject(
                    objectIndex = 1,
                    bounds = RawMlKitBounds(left = 600, top = 500, right = 2000, bottom = 1900),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Documentary camera", confidence = 0.89f, index = 0)
                    )
                )
            ),
            faces = emptyList()
        )

        assertNull(snapshot.primaryRegion(ContentRegionRole.WATER))
        assertNull(snapshot.primaryRegion(ContentRegionRole.DOCUMENT))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:semantic-regions=0"))
    }

    @Test
    fun `object labels match common plural semantic aliases`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "object-plural-semantics",
                outputHandleTag = "output-object-plural-semantics",
                timestampMillis = 46L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = emptyList(),
            objects = listOf(
                RawMlKitDetectedObject(
                    objectIndex = 0,
                    bounds = RawMlKitBounds(left = 100, top = 100, right = 1200, bottom = 1300),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "City buildings", confidence = 0.90f, index = 0)
                    )
                ),
                RawMlKitDetectedObject(
                    objectIndex = 1,
                    bounds = RawMlKitBounds(left = 500, top = 300, right = 1800, bottom = 1800),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Trees", confidence = 0.88f, index = 0)
                    )
                ),
                RawMlKitDetectedObject(
                    objectIndex = 2,
                    bounds = RawMlKitBounds(left = 800, top = 600, right = 2000, bottom = 2200),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Documents", confidence = 0.86f, index = 0)
                    )
                )
            ),
            faces = emptyList()
        )

        assertNotNull(snapshot.primaryRegion(ContentRegionRole.BUILDING))
        assertNotNull(snapshot.primaryRegion(ContentRegionRole.VEGETATION))
        assertNotNull(snapshot.primaryRegion(ContentRegionRole.DOCUMENT))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:semantic-regions=3"))
    }

    @Test
    fun `mapper applies stable budgets to noisy recognition candidates`() {
        val snapshot = MlKitSavedPhotoContentMapper.map(
            request = SavedPhotoContentAnalysisRequest(
                shotId = "noisy",
                outputHandleTag = "output-noisy",
                timestampMillis = 43L
            ),
            sourceWidth = 4000,
            sourceHeight = 3000,
            imageLabels = (0 until 8).map { index ->
                RawMlKitImageLabel(
                    label = "Scene $index",
                    confidence = 0.50f + index * 0.05f,
                    index = index
                )
            },
            objects = (0 until 7).map { index ->
                RawMlKitDetectedObject(
                    objectIndex = index,
                    bounds = RawMlKitBounds(
                        left = 100 + index,
                        top = 200 + index,
                        right = 1000 + index,
                        bottom = 1600 + index
                    ),
                    labels = listOf(
                        RawMlKitObjectLabel(label = "Object $index primary", confidence = 0.55f + index * 0.04f, index = 0),
                        RawMlKitObjectLabel(label = "Object $index secondary", confidence = 0.54f + index * 0.04f, index = 1),
                        RawMlKitObjectLabel(label = "Object $index low", confidence = 0.20f, index = 2)
                    )
                )
            },
            faces = (0 until 6).map { index ->
                RawMlKitFace(
                    faceIndex = index,
                    bounds = RawMlKitBounds(
                        left = 300 + index,
                        top = 400 + index,
                        right = 900 + index,
                        bottom = 1300 + index
                    ),
                    smilingProbability = 0.83f + index * 0.02f,
                    leftEyeOpenProbability = null,
                    rightEyeOpenProbability = null
                )
            }
        )

        assertEquals(6, snapshot.tags.count { it.family == ContentTagFamily.SCENE })
        assertEquals(10, snapshot.tags.count { it.family == ContentTagFamily.OBJECT })
        assertEquals(5, snapshot.regions.count { it.role == ContentRegionRole.OBJECT })
        assertEquals(5, snapshot.regions.count { it.role == ContentRegionRole.FACE })
        assertEquals("scene-7-scene-7", snapshot.topTags(ContentTagFamily.SCENE, limit = 1).single().tagId)
        assertEquals("object-6", snapshot.primaryRegion(ContentRegionRole.OBJECT)?.regionId)
        assertEquals("face-5", snapshot.primaryRegion(ContentRegionRole.FACE)?.regionId)
        assertTrue(snapshot.diagnostics.contains("mlkit-content:image-labels-dropped=2"))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:objects-dropped=2"))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:faces-dropped=1"))
        assertTrue(snapshot.diagnostics.contains("mlkit-content:object-labels-dropped=5"))
    }
}
