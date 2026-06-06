package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeCatalogContractsTest {
    @Test
    fun `mode directory declaration resolves productized labels from shared settings snapshot`() {
        val settingsSnapshot = SessionSettingsSnapshot(
            persisted = PersistedSettings(
                photo = PhotoSettings(
                    defaultFilterProfileId = "portrait-retro",
                    livePhotoEnabledByDefault = true,
                    countdownDuration = CountdownDuration.SECONDS_3
                ),
                video = VideoSettings(
                    defaultVideoSpec = VideoSpec()
                )
            )
        )

        val photoDeclaration = ModeId.PHOTO.modeDirectoryDeclaration(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            settingsSnapshot = settingsSnapshot
        )
        val humanisticDeclaration = ModeId.HUMANISTIC.modeDirectoryDeclaration(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            settingsSnapshot = settingsSnapshot
        )
        val checkinDeclaration = ModeId.CHECK_IN.modeDirectoryDeclaration(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            settingsSnapshot = settingsSnapshot
        )
        val videoDeclaration = ModeId.VIDEO.modeDirectoryDeclaration(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            settingsSnapshot = settingsSnapshot
        )

        assertEquals("Photo", photoDeclaration.catalogProfile.displayName)
        assertEquals("Portrait Retro", photoDeclaration.defaultStyleLabel)
        assertEquals(
            "Flash, frame ratio, Live default, watermark",
            photoDeclaration.declaredSubfeatures
        )
        assertEquals("Humanistic Original", humanisticDeclaration.defaultStyleLabel)
        assertEquals(
            "Street styles, manual controls, frame ratio, Live default, timer, watermark",
            humanisticDeclaration.declaredSubfeatures
        )
        assertEquals("Check-in Original", checkinDeclaration.defaultStyleLabel)
        assertEquals(
            "Portrait depth, multi-frame focus bracket, filter, watermark, frame ratio",
            checkinDeclaration.declaredSubfeatures
        )
        assertEquals("4K 25fps", videoDeclaration.defaultStyleLabel)
    }

    @Test
    fun `checkin declaration degrades portrait and multi-frame subfeatures with capabilities`() {
        val settingsSnapshot = SessionSettingsSnapshot()
        val degradedCapabilities = DeviceCapabilities.DEFAULT.copy(
            supportsNightMultiFrame = false,
            supportsPortraitDepthEffect = false
        )

        val checkinDeclaration = ModeId.CHECK_IN.modeDirectoryDeclaration(
            deviceCapabilities = degradedCapabilities,
            settingsSnapshot = settingsSnapshot
        )

        assertEquals("Check-in Original", checkinDeclaration.defaultStyleLabel)
        assertEquals(
            "Focus fallback, multi-frame focus bracket, filter, watermark, frame ratio",
            checkinDeclaration.declaredSubfeatures
        )
    }

    @Test
    fun `canonical visible modes are photo checkin humanistic document video`() {
        val visibleIds = ModeId.entries
        assertEquals(5, visibleIds.size)
        assertEquals(
            listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.DOCUMENT, ModeId.HUMANISTIC, ModeId.VIDEO),
            visibleIds.sortedBy { it.ordinal }
        )
    }
}
