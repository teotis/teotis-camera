package com.opencamera.app.camera

import com.opencamera.core.media.SceneMaskPayload
import com.opencamera.core.media.SceneMaskSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SceneMaskTypeCollisionTest {

    @Test
    fun `SavedPhotoMaskPixels implements core SceneMaskPayload`() {
        val mask = SceneMaskTestUtils.createUniformMask(8, 8, 0.6f)
        assertTrue(mask is SceneMaskPayload, "SavedPhotoMaskPixels must implement core SceneMaskPayload")
    }

    @Test
    fun `core SceneMaskPayload and app SavedPhotoMaskPixels are distinct types`() {
        val coreClass = SceneMaskPayload::class.java
        val appClass = SavedPhotoMaskPixels::class.java
        assertNotEquals<Any>(coreClass, appClass, "Core interface and app data class must be different types")
    }

    @Test
    fun `core SceneMaskCapability and app PreviewSceneMaskCapability are distinct types`() {
        val coreClass = com.opencamera.core.media.SceneMaskCapability::class.java
        val appClass = PreviewSceneMaskCapability::class.java
        assertNotEquals<Any>(coreClass, appClass, "Core data class and app enum must be different types")
    }

    @Test
    fun `PreviewSceneMaskCapability toCoreSupport maps READY to SUPPORTED`() {
        assertEquals(SceneMaskSupport.SUPPORTED, PreviewSceneMaskCapability.READY.toCoreSupport())
    }

    @Test
    fun `PreviewSceneMaskCapability toCoreSupport maps DEGRADED to DEGRADED`() {
        assertEquals(SceneMaskSupport.DEGRADED, PreviewSceneMaskCapability.DEGRADED.toCoreSupport())
    }

    @Test
    fun `PreviewSceneMaskCapability toCoreSupport maps UNSUPPORTED to UNSUPPORTED`() {
        assertEquals(SceneMaskSupport.UNSUPPORTED, PreviewSceneMaskCapability.UNSUPPORTED.toCoreSupport())
    }

    @Test
    fun `SavedPhotoMaskPixels alphaAt delegates to sampleAlpha`() {
        val mask = SceneMaskTestUtils.createUniformMask(10, 10, 0.7f)
        assertEquals(mask.sampleAlpha(5, 5), mask.alphaAt(5, 5))
        assertEquals(0f, mask.alphaAt(-1, 0))
    }

    @Test
    fun `SavedPhotoMaskPixels descriptor has correct quality`() {
        val mask = SceneMaskTestUtils.createUniformMask(10, 10, 0.7f)
        assertEquals(com.opencamera.core.media.SceneMaskQuality.SAVED_PHOTO, mask.descriptor.quality)
    }

    @Test
    fun `SavedPhotoMaskPixels toDescriptor with custom params`() {
        val mask = SceneMaskTestUtils.createUniformMask(10, 10, 0.7f)
        val descriptor = mask.toDescriptor("shot-123", 4000, 3000)
        assertEquals("shot-123", descriptor.maskId)
        assertEquals(4000, descriptor.transform.sourceWidth)
        assertEquals(3000, descriptor.transform.sourceHeight)
        assertEquals(10, descriptor.transform.maskWidth)
        assertEquals(10, descriptor.transform.maskHeight)
    }
}
