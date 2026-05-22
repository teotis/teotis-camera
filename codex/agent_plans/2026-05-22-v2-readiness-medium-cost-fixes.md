# OpenCamera 2.0 Readiness Medium-Cost Fixes

> 用途：可交给能力较强的外部非多模态 agent 立即修复。  
> 范围：中等解决成本，需要跨 session/device/media/render model 补语义或可观测性，但不要求真实图像算法、RAW DNG、Live motion 或真机矩阵。  
> 建议：每个任务单独分支/单独 agent，不要多人同时改 `CameraXCaptureAdapter.kt` 和 `DefaultCameraSession.kt`。

## 1. 目标

清掉 2.0 准入中阻断“结果可信”和“降级诚实”的文本侧 P0/P1：让失败、降级、占位能力不再静默或伪装成功。

## 2. 任务清单

### M1. 后处理失败必须进入可观测结果

来源：`IO-Chain-Audit.md` P0/P1；主控 Top Blocking Risk。

问题：

- `emitShotCompleted()` 中 `mediaPostProcessor.process()` 的失败可能只进入 `pipelineNotes`。
- 用户看到 `Photo saved`，但水印/滤镜/人像渲染可能未实际生效。
- `ShotResult.toTransactionResult()` status 可能硬编码为 `SUCCESS`，没有检查失败 notes。

目标：

- 后处理失败不得静默吞没。
- 若核心保存成功但后处理失败，应输出明确 degraded result，并让 UI/Dev log 可见。
- 若保存本身失败，应产生 `ShotFailed` 或等价错误事件。

建议实现方向：

1. 统一 `pipelineNotes` 失败标记规范，例如 `failed:<processor>:<reason>`。
2. `ShotResult.toTransactionResult()` 检查 failure notes：
   - 无失败：`SUCCESS`
   - 非致命后处理失败：`DEGRADED` 或添加 warning 字段（按现有 contracts 选最小变更）
   - 致命保存失败：`FAILED`
3. `DefaultCameraSession.handleShotCompleted()` 将 degraded/warning 进入用户可见状态或 Dev log。
4. 增加单测覆盖：
   - 后处理失败但原图保存成功；
   - 后处理致命失败；
   - transaction result 不再永远 success；
   - UI 能展示 degraded/warning。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

### M2. orphaned shot failure 不能只记为 duplicate

来源：`IO-Chain-Audit.md` P1。

问题：

- `DefaultCameraSession.handleShotFailed()` 中 `activeShot == null` 或 ID 不匹配时直接 return，仅记录 `shot.failed.duplicate`。
- 权限撤销或并发清理后，真实失败原因可能丢失。

目标：

- 区分真正 duplicate 与 orphaned failure。
- orphaned failure 至少进入 trace/diagnostics；必要时更新 `lastError` 或 Dev log。
- 不破坏正常重复回调去重。

建议实现方向：

1. 新增 trace event：
   - `shot.failed.duplicate`
   - `shot.failed.orphaned`
2. orphaned failure 保留 failure reason 到 diagnostics。
3. 若当前 state 已 IDLE，可不改变 capture status，但不能吞掉原因。
4. 增加单测：
   - activeShot 已清空后收到失败事件；
   - ID 不匹配失败事件；
   - duplicate completed/failed 仍不污染正常 state。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
```

### M3. 首次拍摄 `KEEP_PREVIEW_FRAME` 缩略图空白 fallback

来源：`IO-Chain-Audit.md` P2。

问题：

- `ThumbnailPolicy.KEEP_PREVIEW_FRAME` 且 `thumbnailSource=None` 时，`latestThumbnailSource` 保留旧值。
- 首次拍摄可能导致缩略图区域空白或无新反馈。

目标：

- 第一次拍摄也要有可信反馈。
- 若不能生成最终保存缩略图，应显示 preview snapshot、pending placeholder 或明确 saving 状态。

建议实现方向：

1. 在 session 或 `ThumbnailRenderCommand` 中增加 fallback 规则：
   - 有 saved media：优先 saved media。
   - 有 pending capture feedback：显示 pending。
   - 首次拍摄且 keep preview：显示 preview snapshot 或 placeholder。
2. 增加 `ThumbnailRenderCommandTest` 和 session 测试。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ThumbnailRenderCommandTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

### M4. RAW 降级为明确 unsupported / saved-only，而不是像已生效功能

来源：`Feature-Availability-Audit.md` P0；主控 P0。

注意：本任务不实现真实 RAW/DNG。真实 RAW 属于高成本决策项。

问题：

- RAW toggle 可见，`rawEnabled` 进入 metadata，但无 DNG 输出。
- 用户无法区分“RAW 已启用”和“只是记录设置”。

目标：

- 在 UI、render model、metadata、diagnostics 中明确 RAW 当前不是可执行输出。
- 如果当前设备/实现只能保存 flag，应显示 `仅记录`、`暂不支持 RAW 输出` 或等价中文文案。
- 不要让用户以为会得到 DNG 文件。

建议实现方向：

1. 在 `RuntimeProControlsRenderModel` 增加 RAW support status：
   - `APPLY`：未来真实 RAW。
   - `SAVED_ONLY`：当前仅记录。
   - `UNSUPPORTED`：不可用。
2. UI 对 `SAVED_ONLY` 做禁用或半禁用展示，并显示原因。
3. `DefaultDeviceShotRequestTranslatorTest` 或 `CameraXCaptureAdapterManualRequestTest` 验证 raw flag 不产生 DNG 输出语义。
4. 文案走 `AppTextResolver`/strings。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterManualRequestTest
```

### M5. 夜景多帧合并降级诚实化

来源：`Feature-Availability-Audit.md` P0；主控 P0。

注意：本任务不实现真实多帧融合算法。真实算法属于高成本决策项。

问题：

- Night 模式展示多帧拍摄策略，但 `MultiFrameMergePlaceholderPostProcessor` 只记录输入并删除临时文件。
- 用户以为获得夜景多帧降噪/HDR，实际效果等同占位。

目标：

- 让当前状态明确成为 `DEGRADED / PLACEHOLDER / UNSUPPORTED`，而不是伪装成真实多帧增强。
- UI/metadata/diagnostics 至少一处用户可见或开发可见地说明“多帧算法未启用/使用单帧 fallback”。

建议实现方向：

1. 在 mode capability 或 capture metadata 中标记 `multiFrameMerge=placeholder`。
2. Night/Scenery render model 展示短提示，如 `多帧合成暂未启用` 或 `单帧增强`。
3. `MultiFrameMergePlaceholderPostProcessor` 输出稳定 pipeline note，例如 `degraded:multi-frame-placeholder`.
4. 测试覆盖：
   - placeholder 不声称 success merge；
   - UI 能从 mode/capability 读到 degraded reason；
   - 临时文件清理仍正常。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.MultiFrameMergeAlgorithmProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCapabilityDegradationTest --tests com.opencamera.core.mode.ModeCaptureStrategyGraphTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

### M6. Live Photo 静态降级显式化

来源：`Feature-Availability-Audit.md` P1；主控 P1。

注意：本任务不实现 preview ring buffer 或 motion mux。真实 Live motion 属于高成本决策项。

问题：

- `captureLivePhoto` 当前 hardcode `METADATA_ONLY`，最终 `STILL_ONLY_FALLBACK`，`frameCount=0`。
- 用户开启 Live Photo 后未必知道只是静态图 + sidecar。

目标：

- 明确显示 Live 当前为 `静态降级` 或 `Live 运动暂不可用`。
- sidecar/metadata 中记录 fallback reason。
- 不阻断普通拍照保存。

建议实现方向：

1. `LiveTemporalAssemblyPlanner` 或 adapter 输出 stable degraded reason。
2. `SessionUiRenderModel` / quick panel 对 Live toggle 显示 degraded reason。
3. Dev log 中记录 `live:still-only-fallback`.
4. 增加测试：
   - `frameCount=0` 时 UI/metadata 有 fallback reason；
   - 普通 still 保存不受影响。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.SessionUiRenderModelTest
```

### M7. 连续变焦语义收敛

来源：`Feature-Availability-Audit.md` Risk。

问题：

- `ZoomControlSupport.CONTINUOUS` 已定义，但 detection 从不返回。
- 如果 UI/gesture 认为存在 continuous support，可能产生误导。

目标：

- 明确当前只支持 discrete preset，或实现最小 continuous detection。
- 不需要做复杂手势重构。

建议实现方向：

1. 如果当前产品只要离散变焦：删除/隐藏 continuous UI 依赖，测试断言 unsupported。
2. 如果保留 pinch：让 pinch 映射到 discrete/clamped ratio，并在 capability 中标注 degraded continuous。
3. 增加测试覆盖 `detectZoomRatioCapability` 和 gesture fallback。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
```

## 3. 跨任务协作边界

- M1/M2/M3 可能同时触碰 `DefaultCameraSession.kt`，建议同一 agent 串行完成。
- M4/M5/M6 都涉及 `SessionUiRenderModel` degraded reason，建议共享文案命名，不要各自发明状态词。
- M1 可能触碰 `core/media` 和 `app/camera`，完成后 D 组 IO 审查应复跑。

## 4. 不要处理的事项

以下属于高成本决策项，不应在本任务中实现：

- Camera2 RAW/DNG 输出。
- 真实多帧对齐融合算法。
- Live Photo 预览环形缓冲、运动段 mux、容器兼容。
- 视频帧级水印烧录。
- 真机 provider death / thermal chamber / 长稳矩阵。

## 5. 交付物

- 代码和测试。
- 每个任务都必须说明“当前是实现真实能力”还是“显式降级”。
- 更新 `codex/documentation.md`。
- 给出实际运行过的 `rtk` 验证命令。
