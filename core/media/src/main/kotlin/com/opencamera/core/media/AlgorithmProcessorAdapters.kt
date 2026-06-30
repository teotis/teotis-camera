package com.opencamera.core.media

fun MultiFrameFusionProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
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

            val frameBundle = if (inputPaths.isNotEmpty()) {
                FrameBundle(
                    shotId = request.node.id,
                    frames = inputPaths.mapIndexed { index, path ->
                        FrameBundleFrame(
                            frameIndex = index,
                            pixelReference = PixelReference.File(path),
                            frameRole = if (index == inputPaths.lastIndex) FrameRole.FUSION_ANCHOR
                            else FrameRole.FUSION_SUPPLEMENT
                        )
                    }
                )
            } else null

            val syntheticResult = ShotResult(
                shotId = request.node.id,
                mediaType = MediaType.PHOTO,
                outputPath = request.node.output,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = frameCount),
                metadata = request.metadata,
                frameBundle = frameBundle,
                intermediateOutputPaths = inputPaths.dropLast(1)
            )
            val processed = delegate.process(syntheticResult)
            val mergeNotes = processed.pipelineNotes.filter { it.startsWith("merge:") }
            return if (processed.pipelineNotes.any { it == "merge:applied=true" }) {
                AlgorithmResult.Applied(
                    output = MediaOutputHandle(displayPath = processed.outputPath),
                    notes = mergeNotes
                )
            } else {
                val reason = processed.pipelineNotes
                    .firstOrNull { it.startsWith("merge:skipped=") }
                    ?.removePrefix("merge:skipped=")
                    ?: "single-frame-or-no-intermediates"
                AlgorithmResult.Skipped(
                    reason = reason,
                    notes = mergeNotes
                )
            }
        }
    }
}

fun FocusStackFusionProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    val delegate = this
    return object : AlgorithmProcessor {
        override val type: AlgorithmType = AlgorithmType.FOCUS_STACK_FUSION

        override fun canProcess(request: AlgorithmRequest): Boolean {
            if (request.node.type != AlgorithmType.FOCUS_STACK_FUSION) return false
            val roles = request.metadata.customTags["focusStackRoles"]
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?: emptyList()
            return request.inputs.size >= 2 && "near" in roles && "far" in roles
        }

        override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
            val roles = request.metadata.customTags["focusStackRoles"]
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?: emptyList()
            val frames = request.inputs.mapIndexed { index, input ->
                FrameBundleFrame(
                    frameIndex = index,
                    pixelReference = PixelReference.File(input.path),
                    focusStackRole = when (roles.getOrNull(index)) {
                        "near" -> FocusStackFrameRole.NEAR
                        "far" -> FocusStackFrameRole.FAR
                        "mid" -> FocusStackFrameRole.MID
                        else -> FocusStackFrameRole.NONE
                    }
                )
            }
            val syntheticResult = ShotResult(
                shotId = request.node.id,
                mediaType = MediaType.PHOTO,
                outputPath = request.node.output,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(
                    frameCount = frames.size,
                    focusStackSpec = FocusStackCaptureSpec.guidedNearFar()
                ),
                metadata = request.metadata,
                frameBundle = FrameBundle(
                    shotId = request.node.id,
                    frames = frames
                ),
                intermediateOutputPaths = request.inputs.map { it.path }
            )
            val processed = delegate.process(syntheticResult)
            val focusNotes = processed.pipelineNotes.filter { it.startsWith("focus-stack:") }
            return if (processed.pipelineNotes.any { it == "focus-stack:applied=true" }) {
                AlgorithmResult.Applied(
                    output = MediaOutputHandle(displayPath = processed.outputPath),
                    notes = focusNotes
                )
            } else {
                val reason = processed.pipelineNotes
                    .firstOrNull { it.startsWith("focus-stack:skipped=") }
                    ?.removePrefix("focus-stack:skipped=")
                    ?: "focus-stack-not-applied"
                AlgorithmResult.Skipped(reason = reason, notes = focusNotes)
            }
        }
    }
}
