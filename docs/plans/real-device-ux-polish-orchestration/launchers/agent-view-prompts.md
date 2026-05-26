# Agent View Prompts

## Package: 00-mode-entry-visibility — Mode Entry Visibility

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/00-mode-entry-visibility.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/00-mode-entry-visibility.md`

**File ownership**: you may edit only the mode/catalog/app mode-track paths listed in the package doc. Do NOT touch effect/preview conflict files, unrelated settings/style/quick/dev files, `INDEX.md`, or another package status file.  
**Dependencies**: none. Run this first.

**Task**: Restore visible Humanistic and Portrait mode entrances through the existing catalog / bottom mode bar / availability filtering chain. Use the existing Mode Plugin -> Session state -> app render model flow; do not create a UI-only hidden mode list.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, clean unrelated conflicts, or require core mode contract redesign -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, acceptance criteria status, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 01-style-copy-noise-cleanup — Style Copy Noise Cleanup

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/01-style-copy-noise-cleanup.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/01-style-copy-noise-cleanup.md`

**File ownership**: you may edit only style/filter/copy files listed in the package doc. Do NOT touch effect rendering, mode visibility, settings navigation, quick dismiss, dev log, `INDEX.md`, or another package status file.  
**Dependencies**: wait for package 00 evidence.

**Task**: Rename user-facing “镜头” to “风格” where it means Style, and remove meaningless selected-filter copy/actions such as “调整所选” and “打开可编辑的自定义副本”. Keep filter selection and custom profile behavior intact.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or change Color Lab/rendering math -> stop and ask.

**Evidence pack**: include before/after copy summary, `rg` proof for removed invalid strings, focused test results, commit/PR, residual visual QA risk, and self-certification.

---

## Package: 02-settings-third-level-navigation — Settings Third-Level Navigation

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/02-settings-third-level-navigation.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/02-settings-third-level-navigation.md`

**File ownership**: you may edit only settings route/render/action files listed in the package doc. Do NOT touch mode catalog, effect rendering, quick dismiss, dev log, `INDEX.md`, or another package status file.  
**Dependencies**: wait for package 00 evidence. Coordinate with package 01 if both edit `SessionUiRenderModel.kt` or strings.

**Task**: Make Settings > Photo > Portrait and Settings > Photo > Watermark immediately route to their intended third-level interfaces, with tab/content/back behavior synchronized.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or create a second settings router outside `CockpitPanelRouter` -> stop and ask.

**Evidence pack**: include route transition table, focused test results, commit/PR, real-device tap smoke status, unresolved risks, and self-certification.

---

## Package: 03-quick-panel-outside-dismiss — Quick Panel Outside Dismiss

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/03-quick-panel-outside-dismiss.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/03-quick-panel-outside-dismiss.md`

**File ownership**: you may edit only Quick outside-dismiss route/render/layout files listed in the package doc. Do NOT change Quick content semantics, mode/catalog, settings reset, dev log, `INDEX.md`, or another package status file.  
**Dependencies**: wait for package 00 evidence if app tests are blocked.

**Task**: Make taps outside the expanded Quick panel close Quick without triggering unintended capture/focus/mode actions, while preserving taps inside Quick controls.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or rewrite gesture/session ownership -> stop and ask.

**Evidence pack**: include hit target/z-order explanation, focused test results, emulator/device tap smoke if available, commit/PR, unresolved risks, and self-certification.

---

## Package: 04-persistence-reset-unification — Persistence Reset Unification

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/04-persistence-reset-unification.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/04-persistence-reset-unification.md`

**File ownership**: you may edit only settings persistence/reset files listed in the package doc. Do NOT touch effect/preview conflict files, dev log, mode visibility, `INDEX.md`, or another package status file.  
**Dependencies**: wait for packages 01, 02, and 03 to complete and merge/rebase.

**Task**: Unify automatic memory and Reset behavior for Settings, Style, Color Lab, and Quick through existing persisted settings/store patterns. Avoid scattered defaults and hidden app-local state owners.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or require a new settings architecture outside current boundaries -> stop and ask.

**Evidence pack**: include inventory table by surface, reducer/serializer/render tests, focused verification, commit/PR, unresolved risks, and self-certification.

---

## Package: 05-dev-log-storage-governance — Dev Log Storage Governance

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/05-dev-log-storage-governance.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/05-dev-log-storage-governance.md`

**File ownership**: you may edit only Dev log/export/render files listed in the package doc. Do NOT touch camera runtime, mode visibility, settings reset, quick dismiss, `INDEX.md`, or another package status file.  
**Dependencies**: none, but package 00 should ideally complete first if app tests are blocked.

**Task**: Add a 20MB total cap for exported Dev logs and one-tap cleanup by type, without deleting anything outside the app debug-log directory and without blocking camera startup.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or change session diagnostics semantics beyond type labels -> stop and ask.

**Evidence pack**: include storage cap algorithm, cleanup safety proof, focused tests, commit/PR, unresolved risks, and self-certification.

---

## Package: 99-integration-audit — Final Integration Audit

Copy the block below into Codex or the final audit agent after all implementation packages finish.

---

**Mode**: final integration auditor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/99-integration-audit.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/99-integration-audit.md`

Read all package docs and all status files. Check file ownership, acceptance criteria, verification evidence, and cross-package UX consistency. Run the final audit commands from `validation/final-audit-prompt.md` if the workspace is merge-clean enough. Output PASS / PARTIAL / FAIL with evidence.

---
