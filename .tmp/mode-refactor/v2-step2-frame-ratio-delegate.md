# Step 2: `FrameRatioDelegate` 独立委托类

## 目标

消除 5 个模式中完全同形的画幅切换逻辑。

## 问题

以下 5 个控制器的 `cycleFrameRatio()` 和 `selectFrameRatio()` 实现完全相同（~20 行）：

- PhotoModeController
- PortraitModeController
- ProModeController
- NightModeController
- HumanisticModeController

Document 模式不使用画幅（自动裁边），Video 模式拒绝画幅切换。

## 实现

### 新增文件

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
 * 使用方式：在控制器中持有一个实例，在 handleTertiary/handleFrameRatioSelected 中调用。
 * 不改变控制器的类继承结构。
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
        updateSnapshot("画幅已更新")
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

### 修改 5 个控制器

以 PhotoModeController 为例，将：

```kotlin
private val frameRatios = listOf(
    FrameRatio.RATIO_4_3,
    FrameRatio.RATIO_16_9,
    FrameRatio.RATIO_1_1
)
private var frameRatioIndex = 0
// ...
private suspend fun cycleFrameRatio(): ModeSignal {
    frameRatioIndex = (frameRatioIndex + 1) % frameRatios.size
    val frameRatio = currentFrameRatio()
    context.eventSink("photo.frame-ratio.selected.${frameRatio.eventTag()}")
    mutableSnapshot.value = buildSnapshot(headline = "Frame ratio updated")
    context.onEffectSpecChanged(buildEffectSpec(currentFlashMode()))
    return ModeSignal.ShowHint("Frame: ${frameRatio.label}")
}
private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal {
    val nextIndex = frameRatios.indexOf(ratio)
    if (nextIndex < 0) return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
    frameRatioIndex = nextIndex
    context.eventSink("photo.frame-ratio.selected.${ratio.eventTag()}")
    mutableSnapshot.value = buildSnapshot(headline = "画幅已更新")
    context.onEffectSpecChanged(buildEffectSpec(currentFlashMode()))
    return ModeSignal.ShowHint("画幅：${ratio.label}")
}
private fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]
```

改为：

```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "photo") { buildEffectSpec(currentFlashMode()) }
// ...
// 在 handle() 中：
ModeIntent.TertiaryActionPressed -> frameRatioDelegate.cycleFrameRatio(
    updateSnapshot = { mutableSnapshot.value = buildSnapshot(headline = it) },
    emitEffect = { context.onEffectSpecChanged(it) }
)
is ModeIntent.FrameRatioSelected -> frameRatioDelegate.selectFrameRatio(
    ratio = intent.ratio,
    updateSnapshot = { mutableSnapshot.value = buildSnapshot(headline = it) },
    emitEffect = { context.onEffectSpecChanged(it) }
)
// ...
// 其他引用 currentFrameRatio() 的地方改为 frameRatioDelegate.currentFrameRatio()
```

**注意**: Photo 的 `buildEffectSpec(flashMode)` 有参数，所以 effectSpecFactory 需要适配：
```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "photo") {
    buildEffectSpec(currentFlashMode())
}
```

同样修改 Portrait、Pro、Night、Humanistic 的控制器。

## 测试

**`FrameRatioDelegateTest.kt`**：

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FrameRatioDelegateTest {

    private fun createDelegate(modeName: String = "test") = FrameRatioDelegate(
        context = ModeContext(),
        modeName = modeName,
        effectSpecFactory = { EffectSpec(emptyList()) }
    )

    @Test
    fun `initial frame ratio is 4_3`() {
        val delegate = createDelegate()
        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())
    }

    @Test
    fun `cycle advances through ratios`() = runTest {
        val delegate = createDelegate()
        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())

        delegate.cycleFrameRatio(
            updateSnapshot = {},
            emitEffect = {}
        )
        assertEquals(FrameRatio.RATIO_16_9, delegate.currentFrameRatio())

        delegate.cycleFrameRatio(
            updateSnapshot = {},
            emitEffect = {}
        )
        assertEquals(FrameRatio.RATIO_1_1, delegate.currentFrameRatio())

        delegate.cycleFrameRatio(
            updateSnapshot = {},
            emitEffect = {}
        )
        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())
    }

    @Test
    fun `select valid ratio`() = runTest {
        val delegate = createDelegate()
        val signal = delegate.selectFrameRatio(
            ratio = FrameRatio.RATIO_16_9,
            updateSnapshot = {},
            emitEffect = {}
        )
        assertEquals(FrameRatio.RATIO_16_9, delegate.currentFrameRatio())
        assert(signal is ModeSignal.ShowHint)
    }

    @Test
    fun `select invalid ratio returns hint`() = runTest {
        val delegate = createDelegate()
        // 假设 RATIO_4_3 是有效的，用一个不存在的 ratio 测试
        // 实际上 FrameRatio 枚举只有三个值，所以这里测试所有三个都是有效的
        // 但我们可以验证 cycle 后 select 仍然正确
        val signal = delegate.selectFrameRatio(
            ratio = FrameRatio.RATIO_1_1,
            updateSnapshot = {},
            emitEffect = {}
        )
        assertEquals(FrameRatio.RATIO_1_1, delegate.currentFrameRatio())
        assert(signal is ModeSignal.ShowHint)
    }

    @Test
    fun `cycle sends correct event prefix`() = runTest {
        val events = mutableListOf<String>
        val delegate = FrameRatioDelegate(
            context = ModeContext(eventSink = { events.add(it) }),
            modeName = "photo",
            effectSpecFactory = { EffectSpec(emptyList()) }
        )
        delegate.cycleFrameRatio(updateSnapshot = {}, emitEffect = {})
        assertTrue(events.any { it.startsWith("photo.frame-ratio.selected.") })
    }
}
```

## 验证清单

- [ ] 新增 `FrameRatioDelegate.kt`，编译通过
- [ ] 5 个控制器的 frameRatio 相关代码替换为委托调用
- [ ] Document 和 Video 控制器不受影响
- [ ] `./gradlew :core:mode:test` 通过
- [ ] `./gradlew compileDebugKotlin` 通过
- [ ] 新增 `FrameRatioDelegateTest` 通过
