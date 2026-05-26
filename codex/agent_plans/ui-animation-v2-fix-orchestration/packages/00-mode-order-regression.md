# 00 Mode Order Regression

## Package ID

`00-mode-order-regression`

## Goal

Restore Humanistic mode in the app-layer product mode directory and mode track order so UI focused verification no longer fails before the animation packages can be judged.

## Source Handoff / Evidence

- Blocked package index: `codex/agent_plans/2026-05-25-ui-animation-v2-upgrade-index.md`
- Validation evidence: focused UI tests failed on `SessionCockpitRenderModelTest` mode directory/track Humanistic expectations.

## File Ownership

- Allowed paths:
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt` only if needed for mode-track coverage
- Forbidden paths:
  - `core/session/**`
  - `core/mode/**`
  - CameraX/device/media files
  - Any package status file except `status/00-mode-order-regression.md`

## Dependencies

None. This package must land first.

## Parallel Safety

Do not run in parallel with other implementation packages. It unblocks their shared verification command.

## Implementation Scope

- Update product visible mode order to include `ModeId.HUMANISTIC`.
- Preserve expected order from current tests: `PHOTO, HUMANISTIC, VIDEO, DOCUMENT`.
- Do not reintroduce Night, Portrait, or Pro into the primary track unless an existing test already expects it.
- Keep `modeDirectoryRenderModel()` and `modeTrackRenderModel()` using the same source of truth.

## Steps

1. Inspect `PRODUCT_MODE_ENTRY_ORDER` and `visibleModeEntryOrder()` in `SessionCockpitRenderModel.kt`.
2. Add `ModeId.HUMANISTIC` in the expected product position.
3. Keep test expectations aligned with current product requirement, not with the broken external landing.
4. Run focused verification.

## Acceptance Criteria

- `modeDirectoryRenderModel()` includes Humanistic when `state.availableModes` includes it.
- `modeTrackRenderModel()` includes Humanistic when `state.availableModes` includes it.
- Product order is `PHOTO, HUMANISTIC, VIDEO, DOCUMENT` for the currently tested main-track set.
- Existing Document mode remains visible when Humanistic is visible.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

Write results to `status/00-mode-order-regression.md` using the status template.
