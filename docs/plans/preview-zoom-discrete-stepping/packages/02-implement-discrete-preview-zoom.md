# Package 02: Implement Discrete Preview Zoom (Session Layer)

## Goal

在 session/device 层实现离散预览变焦。引入 `previewZoomRatio` 字段，将预览窗的基准倍率从连续的 `zoomRatio` 中解耦出来，并在物理镜头切换点进行离散跳跃。

## Allowed Paths

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` — add `previewZoomRatio` to `PreviewConfig`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` — implement stepping logic
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt` — if state type changes needed
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` — add tests
- Coordinator status and scratch files

Do NOT edit any app-layer files (`app/src/main/java/com/opencamera/app/**`).

## Acceptance Criteria

1. **PreviewConfig 扩展**：在 `PreviewConfig` 中新增 `previewZoomRatio: Float = 1f` 字段，语义为"预览窗当前使用的基准倍率"，总是 ≤ `zoomRatio`。

2. **离散预览倍率计算函数**：实现 `computePreviewZoomRatio(captureZoom: Float, lensNodeMap): Float`，规则：
   - 找到 lensNodeMap 中所有 available 节点
   - 取 `thresholdRatio <= captureZoom` 的最大 thresholdRatio
   - 若 captureZoom < 最小 threshold，则返回最小 threshold
   - 返回值为离散的预览基准倍率

3. **handleApplyZoomRatio 更新**：在设置 capture zoom 的同时，计算并存储 preview zoom：
   - captureZoom 保持连续（用户滑杆/手势值）
   - previewZoom 为离散阶梯值
   - 当镜头切换时，previewZoom 也随之更新

4. **resolveActiveDeviceGraph 更新**：在构建 `PreviewConfig` 时，同时设置 `previewZoomRatio`。

5. **单元测试**：
   - 测试 `computePreviewZoomRatio` 在各种 lensNodeMap 配置下的行为
   - 测试镜头切换时 previewZoom 正确跳跃
   - 测试 previewZoomRatio 总是 ≤ captureZoomRatio

## Verification Commands

```bash
# Compile core:device module
rtk ./scripts/run_isolated_gradle.sh :core:device:compileDebugKotlin

# Compile core:session module
rtk ./scripts/run_isolated_gradle.sh :core:session:compileDebugKotlin

# Run session tests
rtk ./scripts/run_isolated_gradle.sh :core:session:testDebugUnitTest

# Verify no app-layer files were changed
git diff --name-only HEAD~1 | grep -v '^core/' | grep -v '^docs/' && echo "WARNING: changed files outside allowed paths" || echo "OK: only core files changed"
```

## Expected Evidence

- Branch: `agent/preview-zoom-discrete-stepping/02-implement-discrete-preview-zoom`
- Commit with changes to `DeviceContracts.kt` and `DefaultCameraSession.kt`
- `PreviewConfig` now has `previewZoomRatio` field
- `computePreviewZoomRatio()` function implemented and tested
- `resolveActiveDeviceGraph()` sets `previewZoomRatio`
- Unit tests pass

## Design Notes (from Package 01)

Package 01 will provide:
- 具体的 previewZoom 映射表（超广角/广角/长焦的阈值和基准倍率）
- lensNodeMap 改进建议（如果需要区分 UW 和 W）
- hysteresis delta 是否需要调整的分析

应在实现前阅读 package 01 的 scratch 分析文档。

### 关键代码路径

1. `DefaultCameraSession.handleApplyZoomRatio()` (line 693): 当前在一个方法中处理 zoom 和 lens 切换。previewZoomRatio 应在此方法中计算。

2. `DefaultCameraSession.evaluateLensNode()` (line 802): 镜头切换的 hysteresis 逻辑。离散 preview zoom 的跳跃应与镜头切换同步。

3. `resolveActiveDeviceGraph()` (line ~768): 构建新的 DeviceGraph 时设置 previewZoomRatio。

### 数据模型设计

```kotlin
// DeviceContracts.kt
data class PreviewConfig(
    // ... existing fields ...
    val zoomRatio: Float = 1f,
    /** 预览窗基准倍率，在物理镜头切换点离散跳跃，始终 ≤ zoomRatio */
    val previewZoomRatio: Float = 1f,
    val requestedLensNode: LensNode? = null
)
```

`previewZoomRatio` 的默认值为 1f，与 `zoomRatio` 默认值一致，保证向后兼容。
