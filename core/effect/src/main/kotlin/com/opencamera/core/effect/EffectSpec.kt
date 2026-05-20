package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings

enum class EffectTarget { PREVIEW, CAPTURE, BOTH }

sealed interface EffectEntry {
    val target: EffectTarget
}

data class FilterEffect(
    val profileId: String,
    val renderSpec: FilterRenderSpec?,
    override val target: EffectTarget = EffectTarget.BOTH
) : EffectEntry

data class WatermarkEffect(
    val templateId: String,
    val tokens: Map<String, String>,
    val style: WatermarkStyleSettings,
    override val target: EffectTarget = EffectTarget.BOTH
) : EffectEntry

data class FrameEffect(
    val ratio: FrameRatio,
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class PortraitEffect(
    val profileId: String,
    val renderPath: String,
    val beautyPreset: String,
    val beautyStrength: String,
    val bokehEffect: String,
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class DocumentEffect(
    val autoCrop: Boolean,
    val contrastProfile: String?,
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class SelfieMirrorEffect(
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class EffectSpec(
    val entries: List<EffectEntry>
) {
    companion object {
        val EMPTY = EffectSpec(emptyList())
    }

    inline fun <reified T : EffectEntry> find(): T? =
        entries.filterIsInstance<T>().firstOrNull()

    fun hasTarget(target: EffectTarget): Boolean =
        entries.any { it.target == target || it.target == EffectTarget.BOTH }
}
