# Package Status: 01-saved-photo-writeback-honesty

- **Agent**: agent-01-saved-photo-writeback-honesty
- **Status**: completed
- **Started**: 2026-05-26T18:40:23Z
- **Completed**: 2026-05-27T03:30:00Z

## Worktree

- Path: `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/01-saved-photo-writeback-honesty`
- Branch: `agent/scene-mask-honesty-repair/01-saved-photo-writeback-honesty`
- Base commit: `80bea846313d46ec902a018039706203b8ce3c20`
- Commit hash: `36d1867f5fdab60bf1b8ee4d527076ecd137399e`

## Changes

- git status: clean (committed)
- git diff --stat (vs main): 2 files changed, 133 insertions, 9 deletions
- Changed files:
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`

## Diff Summary

### PhotoAlgorithmPostProcessor.kt

1. **`AndroidPhotoAlgorithmEditor.applyWithMask()`**: Changed to return honest notes on write failure. When `writeEncodedBytes()` fails, returns `Failed("output-unavailable")` with `scene-mask:saved=degraded` + `scene-mask:reason=output-unavailable` instead of leaking `scene-mask:saved=applied` from `applyStyleWithMask()`.

2. **`resolveMask()`**: Removed `SceneMaskPipelineNotes.saved(SceneMaskSupport.SUPPORTED)` from `extraNotes` when mask is `Available`. The saved status is now owned by the editor's returned notes, not the mask resolution. This prevents duplicate `scene-mask:saved=applied` and ensures the note reflects actual write success.

### PhotoAlgorithmPostProcessorTest.kt

1. **`FakeMaskAwarePhotoAlgorithmEditor`**: Changed from `PhotoAlgorithmApplied` to `ProcessorEditorResult` parameter type; added configurable `maskNotes` parameter.

2. **New test: `mask aware write failure omits applied from pipeline notes`**: Verifies that when the editor returns `Failed("output-unavailable")`, pipeline notes do NOT contain `scene-mask:saved=applied` but DO contain `scene-mask:saved=degraded`.

3. **New test: `mask aware editor writes modified bytes to file target`**: Uses `WritingFakeMaskAwareEditor` to prove actual output file bytes change after mask-aware processing.

4. **New class: `WritingFakeMaskAwareEditor`**: A test fake that writes sentinel bytes to the target file, proving the writeback contract.

## Verification

- Commands run:
  - `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest` → 19 tests completed, 1 failed (pre-existing `unsupported profile is ignored without diagnostics`, confirmed failing on main before this change)
  - `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.SceneMaskPayloadTest` → passed
- Test results: All new tests pass. Pre-existing failure is unrelated to this package.

## Evidence

- Acceptance criteria:
  - [x] A test fails before the fix and passes after, proving mask-aware PhotoAlgorithm writes changed JPEG output (`mask aware editor writes modified bytes to file target`)
  - [x] Pipeline notes do not say `scene-mask:saved=applied` when the mask-aware write fails (`mask aware write failure omits applied from pipeline notes`)
  - [x] No-mask/global behavior remains compatible with existing tests (18/19 pass; 1 pre-existing failure on main)
  - [x] Downstream metadata and output handle behavior are preserved
  - [x] No package changes outside allowed paths
- Explicit statement: `scene-mask:saved=applied` now requires durable output write success. The note is returned by the editor only when `writeEncodedBytes()` succeeds.
- Remaining visual QA risks: None (pure byte-write logic change, no pixel-level visual behavior change).

## Integration

- Integration branch: `agent/scene-mask-honesty-repair/integration`
- Merge status: pending (ready for 99-finalize)
- Cleanup status: pending

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files
- [x] Updated `status/state.tsv` consistently

## Unresolved Risks

- Pre-existing test failure `unsupported profile is ignored without diagnostics` on main branch (unrelated to this package).
