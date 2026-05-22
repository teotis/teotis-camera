package com.opencamera.core.device

import com.opencamera.core.media.CaptureNodeRole
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.primaryVideoNode
import com.opencamera.core.settings.ManualCaptureParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultDeviceShotRequestTranslatorTest {
    private val translator = DefaultDeviceShotRequestTranslator()
    private val executionPlanner = MultiFrameCaptureExecutionPlanner()

    @Test
    fun `single frame shot keeps latency first still request`() {
        val plan = ShotExecutor(idGenerator = { "shot-photo" }).plan(
            CaptureStrategy.SingleFrame()
        )

        val request = translator.translate(plan)

        assertEquals("shot-photo", request.shotId)
        assertEquals(CaptureTemplate.STILL_CAPTURE, request.template)
        assertEquals(StillCaptureQualityPreference.LATENCY, request.stillCaptureQuality)
        assertEquals(1, request.frameCount)
        assertEquals(0L, request.interFrameDelayMillis)
        assertTrue("device:capture-mode=minimize-latency" in request.diagnostics)
    }

    @Test
    fun `single frame shot forwards flash mode into device request`() {
        val plan = ShotExecutor(idGenerator = { "shot-photo-flash" }).plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    flashMode = FlashMode.AUTO
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals(FlashMode.AUTO, request.flashMode)
        assertTrue("device:flash=auto" in request.diagnostics)
    }

    @Test
    fun `single frame shot honors explicit still quality preference`() {
        val plan = ShotExecutor(idGenerator = { "shot-photo-quality" }).plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    stillCaptureQuality = StillCaptureQualityPreference.QUALITY
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals(StillCaptureQualityPreference.QUALITY, request.stillCaptureQuality)
        assertTrue("device:capture-mode=max-quality" in request.diagnostics)
    }

    @Test
    fun `single frame shot forwards explicit still resolution preset into diagnostics`() {
        val plan = ShotExecutor(idGenerator = { "shot-photo-resolution" }).plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
                )
            )
        )

        val request = translator.translate(plan)

        assertTrue("device:still-resolution=8mp" in request.diagnostics)
    }

    @Test
    fun `single frame shot forwards manual capture params into device request when supported`() {
        val requestedManualParams = ManualCaptureParams(
            rawEnabled = true,
            iso = 125,
            shutterSpeedMillis = 8L,
            exposureCompensationSteps = 2,
            focusDistanceDiopters = 1.5f,
            apertureFNumber = 1.8f,
            whiteBalanceKelvin = 5600
        )
        val plan = ShotExecutor(idGenerator = { "shot-photo-manual" }).plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    manualCaptureParams = requestedManualParams
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals(requestedManualParams, request.manualCaptureParams)
        assertEquals(
            ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT,
            request.manualControlCapabilities
        )
        assertTrue("device:manual=partial" in request.diagnostics)
        assertTrue("device:manual-raw=saved-only:on" in request.diagnostics)
        assertTrue("device:manual-iso=applied:125" in request.diagnostics)
        assertTrue("device:manual-shutter=applied:8ms" in request.diagnostics)
        assertTrue("device:manual-ev=applied:2" in request.diagnostics)
        assertTrue("device:manual-focus=applied:1.5" in request.diagnostics)
        assertTrue("device:manual-aperture=applied:1.8" in request.diagnostics)
        assertTrue("device:manual-wb=saved-only:5600K" in request.diagnostics)
    }

    @Test
    fun `unsupported flash control falls back to off with diagnostic`() {
        val unsupportedTranslator = DefaultDeviceShotRequestTranslator(
            deviceCapabilities = DeviceCapabilities(
                supportsFlashControl = false
            )
        )
        val plan = ShotExecutor(idGenerator = { "shot-photo-flash-unsupported" }).plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    flashMode = FlashMode.ON
                )
            )
        )

        val request = unsupportedTranslator.translate(plan)

        assertEquals(FlashMode.OFF, request.flashMode)
        assertTrue("device:flash=unsupported-fallback-off" in request.diagnostics)
    }

    @Test
    fun `saved only manual matrix keeps draft in device request for downstream diagnostics`() {
        val unsupportedTranslator = DefaultDeviceShotRequestTranslator(
            deviceCapabilities = DeviceCapabilities(
                supportsManualControls = false
            )
        )
        val plan = ShotExecutor(idGenerator = { "shot-photo-manual-unsupported" }).plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    manualCaptureParams = ManualCaptureParams(
                        rawEnabled = true,
                        iso = 200,
                        shutterSpeedMillis = 16L,
                        whiteBalanceKelvin = 4300
                    )
                )
            )
        )

        val request = unsupportedTranslator.translate(plan)

        assertEquals(200, request.manualCaptureParams?.iso)
        assertEquals(16L, request.manualCaptureParams?.shutterSpeedMillis)
        assertEquals(4300, request.manualCaptureParams?.whiteBalanceKelvin)
        assertEquals(
            ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT,
            request.manualControlCapabilities
        )
        assertTrue("device:manual=saved-only" in request.diagnostics)
        assertTrue("device:manual-iso=saved-only:200" in request.diagnostics)
        assertTrue("device:manual-wb=saved-only:4300K" in request.diagnostics)
    }

    @Test
    fun `multi frame shot becomes quality first still request with night diagnostics`() {
        val plan = ShotExecutor(idGenerator = { "shot-night" }).plan(
            CaptureStrategy.MultiFrame(
                postProcessSpec = PostProcessSpec(
                    algorithmProfile = "night-multiframe-tripod"
                ),
                captureProfile = CaptureProfile(
                    frameCount = 12,
                    longExposureMillis = 1400L,
                    requiresTripod = true
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals("shot-night", request.shotId)
        assertEquals(CaptureTemplate.STILL_CAPTURE, request.template)
        assertEquals(StillCaptureQualityPreference.QUALITY, request.stillCaptureQuality)
        assertEquals(12, request.frameCount)
        assertEquals(116L, request.interFrameDelayMillis)
        assertTrue("device:capture-mode=max-quality" in request.diagnostics)
        assertTrue("device:frame-count=12" in request.diagnostics)
        assertTrue("device:long-exposure=1400ms" in request.diagnostics)
        assertTrue("device:stability=tripod" in request.diagnostics)
    }

    @Test
    fun `multi frame shot can downgrade still capture quality when requested`() {
        val plan = ShotExecutor(idGenerator = { "shot-night-fast" }).plan(
            CaptureStrategy.MultiFrame(
                captureProfile = CaptureProfile(
                    frameCount = 8,
                    longExposureMillis = 800L,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals(StillCaptureQualityPreference.LATENCY, request.stillCaptureQuality)
        assertTrue("device:capture-mode=minimize-latency" in request.diagnostics)
    }

    @Test
    fun `multi frame shot keeps explicit still resolution preset in diagnostics`() {
        val plan = ShotExecutor(idGenerator = { "shot-night-resolution" }).plan(
            CaptureStrategy.MultiFrame(
                captureProfile = CaptureProfile(
                    frameCount = 8,
                    longExposureMillis = 800L,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.SMALL_2MP
                )
            )
        )

        val request = translator.translate(plan)

        assertTrue("device:still-resolution=2mp" in request.diagnostics)
    }

    @Test
    fun `video recording keeps video template translation`() {
        val plan = ShotExecutor(idGenerator = { "shot-video" }).plan(
            CaptureStrategy.VideoRecording(
                captureProfile = CaptureProfile(
                    torchEnabled = true
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals("shot-video", request.shotId)
        assertEquals(CaptureTemplate.VIDEO_RECORDING, request.template)
        assertEquals(null, request.stillCaptureQuality)
        assertTrue(request.torchEnabled)
        assertTrue("device:template=video-recording" in request.diagnostics)
        assertTrue("device:torch=on" in request.diagnostics)
    }

    @Test
    fun `video translation proves primary video graph node exists`() {
        val plan = ShotExecutor(idGenerator = { "shot-video-graph" }).plan(
            CaptureStrategy.VideoRecording()
        )

        assertTrue(plan.graph.primaryVideoNode() != null)

        val request = translator.translate(plan)

        assertEquals(CaptureTemplate.VIDEO_RECORDING, request.template)
        assertTrue("device:graph=capture-topology" in request.diagnostics)
    }

    @Test
    fun `multi frame translation uses graph temp frame count`() {
        val plan = ShotExecutor(idGenerator = { "shot-night-graph" }).plan(
            CaptureStrategy.MultiFrame(
                captureProfile = CaptureProfile(
                    frameCount = 8,
                    longExposureMillis = 1200L
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals(8, request.frameCount)
        assertTrue("device:graph=capture-topology" in request.diagnostics)
    }

    @Test
    fun `live photo keeps still template with live bundle diagnostics`() {
        val plan = ShotExecutor(idGenerator = { "shot-live" }).plan(
            CaptureStrategy.LivePhoto(
                captureProfile = CaptureProfile(
                    flashMode = FlashMode.AUTO
                ),
                livePhotoSpec = LivePhotoCaptureSpec(
                    motionDurationMillis = 1600,
                    sidecarMimeType = "application/vnd.opencamera.live+json"
                )
            )
        )

        val request = translator.translate(plan)

        assertEquals(CaptureTemplate.STILL_CAPTURE, request.template)
        assertEquals(com.opencamera.core.media.ShotKind.LIVE_PHOTO, request.shotKind)
        assertEquals(FlashMode.AUTO, request.flashMode)
        assertTrue("device:live-photo=bundle" in request.diagnostics)
        assertTrue("device:live-motion=1600ms" in request.diagnostics)
    }

    @Test
    fun `unsupported torch falls back to off with diagnostic`() {
        val unsupportedTranslator = DefaultDeviceShotRequestTranslator(
            deviceCapabilities = DeviceCapabilities(
                supportsFlashControl = false
            )
        )
        val plan = ShotExecutor(idGenerator = { "shot-video-torch-unsupported" }).plan(
            CaptureStrategy.VideoRecording(
                captureProfile = CaptureProfile(
                    torchEnabled = true
                )
            )
        )

        val request = unsupportedTranslator.translate(plan)

        assertEquals(false, request.torchEnabled)
        assertTrue("device:torch=unsupported-fallback-off" in request.diagnostics)
    }

    @Test
    fun `multi frame execution plan routes last frame to final output`() {
        val request = translator.translate(
            ShotExecutor(idGenerator = { "shot-night" }).plan(
                CaptureStrategy.MultiFrame(
                    captureProfile = CaptureProfile(
                        frameCount = 4,
                        longExposureMillis = 800L
                    )
                )
            )
        )

        val plan = executionPlanner.plan(request)

        assertEquals(4, plan.totalFrameCount)
        assertEquals(3, plan.temporaryFrameCount)
        assertEquals(4, plan.finalFrameIndex)
        assertEquals(MultiFrameOutputRole.TEMPORARY, plan.steps.first().outputRole)
        assertEquals(MultiFrameOutputRole.FINAL_OUTPUT, plan.steps.last().outputRole)
        assertEquals(200L, plan.interFrameDelayMillis)
    }

    @Test
    fun `single frame multi frame request still produces final output step`() {
        val plan = executionPlanner.plan(
            DeviceShotRequest(
                shotId = "shot-single-multi",
                template = CaptureTemplate.STILL_CAPTURE,
                shotKind = com.opencamera.core.media.ShotKind.MULTI_FRAME_CAPTURE,
                stillCaptureQuality = StillCaptureQualityPreference.QUALITY,
                frameCount = 1
            )
        )

        assertEquals(1, plan.totalFrameCount)
        assertEquals(0, plan.temporaryFrameCount)
        assertEquals(1, plan.finalFrameIndex)
        assertEquals(MultiFrameOutputRole.FINAL_OUTPUT, plan.steps.single().outputRole)
    }

    @Test
    fun `multi frame execution planner rejects non multi frame shot kinds`() {
        assertFailsWith<IllegalArgumentException> {
            executionPlanner.plan(
                DeviceShotRequest(
                    shotId = "shot-photo",
                    template = CaptureTemplate.STILL_CAPTURE,
                    shotKind = com.opencamera.core.media.ShotKind.STILL_CAPTURE,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            )
        }
    }
}
