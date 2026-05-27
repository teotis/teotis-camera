# 03-panel-control-polish

## Goal

Polish the user-facing panel controls without changing their overall visual language:

- Dev console bottom `关闭` button becomes same-style `清理`, clearing the currently selected log category.
- Quick reset button uses the same style family as the other Quick panel buttons.
- Style / Color Lab bottom `关闭色调` button is removed.
- Right rail `开发` button uses the same style as the other right rail buttons.

## User Symptoms Covered

- Issue 3: in Dev, replace `关闭` with `清理`; same style/UI, clear current category log.
- Issue 4: Quick reset button style should match other Quick panel buttons.
- Issue 5: remove bottom `关闭色调` in Style / Color Lab.
- Issue 7: right rail Dev button style should match other buttons.

## Allowed Paths

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionCallbacks.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- `app/src/main/java/com/opencamera/app/DevLogRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/DevLogExporterTest.kt`
- focused app UI/render-model tests as needed

## Forbidden Paths

- Do not delete the existing exporter/cleanup storage owner.
- Do not remove outside-tap/scrim close behavior for panels.
- Do not introduce a new Dev log storage directory.
- Do not make Style / Color Lab impossible to close; rely on existing route dismissal/scrim or add an explicit non-bottom affordance only if necessary.
- Do not edit coordinator files except `status/03-panel-control-polish.md` and the matching `state.tsv` row.

## Required Investigation

1. Confirm current Dev cleanup plumbing (`cleanupByType`, `cleanupAll`) and selected tab semantics.
2. Replace the visible bottom Dev close action with current-category cleanup:
   - selected `摘要` clears summary;
   - selected `链路` clears pipeline;
   - selected `错误` clears error;
   - selected `全部` clears all.
3. Remove redundant visible cleanup buttons if they conflict with the new bottom action, or keep them hidden if that is the existing model.
4. Change Quick reset style to match `Widget.OpenCamera.QuickBubbleButton` or the local Quick row pattern.
5. Remove the bottom Style / Color Lab close button and associated click binding if it becomes dead code.
6. Make `buttonDevEntry` use the same rail style as `风格` and `快捷`, unless there is a focused test proving a shared style parent is better.

## Acceptance Criteria

- Dev bottom action text is `清理` / `Clear` and keeps the same button footprint as the old close button.
- Tapping the Dev bottom action clears the selected category and refreshes the visible log count/content.
- Export still works.
- Quick reset no longer visually looks like a separate pill from the Quick panel rows.
- `关闭色调` no longer appears at the bottom of Style / Color Lab.
- Right rail `开发` no longer has a distinct background/style from `风格` / `快捷`.
- Focused tests cover selected-tab cleanup and affected render-model text if existing test seams allow it.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Test output summaries.
- Short explanation of how Dev panel remains closable after the bottom button becomes cleanup.

## Unlock Condition

Mark completed only after Dev cleanup behavior and UI style changes are tested locally, or blocked with exact evidence.
