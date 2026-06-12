package com.opencamera.core.capability

/**
 * Resolves multi-frame input format capabilities by combining device hardware
 * capability with CameraX API availability.
 *
 * When the CameraX API does not support multi-frame RAW delivery (as is the case
 * with CameraX 1.4.x), RAW/DNG formats are gated off regardless of device hardware
 * capability. This prevents runtime capture attempts on unsupported API paths.
 *
 * ## CameraX 1.4.x Gate Status
 *
 * - JPEG burst: **SUPPORTED** (standard ImageCapture burst)
 * - YUV burst: **SUPPORTED** (ImageAnalysis with frame callback)
 * - RAW/DNG multi-frame: **UNSUPPORTED** (CameraX 1.4.x has no multi-frame RAW burst API;
 *   `ImageAnalysis` does not deliver RAW frames; `ImageCapture` only supports single-frame RAW)
 * - JPEG+DNG combined: **UNSUPPORTED** (requires multi-frame RAW delivery)
 *
 * Future CameraX versions (1.5+) may provide `ImageCaptureCapabilities.isRawSupported()`
 * and multi-frame RAW APIs. When those are available, this resolver can be updated
 * to enable the RAW/DNG path with verified API proof.
 */
class MultiFrameInputFormatResolver(
    private val apiQuery: MultiFrameInputApiQuery,
    private val supportsStillCapture: Boolean = true,
    private val supportsNightMultiFrame: Boolean = true
) {
    fun resolve(): MultiFrameInputCapabilityMatrix {
        return MultiFrameInputCapabilityMatrix(
            jpegBurst = resolveJpegBurst(),
            yuvBurst = resolveYuvBurst(),
            rawDng = resolveRawDng(),
            jpegDng = resolveJpegDng()
        )
    }

    private fun resolveJpegBurst(): MultiFrameInputFormatCapability {
        if (!supportsStillCapture) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.JPEG_BURST,
                support = CapabilitySupport.UNSUPPORTED,
                reason = "Device does not support still capture"
            )
        }
        if (!supportsNightMultiFrame) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.JPEG_BURST,
                support = CapabilitySupport.DEGRADED,
                reason = "Night multi-frame not supported, single-frame JPEG fallback",
                deviceRawCapable = false,
                cameraXApiSupported = false
            )
        }
        return MultiFrameInputFormatCapability(
            format = MultiFrameInputFormat.JPEG_BURST,
            support = CapabilitySupport.SUPPORTED,
            reason = "JPEG burst capture available via ImageCapture"
        )
    }

    private fun resolveYuvBurst(): MultiFrameInputFormatCapability {
        if (!apiQuery.cameraXYuvBurstSupported()) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.YUV_BURST,
                support = CapabilitySupport.UNSUPPORTED,
                reason = "ImageAnalysis YUV burst not available"
            )
        }
        if (!supportsNightMultiFrame) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.YUV_BURST,
                support = CapabilitySupport.DEGRADED,
                reason = "YUV burst available but night multi-frame not supported"
            )
        }
        return MultiFrameInputFormatCapability(
            format = MultiFrameInputFormat.YUV_BURST,
            support = CapabilitySupport.SUPPORTED,
            reason = "YUV burst available via ImageAnalysis"
        )
    }

    private fun resolveRawDng(): MultiFrameInputFormatCapability {
        val deviceCanDoRaw = supportsStillCapture && apiQuery.deviceRawCaptureSupported()
        if (!deviceCanDoRaw) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.RAW_DNG,
                support = CapabilitySupport.UNSUPPORTED,
                reason = "Device does not support RAW capture hardware"
            )
        }
        if (!apiQuery.cameraXMultiFrameRawSupported()) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.RAW_DNG,
                support = CapabilitySupport.UNSUPPORTED,
                reason = "CameraX multi-frame RAW burst API not available; " +
                    "single-frame RAW may work via ImageCapture but is not usable for fusion",
                deviceRawCapable = true,
                cameraXApiSupported = false
            )
        }
        return MultiFrameInputFormatCapability(
            format = MultiFrameInputFormat.RAW_DNG,
            support = CapabilitySupport.SUPPORTED,
            reason = "RAW/DNG multi-frame capture available",
            deviceRawCapable = true,
            cameraXApiSupported = true
        )
    }

    private fun resolveJpegDng(): MultiFrameInputFormatCapability {
        val deviceCanDoRaw = supportsStillCapture && apiQuery.deviceRawCaptureSupported()
        if (!deviceCanDoRaw) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.JPEG_DNG,
                support = CapabilitySupport.UNSUPPORTED,
                reason = "Device does not support RAW capture hardware"
            )
        }
        if (!apiQuery.cameraXJpegDngCombinedSupported()) {
            return MultiFrameInputFormatCapability(
                format = MultiFrameInputFormat.JPEG_DNG,
                support = CapabilitySupport.UNSUPPORTED,
                reason = "CameraX JPEG+DNG combined multi-frame input not available",
                deviceRawCapable = true,
                cameraXApiSupported = false
            )
        }
        return MultiFrameInputFormatCapability(
            format = MultiFrameInputFormat.JPEG_DNG,
            support = CapabilitySupport.SUPPORTED,
            reason = "JPEG+DNG combined multi-frame input available",
            deviceRawCapable = true,
            cameraXApiSupported = true
        )
    }
}

/**
 * Default [MultiFrameInputApiQuery] for CameraX 1.4.x.
 *
 * CameraX 1.4.x supports single-frame RAW via ImageCapture but does NOT provide:
 * - `ImageCaptureCapabilities.isRawSupported()` (requires 1.5.0+)
 * - Multi-frame RAW burst delivery via any use case
 * - Combined JPEG+DNG multi-frame input
 *
 * YUV burst via ImageAnalysis is available in all CameraX 1.x versions.
 */
class CameraX_1_4_MultiFrameInputApiQuery : MultiFrameInputApiQuery {
    override fun deviceRawCaptureSupported(): Boolean = true

    override fun cameraXMultiFrameRawSupported(): Boolean = false

    override fun cameraXYuvBurstSupported(): Boolean = true

    override fun cameraXJpegDngCombinedSupported(): Boolean = false
}
