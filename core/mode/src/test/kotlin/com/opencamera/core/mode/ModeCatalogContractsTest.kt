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
        assertEquals("Humanistic Street", humanisticDeclaration.defaultStyleLabel)
        assertEquals(
            "Street styles, Pro variant, frame ratio, Live default, timer, watermark",
            humanisticDeclaration.declaredSubfeatures
        )
        assertEquals("4K 25fps", videoDeclaration.defaultStyleLabel)
    }

    @Test
    fun `mode directory declaration degrades scenery and portrait subfeatures with capabilities`() {
        val settingsSnapshot = SessionSettingsSnapshot()
        val degradedCapabilities = DeviceCapabilities.DEFAULT.copy(
            supportsNightMultiFrame = false,
            supportsPortraitDepthEffect = false
        )

        val sceneryDeclaration = ModeId.NIGHT.modeDirectoryDeclaration(
            deviceCapabilities = degradedCapabilities,
            settingsSnapshot = settingsSnapshot
        )
        val portraitDeclaration = ModeId.PORTRAIT.modeDirectoryDeclaration(
            deviceCapabilities = degradedCapabilities,
            settingsSnapshot = settingsSnapshot
        )

        assertEquals("Balanced", sceneryDeclaration.defaultStyleLabel)
        assertEquals(
            "Scenery style, Pro variant, brightening fallback, frame ratio",
            sceneryDeclaration.declaredSubfeatures
        )
        assertEquals("Portrait Original", portraitDeclaration.defaultStyleLabel)
        assertEquals(
            "Portrait style, Pro variant, focus fallback, frame ratio",
            portraitDeclaration.declaredSubfeatures
        )
    }
}
