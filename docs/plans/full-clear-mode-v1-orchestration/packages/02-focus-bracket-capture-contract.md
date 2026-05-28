# Package 02 - Focus Bracket Capture Contract

## Goal

Add reusable media/device contracts for Full Clear V1 focus-bracket capture without touching CameraX execution.

## Allowed Paths

- `core/media/src/main/kotlin/com/opencamera/core/media/**`
- `core/media/src/test/kotlin/com/opencamera/core/media/**`
- `core/device/src/main/kotlin/com/opencamera/core/device/**`
- `core/device/src/test/kotlin/com/opencamera/core/device/**`
- `core/mode/src/main/kotlin/com/opencamera/core/mode/**`
- `core/mode/src/test/kotlin/com/opencamera/core/mode/**`
- `docs/plans/full-clear-mode-v1-orchestration/v1-implementation-design.md`

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- Feature mode plugin implementation except compile-safe references required by contracts.
- Coordinator files outside `status/02-focus-bracket-capture-contract.md`.

## Required Work

1. Define focus-bracket data models in the media/device boundary, not in app UI.
2. Add a `FOCUS_STACK_FUSION` algorithm type or equivalent explicit focus-stack node.
3. Extend shot graph construction so focus bracket captures are distinguishable from night multi-frame merge.
4. Extend device request translation with a per-frame focus plan and support/degradation diagnostics.
5. Keep generic multi-frame/night behavior unchanged.
6. Add tests for supported, degraded, and unsupported bracket semantics.

## Acceptance Criteria

- Full Clear V1 can be represented without stringly typed ad hoc metadata.
- Existing Night multi-frame tests continue to distinguish night merge from focus-stack fusion.
- Device request diagnostics include bracket support and frame roles.
- Unsupported manual focus degrades honestly instead of silently pretending bracket support.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ModeCaptureStrategyGraphTest --tests com.opencamera.core.media.ShotGraphContractsTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:mode:test
```

## Expected Evidence

- Contract summary and exact model names.
- Changed files.
- Verification command results.
- Any compatibility notes for downstream CameraX execution.

