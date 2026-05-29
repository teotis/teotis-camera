# Preview Zoom Discrete Stepping — 分析文档

> Package 01 产出物。此文档仅做分析，不修改任何代码。

---

## 1. 镜头检测分析

### 1.1 当前分类算法

**文件**: `CameraXCaptureAdapter.kt:1241-1294` (`detectLensNodeMap()`)

算法流程：
1. 过滤所有 `LENS_FACING_BACK` 后置摄像头
2. 对每个摄像头取 `normalizedSupportedRatios` 中的最大值作为 `maxZoom`
3. 按 `maxZoom` 升序排序
4. 逐个分类：

| 条件 | 分类结果 | thresholdRatio |
|---|---|---|
| maxZoom 最小（第一个） | `WIDE` | 0.0f |
| maxZoom >= 4.0f | `PERISCOPE` | 5.0f |
| maxZoom >= 1.6f (但 < 4.0f) | `TELEPHOTO` | 2.0f |
| maxZoom < 1.6f（非第一个） | 跳过 | — |

### 1.2 潜在误分类风险

**问题**: 当前启发式不区分超广角（UW）和广角（W）。

对于 vivo X300 等三摄手机（UW + W + T），假设各摄像头的 `maxZoom` 值为：
- UW 超广角: maxZoom ≈ 0.6x（本机焦距 0.6x，数字变焦上限可能到 1.0x 或更高）
- W 广角: maxZoom ≈ 10x（1x 本机焦距，数字变焦可达 10x）
- T 长焦: maxZoom ≈ 5x（2x 本机焦距，数字变焦可达 5x）

按当前算法排序后：
- 第一个（maxZoom 最低）→ WIDE：这可能是超广角而非广角
- maxZoom >= 4.0 → PERISCOPE：广角（10x）会被分类为 PERISCOPE
- maxZoom >= 1.6 → TELEPHOTO：长焦（5x）会被分类为 TELEPHOTO

**结论**: 当前算法会将超广角误分类为 WIDE，将广角（带高倍数字变焦）误分类为 PERISCOPE。但由于 CameraX 的 `setZoomRatio()` 会自动处理物理摄像头切换（SAT），这个误分类主要影响的是 UI 上显示的镜头标签和 `evaluateLensNode()` 的切换阈值，不影响实际硬件变焦行为。

**建议**: 如果需要精确的镜头类型识别，应通过 `CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS` 获取实际焦距，或通过 `CameraCharacteristics.LENS_FACING` + 焦距组合判断。但此改进不在本 package 实现范围内。

### 1.3 detectZoomRatioCapability() 行为

**文件**: `CameraXCaptureAdapter.kt:1129-1174`

- 始终返回 `support = CONTINUOUS`
- 从 `CONTROL_ZOOM_RATIO_RANGE` 读取物理摄像头的 zoom 范围
- 构建 `supportedRatios` 列表（如 [0.6, 1.0, 2.0, 5.0]）
- **关键**: preview zoom 的离散化必须在 session 层实现，不能依赖 CameraX 的离散 preset

---

## 2. 焦段映射表（captureZoom → previewZoom）

### 2.1 当前系统状态

当前系统中 **preview zoom 和 capture zoom 使用同一个 `zoomRatio` 值**。`DeviceGraphSpec` 中只有 `preview.zoomRatio`，没有独立的 capture zoom 字段。

数据流：
```
用户手势 → SessionIntent.ApplyZoomRatio → handleApplyZoomRatio()
  → evaluateLensNode() [hysteresis: 0.1f]
  → resolveActiveDeviceGraph() → state.activeDeviceGraph.preview.zoomRatio
  → SessionEffect.ApplyZoomRatio → CameraXCaptureAdapter.updateZoomRatio()
  → camera.cameraControl.setZoomRatio()
```

### 2.2 离散预览变焦映射表（设计目标）

对于 vivo X300 三摄配置，设计 captureZoom → previewZoom 的离散映射：

| captureZoom 范围 | previewZoomRatio | 活跃镜头 | 视觉效果 |
|---|---|---|---|
| 0.6x ~ 1.0x | 0.6x | UW 超广角 | 预览窗显示 0.6x 画面，captureZoom 越大画幅框越小 |
| 1.0x ~ 2.0x | 1.0x | W 广角 | 预览窗显示 1.0x 画面 |
| 2.0x ~ 5.0x | 2.0x | T 长焦 | 预览窗显示 2.0x 画面 |
| 5.0x ~ max | 5.0x | P 潜望 | 预览窗显示 5.0x 画面 |

### 2.3 切换点与 hysteresis

当前 `LENS_NODE_HYSTERESIS_DELTA = 0.1f`，在阈值 ±0.1 处形成死区：

| 切换方向 | 阈值 | 实际切换点 | 死区宽度 |
|---|---|---|---|
| WIDE → TELEPHOTO | 2.0 | 2.1（上切） | 0.2 |
| TELEPHOTO → WIDE | 2.0 | 1.9（下切） | 0.2 |
| TELEPHOTO → PERISCOPE | 5.0 | 5.1（上切） | 0.2 |
| PERISCOPE → TELEPHOTO | 5.0 | 4.9（下切） | 0.2 |

**对于离散预览变焦的建议**: previewZoomRatio 的切换点应与镜头切换阈值对齐，但不需要 hysteresis——因为 previewZoomRatio 是离散跳跃的，不存在"在阈值附近抖动"的问题。hysteresis 仅对 evaluateLensNode() 的物理镜头切换有意义。

---

## 3. 镜头切换阈值分析

### 3.1 当前 evaluateLensNode() 逻辑

**文件**: `DefaultCameraSession.kt:802-835`

```kotlin
internal const val LENS_NODE_HYSTERESIS_DELTA = 0.1f
```

算法：
1. 过滤掉 `available = false` 的节点
2. 按 `thresholdRatio` 降序排列
3. 从最高阈值向下扫描，找第一个 `ratio >= thresholdRatio` 的节点作为 target
4. 如果需要切换（currentNode != target）：
   - 上切：需要 `ratio >= targetThreshold + 0.1`
   - 下切：需要 `ratio <= currentThreshold - 0.1`

### 3.2 是否需要调整阈值

**当前阈值**: WIDE=0.0, TELEPHOTO=2.0, PERISCOPE=5.0

**分析**:
- 对于 vivo X300，长焦镜头的本机焦距约 2x，阈值 2.0 是合理的
- 潜望镜头的本机焦距约 5x，阈值 5.0 是合理的
- hysteresis delta = 0.1 在连续变焦场景下是合理的死区宽度

**对于离散预览变焦**:
- `evaluateLensNode()` 的阈值不需要改变——它控制的是物理镜头切换
- 离散 previewZoomRatio 的切换点应与 thresholdRatio 对齐（2.0 和 5.0）
- 用户体验：在 captureZoom 从 1.9 升到 2.1 的过程中，preview 从 1.0x 跳到 2.0x，capture 同时从 WIDE 切到 TELEPHOTO

---

## 4. 画幅框面积曲线

### 4.1 缩放机制

**文件**: `PreviewOverlayView.kt:194-210` (`activeContentGeometry()`)

```kotlin
val zoom = renderModel.frame?.zoomRatio ?: 1f
if (zoom <= 1f) return geometry
val scale = 1f / zoom
return geometry.copy(
    activeFrameRect = scaleRectAroundCenter(geometry.activeFrameRect, scale)
)
```

画幅框面积 = `(1/zoom)^2 × contentRect面积`

### 4.2 面积比例表

假设 contentRect 面积为 1.0（归一化）：

| captureZoom | previewZoom | scale = 1/zoom | frame 面积比 | 状态 |
|---|---|---|---|---|
| 1.0x | 1.0x | 1.0 | 100% | 画幅框 = 预览窗 |
| 1.2x | 1.0x | 0.833 | 69.4% | > 1/3 |
| 1.5x | 1.0x | 0.667 | 44.4% | > 1/3 |
| 1.73x | 1.0x | 0.578 | **33.4%** | **≈ 1/3 边界** |
| 2.0x | 1.0x | 0.5 | 25.0% | < 1/3 |
| 2.5x | 1.0x | 0.4 | 16.0% | < 1/3 |
| 3.0x | 2.0x | 0.5 | 25.0% | < 1/3 |
| 5.0x | 2.0x | 0.4 | 16.0% | < 1/3 |
| 5.0x | 5.0x | 1.0 | 100% | 画幅框 = 预览窗 |

### 4.3 1/3 面积边界分析

**公式**: 当 `captureZoom = previewZoomRatio × 1.73` 时，frame 面积 = `(1/1.73)^2 ≈ 33.4% ≈ 1/3`

在离散预览变焦模式下：
- previewZoom = 1.0x 时，captureZoom 在 1.0x ~ 1.73x 范围内 frame 面积 ≥ 1/3
- previewZoom = 1.0x 时，captureZoom 超过 1.73x 后 frame 面积 < 1/3（但仍可接受直到 2.0x）
- previewZoom = 2.0x 时，captureZoom 在 2.0x ~ 3.46x 范围内 frame 面积 ≥ 1/3

**建议**: 当 captureZoom 超过 `previewZoomRatio × 2.0`（frame 面积 < 25%）时，应考虑提升 previewZoomRatio 到下一级。这给出了一个触发 preview zoom 跳跃的参考阈值。

---

## 5. 16:9 溢出根因分析

### 5.1 场景描述

**PreviewRatio = FULL**（4:3 传感器全画面）+ **FrameRatio = 16:9**

### 5.2 几何计算链追踪

**Step 1**: `previewContentGeometry()` 计算 contentRect

当 `PreviewRatio = FULL` 时：
- `previewRatioToContentAspect(FULL)` 返回 `null`
- `contentRect` = 整个 View bounds（例如 1080×1920 竖屏）

**Step 2**: 在 contentRect 内计算 activeFrameRect（FrameRatio = 16:9）

- `computeFrameRect(1080, 1920, 16, 9)`
- 竖屏方向：`orientedFrameRatio(16, 9, PORTRAIT)` → `(9, 16)` → targetRatio = 9/16 = 0.5625
- `availableRatio` = 1080/1920 = 0.5625
- `targetRatio (0.5625) == availableRatio (0.5625)` → 走 `else` 分支
- 高度撑满 1920，宽度 = 1920 × 0.5625 = 1080
- `activeFrameRect` = (0, 0, 1080, 1920) = 整个 View

**Step 3**: zoom 缩放

- `zoom = renderModel.frame?.zoomRatio`（例如 1.0x）
- `zoom <= 1f` → 不缩放，直接返回

**结论**: 在 PreviewRatio=FULL + FrameRatio=16:9 的组合下，activeFrameRect 等于整个 View。16:9 画幅框完全填充了 View，没有溢出。

### 5.3 真正的溢出场景

**溢出发生在 PreviewRatio=4:3 + FrameRatio=16:9 时**：

- contentRect = 4:3 fitCenter 在 View 中（例如 1080×1440，上下各留 240px 黑边）
- activeFrameRect = 16:9 fitCenter 在 contentRect 中
- 竖屏：`computeFrameRect(1080, 1440, 16, 9)` → oriented (9,16) → targetRatio=0.5625
- availableRatio = 1080/1440 = 0.75
- targetRatio (0.5625) < availableRatio (0.75) → 高度撑满 1440，宽度 = 1440 × 0.5625 = 810
- activeFrameRect = (135, 240, 945, 1680) — 完全在 View 内，不溢出

**溢出场景 3: PreviewRatio=FULL + FrameRatio=16:9 + zoom > 1**

- contentRect = 整个 View (1080×1920)
- activeFrameRect（zoom 前）= 整个 View (1080×1920) — 16:9 在 4:3 竖屏 View 中恰好填满
- zoom = 2.0x → scale = 0.5 → 缩放后 frame = (270, 480, 810, 1440) — 在 View 内，不溢出

**关键发现**: 16:9 溢出不是因为几何计算错误，而是因为：
1. 在 PreviewRatio=FULL 模式下，contentRect = 整个 View
2. 16:9 画幅框在 4:3 竖屏 View 中恰好填满（targetRatio == availableRatio）
3. 如果传感器实际输出的是 4:3 画面，但 UI 显示的 16:9 画幅框覆盖了整个 View，用户会认为 16:9 就是全屏——但实际上拍照时会在 4:3 画面上裁切 16:9 区域
4. **真正的"溢出"感知**: 用户看到 16:9 画幅框覆盖全屏，但实际拍照输出的 16:9 图像是从 4:3 传感器裁切的，可能丢失了顶部/底部内容

### 5.4 根因总结

| 因素 | 描述 |
|---|---|
| 直接原因 | PreviewRatio=FULL 时 contentRect = 全 View，16:9 frame 恰好填满 |
| 深层原因 | 4:3 传感器 + FULL 预览 + 16:9 画幅框的组合下，frame 显示区域与实际裁切区域不一致 |
| 用户感知 | 画幅框"溢出"预览窗——实际上是 16:9 frame 与 4:3 contentRect 的边界重合 |
| 修复方向 | 需要在 `activeContentGeometry()` 中对 zoom 缩放后的 frameRect 做 `coerceIn` 钳位到 contentRect 边界内 |

---

## 6. PreviewZoomRatio 数据模型设计

### 6.1 设计目标

引入 `previewZoomRatio` 概念：它是用户看到的预览窗基准倍率，总是 ≤ `captureZoomRatio`，且在物理镜头切换点离散跳跃。

### 6.2 字段位置

**方案: 在 `PreviewConfig` 中添加 `previewZoomRatio`**

```kotlin
// DeviceContracts.kt
data class PreviewConfig(
    val snapshotsEnabled: Boolean = true,
    val zoomRatio: Float = 1f,              // capture zoom（保持不变）
    val previewZoomRatio: Float = 1f,       // 新增：预览窗基准倍率
    val requestedLensNode: LensNode? = null
)
```

**理由**:
- `zoomRatio` 是 capture zoom，控制 CameraX 硬件缩放和拍照裁切
- `previewZoomRatio` 是 preview 基准倍率，控制画幅框的缩放基准
- 两者都在 `PreviewConfig` 中，因为它们都与预览相关
- `previewZoomRatio` 总是 ≤ `zoomRatio`

### 6.3 语义关系

```
previewZoomRatio: 离散值 (0.6, 1.0, 2.0, 5.0)
  ↓ 决定
画幅框缩放基准 = 1 / previewZoomRatio
  ↓ 以及
captureZoomRatio: 连续值 (0.6 ~ max)
  ↓ 决定
CameraX 硬件缩放 + 拍照裁切
```

用户手势修改的是 `captureZoomRatio`（zoomRatio），当 captureZoomRatio 跨过镜头切换阈值时，`previewZoomRatio` 自动跳跃到对应值。

### 6.4 在 SessionState 中的传递路径

```
用户手势 → handleApplyZoomRatio(captureZoom)
  → evaluateLensNode() → 确定 targetLensNode
  → 根据 targetLensNode 确定 previewZoomRatio:
     WIDE → 1.0f (或 0.6f 如果 UW 被分类为 WIDE)
     TELEPHOTO → 2.0f
     PERISCOPE → 5.0f
  → resolveActiveDeviceGraph(
      requestedZoomRatio = captureZoom,
      requestedPreviewZoomRatio = previewZoomRatio  // 新增参数
    )
  → state.activeDeviceGraph.preview.zoomRatio = captureZoom
  → state.activeDeviceGraph.preview.previewZoomRatio = previewZoomRatio
```

### 6.5 在 PreviewFrameRenderModel 中的暴露方式

```kotlin
// SessionPreviewRenderModel.kt
internal data class PreviewFrameRenderModel(
    val ratio: FrameRatio,
    val label: String,
    val dimOutsideFrame: Boolean,
    val bottomInsetPx: Float = 0f,
    val zoomRatio: Float = 1f,           // capture zoom（保持不变）
    val previewZoomRatio: Float = 1f     // 新增：预览基准倍率
)
```

在 `previewOverlayRenderModel()` 中：
```kotlin
PreviewFrameRenderModel(
    ...,
    zoomRatio = state.activeDeviceGraph.preview.zoomRatio,
    previewZoomRatio = state.activeDeviceGraph.preview.previewZoomRatio
)
```

### 6.6 在 PreviewOverlayView 中的使用

`activeContentGeometry()` 修改为使用 `previewZoomRatio` 而非 `zoomRatio` 来缩放画幅框：

```kotlin
private fun activeContentGeometry(): PreviewContentGeometry {
    val geometry = previewContentGeometry(...)
    val previewZoom = renderModel.frame?.previewZoomRatio ?: 1f
    if (previewZoom <= 1f) return geometry
    val scale = 1f / previewZoom
    return geometry.copy(
        activeFrameRect = scaleRectAroundCenter(geometry.activeFrameRect, scale)
    )
}
```

**效果**: 画幅框只在 previewZoomRatio 跳跃时离散变化，不会随 captureZoom 连续缩放。

### 6.7 数据流全景

```
用户连续变焦: captureZoom = 1.0 → 1.5 → 2.0 → 2.5 → 3.0
                                     ↓
evaluateLensNode:    WIDE    WIDE   WIDE→TELE  TELE   TELE
                                     ↓
previewZoomRatio:    1.0     1.0    1.0→2.0    2.0    2.0
                                     ↓
画幅框面积比:        100%    44%    44%→100%   64%    44%
                                     ↑
                              镜头切换点，画幅框跳跃
```

---

## 7. 对 Package 02 和 03 的建议

### 7.1 Package 02 (implement-discrete-preview-zoom)

**核心任务**:
1. 在 `PreviewConfig` 中添加 `previewZoomRatio: Float` 字段
2. 在 `PreviewFrameRenderModel` 中添加 `previewZoomRatio: Float` 字段
3. 修改 `handleApplyZoomRatio()`，在 `evaluateLensNode()` 返回 targetLensNode 时，同步计算对应的 `previewZoomRatio`
4. 修改 `activeContentGeometry()`，使用 `previewZoomRatio` 而非 `zoomRatio` 缩放画幅框
5. 确保 `resolveActiveDeviceGraph()` 正确传递 `previewZoomRatio`

**关键注意点**:
- `previewZoomRatio` 的值应从 `lensNodeMap` 中的 `thresholdRatio` 推导，而非硬编码
- 需要处理 UW 镜头被误分类为 WIDE 的情况（如果需要支持超广角的 previewZoom = 0.6x）
- `zoomRatio`（captureZoom）保持不变，继续控制 CameraX 硬件缩放

### 7.2 Package 03 (fix-overlay-frame-geometry)

**核心任务**:
1. 修复 `activeContentGeometry()` 中 zoom 缩放后的 frameRect 未钳位到 contentRect 的问题
2. 确保 PreviewRatio=FULL + FrameRatio=16:9 场景下画幅框不溢出
3. 在离散 previewZoomRatio 模式下，验证画幅框面积在可接受范围内

**关键注意点**:
- 钳位应在 `scaleRectAroundCenter()` 之后、返回之前执行
- 需要考虑 contentRect 为全 View（PreviewRatio=FULL）时的边界情况
- 16:9 溢出的根本修复可能需要在 `previewContentGeometry()` 中增加对 frameRatio 与 contentRect 关系的检查

---

## 8. 关键源文件索引

| 文件 | 路径 | 职责 |
|---|---|---|
| DeviceContracts.kt | `core/device/.../DeviceContracts.kt` | LensNode、PreviewConfig、ZoomRatioCapability 定义 |
| SessionContracts.kt | `core/session/.../SessionContracts.kt` | SessionState、SessionIntent、SessionEffect 定义 |
| DefaultCameraSession.kt | `core/session/.../DefaultCameraSession.kt` | handleApplyZoomRatio、evaluateLensNode、resolveActiveDeviceGraph |
| CameraXCaptureAdapter.kt | `app/.../camera/CameraXCaptureAdapter.kt` | detectLensNodeMap、detectZoomRatioCapability、updateZoomRatio |
| PreviewOverlayView.kt | `app/.../PreviewOverlayView.kt` | previewContentGeometry、activeContentGeometry、computeFrameRect |
| SessionPreviewRenderModel.kt | `app/.../SessionPreviewRenderModel.kt` | PreviewFrameRenderModel、previewOverlayRenderModel |
| MediaTypes.kt | `core/media/.../MediaTypes.kt` | FrameRatio 定义 |
| MainActivity.kt | `app/.../MainActivity.kt` | previewRatioToContentAspect 调用 |
