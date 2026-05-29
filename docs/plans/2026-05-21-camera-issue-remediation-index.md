# 2026-05-21 Camera Issue Remediation Index

> For agentic workers: this is a handoff index, not an implementation patch. Pick one linked plan, keep changes scoped to that plan, and run the listed focused verification before any broader Stage 7 verification.

## Context

User report, screenshots, and `<HOME>/Downloads/opencamera-debug-1779350306073.log` show a cluster of product regressions in the current Android/Kotlin OpenCamera app. The project is currently in Stage 7: stability governance and automation hardening. Do not start a new stage. Keep fixes within the existing architecture: UI renders state and dispatches session/settings intents; Session Kernel owns runtime state; Device Adapter owns CameraX execution; Media Pipeline owns saved-media results.

The strongest log evidence:

- `ZoomRatioToggled` immediately followed by `zoom.updated -> 2.0x`, matching the report that tapping a specific zoom option advances to the next option instead of selecting the tapped ratio.
- `preview.snapshot.updated` and `PreviewSnapshot` are used for the visible thumbnail, matching the report that mode/lens changes replace the last-photo thumbnail with a preview frame.
- `capture.photo -> capture.saving -> capture.saved` contains no fine-grained adapter/media/postprocess timing, so the reported long capture latency cannot be diagnosed from the exported dev log.
- Filter and watermark panels still contain hard-coded English dynamic copy such as `Use This Look`, `Adjust Selected`, `Current default`, `Ready`, and `Open Style Page`.
- Screenshot evidence shows severe text wrapping, top status overlap with system cutout/status bar, tall narrow mode buttons, and bottom cockpit imbalance.

## Proposed Work Packages

1. [Interaction Routing and Mode Track Hit Targets](./2026-05-21-interaction-routing-and-hit-targets.md)
   - Fix specific zoom selection.
   - Prevent mode-track mis-hit behavior such as tapping Portrait and switching to Humanistic.
   - Make video recording states visually explicit enough that the user gets feedback immediately.
   - Add gallery-open behavior from thumbnail tap if saved media exists.

2. [Media Result, Thumbnail, Capture Latency, and Dev Log Observability](./2026-05-21-media-result-thumbnail-latency-devlog.md)
   - Make thumbnail semantics prefer last saved photo/video media over preview snapshots.
   - Stop preview startup/mode/lens changes from replacing the saved-media thumbnail.
   - Add capture/recording key-path timing events to dev log.
   - Diagnose and reduce local capture delay where safe.

3. [Panel Localization and Visual System Consolidation](./2026-05-21-panel-localization-and-visual-system.md)
   - Localize panel dynamic copy to Chinese by default through `AppTextResolver`.
   - Add outside-tap dismiss behavior for secondary panels.
   - Make the filter palette visible and understandable.
   - Repair top-panel safe-area adaptation, Lens Lab/Filter Lab panel hierarchy, and bottom cockpit layout.

## Dependency Order

Recommended sequence:

1. Interaction routing first. It is narrow and fixes direct wrong behavior.
2. Media/result observability second. It touches session/device/media semantics and helps verify latency.
3. Visual/localization consolidation third. It is broader and should be easier once behavior stops moving underneath it.

These packages can be implemented by different agents if write scopes stay separate, but avoid editing `MainActivity.kt` in parallel without coordination. If parallel execution is required, assign `MainActivity.kt` ownership to one integrator and let other agents prepare render-model/session/device patches.

## Global Verification

Use `rtk` for every command.

Focused commands likely needed across packages:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ThumbnailRenderCommandTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Stage verification after a meaningful closed loop:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle shows transient Kotlin/build-directory errors under `~/.codex-build/OpenCamera`, rerun the smallest failed command serially before declaring a product regression.
