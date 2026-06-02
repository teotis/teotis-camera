package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterProfileShareCodec
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.MapFeatureCatalogStore
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.MapPersistedSettingsStore
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsSerializer
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.session.CameraSession
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionEffect
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionSettingsManagerTest {
    @Test
    fun `apply saves settings and dispatches settings updated intent`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )
        val settings = PersistedSettings(
            photo = PhotoSettings(
                defaultFilterProfileId = "photo-rich"
            ),
            video = VideoSettings(
                defaultVideoSpec = VideoSpec(
                    resolution = VideoResolution.HD_720P,
                    frameRate = VideoFrameRate.FPS_60,
                    audioProfile = AudioProfile.CONCERT
                )
            )
        )

        val result = manager.apply(settings)

        assertEquals(SessionSettingsApplyResult.Applied, result)
        assertEquals(settings, store.load())
        val intent = assertIs<SessionIntent.SettingsUpdated>(session.dispatched.single())
        assertEquals(settings, intent.snapshot.persisted)
    }

    @Test
    fun `apply action reduces current session settings before saving`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val result = manager.apply(
            PersistedSettingsAction.UpdatePhotoFilter("photo-rich")
        )

        assertEquals(SessionSettingsApplyResult.Applied, result)
        assertEquals("photo-rich", store.load().photo.defaultFilterProfileId)
    }

    @Test
    fun `apply catalog action persists manual draft and dispatches refreshed snapshot`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val result = manager.apply(
            FeatureCatalogAction.UpdateManualIso(320)
        )

        assertEquals(SessionSettingsApplyResult.Applied, result)
        assertEquals(320, manager.loadSnapshot().catalog.manualCaptureDraft.iso)
        val intent = assertIs<SessionIntent.SettingsUpdated>(session.dispatched.single())
        assertEquals(320, intent.snapshot.catalog.manualCaptureDraft.iso)
    }

    @Test
    fun `apply catalog saves full manual draft cycle through feature catalog store`() = runTest {
        val session = FakeCameraSession()
        val manager = SessionSettingsManager(
            session = session,
            store = MapPersistedSettingsStore(),
            catalogStore = MapFeatureCatalogStore()
        )

        manager.apply(
            FeatureCatalog(
                manualCaptureDraft = ManualCaptureParams(
                    rawEnabled = true,
                    iso = 640,
                    shutterSpeedMillis = 50L,
                    whiteBalanceKelvin = 5600
                )
            )
        )

        val reloaded = manager.loadSnapshot().catalog.manualCaptureDraft
        assertEquals(true, reloaded.rawEnabled)
        assertEquals(640, reloaded.iso)
        assertEquals(50L, reloaded.shutterSpeedMillis)
        assertEquals(5600, reloaded.whiteBalanceKelvin)
    }

    @Test
    fun `apply watermark template action persists updated photo default`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val result = manager.apply(
            PersistedSettingsAction.UpdatePhotoWatermarkTemplate("travel-polaroid")
        )

        assertEquals(SessionSettingsApplyResult.Applied, result)
        assertEquals("travel-polaroid", store.load().photo.defaultWatermarkTemplateId)
    }

    @Test
    fun `apply does not save while a shot is active`() = runTest {
        val activeShot = ShotRequest(
            shotId = "shot-42",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState().copy(activeShot = activeShot)
        )
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )
        val settings = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "photo-rich")
        )

        val result = manager.apply(settings)

        assertEquals(SessionSettingsApplyResult.BlockedByActiveShot, result)
        assertEquals(PersistedSettings(), store.load())
        assertTrue(session.dispatched.isEmpty())
    }

    @Test
    fun `apply does not save while countdown is active`() = runTest {
        val session = FakeCameraSession(
            initialState = defaultSessionState().copy(
                presentation = SessionPresentationState(
                    countdownRemainingSeconds = 2,
                    lastAction = "Photo capture starts in 2s"
                )
            )
        )
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val result = manager.apply(
            PersistedSettingsAction.UpdateVideoFilter("photo-rich")
        )

        assertEquals(SessionSettingsApplyResult.BlockedByActiveShot, result)
        assertEquals(PersistedSettings(), store.load())
        assertTrue(session.dispatched.isEmpty())
    }

    @Test
    fun `import filter profile persists catalog and dispatches refreshed snapshot`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )
        val sharedProfile = FilterProfileShareCodec.export(
            FilterProfile(
                id = "custom-amber-street",
                label = "Amber Street",
                category = FilterProfileCategory.CUSTOM,
                builtIn = false,
                renderSpec = FilterRenderSpec(
                    brightnessShift = 7,
                    contrast = 1.11f,
                    saturation = 0.93f,
                    warmthShift = 5,
                    vignetteStrength = 0.17f
                )
            )
        )

        val result = manager.importFilterProfile(sharedProfile)

        assertEquals(SessionSettingsApplyResult.Applied, result)
        val snapshot = manager.loadSnapshot()
        assertEquals("Amber Street", snapshot.catalog.filterProfileOrNull("custom-amber-street")?.label)
        val intent = assertIs<SessionIntent.SettingsUpdated>(session.dispatched.single())
        assertEquals(
            "Amber Street",
            intent.snapshot.catalog.filterProfileOrNull("custom-amber-street")?.label
        )
    }

    @Test
    fun `save current filter as custom persists catalog and switches family default`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val result = manager.saveCurrentFilterAsCustom(
            family = FilterLabFamily.PORTRAIT,
            sourceProfileId = "portrait-original"
        )

        assertEquals(SessionSettingsApplyResult.Applied, result)
        val snapshot = manager.loadSnapshot()
        val savedCustom = snapshot.catalog.filterProfiles.first { profile ->
            profile.id == "custom-portrait-original-1"
        }
        assertEquals("Portrait Original Custom 1", savedCustom.label)
        assertEquals(FilterProfileCategory.CUSTOM, savedCustom.category)
        assertEquals("custom-portrait-original-1", store.load().photo.defaultPortraitFilterProfileId)
        val intent = assertIs<SessionIntent.SettingsUpdated>(session.dispatched.single())
        assertEquals(
            "custom-portrait-original-1",
            intent.snapshot.persisted.photo.defaultPortraitFilterProfileId
        )
        assertEquals(
            "Portrait Original Custom 1",
            intent.snapshot.catalog.filterProfileOrNull("custom-portrait-original-1")?.label
        )
    }

    @Test
    fun `prepare filter for adjustment clones built in filter into editable custom default`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val editableId = manager.prepareFilterForAdjustment(
            family = FilterLabFamily.HUMANISTIC,
            sourceProfileId = "humanistic-street"
        )

        assertEquals("custom-street-1", editableId)
        assertEquals("custom-street-1", store.load().photo.defaultHumanisticFilterProfileId)
        assertEquals(
            "Street Custom 1",
            manager.loadSnapshot().catalog.filterProfileOrNull("custom-street-1")?.label
        )
    }

    @Test
    fun `update custom filter render spec rewrites catalog entry and dispatches snapshot`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore(
            PersistedSettingsSerializer.toMap(
                PersistedSettings(
                    photo = PhotoSettings(
                        defaultPortraitFilterProfileId = "custom-portrait-original-1"
                    )
                )
            ).toMutableMap()
        )
        val catalogStore = MapFeatureCatalogStore(
            baseCatalog = FeatureCatalog().withImportedFilterProfile(
                FilterProfile(
                    id = "custom-portrait-original-1",
                    label = "Portrait Original Custom 1",
                    category = FilterProfileCategory.CUSTOM,
                    builtIn = false,
                    renderSpec = FilterRenderSpec()
                )
            )
        )
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val result = manager.updateCustomFilterRenderSpec(
            filterProfileId = "custom-portrait-original-1",
            renderSpec = FilterRenderSpec(
                brightnessShift = 12,
                softGlowStrength = 0.2f,
                haloStrength = 0.1f,
                tintShift = -6,
                highlightCompression = 0.3f
            )
        )

        assertEquals(SessionSettingsApplyResult.Applied, result)
        val updated = manager.loadSnapshot().catalog.filterProfileOrNull("custom-portrait-original-1")
        assertEquals(12, updated?.renderSpec?.brightnessShift)
        assertEquals(0.2f, updated?.renderSpec?.softGlowStrength)
        assertEquals(0.1f, updated?.renderSpec?.haloStrength)
        assertEquals(-6, updated?.renderSpec?.tintShift)
        assertEquals(0.3f, updated?.renderSpec?.highlightCompression)
        val intent = assertIs<SessionIntent.SettingsUpdated>(session.dispatched.single())
        assertEquals(
            0.2f,
            intent.snapshot.catalog.filterProfileOrNull("custom-portrait-original-1")?.renderSpec?.softGlowStrength
        )
    }

    private class FakeCameraSession(
        initialState: SessionState = defaultSessionState()
    ) : CameraSession {
        override val state = MutableStateFlow(initialState)
        override val effects: Flow<SessionEffect> = emptyFlow()
        val dispatched = mutableListOf<SessionIntent>()

        override suspend fun dispatch(intent: SessionIntent) {
            dispatched += intent
        }
    }

    companion object {
        private fun defaultSessionState(): SessionState {
            return SessionState(
                lifecycle = SessionLifecycle.CREATED,
                permissionState = PermissionState(),
                previewHostAvailable = false,
                previewStatus = PreviewStatus.IDLE,
                previewStatusDetail = null,
                activeMode = ModeId.PHOTO,
                availableModes = listOf(ModeId.PHOTO),
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = RecordingStatus.IDLE,
                activeShot = null,
                modeSnapshot = ModeSnapshot(
                    id = ModeId.PHOTO,
                    uiSpec = ModeUiSpec(
                        title = "Photo",
                        shutterLabel = "Capture"
                    ),
                    state = ModeState(
                        headline = "Ready",
                        detail = "Ready"
                    )
                ),
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT,
                activeDeviceGraph = DeviceGraphSpec.stillCapture(
                    preferredLensFacing = LensFacing.BACK,
                    enablePreviewSnapshots = true
                ),
                previewMetrics = PreviewMetrics(),
                presentation = SessionPresentationState(
                    lastAction = "Ready"
                ),
                settings = SessionSettingsSnapshot(
                    catalog = FeatureCatalog()
                )
            )
        }
    }

    @Test
    fun `lens lab palette render spec is present in settings snapshot`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore(
            baseCatalog = FeatureCatalog().withImportedFilterProfile(
                FilterProfile(
                    id = "custom-photo-1",
                    label = "Photo Custom 1",
                    category = FilterProfileCategory.CUSTOM,
                    builtIn = false,
                    renderSpec = FilterRenderSpec()
                )
            )
        )
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        // Update the render spec with palette changes
        manager.updateCustomFilterRenderSpec(
            filterProfileId = "custom-photo-1",
            renderSpec = FilterRenderSpec(
                brightnessShift = 10,
                tintShift = -3,
                warmBoost = 0.15f
            )
        )

        // Verify the render spec is present in the settings snapshot
        val snapshot = manager.loadSnapshot()
        val profile = snapshot.catalog.filterProfileOrNull("custom-photo-1")
        assertNotNull(profile)
        assertEquals(10, profile?.renderSpec?.brightnessShift)
        assertEquals(-3, profile?.renderSpec?.tintShift)
        assertEquals(0.15f, profile?.renderSpec?.warmBoost)
    }

    @Test
    fun `apply UpdateColorLabSpec persists color lab axes and dispatches snapshot`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val spec = ColorLabSpec(colorAxis = 0.42f, toneAxis = -0.18f, strength = 0.8f)
        val result = manager.apply(PersistedSettingsAction.UpdateColorLabSpec(spec))

        assertEquals(SessionSettingsApplyResult.Applied, result)
        val saved = store.load()
        assertEquals(0.42f, saved.photo.colorLabSpec.colorAxis)
        assertEquals(-0.18f, saved.photo.colorLabSpec.toneAxis)
        assertEquals(0.8f, saved.photo.colorLabSpec.strength)
        val intent = assertIs<SessionIntent.SettingsUpdated>(session.dispatched.single())
        assertEquals(0.42f, intent.snapshot.persisted.photo.colorLabSpec.colorAxis)
        assertEquals(-0.18f, intent.snapshot.persisted.photo.colorLabSpec.toneAxis)
    }

    @Test
    fun `apply UpdateColorLabSpec normalizes out of range values`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        val catalogStore = MapFeatureCatalogStore()
        val manager = SessionSettingsManager(
            session = session,
            store = store,
            catalogStore = catalogStore
        )

        val spec = ColorLabSpec(colorAxis = 2.0f, toneAxis = -3.0f, strength = 5.0f)
        manager.apply(PersistedSettingsAction.UpdateColorLabSpec(spec))

        val saved = store.load()
        assertTrue(saved.photo.colorLabSpec.colorAxis in -1f..1f,
            "colorAxis should be normalized to [-1,1]: ${saved.photo.colorLabSpec.colorAxis}")
        assertTrue(saved.photo.colorLabSpec.toneAxis in -1f..1f,
            "toneAxis should be normalized to [-1,1]: ${saved.photo.colorLabSpec.toneAxis}")
        assertTrue(saved.photo.colorLabSpec.strength in 0f..1f,
            "strength should be normalized to [0,1]: ${saved.photo.colorLabSpec.strength}")
    }

    @Test
    fun `resetToDefaults settings restores common settings and persists`() = runTest {
        val modifiedState = defaultSessionState().copy(
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = CommonSettings(
                        gridMode = com.opencamera.core.settings.CompositionGridMode.GOLDEN_RATIO,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    )
                )
            )
        )
        val session = FakeCameraSession(initialState = modifiedState)
        val store = MapPersistedSettingsStore()
        val manager = SessionSettingsManager(session = session, store = store)

        val result = manager.resetToDefaults(ResetTarget.SETTINGS)
        assertIs<SessionSettingsApplyResult.Applied>(result)
        val saved = store.load()
        assertEquals(PersistedSettings().common, saved.common)
    }

    @Test
    fun `resetToDefaults color lab restores default spec`() = runTest {
        val modifiedState = defaultSessionState().copy(
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        colorLabSpec = ColorLabSpec(colorAxis = 0.5f, toneAxis = -0.3f, strength = 0.7f)
                    )
                )
            )
        )
        val session = FakeCameraSession(initialState = modifiedState)
        val store = MapPersistedSettingsStore()
        val manager = SessionSettingsManager(session = session, store = store)

        val result = manager.resetToDefaults(ResetTarget.COLOR_LAB)
        assertIs<SessionSettingsApplyResult.Applied>(result)
        val saved = store.load()
        assertEquals(PersistedSettings().photo.colorLabSpec, saved.photo.colorLabSpec)
    }

    @Test
    fun `resetToDefaults returns NoOp when already at defaults`() = runTest {
        val session = FakeCameraSession()
        val store = MapPersistedSettingsStore()
        store.save(PersistedSettings())
        val manager = SessionSettingsManager(session = session, store = store)

        val result = manager.resetToDefaults(ResetTarget.SETTINGS)
        assertIs<SessionSettingsApplyResult.NoOp>(result)
    }

    @Test
    fun `resetToDefaults blocked during active shot`() = runTest {
        val activeShot = ShotRequest(
            shotId = "test",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState().copy(activeShot = activeShot)
        )
        val store = MapPersistedSettingsStore()
        val manager = SessionSettingsManager(session = session, store = store)

        val result = manager.resetToDefaults(ResetTarget.SETTINGS)
        assertIs<SessionSettingsApplyResult.BlockedByActiveShot>(result)
    }
}
