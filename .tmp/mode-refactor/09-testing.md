# 工作包 9：测试验证方案

## 目标

确保重构后所有模式的行为与重构前完全一致。

## 依赖

工作包 1-8 全部完成。

## 测试策略

### 第一层：编译验证

每个模式迁移完成后立即验证：

```bash
# 全模块编译
./gradlew compileDebugKotlin

# 单模块编译（快速反馈）
./gradlew :core:mode:compileDebugKotlin
./gradlew :feature:mode-photo:compileDebugKotlin
./gradlew :feature:mode-portrait:compileDebugKotlin
./gradlew :feature:mode-video:compileDebugKotlin
./gradlew :feature:mode-night:compileDebugKotlin
./gradlew :feature:mode-pro:compileDebugKotlin
./gradlew :feature:mode-document:compileDebugKotlin
./gradlew :feature:mode-humanistic:compileDebugKotlin
```

### 第二层：现有测试

```bash
# 全量测试
./gradlew test

# 核心模式测试
./gradlew :core:mode:test
```

### 第三层：BaseModeController 单元测试

在 `core/mode/src/test/kotlin/com/opencamera/core/mode/` 下新增：

**`BaseModeControllerTest.kt`**：

测试以下场景：
1. `onEnter` 发送正确的 eventSink 事件
2. `onEnter` 更新 snapshot headline
3. `onEnter` 调用 `onEffectSpecChanged`
4. `onExit` 发送正确的 eventSink 事件
5. `onExit` 设置 inactive snapshot
6. `handle(ShutterPressed)` 调用 `submitCapture()`
7. `handle(SecondaryActionPressed)` 调用 `cycleSecondary()`
8. `handle(TertiaryActionPressed)` 调用 `handleTertiary()`
9. `handle(FrameRatioSelected)` 调用 `handleFrameRatioSelected()`
10. `handle(ProActionPressed)` 调用 `handleProAction()`
11. `onSessionEvent(ShotStarted)` — 匹配 mediaType 时更新 snapshot
12. `onSessionEvent(ShotStarted)` — 不匹配 mediaType 时忽略
13. `onSessionEvent(ShotCompleted)` — 匹配时更新 snapshot + outputPath
14. `onSessionEvent(ShotFailed)` — 匹配时更新 snapshot + reason
15. `deviceGraph()` 默认返回 stillCapture

**`FrameRatioDelegateTest.kt`**：

测试以下场景：
1. `currentFrameRatio()` 初始返回 RATIO_4_3
2. `cycleFrameRatio()` 依次循环 4:3 → 16:9 → 1:1 → 4:3
3. `selectFrameRatio()` 选择有效比例
4. `selectFrameRatio()` 选择无效比例返回提示
5. 事件前缀正确（使用 modeName）

**`ProVariantDelegateTest.kt`**：

测试以下场景：
1. `proVariantEnabled` 初始为 false
2. `toggleProVariant()` 切换状态
3. `toggleProVariant()` 返回正确的信号消息
4. `manualControlsEnabled()` 根据 deviceCapabilities 返回正确值
5. `resolvedAlgorithmProfile()` — proVariant 关闭时返回原值
6. `resolvedAlgorithmProfile()` — proVariant 开启 + 手动控制可用时返回 "${base}-pro"
7. `resolvedAlgorithmProfile()` — proVariant 开启 + 手动控制不可用时返回 "${base}-pro-assist"
8. `buildMetadataTags()` — proVariant 关闭时返回空 map
9. `buildMetadataTags()` — proVariant 开启时返回完整 tags
10. `proActionLabel()` — 各状态下的正确标签

### 第四层：行为回归测试

对每个模式运行以下场景（手动或集成测试）：

| 场景 | 预期行为 |
|------|---------|
| 进入模式 | eventSink 收到 "{mode}.enter"，snapshot 更新为 active |
| 退出模式 | eventSink 收到 "{mode}.exit"，snapshot 更新为 inactive |
| 按下快门 | 返回 SubmitCapture 信号，snapshot 显示 capture requested |
| 切换次要功能 | 返回提示信号，snapshot 更新 |
| 切换画幅 | 返回画幅提示，snapshot 更新，EffectSpec 更新 |
| 切换 Pro 变体 | 状态翻转，snapshot 更新，eventSink 发送事件 |
| 拍照完成事件 | snapshot 显示 saved + outputPath |
| 拍照失败事件 | snapshot 显示 failed + reason |
| 设备能力变化 | snapshot 更新，索引约束正确 |

## 执行顺序

1. 工作包 1 完成后：运行第一层 + 第二层 + 第三层（新测试）
2. 工作包 2-8 每完成一个：运行该模块的第一层 + 第二层
3. 工作包 2-8 全部完成后：运行全量第一层 + 第二层 + 第四层

## 验收标准

- [ ] 全模块编译通过（0 errors, 0 warnings 相关重构）
- [ ] 所有现有测试通过
- [ ] 新增的 BaseModeController、FrameRatioDelegate、ProVariantDelegate 测试通过
- [ ] 代码行数统计：总行数减少 ~35%+
- [ ] 无行为回归（所有模式的 snapshot、eventSink、ModeSignal 输出不变）
