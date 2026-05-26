# Package 01 — Zoom State Arbitration Audit: Evidence

## Package ID

`01-zoom-state-arbitration-audit`

## Coordinator Status

completed

## Worktree / Branch

- worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/01-zoom-state-arbitration-audit`
- branch: `worktree-01-zoom-state-arbitration-audit`
- base commit: `45eec8d`
- commit hash: (read-only audit, no code changes)

## Audit Summary

The INDEX.md and package spec reference `FocalLengthSliderView.kt` as a slider-based zoom control with drag behavior. **This file does not exist in the current codebase.** The zoom UI is implemented as clickable capsule chips, not a draggable slider. The "fast drag rollback" symptom described in the package spec does not directly apply to the current implementation. However, the audit identifies real state-echo risks in the pinch-to-zoom path that could produce similar visual artifacts.

## Source-of-Truth Event Timeline for Zoom Interaction

### Current zoom entry points

| Entry Point | File | Trigger |
|---|---|---|
| Chip tap | `CockpitSurfaceRenderer.kt:104` | `callbacks.onZoomRatioSelected(capsule.ratio)` |
| Pinch gesture | `GesturePolicy.kt:28-35` | `GestureEvent.PinchZoom` accumulated, dispatched every 50ms |
| Toggle (cycle presets) | `SessionIntent.ZoomRatioToggled` | Not bound to any button currently |

### Chip tap timeline

1. **User taps chip** — `CockpitSurfaceRenderer.kt:104` calls `callbacks.onZoomRatioSelected(ratio)`
2. **Dispatch** — `MainActivity.kt:138` dispatches `SessionIntent.ApplyZoomRatio(ratio)` via `lifecycleScope.launch`
3. **Session processes** — `DefaultCameraSession.kt:638` `handleApplyZoomRatio()`:
   - Guards: countdown, active photo shot, unsupported capability
   - Normalizes and clamps ratio to nearest preset (for `DISCRETE_PRESET`) or continuous range
   - Skips if clamped ratio equals current ratio
   - **Optimistic update** (line 676): `_state.value = ...copy(activeDeviceGraph = resolveActiveDeviceGraph(..., requestedZoomRatio = clampedRatio))`
   - Emits `SessionEffect.ApplyZoomRatio(zoomRatio)` (line 687 -> 1290)
4. **Coordinator forwards** — `CameraSessionCoordinator.kt:80` dispatches `DeviceCommand.UpdateZoomRatio(effect.zoomRatio)` to `CameraDeviceAdapter`
5. **State flow emits** — `_state` `MutableStateFlow` notifies collectors
6. **UI re-renders** — `MainActivity.kt:235` `render(state)` calls `cockpitRenderer.renderZoomCapsules(controls)` which re-derives `zoomCapsuleModels(state)` from `state.activeDeviceGraph.preview.zoomRatio`
7. **No device ack for zoom** — `CameraSessionCoordinator.handleDeviceEvent()` has no `ZoomApplied` or `ZoomFailed` event handler. Device feedback is fire-and-forget.

### Pinch-to-zoom timeline

1. **Scale gesture** — `GestureRouter.kt:50` fires `GestureEvent.PinchZoom(scaleFactor, focusX, focusY)`
2. **Policy accumulates** — `GesturePolicy.kt:28`: `cumulativeScaleFactor *= event.scaleFactor`
3. **Throttled dispatch** — Every 50ms, dispatches `SessionIntent.ApplyZoomRatio(currentZoomRatio * cumulativeScaleFactor)`
4. **Session processes** — Same optimistic update path as chip tap
5. **Render echo** — Every `state.collect()` emission triggers full re-render including zoom capsule active state
6. **Stale echo risk** — If user releases pinch and `cumulativeScaleFactor` resets, but a prior `ApplyZoomRatio` is still in the `intentChannel` (UNLIMITED buffer), the session processes stale ratios after the gesture ends

### Critical finding: no stale echo in chip-tap path, but real risk in pinch path

The chip-tap path dispatches a single `ApplyZoomRatio` with a fixed target. There is no "rollback" because the session only processes one intent and the render immediately reflects the optimistic state.

The pinch path has a real risk: `cumulativeScaleFactor` is multiplicative and resets to `1.0f` only on `resetZoomAccumulation()` which is never called in the current code. If the user pinches quickly:
- Multiple `ApplyZoomRatio` intents queue up in the UNLIMITED channel
- Each intent is processed sequentially by the `for (intent in intentChannel)` loop
- The session graph updates optimistically for each one
- But if a render fires between two queued intents, the capsule row could flash intermediate values

## Concrete Rollback Vectors Identified

### Vector 1: Pinch accumulation never resets (HIGH risk)

- **File**: `GesturePolicy.kt:18` — `resetZoomAccumulation()` exists but is never called
- **Effect**: `cumulativeScaleFactor` grows unbounded across separate pinch gestures. A second pinch starts from the accumulated value of the first, causing jumps.
- **File**: `GesturePolicy.kt:32` — `val targetRatio = (currentZoomRatio * cumulativeScaleFactor)` — if `currentZoomRatio` comes from the session state (which reflects the last applied ratio), and `cumulativeScaleFactor` carries over from a previous gesture, the target will be wrong.

### Vector 2: No dispatch cancellation for in-flight zoom (MEDIUM risk)

- **File**: `DefaultCameraSession.kt:59` — `Channel<SessionIntent>(Channel.UNLIMITED)` has no coalescing
- **Effect**: During fast pinch, multiple `ApplyZoomRatio` intents queue up. Each is processed sequentially. There is no mechanism to skip intermediate values or cancel stale ones.
- **Contrast with brightness**: Brightness uses `requestId` and filters stale acks (`DefaultCameraSession.kt:1156`). Zoom has no equivalent.

### Vector 3: No device ack / applied event for zoom (LOW risk for current UI, HIGH for future slider)

- **File**: `CameraSessionCoordinator.kt:105` — `handleDeviceEvent()` has no zoom-related event cases
- **Effect**: The session fires `SessionEffect.ApplyZoomRatio` but never receives confirmation. If the device rejects the zoom (e.g., during video recording on some devices), the session state remains in the rejected value. No error feedback reaches the UI.

### Vector 4: Render echo does NOT overwrite during drag (NOT a current issue)

- **File**: `CockpitSurfaceRenderer.kt:79-117` — `renderZoomCapsules()` reads from `state.activeDeviceGraph.preview.zoomRatio`
- **Analysis**: Since the session updates optimistically BEFORE emitting the effect, the state always reflects the latest requested zoom. The render echo always shows the optimistic value. This is correct behavior — the echo cannot show a stale value because the state was already updated.
- **However**: If a future slider UI has its own local drag state that differs from the session state, the render echo WOULD overwrite it. This is the scenario the INDEX.md describes with `FocalLengthSliderView.setCurrentRatio()`.

## Node Number Rendering Analysis

### Current state

- `CockpitSurfaceRenderer.renderZoomCapsules()` (line 79-117) creates TextView chips with labels from `compactZoomLabel(ratio)` (e.g., "1x", "2", "5")
- Labels are derived from `SessionCockpitRenderModel.kt:570-574`: `compactZoomLabel()` formats with one decimal, drops trailing ".0"
- These labels ARE the node numbers — they appear as chips in a horizontal scroll row
- The INDEX.md says "bottom zoom column/strip has no node numbers" — this refers to a different UI (the `FocalLengthSliderView` which does not exist)

### What would be needed for a slider-based zoom control

If a slider is implemented (replacing or supplementing the chip row):
1. Preset dots need numeric labels drawn alongside/below them
2. Active label must show the continuous value when thumb is between presets
3. Label must not overlap the thumb or floating value indicator
4. During drag, the label should reflect the local thumb position, not the session state

## Recommended Implementation Sequence

### Phase 1: Fix pinch accumulation bug (no UI change needed)

- **File**: `GesturePolicy.kt`
- **Fix**: Call `resetZoomAccumulation()` in `DragCancel` handler and/or when a new pinch gesture begins (detected by `ACTION_DOWN` or `onScaleEnd`)
- **Test**: `GesturePolicyTest` (if exists, otherwise create)

### Phase 2: Add zoom dispatch coalescing (optional, medium priority)

- **File**: `DefaultCameraSession.kt`
- **Option A**: Add a `pendingZoomRatio: Float?` field and only process the latest value when the channel drains (coalescing pattern)
- **Option B**: Use a `ConflatedBroadcastChannel` or `StateFlow` for zoom intents instead of the unlimited channel
- **Trade-off**: Option A is simpler; Option B is more architecturally consistent with the brightness requestId pattern

### Phase 3: Node labels for slider (if slider is adopted)

- **New file**: `FocalLengthSliderView.kt` (does not exist yet)
- **Requirements**:
  - Draw preset dot labels (e.g., "0.6", "1x", "2", "5") below/above each preset dot
  - Floating label shows continuous value during drag
  - Label positioning adapts to avoid overlap with thumb
  - Active preset label highlights when thumb snaps to a preset

### Phase 4: Drag latch + render echo suppression (needed only if slider is adopted)

- **Pattern**: Local drag latch in the slider view; suppress `setCurrentRatio()` while `isDragging == true`
- **Contrast with current**: Current chip row has no drag state, so this is not needed

### Phase 5: Session requestId for zoom (optional, low priority)

- **Pattern**: Mirror the brightness `requestId` pattern from `handleApplyPreviewBrightness()`
- **Benefit**: Enables stale ack filtering if device zoom feedback is ever added
- **File**: `DefaultCameraSession.kt` — add `zoomRequestId` to the zoom intent/effect

## Whether Zoom Needs a Session requestId/ack Path

**Current recommendation: No, not yet.**

- The current chip-based UI has no drag state — each tap dispatches a fixed target, the session updates optimistically, and the render reflects it immediately. There is no rollback vector in this path.
- The pinch-to-zoom path has the accumulation bug (Vector 1) and the queue coalescing gap (Vector 2), but these are fixable at the gesture/session layer without a requestId.
- A requestId becomes necessary IF:
  - A slider UI is adopted with local drag state that could conflict with render echo
  - Device zoom feedback is added (e.g., zoom applied/failed events from CameraX)
  - The user reports real-device rollback that cannot be explained by the gesture bug

## Files/Tests an Implementation Package Should Touch

| File | Change | Priority |
|---|---|---|
| `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` | Reset `cumulativeScaleFactor` on gesture boundary | HIGH |
| `app/src/main/java/com/opencamera/app/gesture/GestureRouter.kt` | Expose `ACTION_DOWN` / `onScaleEnd` to policy | MEDIUM |
| `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` | Optional zoom coalescing in `handleApplyZoomRatio()` | MEDIUM |
| `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt` (new or existing) | Test accumulation reset | HIGH |
| `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` | Add zoom coalescing test if implemented | LOW |

## Risks

- **FocalLengthSliderView does not exist** — The INDEX.md and package spec reference it as existing code, but it is absent from the codebase. Any implementation plan that assumes this file exists will fail. The zoom UI is chip-based, not slider-based.
- **Pinch accumulation bug is a real issue** — `cumulativeScaleFactor` never resets, causing zoom jumps across separate pinch gestures. This should be fixed before any slider work.
- **No zoom device feedback** — The fire-and-forget nature of `SessionEffect.ApplyZoomRatio` means the session cannot detect or recover from device-side zoom failures.

## Evidence Checklist

- [x] worktree path recorded
- [x] branch name recorded
- [x] git status captured (read-only, no changes)
- [x] code references with line numbers
- [x] commands run and output summary
- [x] root-cause hypothesis ranked
- [x] recommended implementation split
- [x] only allowed paths touched (status file only)
