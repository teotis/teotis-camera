# Package 01 — Current IQ Gap Audit

## Package ID

`01-current-iq-gap-audit`

## Goal

Audit the current OpenCamera code and existing plan docs to explain why Humanistic / Color Lab / filters can look like direct-frame capture plus basic processing in dusk/night scenes. Produce a fact-based gap map for exposure stability, highlight rolloff, shadow noise, local contrast, sharpening, white balance/color tendency, preview/saved consistency, and capture reliability.

## Allowed Paths

- Read-only: `core/settings/**`, `core/effect/**`, `core/device/**`, `core/session/**`, `feature/mode-humanistic/**`, `feature/mode-photo/**`, `app/src/**`, `scripts/**`, `docs/plans/**`, `codex/documentation.md`.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/01-current-iq-gap-audit.md` only.

## Forbidden Paths

- Do not edit runtime code, tests, resources, Gradle files, `INDEX.md`, package docs, other status files, or `codex/documentation.md`.
- Do not create implementation branches that change production files.

## Dependencies

None. Can run in parallel with package 02.

## Parallel Safety

Safe. This package writes only its own status file.

## Context

Start from these current facts:
- `PerceptualColorRecipe`, `PreviewColorTransform`, `StyleColorPipeline`, and `PhotoAlgorithmPostProcessor` exist and may already cover part of the Color Lab path.
- Prior Color Lab work fixed local deterministic blockers, but real-device visual QA is not complete.
- Manual exposure/WB support exists in device contracts, but support may be saved-only/degraded depending on device capability.
- The task is not to blame a single UI bug. It is to separate code-path limitations from unavoidable vendor ISP gaps.

## Tasks

1. Read the relevant docs:
   - `docs/plans/2026-05-25-color-lab-real-device-followup-index.md`
   - `docs/plans/2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md`
   - `docs/plans/2026-05-25-humanistic-mode-reopen-35mm-styles.md`
   - any vivo X300 or real-device feedback plans that mention output quality.
2. Audit current source paths for the still-image flow:
   - mode style/profile selection;
   - render recipe generation;
   - preview approximation;
   - capture metadata bridge;
   - saved JPEG postprocess;
   - fail-soft and diagnostics;
   - device/manual capability semantics.
3. Map each user-observed gap to one of:
   - likely vendor ISP / multi-frame advantage, not realistically matchable;
   - feasible OpenCamera pipeline improvement;
   - already implemented but unverified on device;
   - ambiguous / needs sample evidence.
4. Identify the minimum code/test surfaces that a later implementation package would touch, but do not edit them.

## Acceptance Criteria

- Status file contains a table with rows for: exposure stability, sky/highlight detail, lamp highlight control, shadow noise, local contrast/microcontrast, sharpening/texture, white balance, color atmosphere, preview/saved consistency, capture/save reliability.
- Each row has evidence references to source files or existing plan docs.
- Each row is classified as `vendor-only`, `feasible`, `implemented-unverified`, or `needs-samples`.
- The package explicitly states what OpenCamera should NOT promise.
- The package lists 3-6 highest-leverage future implementation surfaces, with file references.

## Verification Commands

Use read-only commands first:

```bash
rtk rg -n "PerceptualColorRecipe|ColorLabSpec|StyleColorPipeline|PreviewColorTransform|PhotoAlgorithmPostProcessor|HumanisticModePlugin|exposure|whiteBalance|highlight|ShotCompleted|ShotFailed" core app feature docs/plans
rtk git status --short
```

Optional focused tests only if needed to confirm current deterministic baseline:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

## Expected Evidence Pack

- Worktree path and branch, if any.
- Commands run and short output summary.
- Source/doc references used.
- IQ gap classification table.
- Future implementation surface list.
- Unresolved risks and sample evidence needed.
- Self-certification that only the allowed status file was touched.

## Stop Gates

Stop and ask before proposing runtime edits, changing tests, or treating vivo system-camera behavior as an achievable parity target.
