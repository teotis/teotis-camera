# Orientation-Adaptive Camera UI Implementation Plan

> **For agentic workers:** This is a non-multimodal implementation handoff. Do not judge visual aesthetics from screenshots. Implement deterministic orientation contracts, UI rotation behavior, CameraX target-rotation wiring, and tests. Use `rtk` for every shell command.

**Goal:** Make portrait/landscape camera use feel intentional: the camera cockpit keeps its stable topology and component bounds, while readable text/directional glyphs rotate with the user's physical device orientation and captured media receives the correct target rotation.

## Product Decision

Implement a **fixed camera cockpit topology with rotating content**.

- Do not create `layout-land/activity_main.xml`.
- Do not rearrange top/bottom/right cockpit regions just because the device is held sideways.
- Do not rotate `PreviewView`, the preview overlay coordinate system, the transparent scrim, or the thumbnail image.
- Rotate only text-bearing controls and direction-sensitive glyph content so labels remain readable when the user holds the phone in landscape.
- Treat preview/capture/media rotation as a device-runtime concern, not as a layout trick.

This matches the user's preferred direction: UI component positions and sizes remain stable, while text and symbols change orientation.

## External Reference Snapshot

Use these as product/engineering references, not as pixel-perfect copies:

- Android CameraX official orientation guidance: [CameraX use case rotations](https://developer.android.com/media/camera/camerax/orientation-rotation?hl=en). Key implication for this project: when camera orientation changes without Activity recreation, use an orientation/display listener and update CameraX use-case target rotation; locked camera UIs need explicit target-rotation handling.
- Apple AVFoundation official rotation API: [AVCaptureDevice.RotationCoordinator](https://developer.apple.com/documentation/avfoundation/avcapturedevice/rotationcoordinator?language=objc). Key implication: mature camera stacks separate capture/preview rotation from app layout.
- Apple Camera Control HIG: [Camera Control](https://developer.apple.com/design/human-interface-guidelines/camera-control?changes=_5). Key implication: hardware/control overlays and camera UI must avoid fighting the capture interface, especially in landscape.
- Apple Support: [Use the Camera Control on iPhone](https://support.apple.com/guide/iphone/use-the-camera-control-iph0c397b154/ios). Key implication: camera controls remain consistent across photo/video and include focus/exposure/zoom semantics rather than a separate landscape-only UI.
- OPPO Find X8 Series official press material: [Find X8 Series launch](https://www.oppo.com/en/newsroom/press/find-x8-series-coloros-15-launch-camera-ai/). Key implication: OPPO emphasizes a stable camera control surface and a Quick Button that can launch camera and control zoom, reinforcing stable controls over layout churn.
- vivo X300 Ultra official page: [vivo X300 Ultra](https://www.vivo.com.cn/vivo/x300ultra). Key implication: vivo sells the camera experience around grip, professional controls, full-focal-length shooting, and optional camera-like accessories; orientation handling should respect physical shooting posture.

## Current Code Facts

- `app/src/main/AndroidManifest.xml` currently has `android:configChanges="orientation|screenSize|keyboardHidden"` but does not lock the camera Activity to a fixed visual orientation.
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt` already contains `CockpitDisplayOrientation`, `CockpitOrientationRenderModel`, and `orientationRenderModel(displayRotation)`.
- `app/src/main/java/com/opencamera/app/MainActivity.kt` already has `applyControlRotationForDisplay()`, but it:
  - reads `display?.rotation`, which is not enough for a fixed camera cockpit;
  - covers only a small subset of controls;
  - misses dynamic zoom chips and mode labels created during render;
  - does not connect orientation to CameraX target rotation.
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` currently builds `Preview`, `ImageCapture`, and `VideoCapture` without explicit target rotation.
- `PreviewOverlayView`, `PreviewOverlayGeometryTest`, `PreviewContentGeometryTest`, and `PhotoFrameRatioPostProcessorTest` already established a centered, direction-aware preview/crop geometry baseline. Do not regress it.

## Architecture Boundary

Ownership must stay clean:

- **App Shell / UI** owns physical-orientation observation and visual content rotation.
- **Session Kernel** owns runtime orientation state, trace, and effects that change device runtime behavior.
- **Device Adapter** owns CameraX `targetRotation` application.
- **Preview geometry** owns active frame/grid/tap coordinate math and must not be recomputed from UI-safe insets.

Do not let `MainActivity` directly call `CameraXCaptureAdapter` to update runtime camera rotation. Route runtime orientation through `SessionIntent -> SessionEffect -> DeviceCommand`.

## Orientation Model

Create a small shared orientation vocabulary instead of passing Android `Surface.ROTATION_*` constants through core modules.

Recommended core contract:

```kotlin
enum class CameraOutputRotation {
    ROTATION_0,
    ROTATION_90,
    ROTATION_180,
    ROTATION_270
}
```

Recommended app-only physical orientation:

```kotlin
internal enum class CameraPhysicalOrientation {
    PORTRAIT,
    LANDSCAPE_LEFT,
    REVERSE_PORTRAIT,
    LANDSCAPE_RIGHT
}

internal data class CameraOrientationRenderModel(
    val physicalOrientation: CameraPhysicalOrientation,
    val contentRotationDegrees: Float,
    val outputRotation: CameraOutputRotation
)
```

Initial mapping for a portrait-natural Android phone:

| Physical bucket | Sensor degree bucket | Content rotation | Output rotation |
| --- | --- | ---: | --- |
| `PORTRAIT` | `315..359` or `0..44` | `0f` | `ROTATION_0` |
| `LANDSCAPE_LEFT` | `45..134` | `90f` | `ROTATION_90` |
| `REVERSE_PORTRAIT` | `135..224` | `180f` | `ROTATION_180` |
| `LANDSCAPE_RIGHT` | `225..314` | `-90f` | `ROTATION_270` |

If real-device verification shows left/right naming reversed, rename labels or swap display names only after confirming saved-media EXIF/video orientation. The `CameraOutputRotation` value is the behavior that matters.

## Implementation Tasks

### Task 1: Add Orientation Contracts And Pure Tests

**Files:**

- Modify: `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- Create: `app/src/main/java/com/opencamera/app/CameraOrientationModels.kt`
- Create: `app/src/test/java/com/opencamera/app/CameraOrientationModelsTest.kt`

**Work:**

1. Add `CameraOutputRotation` to `core/device`.
2. Add app-only physical orientation buckets and render-model mapping.
3. Add a pure function that converts `OrientationEventListener` degrees into a bucket.
4. Ignore `ORIENTATION_UNKNOWN`.
5. Add a small no-jitter guard: do not emit a new model if the bucket is unchanged.

**Required tests:**

- `0`, `10`, `359` map to `PORTRAIT`.
- `90` maps to one landscape bucket.
- `180` maps to `REVERSE_PORTRAIT`.
- `270` maps to the opposite landscape bucket.
- `ORIENTATION_UNKNOWN` returns `null`.
- Landscape buckets return non-zero content rotation.

**Verification:**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraOrientationModelsTest
```

### Task 2: Route Output Rotation Through Session Kernel

**Files:**

- Modify: `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- Modify: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- Modify: `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

**Work:**

1. Add `outputRotation: CameraOutputRotation = CameraOutputRotation.ROTATION_0` to `SessionState`.
2. Add `SessionIntent.OutputRotationChanged(val rotation: CameraOutputRotation)`.
3. Add `SessionEffect.UpdateOutputRotation(val rotation: CameraOutputRotation)`.
4. In `DefaultCameraSession`, ignore duplicate rotations.
5. On a changed rotation:
   - update `SessionState.outputRotation`;
   - record trace event `orientation.output.changed`;
   - emit `SessionEffect.UpdateOutputRotation(rotation)`;
   - do not trigger preview rebind.

**Required tests:**

- Dispatching a new rotation updates state and emits exactly one update effect.
- Dispatching the same rotation twice emits no duplicate effect.
- Rotation change during `PreviewStatus.ACTIVE` does not emit `BindPreview`.
- Rotation change while recording does not stop the active shot.

**Verification:**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

### Task 3: Add Device Command And CameraX Target Rotation

**Files:**

- Modify: `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- Modify: `app/src/main/java/com/opencamera/app/camera/device/CameraDeviceAdapter.kt`
- Modify: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- Modify: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- Test: `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- Add if useful: `app/src/test/java/com/opencamera/app/camera/CameraOutputRotationMappingTest.kt`

**Work:**

1. Add `DeviceCommand.UpdateOutputRotation(val rotation: CameraOutputRotation)`.
2. Add coordinator handling for `SessionEffect.UpdateOutputRotation`.
3. In `CameraXCaptureAdapter`, store `currentOutputRotation`.
4. Map `CameraOutputRotation` to Android `Surface.ROTATION_*` only inside the app/camera layer.
5. On bind:
   - build `ImageCapture` with the current target rotation;
   - build or update `VideoCapture` with the current target rotation;
   - follow Android's locked-orientation guidance for `Preview`: do not rotate `PreviewView`; update preview target rotation only if a focused real-device check proves preview is sideways.
6. On `DeviceCommand.UpdateOutputRotation`:
   - update already-bound `ImageCapture.targetRotation`;
   - update already-bound `VideoCapture.targetRotation` when supported by the CameraX API version in this project;
   - do not unbind/rebind only for rotation;
   - if a target-rotation update fails, emit a structured runtime issue instead of crashing.

**Important active-recording rule:**

Do not stop an active recording to apply a rotation update. If CameraX cannot safely update target rotation mid-recording, store the new target rotation for the next bind/recording and add a trace/runtime note. This prevents orientation handling from breaking the video golden path.

**Required tests:**

- Coordinator forwards `UpdateOutputRotation` to a fake adapter.
- Rotation mapping returns the expected `Surface.ROTATION_*` values.
- Duplicate rotation commands are cheap no-ops in the adapter.

**Verification:**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraOutputRotationMappingTest
```

### Task 4: Replace Ad-Hoc MainActivity Rotation With A Lifecycle Monitor

**Files:**

- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- Create: `app/src/main/java/com/opencamera/app/CameraOrientationMonitor.kt`
- Test: `app/src/test/java/com/opencamera/app/CameraOrientationModelsTest.kt`

**Work:**

1. Lock the camera Activity visual shell to portrait for this cockpit-stable product direction:

```xml
android:screenOrientation="portrait"
```

Keep the existing `configChanges` entry unless a focused Android test proves it is harmful.

2. Add `CameraOrientationMonitor` backed by `OrientationEventListener`.
3. Enable the monitor in `onStart`; disable it in `onStop`.
4. On orientation model change:
   - store it as `latestOrientationRenderModel`;
   - call `renderOrientation(model)`;
   - dispatch `SessionIntent.OutputRotationChanged(model.outputRotation)`.
5. Remove or replace `applyControlRotationForDisplay()`. Do not rely on `display?.rotation` for the fixed-shell UI content rotation path.

**Why this is necessary:**

With a fixed camera cockpit, the Activity should not depend on system layout recreation for every device posture. Physical posture and output orientation must come from a sensor/display listener.

### Task 5: Rotate Registered Content Without Moving Layout Bounds

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- Optional create: `app/src/main/java/com/opencamera/app/OrientationContentRotator.kt`
- Test: `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

**Work:**

Create a helper that applies `View.rotation` to content-bearing controls while leaving layout params untouched.

Initial static registry:

- `titleText`
- `buttonColorLabEntry`
- `buttonSettingsEntry`
- `buttonFilterEntry`
- `buttonQuickLauncher`
- `buttonDevEntry`
- `buttonQuickGrid`
- `buttonQuickFlash`
- `frameRatioQuickTitle`
- `buttonFrameRatio43`
- `buttonFrameRatio169`
- `buttonFrameRatio11`
- `buttonQuickLivePhoto`
- `buttonQuickTimer`
- `lensFacingButton`
- visible setting/filter panel title/action buttons when panels are open

Dynamic render requirements:

- In `renderZoomCapsules()`, apply the latest content rotation to each generated zoom chip.
- In `renderModeTrack()`, apply the latest content rotation to each visible mode button.
- In dynamically generated watermark/filter cards, apply rotation to generated action buttons and short title labels if the parent panel is visible.

Do not rotate:

- `previewView`
- `previewOverlayView`
- `panelDismissScrim`
- `previewThumbnail`
- `filterPaletteSurface`
- non-directional shutter visual background
- frame/grid/watermark preview hints drawn in preview coordinates

Animation:

- Use a short `150ms-180ms` rotation animation for registered controls.
- Cancel the previous animation before starting a new one.
- Do not change `LayoutParams`, margins, constraints, or measured sizes during orientation updates.

**Required tests:**

- `orientationRenderModel` or its replacement still returns non-zero content rotation for both landscape buckets.
- `CameraCockpitRenderModel` defaults to portrait orientation.
- If a rotator helper is pure-testable, verify it targets only registered view roles.

### Task 6: Preserve Preview Geometry And Interaction Coordinates

**Files:**

- Inspect: `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- Inspect: `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- Inspect: `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt`
- Inspect: `app/src/main/java/com/opencamera/app/gesture/GestureRouter.kt`
- Inspect: `docs/plans/2026-05-23-tap-focus-ui-reticle.md`

**Work:**

1. Keep `previewContentGeometry()` as the single source for frame/grid coordinates.
2. Do not feed rotated-control bounds into preview geometry.
3. Tap/focus coordinates must remain based on raw `PreviewView` local coordinates.
4. If tap-focus reticle work lands in parallel, reticle drawing must use preview coordinates and not inherit cockpit content rotation.

**Required tests:**

Run existing geometry tests after orientation work:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest
```

### Task 7: Diagnostics And Dev Verification Hooks

**Files:**

- Modify: `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- Modify if needed: `app/src/main/java/com/opencamera/app/DevLogRenderModel.kt`
- Test: `core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt`

**Work:**

Add output orientation to diagnostics/debug dump:

- current `CameraOutputRotation`;
- last orientation trace event;
- whether target rotation update was applied or deferred.

Do not add a visible user-facing status label for orientation. Keep this in dev/debug surfaces only.

**Verification:**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
```

## Parallelization Map

The work can be split, but merge order matters.

1. **Agent A: Orientation model**
   - Owns `CameraOutputRotation`, `CameraOrientationModels.kt`, and pure tests.
   - Can start first.
2. **Agent B: Session/device runtime path**
   - Owns `SessionIntent`, `SessionEffect`, `DeviceCommand`, coordinator forwarding, and adapter target-rotation mapping.
   - Depends on Agent A's `CameraOutputRotation` contract.
3. **Agent C: UI content rotation**
   - Owns `CameraOrientationMonitor`, `MainActivity.renderOrientation()`, static/dynamic control registration, and manifest shell policy.
   - Depends on Agent A's app render model.
4. **Agent D: Diagnostics and integration verification**
   - Owns diagnostics text, full focused test command set, `assembleDebug`, and Stage 7 verification.
   - Runs after A/B/C merge.

Avoid multiple agents editing the same `MainActivity.kt` region at the same time. Agent C should own `MainActivity` orientation changes; Agent B should touch coordinator/adapter.

## Acceptance Criteria

- No `layout-land/activity_main.xml` is added.
- Camera Activity uses a stable cockpit topology; component constraints and sizes are not changed on orientation updates.
- Physical landscape changes rotate registered text/glyph controls without rotating the preview surface.
- Output rotation changes flow through `SessionIntent -> SessionEffect -> DeviceCommand`.
- CameraX still capture and video capture receive the latest target rotation before new captures/recordings.
- Rotation updates do not trigger preview rebind by default.
- Rotation updates do not stop an active recording.
- Preview frame, grid, and saved-frame geometry tests still pass.
- `verify_stage_7_observability.sh` passes after focused tests.

## Non-Goals

- Do not implement a separate landscape layout.
- Do not redesign the cockpit, mode track, rail, or panels.
- Do not tune offsets by screenshots.
- Do not make `MainActivity` a hidden runtime camera owner.
- Do not solve tablet/foldable multi-window orientation as part of the first closed loop.
- Do not claim final visual polish without a real-device/multimodal follow-up.

## Focused Verification

Run these in order:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraOrientationModelsTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
```

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraOutputRotationMappingTest
```

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
```

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Then run the stage gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Real-Device Follow-Up For Multimodal Owner

After the non-multimodal implementation passes local verification, hand a new APK to a multimodal/real-device owner for:

- portrait photo mode, no panel;
- landscape left photo mode, no panel;
- landscape right photo mode, no panel;
- landscape with quick panel open;
- landscape with style/color/settings panel open;
- photo capture in portrait, landscape left, and landscape right;
- video start/stop in portrait and landscape.

The multimodal owner should verify readability, no overlap, no preview rotation glitch, and correct saved media orientation. Non-multimodal agents should not make screenshot-aesthetic judgments.
