# Package 03 - Dev Link Log Export

## Package ID

`03-dev-link-log-export`

## Goal

Make Dev logs useful for real-device performance/error analysis by adding a link-flow view/export section that surfaces package 01/02 timing records with timestamps, durations, correlation ids, and error/degraded context.

## Branch And Worktree

- Branch: `agent/performance-link-logs/03-dev-link-log-export`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/03-dev-link-log-export`
- Depends on: `01-performance-timing-contract`, `02-core-flow-instrumentation`

## Allowed Paths

- `app/src/main/java/com/opencamera/app/DevLogRenderModel.kt`
- `app/src/main/java/com/opencamera/app/DevLogExporter.kt`
- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity*.kt`
- `app/src/main/res/**`
- `app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/DevLogExporterTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Forbidden Paths

- CameraX runtime behavior.
- Session/device ownership changes.
- Raw frame/image/media data in logs.
- Changing the 20 MB storage cap unless a focused test proves the new policy and the package records the reason.

## Required Design Constraints

- Add a first-class link-flow log surface. A `DevLogTab.LINK` tab is acceptable if it fits the current UI; otherwise add a clearly separated `=== LINK FLOW EVENTS ===` export section while preserving existing tabs.
- Export output must be grep/agent-friendly and include enough columns/fields for analysis: sequence, wall timestamp if available, elapsed time point, flow, stage, status, correlation id, duration, and detail.
- Error/degraded events should be cross-referenceable with link events by correlation id or nearby sequence.
- The export must still include `KEY`, `CORE`, `ERROR`, `ALL`, and core summary sections unless product code already has a better compatible structure.
- Keep release-build behavior unchanged unless the current product policy explicitly exposes Dev logs in release-like builds.

## Acceptance Criteria

- Dev log render tests cover the link-flow view/section and prove timing details appear in export.
- Dev log export tests cover type headers for the new tab/type if a new `DevLogTab` value is introduced.
- Summary text mentions the latest link timing in a useful, compact way.
- Existing Dev log tests continue to pass.

## Verification

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Evidence To Record

- Changed files.
- Export format sample with a preview or capture link record.
- Screenshot-free explanation of how a tester exports `LINK` and `ERROR` logs.
- Verification commands and results.
- Remaining real-device evidence needs.

## Tail Step

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 03-dev-link-log-export
```
