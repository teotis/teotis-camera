# 01 Style Copy Noise Cleanup

## Package ID

`01-style-copy-noise-cleanup`

## Goal

Rename the top/side style entry from “镜头” to “风格” and remove meaningless selected-filter copy such as “调整所选” and “打开可编辑的自定义副本”. The Style panel should feel like a clean product surface, not an engineering affordance dump.

## Problem Statement

Real-device feedback reports noisy copy after selecting filters. The current code already has `StyleLab`, `ColorLab`, `FilterLabPanelRenderer`, family tabs, selected filter cards, and adjustment controls. The fix should simplify information architecture and copy without changing rendering math or saved-output behavior.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FilterLabPanelRenderer.kt`
- style/filter/color lab sections of `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/values*/strings.xml`
- style/filter/color lab tests in `app/src/test/java/com/opencamera/app/**`
- package status file `status/01-style-copy-noise-cleanup.md`

## Forbidden Paths

- `core/effect/**`
- `PreviewOverlayView.kt` and preview color transform conflict files
- mode visibility files owned by package 00
- settings navigation files owned by package 02 unless coordinated
- any other package status file

## Dependencies

Wait for package 00 evidence so the app render tests are not already blocked by hidden mode regressions.

## Parallel Safety

Limited. Prefer not to run in parallel with package 02 if both edit `SessionUiRenderModel.kt` or shared string resources.

## Implementation Notes

- The visible entry label should be “风格” in Chinese contexts and “Style” in English fallback contexts.
- Do not expose “Lens” as a product label for style/filter selection.
- Avoid selected-filter buttons that say only “调整所选” or “打开可编辑的自定义副本”. A selected item can show a subtle selected state and either no extra button or a concrete action like “编辑风格” only if it opens an actual edit surface.
- Keep “Use this look” semantics for unselected profiles if it is still useful.
- Do not make the selected filter non-reversible; toggles and selections should remain user-controllable.

## Acceptance Criteria

- No user-visible string for this flow contains “镜头” when it means style/filter.
- No user-visible string contains “调整所选” or “打开可编辑的自定义副本”.
- Selected style/filter cards avoid redundant action buttons unless the action is concrete and tested.
- Color Lab reset behavior remains separate from Style edit behavior.
- Existing custom filter creation tests continue to pass or are updated to match the cleaner copy.

## Verification Commands

```bash
rtk rg -n "镜头|调整所选|打开可编辑的自定义副本|Lens" app/src/main core/settings/src/main app/src/test core/settings/src/test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

In an isolated worktree, replace `rtk ./gradlew` with `rtk ./scripts/run_isolated_gradle.sh`.

## Expected Evidence Pack

- Before/after copy summary.
- `rg` output summary showing removed invalid strings.
- Focused test summaries.
- Residual real-device visual QA note if no device screenshot was captured.
