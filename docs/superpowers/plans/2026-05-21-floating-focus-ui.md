# Floating Focus UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the OpenCamera main UI to the approved Floating Focus direction: clean preview-first surface, floating utility buttons, Quick Bubble expansion behavior, compact palette panel, and debug-only `DEV` entry.

**Architecture:** Keep the current Android View/XML stack. UI continues to render session state and dispatch intents only; no new session owner, coordinator, or runtime kernel. Split work into XML/style layout changes, MainActivity state/binding changes, render-model/test updates, and visual/reference documentation updates.

**Tech Stack:** Android XML layouts, Kotlin `ComponentActivity`, Material Components buttons/cards, existing `SessionUiRenderModel`, existing `DevLogRenderModel`, Gradle unit tests.

---

## Canonical References

- Decision record: `docs/ui-reference/floating-focus-decision.md`
- Primary visual reference: `docs/ui-reference/floating-button-candidates/floating-focus.svg`
- Secondary interaction reference: `docs/ui-reference/floating-button-candidates/quick-bubble.svg`
- Historical references only: `docs/ui-design-candidates/*`, `docs/ui-reference/flagship-side-rail-palette.*`

## File Responsibility Map

- `app/src/main/res/layout/activity_main.xml`
  - Owns the main camera layout, floating controls, palette panel container, quick bubble container, bottom deck, and debug console position.
- `app/src/main/res/values/themes.xml`
  - Owns reusable button/card styles for floating utility buttons, quick bubbles, palette panel, and debug entry.
- `app/src/main/res/values/strings.xml`
  - Owns display text and accessibility labels for palette, quick bubbles, and debug controls.
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - Owns view binding, local UI visibility state, click handlers, and render application.
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - Owns camera-state-derived control labels and visibility. It must not store UI overlay visibility.
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
  - Owns tests for stable control labels, zoom, quality/resolution text, and UI-render-model behavior.
- `app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt`
  - Owns dev log tab/filter behavior tests. Update only if console entry labels or export text changes.
- `docs/ui-reference/README.md`
  - Owns the canonical visual reference pointer.
- `docs/ui-reference/floating-focus-decision.md`
  - Owns the approved UI decision and acceptance criteria.

## Workstream Parallelization

These can run in parallel after Task 1:

- Task 2: XML layout skeleton
- Task 3: styles and strings
- Task 4: render-model test adjustments

These should run after Task 2 and Task 3:

- Task 5: MainActivity binding and overlay state
- Task 6: palette panel behavior
- Task 7: DEV console relocation

These should run after Tasks 5-7:

- Task 8: verification and visual checklist
- Task 9: APK generation and install instructions

## Task 1: Baseline Audit

**Files:**
- Read: `docs/ui-reference/floating-focus-decision.md`
- Read: `docs/ui-reference/floating-button-candidates/floating-focus.svg`
- Read: `docs/ui-reference/floating-button-candidates/quick-bubble.svg`
- Read: `app/src/main/res/layout/activity_main.xml`
- Read: `app/src/main/java/com/opencamera/app/MainActivity.kt`

- [ ] **Step 1: Confirm canonical reference**

Run:

```bash
rtk sed -n '1,220p' docs/ui-reference/floating-focus-decision.md
```

Expected: document states Floating Focus is primary and Quick Bubble supplies secondary expansion behavior.

- [ ] **Step 2: Locate current controls to move**

Run:

```bash
rtk rg -n "buttonDevEntry|buttonFilterEntry|buttonQuickFlash|buttonQuickRatio|zoomCapsuleScroll|filterPanel|devConsolePanel|bottomSheet|topPanel" app/src/main/res/layout/activity_main.xml app/src/main/java/com/opencamera/app/MainActivity.kt
```

Expected: results show top `DEV` / filter entries, bottom quick flash/ratio, zoom capsule row, filter panel, dev console, and bottom sheet.

- [ ] **Step 3: Record implementation scope**

Create an implementation note in the worker's final response listing the exact controls that will move:

```text
Move from top: buttonFilterEntry, buttonDevEntry
Move from bottom: buttonQuickFlash, buttonQuickRatio, zoomCapsuleScroll/zoomCapsuleRow
Keep bottom: previewThumbnail, buttonShutter, buttonLensFacing, modeTrackScroll/modeTrack
Keep overlay: devConsolePanel, but trigger from floating DEV
Convert: filterPanel becomes palette panel entry surface
```

Do not edit code in this task.

## Task 2: XML Layout Skeleton

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Rename layout comments to Floating Focus**

In `activity_main.xml`, update comments so the main layers read:

```xml
<!-- Layer 1: Full-screen camera preview -->
<!-- Layer 2: Lightweight top status -->
<!-- Layer 3: Floating Focus utilities -->
<!-- Layer 4: Bottom capture deck -->
<!-- Overlay: Quick Bubble tools -->
<!-- Overlay: Palette panel -->
<!-- Overlay: Dev console -->
```

Expected: comments describe the new structure and do not mention a fixed side rail.

- [ ] **Step 2: Simplify top panel**

Keep `titleText` or replace its displayed use as compact mode/status, keep `buttonSettingsEntry`, and remove always-visible top placement for:

```xml
@+id/buttonFilterEntry
@+id/buttonDevEntry
```

Move these IDs into the floating utility group in Step 3. Do not delete the IDs because `MainActivity` binds them.

- [ ] **Step 3: Add floating utility group**

Insert this group after `permissionStatus` and before `modeTrackScroll`:

```xml
<LinearLayout
    android:id="@+id/floatingUtilityGroup"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginEnd="18dp"
    android:gravity="center"
    android:orientation="vertical"
    app:layout_constraintBottom_toTopOf="@id/bottomSheet"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintTop_toBottomOf="@id/topPanel">

    <Button
        android:id="@+id/buttonFilterEntry"
        style="@style/Widget.OpenCamera.FloatingActionButton"
        android:layout_width="52dp"
        android:layout_height="52dp"
        android:text="@string/button_palette_entry" />

    <Button
        android:id="@+id/buttonQuickLauncher"
        style="@style/Widget.OpenCamera.FloatingActionButton"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginTop="12dp"
        android:text="@string/button_quick_launcher" />

    <Button
        android:id="@+id/buttonDevEntry"
        style="@style/Widget.OpenCamera.FloatingDevButton"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginTop="12dp"
        android:text="@string/button_dev_entry"
        android:visibility="gone" />
</LinearLayout>
```

If the project cannot add `buttonQuickLauncher` without code changes in the same task, still add the XML ID and leave binding for Task 5.

- [ ] **Step 4: Add Quick Bubble overlay**

Insert this overlay near the floating utility group:

```xml
<LinearLayout
    android:id="@+id/quickBubblePanel"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginEnd="74dp"
    android:gravity="center"
    android:orientation="vertical"
    android:visibility="gone"
    app:layout_constraintBottom_toTopOf="@id/bottomSheet"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintTop_toBottomOf="@id/topPanel">

    <Button
        android:id="@+id/buttonQuickFlash"
        style="@style/Widget.OpenCamera.QuickBubbleButton"
        android:layout_width="64dp"
        android:layout_height="44dp" />

    <Button
        android:id="@+id/buttonQuickRatio"
        style="@style/Widget.OpenCamera.QuickBubbleButton"
        android:layout_width="64dp"
        android:layout_height="44dp"
        android:layout_marginTop="10dp" />
</LinearLayout>
```

Move the existing `buttonQuickFlash` and `buttonQuickRatio` IDs from the bottom deck into this panel. Do not leave duplicate IDs.

- [ ] **Step 5: Convert zoom capsule to floating/direct control**

Keep `zoomCapsuleScroll` and `zoomCapsuleRow`, but move them above the bottom deck or near the quick launcher so they do not live inside the bottom control panel. Use this structure:

```xml
<HorizontalScrollView
    android:id="@+id/zoomCapsuleScroll"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginBottom="14dp"
    android:clipToPadding="false"
    android:scrollbars="none"
    app:layout_constraintBottom_toTopOf="@id/modeTrackScroll"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent">

    <LinearLayout
        android:id="@+id/zoomCapsuleRow"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="horizontal" />
</HorizontalScrollView>
```

Expected: bottom deck no longer contains zoom.

- [ ] **Step 6: Preserve bottom deck essentials**

Ensure `bottomSheet` contains only:

```text
captureOutput
previewThumbnail
buttonShutter
buttonLensFacing
```

Mode rail can remain immediately above `bottomSheet` as `modeTrackScroll`, or move into the top edge of `bottomSheet` if constraints remain simple.

## Task 3: Styles And Strings

**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add floating styles**

Add these styles to `themes.xml`:

```xml
<style name="Widget.OpenCamera.FloatingActionButton" parent="Widget.MaterialComponents.Button">
    <item name="android:textAllCaps">false</item>
    <item name="android:textColor">#F7FAFF</item>
    <item name="android:textSize">11sp</item>
    <item name="backgroundTint">#B0071014</item>
    <item name="cornerRadius">26dp</item>
    <item name="android:minWidth">0dp</item>
    <item name="android:minHeight">0dp</item>
    <item name="android:paddingStart">4dp</item>
    <item name="android:paddingEnd">4dp</item>
</style>

<style name="Widget.OpenCamera.FloatingDevButton" parent="Widget.MaterialComponents.Button.OutlinedButton">
    <item name="android:textAllCaps">false</item>
    <item name="android:textColor">#75F2E8</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textSize">10sp</item>
    <item name="strokeColor">#6675F2E8</item>
    <item name="backgroundTint">#B00A171B</item>
    <item name="cornerRadius">24dp</item>
    <item name="android:minWidth">0dp</item>
    <item name="android:minHeight">0dp</item>
</style>

<style name="Widget.OpenCamera.QuickBubbleButton" parent="Widget.MaterialComponents.Button">
    <item name="android:textAllCaps">false</item>
    <item name="android:textColor">#F7FAFF</item>
    <item name="android:textSize">10sp</item>
    <item name="backgroundTint">#CC071014</item>
    <item name="cornerRadius">22dp</item>
    <item name="android:minWidth">0dp</item>
    <item name="android:minHeight">0dp</item>
</style>
```

- [ ] **Step 2: Add strings**

Add these strings to `strings.xml`:

```xml
<string name="button_palette_entry">Tone</string>
<string name="button_quick_launcher">Quick</string>
<string name="quick_bubble_panel_content_description">Quick camera controls</string>
<string name="floating_utility_group_content_description">Floating camera tools</string>
```

If `button_dev_entry` already exists, keep the existing value.

- [ ] **Step 3: Check for one-note palette**

Run:

```bash
rtk rg -n "#[0-9A-Fa-f]{6,8}" app/src/main/res/values/themes.xml app/src/main/res/drawable app/src/main/res/layout/activity_main.xml
```

Expected: colors are mostly neutral dark glass with cyan/teal accent; no new dominant purple/blue theme is introduced.

## Task 4: Render Model Tests Before Code Changes

**Files:**
- Modify: `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

- [ ] **Step 1: Add or update a test for control labels**

Add a test that asserts render-model text still supplies zoom, flash, ratio, quality, and resolution labels after layout movement. Use the existing test fixture pattern in `SessionUiRenderModelTest`.

Test intent:

```kotlin
@Test
fun cockpitControlsRemainAvailableForFloatingFocusLayout() {
    val state = defaultSessionStateForTest()
    val controls = sessionControlsRenderModel(state)

    assertThat(controls.zoomCapsules.map { it.label }).containsAtLeast("0.6", "1x", "2x", "5x")
    assertThat(controls.flashText).isNotBlank()
    assertThat(controls.ratioText).isNotBlank()
    assertThat(controls.stillQualityText).isNotBlank()
    assertThat(controls.stillSizeText).isNotBlank()
}
```

Adapt function/property names to the actual existing test helpers. Do not invent a new session owner.

- [ ] **Step 2: Run the focused test and verify failure or pass**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected before implementation: pass if only asserting existing render model behavior; fail only if property names need correction. Fix test names/properties until it compiles.

## Task 5: MainActivity Floating State And Binding

**Files:**
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`

- [ ] **Step 1: Add view fields and local state**

Add fields near existing button fields:

```kotlin
private lateinit var buttonQuickLauncher: Button
private lateinit var quickBubblePanel: LinearLayout
private var isQuickBubblePanelVisible = false
```

Do not put this state in session state.

- [ ] **Step 2: Bind new views**

In `onCreate`, after `buttonFilterEntry` and quick button bindings:

```kotlin
buttonQuickLauncher = findViewById(R.id.buttonQuickLauncher)
quickBubblePanel = findViewById(R.id.quickBubblePanel)
```

- [ ] **Step 3: Wire quick launcher**

In `bindActions()` add:

```kotlin
buttonQuickLauncher.setOnClickListener {
    isQuickBubblePanelVisible = !isQuickBubblePanelVisible
    if (isQuickBubblePanelVisible) {
        isSettingsPanelVisible = false
        isFilterPanelVisible = false
        isDevConsoleVisible = false
    }
    latestSessionState?.let(::render)
}
```

- [ ] **Step 4: Collapse quick bubbles when other overlays open**

Update existing handlers:

```kotlin
buttonSettingsEntry.setOnClickListener {
    isSettingsPanelVisible = !isSettingsPanelVisible
    if (isSettingsPanelVisible) {
        isFilterPanelVisible = false
        isDevConsoleVisible = false
        isQuickBubblePanelVisible = false
        currentSettingsSubpage = SettingsSubpage.ROOT
    }
    latestSessionState?.let(::render)
}
```

Apply the same collapse rule in `buttonFilterEntry` and `buttonDevEntry` handlers:

```kotlin
isQuickBubblePanelVisible = false
```

- [ ] **Step 5: Render floating overlay visibility**

In `renderControls(...)` or the existing render method that updates panels, add:

```kotlin
quickBubblePanel.isVisible = isQuickBubblePanelVisible
buttonQuickLauncher.alpha = if (isQuickBubblePanelVisible) 1f else 0.86f
```

Keep:

```kotlin
buttonDevEntry.isVisible = com.opencamera.app.BuildConfig.DEBUG
devConsolePanel.isVisible = isDevConsoleVisible && com.opencamera.app.BuildConfig.DEBUG
```

- [ ] **Step 6: Run MainActivity compile check**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

Expected: build succeeds with no unresolved view IDs or fields.

## Task 6: Palette Panel Behavior

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`

- [ ] **Step 1: Rename user-facing filter entry to palette**

Keep the existing `filterPanel` ID and filter render model for minimal risk, but make the entry read as palette/tone:

```xml
android:text="@string/button_palette_entry"
```

Expected: users see a style/tone tool, while the implementation can still reuse existing filter panel internals.

- [ ] **Step 2: Make filter panel visually compact**

Change `filterPanel` constraints so it behaves like Floating Focus reference:

```xml
android:layout_width="0dp"
android:layout_height="wrap_content"
android:layout_marginHorizontal="22dp"
android:layout_marginBottom="132dp"
app:layout_constraintBottom_toBottomOf="parent"
app:layout_constraintEnd_toEndOf="parent"
app:layout_constraintStart_toStartOf="parent"
app:layout_constraintTop_toTopOf="@id/cameraPreview"
app:layout_constraintVertical_bias="1.0"
```

If ConstraintLayout rejects `wrap_content` with both top and bottom constraints in the project version, remove the top constraint and keep bottom/start/end constraints.

- [ ] **Step 3: Keep advanced filter controls collapsed by default**

Confirm current `isFilterAdjustmentVisible` default remains:

```kotlin
private var isFilterAdjustmentVisible = false
```

Expected: opening Palette does not immediately show a settings-like wall of controls.

- [ ] **Step 4: Run focused UI render tests**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected: tests pass.

## Task 7: DEV Console Relocation And Release Guard

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- Test: `app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt`

- [ ] **Step 1: Confirm `DEV` is no longer in top panel**

Run:

```bash
rtk sed -n '42,95p' app/src/main/res/layout/activity_main.xml
```

Expected: top panel has no `buttonDevEntry`.

- [ ] **Step 2: Confirm `DEV` release guard remains**

Run:

```bash
rtk rg -n "buttonDevEntry\\.isVisible|BuildConfig.DEBUG|devConsolePanel\\.isVisible" app/src/main/java/com/opencamera/app/MainActivity.kt
```

Expected:

```kotlin
buttonDevEntry.isVisible = com.opencamera.app.BuildConfig.DEBUG
devConsolePanel.isVisible = isDevConsoleVisible && com.opencamera.app.BuildConfig.DEBUG
```

- [ ] **Step 3: Keep console tabs and export unchanged**

Run:

```bash
rtk rg -n "buttonDevTabKey|buttonDevTabCore|buttonDevTabError|buttonDevTabAll|buttonDevExport|DevLogExporter" app/src/main/res/layout/activity_main.xml app/src/main/java/com/opencamera/app/MainActivity.kt app/src/main/java/com/opencamera/app/DevLogExporter.kt
```

Expected: all tabs and export remain wired.

- [ ] **Step 4: Run dev log tests**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest
```

Expected: tests pass.

## Task 8: Full Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Run focused app tests**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.DevLogRenderModelTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Assemble debug APK**

Run:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run Stage 7 observability verification**

Run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Expected: script completes successfully and includes `:app:assembleDebug`.

- [ ] **Step 4: Record APK path**

Run:

```bash
rtk ls -lh <HOME>/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
```

Expected: APK exists.

## Task 9: Visual Acceptance Checklist

**Files:**
- Modify only if implementation differs: `docs/ui-reference/floating-focus-decision.md`

- [ ] **Step 1: Compare implementation against visual references**

Open or inspect:

```text
docs/ui-reference/floating-button-candidates/floating-focus.svg
docs/ui-reference/floating-button-candidates/quick-bubble.svg
```

Check:

```text
Initial screen:
- top strip is compact
- floating utilities are buttons, not a fixed column
- bottom deck has thumbnail, shutter, lens switch, mode rail only
- palette entry is discoverable
- DEV appears only in debug

Expanded state:
- quick actions expand locally
- palette panel is compact and style-oriented
- bottom deck does not move
- debug console opens from floating DEV
```

- [ ] **Step 2: Update documentation only if needed**

If implementation intentionally diverges from the reference, append a dated note to `docs/ui-reference/floating-focus-decision.md`:

```markdown
## Implementation Note

Date: 2026-05-21

The implementation keeps the zoom capsule centered above the mode rail instead of moving it next to the floating quick launcher.
Reason: the existing `zoomCapsuleRow` is already generated dynamically from session state, and keeping it centered preserves direct zoom access while still removing it from the bottom capture deck.
```

Do not add vague notes.

## Task 10: Install Command For 参考设备 Validation

**Files:**
- No source edits expected.

- [ ] **Step 1: Provide install command**

Use:

```bash
adb install -r <HOME>/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Provide crash log command**

If the app crashes on 参考设备, immediately run:

```bash
adb logcat -d -t 500 | grep -iE "FATAL EXCEPTION|AndroidRuntime|ClassCastException|InflateException|Resources\\$NotFoundException|com.opencamera"
```

Expected: logcat output includes the real root cause if there is still a startup crash.

## Final Handoff Criteria

- `docs/ui-reference/README.md` points to Floating Focus, not fixed side rail.
- `activity_main.xml` has floating utility controls and no duplicate IDs.
- `MainActivity.kt` binds every ID exactly once and keeps overlay visibility local.
- `BuildConfig.DEBUG` still gates `DEV`.
- Focused tests pass.
- `:app:assembleDebug` passes.
- `verify_stage_7_observability.sh` passes or any failure is documented with the exact failing command and log excerpt.
