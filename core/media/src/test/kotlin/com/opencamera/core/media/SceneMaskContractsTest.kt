package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneMaskContractsTest {

    private fun sampleTransform(
        sourceWidth: Int = 4000,
        sourceHeight: Int = 3000,
        maskWidth: Int = 256,
        maskHeight: Int = 192,
        rotationDegrees: Int = 0,
        mirrorHorizontally: Boolean = false,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
    ) = SceneMaskTransform(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        maskWidth = maskWidth,
        maskHeight = maskHeight,
        rotationDegrees = rotationDegrees,
        mirrorHorizontally = mirrorHorizontally,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom
    )

    private fun sampleDescriptor(
        maskId: String = "mask-001",
        role: SceneMaskRole = SceneMaskRole.PERSON_SUBJECT,
        quality: SceneMaskQuality = SceneMaskQuality.PREVIEW_APPROXIMATE,
        backendId: String = "mlkit-selfie",
        confidence: Float = 0.85f,
        transform: SceneMaskTransform = sampleTransform(),
        diagnostics: List<String> = emptyList()
    ) = SceneMaskDescriptor(
        maskId = maskId,
        role = role,
        quality = quality,
        backendId = backendId,
        confidence = confidence,
        transform = transform,
        diagnostics = diagnostics
    )

    @Test
    fun `descriptor metadata round trip preserves all fields`() {
        val original = sampleDescriptor(
            diagnostics = listOf("low-light", "partial-occlusion")
        )

        val tags = original.toMetadataTags()
        val restored = SceneMaskDescriptor.fromMetadataTags(tags)

        assertNotNull(restored)
        assertEquals(original.maskId, restored.maskId)
        assertEquals(original.role, restored.role)
        assertEquals(original.quality, restored.quality)
        assertEquals(original.backendId, restored.backendId)
        assertEquals(original.normalizedConfidence(), restored.confidence)
        assertEquals(original.transform.sourceWidth, restored.transform.sourceWidth)
        assertEquals(original.transform.sourceHeight, restored.transform.sourceHeight)
        assertEquals(original.transform.maskWidth, restored.transform.maskWidth)
        assertEquals(original.transform.maskHeight, restored.transform.maskHeight)
        assertEquals(original.transform.rotationDegrees, restored.transform.rotationDegrees)
        assertEquals(original.transform.mirrorHorizontally, restored.transform.mirrorHorizontally)
        assertEquals(original.diagnostics, restored.diagnostics)
    }

    @Test
    fun `normalized confidence clamps values above 1`() {
        val descriptor = sampleDescriptor(confidence = 1.5f)
        assertEquals(1f, descriptor.normalizedConfidence())
    }

    @Test
    fun `normalized confidence clamps values below 0`() {
        val descriptor = sampleDescriptor(confidence = -0.3f)
        assertEquals(0f, descriptor.normalizedConfidence())
    }

    @Test
    fun `normalized confidence preserves values in range`() {
        val descriptor = sampleDescriptor(confidence = 0.72f)
        assertEquals(0.72f, descriptor.normalizedConfidence())
    }

    @Test
    fun `transform values preserve crop and rotation through round trip`() {
        val transform = sampleTransform(
            rotationDegrees = 90,
            mirrorHorizontally = true,
            cropLeft = 0.1f,
            cropTop = 0.2f,
            cropRight = 0.9f,
            cropBottom = 0.8f
        )
        val descriptor = sampleDescriptor(transform = transform)

        val tags = descriptor.toMetadataTags()
        val restored = SceneMaskDescriptor.fromMetadataTags(tags)

        assertNotNull(restored)
        assertEquals(90, restored.transform.rotationDegrees)
        assertTrue(restored.transform.mirrorHorizontally)
    }

    @Test
    fun `fromMetadataTags returns null when required field missing`() {
        val incompleteTags = mapOf(
            "scene-mask:id" to "mask-001",
            "scene-mask:role" to "PERSON_SUBJECT"
        )
        assertNull(SceneMaskDescriptor.fromMetadataTags(incompleteTags))
    }

    @Test
    fun `fromMetadataTags returns null for unknown role`() {
        val tags = mapOf(
            "scene-mask:id" to "mask-001",
            "scene-mask:role" to "UNKNOWN_ROLE",
            "scene-mask:quality" to "PREVIEW_APPROXIMATE",
            "scene-mask:backend" to "mlkit-selfie",
            "scene-mask:confidence" to "0.8",
            "scene-mask:transform" to "4000x3000>256x192:rot0"
        )
        assertNull(SceneMaskDescriptor.fromMetadataTags(tags))
    }

    @Test
    fun `unsupported capability can be represented without a backend`() {
        val capability = SceneMaskCapability(
            subjectMask = SceneMaskSupport.UNSUPPORTED,
            savedPhotoMask = SceneMaskSupport.UNSUPPORTED,
            previewMask = SceneMaskSupport.UNSUPPORTED,
            backendId = "none",
            reason = "No segmentation backend available"
        )

        assertEquals(SceneMaskSupport.UNSUPPORTED, capability.subjectMask)
        assertEquals(SceneMaskSupport.UNSUPPORTED, capability.savedPhotoMask)
        assertEquals(SceneMaskSupport.UNSUPPORTED, capability.previewMask)
    }

    @Test
    fun `degraded capability can be represented without a backend`() {
        val capability = SceneMaskCapability(
            subjectMask = SceneMaskSupport.DEGRADED,
            savedPhotoMask = SceneMaskSupport.UNSUPPORTED,
            previewMask = SceneMaskSupport.DEGRADED,
            backendId = "fallback-edge",
            reason = "GPU acceleration unavailable"
        )

        assertEquals(SceneMaskSupport.DEGRADED, capability.subjectMask)
        assertEquals(SceneMaskSupport.UNSUPPORTED, capability.savedPhotoMask)
        assertEquals(SceneMaskSupport.DEGRADED, capability.previewMask)
    }

    @Test
    fun `pipeline note for preview unsupported uses correct format`() {
        val note = SceneMaskPipelineNotes.preview(SceneMaskSupport.UNSUPPORTED)
        assertEquals("scene-mask:preview=unsupported", note)
    }

    @Test
    fun `pipeline note for saved photo applied uses correct format`() {
        val note = SceneMaskPipelineNotes.saved(SceneMaskSupport.SUPPORTED)
        assertEquals("scene-mask:saved=applied", note)
    }

    @Test
    fun `pipeline note for saved photo degraded uses correct format`() {
        val note = SceneMaskPipelineNotes.saved(SceneMaskSupport.DEGRADED)
        assertEquals("scene-mask:saved=degraded", note)
    }

    @Test
    fun `capability notes include all expected entries`() {
        val capability = SceneMaskCapability(
            subjectMask = SceneMaskSupport.SUPPORTED,
            savedPhotoMask = SceneMaskSupport.DEGRADED,
            previewMask = SceneMaskSupport.SUPPORTED,
            backendId = "mlkit-selfie",
            reason = "Low memory"
        )

        val notes = SceneMaskPipelineNotes.capabilityNotes(capability)

        assertTrue(notes.contains("scene-mask:backend=mlkit-selfie"))
        assertTrue(notes.contains("scene-mask:preview=applied"))
        assertTrue(notes.contains("scene-mask:saved=degraded"))
        assertTrue(notes.contains("scene-mask:reason=Low memory"))
    }

    @Test
    fun `capability notes omit reason when null`() {
        val capability = SceneMaskCapability(
            subjectMask = SceneMaskSupport.SUPPORTED,
            savedPhotoMask = SceneMaskSupport.SUPPORTED,
            previewMask = SceneMaskSupport.SUPPORTED,
            backendId = "mlkit-selfie"
        )

        val notes = SceneMaskPipelineNotes.capabilityNotes(capability)

        assertTrue(notes.none { it.startsWith("scene-mask:reason=") })
    }
}
