package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ProcessorWork
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.addStructuredPostProcessFailure
import com.opencamera.core.media.PostProcessFailure
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessOutputIntegrity
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.toProcessorTargetOrNull
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

internal data class PortraitRenderPayload(
    val target: ProcessorTarget,
    val spec: PortraitRenderSpec
)

internal enum class PortraitRenderMode {
    DEPTH,
    FOCUS
}

internal enum class PortraitBackgroundLightSpotSpec {
    NONE,
    SUBTLE,
    DREAMY
}

internal data class PortraitRenderSpec(
    val mode: PortraitRenderMode,
    val portraitProfile: PortraitProfile,
    val beautyPreset: PortraitBeautyPreset,
    val beautyStrengthLevel: PortraitBeautyStrength,
    val bokehEffect: PortraitBokehEffect,
    val lightSpot: PortraitBackgroundLightSpotSpec,
    val blurScale: Int,
    val focusRadiusXFraction: Float,
    val focusRadiusYFraction: Float,
    val edgeSoftness: Float,
    val vignetteStrength: Float,
    val subjectTracking: Boolean,
    val strength: Float,
    val subjectSmoothing: Float,
    val subjectLift: Float,
    val subjectSaturationBoost: Float,
    val highlightBloom: Float,
    val backgroundBloom: Float
)

internal data class PortraitRenderApplied(
    val warning: String? = null,
    val lightSpotNotes: List<String> = emptyList()
) : ProcessorEditorResult

internal interface PortraitRenderEditor {
    suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ): ProcessorEditorResult
}

internal fun supportsPortraitRenderMetadata(tags: Map<String, String>): Boolean {
    return tags["mode"] == "portrait" ||
        tags["compatMode"] == "portrait" ||
        (
            tags["mode"] == "check-in" &&
                tags["checkInScenario"] in setOf("portrait", "people-place", "object-place")
            )
}

internal fun decidePortraitRenderWork(result: ShotResult): ProcessorWork<PortraitRenderPayload> {
    when (result.photoJpegInput()) {
        PhotoJpegInput.NOT_PHOTO -> return ProcessorWork.None
        PhotoJpegInput.UNSUPPORTED_MIME -> return ProcessorWork.DiagnosticSkip("unsupported-mime")
        PhotoJpegInput.EDITABLE -> Unit
    }

    val tags = result.metadata.customTags
    if (!supportsPortraitRenderMetadata(tags)) {
        return ProcessorWork.None
    }

    val renderPath = tags["renderPath"]
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
        ?: return ProcessorWork.DiagnosticSkip("missing-render-path")
    val bokehStrength = tags["bokehStrength"]?.toFloatOrNull()
    val subjectTracking = tags["subjectTracking"] == "true"
    val depthStrength = tags["portraitDepthStrength"]?.toIntOrNull() ?: 50
    val defaults = PhotoSettings()
    val spec = resolvePortraitRenderSpec(
        renderPath = renderPath,
        bokehStrength = bokehStrength,
        subjectTracking = subjectTracking,
        portraitProfile = PortraitProfile.fromStorageKey(tags["portraitProfile"])
            ?: defaults.portraitProfile,
        beautyPreset = PortraitBeautyPreset.fromStorageKey(tags["portraitBeautyPreset"])
            ?: defaults.portraitBeautyPreset,
        beautyStrength = PortraitBeautyStrength.fromStorageKey(tags["portraitBeautyStrength"])
            ?: defaults.portraitBeautyStrength,
        bokehEffect = PortraitBokehEffect.fromStorageKey(tags["portraitBokehEffect"])
            ?: defaults.portraitBokehEffect,
        depthStrength = depthStrength
    ) ?: return ProcessorWork.DiagnosticSkip("unsupported-render-path")

    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return ProcessorWork.DiagnosticSkip("missing-output-handle")
    return ProcessorWork.Execute(PortraitRenderPayload(target, spec))
}

internal fun resolvePortraitRenderSpec(
    renderPath: String,
    bokehStrength: Float?,
    subjectTracking: Boolean,
    portraitProfile: PortraitProfile,
    beautyPreset: PortraitBeautyPreset,
    beautyStrength: PortraitBeautyStrength,
    bokehEffect: PortraitBokehEffect,
    depthStrength: Int = 50
): PortraitRenderSpec? {
    val depthMultiplier = (depthStrength.coerceIn(0, 100) / 100f) + 0.5f
    val profileStrengthOffset = when (portraitProfile) {
        PortraitProfile.NATIVE -> 0f
        PortraitProfile.LUMINOUS -> 0.18f
    }
    val bokehStrengthOffset = when (bokehEffect) {
        PortraitBokehEffect.NATURAL -> 0f
        PortraitBokehEffect.CREAMY -> 0.18f
        PortraitBokehEffect.DREAMY -> 0.32f
    }
    val beautyIntensity = beautyStrength.intensity
    val presetSmoothing = when (beautyPreset) {
        PortraitBeautyPreset.AUTHENTIC -> 0.14f
        PortraitBeautyPreset.CLEAR -> 0.22f
        PortraitBeautyPreset.RADIANT -> 0.32f
    }
    val presetLift = when (beautyPreset) {
        PortraitBeautyPreset.AUTHENTIC -> 0.03f
        PortraitBeautyPreset.CLEAR -> 0.06f
        PortraitBeautyPreset.RADIANT -> 0.1f
    }
    val presetSaturationBoost = when (beautyPreset) {
        PortraitBeautyPreset.AUTHENTIC -> 0.01f
        PortraitBeautyPreset.CLEAR -> 0.035f
        PortraitBeautyPreset.RADIANT -> 0.065f
    }
    val profileLift = when (portraitProfile) {
        PortraitProfile.NATIVE -> 0f
        PortraitProfile.LUMINOUS -> 0.03f
    }
    val profileBloom = when (portraitProfile) {
        PortraitProfile.NATIVE -> 0f
        PortraitProfile.LUMINOUS -> 0.05f
    }
    val effectBloom = when (bokehEffect) {
        PortraitBokehEffect.NATURAL -> 0.01f
        PortraitBokehEffect.CREAMY -> 0.035f
        PortraitBokehEffect.DREAMY -> 0.085f
    }
    val subjectSmoothing = (presetSmoothing * beautyIntensity).coerceIn(0f, 0.32f)
    val subjectLift = ((presetLift * beautyIntensity) + profileLift).coerceIn(0f, 0.18f)
    val subjectSaturationBoost = ((presetSaturationBoost * beautyIntensity) + profileBloom * 0.3f)
        .coerceIn(0f, 0.12f)
    val highlightBloom = ((effectBloom * beautyIntensity) + profileBloom).coerceIn(0f, 0.2f)
    val backgroundBloom = (effectBloom + (profileBloom * 0.4f)).coerceIn(0f, 0.18f)
    val lightSpotSpec = when (bokehEffect) {
        PortraitBokehEffect.NATURAL -> PortraitBackgroundLightSpotSpec.NONE
        PortraitBokehEffect.CREAMY -> PortraitBackgroundLightSpotSpec.SUBTLE
        PortraitBokehEffect.DREAMY -> PortraitBackgroundLightSpotSpec.DREAMY
    }
    return when (renderPath) {
        "depth" -> {
            val strength = ((bokehStrength ?: 1.8f) + profileStrengthOffset + bokehStrengthOffset)
                .coerceIn(1f, 3f)
            val baseBlurScale = when {
                strength >= 2.2f -> 6
                strength >= 1.6f -> 8
                else -> 10
            }
            val baseFocusRadiusX = (
                0.34f - strength * 0.04f - when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 0f
                    PortraitBokehEffect.CREAMY -> 0.01f
                    PortraitBokehEffect.DREAMY -> 0.016f
                }
                ).coerceIn(0.18f, 0.3f)
            val baseFocusRadiusY = (
                0.43f - strength * 0.04f - when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 0f
                    PortraitBokehEffect.CREAMY -> 0.012f
                    PortraitBokehEffect.DREAMY -> 0.02f
                }
                ).coerceIn(0.24f, 0.36f)
            val baseEdgeSoftness = when (bokehEffect) {
                PortraitBokehEffect.NATURAL -> 0.24f
                PortraitBokehEffect.CREAMY -> 0.28f
                PortraitBokehEffect.DREAMY -> 0.34f
            }
            val baseVignette = (
                0.1f + ((strength - 1f) * 0.04f) + when (portraitProfile) {
                    PortraitProfile.NATIVE -> 0f
                    PortraitProfile.LUMINOUS -> 0.02f
                }
                ).coerceIn(0.08f, 0.22f)
            PortraitRenderSpec(
                mode = PortraitRenderMode.DEPTH,
                portraitProfile = portraitProfile,
                beautyPreset = beautyPreset,
                beautyStrengthLevel = beautyStrength,
                bokehEffect = bokehEffect,
                lightSpot = lightSpotSpec,
                blurScale = max(2, (baseBlurScale / depthMultiplier).toInt()),
                focusRadiusXFraction = (baseFocusRadiusX / depthMultiplier).coerceIn(0.14f, 0.32f),
                focusRadiusYFraction = (baseFocusRadiusY / depthMultiplier).coerceIn(0.2f, 0.38f),
                edgeSoftness = (baseEdgeSoftness * depthMultiplier).coerceIn(0.18f, 0.42f),
                vignetteStrength = (baseVignette * depthMultiplier).coerceIn(0.06f, 0.28f),
                subjectTracking = subjectTracking,
                strength = strength,
                subjectSmoothing = subjectSmoothing,
                subjectLift = subjectLift,
                subjectSaturationBoost = subjectSaturationBoost,
                highlightBloom = highlightBloom,
                backgroundBloom = (backgroundBloom * depthMultiplier).coerceIn(0f, 0.22f)
            )
        }

        "focus" -> {
            val strength = ((bokehStrength ?: 1f) + profileStrengthOffset * 0.6f + bokehStrengthOffset * 0.4f)
                .coerceIn(0.8f, 1.6f)
            val baseBlurScale = when (bokehEffect) {
                PortraitBokehEffect.NATURAL -> 12
                PortraitBokehEffect.CREAMY -> 11
                PortraitBokehEffect.DREAMY -> 10
            }
            val baseFocusRadiusX = when (bokehEffect) {
                PortraitBokehEffect.NATURAL -> 0.34f
                PortraitBokehEffect.CREAMY -> 0.33f
                PortraitBokehEffect.DREAMY -> 0.31f
            }
            val baseFocusRadiusY = when (bokehEffect) {
                PortraitBokehEffect.NATURAL -> 0.44f
                PortraitBokehEffect.CREAMY -> 0.42f
                PortraitBokehEffect.DREAMY -> 0.4f
            }
            val baseEdgeSoftness = when (bokehEffect) {
                PortraitBokehEffect.NATURAL -> 0.22f
                PortraitBokehEffect.CREAMY -> 0.25f
                PortraitBokehEffect.DREAMY -> 0.3f
            }
            val baseVignette = when (portraitProfile) {
                PortraitProfile.NATIVE -> 0.05f
                PortraitProfile.LUMINOUS -> 0.08f
            }
            PortraitRenderSpec(
                mode = PortraitRenderMode.FOCUS,
                portraitProfile = portraitProfile,
                beautyPreset = beautyPreset,
                beautyStrengthLevel = beautyStrength,
                bokehEffect = bokehEffect,
                lightSpot = lightSpotSpec,
                blurScale = max(4, (baseBlurScale / depthMultiplier).toInt()),
                focusRadiusXFraction = (baseFocusRadiusX / depthMultiplier).coerceIn(0.14f, 0.36f),
                focusRadiusYFraction = (baseFocusRadiusY / depthMultiplier).coerceIn(0.2f, 0.46f),
                edgeSoftness = (baseEdgeSoftness * depthMultiplier).coerceIn(0.16f, 0.38f),
                vignetteStrength = (baseVignette * depthMultiplier).coerceIn(0.03f, 0.14f),
                subjectTracking = subjectTracking,
                strength = strength,
                subjectSmoothing = subjectSmoothing,
                subjectLift = subjectLift,
                subjectSaturationBoost = subjectSaturationBoost,
                highlightBloom = highlightBloom,
                backgroundBloom = (backgroundBloom * depthMultiplier).coerceIn(0f, 0.22f)
            )
        }

        else -> null
    }
}

private const val TAG = "PortraitRenderPP"

internal class PortraitRenderPostProcessor(
    private val editor: PortraitRenderEditor,
    private val maskProvider: SavedPhotoSceneMaskProvider? = null,
    private val maskBitmapSource: ((ProcessorTarget) -> Bitmap?)? = null
) : MediaPostProcessor {
    override fun isApplicable(result: ShotResult): Boolean = result.mediaType == MediaType.PHOTO

    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePortraitRenderWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "portrait-render:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                val payload = work.payload
                val maskResult = resolveMask(result)
                val renderResult = if (maskResult != null && editor is MaskAwarePortraitRenderEditor) {
                    try {
                        val (editorResult, maskNotes) = editor.applyWithMask(
                            payload.target, payload.spec, maskResult
                        )
                        val baseResult = applyEditorResult(result, payload, editorResult)
                        maskNotes.fold(baseResult) { acc, note -> acc.addPipelineNotes(note) }
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "Portrait mask-aware render failed: out of memory", e)
                        val structuredFailure = PostProcessFailure(
                            stage = PostProcessFailureStage.PORTRAIT_RENDER,
                            cause = PostProcessFailureCause.OUT_OF_MEMORY,
                            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                            disposition = PostProcessFailureDisposition.RECOVERABLE,
                            processorName = "PortraitRender"
                        )
                        result.addStructuredPostProcessFailure(structuredFailure).addPipelineNotes(
                            "portrait-render:degraded:mask-render-oom",
                            "portrait-render:fallback-focus"
                        )
                    } catch (e: Throwable) {
                        e.rethrowIfCancellationOrFatal()
                        Log.w(TAG, "Portrait mask-aware render failed", e)
                        val structuredFailure = PostProcessFailure(
                            stage = PostProcessFailureStage.PORTRAIT_RENDER,
                            cause = PostProcessFailureCause.EXCEPTION,
                            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                            disposition = PostProcessFailureDisposition.RECOVERABLE,
                            processorName = "PortraitRender"
                        )
                        result.addStructuredPostProcessFailure(structuredFailure).addPipelineNotes(
                            "portrait-render:degraded:mask-render-exception",
                            "portrait-render:fallback-focus"
                        )
                    }
                } else {
                    val degradedNote = when {
                        maskProvider == null -> "portrait-mask:saved=degraded:no-provider"
                        maskResult == null -> "portrait-mask:saved=degraded:mask-unavailable"
                        else -> null
                    }
                    val baseResult = applyEditorResult(result, payload, editor.apply(payload.target, payload.spec))
                    if (degradedNote != null) {
                        baseResult.addPipelineNotes(degradedNote, "portrait-render:fallback-focus")
                    } else {
                        baseResult
                    }
                }
                renderResult
            }
        }
    }

    private suspend fun resolveMask(result: ShotResult): SavedPhotoMaskPixels? {
        val provider = maskProvider ?: return null
        val target = result.outputHandle.toProcessorTargetOrNull() ?: return null
        val decoded = if (maskBitmapSource != null) {
            maskBitmapSource.invoke(target) ?: return null
        } else {
            val sourceBytes = readSourceBytesForMask(target) ?: return null
            if (sourceBytes.isEmpty()) return null
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
        }
        return try {
            val maskRequest = SavedPhotoSceneMaskRequest(
                shotId = result.shotId,
                outputHandleTag = result.outputHandle.displayPath
            )
            when (val maskResult = provider.createSubjectMask(decoded, maskRequest)) {
                is SceneMaskResult.Available -> maskResult.mask
                is SceneMaskResult.Unavailable -> null
                is SceneMaskResult.Failed -> null
            }
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Portrait mask resolve failed", e)
            null
        } finally {
            decoded.recycle()
        }
    }

    private fun readSourceBytesForMask(target: ProcessorTarget): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) null else file.readBytes()
            }
            is ProcessorTarget.ContentUri -> null
        }
    }

    private fun applyEditorResult(
        result: ShotResult,
        payload: PortraitRenderPayload,
        renderResult: ProcessorEditorResult
    ): ShotResult {
        val lightSpotLayerNote = "portrait-layer:light-spot=${payload.spec.lightSpot.name.lowercase()}"
        return when (renderResult) {
            is PortraitRenderApplied -> {
                val notes = mutableListOf(
                    "portrait-render:applied:${payload.spec.mode.name.lowercase()}",
                    lightSpotLayerNote
                )
                if (renderResult.warning != null) {
                    notes += "portrait-render:warning:${renderResult.warning}"
                }
                notes.addAll(renderResult.lightSpotNotes)
                result.addPipelineNotes(*notes.toTypedArray())
            }

            is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                "portrait-render:skipped:${renderResult.reason}",
                lightSpotLayerNote
            )

            is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                "portrait-render:failed:${renderResult.reason}",
                lightSpotLayerNote
            )

            else -> result.addPipelineNotes(lightSpotLayerNote)
        }
    }
}

internal class AndroidPortraitRenderEditor(
    context: Context,
    private val chunkEngine: PortraitRasterChunkEngine = PortraitRasterChunkEngine()
) : MaskAwarePortraitRenderEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val sourceBytes = ProcessorIOUtils.readSourceBytes(target, contentResolver)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val mutableBitmap = MutableArgbBitmapDecoder.decode(sourceBytes)
            ?: return@withContext ProcessorEditorResult.Failed("decode-failed")

        var blurredBitmap: Bitmap? = null
        try {
            blurredBitmap = createBlurredBackground(mutableBitmap, spec)
            chunkEngine.renderFocus(
                original = mutableBitmap,
                blurred = blurredBitmap,
                spec = spec
            )

            val lightSpotNotes = applyLightSpotEffect(mutableBitmap, blurredBitmap, spec)

            val encodedBytes = ByteArrayOutputStream().use { output ->
                check(mutableBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Portrait render JPEG compression failed"
                }
                output.toByteArray()
            }
            if (!contentResolver.writeEncodedBytes(target, encodedBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }

            val exifWarning = contentResolver.restorePreservedExif(target, preservedExif)
            PortraitRenderApplied(exifWarning, lightSpotNotes)
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Portrait render failed", e)
            ProcessorEditorResult.Failed("render-exception")
        } finally {
            blurredBitmap?.recycle()
            mutableBitmap.recycle()
        }
    }

    override suspend fun applyWithMask(
        target: ProcessorTarget,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>> = withContext(Dispatchers.IO) {
        val sourceBytes = ProcessorIOUtils.readSourceBytes(target, contentResolver)
            ?: return@withContext Pair(
                ProcessorEditorResult.Failed("input-unavailable"),
                listOf("portrait-render:fallback-focus")
            )
        if (sourceBytes.isEmpty()) {
            return@withContext Pair(
                ProcessorEditorResult.Failed("empty-source"),
                listOf("portrait-render:fallback-focus")
            )
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val mutableBitmap = MutableArgbBitmapDecoder.decode(sourceBytes)
            ?: return@withContext Pair(
                ProcessorEditorResult.Failed("decode-failed"),
                listOf("portrait-render:fallback-focus")
            )

        var blurredBitmap: Bitmap? = null
        try {
            blurredBitmap = createBlurredBackground(mutableBitmap, spec)
            chunkEngine.renderMaskAware(
                original = mutableBitmap,
                blurred = blurredBitmap,
                spec = spec,
                mask = mask
            )
            val lightSpotNotes = applyLightSpotEffect(mutableBitmap, blurredBitmap, spec)
            val encodedBytes = ByteArrayOutputStream().use { output ->
                check(mutableBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Mask-aware portrait render JPEG compression failed"
                }
                output.toByteArray()
            }
            if (!contentResolver.writeEncodedBytes(target, encodedBytes)) {
                return@withContext Pair(
                    ProcessorEditorResult.Failed("output-unavailable"),
                    listOf("portrait-render:fallback-focus")
                )
            }
            val exifWarning = contentResolver.restorePreservedExif(target, preservedExif)
            val notes = mutableListOf(
                "portrait-mask:saved=applied",
                "portrait-render:subject-mask"
            )
            notes.addAll(lightSpotNotes)
            Pair(PortraitRenderApplied(exifWarning, lightSpotNotes), notes)
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Portrait mask-aware render failed", e)
            Pair(PortraitRenderApplied(warning = "mask-render-failed"), listOf("portrait-render:fallback-focus"))
        } finally {
            blurredBitmap?.recycle()
            mutableBitmap.recycle()
        }
    }

    private fun createBlurredBackground(
        bitmap: Bitmap,
        spec: PortraitRenderSpec
    ): Bitmap {
        val scaledWidth = max(1, bitmap.width / spec.blurScale)
        val scaledHeight = max(1, bitmap.height / spec.blurScale)
        val downscaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        return if (downscaled.width == bitmap.width && downscaled.height == bitmap.height) {
            downscaled
        } else {
            val upscaled = Bitmap.createScaledBitmap(downscaled, bitmap.width, bitmap.height, true)
            if (upscaled !== downscaled) {
                downscaled.recycle()
            }
            upscaled
        }
    }

    private fun applyLightSpotEffect(
        bitmap: Bitmap,
        blurred: Bitmap,
        spec: PortraitRenderSpec
    ): List<String> {
        if (spec.lightSpot == PortraitBackgroundLightSpotSpec.NONE) {
            return listOf("portrait-light-spot:degraded:spec-none")
        }

        val width = bitmap.width
        val height = bitmap.height
        val focusCenterX = width * 0.5f
        val focusCenterY = height * if (spec.subjectTracking) 0.42f else 0.46f
        val radiusX = max(1f, width * spec.focusRadiusXFraction)
        val radiusY = max(1f, height * spec.focusRadiusYFraction)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val highlightCandidates = detectHighlightCandidates(
            pixels, width, height,
            focusCenterX, focusCenterY, radiusX, radiusY, spec
        )

        if (highlightCandidates.isEmpty()) {
            return listOf("portrait-light-spot:degraded:no-highlights")
        }

        val diskCount = renderLightSpotDisks(pixels, highlightCandidates, width, height, spec)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return listOf(
            "portrait-light-spot:applied",
            "portrait-light-spot:disks=$diskCount"
        )
    }

    private fun detectHighlightCandidates(
        pixels: IntArray,
        width: Int,
        height: Int,
        focusCenterX: Float,
        focusCenterY: Float,
        radiusX: Float,
        radiusY: Float,
        spec: PortraitRenderSpec
    ): List<HighlightCandidate> {
        val cellSize = max(16, minOf(width, height) / 20)
        val candidates = mutableListOf<HighlightCandidate>()

        var cellY = 0
        while (cellY < height) {
            var cellX = 0
            while (cellX < width) {
                val endX = minOf(cellX + cellSize, width)
                val endY = minOf(cellY + cellSize, height)

                var bestX = -1
                var bestY = -1
                var bestLuma = HIGHLIGHT_LUMA_THRESHOLD

                for (y in cellY until endY) {
                    for (x in cellX until endX) {
                        val dx = (x - focusCenterX) / radiusX
                        val dy = (y - focusCenterY) / radiusY
                        val normalizedDistance = sqrt(dx * dx + dy * dy)
                        val blurMix = smoothstep(1f, 1f + spec.edgeSoftness, normalizedDistance)
                        if (blurMix < 0.35f) continue

                        val color = pixels[y * width + x]
                        val r = ((color ushr 16) and 0xFF).toFloat()
                        val g = ((color ushr 8) and 0xFF).toFloat()
                        val b = (color and 0xFF).toFloat()
                        val luma = (r * 0.299f) + (g * 0.587f) + (b * 0.114f)

                        if (luma > bestLuma) {
                            bestLuma = luma
                            bestX = x
                            bestY = y
                        }
                    }
                }

                if (bestX >= 0) {
                    candidates.add(HighlightCandidate(bestX, bestY, bestLuma))
                }
                cellX += cellSize
            }
            cellY += cellSize
        }

        return candidates
    }

    private fun renderLightSpotDisks(
        pixels: IntArray,
        candidates: List<HighlightCandidate>,
        width: Int,
        height: Int,
        spec: PortraitRenderSpec
    ): Int {
        val (baseRadius, maxAlpha) = when (spec.lightSpot) {
            PortraitBackgroundLightSpotSpec.SUBTLE -> 4f to 0.025f
            PortraitBackgroundLightSpotSpec.DREAMY -> 9f to 0.055f
            else -> return 0
        }
        val sizeScale = minOf(width, height) / 1080f

        var diskCount = 0
        for (candidate in candidates) {
            val normalizedLuma = ((candidate.luminance / 255f) - HIGHLIGHT_LUMA_THRESHOLD / 255f)
                .coerceIn(0f, 1f)
            val diskRadius = (baseRadius + normalizedLuma * baseRadius * 0.5f) * sizeScale
            val diskAlpha = (maxAlpha * (0.4f + normalizedLuma * 0.6f))
                .coerceIn(0f, 0.08f)
            val diskInt = (diskAlpha * 255f).toInt().coerceIn(1, 20)

            val radiusCeil = diskRadius.toInt() + 1
            val yStart = maxOf(0, candidate.y - radiusCeil)
            val yEnd = minOf(height - 1, candidate.y + radiusCeil)
            val xStart = maxOf(0, candidate.x - radiusCeil)
            val xEnd = minOf(width - 1, candidate.x + radiusCeil)

            for (y in yStart..yEnd) {
                for (x in xStart..xEnd) {
                    val distSq = (x - candidate.x).toFloat().let { it * it } +
                        (y - candidate.y).toFloat().let { it * it }
                    val diskRadiusSq = diskRadius * diskRadius
                    if (distSq >= diskRadiusSq) continue

                    val dist = sqrt(distSq)
                    val feather = 1f - smoothstep(diskRadius * 0.5f, diskRadius, dist)
                    if (feather <= 0f) continue

                    val idx = y * width + x
                    val existing = pixels[idx]
                    val er = (existing ushr 16) and 0xFF
                    val eg = (existing ushr 8) and 0xFF
                    val eb = existing and 0xFF

                    val addAlpha = (diskInt * feather).toInt().coerceIn(0, 20)
                    val nr = minOf(255, er + addAlpha)
                    val ng = minOf(255, eg + addAlpha)
                    val nb = minOf(255, eb + addAlpha)

                    pixels[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
            diskCount++
        }
        return diskCount
    }

    private data class HighlightCandidate(val x: Int, val y: Int, val luminance: Float)

    companion object {
        private const val JPEG_QUALITY = 92
        private const val HIGHLIGHT_LUMA_THRESHOLD = 195f
    }
}

private fun mixChannel(source: Float, target: Float, mix: Float): Float {
    return source + (target - source) * mix.coerceIn(0f, 1f)
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) {
        return if (value >= edge1) 1f else 0f
    }
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun clampChannel(value: Float): Float = value.coerceIn(0f, 255f)
