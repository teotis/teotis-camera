# 02 Settings Third-Level Navigation

## Package ID

`02-settings-third-level-navigation`

## Goal

Make Settings > Photo > Portrait and Settings > Photo > Watermark automatically enter the intended third-level pages instead of only toggling a row value or staying on the root.

## Problem Statement

The code already has Settings routes and subpages:

- `SettingsSubpage.PORTRAIT_LAB`
- `SettingsSubpage.WATERMARK_SELECTOR`
- `SettingsSubpage.WATERMARK_DETAIL`
- `CockpitPanelCommand.OpenPortraitLab`
- `CockpitPanelCommand.OpenWatermarkSelector`
- `CockpitPanelCommand.OpenWatermarkDetail`

The issue is likely that route, selected tab, scroll state, and surface re-render are not synchronized when the user enters from the Photo tab row.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionCallbacks.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- settings page sections of `SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/CockpitPanelRouterTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- package status file `status/02-settings-third-level-navigation.md`

## Forbidden Paths

- mode catalog/plugin code
- style copy package files unless coordinated
- Dev log storage files
- any package status file except this package

## Dependencies

Package 00 should be complete or clearly not blocking app UI tests.

## Parallel Safety

Limited. Do not run in parallel with package 01 if both edit shared render/text files.

## Implementation Notes

- Opening Portrait from the Photo settings section should set the panel route to `Settings(PORTRAIT_LAB)` and keep the selected settings tab semantically Photo when returning to root.
- Opening Watermark should set the route to `Settings(WATERMARK_SELECTOR)` or directly to a detail page only if the product decision is explicit. The current bug text says “水印” should enter a third-level interface; selector is acceptable if it is the first third-level Watermark Lab page.
- Make `SettingsBack` and Android back predictable: detail -> selector -> root, portrait lab -> root, root -> close.
- Ensure `renderLatestSettingsSurfaces()` and `renderAfterPanelChange()` happen in the right order after navigation.

## Acceptance Criteria

- Tapping Settings > Photo > Portrait immediately shows the portrait lab content container.
- Tapping Settings > Photo > Watermark immediately shows Watermark Lab selector or intended detail content.
- Back returns to the correct Settings root with the Photo section visible.
- Top Settings tab state and visible content are synchronized after direct subpage navigation.
- No new hidden settings navigation state is created outside `CockpitPanelRouter`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

In an isolated worktree, replace `rtk ./gradlew` with `rtk ./scripts/run_isolated_gradle.sh`.

## Expected Evidence Pack

- Router state transition table for Portrait and Watermark entry.
- Focused test output summary.
- Note whether real-device tap smoke was run.
