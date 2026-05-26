# UI Static Audit

> 审查日期: 2026-05-22
> 审查范围: 代码、XML、资源、render model、文档一致性
> 审查依据: v2_ui 设计文档 + 3 份 agent plan
> 不含: 截图审美评价、生产代码修改

## Summary

- **Result: Risk** — 无阻断性 Fail，但存在 3 个 P0 级 i18n/命名问题需在 2.0 发布前修复
- **P0:** 3 项（i18n 硬编码、命名不一致）
- **P1:** 4 项（死代码、职责越界、命名歧义）
- **P2/P3:** 5 项（测试缺口、窄屏适配、Dev 入口可见性）

## Findings

| # | Priority | Area | File | Evidence | Expected | Actual | Recommendation |
|---|----------|------|------|----------|----------|--------|----------------|
| 1 | P0 | 文案/i18n | `MainActivity.kt` L1651,1659,1665; `SessionUiRenderModel.kt` L517-519,524,1418 | 6+ 处硬编码中文: `"拍摄进行中，无法更改设置"`, `"设置尚未加载"`, `"当前模式不支持此操作"`, `"当前模式不支持画幅"`, `"等待当前拍摄完成后才能切换画幅"`, `"倒计时结束后才能切换画幅"`, `"画幅"`, `"人像产品调节位于设置下一级…"` | 所有用户可见文案走 `AppTextResolver` 或 `R.string` | 中文直接写死在 Kotlin 源码中，无法本地化 | 迁移至 `AppTextResolver` 对应方法，添加 `R.string` 资源 |
| 2 | P0 | 文案/i18n | `activity_main.xml` L369 | `android:text="Back"` 硬编码英文 | 使用 `@string/` 资源，中文环境显示中文 | 英文硬编码在中文 UI 中，i18n 缺口 | 添加 `button_back` 资源: zh="返回", en="Back" |
| 3 | P0 | 命名一致性 | `MainActivity.kt` | `buttonColorLabEntry` 点击打开 `CockpitPanelRoute.LensLab`，而非 ColorLab 路由 | 按钮 ID 与路由语义一致 | "ColorLab" 按钮打开 "LensLab" 面板 | 统一命名：按钮改 `buttonStyleColorLabEntry` 或路由改 `ColorLab` |
| 4 | P1 | 状态语义 | `CockpitPanelRoute.kt` | 路由名 `FilterLab`/`LensLab` vs 用户可见标签 `风格`/`色彩实验室` | 内部路由名与用户概念对齐 | 内部名 `FilterLab` 对应 `风格`，`LensLab` 对应 `色彩实验室`，映射隐式 | 按 agent plan 建议重命名为 `StyleLab`/`ColorLab`，或在代码中显式记录映射 |
| 5 | P1 | 信息架构 | `strings.xml`, `activity_main.xml` | `button_lens_rail`="设置" 与 `button_settings_entry`="设置" 完全相同；`buttonLensLabEntry` 在 XML 中 `visibility="gone"` | 侧栏无"设置"入口，仅顶部栏有 | 侧栏遗留 `buttonLensLabEntry` 虽已隐藏但资源字符串仍在 | 清理 `button_lens_rail` 资源和 `buttonLensLabEntry` XML 定义 |
| 6 | P1 | 面板路由 | `CameraCockpitRenderModel.kt` | `cameraCockpitRenderModel()` 函数及 `TopStatusRenderModel`, `RightRailRenderModel`, `BottomCockpitRenderModel`, `ZoomStripRenderModel`, `CameraCockpitRenderModel` 数据类已定义但 `MainActivity` 内联构建等效模型 | 无未使用的 render model 代码 | 死代码并行路径，测试覆盖但运行时未使用 | 删除或整合到 `MainActivity` 统一使用 |
| 7 | P1 | UI 职责 | `MainActivity.kt` L424, L713-716 | `cameraCoordinator.attachPreviewHost(this, previewView)` 和 `sessionSettingsManager.apply(PersistedSettingsAction.UpdateColorLabSpec(...))` 直接从 UI 调用 | UI 仅 dispatch intents，不直接调用 Device Adapter/Settings Manager | 直接调用 camera coordinator 和 settings manager | 通过 `SessionIntent` 间接调用，解耦 UI 与运行时 |
| 8 | P2 | 文案/i18n | `SessionUiRenderModel.kt` L675-678, L387, L1432 | `"Starting..."`, `"Recording"`, `"Saving..."` 英文硬编码；`"Color: %.2f, Tone: %.2f"` 格式串未走资源；`"${PortraitProfile.entries.size} product profiles"` 英文 | 所有用户可见文案走 `AppTextResolver` | 英文字符串在 render model 中硬编码 | 迁移至 `AppTextResolver`，添加对应 `R.string` |
| 9 | P2 | 窄屏适配 | `activity_main.xml` L204-246 | quick bubble 按钮固定 `96dp x 44dp`，画幅选项 `48dp x 36dp` | 中文 label+value 不截断 | 96dp 宽度在中文 label+value 组合下可能截断（如"画质\n高清"） | 使用 `wrap_content` 或最小宽度约束，参考 agent plan 的两行布局方案 |
| 10 | P2 | disabled/degraded | `MainActivity.kt` `captureConfigDisabledReason()` | disabled 原因仅通过 `Toast` 展示 | 按 interaction grammar，disabled 原因应通过 status strip 或 inline hint 反馈 | Toast 是瞬态的，用户可能错过 | 添加 status strip 文本反馈，Toast 作为补充 |
| 11 | P2 | 测试覆盖 | `SessionUiRenderModel.kt` L512, L663 | `primaryStatusRenderModel()` 和 `frameRatioControlRenderModel()` 是导出函数但无测试覆盖 | 所有 render model 函数有测试 | 两个函数完全无测试 | 补充测试用例 |
| 12 | P3 | Dev/诊断 | `activity_main.xml` | `buttonDevEntry` 始终可见，无 `BuildConfig.DEBUG` 守卫 | Dev 入口仅在 debug 构建可见 | release 构建也显示 Dev 按钮 | 添加 `if (BuildConfig.DEBUG)` 可见性控制 |

## IA Map

| Surface | Expected (2.0 Spec) | Code Evidence | Status |
|---------|---------------------|---------------|--------|
| 顶部栏左侧 | 应用名 `OpenCamera`，不带模式后缀 | `titleText` 使用 `@string/app_name`，无模式后缀拼接 | **Pass** |
| 顶部栏中部靠右 | `色彩实验室` | `buttonColorLabEntry` 文本 `@string/button_color_lab_entry`="色彩实验室" | **Pass** |
| 顶部栏最右 | `设置` | `buttonSettingsEntry` 文本 `@string/button_settings_entry`="设置" | **Pass** |
| 侧栏 | `风格` / `快捷` / `Dev`，不含设置和色彩实验室 | `buttonFilterEntry`="色调"(非"风格")，`buttonQuickLauncher`="快捷"，`buttonDevEntry`="开发"，`buttonLensLabEntry` 已 hidden | **Risk** — "色调" 应为 "风格"；遗留 hidden 按钮未清理 |
| 底部 cockpit | 缩略图、快门、镜头/变焦 | `previewThumbnail` 56dp + `buttonShutter` 72dp + `buttonLensFacing` | **Pass** |
| 模式轨道 | 拍照→文档→人文→人像→专业→视频 | 6 个可见模式按钮 + `人文` hidden，顺序正确 | **Pass**（人文隐藏是临时状态） |
| 缩放条 | 0.7x / 1x / 2x / 5x | 运行时填充，测试验证 4 chips | **Pass** |
| 面板路由 | 单一 `CockpitPanelRoute`，不能多面板并发 | `activePanelRoute: CockpitPanelRoute` sealed class，`renderPanelVisibility()` 单一入口 | **Pass** |
| 面板关闭 | 外点/返回关闭 | `panelDismissScrim` 点击 → `None`；`onBackPressed()` 处理子页面回退 | **Pass** |
| 文案语言 | 默认中文，无硬编码英文 | 6+ 处硬编码中文，1 处硬编码英文 "Back"，3+ 处硬编码英文在 render model | **Fail** |

## Test Gaps

| 未覆盖函数 | 文件 | 风险 |
|-----------|------|------|
| `primaryStatusRenderModel()` | `SessionUiRenderModel.kt:663` | 状态文本渲染无验证，可能出现不正确的录制/保存状态文案 |
| `frameRatioControlRenderModel()` | `SessionUiRenderModel.kt:512` | 画幅切换 disabled 原因无验证，硬编码中文字符串未被测试捕获 |
| `devLogRenderModel()` | `SessionUiRenderModel.kt` | 独立测试文件存在但未纳入本次验证范围 |
| 录录状态对设置页编辑的影响 | `SessionUiRenderModel.kt` | `RecordingStatus.RECORDING` 时 settings page editing 状态无测试 |
| 权限拒绝状态对 render model 的影响 | 多文件 | `PermissionState(cameraGranted=false)` 从未在 render model 测试中覆盖 |
| 空 catalog 状态 | `SessionUiRenderModel.kt` | 无 filter/watermark template 时的行为未测试 |

**编译 Warning（非阻断）：**
- `MainActivity.kt:1579` — `onBackPressed()` 已废弃，应迁移到 `OnBackPressedDispatcher`
- `SessionUiRenderModel.kt:901` — 未使用变量 `renderModel`
- `SessionUiRenderModel.kt:1519` — 未使用变量 `selectedTemplate`

## 已知测试失败分类

本轮测试 **全部通过**（BUILD SUCCESSFUL，0 failures）。无需分类历史失败。

若 `CameraCockpitRenderModelTest` 在未来出现旧期望失败，按以下分类：
- **与 2.0 IA 相关**：right rail entries 期望 3 个但代码改为 2 个、top status appName 期望变更
- **与历史右侧栏旧期望相关**：right rail label 期望 "Style"/"Quick"/"DEV" 但改为中文
- **与当前审查无关但需后续清理**：orientation 旋转映射、preview ratio chip 边界值

## Handoff To Multimodal 主控

本审查为纯代码/文档一致性审查，不涉及截图或视觉审美判断。

**Multimodal 主控需补充验证的项目：**
1. **窄屏截断** — quick bubble 96dp 按钮在 1080px 宽设备上中文 label+value 是否实际截断
2. **模式轨道触摸** — scroll vs tap 仲裁在真机上的表现（代码层面 OnTouchListener 20dp slop 已审查）
3. **disabled 视觉反馈** — alpha 0.42f 在真机预览上的可辨识度
4. **色彩实验室调色板** — FilterPaletteView 180dp 高度在小屏上的实际表现
5. **面板关闭手势** — scrim 点击区域是否覆盖面板外所有区域

**建议的 Multimodal 测试命令：**
```bash
# 安装 debug APK 到设备
rtk ./gradlew --no-daemon :app:installDebug
```
