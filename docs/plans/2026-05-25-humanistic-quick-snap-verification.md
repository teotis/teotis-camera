# Humanistic Quick Snap Verification And QA Plan

日期：2026-05-25

## Goal

Define the evidence required to accept Humanistic Quick Snap. This package is intentionally stricter than a normal compile gate because the core claim is experiential: Humanistic must feel faster and more moment-oriented than Photo.

## Context

- User request: 研究 vivo、Apple、本地参考实现或独立设计，最终适配当前项目。
- Verified facts:
  - Local verification can prove contracts, mode visibility, capture diagnostics, and fallback behavior.
  - Local unit tests cannot prove real shutter feel, saved JPEG timing, or whether ZSL is actually supported on a specific device.
  - Stage 7 is still the current project milestone, so any feature reopen must keep observability gates green.
- Relevant files:
  - `scripts/verify_stage_7_observability.sh`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterStillCaptureQualityTest.kt`
  - `core/device/src/test/kotlin/com/opencamera/core/device/DefaultDeviceShotRequestTranslatorTest.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- Non-goals:
  - Do not require a large real-device matrix for the first implementation loop.
  - Do not block local contract work on owning a ZSL-capable device.
  - Do not accept marketing claims without diagnostics and device evidence.

## Local Acceptance Criteria

- Compile succeeds after reconciling current still-capture contract drift.
- Unit tests prove:
  - Humanistic appears in mode track order.
  - Humanistic requests default quick-snap latency priority.
  - Humanistic captures still use `ShotKind.STILL_CAPTURE` or `LIVE_PHOTO` only when Live is explicitly enabled.
  - Device translator preserves latency priority and emits diagnostics.
  - CameraX adapter maps supported ZSL to zero-shot-lag mode and unsupported ZSL to minimize-latency.
  - Flash ON/AUTO blocks ZSL diagnostics.
  - Mode switch resets Humanistic zoom baseline without leaking 1.3x into Photo.
- Stage 7 verification still passes.

## Verification Commands

Run focused tests first:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterStillCaptureQualityTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Then run the stage gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Real Device QA

Use one target Android phone first. If available, prefer a device where CameraX reports ZSL support; otherwise use the same protocol and expect degraded/min-latency diagnostics.

1. Fresh launch:
   - Open app.
   - Confirm mode track includes `Photo`, `Humanistic`, `Video`, `Doc`.
   - Switch to Humanistic.
   - Confirm preview is stable and zoom appears tighter than Photo when supported.

2. Quick snap feel:
   - Point at a millisecond timer or moving hand.
   - Take 10 Humanistic photos at normal pace.
   - Take 10 Photo photos at normal pace with the same scene.
   - Capture screen recording if possible.

3. Diagnostics:
   - Confirm Humanistic output metadata or diagnostics includes `captureIntent=quick-snap`.
   - Confirm `device:capture-mode=zero-shot-lag` or `device:capture-mode=minimize-latency`.
   - If ZSL is unsupported, confirm a reason is visible in diagnostics.

4. Output quality:
   - Open saved Humanistic JPEGs.
   - Confirm Street/Portrait/Life styles look distinct from generic Photo Original/Vivid.
   - Confirm no watermark, frame, selfie mirror, or EXIF regression.

5. Tradeoff cases:
   - Enable flash if the UI exposes it or use a fake/device test if not.
   - Confirm ZSL is disabled/degraded when flash is ON/AUTO.
   - Enable Live Photo if applicable.
   - Confirm the app does not falsely claim pure quick snap when Live forces a heavier path.

## Measurement Guidance

The first product target should be qualitative plus diagnostic, not a hard universal millisecond promise:

- Good: Humanistic feedback appears immediately, saved shot corresponds closely to the button press moment, diagnostics are honest.
- Acceptable degraded: Device lacks ZSL, but Humanistic still uses minimize latency and tells diagnostics why.
- Not acceptable: Humanistic is only Photo with a new label, or claims ZSL while using quality/multi-frame/flash-heavy capture.

If a numeric target is needed for one device, measure:

- `requestedAtElapsedMillis -> deviceCaptureStartedAtElapsedMillis`
- `requestedAtElapsedMillis -> deviceCaptureCompletedAtElapsedMillis`
- `requestedAtElapsedMillis -> capture feedback thumbnail visible`

Add these to diagnostics only if the project already exposes them cleanly through `ShotTiming` / `SessionDiagnostics`.

## Codex-Retained Acceptance

Codex/user should retain final acceptance for:

- Whether Humanistic feels faster than Photo.
- Whether 35mm-ish zoom feels right on the target phone.
- Whether the three style labels and looks are product-grade.
- Whether current Stage 7 stabilization work can tolerate reopening this feature now.

## Risks And Notes

- A device may pass local tests and still lack ZSL support. That is not a failure if degraded behavior is explicit.
- If the saved JPEG lags behind the feedback thumbnail by too much, do not hide it with UI animation; treat it as a pipeline timing issue.
- The local reference library supports the idea of anchor-frame feedback and ZSL policy, but OpenCamera should implement it through the existing `CaptureStrategy -> ShotExecutor -> Device Adapter` path.
