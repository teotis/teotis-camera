package com.opencamera.core.mode

import com.opencamera.core.media.MediaType

data class StillShotSessionEventText(
    val shotStartedHeadline: String,
    val shotStartedDetail: String? = null,
    val shotCompletedHeadline: String,
    val shotFailedHeadline: String
)

fun reduceStillShotSessionEvent(
    event: ModeSessionEvent,
    text: StillShotSessionEventText,
    updateSnapshot: (headline: String, detail: String?) -> Unit
): Boolean {
    return when (event) {
        is ModeSessionEvent.ShotStarted -> {
            if (event.shot.mediaType != MediaType.PHOTO) {
                false
            } else {
                updateSnapshot(text.shotStartedHeadline, text.shotStartedDetail)
                true
            }
        }

        is ModeSessionEvent.ShotCompleted -> {
            if (event.result.mediaType != MediaType.PHOTO) {
                false
            } else {
                updateSnapshot(text.shotCompletedHeadline, event.result.outputPath)
                true
            }
        }

        is ModeSessionEvent.ShotFailed -> {
            if (event.mediaType != MediaType.PHOTO) {
                false
            } else {
                updateSnapshot(text.shotFailedHeadline, event.reason)
                true
            }
        }
    }
}
