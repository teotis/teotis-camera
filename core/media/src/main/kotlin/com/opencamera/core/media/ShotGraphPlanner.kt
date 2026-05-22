package com.opencamera.core.media

fun ShotPlan.toShotGraph(): ShotGraph {
    val request = this.request
    val captureNodes = mutableListOf<CaptureNode>()
    val algorithmNodes = mutableListOf<AlgorithmNode>()
    val outputNodes = mutableListOf<OutputNode>()

    val imageFormat = CaptureFrameFormat(mimeType = request.saveRequest.mimeType)
    val videoFormat = CaptureFrameFormat(mimeType = "video/mp4")

    when (request.shotKind) {
        ShotKind.STILL_CAPTURE -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:primary",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = imageFormat
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:primary",
                    role = MediaArtifactRole.PRIMARY_STILL,
                    mimeType = request.saveRequest.mimeType
                )
            )
        }

        ShotKind.MULTI_FRAME_CAPTURE -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:temp-frames",
                    role = CaptureNodeRole.TEMPORARY_FRAME,
                    frameCount = request.captureProfile.frameCount,
                    timingPolicy = CaptureTimingPolicy(sequential = true),
                    requiredFormat = imageFormat
                )
            )
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:primary",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = imageFormat
                )
            )
            algorithmNodes.add(
                AlgorithmNode(
                    id = "${request.shotId}:alg:merge",
                    type = AlgorithmType.MULTI_FRAME_MERGE,
                    inputs = listOf("${request.shotId}:temp-frames"),
                    output = "${request.shotId}:primary",
                    requirement = AlgorithmRequirement.REQUIRED,
                    fallback = AlgorithmFallback.FAIL_SHOT
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:primary",
                    role = MediaArtifactRole.PRIMARY_STILL,
                    mimeType = request.saveRequest.mimeType
                )
            )
        }

        ShotKind.LIVE_PHOTO -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:still",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = imageFormat
                )
            )
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:motion",
                    role = CaptureNodeRole.MOTION_SEGMENT,
                    frameCount = 1,
                    requiredFormat = videoFormat
                )
            )
            algorithmNodes.add(
                AlgorithmNode(
                    id = "${request.shotId}:alg:live-assemble",
                    type = AlgorithmType.LIVE_ASSEMBLE,
                    inputs = listOf("${request.shotId}:still", "${request.shotId}:motion"),
                    output = "${request.shotId}:live-bundle",
                    requirement = AlgorithmRequirement.REQUIRED,
                    fallback = AlgorithmFallback.USE_ORIGINAL
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:still",
                    role = MediaArtifactRole.PRIMARY_STILL,
                    mimeType = request.saveRequest.mimeType
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:motion",
                    role = MediaArtifactRole.MOTION_SEGMENT,
                    mimeType = request.livePhotoSpec?.motionMimeType ?: "video/mp4"
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:sidecar",
                    role = MediaArtifactRole.LIVE_SIDECAR,
                    mimeType = request.livePhotoSpec?.sidecarMimeType
                        ?: "application/vnd.opencamera.live+json"
                )
            )
        }

        ShotKind.VIDEO_RECORDING -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:video",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = videoFormat
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:video",
                    role = MediaArtifactRole.PRIMARY_VIDEO,
                    mimeType = request.saveRequest.mimeType
                )
            )
        }
    }

    val spec = request.postProcessSpec
    if (spec.algorithmProfile != null) {
        algorithmNodes.add(
            AlgorithmNode(
                id = "${request.shotId}:alg:filter",
                type = AlgorithmType.FILTER_RENDER,
                inputs = listOf("${request.shotId}:primary"),
                output = "${request.shotId}:filtered",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.USE_ORIGINAL
            )
        )
    }
    if (spec.watermarkText != null) {
        algorithmNodes.add(
            AlgorithmNode(
                id = "${request.shotId}:alg:watermark",
                type = AlgorithmType.WATERMARK_RENDER,
                inputs = listOf("${request.shotId}:primary"),
                output = "${request.shotId}:watermarked",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.USE_ORIGINAL
            )
        )
    }
    if (request.thumbnailPolicy != ThumbnailPolicy.NONE) {
        algorithmNodes.add(
            AlgorithmNode(
                id = "${request.shotId}:alg:thumbnail",
                type = AlgorithmType.THUMBNAIL_SELECT,
                inputs = listOf("${request.shotId}:primary"),
                output = "${request.shotId}:thumbnail",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.SKIP
            )
        )
    }

    return ShotGraph(
        shotId = request.shotId,
        captureNodes = captureNodes,
        algorithmNodes = algorithmNodes,
        outputNodes = outputNodes
    )
}
