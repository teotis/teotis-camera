# Package 01: Analyze Preview Zoom Strategy

## Goal

分析当前变焦系统的行为和参数，设计预览窗离散变焦的完整策略。此 package 不修改任何代码，只产出分析文档和设计规范。

## Allowed Paths

- `docs/plans/preview-zoom-discrete-stepping/scratch/` (via scratch-path)
- `docs/plans/preview-zoom-discrete-stepping/status/01-analyze-preview-zoom-strategy.md`
- Coordinator state (via `mark-state`)

Read-only access to all source files under `app/`, `core/` for analysis.

## Acceptance Criteria

1. **镜头检测分析**：分析 `detectLensNodeMap()` 在 vivo X300 上的实际分类结果。当前启发式 `maxZoom >= 5f → PERISCOPE, >= 1.6f → TELEPHOTO` 可能将广角镜头（带高倍数字变焦）错误分类。如果存在问题，记录改进方案但不在此 package 实现。

2. **焦段映射表**：输出 captureZoom → previewZoom 离散映射表。涵盖所有物理镜头的本机焦距和过渡区。

3. **镜头切换阈值**：记录当前 `evaluateLensNode()` 的 hysteresis 逻辑（`LENS_NODE_HYSTERESIS_DELTA = 0.1f`），分析是否需要调整阈值以匹配离散预览变焦。

4. **画幅框面积曲线**：对于每个焦段，计算画幅框面积占预览窗的比例，标注 1/3 面积边界。

5. **16:9 溢出根因分析**：追踪 `previewContentGeometry()` → `computeFrameRect()` 在 PreviewRatio=FULL (4:3 sensor) + FrameRatio=16:9 场景下的几何计算链，确认溢出原因：
   - 检查 `previewContentAspect = null` 时 `contentRect` 是否等于完整 view bounds
   - 检查 fitCenter 显示的 4:3 图像在 view 中的实际位置和大小
   - 比较 16:9 frame 与 4:3 内容区的边界关系

6. **PreviewZoomRatio 数据模型设计**：设计 `previewZoomRatio` 字段的位置和语义：
   - 是否放在 `PreviewConfig` 中？
   - 与 `zoomRatio` 的关系（previewZoomRatio <= zoomRatio）
   - 在 `SessionState` 中的传递路径
   - 在 `PreviewFrameRenderModel` 中的暴露方式

## Verification Commands

```bash
# No code changes; verify the analysis doc exists and is well-formed
ls -la docs/plans/preview-zoom-discrete-stepping/scratch/01-analyze-preview-zoom-strategy/analysis.md

# Verify key source files used in analysis still exist
ls -la core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt
ls -la core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt
ls -la app/src/main/java/com/opencamera/app/PreviewOverlayView.kt
```

## Expected Evidence

- Analysis document at scratch path containing:
  - Device lens detection analysis with actual thresholds
  - Zoom range → preview zoom mapping table
  - Frame-to-preview area ratio chart
  - 16:9 overflow root cause with affected code paths
  - PreviewZoomRatio data model design spec
  - Recommendations for packages 02 and 03

## Notes

- 当前 `detectLensNodeMap()` 只检测 WIDE/TELEPHOTO/PERISCOPE 三种镜头类型，不区分超广角和广角。如果 vivo X300 有三颗后摄，可能存在分类不准确。
- `CameraXCaptureAdapter.detectZoomRatioCapability()` 总是返回 `CONTINUOUS` support，这意味着 preview zoom 的离散化必须在 session 层实现，而非依赖 CameraX 的离散 preset。
- 16:9 溢出问题可能已有部分修复（参考 `feature/preview-fitcenter-geometry` 分支和 `2026-05-25-preview-frame-fitcenter-geometry.md` 计划），需要检查当前 mainline 状态。
