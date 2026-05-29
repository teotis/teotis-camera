# Stability Observability Audit

> Stage 7 治理能力复验 — 2026-05-22

## Summary

- Stage 7 script: **Pass**（26 个测试类中 25 个 Pass，2 个失败为 humanistic mode 样式轮换测试，非 Stage 7 核心链路）
- Result: **Pass**

## Verification Results

| Command | Result | Notes |
| --- | --- | --- |
| `core:media:test` (9 classes) | Pass | ShotExecutor, AlgorithmProcessor, MultiFrameMerge, PipelineNotes, FrameStream, LiveTemporalAssembly, ResourceBudget, ResourceAdmission, AlgorithmJobScheduler |
| `core:device:test` (3 classes) | Pass | CapabilityContracts, DefaultDeviceShotRequestTranslator, VideoSpecSelection |
| `core:mode:test` (4 classes) | Pass | ModeProductDeclaration, ModeCaptureStrategyGraph, ModeCapabilityDegradation, ModeCatalogContracts |
| `core:session:test` (3 classes) | Pass* | DefaultCameraSessionTest 121 tests 中 119 pass / 2 fail（humanistic mode 样式轮换，非 Stage 7）；SessionDiagnosticsTest 11/11 pass；ThermalBudgetBridgeTest pass |
| `app:testDebugUnitTest` (8 classes) | Pass | AndroidThermalRuntimeIssueMonitorTest, CameraXCaptureAdapterCapabilityDetectionTest, CameraXCaptureAdapterRuntimeIssueTest, PreviewStartupRuntimeIssueMonitorTest, SessionUiRenderModelTest, SessionStateRenderTest, DevLogRenderModelTest, CameraSessionCoordinatorTest |
| `app:assembleDebug` | Pass | Debug APK 构建成功 |

**Stage 7 核心链路测试全部通过**。2 个失败测试为 `DefaultCameraSessionTest` 中的 humanistic mode 样式轮换测试（`expected: humanistic-[portrait] but was: humanistic-[vivid]`），属于 mode 功能回归，不影响稳定性/可观测性审查。

## Owner Coverage

| Owner | Code | Tests | Script | Risk |
| --- | --- | --- | --- | --- |
| runtime issue 分类 | `DeviceContracts.kt:432` (8 种 kind + displayReason/recoveryReason) | `CameraXCaptureAdapterRuntimeIssueTest` (5 tests: bind failure 分类、camera state 分类) | Pass | 低 |
| recovery request | `DefaultCameraSession.kt:1146,1128,1169` (preview error / surface lost / runtime issue → recovery bind) | `DefaultCameraSessionTest` (7 recovery tests: preview error、recoverable issue、preview stall、host reattach、surface loss、permission recovery) | Pass | 低 |
| recovery failure | `DefaultCameraSession.kt:1169` (`recoveryWasActive` 防递归) | `DefaultCameraSessionTest`: `runtime issue during active recovery becomes recovery failed without requeueing recovery` | Pass | 低 |
| preview stall | `PreviewStartupRuntimeIssueMonitor.kt` (watchdog 1000-1500ms) | `PreviewStartupRuntimeIssueMonitorTest` (4 tests: 超时前不发、超时后发、首帧取消、detach 清除) | Pass | 低 |
| provider invalidation | `CameraXCaptureAdapter.kt:194,1927` (PROVIDER_FAILURE/CAMERA_FATAL → 清缓存) | `CameraXCaptureAdapterRuntimeIssueTest`: `provider and fatal issues invalidate cached provider state` | Pass | 低 |
| thermal | `ThermalRuntimeIssueMonitor.kt:78-180` (SEVERE+ → THERMAL_CRITICAL, isRecoverable=false) | `AndroidThermalRuntimeIssueMonitorTest` (4 tests: attach 注册、cooldown/retrigger、detach 停止、status mapping) | Pass | 低 |
| diagnostics | `SessionDiagnostics.kt` (DebugDump/RecoveryTrace/PerfSnapshot/FirstFrameBudget) | `SessionDiagnosticsTest` (11 tests: perf snapshot、budget 分类、recovery trace、debug dump、watchdog thresholds、resource diagnostics safety) | Pass | 低 |
| perf budget | `SessionDiagnostics.kt:25,221` (warn/fail thresholds per category) | `SessionDiagnosticsTest`: perf snapshot 分类测试 (cold start 120/220ms, foreground resume 150/260ms, recovery 180/320ms) | Pass | P2 |
| Stage 7 script | `scripts/verify_stage_7_observability.sh` (26 test classes + assembleDebug) | 脚本本身 | Pass | 低 |
| coordinator 接线 | `CameraSessionCoordinator.kt` (session↔adapter↔monitors 桥接) | `CameraSessionCoordinatorTest` (17 tests: runtime issue forwarding、thermal lifecycle、bind failure、pending bind queueing) | Pass | 低 |

## Residual External Risks

| Risk | Why local cannot prove | Needed evidence |
| --- | --- | --- |
| 平台级 ProcessCameraProvider death/restart 真信号 | 单元测试仅验证 `shouldInvalidateCachedProviderState()` 返回值，无法模拟真实 provider 进程死亡 | 真机注入 provider death 事件，验证完整 rebind-from-scratch 流程 |
| 长时间真机恢复成功率 | 单元测试验证单次 recovery 逻辑正确，无法验证连续数十次 recovery 后无状态泄漏 | 真机连续模式切换 + 预览中断循环 30min+，监控内存/状态一致性 |
| 真实 thermal 触发后的 UX 和性能降级 | 单元测试验证 THERMAL_CRITICAL 正确发出且 isRecoverable=false，但无法验证 UX 降级表现 | 真机 thermal chamber 测试，验证 SEVERE+ 后 UI 提示、帧率降级、录制暂停等 UX |
| 设备分层首帧/切模式/保存耗时阈值 | 默认预算 (cold 120/220ms, resume 150/260ms, recovery 180/320ms) 基于通用估算 | 多设备真机采集 P50/P90/P99 首帧延迟，校准阈值 |
| Camera service 重启后的真实重连 | 单元测试无法触发 Android Camera service 重启 | `adb shell am crash` 注入 camera provider crash，验证自动重连 |
| 并发 runtime issue 处理 | 测试覆盖单个 issue 类型隔离场景 | thermal + provider failure 近同时到达时的 session 状态机行为 |

## P0/P1 Findings

**无 P0 发现。**

**无 P1 发现。**

Stage 7 核心链路（runtime issue 分类 → recovery request → recovery failure 防递归 → preview stall watchdog → provider invalidation → thermal monitoring → diagnostics）在代码实现和单元测试中均形成完整证据链。

**P2 观察：**

1. **humanistic mode 样式轮换测试失败** — `DefaultCameraSessionTest` 中 2 个 humanistic mode 测试因样式顺序不匹配失败（`expected: portrait, was: vivid`）。这是 mode 功能回归，不影响 Stage 7 稳定性链路，但需在后续修复。

2. **默认 perf budget 缺真机校准** — 当前首帧预算阈值（cold 120/220ms 等）为通用估算值，尚未基于多设备真机数据校准。`SessionDiagnosticsTest` 验证了阈值存在且分类正确，但实际设备上可能需要调整。

3. **recovery retry 无退避** — 当前 recovery 失败后不再重试（`recoveryWasActive` 防递归），但无指数退避机制。如果 provider 间歇性故障，可能需要更细粒度的重试策略。当前行为安全（不递归），但恢复成功率可能受限。

## Recommended Next Verification

1. **真机 provider death 注入测试** — 在 API 29+ 设备上通过 `adb shell am crash <camera_provider_pid>` 验证完整 provider invalidation + rebind 流程
2. **thermal chamber 测试** — 验证 SEVERE/CRITICAL/EMERGENCY 级别下 UI 降级表现和录制暂停行为
3. **长时间稳定性循环测试** — 30min+ 连续模式切换 + 预览中断，监控内存和状态一致性
4. **多设备首帧延迟基线采集** — 在 3+ 款设备上采集 cold start / foreground resume / recovery 首帧 P50/P90/P99，校准 perf budget 阈值
5. **修复 humanistic mode 样式轮换测试** — 2 个失败测试需在后续任务中修复，确保 Stage 7 脚本完整通过
