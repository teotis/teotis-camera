# Package 02 — Zoom ScaleEnd Mainline Recovery

## Package ID
`02-zoom-scaleend-mainline-recovery`

## Goal
Recover the completed ScaleEnd zoom continuity fix from `.claude/worktrees/zoom-scaleend-sync` onto current `main`, so consecutive pinch zoom gestures keep the correct zoom basis.

## Context
- Previous audit failed because current `main` still has `ScaleEnd -> resetZoomAccumulation()`.
- Completed work exists in `.claude/worktrees/zoom-scaleend-sync` with commits `70014cd`, `ca89c21`, and `5acfbd6`.
- The package must land the behavior on active `main`, not only leave it in a side worktree.

## Implementation Scope
- Rebase, cherry-pick, or manually port the effective `ScaleEnd` fix and tests to current `main`.
- Preserve `localZoomRatio` on `ScaleEnd`; reset only throttling/timestamp state.
- Keep existing pinch clamp and dispatch throttling.
- Update only `status/02-zoom-scaleend-mainline-recovery.md` with fresh evidence.

## Acceptance Criteria
- [ ] Current `main` no longer resets `localZoomRatio` to 1x on `ScaleEnd`.
- [ ] A pinch ending at 2x followed by another pinch starts from approximately 2x.
- [ ] Existing clamp behavior remains 0.5x to 10x unless current capabilities specify otherwise.
- [ ] `FocalLengthSliderView` continues to render current session zoom from `state.activeDeviceGraph.preview.zoomRatio`.
- [ ] Focused tests cover consecutive pinch continuity and pass.
- [ ] Any existing `SessionCockpitRenderModelTest` failures are classified with evidence and are not caused by this package.

## Allowed Paths
- `app/src/main/java/com/opencamera/app/gesture/**`
- `app/src/test/java/com/opencamera/app/gesture/**`
- `docs/plans/real-device-hotfix-mainline-recovery/status/02-zoom-scaleend-mainline-recovery.md`

## Forbidden Paths
- `feature/mode-*/**`
- `core/effect/**`
- `core/settings/**`
- `core/session/**`
- `core/device/**`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
- `docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`
- Other packages' status files.

## Dependencies
- Depends on: none

## Parallel Safety
- safe
- Reason: gesture files are disjoint from watermark and shutter rendering files.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Zoom*'
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack
- [ ] Worktree path and branch.
- [ ] Before/after explanation of `ScaleEnd`.
- [ ] Git diff stat and changed files.
- [ ] Verification command results.
- [ ] Commit hash / PR link.
- [ ] Unresolved risks.
