# Zoom Strip Interaction Plan

> **For agentic workers:** Use this as a self-contained implementation handoff. Run shell commands through `rtk`. Keep zoom runtime ownership in Session Kernel and Device Adapter.

**Goal:** Replace the current heavy-feeling zoom buttons with a narrow horizontal zoom strip where each number is a direct zoom point.

**Recommended approach:** Keep the existing `ApplyZoomRatio` contract, but redesign the app-layer zoom UI as a slim, exact-selection strip.

---

## Current Code Facts

- Runtime zoom owner already exists:
  - `SessionIntent.ApplyZoomRatio(ratio)`
  - `SessionEffect.ApplyZoomRatio`
  - `DeviceCommand.UpdateZoomRatio`
  - `CameraXCaptureAdapter.updateZoomRatio()`
- `MainActivity.renderZoomCapsules()` already dispatches exact `SessionIntent.ApplyZoomRatio(capsule.ratio)`.
- The current XML still has both:
  - `zoomCapsuleScroll` above the mode track
  - bottom right `buttonZoomRatio`, which cycles via `ZoomRatioToggled`
- Current zoom chips are normal `Button`s with 48dp minimum width and 32dp minimum height. On a phone screen this can still read as a row of buttons, not a camera zoom scale.

## UX Contract

- Show a narrow horizontal zoom bar above the mode track.
- Labels should be compact numeric points, for example `0.7`, `1x`, `2`, `5`.
- Tapping a point selects that exact ratio. No cycling surprise.
- Active point should be visually clear but not bulky.
- Pinch zoom remains continuous and updates the current zoom state.
- Bottom cockpit should not need a second zoom button unless it is only a compact current-value indicator.

## Implementation Scope

Modify:

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/drawable/bg_zoom_chip.xml`
- `app/src/main/res/drawable/bg_zoom_chip_active.xml`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

Do not modify:

- `DefaultCameraSession` zoom behavior unless tests expose a runtime bug.
- `CameraXCaptureAdapter` zoom execution.

## Design

### 1. Render Model

Keep `ZoomCapsuleRenderModel` if changing names is too broad, but treat it as a preset point:

```kotlin
internal data class ZoomPointRenderModel(
    val label: String,
    val ratio: Float,
    val isActive: Boolean,
    val isEnabled: Boolean = true
)
```

If renaming causes churn, keep the old type and update docs/tests to describe it as a point.

Labels:

- `1f` -> `1x`
- Integer ratios greater than 1 -> `2`, `5`
- Decimal ratios -> `0.7`

Use `AppTextResolver.zoomRatioLabel()` only if it can preserve compact labels. Avoid `5.0x` in the strip.

### 2. XML Layout

Keep `zoomCapsuleScroll` position above `modeTrackScroll`, but make it visually a strip:

- Height: 30-34dp.
- Chips: 34-42dp wide, 28-30dp high.
- Gap: 4-6dp.
- No all-caps.
- Background inactive: translucent dark, thin border or no border.
- Background active: subtle accent fill or underline.

Bottom cockpit:

- Prefer removing `buttonZoomRatio` from the right column and leaving `buttonLensFacing` as the only button there.
- If keeping a zoom current-value control, render it as a small text value and do not make it compete with the strip.
- Keep `buttonZoomRatio` cycling behavior only if it is visually distinct and not the primary path.

### 3. Interaction

In `renderZoomCapsules()`:

- Replace `Button` with `TextView` or a very compact button style.
- Keep `setOnClickListener { dispatch(SessionIntent.ApplyZoomRatio(capsule.ratio)) }`.
- Keep disabled state if zoom unsupported.

Pinch:

- `GesturePolicy` already dispatches `ApplyZoomRatio`.
- If current zoom is between preset points, do not force-highlight the nearest point unless it is within a small threshold such as `0.05f`.
- Show the compact current value in the status line or transient feedback if available.

### 4. Tests

Add render-model tests:

- Supported ratios `[0.7, 1.0, 2.0, 5.0]` produce compact labels.
- Active ratio exactly matches current ratio.
- In-between pinch ratio such as `1.4` has no active preset unless threshold behavior is implemented.
- Unsupported zoom hides the strip or marks it disabled consistently.

Add UI wiring test if practical:

- Verify preset tap path dispatches `ApplyZoomRatio`, not `ZoomRatioToggled`.

## Verification

Focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

Stage:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Manual real-device smoke:

- Tap each zoom point; the selected ratio is exact.
- Pinch zoom still works.
- Mode row remains scrollable and does not steal zoom taps.
- Bottom cockpit feels balanced with thumbnail left, shutter center, lens right.

## Non-Goals

- Do not redesign the whole camera screen in this loop.
- Do not change CameraX zoom execution.
- Do not add vendor lens-switch semantics behind zoom points yet; this strip is ratio selection.
