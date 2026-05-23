# Quick Quality And Resolution Verification Gate

> For text-only agents: use this after either the photo or video quick quality package lands. Run every shell command through `rtk`.

## Goal

Prevent partial implementations where the quick panel changes labels but not session/device behavior, or changes session behavior without truthful UI.

## Required Invariants

- UI quick rows derive values from `SessionState`, not hard-coded strings.
- Photo quality and photo resolution use existing session intents.
- Video quality cycles combined `VideoSpec(resolution, frameRate)` options.
- Video quick quality does not expose unsupported combinations.
- No UI code imports CameraX APIs.
- No app renderer, binder, or coordinator becomes a hidden session owner.

## Source Checks

Run:

```bash
rtk rg -n "strings\\.buttonStillFast" app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt
```

Expected:

- No hard-coded quick quality value remains.
- It is acceptable for tests or string bundles to mention `buttonStillFast`.

Run:

```bash
rtk rg -n "androidx\\.camera|camera2|CameraControl|QualitySelector" app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt
```

Expected:

- No matches. UI and render code must not call CameraX.

Run:

```bash
rtk rg -n "copy\\(resolution = nextResolution\\)" feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt
```

Expected:

- No matches. Video quality cycling must update a full combined spec.

## Focused Tests

Photo package:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Video package:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.VideoSpecSelectionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRecordingQualityTest
```

Full stage gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle shows transient `.codex-build/OpenCamera/.../classes/kotlin/main/com` errors, rerun the smallest failing focused command serially before declaring a product regression.

## Manual Smoke Checklist

Use a local or real-device build after automated tests pass:

1. Open Photo mode.
2. Open `快捷`.
3. Confirm `画质` shows the active quality, not always `快速`.
4. Tap `画质`; confirm active quality changes and preview rebind/recovery is traceable.
5. Tap `像素`; confirm value changes through available still sizes or presets.
6. Start countdown or capture; confirm quality/resolution changes are blocked.
7. Switch to Video mode.
8. Open `快捷`.
9. Confirm quality shows a combined label such as `4K30` or `1080p60`.
10. Tap quality before recording; confirm both resolution and fps change together.
11. Start recording; confirm quality changes are blocked.
12. Stop recording; confirm saved video metadata still records requested and resolved video resolution/fps.

## Non-Scope Checks

Do not fail this feature for:

- Exact Apple/vivo visual parity.
- Lack of a second fps button in video quick panel. A second fps button is explicitly out of scope.
- Not persisting quick video quality to default settings. This pass follows the existing runtime quick-control pattern.
- True 8K / 100fps / 120fps success on all devices. Those remain hardware-dependent and must be capability-gated.

## Final Acceptance

- All focused tests pass.
- Stage 7 verification passes.
- Source checks show no hard-coded quick still label and no CameraX import in UI.
- Manual smoke confirms video quick quality is a combined resolution/fps option.
