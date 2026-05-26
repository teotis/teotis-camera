# Video Saved-Media Thumbnail And Gallery Preload

> For text-only agents: this is an app/media presentation task. Keep ownership in `Session Kernel` for saved-media state and in app shell for Android thumbnail loading. Use `rtk` for every command.

## Goal

Fix the real-device issue where a completed video recording leaves the thumbnail blank or stale. The app must treat saved videos as first-class saved media for thumbnail display and gallery opening, without pretending full video post-processing exists.

## Root Cause

The session already has video saved-media state, but the app presentation path is still image-first:

- `MediaStoreLatestImageQuery.kt` queries only `MediaStore.Images`.
- `MainActivity.loadLatestGalleryImage()` only dispatches `LatestGalleryImageLoaded` from that image query.
- `MainActivity.render()` loads thumbnail URIs through `ImageView.setImageURI()`, which is reliable for still images but not for extracting a frame from a video content URI.
- `GalleryOpenTarget.kt` already chooses `video/*` when `latestSavedMediaType == SavedMediaType.VIDEO`, so the missing piece is thumbnail materialization and latest-media preload.

## Required Behavior

- After a video `ShotCompleted`, `latestThumbnailSource` remains official saved media, `latestSavedMediaType` is `VIDEO`, and the thumbnail slot displays a representative frame.
- On app start, latest-media preload considers both images and videos, sorts by recency, and dispatches the newest saved item.
- The thumbnail click target must keep using official saved media only. Do not open pending capture feedback or preview snapshots as gallery media.
- If a video frame cannot be decoded, the UI must clear or show a deterministic fallback state rather than silently keeping an unrelated old image.
- Photo thumbnail behavior must not regress.

## Suggested Design

### Option A: App-Shell Video Frame Loader

Recommended for this pass.

- Keep `ThumbnailSource.SavedMedia` unchanged.
- Add an app-only helper that takes `uri + SavedMediaType` and returns a render command:
  - `PHOTO`: use the existing URI path.
  - `VIDEO`: extract a small bitmap with `MediaMetadataRetriever` from the `content://` or absolute-file source, store it in `cacheDir/video-thumbnails`, and load that file URI into the `ImageView`.
- Add identity fields to avoid re-extracting the same video frame every render.

Why: it is small, Android-specific, and does not move platform thumbnail decoding into `core:session` or `core:media`.

### Option B: Extend `ThumbnailSource` With A Materialized Render URI

Useful later if multiple UI surfaces need the same materialized video frame.

- Add a new source subtype or extra field for materialized thumbnail assets.
- Requires changing core contracts and more tests.

Why not now: this is heavier than the current defect requires.

## Files To Inspect Or Modify

- `app/src/main/java/com/opencamera/app/MediaStoreLatestImageQuery.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/ThumbnailRenderCommand.kt`
- `app/src/main/java/com/opencamera/app/GalleryOpenTarget.kt`
- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt`
- `app/src/test/java/com/opencamera/app/GalleryOpenTargetTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Implementation Steps

1. Replace or complement `queryLatestGalleryImage()` with `queryLatestGalleryMedia(context)`.
   - Query images and videos.
   - Return `ThumbnailSource.SavedMedia` plus media type.
   - On Android 13+, check `READ_MEDIA_IMAGES` for images and `READ_MEDIA_VIDEO` for videos.
   - On older Android, continue using `READ_EXTERNAL_STORAGE`.
   - Sort candidates by `DATE_ADDED DESC` or equivalent timestamp and pick the newest.

2. Add a small app model for preload results.
   - Example shape:

```kotlin
internal data class LatestGalleryMedia(
    val source: ThumbnailSource.SavedMedia,
    val mediaType: SavedMediaType
)
```

   - The session currently has `SessionIntent.LatestGalleryImageLoaded(source)` with no media type. Prefer adding a video-aware intent such as `LatestGalleryMediaLoaded(source, mediaType)` and keep the old intent only as compatibility if needed.

3. Update `PreviewRecoverySessionProcessor.handleLatestGalleryImageLoaded`.
   - Rename or overload it for media.
   - Preserve the current rule: do not overwrite an existing saved-media thumbnail.
   - Set `latestSavedMediaType` when preloading latest media. Without this, `GalleryOpenTarget` will default video content to `image/*`.

4. Add a video thumbnail materializer in the app layer.
   - Use `MediaMetadataRetriever.setDataSource(context, Uri)` for `content://`.
   - Use path-based data source only for absolute file paths.
   - Extract a frame near the beginning, scale/crop through `ImageView.centerCrop`, and write a small JPEG or PNG under `cacheDir/video-thumbnails`.
   - Return a file URI for `ImageView.setImageURI()`.
   - On failure, return a clear fallback command and log through existing dev diagnostics if a convenient hook exists.

5. Update thumbnail render command logic.
   - The command identity should include both source URI and `SavedMediaType`.
   - Avoid reloading when the same video source has already produced the same cached frame URI.
   - Existing photo behavior should continue to use the content URI directly.

6. Keep gallery opening unchanged except for tests.
   - Ensure `galleryOpenTargetFor(videoSource, SavedMediaType.VIDEO)` opens `video/*`.
   - Do not route cached extracted frame files to the gallery opener.

## Tests

Add or update:

- `ThumbnailRenderCommandTest`
  - photo source loads original content URI.
  - video source requests or resolves a materialized thumbnail URI.
  - same source and same media type is `NoOp`.
  - same URI with different media type is not treated as the same render request.

- `GalleryOpenTargetTest`
  - saved video media opens with `video/*`.
  - cached video frame thumbnail is not used as the gallery target.

- `DefaultCameraSessionTest` or `PreviewRecoverySessionProcessor` coverage
  - latest gallery video preload sets `latestThumbnailSource` and `latestSavedMediaType = VIDEO`.
  - existing saved-media thumbnail is not overwritten by preload.

Focused verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ThumbnailRenderCommandTest --tests com.opencamera.app.GalleryOpenTargetTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Manual Smoke

1. Record a short video.
2. Stop recording and wait for save completion.
3. Confirm the thumbnail slot shows a frame from the video or a deterministic fallback if extraction fails.
4. Tap the thumbnail and confirm Android opens the saved video.
5. Restart the app and confirm the latest video can preload when it is newer than the latest photo.

## Non-Goals

- Do not implement MP4 filter rendering, MP4 watermark burn-in, or video transcoding.
- Do not move Android `MediaMetadataRetriever` into `core:media`.
- Do not open cached thumbnail frame files as if they were saved gallery videos.
