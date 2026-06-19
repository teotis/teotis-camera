package com.opencamera.core.effect

import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.StylePreset

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
