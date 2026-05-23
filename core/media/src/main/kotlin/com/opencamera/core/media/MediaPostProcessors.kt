package com.opencamera.core.media

import java.io.File

class CompositeMediaPostProcessor(
    private val processors: List<MediaPostProcessor>
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        var current = result
        processors.forEach { processor ->
            current = processor.process(current)
        }
        return current
    }
}

class PipelineMetadataPostProcessor : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        val deviceNotes = buildList {
            if (result.captureProfile.frameCount > 1) {
                add("frames:${result.captureProfile.frameCount}")
            }
            result.captureProfile.longExposureMillis?.let { add("exposure:${it}ms") }
            if (result.captureProfile.requiresTripod) {
                add("stability:tripod")
            }
            if (result.captureProfile.flashMode != FlashMode.OFF) {
                add("flash:${result.captureProfile.flashMode.name.lowercase()}")
            }
            if (result.captureProfile.torchEnabled) {
                add("torch:on")
            }
            result.captureProfile.stillCaptureQuality?.let { add("stillQuality:${it.tagValue}") }
            result.captureProfile.stillCaptureResolutionPreset
                ?.let { add("stillResolution:${it.tagValue}") }
        }

        val algorithmNotes = buildList {
            result.metadata.algorithmProfile?.let { add("algorithm:$it") }
            result.metadata.watermarkText?.let { add("watermark:$it") }
            result.metadata.customTags["livePhotoDefault"]?.let { add("live-default:$it") }
            result.metadata.customTags["liveWatermarkBehavior"]
                ?.let { behavior -> add("live-watermark:$behavior") }
        }

        val transactionNotes = buildList {
            result.livePhotoBundle?.let { bundle ->
                add("live-photo:bundle")
                add("live-photo:motion=${bundle.motionMimeType}")
                add("live-photo:sidecar=${bundle.sidecarMimeType}")
                addAll(bundle.temporalNotes())
                add("live:sidecar=${if (bundle.sidecarHandle.contentUri != null) "media-store" else "app-private"}")
            }
            result.metadata.customTags["shutterSoundEnabled"]
                ?.let { enabled -> add("shutter-sound:$enabled") }
            if (result.metadata.customTags["selfieMirrorApply"].toBoolean()) {
                add("selfie-mirror:requested")
            }
            result.metadata.customTags["manualDraftState"]?.let { state ->
                val raw = result.metadata.customTags["manualDraftRaw"] ?: "unknown"
                val iso = result.metadata.customTags["manualDraftIso"] ?: "unknown"
                val shutter = result.metadata.customTags["manualDraftShutterSpeedMillis"] ?: "unknown"
                val whiteBalance = result.metadata.customTags["manualDraftWhiteBalanceKelvin"]
                    ?: "unknown"
                add("manual-draft:$state:raw-$raw:iso-$iso:s-$shutter:wb-$whiteBalance")
            }
            if (result.metadata.exifOverrides.isNotEmpty()) {
                add("exif:${result.metadata.exifOverrides.keys.sorted().joinToString(",")}")
            }
        }

        val degradedNotes = buildList {
            if ((result.pipelineNotes + transactionNotes).any { it == "merge:placeholder" }) {
                add("degraded:multi-frame-placeholder")
            }
            if ((result.pipelineNotes + transactionNotes).any { it == "live:degraded=metadata-only" }) {
                add("degraded:live-still-only")
            }
        }

        val allNotes = deviceNotes + algorithmNotes + transactionNotes + degradedNotes
        if (allNotes.isEmpty()) {
            return result
        }
        return result.copy(pipelineNotes = result.pipelineNotes + allNotes)
    }
}

class MultiFrameMergePlaceholderPostProcessor : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        val intermediatePaths = result.intermediateOutputPaths
        if (result.captureProfile.frameCount <= 1 || intermediatePaths.isEmpty()) {
            return result
        }

        val existingInputs = intermediatePaths.map(::File).filter(File::exists)
        val totalInputBytes = existingInputs.sumOf(File::length)
        val notes = buildList {
            add("merge:placeholder")
            add("merge:inputs=${existingInputs.size + 1}")
            add("merge:temp-frames=${existingInputs.size}")
            add("merge:temp-bytes=$totalInputBytes")
            add("merge:strategy=burst-placeholder")
        }

        return try {
            result.copy(
                pipelineNotes = result.pipelineNotes + notes
            )
        } finally {
            existingInputs.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}
