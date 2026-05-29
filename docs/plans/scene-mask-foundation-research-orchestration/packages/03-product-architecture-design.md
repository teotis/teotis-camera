# Package 03 — Product Architecture Design

## Package ID

`03-product-architecture-design`

## Goal

Translate Scene Mask into OpenCamera product and architecture semantics for Portrait, Humanistic, and Color Lab without copying vendor claims or creating a second hidden session kernel.

## Dependencies

- Wait for `01-backend-capability-matrix`.
- Wait for `02-current-implementation-audit`.

## Allowed Paths

- Read any repository file.
- Write only `docs/plans/scene-mask-foundation-research-orchestration/status/03-product-architecture-design.md`.

## Forbidden Paths

- Runtime code and tests.
- `INDEX.md`.
- Other package status files.

## Design Scope

Cover these product mappings:

- Portrait:
  - subject protection;
  - background blur/style;
  - depth/blur strength slider;
  - light spot / highlight shape if backed by mask.
- Humanistic:
  - subtle subject/background tonal separation;
  - 35mm/50mm/85mm profile mapping without overclaiming ZEISS/品牌联名-style internals.
- Color Lab:
  - subject protection;
  - stronger background color/tone changes;
  - preview approximation vs saved-photo truth.

## Required Architecture Rules

- UI renders state and dispatches intents only.
- Session Kernel owns runtime state and transitions, but not mask pixel buffers.
- Device adapter owns CameraX/Camera2 integration.
- Media pipeline owns saved output postprocessing.
- Any hardware-dependent capability uses explicit `supported`, `degraded`, or `unsupported` semantics.
- Runtime state and persisted settings remain separate.
- No "depth slider is true depth" claim when only 2D segmentation exists.

## Required Output In Status File

- Proposed capability taxonomy:
  - `SceneMaskRole`;
  - quality / freshness;
  - backend;
  - preview vs saved output;
  - product feature support label.
- Product copy and diagnostics rules:
  - what UI may show;
  - what pipeline notes must say;
  - what must remain hidden or degraded.
- Architecture placement:
  - contracts;
  - preview source;
  - saved-photo provider;
  - render recipe / effect model integration;
  - diagnostics.
- Migration path from current implementation to desired design:
  - repair-first items;
  - optional future items;
  - explicitly deferred items.

## Acceptance Criteria

- Design is compatible with current Stage 7 architecture boundaries.
- Design explicitly says how OPPO/vivo-like features are translated, not copied.
- Design gives a clear answer for the user's prompt: whether "Scene Mask Segmentation" is the right foundation and under what honesty limits.
- Design identifies the smallest next implementation loop if the user approves coding.

## Verification Commands

```bash
rtk rg -n "SceneMask|PreviewSceneMask|Portrait|ColorLab|RenderRecipe|filterSpec|pipeline notes|supported|degraded|unsupported" app core docs/plans codex
rtk sed -n '1,220p' codex/Product-2.0-Standard.md
```

## Expected Evidence Pack

Use the standard status template and add `## Product Architecture Proposal`.

