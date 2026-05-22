# 并行任务 E：稳定性、恢复与可观测基线复验（非多模态）

## 1. 目标

确认 Stage 7 的治理能力可支撑 2.0 准入，不只是“测试偶然通过”。

## 2. 基线命令

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

必要时补充最小化命令：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

## 3. 审查点

1. runtime issue 分类是否清晰、可追踪
2. preview stall watchdog 是否覆盖关键生命周期
3. thermal / bind / recovery forwarding 是否闭环
4. diagnostics 是否支持问题定位而非仅日志堆叠
5. 失败是否有可复现路径或最小证据包

## 4. 产出

- `Stability-Observability-Audit.md`
- 失败用例复盘（若有）
- 风险分层建议（可本地解决 vs 需外部条件）

