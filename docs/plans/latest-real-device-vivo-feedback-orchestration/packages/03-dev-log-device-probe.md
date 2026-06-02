# Package 03 - Dev Log Device Probe

## Goal

Make Dev logs useful for real-device debugging:

- Merge the `耗时` tab into `链路`; only keep `链路` as the timing/link-flow surface.
- Add trigger time, monotonic timing, source/location, flow id, stage, and status where possible.
- When app startup or machine-quality probing detects device capabilities, print camera count, lens/focal ranges, output sizes/pixels, supported/degraded/unsupported capability facts, and any probe failures.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- `app/src/main/java/com/opencamera/app/DevLogRenderModel.kt`
- `app/src/main/java/com/opencamera/app/DevLogExporter.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` Dev/diagnostics sections only
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` capability probe/log sections only
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt` capability/diagnostic forwarding only
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- focused Dev log/capability tests
- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/03-dev-log-device-probe.md`
- package-local scratch path

## Forbidden Paths

- Capturing raw frame/image/media payloads in logs.
- Storing secrets, tokens, private paths, or raw user media content.
- Changing camera runtime behavior beyond adding diagnostics/probe forwarding.
- Reintroducing separate `耗时` UI after merging it into `链路`.

## Tasks

1. Inspect current Dev tabs and `real-device-performance-link-logs-orchestration` status to avoid duplicating already-landed infrastructure.
2. Remove or fold the `耗时` tab into `链路` while preserving timing records.
3. Enrich log rows and exports with human-usable trigger time and source/location fields.
4. Add startup/device capability probe summaries that include camera count, lens nodes/focal ranges, output pixels, and degraded reasons.
5. Add tests for render model/export formatting and device probe summary behavior.

## Acceptance Criteria

- Dev UI shows no separate `耗时` tab; timing details live under `链路`.
- Log entries are no longer bare `[Zoom] 663. zoom.updated -> 0.8x`; they include time/source enough to debug real-device behavior.
- Startup/device probe information appears in Dev/log export when capability probing runs.
- Export remains compact and privacy-safe.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Evidence Required

- Example rendered/exported log snippet with sanitized fields.
- Device probe summary schema.
- Test/build results and commit hash.

## Unlock Condition

Mark `completed` only after Dev log tests/build pass and privacy limitations are documented.
