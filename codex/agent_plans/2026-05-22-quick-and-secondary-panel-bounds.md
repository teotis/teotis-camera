# Quick And Secondary Panel Bounds Plan

> **For agentic workers:** This is a non-multimodal layout handoff. The goal is deterministic bounds, no ellipsis, and no off-screen panels. Final visual taste belongs to the multimodal QA plan. Use `rtk` for shell commands.

**Goal:** Fix quick-panel truncation and oversized secondary panels without redesigning the whole app.

## Problem Statement

The newest APK still shows:

- quick sub-panel labels with ellipses,
- fixed narrow buttons that cannot hold state text,
- secondary panels that are too large,
- panels covering other controls,
- content exceeding visible bounds.

These are layout contract failures and can be addressed textually.

## Current Code Facts

- `quickBubblePanel` is a vertical `LinearLayout` near the right rail.
- Quick buttons such as `buttonQuickGrid`, `buttonQuickLivePhoto`, and `buttonQuickTimer` are fixed at `64dp x 44dp`.
- `MainActivity.renderQuickBubble()` writes combined label/value text such as `网格 ${grid.value}`, `实况 ${live.value}`, and `定时 ${timer.value}` into those narrow buttons.
- `filterPanel` is a `NestedScrollView` with `layout_height="wrap_content"` and bottom constraint only.
- `settingsPanel` is height `0dp` and constrained, but still uses a large full-width sheet.
- `@dimen/panel_max_height` exists but is not consistently applied to secondary panels.

## Required Behavior

- Quick-panel text must not ellipsize in Chinese on common 1080-wide devices.
- Quick controls should show label and state separately or use compact state chips.
- Secondary panels must stay within the visible safe area.
- Opening a panel must not hide the shutter, mode track, or essential close affordance.
- Panels should be scrollable internally when content is long.
- Each panel must have a stable maximum width and height policy.

## Implementation Plan

### Step 1: Redesign quick rows as label + value

Replace fixed text buttons with a compact row/chip model. Low-churn XML option:

```xml
<LinearLayout
    android:id="@+id/quickBubblePanel"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <Button
        android:id="@+id/buttonQuickGrid"
        android:layout_width="96dp"
        android:layout_height="44dp" />
</LinearLayout>
```

Better option:

- Create row-style buttons with `minWidth=96dp`.
- Use two-line text only if the button is tall enough.
- Prefer short labels:
  - `网格`
  - `画质`
  - `画幅`
  - `实况`
  - `定时`
  - `更多`
- Move current value into a small trailing chip or second line:
  - `网格\n三分`
  - `实况\n开`
  - `定时\n3s`

If using one-line labels, make value separate:

```kotlin
buttonQuickGrid.text = getString(R.string.button_quick_grid)
buttonQuickGrid.contentDescription = "${getString(R.string.button_quick_grid)} ${grid.value}"
```

Do not keep long state text inside a `64dp` button.

### Step 2: Bound quick panel in the visible area

Constrain quick panel:

- top to `topPanel` bottom,
- bottom to `bottomSheet` top or `modeTrackScroll` top,
- end to parent/right rail,
- set `android:clipToPadding="false"` only if content still stays in bounds.

If content can exceed available height, wrap it in a `NestedScrollView` or make it a two-column compact grid.

Acceptance:

- The quick panel does not overlap the shutter.
- No quick label ellipsizes.
- Frame-ratio row remains tappable at 44dp minimum height.

### Step 3: Add panel max-height behavior

For `filterPanel` and other secondary panels:

- Change `layout_height="wrap_content"` to constrained `0dp` where possible.
- Constrain top to `topPanel` bottom plus margin and bottom to `bottomSheet`/mode area top.
- Use an explicit max-height helper if XML cannot express the needed bound.

Preferred XML direction:

```xml
<androidx.core.widget.NestedScrollView
    android:id="@+id/filterPanel"
    android:layout_width="0dp"
    android:layout_height="0dp"
    app:layout_constraintTop_toBottomOf="@id/topPanel"
    app:layout_constraintBottom_toTopOf="@id/modeTrackScroll" />
```

If the panel must be bottom-floating, then add an `onApplyWindowInsets` or post-layout max-height clamp in `MainActivity`:

```kotlin
panel.layoutParams.height = minOf(availableHeight, resources.getDimensionPixelSize(R.dimen.panel_max_height))
```

Keep this in one helper such as `applyPanelBounds()`.

### Step 4: Reduce secondary panel header/footer text

This continues the third-round panel dedup plan:

- Do not show aggregate state strings in the panel header.
- Keep title, short helper text, and child controls.
- Hide footer summaries when they duplicate child state.

This reduces panel height and avoids crowding.

### Step 5: Add layout/resource tests where feasible

Robolectric/layout inflation tests are preferred if the project already uses them. Otherwise add render-model tests:

- quick labels are short and do not include verbose state bundles,
- quick control content descriptions include state for accessibility,
- panel routes expose a bounded-panel role.

At minimum, add a code-level guard that no quick button text format exceeds the agreed Chinese label length.

## Files To Inspect Or Modify

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Non-Goals

- Do not implement a full new design system.
- Do not solve final visual polish by screenshot guesses.
- Do not expand quick panel into a second settings panel.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

After APK build, hand final visual checks to `2026-05-22-fourth-feedback-multimodal-visual-qa.md`.
