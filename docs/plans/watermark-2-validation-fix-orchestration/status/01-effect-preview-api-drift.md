# Package Status - 01-effect-preview-api-drift

## Package

`01-effect-preview-api-drift`

## Agent

- Agent name: claude-code-agent
- Worktree path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/effect-preview-api-drift
- Branch name: worktree-effect-preview-api-drift
- Commit / PR: 7b81753

## Status

- State: completed
- Summary: Fixed `core:effect` compilation blocker. The actual issue was broken `ManualCaptureParams` imports in `core:media`, not the API drift described in the package doc (`PreviewColorTransform.colorMatrix` / `PreviewEffectRenderModel.colorFidelity` — those symbols don't exist in the codebase). Removed the dead import and field from `MediaTypes.kt` and `ShotLifecycleContracts.kt`. The official 6B3 gate now progresses past `:core:effect:compileTestKotlin` but hits a separate `core:device` blocker (missing `ManualCaptureParams` class in `core:settings`), which is outside this package's scope.

## Changed Files

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaTypes.kt` — removed unused `ManualCaptureParams` import
- `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt` — removed unused `manualCaptureParams` field from `CaptureProfile`

## Verification

| Command | Result | Notes |
| --- | --- | --- |
| `rtk ./scripts/run_isolated_gradle.sh :core:effect:test --tests PreviewEffectAdapterTest` | PASS | All 12 tests pass |
| `rtk ./scripts/run_isolated_gradle.sh :core:effect:test` | PASS | Full suite passes |
| `rtk env OPENCAMERA_BUILD_ROOT=... ./scripts/verify_stage_6b3_watermark_v2.sh` | PARTIAL | Gate progresses past `:core:effect:compileTestKotlin` but fails at `:core:device:compileKotlin` (missing `ManualCaptureParams` class — separate issue) |

## Evidence

- `git status` summary: clean (only untracked `scripts/run_isolated_gradle.sh` copied for isolated builds)
- `git diff --stat` summary: 2 files changed, 3 deletions(-)
- Test result summary: `:core:effect:test` — all tests pass; `:core:effect:compileTestKotlin` — compiles successfully
- Touched only allowed paths: YES (edits are in `core/media`, which is a dependency of `core:effect` and required to unblock compilation; no forbidden paths touched)

## Unresolved Risks

- The official 6B3 gate still fails at `:core:device:compileKotlin` because `ManualCaptureParams` class is missing from `core:settings`. This is outside this package's scope (forbidden path: `core/settings/**`). A follow-up package should create `ManualCaptureParams` in `core:settings` or remove the dead references in `core:device`.
- The package doc described API drift around `PreviewColorTransform.colorMatrix` and `PreviewEffectRenderModel.colorFidelity`, but these symbols don't exist in the codebase — the drift was already resolved or the doc described a previous state.
- `PreviewColorTransform.kt` and `PreviewColorTransformTest.kt` (listed in package doc) don't exist in the codebase.
