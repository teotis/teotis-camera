# Scene Mask Foundation Research — Orchestration Index

## Goal

研究并固化 OpenCamera 的 `Scene Mask / 主体识别基础能力` 设计方案：把 OPPO/vivo 式人像虚化、层次人像、主体保护、背景调色和景深滑杆转译成 OpenCamera 可验证、可降级、不过度宣称的能力合同。输出必须基于当前仓库已有 `SceneMask` 实现和外部官方资料，明确哪些能力是 `SUPPORTED`、`DEGRADED`、`UNSUPPORTED`，并给出下一步是否进入实现修复的判断。

## Research Baseline

- 当前仓库已经有 `core/media/.../SceneMaskContracts.kt`、`PreviewSceneMaskSource.kt`、`MlKitSelfiePreviewSceneMaskSource.kt`、`SavedPhotoSceneMaskProvider.kt`、`MlKitSavedPhotoSceneMaskProvider.kt`、mask-aware photo/portrait editor 接口和相关测试。研究 agents 必须先核实现状，不能假设它们尚未存在。
- 既有方案入口是 [`../2026-05-25-scene-mask-segmentation-index.md`](../2026-05-25-scene-mask-segmentation-index.md)，但当前代码落地可能已经偏离原计划，尤其要核查预览 mask、成片 mask、诊断诚实性、输出写回和边缘质量。
- 官方资料快照：
  - ML Kit Selfie Segmentation Android: beta，API 23+，bundled 约 4.5MB，Pixel 4 latency 约 25-65ms，支持 stream/single-image 和 raw-size mask。Source: https://developers.google.com/ml-kit/vision/selfie-segmentation/android
  - ML Kit Subject Segmentation Android: beta，API 24+，unbundled 约 200KB 但依赖 Google Play services 下载模型，可输出 foreground / multi-subject masks，官方说明当前只支持 static images，Pixel 7 Pro 平均约 200ms。Source: https://developers.google.com/ml-kit/vision/subject-segmentation/android
  - MediaPipe Image Segmenter Android: 可输出 category mask / confidence mask，适合未来通用语义扩展。Source: https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android
  - CameraX `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`: 只交付最新帧，分析跟不上时丢帧，适合 preview segmentation 防止积压。Source: https://developer.android.com/reference/androidx/camera/core/ImageAnalysis
  - OPPO 官方人像资料强调 scene layers、subject/scene separation、skin protection、adjustable bokeh；OpenCamera 可借鉴产品语义，但不能宣称等同厂商私有 pipeline。Sources: https://www.oppo.com/en/newsroom/stories/oppo-reno11-series-portrait-expert-lofficiel/ and https://www.oppo.com/en/newsroom/press/oppo-launches-reno15-series/
  - vivo X300 官方页面强调 ZEISS Multifocal Portrait 和不同焦段/样式 bokeh；OpenCamera 可映射为 profile + subject/background style + depth strength，但不能把 2D mask 伪装成真 depth。Source: https://www.vivo.com/en/products/activity/x300
- Claude Code local version checked for launcher design: `2.1.142 (Claude Code)`. Official CLI reference confirms `claude agents`, `--bg`, `--name`, `--permission-mode`, `--model`, `--effort`, and notes that `claude --help` does not list every flag. Source: https://code.claude.com/docs/en/cli-usage

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: 这是研究/设计编排，不应直接并行改运行时代码。四个包分别覆盖外部能力事实、当前实现核验、产品/架构转译、验证门禁，前两包可并行，后三包需要读前置证据。
- Alternatives rejected:
  - SINGLE_AGENT — 可以完成，但容易把外部能力事实、代码现状和产品语义混在一个报告里，缺少互相校验。
  - BACKGROUND_AGENT_SCRIPT — 提供脚本作为可选 G1 启动方式，但不建议一口气后台启动全部研究包；03/04 需要 01/02 证据。
  - BATCH — 不是机械迁移。
  - AGENT_TEAM — 这是可执行文档研究，不需要高成本 multi-hypothesis teammate mode；如用户要专门做竞品研究可另开。
- Max parallel agents: 2
- Codex-retained work: 最终整合审计；判定是否从研究设计进入实现修复；保留真机/多模态审美判断。

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this plan, all package docs, referenced existing plan docs, and the relevant source/test files.
- Create or reuse an isolated git worktree for research if desired.
- Run read-only shell commands and the listed verification commands through `rtk`.
- Write to ONLY your assigned `status/<package-id>.md` file. If your package explicitly asks for a report, write it under `status/` inside your own file, not in `INDEX.md`.
- Commit locally within the worktree branch if you only changed your own status file or package-scoped research notes.

## Stop Gates — Must Ask

STOP and ask the user before:
- Editing runtime code or tests. This orchestration package is for research/design only.
- Crossing project stages or declaring Stage 7 complete.
- Adding network dependencies, secrets, SDK keys, or external API calls.
- Replacing the existing `SceneMask` architecture without evidence that current contracts cannot be repaired.
- Claiming depth, multi-layer portrait, skin preservation, or subject protection as supported unless the code path and tests prove it.
- Treating ML Kit beta APIs as non-degradable core requirements.
- Touching another package's status file or editing `INDEX.md`.
- Running destructive git operations: force-push, hard reset, deleting branches/worktrees.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` — do NOT edit `INDEX.md`.
- Report: findings, source/code evidence, commands run, unresolved risks, and recommended next step.
- Do NOT delete any worktree unless explicitly instructed.
- Do NOT implement fixes unless the user separately authorizes implementation.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-backend-capability-matrix, 02-current-implementation-audit | yes | none | safe — independent research/status files |
| G2 | 03-product-architecture-design | no | 01, 02 | medium — consumes both evidence packs |
| G3 | 04-verification-real-device-protocol | no | 02, 03 | medium — must align with current implementation gaps |
| G4 | 99-integration-audit | no | 01-04 | final Codex audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/scene-mask-foundation-research-orchestration/status/01-backend-capability-matrix.md` | 01-backend-capability-matrix | 02, 03, 04, 99 |
| `docs/plans/scene-mask-foundation-research-orchestration/status/02-current-implementation-audit.md` | 02-current-implementation-audit | 01, 03, 04, 99 |
| `docs/plans/scene-mask-foundation-research-orchestration/status/03-product-architecture-design.md` | 03-product-architecture-design | 01, 02, 04, 99 |
| `docs/plans/scene-mask-foundation-research-orchestration/status/04-verification-real-device-protocol.md` | 04-verification-real-device-protocol | 01, 02, 03, 99 |
| `docs/plans/scene-mask-foundation-research-orchestration/status/99-integration-audit.md` | 99-integration-audit | 01, 02, 03, 04 |
| `core/media/**`, `core/effect/**`, `app/src/main/**`, `app/src/test/**` | read-only for all packages | no package may edit without new user approval |
| `docs/plans/2026-05-25-scene-mask-*.md`, `docs/plans/2026-05-25-preview-subject-mask-pipeline.md`, `docs/plans/2026-05-25-saved-photo-mask-rendering.md` | read-only for all packages | no package may edit without new user approval |

## Agent Budget

- Recommended Claude Code agents: 4 research agents + Codex audit
- Max parallel agents: 2
- Codex usage: final audit and product judgment only
- When to pause: if package 02 finds the current code already fails focused tests in a way that invalidates package 03 product design assumptions, pause and ask whether to switch from research to implementation repair.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-backend-capability-matrix | agent-view | agent-01-mask-capability | launchers/agent-view-prompts.md#package-01-backend-capability-matrix | status/01-backend-capability-matrix.md |
| 02-current-implementation-audit | agent-view | agent-02-mask-current-audit | launchers/agent-view-prompts.md#package-02-current-implementation-audit | status/02-current-implementation-audit.md |
| 03-product-architecture-design | agent-view | agent-03-mask-design | launchers/agent-view-prompts.md#package-03-product-architecture-design | status/03-product-architecture-design.md |
| 04-verification-real-device-protocol | agent-view | agent-04-mask-qa | launchers/agent-view-prompts.md#package-04-verification-real-device-protocol | status/04-verification-real-device-protocol.md |
| 99-integration-audit | codex | — | validation/final-audit-prompt.md | status/99-integration-audit.md |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-backend-capability-matrix | — | pending | — | — | — | — |
| 02-current-implementation-audit | — | pending | — | — | — | — |
| 03-product-architecture-design | — | pending | — | — | — | — |
| 04-verification-real-device-protocol | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 02 first in either order; 03 after 01/02; 04 after 02/03; 99 audit last.
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
| [01-backend-capability-matrix.md](packages/01-backend-capability-matrix.md) | research agent | none | safe with 02 | Compare ML Kit Selfie, ML Kit Subject, MediaPipe, CameraX constraints, and honesty labels |
| [02-current-implementation-audit.md](packages/02-current-implementation-audit.md) | code audit agent | none | safe with 01 | Audit current repo `SceneMask` implementation and tests against the intended contract |
| [03-product-architecture-design.md](packages/03-product-architecture-design.md) | design agent | 01, 02 | no | Translate OPPO/vivo-like product goals into OpenCamera architecture and UI/metadata semantics |
| [04-verification-real-device-protocol.md](packages/04-verification-real-device-protocol.md) | QA planning agent | 02, 03 | no | Define local gates, visual QA protocol, metrics, and failure examples |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package decision: PASS / PARTIAL / FAIL |

## Recommended Execution Order

1. Launch 01 and 02 in parallel.
2. Launch 03 after 01 and 02 write status evidence.
3. Launch 04 after 02 and 03 write status evidence.
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
- **Option B**: background script — run `rtk bash docs/plans/scene-mask-foundation-research-orchestration/launchers/dispatch-claude-agents.sh` to launch G1 only. Launch later groups manually after dependencies pass.
- **Option C**: Final integration audit — give `validation/final-audit-prompt.md` to Codex.

