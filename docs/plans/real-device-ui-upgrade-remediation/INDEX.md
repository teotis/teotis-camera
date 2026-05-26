# Real Device UI Upgrade Remediation — Orchestration Index

## Goal

修复上一轮外部 agent 对真机 UI 问题 9 / 11 / 12 / 15 的不完整落地：先恢复色彩预览相关测试门禁，再把色彩实验室预览保真度、预览内容边界、预览/成片变焦语义和焦距滑条连续交互分别收敛成可验证闭环。最终交付必须以源码、测试和真机可解释语义为准，不接受仅更新 status 文档或保留 TODO 的完成声明。

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: 五个包中 01/02 都涉及 `core/effect` 与 `PreviewOverlayView`，03/04 都涉及 overlay/session/device/media 语义，05 独立在焦距滑条 UI。按依赖分组执行能减少文件冲突，又允许 01 与 05 并行。
- Alternatives rejected:
  - SINGLE_AGENT — 修复面跨 effect、overlay、device adapter、media crop、UI control，串行会慢且容易漏验。
  - BACKGROUND_AGENT_SCRIPT — 可用作 G1 启动选项，但当前需要人工按依赖顺序观察 01/02 与 03/04 的质量，不建议一次性全量后台启动；新版脚本默认 `CLAUDE_PERMISSION_MODE=default`，若用户显式改成 `auto`，必须先交互执行一次 `claude --permission-mode auto` 完成用户级 opt-in。
  - BATCH — 不是机械迁移，涉及产品语义和测试修复。
  - AGENT_TEAM — 当前是实现修复，不是多假设研究。
- Max parallel agents: 2
- Codex-retained work: 最终集成审核，尤其核对用户原始语义、测试门禁、跨包冲突和真机风险。

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read the plan, index, and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches (incremental, non-destructive operations).
- Write to ONLY your assigned `status/<package-id>.md` file — never edit INDEX.md or another package's status file.

## Stop Gates — Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or making architectural decisions beyond this remediation scope.
- Product-level decisions where requirements are genuinely ambiguous.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees.
- Network access, external API calls, or adding secrets/credentials.
- Overwriting unrelated dirty changes outside your assigned Allowed Paths.
- Fixing verification failures when the fix expands scope beyond your package.
- Claiming preview color fidelity is GOOD while the transform is not actually applied to preview pixels.
- Reintroducing CameraX preview zoom if package 04 establishes a composition-zoom path.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` — do NOT edit INDEX.md.
- Merge, push, or create a PR as the final step — no need to ask.
- Report: what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-effect-test-contract, 05-focal-slider-interaction | yes | none | safe — mostly disjoint files |
| G2 | 02-color-lab-preview-execution | no | 01 | caution — depends on corrected effect contracts/tests |
| G3 | 03-preview-content-bounds | yes, with 02 only if file ownership is respected | 01 | caution — touches `PreviewOverlayView.kt` near color code |
| G4 | 04-preview-capture-zoom-semantics | no | 03 | high — cross-layer zoom semantics and media output |
| G5 | 99-integration-audit | no | 01-05 | final audit only |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt` | 01, then 02 | 03, 04, 05 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` | 01, then 02 | 03, 04, 05 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt` | 01, then 02 | 03, 04, 05 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/*Preview*Test.kt` | 01, then 02 | 03, 04, 05 |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` | 02, 03, then 04 | 01, 05 |
| `app/src/main/java/com/opencamera/app/PreviewColorTransformOverlay.kt` | 02 | 01, 03, 04, 05 |
| `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt` | 03, then 04 | 01, 02, 05 |
| `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` | 03, then 04 | 01, 02, 05 |
| `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt` | 03 | 01, 02, 04, 05 |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` | 04 | 01, 02, 03, 05 |
| `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt` | 04 | 01, 02, 03, 05 |
| `core/media/**` zoom/crop contracts | 04 | 01, 02, 03, 05 |
| `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt` | 05 | 01, 02, 03, 04 |
| `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` zoom slider sections | 05 | 01, 02, 03, 04 |
| `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` zoom slider sections | 05 | 01, 02, 03, 04 |
| `app/src/test/java/com/opencamera/app/*Focal*Test.kt` | 05 | 01, 02, 03, 04 |

## Agent Budget

- Recommended Claude Code agents: 5 implementation agents + Codex audit
- Max parallel agents: 2
- Codex usage: final audit only
- When to pause: any package fails its required verification after one focused fix attempt, or package 04 reveals that true preview/full-lens plus saved zoom crop requires a product decision beyond the stated remediation.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-effect-test-contract | agent-view | agent-01-effect-contract | launchers/agent-view-prompts.md#package-01-effect-test-contract | status/01-effect-test-contract.md |
| 02-color-lab-preview-execution | agent-view | agent-02-color-preview | launchers/agent-view-prompts.md#package-02-color-lab-preview-execution | status/02-color-lab-preview-execution.md |
| 03-preview-content-bounds | agent-view | agent-03-preview-bounds | launchers/agent-view-prompts.md#package-03-preview-content-bounds | status/03-preview-content-bounds.md |
| 04-preview-capture-zoom-semantics | agent-view | agent-04-zoom-semantics | launchers/agent-view-prompts.md#package-04-preview-capture-zoom-semantics | status/04-preview-capture-zoom-semantics.md |
| 05-focal-slider-interaction | agent-view | agent-05-focal-slider | launchers/agent-view-prompts.md#package-05-focal-slider-interaction | status/05-focal-slider-interaction.md |
| 99-integration-audit | codex | — | validation/final-audit-prompt.md | status/99-integration-audit.md |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-effect-test-contract | — | pending | — | — | — | — |
| 02-color-lab-preview-execution | — | pending | — | — | — | — |
| 03-preview-content-bounds | — | pending | — | — | — | — |
| 04-preview-capture-zoom-semantics | — | pending | — | — | — | — |
| 05-focal-slider-interaction | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 05 may merge first in either order; 02 after 01; 03 after 01 and coordinate with 02 if both touched `PreviewOverlayView.kt`; 04 after 03; 99 audit last.
- Rebase policy: rebase on latest main before merge/PR.
- Conflict owner: package 04 owns final zoom semantic conflicts; package 02 owns color-preview conflicts in `PreviewOverlayView.kt`.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit INDEX.md directly — that causes concurrent-write conflicts.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files (full list)
- [ ] commands run (verification commands + output summary)
- [ ] test result summary (pass/fail counts)
- [ ] commit hash / PR link
- [ ] unresolved risks (if any)
- [ ] whether it touched only allowed paths (self-certify)

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-effect-test-contract.md](packages/01-effect-test-contract.md) | implementation agent | none | safe with 05 | Restore broken effect tests and align color-transform contracts |
| [02-color-lab-preview-execution.md](packages/02-color-lab-preview-execution.md) | implementation agent | 01 | caution with 03 | Make preview color behavior real or honestly degraded |
| [03-preview-content-bounds.md](packages/03-preview-content-bounds.md) | implementation agent | 01 | caution with 02 | Constrain 16:9 / frame overlays to actual preview content bounds |
| [04-preview-capture-zoom-semantics.md](packages/04-preview-capture-zoom-semantics.md) | implementation agent | 03 | no | Reconcile full-lens preview with zoomed saved capture area |
| [05-focal-slider-interaction.md](packages/05-focal-slider-interaction.md) | implementation agent | none | safe with 01 | Preserve continuous focal slider final values and add UI tests |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package acceptance audit |

## Recommended Execution Order

1. Launch 01 and 05 in parallel.
2. Launch 02 after 01 passes.
3. Launch 03 after 01 passes; avoid concurrent edit with 02 unless agents coordinate through latest main.
4. Launch 04 after 03 merges.
5. Run 99 integration audit after all packages complete.

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts MUST default to `CLAUDE_PERMISSION_MODE=default`; they must not silently grant `auto` from the repository.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first. Without that opt-in, the first `claude --bg` command can fail before creating a session, leaving Agents View empty.
- If auto opt-in fails, rerun with:
  ```bash
  CLAUDE_PERMISSION_MODE=default rtk bash docs/plans/real-device-ui-upgrade-remediation/launchers/dispatch-claude-agents.sh
  ```

## Launch Options

- **Option A**: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- **Option B**: `claude --bg` script — run `rtk bash docs/plans/real-device-ui-upgrade-remediation/launchers/dispatch-claude-agents.sh` for G1 only; launch later groups manually after dependencies pass. The script defaults to `permission-mode=default`; use `auto` only after interactive opt-in.
- **Option C**: Final integration audit — give `validation/final-audit-prompt.md` to Codex.
