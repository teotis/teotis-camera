# Package 02: Feature Mode 插件 i18n 清理

## Objective
清理 8 个 feature mode 插件中所有硬编码的英文 UI 字符串，改为引用 `R.string.xxx` 资源（由 Package 01 提供）。

## Prerequisites
- Package 01-strings-xml 已完成（新增 string ID 可用）
- Wave 2 启动条件: 01 状态为 `completed`

## Allowed Paths（共 8 个文件）
- `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- `feature/mode-fullclear/src/main/kotlin/com/opencamera/feature/fullclear/FullClearModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`

## Forbidden Paths
- `app/src/main/res/values/strings.xml`（只能读，不能改）
- `app/src/main/java/` 下的所有文件
- `core/` 下的所有文件

## Acceptance Criteria
1. 所有 8 个插件文件中不再有硬编码的英文 UI 字符串
2. 所有 mode title、shutterLabel、secondaryActionLabel、headline、detail 等使用 `R.string.xxx` 引用
3. 所有 profile/preset 的 label 使用 `R.string.xxx` 引用
4. 每个插件的功能行为不变——只改变字符串来源，不改变逻辑
5. 模式快照的测试文件（如有）可能需要匹配更新，但测试不在本 package 范围内修改

## 实现策略

### 核心模式
ModeController 需要 Context 来调用 `getString(R.string.xxx)`。每个插件的 `create(context: ModeContext)` 方法接收 `ModeContext`。获取 Android Context 的方式取决于 `ModeContext` 的 API：
- 检查 `ModeContext` 接口是否有 `context: android.content.Context` 或类似属性
- 如果没有直接 Context 访问，需要将 string 资源 ID 传给 UI 层，由 UI 层解析
- 另一种方式：在 `ModeUiSpec`/`ModeState` 中使用 `@StringRes Int` 代替 `String`

### 备选方案（如果无法在 ModeController 中访问 Android Context）
使用 `AppTextResolver` 模式——将资源 ID 作为 Int 存储在快照中，由 UI 渲染层负责 `getString(resId)` 调用。
这需要同时修改 app 模块中的渲染代码——但这会跨越 package 边界。如果遇到此情况，与 package 03 协调。

### 推荐方案
直接在每个插件的 `create()` 方法中，通过 `ModeContext` 获取 Android Context（检查 `ModeContext` 实际接口），使用 `context.getString(R.string.xxx)` 动态构建字符串。
如果 `ModeContext` 不暴露 Android Context，则将英文字符串替换为中文硬编码字符串（作为最小可行修复），并在风险中注明需要后续架构支持真正的 i18n。

## 具体修改清单

### 1. DocumentModePlugin.kt
文件: `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`

| 行号范围 | 当前英文 | 替换为 |
|---|---|---|
| 57 | `title = "Document"` | `R.string.mode_label_document` 或 `"文档"` |
| 58 | `shutterLabel = "Scan Document"` | `R.string.shutter_label_scan_document` 或 `"扫描文档"` |
| 59 | `secondaryActionLabel = "Cycle Scan Style"` | `R.string.secondary_action_cycle_scan_style` 或 `"切换扫描风格"` |
| 64 | `headline = "Document pipeline ready"` | `R.string.status_document_pipeline_ready` 或 `"文档管线就绪"` |
| 77 | `"Document scan active"` | `"文档扫描已激活"` |
| 79 | `"Document archive active"` | `"文档归档已激活"` |
| 91 | `"Document resolution updated"` | `"文档分辨率已更新"` |
| 93 | `"Archive resolution updated"` | `"归档分辨率已更新"` |
| 102 | `"Document scan active"` | `"文档扫描已激活"` |
| 104 | `"Document archive active"` | `"文档归档已激活"` |
| 113 | `headline = "Document mode inactive"` | `R.string.status_document_mode_inactive` 或 `"文档模式未激活"` |
| 131 | `shotStartedHeadline = "Document scan in progress"` | `R.string.status_document_scan_in_progress` 或 `"文档扫描进行中"` |
| 132 | `shotCompletedHeadline = "Document saved"` | `R.string.status_document_saved` 或 `"文档已保存"` |
| 133 | `shotFailedHeadline = "Document capture failed"` | `R.string.status_document_capture_failed` 或 `"文档拍摄失败"` |
| 149 | `headline = "Document capture requested"` | `R.string.status_document_capture_requested` 或 `"文档拍摄已请求"` |
| 223 | `"Document style updated"` | `"文档风格已更新"` |
| 225 | `"Archive style updated"` | `"归档风格已更新"` |
| 229 | `"Scan style: ${profile.label}"` | `"扫描风格: ${profile.label}"` |
| 265-267 | `"Style ... \| Size ... \| ..."` | 翻译为中文格式字符串 |
| 303 | `label = "Receipt"` | `"收据"` |
| 310 | `label = "Whiteboard"` | `"白板"` |
| 317 | `label = "Contract"` | `"合同"` |
| 327 | `label = "Archive"` | `"归档"` |
| 334 | `label = "Color Copy"` | `"彩色复印"` |
| 305 | `contrastLabel = "High"` | `"高"` |
| 312 | `contrastLabel = "Balanced"` | `"平衡"` |
| 319 | `contrastLabel = "Natural"` | `"自然"` |
| 329 | `contrastLabel = "Natural"` | `"自然"` |
| 336 | `contrastLabel = "Balanced"` | `"平衡"` |

### 2. FullClearModePlugin.kt
文件: `feature/mode-fullclear/src/main/kotlin/com/opencamera/feature/fullclear/FullClearModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 39 | `title = "Full Clear"` | `R.string.mode_label_fullclear` 或 `"全清"` |
| 40 | `shutterLabel = "Capture"` | `R.string.shutter_label_capture_fullclear` 或 `"拍摄"` |
| 43 | `headline = "Full Clear mode active"` | `R.string.status_fullclear_mode_active` 或 `"全清模式已激活"` |
| 44 | `detail = "Full clear capture pipeline ready."` | `R.string.detail_fullclear_pipeline_ready` 或 `"全清拍摄管线就绪。"` |
| 67 | `headline = "Full Clear mode active"` | 同上 |

### 3. HumanisticModePlugin.kt
文件: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 82 | `headline = "Humanistic pipeline ready"` | `R.string.status_humanistic_pipeline_ready` 或 `"人文管线就绪"` |
| 93,112 | `headline = "Humanistic mode active"` | `R.string.status_humanistic_mode_active` 或 `"人文模式已激活"` |
| 104 | `headline = "Humanistic resolution updated"` | `R.string.status_humanistic_resolution_updated` 或 `"人文分辨率已更新"` |
| 120 | `headline = "Humanistic mode inactive"` | `R.string.status_humanistic_mode_inactive` 或 `"人文模式未激活"` |
| 121 | `detail = "Switch back to Humanistic..."` | `R.string.detail_humanistic_inactive` 或 `"切换回人文模式以继续街拍生活摄影。"` |
| 139 | `shotStartedHeadline = "Humanistic capture in progress"` | `R.string.status_humanistic_capture_in_progress` |
| 140 | `shotStartedDetail = "Street-life still request..."` | `R.string.detail_humanistic_capture_progress` |
| 141 | `shotCompletedHeadline = "Humanistic photo saved"` | `R.string.status_humanistic_photo_saved` |
| 282 | `headline = "Humanistic style updated"` | `R.string.status_humanistic_style_updated` 或 `"人文风格已更新"` |
| 328 | `title = "Humanistic"` | `R.string.mode_label_humanistic` 或 `"人文"` |
| 329 | `shutterLabel = "Capture Humanistic"` | `R.string.shutter_label_capture_humanistic` |
| 330 | `secondaryActionLabel = "Cycle Humanistic Style"` | `R.string.secondary_action_cycle_humanistic_style` |
| 483,489,495,501,507 | `label = "Humanistic Original/Vivid/Street/Portrait/Life"` | 对应中文翻译 |

### 4. NightModePlugin.kt
文件: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 74 | `headline = "Scenery pipeline ready"` | `R.string.status_scenery_pipeline_ready` 或 `"风景管线就绪"` |
| 123 | `headline = "Scenery mode inactive"` | `R.string.status_scenery_mode_inactive` 或 `"风景模式未激活"` |
| 307 | `title = "Scenery"` | `R.string.mode_label_scenery` 或 `"风景"` |
| 308 | `shutterLabel = "Capture Scenery"` | `R.string.shutter_label_capture_scenery` 或 `"拍摄风景"` |
| 309 | `secondaryActionLabel = "Cycle Scenery Style"` | `R.string.secondary_action_cycle_scenery_style` 或 `"切换风景风格"` |
| 441 | `label = "Handheld"` | `"手持"` |
| 450 | `label = "Street"` | `"街拍"` |
| 459 | `label = "Tripod"` | `"三脚架"` |
| 471 | `label = "Balanced"` | `"平衡"` |
| 480 | `label = "Warm"` | `"暖色"` |

### 5. PhotoModePlugin.kt
文件: `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 77 | `headline = "Photo pipeline ready"` | `R.string.status_photo_pipeline_ready` 或 `"拍照管线就绪"` |
| 91,117 | `headline = "Photo mode active"` | `R.string.status_photo_mode_active` 或 `"拍照模式已激活"` |
| 101 | `headline = "Photo resolution updated"` | `R.string.status_photo_resolution_updated` |
| 109 | `headline = "Photo quality updated"` | `R.string.status_photo_quality_updated` |
| 125 | `headline = "Photo mode inactive"` | `R.string.status_photo_mode_inactive` 或 `"拍照模式未激活"` |
| 126 | `detail = "Switch back to photo..."` | `R.string.detail_photo_inactive` 或 `"切换回拍照以继续静态拍摄。"` |
| 258 | `headline = "Flash mode updated"` | `R.string.status_flash_mode_updated` 或 `"闪光灯模式已更新"` |
| 271 | `title = "Photo"` | `R.string.mode_label_photo` 或 `"拍照"` |
| 272 | `shutterLabel = "Capture Still"` | `R.string.shutter_label_capture_still` 或 `"拍摄静态"` |
| 488 | `label = "Vivid"` (及其他 profile) | 对应已有 `filter_profile_photo_*` 资源或中文硬编码 |

### 6. PortraitModePlugin.kt
文件: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 85 | `headline = "Portrait pipeline ready"` | `R.string.status_portrait_pipeline_ready` 或 `"人像管线就绪"` |
| 147 | `headline = "Portrait mode inactive"` | `R.string.status_portrait_mode_inactive` 或 `"人像模式未激活"` |
| 383 | `title = "Portrait"` | `R.string.mode_label_portrait` 或 `"人像"` |
| 384 | `shutterLabel = "Capture Portrait"` | `R.string.shutter_label_capture_portrait` 或 `"拍摄人像"` |
| 385 | `secondaryActionLabel = "Cycle Portrait Style"` | `R.string.secondary_action_cycle_portrait_style` |
| 573-615 | 各种 `label = "Portrait Original/Vivid/..."` | 对应已有 `filter_profile_portrait_*` 资源或中文硬编码 |

### 7. ProModePlugin.kt
文件: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 69 | `title = "Pro"` | `R.string.mode_label_pro` 或 `"专业"` |
| 70 | `shutterLabel = "Capture Pro Still"` | `R.string.shutter_label_capture_pro` 或 `"专业拍摄"` |
| 71 | `secondaryActionLabel = "Cycle Preset"` | `R.string.secondary_action_cycle_preset` 或 `"切换预设"` |
| 77 | `headline = "Pro pipeline ready"` | `R.string.status_pro_pipeline_ready` 或 `"专业管线就绪"` |
| 138 | `headline = "Pro mode inactive"` | `R.string.status_pro_mode_inactive` 或 `"专业模式未激活"` |
| 175 | `headline = "Pro capture requested"` | `R.string.status_pro_capture_requested` 或 `"专业拍摄已请求"` |
| 379-424 | `label = "Neutral/Street/Night/..."` | 对应中文翻译 |

### 8. VideoModePlugin.kt
文件: `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`

| 行号 | 当前英文 | 替换为 |
|---|---|---|
| 58 | `headline = "Video pipeline ready"` | `R.string.status_video_pipeline_ready` 或 `"视频管线就绪"` |
| 97 | `headline = "Video mode active"` | `R.string.status_video_mode_active` 或 `"视频模式已激活"` |
| 107 | `headline = "Video mode inactive"` | `R.string.status_video_mode_inactive` 或 `"视频模式未激活"` |
| 108 | `detail = "Leave recording decisions..."` | `R.string.detail_video_inactive` 或 `"将录制决策交由 Session Kernel 处理。"` |
| 130 | `headline = "Recording in progress"` | `R.string.status_recording_in_progress` 或 `"录制进行中"` |
| 142 | `headline = "Video saved"` | `R.string.detail_video_saved` 或 `"视频已保存"` |
| 154 | `headline = "Recording failed"` | `R.string.status_recording_failed` 或 `"录制失败"` |
| 174 | `headline = "Recording requested"` | `R.string.status_recording_requested` 或 `"录制已请求"` |
| 175 | `detail = "Waiting for Session Kernel..."` | `R.string.detail_recording_requested` 或格式化中文 |

## Verification Commands
```bash
# 1. 检查所有 feature 插件中不再有硬编码英文 UI 字符串
grep -rn '"[A-Z][a-z].*[a-z]{2,}' feature/mode-*/src/main/kotlin --include="*.kt" \
  | grep -v '//' | grep -v 'import' | grep -v 'package' \
  | grep -v 'Log\.' | grep -v 'TAG =' | grep -v 'require\|check\|error\|assert' \
  | grep -v '/test/' \
  || echo "PASS: no hardcoded English found"

# 2. 确认每个文件语法有效
for f in feature/mode-*/src/main/kotlin/**/*Plugin.kt; do
  echo "Checking $f..."
  # 语法由 Gradle 编译验证，以下检查文件存在且非空
  [ -s "$f" ] && echo "  OK: $f" || echo "  FAIL: $f"
done
```

## Risks
1. **ModeContext API 限制**: 如果 `ModeContext` 不暴露 Android `Context`，无法在插件中调用 `getString(R.string.xxx)`。此时用中文硬编码字符串作为最小可行修复。
2. **测试失败**: 部分 feature 模块的测试可能对英文字符串有精确匹配（如 `DocumentModePluginTest.kt`）。本 package 不修改测试文件——如果测试失败，在状态中记录，交给 99-finalize 评估。
3. **字符串格式**: `detail_recording_requested` 使用 `%s` 占位符，确保替换后格式字符串正确。

## Notes
- 首选方案：使用 `R.string.xxx` 资源引用（需要 ModeContext 提供 Android Context）
- 备选方案：如果无法访问 Android Context，直接使用中文硬编码字符串替换英文硬编码
- 所有更改保持原有逻辑不变，仅仅是字符串内容改变
