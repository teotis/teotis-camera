# Resource Scheduler And Performance Governance 2.0

> **For agentic workers:** This is a non-multimodal implementation plan. Build budgets, admission control, degradation rules, diagnostics, and tests.

## Goal

Make upper-layer camera processing safe on ordinary Android devices by enforcing explicit resource budgets before enabling frame buffers, multi-frame capture, Live Photo, and app-level algorithms.

## Why This Matters

Moving work from native/vendor layers into app space raises risk:

- preview jank
- shutter lag
- out-of-memory crashes
- slow post-processing
- thermal throttling
- battery drain
- recovery loops after heavy graph binding

2.0 should not "try everything and hope." It should admit, degrade, or reject work based on budgets.

## Resource Model

Suggested contracts:

```kotlin
data class CameraResourceBudget(
    val memoryBytes: Long,
    val maxAnalysisFps: Int,
    val maxProtectedFrames: Int,
    val maxConcurrentAlgorithmJobs: Int,
    val maxPostProcessMillis: Long,
    val thermalState: CameraThermalState,
    val performanceClass: CameraPerformanceClass
)

enum class CameraThermalState {
    NORMAL,
    WARM,
    HOT,
    CRITICAL
}

enum class CameraPerformanceClass {
    LOW,
    MID,
    HIGH,
    UNKNOWN
}

data class CameraWorkAdmission(
    val admitted: Boolean,
    val degraded: Boolean,
    val reason: String,
    val effectiveBudget: CameraResourceBudget,
    val featureAdjustments: Map<String, String>
)
```

## Admission Rules

### Preview Frame Analysis

- Disable or lower FPS when thermal is hot.
- Lower resolution before lowering capture quality.
- Never block preview for analysis.
- Disable analysis during recovery until first frame is active again.

### Live Photo

- Reduce frame count before disabling Live.
- Prefer still-only fallback over shot failure.
- Disable motion source while recording unless explicitly supported.

### Multi-Frame / Night

- Lower frame count when memory or thermal budget is constrained.
- Prefer shorter inter-frame delay if preview/capture queue is at risk.
- Fallback to single-frame with notes when budget is insufficient.

### Portrait

- If segmentation/depth is unavailable or too expensive, fallback to focus/metadata-based rendering.
- Keep user-visible support state degraded rather than hiding the control.

### Filters And Watermarks

- Capture render can run after save if it does not block camera recovery.
- Preview approximation should be cheap and optional.
- Video watermark burn-in is not admitted by default; sidecar-only is acceptable.

## Queueing Model

Use one scheduler for app-level algorithm work. It should be separate from Session state but report low-frequency status to diagnostics.

Rules:

- One primary capture post-process at a time by default.
- Preview analysis jobs are latest-only and cancellable.
- A new shutter can be blocked or queued only through Session Kernel policy.
- Background cleanup must not block UI.
- If an algorithm exceeds budget, record `algorithm:budget-exceeded:<type>` and fallback where possible.

Suggested job classes:

```text
RealtimePreviewJob:
  latest-only, cancellable, low priority

CaptureCriticalJob:
  primary saved output, bounded, failure affects shot

CaptureOptionalJob:
  sidecar, secondary thumbnail, optional live motion, failure degrades

CleanupJob:
  temp delete, non-user-visible, must be reliable
```

## Diagnostics

Add stable notes:

- `resource:class=mid`
- `resource:thermal=warm`
- `resource:analysis-fps=8`
- `resource:live=degraded:max-frames`
- `resource:night=degraded:frame-count-3`
- `resource:algorithm-queue=busy`
- `resource:postprocess=over-budget:1530ms`

These belong in diagnostics/dev log and `ShotResult.pipelineNotes` where tied to a shot.

## Likely Files

Core:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`

App:

- `app/src/main/java/com/opencamera/app/camera/ThermalRuntimeIssueMonitor.kt`
- `app/src/main/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitor.kt`
- possible new scheduler files under `app/src/main/java/com/opencamera/app/camera/perf/`

Tests:

- `core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt`
- new pure tests for admission policy
- app tests for thermal issue mapping

## Implementation Tasks

- [ ] Add resource budget and admission contracts.
- [ ] Add a pure policy for Photo, Live, Night, Portrait, Video, and Preview Analysis admission.
- [ ] Add budget diagnostics to `SessionDebugDump` or related diagnostics without high-frequency updates.
- [ ] Add scheduler contract for algorithm job classes.
- [ ] Add timeout/budget result handling for algorithm processors.
- [ ] Connect existing thermal monitor to resource budget state in a low-frequency way.

## Acceptance Tests

- Thermal hot degrades Live frame buffer and Night frame count.
- Thermal critical blocks new heavy work and reports a runtime issue.
- Low performance class uses lower preview analysis FPS.
- Algorithm job timeout produces a recoverable skip for optional processors.
- Capture-critical processor failure fails the shot; optional processor failure degrades with notes.
- Resource diagnostics can be rendered in Dev Log without raw frame data.

Suggested commands:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not implement per-device final threshold tuning without real-device data.
- Do not make resource governance depend on UI visibility.
- Do not silently disable features without diagnostics.
- Do not introduce unbounded background processing.

