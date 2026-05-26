# Agent View Prompts

## Package: 01-shutter-data-boundary — Shutter Data Boundary

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/packages/01-shutter-data-boundary.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/status/01-shutter-data-boundary.md`

**File ownership**: you may edit the allowed paths listed in the package doc, especially capture/session/device/shutter rendering files. Do NOT touch watermark mode plugin files or zoom gesture files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or require a broad media pipeline redesign -> stop and ask.

**Task**: Read the INDEX and package doc. Fix the shutter data boundary so the loading animation stops at actual data receipt or document an honest CameraX limitation. Add tests and run the listed verification commands.

**Evidence pack**: when done, write completion evidence to your status file only. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-watermark-mainline — Watermark Mainline Landing

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/packages/02-watermark-mainline.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/status/02-watermark-mainline.md`

**File ownership**: you may edit mode plugin, settings, effect, and watermark UI binding paths listed in the package doc. Do NOT touch shutter capture/session/device files or zoom gesture files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or reintroduce video watermark burn-in -> stop and ask.

**Task**: Read the INDEX and package doc. Rebase, cherry-pick, or manually reimplement the non-photo watermark effect/UI wiring on current main. Add focused tests and run the listed verification commands.

**Evidence pack**: when done, write completion evidence to your status file only. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 03-zoom-scaleend-sync — Zoom Scale-End Sync

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/packages/03-zoom-scaleend-sync.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/status/03-zoom-scaleend-sync.md`

**File ownership**: you may edit gesture, focal-length slider, and zoom render-model paths listed in the package doc. Coordinate if you need `CockpitSurfaceRenderer.kt` near shutter rendering. Do NOT touch capture/session/device or watermark mode plugin files.

**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or redesign CameraX zoom capabilities -> stop and ask.

**Task**: Read the INDEX and package doc. Fix `ScaleEnd` zoom basis continuity, preserve smooth pinch dispatch, and verify focal-length slider synchronization. Add tests and run the listed verification commands.

**Evidence pack**: when done, write completion evidence to your status file only. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---
