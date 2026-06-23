package com.opencamera.core.effect

import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.StylePreset
import java.awt.Color

/**
 * A single color stop for a multi-segment preview gradient.
 * [fraction] is 0f..1f position within the gradient.
 */
data class PreviewStop(val color: Int, val fraction: Float)

/** Extract red channel (0-255) from a packed ARGB int. */
private fun Int.rgbR(): Int = (this shr 16) and 0xFF
/** Extract green channel (0-255) from a packed ARGB int. */
private fun Int.rgbG(): Int = (this shr 8) and 0xFF
/** Extract blue channel (0-255) from a packed ARGB int. */
private fun Int.rgbB(): Int = this and 0xFF
/** Pack R, G, B into an opaque ARGB int. */
private fun packRgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

/**
 * Apply a [FilterRenderSpec]'s color adjustments to a list of [PreviewStop]s.
 *
 * For each stop's RGB, applies:
 * - brightnessShift (additive, per channel)
 * - contrast (multiplicative around 0.5 midpoint)
 * - warmthShift (positive = warmer/redder, negative = cooler/bluer)
 * - warmBoost / coolBoost (saturation-scale channel nudges)
 * - saturation (all-channel gain around luminance)
 * - monochromeMix (blend toward grayscale)
 *
 * Returns a new list of [PreviewStop]s with transformed colors.
 */
fun applyFilterRenderSpecToStops(
    spec: FilterRenderSpec,
    stops: List<PreviewStop>
): List<PreviewStop> {
    val bShift = spec.brightnessShift
    val contrast = spec.contrast
    val sat = spec.saturation
    val warm = spec.warmthShift
    val wBoost = spec.warmBoost
    val cBoost = spec.coolBoost
    val monoMix = spec.monochromeMix.coerceIn(0f, 1f)

    return stops.map { stop ->
        val r = stop.color.rgbR().toFloat()
        val g = stop.color.rgbG().toFloat()
        val b = stop.color.rgbB().toFloat()

        // 1. Brightness shift
        var nr = r + bShift.toFloat()
        var ng = g + bShift.toFloat()
        var nb = b + bShift.toFloat()

        // 2. Contrast
        if (contrast != 1f) {
            nr = (nr / 255f - 0.5f) * contrast * 255f + 127.5f
            ng = (ng / 255f - 0.5f) * contrast * 255f + 127.5f
            nb = (nb / 255f - 0.5f) * contrast * 255f + 127.5f
        }

        // 3. Warmth shift
        if (warm != 0) {
            nr += warm.toFloat()
            nb -= warm.toFloat()
        }

        // 4. Warm/cool boost
        if (wBoost > 0f) {
            nr += wBoost * 20f
            ng += wBoost * 8f
        }
        if (cBoost > 0f) {
            nb += cBoost * 20f
            ng += cBoost * 6f
        }

        // 5. Saturation
        if (sat != 1f) {
            val lum = 0.299f * nr + 0.587f * ng + 0.114f * nb
            nr = lum + (nr - lum) * sat
            ng = lum + (ng - lum) * sat
            nb = lum + (nb - lum) * sat
        }

        // 6. Monochrome mix
        if (monoMix > 0f) {
            val gray = 0.299f * nr + 0.587f * ng + 0.114f * nb
            nr = nr + (gray - nr) * monoMix
            ng = ng + (gray - ng) * monoMix
            nb = nb + (gray - nb) * monoMix
        }

        PreviewStop(
            packRgb(nr.toInt(), ng.toInt(), nb.toInt()),
            stop.fraction
        )
    }
}

/**
 * Bridge helpers that connect a [StylePreset] (from the style preset catalog)
 * to the preview effect/transform pipeline without introducing any
 * camera runtime policy into UI code.
 *
 * These are pure functions: given a preset and its resolved [FilterProfile],
 * they produce the same [FilterEffect] and [PreviewColorTransform] that
 * the existing mode plugin → [PreviewEffectAdapter] path would build.
 */

/**
 * Convert a [StylePreset] into a [FilterEffect] suitable for inclusion
 * in an [EffectSpec], using the render spec from the matched [FilterProfile].
 *
 * This is the same [FilterEffect] shape that mode plugins (Photo, Video, etc.)
 * construct in their `buildEffectSpec()`. When the session applies this
 * [FilterEffect] via [PreviewEffectAdapter], the preview color transform
 * updates immediately.
 */
fun StylePreset.toFilterEffect(profile: FilterProfile): FilterEffect {
    return FilterEffect(
        profileId = profileId,
        renderSpec = profile.renderSpec
    )
}

/**
 * Produce a [PreviewColorTransform] from a [FilterProfile]'s render spec.
 *
 * For original/identity presets the transform is identity and no tint
 * overlay is shown. For vivid/texture/monochrome presets the transform
 * encodes saturation, contrast, warmth, and monochrome as a 4×5 color
 * matrix.
 */
fun FilterProfile.toPreviewColorTransform(): PreviewColorTransform {
    return PreviewColorTransform.fromSpec(renderSpec)
}

/**
 * Produce a [PreviewColorTransform] directly from a [FilterRenderSpec].
 * Convenience alias for code that works with the spec directly.
 */
fun FilterRenderSpec.toPreviewColorTransform(): PreviewColorTransform {
    return PreviewColorTransform.fromSpec(this)
}
