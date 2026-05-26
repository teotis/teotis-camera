# Quick Panel Frame Cycle And Brightness Slider

日期：2026-05-25

## Goal

让 `快捷` 面板里的高频项保持同一种行式控件体验：`画幅` 不再展开 `4:3 / 16:9 / 1:1` 三个小子项，而是像其他快捷项一样显示为单个大命中行，点击后自动切换下一个画幅；`亮度` 从 `- / 数值 / +` 三按钮改为可拖动的亮度条，直接调节 session/device preview EV。

## Context

- User request: 截图显示 `快捷` 面板中 `画幅` 子项样式和其他子项不统一，比例选项难按；用户倾向“不用具体子项，自动点击切换即可”；`亮度` 应改为亮度条调节方式。
- Verified facts:
  - `app/src/main/res/layout/activity_main.xml` 当前 `brightnessRow` 是 `buttonBrightnessMinus / buttonBrightnessValue / buttonBrightnessPlus` 三按钮。
  - `activity_main.xml` 当前 `frameRatioRow` 是竖向标题加三个 chip：`buttonFrameRatio43 / buttonFrameRatio169 / buttonFrameRatio11`。
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` 当前按三个 ratio option 设置选中/未选中背景。
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` 当前给三个画幅按钮分别 dispatch `SessionIntent.FrameRatioSelected(...)`，亮度按钮分别 dispatch `DecreasePreviewBrightness / ResetPreviewBrightness / IncreasePreviewBrightness`。
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt` 已有 `SessionIntent.ApplyPreviewBrightness(exposureCompensationSteps: Int)`，不需要为滑条新增 session owner。
  - 旧方案 `2026-05-22-fifth-quick-panel-frame-ratio-sheet.md` 要求三项画幅始终可见；本方案按最新用户反馈修订该口径。
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- Non-goals:
  - 不改画幅实际成片裁切、preview overlay 几何或 CameraX viewport。
  - 不把快捷亮度接到 Color Lab / saved JPEG 后处理亮度。
  - 不新增第二套 session kernel 或绕过 `SessionIntent` 直接驱动 CameraX。
  - 不处理画质、实况、低光夜景等其他快捷项能力。

## Implementation Scope

- 将 `画幅` 行收敛为单个 `Button` 或统一 row button，显示 `画幅 4:3`、`画幅 16:9`、`画幅 1:1` 当前值。
- 点击 `画幅` 行时按 `FrameRatio.entries` 轮换下一个 ratio，并通过既有 `SessionIntent.FrameRatioSelected(nextRatio)` 进入 session/mode owner。
- 将 `亮度` 行改为 label + value + `SeekBar`/Material Slider。优先使用 Android `SeekBar`，除非项目已有稳定 Material Slider 使用模式。
- 滑条值映射到 `PreviewBrightnessRange.minSteps..maxSteps`，用户拖动时 dispatch `SessionIntent.ApplyPreviewBrightness(mappedSteps)`。
- 保持 unsupported、active shot、countdown 等禁用语义，禁用时滑条不可拖动，并保留清楚的 `N/A` 或当前值。

## Steps

1. Inspect existing quick panel contracts.
   - Re-open `SessionCockpitRenderModel.kt` and confirm `QuickPanelSheetRenderModel` currently exposes `frameRatioRow`, `frameRatioOptions`, `frameRatioEnabled`, `frameRatioDisabledReason`, and `brightnessRow`.
   - Re-open `activity_main.xml` and confirm all current quick rows use `@drawable/bg_panel_row` or equivalent row fill.

2. Update render model for frame cycling.
   - Keep `frameRatioRow: QuickPanelRowRenderModel` as the single user-facing row.
   - Add a deterministic next-ratio field, for example `frameRatioNext: FrameRatio?`, or add `nextRatio` to the row-specific model if a local type is cleaner.
   - Compute next ratio from current selected ratio and `FrameRatio.entries`; return `null` when disabled.
   - Remove tests that require `frameRatioOptions` to remain visible, or downgrade that list to a non-UI helper only if still needed elsewhere.

3. Replace frame ratio XML.
   - Remove `frameRatioOptionRow` and the three `buttonFrameRatio*` controls from the quick panel.
   - Add a single full-width button with stable id such as `buttonQuickFrameRatio`, height at least `40dp` and preferably `@dimen/touch_target_min`.
   - Use the same quick row visual family as `网格 / 画质 / 像素 / 实况 / 定时`; no nested mini chips.

4. Rebind frame ratio action.
   - Update `QuickPanelViews` to replace `frame43/frame169/frame11` with `frameRatio: Button`.
   - In `CockpitSurfaceRenderer.renderQuickBubble()`, set text to `"${sheet.frameRatioRow.title} ${sheet.frameRatioRow.value}"`, set enabled from `sheet.frameRatioRow.isEnabled`, and keep alpha/background consistent with other quick rows.
   - In `MainActivityActionBinder`, bind the single button to the render model's next ratio from `snapshot()`. Dispatch `SessionIntent.FrameRatioSelected(nextRatio)` only when non-null.

5. Update brightness render model for slider.
   - Extend `QuickBrightnessRenderModel` with numeric fields:
     - `steps: Int`
     - `minSteps: Int`
     - `maxSteps: Int`
     - `isInteractive: Boolean`
   - Keep `value` for display (`+0`, `+1`, `-2`, `N/A`).
   - For unsupported range, set `isInteractive=false` and use a safe `0..0` slider range or hide the slider according to local style.

6. Replace brightness XML.
   - Replace the three brightness buttons with one row containing:
     - label `亮度`
     - a horizontal `SeekBar`
     - compact value text
   - Give the row at least `48dp` height if possible; the draggable area must be wide enough for one-handed use in portrait.
   - Keep row background `@drawable/bg_panel_row` so it visually matches other quick rows.

7. Bind slider action safely.
   - Update `QuickPanelViews` with `brightnessSlider: SeekBar` and `brightnessValue: TextView` or equivalent.
   - In the renderer, set `seekBar.max = maxSteps - minSteps`, `seekBar.progress = steps - minSteps`, `isEnabled = isInteractive`.
   - Avoid dispatch loops: use a small guard flag or only dispatch from `onProgressChanged(..., fromUser = true)`.
   - Map progress back to `targetSteps = minSteps + progress`, then dispatch `SessionIntent.ApplyPreviewBrightness(targetSteps)`.

8. Update tests.
   - Replace `quick panel sheet frame ratio options remain present when one is selected` with tests that assert:
     - frame row value is current ratio;
     - next ratio cycles `4:3 -> 16:9 -> 1:1 -> 4:3`;
     - video/unsupported/busy state disables the row and has a reason.
   - Add brightness slider render-model tests:
     - supported range exposes min/max/current numeric fields;
     - unsupported range disables interaction and shows `N/A`;
     - active shot/countdown disables interaction.
   - Update any `SessionUiRenderModelTest` duplicate expectations that still require visible frame ratio options in quick panel.

## Acceptance Criteria

- `快捷` 面板里 `画幅` 和 `网格 / 画质 / 像素 / 实况 / 定时` 同样是一个大行控件，不再出现三枚小比例 chip。
- 连续点击 `画幅` 行能按顺序切换 `4:3 / 16:9 / 1:1`，并继续走既有 session/mode 画幅 owner。
- 禁用状态下 `画幅` 行不可点击，仍显示当前值和禁用语义，不消失。
- `亮度` 显示为可拖动亮度条，拖动会 dispatch `ApplyPreviewBrightness`，而不是仅靠 `+/-` 微调按钮。
- 滑条不影响 Color Lab 后处理亮度，不直接调用 CameraX。
- vivo X300 竖屏中，`亮度` 滑条和 `画幅` 行均可稳定单手操作，不需要点中很小的文字或 chip。

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- This supersedes the older quick-panel requirement that `4:3 / 16:9 / 1:1` remain visible as separate choices. Keep the old docs for history, but implement this newer product direction.
- `SeekBar` listener code can accidentally dispatch when the renderer updates progress. Guard `fromUser` or temporarily remove/listener-wrap during render.
- If `PreviewBrightnessRange` is broad on a device, dragging may generate many session intents. If local testing shows noisy dispatch, add lightweight coalescing in the UI listener without moving ownership out of session.
- Final visual acceptance remains Codex/user owned because the screenshot complaint is about real-device touch and visual feel. Non-multimodal agents should implement the deterministic code and tests, then hand back an APK for vivo X300 review.
