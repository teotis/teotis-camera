# 当前状态

- 里程碑：按用户当前轮授权，`6B-8` 已完成并允许进入下一阶段；当前 stage 已切换为第 `7` 阶段“稳定性治理与自动化补强”。
- 阶段进度：原第 `6` 阶段基线与追加包维持已完成/已冻结判断；第 `7` 阶段当前进度更新为 `80%`。
- 架构口径更新：工程对外介绍、面试表述与高层总结统一采用“四层主链路 + 横切治理能力”的说法；四层主链路指 `Mode Plugin / Session Kernel / Device Adapter / Media Pipeline`，其中后处理归入媒体管线内部能力，稳定性 / 可观测性 / 恢复 / 自动化验证作为跨层治理能力描述，不再与主链路并列拆成独立业务层。
- 当前阶段判断：第 `7` 阶段已经不再只有 `trace + metrics + PreviewError` 的半成品。当前仓内已建立 `diagnostics owner + runtime issue owner + recovery failure owner + zoom owner + thermal owner + background recovery owner + perf threshold owner + provider invalidation owner + preview startup stall watchdog owner` 的稳定性主链，但距离 stage exit checklist 仍有平台/真机侧缺口。
- 工程复盘与加固：第 `7` 阶段当前已完成多条已验证闭环：
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 新增 `SessionDebugDump / RecoveryTraceSnapshot / PerfSnapshot`，把 `SessionState + SessionTrace` 升级为统一 diagnostics owner，而不是继续由 `MainActivity` 直接拼 trace 字符串；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 与 [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 现在消费结构化 diagnostics 文本，UI 可以稳定展示 `DebugDump / RecoveryTrace / PerfSnapshot`；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 把 `PreviewError` 从“只进入 `ERROR`”推进到“在 host/权限仍在且无进行中 shot/recording 时自动请求 recovery bind”，并追加 `preview.recovery.requested` trace；
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt)、[`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt)、[`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 与 [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现已把 `bind/provider heuristic + CameraState` 故障收进结构化 `DeviceRuntimeIssue / PreviewRuntimeIssue`，不再只能压平成无类型 `PreviewError`；
  同一条链路现已显式记录 `preview.recovery.failed`，并阻止 `RECOVERING` 状态下的 recoverable issue 递归再次请求 recovery，避免 recovery 自己形成重试环；
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt)、[`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt)、[`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt)、[`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 与 [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 现已建立 `zoomRatioCapability + ZoomRatioToggled + ApplyZoomRatio` 主链，`切变焦` 已不再缺少 owner；
  [`ThermalRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/ThermalRuntimeIssueMonitor.kt) 与 [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 现已建立 `Android PowerManager -> DeviceRuntimeIssue(THERMAL_CRITICAL) -> PreviewRuntimeIssue` 的上层通用接入口；旧系统或无服务时自动退化为空实现，不把无底层支持误判成业务回归；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 与 [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 现已把 `PreviewHostDetached -> PreviewHostAttached` 推进成显式 `preview.host.recovery.requested` 语义；前后台返回不再退回普通 bind，而是进入可追踪的 recovery bind；
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 与 [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 现已把 `lastStartReason` 进一步提升为 `PreviewStartCategory + FirstFrameBudgetSnapshot`，默认阈值化 `cold start / foreground resume / recovery / reconfigure`，不再只展示裸毫秒数；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 命中 `PROVIDER_FAILURE / CAMERA_FATAL` 时会主动作废缓存的 `cameraProvider / boundCamera`，避免后续继续复用陈旧 provider 引用；
  [`PreviewStartupRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitor.kt)、[`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt)、[`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 与 [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 现已把“bind 已发起但长期等不到首帧”的 `preview startup stall` 收进可恢复 `DeviceRuntimeIssueKind.PREVIEW_STALL` 和统一 budget/watchdog 语义；
  [`SessionDiagnosticsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt)、[`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt)、[`PreviewStartupRuntimeIssueMonitorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitorTest.kt)、[`CameraSessionCoordinatorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt)、[`CameraXCaptureAdapterCapabilityDetectionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt)、[`CameraXCaptureAdapterRuntimeIssueTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRuntimeIssueTest.kt)、[`AndroidThermalRuntimeIssueMonitorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/AndroidThermalRuntimeIssueMonitorTest.kt) 与 [`verify_stage_7_observability.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_7_observability.sh) 已把 diagnostics build、preview error recovery、runtime issue forwarding、recovery failure、切变焦、thermal runtime issue、preview startup stall watchdog、perf budget 与 `assembleDebug` 收入口径。
- 当前产品/阶段决策：
  第 `6` 阶段的 feature 完成/冻结判断继续成立，不回退去扩写 `6B-6/6B-7/6B-8`；
  第 `7` 阶段优先做 recovery / observability / automation owner，不在这个阶段抢跑新的模式 feature；
  对显著依赖底层和硬件的稳定性项，优先先把仓内可验证语义与 diagnostics owner 做实，再决定是否进入真机/平台专项。

## 当前阶段判断

- 第 `6` 阶段完成判断继续有效，`6B-6/6B-7/6B-8` 不再是当前 stage 的 owner。
- 第 `7` 阶段当前已建立的主链路：
  [`SessionTrace.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionTrace.kt) 提供 ring-buffer trace owner；
  [`SessionContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt) 中的 `PreviewMetrics` 已继续作为 `PerfSnapshot` 的底座，而不是散落在 UI 文本里重复解释；
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 现已把 `trace + metrics + session state` 收束为统一 diagnostics/export 结构；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现在会在普通 `PreviewError` 命中时，根据 `lifecycle / permission / host / activeShot / recordingStatus` 判断是否进入 recovery 请求路径；
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 已新增 `DeviceRuntimeIssue / DeviceRuntimeIssueKind` 作为底层稳定性事件通用结构，允许后续继续接 `thermal / provider / vendor-specific fatal`；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 现会观察 `CameraState`，并把 `bind/provider heuristic + camera fatal/recoverable state` 发为结构化 runtime issue；
  [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 现会转发 `RuntimeIssue`，并把 preview bind failure 也纳入同一结构，而不是直接退回无类型 `PreviewError`；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现已把 `PreviewRuntimeIssue` 纳入统一 trace，并在 recovery 中失败时显式记录 `preview.recovery.failed`、停止递归重试；
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 与 [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现已把 `zoomRatioCapability`、`ZoomRatioToggled`、`ApplyZoomRatio` 和 graph 内 `preview.zoomRatio` 收进 session/device contract；
  [`ThermalRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/ThermalRuntimeIssueMonitor.kt) 现已把 `PowerManager` thermal status 收敛为可替换 `RuntimeIssueMonitor`，并由 [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 统一转发进 session；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现会保留待恢复的 host detach 原因，并在前台 reattach 或权限恢复后发出 `preview.host.recovery.requested`，把后台恢复纳入同一 recovery trace；
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 现已把首帧性能从“只有 metrics”推进到“带预算状态的 diagnostics owner”；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 现会在 `provider/fatal` runtime issue 上主动清理缓存 provider 状态，给下次 recovery 留出真正重取 provider 的空间；
  [`PreviewStartupRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitor.kt) 与 [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 现已把 `bind started -> no first frame within budget+grace` 提升为 recoverable `PREVIEW_STALL` 事件，避免长稳卡死只能等待底层主动抛错；
  [`verify_stage_7_observability.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_7_observability.sh) 已成为第 `7` 阶段当前正式验证入口。
- 当前判断：第 `7` 阶段尚未完成工程复盘与加固，距离 exit checklist 仍有明显差距；但“统一 diagnostics owner + runtime issue owner + recovery failure guardrail + zoom owner + thermal owner + background recovery owner + perf threshold owner + provider invalidation owner + preview startup stall watchdog owner”这条当前最高价值、且能在仓内验证的恢复主链已经成立。

## 当前验证基线

- `cd OpenCamera && ./scripts/verify_stage_7_observability.sh`
- 该脚本当前覆盖：
  `DefaultCameraSessionTest`
  `SessionDiagnosticsTest`
  `AndroidThermalRuntimeIssueMonitorTest`
  `PreviewStartupRuntimeIssueMonitorTest`
  `CameraXCaptureAdapterCapabilityDetectionTest`
  `CameraXCaptureAdapterRuntimeIssueTest`
  `SessionUiRenderModelTest`
  `CameraSessionCoordinatorTest`
  `:app:assembleDebug`
- 本轮通过：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest`
  `./scripts/verify_stage_7_observability.sh`

## 当前遗留风险

- `CameraXCaptureAdapter` 已能输出 `bind/provider heuristic + CameraState` runtime issue，并在 `provider/fatal` issue 上清理缓存 provider；但 `ProcessCameraProvider` 真正 provider death 仍没有平台级强信号，当前 `provider failure` 里依然包含基于异常文案的保守分类。
- 第 `7` 阶段的 `recovery failure`、`切变焦`、`thermal`、`后台恢复` 与 `preview startup stall` 仓内 owner 已建立，但 `provider death` 真信号与更长时间维度的真机矩阵仍缺少可信来源，继续硬推容易只剩 contract。
- 当前验证仍以 unit/assemble 为主；首帧超时 watchdog 已建立，但 provider death、provider restart 后真实重连成功率和更长稳的热/权限/生命周期组合仍未建立可收敛的自动化验证。
- 本地 Gradle/Kotlin 在并行跑多个 task 时仍偶发 `.codex-build/OpenCamera/.../classes/kotlin/main/com` 缺失型瞬时错误；串行验证后本轮脚本通过，因此当前不判为业务回归。

## 下一步建议

- 第 `7` 阶段若继续推进，最高优先级已切到 `provider death / provider restart 真信号` 这类更依赖平台和真机信号的项；当前仓内结构已允许继续挂接，但缺少可信验证来源。
- 第二优先级是把当前默认 perf budget 接到真实设备/机型阈值矩阵；在没有额外口径和真机的前提下，继续细化只会把默认阈值写死。
- 现有第 `6` 阶段功能闭环无需继续扩写；后续若回到 feature 侧，应单独获得新的阶段授权。
- 最近新增的产品化设计输入已归纳为三份可交付 spec：统一手势入口、模式轨道/快门/变焦 cockpit、PreviewRenderEngine / Filter / Watermark 管线；后续实施应优先遵循这三份设计稿的边界，不要回退到老工程式的大型单体 View 或全局协议中心。
- `OpenCamera UI/Interaction 2.0` 统一设计资料已沉淀到 [`codex/v2_ui`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui)：包含主界面 cockpit 线框、视觉系统、交互语法、功能便利性分层、统一面板与 labs、参考图资料包和多模态延期视觉审查。后续 UI 落地 agent 应优先按这些 Markdown 规格执行；参考 PNG 只作为视觉资料，不替代文字规格。
- `OpenCamera Capability Kernel 2.0` 统一设计资料已沉淀到 [`codex/capability_kernel_v2`](/Volumes/Extreme_SSD/project/codex_camera/codex/capability_kernel_v2)：核心判断是本应用不是厂商系统相机，不能依赖 native cache / ISP-BSP 协同 / 私有算法引擎，因此 2.0 要在不破坏 `Mode Plugin / Session Kernel / Device Adapter / Media Pipeline` 四层主链路的前提下，把能力图谱、帧流缓冲、ShotGraph/算法管线、Live/时序媒体、资源治理、模式集成和可观测验证拆成可并行落地的上层能力内核方案；涉及成片视觉、截图、录屏和画质判断的工作已单独隔离到多模态延期文档。
- `2026-05-22` 真机最新版 APK 反馈已拆成 5 份可交给非多模态 agent 的落地方案：首次授权后预览恢复、零延时缩略图反馈、窄横向变焦条、画幅/预览 cockpit、色调调色板可发现性；需要看截图/录屏/视觉对比的事项单独隔离到多模态延期清单。
- `2026-05-22` 最新版 APK 第二轮真机反馈已按相近问题合并为 3 份非多模态实施方案和 1 份多模态延期 QA：媒体输出/滤镜/缩略图、面板入口信息架构、中文文案与窄屏布局、多模态视觉和成片验收。后续落地应优先处理媒体保存失败和面板 IA，因为它们会同时影响“滤镜成片”和“缩略图回退”的可验证性。
- `2026-05-22` 最新版 APK 第三轮真机反馈已新增总索引、5 份非多模态实施方案和 1 份多模态视觉 QA：水印缩略图首帧反馈、横屏/网格/画幅几何、面板状态去重、风格与镜头实验室 IA、快门按钮视觉刷新，以及需要截图/录屏/保存 JPEG 对比的多模态验收。后续并行落地时优先从水印缩略图和面板状态去重开始，避免先做大 IA 时继续放大已有反馈错觉和重复信息。
- `2026-05-22` 最新版 APK 第四轮真机反馈已新增总索引、4 份非多模态实施方案和 1 份多模态视觉 QA，并按最新产品口径修订顶部/侧栏 IA：顶部左侧仅应用名、中部靠右 `色彩实验室`、最右 `设置`，侧边栏收敛为 `风格 / 快捷 / Dev`；同时覆盖横屏控制旋转与预览成像区域对齐、快捷面板与二级面板边界、模式栏清晰度和命中区域，以及需要截图/录屏/保存图对比的视觉验收。后续落地应优先收敛入口 IA 与面板边界，再处理模式栏触控和横屏/预览几何，避免多个 agent 同时改 `MainActivity.kt`。
- `2026-05-22` 色彩实验室第一阶段已完成最难的 10% 核心内核：新增 `ColorLabSpec`，把二维调色板坐标稳定映射为现有 `FilterRenderSpec`，并让旧 `applyLightPalette` 委托到该内核；剩余 UI/持久化/预览/成片接线已沉淀到 [`2026-05-22-color-lab-stage1-implementation.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-color-lab-stage1-implementation.md)，可交给非多模态 agent 落地。
- `2026-05-22` `风格 + 色彩实验室 2.0` 已完成最难的 30% 核心内核：新增 `StyleColorPipeline`，将“风格基底先继承、色彩实验室后渲染”的顺序、风格强度、风格色彩科学分支和同一 XY 在不同风格下的不同响应固化为可测试纯 Kotlin 规则；同时优化了现有调色盘 UI，实现横向冷暖、纵向影调、点阵网格、中心轴线和正确的上正下负 tone 坐标；剩余 UI/持久化/模式接线/预览与成片一致性已沉淀到 [`2026-05-22-style-color-lab-2-implementation.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-style-color-lab-2-implementation.md)，可拆给多个非多模态 agent 并行落地。
- `2026-05-22` `OpenCamera 2.0 准入综合审查` 已按用户要求升级为可直接分发的 Markdown 任务包：总控方案总结了历史用户需求、友商对标口径、项目定位、2.0 gate 判据和 `GO / CONDITIONAL GO / NO GO` 规则；高难 10% 与多模态限定任务单独收纳到 [`2026-05-22-v2-readiness-hard10-multimodal.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-v2-readiness-hard10-multimodal.md)；A-F 六组非多模态任务分别覆盖 UI 静态自洽、交互流、功能可用、IO 链路、稳定观测和发布门禁汇总。当前主控初判是在缺少最新 APK 截图/录屏/保存媒体/真机日志前不能宣称 `GO`，应先按 `CONDITIONAL GO` 审查流程采证；若拍照/录像/保存/权限恢复任一金路径被 D 组判 P0，则转 `NO GO`。
- `2026-05-22` 本轮 2.0 准入审查问题已按解决成本重新分层：低成本立即修复项沉淀到 [`2026-05-22-v2-readiness-low-cost-fixes.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-v2-readiness-low-cost-fixes.md)，覆盖 i18n/硬编码文案、风格/色彩实验室命名映射、侧栏遗留设置资源、humanistic 旧期望测试、disabled 状态提示、权限永久拒绝引导和 render model 测试缺口；中成本立即修复项沉淀到 [`2026-05-22-v2-readiness-medium-cost-fixes.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-v2-readiness-medium-cost-fixes.md)，覆盖后处理失败可观测、orphaned shot failure、首次缩略图 fallback、RAW/多帧/Live 显式降级和连续变焦语义收敛；高成本决策项沉淀到 [`2026-05-22-v2-readiness-high-cost-decision-items.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-v2-readiness-high-cost-decision-items.md)，包括真实 RAW/DNG、真实夜景多帧、Live motion、视频帧级水印、provider death 真机专项、thermal/长稳/perf 矩阵和多模态成片 QA，需等待用户决策后再进入实现。
- `2026-05-22` 用户处理低/中成本项后，主控复审已沉淀到 [`V2-Readiness-Post-Fix-Review.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/V2-Readiness-Post-Fix-Review.md)：RAW 已显式降级为 saved-only，多帧夜景默认能力已关闭并具备单帧 fallback，后处理失败 notes/degraded 文案、orphaned shot failure trace 和 Live still-only fallback 语义均有进展；UI/render、media、device focused tests 通过。但 `:core:session:test` 与 `verify_stage_7_observability.sh` 仍被 5 个 Night multi-frame 旧期望测试阻断，`activity_main.xml` 仍有 `android:text="Back"` 硬编码，`FilterLab/LensLab` 内部路由名仍未真正收敛为 `StyleLab/ColorLab`。当前判定为 `CONDITIONAL NO GO`，下一步应先同步 Night 测试到默认单帧 fallback / 显式 multi-frame capability 两条路径，再重跑 Stage 7。
- `2026-05-22` 剩余本地阻断已由 Codex 直接收敛并完成复验，最终本地门禁复审沉淀到 [`V2-Readiness-Final-Local-Gate-Review.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/V2-Readiness-Final-Local-Gate-Review.md)，根目录发布门禁报告同步更新为 [`V2-Readiness-Release-Gate-Report.md`](/Volumes/Extreme_SSD/project/codex_camera/V2-Readiness-Release-Gate-Report.md)：Night 多帧正向测试已显式声明 `supportsNightMultiFrame=true`，默认能力仍保持单帧 fallback；`Back` 硬编码已改为 `@string/button_back`；内部路由和角色名已收敛为 `StyleLab / ColorLab / COLOR_LAB`；可见 IA 文案中的 `Lens Lab / 镜头实验室` 残留已清理；`core:session` focused tests、UI focused tests 和 `verify_stage_7_observability.sh` 均通过。当前结论升级为 `CONDITIONAL GO - LOCAL/TEXT GATE`，最终整体 `GO` 仍依赖真机/多模态证据和高成本能力决策。
- `2026-05-22` vivo X300 真机录屏 `/Users/dingren/Downloads/飞书20260522-162635.mp4` 已完成第五轮多模态审查，结论是“本地 gate 已过，但真实 2.0 相机体验仍未达标”：顶部栏中文遮挡、按钮等权且边框风格不统一，Color Lab 调色板点击后回弹且 `进阶` 不应存在，右侧 `色调` 应改为 `镜头`，底部 zoom/mode/shutter 区域割裂，模式栏对比度不足，快捷面板应面板化且画幅选项不能消失，设置/风格面板仍暴露 `Supported` 与 raw render 参数。已新增第五轮总索引和 6 份任务包：[`2026-05-22-fifth-real-device-recording-index.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-real-device-recording-index.md)、[`2026-05-22-fifth-color-lab-palette-persistence.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-color-lab-palette-persistence.md)、[`2026-05-22-fifth-top-bar-and-rail-ia-polish.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-top-bar-and-rail-ia-polish.md)、[`2026-05-22-fifth-bottom-cockpit-zoom-mode-track.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-bottom-cockpit-zoom-mode-track.md)、[`2026-05-22-fifth-quick-panel-frame-ratio-sheet.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-quick-panel-frame-ratio-sheet.md)、[`2026-05-22-fifth-panel-copy-engineering-cleanup.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-panel-copy-engineering-cleanup.md) 与 [`2026-05-22-fifth-multimodal-hard10-qa.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fifth-multimodal-hard10-qa.md)。最优先落地顺序是 Color Lab 持久化 -> 顶部/右栏 IA -> 底部 cockpit/mode track -> 快捷面板 -> 文案清理 -> 新 APK 多模态复验。
- `2026-05-22` 高难 10% / 多模态基线报告已完成并沉淀到 [`Fifth-Recording-Hard10-Multimodal-QA-Report.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/Fifth-Recording-Hard10-Multimodal-QA-Report.md)：已抽取 9 张关键证据帧和 contact sheet，P0 定为 Color Lab 直接操控失效、底部 cockpit 视觉割裂、快捷画幅交互不可靠；P1 定为顶部栏裁切/层级、右栏语义、工程文案泄漏、Dev 过强、预览/画幅几何仍需视觉仲裁。该报告同时定义了下一版 vivo X300 录屏复验协议，后续非多模态 agent 完成实现包后，应由多模态 owner 按该协议重新判定 `GO / CONDITIONAL GO / NO GO`。
- `2026-05-22` 第五轮真机反馈中可落地的高难/多模态 owner 修复已完成首个代码闭环：Color Lab 调色板触摸现在进入 persisted `ColorLabSpec` 写入链路并保持 reticle 即时反馈，Color Lab 页面隐藏 `进阶` 模式切换；右侧第一入口默认文案收敛为 `镜头/Lens`；Color Lab 摘要从原始坐标改为 `Warm / Deep Contrast` 等可理解语义；风格/设置面板不再泄漏 raw render spec 和英文 enum availability 文案。聚焦 UI 测试、`assembleDebug` 和 `verify_stage_7_observability.sh` 均已通过，最新 debug APK 位于 `/Users/dingren/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk`。
- `2026-05-22` 真机发现“Color Lab 预览有效但成片不生效”后，已完成系统化调试和代码闭环：Color Lab/style metadata 在 session 侧本已能进入 `filterSpec.*`，真实高风险断点在 Android Q+ CameraX 保存结果可能不给 `savedUri`，导致 `MediaOutputHandle` 只有展示路径、所有成片后处理拿不到可编辑 `contentUri/filePath` 而跳过。[`CameraXCaptureAdapter.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 现会预创建 MediaStore still `contentUri` 并用 OutputStream 写入，`resolvePhotoOutputHandle()` 保证 CameraX 不返回 URI 时仍保留可编辑目标；[`DefaultCameraSessionTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 锁定 Color Lab 合成后的 `filterSpec.*` 会进入拍摄 metadata，[`CameraXCaptureAdapterLivePhotoTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt) 锁定预创建 URI 句柄合同。该修复同时降低滤镜、画幅裁切、水印、人像渲染、文档裁切、自拍镜像等 saved-photo 后处理被跳过的风险；视频录制仍只保证 metadata/filter profile 进入录制请求，成片视频滤镜不在当前 JPEG 后处理链路内。

---

# 最近有效闭环

## 2026-05-22：Color Lab 预览/成片一致性修复

- 目标：处理 vivo X300 真机发现的严重问题：Color Lab 看起来只影响预览，保存照片未生效；同时排查主要预览/成片链路是否存在同类断点，并确认 Color Lab 二级面板不暴露滤镜子项。
- 根因：
  `PhotoModePlugin / PortraitModePlugin / HumanisticModePlugin / VideoModePlugin` 的预览 effect spec 已通过 `renderStyleColorSpec()` 合入 `ColorLabSpec`；`EffectBridge.toMetadataTags()` 也会把合成后的 `FilterRenderSpec` 写入 `filterSpec.*`。真正高风险断点在 Android Q+ 设备保存路径：原 `createPhotoOutputRequest()` 使用 `ImageCapture.OutputFileOptions.Builder(ContentResolver, collection, values)`，但后续后处理只能依赖 CameraX 回调的 `savedUri`；当真机/ROM 回调未给出有效 URI 时，`ShotResult.outputHandle` 只有展示路径，`PhotoAlgorithmPostProcessor / PhotoFrameRatioPostProcessor / PhotoWatermarkPostProcessor / PortraitRenderPostProcessor / DocumentAutoCropPostProcessor / PhotoSelfieMirrorPostProcessor` 都会因缺少可编辑 target 而跳过。
- 核心结果：
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `resolvePhotoOutputHandle()`，并让 Android Q+ still capture 先预创建 MediaStore `contentUri`、用 `ImageCapture.OutputFileOptions.Builder(outputStream)` 写入，确保成片后处理拿到稳定可编辑目标；capture error 时会清理预创建 URI；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 新增 Color Lab capture metadata 回归，确认 `photo-original + ColorLabSpec(1,-1,1)` 会输出合成后的 `filterSpec.brightnessShift / contrast / saturation / warmthShift / highlightCompression / warmBoost`；
  [`CameraXCaptureAdapterLivePhotoTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt) 新增设备输出句柄回归，确认预创建 MediaStore still handle 在 CameraX 省略 `savedUri` 时仍保留 `contentUri`；
  Color Lab 二级面板的“滤镜子项”在 render model 和 Activity 层已双重隐藏：`showFilterItems=false` 时清空 `filterSelectionList`、隐藏 current summary 和 save custom control；对应 `SessionUiRenderModelTest` 仍通过。
- 链路审计结论：
  JPEG saved-photo 链路中，滤镜/Color Lab、画幅裁切、水印、人像渲染、文档裁切、自拍镜像都依赖同一个可编辑 output handle，本轮修复的是共同设备层断点；metadata 到 postprocessor 的合约本身已成立。
  Video 录制链路当前会写入 filter metadata，但没有等价 JPEG 后处理器，不能承诺“录制视频成片”应用 Color Lab；这属于后续视频滤镜渲染能力范围。
- 验证：
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest.photo\ mode\ carries\ color\ lab\ adjusted\ render\ spec\ through\ session\ shot\ metadata`
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest`
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest`
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest.color\ lab\ shows\ adjustment\ panel\ but\ not\ filter\ items\ and\ family\ tabs`

## 2026-05-22：第五轮真机反馈首批可落地修复

- 目标：优先处理 vivo X300 录屏暴露的高难/多模态 owner 中可在本地闭环验证的问题，避免 Color Lab 继续出现“点击后回弹/不生效”和面板文案继续泄漏工程内部状态。
- 核心结果：
  [`ColorLabPaletteAction.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/ColorLabPaletteAction.kt) 与 [`MainActivity.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/MainActivity.kt) 已让 Color Lab 调色板触摸直接生成 `UpdateColorLabSpec`，保留现有 strength/preset 并 clamp 坐标，reticle 会先即时移动再等待 persisted state 回刷；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 和 [`activity_main.xml`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/res/layout/activity_main.xml) 已让 Color Lab 隐藏 `进阶` 模式切换和进阶控件标题，避免色彩实验室误呈现成专业参数面板；
  [`AppTextResolver.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt) 已把右侧入口默认标签从 `Style/风格` 收敛到 `Lens/镜头`，并把 Color Lab 摘要从 `Color: x, Tone: y` 改为方向性语义；
  [`SessionUiRenderModelTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt)、[`CameraCockpitRenderModelTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt) 和 [`ColorLabPaletteActionTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/ColorLabPaletteActionTest.kt) 覆盖了调色板持久化、右栏 `Lens`、Color Lab 不显示 mode toggle、设置 availability 文案本地化和 raw render spec 不外泄。
- 验证：
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ColorLabPaletteActionTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.FilterPaletteViewTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest`
  `rtk ./gradlew --no-daemon :app:assembleDebug`
  `rtk ./scripts/verify_stage_7_observability.sh`
- 结论：第五轮反馈中的 Color Lab 直接操控失效、右栏语义和工程文案泄漏已完成本地闭环；仍需新 APK 真机录屏复验顶部栏裁切、底部 cockpit、模式栏可视性和快捷画幅面板化。

## 2026-05-22：2.0 剩余本地阻断清理与最终本地门禁复验

- 目标：处理上一轮 2.0 准入复审遗留的本地阻断，直到本地代码、文本链路和自动化 gate 达标。
- 核心结果：
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 中验证 Night multi-frame 的测试显式传入 `DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)`，与默认单帧 fallback 产品契约对齐；
  [`CockpitPanelRoute.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt)、[`MainActivity.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/MainActivity.kt)、[`CameraCockpitRenderModel.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt)、[`SessionUiRenderModel.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 与 [`GestureGuard.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/gesture/GestureGuard.kt) 已把 `FilterLab / LensLab` 语义收敛为 `StyleLab / ColorLab`，角色枚举同步为 `COLOR_LAB`；
  [`activity_main.xml`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/res/layout/activity_main.xml)、[`strings.xml`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/res/values/strings.xml) 与 [`values-en/strings.xml`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/res/values-en/strings.xml) 清理 `Back` 硬编码和 `Lens Lab / 镜头实验室` 可见文案残留；
  [`V2-Readiness-Release-Gate-Report.md`](/Volumes/Extreme_SSD/project/codex_camera/V2-Readiness-Release-Gate-Report.md) 与 [`V2-Readiness-Final-Local-Gate-Review.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/V2-Readiness-Final-Local-Gate-Review.md) 已把最新结论更新为 `CONDITIONAL GO - LOCAL/TEXT GATE`。
- 验证：
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest`
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.gesture.GestureGuardTest`
  `rtk ./scripts/verify_stage_7_observability.sh`
- 结论：本地自动化和文本侧 2.0 cleanup gate 已达标；整体 2.0 最终 `GO` 仍需真机截图/录屏/保存媒体、provider/thermal/长稳日志和高成本能力决策。

## 2026-05-22：人文入口并入拍照风格，模式栏顺序收敛

- 目标：按产品判断移除 `HUMANISTIC` 的主模式视觉入口，把人文核心滤镜资源作为拍照风格子项继续保留，并将模式栏顺序调整为 `拍照 / 风景 / 人像 / 专业 / 视频 / 文档`。
- 核心结果：
  [`SessionUiRenderModel.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 新增可见模式入口顺序，`modeDirectoryRenderModel` 与 `modeTrackRenderModel` 现在过滤 `ModeId.HUMANISTIC` 并按 `PHOTO / NIGHT / PORTRAIT / PRO / VIDEO / DOCUMENT` 输出；
  [`MainActivity.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/MainActivity.kt) 与 [`activity_main.xml`](/Volumes/Extreme_SSD/project/codex_camera/app/src/main/res/layout/activity_main.xml) 同步将模式栏点击绑定和 XML 顺序改为相同 6 项，并隐藏旧人文按钮；
  `HUMANISTIC` mode/plugin/metadata 内部能力未删除，避免破坏历史保存路径、滤镜资源、Live/Pro variant 兼容语义；
  [`SessionUiRenderModelTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 与 [`CameraCockpitRenderModelTest.kt`](/Volumes/Extreme_SSD/project/codex_camera/app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt) 补齐模式入口顺序和“拍照风格池包含 Humanistic Street/Life”回归。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests "com.opencamera.app.SessionUiRenderModelTest.mode directory render model hides humanistic entry and uses product order" --tests "com.opencamera.app.SessionUiRenderModelTest.mode track render model hides humanistic entry and uses product order" --tests "com.opencamera.app.SessionUiRenderModelTest.photo filter family exposes humanistic styles as photo style subitems" --tests "com.opencamera.app.CameraCockpitRenderModelTest.mode track hides humanistic and uses product order"`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./gradlew --no-daemon :app:assembleDebug`
- 备注：完整 `CameraCockpitRenderModelTest` 类当前仍有右侧栏旧期望失败，失败点与本次模式入口改动无关；本轮只验证了新增/修改的 cockpit mode track 用例。

## 2026-05-22：最新版 APK 第四轮真机反馈方案文档

- 目标：把用户对最新版 APK 的新一轮真机问题按处理领域拆解，形成可直接交给非多模态 agent 的 Markdown 落地方案，并把需要观察截图、录屏、横屏真实手感和保存图对比的事项隔离给多模态 agent。
- 核心结果：
  [`2026-05-22-fourth-real-device-feedback-index.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fourth-real-device-feedback-index.md) 建立第四轮反馈总索引、问题映射、依赖顺序和全局验证口径；
  [`2026-05-22-landscape-preview-alignment-and-rotation.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-landscape-preview-alignment-and-rotation.md) 将“横屏交互不好”和“真实成像区域相对预览偏下”合并为方向渲染与预览几何同源问题，要求控件旋转、active content rect 和 frame/grid/tap 区域使用同一套纯几何；
  [`2026-05-22-rail-and-color-lab-entry-consolidation.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-rail-and-color-lab-entry-consolidation.md) 已按最新口径修订为顶部左侧仅应用名、不再拼接 `· 模式名`，顶部中部靠右放 `色彩实验室`，顶部最右放 `设置`，侧边栏固定为 `风格/快捷/Dev`，并要求 `色彩实验室` 是二维调色板后渲染模块而不是专业参数列表；
  [`2026-05-22-quick-and-secondary-panel-bounds.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-quick-and-secondary-panel-bounds.md) 将快捷面板省略号和二级面板过大合并为布局边界问题，要求短标签、状态分离、内部滚动和统一 max-height/safe-area 策略；
  [`2026-05-22-mode-track-legibility-and-hit-targets.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-mode-track-legibility-and-hit-targets.md) 聚焦模式栏文字显著性、触控命中、scroll/tap 仲裁和 auto-scroll 稳定性；
  [`2026-05-22-fourth-feedback-multimodal-visual-qa.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-fourth-feedback-multimodal-visual-qa.md) 单独收纳第四轮需要多模态能力的横屏观感、预览/成片对齐、面板遮挡、模式栏触控录屏和入口 IA 视觉验收。
- 验证：本轮只新增方案文档并更新状态文档，未改运行时代码；已交叉阅读当前 `activity_main.xml`、`MainActivity.kt`、`CameraCockpitRenderModel.kt`、`CockpitPanelRoute.kt`、样式/字符串资源和第三轮 agent plans，按用户新反馈重新合并为四个可并行落地领域。

## 2026-05-22：最新版 APK 第三轮真机反馈方案文档

- 目标：把用户对最新版 APK 的 8 个真机问题按相近根因合并，形成可直接转给非多模态 agent 的 Markdown 落地方案，并把涉及截图审美、横屏真实观感、网格参考、快门按钮手感和保存图视觉对比的事项隔离给多模态 agent。
- 核心结果：
  [`2026-05-22-third-real-device-feedback-index.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-third-real-device-feedback-index.md) 建立第三轮反馈总索引、问题映射、依赖顺序和全局验证口径；
  [`2026-05-22-watermarked-thumbnail-first-feedback.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-watermarked-thumbnail-first-feedback.md) 将“缩略图无水印跳变有水印”定位到 `pendingCaptureFeedback` 早于 `PhotoWatermarkPostProcessor`，要求由 session 根据 shot metadata 抑制或生成可信反馈；
  [`2026-05-22-landscape-grid-frame-ratio-geometry.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-landscape-grid-frame-ratio-geometry.md) 合并横屏、构图网格和画幅几何，要求 `PreviewOverlayView` 用 active frame rect 画网格，并按显示方向调整 4:3/16:9 长边；
  [`2026-05-22-panel-state-deduplication.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-panel-state-deduplication.md) 要求去掉设置页和二级面板 header/footer 的状态合集，让子项自己展示当前值和支持状态；
  [`2026-05-22-style-and-color-lab-ia.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-style-and-color-lab-ia.md) 将用户提出的“风格 vs 色彩/调色”拆成 `风格` 与 `镜头实验室` 两个产品面，并约束预览和成片仍走既有 effect/media pipeline；
  [`2026-05-22-shutter-button-visual-refresh.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-shutter-button-visual-refresh.md) 给出文本可执行的 shutter drawable/state 更新，避免按钮内文字裁切；
  [`2026-05-22-third-feedback-multimodal-visual-qa.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-third-feedback-multimodal-visual-qa.md) 单独收纳需要多模态能力的真实屏幕和保存图验收。
- 验证：本轮只新增方案文档并更新状态文档，未改运行时代码；已交叉阅读当前 UI/session/media 实现、近期 agent plans 和用户截图现象，并用 `rg` 检查新增文档无明显占位词。

## 2026-05-22：Camera Capability Kernel 2.0 设计资料沉淀

- 目标：围绕“通用相机 App 无法依赖厂商 native/ISP/BSP 私有能力，应尽量开放底层可得数据并在 App 层做创造性处理”的产品/架构判断，形成可直接交给多个非多模态 agent 并行落地的 2.0 能力内核方案。
- 核心结果：
  [`codex/capability_kernel_v2/00_capability_kernel_v2_index.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/capability_kernel_v2/00_capability_kernel_v2_index.md) 明确 2.0 不另起隐藏内核，而是在既有四层主链路内增加能力图谱、帧流、算法、资源和验证治理；
  [`01_capability_contract_and_graph.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/capability_kernel_v2/01_capability_contract_and_graph.md) 到 [`07_observability_test_and_agent_handoff.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/capability_kernel_v2/07_observability_test_and_agent_handoff.md) 分别拆分 capability graph、frame stream/buffer、ShotGraph/algorithm pipeline、Live/temporal media、resource scheduler、mode integration、observability/test/agent handoff；
  [`90_multimodal_deferred_capability_qa.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/capability_kernel_v2/90_multimodal_deferred_capability_qa.md) 单独隔离滤镜成片、夜景/多帧、人像虚化、Live motion、缩略图录屏和预览效果等需要多模态或人工视觉判断的事项。
- 验证：本轮只新增方案文档，未改运行时代码；已交叉阅读 `codex/plan.md`、`codex/prompt.md`、`codex/documentation.md`、`core:session/device/media/mode/effect` contracts、`CameraXCaptureAdapter`、现有 UI 2.0 文档和近期 agent plans，并用 `rg` 检查文档清单与占位词。
- 结论：该资料包是后续能力内核 2.0 的设计/施工入口，不构成 stage transition 授权；后续实现仍应先落当前高优先级 1.0 bug 修复，再按 contracts -> frame data exposure -> algorithm pipeline -> mode integration -> resource governance 的顺序推进。

## 2026-05-22：最新版 APK 第二轮真机反馈方案文档

- 目标：把用户对最新版 APK 的 6 个新真机问题按相近根因合并拆解，形成可直接交给非多模态 agent 的 Markdown 落地方案；涉及截图判读、成片视觉对比、缩略图录屏时序的事项单独隔离给多模态 agent。
- 核心结果：
  [`2026-05-22-media-output-filter-thumbnail-stability.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-media-output-filter-thumbnail-stability.md) 将“滤镜未进成片”和“缩略图回退”合并到媒体输出链路，指出日志中 `filterSpec.*` 已进入 shot metadata，但 Live sidecar 走 `MediaStore.Files` 写入 `Pictures` 触发 `Primary directory Pictures not allowed for content://media/external/file`，导致 `ShotCompleted` 和最终后处理/官方缩略图不可验证；
  [`2026-05-22-panel-entry-information-architecture.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-panel-entry-information-architecture.md) 将“镜头 / 镜头实验室 / 设置 / 快捷重复混杂”收敛为入口 IA 调整，建议右侧 `镜头` 改为 `设置`，保留底部镜头按钮做真正前后摄切换，并把 `快捷` 限定为短的一跳控制；
  [`2026-05-22-localization-and-narrow-layout-cleanup.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-localization-and-narrow-layout-cleanup.md) 聚焦中文显示不全、右侧竖排、快捷二行和次级面板英文残留，要求清理 `SessionUiRenderModel` 中剩余英文、缩短右侧/快捷标签、拆掉水印卡片里的多行混合按钮；
  [`2026-05-22-multimodal-followup-visual-and-output-qa.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-multimodal-followup-visual-and-output-qa.md) 单独收纳需要截图、录屏、保存 JPEG 对比的视觉和成片 QA，不交给非多模态实现 agent。
- 验证：本轮只新增方案文档，未改运行时代码；已用用户日志、截图现象、`CameraXCaptureAdapter`、`PhotoAlgorithmPostProcessor`、`DefaultCameraSession`、`CameraCockpitRenderModel`、`SessionUiRenderModel` 与当前资源文件交叉定位 owner。

## 2026-05-22：真机反馈专项方案文档

- 目标：把用户对最新版 APK 的 5 个真机问题拆成可直接交给其他 agent 落地的 Markdown 方案，并隔离需要多模态能力的视觉 QA。
- 核心结果：
  [`2026-05-22-first-launch-permission-preview-recovery.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-first-launch-permission-preview-recovery.md) 聚焦首次授权后预览失败，建议补齐 coordinator pending bind，避免权限弹窗生命周期造成 `BindPreview` effect 被静默丢弃；
  [`2026-05-22-zero-latency-thumbnail-feedback.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-zero-latency-thumbnail-feedback.md) 将预览截图定义为 transient capture feedback，而不是 saved-media thumbnail；
  [`2026-05-22-zoom-strip-interaction.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-zoom-strip-interaction.md) 收敛为窄横向、数字点位、精确 `ApplyZoomRatio` 的变焦条；
  [`2026-05-22-preview-aspect-ratio-cockpit.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-preview-aspect-ratio-cockpit.md) 已补强为中文落地版，明确真实画幅链路隐藏、`PreviewRatioToggled` 仅改状态不改视觉/成片的断点，并给出直接画幅选项、预览遮罩和成片裁切的同源方案；
  [`2026-05-22-tone-palette-discoverability.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-tone-palette-discoverability.md) 让已有 `FilterPaletteView` 从隐藏的 adjust 流程变成 `色调 -> 调色板` 的首屏可发现工具；
  [`2026-05-22-multimodal-deferred-items.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/agent_plans/2026-05-22-multimodal-deferred-items.md) 单独收纳截图标注、录屏时序、真实预览视觉对比和调色板可见性评审。
- 验证：本轮为方案文档交付，未改运行时代码；已用仓内文档和当前实现路径交叉定位对应 owner 与验证入口。

## 2026-05-21：OpenCamera UI/Interaction 2.0 设计资料沉淀

- 目标：在 1.0 问题修复包之外，形成面向 `UI美观性 / 交互直观性 / 相机功能使用方便性` 的 2.0 统一设计资料，方便后续非多模态 agent 直接落地。
- 核心结果：
  [`codex/v2_ui/00_v2_ui_index.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui/00_v2_ui_index.md) 建立总索引、实施阶段、验收标准和 1.0 修复包衔接；
  [`codex/v2_ui/01_camera_cockpit_wireframes.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui/01_camera_cockpit_wireframes.md) 到 [`05_panel_system_and_labs.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui/05_panel_system_and_labs.md) 分别定义 cockpit 线框、视觉系统、交互语法、功能分层和统一面板/labs；
  [`codex/v2_ui/06_reference_image_pack.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui/06_reference_image_pack.md) 与 [`codex/v2_ui/reference_images`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui/reference_images) 沉淀 5 张 1080x1920 PNG 参考图和可复现生成脚本；
  [`codex/v2_ui/90_multimodal_deferred_visual_review.md`](/Volumes/Extreme_SSD/project/codex_camera/codex/v2_ui/90_multimodal_deferred_visual_review.md) 单独隔离截图标注、真实设备视觉 QA、参考图对比、图标/动效审查等需要多模态能力的工作。
- 验证：
  使用 Pillow 打开并确认 5 张参考 PNG 均为 `1080x1920 RGB`；
  文件清单确认 `codex/v2_ui` 下新增 `9` 个 Markdown 文档、`5` 张 PNG 和 `1` 个生成脚本。
- 结论：2.0 当前先作为设计/施工规格沉淀，不改变 Stage 7 架构和运行时代码；后续实现应按 2.0 IA Skeleton、Visual Components、Feature Convenience 三轮推进。

## 2026-04-13：第 `7` 阶段第一轮

- 目标：把仓里零散存在的 `SessionTrace + PreviewMetrics + session state` 提升为统一 `DebugDump / RecoveryTrace / PerfSnapshot` 产物，并建立第 `7` 阶段正式验证脚本。
- 核心结果：
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 新增 `PerfSnapshot / RecoveryTraceSnapshot / SessionDebugDump`；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 新增 `sessionDiagnosticsText()`，不再让 `MainActivity` 直接拼原始 trace；
  [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 现已直接消费结构化 diagnostics 文本；
  [`SessionDiagnosticsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt)、[`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 与 [`verify_stage_7_observability.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_7_observability.sh) 建立第 `7` 阶段当前正式口径。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段已不再缺少统一 diagnostics owner；后续 recovery/automation 可以围绕结构化 dump 继续推进，而不是重复造 UI 拼接字符串。

## 2026-04-13：第 `7` 阶段第二轮

- 目标：把普通 `PreviewError` 从“只进入 `ERROR` 态”推进到具备保守 recovery 请求语义的 session 闭环。
- 核心结果：
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现在会在 `lifecycle=RUNNING`、权限和 preview host 仍在、且没有进行中 shot/recording 时，为 `PreviewError` 发出 `preview.recovery.requested` trace，并请求 recovery bind；
  同文件在录制中或有 in-flight shot 时继续保持保守，不会误触发 recovery rebind；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 新增“preview error 会恢复”和“录制中不会误恢复”两条回归；
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 把 `preview.recovery.requested` 纳入 `RecoveryTraceSnapshot` 事件窗口。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：`PreviewError` 已不再只能把 session 推进死路；第 `7` 阶段现已具备“普通 preview error -> recovery request -> diagnostics 可观测”的最小闭环。

## 2026-04-13：第 `7` 阶段第三轮

- 目标：把 `bind/provider/camera state` 失败从“统一压成 PreviewError 字符串”推进到结构化 runtime issue owner，并纳入 stage7 自动化。
- 核心结果：
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 新增 `DeviceRuntimeIssue / DeviceRuntimeIssueKind` 与 `displayReason / recoveryReason`，为后续平台/硬件专项预留统一结构；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `classifyPreviewBindingFailure()`、`cameraStateRuntimeIssue()` 与 `CameraState` observer，会把 `bind/provider heuristic + CameraState recoverable/fatal` 送入 `DeviceEvent.RuntimeIssue`；
  [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 与 [`SessionContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt) 现已转发 `PreviewRuntimeIssue`，bind failure 不再回退为无类型 `PreviewError`；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现会把 runtime issue 记入 `preview.runtime.issue` trace，并根据 `isRecoverable` 决定是否允许恢复；
  [`CameraXCaptureAdapterRuntimeIssueTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRuntimeIssueTest.kt)、[`CameraSessionCoordinatorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt)、[`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 与 [`verify_stage_7_observability.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_7_observability.sh) 补齐 adapter classify、coordinator forwarding 与 session recovery 口径。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段已不再缺少结构化 runtime issue owner；后续若接 `thermal / provider / vendor fatal`，可以沿用现有结构继续挂接，而不是再发明新的错误文本约定。

## 2026-04-13：第 `7` 阶段第四轮

- 目标：把 recovery 中的失败从“再次触发 recovery 请求的潜在重试环”推进到显式 `recovery failed` 语义。
- 核心结果：
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 现会在 `previewStatus=RECOVERING` 时，把新到达的 runtime issue 记录为 `preview.recovery.failed`，并停止递归再次请求 recovery；
  同文件会把 recoverable recovery failure 统一呈现为 `Preview recovery failed`，critical failure 则提升为 `Preview recovery failed, manual intervention required`；
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 已把 `preview.recovery.failed` 纳入 `RecoveryTraceSnapshot` 和 failure event 集；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 与 [`SessionDiagnosticsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt) 补齐 recovery fail trace 和“不会继续重排 recovery”回归。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段当前恢复主链已从“会报错、会尝试恢复”推进到“恢复失败也有 owner，且不会形成递归 recovery 环”。

## 2026-04-13：第 `7` 阶段第五轮

- 目标：把 stage7 checklist 里的 `切变焦` 从“没有 contract owner 的空项”推进到真正可回归验证的 session/device/app 闭环。
- 核心结果：
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 新增 `ZoomControlSupport / ZoomRatioCapability / PreviewConfig.zoomRatio / DeviceCommand.UpdateZoomRatio`，让 zoom 进入正式设备契约；
  [`SessionContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt) 与 [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 新增 `ZoomRatioToggled / ApplyZoomRatio`，并让 active graph 在切镜头、切模式、录制中都保持已选 zoom ratio；
  [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 与 [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 现已把 zoom 更新下发到 CameraX `cameraControl.setZoomRatio()`，避免为了切变焦重绑 preview；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 与 [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 现已暴露 zoom 按钮，并在 unsupported 设备上稳定呈现 `Zoom N/A`。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段已不再缺少 `切变焦` owner；当前 zoom 至少具备 supported / unsupported / recording 中保持一致的可测试语义。

## 2026-04-13：第 `7` 阶段第六轮

- 目标：把 `thermal` 从只存在 issue kind 的预留状态推进到上层通用接入口和 stage7 自动化回归。
- 核心结果：
  [`ThermalRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/ThermalRuntimeIssueMonitor.kt) 新增 `RuntimeIssueMonitor` 抽象和 `AndroidThermalRuntimeIssueMonitor`，通过 Android `PowerManager` 把 `SEVERE / CRITICAL / EMERGENCY / SHUTDOWN` thermal status 统一映射为 `DeviceRuntimeIssue(THERMAL_CRITICAL)`；
  同文件在旧系统或无 thermal service 时自动退化为 no-op，不把底层缺失误判成 app/session 回归；
  [`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 现会在 preview host attach/detach 时统一管理 thermal monitor 生命周期，并把 thermal runtime issue 转发进 session；
  [`AndroidThermalRuntimeIssueMonitorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/AndroidThermalRuntimeIssueMonitorTest.kt)、[`CameraSessionCoordinatorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt) 与 [`verify_stage_7_observability.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_7_observability.sh) 已把 monitor register/detach、critical issue forwarding 与 unsupported fallback 收进 stage7 正式口径。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段已不再缺少 `thermal` 的上层 owner；后续针对具体设备或平台差异，只需要继续适配 backend，而不用再重写 session/coordinator 契约。

## 2026-04-13：第 `7` 阶段第七轮

- 目标：把 `后台恢复` 从“只有 host attach/detach 事件”推进到显式 recovery 语义和自动化回归。
- 核心结果：
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 新增待恢复的 host detach reason 管理，前台 reattach 不再只发普通 bind，而是会发出 `preview.host.recovery.requested` 并走 `isRecovery=true` 的 bind；
  同文件在“前台先 attach、随后权限恢复”的场景下，也会继续保留待恢复原因，等权限恢复后再走同一条 recovery bind，而不是退回 `camera permission granted` 的普通起预览路径；
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 已把 `preview.host.recovery.requested` 纳入 `RecoveryTraceSnapshot` 事件窗口；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 补齐“后台 detach 后 reattach 进入 recovery”和“前台 attach 后权限恢复仍保留 recovery 语义”两条回归。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段已不再缺少 `后台恢复` 的上层 owner；后续若继续接更细粒度 app lifecycle / process lifecycle 信号，可以沿用当前 recovery trace 继续挂接。

## 2026-04-13：第 `7` 阶段第八轮

- 目标：把 `冷启动 / 首帧` 从“只有毫秒数”推进到带预算状态的 diagnostics owner。
- 核心结果：
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 新增 `PreviewStartCategory / PerfBudgetStatus / FirstFrameBudgetSnapshot`，会按 `cold start / foreground resume / recovery / reconfigure` 分类 `lastStartReason`，并给出默认 `warn/fail` 阈值；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 现会把 perf snapshot 渲染成 `budget=within budget|warning|over budget`，不再只有裸毫秒数；
  [`SessionDiagnosticsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt) 与 [`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 已补齐 recovery/resume 场景下的阈值分类回归。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段已不再缺少 `cold start / first frame` 的默认预算 owner；后续若补真机矩阵，只需要继续调预算来源，而不是再重写 diagnostics 结构。

## 2026-04-13：第 `7` 阶段第九轮

- 目标：把 `provider death` 从“只发 issue 文本”推进到至少会主动作废陈旧 provider 缓存的上层收敛。
- 核心结果：
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `shouldInvalidateCachedProviderState()`，并在 `bind/provider failure` 与 `camera fatal` 命中时主动清理 `cameraProvider / boundCamera / use case` 缓存；
  同文件 `release()` 现在也会把 `cameraProvider` 置空，避免 lifecycle stop 后继续保留旧 provider 引用；
  [`CameraXCaptureAdapterRuntimeIssueTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRuntimeIssueTest.kt) 已补齐“哪些 runtime issue 应触发 provider cache invalidation”的回归。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段对 `provider death` 已具备仓内可验证的最小 guardrail；但真正的平台级 provider death 信号和长稳验证仍不在当前仓内。

## 2026-04-13：第 `7` 阶段第十轮

- 目标：把 `provider death / 长稳恢复` 在仓内仍缺的高价值 owner，从“只能等底层主动抛错”推进到“bind 发起后长期无首帧也能被通用 watchdog 主动收敛”。
- 核心结果：
  [`SessionDiagnostics.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt) 新增 `previewStartTimeoutMillis()`，把现有 `PreviewStartCategory + fail budget` 继续提升为跨 diagnostics / watchdog 共享的超时口径，而不是再复制另一套硬编码阈值；
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 新增 `DeviceRuntimeIssueKind.PREVIEW_STALL` 及其 `displayReason / recoveryReason`，让“无首帧卡死”正式成为结构化 runtime issue，而不是混进 `bind failure` 或无类型错误文案；
  [`PreviewStartupRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitor.kt) 建立 `preview host attached -> preview binding started -> first frame / stop / detach cancel` 的首帧 watchdog；超时后统一发出 recoverable `PREVIEW_STALL`，显式提示当前仍是上层通用收敛，不依赖具体设备 backend；
  [`ThermalRuntimeIssueMonitor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/ThermalRuntimeIssueMonitor.kt)、[`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 与 [`AppContainer.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/AppContainer.kt) 现已把 `thermal + startup stall` 组合成统一 `CompositeRuntimeIssueMonitor`；coordinator 在 bind start、first frame、preview stop、runtime issue 上统一驱动 watchdog 生命周期，避免这条 owner 回灌成新的 session/coordinator 影子状态机；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt)、[`SessionDiagnosticsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt)、[`PreviewStartupRuntimeIssueMonitorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitorTest.kt)、[`CameraSessionCoordinatorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt) 与 [`verify_stage_7_observability.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_7_observability.sh) 已把 timeout budget、watchdog 超时、coordinator 转发和 session recovery 语义收入口径。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest`
  `./scripts/verify_stage_7_observability.sh`
- 结论：第 `7` 阶段对 `provider death / 长稳恢复` 已不再只有被动等待底层报错这一条路；当前仓内至少具备“bind 卡死无首帧 -> 结构化 runtime issue -> recovery request -> 自动化回归”的最小主动 guardrail。剩余最高价值缺口转向平台级 provider death 真信号与真机矩阵，这部分继续推进已显著依赖外部条件。

## 2026-04-13：`6B-8` 第一轮

- 目标：把 `manualCaptureDraft` 从“只写 metadata / EXIF 的说明性数据”推进到真实 `capture profile -> device request translation` owner，并固化当前 stage 验证脚本。
- 核心结果：
  [`MediaPipelineContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt) 为 `CaptureProfile` 新增 `manualCaptureParams`，让手动参数正式进入拍摄契约；
  [`ProModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt) 现会把 `catalog.manualCaptureDraft` 同时写进 `manualDraft*` metadata 与 `captureProfile.manualCaptureParams`；
  [`DeviceShotRequestTranslator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt) 新增 manual request translation 和 `device:manual=*` diagnostics；当设备不支持 manual controls 时会显式降级为 `unsupported-saved-only`，不再默默把 draft 假装成已执行请求；
  [`DefaultDeviceShotRequestTranslatorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/test/kotlin/com/opencamera/core/device/DefaultDeviceShotRequestTranslatorTest.kt) 与 [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 补齐 supported / unsupported request translation 和 `Pro` 模式 manual draft 进入 `CaptureProfile` 的回归；
  新增 [`verify_stage_6b8_manual_pro.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b8_manual_pro.sh) 作为 `6B-8` 第一轮正式验证入口。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./scripts/verify_stage_6b8_manual_pro.sh`
- 结论：`6B-8` 已不再缺少 manual parameter model 到 request translation 的最小 owner 闭环；当前剩余最高价值缺口转向 mode-local pro variant/render model，以及 adapter/provider 对 RAW / manual request 的真实执行。

## 2026-04-13：`6B-8` 第二轮

- 目标：在不复制独立 `Pro` mode 的前提下，为 `NIGHT / PORTRAIT / HUMANISTIC` 建立独立运行态 `Pro` 按钮，并形成 mode-local `pro variant` owner。
- 核心结果：
  [`ModeContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt)、[`SessionContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt)、[`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 与 [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 新增独立 `Pro` 运行态按钮契约与点击链路；
  [`NightModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt)、[`PortraitModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt)、[`HumanisticModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt) 现在都支持 `Enter/Exit Pro`，并会把 `manualCaptureDraft` 与 unsupported/saved-only 提示写进 capture metadata、`CaptureProfile`、snapshot detail 与 EXIF；
  [`ModeCatalogContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeCatalogContracts.kt)、[`ModeCatalogContractsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/test/kotlin/com/opencamera/core/mode/ModeCatalogContractsTest.kt)、[`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 同步把 `Pro variant` 纳入 mode declaration / UI 回归。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
- 结论：`6B-8` 已不再缺少目标 mode 的独立运行态 `Pro` 入口；当前 mode-local owner 已从“全局 Pro mode 旁路”收敛为 “模式内 variant”。

## 2026-04-13：`6B-8` 第三轮

- 目标：把 `manualCaptureParams` 从 `DeviceShotRequest` 继续推进到 adapter partial consume，并显式区分“已执行”和“仅保存语义”。
- 核心结果：
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `Camera2ManualCaptureConfig`、manual execution diagnostics 与 Camera2 interop still request apply：`ISO / shutter / EV / focus / aperture` 进入实际 still request config；
  同文件会把 `RAW / WB` 显式标成 `adapter:manual-request=saved-only/partial`，避免把未真实执行的字段伪装成已下沉；
  新增 [`CameraXCaptureAdapterManualRequestTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterManualRequestTest.kt) 验证 request->Camera2 config 映射和 `applied / partial / saved-only` diagnostics；
  [`verify_stage_6b8_manual_pro.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b8_manual_pro.sh) 升级为当前 stage 的正式验证脚本，纳入 `ModeCatalogContractsTest`、`SessionUiRenderModelTest` 与 `CameraXCaptureAdapterManualRequestTest`。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterManualRequestTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:assembleDebug`
  `./scripts/verify_stage_6b8_manual_pro.sh`
- 结论：`6B-8` 已不再是“只有 contract / translator，没有 adapter consume”的中间态；但 `RAW` 与 `WB Kelvin` 仍未形成仓内可收敛的真实执行闭环。

## 2026-04-13：`6B-8` 第四轮

- 目标：在不硬推真实 `RAW / WB` 执行的前提下，把 mode-local `Pro` 的上层编辑能力、saved-only 持久化和 `per-control capability matrix` 通用结构补齐到 stage exit 所需状态。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt)、[`FeatureCatalogStore.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/FeatureCatalogStore.kt)、[`SharedPreferencesFeatureCatalogStore.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SharedPreferencesFeatureCatalogStore.kt) 与 [`SessionSettingsManager.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionSettingsManager.kt) 让 `manualCaptureDraft` 正式具备 runtime 编辑与持久化闭环，不再只停在 mode metadata；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 与 [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 新增 mode-local `Pro` controls panel，并按控制级能力渲染 `Camera2 interop / Saved only / Temporarily unsupported`；
  [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt)、[`DeviceShotRequestTranslator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt) 与 [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `ManualControlCapabilityMatrix`，并把 manual draft 以 `request + matrix + diagnostics` 的方式推进到 adapter；当前默认让 `ISO / shutter / EV / focus / aperture` 可执行，`RAW / WB` saved-only，显式 unsupported 控件保留 future device adaptation 接点；
  [`CameraXCaptureAdapterCapabilityDetectionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt)、[`CameraXCaptureAdapterManualRequestTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterManualRequestTest.kt)、[`SessionSettingsManagerTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt)、[`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 与 [`verify_stage_6b8_manual_pro.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b8_manual_pro.sh) 把 capability matrix merge、saved-only persistence、runtime controls render、unsupported diagnostics 和 assemble 收为正式口径。
- 验证：
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest`
  `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterManualRequestTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./scripts/verify_stage_6b8_manual_pro.sh`
- 结论：按当前产品决策，`6B-8` 已满足 stage exit checklist。真实 `RAW / WB` 执行与更细粒度真机能力探测继续推进会显著增加硬件风险，适合作为后续设备适配事项，而不再属于当前 stage 的必做闭环。

## 2026-04-12：`6B-7` 第一轮

- 目标：把 `Live` 从“settings / metadata flag”推进到真实 `still + motion + sidecar` 媒体组合契约，并接通三类 still mode 的 capture strategy。
- 核心结果：
  [`MediaPipelineContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt) 新增 `ShotKind.LIVE_PHOTO`、`LivePhotoCaptureSpec`、`LivePhotoBundle` 与 `CaptureStrategy.LivePhoto`，让 `ShotExecutor` 能正式规划 live capture；
  [`DeviceShotRequestTranslator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt) 为 `LIVE_PHOTO` 建立 still template 翻译和 `device:live-photo=*` diagnostics；
  [`PhotoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt)、[`HumanisticModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt)、[`PortraitModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt) 在 `livePhotoDefault=on` 时发出真实 `CaptureStrategy.LivePhoto`；
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt)、[`SessionContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt)、[`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 与 [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 现已保存并展示 `latestLivePhotoBundle`；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `captureLivePhoto()` 与 locatable `motionPath / sidecarPath` 生成。
- 验证：
  `./gradlew --no-daemon :core:media:test --tests com.opencamera.core.media.ShotExecutorTest :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest :app:assembleDebug`
- 结论：`6B-7` 已不再缺少 live capture plan / media bundle / render model 的最小主链。

## 2026-04-12：`6B-7` 第二轮

- 目标：把 `plan.md` 要求中的 `failure cleanup` 从缺口推进到 adapter 可验证 owner。
- 核心结果：
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `stillCaptureCleanupPaths()`、`cleanupStillCaptureArtifacts()` 与 `cleanupAbsoluteFilePaths()`，在 still/live capture 失败或完成事件抛错时对主输出、bundle 路径和中间输出做 best-effort 清理；
  同文件的 `PhotoCaptureOutcome.Failure` 现已携带 cleanup paths，legacy file output 不再在失败路径上静默留下半成品；
  新增 [`CameraXCaptureAdapterLivePhotoTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt) 验证 live bundle 输出删除、content-uri delete hook 和去重后的 cleanup path 语义；
  [`verify_stage_6b7_live_photo.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b7_live_photo.sh) 已纳入 adapter live cleanup 测试。
- 验证：
  `./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest`
  `./scripts/verify_stage_6b7_live_photo.sh`
- 结论：`6B-7` 当前已具备 `Live capture plan + media bundle + failure cleanup` 的仓内可验证闭环，但真实短动态段采集和 mux 仍未落地。

## 2026-04-12：`6B-7` 第三轮

- 目标：把 `thumbnail + sidecar metadata` 从“隐含在 still 输出里”推进到 live bundle 正式 owner，并让 file-backed sidecar 真正落盘。
- 核心结果：
  [`MediaPipelineContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt) 为 `LivePhotoBundle` 新增 `thumbnailPath`，明确 live 的 thumbnail owner 不再只是 still 输出的隐式语义；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `materializeLivePhotoSidecar()` 与 `buildLivePhotoSidecarPayload()`，file-backed live capture 成功后会把 still / motion / sidecar / thumbnail / mime / duration 元数据写入 sidecar 文件；
  同文件在 sidecar materialization 失败时会回退到 `PhotoCaptureOutcome.Failure`，并沿用 live cleanup owner 回滚主输出和 sidecar 残留；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 与 [`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 现在会在 live capture 输出摘要中展示 `Thumbnail:`；
  [`CameraXCaptureAdapterLivePhotoTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt) 补齐 sidecar materialization 与 thumbnail-aware cleanup 回归。
- 验证：
  `./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./scripts/verify_stage_6b7_live_photo.sh`
- 结论：`6B-7` 的 `metadata + thumbnail` 半链路已经从“路径占位”推进到“bundle owner + file-backed sidecar materialization”，剩余最高价值缺口集中到真实 motion capture / mux。

## 2026-04-12：`6B-7` 第四轮

- 目标：把 MediaStore 路径下的 live sidecar 从“只有 display path 的占位信息”推进到独立 handle、真实 materialization 和可回滚 cleanup owner。
- 核心结果：
  [`MediaPipelineContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt) 为 `LivePhotoBundle` 新增 `motionHandle / sidecarHandle / thumbnailHandle`，让 live companion asset 不再只有字符串路径；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 现会在 MediaStore still output 成功后为 live sidecar 创建独立 `MediaStore.Files` 句柄，并通过 `content-uri` 写入 sidecar payload；同文件新增 `stillCaptureCleanupContentUris()`，把 still/live companion content-uri 删除纳入统一 cleanup；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 与 [`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 现在会在 live 输出摘要中附带可用的 MediaStore `content-uri`；
  [`CameraXCaptureAdapterLivePhotoTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt) 补齐 content-uri sidecar materialization、live companion content-uri cleanup 与去重回归。
- 验证：
  `./gradlew --no-daemon :core:media:test --tests com.opencamera.core.media.ShotExecutorTest`
  `./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest`
  `./scripts/verify_stage_6b7_live_photo.sh`
- 结论：`6B-7` 已具备 file-backed / MediaStore 双路径的 live sidecar owner 与 cleanup 语义；当前 stage 剩余最高价值缺口已基本收敛到真实 short motion capture / mux / motion asset materialization，而这部分在当前仓内验证条件下难以安全继续硬推。

## 2026-04-12：`6B-6` 第一轮

- 目标：把 `defaultVideoSpec` 从“settings/UI/metadata 已有”推进到 `capability matrix -> session/device graph -> adapter quality select` 的真实 owner 闭环。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 为录像规格新增 `8K` 契约与按分辨率建模的 fps matrix；
  [`VideoSpecSelection.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/VideoSpecSelection.kt) 与 [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt) 建立 `requested -> resolved` 选择与降级语义，`RecordingConfig` 正式持有 `requestedVideoSpec / videoSpec / qualityPreset`；
  [`VideoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt) 改为基于 capability matrix 轮转录像规格，并把 requested/resolved 视频规格同时写入 shot metadata；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 不再硬编码 “4K fallback to FHD”，而是按 active device constraints 渲染 supported / degraded；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 与 [`CameraXCaptureAdapterRecordingQualityTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRecordingQualityTest.kt) 补齐 `UHD -> Quality.UHD` 的 adapter consume。
- 验证：
  `./gradlew --no-daemon :core:device:test --tests com.opencamera.core.device.VideoSpecSelectionTest :app:compileDebugKotlin`
  `./gradlew --no-daemon :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
  `./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRecordingQualityTest`
- 结论：`6B-6` 的分辨率/规格 owner 已从“设置层影子状态”推进到“device graph + adapter consume”的真实闭环。

## 2026-04-12：`6B-6` 第二轮

- 目标：把 `LOW_LIGHT_AUTO_24FPS` 从“只是一枚 settings 值”推进到可执行的 runtime policy helper，并固化正式 stage 验证入口。
- 核心结果：
  [`VideoSpecSelection.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/VideoSpecSelection.kt) 新增 `VideoSceneSignal` 与 `resolveRuntimeVideoSpec()`，在 low-light signal 命中时把 runtime fps 收敛到 `24fps` 或最接近的 supported fallback；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增 `videoSceneSignalProvider` 注入点，并在录像完成结果里写入 `device:video-scene=low-light` / `device:video-runtime-fps=*` pipeline note；
  [`VideoSpecSelectionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/test/kotlin/com/opencamera/core/device/VideoSpecSelectionTest.kt) 补齐 low-light runtime fps 纯测；
  新增 [`verify_stage_6b6_video_spec.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b6_video_spec.sh) 作为 `6B-6` 当前正式验证入口。
- 验证：
  `./gradlew --no-daemon :core:device:test --tests com.opencamera.core.device.VideoSpecSelectionTest`
  `./scripts/verify_stage_6b6_video_spec.sh`
- 结论：`6B-6` 现在已具备 capability-gated video spec、runtime low-light fps policy helper 与正式验证脚本，但真实 recorder fps / concert route 仍未落底层。

## 2026-04-12：`6B-6` 第三轮

- 目标：把 `6B-6` 剩余的高价值 P1 从“保守默认 capability + 纯 helper”推进到“per-lens capability detect + CameraX target fps bind”。
- 核心结果：
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 新增按镜头读取 `CamcorderProfile` 与 `StreamConfigurationMap` 的 `videoSpecConstraints` 探测，并按当前 lens 合并进 `resolveDeviceCapabilities()`；
  同文件把录像 use case 从 `VideoCapture.withOutput(recorder)` 改为 `VideoCapture.Builder(recorder).setTargetFrameRate(...)`，使 resolved/runtime `VideoSpec.frameRate` 进入实际 CameraX 绑定层；
  low-light runtime 24fps 命中时，adapter 会先按 runtime `VideoSpec` 重绑录像 use case，再启动 recording，并在 pipeline notes 里补充 `device:video-bound-fps=*`；
  [`CameraXCaptureAdapterRecordingQualityTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRecordingQualityTest.kt) 补齐 per-lens constraint merge 与 runtime/bound fps helper 回归。
- 验证：
  `./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRecordingQualityTest`
  `./scripts/verify_stage_6b6_video_spec.sh`
- 结论：`6B-6` 的 fps owner 已从“metadata + runtime helper”继续推进到“CameraX video use case bind 层”，而 per-lens capability matrix 也已不再依赖单一保守默认值。

## 2026-04-11：`6B-5` 第一轮

- 目标：先完成 `portrait profile -> capture metadata -> portrait processor spec` 的核心闭环。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 新增 `PortraitProfile / PortraitBeautyPreset / PortraitBeautyStrength / PortraitBokehEffect` 以及对应 reducer actions；
  [`PersistedSettingsSerializer.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt) 与 [`PersistedSettingsSerializerTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt) 补齐人像产品配置持久化回归；
  [`PortraitModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt) 把 profile / beauty / bokeh 写入 shot metadata 与 EXIF，并同步进入 mode snapshot summary；
  [`PortraitRenderPostProcessor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt) 将新 metadata 解析为轻量人像 render spec，支持更保守的 `Native` 和更鲜明的 `Luminous` 差异化渲染；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 与 [`PortraitRenderPostProcessorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt) 补齐 metadata / EXIF / spec 回归。
- 验证：
  `./gradlew :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest :app:assembleDebug`
- 结论：`6B-5` 的 settings contract、capture metadata 和 lightweight processor spec 主链成立。

## 2026-04-11：`6B-5` 第二轮

- 目标：把 `6B-5` 的人像产品设置从“测试可注入”推进到用户可达的独立设置入口。
- 核心结果：
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 新增独立 `PortraitLabPageRenderModel`，并让 `Lens Lab` root page 明确出现 `Portrait Lab` 入口；
  [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 与 [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 接通 `Lens Lab -> Portrait Lab` 二级页导航，以及 `profile / beauty preset / beauty strength / bokeh effect` 四个设置按钮；
  [`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 补齐 root page 和 portrait lab 的 render model 回归；
  新增 [`verify_stage_6b5_portrait_lab.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b5_portrait_lab.sh) 作为当前 stage 正式验证脚本。
- 验证：
  `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest :app:assembleDebug`
  `./scripts/verify_stage_6b5_portrait_lab.sh`
- 结论：`6B-5` 的 UI / settings 闭环已成立，stage exit checklist 满足。

## 2026-04-11：`6B-4` 第一轮

- 目标：把构图线和倒计时从“设置项 / 文案”推进到真实 preview overlay 与多模式 capture gate。
- 核心结果：
  [`PreviewOverlayView.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/PreviewOverlayView.kt)、[`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 新增真实预览 overlay，支持 `3x3 / Golden` 构图线和 countdown 气泡；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 新增 `previewOverlayRenderModel()`，把 preview host / preview status / countdown 与 grid mode 收敛成 UI 可消费状态；
  [`PortraitModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt) 与 [`NightModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt) 现已消费共享 countdown 设置；`Photo / Humanistic / Portrait / Night` 的 still capture 倒计时语义对齐；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 补齐 countdown 的切模式阻断、preview host detach 取消、权限丢失取消，以及 portrait/night countdown 回归；
  [`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 补齐 overlay render model 单测；
  [`verify_stage_6b4_capture_aids.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b4_capture_aids.sh) 建立 stage 正式验证入口。
- 验证：
  `./scripts/verify_stage_6b4_capture_aids.sh`
- 结论：`6B-4` 的 grid overlay 与多模式 countdown 主链成立。

## 2026-04-11：`6B-4` 第二轮

- 目标：把 `selfie mirror / shutter sound` 从 persisted setting 推进到真实消费语义。
- 核心结果：
  [`ModeContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt) 新增统一 `captureAidMetadataTags()`，把 `captureLensFacing / selfieMirrorEnabled / selfieMirrorApply / shutterSoundEnabled` 收敛成 still modes 共用 metadata tags；
  [`PhotoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt)、[`HumanisticModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt)、[`PortraitModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt)、[`NightModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt)、[`ProModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt)、[`DocumentModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt) 全部接入 capture aid metadata；
  [`PhotoSelfieMirrorPostProcessor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PhotoSelfieMirrorPostProcessor.kt) 新增静态图镜像后处理，前摄自拍镜像不再只停在 metadata；
  [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 现已对前摄预览应用镜像，并在 photo shot 进入执行时按设置触发系统快门声；
  [`MediaPipelineContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt) 将 `shutter-sound:*` 与 `selfie-mirror:requested` 纳入 pipeline notes；
  [`PhotoSelfieMirrorPostProcessorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PhotoSelfieMirrorPostProcessorTest.kt) 与 [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt) 补齐 metadata / postprocessor 回归。
- 验证：
  `./scripts/verify_stage_6b4_capture_aids.sh`
- 结论：`6B-4` 的 selfie mirror / shutter sound 已形成 preview + metadata + still-photo consume 的完整闭环。

## 2026-04-11：`6B-3` 第一轮

- 目标：把水印从“模板 id + 一行文字”推进到模板 resolver、结构化 token 与真实边框成片。
- 核心结果：
  [`PhotoWatermarkPostProcessor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt) 新增 `resolvePhotoWatermarkTemplate()`、`WatermarkFrameBackground`、EXIF token 解析、expanded frame 渲染；
  [`PhotoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt) 为拍照主链补齐 `watermarkModel / watermarkDatetime / watermarkCameraParams` fallback tags；
  [`PhotoWatermarkTemplateResolverTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt) 建立 template resolver 纯测；
  [`verify_stage_6b3_watermark_v2.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b3_watermark_v2.sh) 建立当前 stage 正式验证入口。
- 验证：
  `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest :app:assembleDebug`
  `./scripts/verify_stage_6b3_watermark_v2.sh`
- 结论：`6B-3` 的模板 resolver、结构化 token、边框成片主链成立。

## 2026-04-11：`6B-3` 第二轮

- 目标：把 watermark 渲染控制从硬编码样式推进到 metadata contract 可配置。
- 核心结果：
  [`PhotoWatermarkPostProcessor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt) 为模板解析补齐 `placement / textScale / textOpacity`，并让 classic overlay 与 frame templates 真正消费这些控制项；
  [`PhotoWatermarkTemplateResolverTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt) 补齐 background / position / opacity override 回归。
- 验证：
  `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest :app:assembleDebug`
  `./scripts/verify_stage_6b3_watermark_v2.sh`
- 结论：`6B-3` 当前已具备“模板化 + 可配置 contract + expanded frame”能力，但 settings / UI 侧仍未闭环。

## 2026-04-11：`6B-3` 第三轮

- 目标：把 watermark style settings 从 metadata / reducer 推进到用户可操作的独立 `Watermark Lab`。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 与 [`PersistedSettingsSerializer.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt) 补齐 per-template `placement / scale / opacity / background` 枚举、style settings、reducer 和持久化；
  [`PhotoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt) 把 template-specific style 真正写进 capture metadata；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt)、[`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 新增独立 `Watermark Lab` selector/detail 页面，并把 `Lens Lab` 的 watermark 入口改成进入二级页面而不是直接循环模板；
  [`PersistedSettingsSerializerTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt)、[`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt)、[`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt) 补齐 serializer / metadata / UI render model 回归；
  [`verify_stage_6b3_watermark_v2.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b3_watermark_v2.sh) 纳入当前 stage 的 settings / UI 验证。
- 验证：
  `./gradlew :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest :app:assembleDebug`
  `./scripts/verify_stage_6b3_watermark_v2.sh`
- 结论：`6B-3` 的 settings / UI 闭环已经成立，水印从“模板 id + metadata override”推进到了“独立 Watermark Lab + per-template 持久化设置”。

## 2026-04-11：`6B-3` 第四轮

- 目标：把 `Live` 动态段里的 watermark 行为从口头决策推进到正式 contract，并用真实样本图补一轮人工验证。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 新增 `LiveWatermarkMotionBehavior`，并让 [`LiveMediaBundle`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 正式携带 live watermark 动态策略；
  [`PhotoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt) 与 [`HumanisticModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt) 在 `livePhotoDefault=on` 时写入 `liveWatermarkMode / liveWatermarkBehavior / liveWatermarkBrightnessCoupling / liveWatermarkOpacityCoupling`；
  [`MediaPipelineContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt) 与 [`ShotExecutorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt) 补齐 `live-watermark:*` pipeline note 回归；
  [`DefaultCameraSessionTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt)、[`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt)、[`verify_stage_6b3_watermark_v2.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b3_watermark_v2.sh) 同步升级；
  使用样本图 [`Gj7c0RnXwAEBSS3.jpg`](/Volumes/Extreme SSD/好图/Gj7c0RnXwAEBSS3.jpg) 生成三张人工验证样本图，输出见 [`codex/artifacts/6b3_manual_fixture`](/Volumes/Extreme SSD/New_Camera/codex/artifacts/6b3_manual_fixture/report.txt)。
- 验证：
  `./gradlew :core:media:test --tests com.opencamera.core.media.ShotExecutorTest :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest :app:assembleDebug`
  `./scripts/verify_stage_6b3_watermark_v2.sh`
- 结论：`6B-3` 已不再缺少 `Live watermark` 的产品定义与 metadata 契约；当前剩余高价值问题转向自动化位图 golden 与真正的 live 动态媒体执行。

## 2026-04-11：预览显示能力核查

- 目标：判断当前工程是否已经支持“恰当显示相机预览画面”，若不能则形成可执行的工程复盘记录。
- 核心结果：
  [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml:8) 已有真实 `PreviewView`，[`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt:980) 也确实把 `Preview` 绑定到了 `surfaceProvider`，因此“基础出画链路存在”这一点成立；
  但 [`DeviceContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt:49) 的 `PreviewConfig` 只建模 `snapshotsEnabled`，[`ModeContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt:136) 也没有独立 preview presentation 契约，导致预览比例、镜像、旋转、framing 没有稳定 owner；
  当前 [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml:17) 直接用 `fillCenter` 决定预览裁切策略，而仓内又没有 screenshot / instrumentation 级验证去证明这一策略在多模式、多镜头和横竖屏下是正确的。
- 验证：
  `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest`
  `./gradlew :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`
- 结论：当前项目支持“预览能绑、能出画、能上报首帧和基础恢复状态”，但还不能宣称已经支持“恰当显示相机预览画面”。

## 2026-04-11：`6B-2` 第六轮

- 目标：把 `Filter Lab` 的 advanced items 从“部分接通”补齐到与 [`plan.md`](/Volumes/Extreme SSD/New_Camera/codex/plan.md) 一致的正式调参闭环。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 为 `FilterRenderSpec` 补齐 `tintShift + haloStrength`，并同步进入 metadata tags、share codec 与 imported serializer；
  [`PhotoAlgorithmPostProcessor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt) 让 `halo / tint shift` 进入静态图后处理真实消费链；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt)、[`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 把 `HALO / TEMP SHIFT / TINT SHIFT` 接进 `Filter Lab` advanced roster，形成 12 项进阶调参；
  [`FilterProfileShareCodecTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/test/kotlin/com/opencamera/core/settings/FilterProfileShareCodecTest.kt)、[`SessionSettingsManagerTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt)、[`SessionUiRenderModelTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt)、[`PhotoAlgorithmPostProcessorTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt) 补齐对应回归。
- 验证：
  `./gradlew :core:settings:test --tests com.opencamera.core.settings.FilterProfileShareCodecTest :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest`
  `./scripts/verify_stage_6b2_filter_lab.sh`
- 结论：在导入 / 导出入口继续后置的前提下，`6B-2` 的轻量调色板 + advanced items 正式闭环成立；当前 stage exit checklist 满足。

## 2026-04-10：`6B-1` 收口

- 目标：把 mode directory 从 app 层拼装逻辑下沉为共享契约。
- 核心结果：
  [`ModeCatalogContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeCatalogContracts.kt) 新增 `ModeDirectoryDeclaration + ModeId.modeDirectoryDeclaration()`；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 改为只消费共享 declaration；
  [`ModeCatalogContractsTest.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/test/kotlin/com/opencamera/core/mode/ModeCatalogContractsTest.kt) 与 [`verify_stage_6b1_mode_catalog.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b1_mode_catalog.sh) 建立正式回归。
- 验证：
  `:core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest`
  `./scripts/verify_stage_6b1_mode_catalog.sh`
- 结论：`6B-1` exit checklist 满足，可进入 `6B-2`。

## 2026-04-10：`6B-2` 第一轮

- 目标：把滤镜从硬编码 `algorithmProfile` 推进到可枚举、可分享、可持久化、可静态成片消费的 render spec。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 新增 `FilterRenderSpec`、`FilterProfileShareCodec`、catalog merge 与 imported serializer；
  [`FeatureCatalogStore.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/FeatureCatalogStore.kt) 与 app 侧 store 打通 imported custom filter 持久化；
  [`PhotoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt)、[`HumanisticModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt)、[`VideoModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt) 将选中滤镜 render spec 写入 metadata；
  [`PhotoAlgorithmPostProcessor.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt) 可从 metadata 解析 render spec 执行静态图后处理。
- 验证：
  `:core:settings:test`
  `:app:testDebugUnitTest`
  `:core:session:test`
  `:app:assembleDebug`
- 结论：滤镜 contract、share file、catalog persistence、photo consume 第一条主闭环成立。

## 2026-04-11：`6B-2` 第二轮

- 目标：建立独立 `Filter Lab`，并让 `Humanistic / Portrait` 默认滤镜进入真实消费链。
- 核心结果：
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 新增 `FilterLabFamily / FilterLabPageRenderModel`；
  [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt)、[`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml)、[`strings.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/values/strings.xml) 新增独立 `Filter Lab` 面板与 family tabs；
  [`HumanisticModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt) 与 [`PortraitModePlugin.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt) 正式消费对应默认滤镜；
  [`ModeCatalogContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeCatalogContracts.kt) 修正 photo 默认滤镜 label 渲染。
- 结论：`Filter Lab` 与模式默认滤镜真实消费链成立，但导入 / 导出继续后置。

## 2026-04-11：`6B-2` 第三轮

- 目标：把 custom filter 从“只能导入”推进到“可在 app 内自生成”。
- 核心结果：
  [`SettingsContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt) 新增 `FeatureCatalog.createCustomFilterProfile()`；
  [`SessionSettingsManager.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionSettingsManager.kt) 新增 `saveCurrentFilterAsCustom()`；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt)、[`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt)、[`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 接通 `Save as Custom` 入口。
- 验证：
  `:core:settings:test`
  `:app:testDebugUnitTest`
  `:core:session:test`
  `:app:assembleDebug`
- 结论：custom filter 已可由 app 内最小链路自生成，但仍只是 render spec 快照，不等于完整调参系统。

## 2026-04-11：`6B-2` 第四轮

- 目标：把调节入口挂到当前选中滤镜项本身，而不是列表外的全局按钮。
- 核心结果：
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 为选中项显式建模 `adjustButtonLabel`；
  [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 把列表改成“未选中项切换、选中项调节”的动态卡片；
  [`activity_main.xml`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/res/layout/activity_main.xml) 删除旧的全局 `buttonFilterAdjust`。
- 验证：
  `:app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest`
  `:app:assembleDebug`
- 结论：调节入口位置和 render model 语义与最新交互决策对齐。

## 2026-04-11：`6B-2` 第五轮

- 目标：把 `6B-2` 的人工验证组合固化为正式脚本。
- 核心结果：
  [`verify_stage_6b2_filter_lab.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b2_filter_lab.sh) 建立 stage 正式验证入口，并内置噪音清理。
- 验证：
  `./scripts/verify_stage_6b2_filter_lab.sh`
- 结论：当前 `6B-2` 已具备可重复复跑的正式验证口径。

## 2026-04-11：决策补记

- 先前“`6B-2` 的 P0/P1 已基本清空”的说法只适用于第五轮停点，不再代表当前判断。
- 最新有效判断是：
  导入 / 导出仍明确后置；
  `6B-2` 仍需继续推进调参交互；
  轻量级以长方形调色板承载“横划调颜色 / 纵划调影调”；
  进阶级继续向 [`plan.md`](/Volumes/Extreme SSD/New_Camera/codex/plan.md) 所列 advanced items 收口。

---

# 历史归档（精简版）

## 整理原则

- 主文档只保留当前阶段判断、最近有效闭环、正式验证口径、仍影响下一步的风险。
- 旧阶段的重复验证日志、已失效中间态、相同问题的多轮重述统一压缩。
- 历史内容保留“为什么能进入下一步”与“哪些风险仍然有效”，不保留逐条流水账。

## `6B-0` 摘要

- 结论：`6B-0 设置、持久化和能力目录底座` 已完成并经过复核。
- 已成立的关键事实：
  独立 [`core/settings`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/settings) 模块；
  `SessionSettingsSnapshot` 已进入 [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt)；
  [`ModeContracts.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt) 已把 settings snapshot 作为 mode context 输入；
  [`SessionUiRenderModel.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt) 与 [`MainActivity.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/MainActivity.kt) 已建立 `Lens Lab` 与 render-model-only 交互边界；
  默认 `VideoSpec / photo filter / video filter / Live default / watermark template / manual draft` 均已形成最小真实消费链；
  [`verify_stage_6b0_settings_foundation.sh`](/Volumes/Extreme SSD/New_Camera/OpenCamera/scripts/verify_stage_6b0_settings_foundation.sh) 已成为正式验证入口。
- 留存影响：
  `6B-0` 的功能闭环已满足，但架构 owner 问题并未因 settings 基础设施落地而自动消失。

## 2026-04-10 架构审阅摘要

- 审阅结论：高优先级问题不是“有没有分层”，而是 `state owner / scheduling owner` 尚未钉死。
- 仍有效的风险摘要：
  `P0`：[`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 同时维护公开状态与私有影子状态；
  `P0`：[`CameraSessionCoordinator.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt) 已演化成影子状态机，而不只是桥接层；
  `P1`：[`SessionSettingsManager.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/SessionSettingsManager.kt) 与 session 双判定 settings acceptance，存在持久化 / 运行态分叉窗口；
  `P1`：[`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 过厚，正向 God Object 演化；
  `P1`：mode 层已有状态复制，录像路径尤甚。
- 仍有效的建议：
  若后续进入 `Live / RAW / 更复杂 preview 恢复 / 第 7 阶段稳定性治理`，应优先做 owner 收敛，而不是继续横向叠 feature。

## 第 6 阶段基线摘要

- 结论：原第 6 阶段 feature 扩展基线已完成并归档。
- 已成立的代表性能力：
  `PHOTO / PRO / NIGHT / VIDEO` 的关键能力与动态能力刷新；
  静态图 `style / watermark / frame ratio / document auto-crop / portrait render`；
  video quality、watermark sidecar、native still output size 等已形成真实 session graph、adapter binding 或 media metadata 闭环。
- 留存影响：
  录像 torch 仍不是录制中实时控制；
  视频 watermark 仍非画面内烧录；
  portrait / document / style 仍以保守后处理为主，不代表真实厂商级算法。

## 第 5 阶段摘要

- 结论：`PRO / DOCUMENT / PORTRAIT / NIGHT` 四类复杂模式已迁入，`NIGHT` 已进入真实多帧执行。
- 阶段共识：
  复杂模式保留入口、按能力降级，不因能力不足直接隐藏；
  模式只声明策略与状态，真实执行仍统一收敛到 `Session Kernel + Device Adapter + Media Pipeline`。
- 留存风险：
  多数验证仍停留在 local unit test / assemble 级别；
  `NIGHT` 多帧执行已成立，但最终成片仍不是融合结果；
  真实 Android 生命周期与设备热稳定性仍缺 instrumentation 证明。

## 第 4 阶段完成后的工程复盘与加固

- 结论：进入第 5 阶段前，优先补强统一 `shot / media pipeline` 的状态机一致性和失败清理。
- 关键结果：
  [`DefaultCameraSession.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt) 补强 in-flight shot 防护与权限中断失败收敛；
  [`CameraXCaptureAdapter.kt`](/Volumes/Extreme SSD/New_Camera/OpenCamera/app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt) 建立统一 `emitShotFailure()` 失败清理；
  对应 session 回归测试已建立。
- 留存影响：
  该阶段主要作为后续复杂模式迁入和第 6 阶段 feature 扩展的稳定性背景，不再单独作为当前执行目标。
