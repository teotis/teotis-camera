package com.opencamera.core.effect

import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.settings.toMetadataTags

object EffectBridge {

    fun toMetadataTags(spec: EffectSpec): Map<String, String> {
        val tags = mutableMapOf<String, String>()

        spec.find<FilterEffect>()?.let { effect ->
            tags["filterProfile"] = effect.profileId
            effect.renderSpec?.let { tags.putAll(it.toMetadataTags()) }
            tags.putAll(effect.recipe.toMetadataTags())
        }

        spec.find<WatermarkEffect>()?.let { effect ->
            tags["watermarkTemplate"] = effect.templateId
            tags.putAll(effect.tokens)
            tags["watermarkTextScale"] = effect.style.textScale.multiplier.toString()
            tags["watermarkTextOpacity"] = effect.style.textOpacity.alphaFraction.toString()
            tags["watermarkPosition"] = effect.style.textPlacement.storageKey
            tags["watermarkFrameBackground"] = effect.style.frameBackground.storageKey
        }

        spec.find<FrameEffect>()?.let { effect ->
            tags["frameRatio"] = effect.ratio.tagValue
        }

        spec.find<PortraitEffect>()?.let { effect ->
            tags["mode"] = "portrait"
            tags["renderPath"] = effect.renderPath
            tags["portraitProfile"] = effect.profileId
            tags["portraitBeautyPreset"] = effect.beautyPreset
            tags["portraitBeautyStrength"] = effect.beautyStrength
            tags["portraitBokehEffect"] = effect.bokehEffect
            tags["portraitDepthStrength"] = effect.depthStrength.toString()
        }

        spec.find<DocumentEffect>()?.let { effect ->
            tags["mode"] = "document"
            tags["autoCrop"] = effect.autoCrop.toString()
            effect.contrastProfile?.let { tags["contrastProfile"] = it }
            effect.colorMode?.let { tags["documentColorMode"] = it.tagValue }
            tags["documentScanGuide"] = effect.scanGuide.toString()
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
            watermarkText = watermark?.let(::buildWatermarkText),
            exifOverrides = emptyMap(),
            algorithmProfile = filter?.profileId
        )
    }

    private fun buildWatermarkText(effect: WatermarkEffect): String {
        return effect.tokens.values.filter { it.isNotBlank() }.joinToString(" | ")
    }
}
