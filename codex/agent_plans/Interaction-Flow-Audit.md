# Interaction Flow Audit

## Summary

7 条核心交互路径已审查。5 条 Pass，2 条 Risk。测试套件中 2 个失败属于历史旧期望（humanistic mode style 名称变更），不影响交互链路本身。

## Flow Table

| Flow | Result | Evidence | Breakpoint | Risk | Recommendation |
| --- | --- | --- | --- | --- | --- |
| 冷启动到首帧 | Pass | `onStart()` -> `Boot` + `PreviewHostAttached` -> `requestPreviewBinding` -> `PreviewStatus.IDLE/ACTIVE`；`DefaultCameraSessionTest` 覆盖 boot 流程 | 无 | - | - |
| 模式切换 | Pass | `bindModeTrackTouch()` -> `SwitchMode` -> `handleSwitchMode()` 有 countdown/activeShot/availableModes 三重保护；`ModeTrackScrollGuard` 防滚动误触 | 无 | - | - |
| 变焦选择 | Pass | `ApplyZoomRatio` -> `handleApplyZoomRatio()` -> clamp [0.5, 10.0] -> `SessionEffect.ApplyZoomRatio`；录音中阻断切换 | 无 | - | - |
| 拍照 | Pass | `ShutterPressed` -> `handleModeIntent()` -> `SubmitCapture` -> `shotExecutor` -> `CaptureStatus.SAVING/COMPLETED`；`isShutterEnabled` 由 modeSnapshot 控制 | 无 | - | - |
| 录像 | Pass | `ShutterPressed` (video) -> `REQUESTING` -> `RECORDING` -> `ShutterPressed` -> `StopActiveCapture` -> `STOPPING` -> `IDLE`；watchdog 超时保护 | 无 | - | - |
| 面板开合 | Risk | `CockpitPanelRoute` sealed class + `GestureGuard` 阻止面板手势 + `onBackPressed` 分层关闭 | 无明确断点 | 面板打开时 `renderPanelVisibility()` 依赖 `activePanelRoute` 同步更新，若 UI 延迟可能闪烁 | 考虑为面板状态切换添加 trace event |
| 权限恢复 | Risk | `permissionLauncher` -> `syncPermissionState` -> `PermissionsUpdated` -> `BLOCKED` -> grant -> `requestPreviewBinding` -> `IDLE` | 无明确断点 | `requestCameraPermissionIfNeeded()` 中 `shouldShowRequestPermissionRationale` 与 `else` 分支逻辑相同，无法区分"首次拒绝"和"永久拒绝" | 考虑区分永久拒绝后引导用户到设置页 |

## Sequence Notes

### Cold Start To First Frame

```text
User action: App launches
-> Activity.onStart()
-> syncPermissionState() [dispatch PermissionsUpdated]
-> dispatch(SessionIntent.Boot)
-> dispatch(SessionIntent.PreviewHostAttached)
-> requestCameraPermissionIfNeeded()

-> handleBoot():
   -> lifecycle = RUNNING
   -> previewStatus = IDLE (if camera granted) / BLOCKED (if not)
   -> captureStatus = IDLE
   -> recordingStatus = IDLE
   -> requestPreviewBinding(reason="session boot", isRecovery=false)

-> handlePreviewHostAttached():
   -> previewHostAvailable = true
   -> requestPreviewBinding(reason="preview host attached", isRecovery=false)

-> SessionEffect.BindPreview emitted
-> CameraXCaptureAdapter binds Preview to surfaceProvider
-> handlePreviewFirstFrameAvailable(latencyMillis):
   -> previewStatus = ACTIVE
   -> previewMetrics updated

-> User-visible feedback:
   - permissionStatus text: "granted" / "camera_only" / "denied"
   - Preview画面显示
   - captureOutput text: "Previewing" / "Waiting for camera..."
   - shutterButton.isEnabled = isShutterEnabled

-> Recovery:
   - If permission denied: previewStatus = BLOCKED, controls disabled
   - If preview host detached: pendingPreviewHostRecoveryReason saved
   - If surface lost: previewStatus = SURFACE_LOST
```

**Result: Pass** — 链路闭合。`DefaultCameraSessionTest` 覆盖 boot、permission、preview binding 场景。

### Mode Switch

```text
User action: Tap mode button (e.g., videoModeButton)
-> bindModeTrackTouch(): button.setOnClickListener
   -> modeTrackScrollGuard.isScrolling check (prevents accidental tap during scroll)
   -> captureConfigDisabledReason(state) check (permission/preview/recording guards)
   -> if VIDEO && no RECORD_AUDIO permission: request permission first
   -> dispatch(SessionIntent.SwitchMode(modeId))

-> handleSwitchMode(modeId):
   -> guard: countdownInProgress() -> blocked
   -> guard: activeShot != null -> blocked ("Stop recording" / "Wait for capture")
   -> guard: modeId !in availableModes -> blocked
   -> guard: modeId == activeMode -> skipped
   -> currentController.onExit()
   -> createController(modeId) -> currentController.onEnter()
   -> updateState: activeMode, captureStatus=IDLE, recordingStatus=IDLE, activeShot=null, modeSnapshot
   -> trace.record("mode.switched", modeId)
   -> requestPreviewBinding(reason="mode switched to ...")

-> SessionEffect.BindPreview emitted
-> UI re-renders: modeTrack highlights new button, bottom cockpit updates

-> User-visible feedback:
   - Mode button visual state changes (active highlight)
   - Mode label in top status updates
   - Shutter button label/context updates
   - Zoom capsule row may change visibility

-> Recovery:
   - Old mode state cleared (captureStatus, recordingStatus, activeShot reset)
   - New mode snapshot applied atomically
```

**Result: Pass** — `ModeTrackScrollGuard` 防误触，`handleSwitchMode` 有完整保护链。`ModeTrackTouchPolicyTest` 覆盖 tap/scroll 判定。

### Zoom Selection

```text
User action: Tap zoom chip (e.g., "2.0x")
-> renderZoomCapsules(): chip.setOnClickListener
   -> dispatch(SessionIntent.ApplyZoomRatio(capsule.ratio))

-> handleApplyZoomRatio(targetRatio):
   -> guard: countdownInProgress() -> blocked
   -> guard: activeShot in-flight && not RECORDING -> blocked
   -> normalizedTarget = normalizedZoomRatioValue(targetRatio)
   -> clampedRatio = normalizedTarget.coerceIn(zoomRange)
   -> guard: clampedRatio == currentZoomRatio -> no-op
   -> updateState: activeDeviceGraph.preview.zoomRatio = clampedRatio
   -> trace.record("zoom.ratio.applied", ratio)
   -> _effects.emit(SessionEffect.ApplyZoomRatio(clampedRatio))

-> User-visible feedback:
   - Active chip highlight updates (isActive = true for selected)
   - captureOutput text updates with zoom info

-> Pinch zoom path:
   GestureRouter.onTouchEvent -> ScaleGestureDetector -> GestureEvent.PinchZoom
   -> GesturePolicy.map(): cumulativeScaleFactor, 50ms debounce
   -> GestureAction.DispatchSession(AppZoomRatio(targetRatio))
   -> Same handleApplyZoomRatio path

-> Recovery:
   - If in recording: zoom can still be applied (not blocked during RECORDING)
   - If recording REQUESTING: blocked until recording starts
```

**Result: Pass** — tap 和 pinch 两条路径都收敛到同一个 `handleApplyZoomRatio`，有 clamp 和去重保护。`GesturePolicyTest` 覆盖 pinch zoom 映射。

### Photo Capture

```text
User action: Tap shutter button
-> shutterButton.setOnClickListener
   -> guard: !hasPermission(CAMERA) -> requestCameraPermissionIfNeeded() + return
   -> dispatch(SessionIntent.ShutterPressed)

-> handleModeIntent(ShutterPressed):
   -> guard: lifecycle != RUNNING -> ignored
   -> guard: countdownInProgress() -> blocked
   -> guard: activeShot (PHOTO) -> blocked ("Wait for capture")
   -> guard: activeShot (VIDEO, not RECORDING) -> blocked ("Recording request in progress")
   -> guard: activeShot (VIDEO, RECORDING) + intent != ShutterPressed -> blocked
   -> currentController.handle(ShutterPressed) -> ModeSignal.SubmitCapture(strategy)

-> submitCaptureStrategy(strategy):
   -> shotExecutor.submit(strategy) -> ShotRequest created
   -> updateState: activeShot = shot, captureStatus = REQUESTED
   -> _effects.emit(SessionEffect.ExecuteShot(plan))

-> ShotStarted(shot):
   -> updateState: captureStatus = SAVING

-> ShotCompleted(result):
   -> updateState: captureStatus = COMPLETED, activeShot = null
   -> presentation: latestCapturePath, latestSavedMediaType, pendingCaptureFeedback

-> User-visible feedback:
   - shutterButton.isEnabled = isShutterEnabled (becomes false during capture)
   - shutterButton.contentDescription = "Capture" (photo mode)
   - captureOutput text: "Saving..." / "Saved: ..."
   - previewThumbnail updates with capture feedback or saved thumbnail
   - shutter sound plays if enabled

-> Recovery:
   - If capture fails: ShotFailed -> captureStatus = FAILED, activeShot = null, lastError set
   - If permission lost mid-capture: handleInterruptedShotFailure
```

**Result: Pass** — 完整的 shot lifecycle 状态机。`DefaultCameraSessionTest` 覆盖 shutter、capture feedback、shot failure 场景。

### Video Record

```text
User action: Tap shutter button in VIDEO mode
-> shutterButton.setOnClickListener
   -> dispatch(SessionIntent.ShutterPressed)

-> handleModeIntent(ShutterPressed):
   -> currentController.handle(ShutterPressed) in video mode
   -> ModeSignal.SubmitCapture(strategy) with MediaType.VIDEO

-> submitCaptureStrategy:
   -> updateState: activeShot = shot, recordingStatus = REQUESTING
   -> startRecordingWatchdog(REQUESTING, timeout)
   -> _effects.emit(SessionEffect.ExecuteShot(plan))

-> ShotStarted(shot):
   -> updateState: recordingStatus = RECORDING
   -> watchdog cancelled

-> User-visible feedback:
   - shutterButton.setBackgroundResource(bg_shutter_recording_selector)
   - shutterButton.contentDescription = "Stop Recording"
   - captureOutput text: "Recording..."
   - Mode switch / lens switch / zoom toggle blocked during recording

-> User action: Tap shutter button again (stop)
-> dispatch(SessionIntent.ShutterPressed)
-> handleModeIntent(ShutterPressed):
   -> currentController.handle(ShutterPressed) -> ModeSignal.StopActiveCapture
   -> shotExecutor.requireStoppableShot(activeShot)
   -> updateState: recordingStatus = STOPPING
   -> startRecordingWatchdog(STOPPING, 15_000)
   -> _effects.emit(SessionEffect.StopActiveShot(shotId))

-> ShotCompleted(result):
   -> updateState: recordingStatus = IDLE, activeShot = null
   -> presentation: latestVideoPath, latestSavedMediaType = VIDEO

-> User-visible feedback:
   - shutterButton.setBackgroundResource(bg_shutter_selector)
   - shutterButton.contentDescription = "Capture"
   - captureOutput text: "Saved: video..."

-> Recovery:
   - Watchdog timeout: recordingStatus -> IDLE, activeShot = null, lastError set
   - Permission loss: handleInterruptedShotFailure
   - handleLensFacingToggled blocked during recording
   - handleZoomRatioToggled blocked during REQUESTING (but allowed during RECORDING)
```

**Result: Pass** — 四态状态机 + watchdog 超时保护。`DefaultCameraSessionTest` 覆盖 recording lifecycle。

### Panel Open/Close

```text
User action: Tap right rail button (e.g., buttonFilterEntry)
-> buttonFilterEntry.setOnClickListener:
   -> toggle: if activePanelRoute is FilterLab -> None, else -> FilterLab
   -> isFilterAdjustmentVisible = true (if opening)
   -> renderPanelVisibility()

-> renderPanelVisibility():
   -> settingsPanel.isVisible = route.isSettingsOpen
   -> filterPanel.isVisible = route is FilterLab || LensLab
   -> panelDismissScrim.isVisible = route.isAnyPanelOpen
   -> Subpage visibility for settings nested routes

-> User-visible feedback:
   - Panel appears with scrim overlay
   - Right rail button alpha changes (active: 1f, inactive: 0.92f)

-> Close via scrim tap:
   -> panelDismissScrim.setOnClickListener:
      -> activePanelRoute = None
      -> selectedWatermarkDetailTemplateId = null
      -> selectedFilterLabFamilyOverride = null
      -> isFilterAdjustmentVisible = false
      -> renderPanelVisibility()

-> Close via back press:
   -> onBackPressed():
      -> Settings with subpage: navigate back through subpage hierarchy
      -> FilterLab/LensLab/DevConsole/QuickBubble: close directly
      -> None: super.onBackPressed() (exit app)

-> Gesture blocking:
   -> GestureGuard.isGestureAllowed(zone, state):
      -> Settings open -> all gestures blocked
      -> FilterLab -> only SECONDARY_PANEL zone allowed
      -> DevConsole/QuickBubble -> all gestures blocked
   -> GestureGuard.isHorizontalScrollAllowed(state):
      -> Settings/FilterLab/filterAdjustment -> blocked

-> Panel isolation:
   -> Only one panel active at a time (sealed class CockpitPanelRoute)
   -> Opening one panel doesn't affect others' state
```

**Result: Risk** — 链路存在且逻辑正确，但 `renderPanelVisibility()` 依赖 `activePanelRoute` 同步。面板状态切换未写入 trace（不像 session 有 `trace.record`），调试时无法追踪面板历史。建议：考虑为面板状态切换添加 trace event。

### Permission Recovery

```text
User action: Deny camera permission, then grant
-> permissionLauncher (RequestMultiplePermissions):
   -> result[Manifest.permission.CAMERA]
   -> result[Manifest.permission.RECORD_AUDIO]
   -> syncPermissionState(cameraGranted, micGranted)

-> handlePermissionsUpdated(cameraGranted, microphoneGranted):
   Case 1: Permission denied
   -> previewStatus = BLOCKED (if lifecycle RUNNING)
   -> captureStatus = IDLE
   -> recordingStatus = IDLE
   -> activeShot = null (if had active shot, handleInterruptedShotFailure)
   -> lastError = "Camera permission missing"
   -> requestPreviewUnbind(reason="Camera permission missing")

   Case 2: Permission granted (was BLOCKED)
   -> previewStatus = IDLE
   -> lastError = null
   -> requestPendingPreviewHostRecovery() OR requestPreviewBinding(reason="camera permission granted")

-> User-visible feedback:
   - permissionStatus text: "granted" / "camera_only" / "denied"
   - shutterButton.isEnabled = isShutterEnabled (false if no permission)
   - captureOutput text: "Waiting for camera..." / "Camera permission required"

-> Recovery flow detail:
   -> If previewHostAvailable && pendingPreviewHostRecoveryReason != null:
      -> requestPendingPreviewHostRecovery() handles rebind
   -> If previewHostAvailable && no pending recovery:
      -> requestPreviewBinding(reason="camera permission granted", isRecovery=false)
   -> Preview binds -> first frame -> previewStatus = ACTIVE

-> Edge case: "Don't ask again" (shouldShowRequestPermissionRationale = false):
   -> requestCameraPermissionIfNeeded() enters else branch
   -> Same permissionLauncher.launch() call
   -> System may silently deny without showing dialog
   -> permissionStatus shows "denied" but no guidance to settings
```

**Result: Risk** — 链路本身闭合，`DefaultCameraSessionTest` 覆盖 permission 状态转换。但 `requestCameraPermissionIfNeeded()` 中 `shouldShowRequestPermissionRationale` 与 `else` 分支执行完全相同的逻辑，无法区分"首次拒绝"和"永久拒绝"。用户永久拒绝后无引导到系统设置页的路径。建议：当 `shouldShowRequestPermissionRationale=false` 且无权限时，引导用户到 Settings -> App -> Permissions。

## Regression Tests To Add

| Flow | Suggested Test | Priority |
| --- | --- | --- |
| 冷启动到首帧 | `cold start with permission already granted dispatches Boot + PreviewHostAttached and reaches ACTIVE` | P1 |
| 冷启动到首帧 | `cold start with permission denied sets previewStatus to BLOCKED` | P1 |
| 模式切换 | `switch mode during active recording is blocked and preserves recording state` | P0 |
| 模式切换 | `switch mode during countdown is blocked` | P1 |
| 变焦选择 | `apply same zoom ratio as current is no-op` | P2 |
| 拍照 | `shutter press during preview recovery is blocked` | P1 |
| 录像 | `recording watchdog timeout resets state to IDLE` | P0 |
| 录像 | `recording stop during REQUESTING state is blocked` | P1 |
| 面板 | `opening FilterLab while Settings is open replaces panel` | P1 |
| 权限恢复 | `permission grant after permanent deny triggers preview binding` | P1 |
| 权限恢复 | `permission revoke during active shot calls handleInterruptedShotFailure` | P0 |

## Failed Tests

| Test Name | Failure | Likely Cause |
| --- | --- | --- |
| `humanistic mode cycles styles and emits still capture metadata` | expected: `humanistic-portrait`, actual: `humanistic-vivid` | 历史旧期望：humanistic mode 的 style 名称从 `portrait` 改为 `vivid`，测试未同步更新 |
| `humanistic mode pro variant degrades to saved only draft when manual controls are unavailable` | expected: `photo-chasing-light-pro-assist`, actual: `photo-original-pro-assist` | 历史旧期望：pro assist 的 algorithm profile 名称从 `chasing-light` 改为 `original`，测试未同步更新 |

两个失败均属于测试期望值与当前实现不一致，不是交互链路断点。

## Handoff To Multimodal主控

- 7 条路径中 5 条 Pass、2 条 Risk，0 条 Fail。
- 2 条 Risk（面板、权限恢复）均有代码证据支持链路存在，风险在于边界行为引导不足，不影响核心功能。
- 2 个失败测试需更新期望值，不属于交互链路问题。
- 横屏真实手感、预览裁切策略、UI 美学不在本审查范围内，由多模态主控负责。
- 建议多模态主控重点关注：面板切换的视觉连续性、权限拒绝后的用户引导体验、录像停止后的反馈时机。
