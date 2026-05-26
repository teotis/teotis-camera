# 2026-05-24 Video Mode Real-Device Feedback Index

> For text-only agents: this is the master handoff for two video-mode real-device findings. Pick one linked plan, keep edits scoped, and run every shell command through `rtk`.

## Source Feedback

User real-device findings in video mode:

1. After recording finishes, the thumbnail does not show. The likely cause is missing video-aware handling.
2. There is no appropriate recording time indication.

## Current-Code Evidence

- `CaptureRecordingSessionProcessor.handleShotCompleted()` already records `SavedMediaType.VIDEO`, `latestVideoPath`, and `latestThumbnailSource` when a video `ShotResult` arrives.
- `ShotExecutor.resultFor()` maps `ThumbnailPolicy.USE_SAVED_MEDIA` to `ThumbnailSource.SavedMedia`, so video recordings should have an official saved-media source once CameraX finalizes.
- `MainActivity.loadLatestGalleryImage()` only calls `queryLatestGalleryImage()`, and `MediaStoreLatestImageQuery.kt` only queries `MediaStore.Images`. Cold-start gallery preload cannot discover the latest video.
- Thumbnail rendering in `MainActivity.render()` calls `ImageView.setImageURI()` for the selected render URI. That is acceptable for image content, but it is not a reliable video-frame thumbnail path.
- `galleryOpenTargetFor()` is already video-aware through `SavedMediaType.VIDEO -> "video/*"`, so the open-target side is closer to correct than the thumbnail-render side.
- `SessionPresentationState` has recording status and output paths, but no `recordingStartedAtElapsedMillis`, elapsed duration, or duration label. UI can only show `REQUESTING / RECORDING / STOPPING`.

## Classification

### Implementable Small Loops

1. [Video saved-media thumbnail and gallery preload](./2026-05-24-video-thumbnail-and-gallery-preload.md)
   - Add video-aware latest-media query.
   - Render a video frame thumbnail or clear fallback state explicitly.
   - Keep gallery open target on official saved media only.

2. [Recording elapsed-time presentation](./2026-05-24-video-recording-elapsed-time.md)
   - Add session-owned elapsed-time state.
   - Render a compact `00:00` style indicator while recording.
   - Stop and clear the timer on save, failure, interruption, or shutdown.

### Registered But Not In Scope For This Pass

- Full video post-processing, such as burning filters or watermarks into MP4 frames.
- Video sidecar metadata beyond the existing save-request custom tags.
- A full video editor, playback preview, or in-app gallery.
- Multimodal visual QA of exact thumbnail appearance, because these plans are written for non-multimodal agents.
- Long-duration recording thermal/performance policy changes. Those remain Stage 7 stability work and require separate real-device thresholds.

## Recommended Execution Order

1. Implement video saved-media thumbnail first. It directly addresses the missing post-recording feedback and improves cold-start gallery state.
2. Implement recording elapsed-time second. It touches session state and cockpit rendering but should not depend on thumbnail work.
3. Run the focused tests listed in each plan.
4. Run Stage 7 verification after integration:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Global Acceptance

- After recording a video, the thumbnail slot shows a visible frame or an explicit, test-backed fallback instead of silently staying blank.
- Tapping the thumbnail opens the saved video with `video/*`.
- Returning to the app after a restart can preload the latest saved video when it is newer than the latest saved photo.
- During recording, the cockpit shows elapsed time in a stable compact format.
- No UI, coordinator, or adapter introduces a second hidden session kernel.
- No plan claims video filter/watermark burn-in is complete.
