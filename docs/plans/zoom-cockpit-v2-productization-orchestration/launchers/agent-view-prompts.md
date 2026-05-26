# Agent View Prompts

## Package: 01-product-contract-capability-boundary — Product Contract And Capability Boundary

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/packages/01-product-contract-capability-boundary.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/status/01-product-contract-capability-boundary.md`

**File ownership**: you may edit zoom/focal sections of `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`, `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`, `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`, and focused app/core tests listed in the package. Do NOT touch `FocalLengthSliderView.kt`, `CockpitSurfaceRenderer.kt`, `MainActivity.kt`, `DefaultCameraSession.kt`, or camera adapter files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, claim optical focal hardware facts without evidence, or touch forbidden paths -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-slider-widget-productization — Focal Slider Widget Productization

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/packages/02-slider-widget-productization.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/status/02-slider-widget-productization.md`

**File ownership**: you may edit `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`, optional `FocalLengthSliderMath.kt`, and focused slider tests. Do NOT touch core/session, core/device, render-model contract, cockpit renderer, MainActivity, or camera adapter files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, make the widget authoritative over camera state, or touch forbidden paths -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 03-session-recording-zoom-policy — Session Recording Zoom Policy

Copy the block below into Claude Code Agent View after package 01 completes.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/packages/03-session-recording-zoom-policy.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/status/03-session-recording-zoom-policy.md`

**File ownership**: you may edit zoom handlers in `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`, focused `DefaultCameraSessionTest` zoom/recording tests, and the pinch branch/tests in `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`. Do NOT touch slider widget, cockpit renderer, MainActivity, or camera adapter files without a stop-gate approval.

**Dependencies**: wait for `01-product-contract-capability-boundary`.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, add a coordinator-level zoom state machine, call CameraX directly from UI/session tests, or touch forbidden paths -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 04-cockpit-wiring-and-ux-integration — Cockpit Wiring And UX Integration

Copy the block below into Claude Code Agent View after packages 01, 02, and 03 complete.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/packages/04-cockpit-wiring-and-ux-integration.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/status/04-cockpit-wiring-and-ux-integration.md`

**File ownership**: you may edit focal/zoom methods in `CockpitSurfaceRenderer.kt`, zoom callback wiring in `MainActivity.kt`, the focal slider/zoom cockpit region in `activity_main.xml`, and focused app tests. Do NOT touch core/session, core/device, slider internals, camera adapter, shutter, quick panel, focus/EV, or color lab files.

**Dependencies**: wait for `01-product-contract-capability-boundary`, `02-slider-widget-productization`, and `03-session-recording-zoom-policy`.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, create duplicate zoom controls, dispatch `ZoomRatioToggled` from exact preset controls, or touch forbidden paths -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---
