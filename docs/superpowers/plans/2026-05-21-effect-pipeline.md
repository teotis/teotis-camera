# Effect Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一效果管线：EffectSpec 类型声明 + PreviewOverlayView Canvas 预览 + 自动降级，桥接到现有 PostProcessor 链。

**Architecture:** 新建 `core/effect` 模块定义 EffectSpec sealed class，ModePlugin 通过 EffectSpec 声明效果意图，EffectBridge 桥接到现有 customTags，PreviewEffectAdapter 驱动 PreviewOverlayView Canvas 叠加，EffectCapabilityResolver 自动降级。

**Tech Stack:** Kotlin, Android Canvas API, JUnit 5, kotlinx-coroutines-test

---

## File Structure

### New files
| File | Responsibility |
|------|---------------|
| `core/effect/build.gradle.kts` | 模块构建配置 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt` | 效果声明类型：EffectEntry sealed hierarchy, EffectSpec |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt` | 能力查询与自动降级：EffectCapabilityResolver |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt` | 桥接层：EffectSpec → customTags + PostProcessSpec |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt` | 预览效果模型：PreviewEffectRenderModel, FilterOverlaySpec 等 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` | EffectSpec → PreviewEffectRenderModel 转换 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectSpecTest.kt` | EffectSpec 单元测试 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt` | EffectBridge 单元测试 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectCapabilityResolverTest.kt` | 降级逻辑单元测试 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt` | 预览适配器单元测试 |

### Modified files
| File | Change |
|------|--------|
| `settings.gradle.kts` | 添加 `:core:effect` |
| `core/session/src/main/kotlin/.../SessionContracts.kt` | SessionState 新增 activeEffectSpec 字段 |
| `app/src/main/java/.../PreviewOverlayView.kt` | 新增滤镜叠加、水印提示、边框提示绘制 |
| `app/src/main/java/.../SessionUiRenderModel.kt` | PreviewOverlayRenderModel 新增 effectModel 字段 |
| `feature/mode-photo/.../PhotoModePlugin.kt` | 用 EffectSpec 替代散落的 customTags |
| `feature/mode-portrait/.../PortraitModePlugin.kt` | 同上 |
| `feature/mode-document/.../DocumentModePlugin.kt` | 同上 |
| `feature/mode-humanistic/.../HumanisticModePlugin.kt` | 同上 |
| `feature/mode-night/.../NightModePlugin.kt` | 同上 |
| `feature/mode-video/.../VideoModePlugin.kt` | 同上 |
| `feature/mode-pro/.../ProModePlugin.kt` | 同上 |
| `app/src/main/java/.../AppContainer.kt` | 创建 EffectCapabilityResolver、PreviewEffectAdapter |
| `app/src/main/java/.../MainActivity.kt` | 将 effectModel 传递给 PreviewOverlayView |

---

## Task 1: 创建 core/effect 模块

**Files:**
- Create: `core/effect/build.gradle.kts`
- Create: `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: 创建 build.gradle.kts**

```kotlin
// core/effect/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:media"))
    implementation(project(":core:device"))
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 2: 注册模块**

在 `settings.gradle.kts` 的 `include(":core:session")` 后添加：

```kotlin
include(":core:effect")
```

- [ ] **Step 3: 创建 EffectSpec.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
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

- [ ] **Step 4: 创建 EffectSpecTest.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EffectSpecTest {

    @Test
    fun `EMPTY has no entries`() {
        assertEquals(0, EffectSpec.EMPTY.entries.size)
    }

    @Test
    fun `find returns matching entry`() {
        val filter = FilterEffect("photo-vivid", FilterRenderSpec(), EffectTarget.BOTH)
        val spec = EffectSpec(listOf(filter))
        assertNotNull(spec.find<FilterEffect>())
        assertEquals("photo-vivid", spec.find<FilterEffect>()?.profileId)
    }

    @Test
    fun `find returns null when no match`() {
        val spec = EffectSpec(listOf(FilterEffect("x", null)))
        assertNull(spec.find<WatermarkEffect>())
    }

    @Test
    fun `hasTarget returns true when entry matches`() {
        val spec = EffectSpec(listOf(FilterEffect("x", null, EffectTarget.BOTH)))
        assertTrue(spec.hasTarget(EffectTarget.PREVIEW))
        assertTrue(spec.hasTarget(EffectTarget.CAPTURE))
    }

    @Test
    fun `hasTarget returns false when no entry matches`() {
        val spec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_4_3, EffectTarget.CAPTURE)))
        assertFalse(spec.hasTarget(EffectTarget.PREVIEW))
        assertTrue(spec.hasTarget(EffectTarget.CAPTURE))
    }

    @Test
    fun `multiple entries of different types`() {
        val spec = EffectSpec(listOf(
            FilterEffect("f1", null),
            WatermarkEffect("w1", emptyMap(), WatermarkStyleSettings()),
            FrameEffect(FrameRatio.RATIO_16_9)
        ))
        assertNotNull(spec.find<FilterEffect>())
        assertNotNull(spec.find<WatermarkEffect>())
        assertNotNull(spec.find<FrameEffect>())
        assertNull(spec.find<PortraitEffect>())
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `./gradlew :core:effect:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/effect/ settings.gradle.kts
git commit -m "feat: 创建 core/effect 模块，定义 EffectSpec 类型体系"
```

---

## Task 2: EffectBridge 桥接层

**Files:**
- Create: `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
- Create: `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt`

- [ ] **Step 1: 创建 EffectBridgeTest.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EffectBridgeTest {

    @Test
    fun `toMetadataTags with empty spec returns empty map`() {
        val tags = EffectBridge.toMetadataTags(EffectSpec.EMPTY)
        assertEquals(0, tags.size)
    }

    @Test
    fun `toMetadataTags maps FilterEffect`() {
        val spec = EffectSpec(listOf(
            FilterEffect("photo-vivid", FilterRenderSpec(contrast = 1.08f, saturation = 1.14f))
        ))
        val tags = EffectBridge.toMetadataTags(spec)
        assertEquals("photo-vivid", tags["filterProfile"])
        assertEquals("1.08", tags["filterSpec.contrast"])
        assertEquals("1.14", tags["filterSpec.saturation"])
    }

    @Test
    fun `toMetadataTags maps WatermarkEffect`() {
        val style = WatermarkStyleSettings(
            textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
            textScale = WatermarkTextScale.NORMAL,
            textOpacity = WatermarkTextOpacity.SOFT,
            frameBackground = WatermarkFrameBackground.DARK
        )
        val spec = EffectSpec(listOf(
            WatermarkEffect("classic-overlay", mapOf("watermarkModel" to "Pixel 9"), style)
        ))
        val tags = EffectBridge.toMetadataTags(spec)
        assertEquals("classic-overlay", tags["watermarkTemplate"])
        assertEquals("Pixel 9", tags["watermarkModel"])
        assertEquals("1.0", tags["watermarkTextScale"])
        assertEquals("0.8", tags["watermarkTextOpacity"])
        assertEquals("BOTTOM_RIGHT", tags["watermarkPosition"])
        assertEquals("DARK", tags["watermarkFrameBackground"])
    }

    @Test
    fun `toMetadataTags maps FrameEffect`() {
        val spec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_16_9)))
        val tags = EffectBridge.toMetadataTags(spec)
        assertEquals("16:9", tags["frameRatio"])
    }

    @Test
    fun `toMetadataTags maps PortraitEffect`() {
        val spec = EffectSpec(listOf(
            PortraitEffect("native", "depth", "authentic", 0.35f, "natural")
        ))
        val tags = EffectBridge.toMetadataTags(spec)
        assertEquals("portrait", tags["mode"])
        assertEquals("depth", tags["renderPath"])
        assertEquals("native", tags["portraitProfile"])
        assertEquals("authentic", tags["portraitBeautyPreset"])
        assertEquals("0.35", tags["portraitBeautyStrength"])
        assertEquals("natural", tags["portraitBokehEffect"])
    }

    @Test
    fun `toMetadataTags maps DocumentEffect`() {
        val spec = EffectSpec(listOf(DocumentEffect(true, "receipt")))
        val tags = EffectBridge.toMetadataTags(spec)
        assertEquals("document", tags["mode"])
        assertEquals("true", tags["autoCrop"])
        assertEquals("receipt", tags["contrastProfile"])
    }

    @Test
    fun `toMetadataTags maps SelfieMirrorEffect`() {
        val spec = EffectSpec(listOf(SelfieMirrorEffect()))
        val tags = EffectBridge.toMetadataTags(spec)
        assertEquals("true", tags["selfieMirrorApply"])
    }

    @Test
    fun `toPostProcessSpec with filter and watermark`() {
        val spec = EffectSpec(listOf(
            FilterEffect("photo-vivid", null),
            WatermarkEffect("classic-overlay", mapOf("watermarkModel" to "Pixel 9"), WatermarkStyleSettings())
        ))
        val pps = EffectBridge.toPostProcessSpec(spec)
        assertEquals("photo-vivid", pps.algorithmProfile)
        assertEquals("Pixel 9", pps.watermarkText)
    }

    @Test
    fun `toPostProcessSpec with empty spec`() {
        val pps = EffectBridge.toPostProcessSpec(EffectSpec.EMPTY)
        assertNull(pps.watermarkText)
        assertNull(pps.algorithmProfile)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:effect:test --tests "*.EffectBridgeTest"`
Expected: FAIL (EffectBridge not found)

- [ ] **Step 3: 创建 EffectBridge.kt**

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
            tags["frameRatio"] = effect.ratio.tagValue
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

- [ ] **Step 4: 运行测试**

Run: `./gradlew :core:effect:test --tests "*.EffectBridgeTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt
git commit -m "feat: 实现 EffectBridge 桥接层，EffectSpec → customTags 转换"
```

---

## Task 3: EffectCapabilityResolver 自动降级

**Files:**
- Create: `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt`
- Create: `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectCapabilityResolverTest.kt`

- [ ] **Step 1: 创建 EffectCapabilityResolverTest.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.device.DeviceCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EffectCapabilityResolverTest {

    private val fullCapabilityDevice = DeviceCapabilities.DEFAULT.copy(
        supportsPortraitDepthEffect = true,
        supportsDocumentScanEnhancement = true
    )

    private val limitedCapabilityDevice = DeviceCapabilities.DEFAULT.copy(
        supportsPortraitDepthEffect = false,
        supportsDocumentScanEnhancement = false
    )

    @Test
    fun `empty spec returns empty report`() {
        val resolver = EffectCapabilityResolver(fullCapabilityDevice)
        val report = resolver.resolve(EffectSpec.EMPTY)
        assertEquals(0, report.results.size)
        assertEquals(EffectSpec.EMPTY, report.effectiveSpec)
    }

    @Test
    fun `filter effect is always supported`() {
        val resolver = EffectCapabilityResolver(limitedCapabilityDevice)
        val spec = EffectSpec(listOf(FilterEffect("photo-vivid", null)))
        val report = resolver.resolve(spec)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        assertEquals(1, report.effectiveSpec.entries.size)
    }

    @Test
    fun `watermark effect is always supported`() {
        val resolver = EffectCapabilityResolver(limitedCapabilityDevice)
        val spec = EffectSpec(listOf(
            WatermarkEffect("classic-overlay", emptyMap(), com.opencamera.core.settings.WatermarkStyleSettings())
        ))
        val report = resolver.resolve(spec)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
    }

    @Test
    fun `portrait effect degraded when depth not supported`() {
        val resolver = EffectCapabilityResolver(limitedCapabilityDevice)
        val spec = EffectSpec(listOf(
            PortraitEffect("native", "depth", "authentic", 0.35f, "natural")
        ))
        val report = resolver.resolve(spec)
        assertEquals(EffectSupport.DEGRADED, report.results[0].support)
        val degraded = report.effectiveSpec.find<PortraitEffect>()
        assertEquals("focus", degraded?.renderPath)
    }

    @Test
    fun `portrait effect supported when depth supported`() {
        val resolver = EffectCapabilityResolver(fullCapabilityDevice)
        val spec = EffectSpec(listOf(
            PortraitEffect("native", "depth", "authentic", 0.35f, "natural")
        ))
        val report = resolver.resolve(spec)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        val effective = report.effectiveSpec.find<PortraitEffect>()
        assertEquals("depth", effective?.renderPath)
    }

    @Test
    fun `document effect degraded when scan not supported`() {
        val resolver = EffectCapabilityResolver(limitedCapabilityDevice)
        val spec = EffectSpec(listOf(DocumentEffect(true, "receipt")))
        val report = resolver.resolve(spec)
        assertEquals(EffectSupport.DEGRADED, report.results[0].support)
        val degraded = report.effectiveSpec.find<DocumentEffect>()
        assertEquals(false, degraded?.autoCrop)
        assertNull(degraded?.contrastProfile)
    }

    @Test
    fun `mixed spec resolves each entry independently`() {
        val resolver = EffectCapabilityResolver(limitedCapabilityDevice)
        val spec = EffectSpec(listOf(
            FilterEffect("photo-vivid", null),
            PortraitEffect("native", "depth", "authentic", 0.35f, "natural"),
            DocumentEffect(true, "receipt")
        ))
        val report = resolver.resolve(spec)
        assertEquals(3, report.results.size)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        assertEquals(EffectSupport.DEGRADED, report.results[1].support)
        assertEquals(EffectSupport.DEGRADED, report.results[2].support)
        assertEquals(3, report.effectiveSpec.entries.size)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:effect:test --tests "*.EffectCapabilityResolverTest"`
Expected: FAIL

- [ ] **Step 3: 创建 EffectCapability.kt**

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

- [ ] **Step 4: 运行测试**

Run: `./gradlew :core:effect:test --tests "*.EffectCapabilityResolverTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt core/effect/src/test/kotlin/com/opencamera/core/effect/EffectCapabilityResolverTest.kt
git commit -m "feat: 实现 EffectCapabilityResolver 自动降级框架"
```

---

## Task 4: PreviewEffectModel 和 PreviewEffectAdapter

**Files:**
- Create: `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
- Create: `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
- Create: `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`

- [ ] **Step 1: 创建 PreviewEffectModel.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.WatermarkTextPlacement

data class PreviewEffectRenderModel(
    val filterOverlay: FilterOverlaySpec?,
    val watermarkHint: WatermarkHintSpec?,
    val frameGuideline: FrameGuidelineSpec?,
    val compositionGrid: CompositionGridSpec?
) {
    companion object {
        val EMPTY = PreviewEffectRenderModel(null, null, null, null)
    }
}

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

- [ ] **Step 2: 创建 PreviewEffectAdapterTest.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewEffectAdapterTest {

    private val adapter = PreviewEffectAdapter()

    @Test
    fun `empty spec returns empty model`() {
        val model = adapter.adapt(EffectSpec.EMPTY)
        assertNull(model.filterOverlay)
        assertNull(model.watermarkHint)
        assertNull(model.frameGuideline)
    }

    @Test
    fun `filter effect produces overlay spec`() {
        val spec = EffectSpec(listOf(
            FilterEffect("photo-vivid", FilterRenderSpec(warmthShift = 5, contrast = 1.1f))
        ))
        val model = adapter.adapt(spec)
        assertNotNull(model.filterOverlay)
        assertEquals(5f, model.filterOverlay!!.warmthShift)
    }

    @Test
    fun `watermark effect produces hint spec`() {
        val style = WatermarkStyleSettings(textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT)
        val spec = EffectSpec(listOf(
            WatermarkEffect("classic-overlay", mapOf("watermarkModel" to "Pixel 9"), style)
        ))
        val model = adapter.adapt(spec)
        assertNotNull(model.watermarkHint)
        assertEquals("classic-overlay", model.watermarkHint!!.templateId)
        assertEquals("Pixel 9", model.watermarkHint!!.previewText)
        assertEquals(WatermarkTextPlacement.BOTTOM_RIGHT, model.watermarkHint!!.placement)
    }

    @Test
    fun `frame effect produces guideline spec`() {
        val spec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_16_9)))
        val model = adapter.adapt(spec)
        assertNotNull(model.frameGuideline)
        assertEquals(FrameRatio.RATIO_16_9, model.frameGuideline!!.ratio)
    }

    @Test
    fun `multiple effects produce combined model`() {
        val spec = EffectSpec(listOf(
            FilterEffect("photo-vivid", FilterRenderSpec()),
            WatermarkEffect("w1", emptyMap(), WatermarkStyleSettings()),
            FrameEffect(FrameRatio.RATIO_4_3)
        ))
        val model = adapter.adapt(spec)
        assertNotNull(model.filterOverlay)
        assertNotNull(model.watermarkHint)
        assertNotNull(model.frameGuideline)
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :core:effect:test --tests "*.PreviewEffectAdapterTest"`
Expected: FAIL

- [ ] **Step 4: 创建 PreviewEffectAdapter.kt**

```kotlin
package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec

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
            warmthShift = (spec?.warmthShift ?: 0).toFloat()
        )
    }

    private fun buildWatermarkHint(effect: WatermarkEffect): WatermarkHintSpec {
        return WatermarkHintSpec(
            templateId = effect.templateId,
            placement = effect.style.textPlacement,
            previewText = effect.tokens["watermarkModel"] ?: "Watermark",
            opacity = effect.style.textOpacity.alphaFraction * 0.6f
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
        val warmth = (spec?.warmthShift ?: 0).toFloat()
        val tint = (spec?.tintShift ?: 0).toFloat()
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

- [ ] **Step 5: 运行测试**

Run: `./gradlew :core:effect:test --tests "*.PreviewEffectAdapterTest"`
Expected: PASS

- [ ] **Step 6: 运行全部 core/effect 测试**

Run: `./gradlew :core:effect:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt
git commit -m "feat: 实现 PreviewEffectModel 和 PreviewEffectAdapter"
```

---

## Task 5: SessionState 新增 activeEffectSpec 字段

**Files:**
- Modify: `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt:79-96`

- [ ] **Step 1: 添加 import 和字段**

在 `SessionContracts.kt` 顶部添加 import：

```kotlin
import com.opencamera.core.effect.EffectSpec
```

在 `SessionState` data class 中，`presentation` 字段之前添加：

```kotlin
    val activeEffectSpec: EffectSpec = EffectSpec.EMPTY,
```

- [ ] **Step 2: 运行 session 模块测试**

Run: `./gradlew :core:session:test`
Expected: PASS（默认值保证向后兼容）

- [ ] **Step 3: Commit**

```bash
git add core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt
git commit -m "feat: SessionState 新增 activeEffectSpec 字段"
```

---

## Task 6: PreviewOverlayView 扩展效果绘制

**Files:**
- Modify: `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- Modify: `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` (PreviewOverlayRenderModel)

- [ ] **Step 1: 扩展 PreviewOverlayRenderModel**

在 `SessionUiRenderModel.kt` 中找到 `PreviewOverlayRenderModel` data class，添加字段：

```kotlin
import com.opencamera.core.effect.PreviewEffectRenderModel
```

```kotlin
data class PreviewOverlayRenderModel(
    val gridMode: CompositionGridMode,
    val isGridVisible: Boolean,
    val countdownLabel: String?,
    val isCountdownVisible: Boolean,
    val effectModel: PreviewEffectRenderModel? = null
) {
    val isVisible: Boolean
        get() = isGridVisible || isCountdownVisible || effectModel != null
}
```

注意：需要检查现有的 `isVisible` 逻辑，确保新字段不影响原有行为。当前 `isVisible` 可能是在外部计算的，需要确认后调整。

- [ ] **Step 2: 扩展 PreviewOverlayView**

在 `PreviewOverlayView.kt` 中添加新的 Paint 对象和绘制方法：

```kotlin
import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.FrameGuidelineSpec
import com.opencamera.core.effect.WatermarkHintSpec
```

添加新的 Paint：

```kotlin
    private val filterOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val frameGuidelinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val watermarkHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
```

在 `onDraw` 中添加效果绘制调用：

```kotlin
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderModel.isGridVisible) {
            drawGrid(canvas, renderModel.gridMode)
        }
        renderModel.effectModel?.filterOverlay?.let { drawFilterOverlay(canvas, it) }
        renderModel.effectModel?.frameGuideline?.let { drawFrameGuideline(canvas, it) }
        renderModel.effectModel?.watermarkHint?.let { drawWatermarkHint(canvas, it) }
        if (renderModel.isCountdownVisible) {
            drawCountdown(canvas, renderModel.countdownLabel.orEmpty())
        }
    }
```

添加绘制方法：

```kotlin
    private fun drawFilterOverlay(canvas: Canvas, spec: FilterOverlaySpec) {
        if (spec.tintAlpha <= 0f) return
        filterOverlayPaint.color = spec.tintColor
        filterOverlayPaint.alpha = (spec.tintAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), filterOverlayPaint)

        if (spec.vignetteStrength > 0f) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.7f
            vignettePaint.shader = android.graphics.RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.argb((spec.vignetteStrength * 180).toInt(), 0, 0, 0)),
                floatArrayOf(0.4f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
            vignettePaint.shader = null
        }
    }

    private fun drawFrameGuideline(canvas: Canvas, spec: FrameGuidelineSpec) {
        frameGuidelinePaint.color = spec.borderColor
        frameGuidelinePaint.alpha = (spec.borderAlpha * 255).toInt().coerceIn(0, 255)
        val ratio = spec.ratio.width.toFloat() / spec.ratio.height.toFloat()
        val viewRatio = width.toFloat() / height.toFloat()
        val rect = if (ratio > viewRatio) {
            val w = width.toFloat()
            val h = w / ratio
            val top = (height - h) / 2f
            RectF(0f, top, w, top + h)
        } else {
            val h = height.toFloat()
            val w = h * ratio
            val left = (width - w) / 2f
            RectF(left, 0f, left + w, h)
        }
        canvas.drawRect(rect, frameGuidelinePaint)
    }

    private fun drawWatermarkHint(canvas: Canvas, spec: WatermarkHintSpec) {
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val padding = 16f * density
        val x: Float
        val y: Float
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        when (spec.placement) {
            com.opencamera.core.settings.WatermarkTextPlacement.TOP_LEFT -> {
                x = padding
                y = padding + watermarkHintPaint.textSize
            }
            com.opencamera.core.settings.WatermarkTextPlacement.TOP_RIGHT -> {
                x = width - padding
                y = padding + watermarkHintPaint.textSize
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            com.opencamera.core.settings.WatermarkTextPlacement.BOTTOM_LEFT -> {
                x = padding
                y = height - padding
            }
            com.opencamera.core.settings.WatermarkTextPlacement.BOTTOM_RIGHT -> {
                x = width - padding
                y = height - padding
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            com.opencamera.core.settings.WatermarkTextPlacement.BOTTOM_CENTER -> {
                x = width / 2f
                y = height - padding
                watermarkHintPaint.textAlign = Paint.Align.CENTER
            }
        }
        canvas.drawText(spec.previewText, x, y, watermarkHintPaint)
    }
```

- [ ] **Step 3: 运行 app 模块测试**

Run: `./gradlew :app:test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/opencamera/app/PreviewOverlayView.kt app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt
git commit -m "feat: PreviewOverlayView 支持滤镜叠加、水印提示、边框提示绘制"
```

---

## Task 7: 改造 PhotoModePlugin 为 EffectSpec 驱动

**Files:**
- Modify: `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`

- [ ] **Step 1: 添加 EffectSpec 相关 import**

```kotlin
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.effect.FrameEffect
```

- [ ] **Step 2: 提取 buildEffectSpec 方法**

在 `PhotoModeController` 中添加：

```kotlin
    private fun buildEffectSpec(flashMode: FlashMode): EffectSpec {
        val filter = selectedFilter
        val selectedWatermarkTemplate = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo
            .watermarkStyleFor(selectedWatermarkTemplate.id)
        return EffectSpec(listOf(
            FilterEffect(filter.id, filter.renderSpec),
            WatermarkEffect(
                templateId = selectedWatermarkTemplate.id,
                tokens = mapOf(
                    "watermarkModel" to "OpenCamera",
                    "watermarkDatetime" to watermarkDateTime(),
                    "watermarkCameraParams" to watermarkCameraParams(flashMode)
                ),
                style = watermarkStyle
            ),
            FrameEffect(currentFrameRatio())
        ))
    }
```

- [ ] **Step 3: 重构 ShutterPressed 分支**

将 `handle` 方法中 `ModeIntent.ShutterPressed` 分支的 customTags 构建改为使用 EffectBridge。保持 LivePhoto 和 SingleFrame 两个分支都改。

关键改动：将原来的 `buildMap { put("filterProfile", ...) ... }` 替换为：

```kotlin
val effectSpec = buildEffectSpec(flashMode)
val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
val postProcessSpec = EffectBridge.toPostProcessSpec(effectSpec)
```

然后在 `customTags = buildMap` 中使用 `putAll(bridgeTags)` 保留非效果相关的 tag（如 `mode`, `flash`, `livePhotoDefault`, `frameRatio`, `stillQuality`, `stillResolution`, captureAid 等）。

- [ ] **Step 4: 运行测试**

Run: `./gradlew :feature:mode-photo:test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt
git commit -m "refactor: PhotoModePlugin 使用 EffectSpec 声明效果意图"
```

---

## Task 8: 改造其余 ModePlugin

**Files:**
- Modify: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- Modify: `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- Modify: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- Modify: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- Modify: `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
- Modify: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`

每个 ModePlugin 的改造模式相同：

1. 添加 EffectSpec 相关 import
2. 提取 `buildEffectSpec()` 方法，用 EffectEntry 声明效果
3. 在 `handle(ShutterPressed)` 中用 `EffectBridge.toMetadataTags()` 和 `EffectBridge.toPostProcessSpec()` 替代散落的 customTags 设置
4. 非效果相关的 tag（mode, flash, captureAid 等）保留在 buildMap 中

- [ ] **Step 1: 改造 PortraitModePlugin**

添加 PortraitEffect 到 EffectSpec，用 EffectBridge 转换。保留 `mode=portrait` 等非效果 tag。

- [ ] **Step 2: 改造 DocumentModePlugin**

添加 DocumentEffect 到 EffectSpec。

- [ ] **Step 3: 改造 HumanisticModePlugin**

添加 FilterEffect 到 EffectSpec。

- [ ] **Step 4: 改造 NightModePlugin**

添加 FilterEffect 到 EffectSpec（如有）。

- [ ] **Step 5: 改造 VideoModePlugin**

添加 FilterEffect 到 EffectSpec（如有）。

- [ ] **Step 6: 改造 ProModePlugin**

添加 FilterEffect 到 EffectSpec（如有）。

- [ ] **Step 7: 运行全部 feature 模块测试**

Run: `./gradlew :feature:mode-photo:test :feature:mode-portrait:test :feature:mode-document:test :feature:mode-humanistic:test :feature:mode-night:test :feature:mode-pro:test :feature:mode-video:test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add feature/
git commit -m "refactor: 所有 ModePlugin 使用 EffectSpec 声明效果意图"
```

---

## Task 9: AppContainer 集成

**Files:**
- Modify: `app/src/main/java/com/opencamera/app/AppContainer.kt`

- [ ] **Step 1: 添加 EffectCapabilityResolver 创建**

在 `AppContainer` 中创建 `EffectCapabilityResolver` 实例（需要 `DeviceCapabilities`，在 `cameraAdapter` 创建后可用）：

```kotlin
import com.opencamera.core.effect.EffectCapabilityResolver
import com.opencamera.core.effect.PreviewEffectAdapter
```

```kotlin
    val effectCapabilityResolver = EffectCapabilityResolver(cameraAdapter.capabilities)
    val previewEffectAdapter = PreviewEffectAdapter()
```

- [ ] **Step 2: 运行 app 测试**

Run: `./gradlew :app:test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opencamera/app/AppContainer.kt
git commit -m "feat: AppContainer 集成 EffectCapabilityResolver 和 PreviewEffectAdapter"
```

---

## Task 10: MainActivity 预览效果绑定

**Files:**
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`

- [ ] **Step 1: 在 SessionState 观察中构建预览效果模型**

在 `MainActivity` 中观察 `SessionState` 的 `activeEffectSpec` 变化时，使用 `PreviewEffectAdapter` 构建 `PreviewEffectRenderModel`，设置到 `PreviewOverlayRenderModel.effectModel` 中。

具体位置：找到现有的 `PreviewOverlayRenderModel` 构建逻辑，在其中添加：

```kotlin
val effectModel = previewEffectAdapter.adapt(state.activeEffectSpec)
```

然后传递给 `PreviewOverlayView.render()`。

- [ ] **Step 2: 运行 app 测试**

Run: `./gradlew :app:test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/opencamera/app/MainActivity.kt
git commit -m "feat: MainActivity 绑定预览效果到 PreviewOverlayView"
```

---

## Task 11: 全量验证

- [ ] **Step 1: 运行全部测试**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 2: 构建项目**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat: Effect Pipeline 管线落地完成 - EffectSpec + PreviewOverlay + 自动降级"
```
