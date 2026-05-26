# Package 01 — Watermark Mainline Recovery

## Package ID
`01-watermark-mainline-recovery`

## Goal
Recover the completed watermark work from `.claude/worktrees/02-watermark-mainline` onto current `main`, so the active delivery line has watermark effect/UI wiring for all still-photo modes.

## Context
- Previous audit failed because current `main` still only had `WatermarkEffect` in PhotoMode.
- Completed work exists in `.claude/worktrees/02-watermark-mainline` with implementation commit `e1dfefc` and test fix `82bba76`.
- Root status file remained pending, so the previous orchestration did not land evidence on current main.

## Implementation Scope
- Rebase, cherry-pick, or manually port the effective changes from `.claude/worktrees/02-watermark-mainline` to current `main`.
- Ensure non-photo still modes include `WatermarkEffect` and read per-template style via `watermarkStyleFor`.
- Bring over focused tests that prove effect bridge/spec/style behavior.
- Update only `status/01-watermark-mainline-recovery.md` with fresh evidence.

## Acceptance Criteria
- [ ] Current `main` contains `WatermarkEffect` in photo, humanistic, portrait, night, pro, and document still-photo mode paths.
- [ ] Selected template and style are resolved from persisted photo settings.
- [ ] `watermarkTextScale` and `watermarkTextOpacity` reach metadata/custom tags used by postprocess.
- [ ] Preview watermark hint remains generated from the same `WatermarkEffect` model.
- [ ] `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'` passes on the package branch.
- [ ] `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test` passes.
- [ ] `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test` passes.

## Allowed Paths
- `feature/mode-humanistic/**`
- `feature/mode-portrait/**`
- `feature/mode-night/**`
- `feature/mode-pro/**`
- `feature/mode-document/**`
- `core/effect/src/test/**`
- `core/settings/src/test/**`
- `docs/plans/real-device-hotfix-mainline-recovery/status/01-watermark-mainline-recovery.md`

## Forbidden Paths
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/session/**`
- `core/device/**`
- `app/src/main/java/com/opencamera/app/gesture/**`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
- `docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`
- Other packages' status files.

## Dependencies
- Depends on: none

## Parallel Safety
- safe
- Reason: mode/effect/settings tests are disjoint from zoom and shutter visual paths.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack
- [ ] Worktree path and branch.
- [ ] Whether changes were cherry-picked or manually ported.
- [ ] Git diff stat and changed files.
- [ ] Verification command results.
- [ ] Commit hash / PR link.
- [ ] Unresolved risks.
