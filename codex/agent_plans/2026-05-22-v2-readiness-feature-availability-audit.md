# 并行任务 C：功能可触达与有效可用审查

> 非多模态任务。确认“用户能触达的功能”不是假入口：可执行、可降级、可验证。不要做保存图视觉质量判断。

## 1. 目标

建立功能矩阵，判断每个可见功能是否满足：

```text
入口可见 -> 点击/调整可执行 -> 状态可感知 -> 请求或结果受影响 -> unsupported/degraded 可解释
```

2.0 不能接受“入口存在但只改 UI 文案”的假功能。

## 2. 功能分组

| 组 | 功能 |
| --- | --- |
| 基础 | 拍照、录像、缩略图回看、前后镜头、变焦 |
| 模式 | Photo、Video、Night/Scenery、Portrait、Document、Humanistic、Pro |
| 快捷 | 网格、画幅、闪光、定时、Live、质量/分辨率 |
| 风格 | 预设风格、滤镜、强度 |
| 色彩实验室 | 二维调色板、重置、强度、预览/成片接线状态 |
| 专业 | ISO、快门速度、EV、AF、AWB/WB、RAW |
| 媒体后处理 | 水印、边框、自拍镜像、文档裁切、人像渲染 |
| 诊断 | Dev log、diagnostics、runtime issue 展示 |

## 3. 输入

必读：

- `codex/plan.md`
- `codex/documentation.md`
- `codex/v2_ui/04_camera_function_convenience.md`
- `codex/capability_kernel_v2/00_capability_kernel_v2_index.md`
- `codex/agent_plans/2026-05-22-style-color-lab-2-implementation.md`
- `codex/agent_plans/2026-05-22-media-output-filter-thumbnail-stability.md`

重点代码：

- `core/mode/src/main/kotlin/com/opencamera/core/mode/*`
- `feature/mode-*/src/main/kotlin/**/*.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/*`
- `core/device/src/main/kotlin/com/opencamera/core/device/*`
- `core/media/src/main/kotlin/com/opencamera/core/media/*`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/camera/*PostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

相关测试：

- `core/mode/src/test/kotlin/com/opencamera/core/mode/*Test.kt`
- `core/device/src/test/kotlin/com/opencamera/core/device/*Test.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/*Test.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/*Test.kt`
- `app/src/test/java/com/opencamera/app/camera/*Test.kt`

## 4. 检查矩阵

对每个功能填表：

| 功能 | 入口 | 状态 owner | Session/Mode owner | Device/Media owner | 测试证据 | supported/degraded/unsupported | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |

判定规则：

- `Pass`：入口、状态、执行、结果/降级、测试至少四项成立。
- `Risk`：入口和状态存在，但执行或结果证据不足。
- `Fail`：入口可见但没有真实 owner，或 unsupported 被伪装成支持。

## 5. 高风险专项

优先检查：

1. `色彩实验室` 是否只是面板，还是已经影响 `StyleColorPipeline` / `ColorLabSpec` / `FilterRenderSpec` / 成片 metadata。
2. `画幅` 是否只改 UI，还是进入 preview geometry 与 postprocessor。
3. `Live` 是否有真实 temporal media 语义，还是只写 flag。
4. `RAW/manual` 是否有 unsupported/degraded，而不是假装执行。
5. `水印/滤镜` 是否进入媒体后处理链路。
6. `变焦` 是否走 exact ratio，且 unsupported 设备呈现 `N/A` 或原因。

## 6. 输出格式

生成 `Feature-Availability-Audit.md`：

```markdown
# Feature Availability Audit

## Summary

| Result | Count |
| --- | --- |
| Pass | |
| Risk | |
| Fail | |

## Feature Matrix

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |

## P0/P1 Feature Defects

## Unsupported/Degraded Semantics Gaps

## Recommended Regression Tests

## Handoff To IO / Multimodal
```

## 7. 验证命令

根据审查范围组合运行：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

如全量模块测试成本过高，优先跑与 Fail/Risk 相关的最小测试，并在报告中说明未覆盖范围。

## 8. 边界

- 不做成片画质判断；只判断功能链路和可验证语义。
- 不新增模式或功能。
- 不把厂商私有能力当成当前 2.0 必须支持项。
- 不建议跳过 `Mode Plugin -> Session Kernel -> Device Adapter -> Media Pipeline` 主链路。
