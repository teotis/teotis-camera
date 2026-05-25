# Package 02 — 预览窗/画幅窗双窗设计 Evidence Pack

## Status: COMPLETED

## Worktree
- Path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/preview-crop-window`
- Branch: `worktree-preview-crop-window`

## Changed Files (3 files, +89/-3)

```
app/src/main/java/com/opencamera/app/PreviewOverlayView.kt      | 27 +++++++++-
app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt |  6 ++-
app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt | 59 ++++++++++++++++++++++
```

## Implementation Summary

### 1. SessionPreviewRenderModel.kt
- `PreviewFrameRenderModel` 新增 `zoomRatio: Float = 1f` 字段
- `previewOverlayRenderModel()` 从 `state.activeDeviceGraph.preview.zoomRatio` 读取当前变焦比传入

### 2. PreviewOverlayView.kt
- `activeContentGeometry()` 在 `zoom > 1` 时，对 `activeFrameRect` 按 `1/zoom` 缩放并居中
- 新增 `scaleFrameRect(FrameRect, Float): FrameRect` — 纯 Kotlin 函数，可测试
- 新增 `scaleRectAroundCenter(RectF, Float): RectF` — 运行时使用
- `drawPreviewFrame()`、`drawGrid()`、`drawFocusReticle()` 自动使用缩放后的画幅窗

### 3. PreviewOverlayGeometryTest.kt
- 新增 `scaleFrameRect` 测试 3 个：identity scale、half scale、center preservation
- 新增 zoom 缩放帧测试 3 个：2x halving、1x identity、3x third-size centered

## Behavior
- zoom = 1.0: 画幅窗 = 改造前行为（按 FrameRatio 裁切）
- zoom > 1.0: 画幅窗 = FrameRatio 裁切区域 × (1/zoom)，居中显示
- zoom < 1.0: 不缩放（保持原始画幅窗）
- 半透明遮罩、网格线、对焦光标均跟随缩放后的画幅窗
- `drawFrameGuideline()` 不受影响（直接调用 `previewContentGeometry()`）

## Verification Results

### Build
```
rtk ./gradlew --no-daemon :app:compileDebugKotlin → BUILD SUCCESSFUL
rtk ./gradlew --no-daemon :app:assembleDebug → 编译通过（dex merge 为预存问题，非本次改动）
```

### Tests
```
PreviewOverlayGeometryTest → BUILD SUCCESSFUL (21 tests, all passed)
PreviewContentGeometryTest → BUILD SUCCESSFUL (3 tests, all passed)
SessionUiRenderModelTest → BUILD SUCCESSFUL
```

## Commit
- Hash: `d3e1683`
- Message: `feat: 画幅窗随 zoom ratio 缩放`

## Self-Certification
- Only touched allowed paths: `PreviewOverlayView.kt`, `SessionPreviewRenderModel.kt`, `PreviewOverlayGeometryTest.kt`
- Did NOT touch: `CockpitSurfaceRenderer.kt`, `SessionCockpitRenderModel.kt`, `core/session/`, `core/device/`, `core/effect/`, `feature/`

## Unresolved Risks
- `assembleDebug` 的 dex merge 失败为预存构建基础设施问题（主 workspace 同样失败），非本次改动引入
- `core:effect:compileKotlin` 偶发 internal compiler error 为 Kotlin 编译器问题，与本次代码无关
- 需要在真机上验证变焦时画幅窗的视觉效果和性能
