# Real Device Feedback Acceptance QA

## Goal

Validate the implemented fixes for real-device issues 4, 5, and 6 on the target Android device before declaring the feedback resolved.

## Context

- User request: issues were found through real-device testing, so unit tests alone are not enough.
- Verified facts:
  - The linked implementation packages contain deterministic unit/assemble checks.
  - The most important remaining evidence is visual/touch behavior: hit target feel, feedback position, disappearance, and low-light prompt timing.
- Relevant files:
  - `docs/plans/2026-05-25-real-device-feedback-quick-focus-night-index.md`
  - `app/build/outputs/apk/debug/app-debug.apk` after assembly
  - Any screenshots or screen recordings captured during smoke testing
- Non-goals:
  - Do not accept new feature scope during this QA pass.
  - Do not tune low-light thresholds solely from one ambiguous scene without recording the observed score or conditions.

## Implementation Scope

- Install or run the debug APK on the target device.
- Capture screenshot or screen recording evidence for each acceptance item.
- Record any remaining mismatch as a new follow-up package instead of silently expanding these plans.

## Steps

1. Build a debug APK after packages 1-3 land.
2. Open the camera in portrait orientation and expand `快捷`.
3. Verify quick panel:
   - Brightness is a slider, not three buttons.
   - Frame ratio is one large row; repeated taps cycle `4:3 / 16:9 / 1:1`.
   - `画质` displays the active quality or video combined spec, not grid state.
   - `像素` is present and toggles photo output size where supported.
4. Verify tap feedback:
   - Tap center, corners inside active frame, and near frame edges in `4:3 / 16:9 / 1:1`.
   - Confirm reticle appears under the finger and never outside the active frame.
   - Confirm reticle disappears without another user action.
5. Verify low light:
   - Enter photo mode in a clearly dark scene.
   - Wait at least two sampling intervals plus prompt delay budget.
   - Confirm the floating night prompt appears and hides around 3 seconds later.
   - Tap the prompt and confirm the persisted setting toggles.
6. Save evidence:
   - Attach screenshots or short recordings to the validation note.
   - Record device model, Android version, app commit/build identifier if available, and time of test.

## Acceptance Criteria

- All three reported issues are demonstrably fixed on the same device class that reproduced them.
- Any remaining mismatch is documented with screen evidence and linked to a follow-up plan.
- Stage 7 verification passes after implementation.

## Verification Commands

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- This package is Codex/user retained because it depends on visual judgment and real-device conditions.
- If a non-multimodal agent claims this QA is complete without screenshots or recordings, treat that claim as unverified.
