# Package 04 - Real-Device Log Analysis Protocol

## Package ID

`04-real-device-log-analysis-protocol`

## Goal

Produce the practical real-device testing and analysis protocol for the new link/error logs: exact APK path expectations, install/export commands, flows to exercise, and how to read the exported timing records.

## Branch And Worktree

- Branch: `agent/performance-link-logs/04-real-device-log-analysis-protocol`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/04-real-device-log-analysis-protocol`
- Depends on: `03-dev-link-log-export`

## Allowed Paths

- `docs/plans/real-device-performance-link-logs-orchestration/**`
- `codex/documentation.md` only if the package lands implementation evidence that meaningfully changes current status

## Forbidden Paths

- Runtime source files.
- Test source files.
- Claiming real-device pass/fail without actual device evidence.
- Adding secrets, credentials, private account details, or raw media files.

## Required Protocol Content

Create or update a plan-local document such as `REAL_DEVICE_LOG_ANALYSIS_PROTOCOL.md` with:

- APK build path pattern and a copy-paste `adb install` command.
- In-app steps to export `LINK`, `ERROR`, and `ALL` logs.
- Device metadata to record: model, Android version, commit/build timestamp, test date.
- Flows to exercise: cold preview start, foreground resume, mode switch, zoom/lens threshold, focus tap, brightness adjustment, photo capture, video recording, and a recoverable/degraded path if safely reproducible.
- Analysis table template: correlation id, flow, slowest stage, duration, threshold/budget if present, suspected owner, evidence line, and next action.
- Rules for classifying performance evidence as preview/device/postprocess/save/UI-feedback/recovery.
- Stop conditions for device testing, such as overheating, camera unavailable, app crash, or repeated provider failures.

## Acceptance Criteria

- Protocol is self-contained enough for the user or a device-owning Codex session to run later.
- Protocol distinguishes implementation verification from real-device product confidence.
- Protocol includes expected log snippets or schema examples from package 03 output.
- Package records the exact local verification commands used to build the APK and check orchestration status.

## Verification

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh status
```

## Evidence To Record

- Protocol document path.
- APK output path reported by Gradle or best-known debug APK path.
- Install/export command snippets.
- Verification commands and results.
- Clear statement: real-device QA remains external-assist and has not passed unless evidence is attached.

## Tail Step

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 04-real-device-log-analysis-protocol
```
