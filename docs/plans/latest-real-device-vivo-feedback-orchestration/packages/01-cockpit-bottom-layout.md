# Package 01 - Cockpit Bottom Layout

## Goal

Fix the real-device layout problem shown in image 3: thumbnail and shutter are too close to the bottom while the mode track region has unused vertical space. Rebalance the bottom cockpit so the mode track, zoom slider, thumbnail, shutter, and camera-switch action feel intentionally placed on tall portrait screens.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/**`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt` only for focused cockpit assertions
- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/01-cockpit-bottom-layout.md`
- package-local scratch path

## Forbidden Paths

- Camera runtime/session/device/media behavior files.
- Watermark, Dev log, language, or Quick behavior files unless a failing cockpit test proves a direct dependency.
- Any coordinator file outside this package status and orchestrator state updates.

## Tasks

1. Inspect current cockpit render/layout density and the 2026-05-27/2026-05-28 cockpit layout package history.
2. Move bottom controls upward or redistribute vertical bands so thumbnail and shutter no longer sit at the physical bottom edge while preserving reachability.
3. Keep the mode track visible, readable, and separated from zoom slider and shutter.
4. Add or update deterministic render/layout tests for the new spacing contract.
5. Record before/after layout reasoning in the package status, including what still needs real-device screenshot QA.

## Acceptance Criteria

- Thumbnail and shutter position no longer anchor visually to the bottom edge on the target portrait layout.
- Mode track, zoom slider, shutter, thumbnail, and switch-camera action do not overlap.
- Layout still handles narrow/tall phones and does not regress existing mode track visibility.
- No runtime camera behavior moves into UI layout code.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Evidence Required

- Changed files and commit hash.
- Test results and any layout constants/rationale.
- Remaining real-device screenshot checklist item.

## Unlock Condition

Mark `completed` only after focused tests and assemble pass, or `blocked` with exact failed command and recovery hint.
