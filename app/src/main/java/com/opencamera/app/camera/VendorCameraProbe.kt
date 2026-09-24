package com.opencamera.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.util.SizeF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun appendProbeSection(
    sb: StringBuilder,
    label: String,
    block: () -> Unit
): Boolean {
    return runCatching {
        block()
        true
    }.getOrElse { error ->
        sb.appendLine("  [$label] ERROR: ${probeErrorMessage(error)}")
        false
    }
}

internal fun probeErrorMessage(error: Throwable): String {
    return error.message
        ?.takeIf { it.isNotBlank() }
        ?: error::class.java.simpleName
}

object VendorCameraProbe {

    private val knownVendorKeyClasses = listOf(
        "org.quic.camera2.streamconfigs.StreamConfigurations",
        "org.codeaurora.camera2.streamconfigs.StreamConfigurations",
        "com.xiaomi.camera.util.CameraCharacteristicsExtra",
        "com.xiaomi.engine.MiCameraCharacteristics",
        "com.samsung.android.camera.CameraCharacteristicsExtra",
        "com.oppo.camera.util.OppoCameraCharacteristics",
        "com.vivo.camera.util.VivoCameraCharacteristics",
        "com.oneplus.camera.util.OnePlusCameraCharacteristics",
        "android.hardware.camera2.CameraMetadata",
        "com.mediatek.camera.CameraCharacteristicsExtra",
        "com.huawei.camera.CameraCharacteristicsExtra",
        "com.google.android.camera.experimental.ExperimentalCameraCharacteristics",
    )

    private val rawImageFormats = listOf(
        ImageFormat.RAW_SENSOR to "RAW_SENSOR",
        ImageFormat.RAW10 to "RAW10",
        ImageFormat.RAW12 to "RAW12",
        ImageFormat.RAW_PRIVATE to "RAW_PRIVATE",
    )

    fun probe(context: Context): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val cameraIds = runCatching { cameraManager?.cameraIdList ?: emptyArray() }
            .getOrDefault(emptyArray())
        val sb = StringBuilder()

        sb.appendLine("===== VENDOR CAMERA PROBE =====")
        sb.appendLine("timestamp: ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("camera-count: ${cameraIds.size}")
        sb.appendLine()

        if (cameraManager == null) {
            sb.appendLine("[camera-service] ERROR: CameraManager unavailable")
            sb.appendLine("===== PROBE COMPLETE =====")
            return sb.toString()
        }

        appendProbeSection(sb, "camera-extensions") {
            appendCameraExtensionProbe(sb, CameraExtensionProbe.probe(context))
        }

        for (cameraId in cameraIds) {
            appendCameraProbe(sb, cameraManager, cameraId)
        }

        sb.appendLine("===== PROBE COMPLETE =====")
        return sb.toString()
    }

    fun summary(context: Context): String {
        return CameraExtensionProbe.probe(context).diagnosticSummary()
    }

    fun summaryFromProbeText(content: String): String? {
        val summaryLines = mutableListOf<String>()
        var cameraId: String? = null
        var lensFacing: String? = null
        var hardwareLevel: String? = null
        var physicalIds: String? = null
        val opportunities = mutableListOf<String>()

        fun flushCamera() {
            val id = cameraId ?: return
            val identity = buildString {
                append("cameraId=$id")
                lensFacing?.let { append(" $it") }
                hardwareLevel?.let { append(" $it") }
                physicalIds?.let { append(" physical=$it") }
            }
            summaryLines += identity
            summaryLines += opportunities.map { "  $it" }
            cameraId = null
            lensFacing = null
            hardwareLevel = null
            physicalIds = null
            opportunities.clear()
        }

        content.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("extensions: ") || line.startsWith("image-quality-ext: ") -> {
                    summaryLines += line
                }
                line.startsWith("=== cameraId=") -> {
                    flushCamera()
                    cameraId = line.removePrefix("=== cameraId=").removeSuffix(" ===")
                }
                line.startsWith("--- physical-camera") -> flushCamera()
                cameraId != null && line.startsWith("lens-facing: ") -> {
                    lensFacing = line.removePrefix("lens-facing: ")
                }
                cameraId != null && line.startsWith("hardware-level: ") -> {
                    hardwareLevel = line.removePrefix("hardware-level: ")
                }
                cameraId != null && line.startsWith("physical-camera-ids: ") -> {
                    physicalIds = line.removePrefix("physical-camera-ids: ")
                }
                cameraId != null && (
                    line.startsWith("READY ") || line.startsWith("VERIFY_ON_DEVICE ")
                ) -> opportunities += line
            }
        }
        flushCamera()

        return summaryLines
            .joinToString(separator = "\n")
            .takeIf { it.isNotBlank() }
    }

    private fun appendCameraProbe(
        sb: StringBuilder,
        cameraManager: CameraManager,
        cameraId: String
    ) {
        sb.appendLine("=== cameraId=$cameraId ===")
        val chars = runCatching { cameraManager.getCameraCharacteristics(cameraId) }
            .getOrElse { error ->
                sb.appendLine("  [camera-characteristics] ERROR: ${probeErrorMessage(error)}")
                return
            }

        @Suppress("DEPRECATION")
        val physicalIds = runCatching { chars.getPhysicalCameraIds() }
            .getOrElse { error ->
                sb.appendLine("  [physical-camera-ids] ERROR: ${probeErrorMessage(error)}")
                emptySet()
            }

        appendProbeSection(sb, "camera-basics") {
            sb.appendLine("  lens-facing: ${lensFacingLabel(chars.get(CameraCharacteristics.LENS_FACING))}")
            sb.appendLine("  hardware-level: ${hardwareLevelLabel(chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")
            sb.appendLine("  logical: ${isLogical(chars)}")
            if (physicalIds.isNotEmpty()) {
                sb.appendLine("  physical-camera-ids: ${physicalIds.joinToString(", ")}")
            }
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            sb.appendLine("  capabilities: ${capabilities.map { capabilityLabel(it) }.joinToString(", ")}")
        }

        appendProbeSection(sb, "sensor-info") { appendSensorInfo(sb, chars) }
        appendProbeSection(sb, "lens-info") { appendLensInfo(sb, chars) }
        appendProbeSection(sb, "development-assessment") {
            appendCameraDevelopmentAssessment(sb, chars, physicalIds.size)
        }
        appendProbeSection(sb, "control-surface") { appendControlSurface(sb, chars) }
        appendProbeSection(sb, "stream-inventory") { appendStreamInventory(sb, chars) }
        appendProbeSection(sb, "advanced-platform") { appendAdvancedPlatformFeatures(sb, chars) }
        appendProbeSection(sb, "extended-scene-modes") { appendExtendedSceneModeProbe(sb, chars) }
        appendProbeSection(sb, "raw-capability") { appendRawCapability(sb, chars) }
        appendProbeSection(sb, "characteristics-keys") { appendAllCharacteristicsKeys(sb, chars) }
        appendProbeSection(sb, "hidden-via-reflection") { appendVendorKeysViaReflection(sb, chars) }
        appendProbeSection(sb, "available-keys") { appendAvailableKeys(sb, chars) }

        sb.appendLine()

        for (physicalId in physicalIds) {
            appendPhysicalCameraProbe(sb, cameraManager, cameraId, physicalId)
        }
    }

    private fun appendPhysicalCameraProbe(
        sb: StringBuilder,
        cameraManager: CameraManager,
        parentCameraId: String,
        physicalCameraId: String
    ) {
        sb.appendLine("--- physical-camera id=$physicalCameraId (parent=$parentCameraId) ---")
        val chars = runCatching { cameraManager.getCameraCharacteristics(physicalCameraId) }
            .getOrElse { error ->
                sb.appendLine("  [camera-characteristics] ERROR: ${probeErrorMessage(error)}")
                return
            }

        appendProbeSection(sb, "physical-camera-basics") {
            sb.appendLine("  lens-facing: ${lensFacingLabel(chars.get(CameraCharacteristics.LENS_FACING))}")
            sb.appendLine("  hardware-level: ${hardwareLevelLabel(chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")
        }
        appendProbeSection(sb, "physical-sensor-info") { appendSensorInfo(sb, chars) }
        appendProbeSection(sb, "physical-lens-info") { appendLensInfo(sb, chars) }
        appendProbeSection(sb, "physical-development-assessment") {
            appendCameraDevelopmentAssessment(sb, chars, physicalCameraCount = 0)
        }
        appendProbeSection(sb, "physical-control-surface") { appendControlSurface(sb, chars) }
        appendProbeSection(sb, "physical-stream-inventory") { appendStreamInventory(sb, chars) }
        appendProbeSection(sb, "physical-advanced-platform") { appendAdvancedPlatformFeatures(sb, chars) }
        appendProbeSection(sb, "physical-raw-capability") { appendRawCapability(sb, chars) }
        appendProbeSection(sb, "physical-characteristics-keys") { appendAllCharacteristicsKeys(sb, chars) }
        appendProbeSection(sb, "physical-hidden-via-reflection") { appendVendorKeysViaReflection(sb, chars) }
        appendProbeSection(sb, "physical-available-keys") { appendAvailableKeys(sb, chars) }
        sb.appendLine()
    }

    // ── Sensor info ──────────────────────────────────────────

    private fun appendSensorInfo(sb: StringBuilder, chars: CameraCharacteristics) {
        val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixelArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val whiteLevel = chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
        val sensitivityRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val maxAnalogSensitivity = chars.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
        val cfaPattern = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        val timestampSource = chars.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)

        sb.appendLine("  [sensor-info]")
        if (physicalSize != null) {
            sb.appendLine("    ${formatPhysicalSensorSize(physicalSize)}")
        }
        if (pixelArraySize != null) {
            val pixelCount = pixelArraySize.width.toLong() * pixelArraySize.height
            val mpLabel = "%.1fMP".format(pixelCount / 1_000_000.0)
            sb.appendLine("    pixel-array: ${pixelArraySize.width}×${pixelArraySize.height} ($mpLabel)")
            if (physicalSize != null) {
                sb.appendLine("    ${formatSensorPixelSize(physicalSize, pixelArraySize)}")
            }
        }
        if (whiteLevel != null) sb.appendLine("    white-level: $whiteLevel")
        if (sensitivityRange != null) sb.appendLine("    sensitivity-range: ${sensitivityRange.lower} – ${sensitivityRange.upper}")
        if (maxAnalogSensitivity != null) sb.appendLine("    max-analog-sensitivity: $maxAnalogSensitivity")
        if (exposureRange != null) {
            sb.appendLine("    exposure-time-range: ${exposureRange.lower}ns – ${exposureRange.upper}ns " +
                "(${exposureRange.lower / 1_000_000L}ms – ${exposureRange.upper / 1_000_000L}ms)")
        }
        if (cfaPattern != null) sb.appendLine("    cfa-pattern: ${cfaLabel(cfaPattern)}")
        if (timestampSource != null) sb.appendLine("    timestamp-source: ${tsSourceLabel(timestampSource)}")
    }

    // ── Lens info ────────────────────────────────────────────

    private fun appendLensInfo(sb: StringBuilder, chars: CameraCharacteristics) {
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val filterDensities = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES)
        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)

        val hasInfo = focalLengths != null || apertures != null
        if (!hasInfo) return

        sb.appendLine("  [lens-info]")
        if (focalLengths != null) {
            sb.appendLine("    focal-lengths: ${focalLengths.joinToString("mm, ") { "%.2f".format(it) }}mm" +
                " (35mm-equiv: ${focalLengths.joinToString { "%.0f".format(it * focalLengthCropFactor(chars)) }})")
        }
        if (apertures != null) {
            sb.appendLine("    apertures: f/${apertures.joinToString(", f/") { "%.1f".format(it) }}")
        }
        if (filterDensities != null && filterDensities.isNotEmpty()) {
            sb.appendLine("    nd-filter-densities: ${filterDensities.joinToString()}")
        }
        if (oisModes != null && oisModes.isNotEmpty()) {
            sb.appendLine("    ois-modes: ${oisModes.joinToString { oisLabel(it) }}")
        }
    }

    // ── Development assessment ──────────────────────────────

    private fun appendCameraDevelopmentAssessment(
        sb: StringBuilder,
        chars: CameraCharacteristics,
        physicalCameraCount: Int
    ) {
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.toSet()
            .orEmpty()
        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val requestKeys = chars.safeList { availableCaptureRequestKeys }
        val resultKeys = chars.safeList { availableCaptureResultKeys }
        val physicalRequestKeys = chars.safeList { availablePhysicalCameraRequestKeys }
        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?: intArrayOf()
        val videoStabilizationModes = chars.get(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        ) ?: intArrayOf()
        val facts = CameraDevelopmentFacts(
            hardwareLevel = hardwareLevelLabel(
                chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ),
            physicalCameraCount = physicalCameraCount,
            supportsManualSensor = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            ),
            supportsManualPostProcessing = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
            ),
            supportsRaw = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
            ),
            supportsBurst = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE
            ),
            supportsYuvReprocessing = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING
            ),
            supportsPrivateReprocessing = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING
            ),
            supportsRemosaicReprocessing = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING
            ),
            supportsDepth = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT
            ),
            supportsHighSpeedVideo = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
            ),
            supportsTenBitDynamicRange = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT
            ),
            supportsUltraHighResolution = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
            ),
            supportsStreamUseCases = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE
            ),
            supportsOfflineProcessing = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING
            ),
            hasIsoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) != null,
            hasExposureTimeRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) != null,
            hasManualFocusDistance = (chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f) > 0f,
            hasRawOutput = configMap.hasAnyOutput(
                ImageFormat.RAW_SENSOR,
                ImageFormat.RAW10,
                ImageFormat.RAW12,
                ImageFormat.RAW_PRIVATE
            ),
            hasYuvOutput = configMap.hasAnyOutput(ImageFormat.YUV_420_888),
            hasHighResolutionJpeg = configMap.outputSizes(ImageFormat.JPEG, highResolution = true).isNotEmpty(),
            hasOpticalStabilization = oisModes.contains(
                CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON
            ),
            hasVideoStabilization = videoStabilizationModes.any { it != 0 },
            vendorRequestKeyCount = requestKeys.count { isVendorKeyName(it.name) },
            vendorResultKeyCount = resultKeys.count { isVendorKeyName(it.name) },
            physicalRequestKeyCount = physicalRequestKeys.size
        )

        sb.appendLine(formatCameraDevelopmentAssessment(assessCameraDevelopmentFeatures(facts)))
    }

    // ── Public control surface ───────────────────────────────

    private fun appendControlSurface(sb: StringBuilder, chars: CameraCharacteristics) {
        sb.appendLine("  [control-surface]")
        appendLabeledModes(
            sb,
            "ae-modes",
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
            ::aeModeLabel
        )
        appendLabeledModes(
            sb,
            "af-modes",
            chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
            ::afModeLabel
        )
        appendLabeledModes(
            sb,
            "awb-modes",
            chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
            ::awbModeLabel
        )
        appendLabeledModes(
            sb,
            "ois-modes",
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),
            ::oisLabel
        )
        appendLabeledModes(
            sb,
            "video-stabilization-modes",
            chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),
            ::videoStabilizationLabel
        )
        appendLabeledModes(
            sb,
            "face-detect-modes",
            chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES),
            ::faceDetectModeLabel
        )

        sb.appendLine("    flash-available: ${chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false}")
        sb.appendLine("    minimum-focus-distance: ${chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: "none"}")
        sb.appendLine("    hyperfocal-distance: ${chars.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE) ?: "none"}")
        sb.appendLine("    max-face-count: ${chars.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0}")
        sb.appendLine("    max-regions: AE=${chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0}, " +
            "AF=${chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0}, " +
            "AWB=${chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0}")
        sb.appendLine("    ae-compensation-range: ${formatAnyValue(chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE))}")
        sb.appendLine("    ae-compensation-step: ${formatAnyValue(chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP))}")
        appendLabeledModes(sb, "edge-modes", chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)) { it.toString() }
        appendLabeledModes(sb, "noise-reduction-modes", chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)) { it.toString() }
        appendLabeledModes(sb, "tonemap-modes", chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)) { it.toString() }
        sb.appendLine("    tonemap-max-curve-points: ${chars.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS) ?: 0}")
    }

    private fun appendLabeledModes(
        sb: StringBuilder,
        label: String,
        values: IntArray?,
        formatter: (Int) -> String
    ) {
        sb.appendLine("    $label: ${values?.joinToString { formatter(it) }?.ifBlank { "none" } ?: "none"}")
    }

    // ── Stream inventory ─────────────────────────────────────

    private fun appendStreamInventory(sb: StringBuilder, chars: CameraCharacteristics) {
        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        sb.appendLine("  [stream-inventory]")
        if (configMap == null) {
            sb.appendLine("    unavailable")
            return
        }

        listOf(
            ImageFormat.JPEG to "JPEG",
            ImageFormat.YUV_420_888 to "YUV_420_888",
            ImageFormat.PRIVATE to "PRIVATE",
            ImageFormat.HEIC to "HEIC",
            ImageFormat.RAW_SENSOR to "RAW_SENSOR",
            ImageFormat.RAW10 to "RAW10",
            ImageFormat.RAW12 to "RAW12",
            ImageFormat.RAW_PRIVATE to "RAW_PRIVATE",
            ImageFormat.DEPTH_JPEG to "DEPTH_JPEG",
            ImageFormat.DEPTH16 to "DEPTH16",
            ImageFormat.DEPTH_POINT_CLOUD to "DEPTH_POINT_CLOUD"
        ).forEach { (format, label) ->
            val sizes = configMap.outputSizes(format, highResolution = false)
            val highResolutionSizes = configMap.outputSizes(format, highResolution = true)
            if (sizes.isNotEmpty() || highResolutionSizes.isNotEmpty()) {
                sb.appendLine("    $label: ${formatSizeInventory(sizes, highResolutionSizes)}")
            }
        }

        val highSpeedSizes = runCatching { configMap.highSpeedVideoSizes?.toList().orEmpty() }
            .getOrDefault(emptyList())
        if (highSpeedSizes.isNotEmpty()) {
            sb.appendLine("    high-speed-video:")
            highSpeedSizes.sortedByDescending { it.width.toLong() * it.height }.forEach { size ->
                val ranges = runCatching { configMap.getHighSpeedVideoFpsRangesFor(size)?.toList().orEmpty() }
                    .getOrDefault(emptyList())
                sb.appendLine("      ${size.width}×${size.height}: ${ranges.joinToString { "${it.lower}-${it.upper}fps" }}")
            }
        }
    }

    private fun formatSizeInventory(standard: List<Size>, highResolution: List<Size>): String {
        val parts = mutableListOf<String>()
        if (standard.isNotEmpty()) {
            val max = standard.maxBy { it.width.toLong() * it.height }
            parts += "standard=${standard.size},max=${max.width}×${max.height}"
        }
        if (highResolution.isNotEmpty()) {
            val max = highResolution.maxBy { it.width.toLong() * it.height }
            parts += "high-res=${highResolution.size},max=${max.width}×${max.height}"
        }
        return parts.joinToString(" | ")
    }

    // ── Advanced Android platform features ──────────────────

    private fun appendAdvancedPlatformFeatures(sb: StringBuilder, chars: CameraCharacteristics) {
        sb.appendLine("  [advanced-platform]")
        val dynamicRangeProfiles = if (android.os.Build.VERSION.SDK_INT >= 33) {
            chars.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
        } else null
        val colorSpaceProfiles = if (android.os.Build.VERSION.SDK_INT >= 34) {
            chars.get(CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)
        } else null
        val streamUseCases = if (android.os.Build.VERSION.SDK_INT >= 33) {
            chars.get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES)
        } else null
        sb.appendLine("    dynamic-range-profiles: ${formatDynamicRangeProfiles(dynamicRangeProfiles)}")
        sb.appendLine("    color-space-profiles: ${formatColorSpaceProfiles(colorSpaceProfiles)}")
        sb.appendLine("    stream-use-cases: ${formatStreamUseCases(streamUseCases)}")
        sb.appendLine("    max-output-streams: raw=${chars.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW) ?: 0}, " +
            "processed=${chars.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC) ?: 0}, " +
            "stalling=${chars.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING) ?: 0}")
        sb.appendLine("    pipeline-depth: ${chars.get(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH) ?: 0}")
        sb.appendLine("    partial-result-count: ${chars.get(CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT) ?: 0}")
        sb.appendLine("    sync-max-latency: ${formatAnyValue(chars.get(CameraCharacteristics.SYNC_MAX_LATENCY))}")
        val sensorSyncType = if (android.os.Build.VERSION.SDK_INT >= 28) {
            chars.get(CameraCharacteristics.LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)
        } else null
        sb.appendLine("    logical-sensor-sync-type: ${formatAnyValue(sensorSyncType)}")
    }

    @android.annotation.TargetApi(33)
    private fun formatDynamicRangeProfiles(
        profiles: android.hardware.camera2.params.DynamicRangeProfiles?
    ): String {
        if (profiles == null) return "none"
        return profiles.supportedProfiles
            .sorted()
            .joinToString(separator = " | ") { profile ->
                val constraints = profiles.getProfileCaptureRequestConstraints(profile)
                    .sorted()
                    .joinToString { dynamicRangeProfileLabel(it) }
                    .ifBlank { "none" }
                val latency = profiles.isExtraLatencyPresent(profile)
                "${dynamicRangeProfileLabel(profile)}{constraints=$constraints,extra-latency=$latency}"
            }
            .ifBlank { "none" }
    }

    @android.annotation.TargetApi(33)
    private fun dynamicRangeProfileLabel(profile: Long): String = when (profile) {
        android.hardware.camera2.params.DynamicRangeProfiles.STANDARD -> "STANDARD"
        android.hardware.camera2.params.DynamicRangeProfiles.HLG10 -> "HLG10"
        android.hardware.camera2.params.DynamicRangeProfiles.HDR10 -> "HDR10"
        android.hardware.camera2.params.DynamicRangeProfiles.HDR10_PLUS -> "HDR10_PLUS"
        else -> "0x${profile.toString(16)}"
    }

    @android.annotation.TargetApi(34)
    private fun formatColorSpaceProfiles(
        profiles: android.hardware.camera2.params.ColorSpaceProfiles?
    ): String {
        if (profiles == null) return "none"
        return listOf(
            ImageFormat.JPEG to "JPEG",
            ImageFormat.YUV_420_888 to "YUV_420_888",
            ImageFormat.PRIVATE to "PRIVATE",
            ImageFormat.HEIC to "HEIC",
            ImageFormat.RAW_SENSOR to "RAW_SENSOR"
        ).mapNotNull { (format, label) ->
            val colorSpaces = runCatching { profiles.getSupportedColorSpaces(format) }
                .getOrDefault(emptySet())
            colorSpaces.takeIf { it.isNotEmpty() }
                ?.let { "$label=${it.joinToString { colorSpace -> colorSpace.name }}" }
        }.joinToString(separator = " | ").ifBlank { "none" }
    }

    private fun formatStreamUseCases(useCases: LongArray?): String {
        return useCases
            ?.joinToString { streamUseCaseLabel(it) }
            ?.ifBlank { "none" }
            ?: "none"
    }

    private fun streamUseCaseLabel(useCase: Long): String = when (useCase) {
        0L -> "DEFAULT"
        1L -> "PREVIEW"
        2L -> "STILL_CAPTURE"
        3L -> "VIDEO_RECORD"
        4L -> "PREVIEW_VIDEO_STILL"
        5L -> "VIDEO_CALL"
        6L -> "CROPPED_RAW"
        else -> "0x${useCase.toString(16)}"
    }

    // ── Extended scene modes (Android 16+, accessed via reflection) ──

    /**
     * Construct [CameraCharacteristics.Key] for `CONTROL_EXTENDED_SCENE_MODE_AVAILABLE_MODES`
     * via reflection. The constant was added in API 36, but this project compiles against SDK 35.
     * Reflection allows probing on devices that report the key at runtime (e.g. vivo V2509A).
     */
    private fun extendedSceneModeKey(): CameraCharacteristics.Key<IntArray>? {
        return try {
            val clazz = Class.forName("android.hardware.camera2.CameraCharacteristics\$Control")
            val field = clazz.getDeclaredField("CONTROL_EXTENDED_SCENE_MODE_AVAILABLE_MODES")
            @Suppress("UNCHECKED_CAST")
            field.get(null) as CameraCharacteristics.Key<IntArray>
        } catch (_: Exception) {
            // Not available under current compileSdk or runtime — probe-only, non-fatal
            null
        }
    }

    private fun appendExtendedSceneModeProbe(sb: StringBuilder, chars: CameraCharacteristics) {
        val key = extendedSceneModeKey()
            ?: throw UnsupportedOperationException(
                "CONTROL_EXTENDED_SCENE_MODE_AVAILABLE_MODES not accessible under compileSdk 35"
            )

        val modes = chars.get(key) ?: intArrayOf()

        sb.appendLine("  available-extended-scene-modes: ${modes.joinToString(", ") { extendedSceneModeLabel(it) }}")
        sb.appendLine("  extended-scene-mode-count: ${modes.size}")

        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (configMap != null) {
            val maxStreaming = configMap.getOutputSizes(ImageFormat.JPEG)
                ?.maxByOrNull { it.width * it.height }
            if (maxStreaming != null) {
                sb.appendLine("  max-streaming-size: ${maxStreaming.width}×${maxStreaming.height}")
            }
        }

        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        if (zoomRange != null) {
            sb.appendLine("  zoom-ratio-range: [${zoomRange.lower}, ${zoomRange.upper}]")
        }
    }

    private fun extendedSceneModeLabel(mode: Int): String = when (mode) {
        0 -> "disabled"
        1 -> "bokeh-still-capture"
        2 -> "bokeh-continuous"
        else -> {
            if (mode >= 0x40) "vendor-0x${mode.toString(16)}"
            else "unknown-0x${mode.toString(16)}"
        }
    }

    // ── RAW capability ───────────────────────────────────────

    private fun appendRawCapability(sb: StringBuilder, chars: CameraCharacteristics) {
        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return

        sb.appendLine("  [raw-capability]")

        for ((format, name) in rawImageFormats) {
            val sizes = configMap.getOutputSizes(format)
            val highResSizes = configMap.getHighResolutionOutputSizes(format)

            if (sizes.isNullOrEmpty() && highResSizes.isNullOrEmpty()) continue

            val parts = mutableListOf<String>()
            if (!sizes.isNullOrEmpty()) {
                val max = sizes.maxByOrNull { it.width * it.height }
                parts += "standard=[${sizes.size} sizes, max=${max!!.width}×${max.height}]"
            }
            if (!highResSizes.isNullOrEmpty()) {
                val max = highResSizes.maxByOrNull { it.width * it.height }
                parts += "high-res=[${highResSizes.size} sizes, max=${max!!.width}×${max.height}]"
            }
            sb.appendLine("    $name: ${parts.joinToString(" | ")}")
        }

        val supportsRaw = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
        sb.appendLine("    supports-capture-raw: $supportsRaw")

        val maxJpeg = configMap.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }
        val maxHighResJpeg = configMap.getHighResolutionOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width * it.height }
        sb.appendLine("    max-jpeg-output: ${maxJpeg?.let { "${it.width}×${it.height}" } ?: "none"}")
        sb.appendLine("    max-high-res-jpeg-output: ${maxHighResJpeg?.let { "${it.width}×${it.height}" } ?: "none"}")
    }

    // ── All characteristics keys ─────────────────────────────

    private fun appendAllCharacteristicsKeys(sb: StringBuilder, chars: CameraCharacteristics) {
        val allKeys = chars.keys
        val standardKeys = mutableListOf<String>()
        val vendorKeys = mutableListOf<String>()

        for (key in allKeys) {
            val keyName = key.name
            val value = chars.get(key)
            val formatted = formatKeyValue(keyName, value)

            if (isVendorKeyName(keyName)) {
                vendorKeys += "  $formatted"
            } else {
                standardKeys += "  $formatted"
            }
        }

        if (standardKeys.isNotEmpty()) {
            sb.appendLine("  [standard-keys] (${standardKeys.size})")
            standardKeys.forEach { sb.appendLine(it) }
        }

        if (vendorKeys.isNotEmpty()) {
            sb.appendLine("  [vendor-keys] (${vendorKeys.size})")
            vendorKeys.forEach { sb.appendLine(it) }
        }
    }

    // ── Hidden vendor keys via reflection ─────────────────────

    private fun appendVendorKeysViaReflection(sb: StringBuilder, chars: CameraCharacteristics) {
        val found = mutableListOf<String>()

        for (className in knownVendorKeyClasses) {
            try {
                val clazz = Class.forName(className)
                for (field in clazz.declaredFields) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val fieldType = field.type
                        if (CameraCharacteristics.Key::class.java.isAssignableFrom(fieldType)) {
                            field.isAccessible = true
                            val key = field.get(null) as CameraCharacteristics.Key<Any>
                            val value = chars.get(key)
                            found += "  [$className] ${field.name} = ${formatAnyValue(value)}"
                        }
                    } catch (_: Exception) {
                        // skip individual field access failures
                    }
                }
            } catch (_: ClassNotFoundException) {
                // class not on this device, skip
            } catch (e: Exception) {
                found += "  [$className] LOAD_FAILED: ${e.message}"
            }
        }

        // Also try to probe hidden StreamConfigurationMap methods
        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (configMap != null) {
            try {
                val scmClass = configMap.javaClass
                for (method in scmClass.declaredMethods) {
                    val methodName = method.name
                    if (methodName.contains("HighRes") || methodName.contains("Vendor") ||
                        methodName.contains("Raw") || methodName.contains("10") ||
                        methodName.contains("12") || methodName.contains("Private") ||
                        methodName.contains("remosaic") || methodName.contains("Remosaic")
                    ) {
                        try {
                            method.isAccessible = true
                            val result = if (method.parameterTypes.isEmpty()) {
                                method.invoke(configMap)
                            } else if (method.parameterTypes.size == 1 &&
                                method.parameterTypes[0] == Int::class.javaPrimitiveType
                            ) {
                                if (methodName.contains("HighRes") || methodName.contains("Remosaic")) {
                                    method.invoke(configMap, ImageFormat.JPEG)
                                } else {
                                    method.invoke(configMap, ImageFormat.RAW_SENSOR)
                                }
                            } else {
                                continue
                            }
                            val resultStr = when (result) {
                                is Array<*> -> result.joinToString { formatAnyValue(it) }
                                null -> "null"
                                else -> result.toString()
                            }
                            found += "  [SCM-hidden] $methodName = $resultStr"
                        } catch (_: Exception) {
                            // method invocation failed, skip
                        }
                    }
                }
            } catch (_: Exception) {
                // SCM reflection failed
            }
        }

        if (found.isNotEmpty()) {
            sb.appendLine("  [hidden-via-reflection] (${found.size})")
            found.forEach { sb.appendLine(it) }
        }
    }

    // ── Available keys ───────────────────────────────────────

    /**
     * Safe accessor: some vendors (e.g. vivo/MTK) return null from these
     * properties despite the SDK declaring them @NonNull. The compiler warns
     * about elvis on "non-null" types — this helper bypasses that via try/catch.
     */
    private fun <T> CameraCharacteristics.safeList(
        getter: CameraCharacteristics.() -> List<T>
    ): List<T> = try { getter() } catch (_: Exception) { emptyList() }

    private fun appendAvailableKeys(sb: StringBuilder, chars: CameraCharacteristics) {
        val captureRequestKeys = chars.safeList { availableCaptureRequestKeys }
        if (captureRequestKeys.isNotEmpty()) {
            sb.appendLine("  [available-capture-request-keys] (${captureRequestKeys.size})")
            val vendor = captureRequestKeys.filter { isVendorKeyName(it.name) }
            val std = captureRequestKeys.filter { it !in vendor }
            std.forEach { sb.appendLine("    ${it.name}") }
            if (vendor.isNotEmpty()) {
                sb.appendLine("    --- vendor request keys ---")
                vendor.forEach { sb.appendLine("    ${it.name}") }
            }
        }

        val resultKeys = chars.safeList { availableCaptureResultKeys }
        if (resultKeys.isNotEmpty()) {
            sb.appendLine("  [available-capture-result-keys] (${resultKeys.size})")
            val vendor = resultKeys.filter { isVendorKeyName(it.name) }
            val std = resultKeys.filter { it !in vendor }
            std.forEach { sb.appendLine("    ${it.name}") }
            if (vendor.isNotEmpty()) {
                sb.appendLine("    --- vendor result keys ---")
                vendor.forEach { sb.appendLine("    ${it.name}") }
            }
        }

        val sessionKeys = chars.safeList { availableSessionKeys }
        if (sessionKeys.isNotEmpty()) {
            sb.appendLine("  [available-session-keys] (${sessionKeys.size})")
            val vendor = sessionKeys.filter { isVendorKeyName(it.name) }
            val std = sessionKeys.filter { it !in vendor }
            std.forEach { sb.appendLine("    ${it.name}") }
            if (vendor.isNotEmpty()) {
                sb.appendLine("    --- vendor session keys ---")
                vendor.forEach { sb.appendLine("    ${it.name}") }
            }
        }

        val physicalRequestKeys = chars.safeList { availablePhysicalCameraRequestKeys }
        if (physicalRequestKeys.isNotEmpty()) {
            sb.appendLine("  [available-physical-camera-request-keys] (${physicalRequestKeys.size})")
            physicalRequestKeys.forEach { sb.appendLine("    ${it.name}") }
        }
    }

    private fun isVendorKeyName(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return normalized.startsWith("org.") ||
            normalized.startsWith("com.") ||
            "vendor" in normalized ||
            "extension" in normalized ||
            "xiaomi" in normalized ||
            "qualcomm" in normalized ||
            "samsung" in normalized ||
            "mediatek" in normalized ||
            "oppo" in normalized ||
            "vivo" in normalized ||
            "oneplus" in normalized ||
            "huawei" in normalized
    }

    private fun StreamConfigurationMap?.hasAnyOutput(vararg formats: Int): Boolean {
        return this != null && formats.any { outputSizes(it, highResolution = false).isNotEmpty() }
    }

    private fun StreamConfigurationMap?.outputSizes(
        format: Int,
        highResolution: Boolean
    ): List<Size> {
        if (this == null) return emptyList()
        return runCatching {
            val sizes = if (highResolution) {
                getHighResolutionOutputSizes(format)
            } else {
                getOutputSizes(format)
            }
            sizes?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    // ── Value formatting ─────────────────────────────────────

    private fun formatKeyValue(name: String, value: Any?): String {
        return "$name = ${formatAnyValue(value)}"
    }

    private fun formatAnyValue(value: Any?): String = when (value) {
        null -> "null"
        is IntArray -> value.contentToString()
        is LongArray -> value.contentToString()
        is FloatArray -> value.contentToString()
        is DoubleArray -> value.contentToString()
        is ByteArray -> value.contentToString()
        is Array<*> -> value.contentDeepToString()
        is Size -> "${value.width}×${value.height}"
        is android.util.SizeF -> "${value.width}×${value.height}"
        is android.graphics.Rect -> "Rect(${value.left},${value.top},${value.right},${value.bottom})"
        is android.util.Range<*> -> "[${value.lower}, ${value.upper}]"
        is StreamConfigurationMap -> formatStreamConfigMap(value)
        is Boolean -> value.toString()
        is Int -> value.toString()
        is Long -> value.toString()
        is Float -> "%.3f".format(value)
        is Double -> "%.3f".format(value)
        is String -> "\"$value\""
        else -> value.toString()
    }

    private fun formatStreamConfigMap(scm: StreamConfigurationMap): String {
        val parts = mutableListOf<String>()

        listOf(
            ImageFormat.JPEG to "JPEG",
            ImageFormat.YUV_420_888 to "YUV_420_888",
            ImageFormat.RAW_SENSOR to "RAW_SENSOR",
            ImageFormat.RAW10 to "RAW10",
            ImageFormat.RAW12 to "RAW12",
            ImageFormat.RAW_PRIVATE to "RAW_PRIVATE",
            ImageFormat.YV12 to "YV12",
            ImageFormat.NV21 to "NV21",
            ImageFormat.HEIC to "HEIC",
            ImageFormat.DEPTH_JPEG to "DEPTH_JPEG",
            ImageFormat.DEPTH16 to "DEPTH16",
            ImageFormat.DEPTH_POINT_CLOUD to "DEPTH_POINT_CLOUD",
        ).forEach { (format, label) ->
            val sizes = scm.getOutputSizes(format)
            val highResSizes = scm.getHighResolutionOutputSizes(format)
            if (!sizes.isNullOrEmpty()) {
                val max = sizes.maxByOrNull { it.width * it.height }
                parts += "$label=${sizes.size}sizes(max=${max!!.width}×${max.height})"
            }
            if (!highResSizes.isNullOrEmpty()) {
                val max = highResSizes.maxByOrNull { it.width * it.height }
                parts += "HIGH_RES_$label=${highResSizes.size}sizes(max=${max!!.width}×${max.height})"
            }
        }
        return "SCM{${parts.joinToString("; ")}}"
    }

    // ── Label helpers ────────────────────────────────────────

    private fun lensFacingLabel(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($facing)"
    }

    private fun hardwareLevelLabel(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($level)"
    }

    private fun capabilityLabel(cap: Int): String = when (cap) {
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "HIGH_SPEED_VIDEO"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "OFFLINE_PROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RESOLUTION"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING -> "REMOSAIC_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT -> "DYNAMIC_RANGE_10BIT"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE -> "STREAM_USE_CASE"
        else -> "0x${cap.toString(16)}"
    }

    private fun cfaLabel(cfa: Int): String = when (cfa) {
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> "RGGB (Bayer)"
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> "GRBG"
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> "GBRG"
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> "BGGR"
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB -> "RGB (non-Bayer)"
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO -> "MONO"
        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR -> "NIR"
        else -> "UNKNOWN($cfa)"
    }

    private fun tsSourceLabel(src: Int): String = when (src) {
        CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
        CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
        else -> "UNKNOWN($src)"
    }

    private fun oisLabel(mode: Int): String = when (mode) {
        CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
        CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
        else -> "0x${mode.toString(16)}"
    }

    private fun aeModeLabel(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_AE_MODE_OFF -> "OFF"
        CameraCharacteristics.CONTROL_AE_MODE_ON -> "ON"
        CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH -> "AUTO_FLASH"
        CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ALWAYS_FLASH"
        CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "AUTO_FLASH_REDEYE"
        CameraCharacteristics.CONTROL_AE_MODE_ON_EXTERNAL_FLASH -> "EXTERNAL_FLASH"
        else -> "0x${mode.toString(16)}"
    }

    private fun afModeLabel(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_AF_MODE_OFF -> "OFF"
        CameraCharacteristics.CONTROL_AF_MODE_AUTO -> "AUTO"
        CameraCharacteristics.CONTROL_AF_MODE_MACRO -> "MACRO"
        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
        CameraCharacteristics.CONTROL_AF_MODE_EDOF -> "EDOF"
        else -> "0x${mode.toString(16)}"
    }

    private fun awbModeLabel(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_AWB_MODE_OFF -> "OFF"
        CameraCharacteristics.CONTROL_AWB_MODE_AUTO -> "AUTO"
        CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"
        CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"
        CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM_FLUORESCENT"
        CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"
        CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY_DAYLIGHT"
        CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"
        CameraCharacteristics.CONTROL_AWB_MODE_SHADE -> "SHADE"
        else -> "0x${mode.toString(16)}"
    }

    private fun videoStabilizationLabel(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
        CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
        CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION -> "PREVIEW_STABILIZATION"
        else -> "0x${mode.toString(16)}"
    }

    private fun faceDetectModeLabel(mode: Int): String = when (mode) {
        CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF -> "OFF"
        CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE -> "SIMPLE"
        CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_FULL -> "FULL"
        else -> "0x${mode.toString(16)}"
    }

    private fun isLogical(chars: CameraCharacteristics): Boolean {
        return chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
    }

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))
    }

    // ── Sensor math ──────────────────────────────────────────

    private fun focalLengthCropFactor(chars: CameraCharacteristics): Float {
        val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 1f
        return focalLengthCropFactor(physicalSize)
    }
}

internal fun appendCameraExtensionProbe(
    sb: StringBuilder,
    report: CameraExtensionProbeReport
) {
    sb.append(report.toProbeText())
}

private fun CameraExtensionProbeReport.diagnosticSummary(): String {
    return listOf(summaryLine(), imageQualitySummaryLine()).joinToString(separator = "\n")
}

internal fun formatPhysicalSensorSize(physicalSize: SizeF): String {
    val diagMm = Math.sqrt(
        physicalSize.width.toDouble() * physicalSize.width +
            physicalSize.height.toDouble() * physicalSize.height
    )
    val inchNotation = diagToInchNotation(diagMm)
    return "physical-size: ${physicalSize.width}mm × ${physicalSize.height}mm (~$inchNotation)"
}

internal fun formatSensorPixelSize(
    physicalSize: SizeF,
    pixelArraySize: Size
): String {
    val pixelWidth = physicalSize.width.toDouble() * 1000.0 / pixelArraySize.width
    val pixelHeight = physicalSize.height.toDouble() * 1000.0 / pixelArraySize.height
    return "pixel-size: %.2fμm × %.2fμm".format(pixelWidth, pixelHeight)
}

internal fun focalLengthCropFactor(physicalSize: SizeF): Float {
    val diagMm = Math.sqrt(
        physicalSize.width.toDouble() * physicalSize.width +
            physicalSize.height.toDouble() * physicalSize.height
    )
    return (43.27f / diagMm).toFloat()
}

private fun diagToInchNotation(diagMm: Double): String {
    val inches = diagMm / 25.4
    return when {
        inches >= 1.0 -> "1/${"%.2f".format(1.0 / inches)}\""
        else -> "%.2f\"".format(inches)
    }
}
