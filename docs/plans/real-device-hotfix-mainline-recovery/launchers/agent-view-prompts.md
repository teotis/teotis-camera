# Agent View Prompts

## Package: 01-watermark-mainline-recovery — Watermark Mainline Recovery

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/packages/01-watermark-mainline-recovery.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/status/01-watermark-mainline-recovery.md`

**File ownership**: you may edit non-photo mode plugin paths, core/effect tests, core/settings tests, and your own status file. Do NOT touch session/device/capture, gesture, shutter visual, INDEX, or other status files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or need product decisions -> stop and ask.

**Task**: Recover the completed watermark work from `.claude/worktrees/02-watermark-mainline` onto current main. Verify on your package branch, write evidence to your status file, and merge/PR without editing INDEX.

---

## Package: 02-zoom-scaleend-mainline-recovery — Zoom ScaleEnd Mainline Recovery

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/packages/02-zoom-scaleend-mainline-recovery.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/status/02-zoom-scaleend-mainline-recovery.md`

**File ownership**: you may edit app gesture files/tests and your own status file. Do NOT touch mode plugins, effect/settings, session/device/capture, shutter visual, INDEX, or other status files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or need product decisions -> stop and ask.

**Task**: Recover the completed ScaleEnd continuity fix from `.claude/worktrees/zoom-scaleend-sync` onto current main. Verify on your package branch, write evidence to your status file, and merge/PR without editing INDEX.

---

## Package: 03-shutter-visual-closure — Shutter Visual Closure

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/packages/03-shutter-visual-closure.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/status/03-shutter-visual-closure.md`

**File ownership**: you may edit shutter/cockpit render paths, focused shutter/session tests, and your own status file. Do NOT touch mode plugins, effect/settings, gesture files, capture adapter, INDEX, or other status files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or need product decisions -> stop and ask.

**Task**: Close the active shutter UI visual gap so `SAVING` is visibly loading, `DATA_RECEIVED` is visibly non-loading, and the shutter remains disabled until completion. Verify, write evidence, and merge/PR without editing INDEX.

---
