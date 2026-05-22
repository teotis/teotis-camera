# 工作包 1：BaseModeController + 委托类实现

## 目标

在 `core/mode` 模块中创建 3 个新文件：
1. `BaseModeController.kt` — 抽象基类
2. `FrameRatioDelegate.kt` — 画幅切换委托
3. `ProVariantDelegate.kt` — Pro 变体切换委托

## 依赖

无。这是第一个需要完成的工作包。

## 文件 1：BaseModeController.kt

**路径**: `core/mode/src/main/kotlin/com/opencamera/core/mode/BaseModeController.kt`

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ModeController 的模板基类。
 *
 * 提供所有模式共享的生命周期管理、事件分发和快照更新逻辑。
 * 子类只需实现差异点（buildEffectSpec、submitCapture 等）。
 *
 * @param context 模式上下文，包含设备能力、设置快照、事件通道等
 */
abstract class BaseModeController(
    protected val context: ModeContext
) : ModeController {

    // ── 子类必须提供的属性 ──────────────────────────────────

    /** 模式标识符，如 ModeId.PHOTO */
    abstract override val id: ModeId

    /** 用于 eventSink 事件前缀，如 "photo", "portrait" */
    protected abstract val modeName: String

    /** 用于 onSessionEvent 过滤的媒体类型 */
    protected abstract val mediaType: MediaType

    // ── 子类必须实现的方法 ──────────────────────────────────

    /** 构建当前模式的 EffectSpec（滤镜、水印、画幅效果等） */
    protected abstract fun buildEffectSpec(): EffectSpec

    /** 构建模式快照 */
    protected abstract fun buildSnapshot(headline: String, detail: String): ModeSnapshot

    /** 默认详情文本，用于 buildSnapshot 的默认 detail 参数 */
    protected abstract fun defaultDetail(): String

    /** 处理快门按下：构建 CaptureStrategy 并返回 SubmitCapture 信号 */
    protected abstract fun submitCapture(): ModeSignal

    /** 处理 SecondaryActionPressed（如切换闪光灯/风格/预设） */
    protected abstract fun cycleSecondary(): ModeSignal

    // ── 可选覆盖的钩子 ──────────────────────────────────────

    /** onEnter 中的模式特有初始化逻辑（在通用逻辑之前调用） */
    protected open fun onModeEnter() {}

    /** onExit 中的模式特有清理逻辑（在通用逻辑之前调用） */
    protected open fun onModeExit() {}

    /** 处理 TertiaryActionPressed，默认返回 None */
    protected open fun handleTertiary(): ModeSignal = ModeSignal.None

    /** 处理 ProActionPressed，默认返回 None */
    protected open fun handleProAction(): ModeSignal = ModeSignal.None

    /** 处理 FrameRatioSelected，默认返回"不支持"提示 */
    protected open fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal =
        ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")

    /** ShotStarted 事件的额外处理（在通用快照更新之后） */
    protected open fun onShotStartedEvent(shot: ShotRequest) {}

    /** ShotCompleted 事件的额外处理（在通用快照更新之后） */
    protected open fun onShotCompletedEvent(result: ShotResult) {}

    /** ShotFailed 事件的额外处理（在通用快照更新之后） */
    protected open fun onShotFailedEvent(reason: String) {}

    /** onDeviceCapabilitiesChanged 的模式特有逻辑 */
    protected open fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {}

    /** onEnter 时的自定义 headline，默认为 "${modeName} mode active" */
    protected open fun enterHeadline(): String = "${modeName} mode active"

    /** onEnter 时是否需要重建 snapshot（默认 true） */
    protected open fun rebuildSnapshotOnEnter(): Boolean = true

    // ── 通用基础设施 ────────────────────────────────────────

    protected val mutableSnapshot = MutableStateFlow(
        buildSnapshot(headline = "${modeName} pipeline ready", detail = defaultDetail())
    )

    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    protected fun runtimeState() = context.runtimeState()

    // ── 委托（子类按需在自己的方法中调用） ──────────────────

    protected val frameRatioDelegate = FrameRatioDelegate(context, modeName) { buildEffectSpec() }
    protected val proVariantDelegate = ProVariantDelegate(context, modeName)

    // ── 模板方法实现 ────────────────────────────────────────

    override suspend fun onEnter() {
        context.eventSink("${modeName}.enter")
        onModeEnter()
        if (rebuildSnapshotOnEnter()) {
            mutableSnapshot.value = buildSnapshot(headline = enterHeadline(), detail = defaultDetail())
        }
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
                mutableSnapshot.value = buildSnapshot(
                    headline = "${modeName} capture in progress",
                    detail = defaultDetail()
                )
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

    /** 默认的设备图构建：静态拍照。Video 模式覆盖此方法。 */
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
        mutableSnapshot.value = buildSnapshot(headline = enterHeadline(), detail = defaultDetail())
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "${modeName} quality updated",
            detail = defaultDetail()
        )
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "${modeName} resolution updated",
            detail = defaultDetail()
        )
    }
}
```

## 文件 2：FrameRatioDelegate.kt

**路径**: `core/mode/src/main/kotlin/com/opencamera/core/mode/FrameRatioDelegate.kt`

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio

/**
 * 画幅比例切换委托。
 *
 * 封装 frameRatios 列表、索引管理、cycle/select 逻辑。
 * 被 Photo、Portrait、Night、Pro、Humanistic 5 个模式使用。
 *
 * @param context 模式上下文
 * @param modeName 模式名称（用于事件前缀）
 * @param effectSpecFactory 重建 EffectSpec 的工厂方法
 */
class FrameRatioDelegate(
    private val context: ModeContext,
    private val modeName: String,
    private val effectSpecFactory: () -> EffectSpec
) {
    private val frameRatios = listOf(
        FrameRatio.RATIO_4_3,
        FrameRatio.RATIO_16_9,
        FrameRatio.RATIO_1_1
    )
    private var frameRatioIndex = 0

    fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]

    /**
     * 循环到下一个画幅比例。
     *
     * @param updateSnapshot 回调：子类用它更新自己的 mutableSnapshot
     * @param emitEffect 回调：子类用它调用 context.onEffectSpecChanged
     * @return 提示信号
     */
    suspend fun cycleFrameRatio(
        updateSnapshot: (headline: String) -> Unit,
        emitEffect: suspend (EffectSpec) -> Unit
    ): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % frameRatios.size
        val frameRatio = currentFrameRatio()
        context.eventSink("${modeName}.frame-ratio.selected.${frameRatio.eventTag()}")
        updateSnapshot("${modeName} frame updated")
        emitEffect(effectSpecFactory())
        return ModeSignal.ShowHint("Frame: ${frameRatio.label}")
    }

    /**
     * 选择指定画幅比例。
     *
     * @param ratio 目标画幅
     * @param updateSnapshot 回调
     * @param emitEffect 回调
     * @return 提示信号
     */
    suspend fun selectFrameRatio(
        ratio: FrameRatio,
        updateSnapshot: (headline: String) -> Unit,
        emitEffect: suspend (EffectSpec) -> Unit
    ): ModeSignal {
        val nextIndex = frameRatios.indexOf(ratio)
        if (nextIndex < 0) return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
        frameRatioIndex = nextIndex
        context.eventSink("${modeName}.frame-ratio.selected.${ratio.eventTag()}")
        updateSnapshot("画幅已更新")
        emitEffect(effectSpecFactory())
        return ModeSignal.ShowHint("画幅：${ratio.label}")
    }
}
```

## 文件 3：ProVariantDelegate.kt

**路径**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ProVariantDelegate.kt`

```kotlin
package com.opencamera.core.mode

/**
 * Pro 变体切换委托。
 *
 * 封装 proVariantEnabled 状态管理和手动控制辅助方法。
 * 被 Portrait、Night、Humanistic 3 个模式使用。
 *
 * Pro 模式本身始终是 Pro，不需要切换，因此不使用此委托。
 *
 * @param context 模式上下文
 * @param modeName 模式名称（用于事件前缀）
 */
class ProVariantDelegate(
    private val context: ModeContext,
    private val modeName: String
) {
    var proVariantEnabled: Boolean = false
        private set

    /**
     * 切换 Pro 变体状态。
     *
     * @return Pair(新状态, 信号)
     */
    fun toggleProVariant(): Pair<Boolean, ModeSignal> {
        proVariantEnabled = !proVariantEnabled
        val stateLabel = if (proVariantEnabled) "entered" else "exited"
        val hintPrefix = modeName.replaceFirstChar { it.titlecase() }
        val hint = if (proVariantEnabled) {
            if (manualControlsEnabled()) "${hintPrefix} Pro on"
            else "${hintPrefix} Pro assist on"
        } else {
            "${hintPrefix} Pro off"
        }
        return proVariantEnabled to ModeSignal.ShowHint(hint)
    }

    /** 发送 Pro 变体切换事件。应在 toggleProVariant 之后调用。 */
    suspend fun emitToggleEvent() {
        val stateLabel = if (proVariantEnabled) "entered" else "exited"
        context.eventSink("${modeName}.pro-variant.$stateLabel")
    }

    /** Pro 变体激活时的 headline 后缀 */
    fun proActiveHeadline(baseName: String): String {
        return if (proVariantEnabled) {
            if (manualControlsEnabled()) "${baseName} Pro active"
            else "${baseName} Pro assist active"
        } else {
            "${baseName} mode active"
        }
    }

    /** Pro 变体的 proActionLabel */
    fun proActionLabel(): String {
        return if (proVariantEnabled) {
            if (manualControlsEnabled()) "Exit Pro" else "Exit Pro Assist"
        } else if (manualControlsEnabled()) {
            "Enter Pro"
        } else {
            "Enter Pro Assist"
        }
    }

    // ── 手动控制辅助方法 ────────────────────────────────────

    fun manualControlsEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsAppliedManualControls

    fun currentManualDraft() = context.settingsSnapshot.catalog.manualCaptureDraft

    fun currentManualDraftOrNull() = currentManualDraft().takeIf { proVariantEnabled }

    fun resolvedControlMode(): String =
        if (manualControlsEnabled()) "manual" else "assisted"

    fun manualDraftState(): String =
        if (manualControlsEnabled()) "metadata-draft" else "unsupported"

    fun resolvedAlgorithmProfile(base: String): String {
        return if (!proVariantEnabled) {
            base
        } else if (manualControlsEnabled()) {
            "${base}-pro"
        } else {
            "${base}-pro-assist"
        }
    }

    /** 构建 Pro 变体的元数据 tags（用于 CaptureStrategy 的 metadata） */
    fun buildMetadataTags(): Map<String, String> {
        if (!proVariantEnabled) return emptyMap()
        return buildMap {
            put("controlMode", resolvedControlMode())
            put("manualDraftState", manualDraftState())
            putAll(currentManualDraft().toMetadataTags())
        }
    }

    /** 构建 Pro 变体的摘要文本（用于 detail 字段） */
    fun proSummaryText(): String {
        return if (manualControlsEnabled()) {
            "Pro draft ${currentManualDraft().compactSummary()} is attached to the capture request."
        } else {
            "Pro assist keeps ${currentManualDraft().compactSummary()} as saved-only draft because manual controls are unavailable on this device."
        }
    }

    private fun runtimeState() = context.runtimeState()
}
```

## 验证清单

完成本工作包后，验证：

- [ ] 3 个新文件创建成功，无编译错误
- [ ] `BaseModeController` 是 `abstract class`，实现 `ModeController` 接口
- [ ] `FrameRatioDelegate` 的 `cycleFrameRatio` 和 `selectFrameRatio` 签名正确
- [ ] `ProVariantDelegate` 的 `toggleProVariant` 返回 `Pair<Boolean, ModeSignal>`
- [ ] `import` 语句完整，无遗漏
- [ ] 现有测试仍然通过（`./gradlew :core:mode:test`）
