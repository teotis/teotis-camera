# Package 03 — Feasible Rendering Pipeline Design

## Package ID

`03-feasible-rendering-pipeline-design`

## Goal

Design a feasible OpenCamera pipeline for improving dusk/night Humanistic and Color Lab output using current architecture boundaries. The design should translate the scorecard into session/device/media/effect responsibilities and capability semantics, not into immediate code changes.

## Allowed Paths

- Read-only: `core/settings/**`, `core/effect/**`, `core/device/**`, `core/session/**`, `core/media/**`, `feature/mode-humanistic/**`, `feature/mode-photo/**`, `app/src/**`, `scripts/**`, `docs/plans/**`.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/03-feasible-rendering-pipeline-design.md` only.

## Forbidden Paths

- Do not edit runtime code, tests, resources, `INDEX.md`, package docs, other status files, or `codex/documentation.md`.
- Do not add dependencies, models, native libraries, vendor SDKs, or network calls.

## Dependencies

Wait for package 01 and package 02 status evidence.

## Parallel Safety

Not safe to run before dependencies. Writes only its own status file.

## Design Boundaries

- UI renders state and dispatches intents only.
- Session Kernel owns runtime state, capture/recording transitions, recovery, and diagnostics.
- Mode plugins describe requested behavior and policies.
- Device adapter translates abstract requests into CameraX/platform details.
- Media/effect pipeline owns saved JPEG rendering and preview approximation.
- Hardware-dependent controls must be `supported`, `unsupported`, or `degraded`.

## Tasks

1. Read status outputs from packages 01 and 02.
2. Propose a staged design for:
   - exposure stability request policy where available;
   - conservative highlight compression / tone rolloff in saved JPEG;
   - shadow noise-aware rendering that avoids plastic denoise promises;
   - local contrast and sharpening limits;
   - white balance/color bias semantics;
   - preview approximation honesty;
   - diagnostics and metadata tags.
3. For every proposed behavior, assign ownership:
   - `core/settings` style/recipe semantics;
   - `core/effect` preview/saved recipe representation;
   - `feature/mode-humanistic` and `feature/mode-photo` style policy;
   - `core/device` device request/capability semantics;
   - `app/camera` CameraX/media integration;
   - tests/verification.
4. Decide which improvements should be deterministic first and which require real-device sample review.
5. Identify no-go ideas that would be costly or dishonest in this repo.

## Acceptance Criteria

- Status file contains a proposed architecture map with ownership per layer.
- Every proposed improvement has a capability/diagnostic state: `supported`, `degraded`, `unsupported`, or `sample-review-only`.
- Design explicitly avoids a hidden second session kernel and avoids UI-direct runtime control.
- Design separates saved JPEG rendering from preview approximation and defines how to label mismatch honestly.
- Design includes a minimal first implementation slice and later slices, but does not implement them.

## Verification Commands

```bash
rtk rg -n "ManualControlSupport|DeviceShotRequestTranslator|exposureCompensation|whiteBalance|PreviewColorTransform|RenderRecipe|PerceptualColorRecipe|PhotoAlgorithmPostProcessor|captureFeedbackPolicyFor|ShotCompleted|ShotFailed|diagnostic|RuntimeIssue" core app feature
rtk git status --short
```

Optional focused tests for baseline knowledge:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
```

## Expected Evidence Pack

- Worktree path and branch, if any.
- Commands run and short output summary.
- Architecture ownership map.
- Capability/degradation matrix.
- First-slice recommendation.
- No-go list.
- Unresolved technical risks.
- Self-certification that only the allowed status file was touched.

## Stop Gates

Stop and ask before proposing code edits, new dependencies, private vendor APIs, or architecture that violates the four-layer contract.
