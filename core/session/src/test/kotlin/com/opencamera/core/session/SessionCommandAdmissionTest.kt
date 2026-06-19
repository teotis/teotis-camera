package com.opencamera.core.session

import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionCommandAdmissionTest {
    @Test
    fun `idle admits shared reconfiguration commands`() {
        reconfigurationCommands.forEach { command ->
            assertEquals(
                SessionCommandAdmissionResult.Allowed,
                SessionCommandAdmission.evaluate(command, idleSnapshot),
                "Expected idle admission for $command"
            )
        }
    }

    @Test
    fun `countdown blocks before active shot checks with command specific action text`() {
        reconfigurationCommands.forEach { command ->
            assertEquals(
                SessionCommandAdmissionResult.Blocked(
                    lastAction = command.countdownLastAction,
                    traceDetail = "countdown=3"
                ),
                SessionCommandAdmission.evaluate(
                    command,
                    idleSnapshot.copy(
                        countdownInProgress = true,
                        countdownRemainingSeconds = 3,
                        activeShot = shot(MediaType.PHOTO)
                    )
                ),
                "Expected countdown block for $command"
            )
        }
    }

    @Test
    fun `photo shot blocks shared reconfiguration commands with photo capture wording`() {
        reconfigurationCommands.forEach { command ->
            assertEquals(
                SessionCommandAdmissionResult.Blocked(
                    lastAction = command.photoShotLastAction,
                    traceDetail = "shot=shot-photo,mediaType=PHOTO"
                ),
                SessionCommandAdmission.evaluate(
                    command,
                    idleSnapshot.copy(activeShot = shot(MediaType.PHOTO))
                ),
                "Expected photo shot block for $command"
            )
        }
    }

    @Test
    fun `video pending and active recording preserve shared video wording`() {
        listOf(
            RecordingStatus.REQUESTING to "pending",
            RecordingStatus.RECORDING to "recording"
        ).forEach { (recordingStatus, label) ->
            reconfigurationCommands.forEach { command ->
                assertEquals(
                    SessionCommandAdmissionResult.Blocked(
                        lastAction = command.videoShotLastAction,
                        traceDetail = "shot=shot-video,mediaType=VIDEO"
                    ),
                    SessionCommandAdmission.evaluate(
                        command,
                        idleSnapshot.copy(
                            activeShot = shot(MediaType.VIDEO),
                            recordingStatus = recordingStatus
                        )
                    ),
                    "Expected video $label block for $command"
                )
            }
        }
    }

    private companion object {
        val idleSnapshot = SessionCommandAdmissionSnapshot(
            countdownInProgress = false,
            countdownRemainingSeconds = null,
            activeShot = null,
            recordingStatus = RecordingStatus.IDLE
        )

        val reconfigurationCommands = listOf(
            SessionCommandKind.MODE_SWITCH,
            SessionCommandKind.SETTINGS_UPDATE,
            SessionCommandKind.LENS_SWITCH,
            SessionCommandKind.STILL_CAPTURE_QUALITY,
            SessionCommandKind.STILL_CAPTURE_RESOLUTION,
            SessionCommandKind.PREVIEW_RATIO,
            SessionCommandKind.PREVIEW_BRIGHTNESS
        )

        fun shot(mediaType: MediaType): ShotRequest {
            return ShotRequest(
                shotId = "shot-${mediaType.name.lowercase()}",
                shotKind = when (mediaType) {
                    MediaType.PHOTO -> ShotKind.STILL_CAPTURE
                    MediaType.VIDEO -> ShotKind.VIDEO_RECORDING
                },
                mediaType = mediaType,
                saveRequest = when (mediaType) {
                    MediaType.PHOTO -> SaveRequest.photoLibrary()
                    MediaType.VIDEO -> SaveRequest.videoLibrary()
                },
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        }
    }
}
