package com.opencamera.core.mode

import com.opencamera.core.capability.CapabilityRequirementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModeProductDeclarationTest {

    @Test
    fun `all nine mode ids have product declarations`() {
        ModeId.entries.forEach { modeId ->
            val declaration = modeId.modeProductDeclaration()
            assertEquals(modeId, declaration.modeId)
            assertTrue(declaration.displayName.isNotBlank())
            assertTrue(declaration.requirements.isNotEmpty())
            assertTrue(declaration.strategyVariants.isNotEmpty())
        }
    }

    @Test
    fun `photo declaration gates on still capture`() {
        val declaration = ModeId.PHOTO.modeProductDeclaration()
        assertEquals(CapabilityRequirementKind.STILL_CAPTURE, declaration.primaryGate.kind)
    }

    @Test
    fun `checkin declaration gates on still capture`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        assertEquals(CapabilityRequirementKind.STILL_CAPTURE, declaration.primaryGate.kind)
    }

    @Test
    fun `humanistic declaration gates on still capture`() {
        val declaration = ModeId.HUMANISTIC.modeProductDeclaration()
        assertEquals(CapabilityRequirementKind.STILL_CAPTURE, declaration.primaryGate.kind)
    }

    @Test
    fun `document declaration gates on still capture`() {
        val declaration = ModeId.DOCUMENT.modeProductDeclaration()
        assertEquals(CapabilityRequirementKind.STILL_CAPTURE, declaration.primaryGate.kind)
    }

    @Test
    fun `video declaration gates on video recording`() {
        val declaration = ModeId.VIDEO.modeProductDeclaration()
        assertEquals(CapabilityRequirementKind.VIDEO_RECORDING, declaration.primaryGate.kind)
    }

    @Test
    fun `checkin declaration requires multi-frame capture with best-frame fallback`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        val multiFrameReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.MULTI_FRAME_CAPTURE }
        assertFalse(multiFrameReq.isOptional)
        assertEquals("checkin-best-frame", multiFrameReq.fallbackId)
    }

    @Test
    fun `checkin declaration requires portrait segmentation with focus fallback`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        val segReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.PORTRAIT_SEGMENTATION }
        assertFalse(segReq.isOptional)
        assertEquals("checkin-focus", segReq.fallbackId)
    }

    @Test
    fun `document declaration has document geometry requirement with basic fallback`() {
        val declaration = ModeId.DOCUMENT.modeProductDeclaration()
        val geoReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.DOCUMENT_GEOMETRY }
        assertTrue(geoReq.isOptional)
        assertEquals("document-basic", geoReq.fallbackId)
    }

    @Test
    fun `humanistic declaration requires manual control with auto fallback`() {
        val declaration = ModeId.HUMANISTIC.modeProductDeclaration()
        val manualReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.MANUAL_CONTROL }
        assertFalse(manualReq.isOptional)
        assertEquals("humanistic-auto", manualReq.fallbackId)
    }

    @Test
    fun `photo declaration includes live photo strategy variant`() {
        val declaration = ModeId.PHOTO.modeProductDeclaration()
        val liveVariant = declaration.strategyVariants.firstOrNull { it.type == ModeStrategyType.LIVE_PHOTO }
        assertTrue(liveVariant != null)
        assertEquals("photo-live", liveVariant.id)
    }

    @Test
    fun `checkin declaration includes multi-frame and single-frame variants`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        assertTrue(declaration.strategyVariants.any { it.type == ModeStrategyType.MULTI_FRAME })
        assertTrue(declaration.strategyVariants.any { it.type == ModeStrategyType.SINGLE_FRAME })
    }

    @Test
    fun `checkin declaration effect profile includes portrait and frame effects`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        assertTrue(declaration.effectProfile.usesPortraitEffect)
        assertTrue(declaration.effectProfile.usesFilter)
        assertTrue(declaration.effectProfile.usesFrameEffect)
        assertTrue(declaration.effectProfile.usesWatermark)
    }

    @Test
    fun `document declaration effect profile includes document effect`() {
        val declaration = ModeId.DOCUMENT.modeProductDeclaration()
        assertTrue(declaration.effectProfile.usesDocumentEffect)
        assertFalse(declaration.effectProfile.usesFilter)
        assertFalse(declaration.effectProfile.usesFrameEffect)
    }

    @Test
    fun `humanistic declaration effect profile includes filter and frame but not portrait`() {
        val declaration = ModeId.HUMANISTIC.modeProductDeclaration()
        assertTrue(declaration.effectProfile.usesFilter)
        assertFalse(declaration.effectProfile.usesPortraitEffect)
        assertTrue(declaration.effectProfile.usesFrameEffect)
    }

    @Test
    fun `video declaration strategy type is video recording`() {
        val declaration = ModeId.VIDEO.modeProductDeclaration()
        assertEquals(1, declaration.strategyVariants.size)
        assertEquals(ModeStrategyType.VIDEO_RECORDING, declaration.strategyVariants.first().type)
    }

    @Test
    fun `photo filter and watermark requirements are optional`() {
        val declaration = ModeId.PHOTO.modeProductDeclaration()
        val filterReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.FILTER_CAPTURE_RENDER }
        val watermarkReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.WATERMARK_RENDER }
        assertTrue(filterReq.isOptional)
        assertTrue(watermarkReq.isOptional)
    }

    @Test
    fun `checkin multi-frame requirement is not optional`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        val multiFrameReq = declaration.requirements.first { it.kind == CapabilityRequirementKind.MULTI_FRAME_CAPTURE }
        assertFalse(multiFrameReq.isOptional)
    }

    @Test
    fun `mode ids only include canonical product modes`() {
        val canonicalModes = ModeId.entries
        assertEquals(5, canonicalModes.size)
        assertEquals(
            listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.DOCUMENT, ModeId.HUMANISTIC, ModeId.VIDEO),
            canonicalModes
        )
    }
}
