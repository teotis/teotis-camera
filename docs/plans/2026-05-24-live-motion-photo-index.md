# 2026-05-24 Live Motion Photo Handoff Index

> For text-only agents: this is the master handoff for implementing Photo mode Live capability with the Google Motion Photo container format. Pick one linked plan, keep edits scoped, and run every shell command through `rtk`.

## Source Request

User asks for Photo mode Live capability, using Google's Motion Photo format. Product feel can learn from Apple Live Photos and flagship vivo/oppo implementations, but the proposed engineering direction is app-owned preview stream frames as the short motion segment.

## Stage And Scope Boundary

- Current repository stage is Stage 7, stability governance and automation hardening.
- This handoff designs feature work only. Implementation should start only after the user explicitly authorizes returning to feature work or a 2.0 capability-kernel landing pass.
- No plan below requires multimodal judgment. Non-multimodal agents can implement contracts, byte containers, fake-frame tests, diagnostics, and deterministic app behavior.
- Motion smoothness, visual alignment, watermark beauty, audio quality, and product-polish comparison against Apple/vivo/oppo need later real-device or multimodal QA.

## Current-Code Evidence

- Live is not an empty feature. The repo already has `ShotKind.LIVE_PHOTO`, `CaptureStrategy.LivePhoto`, `LivePhotoCaptureSpec`, `LivePhotoBundle`, `LiveTemporalWindow`, `LiveBundleStatus`, `LiveMotionSource`, `planLiveTemporalAssembly()`, and tests under `core/media`.
- `PhotoModePlugin`, `PortraitModePlugin`, and `HumanisticModePlugin` already switch to `CaptureStrategy.LivePhoto` when `PhotoSettings.livePhotoEnabledByDefault` is true.
- `CameraXCaptureAdapter.captureLivePhoto()` currently captures the primary still, creates a sidecar JSON, and hardcodes `LiveMotionSource.METADATA_ONLY`. That means current Live is intentionally still-only fallback, not real motion.
- `SessionPresentationState.latestLivePhotoBundle` is set only for temporal or degraded-motion bundles. Still-only fallback is visible as `Live photo saved (still only)` and is not stored as a usable bundle.
- `FrameStreamContracts.kt` already defines descriptors, payload access, payload references, and buffer policies, but there is no real app-side ring buffer or CameraX `ImageAnalysis` feed wired into Live.
- `OcwmJpegContainer.kt` shows this project is willing to do deterministic JPEG container work in `core:media`; Motion Photo should use its own Google-compatible writer rather than mixing with the OCWM reversible-watermark archive format.

## Google Motion Photo Format Facts

Primary source: [Android Developers Motion Photo format 1.0](https://developer.android.com/media/platform/motion-photo-format?hl=en).

Important engineering facts from the official specification:

- A Motion Photo is a single file containing a primary still image plus an appended short video.
- The primary image carries Camera XMP metadata and Container XMP metadata.
- Modern format uses `Camera:MotionPhoto`, `Camera:MotionPhotoVersion`, `Camera:MotionPhotoPresentationTimestampUs`, `Container:Directory`, and `Item:Length`.
- Older `MicroVideoOffset` metadata is deleted by this spec and should not be the primary writer target.
- The `MotionPhoto` media item must be the final appended item; no bytes may follow it.
- Supported still types include JPEG, HEIC, and AVIF. For this repo's first pass, write JPEG Motion Photo only because still capture output is currently JPEG.

## Decomposition

Implement in this order:

1. [Preview ring-buffer motion source](./2026-05-24-live-motion-photo-preview-ring-buffer.md)
   - Adds bounded app-owned preview frame buffering and deterministic selection around shutter time.
   - Produces a motion-source plan and diagnostic notes, but can initially emit fake/file-reference frames in tests.

2. [Google Motion Photo container writer](./2026-05-24-live-motion-photo-google-container.md)
   - Converts a saved JPEG still plus an MP4 motion segment into a single Google-compatible `_MP.jpg` style output with XMP container metadata.
   - Pure byte tests can validate XMP fields and appended MP4 placement without camera hardware.

3. [Session, media, and product integration](./2026-05-24-live-motion-photo-session-integration.md)
   - Wires the motion source and Google container into `CameraXCaptureAdapter.captureLivePhoto()`.
   - Updates capability/degraded semantics, session diagnostics, UI labels, save cleanup, and verification script.

## Recommended Architecture

Use a two-stage artifact model:

```text
Mode Plugin
  declares CaptureStrategy.LivePhoto

Session Kernel
  owns admission, shot lifecycle, latest bundle state, diagnostics

Device Adapter
  owns CameraX still capture and preview frame source binding

Media Pipeline
  owns temporal plan, motion segment assembly, Google Motion Photo container, cleanup
```

The first stable output should be:

```text
primary JPEG still + low-res preview-derived MP4 segment + Google Motion Photo XMP + appended MP4 bytes
```

Fallbacks must be explicit:

- `COMPLETE`: still + motion MP4 + Google Motion Photo file succeed.
- `DEGRADED_MOTION`: still succeeds and a short/low-res/post-shutter-only motion segment exists.
- `STILL_ONLY_FALLBACK`: still succeeds but motion/container cannot be produced.
- `UNSUPPORTED`: still capture or storage cannot support Live semantics.

## Why Preview Stream First

Preview stream buffering is the right first implementation for this app:

- It matches the user's proposed direction.
- It avoids pretending OpenCamera has vendor system-camera privileges.
- It is compatible with public Android APIs through CameraX `ImageAnalysis` and conservative low-resolution buffers.
- It fits the existing `FrameStreamContracts.kt`, `LiveTemporalAssemblyPlanner.kt`, and capability-kernel docs.
- It can be tested with fake descriptors and byte fixtures before real-device tuning.

Do not implement a background full-quality recorder as the first pass. CameraX `VideoCapture` plus `ImageCapture` concurrency can conflict, increases shutter lag, and belongs behind an optional future capability.

## Global Acceptance

- Photo mode with Live enabled no longer produces only `METADATA_ONLY` when a preview frame source is available.
- Saved Live output has a primary still and a playable MP4 motion segment.
- Google Motion Photo writer creates one JPEG-based Motion Photo file with `Camera:MotionPhoto=1`, version `1`, Container directory, primary item, and final MotionPhoto item length.
- Failure after primary still does not delete the still unless the save transaction itself is corrupt; motion/container failure degrades visibly.
- `latestLivePhotoBundle` is only set for temporal media.
- Still-only fallback remains honest in UI and diagnostics.
- Raw frame data never enters `SessionState`, UI render models, or mode plugins.
- Stage verification continues to pass after feature-specific verification.

## Suggested Final Verification

Run focused commands first:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest --tests com.opencamera.core.media.FrameStreamContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.MotionPhotoJpegContainerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Then run the Live script after it is updated:

```bash
rtk ./scripts/verify_stage_6b7_live_photo.sh
```

If this work lands during Stage 7 governance, also run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not claim Apple Live Photo parity.
- Do not claim vivo/oppo-level visual quality or stabilization.
- Do not add audio to the first preview-buffer implementation.
- Do not write HEIC/AVIF Motion Photo in the first pass.
- Do not expose frame bytes through session state or UI.
- Do not make `CameraSessionCoordinator` a second Live orchestration kernel.
