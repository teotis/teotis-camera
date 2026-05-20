# Effect Pipeline 实现设计

## 概述

将预览叠加、滤镜、边框、水印和输出后处理收束为统一的效果管线。基于渐进式桥接方案，新建 `core/effect` 模块定义类型安全的 EffectSpec，桥接到现有 PostProcessor 体系，扩展 PreviewOverlayView 支持 Canvas 叠加预览。

## 约束与决策

- **预览渲染**：Canvas 叠加（沿用现有 Bitmap+Canvas 路线，不引入 GL）
- **降级策略**：自动降级（pipeline 组装时查询设备能力，跳过不支持的效果）
- **兼容策略**：EffectSpec 桥接到 customTags（现有 PostProcessor 内部逻辑不变）
- **EffectSpec 归属**：新建 `core/effect` 模块

## 第一层：Effect Spec（core/effect）

### 模块结构

```
core/effect/
  src/main/kotlin/com/opencamera/core/effect/
    EffectSpec.kt
    EffectCapability.kt
    EffectBridge.kt
  build.gradle.kts
```

### EffectSpec.kt

定义统一的效果声明类型。ModePlugin 通过 EffectSpec 声明"我需要什么效果"，而不是散落在 customTags 字符串 map 中。

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkStyleSettings

enum class EffectTarget { PREVIEW, CAPTURE, BOTH }

sealed interface EffectEntry {
    val target: EffectTarget
}

data class FilterEffect(
    val profileId: String,
    val renderSpec: FilterRenderSpec?,
    override val target: EffectTarget = EffectTarget.BOTH
) : EffectEntry

data class WatermarkEffect(
    val templateId: String,
    val tokens: Map<String, String>,
    val style: WatermarkStyleSettings,
    override val target: EffectTarget = EffectTarget.BOTH
) : EffectEntry

data class FrameEffect(
    val ratio: FrameRatio,
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class PortraitEffect(
    val profileId: String,
    val renderPath: String,
    val beautyPreset: String,
    val beautyStrength: Float,
    val bokehEffect: String,
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class DocumentEffect(
    val autoCrop: Boolean,
    val contrastProfile: String?,
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class SelfieMirrorEffect(
    override val target: EffectTarget = EffectTarget.CAPTURE
) : EffectEntry

data class EffectSpec(
    val entries: List<EffectEntry>
) {
    companion object {
        val EMPTY = EffectSpec(emptyList())
    }

    inline fun <reified T : EffectEntry> find(): T? =
        entries.filterIsInstance<T>().firstOrNull()

    fun hasTarget(target: EffectTarget): Boolean =
        entries.any { it.target == target || it.target == EffectTarget.BOTH }
}
```

### EffectCapability.kt

自动降级框架。pipeline 组装时查询设备能力，跳过不支持的效果。

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.device.DeviceCapabilities

enum class EffectSupport { SUPPORTED, DEGRADED, UNSUPPORTED }

data class EffectCapabilityResult(
    val entry: EffectEntry,
    val support: EffectSupport,
    val reason: String? = null
)

data class CapabilityReport(
    val results: List<EffectCapabilityResult>,
    val effectiveSpec: EffectSpec
)

class EffectCapabilityResolver(
    private val deviceCapabilities: DeviceCapabilities
) {
    fun resolve(spec: EffectSpec): CapabilityReport {
        val results = spec.entries.map { resolveEntry(it) }
        val effectiveEntries = results
            .filter { it.support != EffectSupport.UNSUPPORTED }
            .map { it.entry }
        return CapabilityReport(
            results = results,
            effectiveSpec = EffectSpec(effectiveEntries)
        )
    }

    private fun resolveEntry(entry: EffectEntry): EffectCapabilityResult {
        return when (entry) {
            is FilterEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
            is WatermarkEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
            is FrameEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
            is PortraitEffect -> resolvePortrait(entry)
            is DocumentEffect -> resolveDocument(entry)
            is SelfieMirrorEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        }
    }

    private fun resolvePortrait(entry: PortraitEffect): EffectCapabilityResult {
        return if (deviceCapabilities.supportsPortraitDepthEffect) {
            EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        } else {
            val degraded = entry.copy(renderPath = "focus")
            EffectCapabilityResult(degraded, EffectSupport.DEGRADED,
                "Device does not support depth effect, using focus mode")
        }
    }

    private fun resolveDocument(entry: DocumentEffect): EffectCapabilityResult {
        return if (deviceCapabilities.supportsDocumentScanEnhancement) {
            EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        } else {
            val degraded = entry.copy(autoCrop = false, contrastProfile = null)
            EffectCapabilityResult(degraded, EffectSupport.DEGRADED,
                "Device does not support document scan enhancement")
        }
    }
}
```

### EffectBridge.kt

桥接层：将 EffectSpec 转换为现有 PostProcessor 能读取的 customTags 和 PostProcessSpec。

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.PostProcessSpec

object EffectBridge {

    fun toMetadataTags(spec: EffectSpec): Map<String, String> {
        val tags = mutableMapOf<String, String>()

        spec.find<FilterEffect>()?.let { effect ->
            tags["filterProfile"] = effect.profileId
            effect.renderSpec?.let { tags.putAll(it.toMetadataTags()) }
        }

        spec.find<WatermarkEffect>()?.let { effect ->
            tags["watermarkTemplate"] = effect.templateId
            tags.putAll(effect.tokens)
            tags["watermarkTextScale"] = effect.style.textScale.multiplier.toString()
            tags["watermarkTextOpacity"] = effect.style.textOpacity.alphaFraction.toString()
            tags["watermarkPosition"] = effect.style.textPlacement.name
            tags["watermarkFrameBackground"] = effect.style.frameBackground.name
        }

        spec.find<FrameEffect>()?.let { effect ->
            tags["frameRatio"] = effect.ratio.name
        }

        spec.find<PortraitEffect>()?.let { effect ->
            tags["mode"] = "portrait"
            tags["renderPath"] = effect.renderPath
            tags["portraitProfile"] = effect.profileId
            tags["portraitBeautyPreset"] = effect.beautyPreset
            tags["portraitBeautyStrength"] = effect.beautyStrength.toString()
            tags["portraitBokehEffect"] = effect.bokehEffect
        }

        spec.find<DocumentEffect>()?.let { effect ->
            tags["mode"] = "document"
            tags["autoCrop"] = effect.autoCrop.toString()
            effect.contrastProfile?.let { tags["contrastProfile"] = it }
        }

        spec.find<SelfieMirrorEffect>()?.let {
            tags["selfieMirrorApply"] = "true"
        }

        return tags
    }

    fun toPostProcessSpec(spec: EffectSpec): PostProcessSpec {
        val filter = spec.find<FilterEffect>()
        val watermark = spec.find<WatermarkEffect>()
        return PostProcessSpec(
            watermarkText = watermark?.let { buildWatermarkText(it) },
            exifOverrides = emptyMap(),
            algorithmProfile = filter?.profileId
        )
    }

    private fun buildWatermarkText(effect: WatermarkEffect): String {
        return effect.tokens.values.filter { it.isNotBlank() }.joinToString(" | ")
    }
}
```

## 第二层：Render Pipeline（预览渲染）

### 预览效果模型

定义在 `core/effect` 模块中：

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.CompositionGridMode

data class PreviewEffectRenderModel(
    val filterOverlay: FilterOverlaySpec?,
    val watermarkHint: WatermarkHintSpec?,
    val frameGuideline: FrameGuidelineSpec?,
    val compositionGrid: CompositionGridSpec?
)

data class FilterOverlaySpec(
    val tintColor: Int,
    val tintAlpha: Float,
    val vignetteStrength: Float,
    val warmthShift: Float
)

data class WatermarkHintSpec(
    val templateId: String,
    val placement: WatermarkTextPlacement,
    val previewText: String,
    val opacity: Float
)

data class FrameGuidelineSpec(
    val ratio: FrameRatio,
    val borderColor: Int,
    val borderAlpha: Float
)

data class CompositionGridSpec(
    val mode: CompositionGridMode,
    val isVisible: Boolean
)
```

### PreviewEffectAdapter

将 EffectSpec 转换为 PreviewEffectRenderModel：

```kotlin
package com.opencamera.core.effect

class PreviewEffectAdapter {
    fun adapt(spec: EffectSpec): PreviewEffectRenderModel {
        val filter = spec.find<FilterEffect>()
        val watermark = spec.find<WatermarkEffect>()
        val frame = spec.find<FrameEffect>()

        return PreviewEffectRenderModel(
            filterOverlay = filter?.let { buildFilterOverlay(it) },
            watermarkHint = watermark?.let { buildWatermarkHint(it) },
            frameGuideline = frame?.let { buildFrameGuideline(it) },
            compositionGrid = null
        )
    }

    private fun buildFilterOverlay(effect: FilterEffect): FilterOverlaySpec {
        val spec = effect.renderSpec
        return FilterOverlaySpec(
            tintColor = resolveTintColor(spec),
            tintAlpha = resolveTintAlpha(spec),
            vignetteStrength = spec?.vignetteStrength ?: 0f,
            warmthShift = spec?.warmthShift ?: 0f
        )
    }

    private fun buildWatermarkHint(effect: WatermarkEffect): WatermarkHintSpec {
        return WatermarkHintSpec(
            templateId = effect.templateId,
            placement = effect.style.textPlacement,
            previewText = effect.tokens["watermarkModel"] ?: "Watermark",
            opacity = effect.style.textOpacity * 0.6f
        )
    }

    private fun buildFrameGuideline(effect: FrameEffect): FrameGuidelineSpec {
        return FrameGuidelineSpec(
            ratio = effect.ratio,
            borderColor = android.graphics.Color.WHITE,
            borderAlpha = 0.4f
        )
    }

    private fun resolveTintColor(spec: FilterRenderSpec?): Int {
        // 根据 warmthShift 和 tintShift 计算叠加色
        val warmth = spec?.warmthShift ?: 0f
        val tint = spec?.tintShift ?: 0f
        val r = (128 + warmth * 60).toInt().coerceIn(0, 255)
        val g = 128
        val b = (128 - warmth * 60 + tint * 60).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(255, r, g, b)
    }

    private fun resolveTintAlpha(spec: FilterRenderSpec?): Float {
        val saturation = spec?.saturation ?: 1f
        val contrast = spec?.contrast ?: 1f
        return ((2f - saturation) * 0.15f + (contrast - 1f) * 0.1f)
            .coerceIn(0f, 0.4f)
    }
}
```

### PreviewOverlayView 扩展

扩展现有 PreviewOverlayView 支持效果叠加绘制。新增绘制方法：

- `drawFilterOverlay(canvas, spec)` - 半透明颜色层 + 暗角渐变
- `drawFrameGuideline(canvas, spec)` - 裁剪区域边框线
- `drawWatermarkHint(canvas, spec)` - 简化水印文本（低透明度）

## 第三层：Output Pipeline（输出后处理）

### 现有 PostProcessor 不变

现有 7 个 PostProcessor（PhotoWatermarkPostProcessor、PhotoAlgorithmPostProcessor、PortraitRenderPostProcessor、PhotoFrameRatioPostProcessor、PhotoSelfieMirrorPostProcessor、DocumentAutoCropPostProcessor、VideoWatermarkSubtitlePostProcessor）内部逻辑不变。它们继续从 customTags 读取数据，由 EffectBridge 负责转换。

### PostProcessor 链组装顺序不变

```
1. MultiFrameMergePlaceholderPostProcessor
2. DocumentAutoCropPostProcessor
3. PhotoFrameRatioPostProcessor
4. PortraitRenderPostProcessor
5. PhotoAlgorithmPostProcessor
6. PhotoWatermarkPostProcessor
7. PhotoSelfieMirrorPostProcessor
8. VideoWatermarkSubtitlePostProcessor
9. PipelineMetadataPostProcessor
```

## 数据流

### 拍摄流程

```
ModePlugin 构建 EffectSpec
        ↓
EffectCapabilityResolver.resolve(spec) → CapabilityReport
        ↓
effectiveSpec 存入 SessionState.activeEffectSpec
        ↓
    ┌───┴───┐
    ↓       ↓
PreviewPipeline   OutputPipeline
    ↓               ↓
PreviewEffectAdapter  EffectBridge.toMetadataTags(effectiveSpec)
    ↓               ↓
PreviewEffectRenderModel  customTags + PostProcessSpec
    ↓               ↓
PreviewOverlayView   CompositeMediaPostProcessor 链（不变）
```

### ModePlugin 改造模式

每个 ModePlugin 的 `handle(ModeIntent.ShutterPressed)` 改为：

1. 构建 EffectSpec（替代散落的 customTags 设置）
2. 调用 EffectBridge 转换
3. 传入 CaptureStrategy

### EffectSpec 传递路径

ModePlugin 通过两个渠道将 EffectSpec 传递到 SessionState：

1. **实时预览**：ModePlugin 在状态更新时（滤镜切换、水印选择等）通过 `ModeContext.eventSink` 发送 `EffectSpecUpdated(spec)` 事件，Session 收到后更新 `SessionState.activeEffectSpec`
2. **拍摄时**：ModePlugin 在 `ModeSignal.SubmitCapture` 中携带 EffectSpec（扩展 CaptureStrategy 或新增字段），Session 在收到信号时同步更新 `activeEffectSpec`

这样 PreviewOverlayView 可以实时响应效果变化（预览），而 PostProcessor 链在拍摄时获取最新效果声明（成片）。

## 改动范围

| 文件/模块 | 改动类型 | 说明 |
|-----------|---------|------|
| `core/effect/` (新模块) | 新建 | EffectSpec、EffectCapability、EffectBridge、PreviewEffectAdapter、预览模型 |
| `settings.gradle.kts` | 修改 | 注册 core/effect 模块 |
| `core/effect/build.gradle.kts` | 新建 | 依赖 core/settings、core/media、core/device |
| `SessionState` | 修改 | 新增 activeEffectSpec、effectCapabilityReport 字段 |
| `DefaultCameraSession` | 修改 | 在 mode 切换和设置变更时构建/更新 EffectSpec |
| `PreviewOverlayView` | 修改 | 新增滤镜叠加、水印提示、边框提示的绘制逻辑 |
| `PreviewOverlayRenderModel` | 修改 | 新增 effectModel 字段 |
| `PhotoModePlugin` | 修改 | 用 EffectSpec 替代散落的 customTags 构建 |
| `PortraitModePlugin` | 修改 | 同上 |
| `DocumentModePlugin` | 修改 | 同上 |
| `HumanisticModePlugin` | 修改 | 同上 |
| `NightModePlugin` | 修改 | 同上 |
| `VideoModePlugin` | 修改 | 同上 |
| `ProModePlugin` | 修改 | 同上 |
| `AppContainer` | 修改 | 创建 EffectCapabilityResolver、PreviewEffectAdapter 实例 |

## 验收标准

1. ModePlugin 通过 EffectSpec 声明效果意图，不再直接操作 customTags map
2. 预览效果（滤镜叠加、水印提示、边框提示）通过 PreviewOverlayView Canvas 绘制
3. 成片后处理通过现有 PostProcessor 链完成，EffectBridge 保证数据一致
4. 设备能力不足时自动降级（如人像 depth→focus），降级原因记录在 CapabilityReport
5. 输出失败不污染 session 主状态（现有行为不变）
6. 新效果接入时只需新增 EffectEntry 子类 + 对应的 Bridge/Adapter/PostProcessor

## 兼容策略

1. 第一步：新建 core/effect 模块，定义 EffectSpec 类型
2. 第二步：实现 EffectBridge，验证桥接到 customTags 的正确性
3. 第三步：改造 ModePlugin 为 EffectSpec 驱动
4. 第四步：实现 PreviewEffectAdapter 和 PreviewOverlayView 扩展
5. 第五步：实现 EffectCapabilityResolver 自动降级
