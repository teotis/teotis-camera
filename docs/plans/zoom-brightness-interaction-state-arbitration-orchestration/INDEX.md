# Zoom And Brightness Interaction State Arbitration — Orchestration Index

## Goal

研究并固化真机实测发现的两类回弹问题的优化方案：底部变焦控件需要显示节点数字，快速拖动不能先回退原位置再跳到结束位置；快捷亮度条快速拖动也不能被底层回传值覆盖而回弹。输出必须把 zoom UI 本地手势状态、Session Kernel optimistic state、Device Adapter applied/failed state、节流/确认状态区分清楚，形成可复用的交互状态策略，再决定是否进入实现修复。

## Research Baseline

- 当前仓库已有 `Zoom Cockpit V2 Productization` 调度包：[`../zoom-cockpit-v2-productization-orchestration/INDEX.md`](../zoom-cockpit-v2-productization-orchestration/INDEX.md)。本包不要重复做视觉大改，而是补上真机发现的状态竞争和节点数字问题。
- 当前 `FocalLengthSliderView` 会绘制点、轨道、thumb 和拖动浮层，但没有绘制节点数字；`ACTION_UP` 当前总是 snap 到最近 preset，和“连续拖动最终值”预期存在直接冲突。
- 当前 zoom wiring 中 `MainActivity` 在 `onRatioChanged` 和 `onRatioSnapped` 都 dispatch `SessionIntent.ApplyZoomRatio(...)`；`CockpitSurfaceRenderer.renderFocalLengthSlider()` 每次 render 都会把 session 当前 ratio 回写到 view。若快速拖动期间旧 render/旧 device echo 回来，容易出现视觉回退。
- 当前 Session Kernel 对 zoom 是 optimistic：`handleApplyZoomRatio()` 先更新 `activeDeviceGraph.preview.zoomRatio` 再发 `SessionEffect.ApplyZoomRatio`；zoom 没有 request id / applied ack 语义。它能提供跟手状态，但不能区分“用户正在拖动、请求中、设备已确认、旧请求已过期”。
- 当前快捷亮度已是 slider，`SessionCockpitRenderModel.brightnessRenderModel()` 在 `PreviewBrightnessFeedbackStatus.REQUESTED` 时优先显示 `requestedSteps`，并且 session/device 结果带 `requestId` 可过滤 stale ack。
- 亮度仍需复核：`CameraSessionCoordinator.latestPreviewBrightnessCommand(...)` 当前形态既启动一个 `previewBrightnessJob` dispatch，又把同一个 `DeviceCommand.ApplyPreviewBrightness` 返回给外层 dispatch，可能导致重复发送；它是本包必须核验的重点，不应只归因于 UI。
- 当前工作区存在大量未合并/未提交变更，包含 `AA/UU` 文件。本轮只新增调度文档，不改运行时代码；执行代理必须在自己的 worktree 中复核当前基线。
- Claude Code local version checked for launcher design: `2.1.142 (Claude Code)`. Official Claude Code CLI reference says `claude --help` does not list every flag, documents `claude agents`, `--bg`, `--name`, `--permission-mode`, `--model`, `--effort`, and background sessions. Source: https://code.claude.com/docs/en/cli-reference

## Execution Mode Recommendation

- Recommended mode: `AGENT_VIEW`
- Why: 这是研究/设计调度，不应直接并行改运行时代码。zoom audit 和 brightness audit 可以并行，shared strategy 必须消费两者证据，最终由 Codex 判断是否进入实现修复。
- Alternatives rejected:
  - `SINGLE_AGENT` — 可以完成，但容易把 zoom 和 brightness 各自症状混成单点 UI 修补，漏掉状态源竞争。
  - `BACKGROUND_AGENT_SCRIPT` — 提供 G1 可选启动脚本，但不默认自动跑所有包；03/04 依赖前置证据。
  - `BATCH` — 不是机械迁移。
  - `AGENT_TEAM` — 这是可执行文档研究，不需要高成本 multi-hypothesis teammate mode；如用户要专门做多模型竞品研究可另开。
- Max parallel agents: 2
- Codex-retained work: 最终整合审计、是否进入实现修复的决策、真机/多模态手感验收。

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:

- Read this plan, all package docs, referenced existing plan docs, and the relevant source/test files.
- Create or reuse an isolated git worktree for research if desired.
- Run read-only shell commands and the listed focused verification commands through `rtk`.
- Write to ONLY your assigned `status/<package-id>.md` file. If your package needs a report, write it inside your own status file, not in `INDEX.md`.
- Commit locally within the worktree branch if you only changed your own status file or package-scoped research notes.

## Stop Gates — Must Ask

STOP and ask the user before:

- Editing runtime code or tests. This orchestration package is for research/design only.
- Crossing project stages or declaring Stage 7 complete.
- Moving camera runtime ownership out of Session Kernel / Device Adapter.
- Introducing UI-local authoritative camera state. UI may hold ephemeral gesture state only for active pointer interaction.
- Adding network dependencies, secrets, SDK keys, or external API calls.
- Touching another package's status file or editing `INDEX.md`.
- Running destructive git operations: force-push, hard reset, deleting branches/worktrees.
- Fixing verification failures when the fix expands beyond this research package.

## Completion Policy

After completing your assigned package:

- Write your evidence pack to `status/<package-id>.md` — do NOT edit `INDEX.md`.
- Report: findings, code evidence, commands run, unresolved risks, and recommended next step.
- Do NOT delete any worktree unless explicitly instructed.
- Do NOT implement fixes unless the user separately authorizes implementation.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-zoom-state-arbitration-audit, 02-brightness-state-arbitration-audit | yes | none | safe — independent research/status files |
| G2 | 03-shared-control-state-strategy | no | 01, 02 | medium — consumes both evidence packs |
| G3 | 04-verification-real-device-protocol | no | 01, 02, 03 | medium — must align with final strategy |
| G4 | 99-integration-audit | no | 01-04 | final Codex audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/01-zoom-state-arbitration-audit.md` | 01-zoom-state-arbitration-audit | 02, 03, 04, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/02-brightness-state-arbitration-audit.md` | 02-brightness-state-arbitration-audit | 01, 03, 04, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/03-shared-control-state-strategy.md` | 03-shared-control-state-strategy | 01, 02, 04, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/04-verification-real-device-protocol.md` | 04-verification-real-device-protocol | 01, 02, 03, 99 |
| `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/99-integration-audit.md` | 99-integration-audit | 01, 02, 03, 04 |
| `app/src/main/**`, `app/src/test/**`, `core/session/**`, `core/device/**` | read-only for all packages | no package may edit without new user approval |
| `docs/plans/zoom-cockpit-v2-productization-orchestration/**` | read-only reference | no package may edit without new user approval |
| `docs/plans/2026-05-25-quick-panel-*.md` | read-only reference | no package may edit without new user approval |

## Agent Budget

- Recommended Claude Code agents: 4 research agents + Codex audit
- Max parallel agents: 2
- Codex usage: final synthesis, visual/interaction judgment, and authorization boundary only
- When to pause: if package 01 or 02 finds that the current dirty worktree makes source evidence ambiguous, pause and ask whether to rebase/clean in a separate repo-health task before implementation planning.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-zoom-state-arbitration-audit | agent-view | agent-01-zoom-arbitration | `launchers/agent-view-prompts.md#package-01-zoom-state-arbitration-audit` | `status/01-zoom-state-arbitration-audit.md` |
| 02-brightness-state-arbitration-audit | agent-view | agent-02-brightness-arbitration | `launchers/agent-view-prompts.md#package-02-brightness-state-arbitration-audit` | `status/02-brightness-state-arbitration-audit.md` |
| 03-shared-control-state-strategy | agent-view | agent-03-shared-control-strategy | `launchers/agent-view-prompts.md#package-03-shared-control-state-strategy` | `status/03-shared-control-state-strategy.md` |
| 04-verification-real-device-protocol | agent-view | agent-04-device-protocol | `launchers/agent-view-prompts.md#package-04-verification-real-device-protocol` | `status/04-verification-real-device-protocol.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-zoom-state-arbitration-audit | — | pending | — | — | — | — |
| 02-brightness-state-arbitration-audit | — | pending | — | — | — | — |
| 03-shared-control-state-strategy | — | pending | — | — | — | — |
| 04-verification-real-device-protocol | — | pending | — | — | — | — |
| 99-integration-audit | Codex | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 02 first in either order; 03 after both; 04 after 03; 99 audit last.
- Rebase policy: rebase on latest main before merge/PR if an agent commits status evidence.
- Conflict owner: Codex owns final conflict resolution; package agents must not edit shared plan files.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion. Do NOT edit `INDEX.md` directly.

Evidence pack must include:

- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files, if any
- [ ] commands run with output summary
- [ ] code file references used
- [ ] package acceptance criteria status
- [ ] recommended design decision
- [ ] unresolved risks
- [ ] self-certification that only allowed paths were touched

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-zoom-state-arbitration-audit.md](packages/01-zoom-state-arbitration-audit.md) | research/code audit agent | none | safe with 02 | Diagnose zoom node labels, drag rollback, session/device echo, and required contract changes |
| [02-brightness-state-arbitration-audit.md](packages/02-brightness-state-arbitration-audit.md) | research/code audit agent | none | safe with 01 | Diagnose quick brightness rebound, latest-wins dispatch, request id filtering, and UI render overwrite |
| [03-shared-control-state-strategy.md](packages/03-shared-control-state-strategy.md) | design agent | 01, 02 | no | Synthesize one reusable interaction-state strategy for zoom first, then brightness |
| [04-verification-real-device-protocol.md](packages/04-verification-real-device-protocol.md) | QA planning agent | 01, 02, 03 | no | Define local gates, trace checks, and real-device fast-drag smoke |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package decision: PASS / PARTIAL / FAIL |

## Recommended Execution Order

1. Launch 01 and 02 in parallel.
2. Launch 03 after 01 and 02 write status evidence.
3. Launch 04 after 03 writes status evidence.
4. Run 99 integration audit after all packages complete.

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit it without this repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first.
- Do not use `--dangerously-skip-permissions`.

## Launch Options

- **Option A**: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- **Option B**: background script — run `rtk bash docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/launchers/dispatch-claude-agents.sh g1` to launch G1 only. Launch later groups manually after dependencies pass.
- **Option C**: Final integration audit — give `validation/final-audit-prompt.md` to Codex.
