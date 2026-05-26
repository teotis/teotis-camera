# Package 03 - Pinch Zoom Basis Repair

## Package ID

`03-pinch-zoom-basis-repair`

## Goal

Repair preview pinch zoom basis handling so a new pinch starts from the current session zoom ratio, not from `1.0f` or a stale local value after `ScaleEnd`.

## Current Evidence

- `GesturePolicy` stores `localZoomRatio`.
- `ScaleEnd` currently calls `resetZoomAccumulation()`, which sets `localZoomRatio = 1.0f`.
- `MainActivityActionBinder` calls `gesturePolicy.syncZoomRatio(currentZoom)` for non-pinch events before mapping, but `GesturePolicy.map(ScaleEnd)` then resets it back to `1.0f`.
- `GesturePolicyTest` manually calls `syncZoomRatio(...)` after `ScaleEnd` in continuity tests; actual binder flow does not do that after the reset.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/gesture/GestureEvent.kt`
- `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
- `app/src/main/java/com/opencamera/app/gesture/GestureRouter.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` preview gesture binding only
- `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`

## Forbidden Paths

- `core/session/**`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- quick brightness code
- CameraX adapter/coordinator except read-only
- `docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
- another package's status file

## Dependencies

- Depends on: `01-zoom-slider-render-latch`

## Implementation Requirements

1. Define the correct gesture boundary.
   - Prefer explicit `ScaleStart` if `ScaleGestureDetector.onScaleBegin` can emit it.
   - Otherwise ensure the first `PinchZoom` after `ScaleEnd` syncs from `currentZoomRatio`.
2. Preserve continuous within-gesture accumulation.
   - Multiple scale events in one gesture should accumulate smoothly.
3. Do not dispatch spurious session intents on `ScaleStart` / `ScaleEnd`.
4. Update tests to match actual binder flow.
   - Test consecutive pinches without manual post-ScaleEnd sync if the production path no longer needs it.
   - Test that new pinch starts from the supplied current zoom ratio.
5. Keep zoom capability clamping in Session/Device semantics; gesture layer can use conservative local clamps but must not become the runtime owner.

## Acceptance Criteria

- Consecutive pinch gestures start from the current session zoom basis.
- `ScaleEnd` does not force the next pinch back to `1.0f`.
- Within-gesture accumulation still works and remains throttled.
- Focused gesture tests pass.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
```

## Expected Evidence

- worktree path
- branch
- base commit
- commit hash
- changed files
- diff stat
- focused test output summary
- any remaining real-device pinch smoke risk

