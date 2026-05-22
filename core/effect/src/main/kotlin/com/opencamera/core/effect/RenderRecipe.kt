package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.settings.FilterRenderSpec

data class RenderRecipe(
    val filterProfileId: String?,
    val filterRenderSpec: FilterRenderSpec?,
    val frameRatio: FrameRatio?,
    val watermarkTemplateId: String?,
    val watermarkText: String?,
    val selfieMirror: Boolean
) {
    val requiresFinalOutputPostprocess: Boolean
        get() = filterRenderSpec != null ||
            frameRatio != null && frameRatio != FrameRatio.RATIO_4_3 ||
            !watermarkText.isNullOrBlank() ||
            !watermarkTemplateId.isNullOrBlank() ||
            selfieMirror

    companion object {
        val EMPTY = RenderRecipe(
            filterProfileId = null,
            filterRenderSpec = null,
            frameRatio = null,
            watermarkTemplateId = null,
            watermarkText = null,
            selfieMirror = false
        )

        fun from(effectSpec: EffectSpec): RenderRecipe {
            val filter = effectSpec.find<FilterEffect>()
            val watermark = effectSpec.find<WatermarkEffect>()
            val frame = effectSpec.find<FrameEffect>()
            val selfie = effectSpec.find<SelfieMirrorEffect>()

            return RenderRecipe(
                filterProfileId = filter?.profileId,
                filterRenderSpec = filter?.renderSpec,
                frameRatio = frame?.ratio,
                watermarkTemplateId = watermark?.templateId,
                watermarkText = watermark?.tokens?.get("watermarkModel"),
                selfieMirror = selfie != null
            )
        }

        fun from(shot: ShotRequest): RenderRecipe {
            val tags = shot.saveRequest.metadata.customTags
            val filterRenderSpec = FilterRenderSpec.fromMetadataTags(tags)
            val filterProfileId = tags["filterProfile"]
                ?: shot.postProcessSpec.algorithmProfile
                ?: shot.saveRequest.metadata.algorithmProfile
            val frameRatio = FrameRatio.fromTag(tags["frameRatio"])
            val watermarkText = shot.saveRequest.metadata.watermarkText
                ?.trim()
                ?.takeIf(String::isNotEmpty)
            val watermarkTemplateId = tags["watermarkTemplate"]
                ?.trim()
                ?.takeIf(String::isNotEmpty)
            val selfieMirror = tags["selfieMirrorApply"].toBoolean()

            return RenderRecipe(
                filterProfileId = filterProfileId,
                filterRenderSpec = filterRenderSpec,
                frameRatio = frameRatio,
                watermarkTemplateId = watermarkTemplateId,
                watermarkText = watermarkText,
                selfieMirror = selfieMirror
            )
        }
    }
}
