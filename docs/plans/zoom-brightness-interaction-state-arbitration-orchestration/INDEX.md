# Zoom And Brightness Interaction State Arbitration — Orchestration Index

## Goal

研究并固化真机实测发现的两类回弹问题的优化方案：底部变焦控件需要显示节点数字，快速拖动不能先回退原位置再跳到结束位置；快捷亮度条快速拖动也不能被底层回传值覆盖而回弹。输出必须把 zoom UI 本地手势状态、Session Kernel optimistic state、Device Adapter applied/failed state、节流/确认状态区分清楚，形成可复用的交互状态策略，再决定是否进入实现修复。

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/zoom-brightness-arbitration/integration`
- Functional package branches: `agent/zoom-brightness-arbitration/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Research Baseline

- 当前仓库已有 `Zoom Cockpit V2 Productization` 调度包：[`../zoom-cockpit-v2-productization-orchestration/INDEX.md`](../zoom-cockpit-v2-productization-orchestration/INDEX.md)。本包不要重复做视觉大改，而是补上真机发现的状态竞争和节点数字问题。
- 当前 `FocalLengthSliderView` 会绘制点、轨道、thumb 和拖动浮层，但没有绘制节点数字；`ACTION_UP` 当前总是 snap 到最近 preset，和"连续拖动最终值"预期存在直接冲突。
- 当前 zoom wiring 中 `MainActivity` 在 `onRatioChanged` 和 `onRatioSnapped` 都 dispatch `SessionIntent.ApplyZoomRatio(...)`；`CockpitSurfaceRenderer.renderFocalLengthSlider()` 每次 render 都会把 session 当前 ratio 回写到 view。若快速拖动期间旧 render/旧 device echo 回来，容易出现视觉回退。
- 当前 Session Kernel 对 zoom 是 optimistic：`handleApplyZoomRatio()` 先更新 `activeDeviceGraph.preview.zoomRatio` 再发 `SessionEffect.ApplyZoomRatio`；zoom 没有 request id / applied ack 语义。它能提供跟手状态，但不能区分"用户正在拖动、请求中、设备已确认、旧请求已过期"。
- 当前快捷亮度已是 slider，`SessionCockpitRenderModel.brightnessRenderModel()` 在 `PreviewBrightnessFeedbackStatus.REQUESTED` 时优先显示 `requestedSteps`，并且 session/device 结果带 `requestId` 可过滤 stale ack。
- 亮度仍需复核：`CameraSessionCoordinator.latestPreviewBrightnessCommand(...)` 当前形态既启动一个 `previewBrightnessJob` dispatch，又把同一个 `DeviceCommand.ApplyPreviewBrightness` 返回给外层 dispatch，可能导致重复发送；它是本包必须核验的重点，不应只归因于 UI。
- 当前工作区存在大量未合并/未提交变更，包含 `AA/UU` 文件。本轮只新增调度文档，不改运行时代码；执行代理必须在自己的 worktree 中复核当前基线。

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
| 01-zoom-state-arbitration-audit | none | status | completed | 1 |
| 02-brightness-state-arbitration-audit | none | status | completed | 1 |
| 03-shared-control-state-strategy | 01-zoom-state-arbitration-audit, 02-brightness-state-arbitration-audit | status | completed | 2 |
| 04-verification-real-device-protocol | 03-shared-control-state-strategy | status | completed | 3 |
| 99-finalize | 01-zoom-state-arbitration-audit, 02-brightness-state-arbitration-audit, 03-shared-control-state-strategy, 04-verification-real-device-protocol | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01, 02, 03, 04
- Code dependency policy: status dependency — packages are research-only, no code merges needed; finalize verifies evidence completeness.
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

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/01-zoom-state-arbitration-audit.md` | 01-zoom-state-arbitration-audit | 02, 03, 04, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/02-brightness-state-arbitration-audit.md` | 02-brightness-state-arbitration-audit | 01, 03, 04, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/03-shared-control-state-strategy.md` | 03-shared-control-state-strategy | 01, 02, 04, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/04-verification-real-device-protocol.md` | 04-verification-real-device-protocol | 01, 02, 03, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/99-finalize.md` | 99-finalize | 01, 02, 03, 04 |
| `app/src/main/**`, `app/src/test/**`, `core/session/**`, `core/device/**` | read-only for all packages | no package may edit without new user approval |
| `docs/plans/zoom-cockpit-v2-productization-orchestration/**` | read-only reference | no package may edit without new user approval |
| `docs/plans/2026-05-25-quick-panel-*.md` | read-only reference | no package may edit without new user approval |

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-zoom-state-arbitration-audit.md](packages/01-zoom-state-arbitration-audit.md) | research/code audit agent | none | safe with 02 | Diagnose zoom node labels, drag rollback, session/device echo, and required contract changes |
| [02-brightness-state-arbitration-audit.md](packages/02-brightness-state-arbitration-audit.md) | research/code audit agent | none | safe with 01 | Diagnose quick brightness rebound, latest-wins dispatch, request id filtering, and UI render overwrite |
| [03-shared-control-state-strategy.md](packages/03-shared-control-state-strategy.md) | design agent | 01, 02 | no | Synthesize one reusable interaction-state strategy for zoom first, then brightness |
| [04-verification-real-device-protocol.md](packages/04-verification-real-device-protocol.md) | QA planning agent | 03 | no | Define local gates, trace checks, and real-device fast-drag smoke |
| [99-finalize.md](packages/99-finalize.md) | orchestrator | all functional packages | — | Final cross-package verification, integration, and report |

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit it without this repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first.
- Do not use `--dangerously-skip-permissions`.
