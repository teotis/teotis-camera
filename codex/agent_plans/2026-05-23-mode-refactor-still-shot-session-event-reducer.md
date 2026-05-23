# Still Shot Session Event Reducer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Remove repeated still-photo `onSessionEvent` branching from six still mode controllers without changing Video recording behavior.

**Architecture:** Add a reducer in `core/mode` that handles only `MediaType.PHOTO` shot events and calls back to each controller with the correct headline/detail pair. Controllers keep their own `buildSnapshot(...)` methods and mode-specific text.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, `kotlin.test`.

---

## Files

- Create: `core/mode/src/main/kotlin/com/opencamera/core/mode/StillShotSessionEventReducer.kt`
- Create: `core/mode/src/test/kotlin/com/opencamera/core/mode/StillShotSessionEventReducerTest.kt`
- Modify: `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- Modify: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- Modify: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- Modify: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- Modify: `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- Modify: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- Do not modify: `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`

## Design Note

The external `SessionEventHeadlines` proposal is too small: it keeps the repeated `when`, media-type guards, and snapshot update branch in every controller. Use the reducer below instead.

## Task 1: Add The Reducer

- [ ] **Step 1: Create `StillShotSessionEventReducer.kt`**

```kotlin
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
```

- [ ] **Step 2: Add reducer tests**

Create `core/mode/src/test/kotlin/com/opencamera/core/mode/StillShotSessionEventReducerTest.kt`:

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StillShotSessionEventReducerTest {

    private val text = StillShotSessionEventText(
        shotStartedHeadline = "Photo capture in progress",
        shotStartedDetail = "Unified shot pipeline accepted the photo save task.",
        shotCompletedHeadline = "Photo saved",
        shotFailedHeadline = "Photo capture failed"
    )

    @Test
    fun `photo shot started updates snapshot`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotStarted(photoShotRequest()),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        assertTrue(handled)
        assertEquals(
            listOf(
                "Photo capture in progress" to
                    "Unified shot pipeline accepted the photo save task."
            ),
            updates
        )
    }

    @Test
    fun `photo shot completed uses output path as detail`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotCompleted(photoShotResult()),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        assertTrue(handled)
        assertEquals(listOf("Photo saved" to "/tmp/photo.jpg"), updates)
    }

    @Test
    fun `photo shot failed uses reason as detail`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotFailed(
                shotId = "photo-1",
                mediaType = MediaType.PHOTO,
                reason = "write failed"
            ),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        assertTrue(handled)
        assertEquals(listOf("Photo capture failed" to "write failed"), updates)
    }

    @Test
    fun `video events are ignored`() {
        val updates = mutableListOf<Pair<String, String?>>()

        val handled = reduceStillShotSessionEvent(
            event = ModeSessionEvent.ShotStarted(videoShotRequest()),
            text = text,
            updateSnapshot = { headline, detail -> updates += headline to detail }
        )

        assertFalse(handled)
        assertTrue(updates.isEmpty())
    }

    private fun photoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "photo-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = null
        )

    private fun videoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "video-1",
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = LivePhotoCaptureSpec()
        )

    private fun photoShotResult(): ShotResult =
        ShotResult(
            shotId = "photo-1",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/photo.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/photo.jpg"),
            metadata = MediaMetadata()
        )
}
```

- [ ] **Step 3: Verify the reducer test**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.StillShotSessionEventReducerTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Migrate Six Still Controllers

Add these imports to each still mode file:

```kotlin
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.mode.reduceStillShotSessionEvent
```

In each controller, replace the current `onSessionEvent` `when` body with a reducer call. Use this update callback shape to preserve default details:

```kotlin
updateSnapshot = { headline, detail ->
    mutableSnapshot.value = if (detail == null) {
        buildSnapshot(headline = headline)
    } else {
        buildSnapshot(headline = headline, detail = detail)
    }
}
```

### Photo

- [ ] Replace `onSessionEvent` with:

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceStillShotSessionEvent(
        event = event,
        text = StillShotSessionEventText(
            shotStartedHeadline = "Photo capture in progress",
            shotStartedDetail = "Unified shot pipeline accepted the photo save task.",
            shotCompletedHeadline = "Photo saved",
            shotFailedHeadline = "Photo capture failed"
        ),
        updateSnapshot = { headline, detail ->
            mutableSnapshot.value = if (detail == null) {
                buildSnapshot(headline = headline)
            } else {
                buildSnapshot(headline = headline, detail = detail)
            }
        }
    )
}
```

### Portrait

- [ ] Replace `onSessionEvent` with:

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceStillShotSessionEvent(
        event = event,
        text = StillShotSessionEventText(
            shotStartedHeadline = "Portrait capture in progress",
            shotCompletedHeadline = "Portrait saved",
            shotFailedHeadline = "Portrait capture failed"
        ),
        updateSnapshot = { headline, detail ->
            mutableSnapshot.value = if (detail == null) {
                buildSnapshot(headline = headline)
            } else {
                buildSnapshot(headline = headline, detail = detail)
            }
        }
    )
}
```

### Pro

- [ ] Replace `onSessionEvent` with:

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceStillShotSessionEvent(
        event = event,
        text = StillShotSessionEventText(
            shotStartedHeadline = "Pro capture in progress",
            shotCompletedHeadline = "Pro photo saved",
            shotFailedHeadline = "Pro capture failed"
        ),
        updateSnapshot = { headline, detail ->
            mutableSnapshot.value = if (detail == null) {
                buildSnapshot(headline = headline)
            } else {
                buildSnapshot(headline = headline, detail = detail)
            }
        }
    )
}
```

### Night

- [ ] Replace `onSessionEvent` with:

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceStillShotSessionEvent(
        event = event,
        text = StillShotSessionEventText(
            shotStartedHeadline = "Scenery capture in progress",
            shotCompletedHeadline = "Scenery photo saved",
            shotFailedHeadline = "Scenery capture failed"
        ),
        updateSnapshot = { headline, detail ->
            mutableSnapshot.value = if (detail == null) {
                buildSnapshot(headline = headline)
            } else {
                buildSnapshot(headline = headline, detail = detail)
            }
        }
    )
}
```

### Document

- [ ] Replace `onSessionEvent` with:

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceStillShotSessionEvent(
        event = event,
        text = StillShotSessionEventText(
            shotStartedHeadline = "Document scan in progress",
            shotCompletedHeadline = "Document saved",
            shotFailedHeadline = "Document capture failed"
        ),
        updateSnapshot = { headline, detail ->
            mutableSnapshot.value = if (detail == null) {
                buildSnapshot(headline = headline)
            } else {
                buildSnapshot(headline = headline, detail = detail)
            }
        }
    )
}
```

### Humanistic

- [ ] Replace `onSessionEvent` with:

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceStillShotSessionEvent(
        event = event,
        text = StillShotSessionEventText(
            shotStartedHeadline = "Humanistic capture in progress",
            shotStartedDetail = "Street-life still request accepted by the unified shot pipeline.",
            shotCompletedHeadline = "Humanistic photo saved",
            shotFailedHeadline = "Humanistic capture failed"
        ),
        updateSnapshot = { headline, detail ->
            mutableSnapshot.value = if (detail == null) {
                buildSnapshot(headline = headline)
            } else {
                buildSnapshot(headline = headline, detail = detail)
            }
        }
    )
}
```

## Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test :feature:mode-photo:test :feature:mode-portrait:test :feature:mode-pro:test :feature:mode-night:test :feature:mode-document:test :feature:mode-humanistic:test
```

Expected: `BUILD SUCCESSFUL`.

Run session behavior coverage:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Expected: `BUILD SUCCESSFUL`.

## Acceptance Criteria

- Six still controllers call `reduceStillShotSessionEvent(...)` from `onSessionEvent`.
- No still controller keeps a local `when` over `ShotStarted / ShotCompleted / ShotFailed`.
- Existing started/completed/failed headline text is preserved.
- `VideoModePlugin.kt` is unchanged and still handles recording-specific session events itself.
- The reducer ignores `MediaType.VIDEO` events.

