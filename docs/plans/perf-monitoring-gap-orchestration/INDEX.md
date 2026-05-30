# Performance Monitoring Gap Orchestration - Index

## Goal

Implement the 10 monitoring gaps (G1-G10) identified in the performance analysis report, using the existing `PerformanceLinkRecorder` infrastructure (merged from `real-device-performance-link-logs-orchestration`).

The gaps are grouped into 4 functional packages by code ownership and priority:

| Package | Gaps | Priority | Key Files |
|---|---|---|---|
| 01-capture-latency-timing | G1 (Shutter-to-Capture) | High | `CaptureRecordingSessionProcessor.kt`, `DefaultCameraSession.kt` |
| 02-switch-latency-timing | G2 (Mode Switch), G3 (Lens Switch) | High | `DefaultCameraSession.kt` |
| 03-pipeline-timing | G4 (Post-processor), G5 (File I/O), G8 (Video Start) | Medium | `MediaPostProcessors.kt`, `CameraXCaptureAdapter.kt` |
| 04-runtime-metrics | G6 (Preview FPS), G7 (Algorithm Queue), G9 (Video Quality), G10 (Memory) | Medium/Low | `core/media`, `app/camera` |

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/perf-monitoring-gap/integration`
- Functional package branches: `agent/perf-monitoring-gap/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/perf-monitoring-gap/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; inside assigned worktrees, Gradle must use `rtk ./scripts/run_isolated_gradle.sh ...`.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:

- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:

- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-capture-latency-timing | none | status | completed | 1 |
| 02-switch-latency-timing | none | status | completed | 1 |
| 03-pipeline-timing | none | status | completed | 1 |
| 04-runtime-metrics | none | status | completed | 1 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-capture-latency-timing -> 02-switch-latency-timing -> 03-pipeline-timing -> 04-runtime-metrics`
- Code dependency policy: status dependency (all packages independent, merge order is for integration branch cleanliness)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Landing Strategy

- **Primary landing path**: All 4 functional packages complete, all 10 gaps (G1-G10) instrumented with `PerformanceLinkRecorder`, integration verification passes, merged to mainline.
- **Preapproved fallback paths**:
  1. If G1/G2/G3 (high priority) complete but G4-G10 fail: merge high-priority packages only, record medium/low as follow-up.
  2. If any single package fails but others pass: merge completed packages as independent merge candidates.
- **Unacceptable degradation**: Shipping timing instrumentation without monotonic elapsed time (must use `System.nanoTime()`, not `System.currentTimeMillis()`).
- **Abort conditions**: If `PerformanceLinkRecorder` infrastructure is found to be broken or incompatible, abort and investigate.
- **Independent merge candidates if main plan fails**: All 4 functional packages are independent — each touches different code paths and can be verified standalone.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- Abort condition in Landing Strategy is met.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-capture-latency-timing | autonomous | Claude Code | n/a | focused tests + assembleDebug | none | normal graph |
| 02-switch-latency-timing | autonomous | Claude Code | n/a | focused tests + assembleDebug | none | normal graph |
| 03-pipeline-timing | autonomous | Claude Code | n/a | focused tests + assembleDebug | none | normal graph |
| 04-runtime-metrics | autonomous | Claude Code | n/a | focused tests + assembleDebug | none | normal graph |
| real-device-perf-qa | external-assist | user/device owner | requires physical camera device | APK path, install command, exported LINK logs | exported timing data after real-device testing | final product confidence only |

## Existing Infrastructure

The following infrastructure is already merged to main (from `real-device-performance-link-logs-orchestration`):

- `PerformanceLinkEvent` — data class for timing records with flow/stage/correlationId/duration
- `PerformanceLinkRecorder` — interface with `startSpan()`/`completeSpan()`/`recordEvent()`
- `InMemoryPerformanceLinkRecorder` — implementation using `System.nanoTime()`
- `linkRecorder` wired through `AppContainer` → `DefaultCameraSession` → processors
- Existing link spans: preview startup, recovery, lens switch, zoom, focus, brightness, capture/recording

All packages should use the existing `linkRecorder` infrastructure, not create parallel timing systems.
