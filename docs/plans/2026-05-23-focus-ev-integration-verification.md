# Thumbnail Gallery And Tap Focus/EV Integration Verification Plan

> **For agentic workers:** Use this after the gallery, contract, CameraX, and UI packages land. Use `rtk` for every shell command. This plan is text-only; final visual quality judgment remains a human/device acceptance step.

## Goal

Verify that thumbnail gallery open and tap focus/auto EV land together without breaking Stage 7 recovery, media, or UI routing.

## Integration Order

1. Land [Thumbnail Gallery Open Fix](./2026-05-23-thumbnail-gallery-open-fix.md).
2. Land [Tap Focus Session/Device Contract](./2026-05-23-tap-focus-session-device-contract.md).
3. Land [CameraX Focus And AE Execution](./2026-05-23-tap-focus-camerax-execution.md).
4. Land [Tap Focus UI Reticle And Routing](./2026-05-23-tap-focus-ui-reticle.md).
5. Run this verification plan.

## Static Review Checklist

- `MainActivity` thumbnail click no longer uses `latestCapturePath/latestVideoPath` as the primary open target.
- Gallery click opens only `ThumbnailSource.SavedMedia`.
- Gallery click ignores `pendingCaptureFeedback` and `PreviewSnapshot`.
- `MainActivity` does not call CameraX focus APIs directly.
- Tap focus flows through:

```text
GestureAction.FocusAt
-> SessionIntent.PreviewTapToFocus
-> SessionEffect.ApplyPreviewMetering
-> DeviceCommand.ApplyPreviewMetering
-> CameraXCaptureAdapter
-> DeviceEvent.PreviewMeteringCompleted
-> SessionIntent.PreviewMeteringCompleted
-> SessionPresentationState.previewMeteringFeedback
-> PreviewOverlayView reticle
```

- Tap focus state is not written to persisted settings.
- CameraX metering failures do not masquerade as preview bind failures.

## Focused Verification Commands

Run app UI/helper tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.GalleryOpenTargetTest --tests com.opencamera.app.ThumbnailRenderCommandTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.gesture.GesturePolicyTest
```

Run session and coordinator tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.PreviewMeteringActionPlannerTest
```

Build:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Stage gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle shows transient build-directory errors under `~/.codex-build/OpenCamera`, rerun the smallest failed command serially before declaring a product regression.

## Non-Multimodal Real-Device Smoke

These checks can be performed by a text-only agent using logs/toasts and simple pass/fail observations. They do not require screenshot analysis.

### Thumbnail Open

1. Install the debug APK.
2. Launch app and grant camera permission.
3. Capture one photo.
4. Wait until the capture output reports saved media or pipeline notes.
5. Tap the thumbnail.
6. Expected: Android media viewer opens; no `无法打开媒体文件` toast.
7. Return to app.
8. Capture a second photo with a visible postprocess feature enabled, such as a non-default frame ratio or watermark.
9. Tap the thumbnail while saving if a transient feedback image appears.
10. Expected: app does not open the transient preview file.
11. Wait until saved-media thumbnail appears and tap again.
12. Expected: Android media viewer opens the saved media.

### Tap Focus And Auto EV

1. Launch app and wait for preview active.
2. Tap the center of the preview.
3. Expected dev trace contains:

```text
preview.metering.requested
```

4. Expected follow-up trace contains one of:

```text
preview.metering.succeeded
preview.metering.degraded
preview.metering.unsupported
preview.metering.failed
```

5. Tap a different preview area.
6. Expected: a new request id appears and stale completion from the older request does not overwrite current feedback.
7. Open settings or quick panel and tap behind it.
8. Expected: `GestureGuard` blocks preview focus dispatch.
9. Start video recording if supported and tap preview.
10. Expected: app does not crash or stop recording; result may be succeeded, degraded, unsupported, or failed depending on device support.

## Acceptance Criteria

- All focused tests pass.
- `:app:assembleDebug` passes.
- `verify_stage_7_observability.sh` passes.
- Thumbnail click opens latest saved photo/video through a real content URI or FileProvider URI.
- Tap focus/AE produces session trace and device result without direct UI-to-CameraX coupling.
- Unsupported focus/AE is represented explicitly, not hidden as a generic preview failure.

## Residual Risk

- True EV quality is device-dependent. The non-multimodal pass can verify command routing and CameraX result status, but it cannot judge whether the image exposure looks pleasing in every scene.
- Some gallery apps may not be installed on test devices. In that case, the intent launch can still fail, but the failure is different from the current bug. The implementation should keep the toast for missing external viewers.

