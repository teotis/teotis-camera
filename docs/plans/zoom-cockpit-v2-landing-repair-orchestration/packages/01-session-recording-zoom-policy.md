# Package 01 - Session Recording Zoom Policy

## Package ID

`01-session-recording-zoom-policy`

## Goal

Make `DefaultCameraSession` the single runtime owner for Zoom Cockpit V2 recording restrictions. UI may show or hide controls, but Session Kernel must enforce the final policy.

## Current Evidence

- `handleApplyZoomRatio()` blocks countdown and active photo capture, but it does not explicitly restrict active video recording.
- `handleZoomRatioToggled()` currently has a test saying zoom toggle remains available while recording.
- The product requirement is now "点位 + 滑条 + 一位小数焦距 + 能力边界 + 录制中限制"; do not leave recording restrictions only in UI.

## Implementation Scope

- Define and implement conservative Session policy:
  - `REQUESTING` and `STOPPING`: block all zoom changes.
  - `RECORDING + CONTINUOUS`: allow clamped `ApplyZoomRatio` only.
  - `RECORDING + DISCRETE_PRESET`: block `ZoomRatioToggled` and block/apply-nearest only if explicitly justified by tests; default to block to avoid lens-jump claims during recording.
  - `UNSUPPORTED`: block as today.
- Add focused zoom-only tests in `DefaultCameraSessionTest`.
- Do not change CameraX adapter code unless a focused test proves a contract mismatch.

## Acceptance Criteria

- Session blocks discrete preset stepping during active recording.
- Session allows continuous in-range `ApplyZoomRatio` during active recording only when capability is `CONTINUOUS`.
- Session blocks recording `REQUESTING` and `STOPPING`.
- Blocked requests do not emit `SessionEffect.ApplyZoomRatio`.
- Trace and `lastAction` explain the block.
- Existing idle zoom behavior remains intact.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*zoom*"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
```

## Branch And Worktree Policy

- Branch: `agent/zoom-cockpit-v2-landing-repair/01-session-recording-zoom-policy`
- Worktree: `.agent-worktrees/zoom-cockpit-v2-landing-repair/01-session-recording-zoom-policy`

## Unlock Conditions

- Initial ready wave.

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` only if pinch behavior must align with Session policy
- `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt` only if gesture behavior changes

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/camera/**`
- unrelated session tests outside zoom cases

## Expected Evidence Pack

- [ ] coordinator status updated
- [ ] `state.tsv` row updated
- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] base commit recorded
- [ ] commit hash recorded
- [ ] changed files listed
- [ ] verification commands/results recorded
- [ ] unresolved risks noted
- [ ] only allowed paths touched

