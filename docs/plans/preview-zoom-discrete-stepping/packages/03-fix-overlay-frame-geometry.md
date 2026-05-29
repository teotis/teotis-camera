# Package 03: Fix Overlay Frame Geometry

## Goal

在 app/UI 层修复两个问题：
1. **16:9 画幅框溢出**：16:9 拍照流下画幅框大于预览窗内容的 bug
2. **帧缩放离散化**：使用 package 02 新增的 `previewZoomRatio` 替代 `zoomRatio` 进行画幅框缩放计算，使画幅框随离散预览变焦而非连续缩放

## Allowed Paths

- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` — update frame geometry
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt` — expose previewZoomRatio in render model
- `app/src/main/java/com/opencamera/app/MainActivity.kt` — wire preview content aspect if needed
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` — update/add tests
- Coordinator status and scratch files

Do NOT edit any core-layer files (`core/**`).

## Acceptance Criteria

### A. 16:9 画幅框溢出修复

1. **根因修复**：当 `previewContentAspect = null`（FULL 模式，4:3 sensor）时，`contentRect` 应反映 fitCenter 显示的 4:3 图像实际位置，而非完整 view bounds。

2. **改进 `previewContentGeometry()`**：
   - 当 `previewContentAspect` 为 null 时，使用实际的 sensor 预览流宽高比（默认 4:3 或通过 `PreviewView.outputTransform` 获取）计算 contentRect
   - 或：通过 `previewContentAspect = PreviewContentAspect(4, 3)` 显式传入 sensor 比例
   - 确保 `activeFrameRect`（16:9 frame）始终 ≤ `contentRect`（4:3 content）

3. **边界约束**：在 `activeContentGeometry()` 中添加 `activeFrameRect` 对 `contentRect` 的边界检查，clamp 超出部分。

### B. 离散预览变焦帧缩放

4. **PreviewFrameRenderModel 扩展**：新增 `previewZoomRatio` 字段：
   ```kotlin
   data class PreviewFrameRenderModel(
       val ratio: FrameRatio,
       val label: String,
       val dimOutsideFrame: Boolean,
       val bottomInsetPx: Float = 0f,
       val zoomRatio: Float = 1f,
       /** 预览窗基准倍率（离散），用于画幅框缩放计算 */
       val previewZoomRatio: Float = 1f
   )
   ```

5. **渲染模型接线**：在 `previewOverlayRenderModel()` 中从 `state.activeDeviceGraph.preview.previewZoomRatio` 读取值。

6. **帧缩放更新**：`activeContentGeometry()` 中的帧缩放应使用 `previewZoomRatio` 而非 `zoomRatio`：
   ```kotlin
   // 使用 previewZoomRatio 计算帧缩放
   // 当 previewZoomRatio < zoomRatio 时，帧会缩小，展示更多周围内容
   val previewZoom = renderModel.frame?.previewZoomRatio ?: 1f
   val captureZoom = renderModel.frame?.zoomRatio ?: 1f
   if (captureZoom > previewZoom) {
       val scale = previewZoom / captureZoom
       // activeFrameRect 缩小以反映 capture 在 preview 中的裁剪区域
   }
   ```

7. **画幅框面积日志**：添加可选的 trace/debug 日志，记录当前画幅框面积占预览窗的百分比。

### C. 测试

8. **几何测试更新**：
   - 添加 16:9 frame 在 4:3 content 中不溢出的测试
   - 添加 `previewZoomRatio < zoomRatio` 时画幅框正确缩小的测试
   - 添加 1:1 frame、4:3 frame 的边界检查测试

## Verification Commands

```bash
# Compile app module
rtk ./scripts/run_isolated_gradle.sh :app:compileDebugKotlin

# Run overlay geometry tests
rtk ./scripts/run_isolated_gradle.sh :app:testDebugUnitTest --tests="*PreviewOverlayGeometry*"

# Verify no core files were changed
git diff --name-only HEAD~1 | grep '^core/' && echo "WARNING: changed core files" || echo "OK: only app files changed"

# Quick geometry sanity: verify frame rect never exceeds content rect
rtk ./scripts/run_isolated_gradle.sh :app:testDebugUnitTest --tests="*PreviewOverlayGeometry*"
```

## Expected Evidence

- Branch: `agent/preview-zoom-discrete-stepping/03-fix-overlay-frame-geometry`
- Commit with changes to `PreviewOverlayView.kt`, `SessionPreviewRenderModel.kt`
- 16:9 frame bounded by preview content in all scenarios
- Frame scaling uses `previewZoomRatio` from package 02's data model
- Geometry unit tests pass (updated and new)
- If package 02 is not yet merged, this package may use a mock/default `previewZoomRatio` for compilation; 99-finalize will reconcile

## Design Notes

### 关于 16:9 溢出的修复策略

当前 `previewContentGeometry()` 的逻辑（PreviewOverlayView.kt:650-694）：

```
1. 如果 previewContentAspect 非 null（如 16:9）:
   → contentRect = fitCenter(16:9) in view
2. 否则（FULL/4:3 sensor）:
   → contentRect = full view bounds  ← 问题在这里！
3. activeFrameRect = fitCenter(frameRatio) in contentRect
```

当 previewContentAspect = null 且 frameRatio = 16:9 时：
- contentRect = 完整 view (如 390x844 portrait)
- activeFrameRect = fitCenter(9:16 竖屏) in 390x844 → 390x693
- 实际 4:3 sensor 图像在 fitCenter 下约为 390x520（因为有上下 letterbox）
- 390x693 > 390x520 → 画幅框超出实际预览图像

修复方案：当 previewContentAspect 为 null 时，默认使用 4:3 作为内容比例。
这已在 `2026-05-25-preview-frame-fitcenter-geometry.md` 中规划，检查是否已落地。

### 关于 previewZoomRatio vs zoomRatio

Package 02 会在 `PreviewConfig` 中添加 `previewZoomRatio`。在 overlay 层面：

- `zoomRatio` = capture 变焦倍率（连续值，用于 JPEG crop 计算）
- `previewZoomRatio` = 预览窗基准倍率（离散值，用于画幅框缩放计算）
- 帧缩放 = `previewZoomRatio / zoomRatio`

例如：capture at 1.5x, preview at 1.0x → frame scale = 1.0/1.5 = 0.67 → 画幅框占预览窗 67% 线性 = 45% 面积
