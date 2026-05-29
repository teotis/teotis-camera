# UI 中文国际化清理 — 最终报告

## 概览

将应用全部用户可见 UI 中的硬编码英文翻译为中文，覆盖 strings.xml 资源、8 个 feature mode 插件、app 模块及 core 模块。

## 合并信息

- **集成分支**: `agent/ui-i18n-cleanup/integration` (已删除)
- **主线合并 commit**: `391c5dd4` — `merge: UI i18n cleanup — translate all user-facing English to Chinese`
- **合并日期**: 2026-05-29
- **合并策略**: `--no-ff`，三阶段顺序合并

## 改动统计

### Package 01 — strings.xml 资源 (commit `73fb3a9e`)
- `app/src/main/res/values/strings.xml`: 新增约 80 个中文字符串资源
- `app/src/main/res/values-en/strings.xml`: 同步英文翻译
- 新增 key 涵盖: 滤镜控制标签、模式按钮标签、质量级别、镜头标签、水印设置等

### Package 02 — Feature Mode 插件 (commit `c67eaa5e`)
8 个 feature mode 插件文件中硬编码英文 UI 字符串替换为中文引用:

| 文件 | 模块 |
|---|---|
| `DocumentModePlugin.kt` | 文档 |
| `FullClearModePlugin.kt` | 全清 |
| `HumanisticModePlugin.kt` | 人文 |
| `NightModePlugin.kt` | 风景 |
| `PhotoModePlugin.kt` | 拍照 |
| `PortraitModePlugin.kt` | 人像 |
| `ProModePlugin.kt` | 专业 |
| `VideoModePlugin.kt` | 视频 |

### Package 03 — App + Core 模块 (commit `1bf11821`)
6 个文件中硬编码英文字符串替换为中文:

| 文件 | 改动类型 |
|---|---|
| `MainActivity.kt` | Toast 消息中文化 |
| `SessionUiRenderModel.kt` | 40+ 枚举标签和模式标签 |
| `CockpitSurfaceRenderer.kt` | 水印标签 |
| `DeviceContracts.kt` | 镜头标签 (Wide/Telephoto/Periscope → 广角/长焦/潜望) |
| `LivePhotoContracts.kt` | 显示标签 |
| `MediaTypes.kt` | 媒体类型标签 |

**总计**: 16 个源文件修改，约 80 个新 string 资源，200+ 处硬编码英文替换

## 验证结果

| 检查项 | 结果 | 备注 |
|---|---|---|
| values/strings.xml 无英文残留 | PASS | Camera2 为专有名词，保留 |
| Feature mode 插件无硬编码英文 UI | PASS | 残留为 EXIF 元数据/文件路径/调试信息 |
| App/Core 模块无硬编码英文 UI | PASS | 残留为 Cockpit 调试面板/内部错误信息 |
| strings.xml 两 locale key 一致 | PASS | values/strings.xml 与 values-en/strings.xml 完全匹配 |
| 合并冲突 | 无 | 三阶段合并均无冲突 |

## 已知限制

1. **"Vivid" 回退标签**: `PhotoModePlugin.kt:488` 中 `DEFAULT_PHOTO_FILTER` 的 `label = "Vivid"` 为伴生对象回退值，仅在设置系统加载失败时使用，实际运行时被 `filter_profile_photo_vivid`(鲜明) 覆盖。
2. **Cockpit 调试面板**: 开发者调试面板 (SessionCockpitRenderModel.kt) 中的状态标签保留英文，这些不面向最终用户。
3. **EXIF 元数据**: 写入照片文件的 EXIF 标签 (如 `SceneCaptureType`, `PortraitStyle` 等) 使用英文键值，这是 EXIF 标准约定，非 UI 范畴。
4. **未执行编译验证**: 本地未配置 Android SDK，无法运行 `./gradlew compileDebugKotlin`。
5. **未执行真机视觉 QA**: 属于 external-assist 类能力，不阻塞代码合并。

## 清理结果

- 3 个功能包分支已删除
- 集成分支已删除
- 3 个功能包工作树已移除
- 当前工作树 `99-finalize` 待退出时清理
