# Current Shutter Timing Baseline

## 当前声音定时差距

应用快门声音（`MediaActionSound.SHUTTER_CLICK`）当前与 `state.activeShot` 的首次出现绑定——即用户按下快门按钮的瞬间。这与"帧已获取 / 用户可放松"的时间点相差甚远，在 CameraX 的 `ImageCapture.OnImageSavedCallback` 回调中相差数百毫秒至数秒。

声音应该移到"捕获就绪"边界——即帧数据已被设备安全接收，快门可以安全接受下一次拍摄的时刻。Package 04（adapter）和 Package 05（shutter-sound）将实现这个改动。本 Package 02 通过测试锁定当前行为，确保后续改动不会破坏正确性。

## 当前快門行为（已测试基线）

### 普通静态拍摄（STILL_CAPTURE）的 rearm 策略

在 `DATA_RECEIVED` 或 `SAVING` 后，当 `activeShot == null`（会话已 rearm）时：
- `shutterDisabledReason()` 返回 null → 快门已启用
- `shutterVisualState()` 返回 `BACKGROUND_SAVING`（如果 captureStatus == SAVING）或 `PHOTO_READY`
- 后续拍摄可立即开始，不等待后处理/保存完成

### 保守拍摄类型保持快门禁用

以下 shot kind 在 `DATA_RECEIVED` 时会保持 `activeShot` 设置，因此快门保持禁用：
- **MULTI_FRAME_CAPTURE** — 多帧/夜景类路径
- **LIVE_PHOTO** — 动态照片路径
- 其他保持 `activeShot` 直到 `ShotCompleted` 的非普通路径

### 录制时快门作为停止录制控件

- `shutterDisabledReason()` 在 `RecordingStatus.RECORDING` 时返回 null（快门可用作停止录制按钮）
- `captureDisabledReason()` 在录制时返回禁用原因（通用控件守卫）
- 这两个函数的语义不同：快门特定的守卫允许录制，但通用守卫不允许

### 视觉状态机

| 条件 | shutterVisualState | isShutterEnabled |
|---|---|---|
| IDLE, camera granted | PHOTO_READY | true |
| REQUESTED + activeShot | PHOTO_PRESSED | false |
| SAVING + activeShot != null | SAVING | false |
| SAVING + activeShot == null | BACKGROUND_SAVING | true |
| DATA_RECEIVED + activeShot == null | PHOTO_READY | true |
| DATA_RECEIVED + activeShot != null | SAVING | false |
| RECORDING | VIDEO_RECORDING | true |
| RECOVERING | BLOCKED | false |
| Countdown active | COUNTDOWN | false |
| Camera permission denied | BLOCKED | false |
| FAILED | FAILURE_OR_DEGRADED | false |

## 测试覆盖（保护基线）

### SessionCockpitRenderModelTest

| 测试名称 | 覆盖内容 |
|---|---|
| `shutter disabled reason returns null when idle` | 空闲状态检测 |
| `shutter disabled reason returns null during background save after rearm` | rearm 后 SAVING + activeShot==null |
| `shutter disabled reason returns null for DATA_RECEIVED after session rearm` | rearm 后 DATA_RECEIVED + activeShot==null |
| `shutter disabled reason blocks DATA_RECEIVED before rearm for conservative capture` | MULTI_FRAME_CAPTURE + DATA_RECEIVED 阻止 |
| `shutter disabled reason blocks LIVE_PHOTO with activeShot at DATA_RECEIVED` | LIVE_PHOTO + DATA_RECEIVED 阻止 |
| `shutter disabled reason returns null during active recording` | 录制时快门启用 |
| `capture disabled reason blocks during recording but shutter stays enabled` | 录制时 captureDisabled vs shutterDisabled 区别 |
| `shutter visual state is PHOTO_READY when idle` | 空闲视觉状态 |
| `shutter visual state is BACKGROUND_SAVING when capture status is saving without activeShot` | rearm 后后台保存视觉 |
| `shutter visual state transitions from PHOTO_PRESSED to SAVING` | 状态转换链 |
| `shutter visual state is PHOTO_READY for DATA_RECEIVED after session rearm` | rearm 后 DATA_RECEIVED 视觉 |
| `shutter remains clickable via cockpit render model after DATA_RECEIVED rearm` | cockpit 集成验证 |
| `shutter enabled with BACKGROUND_SAVING via cockpit render model` | cockpit BACKGROUND_SAVING 集成 |
| `shutter remains enabled during processing warning after session rearm` | 后处理警告时仍可用 |
| `conservative capture keeps shutter blocked even with pending postprocess` | 保守拍摄 + 后处理 = 阻止 |

### CameraCockpitRenderModelTest

| 测试名称 | 覆盖内容 |
|---|---|
| `bottom cockpit shutter visual state is BACKGROUND_SAVING when saving without activeShot` | cockpit 级别 BACKGROUND_SAVING |
| `bottom cockpit shutter visual state is PHOTO_PRESSED when shot requested` | cockpit 级别 PHOTO_PRESSED |
| `bottom cockpit shutter visual state reflects recording` | cockpit 级别录制状态 |

### SessionUiRenderModelTest

| 测试名称 | 覆盖内容 |
|---|---|
| `capture disabled reason returns null when idle` | 通用守卫空闲 |
| `capture disabled reason returns saving photo` | 通用守卫保存中 |
| `capture disabled reason returns recording` | 通用守卫录制中 |
| `capture disabled reason returns permission required` | 通用守卫权限 |

## 构建基础设施说明

此 package 的 app 模块编译需要 `:feature:mode-fullclear` 模块，该模块在原仓库中不存在。创建了一个最小 stub 模块以允许测试编译和运行。此外，在 `SessionUiRenderModel.kt` 和 `TestAppTextResolver.kt` 中添加了 `FULL_CLEAR` 分支以修复 when 穷举性错误。这些改动是基础设施性的，不影响任何运行时行为。

## 验证结果

```
:core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
→ BUILD SUCCESSFUL

:app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
→ BUILD SUCCESSFUL (所有 70+ 测试通过)
```
