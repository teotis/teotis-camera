# OpenCamera 2.0 Readiness Release Gate Report

**Updated:** 2026-05-22  
**Gate Controller:** Codex local gate controller  
**Verdict:** `CONDITIONAL GO - LOCAL/TEXT GATE`

---

## Executive Summary

本轮剩余阻断已由 Codex 直接处理并完成复验：

- Night multi-frame 测试已与当前产品契约对齐：默认设备能力继续走单帧 fallback；需要验证多帧正向路径的测试显式声明 `supportsNightMultiFrame=true`。
- `Back` 硬编码英文已改为 `@string/button_back`，并补齐 zh/en 资源。
- `FilterLab / LensLab` 内部路由已收敛为 `StyleLab / ColorLab`，相关角色枚举和测试同步更新为 `COLOR_LAB`。
- 可见 IA 文案中残留的 `Lens Lab / 镜头实验室` 已清理，水印/人像子页说明改为位于 `Settings / 设置` 下一级。
- Stage 7 正式验证脚本已通过，包含 app 单测与 `:app:assembleDebug`。

因此，上一版报告中的本地阻断项已清零。当前不能直接宣布整体 `GO` 的原因不再是本地测试失败，而是仍缺少真机和多模态证据来验证 UI 观感、横屏手感、缩略图时序、保存 JPEG/视频输出和 provider/thermal/long-run recovery。

## Gate Checklist

| Gate | Result | Evidence | Remaining condition |
| --- | --- | --- | --- |
| UI design logic coherent | Pass locally | `StyleLab / ColorLab` route naming, Color Lab title, Back i18n, no `Lens Lab` residual text found by `rg` | Requires real screenshots for final visual arbitration |
| Interaction smooth | Pass locally with device caveat | App UI/render focused tests pass | Requires device recordings for panel, permission, landscape gestures |
| Reachable features effective | Conditional pass | RAW/multi-frame/Live are treated as explicit capability or degraded/fallback paths; stale multi-frame tests fixed | Real RAW/DNG, true multi-frame merge, Live motion remain high-cost decisions |
| IO chain clear | Conditional pass | Prior media/device focused tests passed; Stage 7 script passed | Saved JPEG/video/sidecar samples still required |
| Stability/observability supports 2.0 | Pass locally | `verify_stage_7_observability.sh` passes | Provider death, thermal, long-run recovery still need real-device matrix |
| Multimodal pending | Pending | Not provided in repo | Latest APK screenshots, recordings, saved media, device logs |

## Verification Evidence

| Command | Result | Notes |
| --- | --- | --- |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest` | Pass | Confirms Night multi-frame/fallback expectation repair |
| `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.gesture.GestureGuardTest` | Pass | Confirms route naming, Color Lab role, cockpit render, gesture guard |
| `rtk ./scripts/verify_stage_7_observability.sh` | Pass | Includes Stage 7 unit coverage and `:app:assembleDebug` |

Known non-blocking warnings remain from existing resource formatting warnings and deprecated `onBackPressed()` usage; they did not fail build or tests in this gate.

## Closed Issues From Previous Gate

| Previous blocker | Status | Closure |
| --- | --- | --- |
| Night tests expected multi-frame under default capabilities | Closed | Multi-frame positive tests now explicitly pass `DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)` |
| Stage 7 failed due Night tests | Closed | Stage 7 script now passes |
| XML `android:text="Back"` | Closed | Replaced with `@string/button_back`; zh/en strings added |
| `FilterLab / LensLab` route drift | Closed | Routes are now `StyleLab / ColorLab`; role is `COLOR_LAB` |
| Residual visible `Lens Lab` IA copy | Closed locally | `rg` found no `Lens Lab / 镜头实验室 / button_lens_lab_entry / LENS_LAB` in `app/src/main` or tests after cleanup |

## Remaining High-Cost / External Decisions

These are not simple local fixes and should remain user/product decisions:

- Real RAW/DNG output versus saved-only/manual metadata degradation.
- True Night multi-frame merge versus single-frame fallback as the shipped default.
- Real Live motion capture versus still-only fallback.
- Video frame-level watermark burn-in versus sidecar/subtitle/metadata strategy.
- Provider death/restart, thermal behavior, long-run recovery, and device-specific performance budget matrix.
- Multimodal QA for screenshots, recordings, saved JPEG/video samples, thumbnail transition, and visual/interaction polish.

## Recommended Verdict

`CONDITIONAL GO - LOCAL/TEXT GATE`

The repo now satisfies the local automated gate for the current 2.0 readiness cleanup pass. Final overall `GO` still requires real-device and multimodal evidence because the 2.0 standard includes visual coherence, interaction smoothness, and saved-output correctness that cannot be proven by unit tests alone.
