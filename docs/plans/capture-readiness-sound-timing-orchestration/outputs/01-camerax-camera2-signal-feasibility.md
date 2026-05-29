# 01 - CameraX/Camera2 Signal Feasibility Research Output

## Summary

**结论**: 可以在不接管 JPEG 保存路径的前提下，通过 `Camera2Interop.Extender.setSessionCaptureCallback()` 获得比当前 `OnImageSavedCallback → DataReceived` 更早的可靠信号。推荐新增 `DeviceEvent.CaptureCommitted` 事件，在 Camera2 `onCaptureCompleted` 时触发，作为帧已获取/编码完成的分界线。

## 当前回调链 (Repo Terms)

### 完整数据流

```
用户按下快门
  → SessionIntent.RequestShotCapture
  → SessionEffect.ExecuteShot(plan)
  → DeviceCommand.ExecuteShot(plan)
  → CameraXCaptureAdapter.captureStillImage()
      → _events.emit(DeviceEvent.ShotStarted(plan.request))    ← activeShot 设置, 快门音播放
      → captureSinglePhoto()
          → ImageCapture.takePicture(
              OutputFileOptions, executor, OnImageSavedCallback
            )
          ─── HAL 曝光/读出/ISP/JPEG编码 ───
          → onImageSaved(outputFileResults)
          → PhotoCaptureOutcome.Success
      → _events.emit(DeviceEvent.DataReceived(...))             ← activeShot 清除(STILL_CAPTURE), 快门恢复
      → [异步协程] emitShotCompleted(...)                        ← 后处理 + ShotCompleted

CameraSessionCoordinator:
  DeviceEvent.ShotStarted       → SessionIntent.ShotStarted       → handleShotStarted (activeShot=shot)
  DeviceEvent.DataReceived      → SessionIntent.DataReceived      → handleDataReceived (activeShot=null for STILL_CAPTURE)
  DeviceEvent.ShotCompleted     → SessionIntent.ShotCompleted     → handleShotCompleted

MainActivity:
  maybePlayShutterSound(state) ← 当 state.activeShot 首次出现且为 PHOTO 时播放 SHUTTER_CLICK
```

### 关键时间点

| 事件 | 何时触发 | 语义 |
|---|---|---|
| `ShotStarted` | `takePicture()` 调用前 | 请求已发出, 帧尚未获取 |
| `onImageSaved` (即 `DataReceived`) | JPEG 已写入文件/MediaStore | 文件已保存完毕 |
| `ShotCompleted` | 后处理后 | 完整管线结束 |

### 当前快门音时机

`MainActivity.maybePlayShutterSound()` 在 `state.activeShot` 首次出现时播放 `MediaActionSound.SHUTTER_CLICK`。`activeShot` 在 `handleShotStarted` (即 `ShotStarted` 事件) 时设置——这发生在 `takePicture()` 调用**之前**。因此当前快门音在"用户按下瞬间"播放, 而非"帧已获取"时播放。

### 当前快门恢复时机

`CaptureRecordingSessionProcessor.handleDataReceived()` 对 `ShotKind.STILL_CAPTURE` (无 live photo) 在收到 `DataReceived` 时清除 `activeShot`, 使快门可再次按下。`SessionCockpitRenderModel.shutterDisabledReason()` 在 `activeShot == null` 且 `captureStatus` 为 `DATA_RECEIVED` 或 `SAVING` 时返回 null (不禁用)。

## 可行方案

### 方案 A: Camera2 CaptureCallback (推荐 V1 路径)

**API**: `Camera2Interop.Extender(ImageCapture.Builder).setSessionCaptureCallback(CameraCaptureSession.CaptureCallback)`

**验证**: CameraX 1.3.4 的 `camera-camera2` API JAR 中确认 `setSessionCaptureCallback` 是公开 API。

**机制**:
1. 在 `ImageCapture.Builder` 构建时, 通过 `Camera2Interop.Extender` 注册 `CameraCaptureSession.CaptureCallback`
2. 当 Camera2 HAL 完成 capture 处理时, `onCaptureCompleted` 触发
3. 通过 `CaptureResult.CONTROL_CAPTURE_INTENT == CONTROL_CAPTURE_INTENT_STILL_CAPTURE` 过滤预览帧
4. 此时帧已完整获取 (传感器曝光→读出→ISP处理→JPEG编码均已完成)
5. CameraX 继续使用其内置路径完成文件写入 (不受影响)

**时间线**:
```
takePicture() 调用
  ─── 传感器曝光开始 ───
  ─── 传感器读出/ISP/YUV处理 ───
  ─── JPEG 编码 ───
  → onCaptureCompleted 触发          ← NEW: CaptureCommitted (帧已获取, 帧数据已确定)
  ─── CameraX 内部读 JPEG buffer ───
  ─── 写入文件/MediaStore ───
  → onImageSaved 触发                ← 现有 DataReceived (文件已保存)
```

**优势**:
- 不接管 CameraX 的 JPEG 保存路径, CameraX 继续负责写入
- 不创建第二条 capture pipeline
- 是 CameraX 官方公开 API, 不做内部/反射访问
- `onCaptureCompleted` 语义 = "HAL 已完成处理" = "帧数据已确定, 不会因手机移动而改变"
- 与 `onImageSaved` 之间的差距为磁盘 I/O 时间 (数十至数百 ms)

**风险和缓解**:
- 回调在 Camera2 回调线程执行, 必须极其轻量: 只做 `CaptureResult` 读取和 flow `tryEmit`, 其余工作交给协程
- 预览帧也会触发 `onCaptureCompleted` (30fps), 通过 `CONTROL_CAPTURE_INTENT` 过滤后立即返回, 零开销
- `SessionCaptureCallback` 会影响整个 Camera2 session (所有 UseCase 共享一个 session), 但只做过滤和事件投递, 不影响 preview/video
- 各设备上 `onCaptureCompleted` 的实现可能有细微差异, 但 Camera2 CTS 确保 `CONTROL_CAPTURE_INTENT` 和完成语义的一致性

### 方案 B: OnImageCapturedCallback (不推荐)

**API**: `ImageCapture.OnImageCapturedCallback.onCaptureSuccess(ImageProxy)`

**机制**: CameraX 1.3.0+ 提供, 在图像被捕获后、JPEG 编码前触发, 返回 `ImageProxy` (YUV 数据)。

**致命缺陷**:
- 使用此回调时, CameraX **不会**将图像保存到文件。必须由应用自行将 `ImageProxy` 编码为 JPEG 并写入文件
- 这意味着接管整个保存路径: JPEG 编码 (质量/大小/EXIF)、MediaStore 写入、文件命名
- 损失 CameraX/厂商的编码优化 (硬件 JPEG 编码器、HEIF 支持等)
- 创建了与现有 `OnImageSavedCallback` 路径平行的第二条保存路径, 增加维护负担

**结论**: 对当前项目不可接受。CameraX 的托管保存路径是设计约束, 不应为了更早的回调而放弃。

### 方案 C: 保持 DataReceived 为唯一边界 (备选)

如方案 A 因某些设备兼容性问题不可行, 则显式声明 `DataReceived` (onImageSaved) 为最早可用边界, 将快门音从 `ShotStarted` 移至 `DataReceived`。

这仍比当前行为有改进: 声音从"按下瞬间"移至"文件已保存"。

## 被拒绝的方案

| 方案 | 拒绝原因 |
|---|---|
| OnImageCapturedCallback + 自行保存 | 需接管 JPEG 编码和文件写入, 损失厂商优化, 创建平行保存路径 |
| Camera2-only (放弃 CameraX ImageCapture) | 改动范围过大, 超出本计划范围 |
| 内部 API / 反射访问 CameraX 内部 capture callback | 不安全, 随 CameraX 版本升级会崩溃 |
| Preview 帧时间戳推断 capture 完成 | 不可靠, 不同设备预览帧率/管线差异大 |
| 使用 Postview API (ImageCapture.setPostviewEnabled) | Postview 设计用于屏幕闪光/零快门延迟预览, 其回调时机非规范化, 不适合作为 readiness 信号 |

## V1 推荐实现路径

### 新增概念

在 `DeviceEvent` 和 `SessionIntent` 中新增 `CaptureCommitted` 事件:

```
DeviceEvent.CaptureCommitted(shotId: String, mediaType: MediaType, timestampNanos: Long)
SessionIntent.CaptureCommitted(shotId: String, mediaType: MediaType)
```

**语义**: 帧数据已由 Camera2 HAL 完整获取并编码完成 (JPEG buffer 已产出)。用户可安全放下手机, 帧内容不会再因设备移动而改变。文件写入在后台继续, 对快门恢复无阻塞。

### 实现步骤

1. **`core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`**
   - 在 `DeviceEvent` sealed interface 中新增 `CaptureCommitted`
   
2. **`core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`**
   - 在 `SessionIntent` sealed interface 中新增 `CaptureCommitted`
   - 在 `SessionState` 或 `SessionPresentationState` 中新增 `captureCommittedShotId: String?` 字段

3. **`app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`**
   - 在 `buildImageCapture()` 中通过 `Camera2Interop.Extender` 注册 `SessionCaptureCallback`
   - 在 `onCaptureCompleted` 中检测 `CONTROL_CAPTURE_INTENT == STILL_CAPTURE`
   - 通过 `_events.tryEmit(DeviceEvent.CaptureCommitted(...))` 发射事件
   - 跟踪 `pendingCaptureCommittedShotId` 防止重复发射

4. **`core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`**
   - 在 state update 中处理 `SessionIntent.CaptureCommitted`, 设置 `captureCommittedShotId`
   - `canRearmOnDataReceived` 保持不变 (快门仍在 DataReceived 时恢复)

5. **`app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`**
   - 新增 `DeviceEvent.CaptureCommitted → SessionIntent.CaptureCommitted` 的路由

6. **`app/src/main/java/com/opencamera/app/MainActivity.kt`**
   - 修改 `maybePlayShutterSound()`: 改为监听 `captureCommittedShotId` 而非 `activeShot` 出现
   - 声音在 `CaptureCommitted` (帧已获取) 时播放, 而非 `ShotStarted` (请求发送) 时

7. **`app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`**
   - `shutterDisabledReason()`: 考虑新增 `captureCommittedShotId` 状态 (快门在 CaptureCommitted 时仍禁用, 直到 DataReceived 才恢复)
   - `shutterVisualState()`: 可能在 `CaptureCommitted` 到 `DataReceived` 之间显示 "后台保存中" 微指示器
   - 当前 `BACKGROUND_SAVING` 状态可在 `CaptureCommitted` 时触发 (而非等待 DataReceived)

### 备选方案 (如 SessionCaptureCallback 在特定设备上不可靠)

如果真实设备测试发现 `setSessionCaptureCallback` 在某些 Android 版本/设备上不稳定:
- 回退到方案 C: 将快门音移至 `DataReceived`
- 仍保留 `CaptureCommitted` 命名和事件结构, 但在 CameraXCaptureAdapter 中与 `DataReceived` 同时发射
- 这为未来 CameraX 升级到 1.5+ (可能有更好的 readiness API) 保留了扩展点

### 安全约束保持不变

- 多帧/夜拍/Live Photo/高清像素/录制转换/恢复/不支持路径保持保守语义 (不在 DataReceived 前恢复快门)
- `canRearmOnDataReceived` 仅对 `ShotKind.STILL_CAPTURE && mediaType == PHOTO && livePhotoSpec == null` 返回 true
- `CaptureCommitted` 仅改变声音时机, 不改变快门恢复规则
- 不跨 Stage 边界, 不声明 Stage 7 完成

## 确切代码位置

### 需要修改的文件

| 文件 | 改动类型 | 具体位置 |
|---|---|---|
| `core/device/src/main/kotlin/.../DeviceContracts.kt` | 新增 sealed class 成员 | `DeviceEvent` sealed interface (L612-635) |
| `core/session/src/main/kotlin/.../SessionContracts.kt` | 新增 sealed class 成员 + state 字段 | `SessionIntent` (L317-318 附近), `SessionPresentationState` (L194-203) |
| `app/src/main/java/.../CameraXCaptureAdapter.kt` | 新增回调注册 + 事件发射 | `buildImageCapture()` (L3103-3111), 新增 `captureStillImage()` 中的逻辑 (L1728-1853) |
| `core/session/src/main/kotlin/.../CaptureRecordingSessionProcessor.kt` | 新增 intent 处理 | `handleShotStarted` (L226-237 附近), 新增 handleCaptureCommitted, `handleDataReceived` (L313-351) |
| `app/src/main/java/.../CameraSessionCoordinator.kt` | 新增事件路由 | `handleDeviceEvent` (L108-160) |
| `app/src/main/java/.../MainActivity.kt` | 修改声音触发逻辑 | `maybePlayShutterSound()` (L442-452) |
| `app/src/main/java/.../SessionCockpitRenderModel.kt` | 考虑 visual state 调整 | `shutterDisabledReason()` (L174-189), `shutterVisualState()` (L191-213) |

### 不应修改的文件

- `CameraDeviceAdapter.kt` (接口已在 DeviceContracts 中定义)
- 任何测试文件 (由 02-current-shutter-timing-tests 包负责)
- 任何 mode 插件 (feature/mode-*)

## 编译验证

```bash
./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:compileDebugKotlin
```

## 引用

- CameraX 1.3.4 API: `androidx.camera.camera2.interop.Camera2Interop.Extender.setSessionCaptureCallback()`
- Camera2: `android.hardware.camera2.CameraCaptureSession.CaptureCallback.onCaptureCompleted()`
- `CaptureResult.CONTROL_CAPTURE_INTENT` 过滤: 预览帧为 `CONTROL_CAPTURE_INTENT_PREVIEW`, 静态捕获为 `CONTROL_CAPTURE_INTENT_STILL_CAPTURE`
- 代码位置: `CameraXCaptureAdapter.kt:1736` (ShotStarted), `:1774` (DataReceived), `:2162-2188` (captureSinglePhoto)
