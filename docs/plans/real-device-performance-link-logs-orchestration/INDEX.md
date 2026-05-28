# Real Device Performance Link Logs - Orchestration Index

## Goal

Build a performance-aware development log path for future real-device testing:

- The Dev panel's link-flow log must carry part of the performance-analysis responsibility, with exported records that include wall-clock time, monotonic elapsed time points, durations, flow/stage names, status, and correlation ids.
- The app must have a small reusable performance/timing recording system for core flows, so preview startup, capture, recording, recovery, zoom/lens switching, and other high-value paths can be measured consistently and printed into link logs.
- Error logs and link logs must be exportable in a form that a human or agent can analyze after real-device testing without pretending that local unit tests are real-device evidence.

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/performance-link-logs/integration`
- Functional package branches: `agent/performance-link-logs/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; inside assigned worktrees, Gradle must use `rtk ./scripts/run_isolated_gradle.sh ...`.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- resolve unrelated main-checkout conflicts
- record raw image/frame bytes, private media contents, tokens, credentials, or hidden prompts in exported logs
- claim real-device performance acceptance from unit tests, emulator checks, or desktop inspection alone

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-performance-timing-contract | none | status | initial ready package | 1 |
| 02-core-flow-instrumentation | 01-performance-timing-contract | code | package 01 completed and branch available | 2 |
| 03-dev-link-log-export | 01-performance-timing-contract, 02-core-flow-instrumentation | code | packages 01 and 02 completed and branch available | 3 |
| 04-real-device-log-analysis-protocol | 03-dev-link-log-export | status+code | package 03 completed | 4 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-performance-timing-contract -> 02-core-flow-instrumentation -> 03-dev-link-log-export -> 04-real-device-log-analysis-protocol`
- Code dependency policy: packages are intentionally staged because later packages consume the timing contract and exported format. Downstream packages should base on the integration branch or recorded upstream branch if the orchestrator provides it.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- A package moves camera runtime ownership into UI, creates a hidden second session kernel, or directly drives CameraX from mode plugins/UI.
- Timing records use wall-clock time for duration calculations instead of monotonic elapsed time.
- Exported logs omit correlation ids or stage names for core flows.
- Exported logs include raw frame/image/media payloads or sensitive user data.

## Current Evidence Snapshot

- Existing `SessionTraceEvent` already records `sequence`, `name`, `detail`, and wall-clock `timestampMillis`.
- Existing diagnostics already expose `PerfSnapshot`, first-frame budget status, and recovery trace through `SessionDiagnostics.kt` and `SessionUiRenderModel.kt`.
- Existing capture pipeline already produces `ShotTiming` with requested/device/postprocess elapsed timestamps, and `CaptureRecordingSessionProcessor` records `capture.timing` / `recording.timing`.
- Existing Dev logs support `KEY`, `CORE`, `ERROR`, and `ALL` tabs, export files through `DevLogExporter`, cap local debug-log storage at 20 MB, and include core summaries/resource diagnostics.
- The current gap is that timing is still point-specific rather than a reusable link-flow performance record system, and export output is not yet optimized for after-the-fact real-device performance/error analysis.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| [01-performance-timing-contract.md](packages/01-performance-timing-contract.md) | Define reusable monotonic performance/link timing records and tests | `core/session`, `core/media`, focused unit tests |
| [02-core-flow-instrumentation.md](packages/02-core-flow-instrumentation.md) | Instrument high-value runtime flows using the shared contract | session processor, CameraX adapter/coordinator, focused tests |
| [03-dev-link-log-export.md](packages/03-dev-link-log-export.md) | Add a Dev link-flow log/export surface and error-analysis friendly sections | app render model/exporter/Dev UI/tests |
| [04-real-device-log-analysis-protocol.md](packages/04-real-device-log-analysis-protocol.md) | Produce APK path, adb/log export instructions, and analysis checklist | plan docs and verification evidence only |
| [99-finalize.md](packages/99-finalize.md) | Merge, verify, report, and clean up recorded resources after success | integration branch and coordinator files |

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-performance-timing-contract | autonomous | Claude Code | n/a | contract tests | none | normal graph |
| 02-core-flow-instrumentation | autonomous | Claude Code | n/a | focused session/app tests | none | normal graph |
| 03-dev-link-log-export | autonomous | Claude Code | n/a | Dev log render/export tests and build | none | normal graph |
| 04-real-device-log-analysis-protocol | agent-verifiable substitute | Claude Code | cannot execute physical-device testing locally | APK path, install/export commands, checklist, expected log schema | none for implementation; later device logs for confidence | normal graph |
| real-device-performance-qa | external-assist | user/Codex device owner | requires physical camera device, real app interaction, and human/device-owner evidence collection | local tests, assembled APK, exported log schema, adb commands | exported `LINK`/`ERROR` logs, device model/Android version, optional `adb logcat`, pass/fail notes for preview/capture/recording/recovery flows | final product confidence only unless user later makes it release-blocking |

## Real-Device Evidence Contract

The final implementation may be merged after local verification if the user chooses, but product acceptance remains incomplete until a device owner records:

- APK path and install command used.
- Device model, Android version, app build timestamp or commit.
- Exported Dev `LINK` log after preview startup, capture, recording, zoom/lens switch, focus/brightness, and recovery-like flows when reproducible.
- Exported Dev `ERROR` log after at least one known failure/degraded path if available.
- Short analysis notes identifying slowest stage, stage duration, correlation id, and whether the issue is preview, device capture, postprocess, media save, UI feedback, or recovery.
