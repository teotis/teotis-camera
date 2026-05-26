# Package Status: 03-preview-fidelity-honesty

- **Agent**: external split-package agent + agent-render-v2-03-preview (investigation)
- **Status**: accepted locally for approximate preview/fidelity honesty — NOT true saved-output preview parity
- **Started**: 2026-05-25
- **Completed**: 2026-05-26 (reconciliation)

## Worktree

- Path: investigation worktree at `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/agent-render-v2-03-preview` (investigation only, no code changes)
- Branch: `worktree-agent-render-v2-03-preview` (clean, based on `bf87213`)
- Code changes landed via approved upgrade branch `feat/rendering-2-0-upgrade`

## Accepted Evidence

Code evidence on mainline confirms the positive implementation for approximate/fidelity-honest preview:

- `PreviewColorTransform` data class exists at `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt` with fidelity states: `NONE`, `MASK_AWARE`, `FALLBACK`, `APPROXIMATE`, `IDENTITY`.
- `PreviewColorFidelity` enum models honest fidelity states rather than claiming true parity.
- `PreviewEffectAdapter.buildRecipeColorTransform(recipe)` at line 65 builds a recipe-backed color transform.
- `PreviewEffectAdapter` at lines 87-92 sets `PreviewColorFidelity.APPROXIMATE` when a matrix is available, `PreviewColorFidelity.DEGRADED` when only tint fallback is possible.
- `PreviewOverlayView` uses overlay tint approximation (`drawFilterOverlay()`), not a real `PreviewView` color matrix pipeline.
- Commit: `3612c32` (Color Lab Perceptual Rendering)

## What This Does NOT Mean

- Preview is still an approximate overlay/fidelity model, NOT a proven real `PreviewView` color pipeline parity path.
- The CameraX `PreviewView` supports `View.setColorFilter(ColorMatrixColorFilter)` but this is NOT wired.
- `applyColorTransformToPreview(...)` does NOT exist in the codebase.
- This is accepted for honest fidelity modeling (approximate/degraded states), not for claiming true saved-output preview parity.

## Investigation Findings (from agent-render-v2-03-preview)

The V2 agent found that preview uses only a visual overlay tint approximation (semi-transparent colored rectangle via `drawFilterOverlay()`), not a real color matrix transform. The planned implementation to wire `View.setColorFilter()` was not started.

## Remaining Limits

- Real preview color matrix application to `PreviewView` is NOT implemented.
- Preview/saved-output pixel parity cannot be claimed.
- Real-device visual acceptance of preview approximation vs saved output remains pending.

## Self-Certification

- [x] Only touched allowed paths (status file update only)
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files

## Unresolved Risks

- Preview fidelity honesty is model-level only; no real `PreviewView` color filter is applied.
- Devices unable to support stronger preview transform expose degraded approximation, but the transform itself is still overlay-based.
- Real-device Color Lab visual acceptance (corner/edge naturalness, preview vs saved alignment) remains with Codex/user.
