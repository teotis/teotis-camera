# 画幅生效与预览框适配方案

> **For agentic workers:** This is a self-contained implementation handoff for a non-multimodal agent. Do not rely on screenshots. Use the code facts, file paths, text-only acceptance checks, and tests below. Run every shell command through `rtk`.

**Goal:** Fix the real-device issue where "画幅/比例" appears ineffective, and make aspect-ratio selection obvious by exposing direct ratio options and making the preview frame change immediately.

**Recommended approach:** Treat "成片画幅" as the single user-facing feature for this loop. Hide or de-prioritize the current "预览比例" control because it only changes `SessionState.previewRatio` and does not affect preview masking, CameraX, or saved output.

---

## Current Code Facts

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt` defines `FrameRatio` with `4:3`, `16:9`, and `1:1`.
- Still modes already maintain a mode-local frame ratio:
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- Those modes update frame ratio only through `ModeIntent.TertiaryActionPressed`, then emit:
  - `FrameEffect(currentFrameRatio())`
  - capture metadata tag `frameRatio`
  - mode snapshot detail text containing `Frame <ratio>`
- `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt` crops saved JPEG output when `ShotResult.metadata.customTags["frameRatio"]` is present.
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` can draw a weak `FrameGuidelineSpec` border from `activeEffectSpec`, but it does not dim the outside area and grid lines still use the full view.
- `app/src/main/java/com/opencamera/app/MainActivity.kt` currently wires:
  - `buttonQuickRatio -> SessionIntent.StillCaptureResolutionToggled`
  - `buttonPreviewRatio -> SessionIntent.PreviewRatioToggled`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` handles `PreviewRatioToggled` by updating only `SessionState.previewRatio` and trace. It does not update `activeEffectSpec`, `DeviceGraphSpec`, the preview overlay frame, or saved output.
- `app/src/main/res/values/strings.xml` labels `button_quick_ratio` as `分辨率`, while `values-en/strings.xml` labels it as `Ratio`. This mismatch can make testers think they are changing frame ratio when they are changing still resolution.
- There is no direct visible UI path from the latest cockpit controls to `SessionIntent.TertiaryActionPressed`, so the real frame-ratio chain is effectively hidden.

## Problem Diagnosis

The real-device report is consistent with two disconnected concepts:

1. **Real output frame ratio exists but is not reachable.** The chain `TertiaryActionPressed -> FrameEffect -> frameRatio metadata -> PhotoFrameRatioPostProcessor` is implemented, but the visible quick button does not dispatch it.
2. **Preview ratio looks like a ratio feature but is a no-op for the user.** `PreviewRatioToggled` updates a label/state only. It creates an expectation that preview or saved framing will change, but nothing visible changes in the camera feed.
3. **The preview frame is too subtle.** Even when `FrameEffect` is active, `PreviewOverlayView` draws only a border. There is no dimmed outside area, no clipped grid, and no explicit option row, so users cannot tell what will be kept in the final photo.

Therefore, do not fix this by adding another label. The implementation must make one user action update all of these: selected ratio option, preview frame/mask, mode snapshot/effect spec, capture metadata, and saved JPEG crop.

## UX Contract

- The control label is `画幅`, not `预览比例`.
- The visible choices are explicit chips: `4:3`, `16:9`, `1:1`.
- Tapping a chip changes the selected chip immediately.
- Tapping a chip changes the preview frame immediately:
  - `4:3`: normal still-photo frame.
  - `16:9`: visibly shallower frame with top/bottom excluded on portrait screens.
  - `1:1`: centered square frame.
- Areas outside the selected frame are dimmed enough to be understood as outside the final crop.
- Grid lines, if enabled, align to the active frame rather than the whole screen.
- Bottom cockpit, mode track, and zoom strip must read as controls outside the perceived capture frame.
- Saved JPEG output must match the selected visible frame ratio within a small tolerance.
- Video and document modes should not pretend to support this feature. Show disabled state or a short hint.

## Architecture Contract

Keep the existing ownership boundaries:

- UI renders state and dispatches intents only.
- Session kernel validates/blocking rules and forwards mode intents.
- Mode plugins own mode-specific frame-ratio support and produce `FrameEffect`.
- Media pipeline owns final JPEG crop.
- Do not create frame-ratio state in `MainActivity`.
- Do not create a second session kernel in a coordinator, view, adapter, or panel.

For this loop, use `activeEffectSpec.find<FrameEffect>()` as the source for the current visible capture frame. This avoids duplicating state and keeps the overlay aligned with capture metadata.

## Implementation Plan

### 1. Add A Direct Frame Ratio Intent

Modify `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`:

```kotlin
import com.opencamera.core.media.FrameRatio

sealed interface SessionIntent {
    // existing intents...
    data class FrameRatioSelected(val ratio: FrameRatio) : SessionIntent
}
```

Modify `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`:

```kotlin
import com.opencamera.core.media.FrameRatio

sealed interface ModeIntent {
    // existing intents...
    data class FrameRatioSelected(val ratio: FrameRatio) : ModeIntent
}
```

Modify `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`:

```kotlin
when (intent) {
    // existing branches...
    is SessionIntent.FrameRatioSelected -> handleModeIntent(
        ModeIntent.FrameRatioSelected(intent.ratio)
    )
}
```

Also update the active-shot blocking message in `handleModeIntent()` so `FrameRatioSelected` uses wording like:

```kotlin
"等待当前拍摄完成后才能切换画幅"
```

For active video recording, use:

```kotlin
"停止录制后才能切换画幅"
```

Keep `TertiaryActionPressed` for backward compatibility and existing tests. The direct intent is needed for explicit chips.

### 2. Implement Direct Selection In Still Modes

Apply the same pattern to:

- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`

Each still mode should handle both cycling and direct selection. Use the mode's real effect-builder signature instead of introducing a generic helper.

For `PhotoModePlugin.kt`, the direct selection should look like:

```kotlin
override suspend fun handle(intent: ModeIntent): ModeSignal {
    return when (intent) {
        // existing branches...
        ModeIntent.TertiaryActionPressed -> cycleFrameRatio()
        is ModeIntent.FrameRatioSelected -> selectFrameRatio(intent.ratio)
    }
}

private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal {
    val nextIndex = frameRatios.indexOf(ratio)
    if (nextIndex < 0) {
        return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
    }
    frameRatioIndex = nextIndex
    context.eventSink("photo.frame-ratio.selected.${ratio.eventTag()}")
    mutableSnapshot.value = buildSnapshot(headline = "Frame ratio updated")
    context.onEffectSpecChanged(buildEffectSpec(currentFlashMode()))
    return ModeSignal.ShowHint("画幅：${ratio.label}")
}
```

For the other still modes, use the same body but keep their existing event prefix and effect-builder signature:

| File | Event prefix | Effect update |
| --- | --- | --- |
| `NightModePlugin.kt` | `night.frame-ratio.selected.${ratio.eventTag()}` | `context.onEffectSpecChanged(buildEffectSpec())` |
| `PortraitModePlugin.kt` | `portrait.frame-ratio.selected.${ratio.eventTag()}` | `context.onEffectSpecChanged(buildEffectSpec())` |
| `HumanisticModePlugin.kt` | `humanistic.frame-ratio.selected.${ratio.eventTag()}` | `context.onEffectSpecChanged(buildEffectSpec())` |
| `ProModePlugin.kt` | `pro.frame-ratio.selected.${ratio.eventTag()}` | `context.onEffectSpecChanged(buildEffectSpec())` |

Modify unsupported modes:

- `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`: return `ModeSignal.ShowHint("视频模式暂不支持画幅切换")`.
- `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`: return `ModeSignal.ShowHint("文档模式使用自动裁边，不使用普通画幅")`.

### 3. Add A Frame Ratio Render Model

Modify `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` or `CameraCockpitRenderModel.kt`.

Add:

```kotlin
internal data class FrameRatioOptionRenderModel(
    val label: String,
    val ratio: FrameRatio,
    val isSelected: Boolean,
    val isEnabled: Boolean
)

internal data class FrameRatioControlRenderModel(
    val title: String,
    val currentLabel: String,
    val options: List<FrameRatioOptionRenderModel>,
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val disabledReason: String?
)
```

Derive current frame ratio from the effect spec:

```kotlin
private val stillModesWithFrameRatio = setOf(
    ModeId.PHOTO,
    ModeId.NIGHT,
    ModeId.PORTRAIT,
    ModeId.HUMANISTIC,
    ModeId.PRO
)

internal fun frameRatioControlRenderModel(state: SessionState): FrameRatioControlRenderModel {
    val current = state.activeEffectSpec.find<FrameEffect>()?.ratio ?: FrameRatio.RATIO_4_3
    val isSupportedMode = state.activeMode in stillModesWithFrameRatio
    val isBusy = state.activeShot != null || state.countdownRemainingSeconds != null
    val enabled = isSupportedMode && !isBusy
    val reason = when {
        !isSupportedMode -> "当前模式不支持画幅"
        state.activeShot != null -> "等待当前拍摄完成后才能切换画幅"
        state.countdownRemainingSeconds != null -> "倒计时结束后才能切换画幅"
        else -> null
    }
    return FrameRatioControlRenderModel(
        title = "画幅",
        currentLabel = current.label,
        options = FrameRatio.entries.map { ratio ->
            FrameRatioOptionRenderModel(
                label = ratio.label,
                ratio = ratio,
                isSelected = ratio == current,
                isEnabled = enabled
            )
        },
        isVisible = true,
        isEnabled = enabled,
        disabledReason = reason
    )
}
```

If importing `FrameEffect` into the app render model creates a cycle, move this function to a file that already depends on `core/effect`. The app module already uses `PreviewEffectAdapter`, so this should be acceptable.

### 4. Replace The Misleading Quick Controls

Modify `app/src/main/res/layout/activity_main.xml`.

Replace the single `buttonQuickRatio` and the `buttonPreviewRatio` production UI with a direct frame-ratio group. A simple XML shape is enough:

```xml
<TextView
    android:id="@+id/frameRatioQuickTitle"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="画幅" />

<LinearLayout
    android:id="@+id/frameRatioOptionRow"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <Button
        android:id="@+id/buttonFrameRatio43"
        style="@style/Widget.OpenCamera.QuickBubbleButton"
        android:layout_width="48dp"
        android:layout_height="40dp"
        android:text="4:3" />

    <Button
        android:id="@+id/buttonFrameRatio169"
        style="@style/Widget.OpenCamera.QuickBubbleButton"
        android:layout_width="48dp"
        android:layout_height="40dp"
        android:text="16:9" />

    <Button
        android:id="@+id/buttonFrameRatio11"
        style="@style/Widget.OpenCamera.QuickBubbleButton"
        android:layout_width="48dp"
        android:layout_height="40dp"
        android:text="1:1" />
</LinearLayout>
```

Exact styling can reuse existing quick-bubble button drawables. The key requirement is that the options are visible at the same time.

Modify `app/src/main/java/com/opencamera/app/MainActivity.kt`:

- Add view fields for the title, row, and three buttons.
- In `findViews`, bind the new views.
- Set click listeners:

```kotlin
buttonFrameRatio43.setOnClickListener {
    dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_4_3))
}
buttonFrameRatio169.setOnClickListener {
    dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_16_9))
}
buttonFrameRatio11.setOnClickListener {
    dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_1_1))
}
```

- In `renderQuickBubble`, render selected/enabled state from `frameRatioControlRenderModel(state)`.
- Hide `buttonPreviewRatio` or remove it from the panel in this loop. Do not leave a visible no-op "预览比例" control.
- Rename Chinese/English strings:
  - `button_quick_ratio`: `画幅` / `Frame`
  - keep `button_still_resolution`: `分辨率` / `Resolution`

Hotfix fallback only if the XML refactor is too large: make `buttonQuickRatio` dispatch `SessionIntent.TertiaryActionPressed` and label it `画幅\n<current>`. This is acceptable only as an intermediate commit; the final result still needs explicit chips.

### 5. Promote Preview Frame Into The Overlay Model

Modify `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`:

```kotlin
internal data class PreviewFrameRenderModel(
    val ratio: FrameRatio,
    val label: String,
    val dimOutsideFrame: Boolean
)

internal data class PreviewOverlayRenderModel(
    val gridMode: CompositionGridMode,
    val isGridVisible: Boolean,
    val countdownLabel: String?,
    val isCountdownVisible: Boolean,
    val effectModel: PreviewEffectRenderModel? = null,
    val frame: PreviewFrameRenderModel? = null
) {
    val isVisible: Boolean
        get() = isGridVisible || isCountdownVisible || effectModel != null || frame != null
}
```

Derive `frame` from the same `FrameEffect`:

```kotlin
val frameRatio = state.activeEffectSpec.find<FrameEffect>()?.ratio
val frame = if (previewSupportsOverlay && frameRatio != null) {
    PreviewFrameRenderModel(
        ratio = frameRatio,
        label = frameRatio.label,
        dimOutsideFrame = true
    )
} else {
    null
}
```

If keeping the existing `effectModel.frameGuideline`, avoid drawing two borders. Prefer the new `frame` model for frame/mask drawing, while retaining `effectModel` for filter and watermark preview.

### 6. Draw A Real Frame And Mask

Modify `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`.

Add paints:

```kotlin
private val frameScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(116, 0, 0, 0)
    style = Paint.Style.FILL
}

private val frameLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
}
```

Extract a pure rect function so it can be tested:

```kotlin
internal fun computePreviewFrameRect(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int,
    ratioHeight: Int,
    horizontalPaddingPx: Float = 0f,
    topInsetPx: Float = 0f,
    bottomInsetPx: Float = 0f
): RectF {
    val availableLeft = horizontalPaddingPx
    val availableTop = topInsetPx
    val availableRight = viewWidth - horizontalPaddingPx
    val availableBottom = viewHeight - bottomInsetPx
    val availableWidth = (availableRight - availableLeft).coerceAtLeast(1f)
    val availableHeight = (availableBottom - availableTop).coerceAtLeast(1f)
    val targetRatio = ratioWidth.toFloat() / ratioHeight.toFloat()
    val availableRatio = availableWidth / availableHeight
    return if (targetRatio > availableRatio) {
        val w = availableWidth
        val h = w / targetRatio
        val top = availableTop + (availableHeight - h) / 2f
        RectF(availableLeft, top, availableRight, top + h)
    } else {
        val h = availableHeight
        val w = h * targetRatio
        val left = availableLeft + (availableWidth - w) / 2f
        RectF(left, availableTop, left + w, availableBottom)
    }
}
```

Draw the outside dim using an even-odd path:

```kotlin
private fun drawPreviewFrame(canvas: Canvas, frame: PreviewFrameRenderModel) {
    val rect = computePreviewFrameRect(
        viewWidth = width,
        viewHeight = height,
        ratioWidth = frame.ratio.width,
        ratioHeight = frame.ratio.height,
        horizontalPaddingPx = 12f * density,
        topInsetPx = 0f,
        bottomInsetPx = 0f
    )
    if (frame.dimOutsideFrame) {
        val outsidePath = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
            addRect(rect, android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(outsidePath, frameScrimPaint)
    }
    canvas.drawRect(rect, frameGuidelinePaint)
    canvas.drawText(frame.label, rect.left + 10f * density, rect.top + 20f * density, frameLabelPaint)
}
```

Then:

- Draw `frame` before countdown and after filter tint.
- Update grid drawing so when `frame != null`, grid lines use the frame rect instead of full view.
- Do not dim controls. The overlay is already below controls in the XML layer order, so the scrim will only affect the camera preview behind them.

Optional improvement: pass safe insets from `MainActivity` to the overlay so the frame avoids `topPanel`, `modeTrackScroll`, `zoomCapsuleScroll`, and `bottomSheet`. If doing this, keep it as a view-render concern, not session state.

### 7. Keep Saved Output Verification In The Media Pipeline

The first implementation should not rewrite `PhotoFrameRatioPostProcessor.kt` unless tests show a real failure. It already:

- reads `frameRatio` from metadata,
- crops JPEG output,
- preserves a subset of EXIF,
- emits pipeline notes such as `frame-ratio:applied:16:9`.

Add or keep tests for:

- `decidePhotoFrameRatioWork()` returns crop work when metadata contains `frameRatio`.
- `computeCenterCropBounds()` returns expected bounds for `16:9`, `1:1`, and `4:3`.
- saved result receives `frame-ratio:applied:<ratio>` pipeline note after crop.

### 8. Defer Persistence Unless Needed

Current frame ratio resets when a mode controller is recreated. Do not solve that inside `MainActivity`.

If persistence is required after the main fix:

- Add persisted setting in `core/settings` such as `defaultFrameRatioByMode` or `photo.defaultFrameRatio`.
- Let mode plugins read it from `SessionSettingsSnapshot`.
- Add settings-store serialization tests.

This is a follow-up, not required to fix the latest APK usability issue.

## Tests To Add Or Update

### Core Session

File: `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

Add:

- `photo mode applies explicit frame ratio selection`
- `explicit frame ratio selection updates active effect spec`
- `explicit frame ratio selection is blocked during active photo capture`
- `video mode reports frame ratio unsupported`

Expected assertions:

```kotlin
session.dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_16_9))
assertEquals(FrameRatio.RATIO_16_9, session.state.value.activeEffectSpec.find<FrameEffect>()?.ratio)
assertTrue(session.state.value.modeSnapshot.state.detail.contains("Frame 16:9"))

session.dispatch(SessionIntent.ShutterPressed)
advanceUntilIdle()
val shot = assertNotNull(session.state.value.activeShot)
assertEquals("16:9", shot.saveRequest.metadata.customTags["frameRatio"])
```

### App Render Model

File: `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt` or `SessionUiRenderModelTest.kt`

Add:

- `frame ratio control exposes all options`
- `frame ratio control selects ratio from active effect spec`
- `frame ratio control disables in video mode`
- `preview overlay frame follows active frame effect`
- `preview overlay remains visible when grid is off but frame is active`

### Overlay Geometry

File: `app/src/test/java/com/opencamera/app/PreviewOverlayViewTest.kt` or a new JVM-friendly geometry test file.

Add tests for `computePreviewFrameRect()`:

- portrait view `1080x2400`, ratio `1:1` produces square bounds.
- portrait view `1080x2400`, ratio `16:9` produces full width and shallow height.
- portrait view `1080x2400`, ratio `4:3` produces full width and height `810`.

Use tolerance of `1f` for float comparisons.

### Media Pipeline

File: `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt`

Keep existing crop tests passing. Add only if missing:

- `frame ratio processor adds applied note for selected ratio`
- `missing frame ratio metadata leaves result unchanged`

## Verification Commands

Focused commands:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Stage verification:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle reports transient `.codex-build/OpenCamera/.../classes/kotlin/main/com` missing errors, rerun the smallest failing command serially before treating it as a product regression.

## Text-Only Real-Device Smoke

This checklist is written so a non-multimodal agent can execute or hand it to a tester without screenshot interpretation.

1. Open latest debug APK.
2. Enter `拍照` mode.
3. Open `快捷`.
4. Confirm there is a `画幅` control with visible `4:3`, `16:9`, and `1:1` choices.
5. Tap `16:9`.
6. Confirm status/hint says `画幅：16:9` or equivalent.
7. Confirm the preview frame visibly changes immediately to a wide rectangle.
8. Tap `1:1`.
9. Confirm the preview frame becomes a centered square.
10. Capture a photo.
11. Confirm the latest pipeline/debug text contains `frame-ratio:applied:1:1` or the saved image dimensions are square.
12. Switch to `视频`.
13. Confirm the frame-ratio choices are disabled or show `视频模式暂不支持画幅切换`.
14. Confirm there is no visible `预览比例` button that changes only a label.

## Non-Goals

- Do not rewrite the whole cockpit UI.
- Do not make `PreviewView` itself crop/rebind for every ratio in this loop.
- Do not add frame-ratio state to `MainActivity`.
- Do not add screenshot-based assertions.
- Do not move media crop ownership out of the media pipeline.
- Do not introduce a new settings persistence layer unless the main fix is complete and verified.

## Acceptance Criteria

- The user can select `4:3`, `16:9`, or `1:1` directly.
- The selected ratio is visible as selected state in the quick UI.
- The preview frame/mask changes immediately after selection.
- The next still capture carries matching `frameRatio` metadata.
- Saved JPEG output is cropped by `PhotoFrameRatioPostProcessor`.
- `PreviewRatioToggled` is not exposed as a misleading production control.
- Focused tests and `rtk ./scripts/verify_stage_7_observability.sh` pass.

## Implementation Order

1. Add direct session/mode frame-ratio intents.
2. Update still modes and unsupported modes.
3. Add app render model for frame-ratio controls.
4. Replace quick-panel ratio UI with explicit chips.
5. Promote preview frame model and draw dimmed outside area.
6. Add tests.
7. Run focused verification.
8. Run Stage 7 verification.

## Risk Notes

- The existing `PreviewRatio` type may be useful in a future viewport/rebind feature, but it should not be user-facing until it affects something visible.
- `FrameEffect` currently has `EffectTarget.CAPTURE`, yet the preview adapter still reads it for guidance. That is acceptable for this loop because it is a preview of the capture crop, not a separate preview-only effect.
- Cropping after capture can increase postprocess latency. Keep pipeline timing notes visible and do not block this UI fix on CameraX `ViewPort` support.
- If final saved crop still appears wrong after UI routing is fixed, inspect `PhotoFrameRatioPostProcessor` pipeline notes first. The likely failure categories are `missing-output-handle`, `unsupported-mime`, `input-unavailable`, or `output-unavailable`.
