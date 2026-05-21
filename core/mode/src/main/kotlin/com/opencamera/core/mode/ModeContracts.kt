package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.flow.StateFlow

enum class ModeId {
    PHOTO,
    DOCUMENT,
    HUMANISTIC,
    PORTRAIT,
    PRO,
    NIGHT,
    VIDEO
}

data class ModeCatalogProfile(
    val displayName: String,
    val buttonLabel: String
)

fun ModeId.catalogProfile(): ModeCatalogProfile {
    return when (this) {
        ModeId.PHOTO -> ModeCatalogProfile(
            displayName = "Photo",
            buttonLabel = "Photo"
        )
        ModeId.DOCUMENT -> ModeCatalogProfile(
            displayName = "Document",
            buttonLabel = "Doc"
        )
        ModeId.HUMANISTIC -> ModeCatalogProfile(
            displayName = "Humanistic",
            buttonLabel = "Human"
        )
        ModeId.PORTRAIT -> ModeCatalogProfile(
            displayName = "Portrait",
            buttonLabel = "Portrait"
        )
        ModeId.PRO -> ModeCatalogProfile(
            displayName = "Pro",
            buttonLabel = "Pro"
        )
        ModeId.NIGHT -> ModeCatalogProfile(
            displayName = "Scenery",
            buttonLabel = "Scenery"
        )
        ModeId.VIDEO -> ModeCatalogProfile(
            displayName = "Video",
            buttonLabel = "Video"
        )
    }
}

data class ModeContext(
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    val initialLensFacing: LensFacing = LensFacing.BACK,
    val initialStillCaptureQuality: StillCaptureQualityPreference =
        StillCaptureQualityPreference.LATENCY,
    val initialStillCaptureResolutionPreset: StillCaptureResolutionPreset =
        StillCaptureResolutionPreset.LARGE_12MP,
    val runtimeState: () -> ModeRuntimeState = {
        ModeRuntimeState(
            deviceCapabilities = deviceCapabilities,
            lensFacing = initialLensFacing,
            stillCaptureQuality = initialStillCaptureQuality,
            stillCaptureResolutionPreset = initialStillCaptureResolutionPreset
        )
    },
    val eventSink: suspend (String) -> Unit = {},
    val onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
    private val settingsSnapshotProvider: () -> SessionSettingsSnapshot = { SessionSettingsSnapshot() }
) {
    val settingsSnapshot: SessionSettingsSnapshot get() = settingsSnapshotProvider()
}

fun ModeContext.captureAidMetadataTags(): Map<String, String> {
    val persisted = settingsSnapshot.persisted
    val lensFacing = runtimeState().lensFacing
    val selfieMirrorEnabled = persisted.common.selfieMirrorEnabled
    return mapOf(
        "captureLensFacing" to lensFacing.name.lowercase(),
        "selfieMirrorEnabled" to if (selfieMirrorEnabled) "on" else "off",
        "selfieMirrorApply" to (
            lensFacing == LensFacing.FRONT &&
                selfieMirrorEnabled
            ).toString(),
        "shutterSoundEnabled" to if (persisted.common.shutterSoundEnabled) "on" else "off"
    )
}

data class ModeRuntimeState(
    val deviceCapabilities: DeviceCapabilities,
    val lensFacing: LensFacing,
    val stillCaptureQuality: StillCaptureQualityPreference,
    val stillCaptureResolutionPreset: StillCaptureResolutionPreset
)

data class ModeUiSpec(
    val title: String,
    val shutterLabel: String,
    val secondaryActionLabel: String? = null,
    val tertiaryActionLabel: String? = null,
    val proActionLabel: String? = null
)

data class ModeState(
    val headline: String,
    val detail: String,
    val isShutterEnabled: Boolean = true,
    val isSecondaryActionEnabled: Boolean = true,
    val isTertiaryActionEnabled: Boolean = false,
    val isProActionEnabled: Boolean = false,
    val isProVariantActive: Boolean = false
)

data class ModeSnapshot(
    val id: ModeId,
    val uiSpec: ModeUiSpec,
    val state: ModeState
)

sealed interface ModeIntent {
    data object ShutterPressed : ModeIntent
    data object SecondaryActionPressed : ModeIntent
    data object TertiaryActionPressed : ModeIntent
    data object ProActionPressed : ModeIntent
}

sealed interface ModeSessionEvent {
    data class ShotStarted(val shot: ShotRequest) : ModeSessionEvent
    data class ShotCompleted(val result: ShotResult) : ModeSessionEvent
    data class ShotFailed(
        val shotId: String,
        val mediaType: MediaType,
        val reason: String
    ) : ModeSessionEvent
}

sealed interface ModeSignal {
    data object None : ModeSignal
    data class SubmitCapture(
        val strategy: CaptureStrategy,
        val countdownSeconds: Int = 0
    ) : ModeSignal
    data object StopActiveCapture : ModeSignal
    data class ShowHint(val message: String) : ModeSignal
}

interface ModeController {
    val id: ModeId
    val snapshot: StateFlow<ModeSnapshot>

    fun deviceGraph(): DeviceGraphSpec

    suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) = Unit

    suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) = Unit

    suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) = Unit

    suspend fun onEnter()

    suspend fun onExit()

    suspend fun handle(intent: ModeIntent): ModeSignal

    suspend fun onSessionEvent(event: ModeSessionEvent) = Unit
}

interface CameraModePlugin {
    val id: ModeId

    fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean

    fun create(context: ModeContext): ModeController
}

class ModeRegistry(plugins: List<CameraModePlugin>) {
    private val pluginsById = plugins.associateBy { it.id }

    val availableModes: List<ModeId> = pluginsById.keys.sortedBy { it.ordinal }

    fun supportedModes(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
    ): List<ModeId> {
        return availableModes.filter { modeId ->
            pluginsById.getValue(modeId).isSupported(deviceCapabilities)
        }
    }

    fun createController(
        modeId: ModeId,
        context: ModeContext = ModeContext()
    ): ModeController {
        val plugin = pluginsById[modeId]
            ?: error("No mode plugin registered for $modeId")
        check(plugin.isSupported(context.deviceCapabilities)) {
            "Mode $modeId is not supported by $context"
        }
        return plugin.create(context)
    }
}

val FlashMode.label: String
    get() = when (this) {
        FlashMode.OFF -> "Off"
        FlashMode.AUTO -> "Auto"
        FlashMode.ON -> "On"
    }

fun FrameRatio.eventTag(): String = tagValue.replace(':', 'x')
