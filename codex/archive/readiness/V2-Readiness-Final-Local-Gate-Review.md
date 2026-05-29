# OpenCamera 2.0 Readiness Final Local Gate Review

> 日期：2026-05-22  
> 目的：用户要求“剩余问题由 Codex 负责处理，直到达标”后的本地复审  
> 结论性质：本地代码、文本链路和自动化验证结论；不替代真机/多模态最终验收

## Verdict

**CONDITIONAL GO - LOCAL/TEXT GATE**

上一轮 `CONDITIONAL NO GO` 的本地阻断项已经处理完成：

1. Night multi-frame 旧测试期望已修正。
2. Stage 7 正式验证脚本已通过。
3. `activity_main.xml` 的 `Back` 硬编码已移入字符串资源。
4. 内部 `FilterLab / LensLab` 路由已收敛为 `StyleLab / ColorLab`。
5. 可见 IA 文案中的 `Lens Lab / 镜头实验室` 残留已清理。

当前不能直接升级为整体 `GO` 的唯一原因是仍缺少外部证据：真机截图、横屏录屏、拍后缩略图录屏、保存 JPEG/视频输出样本、provider/thermal/long-run recovery 日志。

## Implemented Fixes

### Night capability tests

- Tests that explicitly validate real multi-frame capture now pass `DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)`.
- Default capability remains conservative, so existing fallback tests still cover `supportsNightMultiFrame=false`.
- This keeps product behavior honest: unsupported devices do not silently pretend to produce multi-frame output.

### UI route and IA consistency

- `CockpitPanelRoute.FilterLab` -> `CockpitPanelRoute.StyleLab`.
- `CockpitPanelRoute.LensLab` -> `CockpitPanelRoute.ColorLab`.
- `StyleAndColorLabRole.LENS_LAB` -> `StyleAndColorLabRole.COLOR_LAB`.
- Color Lab title resources now render as `Color Lab / 色彩实验室`.
- Watermark and Portrait subpage supporting copy now says they sit below `Settings / 设置`, not `Lens Lab / 镜头实验室`.

### i18n cleanup

- `button_back` added to zh/en resources.
- Settings back button now uses `@string/button_back`.
- Static sweep no longer finds `Lens Lab`, `镜头实验室`, `button_lens_lab_entry`, `LENS_LAB`, old route names, or `android:text="Back"` in `app/src/main` / relevant tests.

## Verification

| Command | Result |
| --- | --- |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest` | Pass |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.gesture.GestureGuardTest` | Pass |
| `rtk ./scripts/verify_stage_7_observability.sh` | Pass |

## Remaining Decisions

These remain high-cost or external and should not be assigned to a generic non-multimodal agent as simple cleanup:

- Real RAW/DNG versus saved-only degraded manual metadata.
- True Night multi-frame merge versus single-frame fallback as shipped baseline.
- Real Live motion versus still-only fallback.
- Video frame-level watermark burn-in versus sidecar/subtitle/metadata.
- Provider death/restart, thermal, long-run recovery, and device-specific performance thresholds.
- Multimodal visual and output QA for screenshots, recordings, saved JPEG/video samples, thumbnail transition, and UI polish.

## Updated Readiness Judgment

The repository now meets the local automated gate for the current 2.0 cleanup round. It is reasonable to hand this state to device/multimodal QA as `CONDITIONAL GO - LOCAL/TEXT GATE`, with final overall `GO` dependent on real-device evidence.
