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
import com.opencamera.core.settings.ResetTarget
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

class SessionSettingsRenderModelTest {


        @Test
        fun `session summary includes native output and action context`() {
            val baseState = defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                    zoomRatioCapability = ZoomRatioCapability(
                        support = ZoomControlSupport.DISCRETE_PRESET,
                        supportedRatios = listOf(1f, 2f, 5f),
                        defaultRatio = 1f
                    ),
                    availableStillCaptureOutputSizes = listOf(
                        StillCaptureOutputSize(width = 6000, height = 4000),
                        StillCaptureOutputSize(width = 4000, height = 3000)
                    ),
                    availableStillCaptureResolutionPresets = setOf(
                        StillCaptureResolutionPreset.LARGE_12MP,
                        StillCaptureResolutionPreset.MEDIUM_8MP
                    )
                ),
                activeDeviceGraph = DeviceGraphSpec.stillCapture(
                    preferredLensFacing = LensFacing.FRONT,
                    enablePreviewSnapshots = true,
                    zoomRatio = 2f,
                    outputSize = StillCaptureOutputSize(width = 4000, height = 3000)
                )
            )
            val state = baseState.copy(
                previewStatusDetail = "recover after surface lost",
                presentation = baseState.presentation.copy(
                    lastAction = "Still resolution set to 4000x3000"
                )
            )

            val summary = sessionSummaryText(state, TestAppTextResolver())

            assertTrue(summary.contains("Lens: Front (available: back/front)"))
            assertTrue(summary.contains("Zoom: 2.0x (available: 1.0x/2.0x/5.0x | Preset steps)"))
            assertTrue(summary.contains("Still native output: 4000x3000"))
            assertTrue(summary.contains("Native size list: 6000x4000/4000x3000"))
            assertTrue(summary.contains("Settings: Grid 3x3 | Shutter sound Off | Selfie mirror On"))
            assertTrue(
                summary.contains(
                    "Photo defaults: Filter Portrait Retro | Portrait Native Portrait | Watermark Travel Polaroid | Live On (Motion Photo) | Timer 3s"
                )
            )
            assertTrue(summary.contains("Video defaults: 4K 25fps | Mic Concert | Low-light auto 24fps | Filter Rich"))
            assertTrue(summary.contains("Catalog: 22 filters | 7 watermark templates | Live 1500 ms bundle"))
            assertTrue(summary.contains("Manual draft: RAW Off | ISO Auto | S Auto | WB Auto"))
            assertTrue(summary.contains("Action: Still resolution set to 4000x3000"))
        }



        @Test
        fun `settings render model resolves configured defaults from session snapshot`() {
            val state = defaultSessionState()

            val model = sessionSettingsRenderModel(state, TestAppTextResolver())

            assertEquals("Grid 3x3 | Shutter sound Off | Selfie mirror On", model.commonSummary)
            assertEquals(
                "Filter Portrait Retro | Portrait Native Portrait | Watermark Travel Polaroid | Live On (Motion Photo) | Timer 3s",
                model.photoSummary
            )
            assertEquals(
                "4K 25fps | Mic Concert | Low-light auto 24fps | Filter Rich",
                model.videoSummary
            )
            assertEquals("22 filters | 7 watermark templates | Live 1500 ms bundle", model.catalogSummary)
            assertEquals("RAW Off | ISO Auto | S Auto | WB Auto", model.manualDraftSummary)
        }



        @Test
        fun `settings page render model exposes section controls and catalog hints`() {
            val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

            assertEquals("Settings", model.headline)
            assertEquals("", model.heroSummary)
            assertTrue(model.editingEnabled)
            assertEquals(
                "Default photo filter\nPortrait Retro • 18 curated looks",
                model.photoSection.defaultFilter.buttonLabel
            )
            assertEquals(
                "Portrait Lab\nNative Portrait / Authentic / Natural\n部分支持 • Open profile + beauty + bokeh tuning",
                model.photoSection.portraitLab.buttonLabel
            )
            assertEquals(
                "Watermark Lab\nTravel Polaroid\n部分支持 • Open selector + per-template tuning; 7 templates",
                model.photoSection.watermarkTemplate.buttonLabel
            )
            assertEquals(
                "Resolution\n4K • 4 options",
                model.videoSection.resolution.buttonLabel
            )
            assertEquals(
                PersistedSettingsAction.UpdatePhotoFilter("portrait-ccd"),
                model.photoSection.defaultFilter.nextAction
            )
            assertEquals(null, model.photoSection.portraitLab.nextAction)
            assertEquals(
                PersistedSettingsAction.UpdateDefaultVideoSpec(
                    VideoSpec(
                        resolution = VideoResolution.FHD_1080P,
                        frameRate = VideoFrameRate.FPS_25,
                        dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                        audioProfile = AudioProfile.CONCERT
                    )
                ),
                model.videoSection.resolution.nextAction
            )
        }



        @Test
        fun `settings page section summaries are empty to avoid duplication`() {
            val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
            assertEquals("", model.heroSummary)
            assertEquals("", model.commonSection.summary)
            assertEquals("", model.photoSection.summary)
            assertEquals("", model.videoSection.summary)
        }



        @Test
        fun `settings page render model disables editing while shot is active`() {
            val model = sessionSettingsPageRenderModel(
                defaultSessionState(
                    activeShot = ShotRequest(
                        shotId = "shot-1",
                        shotKind = ShotKind.STILL_CAPTURE,
                        mediaType = MediaType.PHOTO,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                        postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
                        captureProfile = CaptureProfile()
                    )
                ),
                TestAppTextResolver()
            )

            assertFalse(model.editingEnabled)
            assertEquals(
                "Finish the current capture before changing saved defaults.",
                model.editingHint
            )
        }



        @Test
        fun `settings page render model surfaces supported degraded and unsupported controls`() {
            val supportedModel = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

            assertEquals(SettingsControlAvailability.SUPPORTED, supportedModel.photoSection.liveSaveFormat.availability)
            assertTrue(supportedModel.photoSection.liveSaveFormat.isInteractive)
            assertNotNull(supportedModel.photoSection.liveSaveFormat.nextAction)
            assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.photoSection.portraitLab.availability)
            assertFalse(supportedModel.photoSection.portraitLab.isInteractive)
            assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.photoSection.watermarkTemplate.availability)
            assertFalse(supportedModel.photoSection.watermarkTemplate.isInteractive)
            assertEquals(SettingsControlAvailability.SUPPORTED, supportedModel.videoSection.frameRate.availability)
            assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.videoSection.dynamicFps.availability)
            assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.videoSection.audioProfile.availability)
            assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.videoSection.defaultFilter.availability)

            val unsupportedModel = sessionSettingsPageRenderModel(
                defaultSessionState(
                    activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                        supportsStillCapture = false,
                        supportsVideoRecording = false,
                        supportsAudioRecording = false
                    )
                ),
                TestAppTextResolver()
            )

            assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.liveSaveFormat.availability)
            assertFalse(unsupportedModel.photoSection.liveSaveFormat.isInteractive)
            assertEquals(null, unsupportedModel.photoSection.liveSaveFormat.nextAction)
            assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.portraitLab.availability)
            assertFalse(unsupportedModel.photoSection.portraitLab.isInteractive)
            assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.watermarkTemplate.availability)
            assertFalse(unsupportedModel.photoSection.watermarkTemplate.isInteractive)
            assertEquals(null, unsupportedModel.photoSection.watermarkTemplate.nextAction)
            assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.videoSection.resolution.availability)
            assertFalse(unsupportedModel.videoSection.resolution.isInteractive)
            assertEquals(null, unsupportedModel.videoSection.resolution.nextAction)
            assertEquals(
                "Audio scene\nConcert\n不支持 • Video recording unavailable on this device",
                unsupportedModel.videoSection.audioProfile.buttonLabel
            )
        }



        @Test
        fun `availability labels use dedicated strings not quality level labels`() {
            val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
            assertTrue(model.photoSection.portraitLab.buttonLabel.contains("部分支持"))
            assertTrue(model.photoSection.watermarkTemplate.buttonLabel.contains("部分支持"))
            assertFalse(model.videoSection.frameRate.buttonLabel.contains("可用"))
            assertFalse(model.videoSection.frameRate.buttonLabel.contains("Fast"))
            assertFalse(model.videoSection.frameRate.buttonLabel.contains("N/A"))
        }



        @Test
        fun `statusText is empty for supported and present for degraded and unsupported`() {
            val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

            // SUPPORTED: statusText is empty
            assertEquals("", model.photoSection.liveSaveFormat.statusText)

            // DEGRADED: statusText contains availability label
            assertEquals("部分支持", model.photoSection.portraitLab.statusText)
            assertEquals("部分支持", model.videoSection.dynamicFps.statusText)

            // UNSUPPORTED model
            val unsupportedModel = sessionSettingsPageRenderModel(
                defaultSessionState(
                    activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                        supportsStillCapture = false,
                        supportsVideoRecording = false
                    )
                ),
                TestAppTextResolver()
            )
            assertEquals("不支持", unsupportedModel.videoSection.resolution.statusText)
        }



        @Test
        fun `settings page has reset action when user adjustments exist`() {
            val state = defaultSessionState()
            val model = sessionSettingsPageRenderModel(state, TestAppTextResolver())

            assertTrue(model.hasSettingsUserAdjustments)
            assertEquals(
                PersistedSettingsAction.ResetToDefaults(ResetTarget.SETTINGS),
                model.resetSettingsAction
            )
        }



        @Test
        fun `settings page keeps reset action when at defaults`() {
            val state = defaultSessionState(
                persistedPhotoSettings = com.opencamera.core.settings.PhotoSettings()
            ).copy(
                settings = com.opencamera.core.settings.SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings()
                )
            )
            val model = sessionSettingsPageRenderModel(state, TestAppTextResolver())

            assertFalse(model.hasSettingsUserAdjustments)
            assertEquals(
                PersistedSettingsAction.ResetToDefaults(ResetTarget.SETTINGS),
                model.resetSettingsAction
            )
        }



        @Test
        fun `quick panel sheet exposes rows without quality`() {
            val state = defaultSessionState()
            val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

            assertEquals("Grid", sheet.gridRow.title)

            assertEquals("Frame", sheet.frameRatioRow.title)
            assertEquals("Live", sheet.liveRow.title)
            assertEquals("Timer", sheet.timerRow.title)

            // Brightness row has slider fields
            assertEquals("Brightness", sheet.brightnessRow.title)
            assertTrue(sheet.brightnessRow.isVisible)
            assertEquals(0, sheet.brightnessRow.steps)
            assertTrue(sheet.brightnessRow.maxSteps >= sheet.brightnessRow.minSteps)
        }



        @Test
        fun `quick panel sheet frame ratio options remain present when one is selected`() {
            val state = defaultSessionState()
            val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

            assertEquals(3, sheet.frameRatioOptions.size)
            val selected = sheet.frameRatioOptions.filter { it.isSelected }
            assertEquals(1, selected.size)
            assertTrue(sheet.frameRatioOptions.all { it.label.isNotBlank() })
        }



        @Test
        fun `quick panel sheet frame ratio disabled for video mode`() {
            val state = defaultSessionState(activeMode = ModeId.VIDEO)
            val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

            assertFalse(sheet.frameRatioEnabled)
            assertNotNull(sheet.frameRatioDisabledReason)
            assertTrue(sheet.frameRatioOptions.all { !it.isEnabled })
        }



        @Test
        fun `quick panel sheet frame ratio disabled during active shot`() {
            val state = defaultSessionState(
                activeShot = ShotRequest(
                    shotId = "test-shot",
                    shotKind = ShotKind.STILL_CAPTURE,
                    mediaType = MediaType.PHOTO,
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
                    postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
                    captureProfile = CaptureProfile()
                )
            )
            val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

            assertFalse(sheet.frameRatioEnabled)
            assertNotNull(sheet.frameRatioDisabledReason)
        }



        @Test
        fun `quick panel rows expose correct control kinds`() {
            val state = defaultSessionState()
            val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

            assertEquals(QuickControlKind.TOGGLE, sheet.liveRow.controlKind)
            assertEquals(QuickControlKind.CYCLE, sheet.gridRow.controlKind)
            assertEquals(QuickControlKind.CYCLE, sheet.timerRow.controlKind)
            assertEquals(QuickControlKind.SEGMENTED, sheet.frameRatioRow.controlKind)
        }



        @Test
        fun `quick panel live toggle isSelected matches on-off state`() {
            val stateOn = defaultSessionState(
                persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                    livePhotoEnabledByDefault = true
                )
            )
            val sheetOn = quickPanelSheetRenderModel(stateOn, TestAppTextResolver(), strings)
            assertTrue(sheetOn.liveRow.isSelected)

            val stateOff = defaultSessionState(
                persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                    livePhotoEnabledByDefault = false
                )
            )
            val sheetOff = quickPanelSheetRenderModel(stateOff, TestAppTextResolver(), strings)
            assertFalse(sheetOff.liveRow.isSelected)
        }

        @Test
        fun `settings page does not contain grid countdown or livePhoto controls`() {
            val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

            // Grid, Countdown, Live Photo removed from settings page (data class fields removed)
            // CommonSettingsSectionRenderModel has no gridMode field
            // PhotoSettingsSectionRenderModel has no livePhoto or countdown fields
            // Only remaining photo controls: defaultFilter, portraitLab, watermarkTemplate, liveSaveFormat
            assertNotNull(model.photoSection.defaultFilter)
            assertNotNull(model.photoSection.portraitLab)
            assertNotNull(model.photoSection.watermarkTemplate)
            assertNotNull(model.photoSection.liveSaveFormat)
        }

        @Test
        fun `quick panel retains grid timer live and watermark controls`() {
            val state = defaultSessionState()
            val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

            // Quick panel retains all dedup items
            assertNotNull(sheet.gridRow)
            assertEquals("Grid", sheet.gridRow.title)
            assertNotNull(sheet.timerRow)
            assertEquals("Timer", sheet.timerRow.title)
            assertNotNull(sheet.liveRow)
            assertEquals("Live", sheet.liveRow.title)
            assertNotNull(sheet.watermarkRow)
            assertEquals("Watermark", sheet.watermarkRow.title)
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

    companion object {
            private val strings = SessionUiStrings(
                buttonSwitchToFront = "Switch to Front",
                buttonSwitchToBack = "Switch to Back",
                buttonSingleLens = "Single Lens",
                buttonZoomPrefix = "Zoom",
                buttonZoomUnavailable = "Zoom N/A",
                buttonStillFast = "Still Fast",
                buttonStillMax = "Still Max",
                buttonStillQualityUnavailable = "Still N/A",
                buttonStill12Mp = "Still 12MP",
                buttonStill8Mp = "Still 8MP",
                buttonStill2Mp = "Still 2MP",
                buttonStillResolutionUnavailable = "Size N/A",
                outputErrorPrefix = "Camera issue:",
                outputVideoPrefix = "Last video:",
                outputLivePrefix = "Last Live photo:",
                outputSavedPrefix = "Last photo:",
                outputPreviewPrefix = "Preview thumbnail:",
                outputWaiting = "No photo captured yet."
            )
        }

}
