# Real Device UI Regression Acceptance QA

## Goal

Validate the implemented repair against the original real-device complaints: shutter color/visibility, preview vertical placement, and Document mode availability.

## Context

- This package is for Codex/user acceptance after an implementation agent completes [Capture, Preview, And Mode Track Repair](./2026-05-25-capture-preview-mode-track-repair.md).
- Non-multimodal agents should not mark this complete from code inspection alone; the key acceptance is visual and interaction behavior on device.
- Relevant app areas:
  - main camera screen in portrait orientation;
  - bottom shutter deck;
  - primary mode switcher;
  - still capture flow through preview feedback and final saved photo.

## Implementation Scope

- No code changes are required by this QA package unless acceptance fails.
- Collect concise evidence: screenshots before capture, immediately after shutter tap, while saving if visible, after saved media appears, and mode-track view showing Document.

## Steps

1. Install or launch the repaired build on the target real device.
2. Open the main camera screen in portrait orientation.
3. Observe the shutter before tapping:
   - color;
   - size and position;
   - no default purple Material tint.
4. Tap shutter once.
5. Observe the shutter immediately and until the final saved photo/thumbnail appears:
   - it remains visible and in place;
   - it does not turn purple;
   - it does not collapse/disappear;
   - if a second tap is blocked, the app gives a clear disabled reason.
6. Observe preview placement:
   - visible preview/capture frame sits above the mode track;
   - mode track does not visually eat into the preview in a way that makes framing feel too low.
7. Scroll or inspect the mode track:
   - `Photo`, `Humanistic`, `Video`, and `Document`/`Doc` are visible or reachable;
   - selecting `Document` switches to Document mode and the mode state/copy changes accordingly.
8. Repeat once after app restart to catch state restoration or first-render issues.

## Acceptance Criteria

- Shutter color is neutral camera-control white/gray for still capture, red only for recording state.
- Shutter remains visible from tap through final photo availability.
- User is not left with a missing primary capture control while processing is ongoing.
- Preview placement feels intentionally above the mode track in portrait orientation.
- Document mode is visible/reachable and switches successfully.
- No new obvious regression appears in Humanistic, Photo, or Video mode entry visibility.

## Verification Commands

Run these after device QA if implementation changed code since the last local verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Real-device screenshot/video evidence is the authority for color and perceived vertical placement.
- If the shutter is visible but disabled during save, that can be acceptable only when duplicate capture is unsupported and the disabled reason is clear.
- If Document appears in render-model tests but not on device, inspect `CockpitSurfaceRenderer.renderModeTrack()` first; index-based button assignment was the verified failure pattern.
