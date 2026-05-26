# vivo X300 UI 面板与底部 cockpit 修复方案

日期：2026-05-24

覆盖用户问题：1、2、3、4、8。此方案只处理布局、视觉一致性、点击外收起和文字清理，不实现新相机能力。

## 目标

1. 二级面板（设置、风格/色彩实验室等）的下边界扩大到“从屏幕下缘向上 2/5”的位置，即面板应覆盖屏幕上方约 3/5 高度。
2. 快捷面板中的“画幅”区域和其他快捷项具备一致的半透明背景，比例按钮命中区域更大、选中/未选中状态清晰。
3. 底部栏目自身透明；不再出现黑色底。画幅预览时，由预览 overlay 对成片区域外做半透明遮罩。
4. 底部栏目不得出现 `opencamera` / `OpenCamera` 品牌字样。
5. 打开快捷面板后，点击面板外区域应自动收起并返回主预览。

## 现有代码入口

重点文件：

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/drawable/bg_bottom_panel.xml`
- `app/src/main/res/drawable/bg_panel_row.xml`
- `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`

相关现状：

- `activity_main.xml` 已有 `secondaryPanelBottomGuide`，当前注释称 `~2/5 from screen top`，实际 `app:layout_constraintGuide_percent="0.4"`。用户要求是“下边框应到整机的 2/5 高度（假设下边缘出发）”，换算为从顶部算 3/5，即 guide percent 应约为 `0.6`。
- `quickBubblePanel` 当前约束到 `modeTrackScroll`，不是统一的二级面板下边界。
- `panelDismissScrim` 已存在，`MainActivityActionBinder.bindPanelActions()` 已绑定 `DismissAll`，但 scrim 可能覆盖层级/点击范围和 quick panel 的 z-order 有冲突，需要验证 quick panel 打开时 scrim 在面板下、预览/底栏上。
- `bg_bottom_panel.xml` 当前 solid 指向 `@color/oc_surface_scrim`，而 `colors.xml` 中该色为透明。如果真机仍黑，黑色可能来自父背景、系统导航栏、预览被底栏约束切掉后露出黑底，或 bottom cockpit 内某个子控件/窗口 inset 区域。
- `Widget.OpenCamera.QuickBubbleButton` 在 `themes.xml` 中也使用 `@color/oc_surface_scrim` 作为 `backgroundTint`，这同样是透明的；不能依赖默认 style 给未选中画幅按钮提供背景。
- `PreviewOverlayView.drawPreviewFrame()` 已有 `frame.dimOutsideFrame` 时的外部遮罩，属于成片区域提示的正确 owner。

## 方案

### 1. 二级面板高度

把二级面板统一解释为顶部入口打开的 overlay sheet，包括：

- `settingsPanel`
- `filterPanel`，也承载色彩实验室
- `devConsolePanel` 如果当前使用相同 guide，也保持一致
- `quickBubblePanel` 可根据设计保留右侧窄面板，但下边界也应跟随二级面板高度，不应短到影响操作

具体改法：

1. 将 `secondaryPanelBottomGuide` 的 `layout_constraintGuide_percent` 从 `0.4` 改为 `0.6`，并更新注释为“from top 3/5, equivalent to 2/5 above bottom”。
2. 将 `settingsPanel` / `filterPanel` / `devConsolePanel` 的 bottom constraint 继续指向该 guide。
3. 将 `quickBubblePanel` 的 bottom constraint 从 `@id/modeTrackScroll` 改为 `@id/secondaryPanelBottomGuide`，使它获得足够高度且和二级面板体系一致。
4. 保留 `layout_marginBottom` 但检查是否导致面板明显短于 guide。若需要，二级面板底部 margin 不超过 `12dp`。

验收：

- vivo X300 竖屏中，打开设置/色彩实验室/快捷后，下边界在屏幕高度约 60% 处。
- 面板不会压到底部快门区，也不会短到只能显示两三行。

### 2. 快捷画幅区域视觉与命中

问题：`frameRatioRow` 当前 `android:background="@color/oc_surface_scrim"`，该色透明，因此看起来没有类似其他子项的半透明背景；未选中比例按钮还会被 `CockpitSurfaceRenderer.renderQuickBubble()` 设置 `background = null`。

具体改法：

1. 给 `frameRatioRow` 使用 `@drawable/bg_panel_row` 或新建轻量背景 `bg_quick_panel_row.xml`，颜色采用 `@color/oc_surface_panel_alt` 或更轻的半透明黑。
2. 不要在未选中比例按钮上执行 `button.background = null`。改为始终设置稳定背景：
   - 选中：`R.drawable.bg_quick_chip_selected`
   - 未选中：新增 `bg_quick_chip.xml` 或把 `Widget.OpenCamera.QuickBubbleButton` 的 `backgroundTint` 改为非透明半透明色。当前默认背景是透明，不能直接复用。
3. 增大比例按钮命中：
   - `buttonFrameRatio43/169/11` 高度从 `32dp` 提到至少 `40dp`
   - 宽度从 `56dp` 提到 `64dp` 或让三项 `0dp + weight=1` 铺满行
   - `frameRatioOptionRow` 宽度建议 `match_parent`
4. 在 `FrameRatioOptionRenderModel.isEnabled == false` 时，三个比例按钮要降低 alpha，并保留背景和禁用态，不要变成透明裸文本。

验收：

- 画幅区域肉眼看起来是一个和“网格/画质/实况/定时”同体系的半透明块。
- 4:3 / 16:9 / 1:1 任意一个按钮在 vivo X300 上单手可稳定点中。
- 选中项背景明显，未选中项仍有可点击控件边界。

### 3. 底栏透明与成片区域提示

原则：底部栏本身保持透明，不承担“预览外遮罩”。成片区域外遮罩只由 `PreviewOverlayView` 根据 frame ratio 绘制。

具体检查和改法：

1. 保持或明确 `bg_bottom_panel.xml` 为完全透明；不要改成黑色或 `oc_surface_panel`。
2. 检查 `activity_main.xml` 中 `cameraPreview` 和 `previewOverlay` 当前被约束到底部 `modeTrackScroll` 上方。这会让预览区域不覆盖底部 cockpit，可能露出窗口/父背景黑色。建议让 `cameraPreview` 和 `previewOverlay` 约束到 parent bottom，全屏铺底；底部控件浮在 preview 上方。
3. 若改为全屏 preview，确保 `PreviewOverlayView.previewContentGeometry()` 仍使用全视图计算，和保存裁切一致。不要为了避开底栏缩小 active frame。
4. `PreviewOverlayView.drawPreviewFrame()` 已有 outside-frame 半透明遮罩。若真机觉得遮罩不足，可只微调 `frameScrimPaint` alpha，但不要给底栏加黑底。

二次审查补充：

- `MainActivity` 当前对 `bottomSheet` 设置 navigation bar bottom padding。如果底栏透明后导航栏区域仍显黑，先检查 `Theme.OpenCamera` 的 `android:navigationBarColor` 当前为 black；这可能是“底部黑色”的一部分。若要修复，应谨慎改为透明并验证手势导航/三键导航两种情况。
- 全屏 preview 后，`PreviewTapFocusGeometry.normalizedPreviewTapOrNull()` 会以 full `PreviewView` 尺寸归一化。底部控件若浮在 preview 上，点击控件区域不应穿透到 preview；确保 bottom controls 本身消费点击，且 overlay 不把 bottom controls 区域当成可对焦区域。
- `PreviewView` 使用 `app:scaleType="fitCenter"`。如果全屏铺底后画面 letterbox 区域变大，需要确认 frame overlay 和实际 CameraX preview content 没有明显错位；必要时增加布局截图/真机点按验证，不要只依赖 unit test。

验收：

- 未选画幅或 4:3 默认时，底栏区域能看到预览穿透，而不是整块黑底。
- 16:9 / 1:1 时，只有成片区域外出现半透明遮罩，底部控件仍清晰可用。
- 保存成片裁切与 preview overlay 的 active frame 保持一致。

### 4. 删除底部 `opencamera` 字样

代码中底部布局未直接看到 `OpenCamera` 文本，`titleText` 在顶部使用 `R.string.app_name`。如果真机底部出现品牌字样，可能来源：

- `captureOutput` 或某个 debug/status 文案被错误显示。
- 预览缩略图/水印模板把 `OpenCamera` 带到底部。
- 系统 recent/debug overlay 或旧 APK 缓存。

具体改法：

1. 全局检索 `OpenCamera` / `opencamera` / `app_name` 在底部相关 view 的赋值路径。
2. 不改顶部 `titleText`，只确保 `bottomSheet` 内没有任何品牌 TextView。
3. 如果来自 `captureOutput`，保持其 `visibility="gone"` 的默认状态，只有保存/错误反馈需要显示时才短暂显示；文案不得含品牌名。
4. 如果来自水印预览或默认水印模板，不属于底栏，应移交水印方案，不在本 UI 修复中改。

验收：

- 底部快门区、模式条、变焦条不出现 `opencamera` / `OpenCamera`。
- 顶部标题是否显示 `OpenCamera` 不在本条修复范围。

### 5. 快捷面板点击外收起

当前已有 `panelDismissScrim` 与 `DismissAll`，但用户实测快捷面板没有收起，说明需要修正可见性、层级或约束。

具体改法：

1. `MainActivityRenderer.renderPanelVisibility()` 里保持 `views.panelDismissScrim.isVisible = route.isAnyPanelOpen`，确认 `QuickBubble` 的 `isAnyPanelOpen` 为 true。
2. 在 XML 层级上让 `panelDismissScrim` 位于 preview 之上，但位于所有面板之下。当前 scrim 在 XML 中早于 quick panel，理论上 quick panel 在其上方。修改时不要把 scrim 放到 quick panel 后面。
3. 如果 scrim 全屏挡住了 quick panel 点击，使用 `elevation` 明确：
   - `panelDismissScrim`: 低于面板，例如 `4dp`
   - `quickBubblePanel/settingsPanel/filterPanel/devConsolePanel`: 高于 scrim，例如 `8dp`
4. 点击 `panelDismissScrim` 必须触发 `CockpitPanelCommand.DismissAll` 并调用 `renderAfterPanelChange()`。已有代码可保留。
5. 增加或更新 `CockpitPanelRouteTest`：QuickBubble 打开后 `DismissAll` 回到 `None`。

二次审查补充：

- 仅测试 router 不够，还要检查 `MainActivityRenderer.renderPanelVisibility()` 对 `QuickBubble` 是否让 scrim 可见、quick panel 可见。若现有测试没有 view 层，可至少添加一个小型 renderer test 或在手动验收清单中明确验证“scrim 显示时 quick panel 仍可点击”。
- `panelDismissScrim` 当前背景是 `#26000000`，用户第 3 条要求底栏透明，但打开面板时出现全屏淡遮罩是合理的。验收时应区分“常态底栏黑底”和“面板打开时 scrim 变暗”。

验收：

- 打开快捷，点击预览空白处、底部透明区、右侧空白处都收起快捷。
- 点击快捷面板内部按钮不会被 scrim 吃掉。
- Android back 仍可关闭快捷。

## 测试建议

优先跑：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

真机验收：

```bash
rtk adb install -r -d /Users/dingren/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
```

手动路径：

1. 打开相机，确认底部不是黑底。
2. 打开快捷，确认画幅区域背景和命中。
3. 点击快捷外区域，确认收起。
4. 打开设置/色彩实验室，确认面板下边界约在屏幕 60% 高度。
5. 切换 16:9/1:1，确认预览外区域半透明提示成片区域。
6. 点按预览中部和接近底部控件上方区域，确认 tap-to-focus 坐标没有因全屏 preview 改动而偏移。
7. 分别在手势导航和三键导航模式下确认底部不出现系统导航栏黑块误判。
