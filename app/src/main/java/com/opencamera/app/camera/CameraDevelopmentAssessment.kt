package com.opencamera.app.camera

internal enum class CameraDevelopmentReadiness(val label: String) {
    READY("READY"),
    VERIFY_ON_DEVICE("VERIFY_ON_DEVICE"),
    UNAVAILABLE("UNAVAILABLE")
}

internal data class CameraDevelopmentFacts(
    val hardwareLevel: String,
    val physicalCameraCount: Int = 0,
    val supportsManualSensor: Boolean = false,
    val supportsManualPostProcessing: Boolean = false,
    val supportsRaw: Boolean = false,
    val supportsBurst: Boolean = false,
    val supportsYuvReprocessing: Boolean = false,
    val supportsPrivateReprocessing: Boolean = false,
    val supportsRemosaicReprocessing: Boolean = false,
    val supportsDepth: Boolean = false,
    val supportsHighSpeedVideo: Boolean = false,
    val supportsTenBitDynamicRange: Boolean = false,
    val supportsUltraHighResolution: Boolean = false,
    val supportsStreamUseCases: Boolean = false,
    val supportsOfflineProcessing: Boolean = false,
    val hasIsoRange: Boolean = false,
    val hasExposureTimeRange: Boolean = false,
    val hasManualFocusDistance: Boolean = false,
    val hasRawOutput: Boolean = false,
    val hasYuvOutput: Boolean = false,
    val hasHighResolutionJpeg: Boolean = false,
    val hasOpticalStabilization: Boolean = false,
    val hasVideoStabilization: Boolean = false,
    val vendorRequestKeyCount: Int = 0,
    val vendorResultKeyCount: Int = 0,
    val physicalRequestKeyCount: Int = 0
)

internal data class CameraDevelopmentOpportunity(
    val id: String,
    val title: String,
    val readiness: CameraDevelopmentReadiness,
    val evidence: String,
    val use: String
)

internal fun assessCameraDevelopmentFeatures(
    facts: CameraDevelopmentFacts
): List<CameraDevelopmentOpportunity> {
    val manualExposureReady = facts.supportsManualSensor && facts.hasIsoRange && facts.hasExposureTimeRange
    val rawReady = facts.supportsRaw && facts.hasRawOutput
    val multiFrameReadiness = when {
        facts.supportsBurst && facts.hasYuvOutput -> CameraDevelopmentReadiness.READY
        facts.hasYuvOutput -> CameraDevelopmentReadiness.VERIFY_ON_DEVICE
        else -> CameraDevelopmentReadiness.UNAVAILABLE
    }
    val reprocessReady = facts.supportsYuvReprocessing ||
        facts.supportsPrivateReprocessing ||
        facts.supportsRemosaicReprocessing
    val ultraHighResolutionReadiness = when {
        facts.supportsUltraHighResolution &&
            (facts.hasHighResolutionJpeg || facts.supportsRemosaicReprocessing) -> CameraDevelopmentReadiness.READY
        facts.hasHighResolutionJpeg -> CameraDevelopmentReadiness.VERIFY_ON_DEVICE
        else -> CameraDevelopmentReadiness.UNAVAILABLE
    }
    val vendorKeyCount = facts.vendorRequestKeyCount + facts.vendorResultKeyCount

    return listOf(
        CameraDevelopmentOpportunity(
            id = "manual-exposure",
            title = "手动曝光",
            readiness = if (manualExposureReady) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = buildString {
                append("hardware=${facts.hardwareLevel}; MANUAL_SENSOR=${facts.supportsManualSensor}; ")
                append("ISO-range=${facts.hasIsoRange}; exposure-range=${facts.hasExposureTimeRange}; ")
                append("manual-post=${facts.supportsManualPostProcessing}")
            },
            use = "驱动专业模式的 ISO、快门和曝光策略；每一项仍需按可用请求键单独启用"
        ),
        CameraDevelopmentOpportunity(
            id = "manual-focus",
            title = "手动对焦",
            readiness = if (facts.hasManualFocusDistance) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "minimum-focus-distance=${facts.hasManualFocusDistance}",
            use = "驱动焦距滑杆、焦点包围和近远景堆栈；固定焦镜头不会被误报为可手动对焦"
        ),
        CameraDevelopmentOpportunity(
            id = "raw-pipeline",
            title = "RAW / DNG 管线",
            readiness = if (rawReady) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "RAW-capability=${facts.supportsRaw}; RAW-output=${facts.hasRawOutput}",
            use = "保存传感器数据、离线白平衡/降噪/色彩研究，并作为 JPEG 算法质量对照"
        ),
        CameraDevelopmentOpportunity(
            id = "multi-frame-yuv",
            title = "YUV 多帧计算摄影",
            readiness = multiFrameReadiness,
            evidence = "BURST=${facts.supportsBurst}; YUV-output=${facts.hasYuvOutput}; offline=${facts.supportsOfflineProcessing}",
            use = "夜景堆栈、HDR、超分、去噪和景深融合；必须用持续吞吐、帧时间戳与内存压力实测收口"
        ),
        CameraDevelopmentOpportunity(
            id = "logical-multi-camera",
            title = "逻辑多摄与物理镜头",
            readiness = if (facts.physicalCameraCount > 0) {
                CameraDevelopmentReadiness.VERIFY_ON_DEVICE
            } else {
                CameraDevelopmentReadiness.UNAVAILABLE
            },
            evidence = "physical-camera-count=${facts.physicalCameraCount}; physical-request-keys=${facts.physicalRequestKeyCount}",
            use = "识别超广角/主摄/长焦节点，研究镜头切换、双摄同步和融合；第三方应用可见 ID 不等于可同时开流"
        ),
        CameraDevelopmentOpportunity(
            id = "ultra-high-resolution",
            title = "超高像素与 remosaic",
            readiness = ultraHighResolutionReadiness,
            evidence = "ultra-high-resolution=${facts.supportsUltraHighResolution}; high-res-JPEG=${facts.hasHighResolutionJpeg}; remosaic=${facts.supportsRemosaicReprocessing}",
            use = "提供高像素静态照片或裁切余量；需验证输出是否真实全分辨率、帧率、内存和厂商处理限制"
        ),
        CameraDevelopmentOpportunity(
            id = "reprocessing",
            title = "相机内重处理",
            readiness = if (reprocessReady) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "YUV=${facts.supportsYuvReprocessing}; PRIVATE=${facts.supportsPrivateReprocessing}; remosaic=${facts.supportsRemosaicReprocessing}",
            use = "把已有帧重新送入相机管线，探索低延迟降噪、ZSL 与厂商 ISP 路径；CameraX 未必直接暴露"
        ),
        CameraDevelopmentOpportunity(
            id = "ten-bit-video",
            title = "10-bit HDR 视频",
            readiness = if (facts.supportsTenBitDynamicRange) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "dynamic-range-10bit=${facts.supportsTenBitDynamicRange}; stream-use-cases=${facts.supportsStreamUseCases}",
            use = "尝试 HLG10/HDR10 等 HDR 采集与更宽调色空间；编码器、Surface 组合和播放器链路需分别验证"
        ),
        CameraDevelopmentOpportunity(
            id = "high-speed-video",
            title = "高帧率视频",
            readiness = if (facts.supportsHighSpeedVideo) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "constrained-high-speed=${facts.supportsHighSpeedVideo}",
            use = "枚举高帧率尺寸/FPS 组合，支持慢动作实验；高帧率会限制分辨率、流组合和部分控制项"
        ),
        CameraDevelopmentOpportunity(
            id = "depth-output",
            title = "景深输出",
            readiness = if (facts.supportsDepth) CameraDevelopmentReadiness.READY else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "depth-output=${facts.supportsDepth}",
            use = "为人像分层和测距提供平台深度数据；没有公开深度输出时应继续使用可解释的视觉分割降级"
        ),
        CameraDevelopmentOpportunity(
            id = "stabilization",
            title = "光学 / 电子稳定",
            readiness = if (facts.hasOpticalStabilization || facts.hasVideoStabilization) {
                CameraDevelopmentReadiness.READY
            } else CameraDevelopmentReadiness.UNAVAILABLE,
            evidence = "OIS=${facts.hasOpticalStabilization}; EIS=${facts.hasVideoStabilization}",
            use = "为视频稳定、手持夜景与长曝光选择设备路径；OIS 与 EIS 必须分别按模式和流组合验证"
        ),
        CameraDevelopmentOpportunity(
            id = "vendor-controls",
            title = "SoC 厂商控制面",
            readiness = if (vendorKeyCount > 0) {
                CameraDevelopmentReadiness.VERIFY_ON_DEVICE
            } else {
                CameraDevelopmentReadiness.UNAVAILABLE
            },
            evidence = "vendor-request-keys=${facts.vendorRequestKeyCount}; vendor-result-keys=${facts.vendorResultKeyCount}",
            use = "候选方向包括场景、降噪、HDR、remosaic 与镜头策略；键名只证明存在，必须先做只读记录与 A/B 实验，不能直接宣称可用"
        )
    )
}

internal fun formatCameraDevelopmentAssessment(
    opportunities: List<CameraDevelopmentOpportunity>
): String {
    return buildString {
        appendLine("  [development-opportunities]")
        appendLine("    legend: READY=平台证据充分 | VERIFY_ON_DEVICE=有入口但必须实机验证 | UNAVAILABLE=当前公开证据不足")
        opportunities.forEach { opportunity ->
            appendLine("    ${opportunity.readiness.label} ${opportunity.title} (${opportunity.id})")
            appendLine("      evidence: ${opportunity.evidence}")
            appendLine("      use: ${opportunity.use}")
        }
    }.trimEnd()
}
