# Vivo X300 Pixel Capability And Shutter Lifecycle — Orchestration Index

## Goal

研究并固化两个真机问题的优化方案：vivo X300 级 48MP/50MP 硬件在快捷像素里只暴露约 13MP 且不可切换，以及普通拍照后快门按钮恢复过慢。输出不是直接改运行时代码，而是把 device adapter 能力枚举、CameraX/Camera2 输出尺寸选择、快捷像素映射、capture lifecycle 分层、UI gating 和真机验收拆成可执行证据包；最终由 Codex 判断是否进入实现修复。

## Research Baseline

- 当前代码已经有 native still output size 概念：`DeviceCapabilities.availableStillCaptureOutputSizes`、`StillCaptureOutputSize`、`StillCaptureConfig.outputSize`、`SessionStateRender.displayedStillCaptureOutputSize(...)` 和 `DefaultCameraSession.nextStillCaptureOutputSize(...)`。
- 当前 Camera2 探测只从 `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(ImageFormat.JPEG)` 建立 still output sizes，并经过 `normalizeStillCaptureOutputSizes(...)` 的 4:3 优先过滤；这可能解释真机最高只出现 13MP，而不是完整 48MP/50MP 档。
- 当前 CameraX bind 使用 `ResolutionSelector` + target output size + `PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE`；研究必须确认这是否足够触发设备的超高像素/maximum-resolution 路径。
- 当前快门链路已经有 `DeviceEvent.DataReceived -> SessionIntent.DataReceived`，但 `shutterDisabledReason(...)` 仍在 photo `activeShot`、`CaptureStatus.SAVING`、`CaptureStatus.DATA_RECEIVED` 时禁用快门；`CameraXCaptureAdapter.emitShotCompleted(...)` 又把 media postprocess 放在 `ShotCompleted` 前，说明“取帧完成”和“处理/保存完成”仍被 UI gating 绑在一起。
- Claude Code local version checked for launcher design: `2.1.142 (Claude Code)`. Official Anthropic CLI reference confirms the general CLI flag surface, but local/project prior docs note Claude Code 2.1 supports `claude --bg --name` and `claude agents`; generated scripts keep the same repository pattern and omit `--permission-mode` by default. Source: https://docs.anthropic.com/en/docs/claude-code/cli-usage

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: 这是研究/设计编排，两个问题牵涉不同主链路但最终会在 session/UI 交汇；先让 01 和 03 并行建立证据，再让 02/04 消费证据，能减少实现前的误判。
- Alternatives rejected:
  - SINGLE_AGENT — 可以完成，但容易把 pixel capability 和 shutter lifecycle 混成一个大报告，降低可验收性。
  - BACKGROUND_AGENT_SCRIPT — 提供脚本作为可选 G1 启动方式，但不建议一口气启动全部包；02/04 需要前置证据。
  - BATCH — 不是机械迁移。
  - AGENT_TEAM — 当前是可执行研究/设计，不需要高成本 multi-hypothesis teammate mode。
- Max parallel agents: 2
- Codex-retained work: 最终整合审计、真机体验判断、是否转入实现修复的产品/架构决策。

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this plan, package docs, AGENTS.md, RTK.md, referenced source/test files, and prior `docs/plans` materials.
- Create or reuse an isolated git worktree for research if desired.
- Run read-only shell commands through `rtk`.
- Run listed focused verification commands through `rtk`; in a worktree use `rtk ./scripts/run_isolated_gradle.sh ...`.
- Write to ONLY your assigned `status/<package-id>.md` file.
- Commit locally within the worktree branch if you changed only your assigned status file or package-scoped research notes.

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

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-pixel-capability-enumeration, 03-shutter-lifecycle-contract | yes | none | safe — independent research/status files |
| G2 | 02-quick-pixel-surface-design | no | 01 | medium — consumes capability evidence and maps UI/session behavior |
| G3 | 04-real-device-verification-protocol | no | 01, 02, 03 | medium — must align with both contracts |
| G4 | 99-integration-audit | no | 01-04 | final Codex audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/01-pixel-capability-enumeration.md` | 01-pixel-capability-enumeration | 02, 03, 04, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/02-quick-pixel-surface-design.md` | 02-quick-pixel-surface-design | 01, 03, 04, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/03-shutter-lifecycle-contract.md` | 03-shutter-lifecycle-contract | 01, 02, 04, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/04-real-device-verification-protocol.md` | 04-real-device-verification-protocol | 01, 02, 03, 99 |
| `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/99-integration-audit.md` | 99-integration-audit | 01, 02, 03, 04 |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` | read-only for 01/02/04 | no package may edit without new user approval |
| `core/device/**`, `core/media/**`, `core/session/**` | read-only for all packages | no package may edit without new user approval |
| `app/src/main/java/com/opencamera/app/SessionStateRender.kt`, `SessionCockpitRenderModel.kt`, `CameraCockpitRenderModel.kt`, `MainActivityActionBinder.kt` | read-only for 02/03/04 | no package may edit without new user approval |
| `app/src/test/**`, `core/*/src/test/**` | read-only for all packages | no package may edit without new user approval |

## Agent Budget

- Recommended Claude Code agents: 4 research/design agents + Codex audit
- Max parallel agents: 2
- Codex usage: final audit and real-device/product judgment
- When to pause: if package 01 proves the current Android/CameraX stack cannot access high-pixel still output on vivo X300 without vendor-private APIs, pause before designing UI switching as supported.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-pixel-capability-enumeration | agent-view | agent-01-pixel-capability | launchers/agent-view-prompts.md#package-01-pixel-capability-enumeration | status/01-pixel-capability-enumeration.md |
| 02-quick-pixel-surface-design | agent-view | agent-02-pixel-surface | launchers/agent-view-prompts.md#package-02-quick-pixel-surface-design | status/02-quick-pixel-surface-design.md |
| 03-shutter-lifecycle-contract | agent-view | agent-03-shutter-lifecycle | launchers/agent-view-prompts.md#package-03-shutter-lifecycle-contract | status/03-shutter-lifecycle-contract.md |
| 04-real-device-verification-protocol | agent-view | agent-04-device-qa | launchers/agent-view-prompts.md#package-04-real-device-verification-protocol | status/04-real-device-verification-protocol.md |
| 99-integration-audit | codex | — | validation/final-audit-prompt.md | status/99-integration-audit.md |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-pixel-capability-enumeration | — | pending | — | — | — | — |
| 02-quick-pixel-surface-design | — | pending | — | — | — | — |
| 03-shutter-lifecycle-contract | — | pending | — | — | — | — |
| 04-real-device-verification-protocol | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 03 first in either order; 02 after 01; 04 after 01/02/03; 99 audit last.
- Rebase policy: rebase on latest main before merge/PR if an agent commits status evidence.
- Conflict owner: Codex owns final conflict resolution; package agents must not edit shared plan files.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit `INDEX.md` directly.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files, if any
- [ ] commands run with output summary
- [ ] source/code references used
- [ ] package acceptance criteria status
- [ ] recommended design decision
- [ ] unresolved risks
- [ ] self-certification that only allowed paths were touched

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-pixel-capability-enumeration.md](packages/01-pixel-capability-enumeration.md) | research agent | none | safe with 03 | Audit still-output capability enumeration and design a truthful high-pixel capability contract |
| [02-quick-pixel-surface-design.md](packages/02-quick-pixel-surface-design.md) | design agent | 01 | no | Map capability evidence into quick pixel UI/session semantics |
| [03-shutter-lifecycle-contract.md](packages/03-shutter-lifecycle-contract.md) | architecture/design agent | none | safe with 01 | Separate frame acquisition, postprocess/save, and UI shutter re-arm semantics |
| [04-real-device-verification-protocol.md](packages/04-real-device-verification-protocol.md) | QA planning agent | 01, 02, 03 | no | Define vivo X300 real-device evidence protocol and timing thresholds |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package decision: PASS / PARTIAL / FAIL |

## Recommended Execution Order

1. Launch 01 and 03 in parallel.
2. Launch 02 after 01 writes status evidence.
3. Launch 04 after 01, 02, and 03 write status evidence.
4. Run 99 integration audit after all packages complete.

## Claude Background Permission Notes

- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit it without this repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first.
- Do not use `--dangerously-skip-permissions`.

## Launch Options

- **Option A**: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- **Option B**: background script — run `rtk bash docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/launchers/dispatch-claude-agents.sh g1` to launch only G1.
- **Option C**: Final integration audit — give `validation/final-audit-prompt.md` to Codex after packages finish.
