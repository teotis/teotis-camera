package com.opencamera.core.media

data class MediaInputRef(
    val path: String,
    val handle: MediaOutputHandle,
    val mimeType: String
)

data class AlgorithmBudget(
    val maxDurationMillis: Long = 30_000,
    val maxMemoryBytes: Long = 256L * 1024 * 1024
)

data class AlgorithmRequest(
    val node: AlgorithmNode,
    val inputs: List<MediaInputRef>,
    val metadata: MediaMetadata,
    val budget: AlgorithmBudget = AlgorithmBudget()
)

sealed interface AlgorithmResult {
    data class Applied(
        val output: MediaOutputHandle,
        val notes: List<String>
    ) : AlgorithmResult

    data class Skipped(
        val reason: String,
        val notes: List<String>
    ) : AlgorithmResult

    data class Failed(
        val reason: String,
        val recoverable: Boolean
    ) : AlgorithmResult
}

interface AlgorithmProcessor {
    val type: AlgorithmType
    fun canProcess(request: AlgorithmRequest): Boolean
    suspend fun process(request: AlgorithmRequest): AlgorithmResult
}
