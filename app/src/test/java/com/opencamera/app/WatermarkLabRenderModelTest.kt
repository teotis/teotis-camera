package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WatermarkLabRenderModelTest {


        @Test
        fun `watermark lab selector render model exposes selection and per template style entry`() {
            val model = watermarkLabSelectorRenderModel(defaultSessionState(), TestAppTextResolver())

            assertEquals("Watermark Lab", model.headline)
            assertEquals("", model.heroSummary)
            assertEquals(7, model.items.size)
            val selected = model.items.first { it.templateId == "travel-polaroid" }
            val classic = model.items.first { it.templateId == "classic-overlay" }
            val pureText = model.items.first { it.templateId == "pure-text" }
            val blurBorder = model.items.first { it.templateId == "blur-four-border" }
            val vanGoghStarry = model.items.first { it.templateId == "van-gogh-starry" }
            val blueHour = model.items.first { it.templateId == "blue-hour" }
            assertFalse(model.items.any { it.templateId == "professional-bottom-bar" })
            assertFalse(model.items.any { it.templateId == "night-street" })
            assertTrue(selected.isSelected)
            assertEquals(null, selected.useAction)
            assertTrue(selected.supportingText.contains("Current default"))
            assertEquals("Style", selected.editButtonLabel)
            assertEquals(
                PersistedSettingsAction.UpdatePhotoWatermarkTemplate("classic-overlay"),
                classic.useAction
            )
            assertTrue(classic.supportingText.contains("Classic overlay"))
            assertTrue(pureText.supportingText.contains("Translucent Bottom Bar", ignoreCase = true))
            assertTrue(blurBorder.supportingText.contains("Blur four border", ignoreCase = true))
            assertEquals("Van Gogh Starry", vanGoghStarry.title)
            assertEquals("Blue Hour", blueHour.title)
            assertTrue(vanGoghStarry.supportingText.contains("Expanded frame"))
            assertTrue(blueHour.supportingText.contains("Expanded frame"))
            assertTrue(model.footer.contains("reversible original JPEG archive"))
        }



        @Test
        fun `watermark lab detail render model exposes frame controls for frame templates`() {
            val baselineState = defaultSessionState()
            val model = watermarkLabDetailRenderModel(
                state = baselineState.copy(
                    settings = baselineState.settings.copy(
                        persisted = baselineState.settings.persisted.copy(
                            photo = baselineState.settings.persisted.photo.copy(
                                defaultWatermarkTemplateId = "travel-polaroid",
                                travelPolaroidWatermarkStyle = WatermarkStyleSettings(
                                    textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                                    textScale = WatermarkTextScale.COMPACT,
                                    textOpacity = WatermarkTextOpacity.SUBTLE,
                                    frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
                                )
                            )
                        )
                    )
                ),
                templateId = "travel-polaroid",
                text = TestAppTextResolver()
            )

            assertEquals("Travel Polaroid", model.headline)
            assertEquals("", model.heroSummary)
            assertEquals(
                PersistedSettingsAction.UpdateWatermarkTextPlacement(
                    templateId = "travel-polaroid",
                    placement = WatermarkTextPlacement.BOTTOM_LEFT
                ),
                model.placementControl.nextAction
            )
            assertEquals(
                PersistedSettingsAction.UpdateWatermarkTextScale(
                    templateId = "travel-polaroid",
                    scale = WatermarkTextScale.NORMAL
                ),
                model.textScaleControl.nextAction
            )
            assertEquals(
                PersistedSettingsAction.UpdateWatermarkTextOpacity(
                    templateId = "travel-polaroid",
                    opacity = WatermarkTextOpacity.SOFT
                ),
                model.textOpacityControl.nextAction
            )
            assertEquals(
                PersistedSettingsAction.UpdateWatermarkFrameBackground(
                    templateId = "travel-polaroid",
                    background = WatermarkFrameBackground.SOURCE_VIVID_BLUR
                ),
                model.frameBackgroundControl?.nextAction
            )
        }



        @Test
        fun `watermark lab detail hides frame background control for classic overlay`() {
            val model = watermarkLabDetailRenderModel(
                state = defaultSessionState(),
                templateId = "classic-overlay",
                text = TestAppTextResolver()
            )

            assertEquals("Classic overlay", model.headline)
            assertEquals(null, model.frameBackgroundControl)
            assertTrue(model.footer.contains("Classic overlay stays inside the source image"))
        }



        @Test
        fun `pure text watermark detail hides frame background`() {
            val model = watermarkLabDetailRenderModel(
                state = defaultSessionState(),
                templateId = "pure-text",
                text = TestAppTextResolver()
            )

            assertEquals("Translucent Bottom Bar", model.headline)
            assertEquals(null, model.frameBackgroundControl)
            assertNotNull(model.placementControl.nextAction)
            assertNotNull(model.textScaleControl.nextAction)
            assertNotNull(model.textOpacityControl.nextAction)
            assertTrue(model.footer.contains("translucent blue bottom bar", ignoreCase = true))
        }



        @Test
        fun `blur four border cycles only blur backgrounds`() {
            val model = watermarkLabDetailRenderModel(
                state = defaultSessionState(),
                templateId = "blur-four-border",
                text = TestAppTextResolver()
            )

            val action = assertNotNull(model.frameBackgroundControl?.nextAction)
            assertTrue(action is PersistedSettingsAction.UpdateWatermarkFrameBackground)
            assertTrue(
                action.background in setOf(
                    WatermarkFrameBackground.SOURCE_BLUR,
                    WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
                    WatermarkFrameBackground.SOURCE_VIVID_BLUR
                ),
                "Expected blur-family background but got ${action.background}"
            )
        }



        @Test
        fun `blur four border cycles only bottom placements`() {
            val model = watermarkLabDetailRenderModel(
                state = defaultSessionState(),
                templateId = "blur-four-border",
                text = TestAppTextResolver()
            )

            val action = assertNotNull(model.placementControl.nextAction)
            assertTrue(action is PersistedSettingsAction.UpdateWatermarkTextPlacement)
            assertTrue(
                action.placement in setOf(
                    WatermarkTextPlacement.BOTTOM_LEFT,
                    WatermarkTextPlacement.BOTTOM_CENTER,
                    WatermarkTextPlacement.BOTTOM_RIGHT
                ),
                "Expected bottom-only placement but got ${action.placement}"
            )
        }



        @Test
        fun `blur four border selector shows descriptive copy`() {
            val model = watermarkLabSelectorRenderModel(defaultSessionState(), TestAppTextResolver())
            val blurItem = model.items.first { it.templateId == "blur-four-border" }

            assertTrue(
                blurItem.supportingText.contains("Blur four border") ||
                blurItem.supportingText.contains("blur-four-border"),
                "Expected blur-four-border descriptive copy in supporting text: ${blurItem.supportingText}"
            )
            assertTrue(
                blurItem.supportingText.contains("Expanded frame") ||
                blurItem.supportingText.contains("expanded") ||
                blurItem.title.contains("Blur"),
                "Expected blur-four-border title or expanded frame hint: ${blurItem.title} / ${blurItem.supportingText}"
            )
        }



        @Test
        fun `blur four border detail footer mentions frame behavior`() {
            val model = watermarkLabDetailRenderModel(
                state = defaultSessionState(),
                templateId = "blur-four-border",
                text = TestAppTextResolver()
            )

            assertNotNull(model.frameBackgroundControl)
            assertTrue(
                model.footer.contains("frame") || model.footer.contains("Frame"),
                "Expected footer to mention frame behavior: ${model.footer}"
            )
            assertTrue(model.footer.contains("Model") || model.footer.contains("model"),
                "Expected footer to list tokens: ${model.footer}")
        }



        private fun defaultSessionState(
            activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
            activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewStatus: PreviewStatus = PreviewStatus.ACTIVE,
            previewMetrics: PreviewMetrics = PreviewMetrics(),
            activeShot: ShotRequest? = null,
            latestSavedMediaType: SavedMediaType? = null,
            latestCapturePath: String? = null,
            latestVideoPath: String? = null,
            latestLivePhotoBundle: LivePhotoBundle? = null,
            latestPipelineNotes: List<String> = emptyList(),
            lastError: String? = null,
            activeMode: ModeId = ModeId.PHOTO,
            availableModes: List<ModeId> = listOf(
                ModeId.PHOTO,
                ModeId.CHECK_IN,
                ModeId.HUMANISTIC,
                ModeId.VIDEO
            ),
            modeSnapshot: ModeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(
                    title = "PHOTO",
                    shutterLabel = "Capture PHOTO"
                ),
                state = ModeState(
                    headline = "PHOTO mode active",
                    detail = "Ready"
                )
            ),
            persistedPhotoSettings: PhotoSettings = PhotoSettings(
                defaultFilterProfileId = "portrait-retro",
                defaultHumanisticFilterProfileId = "humanistic-street",
                defaultPortraitFilterProfileId = "portrait-original",
                defaultWatermarkTemplateId = "travel-polaroid",
                livePhotoEnabledByDefault = true,
                countdownDuration = CountdownDuration.SECONDS_3
            )
        ): SessionState {
            return SessionState(
                lifecycle = SessionLifecycle.RUNNING,
                permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
                previewHostAvailable = true,
                previewStatus = previewStatus,
                previewStatusDetail = null,
                activeMode = activeMode,
                availableModes = availableModes,
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = RecordingStatus.IDLE,
                activeShot = activeShot,
                modeSnapshot = modeSnapshot,
                activeDeviceCapabilities = activeDeviceCapabilities,
                activeDeviceGraph = activeDeviceGraph,
                previewMetrics = previewMetrics,
                settings = SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        common = CommonSettings(
                            gridMode = CompositionGridMode.RULE_OF_THIRDS,
                            shutterSoundEnabled = false,
                            selfieMirrorEnabled = true
                        ),
                        photo = persistedPhotoSettings,
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
                ),
                presentation = SessionPresentationState(
                    lastAction = "Ready",
                    latestCapturePath = latestCapturePath,
                    latestVideoPath = latestVideoPath,
                    latestLivePhotoBundle = latestLivePhotoBundle,
                    latestSavedMediaType = latestSavedMediaType,
                    latestPipelineNotes = latestPipelineNotes,
                    lastError = lastError
                )
            )
        }

}
