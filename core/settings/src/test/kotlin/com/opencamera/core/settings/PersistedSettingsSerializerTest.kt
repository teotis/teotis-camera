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
    fun `serializer round trips live save format`() {
        val settings = PersistedSettings(
            photo = PhotoSettings(liveSaveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR)
        )

        val serialized = PersistedSettingsSerializer.toMap(settings)
        assertEquals("motion-mp4-sidecar", serialized["photo.live.saveFormat"])

        val decoded = PersistedSettingsSerializer.fromMap(serialized)
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, decoded.photo.liveSaveFormat)
    }

    @Test
    fun `serializer falls back to google motion photo when live save format key is missing`() {
        val decoded = PersistedSettingsSerializer.fromMap(emptyMap())
        assertEquals(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG, decoded.photo.liveSaveFormat)
    }

    @Test
    fun `serializer falls back to google motion photo for unknown live save format key`() {
        val decoded = PersistedSettingsSerializer.fromMap(
            mapOf("photo.live.saveFormat" to "unknown-format")
        )
        assertEquals(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG, decoded.photo.liveSaveFormat)
    }

    @Test
    fun `reducer updates live save format without affecting other fields`() {
        val initial = PersistedSettings()
        assertEquals(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG, initial.photo.liveSaveFormat)

        val updated = initial.reduce(
            PersistedSettingsAction.UpdateLiveSaveFormat(LiveSaveFormat.STILL_JPEG_ONLY)
        )
        assertEquals(LiveSaveFormat.STILL_JPEG_ONLY, updated.photo.liveSaveFormat)
        assertEquals(initial.photo.defaultFilterProfileId, updated.photo.defaultFilterProfileId)
        assertEquals(initial.photo.livePhotoEnabledByDefault, updated.photo.livePhotoEnabledByDefault)
        assertEquals(initial.common, updated.common)
        assertEquals(initial.video, updated.video)
    }

    @Test
    fun `map backed settings store preserves live save format in snapshot`() {
        val store = MapPersistedSettingsStore()
        val settings = PersistedSettings(
            photo = PhotoSettings(liveSaveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR)
        )

        store.save(settings)

        assertEquals("motion-mp4-sidecar", store.snapshot()["photo.live.saveFormat"])
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, store.load().photo.liveSaveFormat)
    }

    @Test
    fun `default live save format is google motion photo jpeg`() {
        val settings = PhotoSettings()
        assertEquals(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG, settings.liveSaveFormat)
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
}
