# Mode 控制器模板化重构 — 总览与架构（v2 渐进式方案）

## 背景

外部审查指出 7 个 ModeController（共 3,275 行）存在大量结构性复制。另一位 agent 对解决方案提出修正建议：**不直接上 BaseModeController 模板基类，而是先抽小而纯的 helper/delegate，等这些小块有测试后再评估是否需要基类。**

本方案采纳该建议，采用渐进式 4 步策略。

## 问题确认

结构性复制确实存在，集中在以下区域：

| 重复模式 | 涉及控制器数 | 估算重复行数 |
|----------|------------|------------|
| currentDeviceGraph (stillCapture) | 6/7 | ~8行 × 6 |
| cycleFrameRatio + selectFrameRatio | 5/7 | ~20行 × 5 |
| toggleProVariant + 手动控制辅助方法 | 3/7 | ~30行 × 3 |
| onSessionEvent 三分支 (PHOTO) | 6/7 | ~20行 × 6 |
| onEnter/onExit 生命周期 | 7/7 | ~15行 × 7 |
| MutableStateFlow + buildSnapshot | 7/7 | ~10行 × 7 |

## 为什么不直接上 BaseModeController

1. **CaptureStrategy / metadata / postProcess / EffectSpec 是每个模式最"重"的部分**，它们之间的差异不是模板方法能安全消掉的。Video 和 Document 是强反例。
2. **基类的 hook 体系会把模式差异藏进一堆 override**，代码行数少了但语义复杂度只是搬家。
3. **Stage 7 优先做 recovery / observability / automation**，不应在这个阶段做大范围架构重构。
4. **小 helper 可以独立引入、独立测试、独立回滚**，风险远低于一次性改 7 个文件的继承结构。

## 渐进式策略

### Step 1: `stillCaptureDeviceGraph(context)` — 纯函数

**解决问题**: 6 个 still mode 的 `currentDeviceGraph()` 完全同形。

**方案**: 在 `core/mode` 模块新增一个顶层函数：

```kotlin
// core/mode/src/main/kotlin/com/opencamera/core/mode/StillCaptureGraphHelper.kt
fun stillCaptureDeviceGraph(runtimeState: ModeRuntimeState): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState.stillCaptureQuality,
        resolutionPreset = runtimeState.stillCaptureResolutionPreset
    )
}
```

**采纳方式**: 每个 still mode 的 `currentDeviceGraph()` 改为一行调用。Video 不受影响。

**收益**: 消除 6 处 ~8 行重复，改为 1 行调用。纯函数，零风险。

### Step 2: `FrameRatioDelegate` — 独立委托类

**解决问题**: 5 个模式（Photo, Portrait, Night, Pro, Humanistic）的画幅切换逻辑完全相同。

**方案**: 独立类，封装 frameRatios 列表、索引管理、cycle/select 逻辑。

**采纳方式**: 每个模式在自己的属性中持有一个 `FrameRatioDelegate` 实例，在 `handleTertiary()` 和 `handleFrameRatioSelected()` 中调用。**不改变类继承结构。**

**收益**: 消除 5 处 ~20 行重复。独立类，有独立单元测试。

### Step 3: `ProVariantState` — 独立状态类

**解决问题**: 3 个模式（Portrait, Night, Humanistic）的 Pro 变体切换和手动控制辅助方法完全相同。

**方案**: 独立状态类，封装 `proVariantEnabled`、`toggleProVariant()`、`manualControlsEnabled()` 等方法。

**采纳方式**: 每个模式在自己的属性中持有一个 `ProVariantState` 实例。**不改变类继承结构。**

**收益**: 消除 3 处 ~30 行重复。独立类，有独立单元测试。

### Step 4: `StillShotSessionEventReducer` — 收敛三段式

**解决问题**: 6 个 PHOTO 模式的 `onSessionEvent` 三分支（ShotStarted/ShotCompleted/ShotFailed）结构相同。

**方案**: 辅助函数或小类，接收 ModeSnapshot 构建参数，返回更新后的 snapshot。

**采纳方式**: 每个模式的 `onSessionEvent` 内部调用 reducer，但仍保留自己的 `onSessionEvent` override。

**收益**: 消除 6 处 ~20 行重复。纯函数/小类，可测试。

### Step 5（未来）: 评估是否需要 BaseModeController

等 Step 1-4 完成并稳定后，重新评估：
- 如果剩余的 onEnter/onExit/handle 分发仍有明显重复 → 考虑 BaseModeController
- 如果剩余重复已不显著 → 不做基类，保持现状

## 执行顺序

```
Step 1 (stillCaptureDeviceGraph)  ← 独立，可立即执行
Step 2 (FrameRatioDelegate)       ← 独立，可与 Step 1 并行
Step 3 (ProVariantState)          ← 独立，可与 Step 1-2 并行
Step 4 (SessionEventReducer)      ← 独立，可与 Step 1-3 并行
Step 5 (评估基类)                 ← 依赖 Step 1-4 全部完成
```

Step 1-4 每个都是独立的、小的、可测试的，可以分发给不同的 agent 并行执行。每个 step 完成后独立提交。

## 预期收益

- Step 1: 消除 ~48 行重复（6 × 8 行 → 6 × 1 行）
- Step 2: 消除 ~100 行重复（5 × 20 行）
- Step 3: 消除 ~90 行重复（3 × 30 行）
- Step 4: 消除 ~120 行重复（6 × 20 行）
- 总计: ~350 行，占总量 ~10%

虽然行数减少不如"一步到位基类"方案（~40%），但**风险极低、每步可验证、不破坏现有架构**。

## 与 v1 方案的关系

v1 方案（01-base-class.md ~ 09-testing.md）中的 BaseModeController 设计仍然有效，可以作为 Step 5 的参考。但不作为当前执行目标。
