# Package 03 — Session Recording Zoom Policy

## Package ID

`03-session-recording-zoom-policy`

## Goal

Make Session Kernel the single source of truth for Zoom Cockpit V2 runtime rules: clamp/snap requests by capability, block unsafe states, and apply active-recording restrictions without asking UI to protect the camera runtime.

## Context

- User request: capability boundary plus recording-state restriction.
- Verified facts:
  - `DefaultCameraSession.handleApplyZoomRatio()` already clamps continuous support and snaps discrete support.
  - `handleZoomRatioToggled()` and `handleApplyZoomRatio()` block countdown and still-photo active capture.
  - Active video recording is currently not clearly modeled as a restricted product state for zoom.
  - `GesturePolicy` still clamps pinch locally to `0.5f..10.0f`, leaving final capability clamp to session.
- Relevant files:
  - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
  - `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`
- Non-goals:
  - Do not bypass `SessionIntent.ApplyZoomRatio`.
  - Do not call CameraX/Camera2 directly from UI or mode plugins.
  - Do not change saved-photo crop/preview semantics here.

## Implementation Scope

- Consume the product policy established by package 01.
- Ensure session behavior is explicit for:
  - unsupported zoom: blocked with trace and user-visible last action;
  - discrete preset zoom: nearest supported ratio only;
  - continuous zoom: clamp to capability min/max;
  - `REQUESTING` / `STOPPING` recording states: blocked;
  - active `RECORDING`: only allow policy-approved continuous in-range updates; otherwise block with trace.
- Add tests for active recording behavior:
  - continuous support during recording if allowed by the final package 01 policy;
  - discrete preset support during recording blocked or restricted;
  - pending/stopping recording blocked;
  - no new device effect emitted when blocked.
- Keep pinch policy simple; if it changes, it must still dispatch `ApplyZoomRatio` and not become the authoritative capability owner.

## Steps

1. Re-read package 01 result before editing.
2. Add or update `DefaultCameraSessionTest` zoom cases for recording states and capability support.
3. Update `handleApplyZoomRatio()` and `handleZoomRatioToggled()` only as needed.
4. Keep trace names stable or additive, for example `zoom.apply.blocked` with `recording=RECORDING`.
5. Add/adjust `GesturePolicyTest` only if pinch behavior changes.
6. Run focused session and app gesture tests.

## Acceptance Criteria

- Session Kernel, not UI, enforces unsupported/discrete/continuous zoom bounds.
- Recording states have deterministic zoom behavior and trace evidence.
- Blocked zoom requests do not emit `SessionEffect.ApplyZoomRatio`.
- Allowed zoom requests during recording, if any, are continuous, clamped, and do not imply lens/device rebind.
- Tests cover idle, active photo, pending recording, active recording, stopping recording, discrete support, continuous support, and unsupported support.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
```

## Risks And Notes

- If real CameraX behavior during active video recording is not locally testable, encode the product policy conservatively and call out real-device smoke as residual risk.
- Do not add a new coordinator-level zoom state machine.

## File Ownership

- `03-session-recording-zoom-policy` owns:
  - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` zoom handlers only
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` zoom/recording tests only
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` pinch branch only
  - `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt` zoom sections only
- Other packages must not edit these files without coordination.

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
- `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` only if package 01 explicitly leaves a missing hook

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` unless a focused session test proves an adapter contract mismatch and user approves scope expansion

## Dependencies

- Depends on: `01-product-contract-capability-boundary`

## Parallel Safety

- caution
- Reason: must wait for the product/capability policy, but after that it is mostly core/session and gesture-policy scoped.

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status clean or explained
- [ ] git diff --stat captured
- [ ] changed files listed
- [ ] verification commands run
- [ ] test results summarized
- [ ] commit hash / PR link
- [ ] unresolved risks noted
- [ ] only allowed paths touched
