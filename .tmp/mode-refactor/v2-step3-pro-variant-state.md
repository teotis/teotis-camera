# Step 3: `ProVariantState` 独立状态类

## 目标

消除 3 个模式中完全同形的 Pro 变体切换和手动控制辅助方法。

## 问题

以下 3 个控制器的 `toggleProVariant()` 及 6 个辅助方法实现完全相同（~30 行）：

- PortraitModeController
- NightModeController
- HumanisticModeController

Pro 模式本身始终是 Pro，不需要切换。Photo、Document、Video 不使用 Pro 变体。

## 实现

### 新增文件

**路径**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ProVariantState.kt`

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.toMetadataTags

/**
 * Pro 变体状态管理。
 *
 * 封装 proVariantEnabled 状态和手动控制辅助方法。
 * 被 Portrait、Night、Humanistic 3 个模式使用。
 *
 * Pro 模式本身始终是 Pro，不需要切换，不使用此类。
 * Photo、Document、Video 不使用 Pro 变体，不使用此类。
 *
 * 使用方式：在控制器中持有一个实例，在 toggleProVariant/handleProAction 中调用。
 * 不改变控制器的类继承结构。
 */
class ProVariantState(
    private val context: ModeContext
) {
    var proVariantEnabled: Boolean = false
        private set

    /**
     * 切换 Pro 变体状态并返回事件标签和提示信号。
     *
     * @param modeName 模式名称（用于事件前缀和提示文案）
     * @return Triple(新状态, 事件标签如 "entered"/"exited", 提示信号)
     */
    fun toggle(modeName: String): Triple<Boolean, String, ModeSignal> {
        proVariantEnabled = !proVariantEnabled
        val stateLabel = if (proVariantEnabled) "entered" else "exited"
        val hintPrefix = modeName.replaceFirstChar { it.titlecase() }
        val hint = if (proVariantEnabled) {
            if (manualControlsEnabled()) "${hintPrefix} Pro on"
            else "${hintPrefix} Pro assist on"
        } else {
            "${hintPrefix} Pro off"
        }
        return Triple(proVariantEnabled, stateLabel, ModeSignal.ShowHint(hint))
    }

    /** 构建 Pro 变体切换事件字符串，如 "portrait.pro-variant.entered" */
    fun eventString(modeName: String): String {
        val stateLabel = if (proVariantEnabled) "entered" else "exited"
        return "${modeName}.pro-variant.$stateLabel"
    }

    /** Pro 变体激活时的 headline */
    fun activeHeadline(baseName: String): String {
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
        context.runtimeState().deviceCapabilities.supportsAppliedManualControls

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
}
```

### 修改 3 个控制器

以 PortraitModeController 为例，将：

```kotlin
private var proVariantEnabled = false
// ...
private fun manualControlsEnabled(): Boolean =
    runtimeState().deviceCapabilities.supportsAppliedManualControls
private fun currentManualDraft() = context.settingsSnapshot.catalog.manualCaptureDraft
private fun currentManualDraftOrNull() = currentManualDraft().takeIf { proVariantEnabled }
private fun resolvedControlMode(): String = if (manualControlsEnabled()) "manual" else "assisted"
private fun manualDraftState(): String = if (manualControlsEnabled()) "metadata-draft" else "unsupported"
private fun resolvedAlgorithmProfile(base: String): String {
    return if (!proVariantEnabled) base
    else if (manualControlsEnabled()) "${base}-pro"
    else "${base}-pro-assist"
}
private suspend fun toggleProVariant(): ModeSignal {
    proVariantEnabled = !proVariantEnabled
    context.eventSink("portrait.pro-variant.${if (proVariantEnabled) "entered" else "exited"}")
    mutableSnapshot.value = buildSnapshot(headline = if (proVariantEnabled) { ... })
    return ModeSignal.ShowHint(...)
}
```

改为：

```kotlin
private val proVariantState = ProVariantState(context)
// ...
// 所有 proVariantEnabled 引用改为 proVariantState.proVariantEnabled
// 所有 manualControlsEnabled() 引用改为 proVariantState.manualControlsEnabled()
// 所有 currentManualDraft() 引用改为 proVariantState.currentManualDraft()
// 所有 currentManualDraftOrNull() 引用改为 proVariantState.currentManualDraftOrNull()
// 所有 resolvedAlgorithmProfile(x) 引用改为 proVariantState.resolvedAlgorithmProfile(x)
// 所有 resolvedControlMode() 引用改为 proVariantState.resolvedControlMode()
// 所有 manualDraftState() 引用改为 proVariantState.manualDraftState()

// toggleProVariant 简化为：
private suspend fun toggleProVariant(): ModeSignal {
    val (newState, stateLabel, signal) = proVariantState.toggle("portrait")
    context.eventSink("portrait.pro-variant.$stateLabel")
    mutableSnapshot.value = buildSnapshot(
        headline = proVariantState.activeHeadline("Portrait"),
        detail = styleSummary(currentStyle())
    )
    return signal
}

// buildSnapshot 中的 proActionLabel 改为：
proActionLabel = proVariantState.proActionLabel()

// buildSnapshot 中的 isProVariantActive 改为：
isProVariantActive = proVariantState.proVariantEnabled
```

同样修改 Night 和 Humanistic 控制器。

## 测试

**`ProVariantStateTest.kt`**：

```kotlin
package com.opencamera.core.mode

import org.junit.Assert.*
import org.junit.Test

class ProVariantStateTest {

    private fun createState() = ProVariantState(context = ModeContext())

    @Test
    fun `initial state is disabled`() {
        val state = createState()
        assertFalse(state.proVariantEnabled)
    }

    @Test
    fun `toggle enables`() {
        val state = createState()
        val (enabled, label, signal) = state.toggle("portrait")
        assertTrue(enabled)
        assertEquals("entered", label)
        assert(signal is ModeSignal.ShowHint)
    }

    @Test
    fun `toggle disables after enable`() {
        val state = createState()
        state.toggle("portrait")
        val (enabled, label, _) = state.toggle("portrait")
        assertFalse(enabled)
        assertEquals("exited", label)
    }

    @Test
    fun `event string format`() {
        val state = createState()
        state.toggle("night")
        assertEquals("night.pro-variant.entered", state.eventString("night"))
    }

    @Test
    fun `proActionLabel reflects state`() {
        val state = createState()
        // 初始状态：disabled
        val labelBefore = state.proActionLabel()
        // toggle 后
        state.toggle("portrait")
        val labelAfter = state.proActionLabel()
        assertNotEquals(labelBefore, labelAfter)
    }

    @Test
    fun `buildMetadataTags empty when disabled`() {
        val state = createState()
        assertTrue(state.buildMetadataTags().isEmpty())
    }

    @Test
    fun `resolvedAlgorithmProfile returns base when disabled`() {
        val state = createState()
        assertEquals("photo-vivid", state.resolvedAlgorithmProfile("photo-vivid"))
    }
}
```

## 验证清单

- [ ] 新增 `ProVariantState.kt`，编译通过
- [ ] 3 个控制器的 ProVariant 相关代码替换为状态类调用
- [ ] Photo、Pro、Document、Video 控制器不受影响
- [ ] `./gradlew :core:mode:test` 通过
- [ ] `./gradlew compileDebugKotlin` 通过
- [ ] 新增 `ProVariantStateTest` 通过
