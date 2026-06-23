/**
 * Settings control contract test
 *
 * 覆盖行为:
 * - 对每个 visible 设置控件: isInteractive == true ⟺ nextAction != null && availability != UNSUPPORTED && enabled
 * - 对每个 non-interactive 控件: statusText 非空（可见原因）
 * - photoLive / photoTimer / liveSaveFormat 至少各覆盖 supported 与 unsupported 两态
 * - photoPortraitLab / photoWatermark 仍由 Activity 单独接线（nextAction == null），但 statusText 必须非空
 *
 * 不适合单测的行为:
 * - binder 真实注册 click listener —— 由 SettingsPanelRendererTest 用 Robolectric 验证
 */
package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.RecordingStatus
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsControlContractTest {

    @Test
    fun `photoLive is interactive with toggle action when still capture is supported`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        val control = model.photoSection.photoLive

        assertEquals(SettingsControlAvailability.SUPPORTED, control.availability)
        assertTrue(control.isInteractive)
        assertNotNull(control.nextAction)
        val action = control.nextAction
        assertTrue(action is PersistedSettingsAction.UpdateLivePhotoDefault)
        // 默认 state 中 livePhotoEnabledByDefault = true，所以下一个值应为 false
        assertEquals(false, (action as PersistedSettingsAction.UpdateLivePhotoDefault).enabled)
        assertEquals("", control.statusText)
    }

    @Test
    fun `photoLive degrades honestly with statusText when still capture is unsupported`() {
        val model = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsStillCapture = false
                )
            ),
            TestAppTextResolver()
        )
        val control = model.photoSection.photoLive

        assertEquals(SettingsControlAvailability.UNSUPPORTED, control.availability)
        assertFalse(control.isInteractive)
        assertNull(control.nextAction)
        assertTrue(control.statusText.isNotEmpty())
    }

    @Test
    fun `photoTimer is interactive with cycle action when still capture is supported`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        val control = model.photoSection.photoTimer

        assertEquals(SettingsControlAvailability.SUPPORTED, control.availability)
        assertTrue(control.isInteractive)
        assertNotNull(control.nextAction)
        val action = control.nextAction
        assertTrue(action is PersistedSettingsAction.UpdateCountdownDuration)
        // 默认 countdownDuration = SECONDS_3，cycle 应前进到非 SECONDS_3 值
        val next = (action as PersistedSettingsAction.UpdateCountdownDuration).countdownDuration
        assertTrue(next != CountdownDuration.SECONDS_3)
    }

    @Test
    fun `photoTimer degrades honestly with statusText when still capture is unsupported`() {
        val model = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsStillCapture = false
                )
            ),
            TestAppTextResolver()
        )
        val control = model.photoSection.photoTimer

        assertEquals(SettingsControlAvailability.UNSUPPORTED, control.availability)
        assertFalse(control.isInteractive)
        assertNull(control.nextAction)
        assertTrue(control.statusText.isNotEmpty())
    }

    @Test
    fun `liveSaveFormat baseline stays interactive when supported`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        val control = model.photoSection.liveSaveFormat

        assertEquals(SettingsControlAvailability.SUPPORTED, control.availability)
        assertTrue(control.isInteractive)
        assertNotNull(control.nextAction)
    }

    @Test
    fun `portrait lab and watermark entries stay Activity-owned with non-empty statusText`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        // portraitLab 由 Activity 单独打开页面，nextAction 必须为 null
        val portraitLab = model.photoSection.portraitLab
        assertNull(portraitLab.nextAction)
        assertFalse(portraitLab.isInteractive)
        assertTrue(portraitLab.statusText.isNotEmpty())

        // watermarkTemplate 同上
        val watermark = model.photoSection.watermarkTemplate
        assertNull(watermark.nextAction)
        assertFalse(watermark.isInteractive)
        assertTrue(watermark.statusText.isNotEmpty())
    }

    @Test
    fun `every visible photo section control satisfies interactive invariant in supported state`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        val controls = listOf(
            "defaultFilter" to model.photoSection.defaultFilter,
            "portraitLab" to model.photoSection.portraitLab,
            "watermarkTemplate" to model.photoSection.watermarkTemplate,
            "photoLive" to model.photoSection.photoLive,
            "liveSaveFormat" to model.photoSection.liveSaveFormat,
            "photoTimer" to model.photoSection.photoTimer
        )
        for ((name, control) in controls) {
            val expectedInteractive = control.nextAction != null &&
                control.availability != SettingsControlAvailability.UNSUPPORTED &&
                control.enabled
            assertEquals(
                "$name: isInteractive must equal (nextAction != null && supported && enabled)",
                expectedInteractive,
                control.isInteractive
            )
            if (!control.isInteractive) {
                assertTrue(
                    "$name: non-interactive control must expose non-empty statusText, got '${control.statusText}'",
                    control.statusText.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun `every visible photo section control degrades honestly when still capture is unsupported`() {
        val model = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsStillCapture = false
                )
            ),
            TestAppTextResolver()
        )
        val controls = listOf(
            "defaultFilter" to model.photoSection.defaultFilter,
            "portraitLab" to model.photoSection.portraitLab,
            "watermarkTemplate" to model.photoSection.watermarkTemplate,
            "photoLive" to model.photoSection.photoLive,
            "liveSaveFormat" to model.photoSection.liveSaveFormat,
            "photoTimer" to model.photoSection.photoTimer
        )
        for ((name, control) in controls) {
            assertEquals(
                "$name: when still capture is unsupported, availability must be UNSUPPORTED",
                SettingsControlAvailability.UNSUPPORTED,
                control.availability
            )
            assertFalse("$name: must not be interactive when unsupported", control.isInteractive)
            assertNull("$name: nextAction must be null when unsupported", control.nextAction)
            assertTrue(
                "$name: non-interactive control must expose non-empty statusText, got '${control.statusText}'",
                control.statusText.isNotEmpty()
            )
        }
    }

    @Test
    fun `common and video section controls also satisfy interactive invariant`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        val controls = listOf(
            "language" to model.commonSection.languageControl,
            "shutterSound" to model.commonSection.shutterSound,
            "selfieMirror" to model.commonSection.selfieMirror,
            "videoResolution" to model.videoSection.resolution,
            "videoFrameRate" to model.videoSection.frameRate,
            "videoDynamicFps" to model.videoSection.dynamicFps,
            "videoAudio" to model.videoSection.audioProfile,
            "videoFilter" to model.videoSection.defaultFilter
        )
        for ((name, control) in controls) {
            val expectedInteractive = control.nextAction != null &&
                control.availability != SettingsControlAvailability.UNSUPPORTED &&
                control.enabled
            assertEquals(
                "$name: isInteractive invariant",
                expectedInteractive,
                control.isInteractive
            )
            if (!control.isInteractive) {
                assertTrue(
                    "$name: non-interactive control must expose non-empty statusText, got '${control.statusText}'",
                    control.statusText.isNotEmpty()
                )
            }
        }
    }

    private fun defaultSessionState(
        activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
    ): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = com.opencamera.core.session.PermissionState(
                cameraGranted = true,
                microphoneGranted = true
            ),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
                state = ModeState(headline = "PHOTO mode active", detail = "Ready")
            ),
            activeDeviceCapabilities = activeDeviceCapabilities,
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = CommonSettings(
                        gridMode = CompositionGridMode.RULE_OF_THIRDS,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    ),
                    photo = PhotoSettings(
                        defaultFilterProfileId = "portrait-retro",
                        defaultHumanisticFilterProfileId = "humanistic-street",
                        defaultPortraitFilterProfileId = "portrait-original",
                        defaultWatermarkTemplateId = "travel-polaroid",
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
            ),
            presentation = SessionPresentationState(
                lastAction = "Ready",
                latestCapturePath = null,
                latestVideoPath = null,
                latestLivePhotoBundle = null,
                latestSavedMediaType = null,
                latestPipelineNotes = emptyList(),
                lastError = null
            )
        )
    }
}
