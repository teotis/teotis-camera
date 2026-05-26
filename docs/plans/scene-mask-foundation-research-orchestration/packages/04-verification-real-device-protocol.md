# Package 04 — Verification And Real-Device Protocol

## Package ID

`04-verification-real-device-protocol`

## Goal

Define the local gates and real-device QA protocol that would make Scene Mask claims testable. This package must cover both deterministic engineering checks and visual/product acceptance.

## Dependencies

- Wait for `02-current-implementation-audit`.
- Wait for `03-product-architecture-design`.

## Allowed Paths

- Read any repository file.
- Write only `docs/plans/scene-mask-foundation-research-orchestration/status/04-verification-real-device-protocol.md`.

## Forbidden Paths

- Runtime code and tests.
- `INDEX.md`.
- Other package status files.

## Required Output In Status File

- Focused local verification suite:
  - pure contracts;
  - preview source;
  - saved-photo postprocess;
  - Color Lab no-mask fallback;
  - portrait mask-aware rendering;
  - metadata/pipeline notes;
  - stage gate.
- Real-device protocol:
  - scene setup;
  - modes to test;
  - Color Lab coordinates to sweep;
  - saved JPEG collection;
  - screen recording requirements;
  - diagnostics/pipeline notes to capture.
- Acceptance thresholds:
  - preview/saved direction consistency;
  - subject protection;
  - background change visibility;
  - halo/edge quality;
  - no analyzer stalls;
  - honest degradation on unsupported devices.
- Failure examples and decision labels:
  - PASS;
  - PARTIAL;
  - FAIL;
  - BLOCKED BY DEVICE ACCESS.

## Acceptance Criteria

- Protocol separates unit-test evidence from visual taste judgment.
- Protocol explicitly protects the user's real-device concerns: preview vs saved consistency, Color Lab output, and honest capability display.
- Protocol includes commands using `rtk`.
- Protocol does not require non-multimodal implementation agents to judge natural skin tone from tests alone.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.SceneMaskContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.SceneMaskPayloadTest --tests com.opencamera.app.camera.SceneMaskTypeCollisionTest --tests com.opencamera.app.camera.MaskAwarePortraitRenderMathTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Expected Evidence Pack

Use the standard status template and add `## Verification Protocol`.

