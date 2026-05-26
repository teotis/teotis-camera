# Thumbnail Gallery Open Fix Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This task is text-only and does not require screenshots.

**Goal:** Fix thumbnail click so it opens the latest official saved media instead of failing with `无法打开媒体文件`.

**Architecture:** The Media Pipeline and Session Kernel own saved-media identity. UI may launch an Android viewer only for `ThumbnailSource.SavedMedia`; it must not treat preview snapshots or capture feedback as gallery media.

**Tech Stack:** Kotlin, Android `Intent.ACTION_VIEW`, MediaStore `content://` URIs, FileProvider fallback, app JVM/unit tests.

---

## Root Cause

Current `MainActivity` has two different thumbnail paths:

- Rendering uses `state.presentation.pendingCaptureFeedback` first, then `state.presentation.latestThumbnailSource?.renderUriOrNull()`.
- Click handling uses `presentation.latestCapturePath ?: presentation.latestVideoPath`, then `File(filePath).exists()`, then `FileProvider.getUriForFile(...)`.

For Android Q+ MediaStore output, `latestCapturePath` can be a display path such as:

```text
Pictures/OpenCamera/OpenCamera_20260523_101500.jpg
```

That is not an absolute filesystem path. `File.exists()` returns false, so the click handler shows `gallery_open_failed` even though `ThumbnailSource.SavedMedia.renderUri` already contains a usable `content://media/...` URI.

## Required Behavior

- Open only official saved media:
  - `ThumbnailSource.SavedMedia`
  - latest saved photo/video paths backed by `content://` or a real absolute file
- Never open:
  - `pendingCaptureFeedback`
  - `ThumbnailSource.PreviewSnapshot`
  - `ThumbnailSource.Pending`
  - a MediaStore display path with no URI
- Prefer `SavedMedia.renderUri` when it is `content://`.
- For absolute file fallback, use FileProvider before launching external apps.
- Do not require `File.exists()` for MediaStore display paths.
- Keep mime type based on `SavedMediaType.PHOTO` / `SavedMediaType.VIDEO`.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`

Create:

- `app/src/main/java/com/opencamera/app/GalleryOpenTarget.kt`
- `app/src/test/java/com/opencamera/app/GalleryOpenTargetTest.kt`

Optional test update:

- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt`

## Implementation Tasks

### Task 1: Add a pure gallery target resolver

Create `GalleryOpenTarget.kt` with a pure resolver that does not depend on Android `Context`.

Recommended model:

```kotlin
package com.opencamera.app

import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.session.SavedMediaType

internal enum class GalleryOpenUriKind {
    CONTENT_URI,
    ABSOLUTE_FILE,
    NONE
}

internal data class GalleryOpenTarget(
    val uri: String,
    val kind: GalleryOpenUriKind,
    val mimeType: String
)

internal fun galleryOpenTargetFor(
    source: ThumbnailSource?,
    savedMediaType: SavedMediaType?
): GalleryOpenTarget? {
    val saved = source as? ThumbnailSource.SavedMedia ?: return null
    val mimeType = when (savedMediaType) {
        SavedMediaType.VIDEO -> "video/*"
        SavedMediaType.PHOTO,
        null -> "image/*"
    }

    val renderUri = saved.renderUri?.takeIf { it.isNotBlank() }
    if (renderUri != null && renderUri.startsWith("content://")) {
        return GalleryOpenTarget(
            uri = renderUri,
            kind = GalleryOpenUriKind.CONTENT_URI,
            mimeType = mimeType
        )
    }

    val fileUriPath = renderUri
        ?.takeIf { it.startsWith("file://") }
        ?.removePrefix("file://")
    val absolutePath = fileUriPath
        ?: saved.outputPath.takeIf { it.startsWith("/") }

    return absolutePath?.let { path ->
        GalleryOpenTarget(
            uri = path,
            kind = GalleryOpenUriKind.ABSOLUTE_FILE,
            mimeType = mimeType
        )
    }
}
```

### Task 2: Add resolver tests

Create tests covering these cases:

```kotlin
@Test
fun `content uri saved photo opens content uri`() {
    val target = galleryOpenTargetFor(
        source = ThumbnailSource.SavedMedia(
            outputPath = "Pictures/OpenCamera/photo.jpg",
            renderUri = "content://media/external/images/media/42"
        ),
        savedMediaType = SavedMediaType.PHOTO
    )

    assertEquals(GalleryOpenUriKind.CONTENT_URI, target?.kind)
    assertEquals("content://media/external/images/media/42", target?.uri)
    assertEquals("image/*", target?.mimeType)
}

@Test
fun `relative display path without render uri does not open`() {
    val target = galleryOpenTargetFor(
        source = ThumbnailSource.SavedMedia("Pictures/OpenCamera/photo.jpg"),
        savedMediaType = SavedMediaType.PHOTO
    )

    assertNull(target)
}

@Test
fun `preview snapshot does not open gallery`() {
    val target = galleryOpenTargetFor(
        source = ThumbnailSource.PreviewSnapshot("/tmp/preview.jpg"),
        savedMediaType = SavedMediaType.PHOTO
    )

    assertNull(target)
}

@Test
fun `saved video uses video mime type`() {
    val target = galleryOpenTargetFor(
        source = ThumbnailSource.SavedMedia(
            outputPath = "Movies/OpenCamera/video.mp4",
            renderUri = "content://media/external/video/media/99"
        ),
        savedMediaType = SavedMediaType.VIDEO
    )

    assertEquals("video/*", target?.mimeType)
}
```

### Task 3: Replace thumbnail click logic in MainActivity

Change `previewThumbnail.setOnClickListener` to use `latestThumbnailSource`, not `latestCapturePath/latestVideoPath`.

Expected shape:

```kotlin
previewThumbnail.setOnClickListener {
    val presentation = latestSessionState?.presentation ?: return@setOnClickListener
    val target = galleryOpenTargetFor(
        source = presentation.latestThumbnailSource,
        savedMediaType = presentation.latestSavedMediaType
    ) ?: run {
        Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }

    val uri = when (target.kind) {
        GalleryOpenUriKind.CONTENT_URI -> Uri.parse(target.uri)
        GalleryOpenUriKind.ABSOLUTE_FILE -> {
            val file = File(target.uri)
            if (!file.exists()) {
                Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        }
        GalleryOpenUriKind.NONE -> {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, target.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(intent) }.onFailure {
        Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
    }
}
```

If `GalleryOpenUriKind.NONE` is removed from the enum, remove that branch.

### Task 4: Confirm FileProvider paths still cover fallback files

Current `file_paths.xml` already covers:

```xml
<external-files-path name="captures" path="." />
<files-path name="internal_captures" path="." />
<cache-path name="thumbnails" path="preview-thumbnails/" />
```

Keep it unchanged unless a test proves the fallback capture file is outside these roots.

### Task 5: Run focused verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.GalleryOpenTargetTest --tests com.opencamera.app.ThumbnailRenderCommandTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Manual Smoke

1. Install the new APK.
2. Capture one photo.
3. Wait for the final saved thumbnail.
4. Tap the thumbnail.
5. Expected: Android photo viewer opens the saved photo.
6. Capture another photo with a postprocess feature enabled, such as watermark or frame ratio.
7. Tap before save completes.
8. Expected: no attempt to open the transient feedback file.
9. Tap after save completes.
10. Expected: Android photo viewer opens the final saved media.

## Non-Goals

- Do not change MediaStore save behavior in this task.
- Do not treat preview feedback as gallery media.
- Do not add broad media browser UI.
- Do not move saved-media ownership out of session/media contracts.

