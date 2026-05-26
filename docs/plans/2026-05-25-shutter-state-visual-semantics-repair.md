# Shutter State Visual Semantics Repair

## Goal
Fix the shutter button so its visual state matches the product semantics: normal photo-ready state is white; true busy/blocking states use the app's selected accent treatment; active recording stays clickable as the stop-recording control; theme/default tint must not turn the custom shutter drawable purple on real devices.

## Context
- User request: real-device testing showed the shutter button color is inconsistent and appears purple. The user expects two broad photo-capture states: normal white, and non-clickable/busy using the same selected color language as other app components.
- External-agent result to correct: the previous implementation changed disabled colors to high-opacity `oc_accent` and drove `Button.isEnabled` directly from `captureDisabledReason() == null`.
- Verified facts:
  - `app/src/main/res/layout/activity_main.xml` defines `buttonShutter` as a plain `Button` with `android:background="@drawable/bg_shutter_selector"` and no explicit `backgroundTint` clearing.
  - `app/src/main/res/values/colors.xml` currently has `oc_shutter_fill_disabled=#9955D6BE` and `oc_shutter_ring_disabled=#CC55D6BE`, which is visually much heavier than existing selected chips.
  - Existing selected components use lower-weight accent treatments such as `bg_quick_chip_selected.xml` fill `#3355D6BE` plus `@color/oc_accent` stroke, and `bg_mode_track_active_chip.xml` fill `#4055D6BE`.
  - `captureDisabledReason()` currently returns a reason for `RecordingStatus.RECORDING`.
  - `DefaultCameraSession` intentionally allows `SessionIntent.ShutterPressed` during active recording so the shutter stops recording.
  - `MainActivityActionBinder` also uses shutter click to request camera permission when permission is missing, so permission-missing should not blindly disable the View.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/drawable/bg_shutter_selector.xml`
  - `app/src/main/res/drawable/bg_shutter_photo_disabled.xml`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- Non-goals:
  - Do not change session kernel recording behavior.
  - Do not redesign the full cockpit UI.
  - Do not change video recording red shutter visuals except where necessary to keep the button clickable.
  - Do not rewrite unrelated capture-disabled copy or panel blocking behavior.

## Implementation Scope
- Add an explicit shutter UI-state decision that separates "can this Button be clicked?" from "can a new photo capture start?"
- Keep the shutter enabled during active recording because it is the stop-recording command.
- Keep the shutter enabled when camera permission is missing because the existing click path requests permission.
- Disable or visually block only true no-action busy states, such as photo saving/requested active shot, countdown, preview recovering, recording requesting, and recording stopping.
- Remove theme/default background tint from the shutter so custom drawables render without purple contamination.
- Tune disabled photo shutter colors to match existing selected accent weight, not a heavy solid mint button.

## Steps
1. Inspect current external-agent changes before editing:
   - Confirm `colors.xml` disabled shutter colors.
   - Confirm `CameraCockpitRenderModel.kt` currently derives `isShutterEnabled` from `captureDisabledReason()`.
   - Confirm `MainActivity.kt` passes `captureDisabledReason(state, text) == null` to `renderShutter`.
   - Confirm `CockpitSurfaceRenderer.kt` sets `bottomCockpit.shutter.isEnabled`.
2. Introduce a small app-layer helper in `SessionCockpitRenderModel.kt`, for example:
   - `internal fun shutterDisabledReason(state: SessionState, text: AppTextResolver): String?`
   - It should return a reason for:
     - `PreviewStatus.RECOVERING`
     - `countdownRemainingSeconds != null`
     - active photo shot or `captureStatus == REQUESTED/SAVING`
     - `recordingStatus == REQUESTING`
     - `recordingStatus == STOPPING`
   - It should return `null` for:
     - idle photo-ready state
     - `RecordingStatus.RECORDING`
     - missing camera permission, because the click requests permission
3. Update `CameraCockpitRenderModel.kt`:
   - Set `bottomCockpit.isShutterEnabled = shutterDisabledReason(state, text) == null`.
   - Set `bottomCockpit.disabledReason = shutterDisabledReason(state, text)`.
   - Do not use `captureDisabledReason()` directly for shutter enabled state.
4. Update `MainActivity.kt`:
   - Pass `shutterDisabledReason(state, text) == null` to `cockpitRenderer.renderShutter(...)`.
   - Leave `captureConfigDisabledReason()` and mode-track blocking behavior alone unless tests force a narrow deduplication.
5. Update `CockpitSurfaceRenderer.kt`:
   - Keep recording background selection based on `state.recordingStatus != RecordingStatus.IDLE`.
   - Set the custom background resource first, then clear background tint on `bottomCockpit.shutter`.
   - Set `isEnabled` from the new shutter-specific enabled value.
   - Ensure active recording remains enabled and red.
6. Update XML/resources:
   - In `activity_main.xml`, clear shutter background tint explicitly, for example `android:backgroundTint="@null"` if supported by the current Android resource setup.
   - In code, also clear tint defensively after `setBackgroundResource`, because AppCompat/Material tint can be re-applied at runtime.
   - Change `oc_shutter_fill_disabled` to a low-weight selected fill such as `#3355D6BE`.
   - Change `oc_shutter_ring_disabled` to `#55D6BE` or another value matching `@color/oc_accent`.
7. Update tests:
   - Add or update `CameraCockpitRenderModelTest` coverage:
     - idle photo state -> shutter enabled
     - active recording -> shutter enabled
     - recording requesting/stopping -> shutter disabled
     - capture saving/requested or active photo shot -> shutter disabled
     - preview recovering/countdown -> shutter disabled
     - permission missing -> shutter enabled
   - Add or update `SessionCockpitRenderModelTest` coverage for the new helper.
   - Do not remove existing tests proving recording stops through shutter.
8. Run focused verification, then stage-level verification if the focused pass is clean.

## Acceptance Criteria
- The shutter is white in normal idle photo-ready state.
- The shutter does not render purple on real devices or emulator because background tint is cleared.
- The shutter uses app-selected accent semantics only for true busy/blocking states, with visual weight close to selected chips.
- During active video recording, the shutter remains enabled/clickable and keeps the recording visual state, so tapping it can stop recording.
- During recording starting/stopping, photo saving/requested, countdown, or preview recovery, the shutter is disabled or blocked according to the new shutter-specific helper.
- Missing camera permission does not disable the shutter View, preserving tap-to-request-permission behavior.
- Existing session-kernel behavior is unchanged.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Codex-Retained Acceptance
- After implementation, Codex should review the diff against this plan.
- Codex should perform or request real-device/screenshot verification for the visual outcome, because non-multimodal agents cannot reliably judge whether the button still reads too heavy, too faint, or purple-tinted.

## Risks And Notes
- Do not equate `captureDisabledReason()` with shutter clickability. That helper is broader and includes states where other capture configuration controls should be blocked.
- Android disabled buttons can apply framework alpha/tint. If visual output is still off, prefer an explicit visual state on the drawable/background over relying only on `View.isEnabled=false`.
- If `android:backgroundTint="@null"` is not accepted by the resource compiler, remove that XML line and keep the defensive runtime `backgroundTintList = null` or `ViewCompat.setBackgroundTintList(view, null)` in `CockpitSurfaceRenderer`.
- If implementation touches `captureConfigDisabledReason()` duplication, keep it strictly mechanical and covered by the existing tests. Do not move ownership into the session kernel for this UI-only visual fix.
