# OpenCamera 2.0 Readiness Post-Fix Review

> 日期：2026-05-22  
> 目的：复审用户已处理后的低/中成本修复状态  
> 结论：部分关键问题已收敛，但仍不能进入 2.0 GO

## Verdict

**CONDITIONAL NO GO**

已处理项显示出明显进展：RAW 已显式降级为 saved-only，多帧夜景默认能力已关闭并走单帧 fallback，后处理失败 notes 与 degraded 文案已有基础，orphaned shot failure 已进入 trace，UI 侧 focused tests 通过。

但当前仍有阻断：

1. `:core:session:test` 仍有 5 个 Night multi-frame 旧期望失败。
2. Stage 7 脚本仍失败，失败原因同上。
3. `activity_main.xml` 仍有 `android:text="Back"` 硬编码英文。
4. `FilterLab / LensLab` 内部路由名仍未真正收敛为 `StyleLab / ColorLab`，只是补了映射测试。
5. 多模态/真机材料仍未提供，不能判视觉和成片达标。

## Verification Run

| Command | Result | Notes |
| --- | --- | --- |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.ThumbnailRenderCommandTest` | Pass | UI/render focused tests pass |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.MediaSaveTransactionTest --tests com.opencamera.core.media.ShotExecutorTest --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest --tests com.opencamera.core.media.MultiFrameMergeAlgorithmProcessorTest` | Pass | media degradation/transaction focused tests pass |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest` | Pass | device translator focused tests pass |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest` | Fail | 5 Night multi-frame expectation failures |
| `rtk ./scripts/verify_stage_7_observability.sh` | Fail | blocked by same 5 `DefaultCameraSessionTest` failures |

## Cleared Or Mostly Cleared

### RAW fake entry

Status: **Mostly cleared**

Evidence:

- `Feature-Availability-Audit.md` now records RAW as `Pass(SAVED_ONLY)`.
- `SessionUiRenderModel.kt` maps `ManualControlSupport.SAVED_ONLY` to `SettingsControlAvailability.DEGRADED`.
- `SessionUiRenderModelTest` asserts RAW is degraded and summary contains `RAW / WB stay saved-only`.
- `CameraXCaptureAdapterManualRequestTest` verifies raw is stripped when not apply-capable.

Remaining:

- Real RAW/DNG remains high-cost decision, not 2.0 blocker if saved-only UI is accepted.

### Night multi-frame fake capability

Status: **Partially cleared**

Evidence:

- `DeviceCapabilities.supportsNightMultiFrame` default is now `false`.
- `Feature-Availability-Audit.md` records multi-frame as hidden/disabled by default.
- `DefaultCameraSessionTest` already has explicit fallback tests around `supportsNightMultiFrame=false`.
- `NightModePlugin` has single-frame fallback metadata such as `capturePath=single-frame-fallback` and `MergeStrategy=bright-single-frame`.

Remaining blocker:

- 5 default Night tests still expect multi-frame plans under default capabilities. These tests are now inconsistent with the product decision to default-disable multi-frame.

### Postprocess failure observability

Status: **Mostly cleared in code contracts; needs broader validation**

Evidence:

- `ShotResult.hasPostProcessFailures()` and `postProcessFailureSummary()` exist.
- Postprocessors emit `*:failed:*` pipeline notes.
- `DefaultCameraSession.handleShotCompleted()` maps failed photo postprocess to `Photo saved (degraded)`.
- `MediaPipelineContracts.kt` adds degraded notes for `merge:placeholder` and `live:degraded=metadata-only`.

Remaining:

- Need ensure transaction status/warnings are visible in every relevant UI/dev-log path.
- Needs true saved-image and thumbnail validation on device.

### Orphaned shot failure trace

Status: **Cleared**

Evidence:

- `DefaultCameraSession.handleShotFailed()` records `shot.failed.orphaned` when `activeShot == null`.
- `DefaultCameraSessionTest` asserts orphaned trace exists.

### Live still-only fallback

Status: **Partially cleared**

Evidence:

- `CameraXCaptureAdapter` adds `device:live-photo=still-only-fallback`.
- `LivePhotoBundle.temporalNotes()` emits `live:degraded=metadata-only`.
- `DefaultCameraSession` maps still-only live result to `Live photo saved (still only)`.
- `ShotExecutorTest` covers `live:status=still-only-fallback`.

Remaining:

- Real motion remains high-cost.
- Need UI/settings surface to ensure user sees this before or after capture.
- Need true sidecar scoped-storage validation on device.

## Still Blocking

### B1. Night multi-frame tests are stale against new default capability

Failed tests:

- `night mode cycles profiles and emits multi frame shot plan`
- `night mode uses shared photo countdown before multi frame capture`
- `night mode keeps multi frame plan while applying tertiary frame ratio`
- `night mode pro variant carries manual draft into multi frame capture`
- `night multi frame permission loss clears stale diagnostics and records capture failure trace`

Root cause:

- Product/architecture decision changed default capability to `supportsNightMultiFrame=false`.
- These tests still create a default session and expect `ShotKind.MULTI_FRAME_CAPTURE`, `capturePath=multi-frame`, frame count 6/8/12, and multi-frame diagnostics.

Required fix:

- Split tests into two groups:
  - default device path expects single-frame fallback;
  - explicit `DeviceCapabilities(supportsNightMultiFrame=true)` path expects multi-frame.
- Keep at least one positive multi-frame test with explicit capability so the old path remains covered.

Impact:

- Blocks `:core:session:test`.
- Blocks `verify_stage_7_observability.sh`.
- Blocks any 2.0 readiness upgrade.

### B2. XML hard-coded `Back` remains

Evidence:

- `app/src/main/res/layout/activity_main.xml:360` still contains `android:text="Back"`.

Required fix:

- Replace with `@string/button_back` or equivalent.
- Add zh/en string resources if not already present.

Impact:

- Small but visible i18n issue.
- Low-cost fix still incomplete.

### B3. Internal route naming remains semantically stale

Evidence:

- `CockpitPanelRoute.kt` still defines `FilterLab` and `LensLab`.
- `buttonColorLabEntry` still opens `CockpitPanelRoute.LensLab`.
- Tests now document mapping, e.g. `LensLab route maps to Color Lab label in top bar`.

Interpretation:

- This is no longer hidden, because tests document the mapping.
- But it remains a maintainability and handoff risk. If the goal is true 2.0 design self-consistency, route names should be migrated to `StyleLab / ColorLab`.

Impact:

- Not a runtime blocker.
- Still a P1 maintainability/design-coherence issue.

## Still Needs Multimodal Or Device Evidence

- Watermark/filtered thumbnail has no visible jump.
- Saved JPEG actually contains watermark/filter/color lab/frame ratio effects.
- Video output and sidecar/subtitle are valid on scoped storage.
- Live sidecar fallback works on real device.
- Permission permanent denial UX is understandable.
- Provider death/restart, thermal, long-run recovery remain external validation items.

## Updated Readiness Judgment

| Dimension | Status After Fixes | Notes |
| --- | --- | --- |
| UI design logic | Risk | tests pass, but `Back` hardcoded and route names stale |
| Interaction | Risk | main flows pass; permanent denial UX still needs device check |
| Feature availability | Risk | RAW and multi-frame no longer fake if accepted as degraded/disabled; Live still-only needs clearer UI and device validation |
| IO chain | Risk | failure notes/degraded language improved; true saved output still unverified |
| Stability/observability | Fail for gate | Stage 7 script fails due stale Night tests |

## Recommendation

Do not move to GO yet.

Immediate next work:

1. Fix the 5 Night tests to match default single-frame fallback and explicit multi-frame capability paths.
2. Replace XML `Back` hardcode with string resource.
3. Decide whether to accept documented `FilterLab/LensLab` mapping or require full route rename to `StyleLab/ColorLab`.
4. Re-run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/verify_stage_7_observability.sh
```

If those pass, the project can be reclassified from `CONDITIONAL NO GO` to `CONDITIONAL GO pending multimodal/device QA`.
