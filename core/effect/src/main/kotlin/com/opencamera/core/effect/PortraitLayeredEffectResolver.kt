package com.opencamera.core.effect

import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile

data class PortraitSubjectBeautySpec(
    val smoothing: Float,
    val lift: Float,
    val saturationBoost: Float
)

data class PortraitBackgroundBokehSpec(
    val blurScale: Int,
    val focusRadiusXFraction: Float,
    val focusRadiusYFraction: Float,
    val edgeSoftness: Float,
    val vignetteStrength: Float
)

data class PortraitBackgroundLightSpotSpec(
    val highlightBloom: Float,
    val backgroundBloom: Float
)

data class PortraitLayeredEffectSpec(
    val renderPath: String,
    val portraitProfile: PortraitProfile,
    val beautyPreset: PortraitBeautyPreset,
    val beautyStrength: PortraitBeautyStrength,
    val bokehEffect: PortraitBokehEffect,
    val subjectTracking: Boolean,
    val strength: Float,
    val subjectBeauty: PortraitSubjectBeautySpec,
    val backgroundBokeh: PortraitBackgroundBokehSpec,
    val backgroundLightSpot: PortraitBackgroundLightSpotSpec
)

object PortraitLayeredEffectResolver {

    fun resolve(
        renderPath: String,
        bokehStrength: Float?,
        subjectTracking: Boolean,
        portraitProfile: PortraitProfile,
        beautyPreset: PortraitBeautyPreset,
        beautyStrength: PortraitBeautyStrength,
        bokehEffect: PortraitBokehEffect
    ): PortraitLayeredEffectSpec? {
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

        val subjectBeauty = PortraitSubjectBeautySpec(
            smoothing = subjectSmoothing,
            lift = subjectLift,
            saturationBoost = subjectSaturationBoost
        )
        val lightSpot = PortraitBackgroundLightSpotSpec(
            highlightBloom = highlightBloom,
            backgroundBloom = backgroundBloom
        )

        return when (renderPath) {
            "depth" -> {
                val strength = ((bokehStrength ?: 1.8f) + profileStrengthOffset + bokehStrengthOffset)
                    .coerceIn(1f, 3f)
                PortraitLayeredEffectSpec(
                    renderPath = renderPath,
                    portraitProfile = portraitProfile,
                    beautyPreset = beautyPreset,
                    beautyStrength = beautyStrength,
                    bokehEffect = bokehEffect,
                    subjectTracking = subjectTracking,
                    strength = strength,
                    subjectBeauty = subjectBeauty,
                    backgroundBokeh = PortraitBackgroundBokehSpec(
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
                            ).coerceIn(0.08f, 0.22f)
                    ),
                    backgroundLightSpot = lightSpot
                )
            }

            "focus" -> {
                val strength = ((bokehStrength ?: 1f) + profileStrengthOffset * 0.6f + bokehStrengthOffset * 0.4f)
                    .coerceIn(0.8f, 1.6f)
                PortraitLayeredEffectSpec(
                    renderPath = renderPath,
                    portraitProfile = portraitProfile,
                    beautyPreset = beautyPreset,
                    beautyStrength = beautyStrength,
                    bokehEffect = bokehEffect,
                    subjectTracking = subjectTracking,
                    strength = strength,
                    subjectBeauty = subjectBeauty,
                    backgroundBokeh = PortraitBackgroundBokehSpec(
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
                        }
                    ),
                    backgroundLightSpot = lightSpot
                )
            }

            else -> null
        }
    }
}
