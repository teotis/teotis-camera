package com.opencamera.app.camera.live

import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.settings.LiveSaveFormat

sealed class LivePhotoStatus {
    data class Materialized(val format: LiveSaveFormat) : LivePhotoStatus()
    data class Degraded(val format: LiveSaveFormat, val reason: String) : LivePhotoStatus()
    data object NotRequested : LivePhotoStatus()
    data object Unknown : LivePhotoStatus()
}

object LivePhotoStatusProjection {

    fun project(
        pipelineNotes: List<String>,
        bundle: LivePhotoBundle?,
        saveFormat: LiveSaveFormat
    ): LivePhotoStatus {
        if (saveFormat == LiveSaveFormat.STILL_JPEG_ONLY) {
            return LivePhotoStatus.NotRequested
        }

        if (saveFormat == LiveSaveFormat.MOTION_MP4_SIDECAR) {
            val sidecarValues = pipelineNotes
                .filter { it.startsWith("motion-photo:sidecar-mp4=") }
                .map { it.substringAfter("motion-photo:sidecar-mp4=") }
            val hasInserted = sidecarValues.any { it == "mediastore-inserted" }
            val hasFailed = sidecarValues.any {
                it == "mediastore-skipped" || it.startsWith("mediastore-failed")
            }
            when {
                hasInserted -> return LivePhotoStatus.Materialized(saveFormat)
                hasFailed -> return LivePhotoStatus.Degraded(saveFormat, "sidecar-mp4-not-inserted")
            }
        }

        val statusLine = pipelineNotes.firstOrNull { it.startsWith("live-motion:status=") }
        val motionStatus = statusLine?.substringAfter("=")

        return when (motionStatus) {
            "materialized" -> {
                val actualFormat = extractFormatActual(pipelineNotes)
                LivePhotoStatus.Materialized(actualFormat ?: saveFormat)
            }
            "failed" -> {
                val actualFormat = extractFormatActual(pipelineNotes)
                LivePhotoStatus.Degraded(actualFormat ?: saveFormat, "motion-segment-failed")
            }
            "degraded" -> {
                val actualFormat = extractFormatActual(pipelineNotes)
                LivePhotoStatus.Degraded(actualFormat ?: saveFormat, "sidecar-failed")
            }
            "missing" -> {
                val actualFormat = extractFormatActual(pipelineNotes)
                LivePhotoStatus.Degraded(actualFormat ?: saveFormat, "motion-segment-missing")
            }
            else -> {
                if (bundle != null && bundle.bundleStatus != LiveBundleStatus.STILL_ONLY_FALLBACK) {
                    LivePhotoStatus.Materialized(saveFormat)
                } else if (bundle == null) {
                    LivePhotoStatus.Unknown
                } else {
                    LivePhotoStatus.Degraded(saveFormat, "unknown-reason")
                }
            }
        }
    }

    fun statusText(status: LivePhotoStatus, formatLabel: String? = null, pipelineNotes: List<String> = emptyList()): String? {
        return when (status) {
            is LivePhotoStatus.Materialized -> {
                val label = formatLabel ?: status.format.label
                val recognition = recognitionNote(pipelineNotes)
                if (recognition != null) {
                    "实况已生成（$label）\n$recognition"
                } else {
                    "实况已生成（$label）"
                }
            }
            is LivePhotoStatus.Degraded -> {
                val label = formatLabel ?: status.format.label
                "实况降级为静态照片：${reasonText(status.reason)}"
            }
            LivePhotoStatus.NotRequested -> null
            LivePhotoStatus.Unknown -> "实况状态未知，请查看诊断日志"
        }
    }

    /**
     * Renders the local container-validation outcome. Never claims that a system
     * gallery recognized the photo: external recognition stays pending real-device
     * evidence (01-forensic-baseline gate).
     */
    fun recognitionNote(pipelineNotes: List<String>): String? {
        val containerStatus = pipelineNotes
            .firstOrNull { it.startsWith("gallery-recognition=") }
            ?.substringAfter("gallery-recognition=")
        val externalPending = pipelineNotes.any { it == "gallery-recognition:external=pending" }
        return when {
            containerStatus == "container-validated" ->
                if (externalPending) "容器结构已验证 · 系统相册识别待真机验证" else "容器结构已验证"
            containerStatus != null && containerStatus.startsWith("container-invalid") -> {
                val reason = containerStatus.removePrefix("container-invalid:")
                "容器结构异常：${reason.ifEmpty { "未知原因" }}"
            }
            containerStatus == "container-unchecked" -> "容器结构未验证"
            containerStatus == "not-materialized" -> "实况容器未生成"
            else -> null
        }
    }

    private fun reasonText(reason: String): String = when (reason) {
        "motion-segment-failed" -> "动态片段编码失败"
        "motion-segment-missing" -> "动态片段缺失"
        "sidecar-failed" -> "附属文件写入失败"
        "sidecar-mp4-not-inserted" -> "附属 MP4 写入相册失败"
        "container-failed" -> "容器写入失败"
        "frame-source-not-active" -> "帧源未激活"
        "unknown-reason" -> "未知原因"
        else -> reason
    }

    private fun extractFormatActual(pipelineNotes: List<String>): LiveSaveFormat? {
        val line = pipelineNotes.firstOrNull { it.startsWith("live-format:actual=") } ?: return null
        val key = line.substringAfter("=")
        return LiveSaveFormat.fromStorageKey(key)
    }
}
