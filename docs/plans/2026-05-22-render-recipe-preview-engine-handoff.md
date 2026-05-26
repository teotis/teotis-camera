# Render Recipe And Preview Engine Handoff

> For external non-multimodal agents. Run shell commands through `rtk`. This plan is intentionally limited to text-testable groundwork. Do not attempt visual judgment without real-device screenshots or saved-media samples.

## Goal

Move the project from three divergent output paths toward one shared imaging contract:

- preview display
- saved JPEG postprocess
- capture feedback thumbnail

The current Codex pass already contains the immediate containment:

- raw capture feedback is suppressed when final output needs Color Lab/filter, non-default frame crop, watermark, or selfie mirror postprocess;
- CameraX Preview and capture use a shared `UseCaseGroup`/`ViewPort` where available.

This handoff covers lower-risk groundwork that can proceed in parallel before a full GL preview renderer is implemented.

## Architecture Rules

- UI renders state and dispatches intents only.
- Session Kernel owns runtime state and thumbnail presentation decisions.
- Mode plugins describe requested effects.
- Device adapter owns CameraX binding.
- Media Pipeline owns saved-output postprocess.
- Do not create a second session kernel.

## Work Package A: Render Recipe Contract

Create a pure Kotlin contract that names the final-output-affecting recipe in one place.

Suggested files:

- `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/RenderRecipeTest.kt`

Suggested model:

```kotlin
data class RenderRecipe(
    val filterProfileId: String?,
    val filterRenderSpec: FilterRenderSpec?,
    val frameRatio: FrameRatio?,
    val watermarkTemplateId: String?,
    val watermarkText: String?,
    val selfieMirror: Boolean
) {
    val requiresFinalOutputPostprocess: Boolean
        get() = filterRenderSpec != null ||
            frameRatio != null && frameRatio != FrameRatio.RATIO_4_3 ||
            !watermarkText.isNullOrBlank() ||
            !watermarkTemplateId.isNullOrBlank() ||
            selfieMirror
}
```

Build helpers from `ShotRequest` metadata and from `EffectSpec`. Keep them pure and unit tested.

Acceptance:

- `EffectSpec` with `FilterEffect(renderSpec != null)` yields `requiresFinalOutputPostprocess=true`.
- `ShotRequest` with `filterSpec.version` yields `true`.
- `ShotRequest` with `frameRatio=16:9` yields `true`.
- clean single-frame no-effect shot yields `false`.

## Work Package B: Reuse Recipe In Capture Feedback Policy

Replace the duplicated metadata checks in `captureFeedbackPolicyFor()` with `RenderRecipe.from(shot).requiresFinalOutputPostprocess`.

Files:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

Acceptance:

- Existing capture feedback tests continue passing.
- Tests should assert the recipe, not merely the exact metadata keys, where possible.

## Work Package C: Saved Output Diagnostics

Strengthen diagnostics so true-device verification can prove whether saved media received the recipe.

Files:

- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- related tests under `app/src/test/java/com/opencamera/app/camera`

Required notes:

- `algorithm-render:applied:<profile>` or explicit skipped/failed reason.
- `frame-ratio:applied:<ratio>` or explicit skipped/failed reason.
- `watermark:rendered:<template>` or explicit skipped/failed reason.

Acceptance:

- A filtered shot test checks the exact profile id and one strong render parameter.
- A 16:9 frame-ratio test checks the crop bounds note exists.
- A watermark test checks the template id appears in pipeline notes.

## Work Package D: Real-Device QA Script

Create a markdown checklist for vivo X300 validation.

Suggested file:

- `docs/plans/2026-05-22-vivo-x300-output-consistency-qa.md`

Checklist must ask the tester to collect:

- screen recording,
- saved JPEG files,
- app debug output / pipeline notes,
- one neutral capture and one strong Color Lab capture,
- one 4:3 and one 16:9 capture,
- one watermark capture.

Pass criteria:

- No raw preview feedback appears for postprocessed shots before save completion.
- Saved-media thumbnail replaces any transient state.
- Pipeline notes show applied or explicit skipped/failed notes for each requested recipe part.
- Preview frame and saved JPEG content are materially closer after `UseCaseGroup`/`ViewPort`, with remaining mismatch documented by sample.

## Non-Goals

- Do not implement OpenGL/OES preview rendering in this external-agent pass.
- Do not change CameraX binding ownership.
- Do not move Session thumbnail policy into UI.
- Do not make visual pass/fail claims without supplied images or real-device files.

## Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
rtk ./scripts/verify_stage_7_observability.sh
```
