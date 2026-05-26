# Package 04 — Verification And Real-Device Protocol: Evidence

## Package ID

`04-verification-real-device-protocol`

## Coordinator Status

completed

## Agent

claude-code-main

## Started

2026-05-27T00:55:00Z

## Completed

2026-05-27T01:10:00Z

## Worktree / Branch

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/04-verification-real-device-protocol`
- Branch: `worktree-04-verification-real-device-protocol`
- Base commit: `7eecada` (package 03 completion point)
- Commit hash: (status-file-only package, pending commit)

## Changed Files

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/04-verification-real-device-protocol.md` (this file)

---

## 1. Shared Strategy Summary (from Packages 01, 02, 03)

The verification protocol below is derived from concrete findings in the prior audits:

- **Zoom**: Pinch accumulation never resets (HIGH), no dispatch coalescing (MEDIUM), no device ack path (LOW). Chip-tap path is safe. Slider path (if adopted) needs drag latch + render echo suppression.
- **Brightness**: Duplicate dispatch in `CameraSessionCoordinator.latestPreviewBrightnessCommand()` (CRITICAL), missing UI drag latch (HIGH), CameraX serialization queue (MEDIUM). RequestId stale filtering is already correct.

---

## 2. Focused Local Test Matrix

All tests run in isolation before real-device smoke. Each test has a specific pass condition tied to the identified rollback vectors.

### 2.1 Zoom Slider / Gesture Tests

| Test ID | Test Name | File | What It Verifies | Pass Condition |
|---|---|---|---|---|
| Z-L01 | `GesturePolicy_accumulationResetsOnNewPinch` | `GesturePolicyTest.kt` | Vector 1: cumulativeScaleFactor resets when a new pinch gesture begins | After simulating two separate pinch gestures, the second starts from `cumulativeScaleFactor = 1.0f`, not from the first's accumulated value |
| Z-L02 | `GesturePolicy_accumulationResetsOnDragCancel` | `GesturePolicyTest.kt` | Vector 1: reset on `DragCancel` / `ACTION_UP` | After cancel, `cumulativeScaleFactor == 1.0f` |
| Z-L03 | `GesturePolicy_fastPinchDoesNotOverflow` | `GesturePolicyTest.kt` | Vector 1: rapid scale events don't produce unbounded ratio | 100 rapid scale events produce a ratio within `[minZoom, maxZoom]` |
| Z-L04 | `ZoomCapsuleModels_activeHighlightDuringDrag` | `SessionCockpitRenderModelTest.kt` | Node label rendering during active interaction | The preset whose ratio is closest to the current `zoomRatio` gets `isActive = true` |
| Z-L05 | `ZoomCapsuleModels_compactLabelFormat` | `SessionCockpitRenderModelTest.kt` | Node numbers are visible and correctly formatted | Labels match `compactZoomLabel()` output: "0.6", "1x", "2", "5" (no ".0" suffix) |
| Z-L06 | `ZoomCapsuleModels_noOverlapAtMinSpacing` | `SessionCockpitRenderModelTest.kt` | Node labels don't overlap at minimum zoom preset spacing | With all presets active, bounding rects have no horizontal overlap exceeding 2dp |
| Z-L07 | `DefaultCameraSession_zoomCoalescing` | `DefaultCameraSessionTest.kt` | Vector 2: fast zoom intents coalesce to latest value | Dispatching 10 `ApplyZoomRatio` intents in rapid succession results in only the final ratio being applied to state |
| Z-L08 | `DefaultCameraSession_optimisticUpdateBeforeEffect` | `DefaultCameraSessionTest.kt` | Optimistic state update precedes effect emission | After `handleApplyZoomRatio(ratio)`, `state.activeDeviceGraph.preview.zoomRatio == ratio` before any device event |

### 2.2 Render Model Tests (Zoom + Brightness)

| Test ID | Test Name | File | What It Verifies | Pass Condition |
|---|---|---|---|---|
| R-L01 | `BrightnessRenderModel_requestedPriority` | `SessionCockpitRenderModelTest.kt` | REQUESTED status shows requestedSteps | When `feedback.status == REQUESTED` and `requestedSteps = +3`, render model returns steps = +3 |
| R-L02 | `BrightnessRenderModel_appliedFallback` | `SessionCockpitRenderModelTest.kt` | APPLIED status shows previewBrightnessSteps | When `feedback.status == APPLIED`, render model returns `previewBrightnessSteps` |
| R-L03 | `BrightnessRenderModel_staleAckIgnored` | `SessionCockpitRenderModelTest.kt` | Stale ack doesn't change displayed value | After request "1002" and stale result "1001", render model still shows requestedSteps from "1002" |
| R-L04 | `BrightnessRenderModel_degradedExplained` | `SessionCockpitRenderModelTest.kt` | DEGRADED_SAVED_ONLY doesn't silently snap | When result is DEGRADED, render model returns the applied steps but with a non-APPLIED feedback status flag for UI to display explanation |
| R-L05 | `BrightnessRenderModel_failedNoSnap` | `SessionCockpitRenderModelTest.kt` | FAILED result doesn't overwrite during active drag | When result is FAILED and drag is active, render model preserves last requestedSteps |

### 2.3 Coordinator Tests

| Test ID | Test Name | File | What It Verifies | Pass Condition |
|---|---|---|---|---|
| C-L01 | `Coordinator_brightnessSingleDispatch` | `CameraSessionCoordinatorTest.kt` | Fix for duplicate dispatch | After `handleEffect(ApplyPreviewBrightness)`, exactly one `DeviceCommand.ApplyPreviewBrightness` is dispatched to `cameraAdapter` |
| C-L02 | `Coordinator_brightnessJobCancel` | `CameraSessionCoordinatorTest.kt` | Previous job cancelled on new request | Dispatching request A then B results in A's job being cancelled; only B reaches the adapter |
| C-L03 | `Coordinator_zoomFireAndForget` | `CameraSessionCoordinatorTest.kt` | Zoom dispatch is single-shot | `handleEffect(ApplyZoomRatio)` dispatches exactly one `DeviceCommand.UpdateZoomRatio` |

### 2.4 Session Tests

| Test ID | Test Name | File | What It Verifies | Pass Condition |
|---|---|---|---|---|
| S-L01 | `Session_brightnessStaleAckFiltered` | `DefaultCameraSessionTest.kt` | RequestId mismatch rejects stale result | `handlePreviewBrightnessApplied(result with requestId="old")` when current feedback requestId="new" → state unchanged |
| S-L02 | `Session_brightnessRequestIdIncrements` | `DefaultCameraSessionTest.kt` | Each request gets unique requestId | Two sequential `handleApplyPreviewBrightness` calls produce different requestIds |
| S-L03 | `Session_zoomPendingState` | `DefaultCameraSessionTest.kt` | If zoom pending/ack is added, pending state is visible | After `handleApplyZoomRatio`, `state.activeDeviceGraph.preview.zoomPending == true` until ack (only if contract is introduced) |
| S-L04 | `Session_zoomNoRollbackOnStaleEffect` | `DefaultCameraSessionTest.kt` | Old zoom intent in channel doesn't overwrite newer state | Processing intents [1.0x, 2.0x, 1.5x] in sequence leaves state at 1.5x (coalesced) |

---

## 3. Trace / Log Evidence Protocol

### 3.1 Expected Zoom Trace Sequence (Fast Pinch Drag)

The following trace pattern should appear in `adb logcat` when a fast pinch-zoom is performed correctly (post-fix):

```
[GesturePolicy] onScaleBegin — resetZoomAccumulation()
[GesturePolicy] onScale: factor=1.05 cumulative=1.05
[GesturePolicy] onScale: factor=1.08 cumulative=1.134
[GesturePolicy] onScale: factor=0.95 cumulative=1.077
[GesturePolicy] onScaleEnd — resetZoomAccumulation()
[Session] handleApplyZoomRatio(2.15) — optimistic update
[Session] emit ApplyZoomRatio(2.15)
[Coordinator] dispatch UpdateZoomRatio(2.15)
[GesturePolicy] onScaleBegin — resetZoomAccumulation()  ← second pinch starts clean
```

**Key signals:**
- `resetZoomAccumulation()` appears at both `onScaleBegin` and `onScaleEnd`
- No `cumulative` value exceeds ~3.0 (within one gesture's range)
- Each `handleApplyZoomRatio` shows a ratio that monotonically follows the gesture direction (no jumps backward)

### 3.2 Expected Brightness Trace Sequence (Fast Drag with RequestId)

```
[T0] Session: handleApplyPreviewBrightness(+2) requestId=brightness-1001 status=REQUESTED
[T1] Coordinator: dispatch ApplyPreviewBrightness(1001, +2) — single dispatch
[T2] CameraX: applyPreviewBrightness(1001, +2) — started
[T3] Session: handleApplyPreviewBrightness(+3) requestId=brightness-1002 status=REQUESTED
[T4] Coordinator: cancel job-1001, dispatch ApplyPreviewBrightness(1002, +3) — single dispatch
[T5] CameraX: PreviewBrightnessApplied(1001, +2, APPLIED) — stale
[T6] Session: handlePreviewBrightnessApplied — STALE (current=1002, result=1001) — filtered
[T7] CameraX: PreviewBrightnessApplied(1002, +3, APPLIED) — fresh
[T8] Session: handlePreviewBrightnessApplied — MATCH — previewBrightnessSteps=+3
```

**Key signals:**
- `[T1]` shows single dispatch (no duplicate)
- `[T6]` shows stale filtering working (current requestId != result requestId)
- No `CameraX: applyPreviewBrightness` call with requestId=1001 appearing after `[T4]` (job was cancelled)

### 3.3 Trace Patterns Indicating Rollback Risk

| Pattern | What It Means | Severity |
|---|---|---|
| Two `applyPreviewBrightness` calls with the same requestId | Duplicate dispatch bug not fixed | CRITICAL |
| `cumulativeScaleFactor > 5.0` across gestures | Accumulation not resetting between pinches | HIGH |
| `handleApplyZoomRatio(X)` followed by `handleApplyZoomRatio(Y)` where Y < X during upward drag | Stale intent from channel not coalesced | MEDIUM |
| `PreviewBrightnessApplied(requestId=old)` NOT followed by "STALE" log | Stale filtering broken or requestId not incrementing | HIGH |
| `brightnessSlider.progress` log shows value decreasing during upward drag | Render overwrite happening during active drag (drag latch missing) | HIGH |
| `handleApplyZoomRatio` with ratio outside `[minZoom, maxZoom]` | Clamping failure | LOW (should already be guarded) |

### 3.4 How to Capture Traces

```bash
# Clear logcat and start fresh
adb logcat -c

# Capture with relevant tags
adb logcat -s GesturePolicy:* Session:* Coordinator:* CameraX:* > /tmp/zoom_brightness_trace.txt

# After test scenario, stop capture and analyze
grep -E "resetZoomAccumulation|handleApplyZoomRatio|cumulative" /tmp/zoom_brightness_trace.txt
grep -E "handleApplyPreviewBrightness|STALE|MATCH|dispatch" /tmp/zoom_brightness_trace.txt
```

---

## 4. Real-Device Smoke Protocol

### 4.1 Prerequisites

- APK built with all fixes applied (`assembleDebug` succeeds)
- Device connected via ADB (`adb devices` shows device)
- Camera permissions granted
- App cold-started (not resumed from background)
- Logcat capture started (see 3.4)

### 4.2 Test Scenarios

#### Scenario Z1: Slow Zoom Drag (Min → Max)

**If slider exists:**
1. Touch zoom slider thumb at minimum position
2. Slowly drag to maximum over ~5 seconds
3. Observe thumb position, node labels, and preview zoom

**If chip-based:**
1. Tap 0.6x chip
2. Observe preview and chip highlight
3. Tap each preset in order: 1x, 2, 5
4. Observe smooth transitions

**Pass criteria:**
- [ ] Thumb/chip follows finger/tap exactly (no lag > 100ms)
- [ ] Node numbers visible at each preset, no overlap
- [ ] Preview zoom matches displayed value
- [ ] No thumb jumps backward at any point

#### Scenario Z2: Fast Zoom Fling/Drag and Release

**If slider exists:**
1. Fling zoom slider from min toward max with quick swipe
2. Observe behavior during fling and after release

**If chip-based:**
1. Rapidly tap through presets: 0.6x → 1x → 2 → 5 → 0.6x
2. Observe each transition

**Pass criteria:**
- [ ] No visible thumb/progress rollback during active drag
- [ ] No jump to original value before settling on final value after release
- [ ] Final value matches last intended position

#### Scenario Z3: Preset Node Tap

1. Tap a preset node (e.g., 2x chip)
2. Observe immediate feedback
3. Tap a different preset (e.g., 5x)
4. Observe transition

**Pass criteria:**
- [ ] Highlight moves to tapped preset immediately
- [ ] No flash of previous preset between taps
- [ ] Preview zoom updates to match

#### Scenario Z4: Pinch After Slider Drag

1. Drag slider to 2x position (or tap 2x chip)
2. Perform pinch-to-zoom gesture on preview
3. Observe zoom behavior

**Pass criteria:**
- [ ] Pinch starts from current zoom level (2x), not from a stale accumulated value
- [ ] Pinch zoom is smooth and proportional
- [ ] Releasing pinch does not cause snapback to 2x

#### Scenario Z5: Mode Switch After Zoom

1. Set zoom to 3x
2. Switch camera mode (e.g., Photo → Video → Photo)
3. Observe zoom state

**Pass criteria:**
- [ ] Zoom value preserved or explicitly reset (no silent corruption)
- [ ] Chip/slider reflects actual zoom state after mode switch
- [ ] No crash or ANR during mode switch

#### Scenario Z6: Quick Brightness Slow Drag

1. Open quick brightness panel
2. Slowly drag brightness slider from min to max over ~5 seconds
3. Observe preview brightness and slider thumb

**Pass criteria:**
- [ ] Thumb follows finger exactly
- [ ] Preview brightness changes smoothly
- [ ] No thumb snapback at any point

#### Scenario Z7: Quick Brightness Fast Drag

1. Open quick brightness panel
2. Rapidly drag slider back and forth (3-4 full sweeps in ~2 seconds)
3. Observe thumb behavior and preview

**Pass criteria:**
- [ ] Thumb stays under finger (no visible rollback)
- [ ] Stale device result does not overwrite newest request position
- [ ] Final thumb position matches finger position on release
- [ ] Preview brightness settles to the released value within 500ms

#### Scenario Z8: Active Capture/Recording Disabled Behavior

1. Start video recording
2. Attempt to adjust brightness via quick panel
3. Attempt to change zoom (if supported during recording)
4. Stop recording

**Pass criteria:**
- [ ] If brightness is disabled during recording, slider is visually disabled (grayed out)
- [ ] If zoom is supported during recording, zoom changes work normally
- [ ] No crash when attempting disabled controls
- [ ] Controls re-enable after recording stops

#### Scenario Z9: Preview Recovery After Changing Controls

1. Change zoom rapidly (Z2 scenario)
2. Change brightness rapidly (Z7 scenario)
3. Observe preview recovery

**Pass criteria:**
- [ ] Preview recovers to correct state within 1 second of last interaction
- [ ] No frozen/black preview
- [ ] No stale zoom or brightness values displayed

---

## 5. Pass/Fail Criteria

### 5.1 Local Gate (Must Pass Before Real-Device)

| Criterion | Fail Example | Source |
|---|---|---|
| All Z-L* tests pass | `Z-L01` fails: accumulation doesn't reset | Package 01, Vector 1 |
| All R-L* tests pass | `R-L01` fails: REQUESTED priority not working | Package 02 |
| All C-L* tests pass | `C-L01` fails: duplicate dispatch still present | Package 02, Root Cause 1 |
| All S-L* tests pass | `S-L01` fails: stale ack not filtered | Package 02 |
| `assembleDebug` succeeds | Build failure | — |
| `verify_stage_7_observability.sh` passes | Stage 7 gate failure | Project standard |

### 5.2 Real-Device Gate (Must Pass for Final Claim)

| Criterion | Fail Example | Scenarios |
|---|---|---|
| No visible thumb/progress rollback during active drag | Thumb jumps back 2 positions mid-drag | Z2, Z7 |
| No jump to original value before final value after release | After fling, thumb goes to min then jumps to max | Z2 |
| Node numbers visible and not overlapping | "1x" and "2" labels overlap at small screen size | Z1 |
| Stale device result does not overwrite newest request | After fast brightness drag, thumb snaps to old position | Z7 |
| Failed/degraded result explained without silent snap | Brightness shows no feedback on DEGRADED result | Z8 |
| No crash or ANR during any scenario | App freezes during fast pinch | All |

### 5.3 Distinction: Local Pass vs Real-Device Pass

- **Local pass** = all unit tests green, build succeeds, stage 7 gate passes. This confirms the *logic* is correct.
- **Real-device pass** = all 9 scenarios pass on a physical device. This confirms the *user experience* is correct.
- **Local pass is necessary but not sufficient.** The duplicate dispatch bug and drag-latch gap can only be fully verified with real CameraX behavior (latency, serialization, cancellation semantics).

---

## 6. Evidence Collection Expectations

### 6.1 For Local Tests

- Capture full test output (stdout + stderr)
- Record pass/fail for each test ID (Z-L01 through S-L04)
- If any test fails, record the assertion message and stack trace

### 6.2 For Real-Device

- Capture logcat traces for each scenario (see 3.4)
- Record video of Z2, Z7, and Z9 (the three rollback-prone scenarios)
- Note device model, Android version, and CameraX version
- Record timestamps of any failures with the corresponding logcat excerpt

### 6.3 Rollback-Specific Fail Examples

| Failure | What to Capture | Why |
|---|---|---|
| Thumb snaps back during drag | Video frame showing thumb position before and after snap | Proves drag latch is missing or not working |
| Value jumps to original after release | Logcat showing `handleApplyZoomRatio` with stale ratio from channel | Proves coalescing is broken |
| Brightness flickers between old and new | Logcat showing two `applyPreviewBrightness` calls with same requestId | Proves duplicate dispatch not fixed |
| Node labels overlap | Screenshot of zoom chip row at minimum spacing | Proves layout math error |

---

## 7. Stage 7 Gate Notes

- Stage 7 (`verify_stage_7_observability.sh`) is the project's standard observability gate.
- It should be run after all fixes are implemented, as the final local verification step.
- However, Stage 7 may be expensive (full build + test suite). The focused tests (Section 2) should be run first as a fast feedback loop during development.
- If Stage 7 fails but focused tests pass, investigate whether the failure is related to the zoom/brightness changes or a pre-existing issue (see Package 02's note about `PostprocessOuterGuardTest.kt` compilation failure).

---

## 8. Unresolved QA Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `PostprocessOuterGuardTest.kt` compilation failure blocks `:app:testDebugUnitTest` | Cannot run any app unit tests until fixed | Fix or exclude the broken test class before running focused tests |
| CameraX `Job.cancel()` may not prevent `setExposureCompensationIndex().await()` from completing | Duplicate dispatch fix may not fully eliminate double CameraX calls | Verify with logcat: if two calls appear for same requestId after fix, investigate CameraX cancellation semantics |
| No existing `GesturePolicyTest.kt` | Z-L01, Z-L02, Z-L03 require new test file | Create `GesturePolicyTest.kt` as part of implementation |
| `FocalLengthSliderView` does not exist | Slider-specific tests (Z-L01, Z-L02) and scenarios (Z1, Z2) assume slider UI | Adapt tests and scenarios for chip-based UI; document slider as future work |
| Render loop frequency unknown | Drag latch responsiveness depends on how often `render()` is called | Measure with logcat timestamps during real-device smoke |

---

## Self-Certification

- [x] Only touched allowed paths (`status/04-verification-real-device-protocol.md`)
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files
- [x] Did not edit runtime code/tests
- [x] Protocol covers local tests, trace evidence, real-device smoke, and pass/fail criteria
- [x] Protocol distinguishes local pass from real-device pass
- [x] Protocol includes evidence collection expectations
- [x] Protocol includes rollback-specific fail examples
- [x] Protocol notes Stage 7 gate relationship

## Unresolved Risks

1. **Pre-existing test compilation failure**: `PostprocessOuterGuardTest.kt` blocks `:app:testDebugUnitTest`. Must be fixed or excluded before focused tests can run.
2. **CameraX cancellation semantics**: Need empirical verification that `Job.cancel()` prevents `setExposureCompensationIndex` completion.
3. **Slider UI absence**: `FocalLengthSliderView` does not exist. Slider-specific test scenarios adapted for chip-based UI.
4. **Render loop frequency**: Unknown; needs measurement during real-device smoke.
5. **Package 03 status file empty**: State.tsv marks completed but status file has no evidence. Verification protocol derived from packages 01 and 02 directly.
