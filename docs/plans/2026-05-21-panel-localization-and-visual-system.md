# Panel Localization and Visual System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the camera UI readable, localized, dismissible, and visually coherent without changing core camera ownership boundaries.

**Architecture:** App shell owns panels and external Android UI behavior. Render models should provide localized display strings through `AppTextResolver`; XML/View code should render them without embedding product copy. Session Kernel must not know about panel open/close state.

**Tech Stack:** Android Views/XML, MaterialComponents styles, Kotlin render models, `AppTextResolver`, app unit tests.

---

## Evidence and Scope

User issues covered:

- `5`: Tone/filter secondary panel lacks default Chinese, UI is inelegant/inconsistent, secondary panels should dismiss on outside tap, palette is not visible/recognizable.
- `6`: top bar adaptation is poor; Lens Lab has similar panel problems.
- `10`: bottom bar UI design is poor.
- `11`: other UI issues not explicitly listed.

Observed code:

- `SessionUiRenderModel.kt` hard-codes large amounts of English copy for Filter Lab, Watermark Lab, Portrait Lab, availability labels, action labels, hints, footers, and control labels.
- `MainActivity.kt` dynamically creates buttons with hard-coded English `Use This Template` and `Use This Look`.
- `activity_main.xml` places top panel at `16dp` from parent top without system inset handling; screenshot shows conflict with status bar / Dynamic Island-style cutout in the screen recording.
- `filterPanel` and `settingsPanel` can be closed only via explicit close/back buttons; outside area does not dismiss.
- `filterPaletteSurface` is a plain solid rectangle, so the "palette" exists technically but does not look like a palette.
- Bottom sheet is a large dark block with an imbalanced thumbnail/lens/zoom/shutter layout and oversized wrapped shutter text in screenshots.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/*` as needed for restrained panel/button backgrounds
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`

Optional:

- Create `app/src/main/java/com/opencamera/app/FilterPaletteView.kt` if a custom palette view is cleaner than styling a plain `View`.

## Tasks

- [ ] **Step 1: Inventory and replace hard-coded panel copy**

Search:

```bash
rtk rg -n "\"(Filter Lab|Watermark Lab|Portrait Lab|Use This|Adjust Selected|Current default|Ready|Unavailable|Open Style Page|Switch to|Tap to cycle|Horizontal swipe|Selected default|Supported|Unsupported|Degraded)\" app/src/main/java/com/opencamera/app -S
```

For each user-facing string:

- Add a method to `AppTextResolver` if dynamic.
- Add `strings.xml` and `values-en/strings.xml` resources if static.
- Keep tests using `TestAppTextResolver` so render-model tests are deterministic.

Priority text:

- Filter Lab headline/supporting/current/default/action/hints.
- Watermark Lab selector/detail labels.
- Portrait Lab labels/hints.
- Settings availability labels: supported/degraded/unsupported should not borrow unrelated strings like `Still Max`.

- [ ] **Step 2: Add render-model tests for Chinese/default localization paths**

In `SessionUiRenderModelTest`, add tests that use a resolver returning Chinese strings and assert:

- `filterLabPageRenderModel(...).headline` is Chinese.
- selected filter item `adjustButtonLabel` does not contain `Adjust Selected` or `Ready`.
- `saveCustomControl.buttonLabel` does not contain `Save as Custom`.
- watermark selector item does not contain `Current default` or `Open Style Page`.
- `SettingsControlRenderModel.buttonLabel` uses localized availability labels.

These are pure unit tests and should fail before replacing the hard-coded strings.

- [ ] **Step 3: Add outside-tap dismissal for secondary panels**

In `activity_main.xml`, add a full-screen transparent overlay view below panels and above preview when any panel is open:

```xml
<View
    android:id="@+id/panelDismissScrim"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:background="#00000000"
    android:clickable="true"
    android:focusable="true"
    android:visibility="gone"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent" />
```

Layer it so it receives outside taps but does not cover the visible panel. If XML ordering makes that awkward, use a single root `FrameLayout` overlay group for scrim + panel.

In `MainActivity`:

- Add `panelDismissScrim`.
- On click, close `settingsPanel`, `filterPanel`, `quickBubblePanel`, and `devConsolePanel`.
- Update `renderPanelVisibility()` and `renderDevConsoleVisibility()` so scrim is visible when any secondary panel is visible.

Do not treat this as a session event.

- [ ] **Step 4: Make the filter palette visually obvious**

Preferred small implementation:

- Create `FilterPaletteView : View`.
- Draw a two-axis palette:
  - horizontal axis: cool/neutral/warm or tint color.
  - vertical axis: shadow/tone brightness.
  - draw a small selection reticle based on current render spec if possible.
- Replace the plain `View` `filterPaletteSurface` with `com.opencamera.app.FilterPaletteView`.
- Preserve the existing `handleFilterPaletteTouch()` API by keeping it as a normal view touch listener.

If custom view is too much for the current pass, use a gradient drawable, but custom view is preferred because it makes the control semantically real.

- [ ] **Step 5: Repair top safe-area adaptation**

In `MainActivity.onCreate()` or a helper:

- Use `ViewCompat.setOnApplyWindowInsetsListener` on the root or top panel.
- Apply top inset to `topPanel`/`topScrim` as padding or margin.
- Ensure `titleText` maxLines remains `1` and text does not hide under cutouts/status icons.

In XML:

- Reduce title dominance. `OpenCamera · 拍照` at `20sp` is too large in screenshot; use about `17sp`.
- Keep Lens Lab button compact and no taller than title.
- Let `permissionStatus` appear below without overlapping the preview controls.

- [ ] **Step 6: Consolidate panel and button visual style**

Within current XML/View system:

- Keep panels at 8dp to 16dp radius, not large floating bubbles inside floating bubbles.
- Remove nested-card feel where possible: use section dividers/bands inside panels rather than card inside card.
- Ensure buttons with multiline text have enough width, or split title/value/status into separate TextViews instead of packing three lines into a narrow Button.
- Use consistent typography:
  - Panel headline: 18sp or 20sp.
  - Section title: 15sp/16sp.
  - Supporting text: 12sp/13sp.
  - Control values: 13sp/14sp.

Do not introduce a new design framework. Keep this pass in Views/XML.

- [ ] **Step 7: Improve bottom cockpit without changing capture semantics**

In `activity_main.xml`:

- Keep bottom sheet shorter and visually lighter.
- Place thumbnail left, shutter centered, lens/zoom right in one stable row.
- Make shutter round/circular and avoid multiline English wrap. Use short localized labels from the interaction-routing plan (`快门`, `停止`, `保存中`).
- Keep capture output as a single compact line above or below the row, not competing with the primary controls.

Acceptance: screenshot should no longer show a tall dark panel occupying excessive lower screen area with a square wrapped `Capture Still` button.

- [ ] **Step 8: Verify focused behavior**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual smoke:

- Default Chinese app shows Chinese panel labels and action buttons.
- Open Lens Lab, Filter/Tone panel, quick panel, and dev console; tapping outside closes them.
- Palette is visibly a palette, not a blank rectangle.
- Top title and Lens Lab button do not overlap system status/cutout region.
- Mode row labels remain horizontal.
- Bottom controls are balanced and shutter text fits.

## Non-Goals

- Do not migrate the app to Compose in this pass.
- Do not add new camera features.
- Do not alter Session Kernel ownership to manage panel visibility.
- Do not chase pixel-perfect parity with vivo/参考设备; use them only as product references while preserving current architecture.
