package com.opencamera.core.effect

import com.opencamera.core.media.PostProcessSpec

object EffectBridge {

    fun toMetadataTags(spec: EffectSpec): Map<String, String> {
        val tags = mutableMapOf<String, String>()

        spec.find<FilterEffect>()?.let { effect ->
            tags["filterProfile"] = effect.profileId
            effect.renderSpec?.let { tags.putAll(it.toMetadataTags()) }
        }

        spec.find<WatermarkEffect>()?.let { effect ->
            tags["watermarkTemplate"] = effect.templateId
            tags.putAll(effect.tokens)
            tags["watermarkTextScale"] = effect.style.textScale.multiplier.toString()
            tags["watermarkTextOpacity"] = effect.style.textOpacity.alphaFraction.toString()
            tags["watermarkPosition"] = effect.style.textPlacement.name
            tags["watermarkFrameBackground"] = effect.style.frameBackground.name
        }

        spec.find<FrameEffect>()?.let { effect ->
            tags["frameRatio"] = effect.ratio.tagValue
        }

        spec.find<PortraitEffect>()?.let { effect ->
            tags["mode"] = "portrait"
            tags["renderPath"] = effect.renderPath
            tags["portraitProfile"] = effect.profileId
            tags["portraitBeautyPreset"] = effect.beautyPreset
            tags["portraitBeautyStrength"] = effect.beautyStrength.toString()
            tags["portraitBokehEffect"] = effect.bokehEffect
        }

        spec.find<DocumentEffect>()?.let { effect ->
            tags["mode"] = "document"
            tags["autoCrop"] = effect.autoCrop.toString()
            effect.contrastProfile?.let { tags["contrastProfile"] = it }
        }

        spec.find<SelfieMirrorEffect>()?.let {
            tags["selfieMirrorApply"] = "true"
        }

        return tags
    }

    fun toPostProcessSpec(spec: EffectSpec): PostProcessSpec {
        val filter = spec.find<FilterEffect>()
        val watermark = spec.find<WatermarkEffect>()
        return PostProcessSpec(
            watermarkText = watermark?.let { buildWatermarkText(it) },
            exifOverrides = emptyMap(),
            algorithmProfile = filter?.profileId
        )
    }

    private fun buildWatermarkText(effect: WatermarkEffect): String {
        return effect.tokens.values.filter { it.isNotBlank() }.joinToString(" | ")
    }
}
