# Package 03: App + Core 模块 i18n 清理

## Objective
清理 `app/` 和 `core/` 模块中所有硬编码的英文 UI 字符串，改为使用 `R.string.xxx` 资源引用或中文文本。

## Prerequisites
- Package 01-strings-xml 已完成（新增 string ID 可用）
- Wave 2 启动条件: 01 状态为 `completed`

## Allowed Paths
**App 模块**:
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`

**Core 模块**:
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaTypes.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt`

## Forbidden Paths
- `app/src/main/res/values/strings.xml`（只能读，不能改）
- `feature/` 下的所有文件（Package 02 负责）
- 所有 `*Test.kt` 文件

## Acceptance Criteria
1. 所有枚举的显示标签不再使用英文硬编码
2. MainActivity 中 3 类 Toast 消息使用 `R.string.toast_xxx` 引用
3. CockpitSurfaceRenderer 中的 `contentDescription` 使用中文
4. core 模块中的用户可见标签使用 `R.string.xxx` 引用或中文
5. 功能行为不变——仅改变字符串，不改变逻辑

## 具体修改清单

### Part A: SessionUiRenderModel.kt
文件: `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`

#### A1. ModeLabel 枚举（行 168-171）
当前使用英文 `.name` 或硬编码字符串作为 UI 标签。修改 `displayName()` 或等效方法，映射到 string 资源。

| 枚举值 | 当前 | 目标 |
|---|---|---|
| PHOTO | "Photo" | `R.string.mode_label_photo` |
| HUMANISTIC | "Humanistic" | `R.string.mode_label_humanistic` |
| PORTRAIT | "Portrait" | `R.string.mode_label_portrait` |
| VIDEO | "Video" | `R.string.mode_label_video` |

注意：还有其他 mode（如 DOCUMENT, SCENERY, FULL_CLEAR, PRO）——如果此枚举包含它们，也需一并处理。

#### A2. FilterControlKind 枚举（行 188-199）
当前 `.name` 属性直接作为 UI 标签使用。这些与 `strings.xml` 中的 `filter_ctrl_*` 资源一一对应。

| 枚举值 | 当前硬编码 | strings.xml Key |
|---|---|---|
| EXPOSURE | "Exposure" | filter_ctrl_exposure |
| SOFT_GLOW | "Soft Glow" | filter_ctrl_soft_glow |
| HALO | "Halo" | filter_ctrl_halo |
| GRAIN | "Grain" | filter_ctrl_grain |
| SHARPNESS | "Sharpness" | filter_ctrl_sharpness |
| VIGNETTE | "Vignette" | filter_ctrl_vignette |
| HIGHLIGHTS | "Highlights" | filter_ctrl_highlights |
| SHADOWS | "Shadows" | filter_ctrl_shadows |
| WARM_BOOST | "Warm Boost" | filter_ctrl_warm_boost |
| COOL_BOOST | "Cool Boost" | filter_ctrl_cool_boost |
| TEMPERATURE_SHIFT | "Temp Shift" | filter_ctrl_temp_shift |
| TINT_SHIFT | "Tint Shift" | filter_ctrl_tint_shift |

#### A3. FilterAdvancedControl.Level 枚举（行 2046-2049）
| 枚举值 | 当前 | 目标 |
|---|---|---|
| OFF | "Off" | `R.string.level_off` |
| LOW | "Low" | `R.string.level_low` |
| MEDIUM | "Medium" | `R.string.level_medium` |
| HIGH | "High" | `R.string.level_high` |

#### A4. 硬编码字符串 "Focus"（行 1000）
`collect("Focus", capabilities.focusDistance)` — 将 "Focus" 改为 `R.string.label_focus`（已存在于 strings.xml）。

#### 实现方式
- 如果 SessionUiRenderModel 的函数可接受 Android Context 参数，使用 `context.getString(R.string.xxx)`
- 如果无法传入 Context，在枚举上定义 `val labelResId: Int`，由 UI 渲染层负责 `getString()`
- 参照现有的 `AppTextResolver` 模式（如果存在类似机制）
- 如果以上都不可行，使用中文硬编码字符串作为最小可行修复

### Part B: MainActivity.kt
文件: `app/src/main/java/com/opencamera/app/MainActivity.kt`

| 行号 | 当前 | 修改为 |
|---|---|---|
| 788 | `"Debug log exported: ${file.absolutePath}"` | `getString(R.string.toast_debug_log_exported, file.absolutePath)` |
| 791 | `"Export failed"` | `getString(R.string.toast_export_failed)` |
| 801 | `"Cleanup failed"` | `getString(R.string.toast_cleanup_failed)` |
| 811 | `"Cleanup failed"` | `getString(R.string.toast_cleanup_failed)` |

### Part C: CockpitSurfaceRenderer.kt
文件: `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`

| 行号 | 当前 | 修改为 |
|---|---|---|
| 103 | `"Zoom slider: ${model.disabledReason}"` | `"变焦滑块: ${model.disabledReason}"` 或新 string resource |
| 105 | `"Zoom slider: ${String.format(java.util.Locale.US, "%.1fx", model.currentRatio)}"` | `"变焦滑块: ${String.format(java.util.Locale.US, "%.1fx", model.currentRatio)}"` |

这些是 accessibility `contentDescription` 文本。如果在 Renderer 中无法访问 Android Context，直接用中文硬编码替换英文。

### Part D: DeviceContracts.kt
文件: `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`

#### D1. LensType 标签（行 63-65）
| 枚举值 | 当前 label | 目标 |
|---|---|---|
| WIDE | "Wide" | `R.string.lens_wide` 或 `"广角"` |
| TELEPHOTO | "Telephoto" | `R.string.lens_telephoto` 或 `"长焦"` |
| PERISCOPE | "Periscope" | `R.string.lens_periscope` 或 `"潜望"` |

#### D2. ZoomControlKind 标签（行 382-390）
| 值 | 当前 | 目标 |
|---|---|---|
| UNSUPPORTED | "Unsupported" | `R.string.label_zoom_unsupported` 或 `"不支持"` |
| PRESET_STEPS | "Preset steps" | `R.string.label_zoom_preset_steps` 或 `"预设步进"` |
| CONTINUOUS | "Continuous" | `R.string.label_zoom_continuous` 或 `"连续"` |

#### D3. DeviceRuntimeIssueKind 的 label 属性（行 593-600）
| 当前 | 目标 |
|---|---|
| "Bind failure" | `R.string.issue_bind_failure` |
| "Preview stalled" | `R.string.issue_preview_stall` |
| "Provider failure" | `R.string.issue_provider_failure` |
| "Camera recoverable error" | `R.string.issue_camera_recoverable` |
| "Camera fatal error" | `R.string.issue_camera_fatal` |
| "Camera unavailable" | `R.string.issue_camera_unavailable` |
| "Thermal critical" | `R.string.issue_thermal_critical` |
| "Runtime error" | `R.string.issue_runtime_error` |

### Part E: MediaTypes.kt
文件: `core/media/src/main/kotlin/com/opencamera/core/media/MediaTypes.kt`

| 行号 | 当前 label | 目标 |
|---|---|---|
| 36 | "Fast" | `R.string.button_still_fast`（已存在）或 `"快速"` |
| 40 | "Max" | `R.string.button_still_max`（已存在）或 `"最高"` |

### Part F: LivePhotoContracts.kt
文件: `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt`

| 行号 | 当前 label | 目标 |
|---|---|---|
| 16 | "Still Only" | `R.string.live_photo_still_only` 或 `"仅静态"` |
| 17 | "Motion Metadata Only" | `R.string.live_photo_motion_metadata_only` 或 `"仅动态元数据"` |
| 18 | "Motion Burned In" | `R.string.live_photo_motion_burned_in` 或 `"动态嵌入"` |
| 19 | "Unsupported" | `R.string.live_photo_unsupported` 或 `"不支持"` |
| 69 | "Watermark: Still Only" | `R.string.watermark_still_only` 或 `"水印: 仅静态"` |
| 70 | "Watermark: Metadata Only" | `R.string.watermark_metadata_only` 或 `"水印: 仅元数据"` |
| 71 | "Watermark: Burned In" | `R.string.watermark_burned_in` 或 `"水印: 动态嵌入"` |
| 72 | "Watermark: Unsupported" | `R.string.watermark_unsupported` 或 `"水印: 不支持"` |

## Verification Commands
```bash
# 1. app 模块中不再有硬编码英文 UI 字符串
grep -rn '"[A-Z][a-z].*[a-z]{2,}' app/src/main/java --include="*.kt" \
  | grep -v '//' | grep -v 'TAG =' | grep -v 'import' | grep -v 'package' \
  | grep -v 'Log\.' | grep -v 'require\|check\|error\|assert\|TODO' \
  | grep -v 'BuildConfig' | grep -v '\.put(' | grep -v '\.add(' \
  | grep -v '/test/' \
  || echo "PASS: no hardcoded English in app module"

# 2. core 模块中不再有硬编码英文显示标签
grep -rn 'label = "[A-Z]' core/device/src --include="*.kt" core/media/src --include="*.kt" \
  | grep -v test \
  || echo "PASS: no hardcoded English labels in core modules"

# 3. 检查特定枚举的显示名称
grep -rn '"[A-Z][a-z].*[a-z]{2,}"' core/device/src/main/kotlin --include="*.kt" \
  | grep -v '//' | grep -v 'TAG =' | grep -v 'import' | grep -v 'package' \
  | grep -v 'Log\.' | grep -v 'require\|check\|error\|assert' \
  | grep -v '/test/' \
  || echo "PASS: no hardcoded English strings in core device module"
```

## Core 模块特殊说明
Core 模块是纯 Kotlin 库，可能不直接依赖 Android `R` 类。处理方式的优先级：
1. 如果 core 模块有 `label` 属性用于 UI 显示，确保它最终通过 Context.getString() 解析
2. 如果 core 模块不能依赖 Android 资源，在 data class 中使用 `@StringRes Int` 类型存储资源 ID
3. 如果以上都不可行，将中文文本作为 display label 直接硬编码（比英文更合适当前中文语境）
4. 检查 core 模块的 `build.gradle.kts` 是否已经有 Android resource 依赖

## Notes
- `SessionUiRenderModel.kt` 的 `debugDump` 字符串（行 320-352）是开发者控制台文本，非最终用户 UI，可以保留英文或改为中文——根据现有 panel 中其他字符串的语言一致性决定
- `SessionCockpitRenderModel.kt` 的 `debugDump` 同样处理
- "Focus" 字符串在 `collect("Focus", ...)` 调用中——检查此处的 API 使用模式后替换
