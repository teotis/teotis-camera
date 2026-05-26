# Package 01 - Saved Photo Writeback Honesty

## Package ID

`01-saved-photo-writeback-honesty`

## Goal

Fix the saved-photo Color Lab / PhotoAlgorithm mask-aware path so `scene-mask:saved=applied` means the output JPEG pixels were actually written. Keep no-mask, unavailable, failed, and editor-not-mask-aware paths successful and honestly annotated.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/SceneMaskTestUtils.kt`
- Your coordinator status file: `docs/plans/scene-mask-honesty-repair-orchestration/status/01-saved-photo-writeback-honesty.md`
- `docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`

## Forbidden Paths

- Preview analysis files owned by package 02.
- Verification scripts/docs owned by package 03.
- `INDEX.md` and other package status files.
- Any mode plugin, session kernel, or device adapter file.

## Dependencies

None. Can run in wave 1 with package 02.

## Branch / Worktree Policy

- Branch: `agent/scene-mask-honesty-repair/01-saved-photo-writeback-honesty`
- Worktree: `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/01-saved-photo-writeback-honesty`
- Base: `main`

## Implementation Requirements

1. Inspect `AndroidPhotoAlgorithmEditor.apply(...)` and mirror its durable output behavior in the mask-aware path.
2. Change `AndroidPhotoAlgorithmEditor.applyWithMask(...)` so it:
   - applies mask-aware style to the bitmap;
   - encodes the mutated bitmap to JPEG;
   - writes bytes back to the output target through the same durable writer used by the global path;
   - preserves or restores EXIF where the existing editor pattern does so;
   - returns failure if output is unavailable.
3. Preserve the fallback behavior in `PhotoAlgorithmPostProcessor.process(...)`:
   - mask unavailable -> global render succeeds with `scene-mask:saved=unsupported` and reason;
   - mask failed -> global render succeeds with `scene-mask:saved=degraded` and reason;
   - editor not mask-aware -> global render succeeds with `scene-mask:saved=degraded:editor-not-mask-aware`.
4. Add or update tests that prove actual saved output bytes change for a synthetic masked Color Lab case. Do not rely only on invocation counts or pipeline notes.
5. Keep preview mask and true depth out of this package.

## Acceptance Criteria

- A test fails before the fix and passes after, proving mask-aware `PhotoAlgorithm` writes changed JPEG output.
- Pipeline notes do not say `scene-mask:saved=applied` when the mask-aware write fails.
- No-mask/global behavior remains compatible with existing tests.
- Downstream metadata and output handle behavior are preserved.
- No package changes outside allowed paths.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
```

## Expected Evidence Pack

- Worktree path, branch, base commit, commit hash.
- Changed files.
- Diff summary.
- Test commands and pass/fail summary.
- Explicit statement that `scene-mask:saved=applied` now requires durable output write success.
- Any remaining visual QA risks.

