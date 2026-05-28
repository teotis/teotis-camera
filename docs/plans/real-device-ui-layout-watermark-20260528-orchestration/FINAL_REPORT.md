# Real Device UI Layout Watermark 20260528 - Final Report

## Result

Ready for mainline merge after user-approved real-device QA waiver.

## Merged Packages

- `01-preview-frame-containment` (`b131f81`): constrained preview frame guide geometry and added containment coverage.
- `02-bottom-cockpit-density` (`4b76ed2`): tightened bottom cockpit and mode-track spacing.
- `03-quick-watermark-cycle` (`f966c5b`): added Quick panel Watermark template cycling.
- `04-real-device-acceptance` (`46f4486`): recorded explicit user waiver for real-device acceptance before merge.

## Integration Verification

- `rtk env ANDROID_HOME=/Users/dingren/Library/Android/sdk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest`
  - Result: BUILD SUCCESSFUL
- `rtk env ANDROID_HOME=/Users/dingren/Library/Android/sdk ./scripts/run_isolated_gradle.sh :app:assembleDebug`
  - Result: BUILD SUCCESSFUL

## Waiver

The real-device acceptance package was skipped by explicit user approval on 2026-05-28. This does not prove final visual quality on device; it only removes the manual QA package as a merge blocker.

## Residual Risks

- Final visual feel for frame containment, bottom density, and Quick Watermark cycling still needs later device screenshot review if symptoms persist.
- Existing git noise remains: repeated `non-monotonic index .git/objects/pack/._pack-...idx` messages from macOS resource-fork files. It did not block branch lookup, merge, or Gradle verification in this run.
- Package branches/worktrees are preserved for inspection; cleanup is deferred.
