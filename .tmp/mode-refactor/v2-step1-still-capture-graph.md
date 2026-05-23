# Step 1: `stillCaptureDeviceGraph` 纯函数

## 目标

消除 6 个 still mode 中完全同形的 `currentDeviceGraph()` 方法。

## 问题

以下 6 个控制器的 `currentDeviceGraph()` 实现完全相同（8 行）：

- PhotoModeController (PhotoModePlugin.kt:369)
- PortraitModeController (PortraitModePlugin.kt:411)
- ProModeController (ProModePlugin.kt:276)
- NightModeController (NightModePlugin.kt:352)
- DocumentModeController (DocumentModePlugin.kt:254)
- HumanisticModeController (HumanisticModePlugin.kt:438)

VideoModeController 不同（使用 `DeviceGraphSpec.videoRecording()`），不受影响。

## 实现

### 新增文件

**路径**: `core/mode/src/main/kotlin/com/opencamera/core/mode/StillCaptureGraphHelper.kt`

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.device.DeviceGraphSpec

/**
 * 构建静态拍照的 DeviceGraphSpec。
 *
 * 6 个 still mode (Photo, Portrait, Pro, Night, Document, Humanistic)
 * 的 currentDeviceGraph() 实现完全相同，统一用此函数替代。
 * Video 模式使用 DeviceGraphSpec.videoRecording()，不适用此函数。
 */
fun stillCaptureDeviceGraph(runtimeState: ModeRuntimeState): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState.stillCaptureQuality,
        resolutionPreset = runtimeState.stillCaptureResolutionPreset
    )
}
```

### 修改 6 个控制器

每个控制器的 `currentDeviceGraph()` 方法从 8 行缩减为 1 行：

**PhotoModePlugin.kt** — 将：
```kotlin
private fun currentDeviceGraph(): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState().lensFacing,
        enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState().stillCaptureQuality,
        resolutionPreset = runtimeState().stillCaptureResolutionPreset
    )
}
```
改为：
```kotlin
private fun currentDeviceGraph(): DeviceGraphSpec = stillCaptureDeviceGraph(runtimeState())
```

同样修改：PortraitModePlugin.kt、ProModePlugin.kt、NightModePlugin.kt、DocumentModePlugin.kt、HumanisticModePlugin.kt。

需要在每个文件中添加 import：
```kotlin
import com.opencamera.core.mode.stillCaptureDeviceGraph
```

## 测试

在 `core/mode/src/test/kotlin/com/opencamera/core/mode/` 下新增：

**`StillCaptureGraphHelperTest.kt`**：

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class StillCaptureGraphHelperTest {

    @Test
    fun `returns stillCapture graph with correct parameters`() {
        val state = ModeRuntimeState(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            lensFacing = LensFacing.BACK,
            stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
            stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
        )
        val graph = stillCaptureDeviceGraph(state)
        assert(graph is DeviceGraphSpec.StillCapture)
    }

    @Test
    fun `respects lens facing`() {
        val frontState = ModeRuntimeState(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            lensFacing = LensFacing.FRONT,
            stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
            stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
        )
        val graph = stillCaptureDeviceGraph(frontState) as DeviceGraphSpec.StillCapture
        assertEquals(LensFacing.FRONT, graph.preferredLensFacing)
    }
}
```

## 验证清单

- [ ] 新增 `StillCaptureGraphHelper.kt`，编译通过
- [ ] 6 个控制器的 `currentDeviceGraph()` 改为一行调用
- [ ] VideoModeController 的 `currentDeviceGraph()` 保持不变
- [ ] `./gradlew :core:mode:test` 通过
- [ ] `./gradlew compileDebugKotlin` 通过
- [ ] 新增 `StillCaptureGraphHelperTest` 通过
