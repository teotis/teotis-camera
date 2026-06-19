package com.opencamera.app.camera

import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.opencamera.core.device.PreviewStreamAspect

internal fun targetDimensionsForPreviewStreamAspect(
    aspect: PreviewStreamAspect
): StillCaptureTargetResolution {
    return when (aspect) {
        PreviewStreamAspect.FULL,
        PreviewStreamAspect.RATIO_4_3 -> StillCaptureTargetResolution(1440, 1080)
        PreviewStreamAspect.RATIO_16_9 -> StillCaptureTargetResolution(1920, 1080)
        PreviewStreamAspect.RATIO_1_1 -> StillCaptureTargetResolution(1080, 1080)
    }
}

internal fun targetSizeForPreviewStreamAspect(
    aspect: PreviewStreamAspect
): Size {
    val target = targetDimensionsForPreviewStreamAspect(aspect)
    return Size(target.width, target.height)
}

internal fun previewResolutionSelectorForAspect(
    aspect: PreviewStreamAspect
): ResolutionSelector {
    val aspectStrategy = when (aspect) {
        PreviewStreamAspect.FULL,
        PreviewStreamAspect.RATIO_4_3,
        PreviewStreamAspect.RATIO_1_1 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        PreviewStreamAspect.RATIO_16_9 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
    }
    return ResolutionSelector.Builder()
        .setAspectRatioStrategy(aspectStrategy)
        .setResolutionStrategy(
            ResolutionStrategy(
                targetSizeForPreviewStreamAspect(aspect),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
        .build()
}
