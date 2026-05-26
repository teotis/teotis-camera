# Agent View Prompts

Use these prompts in Claude Code Agent View. This package is updated for Claude Code `2.1.142`, where background sessions are managed with `claude agents`.

Open Agent View from the repo root with:

```bash
claude agents --cwd /Volumes/Extreme_SSD/project/open_camera --permission-mode default --effort high
```

Start with package 00. Do not launch later dependency packages until their "Must wait for" packages are complete.

## Package: 00-mode-order-regression

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/00-mode-order-regression.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/00-mode-order-regression.md`

**File ownership**: edit only `SessionCockpitRenderModel.kt`, `SessionCockpitRenderModelTest.kt`, and `CameraCockpitRenderModelTest.kt` if needed for mode-track coverage.
**Dependencies**: none. This package must land first.
**Stop gates**: force-push, hard reset, delete worktree, cross stage boundaries, touch forbidden paths, expand scope, or overwrite unrelated dirty changes.
**Evidence pack**: write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 01-focus-exposure-feedback-v2

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/01-focus-exposure-feedback-v2.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/01-focus-exposure-feedback-v2.md`

**File ownership**: edit focus/EV feedback paths listed in the package doc. Do not touch shutter, zoom, quick panel, panel route, CameraX, or device adapter files.
**Dependencies**: wait for `00-mode-order-regression` to be completed and merged/rebased into your worktree before final verification.
**Stop gates**: force-push, hard reset, delete worktree, cross stage boundaries, touch forbidden paths, add UI-owned camera runtime behavior, or expand scope.
**Evidence pack**: write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-shutter-state-animation-v2

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/02-shutter-state-animation-v2.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/02-shutter-state-animation-v2.md`

**File ownership**: edit shutter visual paths only, including `ShutterVisualDrawable.kt`, shutter portions of `CockpitSurfaceRenderer.kt`, shutter layout/drawables, and focused shutter tests. Do not edit zoom slider or quick panel portions.
**Dependencies**: wait for `00-mode-order-regression`; coordinate after `03-zoom-cockpit-v2` if both touch `CockpitSurfaceRenderer.kt`.
**Stop gates**: force-push, hard reset, delete worktree, cross stage boundaries, touch forbidden paths, replace session/device ownership, or expand scope.
**Evidence pack**: write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 03-zoom-cockpit-v2

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/03-zoom-cockpit-v2.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/03-zoom-cockpit-v2.md`

**File ownership**: edit zoom cockpit paths listed in the package doc, including `FocalLengthSliderView.kt` and zoom portions of renderer/callback/model/tests. Do not edit shutter or quick panel portions.
**Dependencies**: wait for `00-mode-order-regression`; avoid running in parallel with packages 02 and 05.
**Stop gates**: force-push, hard reset, delete worktree, cross stage boundaries, touch forbidden paths, add hardware claims without capability semantics, or expand scope.
**Evidence pack**: write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 04-panel-transition-route-continuity

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/04-panel-transition-route-continuity.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/04-panel-transition-route-continuity.md`

**File ownership**: edit panel route/transition paths listed in the package doc. Do not add new panels or alter quick/shutter/zoom controls.
**Dependencies**: wait for `00-mode-order-regression`; can run in parallel with package 01.
**Stop gates**: force-push, hard reset, delete worktree, cross stage boundaries, touch forbidden paths, add new product routes, or expand scope.
**Evidence pack**: write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 05-quick-panel-semantic-controls-v2

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/05-quick-panel-semantic-controls-v2.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/05-quick-panel-semantic-controls-v2.md`

**File ownership**: edit quick panel XML, binding, renderer, action binding, render model, and focused quick panel tests listed in the package doc. Do not overwrite shutter or zoom work.
**Dependencies**: wait for `00-mode-order-regression`, then run after packages 02 and 03 settle shared layout/renderer changes.
**Stop gates**: force-push, hard reset, delete worktree, cross stage boundaries, touch forbidden paths, hide unsupported hardware capability, or expand scope.
**Evidence pack**: write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 99-integration-audit

Use this only after every implementation status file is complete.

---

**Mode**: Codex retained final audit
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/99-integration-audit.md`
**Validation prompt**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/validation/final-audit-prompt.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/99-integration-audit.md`

Read all package docs and status files, check file ownership, run final verification, and report PASS / PARTIAL / FAIL. Fix only tiny, obvious, scope-contained integration omissions.

---
