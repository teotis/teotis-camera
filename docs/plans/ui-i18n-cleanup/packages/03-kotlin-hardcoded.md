# Package 03-kotlin-hardcoded - Kotlin 源码硬编码英文清理

## Objective
清理 Kotlin 源码中硬编码的英文用户可见字符串，改为使用字符串资源引用。

## Allowed Paths
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt` (如需添加新方法)
- `app/src/main/res/values/strings.xml` (如需添加新字符串资源)
- `app/src/main/res/values-en/strings.xml` (如需同步)

## Forbidden Paths
- 所有其他文件

## Tasks

### 1. 修复 MainActivity.kt 中的硬编码 Toast/text
| Line | Current | Proposed Fix |
|---|---|---|
| 788 | `"Debug log exported: ${file.absolutePath}"` | 使用 `getString(R.string.dev_log_exported, file.absolutePath)` 并添加 `<string name="dev_log_exported">调试日志已导出: %s</string>` |
| 791 | `"Export failed"` | 使用 `getString(R.string.dev_export_failed)` 并添加 `<string name="dev_export_failed">导出失败</string>` |
| 801 | `"Cleanup failed"` | 使用 `getString(R.string.dev_cleanup_failed)` 并添加 `<string name="dev_cleanup_failed">清理失败</string>` |
| 811 | `"Cleanup failed"` | 同上，复用 `R.string.dev_cleanup_failed` |

### 2. 修复 SessionUiRenderModel.kt 中的 FilterControl 枚举
| Line | Enum Value | Current Display Name | Proposed Fix |
|---|---|---|---|
| 189 | SOFT_GLOW | `"Soft Glow"` | 通过 AppTextResolver 引用 `R.string.filter_ctrl_soft_glow` (已有中文"柔光") |
| 196 | WARM_BOOST | `"Warm Boost"` | 引用 `R.string.filter_ctrl_warm_boost` (已有"暖色增强") |
| 197 | COOL_BOOST | `"Cool Boost"` | 引用 `R.string.filter_ctrl_cool_boost` (已有"冷色增强") |
| 198 | TEMPERATURE_SHIFT | `"Temp Shift"` | 引用 `R.string.filter_ctrl_temp_shift` (已有"色温偏移") |
| 199 | TINT_SHIFT | `"Tint Shift"` | 引用 `R.string.filter_ctrl_tint_shift` (已有"色调偏移") |

**注意**: 枚举构造函数中无法直接访问 Context，需要：
- 方案 A: 在枚举中添加 `resId` 字段，显示时通过 Context 获取
- 方案 B: 在 AppTextResolver 中添加 `filterControlDisplayName()` 方法
- 方案 C: 将显示名称从枚举移出，在渲染时通过 AppTextResolver 获取

### 3. 修复 SessionCockpitRenderModel.kt
| Line | Current | Proposed Fix |
|---|---|---|
| 743 | `"Zoom unavailable"` | 使用 AppTextResolver 的 `zoomUnavailable()` 方法 (已有中文"变焦不可用") |

### 4. 全面扫描
```bash
# 扫描所有 Kotlin 文件中的硬编码英文 UI 字符串
grep -rn 'setText("' app/src/main/java/ --include="*.kt"
grep -rn '\.text = "[A-Z]' app/src/main/java/ --include="*.kt"
grep -rn 'Toast\.makeText.*"[A-Za-z]' app/src/main/java/ --include="*.kt"
grep -rn '("[A-Z][a-z][a-z]* [A-Za-z]*")' app/src/main/java/ --include="*.kt" | grep -v 'import\|package\|Log\.\|TAG\|Exception\|class \|object '
```

## Verification Commands
```bash
# 确认无硬编码英文 Toast
grep -rn 'Toast\.makeText.*"[A-Za-z]' app/src/main/java/ --include="*.kt"
# 应无输出

# 确认无硬编码英文 text 赋值
grep -rn '\.text = "[A-Z][a-z]' app/src/main/java/ --include="*.kt"
# 应无输出

# 构建验证
./gradlew assembleDebug
```

## Acceptance Criteria
- MainActivity.kt 中无硬编码英文 Toast 或 text 赋值
- SessionUiRenderModel.kt 中 FilterControl 枚举的显示名称通过字符串资源获取
- SessionCockpitRenderModel.kt 中无硬编码英文
- 应用编译通过
- 如添加新字符串资源，values/ 和 values-en/ 同步更新

## Expected Evidence
- git diff 显示所有文件修改
- 构建日志确认编译成功
- grep 扫描确认无残留

## Branch/Worktree Policy
- Branch: `agent/ui-i18n-cleanup/03-kotlin-hardcoded`
- Worktree: `.claude/worktrees/03-kotlin-hardcoded`

## Unlock Condition
- None (wave 1, no dependencies)
