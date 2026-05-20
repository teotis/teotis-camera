package com.opencamera.app.camera

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal sealed interface VideoWatermarkSubtitleWork {
    data object None : VideoWatermarkSubtitleWork
    data class Render(
        val target: VideoWatermarkSubtitleTarget,
        val watermarkText: String
    ) : VideoWatermarkSubtitleWork

    data class DiagnosticSkip(val reason: String) : VideoWatermarkSubtitleWork
}

internal sealed interface VideoWatermarkSubtitleTarget {
    data class FilePath(
        val videoPath: String
    ) : VideoWatermarkSubtitleTarget

    data class ContentUri(
        val value: String,
        val fileNameBase: String,
        val relativePath: String
    ) : VideoWatermarkSubtitleTarget
}

internal sealed interface VideoWatermarkSubtitleEditorResult {
    data class Applied(
        val subtitlePath: String,
        val warning: String? = null
    ) : VideoWatermarkSubtitleEditorResult

    data class Skipped(val reason: String) : VideoWatermarkSubtitleEditorResult
    data class Failed(val reason: String) : VideoWatermarkSubtitleEditorResult
}

internal interface VideoWatermarkSubtitleEditor {
    suspend fun apply(
        target: VideoWatermarkSubtitleTarget,
        watermarkText: String
    ): VideoWatermarkSubtitleEditorResult
}

internal fun decideVideoWatermarkSubtitleWork(result: ShotResult): VideoWatermarkSubtitleWork {
    if (result.mediaType != MediaType.VIDEO) {
        return VideoWatermarkSubtitleWork.None
    }
    if (!result.saveRequest.mimeType.equals("video/mp4", ignoreCase = true)) {
        return VideoWatermarkSubtitleWork.DiagnosticSkip("unsupported-mime")
    }
    val watermarkText = result.metadata.watermarkText
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return VideoWatermarkSubtitleWork.None

    val target = result.outputHandle.toVideoWatermarkSubtitleTargetOrNull(
        outputPath = result.outputPath,
        relativePath = result.saveRequest.relativePath
    ) ?: return VideoWatermarkSubtitleWork.DiagnosticSkip("missing-output-handle")

    return VideoWatermarkSubtitleWork.Render(
        target = target,
        watermarkText = watermarkText
    )
}

internal class VideoWatermarkSubtitlePostProcessor(
    private val editor: VideoWatermarkSubtitleEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decideVideoWatermarkSubtitleWork(result)) {
            VideoWatermarkSubtitleWork.None -> result
            is VideoWatermarkSubtitleWork.DiagnosticSkip -> result.withVideoWatermarkNotes(
                "video-watermark:skipped:${work.reason}"
            )

            is VideoWatermarkSubtitleWork.Render -> {
                when (val renderResult = editor.apply(work.target, work.watermarkText)) {
                    is VideoWatermarkSubtitleEditorResult.Applied -> {
                        val notes = buildList {
                            add("video-watermark:subtitle-created")
                            add("video-watermark:subtitle-path:${renderResult.subtitlePath}")
                            renderResult.warning?.let { add("video-watermark:warning:$it") }
                        }
                        result.copy(
                            intermediateOutputPaths = result.intermediateOutputPaths + renderResult.subtitlePath,
                            pipelineNotes = result.pipelineNotes + notes
                        )
                    }

                    is VideoWatermarkSubtitleEditorResult.Skipped -> result.withVideoWatermarkNotes(
                        "video-watermark:skipped:${renderResult.reason}"
                    )

                    is VideoWatermarkSubtitleEditorResult.Failed -> result.withVideoWatermarkNotes(
                        "video-watermark:failed:${renderResult.reason}"
                    )
                }
            }
        }
    }
}

internal class AndroidVideoWatermarkSubtitleEditor(
    context: Context
) : VideoWatermarkSubtitleEditor {
    private val appContext = context.applicationContext

    override suspend fun apply(
        target: VideoWatermarkSubtitleTarget,
        watermarkText: String
    ): VideoWatermarkSubtitleEditorResult = withContext(Dispatchers.IO) {
        val durationMillis = readDurationMillis(target)
            ?: return@withContext VideoWatermarkSubtitleEditorResult.Skipped("duration-unavailable")
        if (durationMillis <= 0L) {
            return@withContext VideoWatermarkSubtitleEditorResult.Skipped("duration-unavailable")
        }

        val subtitleFile = runCatching {
            resolveSubtitleFile(target)
        }.getOrElse {
            return@withContext VideoWatermarkSubtitleEditorResult.Failed("subtitle-path-unavailable")
        }

        val normalizedText = watermarkText.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" ")
            .ifBlank { return@withContext VideoWatermarkSubtitleEditorResult.Skipped("empty-watermark") }
        val subtitleText = buildSrtText(
            durationMillis = durationMillis,
            watermarkText = normalizedText
        )

        val writeSucceeded = runCatching {
            subtitleFile.parentFile?.mkdirs()
            subtitleFile.writeText(subtitleText, Charsets.UTF_8)
        }.isSuccess
        if (!writeSucceeded) {
            return@withContext VideoWatermarkSubtitleEditorResult.Failed("subtitle-write-failed")
        }

        val warning = if (target is VideoWatermarkSubtitleTarget.ContentUri) {
            "app-private-sidecar"
        } else {
            null
        }
        VideoWatermarkSubtitleEditorResult.Applied(
            subtitlePath = subtitleFile.absolutePath,
            warning = warning
        )
    }

    private fun readDurationMillis(target: VideoWatermarkSubtitleTarget): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            when (target) {
                is VideoWatermarkSubtitleTarget.FilePath ->
                    retriever.setDataSource(target.videoPath)

                is VideoWatermarkSubtitleTarget.ContentUri ->
                    retriever.setDataSource(appContext, Uri.parse(target.value))
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resolveSubtitleFile(target: VideoWatermarkSubtitleTarget): File {
        return when (target) {
            is VideoWatermarkSubtitleTarget.FilePath -> {
                val videoFile = File(target.videoPath)
                val baseName = videoFile.nameWithoutExtension.ifBlank { "video_watermark" }
                File(videoFile.parentFile ?: appContext.filesDir, "$baseName.srt")
            }

            is VideoWatermarkSubtitleTarget.ContentUri -> {
                val root = target.relativePath.substringBefore("/", "Movies")
                val nested = target.relativePath.substringAfter("/", "")
                val baseDir = appContext.getExternalFilesDir(root) ?: appContext.filesDir
                val subtitleDir = if (nested.isBlank()) {
                    File(baseDir, "Subtitles")
                } else {
                    File(File(baseDir, nested), "Subtitles")
                }
                File(subtitleDir, "${target.fileNameBase}.srt")
            }
        }
    }

    private fun buildSrtText(
        durationMillis: Long,
        watermarkText: String
    ): String {
        return buildString {
            appendLine("1")
            append(formatTimestamp(0L))
            append(" --> ")
            appendLine(formatTimestamp(durationMillis))
            appendLine(watermarkText)
        }
    }

    private fun formatTimestamp(durationMillis: Long): String {
        val totalMillis = durationMillis.coerceAtLeast(0L)
        val hours = totalMillis / 3_600_000L
        val minutes = (totalMillis % 3_600_000L) / 60_000L
        val seconds = (totalMillis % 60_000L) / 1_000L
        val millis = totalMillis % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}

private fun MediaOutputHandle.toVideoWatermarkSubtitleTargetOrNull(
    outputPath: String,
    relativePath: String
): VideoWatermarkSubtitleTarget? {
    filePath?.let { return VideoWatermarkSubtitleTarget.FilePath(it) }
    displayPath.takeIf { File(it).isAbsolute }?.let { absolutePath ->
        return VideoWatermarkSubtitleTarget.FilePath(absolutePath)
    }
    contentUri?.let { uri ->
        val baseName = outputPath.substringAfterLast('/').substringBeforeLast('.').ifBlank {
            "video_watermark"
        }
        return VideoWatermarkSubtitleTarget.ContentUri(
            value = uri,
            fileNameBase = baseName,
            relativePath = relativePath
        )
    }
    return null
}

private fun ShotResult.withVideoWatermarkNotes(vararg notes: String): ShotResult {
    return copy(pipelineNotes = pipelineNotes + notes)
}
