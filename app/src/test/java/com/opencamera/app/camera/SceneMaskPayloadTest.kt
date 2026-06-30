package com.opencamera.app.camera

import com.opencamera.core.media.ContentRegionRole
import com.opencamera.core.media.SceneMaskQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneMaskPayloadTest {
    @Test
    fun `sample alpha returns 0 for out of bounds`() {
        val mask = SceneMaskTestUtils.createUniformMask(10, 10, 0.8f)
        assertEquals(0f, mask.sampleAlpha(-1, 0))
        assertEquals(0f, mask.sampleAlpha(0, -1))
        assertEquals(0f, mask.sampleAlpha(10, 0))
        assertEquals(0f, mask.sampleAlpha(0, 10))
    }

    @Test
    fun `sample alpha returns correct value for uniform mask`() {
        val mask = SceneMaskTestUtils.createUniformMask(10, 10, 0.75f)
        val alpha = mask.sampleAlpha(5, 5)
        assertEquals(0.75f, alpha, 0.01f)
    }

    @Test
    fun `center subject mask has high alpha in center`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val centerAlpha = mask.sampleAlpha(50, 40)
        assertTrue(centerAlpha > 0.8f, "Center alpha should be high, was $centerAlpha")
    }

    @Test
    fun `center subject mask has low alpha at corners`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val cornerAlpha = mask.sampleAlpha(0, 0)
        assertTrue(cornerAlpha < 0.2f, "Corner alpha should be low, was $cornerAlpha")
    }

    @Test
    fun `left right split mask has correct boundaries`() {
        val mask = SceneMaskTestUtils.createLeftRightSplitMask(100, 50, 0.5f)
        assertTrue(mask.sampleAlpha(25, 25) > 0.9f)
        assertTrue(mask.sampleAlpha(75, 25) < 0.1f)
    }

    @Test
    fun `scene mask coordinate mapper maps coordinates correctly`() {
        val mapper = SceneMaskCoordinateMapper(
            maskWidth = 50,
            maskHeight = 50,
            targetWidth = 100,
            targetHeight = 100
        )
        assertEquals(25, mapper.maskX(50))
        assertEquals(25, mapper.maskY(50))
        assertEquals(0, mapper.maskX(0))
        assertEquals(0, mapper.maskY(0))
    }

    @Test
    fun `scene mask coordinate mapper clamps to mask bounds`() {
        val mapper = SceneMaskCoordinateMapper(
            maskWidth = 50,
            maskHeight = 50,
            targetWidth = 100,
            targetHeight = 100
        )
        assertEquals(49, mapper.maskX(100))
        assertEquals(49, mapper.maskY(100))
    }

    @Test
    fun `scene mask coordinate mapper handles different aspect ratios`() {
        val mapper = SceneMaskCoordinateMapper(
            maskWidth = 200,
            maskHeight = 100,
            targetWidth = 400,
            targetHeight = 200
        )
        assertEquals(100, mapper.maskX(200))
        assertEquals(50, mapper.maskY(100))
    }

    @Test
    fun `mask with edge softness has smooth transition`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(
            100, 100,
            subjectFractionX = 0.25f,
            subjectFractionY = 0.5f,
            edgeSoftness = 0.8f
        )
        val centerAlpha = mask.sampleAlpha(50, 40)
        val edgeAlpha = mask.sampleAlpha(92, 40)
        val cornerAlpha = mask.sampleAlpha(0, 0)

        assertTrue(centerAlpha > edgeAlpha, "Center should be higher than edge, center=$centerAlpha edge=$edgeAlpha")
        assertTrue(edgeAlpha > cornerAlpha, "Edge should be higher than corner, edge=$edgeAlpha corner=$cornerAlpha")
        assertTrue(edgeAlpha > 0.05f, "Edge should have some non-trivial alpha for smoothness, edge=$edgeAlpha")
        assertTrue(edgeAlpha < 0.95f, "Edge should not be fully opaque, edge=$edgeAlpha")
    }

    @Test
    fun `saved photo mask exports content understanding snapshot`() {
        val mask = SceneMaskTestUtils.createUniformMask(16, 12, 0.8f)

        val snapshot = mask.toContentUnderstandingSnapshot(
            maskId = "saved-mask-1",
            sourceWidth = 4000,
            sourceHeight = 3000,
            timestampMillis = 2000L
        )

        assertTrue(snapshot.isAvailable)
        assertEquals(SceneMaskQuality.SAVED_PHOTO, snapshot.quality)
        assertEquals("mlkit-selfie", snapshot.backendId)
        assertTrue(snapshot.hasRegion(ContentRegionRole.PERSON_SUBJECT))
        val subject = snapshot.primaryRegion(ContentRegionRole.PERSON_SUBJECT)
        assertEquals("saved-mask-1", subject?.regionId)
        assertEquals(16, subject?.transform?.maskWidth)
        assertEquals(12, subject?.transform?.maskHeight)
        assertEquals(4000, subject?.transform?.sourceWidth)
        assertEquals(3000, subject?.transform?.sourceHeight)
    }
}
