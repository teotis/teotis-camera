package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.effect.RenderRecipe
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ProcessorWork
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.SceneMaskPayload
import com.opencamera.core.media.SceneMaskPipelineNotes
import com.opencamera.core.media.SceneMaskSupport
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.toProcessorTargetOrNull
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PerceptualColorRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal sealed class MaskResolveResult {
    data class Available(
        val bitmap: Bitmap,
        val mask: SavedPhotoMaskPixels,
        val extraNotes: List<String>
    ) : MaskResolveResult()

    data class Fallback(val extraNotes: List<String>) : MaskResolveResult()
}

internal data class PhotoAlgorithmPayload(
    val target: ProcessorTarget,
    val spec: PhotoAlgorithmSpec
)

internal data class PhotoAlgorithmSpec(
    val profile: String,
    val brightnessShift: Int = 0,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmthShift: Int = 0,
    val tintShift: Int = 0,
    val monochromeMix: Float = 0f,
    val vignetteStrength: Float = 0f,
    val softGlowStrength: Float = 0f,
    val haloStrength: Float = 0f,
    val grainStrength: Float = 0f,
    val sharpnessBoost: Float = 0f,
    val highlightCompression: Float = 0f,
    val shadowLift: Float = 0f,
    val warmBoost: Float = 0f,
    val coolBoost: Float = 0f,
    val recipe: PerceptualColorRecipe = PerceptualColorRecipe.NEUTRAL
)

internal data class PhotoAlgorithmApplied(val warning: String? = null) : ProcessorEditorResult

internal interface PhotoAlgorithmEditor {
    suspend fun apply(
        target: ProcessorTarget,
        spec: PhotoAlgorithmSpec
    ): ProcessorEditorResult
}

internal fun decidePhotoAlgorithmWork(result: ShotResult): ProcessorWork<PhotoAlgorithmPayload> {
    if (result.mediaType != MediaType.PHOTO) {
        return ProcessorWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return ProcessorWork.DiagnosticSkip("unsupported-mime")
    }
    val profile = result.metadata.algorithmProfile
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val recipe = parseRecipeFromTags(result.metadata.customTags)

    val spec = FilterRenderSpec.fromMetadataTags(result.metadata.customTags)?.toPhotoAlgorithmSpec(
        profile = result.metadata.customTags["filterProfile"]
            ?: profile
            ?: "shared-filter",
        recipe = recipe
    ) ?: profile?.let { resolvePhotoAlgorithmSpec(it, recipe) }
        ?: RenderRecipe.from(result).let { renderRecipe ->
            if (renderRecipe.requiresFinalOutputPostprocess) {
                renderRecipe.toPhotoAlgorithmSpec()
            } else {
                null
            }
        }
        ?: return ProcessorWork.None
    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return ProcessorWork.DiagnosticSkip("missing-output-handle")

    return ProcessorWork.Execute(PhotoAlgorithmPayload(target, spec))
}

private fun FilterRenderSpec.toPhotoAlgorithmSpec(
    profile: String,
    recipe: PerceptualColorRecipe = PerceptualColorRecipe.NEUTRAL
): PhotoAlgorithmSpec {
    return PhotoAlgorithmSpec(
        profile = profile,
        brightnessShift = brightnessShift,
        contrast = contrast,
        saturation = saturation,
        warmthShift = warmthShift,
        tintShift = tintShift,
        monochromeMix = monochromeMix,
        vignetteStrength = vignetteStrength,
        softGlowStrength = softGlowStrength,
        haloStrength = haloStrength,
        grainStrength = grainStrength,
        sharpnessBoost = sharpnessBoost,
        highlightCompression = highlightCompression,
        shadowLift = shadowLift,
        warmBoost = warmBoost,
        coolBoost = coolBoost,
        recipe = recipe
    )
}

private fun RenderRecipe.toPhotoAlgorithmSpec(): PhotoAlgorithmSpec {
    val profileId = filterProfileId ?: "recipe-only"
    val spec = filterRenderSpec
    return if (spec != null) {
        spec.toPhotoAlgorithmSpec(profile = profileId, recipe = perceptualColorRecipe)
    } else {
        PhotoAlgorithmSpec(
            profile = profileId,
            recipe = perceptualColorRecipe
        )
    }
}

internal class PhotoAlgorithmPostProcessor(
    private val editor: PhotoAlgorithmEditor,
    private val maskProvider: SavedPhotoSceneMaskProvider? = null,
    private val maskBitmapSource: ((ProcessorTarget) -> android.graphics.Bitmap?)? = null
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoAlgorithmWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "algorithm-render:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                val payload = work.payload
                var maskResolve: MaskResolveResult? = null
                try {
                    val resolvedMask = resolveMask(result).also { maskResolve = it }
                    when (resolvedMask) {
                        is MaskResolveResult.Available -> {
                            if (editor is MaskAwarePhotoAlgorithmEditor) {
                                val (editorResult, maskNotes) = editor.applyWithMask(
                                    payload.target, resolvedMask.bitmap, payload.spec, resolvedMask.mask
                                )
                                val baseResult = applyEditorResult(result, payload, editorResult)
                                val descriptor = resolvedMask.mask.toDescriptor(
                                    maskId = result.shotId,
                                    sourceWidth = resolvedMask.bitmap.width,
                                    sourceHeight = resolvedMask.bitmap.height
                                )
                                val withNotes = (maskNotes + resolvedMask.extraNotes + listOf(
                                    SceneMaskPipelineNotes.preview(SceneMaskSupport.UNSUPPORTED)
                                )).fold(baseResult) { acc, note ->
                                    acc.addPipelineNotes(note)
                                }
                                val sceneMaskTags = descriptor.toMetadataTags()
                                withNotes.copy(
                                    metadata = withNotes.metadata.copy(
                                        customTags = withNotes.metadata.customTags + sceneMaskTags
                                    )
                                )
                            } else {
                                val fallbackNotes = listOf(
                                    SceneMaskPipelineNotes.saved(SceneMaskSupport.DEGRADED) + ":editor-not-mask-aware",
                                    SceneMaskPipelineNotes.preview(SceneMaskSupport.UNSUPPORTED)
                                )
                                (resolvedMask.extraNotes + fallbackNotes).fold(
                                    applyEditorResult(result, payload, editor.apply(payload.target, payload.spec))
                                ) { acc, note -> acc.addPipelineNotes(note) }
                            }
                        }
                        is MaskResolveResult.Fallback -> {
                            resolvedMask.extraNotes.fold(
                                applyEditorResult(result, payload, editor.apply(payload.target, payload.spec))
                            ) { acc, note -> acc.addPipelineNotes(note) }
                        }
                    }
                } catch (_: Throwable) {
                    result.addPipelineNotes("algorithm-render:failed:render-exception")
                } finally {
                    val resolvedMask = maskResolve
                    if (resolvedMask is MaskResolveResult.Available) {
                        resolvedMask.bitmap.recycle()
                    }
                }
            }
        }
    }

    private suspend fun resolveMask(result: ShotResult): MaskResolveResult {
        val provider = maskProvider ?: return MaskResolveResult.Fallback(emptyList())
        val target = result.outputHandle.toProcessorTargetOrNull()
            ?: return MaskResolveResult.Fallback(emptyList())
        val decoded = if (maskBitmapSource != null) {
            maskBitmapSource.invoke(target)
                ?: return MaskResolveResult.Fallback(emptyList())
        } else {
            val sourceBytes = readSourceBytesForMask(target)
                ?: return MaskResolveResult.Fallback(emptyList())
            if (sourceBytes.isEmpty()) return MaskResolveResult.Fallback(emptyList())
            android.graphics.BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                ?: return MaskResolveResult.Fallback(emptyList())
        }
        return try {
            val maskRequest = SavedPhotoSceneMaskRequest(
                shotId = result.shotId,
                outputHandleTag = result.outputHandle.displayPath
            )
            when (val maskResult = provider.createSubjectMask(decoded, maskRequest)) {
                is SceneMaskResult.Available -> MaskResolveResult.Available(
                    bitmap = decoded,
                    mask = maskResult.mask,
                    extraNotes = emptyList()
                )
                is SceneMaskResult.Unavailable -> {
                    decoded.recycle()
                    MaskResolveResult.Fallback(listOf(
                        SceneMaskPipelineNotes.saved(SceneMaskSupport.UNSUPPORTED),
                        SceneMaskPipelineNotes.reason(maskResult.reason)
                    ))
                }
                is SceneMaskResult.Failed -> {
                    decoded.recycle()
                    MaskResolveResult.Fallback(listOf(
                        SceneMaskPipelineNotes.saved(SceneMaskSupport.DEGRADED),
                        SceneMaskPipelineNotes.reason(maskResult.reason)
                    ))
                }
            }
        } catch (_: Throwable) {
            decoded.recycle()
            MaskResolveResult.Fallback(listOf(
                SceneMaskPipelineNotes.saved(SceneMaskSupport.UNSUPPORTED),
                SceneMaskPipelineNotes.reason("mask-resolve-exception")
            ))
        }
    }

    private fun readSourceBytesForMask(target: ProcessorTarget): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = java.io.File(target.path)
                if (!file.exists()) null else file.readBytes()
            }
            is ProcessorTarget.ContentUri -> null
        }
    }

    private fun applyEditorResult(
        result: ShotResult,
        payload: PhotoAlgorithmPayload,
        renderResult: ProcessorEditorResult
    ): ShotResult {
        return when (renderResult) {
            is PhotoAlgorithmApplied -> {
                if (renderResult.warning == null) {
                    result.addPipelineNotes(
                        "algorithm-render:applied:${payload.spec.profile}"
                    )
                } else {
                    result.addPipelineNotes(
                        "algorithm-render:applied:${payload.spec.profile}",
                        "algorithm-render:warning:${renderResult.warning}"
                    )
                }
            }

            is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                "algorithm-render:skipped:${renderResult.reason}"
            )

            is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                "algorithm-render:failed:${renderResult.reason}"
            )

            else -> result
        }
    }
}

internal class AndroidPhotoAlgorithmEditor(
    context: Context
) : MaskAwarePhotoAlgorithmEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: ProcessorTarget,
        spec: PhotoAlgorithmSpec
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val sourceBytes = readSourceBytes(target)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val mutableBitmap: Bitmap
        val preservedExif: Map<String, String>
        try {
            preservedExif = readPreservedExif(sourceBytes)
            val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                ?: return@withContext ProcessorEditorResult.Failed("decode-failed")
            mutableBitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
            if (mutableBitmap !== decoded) {
                decoded.recycle()
            }
        } catch (_: Throwable) {
            return@withContext ProcessorEditorResult.Failed("render-exception")
        }

        try {
            applyStyle(
                bitmap = mutableBitmap,
                spec = spec
            )
            val encodedBytes = encodeJpeg(mutableBitmap)
            if (!writeEncodedBytes(target, encodedBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }

            val exifWarning = restorePreservedExif(target, preservedExif)
            PhotoAlgorithmApplied(exifWarning)
        } catch (_: Throwable) {
            ProcessorEditorResult.Failed("render-exception")
        } finally {
            mutableBitmap.recycle()
        }
    }

    override suspend fun applyWithMask(
        target: ProcessorTarget,
        bitmap: Bitmap,
        spec: PhotoAlgorithmSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>> {
        val sourceBytes = readSourceBytes(target)
        val preservedExif: Map<String, String> = if (sourceBytes != null && sourceBytes.isNotEmpty()) {
            readPreservedExif(sourceBytes)
        } else {
            emptyMap()
        }
        val styleNotes = applyStyleWithMask(bitmap, spec, mask)
        val encodedBytes = encodeJpeg(bitmap)
        if (!writeEncodedBytes(target, encodedBytes)) {
            return Pair(
                ProcessorEditorResult.Failed("output-unavailable"),
                listOf(
                    SceneMaskPipelineNotes.saved(SceneMaskSupport.DEGRADED),
                    SceneMaskPipelineNotes.reason("output-unavailable")
                )
            )
        }
        val exifWarning = restorePreservedExif(target, preservedExif)
        return Pair(PhotoAlgorithmApplied(exifWarning), styleNotes)
    }

    private fun applyStyle(
        bitmap: Bitmap,
        spec: PhotoAlgorithmSpec
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val originalPixels = pixels.copyOf()

        val centerX = width / 2f
        val centerY = height / 2f
        val maxDistance = max(1f, sqrt(centerX * centerX + centerY * centerY))
        val hasVignette = spec.vignetteStrength > 0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                val alpha = color ushr 24 and 0xFF
                val originalRed = color ushr 16 and 0xFF
                val originalGreen = color ushr 8 and 0xFF
                val originalBlue = color and 0xFF
                val blurredRed = averagedChannel(originalPixels, x, y, width, height, 16)
                val blurredGreen = averagedChannel(originalPixels, x, y, width, height, 8)
                val blurredBlue = averagedChannel(originalPixels, x, y, width, height, 0)

                val grayscale = originalRed * 0.299f + originalGreen * 0.587f + originalBlue * 0.114f
                var red = grayscale + (originalRed - grayscale) * spec.saturation
                var green = grayscale + (originalGreen - grayscale) * spec.saturation
                var blue = grayscale + (originalBlue - grayscale) * spec.saturation

                if (spec.monochromeMix > 0f) {
                    red = mix(red, grayscale, spec.monochromeMix)
                    green = mix(green, grayscale, spec.monochromeMix)
                    blue = mix(blue, grayscale, spec.monochromeMix)
                }

                if (spec.softGlowStrength > 0f) {
                    val glowAmount = spec.softGlowStrength.coerceIn(0f, 0.35f)
                    red = mix(red, blurredRed + 8f, glowAmount)
                    green = mix(green, blurredGreen + 8f, glowAmount)
                    blue = mix(blue, blurredBlue + 8f, glowAmount)
                }

                if (spec.haloStrength > 0f) {
                    val haloAmount = spec.haloStrength.coerceIn(0f, 0.35f)
                    val highlightMask =
                        (((maxOf(originalRed, originalGreen, originalBlue) - 172f) / 83f)
                            .coerceIn(0f, 1f)) * haloAmount
                    red = mix(red, blurredRed + 18f, highlightMask)
                    green = mix(green, blurredGreen + 16f, highlightMask)
                    blue = mix(blue, blurredBlue + 20f, highlightMask)
                }

                if (spec.shadowLift > 0f || spec.highlightCompression > 0f) {
                    red = applyHighlightShadow(red, spec.highlightCompression, spec.shadowLift)
                    green = applyHighlightShadow(green, spec.highlightCompression, spec.shadowLift)
                    blue = applyHighlightShadow(blue, spec.highlightCompression, spec.shadowLift)
                }

                val tintCompensation = spec.tintShift * 0.7f
                red = applyContrast(red, spec.contrast) + spec.brightnessShift + spec.warmthShift + tintCompensation
                green = applyContrast(green, spec.contrast) + spec.brightnessShift - spec.tintShift
                blue = applyContrast(blue, spec.contrast) + spec.brightnessShift - spec.warmthShift + tintCompensation

                if (spec.sharpnessBoost > 0f) {
                    val sharpenAmount = spec.sharpnessBoost.coerceIn(0f, 0.4f) * 1.6f
                    red += (originalRed - blurredRed) * sharpenAmount
                    green += (originalGreen - blurredGreen) * sharpenAmount
                    blue += (originalBlue - blurredBlue) * sharpenAmount
                }

                if (spec.warmBoost > 0f || spec.coolBoost > 0f) {
                    red += (spec.warmBoost * 24f) - (spec.coolBoost * 12f)
                    green += (spec.warmBoost - spec.coolBoost) * 4f
                    blue += (spec.coolBoost * 24f) - (spec.warmBoost * 12f)
                }

                if (!spec.recipe.isNeutral) {
                    val result = applyPerceptualAdjustments(
                        red, green, blue, spec.recipe
                    )
                    red = result[0]
                    green = result[1]
                    blue = result[2]
                }

                if (spec.grainStrength > 0f) {
                    val grain = deterministicGrain(x, y) * spec.grainStrength.coerceIn(0f, 0.35f) * 36f
                    red += grain
                    green += grain
                    blue += grain
                }

                if (hasVignette) {
                    val dx = x - centerX
                    val dy = y - centerY
                    val distance = sqrt(dx * dx + dy * dy)
                    val falloff = 1f - ((distance / maxDistance) * spec.vignetteStrength).coerceIn(0f, 0.85f)
                    red *= falloff
                    green *= falloff
                    blue *= falloff
                }

                pixels[index] = (alpha shl 24) or
                    (clampChannel(red).toInt() shl 16) or
                    (clampChannel(green).toInt() shl 8) or
                    clampChannel(blue).toInt()
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun applyStyleWithMask(
        bitmap: Bitmap,
        spec: PhotoAlgorithmSpec,
        mask: SavedPhotoMaskPixels
    ): List<String> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val originalPixels = pixels.copyOf()

        val transform = SceneMaskCoordinateMapper(
            maskWidth = mask.maskWidth,
            maskHeight = mask.maskHeight,
            targetWidth = width,
            targetHeight = height
        )

        val centerX = width / 2f
        val centerY = height / 2f
        val maxDistance = max(1f, sqrt(centerX * centerX + centerY * centerY))
        val hasVignette = spec.vignetteStrength > 0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                val alpha = color ushr 24 and 0xFF
                val originalRed = color ushr 16 and 0xFF
                val originalGreen = color ushr 8 and 0xFF
                val originalBlue = color and 0xFF
                val blurredRed = averagedChannel(originalPixels, x, y, width, height, 16)
                val blurredGreen = averagedChannel(originalPixels, x, y, width, height, 8)
                val blurredBlue = averagedChannel(originalPixels, x, y, width, height, 0)

                val mx = transform.maskX(x)
                val my = transform.maskY(y)
                val rawMaskAlpha = mask.sampleAlpha(mx, my)
                val subjectWeight = smoothstep(0.15f, 0.85f, rawMaskAlpha)

                val grayscale = originalRed * 0.299f + originalGreen * 0.587f + originalBlue * 0.114f
                var red = grayscale + (originalRed - grayscale) * spec.saturation
                var green = grayscale + (originalGreen - grayscale) * spec.saturation
                var blue = grayscale + (originalBlue - grayscale) * spec.saturation

                if (spec.monochromeMix > 0f) {
                    red = mix(red, grayscale, spec.monochromeMix)
                    green = mix(green, grayscale, spec.monochromeMix)
                    blue = mix(blue, grayscale, spec.monochromeMix)
                }

                if (spec.softGlowStrength > 0f) {
                    val glowAmount = spec.softGlowStrength.coerceIn(0f, 0.35f) * (1f - subjectWeight * 0.6f)
                    red = mix(red, blurredRed + 8f, glowAmount)
                    green = mix(green, blurredGreen + 8f, glowAmount)
                    blue = mix(blue, blurredBlue + 8f, glowAmount)
                }

                if (spec.haloStrength > 0f) {
                    val haloAmount = spec.haloStrength.coerceIn(0f, 0.35f) * (1f - subjectWeight * 0.6f)
                    val highlightMask =
                        (((maxOf(originalRed, originalGreen, originalBlue) - 172f) / 83f)
                            .coerceIn(0f, 1f)) * haloAmount
                    red = mix(red, blurredRed + 18f, highlightMask)
                    green = mix(green, blurredGreen + 16f, highlightMask)
                    blue = mix(blue, blurredBlue + 20f, highlightMask)
                }

                if (spec.shadowLift > 0f || spec.highlightCompression > 0f) {
                    val subjectScale = 1f - subjectWeight * 0.5f
                    red = applyHighlightShadow(red, spec.highlightCompression * subjectScale, spec.shadowLift * subjectScale)
                    green = applyHighlightShadow(green, spec.highlightCompression * subjectScale, spec.shadowLift * subjectScale)
                    blue = applyHighlightShadow(blue, spec.highlightCompression * subjectScale, spec.shadowLift * subjectScale)
                }

                val warmthScale = 1f - subjectWeight * 0.6f
                val tintCompensation = spec.tintShift * 0.7f * warmthScale
                val adjustedWarmth = spec.warmthShift * warmthScale
                val adjustedTint = spec.tintShift * warmthScale
                red = applyContrast(red, spec.contrast) + spec.brightnessShift + adjustedWarmth + tintCompensation
                green = applyContrast(green, spec.contrast) + spec.brightnessShift - adjustedTint
                blue = applyContrast(blue, spec.contrast) + spec.brightnessShift - adjustedWarmth + tintCompensation

                if (spec.sharpnessBoost > 0f) {
                    val sharpenAmount = spec.sharpnessBoost.coerceIn(0f, 0.4f) * 1.6f
                    red += (originalRed - blurredRed) * sharpenAmount
                    green += (originalGreen - blurredGreen) * sharpenAmount
                    blue += (originalBlue - blurredBlue) * sharpenAmount
                }

                if (spec.warmBoost > 0f || spec.coolBoost > 0f) {
                    val warmScale = 1f - subjectWeight * 0.4f
                    red += ((spec.warmBoost * 24f) - (spec.coolBoost * 12f)) * warmScale
                    green += ((spec.warmBoost - spec.coolBoost) * 4f) * warmScale
                    blue += ((spec.coolBoost * 24f) - (spec.warmBoost * 12f)) * warmScale
                }

                if (!spec.recipe.isNeutral) {
                    val result = applyPerceptualAdjustmentsMaskAware(
                        red, green, blue, spec.recipe, subjectWeight
                    )
                    red = result[0]
                    green = result[1]
                    blue = result[2]
                }

                if (spec.grainStrength > 0f) {
                    val grain = deterministicGrain(x, y) * spec.grainStrength.coerceIn(0f, 0.35f) * 36f
                    red += grain
                    green += grain
                    blue += grain
                }

                if (hasVignette) {
                    val dx = x - centerX
                    val dy = y - centerY
                    val distance = sqrt(dx * dx + dy * dy)
                    val vignetteScale = 1f - subjectWeight * 0.5f
                    val falloff = 1f - ((distance / maxDistance) * spec.vignetteStrength * vignetteScale).coerceIn(0f, 0.85f)
                    red *= falloff
                    green *= falloff
                    blue *= falloff
                }

                pixels[index] = (alpha shl 24) or
                    (clampChannel(red).toInt() shl 16) or
                    (clampChannel(green).toInt() shl 8) or
                    clampChannel(blue).toInt()
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return listOf("scene-mask:saved=applied", "color-render:subject-protected", "color-render:background-adjusted")
    }

    private fun applyPerceptualAdjustmentsMaskAware(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        recipe: PerceptualColorRecipe,
        subjectWeight: Float
    ): FloatArray {
        val luma = rIn * 0.2126f + gIn * 0.7152f + bIn * 0.0722f
        val lumaNorm = (luma / 255f).coerceIn(0f, 1f)

        val shadowMask = (1f - lumaNorm).coerceIn(0f, 1f)
        val highlightMask = lumaNorm.coerceIn(0f, 1f)

        val subjectToneScale = 1f - subjectWeight * 0.4f
        var r = rIn + recipe.toneLift * shadowMask * 28f * subjectToneScale - recipe.toneDepth * highlightMask * 22f * subjectToneScale
        var g = gIn + recipe.toneLift * shadowMask * 26f * subjectToneScale - recipe.toneDepth * highlightMask * 20f * subjectToneScale
        var b = bIn + recipe.toneLift * shadowMask * 24f * subjectToneScale - recipe.toneDepth * highlightMask * 18f * subjectToneScale

        val chroma = maxOf(r, g, b) - minOf(r, g, b)
        val chromaNorm = (chroma / 255f).coerceIn(0f, 1f)
        val neutralMask = (1f - chromaNorm * 2f).coerceIn(0f, 1f)
        val protectionFactor = neutralMask * recipe.neutralProtection

        val chromaScale = 1f + recipe.chromaBoost * 0.3f * (1f - protectionFactor) * (1f - subjectWeight * 0.3f)
        val gray = r * 0.2126f + g * 0.7152f + b * 0.0722f
        r = gray + (r - gray) * chromaScale
        g = gray + (g - gray) * chromaScale
        b = gray + (b - gray) * chromaScale

        val skinMask = detectSkinMask(r, g, b)
        val combinedSkinProtect = (skinMask + subjectWeight * 0.5f).coerceAtMost(1f) * recipe.skinProtection

        val warmR = recipe.warmthBias.coerceAtLeast(0f)
        val coolB = (-recipe.warmthBias).coerceAtLeast(0f)
        val warmAmount = (warmR * 18f - coolB * 10f) * (1f - combinedSkinProtect * 0.6f)
        val coolAmount = (coolB * 18f - warmR * 10f) * (1f - combinedSkinProtect * 0.6f)
        r += warmAmount
        b += coolAmount

        val shadowR = recipe.shadowTint * shadowMask * 12f
        val highlightB = recipe.highlightTint * highlightMask * 10f
        r += shadowR
        b += highlightB

        val tintAmount = recipe.tintBias * 8f * (1f - combinedSkinProtect * 0.5f)
        g -= tintAmount

        return floatArrayOf(r, g, b)
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Algorithm JPEG compression failed"
            }
            output.toByteArray()
        }
    }

    private fun readSourceBytes(target: ProcessorTarget): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) {
                    null
                } else {
                    file.readBytes()
                }
            }

            is ProcessorTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }

    private fun writeEncodedBytes(
        target: ProcessorTarget,
        encodedBytes: ByteArray
    ): Boolean {
        return when (target) {
            is ProcessorTarget.FilePath -> runCatching {
                File(target.path).outputStream().use { it.write(encodedBytes) }
            }.isSuccess

            is ProcessorTarget.ContentUri -> {
                contentResolver.openOutputStream(Uri.parse(target.value), "rwt")?.use {
                    it.write(encodedBytes)
                } != null
            }
        }
    }

    private fun readPreservedExif(sourceBytes: ByteArray): Map<String, String> {
        return runCatching {
            ByteArrayInputStream(sourceBytes).use { input ->
                val exif = ExifInterface(input)
                EXIF_TAGS_TO_PRESERVE.mapNotNull { tag ->
                    exif.getAttribute(tag)?.let { value -> tag to value }
                }.toMap()
            }
        }.getOrDefault(emptyMap())
    }

    private fun restorePreservedExif(
        target: ProcessorTarget,
        preservedExif: Map<String, String>
    ): String? {
        if (preservedExif.isEmpty()) {
            return null
        }

        val restored = runCatching {
            when (target) {
                is ProcessorTarget.FilePath -> {
                    ExifInterface(target.path).apply {
                        preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
                        saveAttributes()
                    }
                }

                is ProcessorTarget.ContentUri -> {
                    contentResolver.openFileDescriptor(Uri.parse(target.value), "rw")?.use { descriptor ->
                        ExifInterface(descriptor.fileDescriptor).apply {
                            preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
                            saveAttributes()
                        }
                    } ?: error("file-descriptor-unavailable")
                }
            }
        }
        return if (restored.isSuccess) {
            null
        } else {
            "exif-restore-failed"
        }
    }

    private fun applyPerceptualAdjustments(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        recipe: PerceptualColorRecipe
    ): FloatArray {
        val luma = rIn * 0.2126f + gIn * 0.7152f + bIn * 0.0722f
        val lumaNorm = (luma / 255f).coerceIn(0f, 1f)

        val shadowMask = (1f - lumaNorm).coerceIn(0f, 1f)
        val highlightMask = lumaNorm.coerceIn(0f, 1f)

        var r = rIn + recipe.toneLift * shadowMask * 28f - recipe.toneDepth * highlightMask * 22f
        var g = gIn + recipe.toneLift * shadowMask * 26f - recipe.toneDepth * highlightMask * 20f
        var b = bIn + recipe.toneLift * shadowMask * 24f - recipe.toneDepth * highlightMask * 18f

        val chroma = maxOf(r, g, b) - minOf(r, g, b)
        val chromaNorm = (chroma / 255f).coerceIn(0f, 1f)
        val neutralMask = (1f - chromaNorm * 2f).coerceIn(0f, 1f)
        val protectionFactor = neutralMask * recipe.neutralProtection

        val chromaScale = 1f + recipe.chromaBoost * 0.3f * (1f - protectionFactor)
        val gray = r * 0.2126f + g * 0.7152f + b * 0.0722f
        r = gray + (r - gray) * chromaScale
        g = gray + (g - gray) * chromaScale
        b = gray + (b - gray) * chromaScale

        val skinMask = detectSkinMask(r, g, b)
        val skinProtect = skinMask * recipe.skinProtection

        val warmR = recipe.warmthBias.coerceAtLeast(0f)
        val coolB = (-recipe.warmthBias).coerceAtLeast(0f)
        val warmAmount = (warmR * 18f - coolB * 10f) * (1f - skinProtect * 0.6f)
        val coolAmount = (coolB * 18f - warmR * 10f) * (1f - skinProtect * 0.6f)
        r += warmAmount
        b += coolAmount

        val shadowR = recipe.shadowTint * shadowMask * 12f
        val highlightB = recipe.highlightTint * highlightMask * 10f
        r += shadowR
        b += highlightB

        val tintAmount = recipe.tintBias * 8f * (1f - skinProtect * 0.5f)
        g -= tintAmount

        return floatArrayOf(r, g, b)
    }

    private fun detectSkinMask(r: Float, g: Float, b: Float): Float {
        if (r < 60f || g < 40f || b < 20f) return 0f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max - min < 10f) return 0f
        val rRatio = r / max
        val gRatio = g / max
        if (rRatio > 0.8f && gRatio > 0.5f && gRatio < 0.85f && r > g && g > b) {
            return ((rRatio - 0.8f) * 5f).coerceIn(0f, 1f) *
                ((gRatio - 0.5f) * 2.86f).coerceIn(0f, 1f)
        }
        return 0f
    }

    companion object {
        private const val JPEG_QUALITY = 92

        private val EXIF_TAGS_TO_PRESERVE = listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP
        )
    }
}

private fun parseRecipeFromTags(tags: Map<String, String>): PerceptualColorRecipe {
    return com.opencamera.core.settings.parsePerceptualColorRecipe(tags)
}

internal fun resolvePhotoAlgorithmSpec(
    profile: String,
    recipe: PerceptualColorRecipe = PerceptualColorRecipe.NEUTRAL
): PhotoAlgorithmSpec? {
    return when (profile) {
        "photo-default" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.03f,
            saturation = 1.04f
        )

        "photo-vivid" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.08f,
            saturation = 1.14f,
            warmthShift = 2
        )

        "photo-original" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.01f,
            saturation = 1.01f
        )

        "photo-chasing-light" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 9,
            contrast = 1.04f,
            saturation = 1.06f,
            warmthShift = -2
        )

        "photo-rich" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.12f,
            saturation = 1.08f,
            warmthShift = 5,
            vignetteStrength = 0.08f
        )

        "document-receipt-scan" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 8,
            contrast = 1.22f,
            saturation = 0.18f,
            monochromeMix = 0.88f
        )

        "document-whiteboard-scan" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 16,
            contrast = 1.14f,
            saturation = 0.82f
        )

        "document-contract-scan" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 6,
            contrast = 1.1f,
            saturation = 0.45f,
            monochromeMix = 0.5f
        )

        "document-basic-archive" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.05f,
            saturation = 0.92f
        )

        "document-basic-color" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 4,
            contrast = 1.06f,
            saturation = 1.06f
        )

        "portrait-depth-natural" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.04f,
            saturation = 1.08f,
            warmthShift = 4,
            vignetteStrength = 0.14f
        )

        "portrait-depth-dramatic" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.12f,
            saturation = 0.92f,
            warmthShift = -2,
            vignetteStrength = 0.28f
        )

        "portrait-depth-studio" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 10,
            contrast = 1.08f,
            saturation = 0.96f,
            vignetteStrength = 0.1f
        )

        "portrait-focus-balanced" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.02f,
            saturation = 1.03f
        )

        "portrait-focus-closeup" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 6,
            contrast = 1.08f,
            saturation = 1.06f,
            warmthShift = 3
        )

        "night-multiframe-handheld" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 10,
            contrast = 1.05f,
            saturation = 1.02f
        )

        "night-multiframe-street" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 14,
            contrast = 1.08f,
            saturation = 0.95f,
            warmthShift = -4
        )

        "night-multiframe-tripod" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 18,
            contrast = 1.1f,
            saturation = 1.04f
        )

        "night-fallback-balanced" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 8,
            contrast = 1.03f
        )

        "night-fallback-warm" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 10,
            contrast = 1.04f,
            warmthShift = 10
        )

        "pro-manual-neutral" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.01f,
            saturation = 1.01f
        )

        "pro-manual-street" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.1f,
            saturation = 0.94f,
            warmthShift = -3
        )

        "pro-manual-night" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 12,
            contrast = 1.06f,
            saturation = 0.97f
        )

        "pro-assisted-balanced" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.03f,
            saturation = 1.02f
        )

        "pro-assisted-contrast" -> PhotoAlgorithmSpec(
            profile = profile,
            contrast = 1.14f,
            saturation = 0.96f
        )

        "pro-assisted-lowlight" -> PhotoAlgorithmSpec(
            profile = profile,
            brightnessShift = 10,
            contrast = 1.05f,
            saturation = 0.98f
        )

        else -> null
    }?.let { if (recipe.isNeutral) it else it.copy(recipe = recipe) }
}

private fun applyContrast(channel: Float, contrast: Float): Float {
    return (channel - 128f) * contrast + 128f
}

private fun applyHighlightShadow(
    channel: Float,
    highlightCompression: Float,
    shadowLift: Float
): Float {
    var adjusted = channel
    val clampedHighlight = highlightCompression.coerceIn(0f, 0.45f)
    val clampedShadow = shadowLift.coerceIn(0f, 0.45f)
    if (clampedHighlight > 0f && adjusted > 168f) {
        val compressedTarget = 168f + ((adjusted - 168f) * (1f - clampedHighlight))
        adjusted = mix(adjusted, compressedTarget, clampedHighlight)
    }
    if (clampedShadow > 0f && adjusted < 92f) {
        val liftedTarget = adjusted + ((92f - adjusted) * clampedShadow)
        adjusted = mix(adjusted, liftedTarget, clampedShadow)
    }
    return adjusted
}

private fun averagedChannel(
    pixels: IntArray,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    shift: Int
): Float {
    var total = 0f
    var count = 0
    for (offsetY in -1..1) {
        for (offsetX in -1..1) {
            val sampleX = (x + offsetX).coerceIn(0, width - 1)
            val sampleY = (y + offsetY).coerceIn(0, height - 1)
            total += ((pixels[sampleY * width + sampleX] ushr shift) and 0xFF).toFloat()
            count += 1
        }
    }
    return total / count
}

private fun clampChannel(channel: Float): Float {
    return min(255f, max(0f, channel))
}

private fun mix(base: Float, target: Float, amount: Float): Float {
    return base * (1f - amount) + target * amount
}

private fun deterministicGrain(x: Int, y: Int): Float {
    val noise = ((x * 73856093) xor (y * 19349663)) and 0xFF
    return (noise / 255f) - 0.5f
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) return if (value >= edge1) 1f else 0f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
