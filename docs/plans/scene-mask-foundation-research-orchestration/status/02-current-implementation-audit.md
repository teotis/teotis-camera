# Package Status: 02-current-implementation-audit

- **Agent**: Claude Code (background)
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/replicated-tickling-bonbon
- Branch: worktree-replicated-tickling-bonbon

## Changes

- git status: no runtime/test files modified
- git diff --stat: (research-only package, no code changes)
- Changed files: only this status file

## Verification

- Commands run:
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.SceneMaskContractsTest` → BUILD SUCCESSFUL (13s)
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.SceneMaskPayloadTest --tests com.opencamera.app.camera.SceneMaskTypeCollisionTest --tests com.opencamera.app.camera.MaskAwarePortraitRenderMathTest` → BUILD FAILED (compilation error in `SessionPreviewRenderModel.kt` — merge conflict markers at lines 59-63, unrelated to SceneMask)
- Test results:
  - `core:media:SceneMaskContractsTest` — all 12 tests PASSED
  - `app:testDebugUnitTest` (4 test classes) — could not run due to pre-existing merge conflict in `SessionPreviewRenderModel.kt`

## Current Implementation Findings

### Component Status Table

| Component | File | Status | Notes |
|---|---|---|---|
| `SceneMaskContracts` (core types) | `core/media/.../SceneMaskContracts.kt` | **real** | `SceneMaskRole`, `SceneMaskQuality`, `SceneMaskTransform`, `SceneMaskDescriptor`, `SceneMaskPayload`, `SceneMaskSupport`, `SceneMaskCapability`, `SceneMaskPipelineNotes` — all complete, tested, metadata round-trip verified |
| `SceneMaskContractsTest` | `core/media/.../SceneMaskContractsTest.kt` | **real** | 12 tests: metadata round-trip, confidence clamping, capability representations, pipeline note formatting — all pass |
| `PreviewSceneMaskSource` (interface) | `app/.../PreviewSceneMaskSource.kt` | **real** | Clean interface: `capability`, `start`, `stop`, `latestMask`, `onAnalyzeFrame` |
| `PreviewSceneMaskConfig` | `app/.../PreviewSceneMaskSource.kt:23` | **real** | Data class with sensible defaults (256x256, 8fps, mlkit-selfie) |
| `PreviewSceneMaskPayload` | `app/.../PreviewSceneMaskSource.kt:30` | **real** | Confidence mask bytes, rotation, timestamp, diagnostics, `toDescriptor()` with quality label |
| `PreviewSceneMaskCapability` → `SceneMaskSupport` mapping | `app/.../PreviewSceneMaskSource.kt:17` | **real** | `READY→SUPPORTED`, `DEGRADED→DEGRADED`, `UNSUPPORTED→UNSUPPORTED` — tested |
| `toSnapshot()` stale detection | `app/.../PreviewSceneMaskSource.kt:74` | **real** | Stale threshold (500ms default), sets `isStale`, appends pipeline note |
| `NoOpPreviewSceneMaskSource` | `app/.../NoOpPreviewSceneMaskSource.kt` | **real** | Reports `UNSUPPORTED`, returns null, does not close ImageProxy (correct — no-op) |
| `MlKitSelfiePreviewSceneMaskSource` | `app/.../MlKitSelfiePreviewSceneMaskSource.kt` | **partial** | Uses `STREAM_MODE` + `enableRawSizeMask()`. Has throttling via `inferenceInFlight` atomic. BUT: (1) does NOT close `ImageProxy` — resource leak; (2) ignores `targetWidth`/`targetHeight` from config; (3) `maxFps` from config is unused |
| `SavedPhotoSceneMaskProvider` (interface) | `app/.../SavedPhotoSceneMaskProvider.kt` | **real** | Clean interface with `SceneMaskResult` sealed hierarchy |
| `SavedPhotoMaskPixels` | `app/.../SavedPhotoSceneMaskProvider.kt:28` | **real** | Implements `SceneMaskPayload`, `alphaAt`/`sampleAlpha`, coordinate mapping |
| `SceneMaskCoordinateMapper` | `app/.../SavedPhotoSceneMaskProvider.kt:83` | **real** | Scales mask→target coordinates with clamping — tested |
| `NoOpSavedPhotoSceneMaskProvider` | `app/.../SavedPhotoSceneMaskProvider.kt:96` | **real** | Returns `Unavailable("no-op-provider")` |
| `MlKitSavedPhotoSceneMaskProvider` | `app/.../MlKitSavedPhotoSceneMaskProvider.kt` | **real** | Uses `SINGLE_IMAGE_MODE` (correct for still image), `enableRawSizeMask()`, subject ratio check (< 2% → Unavailable), confidence averaging |
| `MaskAwarePhotoAlgorithmEditor` (interface) | `app/.../MaskAwarePhotoAlgorithmEditor.kt` | **partial** | Interface exists, `AndroidPhotoAlgorithmEditor` implements it, BUT `applyWithMask` only modifies in-memory bitmap — does NOT write back to output JPEG. Caller (`PhotoAlgorithmPostProcessor.process`) only attaches metadata tags |
| `MaskAwarePortraitRenderEditor` (interface) | `app/.../MaskAwarePortraitRenderEditor.kt` | **real** | Interface exists, `AndroidPortraitRenderEditor` implements it, `applyWithMask` DOES write back to JPEG via `writeEncodedBytes` (lines 486-497) |
| `PreviewSceneMaskSnapshot` (effect module) | `core/effect/.../PreviewEffectModel.kt:71` | **real** | Data class with `UNAVAILABLE` sentinel, consumed by `PreviewEffectAdapter` |
| `SessionPreviewRenderModel` bridge | `app/.../SessionPreviewRenderModel.kt` | **blocked** | Merge conflict at lines 59-63 (HEAD vs b24d2bd) prevents compilation — not SceneMask-specific |
| `PreviewSceneMaskSourceTest` | `app/.../PreviewSceneMaskSourceTest.kt` | **real** | Tests NoOp behavior, config defaults, payload equality, capability mapping, null-plane handling |
| `SceneMaskPayloadTest` | `app/.../SceneMaskPayloadTest.kt` | **real** | Tests sample alpha bounds, uniform mask, center subject mask, coordinate mapper — proves math |
| `SceneMaskTypeCollisionTest` | `app/.../SceneMaskTypeCollisionTest.kt` | **real** | Verifies app/core types are distinct, `SavedPhotoMaskPixels` implements `SceneMaskPayload` |
| `MaskAwarePortraitRenderMathTest` | `app/.../MaskAwarePortraitRenderMathTest.kt` | **real** | Tests smoothstep mapping, center vs corner blur weight, coordinate mapper — proves mask→render math |
| `SceneMaskTestUtils` | `app/.../SceneMaskTestUtils.kt` | **real** | Factory for synthetic masks (uniform, center-subject, left-right split) + `FakeSavedPhotoSceneMaskProvider` |
| `PhotoAlgorithmPostProcessor` mask-aware routing | `app/.../PhotoAlgorithmPostProcessor.kt:138-174` | **partial** | Correctly routes to mask-aware editor when available, falls back with degraded notes, writes metadata tags. BUT editor itself doesn't persist bitmap changes |

### Audit Question Answers

**Q1: Are mask pixels kept out of SessionState and persisted settings?**
YES. `SessionState` (SessionContracts.kt:196) contains no mask pixel fields. Mask data flows only through the post-processor pipeline. `PreviewSceneMaskSnapshot` is passed separately to `previewOverlayRenderModel()`.

**Q2: Does preview segmentation close ImageProxy exactly once?**
NO — this is a resource leak bug. `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame()` never calls `image.close()` on any code path (early returns at lines 65, 70-71, 75-78, 82-85; success listener at line 91; failure listener at line 124). CameraX `ImageAnalysis` expects the analyzer to close the proxy. `NoOpPreviewSceneMaskSource` correctly does NOT close (comment: "fanout owns ImageProxy lifecycle").

**Q3: Does MlKitSelfiePreviewSceneMaskSource respect PreviewSceneMaskConfig, target resolution, throttling, and background execution?**
PARTIALLY.
- Throttling: YES — `inferenceInFlight` atomic prevents concurrent inference; drops frames when busy.
- Background execution: YES — ML Kit task API runs callbacks on background threads.
- Target resolution: NO — `config.targetWidth`/`config.targetHeight` are logged but never used; segmenter processes at original image resolution.
- `maxFps`: NO — `config.maxFps` is never referenced; frame pacing is not implemented.

**Q4: Does the saved-photo provider use correct still-image mode and write results into actual output JPEG paths?**
PARTIALLY.
- Still-image mode: YES — `MlKitSavedPhotoSceneMaskProvider` uses `SINGLE_IMAGE_MODE` (correct).
- Mask availability check: YES — rejects if subject ratio < 2%.
- Portrait render writeback: YES — `AndroidPortraitRenderEditor.applyWithMask` compresses to JPEG and calls `writeEncodedBytes(target, encodedBytes)` (lines 486-497).
- Photo algorithm writeback: NO — `AndroidPhotoAlgorithmEditor.applyWithMask` modifies bitmap in-memory only, never writes to JPEG. This is a confirmed bug.

**Q5: Are metadata/pipeline notes honest for applied/degraded/unsupported/failed?**
YES. The mapping is well-tested in `PhotoAlgorithmPostProcessorTest`:
- Mask available + editor mask-aware → `scene-mask:saved=applied`
- Mask unavailable → `scene-mask:saved=unsupported`
- Mask failed → `scene-mask:saved=degraded`
- Mask available + editor NOT mask-aware → `scene-mask:saved=degraded:editor-not-mask-aware`
Preview capability maps correctly: `READY→applied`, `DEGRADED→degraded`, `UNSUPPORTED→unsupported`.

**Q6: Are there duplicate app/core capability types that increase drift risk?**
YES — controlled drift. `PreviewSceneMaskCapability` (app enum: READY/DEGRADED/UNSUPPORTED) and `SceneMaskCapability` (core data class with per-feature support levels) are distinct types with a `toCoreSupport()` bridge. `SceneMaskTypeCollisionTest` explicitly verifies they remain distinct and mapping works. The duplication is intentional (preview capability is simpler than the full capability model) but adds a maintenance surface.

**Q7: Do current tests prove behavior or only construction?**
MOSTLY CONSTRUCTION + MATH. Tests verify:
- Contract correctness (metadata round-trip, confidence clamping, pipeline notes)
- Data class equality/inequality
- Mask math (smoothstep, coordinate mapping, alpha sampling)
- Capability mapping
Tests do NOT verify:
- Actual pixel rendering on a bitmap (blur applied to background, not subject)
- JPEG output correctness after mask-aware rendering
- End-to-end mask resolution → editor → output file flow
- Real ML Kit segmentation integration

**Q8: Are existing known blockers from codex/documentation.md still present?**
- Mask-aware output writeback (photo algorithm): STILL PRESENT. `PhotoAlgorithmPostProcessor.applyWithMask` does not write back to JPEG.
- Metadata retention: RESOLVED for portrait render (writes metadata tags). Partially resolved for photo algorithm (writes `SceneMaskDescriptor` metadata tags but bitmap changes are lost).
- Edge softness: NOT TESTED. `MaskAwarePortraitRenderMathTest` proves smoothstep math works, but no test verifies actual pixel-level edge quality on a rendered bitmap.
- Merge conflict in `SessionPreviewRenderModel.kt` (lines 59-63): STILL PRESENT — prevents app module compilation.

### "Do Not Claim Supported Yet" List

1. **Photo algorithm mask-aware rendering persistence** — `AndroidPhotoAlgorithmEditor.applyWithMask` modifies bitmap in-memory but does not write to output JPEG. Any claim that "mask-aware photo filters are applied to saved photos" would be false.
2. **Preview mask target resolution** — `MlKitSelfiePreviewSceneMaskSource` ignores `targetWidth`/`targetHeight` from config. Claiming "preview segmentation respects target resolution" would be false.
3. **Preview mask FPS throttling** — `maxFps` config is unused. Claiming "preview segmentation is throttled to configured FPS" would be false.
4. **ImageProxy lifecycle correctness** — `onAnalyzeFrame` leaks the proxy. Claiming "preview segmentation correctly manages camera resources" would be false.
5. **Real pixel rendering verification** — No test proves actual blur/filter application on bitmap pixels. Claiming "mask-aware rendering is verified" based on math-only tests would be misleading.
6. **Edge softness quality** — Math is tested but actual rendered edge quality is not verified on real images.
7. **Depth approximation, multi-subject, semantic region** — `SceneMaskRole` has FOREGROUND, BACKGROUND, DEPTH_APPROXIMATION, SEMANTIC_REGION but only PERSON_SUBJECT is implemented. These roles are contract-only.

### "Safe to Build On" List

1. **Core contracts** — `SceneMaskContracts.kt` types, metadata serialization, pipeline notes are complete and tested.
2. **Preview mask source interface** — `PreviewSceneMaskSource` interface is clean and well-separated.
3. **Saved-photo mask provider interface** — `SavedPhotoSceneMaskProvider` with `SceneMaskResult` sealed hierarchy is production-ready.
4. **Mask math** — `SavedPhotoMaskPixels`, `SceneMaskCoordinateMapper`, smoothstep mapping are verified.
5. **Portrait render mask-aware writeback** — `AndroidPortraitRenderEditor.applyWithMask` correctly writes JPEG output.
6. **Metadata integration** — `SceneMaskDescriptor.toMetadataTags()` / `fromMetadataTags()` round-trip works; `PhotoAlgorithmPostProcessor` correctly attaches mask metadata to shot results.
7. **Capability → Support mapping** — `PreviewSceneMaskCapability.toCoreSupport()` bridge is tested and correct.
8. **ML Kit saved-photo mode** — `SINGLE_IMAGE_MODE` with raw-size mask and subject-ratio validation is correctly implemented.
9. **Test infrastructure** — `SceneMaskTestUtils` and `FakeSavedPhotoSceneMaskProvider` provide solid test doubles for future work.

## Smallest Implementation Repair Loop (if entering fix mode)

1. **Fix ImageProxy leak** in `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame` — add `image.close()` in all code paths (early returns + success + failure listeners).
2. **Fix photo algorithm mask writeback** — add `writeEncodedBytes` call after `applyStyleWithMask` in `AndroidPhotoAlgorithmEditor.applyWithMask`, mirroring the portrait render pattern.
3. **Resolve merge conflict** in `SessionPreviewRenderModel.kt` lines 59-63 — this blocks all app module compilation.
4. **Wire up target resolution** — downscale the bitmap to `config.targetWidth`×`config.targetHeight` before feeding to ML Kit segmenter in `onAnalyzeFrame`.

## Self-Certification

- [x] Only touched allowed paths (this status file only)
- [x] Did not edit forbidden paths (no runtime code, tests, INDEX.md, or other status files)
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks

- App module cannot compile due to merge conflict in `SessionPreviewRenderModel.kt` — blocks running app-level SceneMask tests until resolved.
- `codex/documentation.md` line 35 records that `PortraitRenderPostProcessorTest` and `SceneMaskPayloadTest` previously failed; cannot re-verify due to app compilation blocker.
- `MlKitSelfiePreviewSceneMaskSource` resource leak (ImageProxy not closed) will cause CameraX frame starvation over time.
- Photo algorithm mask-aware path creates a false impression of applied rendering — pipeline notes say "applied" but the bitmap change is discarded.
