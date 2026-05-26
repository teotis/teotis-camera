# ShotGraph Device And Execution Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. Start only after `2026-05-23-shot-graph-planning-source-of-truth.md` has landed.

**Goal:** Migrate device translation and CameraX execution decisions from duplicated `ShotKind` branching toward the canonical `ShotGraph` carried by `ShotPlan`.

**Architecture:** Keep `ShotPlan` as the session/device command envelope. Read capture topology from `plan.graph` wherever graph semantics are available. Use small adapters so existing CameraX capture helpers remain intact while their dispatch decisions become graph-backed.

**Tech Stack:** Kotlin contracts in `core:device`, session/device commands, CameraX adapter tests in `app:testDebugUnitTest`, existing Stage 7 verification.

---

## Dependency

Do not start until package 1 has landed these semantics:

- `ShotPlan` has `graph: ShotGraph`.
- `ShotPlan.toShotGraph()` returns `plan.graph`.
- `CaptureNodeRole.PRIMARY_VIDEO` exists.
- `ModeCaptureStrategyGraphTest` passes with the graph field.

## Files

Modify:

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
- `core/device/src/test/kotlin/com/opencamera/core/device/DefaultDeviceShotRequestTranslatorTest.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- Existing app camera tests if needed:
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRuntimeIssueTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt`

Avoid unless required:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`

Session should continue emitting `SessionEffect.ExecuteShot(plan)`.

## Step 1: Add Graph Query Helpers

Add helper functions near media graph contracts or in `core:device` if they are device-specific.

Suggested pure helpers:

```kotlin
fun ShotGraph.primaryStillNode(): CaptureNode? =
    captureNodes.firstOrNull { it.role == CaptureNodeRole.PRIMARY_STILL }

fun ShotGraph.primaryVideoNode(): CaptureNode? =
    captureNodes.firstOrNull { it.role == CaptureNodeRole.PRIMARY_VIDEO }

fun ShotGraph.temporaryFrameNode(): CaptureNode? =
    captureNodes.firstOrNull { it.role == CaptureNodeRole.TEMPORARY_FRAME }

fun ShotGraph.requiresAlgorithm(type: AlgorithmType): Boolean =
    algorithmNodes.any { it.type == type }
```

Add pure tests if helpers live in `core:media`.

## Step 2: Make Device Translation Graph-Aware

Keep the public interface stable first:

```kotlin
interface DeviceShotRequestTranslator {
    fun translate(plan: ShotPlan): DeviceShotRequest
}
```

Inside `DefaultDeviceShotRequestTranslator.translate(plan)`, use `plan.graph` for graph-owned values:

- Still capture: require or check `plan.graph.primaryStillNode()`.
- Video recording: require or check `plan.graph.primaryVideoNode()`.
- Multi-frame frame count: read `plan.graph.temporaryFrameNode()?.frameCount`, falling back to `plan.request.captureProfile.frameCount.coerceAtLeast(1)` only as compatibility.
- Multi-frame inter-frame timing: prefer `temporaryFrameNode.timingPolicy.interFrameDelayMillis` when nonzero; otherwise keep the existing long-exposure-derived fallback.

Do not move flash, torch, manual params, quality, or resolution out of `ShotRequest` in this package. Those values are still request/envelope semantics, not graph topology.

Add diagnostics:

```text
device:graph=capture-topology
device:graph-node=<node-id>
```

Only add stable, low-noise diagnostics that tests can assert.

## Step 3: Lock Translator Tests

Update `DefaultDeviceShotRequestTranslatorTest`:

- Single-frame translation still produces `CaptureTemplate.STILL_CAPTURE`.
- Video translation now proves a `PRIMARY_VIDEO` graph node exists before translation.
- Multi-frame translation uses graph temp frame count.
- Multi-frame translation preserves existing long-exposure/inter-frame behavior unless graph timing explicitly overrides it.
- Invalid graph/request mismatch should fail fast in a pure unit test, for example a video request without a primary video graph node.

Mismatch behavior should use `check`/`require` with a useful message:

```text
ShotGraph missing PRIMARY_VIDEO node for VIDEO_RECORDING
```

## Step 4: Make CameraX Dispatch Graph-Aware Without Rewriting Helpers

In `CameraXCaptureAdapter.executeShot(plan)`, keep existing helper functions but choose path through graph predicates:

```kotlin
val graph = plan.graph
when {
    graph.primaryVideoNode() != null -> startVideoRecording(plan, deviceRequest, requestedAt)
    graph.temporaryFrameNode() != null -> captureStillImage(plan, deviceRequest, requestedAt)
    graph.primaryStillNode() != null -> captureStillImage(plan, deviceRequest, requestedAt)
    else -> error("ShotGraph has no executable capture node for ${plan.request.shotId}")
}
```

Inside `captureStillImage`, keep the existing `ShotKind` switch temporarily for choosing `captureSinglePhoto`, `captureMultiFrameStillImage`, or `captureLivePhoto`. Do not rewrite all CameraX mechanics in this step.

Add a follow-up note in code or tests that the final migration should move still sub-dispatch to graph roles:

- primary still only -> single photo
- temp frames + primary still + merge algorithm -> multi-frame
- primary still + motion segment + live assemble -> live photo

## Step 5: Add Graph/Request Consistency Guard

Before executing a shot, validate graph and request agree:

- `VIDEO_RECORDING` requires `PRIMARY_VIDEO`.
- `STILL_CAPTURE` requires `PRIMARY_STILL` and no `TEMPORARY_FRAME`.
- `MULTI_FRAME_CAPTURE` requires `PRIMARY_STILL`, `TEMPORARY_FRAME`, and `MULTI_FRAME_MERGE`.
- `LIVE_PHOTO` requires `PRIMARY_STILL`, `MOTION_SEGMENT`, and `LIVE_ASSEMBLE`.

This validator should be pure and tested. Prefer adding it to `core:media` if it does not depend on CameraX.

Do not make optional filter/watermark/thumbnail nodes block capture execution.

## Focused Verification

Run after translator changes:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
```

Run after CameraX adapter changes:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
```

Then run integration slices:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/verify_stage_7_observability.sh
```

Expected: all pass.

## Acceptance

- Device translation reads graph topology for frame/video/still execution facts.
- CameraX adapter dispatch is graph-aware while preserving current helper behavior.
- `ShotKind` remains as compatibility metadata but is no longer the only execution switch.
- Graph/request mismatches fail early in unit tests, not deep inside CameraX.
- No UI, mode plugin, or product behavior changes are introduced.

## Stop Conditions

Stop and report instead of pushing further if:

- The change requires renaming `SessionEffect.ExecuteShot` or `DeviceCommand.ExecuteShot`.
- The change requires broad rewrites of `CameraXCaptureAdapter` unrelated to shot execution.
- Stage 7 verification starts failing in recovery/diagnostics tests unrelated to media planning.
- Real device behavior would be needed to decide the next step.
