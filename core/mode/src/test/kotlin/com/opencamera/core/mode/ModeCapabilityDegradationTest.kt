package com.opencamera.core.mode

import com.opencamera.core.capability.CapabilityRequirementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModeCapabilityDegradationTest {

    @Test
    fun `checkin declaration describes multi-frame to best-frame degradation`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.MULTI_FRAME_CAPTURE }

        assertTrue(req.degradationDescription.contains("single-frame", ignoreCase = true))
        assertEquals("checkin-best-frame", req.fallbackId)

        val fallbackVariant = declaration.strategyVariants.first { it.id == req.fallbackId }
        assertEquals(ModeStrategyType.SINGLE_FRAME, fallbackVariant.type)
    }

    @Test
    fun `checkin declaration describes depth segmentation to focus degradation`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.PORTRAIT_SEGMENTATION }

        assertTrue(req.degradationDescription.contains("focus", ignoreCase = true))
        assertEquals("checkin-focus", req.fallbackId)

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
    fun `humanistic declaration describes manual to auto degradation`() {
        val declaration = ModeId.HUMANISTIC.modeProductDeclaration()
        val req = declaration.requirements.first { it.kind == CapabilityRequirementKind.MANUAL_CONTROL }

        assertTrue(req.degradationDescription.contains("assisted", ignoreCase = true))
        assertEquals("humanistic-auto", req.fallbackId)

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

    @Test
    fun `checkin focus stack degradation describes honest best-frame fallback`() {
        val declaration = ModeId.CHECK_IN.modeProductDeclaration()
        val stackReq = declaration.requirements.firstOrNull { it.id == "checkin-focus-stack" }

        assertNotNull(stackReq)
        assertTrue(stackReq.isOptional)
        assertEquals("checkin-best-frame", stackReq.fallbackId)
    }
}
