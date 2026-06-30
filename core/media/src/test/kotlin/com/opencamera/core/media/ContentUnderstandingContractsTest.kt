package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentUnderstandingContractsTest {

    private fun sampleSceneMask(
        role: SceneMaskRole = SceneMaskRole.PERSON_SUBJECT,
        quality: SceneMaskQuality = SceneMaskQuality.PREVIEW_APPROXIMATE,
        backendId: String = "mlkit-selfie",
        confidence: Float = 0.83f,
        diagnostics: List<String> = listOf("mode=stream")
    ) = SceneMaskDescriptor(
        maskId = "mask-100",
        role = role,
        quality = quality,
        backendId = backendId,
        confidence = confidence,
        transform = SceneMaskTransform(
            sourceWidth = 1280,
            sourceHeight = 720,
            maskWidth = 256,
            maskHeight = 144,
            rotationDegrees = 90
        ),
        diagnostics = diagnostics
    )

    @Test
    fun `scene mask descriptor becomes a content understanding snapshot`() {
        val descriptor = sampleSceneMask()

        val snapshot = ContentUnderstandingSnapshot.fromSceneMask(
            descriptor = descriptor,
            timestampMillis = 1234L
        )

        assertTrue(snapshot.isAvailable)
        assertEquals("content-mask-100", snapshot.snapshotId)
        assertEquals(SceneMaskQuality.PREVIEW_APPROXIMATE, snapshot.quality)
        assertEquals("mlkit-selfie", snapshot.backendId)
        assertEquals(1234L, snapshot.timestampMillis)
        assertTrue(snapshot.hasRegion(ContentRegionRole.PERSON_SUBJECT))

        val subject = snapshot.primaryRegion(ContentRegionRole.PERSON_SUBJECT)
        assertNotNull(subject)
        assertEquals("mask-100", subject.regionId)
        assertEquals(ContentRegionRole.PERSON_SUBJECT, subject.role)
        assertEquals(0.83f, subject.confidence)
        assertEquals(descriptor.transform, subject.transform)
        assertEquals(listOf("mode=stream"), snapshot.diagnostics)
    }

    @Test
    fun `content snapshot preserves saved-photo quality for export analysis`() {
        val descriptor = sampleSceneMask(
            quality = SceneMaskQuality.SAVED_PHOTO,
            confidence = 0.91f,
            diagnostics = listOf("mode=single-image")
        )

        val snapshot = ContentUnderstandingSnapshot.fromSceneMask(
            descriptor = descriptor,
            timestampMillis = 2000L
        )

        assertEquals(SceneMaskQuality.SAVED_PHOTO, snapshot.quality)
        assertEquals(SceneMaskQuality.SAVED_PHOTO, snapshot.regions.single().quality)
        assertEquals(0.91f, snapshot.regions.single().confidence)
        assertEquals(listOf("mode=single-image"), snapshot.diagnostics)
    }

    @Test
    fun `unavailable snapshot carries degraded diagnostics without regions`() {
        val snapshot = ContentUnderstandingSnapshot.unavailable(
            timestampMillis = 3000L,
            backendId = "none",
            reason = "no-recognition-backend"
        )

        assertFalse(snapshot.isAvailable)
        assertEquals(SceneMaskQuality.UNAVAILABLE, snapshot.quality)
        assertEquals("none", snapshot.backendId)
        assertTrue(snapshot.regions.isEmpty())
        assertTrue(snapshot.diagnostics.contains("content-understanding:unavailable"))
        assertTrue(snapshot.diagnostics.contains("content-understanding:reason=no-recognition-backend"))
    }

    @Test
    fun `capability notes expose each recognition family`() {
        val capability = ContentUnderstandingCapability(
            subjectRegions = SceneMaskSupport.SUPPORTED,
            semanticRegions = SceneMaskSupport.DEGRADED,
            faceLandmarks = SceneMaskSupport.UNSUPPORTED,
            objectTags = SceneMaskSupport.DEGRADED,
            sceneTags = SceneMaskSupport.SUPPORTED,
            backendId = "mlkit-mediapipe",
            reason = "semantic-model-warming"
        )

        val notes = ContentUnderstandingPipelineNotes.capabilityNotes(capability)

        assertTrue(notes.contains("content-understanding:backend=mlkit-mediapipe"))
        assertTrue(notes.contains("content-understanding:subject=applied"))
        assertTrue(notes.contains("content-understanding:semantic-regions=degraded"))
        assertTrue(notes.contains("content-understanding:face-landmarks=unsupported"))
        assertTrue(notes.contains("content-understanding:object-tags=degraded"))
        assertTrue(notes.contains("content-understanding:scene-tags=applied"))
        assertTrue(notes.contains("content-understanding:reason=semantic-model-warming"))
        assertEquals(SceneMaskSupport.SUPPORTED, capability.supportFor(ContentUnderstandingFamily.SUBJECT))
        assertEquals(SceneMaskSupport.DEGRADED, capability.supportFor(ContentUnderstandingFamily.SEMANTIC_REGIONS))
        assertEquals(SceneMaskSupport.UNSUPPORTED, capability.supportFor(ContentUnderstandingFamily.FACE_LANDMARKS))
        assertEquals(SceneMaskSupport.DEGRADED, capability.supportFor(ContentUnderstandingFamily.OBJECT_TAGS))
        assertEquals(SceneMaskSupport.SUPPORTED, capability.supportFor(ContentUnderstandingFamily.SCENE_TAGS))
        assertEquals(
            listOf(
                ContentUnderstandingFamilySupport(ContentUnderstandingFamily.SUBJECT, SceneMaskSupport.SUPPORTED),
                ContentUnderstandingFamilySupport(ContentUnderstandingFamily.SEMANTIC_REGIONS, SceneMaskSupport.DEGRADED),
                ContentUnderstandingFamilySupport(ContentUnderstandingFamily.FACE_LANDMARKS, SceneMaskSupport.UNSUPPORTED),
                ContentUnderstandingFamilySupport(ContentUnderstandingFamily.OBJECT_TAGS, SceneMaskSupport.DEGRADED),
                ContentUnderstandingFamilySupport(ContentUnderstandingFamily.SCENE_TAGS, SceneMaskSupport.SUPPORTED)
            ),
            capability.familySupports()
        )
        assertEquals(
            "degraded",
            ContentUnderstandingSupportLabels.support(capability, ContentUnderstandingFamily.SEMANTIC_REGIONS)
        )
        assertEquals("contentSceneTags", ContentUnderstandingFamilyMetadataLabels.supportTag(ContentUnderstandingFamily.SCENE_TAGS))
        assertEquals(
            mapOf(
                "contentSubjectRegions" to "applied",
                "contentSemanticRegions" to "degraded",
                "contentFaceLandmarks" to "unsupported",
                "contentObjectTags" to "degraded",
                "contentSceneTags" to "applied"
            ),
            ContentUnderstandingFamilyMetadataLabels.supportTags(capability)
        )
        assertEquals(
            listOf(
                "portrait-render:subject=applied",
                "portrait-render:face-landmarks=unsupported"
            ),
            ContentUnderstandingConsumerFamilyLabels.supportNotes(
                prefix = "portrait-render",
                capability = capability,
                families = listOf(ContentUnderstandingFamily.SUBJECT, ContentUnderstandingFamily.FACE_LANDMARKS)
            )
        )
        assertEquals(
            mapOf(
                "portraitContentSkippedSubjectRegions" to "applied",
                "portraitContentSkippedFaceLandmarks" to "unsupported"
            ),
            ContentUnderstandingConsumerFamilyLabels.supportMetadataTags(
                capability = capability,
                keysByFamily = mapOf(
                    ContentUnderstandingFamily.SUBJECT to "portraitContentSkippedSubjectRegions",
                    ContentUnderstandingFamily.FACE_LANDMARKS to "portraitContentSkippedFaceLandmarks"
                )
            )
        )
    }

    @Test
    fun `content understanding families expose stable keys for backend diagnostics`() {
        assertEquals(
            listOf(
                "subject",
                "semantic-regions",
                "face-landmarks",
                "object-tags",
                "scene-tags"
            ),
            ContentUnderstandingFamily.entries.map { it.key }
        )
        assertEquals("subject", ContentUnderstandingFamilyKeys.SUBJECT)
        assertEquals("semantic-regions", ContentUnderstandingFamilyKeys.SEMANTIC_REGIONS)
        assertEquals("face-landmarks", ContentUnderstandingFamilyKeys.FACE_LANDMARKS)
        assertEquals("object-tags", ContentUnderstandingFamilyKeys.OBJECT_TAGS)
        assertEquals("scene-tags", ContentUnderstandingFamilyKeys.SCENE_TAGS)
        assertEquals(ContentUnderstandingFamily.SUBJECT, ContentUnderstandingFamily.fromKey("subject"))
        assertEquals(ContentUnderstandingFamily.SEMANTIC_REGIONS, ContentUnderstandingFamily.fromKey("semantic-regions"))
        assertEquals(ContentUnderstandingFamily.FACE_LANDMARKS, ContentUnderstandingFamily.fromKey("face-landmarks"))
        assertEquals(ContentUnderstandingFamily.OBJECT_TAGS, ContentUnderstandingFamily.fromKey("object-tags"))
        assertEquals(ContentUnderstandingFamily.SCENE_TAGS, ContentUnderstandingFamily.fromKey("scene-tags"))
        assertNull(ContentUnderstandingFamily.fromKey("unknown-family"))
        assertEquals(
            "content-understanding:object-tags=degraded",
            ContentUnderstandingPipelineNotes.familySupport(
                ContentUnderstandingFamily.OBJECT_TAGS,
                SceneMaskSupport.DEGRADED
            )
        )
        assertEquals(
            ContentUnderstandingFamilySupport(
                family = ContentUnderstandingFamily.OBJECT_TAGS,
                support = SceneMaskSupport.DEGRADED
            ),
            ContentUnderstandingPipelineNotes.parseFamilySupport("content-understanding:object-tags=degraded")
        )
        assertEquals(
            ContentUnderstandingFamilySupport(
                family = ContentUnderstandingFamily.SCENE_TAGS,
                support = SceneMaskSupport.SUPPORTED
            ),
            ContentUnderstandingPipelineNotes.parseFamilySupport("content-understanding:scene-tags=applied")
        )
        assertNull(ContentUnderstandingPipelineNotes.parseFamilySupport("content-understanding:reason=model-loading"))
        assertNull(ContentUnderstandingPipelineNotes.parseFamilySupport("content-understanding:unknown-family=degraded"))
    }

    @Test
    fun `content availability and reason diagnostics are shared`() {
        assertEquals("content-understanding:available", ContentUnderstandingPipelineNotes.availability(true))
        assertEquals("content-understanding:unavailable", ContentUnderstandingPipelineNotes.availability(false))
        assertEquals("content-understanding:unavailable", ContentUnderstandingPipelineNotes.unavailable())
        assertEquals(
            "content-understanding:reason=no-recognition-backend",
            ContentUnderstandingPipelineNotes.reason("no-recognition-backend")
        )
        assertEquals(
            "content-understanding:reason=mlkit-content-partial-failure:object-tags+face-landmarks",
            ContentUnderstandingPipelineNotes.partialFailureReason(
                prefix = "mlkit-content-partial-failure",
                failedFamilyKeys = listOf(
                    ContentUnderstandingFamilyKeys.OBJECT_TAGS,
                    ContentUnderstandingFamilyKeys.FACE_LANDMARKS
                )
            )
        )
        assertEquals(
            "content-understanding:reason=mlkit-content-partial-failure:object-tags+face-landmarks",
            ContentUnderstandingPipelineNotes.partialFailureReason(
                prefix = "mlkit-content-partial-failure",
                failedFamilies = listOf(
                    ContentUnderstandingFamily.OBJECT_TAGS,
                    ContentUnderstandingFamily.FACE_LANDMARKS
                )
            )
        )
    }

    @Test
    fun `degraded family diagnostics take precedence over applied diagnostics`() {
        val snapshot = ContentUnderstandingSnapshot.unavailable(
            timestampMillis = 3600L,
            backendId = "test-content",
            reason = "partial-failure"
        ).copy(
            diagnostics = listOf(
                ContentUnderstandingPipelineNotes.familySupport(
                    ContentUnderstandingFamily.OBJECT_TAGS,
                    SceneMaskSupport.SUPPORTED
                ),
                ContentUnderstandingPipelineNotes.familySupport(
                    ContentUnderstandingFamily.OBJECT_TAGS,
                    SceneMaskSupport.DEGRADED
                )
            )
        )

        assertEquals(SceneMaskSupport.DEGRADED, snapshot.capabilitySummary().objectTags)
    }

    @Test
    fun `tag only snapshot is available for scene and object understanding`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-tags-1",
            timestampMillis = 4000L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-sky",
                    label = "Sky",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.88f,
                    backendId = "mlkit-labels"
                ),
                ContentTagDescriptor(
                    tagId = "object-food",
                    label = "Food",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.72f,
                    backendId = "mlkit-objects"
                )
            ),
            diagnostics = listOf("labels=2")
        )

        assertTrue(snapshot.isAvailable)
        assertTrue(snapshot.hasTagFamily(ContentTagFamily.SCENE))
        assertTrue(snapshot.hasTagFamily(ContentTagFamily.OBJECT))
        assertEquals("Sky", snapshot.topTags(ContentTagFamily.SCENE).single().label)
        assertEquals(listOf("labels=2"), snapshot.diagnostics)
    }

    @Test
    fun `face region preserves normalized bounds for portrait consumers`() {
        val region = ContentRegionDescriptor(
            regionId = "face-0",
            role = ContentRegionRole.FACE,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "mlkit-face",
            confidence = 0.93f,
            transform = SceneMaskTransform(
                sourceWidth = 4000,
                sourceHeight = 3000,
                maskWidth = 4000,
                maskHeight = 3000,
                rotationDegrees = 0
            ),
            bounds = ContentRegionBounds(
                left = 0.20f,
                top = 0.10f,
                right = 0.45f,
                bottom = 0.52f
            )
        )

        assertEquals(ContentRegionRole.FACE, region.role)
        assertEquals(0.20f, region.bounds?.left)
        assertEquals(0.52f, region.bounds?.bottom)
    }

    @Test
    fun `combined snapshot preserves regions tags and degradation diagnostics`() {
        val subject = ContentUnderstandingSnapshot.fromSceneMask(
            descriptor = sampleSceneMask(quality = SceneMaskQuality.SAVED_PHOTO),
            timestampMillis = 5000L
        )
        val labels = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "labels",
            timestampMillis = 5000L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-beach",
                    label = "Beach",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.79f,
                    backendId = "mlkit-labels"
                )
            )
        )
        val degraded = ContentUnderstandingSnapshot.unavailable(
            timestampMillis = 5000L,
            backendId = "mlkit-objects",
            reason = "object-detector-unavailable"
        )

        val combined = ContentUnderstandingSnapshot.combine(
            snapshotId = "content-shot-1",
            timestampMillis = 5000L,
            backendId = "mlkit-composite",
            snapshots = listOf(subject, labels, degraded)
        )

        assertTrue(combined.isAvailable)
        assertEquals(SceneMaskQuality.DEGRADED, combined.quality)
        assertTrue(combined.hasRegion(ContentRegionRole.PERSON_SUBJECT))
        assertTrue(combined.hasTagFamily(ContentTagFamily.SCENE))
        assertTrue(combined.diagnostics.contains("content-understanding:reason=object-detector-unavailable"))
    }

    @Test
    fun `snapshot capability summary exposes available and missing recognition families`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-family-summary",
            timestampMillis = 5050L,
            quality = SceneMaskQuality.DEGRADED,
            backendId = "saved-photo-composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.92f
                ),
                contentRegion(
                    role = ContentRegionRole.SKY,
                    confidence = 0.84f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-sky",
                    label = "Sky",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.88f,
                    backendId = "mlkit-labels"
                )
            ),
            diagnostics = listOf("content-understanding:reason=object-detector-unavailable")
        )

        val capability = snapshot.capabilitySummary()
        val notes = ContentUnderstandingPipelineNotes.snapshotCapabilityNotes(snapshot)

        assertEquals(SceneMaskSupport.SUPPORTED, capability.subjectRegions)
        assertEquals(SceneMaskSupport.SUPPORTED, capability.semanticRegions)
        assertEquals(SceneMaskSupport.SUPPORTED, capability.faceLandmarks)
        assertEquals(SceneMaskSupport.UNSUPPORTED, capability.objectTags)
        assertEquals(SceneMaskSupport.SUPPORTED, capability.sceneTags)
        assertEquals("saved-photo-composite", capability.backendId)
        assertEquals("object-detector-unavailable", capability.reason)
        assertTrue(notes.contains("content-understanding:backend=saved-photo-composite"))
        assertTrue(notes.contains("content-understanding:subject=applied"))
        assertTrue(notes.contains("content-understanding:semantic-regions=applied"))
        assertTrue(notes.contains("content-understanding:face-landmarks=applied"))
        assertTrue(notes.contains("content-understanding:object-tags=unsupported"))
        assertTrue(notes.contains("content-understanding:scene-tags=applied"))
        assertTrue(notes.contains("content-understanding:reason=object-detector-unavailable"))
    }

    @Test
    fun `snapshot capability summary treats family degraded diagnostics as degraded support`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-family-degraded",
            timestampMillis = 5051L,
            quality = SceneMaskQuality.DEGRADED,
            backendId = "saved-photo-composite",
            regions = emptyList(),
            tags = emptyList(),
            diagnostics = listOf(
                "content-understanding:object-tags=degraded",
                "content-understanding:reason=mlkit-content-partial-failure:object-tags"
            )
        )

        val capability = snapshot.capabilitySummary()
        val notes = ContentUnderstandingPipelineNotes.snapshotCapabilityNotes(snapshot)

        assertEquals(SceneMaskSupport.DEGRADED, capability.objectTags)
        assertEquals("mlkit-content-partial-failure:object-tags", capability.reason)
        assertTrue(notes.contains("content-understanding:object-tags=degraded"))
    }

    @Test
    fun `primary scene hint resolves recognized scene tags`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-tags-food",
            timestampMillis = 6000L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val hint = snapshot.primarySceneHint()

        assertNotNull(hint)
        assertEquals(ContentSceneHint.FOOD, hint.hint)
        assertEquals(ContentSceneHintSource.TAG, hint.source)
        assertEquals(0.91f, hint.confidence)
    }

    @Test
    fun `primary scene hint can use recognized object tags`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-object-food",
            timestampMillis = 6001L,
            backendId = "mlkit-objects",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "object-0-4-food",
                    label = "Food",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.88f,
                    backendId = "mlkit-objects"
                )
            )
        )

        val hint = snapshot.primarySceneHint()
        val notes = ContentUnderstandingPipelineNotes.semanticSummaryNotes(snapshot)

        assertNotNull(hint)
        assertEquals(ContentSceneHint.FOOD, hint.hint)
        assertEquals(ContentSceneHintSource.TAG, hint.source)
        assertEquals(0.88f, hint.confidence)
        assertTrue(notes.contains("content-understanding:primary-scene-source-id=tag:object-0-4-food"))
    }

    @Test
    fun `primary scene hint falls back to semantic regions`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-region-water",
            timestampMillis = 6002L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "semantic-regions",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.WATER,
                    confidence = 0.86f
                )
            )
        )

        val hint = snapshot.primarySceneHint()

        assertNotNull(hint)
        assertEquals(ContentSceneHint.SKY_WATER, hint.hint)
        assertEquals(ContentSceneHintSource.REGION, hint.source)
        assertEquals(0.86f, hint.confidence)
    }

    @Test
    fun `primary scene hint selects highest confidence recognized signal`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-mixed-scene",
            timestampMillis = 6003L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FOOD,
                    confidence = 0.74f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-night",
                    label = "Night",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.88f,
                    backendId = "mlkit-labels"
                ),
                ContentTagDescriptor(
                    tagId = "scene-unknown",
                    label = "Unknown",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.98f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val hint = snapshot.primarySceneHint()

        assertNotNull(hint)
        assertEquals(ContentSceneHint.LOW_LIGHT, hint.hint)
        assertEquals(ContentSceneHintSource.TAG, hint.source)
        assertEquals(0.88f, hint.confidence)
    }

    @Test
    fun `scene hints ignore semantic tokens embedded inside unrelated words`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-fragment-scene-tags",
            timestampMillis = 6004L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-watermark",
                    label = "Watermark overlay",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.95f,
                    backendId = "mlkit-labels"
                ),
                ContentTagDescriptor(
                    tagId = "scene-nightstand",
                    label = "Nightstand furniture",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.94f,
                    backendId = "mlkit-labels"
                )
            )
        )

        assertNull(snapshot.primarySceneHint())
    }

    @Test
    fun `scene hints match common plural recognition aliases`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-plural-scene-tags",
            timestampMillis = 6005L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-restaurants",
                    label = "Restaurants",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val hint = snapshot.primarySceneHint()

        assertNotNull(hint)
        assertEquals(ContentSceneHint.FOOD, hint.hint)
    }

    @Test
    fun `recognition label classifier owns scene and semantic text mapping`() {
        assertEquals(
            ContentSceneHint.FOOD,
            ContentRecognitionLabelClassifier.sceneHint(tagId = "scene-restaurants", label = "Restaurants")
        )
        assertEquals(
            ContentSceneHint.SKY_WATER,
            ContentRecognitionLabelClassifier.sceneHint(tagId = "scene-beaches", label = "Beaches")
        )
        assertNull(
            ContentRecognitionLabelClassifier.sceneHint(tagId = "scene-watermark", label = "Watermark overlay")
        )

        assertEquals(
            ContentRegionRole.BUILDING,
            ContentRecognitionLabelClassifier.semanticRegionRole(label = "City buildings")
        )
        assertEquals(
            ContentRegionRole.VEGETATION,
            ContentRecognitionLabelClassifier.semanticRegionRole(label = "Trees")
        )
        assertNull(ContentRecognitionLabelClassifier.semanticRegionRole(label = "Documentary camera"))
    }

    @Test
    fun `primary subject hint resolves recognized subject regions`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-subject-face",
            timestampMillis = 6100L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "semantic-regions",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.PERSON_SUBJECT,
                    confidence = 0.82f
                ),
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.94f
                )
            )
        )

        val hint = snapshot.primarySubjectHint(
            ContentSubjectHint.FACE,
            ContentSubjectHint.PERSON
        )

        assertNotNull(hint)
        assertEquals(ContentSubjectHint.FACE, hint.hint)
        assertEquals(ContentSubjectHintSource.REGION, hint.source)
        assertEquals("face", hint.sourceKey)
        assertEquals("region-${ContentRegionRole.FACE.name.lowercase()}", hint.sourceId)
        assertEquals(0.94f, hint.confidence)
    }

    @Test
    fun `primary subject hint resolves object tags with stable source metadata`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-subject-object-tag",
            timestampMillis = 6101L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "object-coffee-cup",
                    label = "Coffee cup",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.88f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val hint = snapshot.primarySubjectHint(ContentSubjectHint.OBJECT)

        assertNotNull(hint)
        assertEquals(ContentSubjectHint.OBJECT, hint.hint)
        assertEquals(ContentSubjectHintSource.TAG, hint.source)
        assertEquals("object", hint.sourceKey)
        assertEquals("object-coffee-cup", hint.sourceId)
        assertEquals(0.88f, hint.confidence)
    }

    @Test
    fun `primary subject hint ignores signals outside requested hints`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-subject-filtered",
            timestampMillis = 6102L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.OBJECT,
                    confidence = 0.97f
                ),
                contentRegion(
                    role = ContentRegionRole.PERSON_SUBJECT,
                    confidence = 0.81f
                )
            )
        )

        val hint = snapshot.primarySubjectHint(
            ContentSubjectHint.FACE,
            ContentSubjectHint.PERSON
        )

        assertNotNull(hint)
        assertEquals(ContentSubjectHint.PERSON, hint.hint)
        assertEquals(ContentSubjectHintSource.REGION, hint.source)
        assertEquals("person-subject", hint.sourceKey)
        assertEquals(0.81f, hint.confidence)
    }

    @Test
    fun `semantic summary notes expose primary scene people and object signals`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-semantic-summary",
            timestampMillis = 6200L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.93f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                ),
                ContentTagDescriptor(
                    tagId = "object-coffee-cup",
                    label = "Coffee cup",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.88f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val notes = ContentUnderstandingPipelineNotes.semanticSummaryNotes(snapshot)

        assertTrue(notes.contains("content-understanding:primary-scene=food"))
        assertTrue(notes.contains("content-understanding:primary-scene-source=tag"))
        assertTrue(notes.contains("content-understanding:primary-scene-source-id=tag:scene-food"))
        assertTrue(notes.contains("content-understanding:primary-scene-confidence=0.91"))
        assertTrue(notes.contains("content-understanding:primary-people=face"))
        assertTrue(notes.contains("content-understanding:primary-people-source=region:face"))
        assertTrue(notes.contains("content-understanding:primary-people-confidence=0.93"))
        assertTrue(notes.contains("content-understanding:primary-object=object"))
        assertTrue(notes.contains("content-understanding:primary-object-source=tag:object-coffee-cup"))
        assertTrue(notes.contains("content-understanding:primary-object-confidence=0.88"))
    }

    @Test
    fun `semantic region candidates expose stable ranked labels`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-semantic-candidates",
            timestampMillis = 6201L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.BACKGROUND,
                    regionId = "background-0",
                    confidence = 0.99f
                ),
                contentRegion(
                    role = ContentRegionRole.FOOD,
                    regionId = "food-0",
                    confidence = 0.86f,
                    bounds = ContentRegionBounds(
                        left = 0.20f,
                        top = 0.15f,
                        right = 0.50f,
                        bottom = 0.70f
                    )
                ),
                contentRegion(
                    role = ContentRegionRole.DOCUMENT,
                    regionId = "document-0",
                    confidence = 0.92f,
                    bounds = ContentRegionBounds(
                        left = 0.10f,
                        top = 0.20f,
                        right = 0.60f,
                        bottom = 0.80f
                    )
                )
            )
        )

        val candidates = snapshot.semanticRegionCandidates()
        val notes = ContentUnderstandingPipelineNotes.semanticSummaryNotes(snapshot)

        assertEquals(
            listOf(ContentRegionRole.DOCUMENT, ContentRegionRole.FOOD),
            candidates.map { it.role }
        )
        assertEquals(
            "document@region:document-0:0.92,food@region:food-0:0.86",
            ContentUnderstandingCandidateLabels.semanticRegions(candidates)
        )
        assertTrue(
            notes.contains(
                "content-understanding:semantic-region-candidates=document@region:document-0:0.92,food@region:food-0:0.86"
            )
        )
        assertEquals(
            "region:document-0=0.10,0.20,0.60,0.80;region:food-0=0.20,0.15,0.50,0.70",
            ContentUnderstandingCandidateLabels.semanticRegionBounds(candidates)
        )
        assertTrue(
            notes.contains(
                "content-understanding:semantic-region-bounds=region:document-0=0.10,0.20,0.60,0.80;region:food-0=0.20,0.15,0.50,0.70"
            )
        )
    }

    @Test
    fun `semantic region candidate labels preserve concrete region identity`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-repeated-semantic-candidates",
            timestampMillis = 6202L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.DOCUMENT,
                    regionId = "document-0",
                    confidence = 0.91f
                ),
                contentRegion(
                    role = ContentRegionRole.DOCUMENT,
                    regionId = "document-1",
                    confidence = 0.93f
                )
            )
        )

        assertEquals(
            "document@region:document-1:0.93,document@region:document-0:0.91",
            ContentUnderstandingCandidateLabels.semanticRegions(snapshot.semanticRegionCandidates())
        )
    }

    @Test
    fun `semantic region candidates use stable default budget`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-semantic-budget",
            timestampMillis = 6203L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = (0..7).map { index ->
                contentRegion(
                    role = ContentRegionRole.DOCUMENT,
                    regionId = "document-$index",
                    confidence = 0.80f + (index * 0.01f)
                )
            }
        )

        val defaultCandidates = snapshot.semanticRegionCandidates()
        val allCandidates = snapshot.semanticRegionCandidates(limit = 8)
        val notes = ContentUnderstandingPipelineNotes.semanticSummaryNotes(snapshot)

        assertEquals(6, defaultCandidates.size)
        assertEquals(8, allCandidates.size)
        assertEquals("region:document-7", defaultCandidates.first().source)
        assertEquals("region:document-2", defaultCandidates.last().source)
        assertFalse(
            ContentUnderstandingCandidateLabels.semanticRegions(defaultCandidates)
                .contains("region:document-1")
        )
        assertTrue(
            notes.contains(
                "content-understanding:semantic-region-candidates=" +
                    ContentUnderstandingCandidateLabels.semanticRegions(defaultCandidates)
            )
        )
    }

    @Test
    fun `adaptation profile controls semantic and placement candidate thresholds and budgets`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-profiled-candidates",
            timestampMillis = 62035L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.SKY,
                    regionId = "sky-high",
                    confidence = 0.82f,
                    bounds = ContentRegionBounds(0.0f, 0.0f, 1.0f, 0.40f)
                ),
                contentRegion(
                    role = ContentRegionRole.FOOD,
                    regionId = "food-mid",
                    confidence = 0.64f,
                    bounds = ContentRegionBounds(0.15f, 0.45f, 0.55f, 0.85f)
                ),
                contentRegion(
                    role = ContentRegionRole.FACE,
                    regionId = "face-mid",
                    confidence = 0.66f,
                    bounds = ContentRegionBounds(0.58f, 0.18f, 0.88f, 0.55f)
                )
            )
        )
        val profile = ContentSavedPhotoAdaptationProfile(
            minConfidence = 0.7f,
            semanticRegionMinConfidence = 0.6f,
            semanticRegionLimit = 2,
            placementAvoidanceMinConfidence = 0.6f,
            placementAvoidanceLimit = 2
        )

        assertEquals(listOf("region:sky-high"), snapshot.semanticRegionCandidates().map { it.source })
        assertEquals(listOf("region:sky-high"), snapshot.placementAvoidanceCandidates().map { it.source })
        assertEquals(
            listOf("region:sky-high", "region:food-mid"),
            snapshot.semanticRegionCandidates(profile).map { it.source }
        )
        assertEquals(
            listOf("region:sky-high", "region:face-mid"),
            snapshot.placementAvoidanceCandidates(profile).map { it.source }
        )
    }

    @Test
    fun `placement avoidance candidates expose bounded content geometry`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-placement-avoidance",
            timestampMillis = 6204L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    regionId = "face-0",
                    confidence = 0.94f,
                    bounds = ContentRegionBounds(
                        left = 0.12f,
                        top = 0.10f,
                        right = 0.42f,
                        bottom = 0.58f
                    )
                ),
                contentRegion(
                    role = ContentRegionRole.DOCUMENT,
                    regionId = "document-0",
                    confidence = 0.90f,
                    bounds = ContentRegionBounds(
                        left = 0.48f,
                        top = 0.18f,
                        right = 0.88f,
                        bottom = 0.82f
                    )
                ),
                contentRegion(
                    role = ContentRegionRole.FOOD,
                    regionId = "food-low",
                    confidence = 0.66f,
                    bounds = ContentRegionBounds(
                        left = 0.10f,
                        top = 0.70f,
                        right = 0.32f,
                        bottom = 0.92f
                    )
                ),
                contentRegion(
                    role = ContentRegionRole.OBJECT,
                    regionId = "object-unbounded",
                    confidence = 0.93f
                )
            )
        )

        val candidates = snapshot.placementAvoidanceCandidates()
        val notes = ContentUnderstandingPipelineNotes.semanticSummaryNotes(snapshot)

        assertEquals(
            listOf("region:face-0", "region:document-0"),
            candidates.map { it.source }
        )
        assertEquals(
            "face@region:face-0:0.94=0.12,0.10,0.42,0.58;" +
                "document@region:document-0:0.90=0.48,0.18,0.88,0.82",
            ContentUnderstandingCandidateLabels.placementAvoidance(candidates)
        )
        assertTrue(
            notes.contains(
                "content-understanding:placement-avoidance=" +
                    ContentUnderstandingCandidateLabels.placementAvoidance(candidates)
            )
        )
    }

    @Test
    fun `placement avoidance candidate labels expose shared geometry fields`() {
        val candidate = ContentPlacementAvoidanceCandidate(
            role = ContentRegionRole.FACE,
            source = "region:face-0",
            confidence = 0.94f,
            bounds = ContentRegionBounds(
                left = 0.12f,
                top = 0.10f,
                right = 0.42f,
                bottom = 0.58f
            )
        )

        assertEquals("face", ContentUnderstandingCandidateLabels.placementRole(candidate))
        assertEquals("region:face-0", ContentUnderstandingCandidateLabels.placementSource(candidate))
        assertEquals("0.94", ContentUnderstandingCandidateLabels.placementConfidence(candidate))
        assertEquals("0.27,0.34", ContentUnderstandingCandidateLabels.placementCenter(candidate))
        assertEquals("0.30,0.48", ContentUnderstandingCandidateLabels.placementSize(candidate))
        assertEquals("0.14", ContentUnderstandingCandidateLabels.placementArea(candidate))
    }

    @Test
    fun `adaptation winner labels expose shared key source and confidence`() {
        val scene = ContentSceneHintMatch(
            hint = ContentSceneHint.FOOD,
            source = ContentSceneHintSource.TAG,
            sourceKey = "scene",
            sourceId = "scene-food",
            confidence = 0.91f
        )
        val subject = ContentSubjectHintMatch(
            hint = ContentSubjectHint.FACE,
            source = ContentSubjectHintSource.REGION,
            sourceKey = "face",
            sourceId = "face-0",
            confidence = 0.93f
        )
        val checkIn = ContentCheckInScenarioDecision(
            scenario = ContentCheckInScenarioHint.PEOPLE_PLACE,
            source = "region:face",
            confidence = 0.93f
        )

        assertEquals("food", ContentUnderstandingCandidateLabels.sceneHint(scene))
        assertEquals("tag:scene-food", ContentUnderstandingCandidateLabels.sceneSource(scene))
        assertEquals("0.91", ContentUnderstandingCandidateLabels.sceneConfidence(scene))
        assertEquals("face", ContentUnderstandingCandidateLabels.subjectHint(subject))
        assertEquals("region:face", ContentUnderstandingCandidateLabels.subjectSource(subject))
        assertEquals("0.93", ContentUnderstandingCandidateLabels.subjectConfidence(subject))
        assertEquals("people-place", ContentUnderstandingCandidateLabels.checkInScenario(checkIn))
        assertEquals("region:face", ContentUnderstandingCandidateLabels.checkInSource(checkIn))
        assertEquals("0.93", ContentUnderstandingCandidateLabels.checkInConfidence(checkIn))
    }

    @Test
    fun `scene source kind labels are shared for summary and adaptation notes`() {
        val tagScene = ContentSceneHintMatch(
            hint = ContentSceneHint.FOOD,
            source = ContentSceneHintSource.TAG,
            sourceKey = "scene",
            sourceId = "scene-food",
            confidence = 0.91f
        )
        val regionScene = ContentSceneHintMatch(
            hint = ContentSceneHint.SKY_WATER,
            source = ContentSceneHintSource.REGION,
            sourceKey = "sky",
            sourceId = "sky-0",
            confidence = 0.86f
        )

        assertEquals("tag", ContentUnderstandingCandidateLabels.sceneSourceKind(tagScene))
        assertEquals("region", ContentUnderstandingCandidateLabels.sceneSourceKind(regionScene))
    }

    @Test
    fun `consumer skip reason labels are shared across content consumers`() {
        assertEquals("missing-content", ContentUnderstandingConsumerSkipLabels.MISSING_CONTENT)
        assertEquals("not-ready", ContentUnderstandingConsumerSkipLabels.NOT_READY)
        assertEquals("no-scene-hint", ContentUnderstandingConsumerSkipLabels.NO_SCENE_HINT)
        assertEquals("no-subject-hint", ContentUnderstandingConsumerSkipLabels.NO_SUBJECT_HINT)
        assertEquals("no-scenario", ContentUnderstandingConsumerSkipLabels.NO_SCENARIO)
        assertEquals(
            "low-confidence-scene-hint",
            ContentUnderstandingConsumerSkipLabels.LOW_CONFIDENCE_SCENE_HINT
        )
        assertEquals(
            "low-confidence-subject-hint",
            ContentUnderstandingConsumerSkipLabels.LOW_CONFIDENCE_SUBJECT_HINT
        )
        assertEquals("low-confidence-scenario", ContentUnderstandingConsumerSkipLabels.LOW_CONFIDENCE_SCENARIO)
    }

    @Test
    fun `support labels are shared across content notes and metadata`() {
        assertEquals("applied", ContentUnderstandingSupportLabels.support(SceneMaskSupport.SUPPORTED))
        assertEquals("degraded", ContentUnderstandingSupportLabels.support(SceneMaskSupport.DEGRADED))
        assertEquals("unsupported", ContentUnderstandingSupportLabels.support(SceneMaskSupport.UNSUPPORTED))
    }

    @Test
    fun `saved photo adaptation readiness excludes preview approximate snapshots`() {
        val saved = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-saved",
            timestampMillis = 6300L,
            backendId = "mlkit-labels",
            quality = SceneMaskQuality.SAVED_PHOTO,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                )
            )
        )
        val degradedWithEvidence = saved.copy(
            snapshotId = "content-degraded",
            quality = SceneMaskQuality.DEGRADED
        )
        val previewApproximate = saved.copy(
            snapshotId = "content-preview",
            quality = SceneMaskQuality.PREVIEW_APPROXIMATE
        )

        assertTrue(saved.isReadyForSavedPhotoAdaptation())
        assertTrue(degradedWithEvidence.isReadyForSavedPhotoAdaptation())
        assertFalse(previewApproximate.isReadyForSavedPhotoAdaptation())
        assertFalse(
            ContentUnderstandingSnapshot.unavailable(
                timestampMillis = 6301L,
                backendId = "none",
                reason = "missing-backend"
            ).isReadyForSavedPhotoAdaptation()
        )
    }

    @Test
    fun `saved photo adaptation decisions share style portrait and check in signals`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-adaptation",
            timestampMillis = 6400L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.93f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                ),
                ContentTagDescriptor(
                    tagId = "object-coffee-cup",
                    label = "Coffee cup",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.88f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertEquals(ContentSceneHint.FOOD, decisions.styleScene?.hint)
        assertEquals(ContentSubjectHint.FACE, decisions.portraitSubject?.hint)
        assertEquals(ContentCheckInScenarioHint.PEOPLE_PLACE, decisions.checkInScenario?.scenario)
        assertEquals("region:face", decisions.checkInScenario?.source)
        assertEquals(0.93f, decisions.checkInScenario?.confidence)
    }

    @Test
    fun `saved photo adaptation decisions expose ranked candidates for tuning`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-ranked-candidates",
            timestampMillis = 6402L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.SKY,
                    regionId = "sky-0",
                    confidence = 0.86f
                ),
                contentRegion(
                    role = ContentRegionRole.FACE,
                    regionId = "face-0",
                    confidence = 0.93f
                ),
                contentRegion(
                    role = ContentRegionRole.PERSON_SUBJECT,
                    regionId = "person-0",
                    confidence = 0.82f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                ),
                ContentTagDescriptor(
                    tagId = "object-laptop",
                    label = "Laptop",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.89f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertEquals(
            listOf(ContentSceneHint.FOOD, ContentSceneHint.SKY_WATER),
            decisions.styleSceneCandidates.map { it.hint }
        )
        assertEquals(
            listOf(ContentSubjectHint.FACE, ContentSubjectHint.PERSON),
            decisions.portraitSubjectCandidates.map { it.hint }
        )
        assertEquals(
            listOf(ContentCheckInScenarioHint.PEOPLE_PLACE, ContentCheckInScenarioHint.OBJECT_PLACE),
            decisions.checkInScenarioCandidates.map { it.scenario }
        )

        val notes = ContentUnderstandingPipelineNotes.savedPhotoAdaptationDecisionNotes(snapshot)

        assertTrue(
            notes.contains(
                "content-understanding:adaptation-style-scene-candidates=food@tag:scene-food:0.91,sky-water@region:sky:0.86"
            )
        )
        assertTrue(
            notes.contains(
                "content-understanding:adaptation-portrait-subject-candidates=face@region:face:0.93,person-subject@region:person-subject:0.82"
            )
        )
        assertTrue(
            notes.contains(
                "content-understanding:adaptation-checkin-candidates=people-place@region:face:0.93,object-place@tag:object-laptop:0.89"
            )
        )
        assertEquals(
            "food@tag:scene-food:0.91,sky-water@region:sky:0.86",
            ContentUnderstandingCandidateLabels.sceneCandidates(decisions.styleSceneCandidates)
        )
        assertEquals(
            "face@region:face:0.93,person-subject@region:person-subject:0.82",
            ContentUnderstandingCandidateLabels.subjectCandidates(decisions.portraitSubjectCandidates)
        )
        assertEquals(
            "people-place@region:face:0.93,object-place@tag:object-laptop:0.89",
            ContentUnderstandingCandidateLabels.checkInCandidates(decisions.checkInScenarioCandidates)
        )
    }

    @Test
    fun `check in scenario uses strongest ranked scenario candidate`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-checkin-ranked-winner",
            timestampMillis = 6403L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.74f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "object-product",
                    label = "Product",
                    family = ContentTagFamily.OBJECT,
                    confidence = 0.94f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertEquals(ContentCheckInScenarioHint.OBJECT_PLACE, decisions.checkInScenario?.scenario)
        assertEquals(
            listOf(ContentCheckInScenarioHint.OBJECT_PLACE, ContentCheckInScenarioHint.PEOPLE_PLACE),
            decisions.checkInScenarioCandidates.map { it.scenario }
        )
    }

    @Test
    fun `saved photo adaptation profiles separate default decisions from low confidence review`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-adaptation-profile",
            timestampMillis = 64035L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.62f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.63f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val defaultDecisions = snapshot.savedPhotoAdaptationDecisions(ContentSavedPhotoAdaptationProfile.DEFAULT)
        val reviewDecisions = snapshot.savedPhotoAdaptationDecisions(ContentSavedPhotoAdaptationProfile.LOW_CONFIDENCE_REVIEW)

        assertEquals(0.7f, ContentSavedPhotoAdaptationProfile.DEFAULT.minConfidence)
        assertEquals(0f, ContentSavedPhotoAdaptationProfile.LOW_CONFIDENCE_REVIEW.minConfidence)
        assertNull(defaultDecisions.styleScene)
        assertNull(defaultDecisions.portraitSubject)
        assertEquals(ContentSceneHint.FOOD, reviewDecisions.styleScene?.hint)
        assertEquals(ContentSubjectHint.FACE, reviewDecisions.portraitSubject?.hint)
    }

    @Test
    fun `document semantic region drives check in clarity scenario`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-checkin-document",
            timestampMillis = 6404L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.DOCUMENT,
                    regionId = "document-0",
                    confidence = 0.92f
                )
            )
        )

        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertEquals("clarity", decisions.checkInScenario?.scenario?.storageKey)
        assertEquals("region:document", decisions.checkInScenario?.source)
        assertEquals(
            "clarity@region:document:0.92",
            ContentUnderstandingCandidateLabels.checkInCandidates(decisions.checkInScenarioCandidates)
        )
    }

    @Test
    fun `saved photo adaptation decisions are empty for preview approximate content`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-preview-adaptation",
            timestampMillis = 6401L,
            quality = SceneMaskQuality.PREVIEW_APPROXIMATE,
            backendId = "preview-content",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.93f,
                    quality = SceneMaskQuality.PREVIEW_APPROXIMATE
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "preview-content"
                )
            )
        )

        val decisions = snapshot.savedPhotoAdaptationDecisions()

        assertEquals(ContentSavedPhotoAdaptationDecisions.EMPTY, decisions)
    }

    @Test
    fun `adaptation decision notes expose shared downstream decisions`() {
        val snapshot = ContentUnderstandingSnapshot(
            snapshotId = "content-adaptation-notes",
            timestampMillis = 6500L,
            quality = SceneMaskQuality.SAVED_PHOTO,
            backendId = "composite",
            regions = listOf(
                contentRegion(
                    role = ContentRegionRole.FACE,
                    confidence = 0.93f
                )
            ),
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "mlkit-labels"
                )
            )
        )

        val notes = ContentUnderstandingPipelineNotes.savedPhotoAdaptationDecisionNotes(snapshot)

        assertTrue(notes.contains("content-understanding:adaptation-ready=true"))
        assertTrue(notes.contains("content-understanding:adaptation-style-scene=food"))
        assertTrue(notes.contains("content-understanding:adaptation-style-scene-source=tag"))
        assertTrue(notes.contains("content-understanding:adaptation-style-scene-source-id=tag:scene-food"))
        assertTrue(notes.contains("content-understanding:adaptation-style-scene-confidence=0.91"))
        assertTrue(notes.contains("content-understanding:adaptation-portrait-subject=face"))
        assertTrue(notes.contains("content-understanding:adaptation-portrait-subject-source=region:face"))
        assertTrue(notes.contains("content-understanding:adaptation-portrait-subject-confidence=0.93"))
        assertTrue(notes.contains("content-understanding:adaptation-checkin-scenario=people-place"))
        assertTrue(notes.contains("content-understanding:adaptation-checkin-source=region:face"))
        assertTrue(notes.contains("content-understanding:adaptation-checkin-confidence=0.93"))
    }

    @Test
    fun `adaptation decision notes mark preview approximate content as not ready`() {
        val snapshot = ContentUnderstandingSnapshot.fromTags(
            snapshotId = "content-preview-notes",
            timestampMillis = 6501L,
            backendId = "preview-content",
            quality = SceneMaskQuality.PREVIEW_APPROXIMATE,
            tags = listOf(
                ContentTagDescriptor(
                    tagId = "scene-food",
                    label = "Food",
                    family = ContentTagFamily.SCENE,
                    confidence = 0.91f,
                    backendId = "preview-content"
                )
            )
        )

        val notes = ContentUnderstandingPipelineNotes.savedPhotoAdaptationDecisionNotes(snapshot)

        assertTrue(notes.contains("content-understanding:adaptation-ready=false"))
        assertTrue(notes.contains("content-understanding:adaptation-not-ready-quality=preview_approximate"))
    }

    @Test
    fun `adaptation decision notes explain unavailable content reasons`() {
        val snapshot = ContentUnderstandingSnapshot.unavailable(
            timestampMillis = 6502L,
            backendId = "none",
            reason = "no-recognition-backend"
        )

        val notes = ContentUnderstandingPipelineNotes.savedPhotoAdaptationDecisionNotes(snapshot)

        assertTrue(notes.contains("content-understanding:adaptation-ready=false"))
        assertTrue(notes.contains("content-understanding:adaptation-not-ready-quality=unavailable"))
        assertTrue(notes.contains("content-understanding:adaptation-not-ready-reason=no-recognition-backend"))
    }

    @Test
    fun `scene mask roles map into content region roles`() {
        assertEquals(
            ContentRegionRole.PERSON_SUBJECT,
            SceneMaskRole.PERSON_SUBJECT.toContentRegionRole()
        )
        assertEquals(
            ContentRegionRole.FOREGROUND,
            SceneMaskRole.FOREGROUND.toContentRegionRole()
        )
        assertEquals(
            ContentRegionRole.BACKGROUND,
            SceneMaskRole.BACKGROUND.toContentRegionRole()
        )
        assertEquals(
            ContentRegionRole.DEPTH_APPROXIMATION,
            SceneMaskRole.DEPTH_APPROXIMATION.toContentRegionRole()
        )
        assertEquals(
            ContentRegionRole.SEMANTIC_REGION,
            SceneMaskRole.SEMANTIC_REGION.toContentRegionRole()
        )
    }

    private fun contentRegion(
        role: ContentRegionRole,
        confidence: Float,
        regionId: String = "region-${role.name.lowercase()}",
        quality: SceneMaskQuality = SceneMaskQuality.SAVED_PHOTO,
        bounds: ContentRegionBounds? = null
    ): ContentRegionDescriptor = ContentRegionDescriptor(
        regionId = regionId,
        role = role,
        quality = quality,
        backendId = "semantic-regions",
        confidence = confidence,
        transform = SceneMaskTransform(
            sourceWidth = 4000,
            sourceHeight = 3000,
            maskWidth = 4000,
            maskHeight = 3000,
            rotationDegrees = 0
        ),
        bounds = bounds
    )
}
