# 05-integration-visual-smoke-protocol

## Goal

Verify that the implementation packages integrate locally and produce a concrete real-device smoke checklist for the exact screenshots and symptoms. This package does not replace the user's final device judgment.

## Dependencies

Depends on packages `01`, `02`, `03`, and `04`.

## Allowed Paths

- `docs/plans/real-device-ux-regression-20260527-orchestration/status/05-integration-visual-smoke-protocol.md`
- `codex/documentation.md` only if the package has meaningful verified implementation evidence to record and no finalize package will do the same update

## Forbidden Paths

- Do not edit runtime source unless explicitly reassigned by the user.
- Do not mark real-device visual acceptance complete without actual device evidence.
- Do not modify other package status files.

## Required Work

1. Read all package statuses and commits.
2. From an integration branch or a worktree containing all available package changes, run focused verification:
   - zoom slider/session/device tests from package 01;
   - cockpit/mode track tests from package 02;
   - Dev/Quick/Style tests from package 03;
   - Document organizer tests from package 04.
3. Run assemble.
4. If local verification passes, write a real-device smoke protocol covering:
   - drag across `2x` and `5x` and observe preview/lens switch;
   - Dev selected-tab cleanup;
   - Quick reset visual style;
   - no bottom `关闭色调` in Style/Color Lab;
   - Portrait appears and can be selected from mode track;
   - right rail `开发` style matches `风格` / `快捷`;
   - bottom deck is compact and top bar has black/scrim background;
   - Document organizer is compact and closes on one tap.

## Verification Commands

Run from the assigned worktree or integration branch:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree/branch/base/commit if this package commits documentation.
- Exact commands and pass/fail outputs.
- Smoke protocol with device, APK path, and expected observations.
- Clear statement: local tests/build passed, real-device final QA still pending unless performed.

## Unlock Condition

Mark completed only after local integration verification has run or mark blocked with the exact package/command that prevents meaningful verification.
