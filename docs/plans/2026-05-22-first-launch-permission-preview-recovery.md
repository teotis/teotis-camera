# First Launch Permission Preview Recovery Plan

> **For agentic workers:** Use this as a self-contained implementation handoff. Run shell commands through `rtk`. Keep the fix inside Stage 7 stability governance; do not start a new product stage.

**Goal:** Fix the real-device issue where first launch after camera permission grant does not show preview, while the second app entry works.

**Recommended approach:** Make preview binding idempotent across permission-dialog lifecycle churn by ensuring `SessionEffect.BindPreview` is never silently dropped when the app-side preview host is temporarily absent.

---

## User-Observed Evidence

- First fresh entry asks for permission.
- After granting permission, preview does not come up.
- Closing/re-entering the app makes preview work.
- The screenshot dev log shows repeated `permissions.updated -> camera=false,mic=false`, `session.boot.skipped`, `preview.host.detached -> Activity moved to background`, later `preview.host.attached`, and only at the end `permissions.updated -> camera=true,mic=true`.

This strongly suggests a timing window around permission UI, Activity `onStop/onStart`, session host state, and coordinator attachment.

## Current Code Facts

- `MainActivity.onStart()` currently does:
  - `container.cameraCoordinator.attachPreviewHost(this, previewView)`
  - `syncPermissionState()`
  - `dispatch(SessionIntent.Boot)`
  - `dispatch(SessionIntent.PreviewHostAttached)`
  - `requestCameraPermissionIfNeeded()`
- `DefaultCameraSession.handlePermissionsUpdated()` emits a bind when camera permission transitions from false to true and no pending host recovery exists.
- `DefaultCameraSession.requestPreviewBinding()` only emits when lifecycle is running, permission is granted, and `previewHostAvailable` is true.
- `CameraSessionCoordinator.bindPreview()` currently returns immediately when `lifecycleOwner` or `previewView` is null. That means a valid bind effect can be lost if it arrives while the Activity host was cleared during the permission dialog lifecycle.

## Implementation Scope

Modify:

- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt` only if coordinator queuing is insufficient

Do not modify:

- CameraX adapter internals unless tests prove bind is attempted and CameraX fails.
- Mode plugins.
- Media pipeline.

## Design

### 1. Queue One Pending Preview Bind In Coordinator

Add a small `PendingPreviewBind` data holder inside `CameraSessionCoordinator`:

```kotlin
private data class PendingPreviewBind(
    val modeId: ModeId,
    val deviceGraph: DeviceGraphSpec,
    val reason: String,
    val isRecovery: Boolean
)
```

When `bindPreview()` receives a bind effect but `lifecycleOwner` or `previewView` is null:

- Store or replace `pendingPreviewBind`.
- Do not dispatch `PreviewBindingStarted`.
- Do not report a runtime issue.
- Return.

When `attachPreviewHost()` is called:

- Set `lifecycleOwner` and `previewView`.
- Notify `runtimeIssueMonitor.onPreviewHostAttached()`.
- If `pendingPreviewBind` exists, launch a coroutine and call `bindPreview()` with that pending request, then clear it only after the bind attempt starts.

This keeps the coordinator as a bridge, not a second session kernel: it is only holding an unconsumed effect until the Android host exists again.

### 2. Replace Stale Pending Bind On Newer Session Effect

If multiple bind effects arrive while detached, keep only the latest one. A later mode/lens/graph bind is more authoritative than an old `session boot` bind.

On `unbindPreview(clearHost = true)`, do not blindly erase pending bind unless the unbind corresponds to a newer detach event. The safe default is:

- Clear host references.
- Keep pending bind if it was already queued by a later permission grant.
- Replace it when a newer `BindPreview` effect arrives.

### 3. Add Session Regression For Permission Grant After Dialog Churn

Add or strengthen a `DefaultCameraSessionTest` sequence:

```text
Boot with camera=false.
PreviewHostAttached.
PreviewHostDetached("permission dialog background").
PreviewHostAttached.
PermissionsUpdated(camera=true, mic=true).
Expect one BindPreview with reason "camera permission granted" or recovery reason.
```

The expected session-level outcome is a bind effect. This does not cover the dropped-effect bug by itself, so coordinator tests are required too.

### 4. Add Coordinator Regression For Dropped Bind

Add a `CameraSessionCoordinatorTest`:

```text
Create coordinator without calling attachPreviewHost().
Emit SessionEffect.BindPreview(mode=PHOTO, graph=photoGraph, reason="camera permission granted").
Verify adapter.bindRequests == 0 and coordinator has no runtime error.
Call attachPreviewHost(lifecycleOwner, previewView).
advanceUntilIdle().
Verify adapter.bindRequests == 1 and adapter.boundGraph() == photoGraph.
Verify session received PreviewBindingStarted only after host attach.
```

Also add a replacement test:

```text
Emit pending PHOTO bind while host missing.
Emit pending NIGHT bind while host missing.
Attach host.
Verify only NIGHT graph binds.
```

### 5. MainActivity Follow-Up If Needed

If coordinator queuing does not close the real-device loop, add an app-side nudge in `permissionLauncher`:

- After `syncPermissionState(cameraGranted = true, ...)`, if lifecycle is at least `STARTED`, call `container.cameraCoordinator.attachPreviewHost(this, previewView)` and dispatch `SessionIntent.PreviewHostAttached`.
- Keep this idempotent. Existing `DefaultCameraSession.handlePreviewHostAttached()` already handles duplicate attach with `preview.host.attach.skipped`.

Prefer the coordinator fix first because it handles any future host/effect ordering issue, not only the permission callback.

## Verification

Focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Stage:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Manual real-device smoke:

- Fresh install or clear app data.
- Launch app.
- Grant camera and mic permissions.
- Preview should become active without leaving the app.
- Export dev log. Expected sequence includes `permissions.updated -> camera=true`, `preview.binding.started` or `preview.recovery.started`, and `preview.first.frame`.

## Non-Goals

- Do not change the permission request UX in this fix.
- Do not add a second session owner in `MainActivity`.
- Do not suppress `PreviewStartupRuntimeIssueMonitor`; it remains the safety net if CameraX binds but no first frame arrives.
