# Package 03 - Pinch Zoom Basis Repair - Status

## Result: PASS

## Worktree

- path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/03-pinch-zoom-basis-repair`
- branch: `agent/zoom-brightness-rollback/03-pinch-zoom-basis-repair`
- base commit: `3befe77` (from `agent/zoom-brightness-rollback/01-zoom-slider-render-latch`)
- commit hash: `0ecc9ee`

## Changed Files

- `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
- `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`

## Diff Stat

```
 app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt     | 2 +-
 app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt | 7 ++-----
 2 files changed, 3 insertions(+), 6 deletions(-)
```

## Implementation Summary

**Root cause**: `GesturePolicy.map(ScaleEnd)` called `resetZoomAccumulation()` which set `localZoomRatio = 1.0f`. In the binder flow, `syncZoomRatio(currentZoom)` runs before `map()` for non-pinch events, but `map(ScaleEnd)` then overwrote it back to `1.0f`. The next pinch started from `1.0f` instead of the current session zoom.

**Fix**: Changed `ScaleEnd` handler to only reset `lastPinchTimestamp` (allowing the next pinch to dispatch immediately) without resetting `localZoomRatio`. The binder's `syncZoomRatio(currentZoom)` on non-pinch events ensures the basis reflects the current session zoom.

**Test updates**:
- Removed redundant manual `syncZoomRatio()` calls from continuity tests (the production path no longer needs them after `ScaleEnd`).
- Updated `scaleEnd resets zoom accumulation` → `scaleEnd preserves zoom accumulation for next pinch`: after `ScaleEnd`, a new pinch with scale 2.0f now correctly produces 4.0f (accumulated from 2.0f basis) instead of 2.0f (from reset 1.0f basis).

## Verification

```
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
```

Result: BUILD SUCCESSFUL — all GesturePolicyTest tests passed.

## Remaining Real-Device Pinch Smoke Risk

- Low. The fix is isolated to gesture-layer zoom basis handling. Within-gesture accumulation and throttling are unchanged. The binder's sync-before-map pattern for non-pinch events ensures the basis is always current when a new pinch starts.
