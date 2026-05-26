# vivo X300 真机反馈方案索引

日期：2026-05-24

目标：把用户在 vivo X300 真机实测中发现的 10 个问题聚合成可交给非多模态 agent 的闭环方案。本文只负责分派和边界，不要求接手 agent 读取截图或录屏。

## 总体分组

| 原编号 | 问题摘要 | 分组 | 处理策略 | 交接文档 |
| --- | --- | --- | --- | --- |
| 1 | 二级面板垂直高度过小，下边框应到整机从下缘算 2/5 高度 | A. UI 面板和预览遮罩 | 可落地 | `docs/plans/2026-05-24-vivo-x300-ui-panel-cockpit-polish.md` |
| 2 | 快捷中画幅缺少一致半透明背景，比例选项难选 | A. UI 面板和预览遮罩 | 可落地 | `docs/plans/2026-05-24-vivo-x300-ui-panel-cockpit-polish.md` |
| 3 | 底部栏目不应黑底；底栏透明，预览窗以外区域半透明提示成片区域 | A. UI 面板和预览遮罩 | 可落地 | `docs/plans/2026-05-24-vivo-x300-ui-panel-cockpit-polish.md` |
| 4 | 底部栏目有 `opencamera` 字样，应删去 | A. UI 面板和预览遮罩 | 可落地，需确认来源 | `docs/plans/2026-05-24-vivo-x300-ui-panel-cockpit-polish.md` |
| 5 | 开发选项中“核心”和“关键”重复 | B. Dev 信息架构 | 可落地 | `docs/plans/2026-05-24-vivo-x300-dev-console-ia.md` |
| 6 | 快捷中实况能力未实现 | D. 暂缓能力项 | 暂不出实现方案，先列能力边界 | 本文“暂缓项” |
| 7 | 快捷中画质无效无用，交互无感 | D. 暂缓能力项 | 暂不出完整能力方案；可选择先隐藏/降级 | 本文“暂缓项” |
| 8 | 快捷面板没有点击区域外自动收缩返回 | A. UI 面板和预览遮罩 | 可落地 | `docs/plans/2026-05-24-vivo-x300-ui-panel-cockpit-polish.md` |
| 9 | 色彩实验室角落点选后预览/成片不明显 | C. Color Lab 强度 | 可落地 | `docs/plans/2026-05-24-vivo-x300-color-lab-strength.md` |
| 10 | 设置项目中“拍照”和“视频”选项无效 | D. 暂缓能力项 | 需先判定是 tab 切换失效还是 tab 内选项无效 | 本文“暂缓项” |

## 分派建议

建议并行分给 3 个 agent：

1. UI Agent：执行 `2026-05-24-vivo-x300-ui-panel-cockpit-polish.md`。这是最高优先级，因为它覆盖 1/2/3/4/8，且大多是布局、样式、点击外收起。
2. Dev IA Agent：执行 `2026-05-24-vivo-x300-dev-console-ia.md`。这是低风险文案和事件分桶调整。
3. Color Agent：执行 `2026-05-24-vivo-x300-color-lab-strength.md`。这是核心 settings/effect 强度调整，需要纯 Kotlin 测试约束。

不要把 D 组交给普通实现 agent 直接写功能。D 组会碰到“功能真实有效还是显式降级/隐藏”的产品判断，尤其是实况和画质。

## 暂缓项：不直接出代码落地方案

## 二次审查补充

下游 agent 执行前应注意：

- UI 方案里的 widget 样式实际主要在 `app/src/main/res/values/themes.xml`，不是 `styles.xml`。`styles.xml` 当前主要是 text appearances。
- `Widget.OpenCamera.QuickBubbleButton` 当前 `backgroundTint` 是 `@color/oc_surface_scrim`，而该色为透明；因此“复用默认快捷按钮背景”不能解决画幅裸露问题。实现时需要改 style tint 或新增明确的 quick row/chip drawable。
- 如果把 `cameraPreview` / `previewOverlay` 改成铺到 parent bottom，必须同步验证 tap-to-focus 的有效区域、frame ratio overlay、bottom inset。不要只看黑底消失。
- `设置` 中 `拍照/视频` 无效不应完全搁置。建议先追加一个“轻量诊断任务”：为 settings tab 点击和 section visibility 增补 unit test / render trace，确认是 tab 切换失效还是 tab 内控制项无效。

### 6. 快捷中实况能力未实现

当前文本证据显示 Live Photo 已有降级链路，但 CameraX 侧仍可能是 metadata-only / still-only fallback。相关线索：

- `codex/Feature-Availability-Audit.md` 将 Live Photo 标为 `DEGRADED (STILL_ONLY_FALLBACK)` 风险项。
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` 中存在 `device:live-photo=still-only-fallback` 等 notes。
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` 当前将 live 默认项渲染为 `DEGRADED` 但仍给 `nextAction`。

建议先让产品/主控确认：vivo X300 验收期是否要求真实动态实况，还是允许在 2.0 阶段以“静态照片 + 明确降级提示/隐藏快捷入口”处理。若要求真实动态实况，需要单独开能力方案，不应作为本轮 UI bug 修复。

### 7. 快捷中画质无效无用

当前代码线索：

- 快捷行 `qualityRow` 在 `SessionCockpitRenderModel.kt` 中固定显示 `strings.buttonStillFast`。
- 点击走 `SessionIntent.StillCaptureQualityToggled`，session/device 层确实有 `StillCaptureQualityPreference.LATENCY/QUALITY`。
- 真机“无感”可能来自 CameraX/JPEG 质量/分辨率没有用户可观察差异，也可能只是 UI 没有即时状态反馈。

建议先做产品判断：

- 若无法保证 vivo X300 上可观察差异，快捷入口应隐藏或改为“速度/质量偏好”并显示当前值变化，不承诺画质提升。
- 若要求真实画质提升，需要另开 device/media 方案，验证 CameraX 输出质量、分辨率选择、EXIF/文件大小/保存耗时差异。

### 10. 设置项目中“拍照”和“视频”选项无效

这里存在歧义：

- 如果用户指设置页顶部 `拍照` / `视频` tab 点了不切换：这是 UI bug，应补入 UI Agent 任务。
- 如果用户指 tab 内的照片/视频设置项点了无实际效果：需要逐项确认哪些设置无效，并按能力真实性分类处理。

当前代码中 `SettingsPanelRenderer.renderTabs()` 会按 `SettingsTab.PHOTO/VIDEO` 切换 section 可见性，`MainActivityActionBinder` 也绑定了 tab 点击。因此需要先用真机或测试确认失败点，不建议在信息不足时直接改。

补充建议：先由 UI Agent 增加或核对 `SettingsTab` 相关测试，至少覆盖：

- `SelectSettingsTab(SettingsTab.PHOTO)` 后 `selectedSettingsTab == PHOTO`。
- `SettingsPanelRenderer.renderTabs(PHOTO)` 后 `photoSection.isVisible == true` 且 `commonSection/videoSection` 隐藏。
- `SettingsPanelRenderer.renderTabs(VIDEO)` 同理。

若这些测试通过，问题大概率在 tab 内某些 setting 的实际能力或反馈，而不是 tab 切换本身。

## 共同验收命令

每个落地 agent 至少运行相关单测和构建：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

若修改 `core/settings` 的 Color Lab 映射，额外运行：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.StyleColorPipelineTest
```

最终如要完整 Stage 7 回归：

```bash
rtk ./scripts/verify_stage_7_observability.sh
```
