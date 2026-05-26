# Vivo X300 Pixel Capability And Shutter Lifecycle — Orchestration Index

## Goal

研究并固化两个真机问题的优化方案：vivo X300 级 48MP/50MP 硬件在快捷像素里只暴露约 13MP 且不可切换，以及普通拍照后快门按钮恢复过慢。输出不是直接改运行时代码，而是把 device adapter 能力枚举、CameraX/Camera2 输出尺寸选择、快捷像素映射、capture lifecycle 分层、UI gating 和真机验收拆成可执行证据包；最终由 99-finalize 判断是否进入实现修复。

## User Entry Points

- **Manual**: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- **Script**: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- **Status**: run `bash launchers/orchestrate.sh status`.
- **Retry**: run `bash launchers/orchestrate.sh retry <package-id>` (valid targets: `01-pixel-capability-enumeration`, `02-quick-pixel-surface-design`, `03-shutter-lifecycle-contract`, `04-real-device-verification-protocol`).
- **Manual advancement fallback**: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration`
- Mainline branch: `main`
- Integration branch: `integration/vivo-x300-pixel-shutter-lifecycle`
- Functional package branches: `agent/vivo-x300/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Research Baseline

- 当前代码已经有 native still output size 概念：`DeviceCapabilities.availableStillCaptureOutputSizes`、`StillCaptureOutputSize`、`StillCaptureConfig.outputSize`、`SessionStateRender.displayedStillCaptureOutputSize(...)` 和 `DefaultCameraSession.nextStillCaptureOutputSize(...)`。
- 当前 Camera2 探测只从 `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(ImageFormat.JPEG)` 建立 still output sizes，并经过 `normalizeStillCaptureOutputSizes(...)` 的 4:3 优先过滤；这可能解释真机最高只出现 13MP，而不是完整 48MP/50MP 档。
- 当前 CameraX bind 使用 `ResolutionSelector` + target output size + `PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE`；研究必须确认这是否足够触发设备的超高像素/maximum-resolution 路径。
- 当前快门链路已经有 `DeviceEvent.DataReceived -> SessionIntent.DataReceived`，但 `shutterDisabledReason(...)` 仍在 photo `activeShot`、`CaptureStatus.SAVING`、`CaptureStatus.DATA_RECEIVED` 时禁用快门；`CameraXCaptureAdapter.emitShotCompleted(...)` 又把 media postprocess 放在 `ShotCompleted` 前，说明"取帧完成"和"处理/保存完成"仍被 UI gating 绑在一起。
- Claude Code local version checked for launcher design: `2.1.142 (Claude Code)`. Official Anthropic CLI reference confirms the general CLI flag surface, but local/project prior docs note Claude Code 2.1 supports `claude --bg --name` and `claude agents`; generated scripts keep the same repository pattern and omit `--permission-mode` by default. Source: https://docs.anthropic.com/en/docs/claude-code/cli-usage

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
| 01-pixel-capability-enumeration | none | status | completed | 1 |
| 03-shutter-lifecycle-contract | none | status | completed | 1 |
| 02-quick-pixel-surface-design | 01-pixel-capability-enumeration | status | completed | 2 |
| 04-real-device-verification-protocol | 01-pixel-capability-enumeration, 02-quick-pixel-surface-design, 03-shutter-lifecycle-contract | status | completed | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01, 03, 02, 04
- Code dependency policy: status dependency (packages are research/design only; no code branches to merge)
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

## Stop Gates — Must Ask

STOP and ask the user before:
- Editing runtime code or tests. This package is research/design only.
- Crossing Stage boundaries or declaring Stage 7 complete.
- Adding network dependencies, SDK keys, secrets, or external API calls.
- Using vendor-private APIs or claiming vivo-specific support without real-device evidence.
- Treating desktop/unit-test evidence as final vivo X300 acceptance.
- Changing capture semantics in a way that allows overlapping captures in night, multi-frame, high-pixel, recording, or unstable device states.
- Touching another package's status file or editing INDEX.md from a package agent.
- Running destructive git operations: force-push, hard reset, deleting branches/worktrees.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` — do NOT edit `INDEX.md`.
- Report findings, source/code evidence, commands run, acceptance criteria status, and unresolved risks.
- Do NOT delete any worktree unless explicitly instructed.
- Do NOT implement fixes unless the user separately authorizes implementation.

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/01-pixel-capability-enumeration.md` | 01-pixel-capability-enumeration | 02, 03, 04, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/02-quick-pixel-surface-design.md` | 02-quick-pixel-surface-design | 01, 03, 04, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/03-shutter-lifecycle-contract.md` | 03-shutter-lifecycle-contract | 01, 02, 04, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/04-real-device-verification-protocol.md` | 04-real-device-verification-protocol | 01, 02, 03, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/99-finalize.md` | 99-finalize | 01, 02, 03, 04 |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` | read-only for 01/02/04 | no package may edit without new user approval |
| `core/device/**`, `core/media/**`, `core/session/**` | read-only for all packages | no package may edit without new user approval |
| `app/src/main/java/com/opencamera/app/SessionStateRender.kt`, `SessionCockpitRenderModel.kt`, `CameraCockpitRenderModel.kt`, `MainActivityActionBinder.kt` | read-only for 02/03/04 | no package may edit without new user approval |
| `app/src/test/**`, `core/*/src/test/**` | read-only for all packages | no package may edit without new user approval |

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-pixel-capability-enumeration.md](packages/01-pixel-capability-enumeration.md) | research agent | none | safe with 03 | Audit still-output capability enumeration and design a truthful high-pixel capability contract |
| [02-quick-pixel-surface-design.md](packages/02-quick-pixel-surface-design.md) | design agent | 01 | no | Map capability evidence into quick pixel UI/session semantics |
| [03-shutter-lifecycle-contract.md](packages/03-shutter-lifecycle-contract.md) | architecture/design agent | none | safe with 01 | Separate frame acquisition, postprocess/save, and UI shutter re-arm semantics |
| [04-real-device-verification-protocol.md](packages/04-real-device-verification-protocol.md) | QA planning agent | 01, 02, 03 | no | Define vivo X300 real-device evidence protocol and timing thresholds |
| [99-finalize.md](packages/99-finalize.md) | orchestrator | all functional packages | — | Final cross-package verification, integration, and PASS/PARTIAL/FAIL decision |

## Agent Budget

- Recommended Claude Code agents: 4 research/design agents + finalize
- Max parallel agents: 2
- Finalize usage: final audit and real-device/product judgment
- When to pause: if package 01 proves the current Android/CameraX stack cannot access high-pixel still output on vivo X300 without vendor-private APIs, pause before designing UI switching as supported.

## Claude Background Permission Notes

- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit it without this repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first.
- Do not use `--dangerously-skip-permissions`.
