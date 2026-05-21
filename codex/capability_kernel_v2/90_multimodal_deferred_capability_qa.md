# Multimodal Deferred Capability QA

## Purpose

This document separates 2.0 work that requires image, screenshot, recording, or saved-media interpretation from work that text-only agents can safely implement.

Non-multimodal agents should use documents `00` through `07`. They should not block implementation on this file.

## Deferred Multimodal Tasks

### 1. Filter And Color Quality Review

Inputs:

- saved JPEGs before and after filter render
- reference looks for Photo/Humanistic/Portrait
- screenshots of preview tone approximation

Tasks:

- compare whether capture output resembles preview intent
- identify over-saturation, crushed shadows, skin tone shifts, halo/grain artifacts
- recommend numeric adjustments for `FilterRenderSpec`

Outputs:

- Markdown findings with image names and exact suggested parameter changes
- optional annotated crops under `codex/capability_kernel_v2/visual_review/`

### 2. Portrait And Bokeh Review

Inputs:

- portrait saved images
- subject/background examples
- bokeh profile outputs

Tasks:

- inspect subject boundary quality
- compare fallback focus mode vs approximate bokeh
- identify obvious face/skin rendering issues

Outputs:

- findings grouped as segmentation, blur shape, skin tone, artifacts
- deterministic implementation requests for text-only agents

### 3. Night / Multi-Frame Visual Review

Inputs:

- single-frame fallback output
- multi-frame output
- low-light scene samples
- logs with frame count and timing notes

Tasks:

- inspect ghosting, blur, brightness, noise, highlight clipping
- compare whether frame-count increases visibly help
- recommend safe defaults per device class

Outputs:

- scene-by-scene findings
- suggested frame counts and exposure budgets

### 4. Live Photo Motion Review

Inputs:

- Live still
- motion segment or frame sequence
- sidecar metadata

Tasks:

- check still/motion alignment
- check motion duration and perceived smoothness
- identify obvious timestamp offset
- verify watermark behavior if rendered into motion

Outputs:

- alignment findings
- recommended pre/post shutter window changes

### 5. Thumbnail And Capture Feedback Visual QA

Inputs:

- screen recordings during capture
- saved media paths
- dev log timing

Tasks:

- verify thumbnail does not roll back to preview
- verify capture feedback feels immediate
- identify stale image flashes or wrong media opened by thumbnail tap

Outputs:

- issue list with deterministic reproduction steps
- handoff to session/UI agents if state precedence needs adjustment

### 6. UI Preview Effect Review

Inputs:

- screenshots with preview filter approximation
- Tone Lab / Lens Lab screenshots
- saved output paired with preview screenshot

Tasks:

- check whether preview approximation sets correct expectation
- inspect UI overlap with preview effect hints
- verify unsupported/degraded copy is understandable

Outputs:

- annotated screenshots
- copy/layout change requests

## Report Format

```text
# Capability Kernel 2.0 Visual QA Report

## Build
- branch/commit:
- APK:
- device:
- date:

## Scenarios
- Photo filter:
- Live:
- Night:
- Portrait:
- Video:

## Findings
- CQA-001 [P0/P1/P2]: title
  - scenario:
  - evidence:
  - expected:
  - recommended deterministic change:
  - owner:

## Pass Criteria
- no P0 saved-output failures
- no thumbnail rollback
- Live complete/degraded status matches observed output
- filter capture output is visibly consistent with selected look
- portrait/night artifacts are documented before claims of quality improvement
```

## Handoff Rules

Good handoff to non-multimodal agents:

```text
In `FilterRenderSpec`, reduce default custom-vivid saturation multiplier from 1.20 to 1.12 and lower highlightCompression from 0.20 to 0.12. Add a regression test asserting the profile resolves to those exact values.
```

Bad handoff:

```text
Make the filter more premium.
```

The multimodal reviewer must translate visual judgment into deterministic file, value, behavior, or test instructions.

## Non-Blocking Rule

Text-only 2.0 implementation can proceed without this review when it is building:

- contracts
- capability resolution
- buffer policy
- transaction semantics
- diagnostics
- pure tests
- fake processor behavior

Do not claim final image quality, portrait quality, night quality, or Live motion polish until this review or equivalent human/device review is complete.

