# Package 01 - Full Clear Product Definition And Mode Surface

## Goal

Add the user-visible Full Clear / `全清` product surface and mode scaffold without implementing device focus bracket execution in this package.

## Allowed Paths

- `core/mode/**`
- `feature/mode-fullclear/**`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/src/main/java/com/opencamera/app/**`
- `app/src/main/res/**`
- `core/session/src/test/**`
- `core/mode/src/test/**`
- `feature/mode-fullclear/src/test/**`
- `docs/plans/full-clear-mode-v1-orchestration/product-definition.md`
- `docs/plans/full-clear-mode-v1-orchestration/v1-implementation-design.md`

## Forbidden Paths

- CameraX adapter execution files except compile fixes required by mode registration.
- Existing unrelated feature mode behavior.
- Coordinator files outside `status/01-full-clear-product-definition.md`.

## Required Work

1. Add `ModeId.FULL_CLEAR` with catalog/display labels.
2. Add a `ModeProductDeclaration` entry that names `focus-bracket-capture` and `focus-stack-fusion` as V1 capabilities with explicit degraded/unsupported semantics.
3. Add a `feature/mode-fullclear` module and `FullClearModePlugin`.
4. Register the plugin wherever app/session mode registry is built.
5. Add mode track/render/i18n coverage so `全清` is visible and selectable.
6. The plugin may initially submit a conservative capture request with metadata tags if focus-bracket contracts are not yet merged; downstream packages will connect V1 strategy.
7. Update the product definition/design docs only where implementation decisions changed.

## Acceptance Criteria

- Full Clear is present in supported modes when still capture is supported.
- Full Clear is absent when still capture is unsupported.
- Mode track/order is deterministic and does not hide existing modes.
- Mode plugin tests cover shutter metadata and honest V1 labels.
- No UI or mode code calls CameraX/Camera2 directly.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeProductDeclarationTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-fullclear:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Expected Evidence

- Worktree, branch, base commit, commit hash.
- Changed files list.
- Verification command results.
- Notes on any labels/order changes.

