# External Audit Claim Verification

Date: 2026-05-27
Scope:
- `structural_abstraction_architect_report.html`
- `docs/plans/deep-audit-optimization-orchestration/`
- `docs/plans/2026-05-27-comprehensive-analysis-report.md`

This is a verification pass over external-agent reports. The reports are treated as untrusted input; each item below is classified by current code evidence.

## Executive Judgment

The reports contain useful architectural signals, but their aggregate metrics and several current-state claims are unreliable. Use them as a candidate backlog, not as an implementation contract.

Highest-confidence actionable findings:
- `CameraXCaptureAdapter.kt`, `SessionUiRenderModel.kt`, `DefaultCameraSession.kt`, and `MainActivity.kt` are genuinely large and worth gradual decomposition.
- The effect/capture pipeline still has repeated representation and metadata bridging pressure: typed `EffectSpec`, `PostProcessSpec`, and `customTags`.
- App-layer domain post-processing is real: `PhotoAlgorithmPostProcessor.kt`, `PortraitRenderPostProcessor.kt`, and `PhotoWatermarkPostProcessor.kt` contain substantial rendering/domain behavior in `app`.
- Feature modules currently have no direct unit tests under `feature/*/src/test`.
- Session owns document-batch specific intents and state, which is a real example of mode-specific knowledge accumulating in the session kernel.

Claims that should not be acted on as written:
- The project does not have `10,938` Kotlin files or `571,619` source lines in the main source tree. Current main Kotlin/Java source count is about `269` files and `68,738` lines when generated/hidden/planning areas are excluded.
- The reported `79%` "coverage" is not a measured JaCoCo/Kover coverage number; it is a test-file/main-file ratio from the report.
- `core:effect` no longer directly depends on `core:device`; it already has an effect-owned `EffectCapabilityQuery`.
- Tap-to-focus is not an `UNSUPPORTED` stub now. `DeviceCommand.ApplyPreviewMetering` calls CameraX `startFocusAndMetering`.
- Thumbnail open is not purely `latestCapturePath/latestVideoPath + File.exists`; saved media prefers `ThumbnailSource.SavedMedia.renderUri` when it is a `content://` URI, falling back to file paths.
- Document batch organizer UI and move/remove actions are present and bound.
- "Introduce event bus", "create core:shared", "state machine framework", "MapStruct/codegen", and "Hilt migration" are not validated by the reports with enough deletion proof. Treat as speculative unless a smaller local pain case is shown.

## Evidence Ledger

### Baseline Metrics

- Main source files counted with `rg --files -g '*.kt' -g '*.java'` excluding hidden/build/docs/codex/specs/public/gradle areas: `269`.
- Main source lines from that file set: `68,738`.
- Test files: `app=67`, `core=56`, `feature=0`.
- Feature mode source: `7` Kotlin files, `3,260` lines.
- Modules in `settings.gradle.kts`: `:app`, eight `:core:*` modules, seven `:feature:*` modules. No `:core:shared`.

### Confirmed Current Problems

1. Large classes/files are real.
   - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`: `3,134` lines.
   - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`: `2,289` lines.
   - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`: `1,604` lines.
   - `app/src/main/java/com/opencamera/app/MainActivity.kt`: `851` lines.

2. `CameraSessionCoordinator` is a boundary pressure point, but not a UI-layer violation in the simple sense.
   - It collects `session.effects`, translates them into `DeviceCommand`, listens to `cameraAdapter.events`, and dispatches `SessionIntent` back to the session.
   - It also reads `session.state.value.activeDeviceCapabilities` when syncing capabilities.
   - This is app composition glue. The real risk is that the bridge can become a hidden mini-kernel if more policy is added there.

3. `SessionContracts.kt` exposes many cross-module types.
   - It imports device, capability, effect, media, mode, and settings types.
   - `SessionState` itself is not 50+ direct fields in current code; it has 21 constructor fields plus presentation substate and compatibility getters.
   - The concern is coupling and API surface, not the exact field count reported.

4. Effect pipeline representation pressure is real.
   - `EffectSpec` is typed.
   - `EffectBridge.toMetadataTags()` flattens it into string metadata.
   - `EffectBridge.toPostProcessSpec()` drops to a smaller post-process shape.
   - Post-processors then re-read `customTags`, for example filter specs, frame ratio, watermark, selfie mirror, document/portrait markers.

5. App-layer post-processing domain logic is real.
   - `PhotoAlgorithmPostProcessor.kt`: `1,120` lines.
   - `PortraitRenderPostProcessor.kt`: `993` lines.
   - `PhotoWatermarkPostProcessor.kt`: `1,295` lines.
   - This is a good structural candidate, but the migration target must preserve Android bitmap/EXIF/file dependencies.

6. Feature modules have no direct tests.
   - `feature/*` contains seven mode plugin source files and no `*Test.kt`.
   - Some mode behavior is covered indirectly through `core/mode`, `core/session`, and app tests, so "feature has no tests" is true but "mode behavior is untested" is too strong.

7. Document-batch mode knowledge lives in session.
   - `SessionIntent` includes `DocumentBatchClear`, `DocumentBatchRemoveItem`, `DocumentBatchMoveItem`, `DocumentBatchReorder`, and `DocumentBatchFinish`.
   - `DefaultCameraSession` handles these intents directly.
   - This supports the structural report's broader claim that mode-specific concerns are accumulating in session.

## Invalid Or Outdated Claims

1. Tap-to-focus chain not wired.
   - Current code forwards `SessionEffect.ApplyPreviewMetering` to `DeviceCommand.ApplyPreviewMetering`.
   - `CameraXCaptureAdapter.applyPreviewMetering()` creates CameraX metering points, checks support, calls `startFocusAndMetering`, and emits `PreviewMeteringCompleted`.
   - Tests exist in `DefaultCameraSessionTest`, `PreviewRecoverySessionProcessorTest`, and `CameraSessionCoordinatorTest`.

2. Thumbnail click is disconnected from MediaStore URI.
   - `MainActivity.openLatestGalleryMedia()` uses `galleryOpenTargetFor(source = latestThumbnailSource, savedMediaType = latestSavedMediaType)`.
   - `galleryOpenTargetFor()` prefers `SavedMedia.renderUri` when it is `content://`, then falls back to file URI or absolute path.
   - The fallback still exists, but the report's "pure FileProvider path" claim is outdated.

3. Document organizer UI not bound.
   - `DocumentBatchOrganizerRenderer` exists.
   - `MainActivity` initializes it and wires remove/up/down actions to session intents.
   - `MainActivityRenderer` controls the organizer route visibility.
   - Render-model tests and router tests exist.

4. `core:effect` depends on `core:device`.
   - `core/effect/build.gradle.kts` depends on `:core:settings` and `:core:media`, not `:core:device`.
   - `EffectCapabilityQuery` is already effect-owned.

5. "Coverage 79%" and "target 90%" as coverage metrics.
   - No JaCoCo/Kover configuration was found in the inspected build files.
   - The report's table computes test files divided by main files, which is not code coverage.

## Recommended Next Actions

P0, safe to delegate after scoping:
- Add direct unit tests for the seven feature mode plugins. Start with behavior already duplicated across plugins: `EffectBridge` tags, `PostProcessSpec`, graph template, and mode session events.
- Split `CameraXCaptureAdapter` around existing internal seams only after tests pin behavior: preview binding/provider lifecycle, metering/brightness, still capture execution, video recording, and thumbnail/live helpers.
- Add a small architecture guard or test that verifies `core:effect` stays independent from `core:device`, since this was already fixed and is worth preventing from regressing.

P1, keep Codex-reviewed:
- Design a narrow "effect payload carried through capture" migration before changing the pipeline. The proof target should be deleting selected `customTags` re-parsing in post-processors, not introducing a generic abstraction.
- Move or wrap app-layer post-processing domain logic only after separating Android-bound bitmap/EXIF/file concerns from pure render decisions.
- Decide whether document-batch intents should move behind a mode-owned command extension or remain session-owned for now. The current design works, but it is a good test case for "mode-specific intent delegation".

Do not delegate as implementation yet:
- Event bus.
- `:core:shared`.
- Generic state machine framework.
- MapStruct/code generation.
- Hilt/DI migration.
- Parallelizing post-processing for claimed percentage gains without measured bottleneck data.
