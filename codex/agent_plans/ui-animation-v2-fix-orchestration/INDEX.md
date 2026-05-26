# UI Animation V2 Fix — Orchestration Index

## Goal

Repair the blocked UI Components And Animation V2 landing so OpenCamera has truthful, tested, and integrated focus/exposure feedback, shutter animation, zoom cockpit, panel transitions, and Quick Panel semantic controls. The orchestration also includes one prerequisite regression fix: restore Humanistic mode in the product mode directory/track order so focused UI verification can run.

## Execution Mode Recommendation

- Recommended mode: `AGENT_VIEW`
- Why: There are six scoped packages, but several share Android UI files. Agent View gives isolated worktrees and evidence packs while allowing limited safe parallelism.
- Alternatives rejected:
  - `SINGLE_AGENT` — possible, but slower and less auditable for the five distinct UI surfaces.
  - `CLAUDE_BG_SCRIPT` — generated as an option, but not the default because shared UI files make manual launch order safer.
  - `BATCH` — not a mechanical repo-wide transform.
  - `AGENT_TEAM` — unnecessary for direct implementation and higher token cost.
- Max parallel agents: 2
- Codex-retained work: final integration audit, visual/timing judgment, real-device smoke, and any product GO/NO-GO decision.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:

- Read this orchestration index, all referenced package documents, and original handoff documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches when available as incremental, non-destructive operations.
- Write to ONLY your assigned `status/<package-id>.md` file. Never edit this `INDEX.md` or another package's status file.

## Stop Gates — Must Ask

STOP and ask the user before:

- Crossing project stage boundaries or changing architecture beyond UI render/app-shell scope.
- Making product-level decisions where requirements are genuinely ambiguous.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees, or deleting user changes.
- Network access, external API calls, or adding secrets/credentials.
- Overwriting unrelated dirty changes outside your assigned Allowed Paths.
- Fixing verification failures when the fix expands scope beyond your package.
- Touching CameraX/device/session-kernel behavior except where the package explicitly allows dispatch or render-model tests.

## Completion Policy

After completing your assigned package:

- Write your evidence pack to `status/<package-id>.md`. Do NOT edit `INDEX.md`.
- Merge, push, or create a PR as the final step if your environment supports it and no stop gate applies.
- Report: what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
| --- | --- | --- | --- | --- |
| G0 | 00-mode-order-regression | no | none | high priority prerequisite; fixes failing focused verification. |
| G1 | 01-focus-exposure-feedback-v2, 04-panel-transition-route-continuity | yes | G0 complete | low-medium; mostly disjoint production files, both may update tests. |
| G2 | 03-zoom-cockpit-v2 | no | G0 complete; preferably after G1 starts | medium; owns zoom rendering and may touch shared gesture tests. |
| G3 | 02-shutter-state-animation-v2 | no | G0 complete; coordinate after 03 if both edit `CockpitSurfaceRenderer.kt` | medium-high; shares cockpit renderer/layout files with zoom/quick. |
| G4 | 05-quick-panel-semantic-controls-v2 | no | G0 complete; after 02 and 03 if they edit shared UI files | high; touches layout, view binding, renderer, action binder, render models. |
| G5 | 99-integration-audit | no | all implementation packages complete | Codex retained final audit. |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` mode order section only | 00-mode-order-regression | Others may read; edit only with coordination. |
| `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` mode directory/track tests only | 00-mode-order-regression | Others may add package-specific tests in separate sections. |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` focus reticle drawing/animation only | 01-focus-exposure-feedback-v2 | 03/04/05 must not edit focus reticle code. |
| `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt` focus reticle model only | 01-focus-exposure-feedback-v2 | Others must not edit focus reticle mapping. |
| `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` focus/EV branch only | 01-focus-exposure-feedback-v2 | 03 may edit pinch branch; coordinate if same lines change. |
| `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt` and shutter visual state | 02-shutter-state-animation-v2 | Others must not edit. |
| `app/src/main/res/drawable/bg_shutter*` | 02-shutter-state-animation-v2 | Others must not edit. |
| `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt` | 03-zoom-cockpit-v2 | Others must not edit. |
| `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` zoom methods only | 03-zoom-cockpit-v2 | 02 owns shutter method; 05 owns quick method. |
| `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt` | 04-panel-transition-route-continuity | Others must not edit. |
| `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`, `CockpitPanelRouter.kt` | 04-panel-transition-route-continuity | Others must not edit. |
| `app/src/main/res/layout/activity_main.xml` panel/scrim attributes only | 04-panel-transition-route-continuity | 02 owns shutter block; 03 owns focal slider block; 05 owns quick panel block. |
| `app/src/main/res/layout/activity_main.xml` quick panel block only | 05-quick-panel-semantic-controls-v2 | 02/03/04 must not edit quick panel block. |
| `app/src/main/java/com/opencamera/app/MainActivityViews.kt` quick panel view binding only | 05-quick-panel-semantic-controls-v2 | 02/03 must coordinate if changing bottom cockpit binding. |
| `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` quick panel binding only | 05-quick-panel-semantic-controls-v2 | 01 owns focus/EV gesture branch. |
| `codex/agent_plans/ui-animation-v2-fix-orchestration/status/<package-id>.md` | assigned package | Other packages must not edit. |
| `codex/agent_plans/ui-animation-v2-fix-orchestration/INDEX.md` | Codex/integration auditor only | Package agents must not edit. |

## Agent Budget

- Recommended Claude Code agents: 5 implementation agents plus Codex final audit.
- Max parallel agents: 2.
- Codex usage: final audit, visual QA, device smoke, and conflict resolution if package evidence disagrees.
- When to pause: any package edits outside allowed paths, focused app tests fail after a package claims completion, or two agents report overlapping changes in `CockpitSurfaceRenderer.kt`, `activity_main.xml`, or `MainActivityActionBinder.kt`.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 00-mode-order-regression | agent-view | agent-00-mode-order | `launchers/agent-view-prompts.md#package-00-mode-order-regression` | `status/00-mode-order-regression.md` |
| 01-focus-exposure-feedback-v2 | agent-view | agent-01-focus-feedback | `launchers/agent-view-prompts.md#package-01-focus-exposure-feedback-v2` | `status/01-focus-exposure-feedback-v2.md` |
| 02-shutter-state-animation-v2 | agent-view | agent-02-shutter-animation | `launchers/agent-view-prompts.md#package-02-shutter-state-animation-v2` | `status/02-shutter-state-animation-v2.md` |
| 03-zoom-cockpit-v2 | agent-view | agent-03-zoom-cockpit | `launchers/agent-view-prompts.md#package-03-zoom-cockpit-v2` | `status/03-zoom-cockpit-v2.md` |
| 04-panel-transition-route-continuity | agent-view | agent-04-panel-transition | `launchers/agent-view-prompts.md#package-04-panel-transition-route-continuity` | `status/04-panel-transition-route-continuity.md` |
| 05-quick-panel-semantic-controls-v2 | agent-view | agent-05-quick-panel | `launchers/agent-view-prompts.md#package-05-quick-panel-semantic-controls-v2` | `status/05-quick-panel-semantic-controls-v2.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 00-mode-order-regression | — | pending | — | — | — | `status/00-mode-order-regression.md` |
| 01-focus-exposure-feedback-v2 | — | pending | — | — | — | `status/01-focus-exposure-feedback-v2.md` |
| 02-shutter-state-animation-v2 | — | pending | — | — | — | `status/02-shutter-state-animation-v2.md` |
| 03-zoom-cockpit-v2 | — | pending | — | — | — | `status/03-zoom-cockpit-v2.md` |
| 04-panel-transition-route-continuity | — | pending | — | — | — | `status/04-panel-transition-route-continuity.md` |
| 05-quick-panel-semantic-controls-v2 | — | pending | — | — | — | `status/05-quick-panel-semantic-controls-v2.md` |
| 99-integration-audit | Codex | pending | — | — | — | `status/99-integration-audit.md` |

## Merge Strategy

- Merge order: 00 first; then 01 and 04; then 03; then 02; then 05; final audit last.
- Rebase policy: each package should rebase or merge latest main/package base before final verification.
- Conflict owner: the later package in the merge order resolves conflicts in shared UI files, but must preserve earlier package acceptance criteria.
- Final integration agent: Codex.
- Do not delete worktrees until final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion. Do NOT edit `INDEX.md` directly.

Evidence pack must include:

- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files, full list
- [ ] commands run, verification commands plus output summary
- [ ] test result summary, pass/fail counts
- [ ] commit hash / PR link
- [ ] unresolved risks, if any
- [ ] whether it touched only allowed paths, self-certify

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
| --- | --- | --- | --- | --- |
| [00-mode-order-regression.md](./packages/00-mode-order-regression.md) | implementation agent | none | prerequisite only | Restore Humanistic product order and unblock focused UI tests. |
| [01-focus-exposure-feedback-v2.md](./packages/01-focus-exposure-feedback-v2.md) | implementation agent | 00 | parallel with 04 | Add focus/AE reticle animation and EV feedback policy. |
| [02-shutter-state-animation-v2.md](./packages/02-shutter-state-animation-v2.md) | implementation agent | 00, coordinate after 03 | not parallel with 03/05 | Wire the shutter visual drawable/state into the actual shutter control. |
| [03-zoom-cockpit-v2.md](./packages/03-zoom-cockpit-v2.md) | implementation agent | 00 | not parallel with 02/05 | Finish zoom slider semantics, tests, and capability behavior. |
| [04-panel-transition-route-continuity.md](./packages/04-panel-transition-route-continuity.md) | implementation agent | 00 | parallel with 01 | Add route transition helper and tests. |
| [05-quick-panel-semantic-controls-v2.md](./packages/05-quick-panel-semantic-controls-v2.md) | implementation agent | 00, 02, 03 | final UI implementation package | Convert quick rows to semantic controls. |
| [99-integration-audit.md](./packages/99-integration-audit.md) | Codex retained | all packages complete | — | Audit all evidence and final integration gates. |

## Recommended Execution Order

1. Launch `00-mode-order-regression` first and merge it before other packages.
2. Launch `01-focus-exposure-feedback-v2` and `04-panel-transition-route-continuity` in parallel.
3. Launch `03-zoom-cockpit-v2`.
4. Launch `02-shutter-state-animation-v2` after or coordinated with zoom, because both touch cockpit rendering.
5. Launch `05-quick-panel-semantic-controls-v2` after shared cockpit/layout churn settles.
6. Run Codex final integration audit.

## Launch Options

- Option A: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- Option B: `claude --bg` script — run `bash launchers/dispatch-claude-agents.sh` from this repo. The script launches only the safe first wave by default.
- Option C: `/batch` — not recommended for this non-mechanical UI work.
- Option D: Final integration audit — give `validation/final-audit-prompt.md` to Codex after all status files are complete.
