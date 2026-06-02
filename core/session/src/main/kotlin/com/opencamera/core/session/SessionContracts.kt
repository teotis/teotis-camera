package com.opencamera.core.session

import com.opencamera.core.device.CameraOutputRotation
import com.opencamera.core.capability.CapabilityGraphReport
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.LensNode
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.RenderRecipe
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
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
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
    DATA_RECEIVED,
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

enum class CaptureFeedbackPolicy {
    ALLOW_PREVIEW_BITMAP,
    SUPPRESS_UNTIL_SAVED_MEDIA
}

internal fun captureFeedbackPolicyFor(shot: ShotRequest): CaptureFeedbackPolicy {
    val recipe = RenderRecipe.from(shot)
    val metadataFilterSpec = FilterRenderSpec.fromMetadataTags(shot.saveRequest.metadata.customTags)
    val suppressRawFeedback = metadataFilterSpec.requiresTrustedSavedMedia(
        profileId = recipe.filterProfileId
    ) ||
        !recipe.perceptualColorRecipe.isNeutral ||
        recipe.frameRatio != null && recipe.frameRatio != FrameRatio.RATIO_4_3 ||
        recipe.selfieMirror ||
        recipe.watermarkTemplateId?.let { it != "classic-overlay" } == true
    return if (suppressRawFeedback) {
        CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA
    } else {
        CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP
    }
}

private fun FilterRenderSpec?.requiresTrustedSavedMedia(profileId: String?): Boolean {
    if (this == null) {
        return false
    }
    if (profileId != null && profileId in LOW_RISK_PREVIEW_FILTER_PROFILES && this == defaultFilterRenderSpecOrNull(profileId)) {
        return false
    }
    return true
}

private val LOW_RISK_PREVIEW_FILTER_PROFILES = setOf("photo-original", "photo-vivid")

enum class PreviewMeteringFeedbackStatus {
    REQUESTED,
    SUCCEEDED,
    DEGRADED_AUTO_EXPOSURE_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewMeteringFeedback(
    val requestId: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val status: PreviewMeteringFeedbackStatus,
    val reason: String? = null
)

enum class PreviewBrightnessFeedbackStatus {
    REQUESTED,
    APPLIED,
    DEGRADED_SAVED_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewBrightnessFeedback(
    val requestId: String,
    val requestedSteps: Int,
    val appliedSteps: Int?,
    val status: PreviewBrightnessFeedbackStatus,
    val reason: String? = null
)

enum class PhotoLowLightPromptStatus {
    HIDDEN,
    AVAILABLE_ENABLED,
    AVAILABLE_DISABLED,
    DEGRADED_ENABLED,
    DEGRADED_DISABLED,
    UNSUPPORTED
}

data class PhotoLowLightPrompt(
    val status: PhotoLowLightPromptStatus,
    val visibleUntilElapsedMillis: Long?,
    val brightnessScore: Float?,
    val message: String
)

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

data class PendingPostprocessUiState(
    val shotId: String,
    val mediaType: MediaType,
    val message: String,
    val warnBeforeExit: Boolean = true
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
    val lastError: String? = null,
    val previewMeteringFeedback: PreviewMeteringFeedback? = null,
    val previewBrightnessSteps: Int = 0,
    val previewBrightnessFeedback: PreviewBrightnessFeedback? = null,
    val photoSceneSignal: com.opencamera.core.device.PhotoSceneSignal = com.opencamera.core.device.PhotoSceneSignal(),
    val photoLowLightPrompt: PhotoLowLightPrompt? = null,
    val recordingStartedAtElapsedMillis: Long? = null,
    val recordingElapsedMillis: Long? = null,
    val documentBatch: DocumentBatchState = DocumentBatchState.inactive(),
    val pendingPostprocess: PendingPostprocessUiState? = null,
    val captureReadiness: com.opencamera.core.device.CaptureReadiness? = null
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
    val outputRotation: CameraOutputRotation = CameraOutputRotation.ROTATION_0,
    val shutterPressedAtElapsedMillis: Long? = null,
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

    val recordingStartedAtElapsedMillis: Long?
        get() = presentation.recordingStartedAtElapsedMillis

    val recordingElapsedMillis: Long?
        get() = presentation.recordingElapsedMillis
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
    data class LatestGalleryImageLoaded(
        val source: ThumbnailSource.SavedMedia
    ) : SessionIntent
    data class LatestGalleryMediaLoaded(
        val source: ThumbnailSource.SavedMedia,
        val mediaType: SavedMediaType
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
    data class CaptureCommitted(
        val shotId: String,
        val mediaType: MediaType,
        val source: String,
        val elapsedTimestampMs: Long? = null
    ) : SessionIntent
    data class DataReceived(val shotId: String, val mediaType: MediaType) : SessionIntent
    data class ShotCompleted(val result: ShotResult) : SessionIntent
    data class ShotFailed(
        val shotId: String,
        val mediaType: MediaType,
        val reason: String
    ) : SessionIntent
    data class ThermalStateChanged(val thermalState: CameraThermalState) : SessionIntent
    data class PerformanceClassChanged(val performanceClass: CameraPerformanceClass) : SessionIntent
    data class PreviewTapToFocus(val normalizedX: Float, val normalizedY: Float) : SessionIntent
    data class PreviewMeteringCompleted(val result: com.opencamera.core.device.PreviewMeteringResult) : SessionIntent
    data class OutputRotationChanged(val rotation: CameraOutputRotation) : SessionIntent
    data class ApplyPreviewBrightness(val exposureCompensationSteps: Int) : SessionIntent
    data object IncreasePreviewBrightness : SessionIntent
    data object DecreasePreviewBrightness : SessionIntent
    data object ResetPreviewBrightness : SessionIntent
    data class PreviewBrightnessApplied(
        val result: com.opencamera.core.device.PreviewBrightnessResult
    ) : SessionIntent
    data class PhotoSceneSignalUpdated(
        val signal: com.opencamera.core.device.PhotoSceneSignal
    ) : SessionIntent
    data object PhotoLowLightPromptExpired : SessionIntent
    data class PreviewMeteringFeedbackExpired(val requestId: String) : SessionIntent
    data object DocumentBatchClear : SessionIntent
    data class DocumentBatchRemoveItem(val itemId: String) : SessionIntent
    data class DocumentBatchMoveItem(val itemId: String, val direction: DocumentBatchMoveDirection) : SessionIntent
    data class DocumentBatchReorder(val orderedItemIds: List<String>) : SessionIntent
    data object DocumentBatchFinish : SessionIntent
}

sealed interface SessionEffect {
    data class ExecuteShot(val plan: ShotPlan) : SessionEffect
    data class StopActiveShot(val shotId: String) : SessionEffect
    data class ApplyZoomRatio(val zoomRatio: Float, val previewZoomRatio: Float) : SessionEffect
    data class SwitchLensNode(val lensNode: LensNode, val reason: String) : SessionEffect
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
    data class ApplyPreviewMetering(val request: com.opencamera.core.device.PreviewMeteringRequest) : SessionEffect
    data class UpdateOutputRotation(val rotation: CameraOutputRotation) : SessionEffect
    data class ApplyPreviewBrightness(
        val request: com.opencamera.core.device.PreviewBrightnessRequest
    ) : SessionEffect
}

interface CameraSession {
    val state: StateFlow<SessionState>
    val effects: Flow<SessionEffect>

    suspend fun dispatch(intent: SessionIntent)
}
