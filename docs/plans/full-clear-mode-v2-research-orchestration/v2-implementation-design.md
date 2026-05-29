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
    // Degradation reasons accumulate; the best available route wins

    // 1. Unsupported check
    if (!capabilities.supportsManualControls && !capabilities.hasUltraWideLens) {
        return RouteSelectionResult(FullClearRoute.UNSUPPORTED, 0f, listOf("no-manual-controls-no-uw"))
    }

    // 2. Deep-DOF path: always preferred when ultra-wide exists and subject is not too close
    if (capabilities.hasUltraWideLens) {
        val uwMinDiopters = capabilities.ultraWideMinFocusDiopters ?: 0f
        val nearDiopters = nearHint.minDiopters
        if (nearDiopters == null || nearDiopters <= uwMinDiopters) {
            // Subject within ultra-wide focus range → route to deep-DOF
            val confidence = if (nearDiopters != null) 0.9f else 0.7f
            return RouteSelectionResult(FullClearRoute.DEEP_DOF_LENS, confidence, emptyList())
        }
        // Subject too close → deep-DOF degraded but still preferred over nothing
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
            // Bracket requires stability
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

    // 4. Lens-pair bracket: experimental, requires both tele and wide
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
    val lensNode: LensNode,  // WIDE, TELEPHOTO, etc.
    val bracketSteps: List<FocusBracketStep>,
    val maxTotalLatencyMs: Long,
    val fallbackPolicy: FullClearFallbackPolicy,
    val diagnostics: List<String>
)

enum class FullClearFallbackPolicy {
    /** On any bracket frame failure, save the best captured frame. */
    SAVE_BEST_AVAILABLE_FRAME,

    /** On bracket failure, retry as single-frame with best-guess focus. */
    RETRY_SINGLE_FRAME,

    /** On failure, report error to user. */
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
            lensNode = LensNode.WIDE,  // ultra-wide mapped through zoom
            bracketSteps = emptyList(),
            maxTotalLatencyMs = 1_000L,
            fallbackPolicy = FullClearFallbackPolicy.RETRY_SINGLE_FRAME,
            diagnostics = listOf("fc:plan:deep-dof:single-frame")
        )

        FullClearRoute.FOCUS_BRACKET -> {
            val nearDiopters = assessment.nearSubject.minDiopters ?: 3f
            val farDiopters = 0f  // infinity
            val frameCount = capabilities.maxBracketFrames.coerceIn(3, 6)
            val steps = buildFocusBracketSteps(nearDiopters, farDiopters, frameCount)
            FullClearCapturePlan(
                route = route,
                lensNode = LensNode.WIDE,
                bracketSteps = steps,
                maxTotalLatencyMs = frameCount * (capabilities.focusDistancePerFrameLatencyMs ?: 300L) + 2_000L,
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
    // Near focus
    steps.add(FocusBracketStep(0, nearDiopters, "near"))
    // Mid points
    if (frameCount > 2) {
        val midCount = frameCount - 2
        for (i in 1..midCount) {
            val t = i.toFloat() / (midCount + 1)
            val diopters = nearDiopters + (farDiopters - nearDiopters) * t
            steps.add(FocusBracketStep(i, diopters, "mid-$i"))
        }
    }
    // Far focus
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

New capability queries added to `DeviceCapabilities` or a V2-specific extension:

```kotlin
// Placement: core/device

fun DeviceCapabilities.fullClearCapabilities(): FullClearDeviceCapabilities =
    FullClearDeviceCapabilities.from(this)
```

Real-device profiling values (`ultraWideMinFocusDiopters`, `focusDistancePerFrameLatencyMs`, `lensSwitchLatencyMs`) are populated by the app-layer Camera2 query at session start, stored in `DeviceCapabilities`, and consumed by the pure route selection function. This keeps the route selection testable: tests inject `FullClearDeviceCapabilities` with known values; real devices provide real values.

### DeviceGraphSpec Extension

For the deep-DOF path, the mode controller needs to request the ultra-wide lens:

```kotlin
// The existing DeviceGraphSpec already supports zoom ratio selection
// which maps to lens switching through ZoomRatioCapability.lensNodeMap.
// V2 uses the existing mechanism:

fun fullClearDeviceGraph(
    plan: FullClearCapturePlan,
    runtimeState: ModeRuntimeState
): DeviceGraphSpec {
    val base = stillCaptureDeviceGraph(runtimeState)
    // For deep-DOF, request 0.5x or 0.6x zoom to engage ultra-wide
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
    // Alignment stage diagnostics
    val alignmentConfidence: Float,       // 0.0–1.0 composite alignment score
    val alignmentMetric: String,          // "orb_homography_ncc", "homography_only", "none"
    val perFrameInlierRatios: List<Float>, // one per non-reference frame
    // Breathing stage diagnostics
    val breathingSeverity: Float,         // 0.0–1.0, from scale deviation and overlap loss
    val breathingOverlapRatio: Float,     // overlap ratio after breathing correction
    // Sharpness stage diagnostics
    val perFrameSharpnessScores: List<Float>, // Laplacian variance per frame
    val referenceFrameIndex: Int,         // which frame was used as alignment reference
    // Fusion stage diagnostics
    val fusionConfidence: Float,          // 0.0–1.0 composite fusion quality score
    val fusionMetric: String,             // "laplacian_pyramid_max", "simple_blend", "none"
    val edgeConsistencyScore: Float,      // 0.0–1.0, ghost/double-edge detection
    val ghostRejectionApplied: Boolean,   // whether ghost rejection was triggered
    // Result
    val resultState: FullClearResultState,
    val degradationReasons: List<String>,
    val timestampMs: Long,
    val sessionId: String
) {
    fun toPipelineNotes(): List<String> = buildList {
        add("fc:route=${route.name.lowercase()}")
        add("fc:lens=${lensUsed.tagValue}")
        add("fc:frames=$bracketFrameCount")
        add("fc:ref-frame=$referenceFrameIndex")
        // Alignment
        add("fc:align-confidence=$alignmentConfidence")
        add("fc:align-metric=$alignmentMetric")
        perFrameInlierRatios.forEachIndexed { i, r ->
            add("fc:align-inlier[$i]=$r")
        }
        // Breathing
        add("fc:breathing-severity=$breathingSeverity")
        add("fc:breathing-overlap=$breathingOverlapRatio")
        // Sharpness
        perFrameSharpnessScores.forEachIndexed { i, s ->
            add("fc:sharpness[$i]=$s")
        }
        // Fusion
        add("fc:fusion-confidence=$fusionConfidence")
        add("fc:fusion-metric=$fusionMetric")
        add("fc:edge-consistency=$edgeConsistencyScore")
        add("fc:ghost-rejection=$ghostRejectionApplied")
        // Result
        add("fc:result=${resultState.name.lowercase()}")
        degradationReasons.forEach { add("fc:degraded=$it") }
        add("fc:timestamp=$timestampMs")
        add("fc:session=$sessionId")
    }
}

enum class FullClearResultState {
    /** Multi-frame fusion succeeded with acceptable confidence. */
    FUSED,

    /** Fusion gates failed or were bypassed; single sharpest frame saved. */
    BEST_FRAME,

    /** Fusion applied but with known quality issues (breathing, ghost, etc.). */
    DEGRADED
}
```

### FullClearFusionProcessor

A new `AlgorithmProcessor` implementation (like the existing `MultiFrameMergePlaceholderPostProcessor`):

```kotlin
// Placement: core/media or app layer

**FullClearFusionProcessor** delegates to the fusion pipeline stages defined below. The processor itself is a thin orchestrator; each stage is independently testable.

```kotlin
// Placement: core/media

class FullClearFusionProcessor : AlgorithmProcessor {
    override val type: AlgorithmType = AlgorithmType.MULTI_FRAME_MERGE

    override fun canProcess(request: AlgorithmRequest): Boolean {
        val frameCount = request.metadata.customTags["fcFrameCount"]?.toIntOrNull() ?: 0
        return frameCount >= 2 && request.inputs.size >= 2
    }

    override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
        val route = request.metadata.customTags["fcRoute"] ?: "unknown"
        val frameCount = request.metadata.customTags["fcFrameCount"]?.toIntOrNull() ?: 1
        val sessionId = request.metadata.customTags["sessionId"] ?: "unknown"

        // Stage 0: Load and normalize frames
        val frames = loadFrames(request.inputs)
        val normalized = ExposureNormalizer.normalize(frames)

        // Stage 1: Coarse alignment (ORB + homography)
        val refIndex = selectReferenceFrame(normalized)
        val alignments = normalized.mapIndexedNotNull { i, frame ->
            if (i == refIndex) null
            else FrameAligner.align(normalized[refIndex], frame, i)
        }

        // Stage 2: Alignment confidence gate
        val alignmentResult = AlignmentConfidenceEvaluator.evaluate(alignments)
        if (alignmentResult.confidence < ALIGNMENT_THRESHOLD) {
            return buildBestFrameResult(request, frames, refIndex, route, sessionId,
                alignmentResult, degradationReasons = listOf("alignment-below-threshold"))
        }

        // Stage 3: Lens breathing compensation
        val breathingResult = BreathingCompensator.compensate(
            normalized[refIndex], normalized, alignments
        )

        // Stage 4: Sharpness maps
        val sharpnessMaps = SharpnessMapGenerator.generate(normalized)

        // Stage 5: Laplacian pyramid fusion
        val fusedImage = LaplacianPyramidFuser.fuse(
            frames = normalized,
            alignments = alignments,
            sharpnessMaps = sharpnessMaps,
            referenceIndex = refIndex,
            breathingCompensation = breathingResult
        )

        // Stage 6: Fusion confidence gate
        val fusionConf = FusionConfidenceEvaluator.evaluate(
            fusedImage, normalized, alignments
        )
        if (fusionConf.confidence < FUSION_THRESHOLD) {
            return buildBestFrameResult(request, frames, refIndex, route, sessionId,
                alignmentConfidence = alignmentResult.confidence,
                breathingResult = breathingResult,
                sharpnessMaps = sharpnessMaps,
                fusionConfidence = fusionConf,
                degradationReasons = listOf("fusion-below-threshold")
            )
        }

        // Stage 7: Output selection
        val report = buildFusionReport(
            route = route, refIndex = refIndex, frameCount = frameCount,
            alignments = alignments, alignmentResult = alignmentResult,
            breathingResult = breathingResult, sharpnessMaps = sharpnessMaps,
            fusionConf = fusionConf, resultState = FullClearResultState.FUSED,
            sessionId = sessionId
        )

        return AlgorithmResult.Applied(
            output = fusedImage.handle,
            notes = report.toPipelineNotes()
        )
    }
}
```



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

No new `ShotKind` needed. No new `AlgorithmType` needed for the initial contract. The V2-specific behavior (focus-distance per frame, alignment check, fusion confidence) is driven by metadata tags on the `ShotRequest` and `AlgorithmRequest`.

---

## V2 Fusion Algorithm Design

This section defines the fusion algorithm stages, confidence gates, fallback decision tree, and data contracts. Every stage is designed as a pure function on input frames — no HAL, no CameraX, no I/O inside the algorithm. This makes every stage independently unit-testable with synthetic frame pairs.

### Algorithm Overview

```
Bracket Frames (N) ──────────────────────────────────────────────────────┐
                                                                          │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 0: Exposure Normalization                                   │   │
  │   • Convert YUV → linear RGB working space                        │   │
  │   • Histogram-mean matching across frames                         │   │
  │   • Select reference frame (highest Laplacian variance)           │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼                                          │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 1: Coarse Alignment (ORB + Homography)                      │   │
  │   • Detect ORB features on each frame                             │   │
  │   • Match features: reference ↔ each bracket frame                │   │
  │   • Estimate global homography via RANSAC                         │   │
  │   • Output: N-1 homography matrices, inlier ratios                │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼                                          │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 2: Alignment Confidence Gate                                 │   │
  │   • Warp each frame by its homography                             │   │
  │   • Compute NCC on 16×16 grid between warped frame and reference  │   │
  │   • alignmentConfidence = mean(grid NCC) × mean(inlier ratio)     │   │
  │   • GATE: if < 0.6 → BEST_FRAME fallback                          │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼ (passed)                                 │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 3: Lens Breathing Compensation                              │   │
  │   • Extract scale component from homography matrices              │   │
  │   • If scale deviation > 5%: crop/scale to match reference FOV    │   │
  │   • Compute corrected overlap ratio                               │   │
  │   • breathingSeverity = f(scale deviation, overlap loss)          │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼                                          │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 4: Sharpness Map Per Frame                                  │   │
  │   • Compute Laplacian variance on 8×8 blocks per frame            │   │
  │   • Normalize scores to [0, 1] per frame                          │   │
  │   • Bilinear interpolation to per-pixel weight map                │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼                                          │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 5: Laplacian Pyramid Fusion                                 │   │
  │   • Build Gaussian pyramid (4 levels) per aligned frame           │   │
  │   • Build Laplacian pyramid from Gaussian differences             │   │
  │   • At each level: blend using per-pixel max-sharpness weight     │   │
  │   • Collapse pyramid → fused RGB image                            │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼                                          │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 6: Fusion Confidence Gate                                    │   │
  │   • Edge consistency: Canny on fused vs per-frame edges           │   │
  │   • Ghost detection: double-edge signature in fused result        │   │
  │   • Artifact check: variance in fused flat regions                │   │
  │   • GATE: if fusionConfidence < 0.5 → BEST_FRAME fallback         │   │
  └────────────────────────────┬─────────────────────────────────────┘   │
                               ▼ (passed)                                 │
  ┌──────────────────────────────────────────────────────────────────┐   │
  │ Stage 7: Output Selection                                         │   │
  │   • FUSED: both gates passed, no severe breathing                 │   │
  │   • DEGRADED: gates passed but breathing severity > 0.3           │   │
  │   • BEST_FRAME: any gate failed → save sharpest single frame      │   │
  └──────────────────────────────────────────────────────────────────┘   │
```

### Algorithm Thresholds

All thresholds are tunable constants, not magic numbers. They should be validated on real-device data during implementation.

| Threshold | Default | Meaning | Validation Method |
|---|---|---|---|
| `ALIGNMENT_THRESHOLD` | 0.6 | Minimum alignment confidence to proceed to fusion | Real-device pass rate on handheld bracket pairs |
| `FUSION_THRESHOLD` | 0.5 | Minimum fusion confidence to accept fused output | Perceptual comparison: fused vs best-frame |
| `BREATHING_SEVERITY_THRESHOLD` | 0.3 | Max breathing severity before result is marked DEGRADED | Real-device lens characterization |
| `INLIER_RATIO_MIN` | 0.3 | Minimum inlier ratio for homography to be considered valid | ORB on smartphone bracket frames |
| `SCALE_DEVIATION_MAX` | 0.05 | Max homography scale deviation from identity (5%) before breathing correction activates | Lens calibration data |
| `OVERLAP_MIN` | 0.8 | Minimum corrected overlap ratio before breathing is considered severe | Real-device FOV shift measurement |
| `SHARPNESS_MIN` | 0.1 | Per-frame mean sharpness below which a frame is tagged as potentially blurred | OOF detection on real frames |
| `PYRAMID_LEVELS` | 4 | Number of Laplacian pyramid levels | Determined by bracket frame resolution |
| `NCC_GRID_SIZE` | 16 | Grid dimension for NCC computation | Balance between spatial precision and noise |

### Stage 0: Exposure Normalization

**Purpose**: Different focus distances produce different exposures (AF adjusts exposure per focus plane). Before alignment and fusion, frames must be normalized to comparable brightness and color.

**Algorithm**:
1. Convert each frame from YUV_420_888 (CameraX default) to linear RGB working space.
2. Compute per-channel histogram mean (R, G, B) for each frame.
3. Select the frame with the highest Laplacian variance as the reference frame. This is typically the mid-focus frame where the subject is sharpest.
4. For each non-reference frame, compute per-channel gain: `gain_c = mean_ref_c / mean_frame_c`, clamped to [0.5, 2.0] to prevent extreme correction.
5. Apply gain to each non-reference frame.
6. Clip to [0.0, 1.0] range.

**Contract**:
```kotlin
object ExposureNormalizer {
    data class NormalizedFrames(
        val frames: List<RGBFrame>,
        val referenceIndex: Int,
        val perFrameGains: List<FloatArray>,  // [R, G, B] per frame
        val diagnostics: List<String>
    )

    fun normalize(frames: List<ImageProxy>): NormalizedFrames
    fun selectReferenceFrame(frames: List<RGBFrame>): Int  // highest Laplacian variance
}
```

**Testability**: Pure function on pixel buffers. Test with synthetic frames of known brightness offsets → verify gain computation and clipping.

### Stage 1: Coarse Alignment (ORB + Homography)

**Purpose**: Estimate the geometric transform between each bracket frame and the reference frame. Handheld bracket frames have small inter-frame translations (hand shake) and small rotations.

**Algorithm**:
1. Detect ORB features on each frame (target: 500 features per frame). ORB is chosen over SIFT for speed and over Harris corners for rotation invariance. The inter-frame motion between bracket frames is small (< 5% frame dimension), so feature quality matters more than feature quantity.
2. For each non-reference frame `i`:
   a. Match ORB descriptors between reference and frame `i` using brute-force Hamming distance with Lowe's ratio test (ratio = 0.75).
   b. Filter matches: keep only matches where `best_distance < 0.75 * second_best_distance`.
   c. Estimate global homography (3×3 matrix) from matched point pairs using RANSAC with 4-point minimal solver.
   d. Record inlier ratio: `inliers.count / matches.count`.
3. If inlier ratio < `INLIER_RATIO_MIN` (0.3) for any frame pair, that frame is excluded from fusion. If all frame pairs fail, alignment is impossible → BEST_FRAME fallback.

**Contract**:
```kotlin
data class FrameAlignment(
    val frameIndex: Int,
    val homography: FloatArray,   // 3×3 row-major
    val inlierCount: Int,
    val matchCount: Int,
    val inlierRatio: Float        // inlierCount / matchCount
)

object FrameAligner {
    fun align(ref: RGBFrame, target: RGBFrame, targetIndex: Int): FrameAlignment?
    // Returns null if inlier ratio < INLIER_RATIO_MIN
}
```

**Testability**: Pure function. Test with synthetic frame pairs:
- Identity transform (same image) → verify homography ≈ identity, inlier ratio ≈ 1.0
- Known translation (10px right, 5px down) → verify homography encodes correct translation
- Known rotation (2°) → verify homography encodes correct rotation
- Purely random images → verify inlier ratio < 0.3, returns null

### Stage 2: Alignment Confidence Gate

**Purpose**: Even when homography estimation "succeeds" (enough inliers), the alignment may be poor due to parallax, subject movement, or rolling shutter. NCC on a grid provides spatially-aware alignment quality.

**Algorithm**:
1. Warp each non-reference frame by its homography matrix (bilinear interpolation).
2. For each warped frame pair `(warped_i, reference)`:
   a. Divide both images into an `NCC_GRID_SIZE × NCC_GRID_SIZE` grid (16×16 = 256 cells).
   b. For each grid cell, compute Normalized Cross-Correlation (NCC):
      `NCC = Σ((A - μ_A)(B - μ_B)) / sqrt(Σ(A - μ_A)² · Σ(B - μ_B)²)`
   c. The grid NCC score for frame `i` is the mean of all cell NCC values.
3. Composite alignment confidence:
   `alignmentConfidence = mean(grid_ncc_scores) * mean(all_inlier_ratios)`
   This weights both feature-level agreement (inliers) and pixel-level agreement (NCC).
4. Gate: if `alignmentConfidence < ALIGNMENT_THRESHOLD (0.6)` → BEST_FRAME fallback.

**Contract**:
```kotlin
data class AlignmentConfidenceResult(
    val confidence: Float,              // composite 0–1
    val perFrameNccScores: List<Float>, // mean NCC per frame pair
    val perFrameInlierRatios: List<Float>,
    val passed: Boolean                 // confidence >= ALIGNMENT_THRESHOLD
)

object AlignmentConfidenceEvaluator {
    fun evaluate(alignments: List<FrameAlignment>): AlignmentConfidenceResult
}
```

**Testability**: Pure function. Test with:
- Perfectly aligned frames (same image) → verify confidence ≈ 1.0
- Slightly misaligned frames (1-2px shift) → verify confidence ≈ 0.9
- Moderately misaligned (5px shift) → verify confidence ≈ 0.7
- Heavily misaligned (20px shift or rotation > 5°) → verify confidence < 0.6, passed = false

### Stage 3: Lens Breathing Compensation

**Purpose**: Focus changes on some lenses cause noticeable field-of-view shift (breathing). The near-focus frame may have a different FOV than the far-focus frame, causing edge artifacts in fusion even when alignment is correct.

**Algorithm**:
1. Extract the scale component from each homography matrix by decomposing `H = K * R * K^-1` or simply reading the diagonal elements.
2. Compute scale deviation: `|scale - 1.0|` for each frame pair.
3. If any frame pair has scale deviation > `SCALE_DEVIATION_MAX (0.05, i.e. 5%)`:
   a. Determine the largest frame (smallest scale, widest FOV) as the crop reference.
   b. Crop all other frames to match the largest frame's FOV in the aligned coordinate space.
   c. Compute overlap ratio: `intersection_area / reference_frame_area`.
4. Compute breathing severity:
   `breathingSeverity = max(scale_deviation / 0.15, 1.0 - overlap_ratio) * (1.0 - overlap_ratio <= 0.2 ? 0.5 : 1.0)`
   This produces 0.0 (no breathing) to 1.0 (severe breathing, large FOV mismatch).
5. Record the crop region and overlap ratio in diagnostics.

**Contract**:
```kotlin
data class BreathingCompensationResult(
    val scaleDeviations: List<Float>,   // per frame pair
    val correctedOverlapRatio: Float,   // after correction
    val severity: Float,                // 0–1 composite
    val cropRect: Rect?,                // applied crop, null if no correction needed
    val diagnostics: List<String>
)

object BreathingCompensator {
    fun compensate(
        reference: RGBFrame,
        frames: List<RGBFrame>,
        alignments: List<FrameAlignment>
    ): BreathingCompensationResult
}
```

**Testability**: Pure function. Test with:
- Frames with known scale factors (1.0, 1.02, 0.98) → verify severity ≈ 0
- Frames with large scale mismatch (1.0, 1.10) → verify severity > 0.5, crop applied
- Frames with extreme mismatch (1.0, 1.20) → verify severity > 0.8

### Stage 4: Sharpness Map Per Frame

**Purpose**: Each bracket frame is sharp in a different depth plane. The sharpness map tells the fusion stage which pixels to take from which frame.

**Algorithm**:
1. Convert each aligned frame to grayscale.
2. Divide each frame into 8×8 pixel blocks (tunable based on resolution; assume ~12MP input).
3. For each block, compute Laplacian variance:
   `var = variance(∇²I) = Σ(∇²I - μ_∇²)² / N`
   where `∇²I` is the Laplacian of the image (3×3 kernel: `[[0,1,0],[1,-4,1],[0,1,0]]`).
4. Normalize per-frame block scores to [0, 1] range (min-max normalization within each frame).
5. Compute per-frame mean sharpness. Tag any frame with mean sharpness < `SHARPNESS_MIN (0.1)` as potentially blurred.
6. Generate per-pixel weight map by bilinear interpolation between block centers.
7. Output: N sharpness maps (same dimensions as reference frame).

**Contract**:
```kotlin
data class SharpnessMap(
    val frameIndex: Int,
    val blockScores: FloatArray,   // length = (width/8) * (height/8)
    val meanSharpness: Float,      // mean of blockScores
    val perPixelWeights: FloatArray // width * height, computed on demand
)

object SharpnessMapGenerator {
    fun generate(frames: List<RGBFrame>): List<SharpnessMap>
}
```

**Testability**: Pure function. Test with:
- Synthetic sharp frame (high-frequency pattern) → verify meanSharpness > 0.5
- Synthetic blurred frame (Gaussian blur σ=5) → verify meanSharpness < 0.1
- Frame with sharp region + blurred region → verify block-level scores reflect local sharpness

### Stage 5: Laplacian Pyramid Fusion

**Purpose**: Multi-scale fusion avoids seam artifacts that occur with single-scale pixel selection. The Laplacian pyramid decomposes each frame into frequency bands; fusion selects the sharpest contribution at each scale.

**Algorithm**:
1. Build Gaussian pyramid for each aligned frame (default: 4 levels).
   - Level 0 = original resolution
   - Level k = Gaussian blur (σ = 2^k) + downsample ×2 of level k-1
2. Build Laplacian pyramid from Gaussian differences:
   - For levels 0 to N-2: `L_k = G_k - expand(G_{k+1})`
   - Level N-1 (coarsest): `L_{N-1} = G_{N-1}`
3. Build weight pyramids from sharpness maps:
   - For each frame's sharpness map, build the same pyramid.
   - At each level: normalize weights across frames so `Σ weight_i = 1` per pixel.
4. Blend at each level:
   - `L_fused_k(x, y) = Σ_i weight_i_k(x, y) * L_i_k(x, y)`
5. Collapse pyramid (reverse process → reconstruct full-resolution fused image).

**Contract**:
```kotlin
object LaplacianPyramidFuser {
    data class PyramidConfig(
        val levels: Int = 4,
        val gaussianKernelSigma: Float = 1.0f
    )

    fun fuse(
        frames: List<RGBFrame>,
        alignments: List<FrameAlignment>,
        sharpnessMaps: List<SharpnessMap>,
        referenceIndex: Int,
        breathingCompensation: BreathingCompensationResult,
        config: PyramidConfig = PyramidConfig()
    ): RGBFrame
}
```

**Testability**: Pure function. Test with:
- Two identical frames → verify fused output equals input
- One all-sharp frame + one all-blurred frame → verify fused output matches sharp frame
- Half-sharp/half-blurred complementary frames → verify fused output is sharp everywhere (seam-free)
- Three frames with different sharp regions → verify no color shift at blend boundaries

### Stage 6: Fusion Confidence Gate

**Purpose**: Detect fusion artifacts (ghosting, double edges, unnatural flat regions) post-fusion. This is a safety net — even when alignment passed the confidence check, fusion can produce artifacts due to parallax, subject motion between frames, or lighting changes.

**Algorithm**:
1. **Edge consistency check**:
   a. Run Canny edge detection on the fused image and on each source frame.
   b. For each edge pixel in the fused image, check if a corresponding edge exists in at least one source frame within a 2px radius.
   c. `edgeConsistencyScore = matched_edge_pixels / total_fused_edge_pixels` (0–1).
   d. A low score means the fusion created edges that don't exist in any source (ghost artifact).
2. **Ghost detection**:
   a. For edge pixels in the fused image that have NO corresponding source edge, check the local neighborhood (5×5) for pairs of parallel/nearby edges in source frames.
   b. If found, this is a "double-edge" ghost signature.
   c. `ghostRejectionApplied = count(double_edge_regions) > 0`.
3. **Artifact check**:
   a. Identify "flat" regions in the fused image (Laplacian variance < 0.01 in 16×16 blocks).
   b. For each flat region, compute per-channel variance of pixel values.
   c. High variance in a flat region indicates fusion noise/artifacts.
   d. `artifactScore = 1.0 - (artifact_free_regions / total_flat_regions)` → 0 = clean, 1 = severe artifacts.
4. Composite fusion confidence:
   `fusionConfidence = 0.4 * edgeConsistencyScore + 0.3 * (1.0 - artifactScore) + 0.3 * (if (ghostRejectionApplied) 0.3 else 1.0)`
5. Gate: if `fusionConfidence < FUSION_THRESHOLD (0.5)` → BEST_FRAME fallback.

**Contract**:
```kotlin
data class FusionConfidenceResult(
    val edgeConsistencyScore: Float,  // 0–1, higher = better
    val artifactScore: Float,         // 0–1, lower = better
    val ghostRejectionApplied: Boolean,
    val confidence: Float,            // composite 0–1
    val diagnostics: List<String>
)

object FusionConfidenceEvaluator {
    fun evaluate(
        fused: RGBFrame,
        sources: List<RGBFrame>,
        alignments: List<FrameAlignment>
    ): FusionConfidenceResult
}
```

**Testability**: Pure function. Test with:
- Fused = source → verify confidence ≈ 1.0, edgeConsistencyScore ≈ 1.0
- Fused with synthetic double edges → verify ghostRejectionApplied = true, confidence < 0.5
- Fused with random noise in flat regions → verify artifactScore > 0.5, confidence reduced
- Fused from misaligned frames → verify edgeConsistencyScore < 0.7

### Stage 7: Output Selection Decision Tree

**Purpose**: Make the final decision on what to save, based on all accumulated confidence scores. BEST_FRAME is a first-class successful outcome, not an error.

```
                    ┌─────────────────────┐
                    │ alignmentConfidence │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
         < ALIGNMENT      [0.6, 0.8)       ≥ 0.8
         _THRESHOLD (0.6)                   │
              │                │                │
              ▼                ▼                │
         BEST_FRAME      ┌──────────┐          │
                         │ fusion   │          │
                         │ confidence│          │
                         └─────┬────┘          │
                               │                │
                    ┌──────────┼──────────┐    │
                    ▼          ▼          ▼    │
               < FUSION   [0.5, 0.7)   ≥ 0.7  │
               _THRESHOLD    │           │     │
               (0.5)         │           │     │
                    │         │           │     │
                    ▼         ▼           ▼     ▼
               BEST_FRAME  ┌──────────────┐   │
                           │ breathing    │   │
                           │ severity     │   │
                           └──────┬───────┘   │
                                  │            │
                         ┌────────┼────────┐   │
                         ▼        ▼        ▼   │
                       > 0.3   ≤ 0.3      0    │
                         │        │        │   │
                         ▼        ▼        ▼   ▼
                      DEGRADED  FUSED    FUSED
```

**Selection rules in code**:
1. If `alignmentConfidence < ALIGNMENT_THRESHOLD` (0.6) → output = sharpest single frame, resultState = `BEST_FRAME`.
2. If `fusionConfidence < FUSION_THRESHOLD` (0.5) → output = sharpest single frame, resultState = `BEST_FRAME`.
3. If `breathingSeverity > BREATHING_SEVERITY_THRESHOLD` (0.3) → output = fused, resultState = `DEGRADED` (fusion succeeded but breathing may cause edge artifacts).
4. Otherwise → output = fused, resultState = `FUSED`.

**Sharpest frame selection** (for BEST_FRAME fallback):
- Use `perFrameSharpnessScores` from Stage 4.
- Select the frame with the highest `meanSharpness`.
- This is already computed and cached; no additional work needed.

**Key design principle**: BEST_FRAME is NOT an error. It means "the algorithm determined that fusion would produce worse output than a single frame." This is an honest answer to the user: the saved image is as good as the system can produce under the conditions.

### Fallback Decision Tree Contract

```kotlin
object FullClearOutputSelector {
    data class OutputDecision(
        val resultState: FullClearResultState,
        val outputFrameIndex: Int,       // which frame to save (for BEST_FRAME)
        val fusedImage: RGBFrame?,       // non-null for FUSED/DEGRADED
        val decisionPath: List<String>,  // decision trace for diagnostics
        val report: FullClearFusionReport
    )

    fun select(
        route: FullClearRoute,
        frames: List<RGBFrame>,
        alignmentResult: AlignmentConfidenceResult,
        breathingResult: BreathingCompensationResult,
        sharpnessMaps: List<SharpnessMap>,
        fusionConfidence: FusionConfidenceResult?,
        fusedImage: RGBFrame?,
        sessionId: String
    ): OutputDecision
}
```

### Per-Stage Degradation Classification

Each stage can independently classify what went wrong, providing structured diagnostics for later QA:

| Stage | Degradation Tag | Meaning | User-Visible? |
|---|---|---|---|
| Alignment | `alignment-low-inliers` | Few feature matches between frames | No (internal) |
| Alignment | `alignment-low-ncc` | Pixel-level alignment poor despite feature matches | No (internal) |
| Alignment | `alignment-rotation` | Inter-frame rotation exceeds homography limits | No (internal) |
| Breathing | `breathing-mild` | Scale deviation 5-10%, mild correction applied | No (internal) |
| Breathing | `breathing-severe` | Scale deviation > 15%, large crop applied | Could affect composition |
| Sharpness | `frame-blurred` | One or more frames below sharpness threshold | No (internal, but degrades fusion) |
| Sharpness | `all-frames-blurred` | All frames below threshold (camera shake) | User should retry |
| Fusion | `fusion-ghost` | Double-edge artifact detected post-fusion | Yes, causes visible artifacts |
| Fusion | `fusion-artifact` | Noise detected in flat regions | Yes, may be visible |
| Fusion | `fusion-low-edge-consistency` | Fused edges not present in any source | Yes, hallucinated detail |
| General | `bracket-abandoned` | Fusion not attempted (alignment failed early) | No (result is best-frame) |
| General | `best-frame-selected` | Sharpest single frame saved instead of fusion | Shown in UI as guidance |

### Data Contracts Summary

All algorithm stages use the following intermediate data structures, which flow through the pipeline without I/O:

```kotlin
// Stage 0 output
data class NormalizedFrameSet(
    val frames: List<RGBFrame>,
    val referenceIndex: Int,
    val perFrameGains: List<FloatArray>
)

// Stage 1 output
data class FrameAlignment(
    val frameIndex: Int,
    val homography: FloatArray,
    val inlierRatio: Float,
    val matchCount: Int
)

// Stage 2 output
data class AlignmentConfidenceResult(
    val confidence: Float,
    val perFrameNccScores: List<Float>,
    val perFrameInlierRatios: List<Float>,
    val passed: Boolean
)

// Stage 3 output
data class BreathingCompensationResult(
    val scaleDeviations: List<Float>,
    val correctedOverlapRatio: Float,
    val severity: Float,
    val cropRect: Rect?
)

// Stage 4 output
data class SharpnessMap(
    val frameIndex: Int,
    val blockScores: FloatArray,
    val meanSharpness: Float,
    val perPixelWeights: FloatArray?
)

// Stage 5 output: RGBFrame (fused image)

// Stage 6 output
data class FusionConfidenceResult(
    val edgeConsistencyScore: Float,
    val artifactScore: Float,
    val ghostRejectionApplied: Boolean,
    val confidence: Float
)

// Stage 7 output: OutputDecision (see above)
```

### Algorithm Limitations Documented

These are documented so future implementors know what the algorithm does NOT handle:

1. **No parallax handling**: Global homography assumes planar scenes or distant backgrounds. Close subjects with significant depth variation will produce local misalignment that the NCC grid may detect (→ fallback) but cannot correct. Full parallax handling requires dense optical flow or depth-aware warping, which is out of scope for V2.

2. **No subject motion compensation**: If the subject or background elements move between bracket frames (wind-blown leaves, walking people), the fusion will create ghost artifacts. The ghost detection in Stage 6 may catch this, but there is no motion-segmentation or inpainting fallback.

3. **No HDR fusion**: Exposure normalization matches global brightness but does not recover clipped highlights or crushed shadows. If bracket frames have different exposures (AE varies with focus point), clipped regions remain clipped.

4. **No semantic understanding**: The algorithm fuses based on low-level features (sharpness, edges). It does not know that a face should come from one frame and the background from another. Sharpness-based fusion usually handles this correctly for focus stacks, but it can fail for scenes where sharpness is ambiguous (repetitive textures, flat colored surfaces).

5. **ORB feature fragility**: ORB is fast but not as robust as SIFT for large viewpoint changes. If rotation between frames exceeds ~15°, ORB matching may fail. This is unlikely for focus brackets (frames are captured < 500ms apart) but possible with lens switching.

6. **Pyramid level count assumes ~12MP input**: With 4 pyramid levels and 12MP input, the coarsest level is ~0.75MP (adequate for low-frequency blending). Lower-resolution input may need fewer levels.

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

These are all set by the mode controller in response to session events and scene assessment.

---

## Android/CameraX/Camera2 Constraints

### Constraint 1: Focus Distance Is Not Always Writable

**Impact**: `FOCUS_BRACKET` route availability depends on `ManualControlCapabilityMatrix.focusDistance == APPLY`.

**Detection**: Write a test value via `Camera2Interop.Extender<ImageCapture.Builder>.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, value)`, capture a frame, read back the EXIF focus distance. If it doesn't match, mark as `DEGRADED_SINGLE_FRAME`.

**Fallback**: Route to `DEEP_DOF_LENS` if ultra-wide is available, otherwise `BEST_FRAME_ONLY`.

**Test strategy**: Unit test `FullClearDeviceCapabilities.from()` with a `DeviceCapabilities` fixture where `focusDistance == SAVED_ONLY` → verify `focusDistanceWritable == false` → verify route selector returns non-bracket route.

### Constraint 2: Per-Frame Focus Latency

**Impact**: Focus bracket frame count and inter-frame delay depend on how fast the lens can change focus.

**Detection (real device only)**: Time a 3-frame bracket (near → mid → far) end-to-end. Profile per-frame latency. If > 500ms per frame, reduce bracket to 2 frames (degraded-rebind path per product definition).

**Gating**: `maxBracketFrames` in `FullClearDeviceCapabilities` is populated by real-device profiling. Default conservative value is 3. Tests inject known values.

**Test strategy**: Unit test with `focusDistancePerFrameLatencyMs = 100` → verify 5-frame plan. With `focusDistancePerFrameLatencyMs = 800` → verify degraded 2-frame plan.

### Constraint 3: Lens Switching Disrupts Preview

**Impact**: `LENS_PAIR_BRACKET` requires switching lenses mid-bracket, which causes a preview gap in CameraX. This route is marked experimental/degraded.

**Detection (real device only)**: Time a lens-switch-and-capture cycle. If > 1s, route is not recommended for handheld.

**Gating**: `LENS_PAIR_BRACKET` is only offered when `DEEP_DOF_LENS` and `FOCUS_BRACKET` are both unavailable, AND the device has both tele and ultra-wide lenses.

**Test strategy**: Unit test with `lensSwitchLatencyMs = 2_000` → verify `LENS_PAIR_BRACKET` confidence < 0.3.

### Constraint 4: Motion During Bracket

**Impact**: Gyro motion during bracket capture invalidates alignment. Fusion must be gated on post-capture alignment confidence, not just pre-capture stability.

**Gating**: Pre-capture gyro check enables/disables the shutter. Post-capture alignment check gates fusion. Two independent gates.

**Test strategy**: Unit test alignment gate: `alignmentConfidence < threshold` → verify `resultState == BEST_FRAME`. Test pre-capture gate: `stability.isStable == false` → verify route confidence reduced.

### Constraint 5: Thermal Throttling Reduces Frame Budget

**Impact**: Existing `ResourceAdmissionPolicy.admitMultiFrame()` already reduces frame count under thermal stress. V2 must respect this.

**Integration**: `FullClearCapturePlan.maxTotalLatencyMs` should be checked against the admitted budget. If the budget cuts frame count, the plan degrades.

**Test strategy**: Unit test with `CameraThermalState.HOT` → verify `admitMultiFrame` reduces effective frame count → verify plan builder reduces bracket steps.

### Constraint 6: No RAW/DNG Fusion

**Impact**: V2 outputs JPEG only. RAW fusion is a non-goal (per product definition).

**Gating**: `CaptureStrategy.MultiFrame` with `postProcessSpec` always produces JPEG output. The existing pipeline enforces this.

---

## Capability Matrix Implementation

Mapping product definition capabilities to code contracts:

| Product Capability | Code Contract | Detection |
|---|---|---|
| Deep-DOF Lens Path | `FullClearRoute.DEEP_DOF_LENS` | `FullClearDeviceCapabilities.hasUltraWideLens` && `ZoomRatioCapability.lensNodeMap` |
| Focus Bracket Path | `FullClearRoute.FOCUS_BRACKET` | `ManualControlCapabilityMatrix.focusDistance == APPLY` |
| Lens-Aware Routing | `FullClearRoute.LENS_PAIR_BRACKET` | `lensNodeMap` size > 1 |
| Alignment Confidence | `FullClearFusionReport.alignmentConfidence` | Computed post-capture in `FullClearFusionProcessor` |
| Fusion Confidence | `FullClearFusionReport.fusionConfidence` | Computed post-fusion in `FullClearFusionProcessor` |
| Saved Diagnostics | `FullClearFusionReport.toPipelineNotes()` | Always written; flows through `ShotResult.pipelineNotes` |

---

## Implementation Risks

### Risk 1: Per-Frame Focus Override on CameraX

**Severity**: Medium
**Description**: The existing `DeviceShotRequest` model assumes all frames in a multi-frame shot use the same capture parameters. Adding `perFrameFocusOverrides` requires the app-layer CameraX adapter to reconfigure `ImageCapture` between frames, which may not be directly supported by CameraX's builder pattern.

**Mitigation**: If CameraX per-frame reconfiguration proves unreliable, fall back to Camera2 interop mode: create a new `CaptureRequest` per frame with different `LENS_FOCUS_DISTANCE`, submitted through `Camera2Interop.Extender`. This is a well-known pattern.

**Verification**: Unit test `DeviceShotRequestTranslator` with `perFrameFocusOverrides` → verify correct frame-indexed focus values. Integration test on real device with Camera2 interop.

### Risk 2: Alignment Failure on Handheld

**Severity**: High
**Description**: Handheld bracket frames have inter-frame motion from hand shake, subject movement, and lens breathing. Alignment may fail more often than it succeeds.

**Mitigation**: The architecture gates fusion on alignment confidence. If alignment fails, the system falls back to best-frame. The product promise says "V2 does not guarantee both near and far planes are equally sharp." This is honest in code: `resultState == BEST_FRAME` is a valid and expected outcome.

**Verification**: The alignment confidence gate is a pure function on frame pairs → fully unit-testable. Real-device pass rate must be validated in package 05.

### Risk 3: Lens Breathing Changes Framing

**Severity**: Medium
**Description**: Focus changes on some lenses cause noticeable FOV shift (breathing), which confuses alignment and produces edge artifacts in fusion.

**Mitigation**: Lens breathing compensation in the fusion processor (crop/scale each frame to match the reference frame's FOV). If breathing is too severe, degrade to best-frame.

**Verification**: Unit test breathing compensation with synthetic FOV-shifted frame pairs.

### Risk 4: Ultra-Wide Minimum Focus Distance Unknown

**Severity**: Low
**Description**: `LENS_INFO_MINIMUM_FOCUS_DISTANCE` may return 0 (unknown) on some devices. The route selector can't determine if the subject is within the ultra-wide's focus range.

**Mitigation**: When minimum focus distance is unknown, assume the ultra-wide can focus close (most modern ultra-wides can). The worst case is a blurry near subject, which the user will see and can retry. The fallback is `BEST_FRAME_ONLY`.

### Risk 5: No Existing V1 FullClear Mode Plugin

**Severity**: Low
**Description**: `ModeId.FULL_CLEAR` exists in the enum but there is no `feature/mode-full-clear` module implementing a `FullClearModePlugin`. V2 must create the entire mode plugin from scratch.

**Mitigation**: Follow the `NightModePlugin` pattern exactly. The code structure is well-established. The V2 mode is simpler than Night: it has fewer profiles, no flash logic, no live photo, no filter rendering.

---

## Test Strategy

### Unit Tests: Architecture Contracts (No Device Required)

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
   - All routes → verify `degradationReasons` are populated

3. **`buildFullClearCapturePlan` tests**:
   - `DEEP_DOF_LENS` → 0 bracket steps, single frame
   - `FOCUS_BRACKET` with 3 frames → near, mid, far focus steps
   - `FOCUS_BRACKET` with 5 frames → 5 steps with correct diopter interpolation
   - `UNSUPPORTED` → 0 bracket steps, fallback `REPORT_ERROR`

4. **`FullClearFusionReport` tests**:
   - `toPipelineNotes()` produces correctly formatted tags
   - All per-stage fields present regardless of result state
   - `BEST_FRAME` result still records route, alignment diagnostics, and degradation reasons
   - `FUSED` result has all fusion-stage fields populated (edge consistency, ghost rejection)
   - `DEGRADED` result has breathing severity > 0.3 and fusion confidence in acceptable range

5. **`FullClearModeController` tests** (using test ModeContext):
   - Shutter press with `DEEP_DOF_LENS` route → emits `CaptureStrategy.SingleFrame`
   - Shutter press with `FOCUS_BRACKET` route → emits `CaptureStrategy.MultiFrame` with correct frameCount
   - `isSupported()` returns true when `supportsStillCapture` is true
   - User guidance headline updates on route change
   - `isShutterEnabled` is false when route is `UNSUPPORTED`

### Unit Tests: Algorithm Stages (No Device Required, Synthetic Frame Pairs)

Each algorithm stage is a pure function → testable with programmatically generated frame pairs. No real camera frames needed.

#### Stage 0: ExposureNormalizer

| Test Case | Input | Expected |
|---|---|---|
| Identical frames | 3 copies of same image | gains = [1.0, 1.0, 1.0], ref = sharpest (all tie) |
| Dark frame + bright frame | Same content, ×0.5 and ×1.5 brightness | gains ≈ [2.0, 0.67] (clamped to [0.5, 2.0]) |
| Extreme brightness gap | ×0.2 and ×3.0 | gains clamped to [0.5, 2.0] |
| Select reference frame | Frame A (Gaussian blur σ=3), Frame B (sharp) | referenceIndex = 1 (Frame B sharper) |
| Color cast correction | Frame A neutral, Frame B warm (R gain 1.3) | R gain applied to warm frame |

#### Stage 1: FrameAligner

| Test Case | Input | Expected |
|---|---|---|
| Identity | Two identical frames | homography ≈ identity, inlierRatio > 0.9 |
| Translation | 10px right, 5px down | homography encodes correct tx, ty |
| Small rotation | 2° rotation | homography encodes correct angle |
| Scale change | 5% zoom | homography scale component ≈ 0.95 or 1.05 |
| Large rotation | 30° rotation | inlierRatio < 0.3, returns null (ORB limit) |
| Random noise images | Two unrelated images | inlierRatio < 0.3, returns null |

#### Stage 2: AlignmentConfidenceEvaluator

| Test Case | Input | Expected |
|---|---|---|
| Perfect alignment | inlierRatio = 0.95, NCC ≈ 1.0 | confidence > 0.9, passed = true |
| Good alignment | inlierRatio = 0.70, NCC ≈ 0.85 | confidence ≈ 0.6, passed = true (borderline) |
| Poor NCC | inlierRatio = 0.80, NCC = 0.3 (parallax) | confidence < 0.5, passed = false |
| Poor inliers | inlierRatio = 0.30, NCC = 0.8 | confidence < 0.4, passed = false |
| Mixed frames | Frame 1 OK, Frame 2 poor | confidence weighted down by poor frame |
| Single frame pair | One alignment only | confidence based on single pair |

#### Stage 3: BreathingCompensator

| Test Case | Input | Expected |
|---|---|---|
| No breathing | scale = 1.0 on all alignments | severity = 0.0, no crop applied |
| Mild breathing | scale = 1.06 (6% deviation) | severity > 0, < 0.3, crop applied |
| Moderate breathing | scale = 1.12 (12% deviation) | severity ≈ 0.5 |
| Severe breathing | scale = 1.20 (20% deviation) | severity > 0.8, overlap ratio < 0.8 |
| Mixed | Frame 1 scale = 1.02, Frame 2 scale = 1.15 | severity computed from worst frame |

#### Stage 4: SharpnessMapGenerator

| Test Case | Input | Expected |
|---|---|---|
| All sharp | High-frequency pattern (checkerboard) | meanSharpness > 0.5 |
| All blurred | Gaussian blur σ=5 | meanSharpness < 0.1 |
| Half sharp, half blurred | Left half sharp, right half blurred | block scores high on left, low on right |
| Different sharp regions | Frame A sharp left, Frame B sharp right | per-frame maps correctly localize sharpness |
| Empty/uniform image | Solid gray frame | meanSharpness ≈ 0.0 (no edges) |

#### Stage 5: LaplacianPyramidFuser

| Test Case | Input | Expected |
|---|---|---|
| Identical frames | 2 copies of same image | output = input (no artifacts) |
| Best frame all sharp | Frame A sharp, Frame B blurred | output ≈ Frame A |
| Complementary sharpness | Frame A sharp left+blur right; Frame B blur left+sharp right | output sharp everywhere, no seam visible |
| Three frames | Sharp near, mid, far each sharp in different region | output sharp in all regions |
| Color fidelity | Different color balance across frames | no color shift at blend boundaries |

#### Stage 6: FusionConfidenceEvaluator

| Test Case | Input | Expected |
|---|---|---|
| Perfect fusion | fused = any source frame | confidence > 0.9, edgeConsistencyScore ≈ 1.0 |
| Ghost present | fused = source + offset copy blended 50% | ghostRejectionApplied = true, confidence < 0.5 |
| Artifacts | fused = source + random noise (σ=0.1) | artifactScore > 0.3, confidence reduced |
| Missing edges | fused = blurred version of source | edgeConsistencyScore < 0.7 |
| Natural fusion | fused from well-aligned frames | confidence > 0.7 |

#### Stage 7: FullClearOutputSelector

| Test Case | Input | Expected |
|---|---|---|
| Both gates pass, no breathing | alignmentConf > 0.8, fusionConf > 0.7, breathingSev = 0 | resultState = FUSED |
| Alignment fail | alignmentConf = 0.4 | resultState = BEST_FRAME, sharpest frame selected |
| Fusion fail | alignmentConf = 0.8, fusionConf = 0.3 | resultState = BEST_FRAME |
| Degraded path | alignmentConf = 0.7, fusionConf = 0.6, breathingSev = 0.5 | resultState = DEGRADED |
| Borderline pass | alignmentConf = 0.61, fusionConf = 0.51, breathingSev = 0.0 | resultState = FUSED |
| Decision trace recorded | Any case | decisionPath contains all gate evaluations |

### Synthetic Test Data Generation

For implementation, synthetic frame pairs can be generated with:

1. **Geometric transforms**: OpenCV `warpPerspective` with known homography matrices for translation/rotation/scale testing.
2. **Sharpness variation**: Gaussian blur with controlled σ applied to regions of frames.
3. **Exposure variation**: Per-channel gain multiplication.
4. **Breathing simulation**: Scale + crop with known scale factors.
5. **Ghost simulation**: Blend two offset copies of the same image at 50% opacity.
6. **Artifact simulation**: Add Gaussian noise to flat regions of fused output.

All synthetic test images can be generated at implementation time (e.g., 512×512 grayscale or RGB buffers). No external test assets required.

### Real-Device Test Matrix (Deferred to Package 05)

These tests require physical device + controlled scenes. They cannot be automated without hardware.

#### Device Capability Profiling

| Test | Method | Metric | Success Criteria |
|---|---|---|---|
| Per-frame focus latency | Time 5-frame bracket (near→far), measure inter-frame interval | ms per frame | < 500ms/frame for 5-frame bracket |
| Focus distance writability | Set LENS_FOCUS_DISTANCE, capture, read EXIF | Match between set and EXIF | EXIF distance within 10% of set value |
| Lens switch latency | Time tele→wide→tele cycle (10 iterations) | ms per switch | < 1s for acceptable handheld |
| Ultra-wide min focus | Gradual approach to near subject, note where AF fails | diopters | Recorded per device model |
| Gyro noise floor | Record gyro while device on tripod | rad/s RMS | < 0.01 rad/s noise |

#### Algorithm Pass Rate

| Test | Scene | Bracket Count | Metric | Target |
|---|---|---|---|---|
| Tripod bracket | Textured static scene (bookshelf) | 5 frames | fusion pass rate | > 90% FUSED |
| Handheld bracket (steady) | Textured static scene, two-handed grip | 3 frames | fusion pass rate | > 60% FUSED + DEGRADED |
| Handheld bracket (walking) | Outdoors, natural walking | 3 frames | alignment confidence | < 0.3 (expect BEST_FRAME) |
| Breathing lens | Lens known to breathe (e.g., some telephotos) | 3 frames | breathing severity | > 0.3 (expect DEGRADED) |
| Subject motion | Wind-blown leaves in bracket frames | 3 frames | ghost detection | ghostRejectionApplied = true |
| Low light | Indoor dim lighting, ISO > 800 | 3 frames | sharpness scores | meanSharpness < 0.3 (noise dominates) |
| Macro near-far | Close subject (10cm) + distant background (10m) | 5 frames | visual quality | Perceptual A/B vs single frame |

#### End-to-End Pipeline

| Test | Method | Success Criteria |
|---|---|---|
| Shutter-to-save latency | Measure from tap to ShotResult callback | < 3s for 3-frame bracket (per product def) |
| Thermal degradation | Run 20 brackets in 2 minutes | Frame count degrades gracefully, no crash |
| Memory | Profile heap during 5-frame fusion at 12MP | < 200MB peak (typical Android budget) |
| Diagnostics completeness | Check pipelineNotes after each shot | All `fc:*` tags present, no null/empty required fields |
| Fallback correctness | Force alignment failure (shake during bracket) | BEST_FRAME saved, degradation reasons logged |

#### Perceptual Quality (Requires Human Judgment)

| Test | Scene | Comparison | Judgment |
|---|---|---|---|
| A/B vs single frame | Close subject + distant background | FUSED vs best single frame from bracket | Is FUSED better? (Yes/No/Equal) |
| A/B vs V1 | Same scene | V2 FUSED vs V1 best frame | Is V2 improvement visible? |
| Edge artifact check | High-contrast edges in scene | FUSED image at 100% zoom | Any visible halos, double edges, or blend seams? |
| Color consistency | Scene with uniform colored surface | Compare fused to source frames at 100% | Any color banding or shift at blend boundaries? |

### What Cannot Be Proven Locally

The following algorithm properties require real-device data and cannot be verified through synthetic tests or design review alone:

1. **Handheld alignment pass rate**: The actual percentage of bracket captures that pass the alignment confidence gate on a specific device model. Synthetic tests verify the gate logic but not the distribution of real inter-frame motion.

2. **Perceptual fusion quality**: Whether fused output "looks better" than the best single frame. This requires human A/B judgment on real scenes with real bracket frames. Synthetic tests can verify no artifacts, but not aesthetic superiority.

3. **Ghost rejection effectiveness on real scenes**: The ghost detection logic can be verified with synthetic double edges, but real ghost artifacts (from subject motion, parallax, lighting changes) have different signatures that may or may not be caught.

4. **Threshold calibration**: The default thresholds (0.6 alignment, 0.5 fusion, 0.3 breathing) are educated estimates based on algorithm design principles. Real-device tuning may shift these significantly.

5. **ORB feature match quality on smartphone sensors**: Feature detection and matching performance depends on sensor noise characteristics, lens sharpness, and ISP processing (denoising, sharpening) applied before the algorithm sees the frames.

6. **Lens breathing characterization**: Breathing severity depends on the specific lens module. Some lenses breathe more than others. The breathing compensation algorithm's effectiveness can only be measured per device model.

7. **Latency budget feasibility**: The total end-to-end latency (focus change + capture + fusion) meeting the < 3s target depends on real device performance, which synthetic tests cannot measure.

8. **Memory footprint on real frames**: Peak memory during pyramid fusion depends on frame resolution, bit depth, and the number of intermediate buffers. The 200MB target is an estimate.

### Verification Command

```bash
rtk grep -n "alignment\|confidence\|fallback\|FullClearFusionReport\|best-frame\|lens-breathing\|exposure.normalization\|sharpness.map\|laplacian.pyramid\|fusion.confidence\|ghost.rejection\|output.selection\|synthetic.test\|real-device\|degration\|FUSED\|BEST_FRAME\|DEGRADED" docs/plans/full-clear-mode-v2-research-orchestration/v2-implementation-design.md docs/plans/full-clear-mode-v2-research-orchestration/v2-roadmap.md
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
