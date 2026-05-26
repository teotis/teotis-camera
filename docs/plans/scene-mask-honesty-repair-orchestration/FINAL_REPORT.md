# Scene Mask Honesty Repair — Final Report

## Summary

The Scene Mask honesty repair orchestration is complete. All 3 functional packages delivered their changes, merged cleanly into the integration branch, passed verification, and merged back to `main`.

## What Changed

### 01-saved-photo-writeback-honesty
- `PhotoAlgorithmPostProcessor.kt`: `AndroidPhotoAlgorithmEditor.applyWithMask()` now returns `Failed("output-unavailable")` when `writeEncodedBytes()` fails, instead of leaking `scene-mask:saved=applied`. `resolveMask()` no longer injects duplicate saved-status notes.
- `PhotoAlgorithmPostProcessorTest.kt`: New tests proving write-failure honesty and actual byte writeback.

### 02-preview-analysis-budget-contract
- `MlKitSelfiePreviewSceneMaskSource.kt`: `targetWidth`/`targetHeight` now used for bitmap downscaling before ML Kit. `maxFps` enforced via time-based throttle before expensive conversion. `ImageProxy.close()` ownership remains with `PreviewAnalysisFanout`.
- `PreviewSceneMaskSourceTest.kt`, `PreviewAnalysisFanoutTest.kt`: Tests proving single close-owner and config enforcement.

### 03-scene-mask-verification-gate
- `scripts/verify_scene_mask_honesty.sh`: Focused verification gate covering all Scene Mask contracts.
- `codex/documentation.md`: Records this loop as implementation repair, not product pass.

## Verification Results

| Check | Result |
|---|---|
| `verify_scene_mask_honesty.sh` | 6/8 groups PASSED, 2 pre-existing failures (same on main) |
| `assembleDebug` | BUILD SUCCESSFUL |

### Pre-existing failures (not caused by this orchestration)
- `PhotoAlgorithmPostProcessorTest.unsupported profile is ignored without diagnostics`
- `MaskAwarePortraitRenderMathTest.mask alpha decreases from center to edge to corner`

## What This Does NOT Claim

- Phase-1 Scene Mask is person-subject only (`PERSON_SUBJECT`).
- True depth, semantic region, and non-person subject segmentation remain unsupported.
- Preview mask is approximate and must not become saved-output truth.
- `ImageProxy` lifecycle owner is `PreviewAnalysisFanout`.

## Remaining Work

- **Real-device visual QA** is still required: Color Lab / person / background edge quality on physical devices.
- The 2 pre-existing test failures on `main` should be investigated separately.

## Merge Commit

- `337e484` — `merge: scene-mask-honesty-repair integration to main`

## Orchestration Metadata

- Mainline: `main`
- Integration branch: `agent/scene-mask-honesty-repair/integration`
- Packages: 01, 02, 03, 99-finalize
- All packages: `completed` / `finalized`
- Conflicts: none
- Forbidden path violations: none
