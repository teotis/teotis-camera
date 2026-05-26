# Package Status: 02-recipe-single-truth

- **Agent**: external split-package agent (accepted by post-merge follow-up reconciliation)
- **Status**: accepted locally — positive implementation result merged to mainline
- **Started**: 2026-05-25
- **Completed**: 2026-05-26 (reconciliation)

## Worktree

- Path: mainline (no isolated worktree; result merged via approved upgrade branch `feat/rendering-2-0-upgrade`)
- Branch: `feat/rendering-2-0-upgrade`

## Accepted Evidence

Code evidence on mainline confirms the positive implementation:

- `RenderRecipe.from(ShotResult)` exists and is used by `PhotoAlgorithmPostProcessor.kt:95` as a fallback path.
- `PerceptualColorRecipe` metadata migrated to canonical `recipe.` prefix with backward compatibility for old `recipeToneLift` style tags.
- `PhotoAlgorithmPostProcessor.decidePhotoAlgorithmWork(...)` at line 87 parses recipe from tags, then falls back to `RenderRecipe.from(result)` at line 95.
- `RenderRecipe` test and `EffectBridge` test confirm round-trip metadata behavior.
- Commit: `ce250f3` (Render Recipe Single Truth)

## Verification

- Commands run (from approved upgrade index verification results):
  - `:core:settings:test` (PerceptualColorRecipeTest, StyleColorPipelineTest, ColorLabSpecTest) — PASS
  - `:core:effect:test` (RenderRecipeTest, EffectBridgeTest) — PASS
  - `:app:testDebugUnitTest` (PhotoAlgorithmPostProcessorTest) — PASS
  - `:app:assembleDebug` — BUILD SUCCESSFUL

## Remaining Limits

- Old V2 audit noted `PhotoAlgorithmPostProcessor` still has local `parseRecipeFromTags(...)` — the reconciliation accepts that `RenderRecipe.from(result)` is now the canonical fallback, but local parsing remains for backward compatibility.
- Real-device verification of recipe round-trip in saved JPEG metadata remains pending.

## Self-Certification

- [x] Only touched allowed paths (status file update only)
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files

## Unresolved Risks

- Local `parseRecipeFromTags` and canonical `RenderRecipe.from` coexistence may cause drift if new recipe fields are added without updating both paths.
- Real-device metadata inspection to confirm `recipe.*` tags in saved EXIF/custom tags remains pending.
