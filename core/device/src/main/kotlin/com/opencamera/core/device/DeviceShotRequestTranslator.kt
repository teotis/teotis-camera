package com.opencamera.core.device

import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.primaryStillNode
import com.opencamera.core.media.primaryVideoNode
import com.opencamera.core.media.temporaryFrameNode
import com.opencamera.core.settings.ManualCaptureParams

data class DeviceShotRequest(
    val shotId: String,
    val template: CaptureTemplate,
    val shotKind: ShotKind,
    val stillCaptureQuality: StillCaptureQualityPreference? = null,
    val flashMode: FlashMode = FlashMode.OFF,
    val torchEnabled: Boolean = false,
    val manualCaptureParams: ManualCaptureParams? = null,
    val manualControlCapabilities: ManualControlCapabilityMatrix =
        DeviceCapabilities.DEFAULT.resolvedManualControlCapabilities,
    val frameCount: Int = 1,
    val interFrameDelayMillis: Long = 0L,
    val diagnostics: List<String> = emptyList()
)

interface DeviceShotRequestTranslator {
    fun translate(plan: ShotPlan): DeviceShotRequest
}

class DefaultDeviceShotRequestTranslator(
    private val deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
) : DeviceShotRequestTranslator {
    override fun translate(plan: ShotPlan): DeviceShotRequest {
        val request = plan.request
        val graph = plan.graph
        return when (request.shotKind) {
            ShotKind.STILL_CAPTURE -> {
                val stillNode = graph.primaryStillNode()
                checkNotNull(stillNode) {
                    "ShotGraph missing PRIMARY_STILL node for STILL_CAPTURE"
                }
                val resolvedFlashMode = resolveFlashMode(request.captureProfile.flashMode)
                val stillCaptureQuality = request.captureProfile.stillCaptureQuality
                    ?: StillCaptureQualityPreference.LATENCY
                val stillCaptureResolutionPreset = request.captureProfile.stillCaptureResolutionPreset
                    ?: StillCaptureResolutionPreset.LARGE_12MP
                DeviceShotRequest(
                    shotId = request.shotId,
                    template = CaptureTemplate.STILL_CAPTURE,
                    shotKind = request.shotKind,
                    stillCaptureQuality = stillCaptureQuality,
                    flashMode = resolvedFlashMode,
                    manualCaptureParams = resolveManualCaptureParams(
                        request.captureProfile.manualCaptureParams
                    ),
                    manualControlCapabilities = deviceCapabilities.resolvedManualControlCapabilities,
                    diagnostics = buildList {
                        add("device:template=still-capture")
                        add("device:graph=capture-topology")
                        add("device:graph-node=${stillNode.id}")
                        add("device:capture-mode=${stillCaptureQuality.captureModeTag()}")
                        add("device:still-resolution=${stillCaptureResolutionPreset.tagValue}")
                        addManualDiagnostics(request.captureProfile.manualCaptureParams)
                        addFlashDiagnostics(
                            requestedFlashMode = request.captureProfile.flashMode,
                            resolvedFlashMode = resolvedFlashMode
                        )
                    }
                )
            }

            ShotKind.MULTI_FRAME_CAPTURE -> translateMultiFrame(plan)

            ShotKind.LIVE_PHOTO -> {
                val stillNode = graph.primaryStillNode()
                checkNotNull(stillNode) {
                    "ShotGraph missing PRIMARY_STILL node for LIVE_PHOTO"
                }
                val resolvedFlashMode = resolveFlashMode(request.captureProfile.flashMode)
                val stillCaptureQuality = request.captureProfile.stillCaptureQuality
                    ?: StillCaptureQualityPreference.LATENCY
                val stillCaptureResolutionPreset = request.captureProfile.stillCaptureResolutionPreset
                    ?: StillCaptureResolutionPreset.LARGE_12MP
                DeviceShotRequest(
                    shotId = request.shotId,
                    template = CaptureTemplate.STILL_CAPTURE,
                    shotKind = request.shotKind,
                    stillCaptureQuality = stillCaptureQuality,
                    flashMode = resolvedFlashMode,
                    manualCaptureParams = resolveManualCaptureParams(
                        request.captureProfile.manualCaptureParams
                    ),
                    manualControlCapabilities = deviceCapabilities.resolvedManualControlCapabilities,
                    diagnostics = buildList {
                        add("device:template=still-capture")
                        add("device:graph=capture-topology")
                        add("device:graph-node=${stillNode.id}")
                        add("device:capture-mode=${stillCaptureQuality.captureModeTag()}")
                        add("device:still-resolution=${stillCaptureResolutionPreset.tagValue}")
                        add("device:live-photo=bundle")
                        addManualDiagnostics(request.captureProfile.manualCaptureParams)
                        request.livePhotoSpec?.let { livePhotoSpec ->
                            add("device:live-motion=${livePhotoSpec.motionDurationMillis}ms")
                            add("device:live-sidecar=${livePhotoSpec.sidecarMimeType}")
                        }
                        addFlashDiagnostics(
                            requestedFlashMode = request.captureProfile.flashMode,
                            resolvedFlashMode = resolvedFlashMode
                        )
                    }
                )
            }

            ShotKind.VIDEO_RECORDING -> {
                val videoNode = graph.primaryVideoNode()
                checkNotNull(videoNode) {
                    "ShotGraph missing PRIMARY_VIDEO node for VIDEO_RECORDING"
                }
                val resolvedTorchEnabled = resolveTorchEnabled(request.captureProfile.torchEnabled)
                DeviceShotRequest(
                    shotId = request.shotId,
                    template = CaptureTemplate.VIDEO_RECORDING,
                    shotKind = request.shotKind,
                    torchEnabled = resolvedTorchEnabled,
                    manualControlCapabilities = deviceCapabilities.resolvedManualControlCapabilities,
                    diagnostics = buildList {
                        add("device:template=video-recording")
                        add("device:graph=capture-topology")
                        add("device:graph-node=${videoNode.id}")
                        add("device:video=recorder")
                        addTorchDiagnostics(
                            requestedTorchEnabled = request.captureProfile.torchEnabled,
                            resolvedTorchEnabled = resolvedTorchEnabled
                        )
                    }
                )
            }
        }
    }

    private fun translateMultiFrame(plan: ShotPlan): DeviceShotRequest {
        val graph = plan.graph
        val stillNode = graph.primaryStillNode()
        checkNotNull(stillNode) {
            "ShotGraph missing PRIMARY_STILL node for MULTI_FRAME_CAPTURE"
        }
        val tempNode = graph.temporaryFrameNode()
        checkNotNull(tempNode) {
            "ShotGraph missing TEMPORARY_FRAME node for MULTI_FRAME_CAPTURE"
        }

        val captureProfile = plan.request.captureProfile
        val graphFrameCount = tempNode.frameCount.coerceAtLeast(1)
        val normalizedFrameCount = graphFrameCount
        val graphInterFrameDelay = tempNode.timingPolicy.interFrameDelayMillis
        val interFrameDelayMillis = if (graphInterFrameDelay > 0) {
            graphInterFrameDelay
        } else {
            captureProfile.longExposureMillis
                ?.div(normalizedFrameCount.toLong())
                ?.coerceIn(60L, 240L)
                ?: 80L
        }
        val resolvedFlashMode = resolveFlashMode(captureProfile.flashMode)
        val stillCaptureQuality = captureProfile.stillCaptureQuality
            ?: StillCaptureQualityPreference.QUALITY
        val stillCaptureResolutionPreset = captureProfile.stillCaptureResolutionPreset
            ?: StillCaptureResolutionPreset.LARGE_12MP

        return DeviceShotRequest(
            shotId = plan.request.shotId,
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = plan.request.shotKind,
            stillCaptureQuality = stillCaptureQuality,
            flashMode = resolvedFlashMode,
            manualCaptureParams = resolveManualCaptureParams(captureProfile.manualCaptureParams),
            manualControlCapabilities = deviceCapabilities.resolvedManualControlCapabilities,
            frameCount = normalizedFrameCount,
            interFrameDelayMillis = interFrameDelayMillis,
            diagnostics = buildList {
                add("device:template=still-capture")
                add("device:graph=capture-topology")
                add("device:graph-node=${stillNode.id}")
                add("device:capture-mode=${stillCaptureQuality.captureModeTag()}")
                add("device:still-resolution=${stillCaptureResolutionPreset.tagValue}")
                addManualDiagnostics(captureProfile.manualCaptureParams)
                addFlashDiagnostics(
                    requestedFlashMode = captureProfile.flashMode,
                    resolvedFlashMode = resolvedFlashMode
                )
                add("device:frame-count=$normalizedFrameCount")
                add("device:inter-frame-delay=${interFrameDelayMillis}ms")
                captureProfile.longExposureMillis?.let { add("device:long-exposure=${it}ms") }
                add(
                    "device:stability=" +
                        if (captureProfile.requiresTripod) {
                            "tripod"
                        } else {
                            "handheld"
                        }
                )
            }
        )
    }

    private fun resolveFlashMode(requestedFlashMode: FlashMode): FlashMode {
        return if (deviceCapabilities.supportsFlashControl) {
            requestedFlashMode
        } else {
            FlashMode.OFF
        }
    }

    private fun resolveManualCaptureParams(
        requestedManualCaptureParams: ManualCaptureParams?
    ): ManualCaptureParams? {
        if (requestedManualCaptureParams == null || requestedManualCaptureParams.isAuto()) {
            return null
        }
        return requestedManualCaptureParams
    }

    private fun resolveTorchEnabled(requestedTorchEnabled: Boolean): Boolean {
        return if (deviceCapabilities.supportsFlashControl) {
            requestedTorchEnabled
        } else {
            false
        }
    }

    private fun MutableList<String>.addFlashDiagnostics(
        requestedFlashMode: FlashMode,
        resolvedFlashMode: FlashMode
    ) {
        when {
            requestedFlashMode != FlashMode.OFF && !deviceCapabilities.supportsFlashControl ->
                add("device:flash=unsupported-fallback-off")

            resolvedFlashMode != FlashMode.OFF ->
                add("device:flash=${resolvedFlashMode.name.lowercase()}")
        }
    }

    private fun MutableList<String>.addTorchDiagnostics(
        requestedTorchEnabled: Boolean,
        resolvedTorchEnabled: Boolean
    ) {
        when {
            requestedTorchEnabled && !deviceCapabilities.supportsFlashControl ->
                add("device:torch=unsupported-fallback-off")

            resolvedTorchEnabled ->
                add("device:torch=on")
        }
    }

    private fun MutableList<String>.addManualDiagnostics(
        requestedManualCaptureParams: ManualCaptureParams?
    ) {
        when {
            requestedManualCaptureParams == null || requestedManualCaptureParams.isAuto() ->
                add("device:manual=auto")

            else -> {
                val manualCapabilities = deviceCapabilities.resolvedManualControlCapabilities
                val summary = manualCapabilities.supportSummary(requestedManualCaptureParams)
                val overallStatus = when {
                    summary.applied.isNotEmpty() &&
                        (summary.savedOnly.isNotEmpty() || summary.unsupported.isNotEmpty()) ->
                        "partial"

                    else -> summary.overallStatus.tagValue
                }
                add("device:manual=$overallStatus")
                add(
                    "device:manual-raw=" +
                        "${manualCapabilities.raw.tagValue}:" +
                        if (requestedManualCaptureParams.rawEnabled) "on" else "off"
                )
                add(
                    "device:manual-iso=" +
                        "${manualCapabilities.iso.tagValue}:" +
                        (requestedManualCaptureParams.iso?.toString() ?: "auto")
                )
                add(
                    "device:manual-shutter=" +
                        "${manualCapabilities.shutter.tagValue}:" +
                        (requestedManualCaptureParams.shutterSpeedMillis?.let { "${it}ms" } ?: "auto")
                )
                add(
                    "device:manual-ev=" +
                        "${manualCapabilities.exposureCompensation.tagValue}:" +
                        (requestedManualCaptureParams.exposureCompensationSteps?.toString() ?: "auto")
                )
                add(
                    "device:manual-focus=" +
                        "${manualCapabilities.focusDistance.tagValue}:" +
                        (requestedManualCaptureParams.focusDistanceDiopters?.toString() ?: "auto")
                )
                add(
                    "device:manual-aperture=" +
                        "${manualCapabilities.aperture.tagValue}:" +
                        (requestedManualCaptureParams.apertureFNumber?.toString() ?: "auto")
                )
                add(
                    "device:manual-wb=" +
                        "${manualCapabilities.whiteBalance.tagValue}:" +
                        (requestedManualCaptureParams.whiteBalanceKelvin?.let { "${it}K" } ?: "auto")
                )
            }
        }
    }

    private fun StillCaptureQualityPreference.captureModeTag(): String {
        return when (this) {
            StillCaptureQualityPreference.LATENCY -> "minimize-latency"
            StillCaptureQualityPreference.QUALITY -> "max-quality"
        }
    }

    private fun ManualCaptureParams.isAuto(): Boolean {
        return !rawEnabled &&
            iso == null &&
            shutterSpeedMillis == null &&
            exposureCompensationSteps == null &&
            focusDistanceDiopters == null &&
            apertureFNumber == null &&
            whiteBalanceKelvin == null
    }
}
