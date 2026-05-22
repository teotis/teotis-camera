# 并行任务 A：UI 设计逻辑自洽静态审查

> 非多模态任务。只做代码、XML、资源、render model、文档一致性审查，不做截图审美结论，不改生产代码。

## 1. 目标

审查 UI 设计逻辑是否在代码层可映射、可维护、无明显自相矛盾，并判断是否满足 2.0 cockpit 设计口径：

- 顶部栏：左侧应用名，中部靠右 `色彩实验室`，最右 `设置`。
- 侧边栏：`风格 / 快捷 / Dev`，不放 `设置` 或 `色彩实验室`。
- 底部 cockpit：缩略图、快门、镜头/变焦控制稳定。
- 面板系统：单一 active route，外点/返回关闭，不能多个面板并发竞争。
- 文案：默认中文，短标签，不能靠大段解释撑 UI。

## 2. 输入

必读：

- `codex/v2_ui/00_v2_ui_index.md`
- `codex/v2_ui/01_camera_cockpit_wireframes.md`
- `codex/v2_ui/02_visual_system.md`
- `codex/v2_ui/03_interaction_grammar.md`
- `codex/v2_ui/05_panel_system_and_labs.md`
- `codex/agent_plans/2026-05-22-rail-and-color-lab-entry-consolidation.md`
- `codex/agent_plans/2026-05-22-quick-and-secondary-panel-bounds.md`
- `codex/agent_plans/2026-05-22-mode-track-legibility-and-hit-targets.md`

重点代码：

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionStateRender.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/drawable/bg_*.xml`

相关测试：

- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CockpitPanelRouteTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionStateRenderTest.kt`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`

## 3. 审查清单

| 项 | Pass 标准 | Risk/Fail 信号 |
| --- | --- | --- |
| 信息架构 | 顶部、侧栏、底部 cockpit 职责清楚 | 色彩实验室/设置/风格/快捷出现在多个层级 |
| 面板路由 | 单一 `CockpitPanelRoute` 或等价单 owner | 多个 boolean 同时控制面板显隐 |
| 状态语义 | 同一状态只有一套名称和显示逻辑 | `LensLab`/`ColorLab`/`ToneLab` 等旧名混用 |
| UI 职责 | UI render + dispatch，不直接驱动 camera runtime | `MainActivity` 直接调用 CameraX/DeviceAdapter 运行时逻辑 |
| 文案/i18n | 用户可见文案来自 string/i18n 或稳定 resolver | hard-coded 英文、长中文、省略后无法理解 |
| 窄屏适配 | 标签短，布局尺寸来自 dimen/约束 | 多行按钮、竖排文字、依靠超长 label |
| disabled/degraded | 控件禁用原因可展示 | disabled 只有灰掉，没有原因 |
| Dev/诊断 | Dev 是明确低频入口 | Dev 信息污染普通拍摄路径 |

## 4. 输出格式

生成 `UI-Static-Audit.md`，包含：

```markdown
# UI Static Audit

## Summary

- Result: Pass / Risk / Fail
- P0:
- P1:
- P2/P3:

## Findings

| Priority | Area | File | Evidence | Expected | Actual | Recommendation |
| --- | --- | --- | --- | --- | --- | --- |

## IA Map

| Surface | Expected | Code evidence | Status |
| --- | --- | --- | --- |

## Test Gaps

## Handoff To Multimodal主控
```

至少输出 10 个高价值发现；若不足 10 个，说明“未发现更多”的证据范围。

## 5. 验证命令

所有命令必须用 `rtk`：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionStateRenderTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

若 `CameraCockpitRenderModelTest` 存在已知旧期望失败，必须把失败点拆成：

- 与本轮 2.0 IA 相关。
- 与历史右侧栏旧期望相关。
- 与当前审查无关但需后续清理。

## 6. 边界

- 不评价截图是否“好看”，只评价代码是否符合文档和结构逻辑。
- 不改 `MainActivity.kt`、XML 或资源；发现问题只记录。
- 不把参考 PNG 当成文字规格。
- 不新增 UI 架构层；不得建议迁移 Compose。
