package com.opencamera.core.media

import java.io.File

interface MediaPostProcessor {
    fun isApplicable(result: ShotResult): Boolean = true

    suspend fun process(result: ShotResult): ShotResult
}

sealed interface ProcessorTarget {
    data class FilePath(val path: String) : ProcessorTarget
    data class ContentUri(val value: String) : ProcessorTarget
}

sealed interface ProcessorWork<out T> {
    data object None : ProcessorWork<Nothing>
    data class Execute<T>(val payload: T) : ProcessorWork<T>
    data class DiagnosticSkip(val reason: String) : ProcessorWork<Nothing>
}

interface ProcessorEditorResult {
    data class Skipped(val reason: String) : ProcessorEditorResult
    data class Failed(val reason: String) : ProcessorEditorResult
}

fun MediaOutputHandle.toProcessorTargetOrNull(): ProcessorTarget? {
    contentUri?.let { return ProcessorTarget.ContentUri(it) }
    filePath?.let { return ProcessorTarget.FilePath(it) }
    return displayPath.takeIf { File(it).isAbsolute }?.let(ProcessorTarget::FilePath)
}

fun ShotResult.addPipelineNotes(vararg notes: String): ShotResult {
    return copy(pipelineNotes = pipelineNotes + notes)
}

/**
 * Liveness-aware entry envelope for a media post-processor invocation.
 *
 * Adds an optional [configSnapshot] (shot-time UI configuration) and an optional
 * [liveness] deadline so a processor can both (a) read frozen config instead of live
 * settings and (b) abort on deadline expiry. Both default to `null` so existing
 * call-sites can adopt the type incrementally; the implementation in
 * `MediaPostProcessors.kt` is owned by package 03 and unchanged here.
 */
data class PostProcessRequest(
    val result: ShotResult,
    val configSnapshot: ShotConfigSnapshot? = null,
    val liveness: PostProcessLivenessDeadline? = null
)
