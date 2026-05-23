# Frame Ratio Delegate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Remove duplicated frame-ratio list/index/cycle/select logic from Photo, Portrait, Pro, Night, and Humanistic mode controllers.

**Architecture:** Add a small delegate in `core/mode`. Controllers keep mode-specific labels, event prefixes, and effect-spec builders; the delegate owns only ratio state transition, event emission, snapshot callback, and effect emission.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, `kotlin.test`, `kotlinx.coroutines.runBlocking`.

---

## Files

- Create: `core/mode/src/main/kotlin/com/opencamera/core/mode/FrameRatioDelegate.kt`
- Create: `core/mode/src/test/kotlin/com/opencamera/core/mode/FrameRatioDelegateTest.kt`
- Modify: `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- Modify: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- Modify: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- Modify: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- Modify: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- Do not modify behavior: `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- Do not modify behavior: `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`

## Task 1: Add The Delegate

- [ ] **Step 1: Create `FrameRatioDelegate.kt`**

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio

class FrameRatioDelegate(
    private val context: ModeContext,
    private val modeEventPrefix: String,
    private val effectSpecProvider: () -> EffectSpec,
    private val supportedRatios: List<FrameRatio> = DEFAULT_FRAME_RATIOS
) {
    init {
        require(supportedRatios.isNotEmpty()) {
            "FrameRatioDelegate requires at least one supported ratio"
        }
    }

    private var frameRatioIndex = 0

    fun currentFrameRatio(): FrameRatio = supportedRatios[frameRatioIndex]

    suspend fun cycleFrameRatio(
        snapshotHeadline: String,
        updateSnapshot: (headline: String) -> Unit
    ): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % supportedRatios.size
        val frameRatio = currentFrameRatio()
        return publishSelection(
            frameRatio = frameRatio,
            snapshotHeadline = snapshotHeadline,
            hintMessage = "Frame: ${frameRatio.label}",
            updateSnapshot = updateSnapshot
        )
    }

    suspend fun selectFrameRatio(
        ratio: FrameRatio,
        snapshotHeadline: String = "画幅已更新",
        updateSnapshot: (headline: String) -> Unit
    ): ModeSignal {
        val nextIndex = supportedRatios.indexOf(ratio)
        if (nextIndex < 0) {
            return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
        }
        frameRatioIndex = nextIndex
        return publishSelection(
            frameRatio = ratio,
            snapshotHeadline = snapshotHeadline,
            hintMessage = "画幅：${ratio.label}",
            updateSnapshot = updateSnapshot
        )
    }

    private suspend fun publishSelection(
        frameRatio: FrameRatio,
        snapshotHeadline: String,
        hintMessage: String,
        updateSnapshot: (headline: String) -> Unit
    ): ModeSignal {
        context.eventSink(
            "${modeEventPrefix}.frame-ratio.selected.${frameRatio.eventTag()}"
        )
        updateSnapshot(snapshotHeadline)
        context.onEffectSpecChanged(effectSpecProvider())
        return ModeSignal.ShowHint(hintMessage)
    }

    companion object {
        val DEFAULT_FRAME_RATIOS: List<FrameRatio> = listOf(
            FrameRatio.RATIO_4_3,
            FrameRatio.RATIO_16_9,
            FrameRatio.RATIO_1_1
        )
    }
}
```

- [ ] **Step 2: Add delegate tests**

Create `core/mode/src/test/kotlin/com/opencamera/core/mode/FrameRatioDelegateTest.kt`:

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameRatioDelegateTest {

    @Test
    fun `initial frame ratio is 4_3`() {
        val delegate = FrameRatioDelegate(
            context = ModeContext(),
            modeEventPrefix = "photo",
            effectSpecProvider = { EffectSpec.EMPTY }
        )

        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())
    }

    @Test
    fun `cycle advances and emits event snapshot and effect`() = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        var headline = ""
        val delegate = FrameRatioDelegate(
            context = ModeContext(
                eventSink = { event -> events += event },
                onEffectSpecChanged = { effectSpec -> effects += effectSpec }
            ),
            modeEventPrefix = "photo",
            effectSpecProvider = { EffectSpec.EMPTY }
        )

        val signal = delegate.cycleFrameRatio(
            snapshotHeadline = "Frame ratio updated",
            updateSnapshot = { nextHeadline -> headline = nextHeadline }
        )

        assertEquals(FrameRatio.RATIO_16_9, delegate.currentFrameRatio())
        assertEquals(listOf("photo.frame-ratio.selected.16x9"), events)
        assertEquals(1, effects.size)
        assertEquals("Frame ratio updated", headline)
        assertEquals(ModeSignal.ShowHint("Frame: 16:9"), signal)
    }

    @Test
    fun `select valid ratio updates current ratio`() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FrameRatioDelegate(
            context = ModeContext(eventSink = { event -> events += event }),
            modeEventPrefix = "night",
            effectSpecProvider = { EffectSpec.EMPTY }
        )

        val signal = delegate.selectFrameRatio(
            ratio = FrameRatio.RATIO_1_1,
            updateSnapshot = {}
        )

        assertEquals(FrameRatio.RATIO_1_1, delegate.currentFrameRatio())
        assertEquals(listOf("night.frame-ratio.selected.1x1"), events)
        assertEquals(ModeSignal.ShowHint("画幅：1:1"), signal)
    }

    @Test
    fun `select unsupported ratio does not emit side effects`() = runBlocking {
        val events = mutableListOf<String>()
        var effectCount = 0
        val delegate = FrameRatioDelegate(
            context = ModeContext(
                eventSink = { event -> events += event },
                onEffectSpecChanged = { effectCount += 1 }
            ),
            modeEventPrefix = "document",
            effectSpecProvider = { EffectSpec.EMPTY },
            supportedRatios = listOf(FrameRatio.RATIO_4_3)
        )

        val signal = delegate.selectFrameRatio(
            ratio = FrameRatio.RATIO_16_9,
            updateSnapshot = {}
        )

        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())
        assertTrue(events.isEmpty())
        assertEquals(0, effectCount)
        assertEquals(ModeSignal.ShowHint("当前模式不支持 16:9 画幅"), signal)
    }
}
```

- [ ] **Step 3: Verify the delegate test**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.FrameRatioDelegateTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Migrate Five Controllers

Add this import to each migrated feature file:

```kotlin
import com.opencamera.core.mode.FrameRatioDelegate
```

### Photo

- [ ] Remove `frameRatios` and `frameRatioIndex` from `PhotoModeController`.
- [ ] Add this property after `flashModeIndex`:

```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "photo") {
    buildEffectSpec(currentFlashMode())
}
```

- [ ] Replace frame-ratio helpers with:

```kotlin
private suspend fun cycleFrameRatio(): ModeSignal =
    frameRatioDelegate.cycleFrameRatio(
        snapshotHeadline = "Frame ratio updated",
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
    frameRatioDelegate.selectFrameRatio(
        ratio = ratio,
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private fun currentFrameRatio(): FrameRatio =
    frameRatioDelegate.currentFrameRatio()
```

### Portrait

- [ ] Remove `frameRatios` and `frameRatioIndex` from `PortraitModeController`.
- [ ] Add:

```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "portrait") {
    buildEffectSpec()
}
```

- [ ] Replace frame-ratio helpers with:

```kotlin
private suspend fun cycleFrameRatio(): ModeSignal =
    frameRatioDelegate.cycleFrameRatio(
        snapshotHeadline = if (depthEffectEnabled()) {
            "Portrait frame updated"
        } else {
            "Focus frame updated"
        },
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
    frameRatioDelegate.selectFrameRatio(
        ratio = ratio,
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private fun currentFrameRatio(): FrameRatio =
    frameRatioDelegate.currentFrameRatio()
```

### Pro

- [ ] Remove `frameRatios` and `frameRatioIndex` from `ProModeController`.
- [ ] Add:

```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "pro") {
    buildEffectSpec()
}
```

- [ ] Replace frame-ratio helpers with:

```kotlin
private suspend fun cycleFrameRatio(): ModeSignal =
    frameRatioDelegate.cycleFrameRatio(
        snapshotHeadline = if (manualControlsEnabled()) {
            "Frame ratio updated"
        } else {
            "Assist frame ratio updated"
        },
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
    frameRatioDelegate.selectFrameRatio(
        ratio = ratio,
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private fun currentFrameRatio(): FrameRatio =
    frameRatioDelegate.currentFrameRatio()
```

### Night

- [ ] Remove `frameRatios` and `frameRatioIndex` from `NightModeController`.
- [ ] Add:

```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "night") {
    buildEffectSpec()
}
```

- [ ] Replace frame-ratio helpers with:

```kotlin
private suspend fun cycleFrameRatio(): ModeSignal =
    frameRatioDelegate.cycleFrameRatio(
        snapshotHeadline = if (multiFrameEnabled()) {
            "Scenery frame updated"
        } else {
            "Scenery assist frame updated"
        },
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
    frameRatioDelegate.selectFrameRatio(
        ratio = ratio,
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private fun currentFrameRatio(): FrameRatio =
    frameRatioDelegate.currentFrameRatio()
```

### Humanistic

- [ ] Remove `frameRatios` and `frameRatioIndex` from `HumanisticModeController`.
- [ ] Add:

```kotlin
private val frameRatioDelegate = FrameRatioDelegate(context, "humanistic") {
    buildEffectSpec()
}
```

- [ ] Replace frame-ratio helpers with:

```kotlin
private suspend fun cycleFrameRatio(): ModeSignal =
    frameRatioDelegate.cycleFrameRatio(
        snapshotHeadline = "Humanistic frame updated",
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
    frameRatioDelegate.selectFrameRatio(
        ratio = ratio,
        updateSnapshot = { headline ->
            mutableSnapshot.value = buildSnapshot(headline = headline)
        }
    )

private fun currentFrameRatio(): FrameRatio =
    frameRatioDelegate.currentFrameRatio()
```

## Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test :feature:mode-photo:test :feature:mode-portrait:test :feature:mode-pro:test :feature:mode-night:test :feature:mode-humanistic:test
```

Expected: `BUILD SUCCESSFUL`.

Run existing behavioral coverage:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Expected: `BUILD SUCCESSFUL`, especially the existing tests whose names include `frame ratio`.

## Acceptance Criteria

- The five migrated controllers no longer declare local `frameRatios` or `frameRatioIndex`.
- Existing event strings such as `photo.frame-ratio.selected.16x9` and `night.frame-ratio.selected.16x9` are preserved.
- Existing mode-specific cycle headlines are preserved.
- Document and Video still return their current unsupported hints.
- Existing capture metadata tag `frameRatio` behavior remains covered through `DefaultCameraSessionTest`.

