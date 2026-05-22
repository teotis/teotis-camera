package com.opencamera.core.media

fun MultiFrameMergePlaceholderPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    val delegate = this
    return object : AlgorithmProcessor {
        override val type: AlgorithmType = AlgorithmType.MULTI_FRAME_MERGE

        override fun canProcess(request: AlgorithmRequest): Boolean {
            val frameCount = request.metadata.customTags["frameCount"]?.toIntOrNull() ?: 0
            return frameCount > 1 && request.inputs.isNotEmpty()
        }

        override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
            val frameCount = request.metadata.customTags["frameCount"]?.toIntOrNull() ?: 0
            val inputPaths = request.inputs.map { it.path }
            val syntheticResult = ShotResult(
                shotId = request.node.id,
                mediaType = MediaType.PHOTO,
                outputPath = request.node.output,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = frameCount),
                metadata = request.metadata,
                intermediateOutputPaths = inputPaths
            )
            val processed = delegate.process(syntheticResult)
            val mergeNotes = processed.pipelineNotes.filter { it.startsWith("merge:") }
            return if (mergeNotes.isNotEmpty()) {
                AlgorithmResult.Applied(
                    output = MediaOutputHandle(displayPath = processed.outputPath),
                    notes = mergeNotes
                )
            } else {
                AlgorithmResult.Skipped(
                    reason = "single-frame-or-no-intermediates",
                    notes = emptyList()
                )
            }
        }
    }
}
