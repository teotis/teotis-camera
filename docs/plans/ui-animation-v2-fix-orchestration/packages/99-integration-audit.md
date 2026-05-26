# 99 Integration Audit

## Package ID

`99-integration-audit`

## Goal

Audit the completed UI Animation V2 fix packages against every acceptance criterion, verify cross-package consistency, run final integration gates, and report PASS / PARTIAL / FAIL.

## Owner

Codex retained. Do not assign this to an implementation agent unless the user explicitly asks.

## Dependencies

All implementation package status files must be complete:

- `status/00-mode-order-regression.md`
- `status/01-focus-exposure-feedback-v2.md`
- `status/02-shutter-state-animation-v2.md`
- `status/03-zoom-cockpit-v2.md`
- `status/04-panel-transition-route-continuity.md`
- `status/05-quick-panel-semantic-controls-v2.md`

## Audit Steps

1. Read this orchestration `INDEX.md`.
2. Read all package docs under `packages/`.
3. Read all package status files.
4. Check `rtk git status --short`, recent commits/branches, and changed files.
5. Compare each package delivery against its acceptance criteria.
6. Check for file ownership violations.
7. Run final verification commands.
8. Perform or request visual/device QA for timing and touch feel.
9. Report PASS / PARTIAL / FAIL.

## Final Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Acceptance Criteria

- All package-specific acceptance criteria are met or explicitly waived by the user.
- Final focused UI/render tests pass.
- `assembleDebug` passes.
- Stage 7 observability gate passes or any failure is documented as unrelated and accepted by the user.
- No package introduced UI-local runtime camera ownership or direct CameraX calls from UI.
- Codex/user visual QA confirms interaction timing is acceptable.
