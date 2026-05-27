# Package 03 - Quick Watermark Cycle

## Package ID

`03-quick-watermark-cycle`

## Goal

Fix issue 4 from the 2026-05-28 real-device notes: add a Watermark shortcut to the Quick panel. Tapping it cycles through available watermark templates and immediately updates the persisted default template used by preview/capture.

## User Symptom Covered

- The Quick panel lacks a high-frequency Watermark toggle/cycle control even though watermark choice is a shooting-time decision.

## Branch And Worktree

- Branch: `agent/real-device-ui-layout-watermark-20260528/03-quick-watermark-cycle`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-03-quick-watermark-cycle`

## Allowed Paths

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` only for shared watermark label helpers if needed
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt` only if reducer/application coverage needs a narrow assertion

## Forbidden Paths

- Do not add a second watermark settings store.
- Do not bypass `PersistedSettingsAction.UpdatePhotoWatermarkTemplate`.
- Do not change saved-photo watermark renderer behavior.
- Do not put a Watermark Lab navigation button in Quick; this package is a cycle shortcut, not a settings-page replacement.
- Do not edit another package status file or `INDEX.md`.

## Required Investigation

1. Inspect `QuickPanelSheetRenderModel` and existing rows for grid, resolution, brightness, frame ratio, live, timer, and reset.
2. Add a watermark row whose label/value reflects the current `settings.photo.defaultWatermarkTemplateId`.
3. Compute the next available template from `state.settings.featureCatalog.watermarkTemplates`; disable the row with a truthful reason when still capture or templates are unavailable.
4. Bind the button to `PersistedSettingsAction.UpdatePhotoWatermarkTemplate(nextTemplateId)` through the existing settings action path.
5. Ensure the preview/capture watermark preview path naturally observes the persisted template after the setting changes.

## Acceptance Criteria

- Quick panel shows a Watermark row with the current localized template label.
- Tapping the Watermark row cycles through every available template and wraps back to the first.
- The row is disabled when no watermark templates are available or still capture is unsupported.
- The control uses existing settings persistence and does not create hidden UI state.
- Tests cover current label, next action, disabled state, and wraparound.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Quick panel row/action summary.
- Test output summaries.
- Any remaining visual QA notes for the new row in the constrained Quick panel height.

## Unlock Condition

Package `04-real-device-acceptance` may start after this package records completed status.
