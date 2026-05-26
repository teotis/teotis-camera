# 并行任务 E：稳定性、恢复与可观测基线复验

> 非多模态任务。确认 Stage 7 治理能力可支撑 2.0 准入，不只是“测试偶然通过”。不做真机视觉判断，不新增稳定性 feature。

## 1. 目标

复验以下 Stage 7 能力是否形成可回放证据：

- diagnostics owner：`SessionDebugDump / RecoveryTraceSnapshot / PerfSnapshot`
- runtime issue owner：provider/bind/camera fatal/recoverable/thermal/preview stall
- recovery guardrail：普通 preview error、host recovery、recovery failure 不递归
- perf budget：cold start / foreground resume / recovery / reconfigure 首帧预算
- provider invalidation：fatal/provider failure 后不复用陈旧 provider
- preview startup stall watchdog：bind 发起后无首帧可主动收敛

## 2. 输入

必读：

- `codex/documentation.md`
- `scripts/verify_stage_7_observability.sh`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/ThermalRuntimeIssueMonitor.kt`
- `app/src/main/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitor.kt`

相关测试：

- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt`
- `app/src/test/java/com/opencamera/app/camera/AndroidThermalRuntimeIssueMonitorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PreviewStartupRuntimeIssueMonitorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRuntimeIssueTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt`

## 3. 基线命令

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

必要时补充最小化命令：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitorTest --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
```

如果遇到 `~/.codex-build/OpenCamera` Kotlin/build-directory 瞬时错误，按项目规则先串行重跑最小相关命令，再判断是否产品回归。

## 4. 审查点

| 项 | Pass 标准 | Risk/Fail 信号 |
| --- | --- | --- |
| runtime issue 分类 | kind、recoverable、display/recovery reason 清晰 | 错误只剩字符串或无法归类 |
| recovery request | 权限/host/preview error 可以进入 recovery bind | 预览错误只进 ERROR 死路 |
| recovery failure | recovery 中再失败记录 failed 且不递归 | recoverable issue 无限重排 |
| preview stall | bind started 后无首帧会发 `PREVIEW_STALL` | 只能等待底层抛错 |
| provider invalidation | fatal/provider failure 清缓存 | 复用旧 provider 导致下次恢复不可控 |
| thermal | 旧系统 no-op，新系统 severe+ forward | 无服务被误判失败，或 critical 静默 |
| diagnostics | debug dump/recovery trace/perf snapshot 能解释现场 | 只有散乱日志 |
| Stage 7 script | 脚本覆盖当前 owner | 新 owner 未入脚本 |

## 5. 输出格式

生成 `Stability-Observability-Audit.md`：

```markdown
# Stability Observability Audit

## Summary

- Stage 7 script: Pass / Fail
- Result: Pass / Risk / Fail

## Verification Results

| Command | Result | Notes |
| --- | --- | --- |

## Owner Coverage

| Owner | Code | Tests | Script | Risk |
| --- | --- | --- | --- | --- |

## Residual External Risks

| Risk | Why local cannot prove | Needed evidence |
| --- | --- | --- |

## P0/P1 Findings

## Recommended Next Verification
```

## 6. 外部依赖必须单列

以下不能在本地 unit/assemble 中直接判 `Pass`：

- 平台级 `ProcessCameraProvider` death/restart 真信号。
- 长时间真机恢复成功率。
- 真实 thermal 触发后的 UX 和性能降级。
- 设备分层首帧/切模式/保存耗时阈值。
- Camera service 重启后的真实重连。

报告里必须标成 `需真机/平台验证`，不能用已有 unit test 伪装通过。

## 7. P0/P1 判定规则

- P0：Stage 7 script 无法通过，且失败在核心 session/device/media/app 稳定链路。
- P0：recovery 形成递归或导致不可恢复死路。
- P1：核心 runtime issue 有 owner 但 diagnostics 不足以定位。
- P1：provider/stall/thermal 只能在日志中出现，无法转成 session 可处理事件。
- P2：默认预算或分类存在但缺真机阈值。

## 8. 边界

- 不新增 provider death 平台适配。
- 不调性能阈值写死设备值。
- 不改 production code；发现问题交给后续修复任务。
