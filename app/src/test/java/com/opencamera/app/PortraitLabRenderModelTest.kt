package com.opencamera.app

import androidx.annotation.StringRes
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
import com.opencamera.core.media.SaveRequest
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
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitLabRenderModelTest {


        @Test
        fun `portrait lab render model exposes profile beauty and bokeh controls`() {
            val baseline = defaultSessionState()
            val model = portraitLabPageRenderModel(
                baseline.copy(
                    settings = baseline.settings.copy(
                        persisted = baseline.settings.persisted.copy(
                            photo = baseline.settings.persisted.photo.copy(
                                portraitProfile = PortraitProfile.LUMINOUS,
                                portraitBeautyPreset = PortraitBeautyPreset.RADIANT,
                                portraitBeautyStrength = PortraitBeautyStrength.ELEVATED,
                                portraitBokehEffect = PortraitBokehEffect.DREAMY
                            )
                        )
                    )
                ),
                TestAppTextResolver()
            )

            assertEquals("Portrait Lab", model.headline)
            assertEquals("", model.heroSummary)
            assertEquals(
                PersistedSettingsAction.UpdatePortraitProfile(PortraitProfile.NATIVE),
                model.profileControl.nextAction
            )
            assertEquals(
                PersistedSettingsAction.UpdatePortraitBeautyPreset(PortraitBeautyPreset.AUTHENTIC),
                model.beautyPresetControl.nextAction
            )
            assertEquals(
                PersistedSettingsAction.UpdatePortraitBeautyStrength(PortraitBeautyStrength.OFF),
                model.beautyStrengthControl.nextAction
            )
            assertEquals(
                PersistedSettingsAction.UpdatePortraitBokehEffect(PortraitBokehEffect.NATURAL),
                model.bokehEffectControl.nextAction
            )
            assertTrue(model.footer.contains("Tone Lab still owns portrait color style selection"))
        }



        @Test
        fun `portrait lab render model exposes depth strength slider`() {
            val baseline = defaultSessionState()
            val model = portraitLabPageRenderModel(
                baseline.copy(
                    settings = baseline.settings.copy(
                        persisted = baseline.settings.persisted.copy(
                            photo = baseline.settings.persisted.photo.copy(
                                portraitDepthStrength = 75
                            )
                        )
                    )
                ),
                TestAppTextResolver()
            )

            assertEquals(75, model.depthStrength)
            assertEquals("75%", model.depthStrengthLabel)
            assertNotNull(model.updateDepthStrengthAction)
        }



        @Test
        fun `portrait lab depth strength action is null when editing disabled`() {
            val model = portraitLabPageRenderModel(
                defaultSessionState(
                    activeShot = ShotRequest(
                        shotId = "shot-depth",
                        shotKind = ShotKind.STILL_CAPTURE,
                        mediaType = MediaType.PHOTO,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                        postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
                        captureProfile = CaptureProfile()
                    ),
                    persistedPhotoSettings = PhotoSettings(portraitDepthStrength = 75)
                ),
                TestAppTextResolver()
            )

            assertEquals(75, model.depthStrength)
            assertNull(model.updateDepthStrengthAction)
        }



        @Test
        fun `portrait lab page render model uses text resolver for all labels`() {
            val customResolver = object : TestAppTextResolver() {
                internal override fun get(@StringRes resId: Int): String = when (resId) {
                    R.string.button_portrait_mode -> "人像实验室"
                    R.string.label_portrait_profile -> "人像配置文件"
                    R.string.label_beauty_preset -> "美颜预设"
                    R.string.label_beauty_strength -> "美颜强度"
                    R.string.label_bokeh_effect -> "散景效果"
                    else -> super.get(resId)
                }
            }
            val model = portraitLabPageRenderModel(defaultSessionState(), customResolver)
            assertEquals("人像实验室", model.headline)
            assertTrue(model.profileControl.buttonLabel.startsWith("人像配置文件"))
            assertTrue(model.beautyPresetControl.buttonLabel.startsWith("美颜预设"))
            assertTrue(model.beautyStrengthControl.buttonLabel.startsWith("美颜强度"))
            assertTrue(model.bokehEffectControl.buttonLabel.startsWith("散景效果"))
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
