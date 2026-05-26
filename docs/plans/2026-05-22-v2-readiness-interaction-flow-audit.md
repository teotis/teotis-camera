# 并行任务 B：用户交互流畅性审查

> 非多模态任务。用代码、测试、文本时序和日志审查“可达、可执行、可恢复”。不做 UI 美学评价，不改生产代码。

## 1. 目标

验证关键交互路径是否符合 2.0 标准：

- 用户点击的 exact control 触发 exact action。
- 面板开合、返回、外点关闭一致。
- 模式、变焦、快门、录像、权限恢复都有可理解的状态反馈。
- 交互不产生第二套 session runtime owner。

## 2. 核心路径

| 路径 | 需要证明 | 典型风险 |
| --- | --- | --- |
| 冷启动到首帧 | 权限、host attach、preview bind、first frame 或 stall 可追踪 | 首帧卡死无反馈 |
| 模式切换 | tap exact mode -> exact selected mode -> render model 更新 | 点击相邻项、循环选择、旧 mode state 残留 |
| 变焦选择 | tap `0.7x/1.0x/2.0x/5.0x` -> exact ratio | 点击某项却切到下一项 |
| 拍照 | shutter enabled 条件、saving feedback、thumbnail feedback | 快门无响应或反馈晚 |
| 录像 | start/recording/stopping/saved 状态连续 | 停止无反馈或状态残留 |
| 面板 | open one route、outside/back close、nested route 清楚 | 多面板叠加、遮挡快门 |
| 权限拒绝后恢复 | permission intent -> recovery bind -> preview usable | 恢复后 UI 仍禁用或无解释 |

## 3. 输入

必读：

- `codex/v2_ui/03_interaction_grammar.md`
- `docs/plans/2026-05-21-interaction-routing-and-hit-targets.md`
- `docs/plans/2026-05-22-mode-track-legibility-and-hit-targets.md`
- `docs/plans/2026-05-22-landscape-preview-alignment-and-rotation.md`
- `codex/documentation.md`

重点代码：

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/ModeTrackTouchPolicy.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/gesture/GestureRouter.kt`
- `app/src/main/java/com/opencamera/app/gesture/GestureGuard.kt`
- `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`

相关测试：

- `app/src/test/java/com/opencamera/app/ModeTrackTouchPolicyTest.kt`
- `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`
- `app/src/test/java/com/opencamera/app/gesture/GestureGuardTest.kt`
- `app/src/test/java/com/opencamera/app/CockpitPanelRouteTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## 4. 审查方法

为每条核心路径写一段文本时序：

```text
User action
-> App handler / route
-> Session intent or app-shell state
-> State/effect change
-> User-visible feedback
-> Recovery/close/cancel behavior
```

每条路径标注：

- `Pass`：有测试或代码证据证明链路闭合。
- `Risk`：链路存在但证据不足或边界模糊。
- `Fail`：明确断点或错误语义。

## 5. 输出格式

生成 `Interaction-Flow-Audit.md`：

```markdown
# Interaction Flow Audit

## Summary

## Flow Table

| Flow | Result | Evidence | Breakpoint | Risk | Recommendation |
| --- | --- | --- | --- | --- | --- |

## Sequence Notes

### Cold Start To First Frame
### Mode Switch
### Zoom Selection
### Photo Capture
### Video Record
### Panel Open/Close
### Permission Recovery

## Regression Tests To Add

## Handoff To Multimodal主控
```

每条路径至少给 1 个可执行回归建议。

## 6. 验证命令

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ModeTrackTouchPolicyTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.gesture.GestureGuardTest --tests com.opencamera.app.CockpitPanelRouteTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

如测试失败，报告中必须包含失败测试名、断言摘要、是否可能是历史旧期望。

## 7. 边界

- 不做视觉美学结论；横屏真实手感交给多模态主控。
- 不修改 gesture/session 代码。
- 不建议让 UI 直接驱动 camera runtime。
- 不把“无测试覆盖”直接判 Fail；应标 Risk 并说明需要的测试。
