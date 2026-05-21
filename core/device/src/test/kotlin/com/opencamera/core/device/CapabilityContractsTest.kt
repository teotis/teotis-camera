package com.opencamera.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityContractsTest {

    private val sampleRequirement = CapabilityRequirement(
        id = "test-feature",
        kind = CapabilityRequirementKind.STILL_CAPTURE,
        requiredFor = setOf(CapabilityUseSite.CAPTURE)
    )

    @Test
    fun `CapabilitySupport has five values`() {
        assertEquals(5, CapabilitySupport.entries.size)
    }

    @Test
    fun `SUPPORTED is applied`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.SUPPORTED,
            reason = "OK"
        )
        assertTrue(resolution.isApplied)
    }

    @Test
    fun `DEGRADED is applied`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.DEGRADED,
            reason = "fallback"
        )
        assertTrue(resolution.isApplied)
    }

    @Test
    fun `SAVED_ONLY is not applied`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.SAVED_ONLY,
            reason = "draft only"
        )
        assertFalse(resolution.isApplied)
    }

    @Test
    fun `PREVIEW_ONLY is not applied`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.PREVIEW_ONLY,
            reason = "preview only"
        )
        assertFalse(resolution.isApplied)
    }

    @Test
    fun `UNSUPPORTED is not applied`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.UNSUPPORTED,
            reason = "not available"
        )
        assertFalse(resolution.isApplied)
    }

    @Test
    fun `pipelineNote format includes id and support tag`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.DEGRADED,
            reason = "fallback"
        )
        assertEquals("capability:test-feature=degraded", resolution.pipelineNote)
    }

    @Test
    fun `pipelineNote includes fallback when selected`() {
        val resolution = CapabilityResolution(
            requirement = sampleRequirement,
            support = CapabilitySupport.DEGRADED,
            reason = "fallback",
            selectedFallbackId = "single-frame"
        )
        assertEquals("capability:test-feature=degraded:single-frame", resolution.pipelineNote)
    }

    @Test
    fun `empty report has no pipeline notes`() {
        val report = CapabilityGraphReport(
            featureId = "test",
            requested = emptyList(),
            resolved = emptyList()
        )
        assertTrue(report.pipelineNotes.isEmpty())
        assertTrue(report.allApplied)
        assertFalse(report.hasUnsupported)
    }

    @Test
    fun `allApplied returns true when all resolved are SUPPORTED or DEGRADED`() {
        val r1 = CapabilityResolution(sampleRequirement, CapabilitySupport.SUPPORTED, "OK")
        val r2 = CapabilityResolution(
            sampleRequirement.copy(id = "other"),
            CapabilitySupport.DEGRADED,
            "fallback"
        )
        val report = CapabilityGraphReport("test", emptyList(), listOf(r1, r2))
        assertTrue(report.allApplied)
    }

    @Test
    fun `allApplied returns false when any resolved is SAVED_ONLY`() {
        val r1 = CapabilityResolution(sampleRequirement, CapabilitySupport.SUPPORTED, "OK")
        val r2 = CapabilityResolution(
            sampleRequirement.copy(id = "other"),
            CapabilitySupport.SAVED_ONLY,
            "draft"
        )
        val report = CapabilityGraphReport("test", emptyList(), listOf(r1, r2))
        assertFalse(report.allApplied)
    }

    @Test
    fun `allApplied returns false when any resolved is UNSUPPORTED`() {
        val r1 = CapabilityResolution(sampleRequirement, CapabilitySupport.SUPPORTED, "OK")
        val r2 = CapabilityResolution(
            sampleRequirement.copy(id = "other"),
            CapabilitySupport.UNSUPPORTED,
            "not available"
        )
        val report = CapabilityGraphReport("test", emptyList(), listOf(r1, r2))
        assertFalse(report.allApplied)
    }

    @Test
    fun `hasUnsupported returns true when any resolved is UNSUPPORTED`() {
        val r1 = CapabilityResolution(sampleRequirement, CapabilitySupport.SUPPORTED, "OK")
        val r2 = CapabilityResolution(
            sampleRequirement.copy(id = "other"),
            CapabilitySupport.UNSUPPORTED,
            "not available"
        )
        val report = CapabilityGraphReport("test", emptyList(), listOf(r1, r2))
        assertTrue(report.hasUnsupported)
    }

    @Test
    fun `resolutionFor finds by id`() {
        val r1 = CapabilityResolution(sampleRequirement, CapabilitySupport.SUPPORTED, "OK")
        val r2 = CapabilityResolution(
            sampleRequirement.copy(id = "other"),
            CapabilitySupport.DEGRADED,
            "fallback"
        )
        val report = CapabilityGraphReport("test", emptyList(), listOf(r1, r2))
        assertNotNull(report.resolutionFor("test-feature"))
        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("test-feature")?.support)
    }

    @Test
    fun `resolutionFor returns null for missing id`() {
        val r1 = CapabilityResolution(sampleRequirement, CapabilitySupport.SUPPORTED, "OK")
        val report = CapabilityGraphReport("test", emptyList(), listOf(r1))
        assertNull(report.resolutionFor("nonexistent"))
    }
}
