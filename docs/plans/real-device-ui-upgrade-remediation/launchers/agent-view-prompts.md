# Agent View Prompts

## Launch Notes

- Manual Agent View dispatch: copy one package block below into Claude Code Agent View when its dependencies are ready.
- Background dispatch: use `rtk bash docs/plans/real-device-ui-upgrade-remediation/launchers/dispatch-claude-agents.sh` for G1 only (`01` + `05`).
- The background script defaults to `CLAUDE_PERMISSION_MODE=default`. If you intentionally use `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first; otherwise `claude --bg` can fail before a session is created, so Agents View will remain empty.
- After launching background sessions, monitor with `claude agents --cwd /Volumes/Extreme_SSD/project/open_camera`.

## Package: 01-effect-test-contract — Effect Preview Contract/Test Repair

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/01-effect-test-contract.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/status/01-effect-test-contract.md`

**File ownership**: you may edit `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`, `PreviewEffectAdapter.kt`, `PreviewEffectModel.kt`, and `core/effect/src/test/kotlin/com/opencamera/core/effect/*Preview*Test.kt`. Do NOT touch app overlay, camera adapter, session, feature, or focal slider files.
**Dependencies**: none. Safe to run with package 05.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or claim GOOD preview fidelity without real pixel application -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-color-lab-preview-execution — Color Lab Preview Execution and Fidelity Honesty

Copy the block below into Claude Code Agent View after package 01 completes.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/02-color-lab-preview-execution.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/status/02-color-lab-preview-execution.md`

**File ownership**: you may edit `PreviewOverlayView.kt`, `PreviewColorTransformOverlay.kt`, focused app overlay tests, and the listed `core/effect` preview transform files/tests. Do NOT touch camera adapter, saved-photo algorithm postprocessor, session kernel, feature modules, or focal slider files.
**Dependencies**: wait for package 01. Coordinate with package 03 if both touch `PreviewOverlayView.kt`.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, leave TODO/commented implementation, or mark fidelity GOOD without real preview pixel application -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 03-preview-content-bounds — Preview Content Bounds for Frame Overlay

Copy the block below into Claude Code Agent View after package 01 completes.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/03-preview-content-bounds.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/status/03-preview-content-bounds.md`

**File ownership**: you may edit `SessionPreviewRenderModel.kt`, `PreviewOverlayView.kt`, `PreviewOverlayGeometryTest.kt`, `PreviewContentGeometryTest.kt`, and narrowly scoped render model tests. Do NOT touch `core/effect`, camera adapter, saved-photo postprocessor, or focal slider files.
**Dependencies**: wait for package 01. Coordinate with package 02 if both touch `PreviewOverlayView.kt`.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or hardcode a content-bound behavior without tests and residual-risk note -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 04-preview-capture-zoom-semantics — Preview vs Capture Zoom Semantics

Copy the block below into Claude Code Agent View after package 03 completes.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/04-preview-capture-zoom-semantics.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/status/04-preview-capture-zoom-semantics.md`

**File ownership**: you may edit preview render/overlay geometry, `CameraXCaptureAdapter.kt`, `PhotoFrameRatioPostProcessor.kt`, focused tests, and narrowly scoped `core/media` or `core/device` zoom/crop contracts if required. Do NOT touch `core/effect`, focal slider files, or unrelated mode plugins.
**Dependencies**: wait for package 03.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or discover that matching saved output to visible frame requires a product decision beyond this package -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 05-focal-slider-interaction — Focal Slider Interaction Polish

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/05-focal-slider-interaction.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/status/05-focal-slider-interaction.md`

**File ownership**: you may edit `FocalLengthSliderView.kt`, zoom-slider sections of `CockpitSurfaceRenderer.kt`, zoom-slider sections of `SessionCockpitRenderModel.kt`, focal slider layout region, and focused slider tests. Do NOT touch overlay, `core/effect`, session kernel, or camera adapter files.
**Dependencies**: none. Safe to run with package 01.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or remove preset quick-jump behavior while fixing continuous drag -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---
