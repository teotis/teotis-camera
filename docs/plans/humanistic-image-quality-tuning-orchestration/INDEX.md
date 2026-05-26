# Humanistic Image Quality Tuning — Orchestration Index

## Goal

研究并固化 OpenCamera 在 vivo 真机样张对比后暴露出的长期成片调校方向：不承诺追平系统相机的底层 ISP、HDR、多帧和厂商算法能力，而是把“更稳的曝光、更低噪的暗部、更自然的高光压制、更有辨识度但不过度的色彩曲线”转译成 OpenCamera 可实现、可降级、可验证的人文模式 / 滤镜 / Color Lab 风格目标、管线边界和真机验收协议。

## Problem Statement

用户真机对比发现：图 3/5 这类 vivo 系统相机黄昏/夜景样张在天空层次、灯光高光、暗部噪声、局部对比、白平衡和色彩倾向上更稳；图 4/6 中 OpenCamera 更像直接取帧后叠加基础处理，局部锐度、噪声、动态范围、色彩氛围都弱一些。这不是单个 UI bug，而是 Rendering 2.0 / Humanistic / Color Lab 的长期调校路线问题。

## Research Baseline

- 既有 Color Lab 修复已把 `PerceptualColorRecipe`、preview transform、saved-photo postprocess 和 fail-soft 边界打通到本地确定性验证，但真机视觉强度和保存图质量仍需设备 QA。
- 既有 Rendering 2.0 计划已经明确：Color Lab 参考 vivo/Apple 式产品语义时只能借鉴 tone/color/intensity 和感知式局部调整，不得宣称厂商私有 ISP 能力。
- 既有人文模式重新开放计划偏向 35mm/街头/肖像/生活风格，但还没有把黄昏/夜景成片质量差距沉淀成风格目标、场景策略和验收 scorecard。
- 当前可控抓手主要在 session/device request、manual/auto capability semantics、preview approximation、saved JPEG postprocess、diagnostics、metadata 和 real-device QA；不假设能获得 vivo 系统相机的私有 tuning、多帧 HDR、AIISP 或底层 RAW/ISP hooks。
- Claude Code local version checked for launcher design: `2.1.142 (Claude Code)`. Official CLI reference confirms `claude --bg`, named/resumable sessions, `claude agents`, and permission/model flags. Source: https://code.claude.com/docs/en/cli-usage

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: 这是研究/设计编排，包之间需要互相校验但不应并行改运行时代码。01/02 可并行产生事实和风格语义，03/04/05 需要按依赖读取证据。
- Alternatives rejected:
  - SINGLE_AGENT — 可以完成，但容易把代码现状、产品审美、管线可行性和真机协议混在一个长报告里，缺少证据隔离。
  - BACKGROUND_AGENT_SCRIPT — 提供脚本作为可选 G1 启动方式，但不建议一口气后台启动全部研究包；后续包依赖前置证据。
  - BATCH — 不是机械迁移。
  - AGENT_TEAM — 当前需要可执行文档研究和证据包，不需要高成本多假设团队模式。
- Max parallel agents: 2
- Codex-retained work: 最终整合审计、多模态/产品 taste 判断、是否进入实现阶段的边界判断。

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this plan, package docs, referenced existing plan docs, relevant source/test files, and local diagnostics.
- Create or reuse an isolated git worktree for research if desired.
- Run read-only shell commands and listed verification commands through `rtk`.
- Write to ONLY your assigned `status/<package-id>.md` file. If your package asks for a report, write it inside your own status file.
- Commit locally within the worktree branch if you only changed your own status file or package-scoped research evidence.

## Stop Gates — Must Ask

STOP and ask the user before:
- Editing runtime code, tests, resources, Gradle files, or shared project docs outside your assigned status file.
- Claiming OpenCamera can match vivo system camera, BlueImage/ISP behavior, multi-frame HDR, or vendor tuning.
- Adding network dependencies, SDKs, models, secrets, or external API calls.
- Moving ownership across UI / Session Kernel / Device Adapter / Media Pipeline boundaries.
- Creating a second hidden session kernel or making UI directly drive camera runtime behavior.
- Turning taste judgments into deterministic pass/fail without a user/Codex real-device review owner.
- Crossing Stage boundaries or declaring Stage 7 complete.
- Touching another package's status file or editing `INDEX.md`.
- Running destructive git operations: force-push, hard reset, deleting branches/worktrees.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` — do NOT edit `INDEX.md`.
- Report: findings, file/source evidence, commands run, acceptance criteria status, unresolved risks, and recommended next step.
- Do NOT delete any worktree unless explicitly instructed.
- Do NOT implement fixes unless the user separately authorizes implementation.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-current-iq-gap-audit, 02-style-target-scorecard | yes | none | safe — independent research/status files |
| G2 | 03-feasible-rendering-pipeline-design | no | 01, 02 | medium — consumes current gaps and style target |
| G3 | 04-real-device-capture-protocol | no | 01, 02, 03 | medium — must align with feasible metrics and device evidence |
| G4 | 05-implementation-roadmap | no | 01-04 | medium — converts evidence into next implementation packages |
| G5 | 99-integration-audit | no | 01-05 | final Codex audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/01-current-iq-gap-audit.md` | 01-current-iq-gap-audit | 02, 03, 04, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/02-style-target-scorecard.md` | 02-style-target-scorecard | 01, 03, 04, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/03-feasible-rendering-pipeline-design.md` | 03-feasible-rendering-pipeline-design | 01, 02, 04, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/04-real-device-capture-protocol.md` | 04-real-device-capture-protocol | 01, 02, 03, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/05-implementation-roadmap.md` | 05-implementation-roadmap | 01, 02, 03, 04, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/99-integration-audit.md` | 99-integration-audit | 01, 02, 03, 04, 05 |
| `core/settings/**`, `core/effect/**`, `core/device/**`, `core/session/**`, `feature/mode-humanistic/**`, `feature/mode-photo/**`, `app/src/**` | read-only for all packages | no package may edit without new user approval |
| `docs/plans/2026-05-25-color-lab*.md`, `docs/plans/2026-05-25-rendering-2-0*.md`, `docs/plans/2026-05-25-humanistic*.md`, `docs/plans/2026-05-24-vivo-x300*.md`, `docs/plans/2026-05-22-vivo-x300*.md` | read-only for all packages | no package may edit without new user approval |

## Agent Budget

- Recommended Claude Code agents: 5 research/design agents + Codex audit
- Max parallel agents: 2
- Codex usage: final audit, product taste, multimodal real-device sample review
- When to pause: if 01 finds the current capture/save path is still unreliable for Color Lab/Humanistic shots, pause implementation design and recommend reliability repair before visual tuning.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-current-iq-gap-audit | agent-view | agent-01-iq-gap-audit | launchers/agent-view-prompts.md#package-01-current-iq-gap-audit | status/01-current-iq-gap-audit.md |
| 02-style-target-scorecard | agent-view | agent-02-style-scorecard | launchers/agent-view-prompts.md#package-02-style-target-scorecard | status/02-style-target-scorecard.md |
| 03-feasible-rendering-pipeline-design | agent-view | agent-03-rendering-pipeline | launchers/agent-view-prompts.md#package-03-feasible-rendering-pipeline-design | status/03-feasible-rendering-pipeline-design.md |
| 04-real-device-capture-protocol | agent-view | agent-04-real-device-iq-qa | launchers/agent-view-prompts.md#package-04-real-device-capture-protocol | status/04-real-device-capture-protocol.md |
| 05-implementation-roadmap | agent-view | agent-05-iq-roadmap | launchers/agent-view-prompts.md#package-05-implementation-roadmap | status/05-implementation-roadmap.md |
| 99-integration-audit | codex | — | validation/final-audit-prompt.md | status/99-integration-audit.md |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-current-iq-gap-audit | — | pending | — | — | — | — |
| 02-style-target-scorecard | — | pending | — | — | — | — |
| 03-feasible-rendering-pipeline-design | — | pending | — | — | — | — |
| 04-real-device-capture-protocol | — | pending | — | — | — | — |
| 05-implementation-roadmap | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 02 first in either order; 03 after 01/02; 04 after 01/02/03; 05 after 01-04; 99 audit last.
- Rebase policy: rebase on latest main before merge/PR if an agent commits status evidence.
- Conflict owner: Codex owns final conflict resolution; package agents must not edit shared plan files.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit `INDEX.md` directly — that causes concurrent-write conflicts.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files, if any
- [ ] commands run with output summary
- [ ] source links and code file references used
- [ ] package acceptance criteria status
- [ ] recommended design decision
- [ ] unresolved risks
- [ ] self-certification that only allowed paths were touched

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-current-iq-gap-audit.md](packages/01-current-iq-gap-audit.md) | code/product audit agent | none | safe with 02 | Audit current Color Lab/Humanistic/rendering path against the real-device IQ symptoms |
| [02-style-target-scorecard.md](packages/02-style-target-scorecard.md) | product/style research agent | none | safe with 01 | Define realistic style targets and an image-quality scorecard without vendor overclaiming |
| [03-feasible-rendering-pipeline-design.md](packages/03-feasible-rendering-pipeline-design.md) | architecture design agent | 01, 02 | no | Design feasible exposure/noise/highlight/color/sharpness controls within existing boundaries |
| [04-real-device-capture-protocol.md](packages/04-real-device-capture-protocol.md) | QA planning agent | 01, 02, 03 | no | Define capture protocol, sample naming, scorecard, and failure thresholds for vivo comparison |
| [05-implementation-roadmap.md](packages/05-implementation-roadmap.md) | planning agent | 01-04 | no | Convert findings into scoped implementation packages and stop gates |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package decision: PASS / PARTIAL / FAIL |

## Recommended Execution Order

1. Launch 01 and 02 in parallel.
2. Launch 03 after 01 and 02 write status evidence.
3. Launch 04 after 01, 02, and 03 write status evidence.
4. Launch 05 after 01-04 write status evidence.
5. Run 99 integration audit after all packages complete.

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit it without this repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first.
- Do not use `--dangerously-skip-permissions`.

## Launch Options

- **Option A**: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- **Option B**: background script — run `rtk bash docs/plans/humanistic-image-quality-tuning-orchestration/launchers/dispatch-claude-agents.sh` to launch G1 only. Launch later groups manually after dependencies pass.
- **Option C**: Final integration audit — give `validation/final-audit-prompt.md` to Codex.
