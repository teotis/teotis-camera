package com.opencamera.app.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.TimeUnit

internal enum class CameraExtensionLensFacing(val label: String) {
    BACK("BACK"),
    FRONT("FRONT")
}

internal enum class CameraExtensionMode(
    val label: String
) {
    NIGHT("night"),
    HDR("hdr"),
    BOKEH("bokeh"),
    AUTO("auto"),
    FACE_RETOUCH("face-retouch")
}

internal enum class CameraExtensionSupport(
    val label: String
) {
    SUPPORTED("supported"),
    UNSUPPORTED("unsupported"),
    SELECTOR_ERROR("selector-error"),
    MANAGER_UNAVAILABLE("manager-unavailable"),
    QUERY_ERROR("query-error")
}

internal data class CameraExtensionProbeEntry(
    val lensFacing: CameraExtensionLensFacing,
    val mode: CameraExtensionMode,
    val support: CameraExtensionSupport,
    val detail: String? = null
) {
    fun compact(): String = "${mode.label}=${support.label}"

    fun detailed(): String {
        return buildString {
            append("${mode.label}: ${support.label}")
            if (!detail.isNullOrBlank()) {
                append(": ")
                append(detail)
            }
        }
    }
}

internal data class CameraExtensionProbeReport(
    val entries: List<CameraExtensionProbeEntry>
) {
    fun entry(
        lensFacing: CameraExtensionLensFacing,
        mode: CameraExtensionMode
    ): CameraExtensionProbeEntry? {
        return entries.firstOrNull { it.lensFacing == lensFacing && it.mode == mode }
    }

    fun summaryLine(): String {
        val lensSummaries = CameraExtensionLensFacing.entries.map { lens ->
            val modeSummaries = CameraExtensionMode.entries.map { mode ->
                entry(lens, mode)?.compact() ?: "${mode.label}=unknown"
            }
            "${lens.label} ${modeSummaries.joinToString(" ")}"
        }
        return "extensions: ${lensSummaries.joinToString(" | ")}"
    }

    fun imageQualitySummaryLine(): String {
        val backHdr = entry(CameraExtensionLensFacing.BACK, CameraExtensionMode.HDR)
            ?.support
            ?.label
            ?: "unknown"
        val backNight = entry(CameraExtensionLensFacing.BACK, CameraExtensionMode.NIGHT)
            ?.support
            ?.label
            ?: "unknown"
        return "image-quality-ext: blue-hour-hdr=$backHdr low-light-night=$backNight"
    }

    fun toProbeText(): String {
        return buildString {
            appendLine("  [camera-extensions]")
            appendLine("    ${summaryLine()}")
            appendLine("    ${imageQualitySummaryLine()}")
            CameraExtensionLensFacing.entries.forEach { lens ->
                appendLine("    ${lens.label}:")
                CameraExtensionMode.entries.forEach { mode ->
                    val current = entry(lens, mode)
                    appendLine("      ${current?.detailed() ?: "${mode.label}: unknown"}")
                }
            }
        }
    }
}

internal class CameraExtensionManagerUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

internal interface CameraExtensionProbeClient {
    fun isExtensionAvailable(
        lensFacing: CameraExtensionLensFacing,
        mode: CameraExtensionMode
    ): Boolean

    fun verifyExtensionSelector(
        lensFacing: CameraExtensionLensFacing,
        mode: CameraExtensionMode
    )
}

internal object CameraExtensionProbe {
    fun probe(context: Context): CameraExtensionProbeReport {
        return runCatching {
            val provider = ProcessCameraProvider.getInstance(context)
                .get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val manager = ExtensionsManager.getInstanceAsync(context, provider)
                .get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            probe(AndroidCameraExtensionProbeClient(manager))
        }.getOrElse { error ->
            managerUnavailableReport(probeErrorMessage(error))
        }
    }

    fun probe(client: CameraExtensionProbeClient): CameraExtensionProbeReport {
        val entries = mutableListOf<CameraExtensionProbeEntry>()
        for (lensFacing in CameraExtensionLensFacing.entries) {
            for (mode in CameraExtensionMode.entries) {
                val entry = try {
                    if (!client.isExtensionAvailable(lensFacing, mode)) {
                        CameraExtensionProbeEntry(
                            lensFacing = lensFacing,
                            mode = mode,
                            support = CameraExtensionSupport.UNSUPPORTED
                        )
                    } else {
                        verifySelector(client, lensFacing, mode)
                    }
                } catch (error: CameraExtensionManagerUnavailableException) {
                    return managerUnavailableReport(probeErrorMessage(error))
                } catch (error: Throwable) {
                    CameraExtensionProbeEntry(
                        lensFacing = lensFacing,
                        mode = mode,
                        support = CameraExtensionSupport.QUERY_ERROR,
                        detail = probeErrorMessage(error)
                    )
                }
                entries += entry
            }
        }
        return CameraExtensionProbeReport(entries)
    }

    private fun verifySelector(
        client: CameraExtensionProbeClient,
        lensFacing: CameraExtensionLensFacing,
        mode: CameraExtensionMode
    ): CameraExtensionProbeEntry {
        return runCatching {
            client.verifyExtensionSelector(lensFacing, mode)
            CameraExtensionProbeEntry(
                lensFacing = lensFacing,
                mode = mode,
                support = CameraExtensionSupport.SUPPORTED
            )
        }.getOrElse { error ->
            CameraExtensionProbeEntry(
                lensFacing = lensFacing,
                mode = mode,
                support = CameraExtensionSupport.SELECTOR_ERROR,
                detail = probeErrorMessage(error)
            )
        }
    }

    private fun managerUnavailableReport(reason: String): CameraExtensionProbeReport {
        return CameraExtensionProbeReport(
            entries = CameraExtensionLensFacing.entries.flatMap { lensFacing ->
                CameraExtensionMode.entries.map { mode ->
                    CameraExtensionProbeEntry(
                        lensFacing = lensFacing,
                        mode = mode,
                        support = CameraExtensionSupport.MANAGER_UNAVAILABLE,
                        detail = reason
                    )
                }
            }
        )
    }
}

private class AndroidCameraExtensionProbeClient(
    private val manager: ExtensionsManager
) : CameraExtensionProbeClient {
    override fun isExtensionAvailable(
        lensFacing: CameraExtensionLensFacing,
        mode: CameraExtensionMode
    ): Boolean {
        return manager.isExtensionAvailable(lensFacing.toCameraSelector(), mode.toExtensionMode())
    }

    override fun verifyExtensionSelector(
        lensFacing: CameraExtensionLensFacing,
        mode: CameraExtensionMode
    ) {
        manager.getExtensionEnabledCameraSelector(
            lensFacing.toCameraSelector(),
            mode.toExtensionMode()
        )
    }
}

private fun CameraExtensionLensFacing.toCameraSelector(): CameraSelector {
    return when (this) {
        CameraExtensionLensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraExtensionLensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
}

private fun CameraExtensionMode.toExtensionMode(): Int {
    return when (this) {
        CameraExtensionMode.NIGHT -> ExtensionMode.NIGHT
        CameraExtensionMode.HDR -> ExtensionMode.HDR
        CameraExtensionMode.BOKEH -> ExtensionMode.BOKEH
        CameraExtensionMode.AUTO -> ExtensionMode.AUTO
        CameraExtensionMode.FACE_RETOUCH -> ExtensionMode.FACE_RETOUCH
    }
}

private const val PROBE_TIMEOUT_SECONDS = 3L
