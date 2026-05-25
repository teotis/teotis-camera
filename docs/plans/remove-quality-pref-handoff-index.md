# 移除画质设置 - 剩余工作交接索引

## 背景

commit `cfa034b` 已完成核心结构改动：
- 新增 `StillCaptureResolutionOption` + `smartFilterResolutionOptions`
- 从接口层移除 `onStillCaptureQualityChanged`
- 从 UI 快捷面板移除画质行
- 更新 `DeviceGraphSpec`、`StillCaptureConfig`、`ModeContext`、`ModeRuntimeState`

## 剩余工作包

| # | 工作包 | 难度 | 文件数 | 依赖 |
|---|--------|------|--------|------|
| A | CameraXCaptureAdapter 运行时代码 | 高 | 1 | 无 |
| B | ModePlugin + 核心库清理 | 中 | 7 | 无 |
| C | 测试文件更新 | 中 | 8+ | A+B 完成后 |
| D | 删除 StillCaptureQualityPreference 枚举 | 低 | 1 | C 完成后 |

## 执行顺序

A 和 B 可以并行执行。C 依赖 A+B 完成。D 是最后的清理步骤。

## 关键约束

- `StillCaptureQualityPreference` 枚举暂时保留（ShotLifecycleContracts 仍引用）
- CameraX 的 `ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY` / `MAXIMIZE_QUALITY` 仍需使用，只是不再暴露给用户
- 所有 `CaptureProfile.stillCaptureQuality` 字段赋值改为 `null` 或移除
