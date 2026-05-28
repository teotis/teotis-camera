package com.opencamera.core.mode

import com.opencamera.core.capability.CapabilityRequirementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModeCapabilityDegradationTest {

    @Test
    fun `night declaration describes multi-frame to single-frame degradation`() {
        val declaration = ModeId.NIGHT.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.MULTI_FRAME_CAPTURE }

        assertTrue(req.degradationDescription.contains("single-frame", ignoreCase = true))
        assertEquals("night-single-frame", req.fallbackId)

        val fallbackVariant = declaration.strategyVariants.first { it.id == req.fallbackId }
        assertEquals(ModeStrategyType.SINGLE_FRAME, fallbackVariant.type)
    }

    @Test
    fun `portrait declaration describes depth to focus degradation`() {
        val declaration = ModeId.PORTRAIT.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.PORTRAIT_SEGMENTATION }

        assertTrue(req.degradationDescription.contains("focus", ignoreCase = true))
        assertEquals("portrait-focus", req.fallbackId)

        val fallbackVariant = declaration.strategyVariants.first { it.id == req.fallbackId }
        assertEquals(ModeStrategyType.SINGLE_FRAME, fallbackVariant.type)
    }

    @Test
    fun `document declaration describes enhanced to basic degradation`() {
        val declaration = ModeId.DOCUMENT.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.DOCUMENT_GEOMETRY }

        assertTrue(req.degradationDescription.contains("basic", ignoreCase = true))
        assertEquals("document-basic", req.fallbackId)

        val fallbackVariant = declaration.strategyVariants.first { it.id == req.fallbackId }
        assertEquals(ModeStrategyType.SINGLE_FRAME, fallbackVariant.type)
    }

    @Test
    fun `pro declaration describes manual to assisted degradation`() {
        val declaration = ModeId.PRO.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.MANUAL_CONTROL }

        assertTrue(req.degradationDescription.contains("assisted", ignoreCase = true))
        assertEquals("pro-assisted", req.fallbackId)

        val fallbackVariant = declaration.strategyVariants.first { it.id == req.fallbackId }
        assertEquals(ModeStrategyType.SINGLE_FRAME, fallbackVariant.type)
    }

    @Test
    fun `photo live photo degradation to still-only is described`() {
        val declaration = ModeId.PHOTO.modeProductDeclaration()
        val motionReq = declaration.requirements.firstOrNull { it.kind == CapabilityRequirementKind.TEMPORAL_RING_BUFFER }

        assertNotNull(motionReq)
        assertTrue(motionReq.degradationDescription.contains("still-only", ignoreCase = true))
        assertTrue(motionReq.isOptional)
    }

    @Test
    fun `all required requirements have fallback variants`() {
        ModeId.entries.forEach { modeId ->
            val declaration = modeId.modeProductDeclaration()
            declaration.requirements.filter { !it.isOptional }.forEach { req ->
                val fallbackId = req.fallbackId
                    ?: error("${modeId} required requirement '${req.id}' has no fallbackId")
                val fallbackVariant = declaration.strategyVariants.firstOrNull { it.id == fallbackId }
                    ?: error("${modeId} fallback '$fallbackId' has no matching strategy variant")
                assertFalse(
                    fallbackVariant.requiredCapabilityIds.contains(req.id),
                    "${modeId} fallback '$fallbackId' should not require the capability it falls back from"
                )
            }
        }
    }

    @Test
    fun `all strategy variants reference valid capability ids`() {
        ModeId.entries.forEach { modeId ->
            val declaration = modeId.modeProductDeclaration()
            val requirementIds = declaration.requirements.map { it.id }.toSet()
            declaration.strategyVariants.forEach { variant ->
                variant.requiredCapabilityIds.forEach { capId ->
                    assertTrue(
                        requirementIds.contains(capId),
                        "${modeId} variant '${variant.id}' references unknown capability '$capId'"
                    )
                }
            }
        }
    }
}
