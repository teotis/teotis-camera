package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.AlgorithmJobClass
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.OcwmJpegContainer
import com.opencamera.core.media.PostProcessFailure
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessOutputIntegrity
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ReversibleWatermarkArchiveManifest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.addStructuredPostProcessFailure
import com.opencamera.core.media.sha256Hex
import com.opencamera.core.media.toProcessorTargetOrNull
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

internal sealed interface PhotoWatermarkWork {
    data object None : PhotoWatermarkWork
    data class Render(
        val target: ProcessorTarget,
        val metadata: MediaMetadata,
        val watermarkText: String,
        val templateId: String
    ) : PhotoWatermarkWork

    data class DiagnosticSkip(
        val reason: String,
        val templateId: String? = null
    ) : PhotoWatermarkWork
}

internal data class ResolvedPhotoWatermarkTemplate(
    val templateId: String,
    val title: String,
    val supportingLines: List<String>,
    val frameBackground: WatermarkFrameBackground,
    val usesExpandedFrame: Boolean,
    val placement: WatermarkTextPlacement,
    val textScale: Float,
    val textOpacity: Float,
    val captureCropZoom: Float = 1f,
    val warning: String? = null
)

internal data class PhotoWatermarkBitmapRenderResult(
    val bitmap: Bitmap,
    val warning: String? = null
)

internal enum class WatermarkSceneVariant(
    val referenceAspect: Float
) {
    PORTRAIT(1080f / 1660f),
    SQUARE(1f),
    LANDSCAPE(1660f / 1080f);

    companion object {
        fun closestTo(aspect: Float): WatermarkSceneVariant =
            entries.minBy { kotlin.math.abs(it.referenceAspect - aspect) }
    }
}

internal data class StaticWatermarkFrameAsset(
    val packageId: String,
    val variant: WatermarkSceneVariant,
    val assetPath: String,
    val referenceAspect: Float
)

internal data class StaticWatermarkAssetPackage(
    val templateId: String,
    val frameAssets: Map<WatermarkSceneVariant, StaticWatermarkFrameAsset>
) {
    fun frameAssetFor(aspect: Float): StaticWatermarkFrameAsset {
        val variant = WatermarkSceneVariant.closestTo(aspect)
        return frameAssets.getValue(variant)
    }
}

internal object StaticHighDesignWatermarkPackages {
    val VAN_GOGH_STARRY = staticPackage(
        templateId = TEMPLATE_VAN_GOGH_STARRY,
        portraitPath = "watermarks/van_gogh_starry_portrait.png",
        squarePath = "watermarks/van_gogh_starry_square.png",
        landscapePath = "watermarks/van_gogh_starry_landscape.png"
    )
    val BLUE_HOUR = staticPackage(
        templateId = TEMPLATE_BLUE_HOUR,
        portraitPath = "watermarks/blue_hour_portrait.png",
        squarePath = "watermarks/blue_hour_square.png",
        landscapePath = "watermarks/blue_hour_landscape.png"
    )

    fun forTemplate(templateId: String): StaticWatermarkAssetPackage? =
        when (templateId) {
            TEMPLATE_VAN_GOGH_STARRY -> VAN_GOGH_STARRY
            TEMPLATE_BLUE_HOUR -> BLUE_HOUR
            else -> null
        }

    private fun staticPackage(
        templateId: String,
        portraitPath: String,
        squarePath: String,
        landscapePath: String
    ): StaticWatermarkAssetPackage {
        return StaticWatermarkAssetPackage(
            templateId = templateId,
            frameAssets = WatermarkSceneVariant.entries.associateWith { variant ->
                StaticWatermarkFrameAsset(
                    packageId = templateId,
                    variant = variant,
                    assetPath = when (variant) {
                        WatermarkSceneVariant.PORTRAIT -> portraitPath
                        WatermarkSceneVariant.SQUARE -> squarePath
                        WatermarkSceneVariant.LANDSCAPE -> landscapePath
                    },
                    referenceAspect = variant.referenceAspect
                )
            }
        )
    }
}

internal interface StaticWatermarkAssetProvider {
    fun load(asset: StaticWatermarkFrameAsset): Bitmap?
}

internal data class WatermarkScene(
    val templateId: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val photoSlot: RectF,
    val safeArea: RectF,
    val variant: WatermarkSceneVariant,
    val layers: List<WatermarkSceneLayer>
)

internal sealed interface WatermarkSceneLayer {
    data class SolidBackground(
        val frameBackground: WatermarkFrameBackground
    ) : WatermarkSceneLayer

    data object PhotoSlot : WatermarkSceneLayer

    data class BorderAsset(
        val asset: StaticWatermarkFrameAsset,
        val destination: RectF,
        val mask: WatermarkSceneMask = WatermarkSceneMask.NONE
    ) : WatermarkSceneLayer

    data class TextSlot(
        val text: String,
        val bounds: RectF,
        val style: WatermarkTextSlotStyle,
        val alignment: Paint.Align,
        val textSize: Float
    ) : WatermarkSceneLayer
}

internal enum class WatermarkSceneMask {
    NONE,
    CLEAR_PHOTO_SLOT
}

internal enum class WatermarkTextSlotStyle {
    STARRY_METADATA,
    BLUE_HOUR_TITLE,
    BLUE_HOUR_METADATA
}

internal enum class VanGoghStarryFrameAssetVariant(
    val assetPath: String,
    val referenceAspect: Float
) {
    PORTRAIT(
        assetPath = "watermarks/van_gogh_starry_portrait.png",
        referenceAspect = 1080f / 1660f
    ),
    SQUARE(
        assetPath = "watermarks/van_gogh_starry_square.png",
        referenceAspect = 1f
    ),
    LANDSCAPE(
        assetPath = "watermarks/van_gogh_starry_landscape.png",
        referenceAspect = 1660f / 1080f
    );

    companion object {
        fun closestTo(aspect: Float): VanGoghStarryFrameAssetVariant =
            entries.minBy { kotlin.math.abs(it.referenceAspect - aspect) }
    }
}

internal interface VanGoghStarryFrameAssetProvider {
    fun load(variant: VanGoghStarryFrameAssetVariant): Bitmap?
}

internal data class PhotoWatermarkApplied(
    val warning: String? = null,
    val timingNote: String? = null
) : ProcessorEditorResult

internal interface PhotoWatermarkEditor {
    suspend fun apply(
        target: ProcessorTarget,
        metadata: MediaMetadata,
        watermarkText: String,
        templateId: String
    ): ProcessorEditorResult
}

internal fun decidePhotoWatermarkWork(result: ShotResult): PhotoWatermarkWork {
    when (result.photoJpegInput()) {
        PhotoJpegInput.NOT_PHOTO -> return PhotoWatermarkWork.None
        PhotoJpegInput.UNSUPPORTED_MIME ->
            return PhotoWatermarkWork.DiagnosticSkip("unsupported-mime")
        PhotoJpegInput.EDITABLE -> Unit
    }
    val templateId = resolveWatermarkTemplateId(
        result.metadata.customTags[PHOTO_WATERMARK_TEMPLATE_KEY]
    )
    if (!metadataRequestsPhotoWatermark(result.metadata)) {
        return PhotoWatermarkWork.None
    }
    val watermarkText = result.metadata.watermarkText
        ?.trim()
        .orEmpty()

    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return PhotoWatermarkWork.DiagnosticSkip(
            reason = "missing-output-handle",
            templateId = templateId
        )

    return PhotoWatermarkWork.Render(
        target = target,
        metadata = result.metadata,
        watermarkText = watermarkText,
        templateId = templateId
    )
}

internal fun metadataRequestsPhotoWatermark(metadata: MediaMetadata): Boolean {
    if (!metadata.watermarkText.isNullOrBlank()) {
        return true
    }
    val explicitTemplateId = metadata.customTags[PHOTO_WATERMARK_TEMPLATE_KEY]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return false
    return explicitTemplateId != TEMPLATE_CLASSIC_OVERLAY
}

private const val TAG = "PhotoWatermarkPP"

internal class PhotoWatermarkPostProcessor(
    private val editor: PhotoWatermarkEditor
) : MediaPostProcessor {
    override fun isApplicable(result: ShotResult): Boolean = result.mediaType == MediaType.PHOTO

    override fun jobClass(result: ShotResult): AlgorithmJobClass =
        AlgorithmJobClass.CAPTURE_OPTIONAL

    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoWatermarkWork(result)) {
            PhotoWatermarkWork.None -> result
            is PhotoWatermarkWork.DiagnosticSkip -> result.addPipelineNotes(
                *buildList {
                    work.templateId?.let { add("watermark:template:$it") }
                    add("watermark:skipped:${work.reason}")
                }.toTypedArray()
            )

            is PhotoWatermarkWork.Render -> {
                when (val renderResult = editor.apply(
                    target = work.target,
                    metadata = work.metadata,
                    watermarkText = work.watermarkText,
                    templateId = work.templateId
                )) {
                    is PhotoWatermarkApplied -> {
                        result.addPipelineNotes(
                            *buildList {
                                add("watermark:rendered:${work.templateId}")
                                renderResult.warning?.let { add("watermark:warning:$it") }
                                renderResult.timingNote?.let { add(it) }
                            }.toTypedArray()
                        )
                    }

                    is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                        "watermark:template:${work.templateId}",
                        "watermark:skipped:${renderResult.reason}"
                    )

                    is ProcessorEditorResult.Failed -> {
                        val (cause, integrity) = watermarkFailureMapping(renderResult.reason)
                        val structuredFailure = PostProcessFailure(
                            stage = PostProcessFailureStage.WATERMARK,
                            cause = cause,
                            integrity = integrity,
                            disposition = PostProcessFailureDisposition.RECOVERABLE
                        )
                        result.addPipelineNotes(
                            "watermark:template:${work.templateId}",
                            "watermark:failed:${renderResult.reason}"
                        ).addStructuredPostProcessFailure(structuredFailure)
                    }

                    else -> result
                }
            }
        }
    }
}

internal fun watermarkFailureMapping(reason: String): Pair<PostProcessFailureCause, PostProcessOutputIntegrity> =
    when (reason) {
        "decode-failed" -> PostProcessFailureCause.DECODE_FAILED to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "decode-oom" -> PostProcessFailureCause.OUT_OF_MEMORY to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "decode-exception" -> PostProcessFailureCause.DECODE_FAILED to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "encode-oom" -> PostProcessFailureCause.OUT_OF_MEMORY to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "encode-failed" -> PostProcessFailureCause.ENCODE to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "output-unavailable" -> PostProcessFailureCause.OUTPUT_UNAVAILABLE to PostProcessOutputIntegrity.POSSIBLY_MODIFIED
        else -> PostProcessFailureCause.EXCEPTION to PostProcessOutputIntegrity.ORIGINAL_INTACT
    }

internal class AndroidPhotoWatermarkEditor(
    context: Context,
    internal val vanGoghStarryFrameAssetProvider: VanGoghStarryFrameAssetProvider =
        AndroidVanGoghStarryFrameAssetProvider(context.applicationContext),
    internal val staticWatermarkAssetProvider: StaticWatermarkAssetProvider =
        AndroidStaticWatermarkAssetProvider(context.applicationContext)
) : PhotoWatermarkEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: ProcessorTarget,
        metadata: MediaMetadata,
        watermarkText: String,
        templateId: String
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val sourceBytes = ProcessorIOUtils.readSourceBytes(target, contentResolver)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val mutableBitmap = try {
            MutableArgbBitmapDecoder.decode(sourceBytes)
                ?: return@withContext ProcessorEditorResult.Failed("decode-failed")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Watermark decode failed: out of memory", e)
            return@withContext ProcessorEditorResult.Failed("decode-oom")
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Watermark decode failed", e)
            return@withContext ProcessorEditorResult.Failed("decode-exception")
        }
        val t1 = System.currentTimeMillis()

        val originalWidth = mutableBitmap.width
        val originalHeight = mutableBitmap.height

        var renderedBitmap: Bitmap? = null
        try {
            val resolvedTemplate = resolvePhotoWatermarkTemplate(
                templateId = templateId,
                watermarkText = watermarkText,
                metadata = metadata,
                preservedExif = preservedExif
            )
            val renderResult = renderPhotoWatermarkBitmap(
                bitmap = mutableBitmap,
                template = resolvedTemplate,
                vanGoghStarryFrameAssetProvider = vanGoghStarryFrameAssetProvider,
                staticWatermarkAssetProvider = staticWatermarkAssetProvider
            )
            renderedBitmap = renderResult.bitmap
            val t2 = System.currentTimeMillis()
            val encodedBytes = try {
                ByteArrayOutputStream().use { output ->
                    check(renderResult.bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Watermark JPEG compression failed"
                    }
                    output.toByteArray()
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Watermark encode failed: out of memory", e)
                return@withContext ProcessorEditorResult.Failed("encode-oom")
            } catch (e: Throwable) {
                e.rethrowIfCancellationOrFatal()
                Log.w(TAG, "Watermark encode failed", e)
                return@withContext ProcessorEditorResult.Failed("encode-failed")
            }
            val t3 = System.currentTimeMillis()

            val (archiveBytes, archiveWarning) = embedArchiveAfterVisibleWrite(
                originalBytes = sourceBytes,
                visibleBytesAfterExifRestore = encodedBytes,
                templateId = templateId,
                originalWidth = originalWidth,
                originalHeight = originalHeight
            )
            val finalBytes = archiveBytes ?: encodedBytes
            if (!contentResolver.writeEncodedBytes(target, finalBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }
            val t4 = System.currentTimeMillis()

            val exifWarning = contentResolver.restorePreservedExif(
                target = target,
                preservedExif = preservedExif
            )
            val t5 = System.currentTimeMillis()

            val timingNote = "watermark:timing:$templateId size=${originalWidth}x${originalHeight} " +
                "decode=${t1 - t0}ms render=${t2 - t1}ms encode=${t3 - t2}ms " +
                "write+archive=${t4 - t3}ms exif=${t5 - t4}ms total=${t5 - t0}ms"

            PhotoWatermarkApplied(
                warning = mergeWarnings(renderResult.warning, exifWarning, archiveWarning),
                timingNote = timingNote
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Watermark render failed: out of memory", e)
            ProcessorEditorResult.Failed("render-oom")
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Watermark render failed", e)
            ProcessorEditorResult.Failed("render-exception")
        } finally {
            if (renderedBitmap != null && renderedBitmap !== mutableBitmap) {
                renderedBitmap.recycle()
            }
            mutableBitmap.recycle()
        }
    }

    private fun mergeWarnings(
        templateWarning: String?,
        exifWarning: String?,
        archiveWarning: String? = null,
        archiveWriteWarning: String? = null
    ): String? {
        return listOfNotNull(templateWarning, exifWarning, archiveWarning, archiveWriteWarning)
            .takeIf { warnings -> warnings.isNotEmpty() }
            ?.joinToString(separator = ",")
    }

    companion object {
        private const val JPEG_QUALITY = 92
    }
}

private class AndroidVanGoghStarryFrameAssetProvider(
    context: Context
) : VanGoghStarryFrameAssetProvider {
    private val appContext = context.applicationContext
    private val cache = mutableMapOf<VanGoghStarryFrameAssetVariant, Bitmap>()

    override fun load(variant: VanGoghStarryFrameAssetVariant): Bitmap? {
        cache[variant]?.let { return it }
        return try {
            appContext.assets.open(variant.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }?.also { decoded ->
                cache[variant] = decoded
            }
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Unable to load Van Gogh starry frame asset: ${variant.assetPath}", e)
            null
        }
    }
}

private class AndroidStaticWatermarkAssetProvider(
    context: Context
) : StaticWatermarkAssetProvider {
    private val appContext = context.applicationContext
    private val cache = mutableMapOf<String, Bitmap>()

    override fun load(asset: StaticWatermarkFrameAsset): Bitmap? {
        cache[asset.assetPath]?.let { return it }
        return try {
            appContext.assets.open(asset.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }?.also { decoded ->
                cache[asset.assetPath] = decoded
            }
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "Unable to load static watermark asset: ${asset.assetPath}", e)
            null
        }
    }
}

internal fun buildWatermarkArchive(
    originalBytes: ByteArray,
    visibleBytes: ByteArray,
    templateId: String,
    originalWidth: Int,
    originalHeight: Int
): OcwmJpegContainer.EmbeddedArchive? {
    if (originalBytes.isEmpty() || visibleBytes.isEmpty()) return null
    val manifest = ReversibleWatermarkArchiveManifest(
        watermarkTemplateId = templateId,
        visibleImageSha256 = sha256Hex(visibleBytes),
        payloadSha256 = sha256Hex(originalBytes),
        payloadLength = originalBytes.size.toLong(),
        originalWidth = originalWidth,
        originalHeight = originalHeight
    )
    return OcwmJpegContainer.EmbeddedArchive(manifest = manifest, payload = originalBytes)
}

internal fun embedArchiveAfterVisibleWrite(
    originalBytes: ByteArray,
    visibleBytesAfterExifRestore: ByteArray?,
    templateId: String,
    originalWidth: Int,
    originalHeight: Int
): Pair<ByteArray?, String?> {
    if (originalBytes.isEmpty()) return null to "archive-input-empty"
    if (visibleBytesAfterExifRestore == null) return null to "archive-visible-unavailable"
    val archive = buildWatermarkArchive(
        originalBytes = originalBytes,
        visibleBytes = visibleBytesAfterExifRestore,
        templateId = templateId,
        originalWidth = originalWidth,
        originalHeight = originalHeight
    ) ?: return null to "archive-embed-failed"
    return try {
        OcwmJpegContainer.embedArchive(visibleBytesAfterExifRestore, archive) to null
    } catch (e: Throwable) {
        e.rethrowIfCancellationOrFatal()
        Log.w(TAG, "Watermark archive embed failed", e)
        null to "archive-embed-failed"
    }
}

private const val TEXT_SIZE_RATIO = 0.042f
private const val DETAIL_TEXT_SCALE = 0.58f
private const val PADDING_RATIO = 0.028f
private const val MIN_TEXT_SIZE_PX = 24f
private const val MIN_DETAIL_TEXT_SIZE_PX = 18f
private const val MAX_TEXT_SIZE_PX = 88f
private const val MAX_TEXT_SCALE = 1.4f
private const val MIN_PADDING_PX = 18f
private const val MIN_CORNER_RADIUS_PX = 12f
private const val BLUR_DOWNSAMPLE_DIVISOR = 18
private const val BLUR_EDGE_DOWNSAMPLE_DIVISOR = 8

private enum class ExpandedFrameDecoration {
    NONE,
    TRAVEL_MAP,
    ARCHIVAL_PAPER,
    NIGHT_MEMORY
}

private data class ComplexWatermarkFrameLayout(
    val framedWidth: Int,
    val framedHeight: Int,
    val sideBorder: Float,
    val topBorder: Float,
    val bottomBandHeight: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val padding: Float
) {
    val bandTop: Float = topBorder + sourceHeight
    val sourceRect: RectF
        get() = RectF(
            sideBorder,
            topBorder,
            sideBorder + sourceWidth,
            topBorder + sourceHeight
        )
    val bottomBandRect: RectF
        get() = RectF(0f, bandTop, framedWidth.toFloat(), framedHeight.toFloat())
}

internal enum class PhotoWatermarkMetadataToken {
    DATETIME,
    LOCATION,
    CAMERA_PARAMS,
    PROFILE_NAME
}

internal enum class PhotoWatermarkTemplateType(
    val storageKey: String,
    val defaultPlacement: WatermarkTextPlacement,
    val defaultFrameBackground: WatermarkFrameBackground,
    val usesExpandedFrame: Boolean,
    val metadataTokens: List<PhotoWatermarkMetadataToken>
) {
    CLASSIC_OVERLAY(
        storageKey = TEMPLATE_CLASSIC_OVERLAY,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        defaultFrameBackground = WatermarkFrameBackground.DARK,
        usesExpandedFrame = false,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.LOCATION,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS
        )
    ),
    TRAVEL_POLAROID(
        storageKey = TEMPLATE_TRAVEL_POLAROID,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        defaultFrameBackground = WatermarkFrameBackground.WHITE,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.LOCATION,
            PhotoWatermarkMetadataToken.PROFILE_NAME
        )
    ),
    RETRO_FRAME(
        storageKey = TEMPLATE_RETRO_FRAME,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
        defaultFrameBackground = WatermarkFrameBackground.WHITE,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS,
            PhotoWatermarkMetadataToken.PROFILE_NAME
        )
    ),
    PURE_TEXT(
        storageKey = TEMPLATE_PURE_TEXT,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        defaultFrameBackground = WatermarkFrameBackground.DARK,
        usesExpandedFrame = false,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.LOCATION,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS
        )
    ),
    BLUR_FOUR_BORDER(
        storageKey = TEMPLATE_BLUR_FOUR_BORDER,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        defaultFrameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS,
            PhotoWatermarkMetadataToken.PROFILE_NAME
        )
    ),
    PROFESSIONAL_BOTTOM_BAR(
        storageKey = TEMPLATE_PROFESSIONAL_BOTTOM_BAR,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
        defaultFrameBackground = WatermarkFrameBackground.DARK,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS
        )
    ),
    NIGHT_STREET(
        storageKey = TEMPLATE_NIGHT_STREET,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        defaultFrameBackground = WatermarkFrameBackground.DARK,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.LOCATION,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS
        )
    ),
    VAN_GOGH_STARRY(
        storageKey = TEMPLATE_VAN_GOGH_STARRY,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
        defaultFrameBackground = WatermarkFrameBackground.DARK,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.LOCATION,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS
        )
    ),
    BLUE_HOUR(
        storageKey = TEMPLATE_BLUE_HOUR,
        defaultPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        defaultFrameBackground = WatermarkFrameBackground.DARK,
        usesExpandedFrame = true,
        metadataTokens = listOf(
            PhotoWatermarkMetadataToken.DATETIME,
            PhotoWatermarkMetadataToken.LOCATION,
            PhotoWatermarkMetadataToken.CAMERA_PARAMS
        )
    );

    fun resolveFrameBackground(requested: WatermarkFrameBackground?): WatermarkFrameBackground {
        val resolved = requested ?: defaultFrameBackground
        return when (this) {
            BLUR_FOUR_BORDER -> resolved.takeIf { it in SUPPORTED_BLUR_BACKGROUNDS }
                ?: defaultFrameBackground
            RETRO_FRAME -> resolved.takeIf { it in SUPPORTED_RETRO_FRAME_BACKGROUNDS }
                ?: defaultFrameBackground
            NIGHT_STREET,
            VAN_GOGH_STARRY,
            BLUE_HOUR -> resolved.takeIf { it in SUPPORTED_NIGHT_STREET_BACKGROUNDS }
                ?: defaultFrameBackground
            else -> resolved
        }
    }

    companion object {
        private val byStorageKey = entries.associateBy(PhotoWatermarkTemplateType::storageKey)

        fun fromStorageKeyOrNull(storageKey: String?): PhotoWatermarkTemplateType? {
            return storageKey
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(byStorageKey::get)
        }
    }
}

internal fun resolvePhotoWatermarkTemplateType(templateId: String?): PhotoWatermarkTemplateType {
    return PhotoWatermarkTemplateType.fromStorageKeyOrNull(templateId)
        ?: PhotoWatermarkTemplateType.CLASSIC_OVERLAY
}

internal fun usesComplexWatermarkOverlay(templateId: String?): Boolean {
    return when (PhotoWatermarkTemplateType.fromStorageKeyOrNull(templateId)) {
        PhotoWatermarkTemplateType.VAN_GOGH_STARRY,
        PhotoWatermarkTemplateType.BLUE_HOUR -> true
        else -> false
    }
}

internal fun renderPhotoWatermarkBitmap(
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    vanGoghStarryFrameAssetProvider: VanGoghStarryFrameAssetProvider? = null,
    staticWatermarkAssetProvider: StaticWatermarkAssetProvider? = null
): PhotoWatermarkBitmapRenderResult {
    val minEdge = minOf(bitmap.width, bitmap.height).toFloat()
    val titleTextSize = (
        (minEdge * TEXT_SIZE_RATIO).coerceIn(MIN_TEXT_SIZE_PX, MAX_TEXT_SIZE_PX) *
            template.textScale
        ).coerceIn(MIN_TEXT_SIZE_PX, MAX_TEXT_SIZE_PX * MAX_TEXT_SCALE)
    val detailTextSize = (titleTextSize * DETAIL_TEXT_SCALE).coerceAtLeast(MIN_DETAIL_TEXT_SIZE_PX)
    val padding = (minEdge * PADDING_RATIO).coerceAtLeast(MIN_PADDING_PX)
    val templateType = PhotoWatermarkTemplateType.fromStorageKeyOrNull(template.templateId)
        ?: return PhotoWatermarkBitmapRenderResult(
            bitmap = bitmap,
            warning = mergeWarnings(template.warning, "template-fallback")
        )
    return when (templateType) {
        PhotoWatermarkTemplateType.CLASSIC_OVERLAY -> {
            val canvas = Canvas(bitmap)
            drawClassicOverlay(
                canvas = canvas,
                bitmap = bitmap,
                template = template,
                titleTextSize = titleTextSize,
                detailTextSize = detailTextSize,
                padding = padding
            )
            PhotoWatermarkBitmapRenderResult(
                bitmap = bitmap,
                warning = template.warning
            )
        }

        PhotoWatermarkTemplateType.TRAVEL_POLAROID -> drawExpandedFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize,
            detailTextSize = detailTextSize,
            padding = padding,
            sideBorderScale = 1.0f,
            topBorderScale = 0.9f,
            bottomBandScale = 4.6f,
            titleColor = Color.rgb(42, 82, 61),
            detailColor = Color.rgb(94, 125, 103),
            centered = false,
            decoration = ExpandedFrameDecoration.TRAVEL_MAP
        )

        PhotoWatermarkTemplateType.PURE_TEXT -> {
            val canvas = Canvas(bitmap)
            drawTranslucentBottomBarOverlay(
                canvas = canvas,
                bitmap = bitmap,
                template = template,
                titleTextSize = titleTextSize,
                detailTextSize = detailTextSize,
                padding = padding
            )
            PhotoWatermarkBitmapRenderResult(bitmap = bitmap, warning = template.warning)
        }

        PhotoWatermarkTemplateType.RETRO_FRAME -> drawExpandedFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize * 0.84f,
            detailTextSize = detailTextSize * 0.9f,
            padding = padding,
            sideBorderScale = 0.88f,
            topBorderScale = 0.74f,
            bottomBandScale = 2.6f,
            titleColor = Color.argb(250, 224, 205, 154),
            detailColor = Color.argb(220, 188, 166, 118),
            centered = true,
            decoration = ExpandedFrameDecoration.ARCHIVAL_PAPER
        )

        PhotoWatermarkTemplateType.BLUR_FOUR_BORDER -> drawBlurFourBorderFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize,
            detailTextSize = detailTextSize,
            padding = padding
        )

        PhotoWatermarkTemplateType.PROFESSIONAL_BOTTOM_BAR -> drawProfessionalBottomBar(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize,
            detailTextSize = detailTextSize,
            padding = padding
        )

        PhotoWatermarkTemplateType.NIGHT_STREET -> drawExpandedFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize * 0.98f,
            detailTextSize = detailTextSize,
            padding = padding,
            sideBorderScale = 0.95f,
            topBorderScale = 0.82f,
            bottomBandScale = 4.35f,
            titleColor = Color.argb(255, 230, 234, 240),
            detailColor = Color.argb(220, 174, 184, 198),
            centered = false,
            decoration = ExpandedFrameDecoration.NIGHT_MEMORY
        )

        PhotoWatermarkTemplateType.VAN_GOGH_STARRY -> drawVanGoghStarryFrame(
            source = bitmap,
            template = template,
            detailTextSize = detailTextSize,
            padding = padding,
            frameAssetProvider = vanGoghStarryFrameAssetProvider,
            staticWatermarkAssetProvider = staticWatermarkAssetProvider
        )

        PhotoWatermarkTemplateType.BLUE_HOUR -> drawBlueHourFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize * 1.12f,
            detailTextSize = detailTextSize,
            padding = padding,
            staticWatermarkAssetProvider = staticWatermarkAssetProvider
        )
    }
}

private fun drawClassicOverlay(
    canvas: Canvas,
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
) {
    val cornerRadius = (padding * 0.8f).coerceAtLeast(MIN_CORNER_RADIUS_PX)
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        setShadowLayer(titleTextSize * 0.18f, 0f, titleTextSize * 0.08f, Color.argb(160, 0, 0, 0))
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 220, 232, 245)
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (235 * template.textOpacity).toInt()
    }
    val lines = listOf(template.title) + template.supportingLines
    val blockWidth = lines.maxOf { line ->
        titlePaint.measureText(line).coerceAtLeast(detailPaint.measureText(line))
    }
    val lineGap = detailTextSize * 0.28f
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val titleHeight = titleMetrics.descent - titleMetrics.ascent
    val detailHeight = detailMetrics.descent - detailMetrics.ascent
    val blockHeight = titleHeight +
        template.supportingLines.size * detailHeight +
        template.supportingLines.size * lineGap
    val left = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.BOTTOM_LEFT -> padding
        WatermarkTextPlacement.BOTTOM_CENTER -> (bitmap.width - blockWidth) / 2f
        WatermarkTextPlacement.TOP_RIGHT,
        WatermarkTextPlacement.BOTTOM_RIGHT -> bitmap.width - padding - blockWidth - padding
    }
    val top = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.TOP_RIGHT -> padding
        WatermarkTextPlacement.BOTTOM_LEFT,
        WatermarkTextPlacement.BOTTOM_RIGHT,
        WatermarkTextPlacement.BOTTOM_CENTER -> bitmap.height - padding - blockHeight - padding
    }
    val backgroundRect = RectF(
        left - padding,
        top - padding,
        left + blockWidth + padding,
        top + blockHeight + padding
    )
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((144 * template.textOpacity).toInt(), 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

    var baseline = top - titleMetrics.ascent
    canvas.drawText(template.title, left, baseline, titlePaint)
    template.supportingLines.forEach { line ->
        baseline += detailHeight + lineGap
        canvas.drawText(line, left, baseline, detailPaint)
    }
}

private fun drawTranslucentBottomBarOverlay(
    canvas: Canvas,
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
) {
    val minEdge = minOf(bitmap.width, bitmap.height).toFloat()
    val barHeight = maxOf(
        minEdge * 0.19f,
        titleTextSize + detailTextSize * 2.0f + padding * 1.35f
    ).coerceAtMost(bitmap.height * 0.28f)
    val barTop = bitmap.height - barHeight
    val barRect = RectF(0f, barTop, bitmap.width.toFloat(), bitmap.height.toFloat())
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((188 * template.textOpacity).toInt().coerceIn(0, 188), 7, 19, 33)
        style = Paint.Style.FILL
    }
    canvas.drawRect(barRect, backgroundPaint)

    val accentWidth = (minEdge * 0.01f).coerceIn(3f, 7f)
    val accentLeft = padding * 0.68f
    val accentRect = RectF(
        accentLeft,
        barTop + barHeight * 0.22f,
        accentLeft + accentWidth,
        bitmap.height - barHeight * 0.18f
    )
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((230 * template.textOpacity).toInt().coerceIn(0, 230), 238, 214, 154)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(accentRect, accentWidth, accentWidth, accentPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((248 * template.textOpacity).toInt().coerceIn(0, 248), 246, 250, 255)
        textSize = titleTextSize * 0.88f
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((220 * template.textOpacity).toInt().coerceIn(0, 220), 196, 214, 232)
        textSize = detailTextSize * 0.92f
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val textLeft = accentRect.right + padding * 0.72f
    val availableWidth = bitmap.width - textLeft - padding
    val titleMetrics = titlePaint.fontMetrics
    val titleBaseline = barTop + barHeight * 0.38f - (titleMetrics.ascent + titleMetrics.descent) / 2f
    canvas.drawText(fitText(template.title, titlePaint, availableWidth), textLeft, titleBaseline, titlePaint)

    val secondary = template.supportingLines.take(2).joinToString("  ·  ")
    if (secondary.isNotBlank()) {
        val detailMetrics = detailPaint.fontMetrics
        val detailBaseline = titleBaseline +
            (titleMetrics.descent - titleMetrics.ascent) * 0.72f +
            (detailMetrics.descent - detailMetrics.ascent) * 0.52f
        canvas.drawText(fitText(secondary, detailPaint, availableWidth), textLeft, detailBaseline, detailPaint)
    }
}

private fun drawExpandedFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float,
    sideBorderScale: Float,
    topBorderScale: Float,
    bottomBandScale: Float,
    titleColor: Int,
    detailColor: Int,
    centered: Boolean,
    decoration: ExpandedFrameDecoration = ExpandedFrameDecoration.NONE
): PhotoWatermarkBitmapRenderResult {
    val sideBorder = (padding * sideBorderScale).coerceAtLeast(MIN_PADDING_PX * 0.7f)
    val topBorder = (padding * topBorderScale).coerceAtLeast(MIN_PADDING_PX * 0.65f)
    val bottomBandHeight = maxOf(titleTextSize * bottomBandScale, source.height * 0.18f)
    val framedWidth = (source.width + sideBorder * 2f).toInt()
    val framedHeight = (source.height + topBorder + bottomBandHeight).toInt()
    val framedBitmap = Bitmap.createBitmap(framedWidth, framedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    val fullRect = RectF(0f, 0f, framedWidth.toFloat(), framedHeight.toFloat())
    if (decoration == ExpandedFrameDecoration.ARCHIVAL_PAPER) {
        drawGrandTourFrameBackground(canvas, fullRect, topBorder + source.height)
    } else {
        drawFrameBackground(canvas, source, fullRect, template.frameBackground)
    }
    canvas.drawBitmap(source, sideBorder, topBorder, null)
    if (decoration == ExpandedFrameDecoration.NIGHT_MEMORY) {
        drawNightMemoryFrameWash(
            canvas = canvas,
            framedWidth = framedWidth,
            framedHeight = framedHeight,
            bandTop = topBorder + source.height,
            sideBorder = sideBorder,
            topBorder = topBorder,
            sourceWidth = source.width,
            sourceHeight = source.height
        )
    }

    val contentLeft = sideBorder + padding
    val contentRight = framedWidth - sideBorder - padding
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when {
            centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        if (decoration == ExpandedFrameDecoration.ARCHIVAL_PAPER) {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = detailColor
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when {
            centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        if (decoration == ExpandedFrameDecoration.ARCHIVAL_PAPER) {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            letterSpacing = 0.06f
        }
    }
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val blockTop = source.height + topBorder + padding
    var baseline = blockTop - titleMetrics.ascent
    val titleX = when {
        centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    canvas.drawText(template.title, titleX, baseline, titlePaint)
    val detailX = when {
        centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    template.supportingLines.take(2).forEach { line ->
        baseline += (detailMetrics.descent - detailMetrics.ascent) + detailTextSize * 0.24f
        canvas.drawText(
            fitText(line, detailPaint, contentRight - contentLeft),
            detailX,
            baseline,
            detailPaint
        )
    }

    when (decoration) {
        ExpandedFrameDecoration.TRAVEL_MAP -> drawTravelMapDecoration(
            canvas = canvas,
            framedWidth = framedWidth,
            framedHeight = framedHeight,
            bandTop = topBorder + source.height,
            sideBorder = sideBorder,
            padding = padding
        )

        ExpandedFrameDecoration.ARCHIVAL_PAPER -> drawArchivalPaperBorder(
            canvas = canvas,
            framedWidth = framedWidth,
            framedHeight = framedHeight,
            sideBorder = sideBorder,
            topBorder = topBorder,
            sourceWidth = source.width,
            sourceHeight = source.height
        )

        ExpandedFrameDecoration.NIGHT_MEMORY -> drawNightMemoryDecoration(
            canvas = canvas,
            framedWidth = framedWidth,
            framedHeight = framedHeight,
            bandTop = topBorder + source.height,
            sideBorder = sideBorder,
            topBorder = topBorder,
            sourceWidth = source.width,
            sourceHeight = source.height,
            padding = padding
        )

        ExpandedFrameDecoration.NONE -> Unit
    }

    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = template.warning
    )
}

private fun drawVanGoghStarryFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    detailTextSize: Float,
    padding: Float,
    frameAssetProvider: VanGoghStarryFrameAssetProvider?,
    staticWatermarkAssetProvider: StaticWatermarkAssetProvider?
): PhotoWatermarkBitmapRenderResult {
    val sideBorder = (source.width * 0.027f).coerceAtLeast(MIN_PADDING_PX * 1.0f)
    val topBorder = (source.height * 0.083f).coerceAtLeast(MIN_PADDING_PX * 1.0f)
    val bottomBandHeight = maxOf(detailTextSize * 4.0f, source.height * 0.128f)
    val metadata = starryMoonMetadata(template.supportingLines)
    val textSlot = metadata.takeIf(String::isNotBlank)?.let {
        HighDesignTextSlotSpec(
            text = it,
            style = WatermarkTextSlotStyle.STARRY_METADATA,
            alignment = Paint.Align.CENTER,
            textSize = (detailTextSize * 0.98f).coerceAtLeast(MIN_DETAIL_TEXT_SIZE_PX),
            boundsResolver = { layout ->
                RectF(
                    layout.sideBorder + layout.padding,
                    layout.bandTop + layout.bottomBandHeight * 0.22f,
                    layout.framedWidth - layout.sideBorder - layout.padding,
                    layout.bandTop + layout.bottomBandHeight * 0.88f
                )
            }
        )
    }
    val provider = staticWatermarkAssetProvider
        ?: frameAssetProvider?.let(::VanGoghStaticWatermarkAssetProvider)
    return drawStaticHighDesignWatermarkFrame(
        source = source,
        template = template,
        sideBorder = sideBorder,
        topBorder = topBorder,
        bottomBandHeight = bottomBandHeight,
        padding = padding,
        assetPackage = StaticHighDesignWatermarkPackages.VAN_GOGH_STARRY,
        assetProvider = provider,
        textSlots = listOfNotNull(textSlot)
    )
}

private fun drawBlueHourFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float,
    staticWatermarkAssetProvider: StaticWatermarkAssetProvider? = null
): PhotoWatermarkBitmapRenderResult {
    val sideBorder = (source.width * 0.027f).coerceAtLeast(MIN_PADDING_PX * 1.0f)
    val topBorder = (source.height * 0.083f).coerceAtLeast(MIN_PADDING_PX * 1.0f)
    val bottomBandHeight = maxOf(titleTextSize * 2.2f, source.height * 0.128f, padding * 2.8f)
    val titleSlot = HighDesignTextSlotSpec(
        text = template.title,
        style = WatermarkTextSlotStyle.BLUE_HOUR_TITLE,
        alignment = Paint.Align.LEFT,
        textSize = titleTextSize,
        boundsResolver = { layout ->
            RectF(
                layout.sideBorder + layout.padding,
                layout.bandTop + layout.bottomBandHeight * 0.10f,
                layout.framedWidth - layout.sideBorder - layout.padding,
                layout.bandTop + layout.bottomBandHeight * 0.58f
            )
        }
    )
    val metadataSlot = blueHourMetadata(template.supportingLines).takeIf(String::isNotBlank)?.let {
        HighDesignTextSlotSpec(
            text = it,
            style = WatermarkTextSlotStyle.BLUE_HOUR_METADATA,
            alignment = Paint.Align.LEFT,
            textSize = detailTextSize,
            boundsResolver = { layout ->
                RectF(
                    layout.sideBorder + layout.padding,
                    layout.bandTop + layout.bottomBandHeight * 0.48f,
                    layout.framedWidth - layout.sideBorder - layout.padding,
                    layout.bandTop + layout.bottomBandHeight * 0.86f
                )
            }
        )
    }
    return drawStaticHighDesignWatermarkFrame(
        source = source,
        template = template,
        sideBorder = sideBorder,
        topBorder = topBorder,
        bottomBandHeight = bottomBandHeight,
        padding = padding,
        assetPackage = StaticHighDesignWatermarkPackages.BLUE_HOUR,
        assetProvider = staticWatermarkAssetProvider,
        textSlots = listOfNotNull(titleSlot, metadataSlot)
    )
}

private data class HighDesignTextSlotSpec(
    val text: String,
    val style: WatermarkTextSlotStyle,
    val alignment: Paint.Align,
    val textSize: Float,
    val boundsResolver: (ComplexWatermarkFrameLayout) -> RectF
)

private class VanGoghStaticWatermarkAssetProvider(
    private val delegate: VanGoghStarryFrameAssetProvider
) : StaticWatermarkAssetProvider {
    override fun load(asset: StaticWatermarkFrameAsset): Bitmap? {
        val variant = when (asset.variant) {
            WatermarkSceneVariant.PORTRAIT -> VanGoghStarryFrameAssetVariant.PORTRAIT
            WatermarkSceneVariant.SQUARE -> VanGoghStarryFrameAssetVariant.SQUARE
            WatermarkSceneVariant.LANDSCAPE -> VanGoghStarryFrameAssetVariant.LANDSCAPE
        }
        return delegate.load(variant)
    }
}

private fun drawStaticHighDesignWatermarkFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    sideBorder: Float,
    topBorder: Float,
    bottomBandHeight: Float,
    padding: Float,
    assetPackage: StaticWatermarkAssetPackage,
    assetProvider: StaticWatermarkAssetProvider?,
    textSlots: List<HighDesignTextSlotSpec>
): PhotoWatermarkBitmapRenderResult {
    val framedWidth = (source.width + sideBorder * 2f).toInt()
    val framedHeight = (source.height + topBorder + bottomBandHeight).toInt()
    val layout = ComplexWatermarkFrameLayout(
        framedWidth = framedWidth,
        framedHeight = framedHeight,
        sideBorder = sideBorder,
        topBorder = topBorder,
        bottomBandHeight = bottomBandHeight,
        sourceWidth = source.width,
        sourceHeight = source.height,
        padding = padding
    )
    val aspect = framedWidth.toFloat() / framedHeight.toFloat()
    val frameAsset = assetPackage.frameAssetFor(aspect)
    val scene = WatermarkScene(
        templateId = template.templateId,
        outputWidth = framedWidth,
        outputHeight = framedHeight,
        photoSlot = layout.sourceRect,
        safeArea = RectF(
            layout.sideBorder + layout.padding,
            layout.topBorder + layout.padding,
            layout.framedWidth - layout.sideBorder - layout.padding,
            layout.framedHeight - layout.padding
        ),
        variant = frameAsset.variant,
        layers = buildList {
            add(WatermarkSceneLayer.SolidBackground(template.frameBackground))
            add(WatermarkSceneLayer.PhotoSlot)
            add(
                WatermarkSceneLayer.BorderAsset(
                    asset = frameAsset,
                    destination = RectF(0f, 0f, framedWidth.toFloat(), framedHeight.toFloat())
                )
            )
            textSlots.forEach { slot ->
                add(
                    WatermarkSceneLayer.TextSlot(
                        text = slot.text,
                        bounds = slot.boundsResolver(layout),
                        style = slot.style,
                        alignment = slot.alignment,
                        textSize = slot.textSize
                    )
                )
            }
        }
    )
    val warning = if (assetProvider == null) {
        "static-asset-provider-unavailable:${assetPackage.templateId}"
    } else {
        null
    }
    return renderStaticWatermarkScene(
        source = source,
        scene = scene,
        template = template,
        assetProvider = assetProvider,
        warning = mergeWarnings(template.warning, warning)
    )
}

private fun renderStaticWatermarkScene(
    source: Bitmap,
    scene: WatermarkScene,
    template: ResolvedPhotoWatermarkTemplate,
    assetProvider: StaticWatermarkAssetProvider?,
    warning: String?
): PhotoWatermarkBitmapRenderResult {
    val framedBitmap = Bitmap.createBitmap(scene.outputWidth, scene.outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    var sceneWarning = warning
    scene.layers.forEach { layer ->
        when (layer) {
            is WatermarkSceneLayer.SolidBackground -> {
                drawStaticHighDesignFrameBackground(
                    canvas = canvas,
                    source = source,
                    destination = RectF(0f, 0f, scene.outputWidth.toFloat(), scene.outputHeight.toFloat()),
                    background = layer.frameBackground,
                    templateId = scene.templateId
                )
            }

            is WatermarkSceneLayer.BorderAsset -> {
                val asset = assetProvider?.load(layer.asset)
                if (asset != null && !asset.isRecycled) {
                    val checkpoint = canvas.save()
                    if (layer.mask == WatermarkSceneMask.CLEAR_PHOTO_SLOT) {
                        val maskPath = Path().apply {
                            fillType = Path.FillType.EVEN_ODD
                            addRect(layer.destination, Path.Direction.CW)
                            addRect(scene.photoSlot, Path.Direction.CW)
                        }
                        canvas.clipPath(maskPath)
                    }
                    drawBitmapFill(asset, layer.destination, canvas)
                    canvas.restoreToCount(checkpoint)
                    drawStaticHighDesignThemeWash(
                        canvas = canvas,
                        templateId = scene.templateId,
                        destination = layer.destination,
                        photoSlot = scene.photoSlot
                    )
                } else {
                    sceneWarning = mergeWarnings(
                        sceneWarning,
                        "static-asset-missing:${layer.asset.packageId}:${layer.asset.variant.name.lowercase(Locale.US)}"
                    )
                }
            }

            WatermarkSceneLayer.PhotoSlot -> {
                canvas.drawBitmap(source, scene.photoSlot.left, scene.photoSlot.top, null)
            }

            is WatermarkSceneLayer.TextSlot -> {
                drawWatermarkSceneTextSlot(canvas, layer, template)
            }
        }
    }
    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = sceneWarning
    )
}

private fun drawWatermarkSceneTextSlot(
    canvas: Canvas,
    slot: WatermarkSceneLayer.TextSlot,
    template: ResolvedPhotoWatermarkTemplate
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = slot.alignment
        textSize = slot.textSize
        when (slot.style) {
            WatermarkTextSlotStyle.STARRY_METADATA -> {
                color = Color.argb((228 * template.textOpacity).toInt().coerceIn(0, 228), 224, 198, 142)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                setShadowLayer(slot.textSize * 0.18f, 0f, slot.textSize * 0.04f, Color.argb(132, 0, 0, 0))
            }
            WatermarkTextSlotStyle.BLUE_HOUR_TITLE -> {
                color = Color.argb((255 * template.textOpacity).toInt().coerceIn(0, 255), 224, 238, 255)
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                setShadowLayer(slot.textSize * 0.22f, 0f, slot.textSize * 0.05f, Color.argb(172, 0, 4, 16))
            }
            WatermarkTextSlotStyle.BLUE_HOUR_METADATA -> {
                color = Color.argb((224 * template.textOpacity).toInt().coerceIn(0, 224), 162, 190, 220)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
        }
    }
    val metrics = paint.fontMetrics
    val baseline = slot.bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
    val x = when (slot.alignment) {
        Paint.Align.LEFT -> slot.bounds.left
        Paint.Align.RIGHT -> slot.bounds.right
        Paint.Align.CENTER -> slot.bounds.centerX()
    }
    canvas.drawText(
        fitText(slot.text, paint, slot.bounds.width()),
        x,
        baseline,
        paint
    )
}

private fun drawStaticHighDesignThemeWash(
    canvas: Canvas,
    templateId: String,
    destination: RectF,
    photoSlot: RectF
) {
    val washColor = when (templateId) {
        TEMPLATE_VAN_GOGH_STARRY -> Color.argb(78, 10, 42, 96)
        TEMPLATE_BLUE_HOUR -> Color.argb(126, 5, 44, 92)
        else -> return
    }
    val checkpoint = canvas.save()
    val outsidePhotoPath = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(destination, Path.Direction.CW)
        addRect(photoSlot, Path.Direction.CW)
    }
    canvas.clipPath(outsidePhotoPath)
    canvas.drawRect(destination, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = washColor
        style = Paint.Style.FILL
    })
    canvas.restoreToCount(checkpoint)
}

private fun drawBitmapFill(bitmap: Bitmap, destination: RectF, canvas: Canvas) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    canvas.drawBitmap(bitmap, null, destination, paint)
}

private fun drawBlurFourBorderFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
): PhotoWatermarkBitmapRenderResult {
    val minEdge = minOf(source.width, source.height).toFloat()
    val metrics = blurFourBorderFrameMetrics(
        minEdge = minEdge,
        titleTextSize = titleTextSize
    )
    val sideBorder = metrics.sideBorder
    val topBorder = metrics.topBorder
    val bottomBorder = metrics.bottomBorder
    val framedWidth = (source.width + sideBorder * 2f).toInt()
    val framedHeight = (source.height + topBorder + bottomBorder).toInt()
    val framedBitmap = Bitmap.createBitmap(framedWidth, framedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    val sideBorderInt = sideBorder.toInt()
    val topBorderInt = topBorder.toInt()
    val bottomBorderInt = bottomBorder.toInt()
    drawContentAwareEdgeBorder(
        canvas, source, framedWidth, framedHeight,
        sideBorderInt, topBorderInt, bottomBorderInt,
        background = template.frameBackground,
        captureCropZoom = template.captureCropZoom
    )
    canvas.drawBitmap(source, sideBorder, topBorder, null)

    drawFourBorderCardAccents(
        canvas = canvas,
        background = template.frameBackground,
        framedWidth = framedWidth,
        framedHeight = framedHeight,
        sideBorder = sideBorder,
        bottomBorder = bottomBorder
    )

    val titleColor = when (template.frameBackground) {
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(255, 245, 240, 230)
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(255, 58, 55, 50)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(255, 255, 250, 235)
        else -> Color.WHITE
    }
    val detailColor = when (template.frameBackground) {
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(230, 225, 218, 208)
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(230, 95, 90, 82)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(230, 235, 228, 210)
        else -> Color.argb(220, 235, 238, 242)
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = detailColor
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val contentLeft = sideBorder + padding
    val contentRight = framedWidth - sideBorder - padding
    val metadata = blurFourBorderSignatureMetadata(template.supportingLines)
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val titleHeight = titleMetrics.descent - titleMetrics.ascent
    val detailHeight = detailMetrics.descent - detailMetrics.ascent
    val lineGap = detailTextSize * 0.42f
    val blockHeight = titleHeight + if (metadata.isNotEmpty()) lineGap + detailHeight else 0f
    val bandTop = source.height + topBorder
    val blockTop = bandTop + (bottomBorder - blockHeight) / 2f
    val centerX = framedWidth / 2f
    val titleBaseline = blockTop - titleMetrics.ascent
    canvas.drawText(
        fitText(template.title, titlePaint, contentRight - contentLeft),
        centerX,
        titleBaseline,
        titlePaint
    )
    if (metadata.isNotEmpty()) {
        val metadataBaseline = titleBaseline + titleMetrics.descent + lineGap - detailMetrics.ascent
        canvas.drawText(
            fitText(metadata, detailPaint, contentRight - contentLeft),
            centerX,
            metadataBaseline,
            detailPaint
        )
    }

    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = template.warning
    )
}

internal fun blurFourBorderSignatureMetadata(lines: List<String>): String {
    return lines
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("   ")
}

internal data class BlurFourBorderFrameMetrics(
    val sideBorder: Float,
    val topBorder: Float,
    val bottomBorder: Float
)

internal fun blurFourBorderFrameMetrics(
    minEdge: Float,
    titleTextSize: Float
): BlurFourBorderFrameMetrics {
    return BlurFourBorderFrameMetrics(
        sideBorder = maxOf(24f, minEdge * 0.06f),
        topBorder = maxOf(24f, minEdge * 0.055f),
        bottomBorder = maxOf(titleTextSize * 3.15f, minEdge * 0.16f)
    )
}

private fun drawFourBorderCardAccents(
    canvas: Canvas,
    background: WatermarkFrameBackground,
    framedWidth: Int,
    framedHeight: Int,
    sideBorder: Float,
    bottomBorder: Float
) {
    val accentColor = when (background) {
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(58, 178, 196, 214)
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(50, 245, 238, 220)
        else -> Color.argb(48, 176, 202, 208)
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }
    canvas.drawRect(
        1f,
        1f,
        framedWidth - 1f,
        framedHeight - 1f,
        accentPaint
    )
    if (background == WatermarkFrameBackground.SOURCE_VIVID_BLUR ||
        background == WatermarkFrameBackground.SOURCE_LIGHT_BLUR
    ) {
        val y = framedHeight - bottomBorder + bottomBorder * 0.18f
        val rosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 198, 174, 205)
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, framedWidth * 0.002f)
            strokeCap = Paint.Cap.ROUND
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(66, 164, 200, 212)
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, framedWidth * 0.002f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(sideBorder + 8f, y, framedWidth / 2f, y, rosePaint)
        canvas.drawLine(framedWidth / 2f, y, framedWidth - sideBorder - 8f, y, cyanPaint)
    }
}

private fun drawProfessionalBottomBar(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
): PhotoWatermarkBitmapRenderResult {
    val bottomBandHeight = maxOf(titleTextSize * 3.8f, source.height * 0.15f)
    val framedWidth = source.width
    val framedHeight = (source.height + bottomBandHeight).toInt()
    val framedBitmap = Bitmap.createBitmap(framedWidth, framedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    val fullRect = RectF(0f, 0f, framedWidth.toFloat(), framedHeight.toFloat())
    drawFrameBackground(canvas, source, fullRect, template.frameBackground)
    canvas.drawBitmap(source, 0f, 0f, null)

    val hairlineColor = when (template.frameBackground) {
        WatermarkFrameBackground.WHITE -> Color.argb(40, 0, 0, 0)
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(50, 0, 0, 0)
        else -> Color.argb(40, 255, 255, 255)
    }
    val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = hairlineColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    canvas.drawLine(
        0f,
        source.height.toFloat(),
        framedWidth.toFloat(),
        source.height.toFloat(),
        hairlinePaint
    )

    val titleColor = when (template.frameBackground) {
        WatermarkFrameBackground.DARK,
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(255, 248, 248, 252)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(255, 255, 250, 235)
        else -> Color.argb(255, 44, 48, 58)
    }
    val detailColor = when (template.frameBackground) {
        WatermarkFrameBackground.DARK,
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(226, 190, 194, 204)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(230, 235, 228, 210)
        else -> Color.argb(220, 120, 118, 112)
    }
    val contentLeft = padding
    val contentRight = framedWidth - padding
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = detailColor
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val blockTop = source.height + padding
    var baseline = blockTop - titleMetrics.ascent
    val titleX = when (template.placement) {
        WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    canvas.drawText(template.title, titleX, baseline, titlePaint)
    val detailX = when (template.placement) {
        WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    template.supportingLines.take(2).forEach { line ->
        baseline += (detailMetrics.descent - detailMetrics.ascent) + detailTextSize * 0.24f
        canvas.drawText(
            fitText(line, detailPaint, contentRight - contentLeft),
            detailX,
            baseline,
            detailPaint
        )
    }
    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = template.warning
    )
}

private fun drawContentAwareEdgeBorder(
    canvas: Canvas,
    source: Bitmap,
    framedWidth: Int,
    framedHeight: Int,
    sideBorder: Int,
    topBorder: Int,
    bottomBorder: Int,
    background: WatermarkFrameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
    captureCropZoom: Float = 1f
) {
    val expandedBackground = createBlurredExpandedEdgeBitmap(
        source = source,
        framedWidth = framedWidth,
        framedHeight = framedHeight,
        captureCropZoom = captureCropZoom
    )
    canvas.drawBitmap(expandedBackground, 0f, 0f, null)
    expandedBackground.recycle()

    val tintOverlay = contentAwareEdgeTintOverlay(background)
    if (tintOverlay != Color.TRANSPARENT) {
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintOverlay
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, framedWidth.toFloat(), topBorder.toFloat(), tintPaint)
        canvas.drawRect(0f, (framedHeight - bottomBorder).toFloat(), framedWidth.toFloat(), framedHeight.toFloat(), tintPaint)
        canvas.drawRect(0f, topBorder.toFloat(), sideBorder.toFloat(), (framedHeight - bottomBorder).toFloat(), tintPaint)
        canvas.drawRect((framedWidth - sideBorder).toFloat(), topBorder.toFloat(), framedWidth.toFloat(), (framedHeight - bottomBorder).toFloat(), tintPaint)
    }
}

internal fun contentAwareEdgeTintOverlay(background: WatermarkFrameBackground): Int {
    return when (background) {
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(64, 20, 20, 20)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(64, 210, 218, 226)
        else -> Color.TRANSPARENT
    }
}

private fun createBlurredExpandedEdgeBitmap(
    source: Bitmap,
    framedWidth: Int,
    framedHeight: Int,
    captureCropZoom: Float = 1f
): Bitmap {
    val backgroundSource = createCaptureCropSource(source, captureCropZoom)
    val minFramedEdge = minOf(framedWidth, framedHeight)
    val divisor = if (minFramedEdge < 600) {
        1
    } else {
        BLUR_EDGE_DOWNSAMPLE_DIVISOR
    }
    val dsWidth = maxOf(1, framedWidth / divisor)
    val dsHeight = maxOf(1, framedHeight / divisor)
    val downsampled = Bitmap.createScaledBitmap(backgroundSource, dsWidth, dsHeight, true)
    if (backgroundSource !== source) {
        backgroundSource.recycle()
    }
    val mutable = downsampled.copy(Bitmap.Config.ARGB_8888, true)
    if (mutable !== downsampled) {
        downsampled.recycle()
    }
    applySeparableBoxBlur(
        bitmap = mutable,
        horizontalRadius = maxOf(4, blurFourBorderBlurRadiusForLength(framedWidth) / divisor),
        verticalRadius = maxOf(4, blurFourBorderBlurRadiusForLength(framedHeight) / divisor),
        passes = 3
    )
    val result = if (divisor > 1) {
        val upscaled = Bitmap.createScaledBitmap(mutable, framedWidth, framedHeight, true)
        mutable.recycle()
        upscaled
    } else {
        mutable
    }
    return result
}

private fun createCaptureCropSource(source: Bitmap, captureCropZoom: Float): Bitmap {
    val zoom = captureCropZoom.coerceAtLeast(1f)
    if (zoom <= 1.01f) {
        return source
    }
    val cropWidth = (source.width / zoom).toInt().coerceIn(1, source.width)
    val cropHeight = (source.height / zoom).toInt().coerceIn(1, source.height)
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
}

internal fun blurFourBorderBlurRadiusForLength(length: Int): Int {
    return maxOf(16, minOf(72, length / 24))
}

private fun applySeparableBoxBlur(
    bitmap: Bitmap,
    horizontalRadius: Int,
    verticalRadius: Int,
    passes: Int
) {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return

    var input = IntArray(width * height)
    var output = IntArray(width * height)
    bitmap.getPixels(input, 0, width, 0, 0, width, height)

    repeat(passes) {
        boxBlurHorizontal(input, output, width, height, horizontalRadius.coerceAtLeast(1))
        boxBlurVertical(output, input, width, height, verticalRadius.coerceAtLeast(1))
    }
    bitmap.setPixels(input, 0, width, 0, 0, width, height)
}

private fun boxBlurHorizontal(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int
) {
    val windowSize = radius * 2 + 1
    for (y in 0 until height) {
        val rowOffset = y * width
        var alphaSum = 0
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        for (offset in -radius..radius) {
            val pixel = input[rowOffset + offset.coerceIn(0, width - 1)]
            alphaSum += Color.alpha(pixel)
            redSum += Color.red(pixel)
            greenSum += Color.green(pixel)
            blueSum += Color.blue(pixel)
        }
        for (x in 0 until width) {
            output[rowOffset + x] = Color.argb(
                alphaSum / windowSize,
                redSum / windowSize,
                greenSum / windowSize,
                blueSum / windowSize
            )
            val removePixel = input[rowOffset + (x - radius).coerceIn(0, width - 1)]
            val addPixel = input[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]
            alphaSum += Color.alpha(addPixel) - Color.alpha(removePixel)
            redSum += Color.red(addPixel) - Color.red(removePixel)
            greenSum += Color.green(addPixel) - Color.green(removePixel)
            blueSum += Color.blue(addPixel) - Color.blue(removePixel)
        }
    }
}

private fun boxBlurVertical(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int
) {
    val windowSize = radius * 2 + 1
    for (x in 0 until width) {
        var alphaSum = 0
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        for (offset in -radius..radius) {
            val pixel = input[offset.coerceIn(0, height - 1) * width + x]
            alphaSum += Color.alpha(pixel)
            redSum += Color.red(pixel)
            greenSum += Color.green(pixel)
            blueSum += Color.blue(pixel)
        }
        for (y in 0 until height) {
            output[y * width + x] = Color.argb(
                alphaSum / windowSize,
                redSum / windowSize,
                greenSum / windowSize,
                blueSum / windowSize
            )
            val removePixel = input[(y - radius).coerceIn(0, height - 1) * width + x]
            val addPixel = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            alphaSum += Color.alpha(addPixel) - Color.alpha(removePixel)
            redSum += Color.red(addPixel) - Color.red(removePixel)
            greenSum += Color.green(addPixel) - Color.green(removePixel)
            blueSum += Color.blue(addPixel) - Color.blue(removePixel)
        }
    }
}

private fun drawFrameBackground(
    canvas: Canvas,
    source: Bitmap,
    destination: RectF,
    background: WatermarkFrameBackground
) {
    when (background) {
        WatermarkFrameBackground.DARK -> {
            canvas.drawColor(Color.argb(220, 24, 20, 18))
        }

        WatermarkFrameBackground.WHITE -> {
            canvas.drawColor(Color.argb(244, 255, 247, 224))
        }

        WatermarkFrameBackground.SOURCE_BLUR,
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> {
            val downsampled = Bitmap.createScaledBitmap(
                source,
                maxOf(1, source.width / BLUR_DOWNSAMPLE_DIVISOR),
                maxOf(1, source.height / BLUR_DOWNSAMPLE_DIVISOR),
                true
            )
            val srcRect = Rect(0, 0, downsampled.width, downsampled.height)
            val vividMatrix = if (background == WatermarkFrameBackground.SOURCE_VIVID_BLUR) {
                ColorMatrix().apply { setSaturation(1.2f) }
            } else {
                null
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
                alpha = 228
                vividMatrix?.let { colorFilter = ColorMatrixColorFilter(it) }
            }
            canvas.drawBitmap(downsampled, srcRect, destination, paint)
            downsampled.recycle()
            val overlayColor = when (background) {
                WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(88, 20, 20, 20)
                WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(124, 255, 244, 228)
                WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(96, 218, 220, 232)
                else -> Color.TRANSPARENT
            }
            canvas.drawRect(destination, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlayColor
                style = Paint.Style.FILL
            })
        }
    }
}

private fun drawStaticHighDesignFrameBackground(
    canvas: Canvas,
    source: Bitmap,
    destination: RectF,
    background: WatermarkFrameBackground,
    templateId: String
) {
    when (templateId) {
        TEMPLATE_BLUE_HOUR -> {
            canvas.drawRect(destination, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(8, 38, 72)
                style = Paint.Style.FILL
            })
            val lowerWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(130, 18, 74, 128)
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                destination.left,
                destination.top + destination.height() * 0.72f,
                destination.right,
                destination.bottom,
                lowerWash
            )
            val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(46, 190, 218, 235)
                style = Paint.Style.FILL
            }
            canvas.drawRect(destination, veil)
        }

        TEMPLATE_VAN_GOGH_STARRY -> {
            canvas.drawRect(destination, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(8, 28, 66)
                style = Paint.Style.FILL
            })
            val lowerWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(122, 14, 48, 102)
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                destination.left,
                destination.top + destination.height() * 0.78f,
                destination.right,
                destination.bottom,
                lowerWash
            )
        }

        else -> drawFrameBackground(canvas, source, destination, background)
    }
}

private fun drawGrandTourFrameBackground(
    canvas: Canvas,
    destination: RectF,
    bandTop: Float
) {
    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(14, 36, 29)
        style = Paint.Style.FILL
    }
    canvas.drawRect(destination, basePaint)

    val upperWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(74, 52, 78, 56)
        style = Paint.Style.FILL
    }
    canvas.drawRect(destination.left, destination.top, destination.right, bandTop, upperWash)

    val bandWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(146, 8, 24, 20)
        style = Paint.Style.FILL
    }
    canvas.drawRect(destination.left, bandTop, destination.right, destination.bottom, bandWash)

    val paperGrain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(26, 226, 205, 154)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }
    val step = maxOf(9f, destination.width() * 0.035f)
    var x = destination.left - step
    while (x < destination.right + step) {
        canvas.drawLine(x, destination.top, x + step * 0.7f, destination.bottom, paperGrain)
        x += step
    }
}

private fun drawTravelMapDecoration(
    canvas: Canvas,
    framedWidth: Int,
    framedHeight: Int,
    bandTop: Float,
    sideBorder: Float,
    padding: Float
) {
    val left = maxOf(framedWidth * 0.56f, framedWidth / 2f + padding)
    val right = framedWidth - sideBorder - padding * 0.55f
    val top = bandTop + padding * 0.72f
    val bottom = framedHeight - padding * 0.7f
    if (right <= left || bottom <= top) return

    val width = right - left
    val height = bottom - top
    val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 83, 132, 100)
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.2f, framedWidth * 0.0024f)
        strokeCap = Paint.Cap.ROUND
    }
    repeat(3) { index ->
        val offset = index * height * 0.19f
        val contour = Path().apply {
            moveTo(left + width * 0.02f, top + height * (0.2f + index * 0.11f))
            cubicTo(
                left + width * 0.24f, top - height * 0.05f + offset,
                left + width * 0.43f, top + height * 0.52f + offset,
                left + width * 0.64f, top + height * 0.28f + offset
            )
            cubicTo(
                left + width * 0.78f, top + height * 0.12f + offset,
                left + width * 0.9f, top + height * 0.48f + offset,
                right, top + height * 0.34f + offset
            )
        }
        canvas.drawPath(contour, contourPaint)
    }

    val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(178, 42, 82, 61)
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.8f, framedWidth * 0.0035f)
        strokeCap = Paint.Cap.ROUND
    }
    val route = Path().apply {
        moveTo(left + width * 0.08f, bottom - height * 0.14f)
        cubicTo(
            left + width * 0.28f, top + height * 0.36f,
            left + width * 0.48f, bottom - height * 0.12f,
            left + width * 0.7f, top + height * 0.24f
        )
    }
    canvas.drawPath(route, routePaint)

    val markerRadius = maxOf(2.4f, framedWidth * 0.006f)
    val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 42, 82, 61)
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.4f, markerRadius * 0.42f)
    }
    canvas.drawCircle(left + width * 0.08f, bottom - height * 0.14f, markerRadius, markerPaint)
    canvas.drawCircle(left + width * 0.7f, top + height * 0.24f, markerRadius, markerPaint)

    val crossX = left + width * 0.89f
    val crossY = bottom - height * 0.2f
    val crossSize = markerRadius * 1.9f
    canvas.drawLine(crossX - crossSize, crossY, crossX + crossSize, crossY, markerPaint)
    canvas.drawLine(crossX, crossY - crossSize, crossX, crossY + crossSize, markerPaint)
}

private fun drawNightMemoryFrameWash(
    canvas: Canvas,
    framedWidth: Int,
    framedHeight: Int,
    bandTop: Float,
    sideBorder: Float,
    topBorder: Float,
    sourceWidth: Int,
    sourceHeight: Int
) {
    val nightWashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(238, 5, 12, 32)
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, framedWidth.toFloat(), topBorder, nightWashPaint)
    canvas.drawRect(0f, topBorder, sideBorder, topBorder + sourceHeight, nightWashPaint)
    canvas.drawRect(
        sideBorder + sourceWidth,
        topBorder,
        framedWidth.toFloat(),
        topBorder + sourceHeight,
        nightWashPaint
    )
    canvas.drawRect(0f, bandTop, framedWidth.toFloat(), framedHeight.toFloat(), nightWashPaint)
}

private fun drawNightMemoryDecoration(
    canvas: Canvas,
    framedWidth: Int,
    framedHeight: Int,
    bandTop: Float,
    sideBorder: Float,
    topBorder: Float,
    sourceWidth: Int,
    sourceHeight: Int,
    padding: Float
) {
    val coolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(88, 160, 176, 202)
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, framedWidth * 0.0018f)
        strokeCap = Paint.Cap.ROUND
    }
    val warmLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(172, 222, 160, 82)
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.1f, framedWidth * 0.002f)
        strokeCap = Paint.Cap.ROUND
    }
    val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(196, 226, 158, 82)
        style = Paint.Style.FILL
    }
    val frameInset = maxOf(5f, minOf(framedWidth, framedHeight) * 0.009f)
    val corner = maxOf(24f, minOf(framedWidth, framedHeight) * 0.055f)
    fun drawCorner(left: Boolean, top: Boolean) {
        val x = if (left) frameInset else framedWidth - frameInset
        val y = if (top) frameInset else framedHeight - frameInset
        val xDir = if (left) 1f else -1f
        val yDir = if (top) 1f else -1f
        canvas.drawLine(x, y, x + xDir * corner, y, coolPaint)
        canvas.drawLine(x, y, x, y + yDir * corner, coolPaint)
    }
    drawCorner(left = true, top = true)
    drawCorner(left = false, top = true)
    drawCorner(left = true, top = false)
    drawCorner(left = false, top = false)

    val imageInset = maxOf(3f, framedWidth * 0.004f)
    canvas.drawRect(
        sideBorder - imageInset,
        topBorder - imageInset,
        sideBorder + sourceWidth + imageInset,
        topBorder + sourceHeight + imageInset,
        Paint(coolPaint).apply { alpha = 52 }
    )
    canvas.drawLine(
        sideBorder + padding * 0.25f,
        bandTop + padding * 0.54f,
        framedWidth - sideBorder - padding * 0.25f,
        bandTop + padding * 0.48f,
        warmLinePaint
    )
    val lampRadius = maxOf(3f, padding * 0.13f)
    canvas.drawCircle(framedWidth - sideBorder - padding * 0.65f, bandTop + padding * 0.9f, lampRadius, lampPaint)
    canvas.drawCircle(
        framedWidth - sideBorder - padding * 1.12f,
        bandTop + padding * 1.22f,
        lampRadius * 0.72f,
        lampPaint
    )
}

private fun starryMoonMetadata(lines: List<String>): String {
    return lines
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" · ")
}

private fun blueHourMetadata(lines: List<String>): String {
    return lines
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" · ")
}

private fun drawArchivalPaperBorder(
    canvas: Canvas,
    framedWidth: Int,
    framedHeight: Int,
    sideBorder: Float,
    topBorder: Float,
    sourceWidth: Int,
    sourceHeight: Int
) {
    val frameColor = Color.argb(188, 218, 190, 126)
    val innerColor = Color.argb(116, 122, 98, 58)
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frameColor
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.2f, framedWidth * 0.0026f)
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = innerColor
        style = Paint.Style.STROKE
        strokeWidth = maxOf(0.8f, framedWidth * 0.0017f)
    }
    val outerInset = maxOf(4f, minOf(framedWidth, framedHeight) * 0.012f)
    canvas.drawRect(
        outerInset,
        outerInset,
        framedWidth - outerInset,
        framedHeight - outerInset,
        outerPaint
    )

    val inset = 4f
    canvas.drawRect(
        sideBorder - inset, topBorder - inset,
        sideBorder + sourceWidth + inset, topBorder + sourceHeight + inset,
        innerPaint
    )
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 218, 190, 126)
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.0f, framedWidth * 0.002f)
        strokeCap = Paint.Cap.SQUARE
    }
    val lineY = topBorder + sourceHeight + inset + 2f
    val lineMargin = sideBorder + 10f
    canvas.drawLine(lineMargin, lineY, framedWidth - lineMargin, lineY, accentPaint)
    canvas.drawLine(
        lineMargin + framedWidth * 0.12f,
        framedHeight - maxOf(10f, topBorder * 0.72f),
        framedWidth - lineMargin - framedWidth * 0.12f,
        framedHeight - maxOf(10f, topBorder * 0.72f),
        Paint(accentPaint).apply { alpha = 92 }
    )

    val step = maxOf(8f, minOf(framedWidth, framedHeight) * 0.028f)
    fun drawCorner(left: Boolean, top: Boolean) {
        val xEdge = if (left) outerInset + step * 0.5f else framedWidth - outerInset - step * 0.5f
        val yEdge = if (top) outerInset + step * 0.5f else framedHeight - outerInset - step * 0.5f
        val xDirection = if (left) 1f else -1f
        val yDirection = if (top) 1f else -1f
        canvas.drawLine(xEdge, yEdge, xEdge + xDirection * step * 2.2f, yEdge, outerPaint)
        canvas.drawLine(xEdge, yEdge, xEdge, yEdge + yDirection * step * 2.2f, outerPaint)
        val arcRight = xEdge + xDirection * step * 1.35f
        val arcBottom = yEdge + yDirection * step * 1.35f
        canvas.drawArc(
            RectF(
                minOf(xEdge, arcRight),
                minOf(yEdge, arcBottom),
                maxOf(xEdge, arcRight),
                maxOf(yEdge, arcBottom)
            ),
            if (left && top) 180f else if (!left && top) 270f else if (left) 90f else 0f,
            90f,
            false,
            Paint(innerPaint).apply { alpha = 122 }
        )
        canvas.drawLine(
            xEdge + xDirection * step * 0.55f,
            yEdge + yDirection * step * 0.38f,
            xEdge + xDirection * step * 1.8f,
            yEdge + yDirection * step * 0.38f,
            innerPaint
        )
    }
    drawCorner(left = true, top = true)
    drawCorner(left = false, top = true)
    drawCorner(left = true, top = false)
    drawCorner(left = false, top = false)

}

private fun fitText(
    text: String,
    paint: Paint,
    maxWidth: Float
): String {
    if (paint.measureText(text) <= maxWidth) {
        return text
    }
    var endIndex = text.length
    while (endIndex > 1) {
        val candidate = text.take(endIndex).trimEnd() + "…"
        if (paint.measureText(candidate) <= maxWidth) {
            return candidate
        }
        endIndex -= 1
    }
    return text.take(1) + "…"
}

private fun mergeWarnings(
    templateWarning: String?,
    exifWarning: String?,
    archiveWarning: String? = null
): String? {
    return listOfNotNull(templateWarning, exifWarning, archiveWarning)
        .takeIf { warnings -> warnings.isNotEmpty() }
        ?.joinToString(separator = ",")
}

private const val PHOTO_WATERMARK_TEMPLATE_KEY = "watermarkTemplate"
private const val PHOTO_WATERMARK_BACKGROUND_KEY = "watermarkFrameBackground"
private const val PHOTO_WATERMARK_MODEL_KEY = "watermarkModel"
private const val PHOTO_WATERMARK_DATETIME_KEY = "watermarkDatetime"
private const val PHOTO_WATERMARK_LOCATION_KEY = "watermarkLocation"
private const val PHOTO_WATERMARK_CAMERA_PARAMS_KEY = "watermarkCameraParams"
private const val PHOTO_WATERMARK_POSITION_KEY = "watermarkPosition"
private const val CHECK_IN_CONTENT_WATERMARK_PLACEMENT_KEY = "checkInContentWatermarkPlacement"
private const val CHECK_IN_CONTENT_WATERMARK_DENSITY_KEY = "checkInContentWatermarkDensity"
private const val PHOTO_WATERMARK_TEXT_SCALE_KEY = "watermarkTextScale"
private const val PHOTO_WATERMARK_TEXT_OPACITY_KEY = "watermarkTextOpacity"
private const val PHOTO_WATERMARK_MODE_NAME_KEY = "watermarkModeName"
private const val PHOTO_WATERMARK_PROFILE_NAME_KEY = "watermarkProfileName"
private const val TEMPLATE_CLASSIC_OVERLAY = "classic-overlay"
private const val TEMPLATE_TRAVEL_POLAROID = "travel-polaroid"
private const val TEMPLATE_RETRO_FRAME = "retro-frame"
private const val TEMPLATE_PURE_TEXT = "pure-text"
private const val TEMPLATE_BLUR_FOUR_BORDER = "blur-four-border"
private const val TEMPLATE_PROFESSIONAL_BOTTOM_BAR = "professional-bottom-bar"
private const val TEMPLATE_NIGHT_STREET = "night-street"
private const val TEMPLATE_VAN_GOGH_STARRY = "van-gogh-starry"
private const val TEMPLATE_BLUE_HOUR = "blue-hour"

private fun resolveWatermarkTemplateId(templateId: String?): String {
    return templateId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: TEMPLATE_CLASSIC_OVERLAY
}

internal fun resolvePhotoWatermarkTemplate(
    templateId: String,
    watermarkText: String,
    metadata: MediaMetadata,
    preservedExif: Map<String, String>
): ResolvedPhotoWatermarkTemplate {
    val requestedTemplateId = templateId.trim().ifEmpty { TEMPLATE_CLASSIC_OVERLAY }
    val templateType = resolvePhotoWatermarkTemplateType(requestedTemplateId)
    val warning = if (templateType.storageKey == requestedTemplateId) {
        null
    } else {
        "template-fallback"
    }
    val model = resolveDeviceModel(metadata.customTags, preservedExif)
    val datetime = metadata.customTags[PHOTO_WATERMARK_DATETIME_KEY]
        ?: formatExifDateTime(
            preservedExif[ExifInterface.TAG_DATETIME_ORIGINAL]
                ?: preservedExif[ExifInterface.TAG_DATETIME]
        )
    val location = metadata.customTags[PHOTO_WATERMARK_LOCATION_KEY]
        ?: formatExifLocation(preservedExif)
    val cameraParams = metadata.customTags[PHOTO_WATERMARK_CAMERA_PARAMS_KEY]
        ?: formatCameraParams(preservedExif)
    val profileName = contentAwareCheckInProfileName(
        metadata.customTags[PHOTO_WATERMARK_PROFILE_NAME_KEY],
        metadata.customTags
    )
    val tokenValues = mapOf(
        PhotoWatermarkMetadataToken.DATETIME to datetime,
        PhotoWatermarkMetadataToken.LOCATION to location,
        PhotoWatermarkMetadataToken.CAMERA_PARAMS to cameraParams,
        PhotoWatermarkMetadataToken.PROFILE_NAME to profileName
    )
    val supportedTokens = templateType.resolveMetadataTokens(metadata.customTags)
        .mapNotNull(tokenValues::get)
    return ResolvedPhotoWatermarkTemplate(
        templateId = templateType.storageKey,
        title = resolveWatermarkTitle(templateType, watermarkText, metadata.customTags, model),
        supportingLines = chunkWatermarkTokens(supportedTokens),
        frameBackground = templateType.resolveFrameBackground(
            WatermarkFrameBackground.fromStorageKey(
                metadata.customTags[PHOTO_WATERMARK_BACKGROUND_KEY]
            )
        ),
        usesExpandedFrame = templateType.usesExpandedFrame,
        placement = resolvePhotoWatermarkPlacement(templateType, metadata.customTags),
        textScale = metadata.customTags[PHOTO_WATERMARK_TEXT_SCALE_KEY]
            ?.toFloatOrNull()
            ?.coerceIn(0.75f, 1.4f)
            ?: 1f,
        textOpacity = metadata.customTags[PHOTO_WATERMARK_TEXT_OPACITY_KEY]
            ?.toFloatOrNull()
            ?.coerceIn(0.35f, 1f)
            ?: 1f,
        captureCropZoom = metadata.customTags["captureCropZoom"]
            ?.toFloatOrNull()
            ?.coerceAtLeast(1f)
            ?: 1f,
        warning = warning
    )
}

private fun resolvePhotoWatermarkPlacement(
    templateType: PhotoWatermarkTemplateType,
    customTags: Map<String, String>
): WatermarkTextPlacement {
    val contentPlacement = if (customTags["mode"] == "check-in") {
        WatermarkTextPlacement.fromStorageKey(customTags[CHECK_IN_CONTENT_WATERMARK_PLACEMENT_KEY])
    } else {
        null
    }
    return contentPlacement
        ?: WatermarkTextPlacement.fromStorageKey(customTags[PHOTO_WATERMARK_POSITION_KEY])
        ?: templateType.defaultPlacement
}

private fun PhotoWatermarkTemplateType.resolveMetadataTokens(
    customTags: Map<String, String>
): List<PhotoWatermarkMetadataToken> {
    if (customTags["mode"] != "check-in") return metadataTokens
    return when (customTags[CHECK_IN_CONTENT_WATERMARK_DENSITY_KEY]) {
        "compact" -> emptyList()
        "balanced" -> metadataTokens.filter { token ->
            token == PhotoWatermarkMetadataToken.PROFILE_NAME ||
                token == PhotoWatermarkMetadataToken.LOCATION
        }
        "detailed" -> metadataTokens
        else -> metadataTokens
    }
}

private val SUPPORTED_BLUR_BACKGROUNDS = setOf(
    WatermarkFrameBackground.SOURCE_BLUR,
    WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
    WatermarkFrameBackground.SOURCE_VIVID_BLUR
)

private val SUPPORTED_RETRO_FRAME_BACKGROUNDS = setOf(
    WatermarkFrameBackground.WHITE,
    WatermarkFrameBackground.DARK
)

private val SUPPORTED_NIGHT_STREET_BACKGROUNDS = setOf(
    WatermarkFrameBackground.DARK
)

private fun resolveWatermarkTitle(
    templateType: PhotoWatermarkTemplateType,
    watermarkText: String,
    customTags: Map<String, String>,
    deviceModel: String
): String {
    val modeName = customTags[PHOTO_WATERMARK_MODE_NAME_KEY]
    val profileName = contentAwareCheckInProfileName(
        customTags[PHOTO_WATERMARK_PROFILE_NAME_KEY],
        customTags
    )
    if (templateType == PhotoWatermarkTemplateType.BLUE_HOUR) {
        return "BLUE HOUR"
    }
    if (modeName != null) {
        val title = buildString {
            append(deviceModel)
            append(" · ")
            append(modeName)
            profileName?.let {
                append(" ")
                append(it)
            }
        }
        return title
    }
    val normalizedText = watermarkText.trim().ifBlank { "OpenCamera" }
    return when {
        templateType == PhotoWatermarkTemplateType.TRAVEL_POLAROID &&
            normalizedText.startsWith("PHOTO ") -> "去有天空的地方"
        else -> normalizedText
    }
}

private fun resolveDeviceModel(
    customTags: Map<String, String>,
    preservedExif: Map<String, String>
): String {
    return customTags[PHOTO_WATERMARK_MODEL_KEY]
        ?: preservedExif[ExifInterface.TAG_MODEL]
        ?: Build.MODEL
        ?: "OpenCamera"
}

private fun chunkWatermarkTokens(tokens: List<String>): List<String> {
    return tokens
        .filter { it.isNotBlank() }
        .chunked(2)
        .map { row -> row.joinToString(separator = " • ") }
}

private fun formatExifDateTime(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.replaceFirst(":", "-")
        ?.replace(":", "-", ignoreCase = false)
}

private fun formatExifLocation(exif: Map<String, String>): String? {
    val latitude = exifCoordinateOrNull(
        coordinate = exif[ExifInterface.TAG_GPS_LATITUDE],
        reference = exif[ExifInterface.TAG_GPS_LATITUDE_REF]
    )
    val longitude = exifCoordinateOrNull(
        coordinate = exif[ExifInterface.TAG_GPS_LONGITUDE],
        reference = exif[ExifInterface.TAG_GPS_LONGITUDE_REF]
    )
    if (latitude == null || longitude == null) {
        return null
    }
    return String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
}

private fun exifCoordinateOrNull(
    coordinate: String?,
    reference: String?
): Double? {
    val raw = coordinate?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val parts = raw.split(",")
    if (parts.size != 3) {
        return null
    }
    val degrees = parts[0].trim().substringBefore('/').toDoubleOrNull() ?: return null
    val minutes = fractionToDouble(parts[1]) ?: return null
    val seconds = fractionToDouble(parts[2]) ?: return null
    val decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
    return when (reference?.trim()?.uppercase(Locale.US)) {
        "S", "W" -> -decimal
        else -> decimal
    }
}

private fun fractionToDouble(value: String): Double? {
    val normalized = value.trim()
    val parts = normalized.split('/')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()
        2 -> {
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
            numerator / denominator
        }
        else -> null
    }
}

private fun formatCameraParams(exif: Map<String, String>): String? {
    val params = buildList {
        exif[ExifInterface.TAG_EXPOSURE_TIME]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { add(it) }
        exif[ExifInterface.TAG_F_NUMBER]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { add("f/$it") }
        exif[ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { add("ISO $it") }
    }
    return params.takeIf { it.isNotEmpty() }?.joinToString(separator = " • ")
}
