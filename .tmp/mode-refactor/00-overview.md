# Mode 控制器模板化重构 — 总览与架构

## 问题确认

外部审查结论**完全属实**。7 个 ModeController 实现（共 3,275 行）中存在大量结构性复制：

| 重复模式 | 涉及控制器数 | 重复行数（估算） |
|----------|------------|----------------|
| onEnter/onExit 生命周期 | 7/7 | ~15行 × 7 |
| onSessionEvent 三分支 | 7/7 | ~25行 × 7 |
| cycleFrameRatio + selectFrameRatio | 5/7 | ~20行 × 5 |
| currentDeviceGraph (stillCapture) | 6/7 | ~8行 × 6 |
| toggleProVariant + 手动控制辅助方法 | 3/7 | ~30行 × 3 |
| handle(intent) 分发结构 | 7/7 | ~10行 × 7 |
| MutableStateFlow + buildSnapshot | 7/7 | ~10行 × 7 |
| frameRatios 列表 + index | 6/7 | ~6行 × 6 |

**保守估计重复代码量：~450 行（占总量 14%），结构性重复影响 ~65% 的代码区域。**

## 架构目标

1. 消除结构性复制，提取通用逻辑到基类和委托类
2. 子类只保留差异点（模式特有逻辑）
3. **不改变 `ModeController` 接口**，对外部消费者（DefaultCameraSession）零影响
4. 每个重构后的控制器预估从 336-626 行降至 150-350 行

## 模块结构

```
core/mode/src/main/kotlin/com/opencamera/core/mode/
├── ModeContracts.kt          (不动 — 接口定义)
├── ModeCatalogContracts.kt   (不动)
├── ModeProductDeclaration.kt (不动)
├── BaseModeController.kt     (新增 — 抽象基类)
├── FrameRatioDelegate.kt     (新增 — 画幅委托)
└── ProVariantDelegate.kt     (新增 — Pro 变体委托)
```

## BaseModeController 设计

```kotlin
abstract class BaseModeController(
    protected val context: ModeContext
) : ModeController {

    // --- 子类必须提供的属性 ---
    abstract override val id: ModeId
    protected abstract val modeName: String         // 用于 eventSink 的前缀，如 "photo", "portrait"
    protected abstract val mediaType: MediaType      // 用于 onSessionEvent 过滤

    // --- 子类必须实现的方法 ---
    protected abstract fun buildEffectSpec(): EffectSpec
    protected abstract fun buildSnapshot(headline: String, detail: String): ModeSnapshot
    protected abstract fun defaultDetail(): String
    protected abstract fun submitCapture(): ModeSignal
    protected abstract fun cycleSecondary(): ModeSignal  // SecondaryActionPressed 的处理

    // --- 可选覆盖的钩子 ---
    protected open fun onModeEnter() {}    // onEnter 中的模式特有初始化
    protected open fun onModeExit() {}     // onExit 中的模式特有清理
    protected open fun handleTertiary(): ModeSignal = ModeSignal.None
    protected open fun handleProAction(): ModeSignal = ModeSignal.None
    protected open fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal =
        ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
    protected open fun onShotStartedEvent(shot: ShotRequest) {}
    protected open fun onShotCompletedEvent(result: ShotResult) {}
    protected open fun onShotFailedEvent(reason: String) {}
    protected open fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {}

    // --- 通用基础设施 ---
    protected val mutableSnapshot = MutableStateFlow(buildSnapshot("${modeName} pipeline ready"))
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    protected fun runtimeState() = context.runtimeState()

    // --- 委托（子类按需使用） ---
    protected val frameRatioDelegate = FrameRatioDelegate(context, modeName) { buildEffectSpec() }
    protected val proVariantDelegate = ProVariantDelegate(context, modeName)

    // --- 模板方法实现 ---
    override suspend fun onEnter() {
        context.eventSink("${modeName}.enter")
        onModeEnter()
        mutableSnapshot.value = buildSnapshot(headline = "${modeName} mode active")
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("${modeName}.exit")
        onModeExit()
        mutableSnapshot.value = buildSnapshot(
            headline = "${modeName} mode inactive",
            detail = "Switch back to ${modeName} to resume."
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCapture()
            ModeIntent.SecondaryActionPressed -> cycleSecondary()
            ModeIntent.TertiaryActionPressed -> handleTertiary()
            is ModeIntent.FrameRatioSelected -> handleFrameRatioSelected(intent.ratio)
            ModeIntent.ProActionPressed -> handleProAction()
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType != mediaType) return
                onShotStartedEvent(event.shot)
                mutableSnapshot.value = buildSnapshot(headline = "${modeName} capture in progress")
            }
            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != mediaType) return
                onShotCompletedEvent(event.result)
                mutableSnapshot.value = buildSnapshot(
                    headline = "${modeName} saved",
                    detail = event.result.outputPath
                )
            }
            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != mediaType) return
                onShotFailedEvent(event.reason)
                mutableSnapshot.value = buildSnapshot(
                    headline = "${modeName} capture failed",
                    detail = event.reason
                )
            }
            else -> Unit
        }
    }

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    protected open fun currentDeviceGraph(): DeviceGraphSpec {
        return DeviceGraphSpec.stillCapture(
            preferredLensFacing = runtimeState().lensFacing,
            enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
            qualityPreference = runtimeState().stillCaptureQuality,
            resolutionPreset = runtimeState().stillCaptureResolutionPreset
        )
    }

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        onDeviceCapabilitiesChangedImpl(deviceCapabilities)
        mutableSnapshot.value = buildSnapshot(headline = "${modeName} mode active")
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(headline = "${modeName} quality updated")
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(headline = "${modeName} resolution updated")
    }
}
```

## 委托设计

### FrameRatioDelegate

提供画幅切换的完整逻辑，被 5 个模式使用（Photo, Portrait, Night, Pro, Humanistic）。

```kotlin
class FrameRatioDelegate(
    private val context: ModeContext,
    private val modeName: String,
    private val onEffectSpecChanged: () -> EffectSpec
) {
    private val frameRatios = listOf(RATIO_4_3, RATIO_16_9, RATIO_1_1)
    private var frameRatioIndex = 0

    fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]

    suspend fun cycleFrameRatio(
        updateSnapshot: (String) -> Unit,
        emitEffect: suspend (EffectSpec) -> Unit
    ): ModeSignal { ... }

    suspend fun selectFrameRatio(
        ratio: FrameRatio,
        updateSnapshot: (String) -> Unit,
        emitEffect: suspend (EffectSpec) -> Unit
    ): ModeSignal { ... }
}
```

### ProVariantDelegate

提供 Pro 变体切换逻辑 + 手动控制辅助方法，被 3 个模式使用（Portrait, Night, Humanistic）。

```kotlin
class ProVariantDelegate(
    private val context: ModeContext,
    private val modeName: String
) {
    var proVariantEnabled: Boolean = false
        private set

    fun toggleProVariant(): Pair<Boolean, ModeSignal> { ... }
    fun manualControlsEnabled(): Boolean { ... }
    fun currentManualDraft(): ManualCaptureDraft { ... }
    fun currentManualDraftOrNull(): ManualCaptureDraft? { ... }
    fun resolvedControlMode(): String { ... }
    fun manualDraftState(): String { ... }
    fun resolvedAlgorithmProfile(base: String): String { ... }
}
```

## 工作包拆分

| 编号 | 文档 | 依赖 | 可并行 |
|------|------|------|--------|
| 1 | `01-base-class.md` — BaseModeController + 两个委托的实现 | 无 | — |
| 2 | `02-migrate-photo.md` — Photo 模式迁移 | 依赖 1 | 与 3-8 并行 |
| 3 | `03-migrate-portrait.md` — Portrait 模式迁移 | 依赖 1 | 与 2,4-8 并行 |
| 4 | `04-migrate-video.md` — Video 模式迁移 | 依赖 1 | 与 2-3,5-8 并行 |
| 5 | `05-migrate-night.md` — Night 模式迁移 | 依赖 1 | 与 2-4,6-8 并行 |
| 6 | `06-migrate-pro.md` — Pro 模式迁移 | 依赖 1 | 与 2-5,7-8 并行 |
| 7 | `07-migrate-document.md` — Document 模式迁移 | 依赖 1 | 与 2-6,8 并行 |
| 8 | `08-migrate-humanistic.md` — Humanistic 模式迁移 | 依赖 1 | 与 2-7 并行 |
| 9 | `09-testing.md` — 测试验证方案 | 依赖 1-8 全部完成 | — |

## 预期收益

- 代码总量：3,275 行 → ~2,000 行（减少 ~40%）
- 每个新增模式的开发成本：从 400-600 行降至 150-300 行
- Bug 修复传播：修复基类 = 修复所有模式
- 一致性保证：通用行为由基类强制统一
