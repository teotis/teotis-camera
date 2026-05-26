# Package 02 — Brightness State Arbitration Audit — Evidence

## Package ID

`02-brightness-state-arbitration-audit`

## Worktree / Branch

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/brightness-audit-02`
- Branch: `worktree-brightness-audit-02` (based on main HEAD `f5367d3`)

## Git Status (snapshot)

```
On branch worktree-brightness-audit-02
nothing to commit, working tree clean
```

## Commands Run

```bash
# Symbol search across app and core
grep -rn "ApplyPreviewBrightness|PreviewBrightness|brightnessSlider|setOnSeekBarChangeListener|latestPreviewBrightnessCommand" app core
# Result: mapped all brightness-related call sites across 6 files

# Find render entry points
grep -n "renderQuickBubble|brightnessSlider|brightnessRenderModel" app/src/main/java/com/opencamera/app/MainActivity.kt
# Result: renderQuickBubble called from render() at line 322 on every state emission

# Find all brightness rendering references
grep -rn "renderQuickBubble|brightnessSlider|brightnessRenderModel" app/src/main/java/com/opencamera/app/
# Result: 14 references across MainActivity, MainActivityActionBinder, CockpitSurfaceRenderer, SessionCockpitRenderModel, MainActivityViews

# Find DeviceContracts definitions
grep -rn "class PreviewBrightnessRequest|class PreviewBrightnessResult" core/
# Result: definitions in core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt

# Test run attempted:
./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionCockpitRenderModelTest \
  --tests com.opencamera.app.SessionUiRenderModelTest
# Result: BUILD FAILED — pre-existing compilation error in PostprocessOuterGuardTest.kt (unrelated to brightness)
```

## Code References (with line numbers)

| File | Lines | Role |
|---|---|---|
| `app/.../MainActivityActionBinder.kt` | 130-140 | SeekBar listener: dispatches `SessionIntent.ApplyPreviewBrightness` on `onProgressChanged(fromUser=true)` |
| `app/.../CockpitSurfaceRenderer.kt` | 107-131 | `renderQuickBubble()`: writes `brightnessSlider.progress` unconditionally on every render |
| `app/.../SessionCockpitRenderModel.kt` | 231-269 | `brightnessRenderModel()`: returns `requestedSteps` when feedback status is `REQUESTED`, else `previewBrightnessSteps` |
| `core/session/.../DefaultCameraSession.kt` | 1067-1156 | `handleApplyPreviewBrightness()`: generates requestId, sets `REQUESTED` feedback, emits effect. `handlePreviewBrightnessApplied()`: filters stale ack by requestId |
| `app/.../camera/CameraSessionCoordinator.kt` | 101-113 | `handleEffect` → `latestPreviewBrightnessCommand()`: **duplicate dispatch** — launches `scope.launch { dispatch() }` AND returns command for outer `handleEffect` to also dispatch |
| `app/.../camera/CameraXCaptureAdapter.kt` | 1496-1553 | `applyPreviewBrightness()`: `setExposureCompensationIndex().await()` — suspend call, serializes on CameraX main thread |
| `core/session/.../SessionContracts.kt` | 127-141 | `PreviewBrightnessFeedback` / `PreviewBrightnessFeedbackStatus` enum |
| `core/device/.../DeviceContracts.kt` | 271-300 | `PreviewBrightnessRequest`, `PreviewBrightnessResult`, `PreviewBrightnessResultStatus` |

## Event Timeline — Fast Brightness Drag

```
T0  User touches SeekBar (onStartTrackingTouch — currently no-op)
T1  User drags → onProgressChanged(fromUser=true)
    → SessionIntent.ApplyPreviewBrightness(targetSteps=+2)
T2  DefaultCameraSession.handleApplyPreviewBrightness(+2)
    → requestId="brightness-1001"
    → state: previewBrightnessFeedback = {requestId="1001", requestedSteps=+2, status=REQUESTED}
    → emit SessionEffect.ApplyPreviewBrightness({requestId="1001", steps=+2})
T3  CameraSessionCoordinator.handleEffect(ApplyPreviewBrightness)
    → latestPreviewBrightnessCommand(effect)
      → previewBrightnessJob?.cancel()  // cancel previous job (if any)
      → previewBrightnessJob = scope.launch {
          cameraAdapter.dispatch(DeviceCommand.ApplyPreviewBrightness({1001, +2}))
        }  // dispatch #1
      → return DeviceCommand.ApplyPreviewBrightness({1001, +2})  // returned to handleEffect
    → cameraAdapter.dispatch(DeviceCommand.ApplyPreviewBrightness({1001, +2}))  // dispatch #2 (DUPLICATE!)
T4  CameraXCaptureAdapter.applyPreviewBrightness({1001, +2})
    → setExposureCompensationIndex(+2).await()  // suspend, takes ~50-200ms
T5  User drags further → onProgressChanged(+3)
    → SessionIntent.ApplyPreviewBrightness(+3)
T6  DefaultCameraSession: requestId="brightness-1002"
    → state: feedback = {requestId="1002", requestedSteps=+3, status=REQUESTED}
    → emit effect
T7  CameraSessionCoordinator: cancel job from T3, launch new + return duplicate
    → CameraX receives TWO commands for +3
T8  CameraX returns APPLIED for requestId="1001" (from T4 duplicate #2)
    → DeviceEvent.PreviewBrightnessApplied({requestId="1001", steps=+2, APPLIED})
T9  CameraSessionCoordinator.handleDeviceEvent → SessionIntent.PreviewBrightnessApplied
T10 DefaultCameraSession.handlePreviewBrightnessApplied:
    → currentFeedback.requestId="1002" != result.requestId="1001" → STALE, filtered ✓
T11 Next render cycle: brightnessRenderModel sees feedback.status=REQUESTED → shows requestedSteps=+3 ✓
T12 CameraX returns APPLIED for requestId="1002" (the real one)
    → handlePreviewBrightnessApplied: requestId matches
    → state: previewBrightnessSteps=+3, feedback.status=APPLIED
T13 Next render: brightnessRenderModel sees status=APPLIED → uses previewBrightnessSteps=+3 ✓
    BUT if user has already dragged to +4 at T13, render overwrites slider.progress to +3
    → VISUAL SNAPBACK to +3 while user is at +4
```

## Root Cause Analysis — Ranked

### 1. CRITICAL: Duplicate Dispatch in `CameraSessionCoordinator.latestPreviewBrightnessCommand()`

**File:** `app/.../camera/CameraSessionCoordinator.kt:107-113`

```kotlin
private fun latestPreviewBrightnessCommand(effect: SessionEffect.ApplyPreviewBrightness): DeviceCommand {
    previewBrightnessJob?.cancel()
    previewBrightnessJob = scope.launch {
        cameraAdapter.dispatch(DeviceCommand.ApplyPreviewBrightness(effect.request))  // dispatch #1
    }
    return DeviceCommand.ApplyPreviewBrightness(effect.request)  // dispatch #2
}
```

This method is called from `handleEffect()` which then also dispatches the returned command:

```kotlin
is SessionEffect.ApplyPreviewBrightness -> cameraAdapter.dispatch(
    latestPreviewBrightnessCommand(effect)  // returned command dispatched again!
)
```

**Impact:** Every brightness request hits CameraX **twice**. The `previewBrightnessJob?.cancel()` cancels the launched coroutine, but if the CameraX `dispatch` has already entered `applyPreviewBrightness()` and is awaiting `setExposureCompensationIndex().await()`, cancellation may not prevent the device call. The outer dispatch also runs independently. This means:
- Two `setExposureCompensationIndex` calls for every single drag step
- Two `DeviceEvent.PreviewBrightnessApplied` events per request
- The second event carries the same requestId but may arrive after the next request has already been dispatched, causing unnecessary stale-filter churn

**Likely root cause of visible rebound:** The duplicate dispatch doubles CameraX load. If CameraX serializes these on its main thread, the second call may complete after the user has already moved to a new position, and its APPLIED result updates `previewBrightnessSteps` to the old value.

### 2. HIGH: Missing UI Drag Latch — Render Overwrites Active Drag

**File:** `app/.../CockpitSurfaceRenderer.kt:124`

```kotlin
quickPanel.brightnessSlider.progress = brightness.steps - brightness.minSteps
```

Every call to `renderQuickBubble()` writes `brightnessSlider.progress` unconditionally. The render is triggered by every `SessionState` emission (see `MainActivity.kt:322` → `render()`). There is **no drag-latch mechanism**:
- `onStartTrackingTouch()` at `MainActivityActionBinder.kt:138` is a no-op
- `onStopTrackingTouch()` at `MainActivityActionBinder.kt:139` is a no-op
- No flag suppresses render-model writes to the SeekBar during active pointer contact

**Impact:** Even with REQUESTED priority in the render model, when an APPLIED result comes back and updates `previewBrightnessSteps`, the next render overwrites the slider position. If the user has moved further since the request, the thumb snaps back.

### 3. MEDIUM: CameraX `setExposureCompensationIndex().await()` Serialization

**File:** `app/.../camera/CameraXCaptureAdapter.kt:1519-1522`

```kotlin
camera.cameraControl
    .setExposureCompensationIndex(request.exposureCompensationSteps)
    .await()
```

This is a suspend call that blocks until CameraX applies the exposure change (~50-200ms per call). Rapid drag steps generate a queue of these calls. Combined with the duplicate dispatch, the queue depth doubles. CameraX may batch or drop intermediate values, but the result events still fire for each, creating stale-ack churn.

### 4. LOW: Feedback Status Transition Creates Brief Visual Gap

When `APPLIED` result arrives:
- `handlePreviewBrightnessApplied()` sets `feedback.status = APPLIED` and `previewBrightnessSteps = appliedSteps`
- On next render, `brightnessRenderModel()` returns `state.presentation.previewBrightnessSteps` (not `requestedSteps`)
- If the user has dispatched a new request since, the render model correctly shows the new `requestedSteps` because the new feedback has `status = REQUESTED`
- **This is correct behavior** — the gap only exists if the APPLIED result arrives between dispatches, which is sub-millisecond in practice

## Why `REQUESTED` Render-Model Priority Is Necessary But Not Sufficient

**Necessary:** Without REQUESTED priority, the render model would always show `previewBrightnessSteps` (the last-applied value). During fast drag, the applied value lags behind the requested value by the CameraX latency (50-200ms). The thumb would trail the finger.

**Not sufficient:** REQUESTED priority only affects the *value* displayed. It does not prevent the render cycle from *writing* to the SeekBar during active drag. The missing piece is a **drag latch** that suppresses `brightnessSlider.progress = ...` while the user's finger is on the SeekBar. The latch should:
- Set a flag in `onStartTrackingTouch()`
- Clear the flag in `onStopTrackingTouch()`
- Skip the `progress = ...` write when the flag is set

This ensures the user's finger controls the thumb position during drag, and the render model only reconciles after the finger lifts.

## Should Brightness Share the Zoom Interaction Policy?

| Dimension | Zoom | Brightness |
|---|---|---|
| Value type | Continuous float | Bounded integer steps |
| Request ID | No | Yes (already has) |
| Stale filtering | No | Yes (already filters by requestId) |
| Result statuses | Implicit (applied or not) | Explicit: APPLIED, DEGRADED_SAVED_ONLY, FAILED, UNSUPPORTED |
| Duplicate dispatch | No (single dispatch path) | **Yes (bug in coordinator)** |
| UI drag latch | No (but needed for focal slider) | No (needed for SeekBar) |

**Recommendation:** Brightness should **reuse the zoom strategy's drag-latch pattern** but **not share the same runtime owner**. Brightness already has superior request-id semantics. The shared strategy should define:
1. A UI-local drag-latch flag (per control)
2. A render-suppression rule: "do not write control value from render model while drag latch is active"
3. A post-drag reconciliation: "on stop tracking, if the final value differs from the last-applied, dispatch one final request"

Brightness keeps its own request-id filtering and result-status handling. Zoom gets request-id semantics added as part of package 01.

## Recommended Repair Order

1. **Fix duplicate dispatch** in `CameraSessionCoordinator.latestPreviewBrightnessCommand()`:
   - Remove the `scope.launch { cameraAdapter.dispatch(...) }` — keep only the returned command
   - The outer `handleEffect` already dispatches the returned command
   - OR remove the outer dispatch and keep only the launched job (pick one path)
2. **Add UI drag latch** to SeekBar binding:
   - `onStartTrackingTouch`: set `isDraggingBrightness = true`
   - `onStopTrackingTouch`: set `isDraggingBrightness = false`, dispatch final value if needed
   - `renderQuickBubble`: skip `brightnessSlider.progress = ...` when `isDraggingBrightness`
3. **Preserve request-id stale filtering** (already correct in `DefaultCameraSession`)
4. **Handle degraded/failed results** without snapping thumb while dragging:
   - If drag latch is active, queue the error/status for display after drag ends
   - If drag latch is inactive, show error state immediately

## Files/Tests for Implementation Package

| File | Change |
|---|---|
| `app/.../camera/CameraSessionCoordinator.kt` | Fix `latestPreviewBrightnessCommand()` — remove duplicate dispatch |
| `app/.../CockpitSurfaceRenderer.kt` | Add drag-latch parameter to `renderQuickBubble()`, skip progress write when latched |
| `app/.../MainActivityActionBinder.kt` | Implement `onStartTrackingTouch`/`onStopTrackingTouch` with drag-latch flag |
| `app/.../MainActivity.kt` | Wire drag-latch flag to render call |
| `app/.../SessionCockpitRenderModel.kt` | No changes needed — REQUESTED priority already correct |
| `core/session/.../DefaultCameraSession.kt` | No changes needed — stale filtering already correct |
| Tests: `SessionCockpitRenderModelTest` | Verify REQUESTED priority behavior |
| Tests: `CameraSessionCoordinatorTest` (new) | Verify single dispatch, no duplicate |

## Acceptance Criteria Status

- [x] Status file contains a source-of-truth timeline for brightness drag
- [x] Status file verifies the suspected duplicate dispatch in `CameraSessionCoordinator` — **CONFIRMED**: `latestPreviewBrightnessCommand()` both launches a job AND returns a command, causing double dispatch
- [x] Status file explains why `REQUESTED` render-model priority is necessary but not sufficient — it controls the value displayed but doesn't suppress render writes during active drag
- [x] Status file recommends how brightness reuses the zoom strategy — shared drag-latch pattern, brightness-specific request-id and result handling
- [x] Status file lists exact files/tests an implementation package should touch

## Unresolved Risks

1. **Pre-existing test compilation failure**: `PostprocessOuterGuardTest.kt` has broken constructor calls — blocks all unit test runs in `:app:testDebugUnitTest`. Must be fixed before implementation package can verify with tests.
2. **CameraX cancellation semantics**: Need to verify whether `Job.cancel()` on `previewBrightnessJob` actually prevents the `setExposureCompensationIndex().await()` call from completing, or whether CameraX queues it regardless.
3. **Render loop frequency**: Need to measure how often `render()` is called during fast drag — if it's on every state emission (including intermediate REQUESTED→APPLIED transitions), the drag latch must be very responsive.

## Self-Certification

Only allowed path was touched: `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/02-brightness-state-arbitration-audit.md`

No runtime code, test code, INDEX.md, or other status files were modified.
