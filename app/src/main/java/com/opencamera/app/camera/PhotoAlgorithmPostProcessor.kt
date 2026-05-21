package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotResult
import com.opencamera.core.settings.FilterRenderSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal sealed interface PhotoAlgorithmWork {
    data object None : PhotoAlgorithmWork
    data class Render(
        val target: PhotoAlgorithmTarget,
        val spec: PhotoAlgorithmSpec
    ) : PhotoAlgorithmWork

    data class DiagnosticSkip(val reason: String) : PhotoAlgorithmWork
}

internal sealed interface PhotoAlgorithmTarget {
    data class FilePath(val path: String) : PhotoAlgorithmTarget
    data class ContentUri(val value: String) : PhotoAlgorithmTarget
}

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
    val coolBoost: Float = 0f
)

internal sealed interface PhotoAlgorithmEditorResult {
    data class Applied(val warning: String? = null) : PhotoAlgorithmEditorResult
    data class Skipped(val reason: String) : PhotoAlgorithmEditorResult
    data class Failed(val reason: String) : PhotoAlgorithmEditorResult
}

internal interface PhotoAlgorithmEditor {
    suspend fun apply(
        target: PhotoAlgorithmTarget,
        spec: PhotoAlgorithmSpec
    ): PhotoAlgorithmEditorResult
}

internal fun decidePhotoAlgorithmWork(result: ShotResult): PhotoAlgorithmWork {
    if (result.mediaType != MediaType.PHOTO) {
        return PhotoAlgorithmWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return PhotoAlgorithmWork.DiagnosticSkip("unsupported-mime")
    }
    val profile = result.metadata.algorithmProfile
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val spec = FilterRenderSpec.fromMetadataTags(result.metadata.customTags)?.toPhotoAlgorithmSpec(
        profile = result.metadata.customTags["filterProfile"]
            ?: profile
            ?: "shared-filter"
    ) ?: profile?.let(::resolvePhotoAlgorithmSpec)
        ?: return PhotoAlgorithmWork.None
    val target = result.outputHandle.toPhotoAlgorithmTargetOrNull()
        ?: return PhotoAlgorithmWork.DiagnosticSkip("missing-output-handle")

    return PhotoAlgorithmWork.Render(
        target = target,
        spec = spec
    )
}

private fun FilterRenderSpec.toPhotoAlgorithmSpec(profile: String): PhotoAlgorithmSpec {
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
        coolBoost = coolBoost
    )
}

internal class PhotoAlgorithmPostProcessor(
    private val editor: PhotoAlgorithmEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoAlgorithmWork(result)) {
            PhotoAlgorithmWork.None -> result
            is PhotoAlgorithmWork.DiagnosticSkip -> result.withPipelineNotes(
                "algorithm-render:skipped:${work.reason}"
            )

            is PhotoAlgorithmWork.Render -> {
                when (val renderResult = editor.apply(work.target, work.spec)) {
                    is PhotoAlgorithmEditorResult.Applied -> {
                        if (renderResult.warning == null) {
                            result.withPipelineNotes(
                                "algorithm-render:applied:${work.spec.profile}"
                            )
                        } else {
                            result.withPipelineNotes(
                                "algorithm-render:applied:${work.spec.profile}",
                                "algorithm-render:warning:${renderResult.warning}"
                            )
                        }
                    }

                    is PhotoAlgorithmEditorResult.Skipped -> result.withPipelineNotes(
                        "algorithm-render:skipped:${renderResult.reason}"
                    )

                    is PhotoAlgorithmEditorResult.Failed -> result.withPipelineNotes(
                        "algorithm-render:failed:${renderResult.reason}"
                    )
                }
            }
        }
    }
}

internal class AndroidPhotoAlgorithmEditor(
    context: Context
) : PhotoAlgorithmEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: PhotoAlgorithmTarget,
        spec: PhotoAlgorithmSpec
    ): PhotoAlgorithmEditorResult = withContext(Dispatchers.IO) {
        val sourceBytes = readSourceBytes(target)
            ?: return@withContext PhotoAlgorithmEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext PhotoAlgorithmEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return@withContext PhotoAlgorithmEditorResult.Failed("decode-failed")
        val mutableBitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
        if (mutableBitmap !== decoded) {
            decoded.recycle()
        }

        try {
            applyStyle(
                bitmap = mutableBitmap,
                spec = spec
            )
            val encodedBytes = ByteArrayOutputStream().use { output ->
                check(mutableBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Algorithm JPEG compression failed"
                }
                output.toByteArray()
            }
            if (!writeEncodedBytes(target, encodedBytes)) {
                return@withContext PhotoAlgorithmEditorResult.Failed("output-unavailable")
            }

            val exifWarning = restorePreservedExif(target, preservedExif)
            PhotoAlgorithmEditorResult.Applied(exifWarning)
        } catch (_: Throwable) {
            PhotoAlgorithmEditorResult.Failed("render-exception")
        } finally {
            mutableBitmap.recycle()
        }
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

    private fun readSourceBytes(target: PhotoAlgorithmTarget): ByteArray? {
        return when (target) {
            is PhotoAlgorithmTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) {
                    null
                } else {
                    file.readBytes()
                }
            }

            is PhotoAlgorithmTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }

    private fun writeEncodedBytes(
        target: PhotoAlgorithmTarget,
        encodedBytes: ByteArray
    ): Boolean {
        return when (target) {
            is PhotoAlgorithmTarget.FilePath -> runCatching {
                File(target.path).outputStream().use { it.write(encodedBytes) }
            }.isSuccess

            is PhotoAlgorithmTarget.ContentUri -> {
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
        target: PhotoAlgorithmTarget,
        preservedExif: Map<String, String>
    ): String? {
        if (preservedExif.isEmpty()) {
            return null
        }

        val restored = runCatching {
            when (target) {
                is PhotoAlgorithmTarget.FilePath -> {
                    ExifInterface(target.path).apply {
                        preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
                        saveAttributes()
                    }
                }

                is PhotoAlgorithmTarget.ContentUri -> {
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

internal fun resolvePhotoAlgorithmSpec(profile: String): PhotoAlgorithmSpec? {
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
    }
}

private fun MediaOutputHandle.toPhotoAlgorithmTargetOrNull(): PhotoAlgorithmTarget? {
    contentUri?.let { return PhotoAlgorithmTarget.ContentUri(it) }
    filePath?.let { return PhotoAlgorithmTarget.FilePath(it) }
    val absPath = if (File(displayPath).isAbsolute) {
        displayPath
    } else {
        @Suppress("DEPRECATION")
        File(android.os.Environment.getExternalStoragePublicDirectory(null), displayPath).absolutePath
    }
    return File(absPath).takeIf { it.exists() }?.absolutePath?.let(PhotoAlgorithmTarget::FilePath)
}

private fun ShotResult.withPipelineNotes(vararg notes: String): ShotResult {
    return copy(pipelineNotes = pipelineNotes + notes)
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
