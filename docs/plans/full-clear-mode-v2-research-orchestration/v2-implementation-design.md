# Full Clear V2 - Implementation Design

## Architecture Overview

V2 fits the existing four-layer architecture without creating new layers or hidden session kernels:

```
┌─────────────────────────────────────────────────────────────────┐
│  Mode Plugin (core/mode + feature/mode-full-clear)               │
│  Owns: route selection policy, user guidance states, intent →   │
│        CaptureStrategy mapping                                  │
│  Emits: ModeSignal.SubmitCapture(strategy)                      │
│  NEVER calls CameraX/Camera2/HAL directly                       │
├─────────────────────────────────────────────────────────────────┤
│  Session Kernel (core/session)                                   │
│  Owns: mode lifecycle, shot dispatch, thermal/resource gating,  │
│        SessionState → UI rendering                              │
│  Routes: SessionIntent → ModeController.handle(ModeIntent)      │
│         ModeSignal → SessionEffect                              │
│  No V2-specific session kernel changes needed                   │
├─────────────────────────────────────────────────────────────────┤
│  Device Adapter (core/device)                                    │
│  Owns: capability detection, ShotPlan → DeviceShotRequest,      │
│        per-frame focus control, lens switching, metering        │
│  New: FullClear capability queries, per-frame focus plan        │
│  Testable: all route decisions are pure functions on            │
│           DeviceCapabilities                                    │
├─────────────────────────────────────────────────────────────────┤
│  Media Pipeline (core/media)                                     │
│  Owns: ShotGraph with capture/algo/output nodes, fusion         │
│        algorithm, fallback, diagnostics                         │
│  New: FullClearFusionReport, alignment + fusion confidence      │
│  Extends: existing MULTI_FRAME_CAPTURE ShotKind                 │
└─────────────────────────────────────────────────────────────────┘
```

### Layer Boundary Rules (from AGENTS.md)

| Rule | V2 Compliance |
|---|---|
| UI renders state, dispatches intents only | V2 guidance states flow through `ModeState.headline/detail`; UI only calls `SessionIntent.ShutterPressed` |
| Session Kernel owns session state, preview, recovery | No V2-specific session changes; existing `DefaultCameraSession` handles V2 shots like any other mode |
| Mode plugins describe requested behavior, never call CameraX | `FullClearModeController` emits `CaptureStrategy.MultiFrame` or `CaptureStrategy.SingleFrame`; zero CameraX references |
| Device adapters translate abstract requests into platform details | `DeviceShotRequestTranslator` extended for per-frame focus; `DeviceCapabilities` extended for V2 queries |
| Runtime state and persisted settings separate | Route selection uses `DeviceCapabilities` (runtime) not settings; V2-specific settings go through existing `SessionSettingsSnapshot` |
| No second hidden session kernel | FullClearRoute is a stateless pure function; no coordinator, manager, or bridge owns session state |
| Hardware capability has explicit supported/degraded/unsupported | Every V2 capability tier maps to the existing three-tier model from the product definition |

---

## Data Flow: Shutter Press to Saved Result

```
User taps shutter
  │
  ▼
UI → SessionIntent.ShutterPressed
  │
  ▼
DefaultCameraSession → ModeController.handle(ModeIntent.ShutterPressed)
  │
  ▼
FullClearModeController:
  1. FullClearSceneAssessment.assess(deviceCapabilities, sceneHints) → assessment
  2. FullClearRouteSelector.select(assessment, deviceCapabilities) → route
  3. FullClearCapturePlan.build(route, assessment) → plan
  4. Emit ModeSignal.SubmitCapture(strategy) with metadata
  │
  ▼
DefaultCameraSession → ShotExecutor.plan(strategy) → ShotPlan
  │
  ▼
SessionEffect.ExecuteShot(plan)
  │
  ▼
Device Adapter:
  DeviceShotRequestTranslator.translate(plan) → DeviceShotRequest
  For bracket path: per-frame focus distances in DeviceShotRequest
  │
  ▼
CameraX/Camera2 execution (app layer, outside V2 design scope)
  │
  ▼
Media Pipeline:
  ShotGraph: TEMPORARY_FRAME nodes + PRIMARY_STILL + FULL_CLEAR_FUSION algo
  AlgorithmProcessor.process(frames) → fused or fallback
  FullClearFusionReport written to pipelineNotes
  │
  ▼
ShotResult → Session → ModeController.onSessionEvent(ShotCompleted)
  │
  ▼
UI updates: result state from pipelineNotes
```

---

## Core Contracts

### 1. FullClearSceneAssessment

Assesses the current scene for Full Clear suitability. Runs as a pure function on available data — no CameraX dependency.

```kotlin
// Placement: core/device (capability queries are device-layer concerns)

data class SubjectDistanceEstimate(
    val minDiopters: Float?,       // null = unknown
    val maxDiopters: Float?,       // null = unknown
    val confidence: Float,         // 0.0–1.0, from AF/metering hint
    val source: String             // "af", "manual-point", "estimated"
)

data class SceneStabilitySignal(
    val motionMagnitude: Float?,   // gyro magnitude, null = unavailable
    val isStable: Boolean,
    val source: String             // "gyro", "none"
)

data class FullClearSceneAssessment(
    val nearSubject: SubjectDistanceEstimate,
    val farBackground: SubjectDistanceEstimate,
    val stability: SceneStabilitySignal,
    val recommendedRoute: FullClearRoute,
    val routeConfidence: Float,    // 0.0–1.0
    val diagnostics: List<String>
) {
    companion object {
        val UNKNOWN = FullClearSceneAssessment(
            nearSubject = SubjectDistanceEstimate(null, null, 0f, "unknown"),
            farBackground = SubjectDistanceEstimate(null, null, 0f, "unknown"),
            stability = SceneStabilitySignal(null, false, "none"),
            recommendedRoute = FullClearRoute.BEST_FRAME_ONLY,
            routeConfidence = 0f,
            diagnostics = listOf("fc:assessment=unknown")
        )
    }
}
```

**Assessment logic** (pure function, no I/O):

```kotlin
fun assessFullClearScene(
    capabilities: FullClearDeviceCapabilities,
    nearHint: SubjectDistanceEstimate = SubjectDistanceEstimate(null, null, 0f, "unknown"),
    farHint: SubjectDistanceEstimate = SubjectDistanceEstimate(null, null, 0f, "unknown"),
    stability: SceneStabilitySignal = SceneStabilitySignal(null, false, "none")
): FullClearSceneAssessment {
    // 1. Check if any route is possible
    val routeResult = selectFullClearRoute(capabilities, nearHint, farHint, stability)

    // 2. Build diagnostics
    val diags = buildList {
        add("fc:assessment:route=${routeResult.route.name.lowercase()}")
        add("fc:assessment:confidence=${routeResult.confidence}")
        nearHint.source.let { add("fc:near-source=$it") }
        stability.source.let { add("fc:stability-source=$it") }
    }

    return FullClearSceneAssessment(
        nearSubject = nearHint,
        farBackground = farHint,
        stability = stability,
        recommendedRoute = routeResult.route,
        routeConfidence = routeResult.confidence,
        diagnostics = diags
    )
}
```

### 2. FullClearRoute

Enumeration of available optical/computational paths. Route selection is a pure function on `FullClearDeviceCapabilities`.

```kotlin
// Placement: core/device

enum class FullClearRoute {
    /** Ultra-wide or equivalent deep-DOF lens covers near+far in one frame. */
    DEEP_DOF_LENS,

    /** Main camera focus bracket: near, mid, far → fusion. */
    FOCUS_BRACKET,

    /** Two-lens pair: tele for close subject, wide for background → fusion. */
    LENS_PAIR_BRACKET,

    /** Single best frame with AF at hyperfocal or user-selected point. */
    BEST_FRAME_ONLY,

    /** No viable Full Clear path on this device. */
    UNSUPPORTED
}

data class FullClearDeviceCapabilities(
    // Deep-DOF path
    val hasUltraWideLens: Boolean,
    val ultraWideMinFocusDiopters: Float?,  // null = unknown
    val ultraWideFocalLength35mm: Float?,   // for DOF estimation

    // Focus bracket path
    val focusDistanceWritable: Boolean,
    val focusDistancePerFrameLatencyMs: Long?,  // null = unknown
    val maxBracketFrames: Int,

    // Lens-pair path
    val hasTelephotoLens: Boolean,
    val lensSwitchLatencyMs: Long?,          // null = unknown
    val lensPairMaxFramingShift: Float?,     // null = unknown

    // Common
    val supportsManualControls: Boolean,
    val gyroAvailable: Boolean
) {
    companion object {
        fun from(deviceCapabilities: DeviceCapabilities): FullClearDeviceCapabilities {
            val manual = deviceCapabilities.resolvedManualControlCapabilities
            val zoom = deviceCapabilities.zoomRatioCapability
            return FullClearDeviceCapabilities(
                hasUltraWideLens = zoom.lensNodeMap.values.any {
                    it.available && it.thresholdRatio < 1f
                },
                ultraWideMinFocusDiopters = null,  // requires Camera2 query at runtime
                ultraWideFocalLength35mm = null,   // requires Camera2 query at runtime
                focusDistanceWritable = manual.focusDistance == ManualControlSupport.APPLY,
                focusDistancePerFrameLatencyMs = null,  // requires device-specific profiling
                maxBracketFrames = when {
                    manual.focusDistance != ManualControlSupport.APPLY -> 0
                    deviceCapabilities.supportsNightMultiFrame -> 6  // known multi-frame capable
                    else -> 3  // conservative default
                },
                hasTelephotoLens = zoom.lensNodeMap.values.any {
                    it.available && it.thresholdRatio > 1f
                },
                lensSwitchLatencyMs = null,     // requires device-specific profiling
                lensPairMaxFramingShift = null, // requires device-specific profiling
                supportsManualControls = deviceCapabilities.supportsManualControls,
                gyroAvailable = true  // Android always has gyro, availability TBD per device
            )
        }
    }
}
```

**Route selection logic** (pure function):

```kotlin
data class RouteSelectionResult(
    val route: FullClearRoute,
    val confidence: Float,
    val degradationReasons: List<String>
)

fun selectFullClearRoute(
    capabilities: FullClearDeviceCapabilities,
    nearHint: SubjectDistanceEstimate,
    farHint: SubjectDistanceEstimate,
    stability: SceneStabilitySignal
): RouteSelectionResult {
    // 1. Unsupported check
    if (!capabilities.supportsManualControls && !capabilities.hasUltraWideLens) {
        return RouteSelectionResult(FullClearRoute.UNSUPPORTED, 0f, listOf("no-manual-controls-no-uw"))
    }

    // 2. Deep-DOF path: always preferred when ultra-wide exists
    if (capabilities.hasUltraWideLens) {
        val uwMinDiopters = capabilities.ultraWideMinFocusDiopters ?: 0f
        val nearDiopters = nearHint.minDiopters
        if (nearDiopters == null || nearDiopters <= uwMinDiopters) {
            val confidence = if (nearDiopters != null) 0.9f else 0.7f
            return RouteSelectionResult(FullClearRoute.DEEP_DOF_LENS, confidence, emptyList())
        }
        if (!capabilities.focusDistanceWritable) {
            return RouteSelectionResult(
                FullClearRoute.DEEP_DOF_LENS, 0.5f,
                listOf("uw-near-limit:subject-too-close")
            )
        }
    }

    // 3. Focus bracket path: requires writable focus distance
    if (capabilities.focusDistanceWritable && capabilities.maxBracketFrames >= 2) {
        if (!stability.isStable) {
            return RouteSelectionResult(
                FullClearRoute.FOCUS_BRACKET, 0.3f,
                listOf("bracket-unstable")
            )
        }
        val confidence = when {
            capabilities.maxBracketFrames >= 5 -> 0.85f
            capabilities.maxBracketFrames >= 3 -> 0.7f
            else -> 0.5f
        }
        return RouteSelectionResult(FullClearRoute.FOCUS_BRACKET, confidence, emptyList())
    }

    // 4. Lens-pair bracket: experimental
    if (capabilities.hasTelephotoLens && capabilities.hasUltraWideLens) {
        return RouteSelectionResult(
            FullClearRoute.LENS_PAIR_BRACKET, 0.3f,
            listOf("lens-pair-experimental")
        )
    }

    // 5. Best frame fallback
    return RouteSelectionResult(
        FullClearRoute.BEST_FRAME_ONLY, 0.2f,
        listOf("no-bracket-path-available")
    )
}
```

### 3. FullClearCapturePlan

Maps the selected route to concrete capture parameters.

```kotlin
// Placement: core/mode (mode-layer capture planning)

data class FocusBracketStep(
    val frameIndex: Int,
    val focusDistanceDiopters: Float,
    val label: String  // "near", "mid", "far"
)

data class FullClearCapturePlan(
    val route: FullClearRoute,
    val lensNode: LensNode,
    val bracketSteps: List<FocusBracketStep>,
    val maxTotalLatencyMs: Long,
    val fallbackPolicy: FullClearFallbackPolicy,
    val diagnostics: List<String>
)

enum class FullClearFallbackPolicy {
    SAVE_BEST_AVAILABLE_FRAME,
    RETRY_SINGLE_FRAME,
    REPORT_ERROR
}

fun buildFullClearCapturePlan(
    route: FullClearRoute,
    assessment: FullClearSceneAssessment,
    capabilities: FullClearDeviceCapabilities
): FullClearCapturePlan {
    return when (route) {
        FullClearRoute.DEEP_DOF_LENS -> FullClearCapturePlan(
            route = route,
            lensNode = LensNode.WIDE,
            bracketSteps = emptyList(),
            maxTotalLatencyMs = 1_000L,
            fallbackPolicy = FullClearFallbackPolicy.RETRY_SINGLE_FRAME,
            diagnostics = listOf("fc:plan:deep-dof:single-frame")
        )

        FullClearRoute.FOCUS_BRACKET -> {
            val nearDiopters = assessment.nearSubject.minDiopters ?: 3f
            val farDiopters = 0f
            val frameCount = capabilities.maxBracketFrames.coerceIn(3, 6)
            val steps = buildFocusBracketSteps(nearDiopters, farDiopters, frameCount)
            FullClearCapturePlan(
                route = route,
                lensNode = LensNode.WIDE,
                bracketSteps = steps,
                maxTotalLatencyMs = frameCount *
                    (capabilities.focusDistancePerFrameLatencyMs ?: 300L) + 2_000L,
                fallbackPolicy = FullClearFallbackPolicy.SAVE_BEST_AVAILABLE_FRAME,
                diagnostics = listOf(
                    "fc:plan:focus-bracket:frames=$frameCount",
                    "fc:plan:near-diopters=$nearDiopters",
                    "fc:plan:far-diopters=$farDiopters"
                )
            )
        }

        FullClearRoute.LENS_PAIR_BRACKET -> FullClearCapturePlan(
            route = route,
            lensNode = LensNode.TELEPHOTO,
            bracketSteps = listOf(
                FocusBracketStep(0, 5f, "tele-close"),
                FocusBracketStep(1, 0f, "wide-far")
            ),
            maxTotalLatencyMs = (capabilities.lensSwitchLatencyMs ?: 1_500L) + 2_000L,
            fallbackPolicy = FullClearFallbackPolicy.SAVE_BEST_AVAILABLE_FRAME,
            diagnostics = listOf("fc:plan:lens-pair:experimental")
        )

        FullClearRoute.BEST_FRAME_ONLY -> FullClearCapturePlan(
            route = route,
            lensNode = LensNode.WIDE,
            bracketSteps = emptyList(),
            maxTotalLatencyMs = 1_000L,
            fallbackPolicy = FullClearFallbackPolicy.REPORT_ERROR,
            diagnostics = listOf("fc:plan:best-frame:fallback")
        )

        FullClearRoute.UNSUPPORTED -> FullClearCapturePlan(
            route = route,
            lensNode = LensNode.WIDE,
            bracketSteps = emptyList(),
            maxTotalLatencyMs = 0L,
            fallbackPolicy = FullClearFallbackPolicy.REPORT_ERROR,
            diagnostics = listOf("fc:plan:unsupported")
        )
    }
}

private fun buildFocusBracketSteps(
    nearDiopters: Float,
    farDiopters: Float,
    frameCount: Int
): List<FocusBracketStep> {
    val steps = mutableListOf<FocusBracketStep>()
    steps.add(FocusBracketStep(0, nearDiopters, "near"))
    if (frameCount > 2) {
        val midCount = frameCount - 2
        for (i in 1..midCount) {
            val t = i.toFloat() / (midCount + 1)
            val diopters = nearDiopters + (farDiopters - nearDiopters) * t
            steps.add(FocusBracketStep(i, diopters, "mid-$i"))
        }
    }
    steps.add(FocusBracketStep(frameCount - 1, farDiopters, "far"))
    return steps
}
```

---

## Mode Plugin: FullClearModePlugin

Following the existing pattern from `NightModePlugin`, the V2 mode plugin is a new feature module `:feature:mode-full-clear`.

```kotlin
// Placement: feature/mode-full-clear/src/main/kotlin/.../FullClearModePlugin.kt

class FullClearModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.FULL_CLEAR

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return FullClearModeController(context)
    }
}
```

**Key design decisions for the mode controller:**

1. **Route selection on shutter press**: `FullClearSceneAssessment` runs when the user presses shutter, not continuously. This avoids preview analysis overhead. Warm-up assessment can be added later as an optimization.

2. **User guidance states map to `ModeState`**:
   - `headline` carries the primary guidance label (Chinese)
   - `detail` carries the secondary explanation
   - `isShutterEnabled` gates based on `ready` vs `too_close`/`unsupported`

3. **CaptureStrategy selection**:
   - `DEEP_DOF_LENS` → `CaptureStrategy.SingleFrame` with metadata tagging the route
   - `FOCUS_BRACKET` → `CaptureStrategy.MultiFrame` with `frameCount` from plan, focus metadata in customTags
   - `LENS_PAIR_BRACKET` → `CaptureStrategy.MultiFrame` (experimental)
   - `BEST_FRAME_ONLY` → `CaptureStrategy.SingleFrame`
   - `UNSUPPORTED` → shutter disabled, guidance shown

4. **No CameraX dependency**: The mode controller imports only from `core.mode`, `core.device`, `core.media`, and `core.settings`. It does not import `androidx.camera` or `android.hardware.camera2`.

### User Guidance State Machine

```
                    ┌──────────┐
         ┌─────────│  READY   │─────────┐
         │         └──────────┘         │
         ▼                              ▼
   ┌──────────┐                  ┌──────────────┐
   │TOO_CLOSE │                  │  USE_WIDE    │
   └──────────┘                  └──────────────┘
         │                              │
         │  (user moves back)           │  (user switches)
         ▼                              ▼
   ┌──────────┐                  ┌──────────────┐
   │  READY   │                  │HOLD_STEADY   │
   └──────────┘                  └──────────────┘
         │                              │
         │  (motion > threshold)        │  (motion OK)
         ▼                              ▼
   ┌──────────────┐               ┌──────────┐
   │HOLD_STEADY   │               │CAPTURING │
   └──────────────┘               └──────────┘
                                          │
                                          ▼
                                   ┌──────────────┐
                                   │ PROCESSING   │
                                   └──────────────┘
                                          │
                              ┌───────────┼───────────┐
                              ▼           ▼           ▼
                        ┌────────┐ ┌──────────┐ ┌──────────┐
                        │ FUSED  │ │BEST_FRAME│ │ DEGRADED │
                        └────────┘ └──────────┘ └──────────┘
```

All states flow through the existing `ModeState` → `ModeSnapshot` → `SessionState` pipeline. UI renders them without knowing V2 internals.

---

## Device Adapter Extensions

### Per-Frame Focus Control

The existing `DeviceShotRequest` carries a single `manualCaptureParams` for the entire shot. For focus bracketing, each frame needs a different focus distance. This requires a **minimal extension**:

```kotlin
// Placement: core/device

data class PerFrameFocusOverride(
    val frameIndex: Int,
    val focusDistanceDiopters: Float
)

data class DeviceShotRequest(
    // ... existing fields unchanged ...
    val perFrameFocusOverrides: List<PerFrameFocusOverride> = emptyList()
)
```

The app-layer CameraX adapter reads `perFrameFocusOverrides` and applies the corresponding `CaptureRequest.LENS_FOCUS_DISTANCE` before each frame in the multi-frame sequence. This is a **device adapter concern only** — the mode plugin never sees Camera2 keys.

### Capability Detection

```kotlin
// Placement: core/device

fun DeviceCapabilities.fullClearCapabilities(): FullClearDeviceCapabilities =
    FullClearDeviceCapabilities.from(this)
```

Real-device profiling values (`ultraWideMinFocusDiopters`, `focusDistancePerFrameLatencyMs`, `lensSwitchLatencyMs`) are populated by the app-layer Camera2 query at session start, stored in `DeviceCapabilities`, and consumed by the pure route selection function. This keeps the route selection testable: tests inject `FullClearDeviceCapabilities` with known values; real devices provide real values.

### DeviceGraphSpec For Full Clear Route

```kotlin
fun fullClearDeviceGraph(
    plan: FullClearCapturePlan,
    runtimeState: ModeRuntimeState
): DeviceGraphSpec {
    val base = stillCaptureDeviceGraph(runtimeState)
    val zoomForRoute = when (plan.route) {
        FullClearRoute.DEEP_DOF_LENS -> 0.6f
        else -> base.preview.zoomRatio
    }
    return base.copy(
        preview = base.preview.copy(zoomRatio = normalizedZoomRatioValue(zoomForRoute))
    )
}
```

---

## Media Pipeline Extensions

### FullClearFusionReport

Post-fusion diagnostics that become part of `ShotResult.pipelineNotes`:

```kotlin
// Placement: core/media

data class FullClearFusionReport(
    val route: FullClearRoute,
    val lensUsed: LensNode,
    val bracketFrameCount: Int,
    val alignmentConfidence: Float,
    val alignmentMetric: String,     // "homography_ncc", "homography_only", "none"
    val fusionConfidence: Float,
    val fusionMetric: String,        // "laplacian_pyramid", "simple_blend", "none"
    val resultState: FullClearResultState,
    val degradationReasons: List<String>,
    val timestampMs: Long,
    val sessionId: String
) {
    fun toPipelineNotes(): List<String> = buildList {
        add("fc:route=${route.name.lowercase()}")
        add("fc:lens=${lensUsed.tagValue}")
        add("fc:frames=$bracketFrameCount")
        add("fc:align-confidence=$alignmentConfidence")
        add("fc:align-metric=$alignmentMetric")
        add("fc:fusion-confidence=$fusionConfidence")
        add("fc:fusion-metric=$fusionMetric")
        add("fc:result=${resultState.name.lowercase()}")
        degradationReasons.forEach { add("fc:degraded=$it") }
        add("fc:timestamp=$timestampMs")
        add("fc:session=$sessionId")
    }
}

enum class FullClearResultState {
    FUSED,
    BEST_FRAME,
    DEGRADED
}
```

### FullClearFusionProcessor

A new `AlgorithmProcessor` implementation reusing the existing `MULTI_FRAME_MERGE` algorithm type:

```kotlin
// Placement: core/media or app layer

class FullClearFusionProcessor : AlgorithmProcessor {
    override val type: AlgorithmType = AlgorithmType.MULTI_FRAME_MERGE

    override fun canProcess(request: AlgorithmRequest): Boolean {
        val frameCount = request.metadata.customTags["fcFrameCount"]?.toIntOrNull() ?: 0
        return frameCount >= 2 && request.inputs.size >= 2
    }

    override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
        val frameCount = request.metadata.customTags["fcFrameCount"]?.toIntOrNull() ?: 1
        val route = request.metadata.customTags["fcRoute"] ?: "unknown"

        // 1. Load frames
        // 2. Coarse alignment (global homography)
        // 3. Alignment confidence check → gate fusion
        // 4. Lens breathing compensation
        // 5. Sharpness map per frame
        // 6. Laplacian pyramid fusion
        // 7. Fusion confidence check → gate output
        // 8. Fallback: save best single frame if confidence below threshold

        val report = FullClearFusionReport(
            route = FullClearRoute.valueOf(route.uppercase()),
            lensUsed = LensNode.WIDE,
            bracketFrameCount = frameCount,
            alignmentConfidence = 0f,  // placeholder
            alignmentMetric = "none",
            fusionConfidence = 0f,     // placeholder
            fusionMetric = "none",
            resultState = FullClearResultState.BEST_FRAME,
            degradationReasons = listOf("fc:placeholder-processor"),
            timestampMs = System.currentTimeMillis(),
            sessionId = request.metadata.customTags["sessionId"] ?: "unknown"
        )

        return AlgorithmResult.Applied(
            output = request.inputs.first().handle,
            notes = report.toPipelineNotes()
        )
    }
}
```

**Real fusion is deferred to package 04 (algorithm design).** The architecture preserves the contract while the algorithm is designed separately.

### ShotGraph for Full Clear

The existing `ShotGraphBuilder` already handles `MULTI_FRAME_CAPTURE` with `TEMPORARY_FRAME` nodes + `MULTI_FRAME_MERGE` algorithm. V2 reuses this exactly:

```
ShotGraph for FOCUS_BRACKET:
  CaptureNodes:
    - TEMPORARY_FRAME (frameCount = N, sequential)
    - PRIMARY_STILL (frameCount = 1)
  AlgorithmNodes:
    - MULTI_FRAME_MERGE (inputs: TEMPORARY_FRAME outputs, REQUIRED, FAIL_SHOT fallback)
  OutputNodes:
    - PRIMARY_STILL (mimeType = image/jpeg)
```

No new `ShotKind` needed. No new `AlgorithmType` needed for the initial contract. V2-specific behavior (focus-distance per frame, alignment check, fusion confidence) is driven by metadata tags.

---

## User Guidance to ModeState Mapping

Each V2 product state maps to `ModeState` fields:

| V2 State | `headline` | `detail` | `isShutterEnabled` |
|---|---|---|---|
| `ready` | "就绪" | "适合全清拍摄" | true |
| `too_close` | "请稍退后" | "拍摄对象距离太近" | false |
| `use_wide` | "请切换超广角" | "超广角效果更佳" | true |
| `hold_steady` | "请保持稳定" | "手机稳定后可拍摄" | false |
| `unsupported` | "建议广角模式" | "当前设备不支持全清" | false |
| `capturing` | "拍摄中" | "请保持手机稳定" | false |
| `processing` | "处理中" | "正在生成全清照片" | false |
| `fused` | "全清" | "远近皆清晰" | true |
| `best_frame` | "已保存最佳帧" | "本次未使用融合处理" | true |
| `degraded` | "已保存单帧" | "全清处理未能完成" | true |

---

## Android/CameraX/Camera2 Constraints

### Constraint 1: Focus Distance Is Not Always Writable

**Impact**: `FOCUS_BRACKET` route depends on `ManualControlCapabilityMatrix.focusDistance == APPLY`.

**Detection**: Write test focus value via `Camera2Interop.Extender`, capture frame, read back EXIF. If mismatch, mark degraded.

**Fallback**: Route to `DEEP_DOF_LENS` if ultra-wide available, otherwise `BEST_FRAME_ONLY`.

**Test strategy**: Unit test `FullClearDeviceCapabilities.from()` with `focusDistance == SAVED_ONLY` → verify `focusDistanceWritable == false` → verify route selector returns non-bracket route.

### Constraint 2: Per-Frame Focus Latency

**Impact**: Bracket frame count and inter-frame delay depend on lens focus speed.

**Detection (real device only)**: Time a 3-frame bracket end-to-end. If > 500ms per frame, reduce to 2 frames.

**Gating**: `maxBracketFrames` in `FullClearDeviceCapabilities` populated by real-device profiling. Default conservative: 3.

**Test strategy**: Unit test with `focusDistancePerFrameLatencyMs = 100` → 5-frame plan. With `800ms` → degraded 2-frame plan.

### Constraint 3: Lens Switching Disrupts Preview

**Impact**: `LENS_PAIR_BRACKET` requires mid-bracket lens switch; marked experimental.

**Detection (real device only)**: Time lens-switch-and-capture cycle. If > 1s, route not recommended.

**Test strategy**: Unit test with `lensSwitchLatencyMs = 2_000` → verify `LENS_PAIR_BRACKET` confidence < 0.3.

### Constraint 4: Motion During Bracket

**Impact**: Gyro motion invalidates alignment. Fusion gated on post-capture alignment confidence.

**Gating**: Pre-capture gyro check → shutter gate. Post-capture alignment check → fusion gate. Two independent gates.

**Test strategy**: Unit test `alignmentConfidence < threshold` → `resultState == BEST_FRAME`. Unit test `stability.isStable == false` → route confidence reduced.

### Constraint 5: Thermal Throttling Reduces Frame Budget

**Impact**: `ResourceAdmissionPolicy.admitMultiFrame()` reduces frame count under thermal stress.

**Integration**: Check `FullClearCapturePlan.maxTotalLatencyMs` against admitted budget. Budget cuts frame count → plan degrades.

**Test strategy**: Unit test with `CameraThermalState.HOT` → verify `admitMultiFrame` reduces effective frame count → verify plan builder reduces bracket steps.

### Constraint 6: No RAW/DNG Fusion

**Impact**: V2 outputs JPEG only. RAW fusion is a non-goal (per product definition).

**Gating**: `CaptureStrategy.MultiFrame` with `postProcessSpec` always produces JPEG output. Existing pipeline enforces this.

---

## Capability Matrix Implementation

Mapping product definition capabilities to code contracts:

| Product Capability | Code Contract | Detection |
|---|---|---|
| Deep-DOF Lens Path | `FullClearRoute.DEEP_DOF_LENS` | `FullClearDeviceCapabilities.hasUltraWideLens` && `lensNodeMap` |
| Focus Bracket Path | `FullClearRoute.FOCUS_BRACKET` | `ManualControlCapabilityMatrix.focusDistance == APPLY` |
| Lens-Aware Routing | `FullClearRoute.LENS_PAIR_BRACKET` | `lensNodeMap` size > 1 |
| Alignment Confidence | `FullClearFusionReport.alignmentConfidence` | Computed post-capture in `FullClearFusionProcessor` |
| Fusion Confidence | `FullClearFusionReport.fusionConfidence` | Computed post-fusion in `FullClearFusionProcessor` |
| Saved Diagnostics | `FullClearFusionReport.toPipelineNotes()` | Always written; flows through `ShotResult.pipelineNotes` |

---

## Implementation Risks

### Risk 1: Per-Frame Focus Override on CameraX

**Severity**: Medium
**Description**: Adding `perFrameFocusOverrides` requires the app-layer CameraX adapter to reconfigure `ImageCapture` between frames, which may not be directly supported by CameraX's builder pattern.

**Mitigation**: Fall back to Camera2 interop mode: create a new `CaptureRequest` per frame with different `LENS_FOCUS_DISTANCE`, submitted through `Camera2Interop.Extender`.

**Verification**: Unit test `DeviceShotRequestTranslator` with `perFrameFocusOverrides` → verify correct frame-indexed focus values. Integration test on real device with Camera2 interop.

### Risk 2: Alignment Failure on Handheld

**Severity**: High
**Description**: Handheld bracket frames have inter-frame motion from hand shake, subject movement, and lens breathing. Alignment may fail more often than it succeeds.

**Mitigation**: Architecture gates fusion on alignment confidence. If alignment fails, falls back to best-frame. `resultState == BEST_FRAME` is a valid and expected outcome.

**Verification**: Alignment confidence gate is a pure function on frame pairs → fully unit-testable. Real-device pass rate validated in package 05.

### Risk 3: Lens Breathing Changes Framing

**Severity**: Medium
**Description**: Focus changes on some lenses cause FOV shift (breathing), confusing alignment and producing fusion edge artifacts.

**Mitigation**: Lens breathing compensation in fusion processor (crop/scale each frame to reference FOV). Severe breathing → degrade to best-frame.

**Verification**: Unit test breathing compensation with synthetic FOV-shifted frame pairs.

### Risk 4: Ultra-Wide Minimum Focus Distance Unknown

**Severity**: Low
**Description**: `LENS_INFO_MINIMUM_FOCUS_DISTANCE` may return 0 (unknown) on some devices.

**Mitigation**: When unknown, assume ultra-wide can focus close. Worst case: blurry near subject, user retries. Fallback: `BEST_FRAME_ONLY`.

### Risk 5: No Existing V1 FullClear Mode Plugin

**Severity**: Low
**Description**: `ModeId.FULL_CLEAR` exists in enum but no `feature/mode-full-clear` module. V2 must create entire mode plugin from scratch.

**Mitigation**: Follow `NightModePlugin` pattern exactly. V2 mode is simpler: fewer profiles, no flash logic, no live photo, no filter rendering.

---

## Test Strategy

### Unit Tests (No Device Required)

All core contracts are pure functions → fully unit-testable without Android framework:

1. **`FullClearDeviceCapabilities` tests**:
   - `from(DeviceCapabilities)` maps correctly for focus-writable vs non-writable devices
   - `from(DeviceCapabilities)` correctly identifies ultra-wide availability from `lensNodeMap`
   - Default `maxBracketFrames` is 3 for conservative, 6 for multi-frame-capable

2. **`selectFullClearRoute` tests**:
   - Ultra-wide available + subject in range → `DEEP_DOF_LENS` (confidence ≥ 0.7)
   - Ultra-wide not available + focus writable + stable → `FOCUS_BRACKET` (confidence ≥ 0.5)
   - Focus not writable + ultra-wide not available → `BEST_FRAME_ONLY`
   - No manual controls + no ultra-wide → `UNSUPPORTED`
   - Tele + ultra-wide but no focus write → `LENS_PAIR_BRACKET` (degraded)
   - Unstable + focus writable → `FOCUS_BRACKET` with reduced confidence
   - All routes → verify `degradationReasons` populated

3. **`buildFullClearCapturePlan` tests**:
   - `DEEP_DOF_LENS` → 0 bracket steps, single frame
   - `FOCUS_BRACKET` with 3 frames → near, mid, far focus steps
   - `FOCUS_BRACKET` with 5 frames → 5 steps with correct diopter interpolation
   - `UNSUPPORTED` → 0 bracket steps, fallback `REPORT_ERROR`

4. **`FullClearFusionReport` tests**:
   - `toPipelineNotes()` produces correctly formatted tags
   - All required fields present regardless of result state
   - `BEST_FRAME` result still records route and degradation reasons

5. **`FullClearModeController` tests** (using test ModeContext):
   - Shutter press with `DEEP_DOF_LENS` route → emits `CaptureStrategy.SingleFrame`
   - Shutter press with `FOCUS_BRACKET` route → emits `CaptureStrategy.MultiFrame` with correct frameCount
   - `isSupported()` returns true when `supportsStillCapture` is true
   - User guidance headline updates on route change
   - `isShutterEnabled` is false when route is `UNSUPPORTED`

### Real-Device Tests (Deferred to Package 05)

- Per-frame focus distance write + verify on physical device
- Lens switch latency profiling
- Gyro stability threshold tuning
- End-to-end bracket capture + fusion pass rate
- Thermal degradation behavior under sustained use

### Verification Command

```bash
grep -n "Mode Plugin\|Session Kernel\|Device Adapter\|Media Pipeline\|FullClearRoute\|FullClearCapturePlan\|FullClearSceneAssessment\|FullClearFusionReport\|FullClearDeviceCapabilities\|FullClearModeController\|FullClearFallbackPolicy" docs/plans/full-clear-mode-v2-research-orchestration/v2-implementation-design.md
```

---

## Future Implementation Package Candidates

From this architecture design, the following implementation packages emerge:

| Package | Scope | Dependencies |
|---|---|---|
| `fc-v2-core-contracts` | `FullClearSceneAssessment`, `FullClearRoute`, `FullClearCapturePlan`, `FullClearDeviceCapabilities`, `FullClearFusionReport` in `core/device` and `core/media` | 03 (this design) |
| `fc-v2-mode-plugin` | `:feature:mode-full-clear` module with `FullClearModePlugin` and `FullClearModeController` | `fc-v2-core-contracts` |
| `fc-v2-device-adapter` | Per-frame focus override support in `DeviceShotRequest` and `DeviceShotRequestTranslator`; Camera2 focus-distance capability detection | `fc-v2-core-contracts` |
| `fc-v2-fusion-processor` | `FullClearFusionProcessor` (alignment, fusion, fallback) in media pipeline | `fc-v2-core-contracts`, 04 (algorithm design) |
| `fc-v2-diagnostics` | Diagnostics persistence (sidecar JSON or EXIF custom tags) | `fc-v2-core-contracts` |
| `fc-v2-real-device-validation` | Physical device profiling, latency measurement, bracket pass-rate QA | all implementation packages |
