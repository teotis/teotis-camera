package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
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
import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
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

class RuntimeProControlsRenderModelTest {


        @Test
        fun `runtime pro controls render model exposes editable controls for active pro variant`() {
            val baseline = defaultSessionState()
            val state = baseline.copy(
                activeMode = ModeId.HUMANISTIC,
                modeSnapshot = baseline.modeSnapshot.copy(
                    id = ModeId.HUMANISTIC,
                    state = baseline.modeSnapshot.state.copy(
                        isProVariantActive = true
                    )
                ),
                settings = baseline.settings.copy(
                    catalog = baseline.settings.catalog.copy(
                        manualCaptureDraft = com.opencamera.core.settings.ManualCaptureParams(
                            rawEnabled = true,
                            iso = 320,
                            shutterSpeedMillis = 33L,
                            whiteBalanceKelvin = 4800
                        )
                    )
                )
            )

            val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

            assertTrue(model.isVisible)
            assertEquals("Humanistic Professional Controls", model.headline)
            assertEquals(SettingsControlAvailability.DEGRADED, model.rawControl.availability)
            assertEquals(SettingsControlAvailability.SUPPORTED, model.isoControl.availability)
            assertEquals("320", model.isoControl.value)
            assertEquals("33ms", model.shutterControl.value)
            assertEquals("4800K", model.whiteBalanceControl.value)
            assertTrue(model.summary.contains("RAW On | ISO 320 | Shutter 33ms | WB 4800K"))
            assertTrue(model.summary.contains("stay saved-only"))
            assertTrue(model.isoControl.nextAction != null)
        }



        @Test
        fun `raw control isToggleOn reflects draft rawEnabled when raw is applicable`() {
            val baseline = defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    manualControlCapabilities = ManualControlCapabilityMatrix(
                        raw = ManualControlSupport.APPLY,
                        iso = ManualControlSupport.APPLY,
                        shutter = ManualControlSupport.APPLY,
                        exposureCompensation = ManualControlSupport.APPLY,
                        focusDistance = ManualControlSupport.APPLY,
                        aperture = ManualControlSupport.APPLY,
                        whiteBalance = ManualControlSupport.APPLY
                    )
                )
            )
            val stateOff = baseline.copy(
                activeMode = ModeId.HUMANISTIC,
                modeSnapshot = baseline.modeSnapshot.copy(
                    id = ModeId.HUMANISTIC,
                    state = baseline.modeSnapshot.state.copy(isProVariantActive = true)
                )
            )

            val modelOff = runtimeProControlsRenderModel(stateOff, TestAppTextResolver())

            assertEquals(false, modelOff.rawControl.isToggleOn)
            assertNull(modelOff.isoControl.isToggleOn)

            val stateOn = stateOff.copy(
                settings = stateOff.settings.copy(
                    catalog = stateOff.settings.catalog.copy(
                        manualCaptureDraft = stateOff.settings.catalog.manualCaptureDraft.copy(
                            rawEnabled = true
                        )
                    )
                )
            )

            val modelOn = runtimeProControlsRenderModel(stateOn, TestAppTextResolver())

            assertEquals(true, modelOn.rawControl.isToggleOn)
        }

        @Test
        fun `raw control isToggleOn is null when raw is saved only`() {
            val baseline = defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    manualControlCapabilities = ManualControlCapabilityMatrix(
                        raw = ManualControlSupport.SAVED_ONLY,
                        iso = ManualControlSupport.APPLY,
                        shutter = ManualControlSupport.APPLY,
                        exposureCompensation = ManualControlSupport.APPLY,
                        focusDistance = ManualControlSupport.APPLY,
                        aperture = ManualControlSupport.APPLY,
                        whiteBalance = ManualControlSupport.APPLY
                    )
                )
            )
            val state = baseline.copy(
                activeMode = ModeId.HUMANISTIC,
                modeSnapshot = baseline.modeSnapshot.copy(
                    id = ModeId.HUMANISTIC,
                    state = baseline.modeSnapshot.state.copy(isProVariantActive = true)
                )
            )

            val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

            assertNull(model.rawControl.isToggleOn)
        }

        @Test
        fun `runtime pro controls degrade to saved only on devices without manual support`() {
            val baseline = defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsManualControls = false
                )
            )
            val state = baseline.copy(
                activeMode = ModeId.HUMANISTIC,
                modeSnapshot = baseline.modeSnapshot.copy(
                    id = ModeId.HUMANISTIC,
                    state = baseline.modeSnapshot.state.copy(
                        isProVariantActive = true
                    )
                )
            )

            val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

            assertTrue(model.isVisible)
            assertEquals(SettingsControlAvailability.DEGRADED, model.isoControl.availability)
            assertEquals("Saved only", model.isoControl.supportLabel)
            assertTrue(model.supportingText.contains("saved-only or temporarily-unsupported"))
            assertTrue(model.summary.contains("stay saved-only"))
        }



        @Test
        fun `runtime pro controls surface unsupported controls from capability matrix`() {
            val baseline = defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    manualControlCapabilities = ManualControlCapabilityMatrix(
                        raw = ManualControlSupport.SAVED_ONLY,
                        iso = ManualControlSupport.APPLY,
                        shutter = ManualControlSupport.APPLY,
                        exposureCompensation = ManualControlSupport.APPLY,
                        focusDistance = ManualControlSupport.APPLY,
                        aperture = ManualControlSupport.UNSUPPORTED,
                        whiteBalance = ManualControlSupport.UNSUPPORTED
                    )
                )
            )
            val state = baseline.copy(
                activeMode = ModeId.CHECK_IN,
                modeSnapshot = baseline.modeSnapshot.copy(
                    id = ModeId.CHECK_IN,
                    state = baseline.modeSnapshot.state.copy(
                        isProVariantActive = true
                    )
                )
            )

            val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

            assertEquals(SettingsControlAvailability.UNSUPPORTED, model.apertureControl.availability)
            assertEquals("Temporarily unsupported", model.apertureControl.supportLabel)
            assertEquals(
                SettingsControlAvailability.UNSUPPORTED,
                model.whiteBalanceControl.availability
            )
            assertTrue(model.summary.contains("Aperture / WB temporarily unsupported"))
        }

        @Test
        fun `runtime pro controls wrap nullable option controls back to auto after max value`() {
            val baseline = defaultSessionState()
            val state = baseline.copy(
                activeMode = ModeId.HUMANISTIC,
                modeSnapshot = baseline.modeSnapshot.copy(
                    id = ModeId.HUMANISTIC,
                    state = baseline.modeSnapshot.state.copy(
                        isProVariantActive = true
                    )
                ),
                settings = baseline.settings.copy(
                    catalog = baseline.settings.catalog.copy(
                        manualCaptureDraft = ManualCaptureParams(
                            iso = 1600,
                            shutterSpeedMillis = 500L,
                            exposureCompensationSteps = 2,
                            focusDistanceDiopters = 4.0f,
                            apertureFNumber = 4.0f,
                            whiteBalanceKelvin = 6500
                        )
                    )
                )
            )

            val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

            assertEquals(FeatureCatalogAction.UpdateManualIso(null), model.isoControl.nextAction)
            assertEquals(
                FeatureCatalogAction.UpdateManualShutterSpeedMillis(null),
                model.shutterControl.nextAction
            )
            assertEquals(
                FeatureCatalogAction.UpdateManualExposureCompensationSteps(null),
                model.exposureControl.nextAction
            )
            assertEquals(
                FeatureCatalogAction.UpdateManualFocusDistanceDiopters(null),
                model.focusControl.nextAction
            )
            assertEquals(
                FeatureCatalogAction.UpdateManualApertureFNumber(null),
                model.apertureControl.nextAction
            )
            assertEquals(
                FeatureCatalogAction.UpdateManualWhiteBalanceKelvin(null),
                model.whiteBalanceControl.nextAction
            )
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
