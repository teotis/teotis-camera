# Package 02 — Current Implementation Audit

## Package ID

`02-current-implementation-audit`

## Goal

Audit the current repository implementation of Scene Mask against the intended architecture and product honesty rules. This package must answer: "What is already real, what is partial, and what is only a claim?"

## Allowed Paths

- Read any repository file.
- Write only `docs/plans/scene-mask-foundation-research-orchestration/status/02-current-implementation-audit.md`.

## Forbidden Paths

- Runtime code and tests.
- `INDEX.md`.
- Other package status files.

## Primary Files To Inspect

- `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/SceneMaskContractsTest.kt`
- `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
- `app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt`
- `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
- `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt`
- `app/src/main/java/com/opencamera/app/camera/MaskAwarePhotoAlgorithmEditor.kt`
- `app/src/main/java/com/opencamera/app/camera/MaskAwarePortraitRenderEditor.kt`
- `app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt`
- `app/src/test/java/com/opencamera/app/camera/SceneMaskPayloadTest.kt`
- `app/src/test/java/com/opencamera/app/camera/SceneMaskTypeCollisionTest.kt`
- `app/src/test/java/com/opencamera/app/camera/MaskAwarePortraitRenderMathTest.kt`

## Audit Questions

1. Are mask pixels kept out of `SessionState` and persisted settings?
2. Does preview segmentation close `ImageProxy` exactly once, or does ownership sit elsewhere?
3. Does `MlKitSelfiePreviewSceneMaskSource` actually respect `PreviewSceneMaskConfig`, target resolution, throttling, and background execution?
4. Does the saved-photo provider use the correct still-image mode and write results into actual output JPEG paths?
5. Are metadata/pipeline notes honest for applied/degraded/unsupported/failed?
6. Are there duplicate app/core capability types that increase drift risk?
7. Do current tests prove behavior or only construction?
8. Are existing known blockers from `codex/documentation.md` still present, especially mask-aware output writeback, metadata retention, and edge softness?

## Required Output In Status File

- A table of current components with status: `real`, `partial`, `claim-only`, `blocked`, or `unknown`.
- Concrete file/line references for each finding.
- Focused test results if run.
- A "do not claim supported yet" list.
- A "safe to build on" list.

## Acceptance Criteria

- Findings are grounded in source and tests, not impressions.
- Audit distinguishes preview approximation from saved-photo output.
- Audit explicitly checks whether existing code maps to `SUPPORTED / DEGRADED / UNSUPPORTED`.
- Audit names the smallest implementation repair loop if research finds blocked code.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.SceneMaskContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.SceneMaskPayloadTest --tests com.opencamera.app.camera.SceneMaskTypeCollisionTest --tests com.opencamera.app.camera.MaskAwarePortraitRenderMathTest
```

If Gradle shows transient build-root issues, rerun the smallest failed command once before reporting product regression.

## Expected Evidence Pack

Use the standard status template and add `## Current Implementation Findings`.

