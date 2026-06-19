package com.opencamera.app.camera

import com.opencamera.core.media.ShotPlan

/**
 * Events translated from CameraX VideoRecordEvent by the Adapter.
 * Decouples the controller from CameraX framework types for deterministic testing.
 */
sealed interface RecordingControllerEvent {
    data object Started : RecordingControllerEvent

    data class Finalized(
        val hasError: Boolean,
        val errorMessage: String?,
        val outputUri: String?
    ) : RecordingControllerEvent
}

/**
 * Typed outcomes emitted by the controller. The Adapter translates these to DeviceEvent.
 * No CameraX or Android framework types leak through this interface.
 */
sealed interface RecordingOutcome {
    data class TorchChange(val enabled: Boolean) : RecordingOutcome

    data class Released(
        val shotId: String,
        val mediaType: com.opencamera.core.media.MediaType
    ) : RecordingOutcome

    data class FinalizeSuccess(
        val plan: ShotPlan,
        val outputPath: String,
        val outputUri: String?,
        val diagnostics: List<String>
    ) : RecordingOutcome

    data class FinalizeError(
        val shotId: String,
        val mediaType: com.opencamera.core.media.MediaType,
        val errorMessage: String
    ) : RecordingOutcome
}

/**
 * Manages the state machine for video recording lifecycle.
 *
 * Owns: active Recording, active ShotPlan, lifecycle-interrupted shot IDs,
 * current torch state, and recording quality tracker start/stop transitions.
 *
 * The Adapter provides callbacks for CameraX recording control, torch hardware,
 * and audio permission checks. Typed [RecordingOutcome] results are emitted for
 * the Adapter to translate to DeviceEvent.
 */
internal class VideoRecordingController(
    private val isAudioPermissionGranted: () -> Boolean,
    private val onTorchChange: suspend (Boolean) -> Unit,
    private val qualityTrackerStart: (Int?) -> Unit = {},
    private val qualityTrackerStop: () -> Unit = {},
    private val qualityTrackerRecordFrame: (Long) -> Unit = {}
) {
    private var _activePlan: ShotPlan? = null
    private var _activeFrameRateHint: Int? = null
    private var _currentTorchEnabled = false
    private val _lifecycleInterruptedShotIds = mutableSetOf<String>()

    val isActive: Boolean get() = _activePlan != null

    fun handleEvent(event: RecordingControllerEvent) {
        when (event) {
            is RecordingControllerEvent.Started -> handleStarted()
            is RecordingControllerEvent.Finalized -> handleFinalized(event)
        }
    }

    private fun handleStarted() {
        qualityTrackerStart(_activeFrameRateHint)
    }

    private fun handleFinalized(event: RecordingControllerEvent.Finalized) {
        val plan = _activePlan ?: return
        val shotId = plan.request.shotId
        val wasInterrupted = _lifecycleInterruptedShotIds.remove(shotId)
        qualityTrackerStop()
        _currentTorchEnabled = false
        _activePlan = null
        _activeFrameRateHint = null

        if (wasInterrupted) return

        if (event.hasError) {
            outcomes.add(
                RecordingOutcome.FinalizeError(
                    shotId = shotId,
                    mediaType = plan.request.mediaType,
                    errorMessage = event.errorMessage ?: "Recording finalize error"
                )
            )
        } else {
            outcomes.add(
                RecordingOutcome.FinalizeSuccess(
                    plan = plan,
                    outputPath = "", // resolved by adapter via VideoOutputRequest
                    outputUri = event.outputUri,
                    diagnostics = listOf("video-scene=normal")
                )
            )
        }
    }

    // -- Adapter-facing recording lifecycle --

    val outcomes = mutableListOf<RecordingOutcome>()

    fun startRecording(plan: ShotPlan, expectedFrameRate: Int? = null) {
        require(_activePlan == null) { "Video recording already in progress" }
        _activePlan = plan
        _activeFrameRateHint = expectedFrameRate
    }

    fun stopRecording() {
        _activePlan ?: error("No active recording to stop")
        qualityTrackerStop()
    }

    /**
     * Silently clear all recording state without emitting outcomes.
     * Used by Adapter during use-case rebind (closeActiveRecording=true).
     */
    fun clearRecording() {
        if (_activePlan != null) {
            qualityTrackerStop()
            _currentTorchEnabled = false
            _activePlan = null
            _activeFrameRateHint = null
        }
    }

    fun activePlan(): ShotPlan? = _activePlan

    /**
     * Reset torch tracking state when camera provider is invalidated.
     * Does not stop tracker or clear recording — only resets torch flag.
     */
    fun resetTorchState() {
        _currentTorchEnabled = false
    }

    // -- Torch control --

    val isTorchEnabled: Boolean get() = _currentTorchEnabled

    suspend fun applyTorch(enabled: Boolean) {
        if (_currentTorchEnabled == enabled) return
        onTorchChange(enabled)
        _currentTorchEnabled = enabled
    }

    // -- Audio permission --

    fun hasAudioPermission(): Boolean = isAudioPermissionGranted()

    // -- Lifecycle release --

    fun release() {
        if (_activePlan != null) {
            val activePlan = _activePlan!!
            _lifecycleInterruptedShotIds.add(activePlan.request.shotId)
            qualityTrackerStop()
            _currentTorchEnabled = false
            _activePlan = null
            _activeFrameRateHint = null
            outcomes.add(
                RecordingOutcome.Released(
                    shotId = activePlan.request.shotId,
                    mediaType = activePlan.request.mediaType
                )
            )
        }
    }
}
