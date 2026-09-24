package com.opencamera.app.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraDevelopmentAssessmentTest {

    @Test
    fun `flagship camera facts produce actionable development opportunities`() {
        val report = assessCameraDevelopmentFeatures(
            CameraDevelopmentFacts(
                hardwareLevel = "LEVEL_3",
                physicalCameraCount = 3,
                supportsManualSensor = true,
                supportsManualPostProcessing = true,
                supportsRaw = true,
                supportsBurst = true,
                supportsYuvReprocessing = true,
                supportsPrivateReprocessing = true,
                supportsRemosaicReprocessing = true,
                supportsDepth = true,
                supportsHighSpeedVideo = true,
                supportsTenBitDynamicRange = true,
                supportsUltraHighResolution = true,
                supportsStreamUseCases = true,
                supportsOfflineProcessing = true,
                hasIsoRange = true,
                hasExposureTimeRange = true,
                hasManualFocusDistance = true,
                hasRawOutput = true,
                hasYuvOutput = true,
                hasHighResolutionJpeg = true,
                hasOpticalStabilization = true,
                hasVideoStabilization = true,
                vendorRequestKeyCount = 12,
                vendorResultKeyCount = 18,
                physicalRequestKeyCount = 4
            )
        )

        assertEquals(CameraDevelopmentReadiness.READY, report.first { it.id == "manual-exposure" }.readiness)
        assertEquals(CameraDevelopmentReadiness.READY, report.first { it.id == "manual-focus" }.readiness)
        assertEquals(CameraDevelopmentReadiness.READY, report.first { it.id == "raw-pipeline" }.readiness)
        assertEquals(CameraDevelopmentReadiness.READY, report.first { it.id == "multi-frame-yuv" }.readiness)
        assertEquals(CameraDevelopmentReadiness.VERIFY_ON_DEVICE, report.first { it.id == "logical-multi-camera" }.readiness)
        assertEquals(CameraDevelopmentReadiness.VERIFY_ON_DEVICE, report.first { it.id == "vendor-controls" }.readiness)
        assertTrue(report.first { it.id == "ultra-high-resolution" }.evidence.contains("remosaic"))
        assertTrue(report.first { it.id == "ten-bit-video" }.use.contains("HDR"))
    }

    @Test
    fun `limited camera facts explain unavailable paths instead of overclaiming`() {
        val report = assessCameraDevelopmentFeatures(
            CameraDevelopmentFacts(
                hardwareLevel = "LIMITED",
                hasYuvOutput = true
            )
        )

        assertEquals(CameraDevelopmentReadiness.UNAVAILABLE, report.first { it.id == "manual-exposure" }.readiness)
        assertEquals(CameraDevelopmentReadiness.UNAVAILABLE, report.first { it.id == "manual-focus" }.readiness)
        assertEquals(CameraDevelopmentReadiness.UNAVAILABLE, report.first { it.id == "raw-pipeline" }.readiness)
        assertEquals(CameraDevelopmentReadiness.VERIFY_ON_DEVICE, report.first { it.id == "multi-frame-yuv" }.readiness)
        assertEquals(CameraDevelopmentReadiness.UNAVAILABLE, report.first { it.id == "vendor-controls" }.readiness)
    }

    @Test
    fun `assessment text separates readiness evidence and engineering use`() {
        val text = formatCameraDevelopmentAssessment(
            listOf(
                CameraDevelopmentOpportunity(
                    id = "manual-exposure",
                    title = "手动曝光",
                    readiness = CameraDevelopmentReadiness.READY,
                    evidence = "MANUAL_SENSOR + ISO/曝光范围",
                    use = "可驱动专业模式"
                )
            )
        )

        assertTrue(text.contains("[development-opportunities]"))
        assertTrue(text.contains("READY 手动曝光"))
        assertTrue(text.contains("evidence: MANUAL_SENSOR + ISO/曝光范围"))
        assertTrue(text.contains("use: 可驱动专业模式"))
        assertTrue(text.contains("READY=平台证据充分"))
    }
}
