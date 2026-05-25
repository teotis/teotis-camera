package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistedSettingsSerializerTest {
    @Test
    fun `serializer round trips persisted settings and keeps default video spec`() {
        val settings = PersistedSettings(
            common = CommonSettings(
                gridMode = CompositionGridMode.RULE_OF_THIRDS,
                shutterSoundEnabled = false,
                selfieMirrorEnabled = true
            ),
            photo = PhotoSettings(
                defaultFilterProfileId = "portrait-retro",
                defaultHumanisticFilterProfileId = "humanistic-life",
                defaultPortraitFilterProfileId = "portrait-ccd",
                portraitProfile = PortraitProfile.LUMINOUS,
                portraitBeautyPreset = PortraitBeautyPreset.RADIANT,
                portraitBeautyStrength = PortraitBeautyStrength.ELEVATED,
                portraitBokehEffect = PortraitBokehEffect.DREAMY,
                defaultWatermarkTemplateId = "travel-polaroid",
                classicOverlayWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                    textScale = WatermarkTextScale.LARGE,
                    textOpacity = WatermarkTextOpacity.SUBTLE,
                    frameBackground = WatermarkFrameBackground.DARK
                ),
                travelPolaroidWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                    textScale = WatermarkTextScale.COMPACT,
                    textOpacity = WatermarkTextOpacity.SOLID,
                    frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
                ),
                retroFrameWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.TOP_LEFT,
                    textScale = WatermarkTextScale.NORMAL,
                    textOpacity = WatermarkTextOpacity.SOFT,
                    frameBackground = WatermarkFrameBackground.WHITE
                ),
                pureTextWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                    textScale = WatermarkTextScale.LARGE,
                    textOpacity = WatermarkTextOpacity.SUBTLE,
                    frameBackground = WatermarkFrameBackground.DARK
                ),
                blurFourBorderWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                    textScale = WatermarkTextScale.COMPACT,
                    textOpacity = WatermarkTextOpacity.SOLID,
                    frameBackground = WatermarkFrameBackground.SOURCE_VIVID_BLUR
                ),
                livePhotoEnabledByDefault = true,
                countdownDuration = CountdownDuration.SECONDS_3
            ),
            video = VideoSettings(
                defaultVideoSpec = VideoSpec(
                    resolution = VideoResolution.UHD_4K,
                    frameRate = VideoFrameRate.FPS_25,
                    dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                    audioProfile = AudioProfile.CONCERT
                ),
                defaultFilterProfileId = "photo-rich"
            )
        )

        val serialized = PersistedSettingsSerializer.toMap(settings)
        val decoded = PersistedSettingsSerializer.fromMap(serialized)

        assertEquals(settings, decoded)
        assertEquals("humanistic-life", serialized["photo.defaultHumanisticFilterProfileId"])
        assertEquals("portrait-ccd", serialized["photo.defaultPortraitFilterProfileId"])
        assertEquals("luminous", serialized["photo.portrait.profile"])
        assertEquals("radiant", serialized["photo.portrait.beautyPreset"])
        assertEquals("elevated", serialized["photo.portrait.beautyStrength"])
        assertEquals("dreamy", serialized["photo.portrait.bokehEffect"])
        assertEquals("4k", serialized["video.defaultVideoResolution"])
        assertEquals("concert", serialized["video.defaultVideoAudioProfile"])
        assertEquals("travel-polaroid", serialized["photo.defaultWatermarkTemplateId"])
        assertEquals("top-right", serialized["photo.watermark.classicOverlay.position"])
        assertEquals("compact", serialized["photo.watermark.travelPolaroid.scale"])
        assertEquals("white", serialized["photo.watermark.retroFrame.background"])
        assertEquals("top-right", serialized["photo.watermark.pureText.position"])
        assertEquals("subtle", serialized["photo.watermark.pureText.opacity"])
        assertEquals("source-vivid-blur", serialized["photo.watermark.blurFourBorder.background"])
    }

    @Test
    fun `serializer falls back to safe defaults for unknown values`() {
        val decoded = PersistedSettingsSerializer.fromMap(
            mapOf(
                "common.gridMode" to "unknown-grid",
                "common.shutterSoundEnabled" to "invalid-bool",
                "photo.portrait.profile" to "ultra",
                "photo.portrait.beautyPreset" to "glass",
                "photo.portrait.beautyStrength" to "200%",
                "photo.portrait.bokehEffect" to "ring-flare",
                "photo.defaultWatermarkTemplateId" to "broken-template",
                "photo.watermark.classicOverlay.position" to "middle",
                "photo.watermark.travelPolaroid.scale" to "huge",
                "photo.watermark.retroFrame.opacity" to "ghost",
                "photo.watermark.retroFrame.background" to "pastel",
                "photo.countdownDuration" to "77s",
                "video.defaultVideoResolution" to "12k",
                "video.defaultVideoFrameRate" to "144",
                "video.defaultVideoDynamicFpsPolicy" to "magic",
                "video.defaultVideoAudioProfile" to "surround"
            )
        )

        assertEquals(PersistedSettings(), decoded)
    }

    @Test
    fun `settings reducer updates targeted fields without rewriting unrelated branches`() {
        val initial = PersistedSettings()
        val reduced = initial
            .reduce(PersistedSettingsAction.UpdateGridMode(CompositionGridMode.GOLDEN_RATIO))
            .reduce(PersistedSettingsAction.UpdateHumanisticFilter("humanistic-life"))
            .reduce(PersistedSettingsAction.UpdatePortraitFilter("portrait-ccd"))
            .reduce(PersistedSettingsAction.UpdatePortraitProfile(PortraitProfile.LUMINOUS))
            .reduce(
                PersistedSettingsAction.UpdatePortraitBeautyPreset(PortraitBeautyPreset.CLEAR)
            )
            .reduce(
                PersistedSettingsAction.UpdatePortraitBeautyStrength(
                    PortraitBeautyStrength.BALANCED
                )
            )
            .reduce(
                PersistedSettingsAction.UpdatePortraitBokehEffect(PortraitBokehEffect.CREAMY)
            )
            .reduce(
                PersistedSettingsAction.UpdatePhotoWatermarkTemplate("travel-polaroid")
            )
            .reduce(
                PersistedSettingsAction.UpdateWatermarkTextPlacement(
                    templateId = "travel-polaroid",
                    placement = WatermarkTextPlacement.BOTTOM_CENTER
                )
            )
            .reduce(
                PersistedSettingsAction.UpdateWatermarkTextScale(
                    templateId = "travel-polaroid",
                    scale = WatermarkTextScale.LARGE
                )
            )
            .reduce(
                PersistedSettingsAction.UpdateWatermarkTextOpacity(
                    templateId = "travel-polaroid",
                    opacity = WatermarkTextOpacity.SUBTLE
                )
            )
            .reduce(
                PersistedSettingsAction.UpdateWatermarkFrameBackground(
                    templateId = "travel-polaroid",
                    background = WatermarkFrameBackground.SOURCE_BLUR
                )
            )
            .reduce(
                PersistedSettingsAction.UpdateDefaultVideoSpec(
                    VideoSpec(
                        resolution = VideoResolution.HD_720P,
                        frameRate = VideoFrameRate.FPS_60,
                        dynamicFpsPolicy = DynamicVideoFpsPolicy.LOCKED,
                        audioProfile = AudioProfile.STANDARD
                    )
                )
            )
            .reduce(PersistedSettingsAction.UpdateCountdownDuration(CountdownDuration.SECONDS_10))

        assertEquals(CompositionGridMode.GOLDEN_RATIO, reduced.common.gridMode)
        assertEquals("humanistic-life", reduced.photo.defaultHumanisticFilterProfileId)
        assertEquals("portrait-ccd", reduced.photo.defaultPortraitFilterProfileId)
        assertEquals(PortraitProfile.LUMINOUS, reduced.photo.portraitProfile)
        assertEquals(PortraitBeautyPreset.CLEAR, reduced.photo.portraitBeautyPreset)
        assertEquals(PortraitBeautyStrength.BALANCED, reduced.photo.portraitBeautyStrength)
        assertEquals(PortraitBokehEffect.CREAMY, reduced.photo.portraitBokehEffect)
        assertEquals("travel-polaroid", reduced.photo.defaultWatermarkTemplateId)
        assertEquals(
            WatermarkTextPlacement.BOTTOM_CENTER,
            reduced.photo.travelPolaroidWatermarkStyle.textPlacement
        )
        assertEquals(
            WatermarkTextScale.LARGE,
            reduced.photo.travelPolaroidWatermarkStyle.textScale
        )
        assertEquals(
            WatermarkTextOpacity.SUBTLE,
            reduced.photo.travelPolaroidWatermarkStyle.textOpacity
        )
        assertEquals(
            WatermarkFrameBackground.SOURCE_BLUR,
            reduced.photo.travelPolaroidWatermarkStyle.frameBackground
        )
        assertEquals(VideoResolution.HD_720P, reduced.video.defaultVideoSpec.resolution)
        assertEquals(VideoFrameRate.FPS_60, reduced.video.defaultVideoSpec.frameRate)
        assertEquals(CountdownDuration.SECONDS_10, reduced.photo.countdownDuration)
        assertTrue(reduced.common.shutterSoundEnabled)
        assertEquals(initial.photo.defaultFilterProfileId, reduced.photo.defaultFilterProfileId)
        assertEquals(
            initial.photo.classicOverlayWatermarkStyle,
            reduced.photo.classicOverlayWatermarkStyle
        )
    }

    @Test
    fun `map backed settings store loads last saved snapshot`() {
        val store = MapPersistedSettingsStore()
        val settings = PersistedSettings(
            photo = PhotoSettings(
                defaultFilterProfileId = "photo-rich",
                defaultHumanisticFilterProfileId = "humanistic-life",
                defaultPortraitFilterProfileId = "portrait-rich",
                portraitProfile = PortraitProfile.LUMINOUS,
                portraitBeautyPreset = PortraitBeautyPreset.CLEAR,
                portraitBeautyStrength = PortraitBeautyStrength.BALANCED,
                portraitBokehEffect = PortraitBokehEffect.CREAMY,
                defaultWatermarkTemplateId = "retro-frame",
                retroFrameWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                    textScale = WatermarkTextScale.LARGE,
                    textOpacity = WatermarkTextOpacity.SOLID,
                    frameBackground = WatermarkFrameBackground.WHITE
                ),
                livePhotoEnabledByDefault = true,
                countdownDuration = CountdownDuration.SECONDS_5
            ),
            video = VideoSettings(
                defaultVideoSpec = VideoSpec(
                    resolution = VideoResolution.HD_720P,
                    frameRate = VideoFrameRate.FPS_60,
                    dynamicFpsPolicy = DynamicVideoFpsPolicy.LOCKED,
                    audioProfile = AudioProfile.STANDARD
                )
            )
        )

        store.save(settings)

        assertEquals(settings, store.load())
        assertEquals("photo-rich", store.snapshot()["photo.defaultFilterProfileId"])
        assertEquals("humanistic-life", store.snapshot()["photo.defaultHumanisticFilterProfileId"])
        assertEquals("portrait-rich", store.snapshot()["photo.defaultPortraitFilterProfileId"])
        assertEquals("luminous", store.snapshot()["photo.portrait.profile"])
        assertEquals("clear", store.snapshot()["photo.portrait.beautyPreset"])
        assertEquals("balanced", store.snapshot()["photo.portrait.beautyStrength"])
        assertEquals("creamy", store.snapshot()["photo.portrait.bokehEffect"])
        assertEquals("retro-frame", store.snapshot()["photo.defaultWatermarkTemplateId"])
        assertEquals("top-right", store.snapshot()["photo.watermark.retroFrame.position"])
        assertEquals("large", store.snapshot()["photo.watermark.retroFrame.scale"])
        assertEquals("720p", store.snapshot()["video.defaultVideoResolution"])
    }

    @Test
    fun `persisted settings reducer updates color lab spec`() {
        val spec = ColorLabSpec(colorAxis = 0.5f, toneAxis = -0.25f, strength = 0.8f)

        val settings = PersistedSettings().reduce(
            PersistedSettingsAction.UpdateColorLabSpec(spec)
        )

        assertEquals(spec.normalized(), settings.photo.colorLabSpec)
    }

    @Test
    fun `serializer round trips color lab spec`() {
        val settings = PersistedSettings(
            photo = PhotoSettings(
                colorLabSpec = ColorLabSpec(
                    colorAxis = -0.75f,
                    toneAxis = 0.5f,
                    strength = 0.6f,
                    presetId = "cool-air"
                )
            )
        )

        val decoded = PersistedSettingsSerializer.fromMap(
            PersistedSettingsSerializer.toMap(settings)
        )

        assertEquals(settings.photo.colorLabSpec, decoded.photo.colorLabSpec)
    }

    @Test
    fun `default photo settings has low light night assist enabled`() {
        val settings = PhotoSettings()
        assertTrue(settings.lowLightNightAssistEnabled)
    }

    @Test
    fun `reducer toggles low light night assist without affecting other fields`() {
        val initial = PersistedSettings()
        assertTrue(initial.photo.lowLightNightAssistEnabled)

        val disabled = initial.reduce(
            PersistedSettingsAction.UpdatePhotoLowLightNightAssistEnabled(false)
        )
        assertFalse(disabled.photo.lowLightNightAssistEnabled)
        assertEquals(initial.photo.defaultFilterProfileId, disabled.photo.defaultFilterProfileId)
        assertEquals(initial.photo.countdownDuration, disabled.photo.countdownDuration)
        assertEquals(initial.common, disabled.common)
        assertEquals(initial.video, disabled.video)

        val reEnabled = disabled.reduce(
            PersistedSettingsAction.UpdatePhotoLowLightNightAssistEnabled(true)
        )
        assertTrue(reEnabled.photo.lowLightNightAssistEnabled)
    }

    @Test
    fun `serializer round trips low light night assist field`() {
        val settings = PersistedSettings(
            photo = PhotoSettings(lowLightNightAssistEnabled = false)
        )

        val serialized = PersistedSettingsSerializer.toMap(settings)
        assertEquals("false", serialized["photo.lowLightNightAssistEnabled"])

        val decoded = PersistedSettingsSerializer.fromMap(serialized)
        assertFalse(decoded.photo.lowLightNightAssistEnabled)
    }

    @Test
    fun `serializer falls back to enabled when low light key is missing`() {
        val decoded = PersistedSettingsSerializer.fromMap(emptyMap())
        assertTrue(decoded.photo.lowLightNightAssistEnabled)
    }

    @Test
    fun `all portrait enum storage keys deserialize correctly for backward compatibility`() {
        // PortraitProfile
        assertEquals(PortraitProfile.NATIVE, PortraitProfile.fromStorageKey("native"))
        assertEquals(PortraitProfile.LUMINOUS, PortraitProfile.fromStorageKey("luminous"))

        // PortraitBeautyPreset
        assertEquals(PortraitBeautyPreset.AUTHENTIC, PortraitBeautyPreset.fromStorageKey("authentic"))
        assertEquals(PortraitBeautyPreset.CLEAR, PortraitBeautyPreset.fromStorageKey("clear"))
        assertEquals(PortraitBeautyPreset.RADIANT, PortraitBeautyPreset.fromStorageKey("radiant"))

        // PortraitBeautyStrength
        assertEquals(PortraitBeautyStrength.OFF, PortraitBeautyStrength.fromStorageKey("off"))
        assertEquals(PortraitBeautyStrength.SOFT, PortraitBeautyStrength.fromStorageKey("soft"))
        assertEquals(PortraitBeautyStrength.BALANCED, PortraitBeautyStrength.fromStorageKey("balanced"))
        assertEquals(PortraitBeautyStrength.ELEVATED, PortraitBeautyStrength.fromStorageKey("elevated"))

        // PortraitBokehEffect
        assertEquals(PortraitBokehEffect.NATURAL, PortraitBokehEffect.fromStorageKey("natural"))
        assertEquals(PortraitBokehEffect.CREAMY, PortraitBokehEffect.fromStorageKey("creamy"))
        assertEquals(PortraitBokehEffect.DREAMY, PortraitBokehEffect.fromStorageKey("dreamy"))
    }

    @Test
    fun `default watermark catalog includes pure text and blur four border`() {
        val catalog = FeatureCatalog()
        val pureText = catalog.watermarkTemplates.first { it.id == "pure-text" }
        val blurBorder = catalog.watermarkTemplates.first { it.id == "blur-four-border" }

        assertEquals(WatermarkTemplateKind.TEXT_OVERLAY, pureText.kind)
        assertFalse(pureText.supportsFrameBorder)
        assertEquals(WatermarkTemplateKind.EXPANDED_FRAME, blurBorder.kind)
        assertTrue(blurBorder.supportsFrameBorder)
        assertEquals(
            setOf(
                WatermarkFrameBackground.SOURCE_BLUR,
                WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
                WatermarkFrameBackground.SOURCE_VIVID_BLUR
            ),
            blurBorder.allowedFrameBackgrounds
        )
    }

    @Test
    fun `default watermark catalog includes professional bottom bar`() {
        val catalog = FeatureCatalog()
        val proBar = catalog.watermarkTemplates.first { it.id == "professional-bottom-bar" }

        assertEquals(WatermarkTemplateKind.EXPANDED_FRAME, proBar.kind)
        assertTrue(proBar.supportsFrameBorder)
        assertEquals(
            setOf("model", "datetime", "camera-params"),
            proBar.tokenKeys
        )
        assertEquals(
            setOf(
                WatermarkTextPlacement.BOTTOM_LEFT,
                WatermarkTextPlacement.BOTTOM_CENTER,
                WatermarkTextPlacement.BOTTOM_RIGHT
            ),
            proBar.allowedPlacements
        )
        assertTrue(proBar.allowedFrameBackgrounds.contains(WatermarkFrameBackground.DARK))
        assertTrue(proBar.allowedFrameBackgrounds.contains(WatermarkFrameBackground.WHITE))
    }

    @Test
    fun `serializer round trips professional bottom bar watermark style`() {
        val settings = PersistedSettings(
            photo = PhotoSettings(
                defaultWatermarkTemplateId = "professional-bottom-bar",
                professionalBottomBarWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
                    textScale = WatermarkTextScale.LARGE,
                    textOpacity = WatermarkTextOpacity.SUBTLE,
                    frameBackground = WatermarkFrameBackground.WHITE
                )
            )
        )

        val serialized = PersistedSettingsSerializer.toMap(settings)
        assertEquals(
            "professional-bottom-bar",
            serialized["photo.defaultWatermarkTemplateId"]
        )
        assertEquals(
            "bottom-left",
            serialized["photo.watermark.professionalBottomBar.position"]
        )
        assertEquals(
            "large",
            serialized["photo.watermark.professionalBottomBar.scale"]
        )
        assertEquals(
            "white",
            serialized["photo.watermark.professionalBottomBar.background"]
        )

        val decoded = PersistedSettingsSerializer.fromMap(serialized)
        assertEquals(settings, decoded)
    }

    @Test
    fun `default portrait depth strength is 50`() {
        val settings = PhotoSettings()
        assertEquals(50, settings.portraitDepthStrength)
    }

    @Test
    fun `reducer updates portrait depth strength`() {
        val initial = PersistedSettings()
        val reduced = initial.reduce(
            PersistedSettingsAction.UpdatePortraitDepthStrength(75)
        )
        assertEquals(75, reduced.photo.portraitDepthStrength)
        assertEquals(initial.photo.defaultFilterProfileId, reduced.photo.defaultFilterProfileId)
        assertEquals(initial.photo.portraitBokehEffect, reduced.photo.portraitBokehEffect)
        assertEquals(initial.common, reduced.common)
        assertEquals(initial.video, reduced.video)
    }

    @Test
    fun `reducer clamps portrait depth strength to 0`() {
        val reduced = PersistedSettings().reduce(
            PersistedSettingsAction.UpdatePortraitDepthStrength(-10)
        )
        assertEquals(0, reduced.photo.portraitDepthStrength)
    }

    @Test
    fun `reducer clamps portrait depth strength to 100`() {
        val reduced = PersistedSettings().reduce(
            PersistedSettingsAction.UpdatePortraitDepthStrength(150)
        )
        assertEquals(100, reduced.photo.portraitDepthStrength)
    }

    @Test
    fun `serializer round trips portrait depth strength`() {
        val settings = PersistedSettings(
            photo = PhotoSettings(portraitDepthStrength = 75)
        )
        val serialized = PersistedSettingsSerializer.toMap(settings)
        assertEquals("75", serialized["photo.portrait.depthStrength"])
        val decoded = PersistedSettingsSerializer.fromMap(serialized)
        assertEquals(75, decoded.photo.portraitDepthStrength)
    }

    @Test
    fun `serializer falls back to 50 when depth strength key is missing`() {
        val decoded = PersistedSettingsSerializer.fromMap(emptyMap())
        assertEquals(50, decoded.photo.portraitDepthStrength)
    }

    @Test
    fun `serializer falls back to 50 for invalid depth strength value`() {
        val decoded = PersistedSettingsSerializer.fromMap(
            mapOf("photo.portrait.depthStrength" to "abc")
        )
        assertEquals(50, decoded.photo.portraitDepthStrength)
    }
}
