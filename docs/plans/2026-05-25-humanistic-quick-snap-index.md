# Humanistic Quick Snap Reopen Index

日期：2026-05-25

## Goal

重新开放 `HUMANISTIC` 人文模式，并让它不只是“拍照模式换一套滤镜”。推荐把人文模式定义为 **快速抓拍 / 街头人文记录模式**：进入模式即采用更适合街拍的默认视角与风格，优先降低按下快门到定格画面的延迟，同时保留已有 Live、frame ratio、watermark、Pro variant 等能力作为二级能力。

## Decision

推荐方向：**Humanistic Quick Snap**。

第一闭环不要先做复杂 AI 街拍、自动选片或多帧融合；先做用户能马上感知的三件事：

- 人文模式重新出现在模式轨道。
- 人文模式默认使用 35mm-ish 视角、三套人文风格和独立保存/metadata。
- 人文模式快门默认走低延迟策略：CameraX ZSL 可用时优先 ZSL，不可用时退化到 `MINIMIZE_LATENCY`，并用预览截图做即时反馈。

原因：

- vivo X300 官方页把移动影像重点放在多焦段、自然人像、抓拍定格和 Live 生命力上，其中页面列出 23mm/35mm/50mm/85mm/100mm 等人像焦段，并把“运动人像”描述为集零延时、基础画质、定格等能力；OpenCamera 可吸收“35mm + 定格瞬间”的产品语义，但不能承诺 vivo 私有 ISP/算法。
- Apple 官方相机体验里，Camera Control 可快速打开相机并再次点击拍照，Burst 用于运动主体的高速连续选择，Photographic Styles 提供 tone/color/intensity 的少控件强语义调节；OpenCamera 可借鉴“入口快、反馈快、风格语义清晰”的体验，而不是复制硬件按钮。
- Android CameraX 官方提供 ZSL capture mode，但明确是实验能力，并有 API、PRIVATE reprocessing、flash/video/extensions 等限制；因此必须 capability-gated，而不是把“零延迟”写死为保证。

## Verified Facts

- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt` 已存在，`ModeId.HUMANISTIC` 已注册到 `AppContainer.kt`，且支持 style、frame ratio、Live Photo、watermark、Pro variant 和 capture metadata。
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` 的 `PRODUCT_MODE_ENTRY_ORDER` 当前只包含 `PHOTO, VIDEO, DOCUMENT`，所以人文模式在主模式轨道被隐藏。
- 旧方案 [`docs/plans/handoff-humanistic-mode-reopen.md`](../../docs/plans/handoff-humanistic-mode-reopen.md) 已提出“解隐藏 + 35mm + 三风格”，但它不是以快速抓拍为核心，并且部分字段口径已经与当前代码不一致。
- 当前代码存在需要先核实/修复的契约漂移：`CameraXCaptureAdapter.kt` 读取 `deviceGraph.stillCapture.qualityPreference`、`deviceGraph.stillCapture.resolutionPreset` 和 `deviceRequest.stillCaptureQuality`，但当前 `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` 中 `StillCaptureConfig` / `DeviceShotRequest` 展示的字段不包含这些属性。实现 agent 必须先用编译或源码确认该漂移，不要在未收口前继续叠功能。
- `CameraXCaptureAdapter.createImageCapture` 已根据 `StillCaptureQualityPreference.LATENCY / QUALITY` 映射到 `ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY / CAPTURE_MODE_MAXIMIZE_QUALITY`，这是快速抓拍第一闭环可复用的入口。
- `CameraXCaptureAdapter.captureStillImage` 已在 `ShotStarted` 后调用 `captureCaptureFeedbackSnapshot`，具备“按下后立即用预览帧反馈”的基础。
- `LivePreviewFrameSource` / motion photo 相关能力已经存在，但人文快速抓拍第一闭环不应默认变成 Live 或多帧；Live 保留为用户设置或二级能力。
- `codex-camera-reference/01-architecture/algo-shot-instance.md` 提到 参考相机应用 的 `doAnchorFrameAsThumbnail()`、`enableZSL`、`zslPolicy` 和 single/burst shot 参数；适合被吸收到 OpenCamera 的 `CaptureStrategy / CaptureProfile / DeviceShotRequest`，而不是搬回旧式 shot instance 继承树。

## References

- vivo X300 官方页：https://www.vivo.com.cn/vivo/x300/
- Apple “Take great photos and videos”：https://support.apple.com/en-mide/guide/iphone/iph9bbc8619e/ios
- Apple “Burst mode”：https://support.apple.com/en-ie/guide/iphone/ipha42c55cd0/ios
- Apple “Photographic Styles”：https://support.apple.com/en-mt/guide/iphone/iph629d2cd37/ios
- Android CameraX ZSL：https://developer.android.com/media/camera/camerax/take-photo/zsl
- Android CameraX capture options：https://developer.android.com/media/camera/camerax/take-photo/options
- Local reference: `/Volumes/Extreme_SSD/工作数据/codex-camera-reference/01-architecture/algo-shot-instance.md`
- Local reference: `/Volumes/Extreme_SSD/工作数据/codex-camera-reference/04-ui-components/core-camera-views.md`

## Work Packages

1. [Contracts And CameraX Quick Capture](./2026-05-25-humanistic-quick-snap-contracts.md)
   - Adds capability-gated quick capture semantics to existing media/device contracts.
   - Reconciles current still-capture contract drift before adding ZSL.

2. [Humanistic Mode Product Reopen](./2026-05-25-humanistic-quick-snap-mode-ui.md)
   - Reopens Humanistic in the mode track.
   - Applies 35mm-ish default zoom, three humanistic styles, quick-snap metadata, and mode copy.

3. [Verification And Real Device QA](./2026-05-25-humanistic-quick-snap-verification.md)
   - Defines local unit/assemble gates plus real-device shutter-lag and visual acceptance.
   - Reserves final feel/latency judgment for Codex/user with device evidence.

## Recommended Order

1. Run a focused compile or source reconciliation for `DeviceContracts.kt` vs `CameraXCaptureAdapter.kt`.
2. Implement quick capture contracts and fake/unit tests.
3. Add CameraX adapter ZSL/min-latency selection and diagnostics.
4. Reopen Humanistic UI and mode behavior.
5. Run focused tests, then `rtk ./scripts/verify_stage_7_observability.sh`.
6. Codex/user performs real-device quick-snap QA.

## Codex-Retained Work

- Final product judgment on whether the mode feels meaningfully faster than Photo.
- Multimodal QA of preview flash/thumbnail feedback and saved JPEG style.
- Decision on whether Humanistic becomes a current-stage exception while Stage 7 remains otherwise stabilization-focused.

## Delegable Work

- Pure Kotlin contract additions and tests.
- CameraX capture mode selection behind explicit capability/degradation diagnostics.
- Mode-track render model and Humanistic mode metadata/style updates.
- Verification script additions.

## Blocked Or Deferred

- Vendor HAL/VendorTag ZSL integration.
- AI auto best-frame selection.
- High-speed burst gallery and post-shot chooser.
- Always-on background camera launch from lock screen or hardware key.
- Product claim of true “zero latency” without real-device evidence.

## Global Acceptance Criteria

- Humanistic appears in the primary mode track without reintroducing hidden UI state.
- Humanistic defaults to a quick-snap strategy distinct from Photo.
- ZSL is used only when supported; fallback behavior is explicit in diagnostics and tests.
- Flash/Live/multi-frame interactions do not create fake ZSL claims.
- Saved photos include humanistic quick-snap metadata and remain compatible with existing postprocessors.
- Stage 7 observability gate remains passable after implementation.
