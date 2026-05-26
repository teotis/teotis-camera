# Package 03 — Zoom Scale-End Sync

## Package ID
`03-zoom-scaleend-sync`

## Goal
Fix the remaining pinch zoom continuity issue and ensure the bottom focal-length UI stays synchronized with the active zoom ratio during and after two-finger zoom.

## Context
- User issue: two-finger zoom exists but preview is not smooth and bottom focal-length UI does not synchronize.
- Current validation finding: `GesturePolicy` has a local zoom accumulator and 16ms throttle, but `ScaleEnd` resets the accumulator to `1.0f`. Because `MainActivityActionBinder` syncs only before calling `map()`, `ScaleEnd` can erase the current zoom basis and make the next pinch start from 1x.
- Current validation finding: `FocalLengthSliderView` exists and is rendered from session zoom, but old zoom capsule exact-active logic remains in render models/tests.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GestureRouter.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GestureEvent.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- Non-goals:
  - Do not redesign CameraX zoom capabilities.
  - Do not change watermark or shutter capture data semantics.

## Implementation Scope
- Fix `ScaleEnd` so it preserves or resyncs the latest session zoom ratio instead of resetting the next gesture to 1x.
- Keep pinch updates smooth, bounded, and stable.
- Ensure bottom focal-length slider receives and displays continuous zoom changes after pinch.
- Retire or update old exact-match zoom capsule expectations if the slider is now the runtime UI.
- Add focused tests for consecutive pinch gestures separated by `ScaleEnd`.

## Acceptance Criteria
- [ ] A pinch ending at 2x followed by a second pinch starts from approximately 2x, not 1x.
- [ ] Pinch zoom dispatch remains throttled around 16ms or otherwise demonstrably smooth.
- [ ] Zoom ratio remains clamped to supported range.
- [ ] Bottom `FocalLengthSliderView` is updated from `state.activeDeviceGraph.preview.zoomRatio`.
- [ ] The slider does not snap during pinch updates; snap-on-release applies only to direct slider interaction.
- [ ] Tests cover `ScaleEnd` continuity and slider/current-ratio sync.
- [ ] Existing gesture tests pass.

## Allowed Paths
- `app/src/main/java/com/opencamera/app/gesture/**`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/res/layout/activity_main.xml`
- Focused tests under `app/src/test/java/com/opencamera/app/gesture/**` and `app/src/test/java/com/opencamera/app/*RenderModelTest.kt`

## Forbidden Paths
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/session/**`
- `core/device/**`
- `feature/mode-*/**`
- `core/effect/**`
- `core/settings/**`

## Dependencies
- Depends on: none

## Parallel Safety
- caution
- Reason: may touch `CockpitSurfaceRenderer.kt`, which package 01 can also touch for shutter visual rendering. Coordinate if both edit adjacent sections.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Zoom*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack
- [ ] Worktree path and branch.
- [ ] Explanation of `ScaleEnd` behavior before/after.
- [ ] Git diff stat and changed files.
- [ ] Tests proving consecutive pinch continuity.
- [ ] Verification command results.
- [ ] Commit hash / PR link.
- [ ] Unresolved risks.
