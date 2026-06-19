package com.opencamera.core.session

import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotRequest

internal enum class SessionCommandKind(
    val countdownLastAction: String,
    private val activeShotText: ActiveShotAdmissionText
) {
    MODE_SWITCH(
        countdownLastAction = "Wait for countdown to finish before switching modes",
        activeShotText = ActiveShotAdmissionText.PhotoOrVideo(
            photoLastAction = "Wait for current capture to finish before switching modes",
            videoLastAction = "Stop recording before switching modes"
        )
    ),
    SETTINGS_UPDATE(
        countdownLastAction = "Wait for countdown to finish before updating settings",
        activeShotText = ActiveShotAdmissionText.AnyMedia(
            lastAction = "Wait for current capture to finish before updating settings"
        )
    ),
    LENS_SWITCH(
        countdownLastAction = "Wait for countdown to finish before switching lenses",
        activeShotText = ActiveShotAdmissionText.PhotoOrVideo(
            photoLastAction = "Wait for current capture to finish before switching lenses",
            videoLastAction = "Stop recording before switching lenses"
        )
    ),
    STILL_CAPTURE_QUALITY(
        countdownLastAction = "Wait for countdown to finish before changing still quality",
        activeShotText = ActiveShotAdmissionText.PhotoOrVideo(
            photoLastAction = "Wait for current capture to finish before changing still quality",
            videoLastAction = "Stop recording before changing still quality"
        )
    ),
    STILL_CAPTURE_RESOLUTION(
        countdownLastAction = "Wait for countdown to finish before changing still resolution",
        activeShotText = ActiveShotAdmissionText.PhotoOrVideo(
            photoLastAction = "Wait for current capture to finish before changing still resolution",
            videoLastAction = "Stop recording before changing still resolution"
        )
    ),
    PREVIEW_RATIO(
        countdownLastAction = "Wait for countdown to finish before switching preview ratio",
        activeShotText = ActiveShotAdmissionText.PhotoOrVideo(
            photoLastAction = "Wait for current capture to finish before switching preview ratio",
            videoLastAction = "Stop recording before switching preview ratio"
        )
    ),
    PREVIEW_BRIGHTNESS(
        countdownLastAction = "Wait for countdown to finish before adjusting brightness",
        activeShotText = ActiveShotAdmissionText.AnyMedia(
            lastAction = "Wait for current capture to finish before adjusting brightness"
        )
    );

    val photoShotLastAction: String
        get() = activeShotText.lastActionFor(MediaType.PHOTO)

    val videoShotLastAction: String
        get() = activeShotText.lastActionFor(MediaType.VIDEO)

    internal fun lastActionFor(activeShot: ShotRequest): String {
        return activeShotText.lastActionFor(activeShot.mediaType)
    }
}

internal data class SessionCommandAdmissionSnapshot(
    val countdownInProgress: Boolean,
    val countdownRemainingSeconds: Int?,
    val activeShot: ShotRequest?,
    val recordingStatus: RecordingStatus
)

internal sealed interface SessionCommandAdmissionResult {
    data object Allowed : SessionCommandAdmissionResult

    data class Blocked(
        val lastAction: String,
        val traceDetail: String
    ) : SessionCommandAdmissionResult
}

internal object SessionCommandAdmission {
    fun evaluate(
        command: SessionCommandKind,
        snapshot: SessionCommandAdmissionSnapshot
    ): SessionCommandAdmissionResult {
        if (snapshot.countdownInProgress) {
            return SessionCommandAdmissionResult.Blocked(
                lastAction = command.countdownLastAction,
                traceDetail = "countdown=${snapshot.countdownRemainingSeconds}"
            )
        }

        val activeShot = snapshot.activeShot ?: return SessionCommandAdmissionResult.Allowed
        return SessionCommandAdmissionResult.Blocked(
            lastAction = command.lastActionFor(activeShot),
            traceDetail = "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
        )
    }
}

private sealed interface ActiveShotAdmissionText {
    fun lastActionFor(mediaType: MediaType): String

    data class AnyMedia(
        private val lastAction: String
    ) : ActiveShotAdmissionText {
        override fun lastActionFor(mediaType: MediaType): String = lastAction
    }

    data class PhotoOrVideo(
        private val photoLastAction: String,
        private val videoLastAction: String
    ) : ActiveShotAdmissionText {
        override fun lastActionFor(mediaType: MediaType): String {
            return when (mediaType) {
                MediaType.PHOTO -> photoLastAction
                MediaType.VIDEO -> videoLastAction
            }
        }
    }
}
