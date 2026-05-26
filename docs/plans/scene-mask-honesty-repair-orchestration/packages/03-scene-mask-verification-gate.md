# Package 03 - Scene Mask Verification Gate

## Package ID

`03-scene-mask-verification-gate`

## Goal

Create a focused Scene Mask honesty gate and update planning/status docs so future agents cannot mistake partial mask plumbing for product support. This package runs after packages 01 and 02 because it should verify their repaired behavior.

## Allowed Paths

- `scripts/verify_scene_mask_honesty.sh`
- `docs/plans/scene-mask-honesty-repair-orchestration/**`
- `docs/plans/INDEX.md`
- `codex/documentation.md`
- Existing Scene Mask plan docs under `docs/plans/2026-05-25-scene-mask*.md`
- `docs/plans/2026-05-25-preview-subject-mask-pipeline.md`
- `docs/plans/2026-05-25-saved-photo-mask-rendering.md`
- Your coordinator status file: `docs/plans/scene-mask-honesty-repair-orchestration/status/03-scene-mask-verification-gate.md`
- `docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`

## Forbidden Paths

- Runtime Kotlin code owned by packages 01 and 02 unless a tiny documentation-driven compile fix is required and explicitly noted.
- Other package status files.
- Stage transition docs beyond recording this verified loop.

## Dependencies

- `01-saved-photo-writeback-honesty`
- `02-preview-analysis-budget-contract`

## Branch / Worktree Policy

- Branch: `agent/scene-mask-honesty-repair/03-scene-mask-verification-gate`
- Worktree: `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/03-scene-mask-verification-gate`
- Base: integration branch after packages 01 and 02 merge.

## Implementation Requirements

1. Add `scripts/verify_scene_mask_honesty.sh` that runs the focused local gates for:
   - `SceneMaskContractsTest`;
   - `PhotoAlgorithmPostProcessorTest`;
   - `SceneMaskPayloadTest`;
   - `SceneMaskTypeCollisionTest`;
   - `MaskAwarePortraitRenderMathTest`;
   - `PreviewSceneMaskSourceTest`;
   - `PreviewAnalysisFanoutTest`;
   - `CameraXCaptureAdapterLivePhotoTest` and `LivePreviewFrameSourceTest` if package 02 touched fanout wiring.
2. The script must be runnable as:
   ```bash
   rtk ./scripts/verify_scene_mask_honesty.sh
   ```
   Internal Gradle commands must follow the repository command policy.
3. Update docs to record the repaired truth:
   - Phase-1 Scene Mask is person-subject only.
   - Saved-photo mask must re-segment saved bitmap and write output bytes.
   - Preview mask is approximate and must not become saved output truth.
   - `ImageProxy` lifecycle owner is `PreviewAnalysisFanout`.
   - True depth / semantic region / non-person subject segmentation remain unsupported unless separately implemented.
4. Run the focused script and, if feasible, `rtk ./gradlew --no-daemon :app:assembleDebug`.
5. Do not declare real-device visual pass. Leave real-device Color Lab/person/background edge quality as a separate QA item.

## Acceptance Criteria

- `scripts/verify_scene_mask_honesty.sh` exists, is executable, and passes locally or records a precise blocker.
- Documentation no longer overclaims Scene Mask support.
- `docs/plans/INDEX.md` links this orchestration package.
- `codex/documentation.md` records this loop as an implementation repair package, not a product pass.
- No package changes outside allowed paths.

## Verification Commands

```bash
rtk ./scripts/verify_scene_mask_honesty.sh
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

- Worktree path, branch, base commit, commit hash.
- Changed files.
- Diff summary.
- Verification script output summary.
- Documentation changes summary.
- Explicit statement that real-device visual QA remains pending.

