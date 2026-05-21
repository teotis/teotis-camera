package com.opencamera.core.session

import com.opencamera.core.device.CapabilityGraphReport
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.CaptureFeedbackPreview
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SessionLifecycle {
    CREATED,
    RUNNING,
    STOPPED
}

enum class PreviewStatus {
    IDLE,
    STARTING,
    ACTIVE,
    RECOVERING,
    SURFACE_LOST,
    BLOCKED,
    ERROR
}

enum class CaptureStatus {
    IDLE,
    REQUESTED,
    SAVING,
    COMPLETED,
    FAILED
}

enum class RecordingStatus {
    IDLE,
    REQUESTING,
    RECORDING,
    STOPPING
}

enum class SavedMediaType {
    PHOTO,
    VIDEO
}

enum class PreviewRatio(val tagValue: String, val label: String) {
    FULL("full", "Full"),
    RATIO_4_3("4:3", "4:3"),
    RATIO_16_9("16:9", "16:9"),
    RATIO_1_1("1:1", "1:1");

    companion object {
        fun fromTag(value: String?): PreviewRatio? =
            entries.firstOrNull { it.tagValue == value }
    }
}

data class PermissionState(
    val cameraGranted: Boolean = false,
    val microphoneGranted: Boolean = false
)

data class PreviewMetrics(
    val bindCount: Int = 0,
    val recoveryCount: Int = 0,
    val lastFirstFrameLatencyMillis: Long? = null,
    val bestFirstFrameLatencyMillis: Long? = null,
    val worstFirstFrameLatencyMillis: Long? = null,
    val lastStartReason: String? = null
)

data class SessionPresentationState(
    val countdownRemainingSeconds: Int? = null,
    val previewThumbnailPath: String? = null,
    val latestThumbnailSource: ThumbnailSource? = null,
    val pendingCaptureFeedback: CaptureFeedbackPreview? = null,
    val previewSnapshotGeneration: Int = 0,
    val lastAction: String = "",
    val latestCapturePath: String? = null,
    val latestVideoPath: String? = null,
    val latestLivePhotoBundle: LivePhotoBundle? = null,
    val latestSavedMediaType: SavedMediaType? = null,
    val latestPipelineNotes: List<String> = emptyList(),
    val lastError: String? = null
)

data class SessionState(
    val lifecycle: SessionLifecycle,
    val permissionState: PermissionState,
    val previewHostAvailable: Boolean,
    val previewStatus: PreviewStatus,
    val previewStatusDetail: String?,
    val activeMode: ModeId,
    val availableModes: List<ModeId>,
    val captureStatus: CaptureStatus,
    val recordingStatus: RecordingStatus,
    val activeShot: ShotRequest?,
    val modeSnapshot: ModeSnapshot,
    val activeDeviceCapabilities: DeviceCapabilities,
    val activeDeviceGraph: DeviceGraphSpec,
    val previewMetrics: PreviewMetrics,
    val settings: SessionSettingsSnapshot = SessionSettingsSnapshot(),
    val activeEffectSpec: EffectSpec = EffectSpec.EMPTY,
    val activeCapabilityReport: CapabilityGraphReport? = null,
    val previewRatio: PreviewRatio = PreviewRatio.FULL,
    val presentation: SessionPresentationState = SessionPresentationState()
) {
    val countdownRemainingSeconds: Int?
        get() = presentation.countdownRemainingSeconds

    val previewThumbnailPath: String?
        get() = presentation.previewThumbnailPath

    val latestThumbnailSource: ThumbnailSource?
        get() = presentation.latestThumbnailSource

    val lastAction: String
        get() = presentation.lastAction

    val latestCapturePath: String?
        get() = presentation.latestCapturePath

    val latestVideoPath: String?
        get() = presentation.latestVideoPath

    val latestLivePhotoBundle: LivePhotoBundle?
        get() = presentation.latestLivePhotoBundle

    val latestSavedMediaType: SavedMediaType?
        get() = presentation.latestSavedMediaType

    val latestPipelineNotes: List<String>
        get() = presentation.latestPipelineNotes

    val capabilityPipelineNotes: List<String>
        get() = activeCapabilityReport?.pipelineNotes ?: emptyList()

    val lastError: String?
        get() = presentation.lastError
}

sealed interface SessionIntent {
    data object Boot : SessionIntent
    data object Shutdown : SessionIntent
    data class SettingsUpdated(val snapshot: SessionSettingsSnapshot) : SessionIntent
    data class SwitchMode(val modeId: ModeId) : SessionIntent
    data object ShutterPressed : SessionIntent
    data object SecondaryActionPressed : SessionIntent
    data object TertiaryActionPressed : SessionIntent
    data object ProActionPressed : SessionIntent
    data class CountdownTick(val remainingSeconds: Int) : SessionIntent
    data object CountdownCompleted : SessionIntent
    data object LensFacingToggled : SessionIntent
    data object ZoomRatioToggled : SessionIntent
    data class ApplyZoomRatio(val ratio: Float) : SessionIntent
    data object StillCaptureQualityToggled : SessionIntent
    data object StillCaptureResolutionToggled : SessionIntent
    data object PreviewRatioToggled : SessionIntent
    data class FrameRatioSelected(val ratio: FrameRatio) : SessionIntent
    data class DeviceCapabilitiesUpdated(val capabilities: DeviceCapabilities) : SessionIntent
    data class PermissionsUpdated(
        val cameraGranted: Boolean,
        val microphoneGranted: Boolean
    ) : SessionIntent
    data object PreviewHostAttached : SessionIntent
    data class PreviewHostDetached(val reason: String) : SessionIntent
    data class PreviewBindingStarted(
        val reason: String,
        val isRecovery: Boolean
    ) : SessionIntent
    data class PreviewFirstFrameAvailable(val firstFrameLatencyMillis: Long) : SessionIntent
    data class PreviewSnapshotUpdated(
        val source: ThumbnailSource,
        val generation: Int = 0
    ) : SessionIntent
    data class CaptureFeedbackSnapshotUpdated(
        val shotId: String,
        val outputPath: String
    ) : SessionIntent
    data class PreviewSurfaceLost(val reason: String) : SessionIntent
    data class PreviewError(val reason: String) : SessionIntent
    data class PreviewRuntimeIssue(val issue: DeviceRuntimeIssue) : SessionIntent
    data class PreviewStopped(val reason: String) : SessionIntent
    data class ShotStarted(val shot: ShotRequest) : SessionIntent
    data class ShotCompleted(val result: ShotResult) : SessionIntent
    data class ShotFailed(
        val shotId: String,
        val mediaType: MediaType,
        val reason: String
    ) : SessionIntent
    data class ThermalStateChanged(val thermalState: CameraThermalState) : SessionIntent
    data class PerformanceClassChanged(val performanceClass: CameraPerformanceClass) : SessionIntent
}

sealed interface SessionEffect {
    data class ExecuteShot(val plan: ShotPlan) : SessionEffect
    data class StopActiveShot(val shotId: String) : SessionEffect
    data class ApplyZoomRatio(val zoomRatio: Float) : SessionEffect
    data class BindPreview(
        val modeId: ModeId,
        val deviceGraph: DeviceGraphSpec,
        val reason: String,
        val isRecovery: Boolean
    ) : SessionEffect
    data class UnbindPreview(
        val reason: String,
        val clearHost: Boolean
    ) : SessionEffect
}

interface CameraSession {
    val state: StateFlow<SessionState>
    val effects: Flow<SessionEffect>

    suspend fun dispatch(intent: SessionIntent)
}
