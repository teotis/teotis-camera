# Step 4: `StillShotSessionEventReducer` 收敛三段式

## 目标

消除 6 个 PHOTO 模式中结构相同的 `onSessionEvent` 三分支处理。

## 问题

以下 6 个控制器的 `onSessionEvent` 中 ShotStarted/ShotCompleted/ShotFailed 分支结构相同（~20 行）：

- PhotoModeController
- PortraitModeController
- ProModeController
- NightModeController
- DocumentModeController
- HumanisticModeController

VideoModeController 使用 `MediaType.VIDEO`，且额外维护 `isRecording` 状态，不适用。

## 设计考量

这个 step 比 Step 1-3 更复杂，因为：
1. `onSessionEvent` 是 `suspend fun`，且直接操作 `mutableSnapshot`
2. 每个模式的 headline/detail 文案略有不同（"Photo capture in progress" vs "Portrait capture in progress"）
3. Video 的 `onSessionEvent` 有完全不同的逻辑

**方案**: 提供一个辅助函数，接收模式名称和 MediaType，返回标准化的 headline。控制器仍然保留自己的 `onSessionEvent` override，但内部调用辅助函数来构建 headline，消除 if-return-guard 和 headline 构建的重复。

## 实现

### 新增文件

**路径**: `core/mode/src/main/kotlin/com/opencamera/core/mode/SessionEventHeadlines.kt`

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.media.MediaType

/**
 * onSessionEvent 三分支的标准 headline 构建。
 *
 * 6 个 PHOTO 模式的 onSessionEvent 结构相同：
 * - ShotStarted → guard mediaType → "{Mode} capture in progress"
 * - ShotCompleted → guard mediaType → "{Mode} saved" + outputPath
 * - ShotFailed → guard mediaType → "{Mode} capture failed" + reason
 *
 * 此对象提供统一的 headline 构建，消除 6 处重复。
 * Video 模式不适用（MediaType.VIDEO + 额外 isRecording 状态）。
 */
object SessionEventHeadlines {

    /** ShotStarted 的标准 headline */
    fun shotStartedHeadline(modeDisplayName: String): String =
        "${modeDisplayName} capture in progress"

    /** ShotCompleted 的标准 headline */
    fun shotCompletedHeadline(modeDisplayName: String): String =
        "${modeDisplayName} saved"

    /** ShotFailed 的标准 headline */
    fun shotFailedHeadline(modeDisplayName: String): String =
        "${modeDisplayName} capture failed"
}
```

### 修改 6 个控制器

以 PhotoModeController 为例，将：

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    when (event) {
        is ModeSessionEvent.ShotStarted -> {
            if (event.shot.mediaType != MediaType.PHOTO) return
            mutableSnapshot.value = buildSnapshot(
                headline = "Photo capture in progress",
                detail = "Unified shot pipeline accepted the photo save task."
            )
        }
        is ModeSessionEvent.ShotCompleted -> {
            if (event.result.mediaType != MediaType.PHOTO) return
            mutableSnapshot.value = buildSnapshot(
                headline = "Photo saved",
                detail = event.result.outputPath
            )
        }
        is ModeSessionEvent.ShotFailed -> {
            if (event.mediaType != MediaType.PHOTO) return
            mutableSnapshot.value = buildSnapshot(
                headline = "Photo capture failed",
                detail = event.reason
            )
        }
        else -> Unit
    }
}
```

简化为：

```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    when (event) {
        is ModeSessionEvent.ShotStarted -> {
            if (event.shot.mediaType != MediaType.PHOTO) return
            mutableSnapshot.value = buildSnapshot(
                headline = SessionEventHeadlines.shotStartedHeadline("Photo"),
                detail = "Unified shot pipeline accepted the photo save task."
            )
        }
        is ModeSessionEvent.ShotCompleted -> {
            if (event.result.mediaType != MediaType.PHOTO) return
            mutableSnapshot.value = buildSnapshot(
                headline = SessionEventHeadlines.shotCompletedHeadline("Photo"),
                detail = event.result.outputPath
            )
        }
        is ModeSessionEvent.ShotFailed -> {
            if (event.mediaType != MediaType.PHOTO) return
            mutableSnapshot.value = buildSnapshot(
                headline = SessionEventHeadlines.shotFailedHeadline("Photo"),
                detail = event.reason
            )
        }
        else -> Unit
    }
}
```

**注意**: 这个 step 的收益相对较小（主要是 headline 构建的去重），且 `onSessionEvent` 的 guard + snapshot 更新结构仍然保留在每个控制器中。如果觉得收益不够显著，可以跳过此 step。

**更高收益的替代方案**: 如果基类最终不做，可以考虑一个更完整的 reducer：

```kotlin
// 更完整的版本 — 但需要控制器传入 buildSnapshot 函数
fun reduceSessionEvent(
    event: ModeSessionEvent,
    mediaType: MediaType,
    modeDisplayName: String,
    onShotStartedDetail: () -> String = { "" },
    onShotCompletedDetail: (String) -> String = { it },
    onShotFailedDetail: (String) -> String = { it },
    updateSnapshot: (headline: String, detail: String) -> Unit
): Boolean {
    return when (event) {
        is ModeSessionEvent.ShotStarted -> {
            if (event.shot.mediaType != mediaType) return false
            updateSnapshot(
                "${modeDisplayName} capture in progress",
                onShotStartedDetail()
            )
            true
        }
        is ModeSessionEvent.ShotCompleted -> {
            if (event.result.mediaType != mediaType) return false
            updateSnapshot(
                "${modeDisplayName} saved",
                onShotCompletedDetail(event.result.outputPath)
            )
            true
        }
        is ModeSessionEvent.ShotFailed -> {
            if (event.mediaType != mediaType) return false
            updateSnapshot(
                "${modeDisplayName} capture failed",
                onShotFailedDetail(event.reason)
            )
            true
        }
        else -> false
    }
}
```

使用方式：
```kotlin
override suspend fun onSessionEvent(event: ModeSessionEvent) {
    reduceSessionEvent(
        event = event,
        mediaType = MediaType.PHOTO,
        modeDisplayName = "Photo",
        onShotStartedDetail = { "Unified shot pipeline accepted the photo save task." },
        updateSnapshot = { headline, detail -> mutableSnapshot.value = buildSnapshot(headline, detail) }
    )
}
```

## 测试

**`SessionEventHeadlinesTest.kt`**：

```kotlin
package com.opencamera.core.mode

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEventHeadlinesTest {

    @Test
    fun `shot started headline`() {
        assertEquals("Photo capture in progress", SessionEventHeadlines.shotStartedHeadline("Photo"))
        assertEquals("Portrait capture in progress", SessionEventHeadlines.shotStartedHeadline("Portrait"))
    }

    @Test
    fun `shot completed headline`() {
        assertEquals("Photo saved", SessionEventHeadlines.shotCompletedHeadline("Photo"))
    }

    @Test
    fun `shot failed headline`() {
        assertEquals("Night capture failed", SessionEventHeadlines.shotFailedHeadline("Night"))
    }
}
```

## 验证清单

- [ ] 新增 `SessionEventHeadlines.kt`（或 `SessionEventReducer.kt`），编译通过
- [ ] 6 个控制器的 onSessionEvent headline 构建使用统一函数
- [ ] Video 控制器不受影响
- [ ] `./gradlew :core:mode:test` 通过
- [ ] `./gradlew compileDebugKotlin` 通过
