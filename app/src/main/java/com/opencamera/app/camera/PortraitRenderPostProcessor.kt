package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
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

internal data class PortraitRenderSpec(
    val mode: PortraitRenderMode,
    val portraitProfile: PortraitProfile,
    val beautyPreset: PortraitBeautyPreset,
    val beautyStrengthLevel: PortraitBeautyStrength,
    val bokehEffect: PortraitBokehEffect,
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

internal data class PortraitRenderApplied(val warning: String? = null) : ProcessorEditorResult

internal interface PortraitRenderEditor {
    suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ): ProcessorEditorResult
}

internal fun decidePortraitRenderWork(result: ShotResult): ProcessorWork<PortraitRenderPayload> {
    if (result.mediaType != MediaType.PHOTO) {
        return ProcessorWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return ProcessorWork.DiagnosticSkip("unsupported-mime")
    }

    val tags = result.metadata.customTags
    if (tags["mode"] != "portrait") {
        return ProcessorWork.None
    }

    val renderPath = tags["renderPath"]
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
        ?: return ProcessorWork.DiagnosticSkip("missing-render-path")
    val bokehStrength = tags["bokehStrength"]?.toFloatOrNull()
    val subjectTracking = tags["subjectTracking"] == "true"
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
            ?: defaults.portraitBokehEffect
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
    bokehEffect: PortraitBokehEffect
): PortraitRenderSpec? {
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
    return when (renderPath) {
        "depth" -> {
            val strength = ((bokehStrength ?: 1.8f) + profileStrengthOffset + bokehStrengthOffset)
                .coerceIn(1f, 3f)
            PortraitRenderSpec(
                mode = PortraitRenderMode.DEPTH,
                portraitProfile = portraitProfile,
                beautyPreset = beautyPreset,
                beautyStrengthLevel = beautyStrength,
                bokehEffect = bokehEffect,
                blurScale = when {
                    strength >= 2.2f -> 6
                    strength >= 1.6f -> 8
                    else -> 10
                },
                focusRadiusXFraction = (
                    0.34f - strength * 0.04f - when (bokehEffect) {
                        PortraitBokehEffect.NATURAL -> 0f
                        PortraitBokehEffect.CREAMY -> 0.01f
                        PortraitBokehEffect.DREAMY -> 0.016f
                    }
                    ).coerceIn(0.18f, 0.3f),
                focusRadiusYFraction = (
                    0.43f - strength * 0.04f - when (bokehEffect) {
                        PortraitBokehEffect.NATURAL -> 0f
                        PortraitBokehEffect.CREAMY -> 0.012f
                        PortraitBokehEffect.DREAMY -> 0.02f
                    }
                    ).coerceIn(0.24f, 0.36f),
                edgeSoftness = when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 0.24f
                    PortraitBokehEffect.CREAMY -> 0.28f
                    PortraitBokehEffect.DREAMY -> 0.34f
                },
                vignetteStrength = (
                    0.1f + ((strength - 1f) * 0.04f) + when (portraitProfile) {
                        PortraitProfile.NATIVE -> 0f
                        PortraitProfile.LUMINOUS -> 0.02f
                    }
                    ).coerceIn(0.08f, 0.22f),
                subjectTracking = subjectTracking,
                strength = strength,
                subjectSmoothing = subjectSmoothing,
                subjectLift = subjectLift,
                subjectSaturationBoost = subjectSaturationBoost,
                highlightBloom = highlightBloom,
                backgroundBloom = backgroundBloom
            )
        }

        "focus" -> {
            val strength = ((bokehStrength ?: 1f) + profileStrengthOffset * 0.6f + bokehStrengthOffset * 0.4f)
                .coerceIn(0.8f, 1.6f)
            PortraitRenderSpec(
                mode = PortraitRenderMode.FOCUS,
                portraitProfile = portraitProfile,
                beautyPreset = beautyPreset,
                beautyStrengthLevel = beautyStrength,
                bokehEffect = bokehEffect,
                blurScale = when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 12
                    PortraitBokehEffect.CREAMY -> 11
                    PortraitBokehEffect.DREAMY -> 10
                },
                focusRadiusXFraction = when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 0.34f
                    PortraitBokehEffect.CREAMY -> 0.33f
                    PortraitBokehEffect.DREAMY -> 0.31f
                },
                focusRadiusYFraction = when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 0.44f
                    PortraitBokehEffect.CREAMY -> 0.42f
                    PortraitBokehEffect.DREAMY -> 0.4f
                },
                edgeSoftness = when (bokehEffect) {
                    PortraitBokehEffect.NATURAL -> 0.22f
                    PortraitBokehEffect.CREAMY -> 0.25f
                    PortraitBokehEffect.DREAMY -> 0.3f
                },
                vignetteStrength = when (portraitProfile) {
                    PortraitProfile.NATIVE -> 0.05f
                    PortraitProfile.LUMINOUS -> 0.08f
                },
                subjectTracking = subjectTracking,
                strength = strength,
                subjectSmoothing = subjectSmoothing,
                subjectLift = subjectLift,
                subjectSaturationBoost = subjectSaturationBoost,
                highlightBloom = highlightBloom,
                backgroundBloom = backgroundBloom
            )
        }

        else -> null
    }
}

internal class PortraitRenderPostProcessor(
    private val editor: PortraitRenderEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePortraitRenderWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "portrait-render:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                val payload = work.payload
                when (val renderResult = editor.apply(payload.target, payload.spec)) {
                    is PortraitRenderApplied -> {
                        if (renderResult.warning == null) {
                            result.addPipelineNotes(
                                "portrait-render:applied:${payload.spec.mode.name.lowercase()}"
                            )
                        } else {
                            result.addPipelineNotes(
                                "portrait-render:applied:${payload.spec.mode.name.lowercase()}",
                                "portrait-render:warning:${renderResult.warning}"
                            )
                        }
                    }

                    is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                        "portrait-render:skipped:${renderResult.reason}"
                    )

                    is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                        "portrait-render:failed:${renderResult.reason}"
                    )

                    else -> result
                }
            }
        }
    }
}

internal class AndroidPortraitRenderEditor(
    context: Context
) : PortraitRenderEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val sourceBytes = readSourceBytes(target)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return@withContext ProcessorEditorResult.Failed("decode-failed")
        val mutableBitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
        if (mutableBitmap !== decoded) {
            decoded.recycle()
        }

        var blurredBitmap: Bitmap? = null
        try {
            blurredBitmap = createBlurredBackground(mutableBitmap, spec)
            applyPortraitRender(
                original = mutableBitmap,
                blurred = blurredBitmap,
                spec = spec
            )

            val encodedBytes = ByteArrayOutputStream().use { output ->
                check(mutableBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Portrait render JPEG compression failed"
                }
                output.toByteArray()
            }
            if (!writeEncodedBytes(target, encodedBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }

            val exifWarning = restorePreservedExif(target, preservedExif)
            PortraitRenderApplied(exifWarning)
        } catch (_: Throwable) {
            ProcessorEditorResult.Failed("render-exception")
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

    private fun applyPortraitRender(
        original: Bitmap,
        blurred: Bitmap,
        spec: PortraitRenderSpec
    ) {
        val width = original.width
        val height = original.height
        val originalPixels = IntArray(width * height)
        val blurredPixels = IntArray(width * height)
        original.getPixels(originalPixels, 0, width, 0, 0, width, height)
        blurred.getPixels(blurredPixels, 0, width, 0, 0, width, height)

        val focusCenterX = width * 0.5f
        val focusCenterY = height * if (spec.subjectTracking) 0.42f else 0.46f
        val radiusX = max(1f, width * spec.focusRadiusXFraction)
        val radiusY = max(1f, height * spec.focusRadiusYFraction)
        val frameCenterX = width / 2f
        val frameCenterY = height / 2f
        val maxDistance = max(1f, sqrt(frameCenterX * frameCenterX + frameCenterY * frameCenterY))

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val sourceColor = originalPixels[index]
                val blurredColor = blurredPixels[index]
                val alpha = sourceColor ushr 24 and 0xFF

                val dx = (x - focusCenterX) / radiusX
                val dy = (y - focusCenterY) / radiusY
                val normalizedDistance = sqrt(dx * dx + dy * dy)
                val blurMix = smoothstep(1f, 1f + spec.edgeSoftness, normalizedDistance)
                val sourceRed = ((sourceColor ushr 16) and 0xFF).toFloat()
                val sourceGreen = ((sourceColor ushr 8) and 0xFF).toFloat()
                val sourceBlue = (sourceColor and 0xFF).toFloat()
                val blurredRed = ((blurredColor ushr 16) and 0xFF).toFloat()
                val blurredGreen = ((blurredColor ushr 8) and 0xFF).toFloat()
                val blurredBlue = (blurredColor and 0xFF).toFloat()

                var red = mixChannel(
                    sourceRed,
                    blurredRed,
                    blurMix
                )
                var green = mixChannel(
                    sourceGreen,
                    blurredGreen,
                    blurMix
                )
                var blue = mixChannel(
                    sourceBlue,
                    blurredBlue,
                    blurMix
                )

                val frameDx = x - frameCenterX
                val frameDy = y - frameCenterY
                val frameDistance = sqrt(frameDx * frameDx + frameDy * frameDy)
                val vignette = 1f - ((frameDistance / maxDistance) * spec.vignetteStrength).coerceIn(0f, 0.28f)
                red *= vignette
                green *= vignette
                blue *= vignette

                val subjectWeight = 1f - blurMix
                val smoothingMix = (spec.subjectSmoothing * subjectWeight).coerceIn(0f, 0.28f)
                if (smoothingMix > 0f) {
                    red = mixChannel(red, blurredRed, smoothingMix)
                    green = mixChannel(green, blurredGreen, smoothingMix)
                    blue = mixChannel(blue, blurredBlue, smoothingMix)
                }

                val luminance = (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
                val saturationBoost = spec.subjectSaturationBoost * subjectWeight
                if (saturationBoost > 0f) {
                    red = luminance + (red - luminance) * (1f + saturationBoost)
                    green = luminance + (green - luminance) * (1f + saturationBoost)
                    blue = luminance + (blue - luminance) * (1f + saturationBoost)
                }

                val lift = spec.subjectLift * subjectWeight
                if (lift > 0f) {
                    red += (255f - red) * lift
                    green += (255f - green) * lift
                    blue += (255f - blue) * lift
                }

                val highlightFactor = ((luminance / 255f) - 0.52f).coerceIn(0f, 1f)
                val subjectBloom = spec.highlightBloom * subjectWeight * highlightFactor
                val backgroundBloom = spec.backgroundBloom * blurMix * highlightFactor
                val totalBloom = (subjectBloom + backgroundBloom).coerceIn(0f, 0.24f)
                if (totalBloom > 0f) {
                    red += (255f - red) * totalBloom
                    green += (255f - green) * totalBloom
                    blue += (255f - blue) * totalBloom
                }

                originalPixels[index] = (alpha shl 24) or
                    (clampChannel(red).toInt() shl 16) or
                    (clampChannel(green).toInt() shl 8) or
                    clampChannel(blue).toInt()
            }
        }

        original.setPixels(originalPixels, 0, width, 0, 0, width, height)
    }

    private fun readSourceBytes(target: ProcessorTarget): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) null else file.readBytes()
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
        return if (restored.isSuccess) null else "exif-restore-failed"
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
