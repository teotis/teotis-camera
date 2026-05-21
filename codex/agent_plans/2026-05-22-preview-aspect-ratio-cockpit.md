# Preview Aspect Ratio And Camera Cockpit Plan

> **For agentic workers:** Use this as a self-contained implementation handoff. Run shell commands through `rtk`. Keep UI as render-state plus intent dispatch; do not move camera runtime ownership into views.

**Goal:** Mature the camera screen around capture frame, preview viewport, and bottom cockpit so aspect ratio changes feel intentional and the preview area is not visually ambiguous.

**Recommended approach:** Separate three concepts that are currently too easy to confuse: output frame ratio, preview viewport/mask, and bottom controls safe area.

---

## Current Code Facts

- `PreviewView` fills the whole screen with `app:scaleType="fillCenter"`.
- `PreviewOverlayView` can draw grid lines, filter overlay, watermark hint, and a frame guideline.
- `PhotoModePlugin` and `HumanisticModePlugin` have mode-local frame ratio cycling through `4:3`, `16:9`, and `1:1`.
- `PhotoFrameRatioPostProcessor` can crop final JPEG output according to the `frameRatio` metadata tag.
- The visible preview frame is currently only a guideline. It is not yet a full viewport contract with dimmed outside area, safe controls, or direct ratio affordance.
- `buttonQuickRatio` currently dispatches `StillCaptureResolutionToggled`, which is size/resolution, not frame ratio. This can confuse users who expect "比例/画幅".

## UX Contract

- The user should always know what part of the preview will be kept in the final photo.
- Switching aspect ratio should visibly change the framed capture area immediately.
- Bottom controls should sit outside the perceived capture frame when possible.
- `比例/画幅` must mean frame ratio, while `尺寸/画质` must mean resolution or quality.
- The screen should remain preview-first: top status, right rail, slim zoom strip, mode track, compact bottom cockpit.

## Implementation Scope

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- Tests in `core/session`, `app`, and affected feature modules

Optional later:

- Persist default frame ratio per mode in `core/settings`.

## Design

### 1. Rename User-Facing Concepts

Use these labels consistently:

- `画幅` or `比例`: selected output frame ratio (`4:3`, `16:9`, `1:1`).
- `尺寸`: still output size/resolution (`12MP`, `8MP`, etc.).
- `画质`: latency/quality preference.

Change Quick Bubble:

- `buttonQuickRatio` should dispatch frame-ratio cycling, not `StillCaptureResolutionToggled`.
- Move still resolution to settings or a `尺寸` quick item.

Minimal first step:

```kotlin
buttonQuickRatio.setOnClickListener {
    dispatch(SessionIntent.TertiaryActionPressed)
}
```

Only do this for modes whose `uiSpec.tertiaryActionLabel` represents frame ratio. If not all modes support it, render the button disabled with a reason.

### 2. Promote Frame Ratio Into Render Model

Add an app render model that can be derived from `activeEffectSpec` or mode snapshot:

```kotlin
internal data class PreviewFrameRenderModel(
    val isVisible: Boolean,
    val label: String,
    val ratioWidth: Int,
    val ratioHeight: Int,
    val dimOutsideFrame: Boolean
)
```

`PreviewOverlayView` should draw:

- A clear frame rect.
- A dimmed outside area when a non-fullscreen ratio is selected.
- Existing grid lines clipped or visually aligned to the frame rect if feasible.

Keep final JPEG crop in `PhotoFrameRatioPostProcessor`; the overlay is only preview guidance.

### 3. Avoid Hidden Mode-Local State Drift

The current frame ratio index lives inside mode controllers. That is acceptable for a minimal loop, but a mature product should not reset unexpectedly.

Add one of these follow-ups:

- Short-term: include current frame ratio label in `ModeSnapshot.uiSpec.detail` and render it in Quick/Status.
- Better: add persisted `defaultFrameRatioByMode` or `photo.defaultFrameRatio`, then let mode plugins read it from `SessionSettingsSnapshot`.

Do not create frame-ratio state in `MainActivity`.

### 4. Cockpit Layout Adjustments

Make the control stack read as stable zones:

1. Preview feed and overlay.
2. Right rail: `色调`, `快捷`, `镜头` or labs.
3. Zoom strip.
4. Mode track.
5. Bottom cockpit: thumbnail, shutter, lens.

Constraints:

- Bottom cockpit should be compact, around current 112-132dp including nav insets.
- Capture status is one line.
- No tall debug panel in release builds.
- Do not place cards inside cards.

### 5. Tests

Add tests:

- `PreviewOverlayRenderModelTest` or `SessionUiRenderModelTest`: selected frame ratio produces visible frame model.
- `DefaultCameraSessionTest`: tertiary action in photo/humanistic mode updates mode snapshot and trace.
- `PhotoFrameRatioPostProcessorTest`: existing final crop tests remain passing.
- `CameraCockpitRenderModelTest`: Quick ratio label and enabled state match active mode.

## Verification

Focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Stage:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Manual real-device smoke:

- Open photo mode.
- Tap `画幅/比例`; frame overlay changes among `4:3`, `16:9`, `1:1`.
- Capture a photo; final saved output matches the visible frame ratio.
- Bottom controls do not imply they are part of the capture frame.

## Non-Goals

- Do not rewrite the entire UI architecture.
- Do not make PreviewView itself crop to every ratio in this loop; start with truthful overlay and final-output match.
- Do not move frame ratio into `MainActivity` local state.
- Do not add visual mockup generation here; screenshot judgment belongs in the multimodal deferred plan.
