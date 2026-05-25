# 人文模式重新开放 — 交接方案

## 目标

将已实现但隐藏的人文模式（Humanistic）重新开放到模式轨道 UI，默认使用 35mm 等效焦距，并将风格明确区分为「街头」「肖像」「生活」三类，与拍照模式形成清晰差异。

## 验证事实

- 人文模式已完整实现：`HumanisticModePlugin.kt`（491 行），包含 5 种风格、Pro 变体、Live Photo、帧比例切换等全部能力。
- 当前模式轨道仅显示 `PHOTO, VIDEO, DOCUMENT`（`SessionCockpitRenderModel.kt:473`），人文模式被排除在外。
- 项目中**无焦距概念**，仅有 zoom ratio 抽象（`ZoomRatioCapability`）。35mm 等效需要通过 zoom ratio 实现。
- `DeviceGraphSpec.stillCapture()` 已支持 `zoomRatio` 参数（默认 1.0），但 `stillCaptureDeviceGraph()` helper 未传递该参数。
- 模式切换时 session 保留前一个模式的 zoom ratio（`resolvedActiveDeviceGraph()` 使用 `_state.value.activeDeviceGraph.preview.zoomRatio`）。

## 非目标

- 不修改拍照模式、人像模式或其他模式的行为。
- 不新增 ModeId 枚举值（HUMANISTIC 已存在）。
- 不改变 Session Kernel 或 Device Adapter 层的核心契约。

---

## 实施范围

### 任务 1：模式轨道 UI 解除隐藏

**文件**: `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`

**改动**: 将 `PRODUCT_MODE_ENTRY_ORDER`（第 473 行）从：
```kotlin
private val PRODUCT_MODE_ENTRY_ORDER = listOf(
    ModeId.PHOTO,
    ModeId.VIDEO,
    ModeId.DOCUMENT
)
```
改为：
```kotlin
private val PRODUCT_MODE_ENTRY_ORDER = listOf(
    ModeId.PHOTO,
    ModeId.HUMANISTIC,
    ModeId.VIDEO,
    ModeId.DOCUMENT
)
```

**影响**: `visibleModeEntryOrder()` 会自动将 HUMANISTIC 包含在模式轨道中。无需其他 UI 改动，因为 `modeTrackRenderModel()` 和 `text.modeTrackLabel()` 已支持所有 ModeId。

### 任务 2：人文模式默认 35mm 等效焦距 + 模式切换 zoom 重置

**背景**: 手机主摄通常为 24-26mm 等效焦距。35mm 等效 ≈ 1.3x zoom ratio（对应 ~34mm，保守接近 35mm 标准）。

**设计决策**: 切出人文模式时 zoom 自动重置为新模式的默认值（而非保留人文模式的 1.3x）。

**改动 A — 扩展 stillCaptureDeviceGraph helper**

**文件**: `core/mode/src/main/kotlin/com/opencamera/core/mode/StillCaptureGraphHelper.kt`

当前代码：
```kotlin
fun stillCaptureDeviceGraph(runtimeState: ModeRuntimeState): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState.stillCaptureQuality,
        resolutionPreset = runtimeState.stillCaptureResolutionPreset
    )
}
```

新增带 zoomRatio 参数的重载：
```kotlin
fun stillCaptureDeviceGraph(
    runtimeState: ModeRuntimeState,
    zoomRatio: Float = 1f
): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState.stillCaptureQuality,
        resolutionPreset = runtimeState.stillCaptureResolutionPreset,
        zoomRatio = zoomRatio
    )
}
```

**改动 B — HumanisticModeController 使用 1.3x 默认 zoom**

**文件**: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`

修改 `currentDeviceGraph()` 方法（第 396-397 行），从：
```kotlin
private fun currentDeviceGraph(): DeviceGraphSpec =
    stillCaptureDeviceGraph(runtimeState())
```
改为：
```kotlin
private fun currentDeviceGraph(): DeviceGraphSpec =
    stillCaptureDeviceGraph(
        runtimeState(),
        zoomRatio = HUMANISTIC_DEFAULT_ZOOM_RATIO
    )
```

在 companion object 中添加常量：
```kotlin
companion object {
    private const val HUMANISTIC_DEFAULT_ZOOM_RATIO = 1.3f
    // ... existing code
}
```

**改动 C — Session 模式切换时重置 zoom**

**问题**: 当前 `resolvedActiveDeviceGraph()` 使用 `_state.value.activeDeviceGraph.preview.zoomRatio`（session 持有的旧 zoom）覆盖新模式 `deviceGraph()` 中的 zoom。这导致从人文模式（1.3x）切到拍照模式后 zoom 仍为 1.3x。

**文件**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`

新增 helper 方法（在 `resolvedActiveDeviceGraph` 附近，约第 1298 行）：
```kotlin
private fun resolvedModeSwitchDeviceGraph(): DeviceGraphSpec {
    val baseGraph = currentController.deviceGraph()
    return resolveActiveDeviceGraph(
        baseGraph = baseGraph,
        deviceCapabilities = _state.value.activeDeviceCapabilities,
        requestedOutputSize = baseGraph.stillCapture.outputSize,
        requestedZoomRatio = baseGraph.preview.zoomRatio
    )
}
```

修改 `handleSwitchMode()` 方法（第 459 行），将：
```kotlin
activeDeviceGraph = resolvedActiveDeviceGraph(),
```
改为：
```kotlin
activeDeviceGraph = resolvedModeSwitchDeviceGraph(),
```

**行为说明**:
- 模式切换时，新模式的 `deviceGraph()` 中的 zoomRatio 成为新 baseline（人文=1.3x，其他=1.0x）。
- 同一模式内，用户 pinch/zoom 胶囊调整的 zoom 仍然保留（因为非切换路径继续使用 `resolvedActiveDeviceGraph()`）。
- 切出人文模式后 zoom 自动回到新模式的默认值。

### 任务 3：风格明确化 — 街头、肖像、生活

**当前状态**: 5 种风格名称为 Humanistic Original / Vivid / Street / Portrait / Life，与拍照模式的风格（Original / Vivid / Chasing Light / Rich）有重叠感。

**目标**: 保留 3 种核心风格，名称更明确地区分于拍照模式：

| 当前 ID | 当前名称 | 新名称 | 算法映射 | 差异化说明 |
|---|---|---|---|---|
| `humanistic-street` | Humanistic Street | **街头 Street** | photo-chasing-light | 低暖调、高亮度，适合街拍 |
| `humanistic-portrait` | Humanistic Portrait | **肖像 Portrait** | portrait-original | 暖调 + 暗角，突出人物 |
| `humanistic-life` | Humanistic Life | **生活 Life** | photo-rich | 高对比 + 暖调 + 暗角，日常记录 |

**移除**: `humanistic-original` 和 `humanistic-vivid` — 这两种与拍照模式的 Original / Vivid 过于相似，不构成差异化。

**改动 A — SettingsDefaults.kt**

**文件**: `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`

移除 `humanistic-original`（第 121-128 行）和 `humanistic-vivid`（第 130-138 行）的 filter profile 定义。

更新剩余三种的 label：
- `humanistic-street`: label 改为 `"街头 Street"`
- `humanistic-portrait`: label 改为 `"肖像 Portrait"`
- `humanistic-life`: label 改为 `"生活 Life"`

**改动 B — HumanisticModePlugin.kt**

更新 `DEFAULT_HUMANISTIC_STYLES` companion object（第 457-489 行），移除 original 和 vivid 两项，更新 label：
```kotlin
private val DEFAULT_HUMANISTIC_STYLES = listOf(
    HumanisticStyle(
        id = "humanistic-street",
        label = "街头 Street",
        algorithmProfile = "photo-chasing-light",
        renderSpec = defaultFilterRenderSpecOrNull("photo-chasing-light")
    ),
    HumanisticStyle(
        id = "humanistic-portrait",
        label = "肖像 Portrait",
        algorithmProfile = "portrait-original",
        renderSpec = defaultFilterRenderSpecOrNull("humanistic-portrait")
    ),
    HumanisticStyle(
        id = "humanistic-life",
        label = "生活 Life",
        algorithmProfile = "photo-rich",
        renderSpec = defaultFilterRenderSpecOrNull("photo-rich")
    )
)
```

更新 `mappedAlgorithmProfile()` 方法（第 385-394 行），移除 original 和 vivid 的映射。

**改动 C — 默认风格 fallback**

更新 `resolvedDefaultStyleIndex()` 逻辑 — 如果当前 persisted 的 `defaultHumanisticFilterProfileId` 是被移除的 ID（`humanistic-original` 或 `humanistic-vivid`），fallback 到 `humanistic-street`（index 0）。

当前逻辑已通过 `.takeIf { index >= 0 } ?: 0` 处理了 fallback，无需额外改动。

**改动 D — ModeCatalogContracts.kt**

**文件**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeCatalogContracts.kt`

更新第 49-50 行的 `declaredSubfeatures`，从：
```kotlin
ModeId.HUMANISTIC ->
    "Street styles, Pro variant, frame ratio, Live default, timer, watermark"
```
改为：
```kotlin
ModeId.HUMANISTIC ->
    "35mm lens, Street/Portrait/Life styles, Pro variant, frame ratio, Live default, timer"
```

### 任务 4：模式轨道标签

**已确认无需改动**。`AppTextResolver` 中 `ModeId.HUMANISTIC` 的标签：
- 中文（默认）：`"人文"` — `app/src/main/res/values/strings.xml`
- 英文：`"Human"` — `app/src/main/res/values-en/strings.xml`
- Kotlin fallback：`"Human"`

模式轨道通过 `text.modeTrackLabel(ModeId.HUMANISTIC)` 自动获取正确标签。

注意：测试中 `TestAppTextResolver` 使用 `"Humanistic"` 作为 fallback，与生产的 `"Human"` 不一致。如果测试中涉及人文模式的快照验证，可能需要同步更新。

---

## 步骤

1. **解除隐藏**: 修改 `PRODUCT_MODE_ENTRY_ORDER` 添加 `ModeId.HUMANISTIC`。
2. **35mm zoom**: 扩展 `stillCaptureDeviceGraph()` 支持 zoomRatio 参数，修改 `HumanisticModeController.currentDeviceGraph()` 传入 1.3f。
3. **zoom 重置**: 在 `DefaultCameraSession` 新增 `resolvedModeSwitchDeviceGraph()` helper，模式切换时使用新模式的默认 zoom 而非保留旧 zoom。
4. **风格精简**: 从 SettingsDefaults 和 HumanisticModePlugin 中移除 original/vivid 风格，更新剩余三种的 label。
5. **目录声明**: 更新 ModeCatalogContracts 中的 subfeatures 描述。
6. **验证**: 编译通过 + 单元测试通过 + 模式切换行为正确。

## 验收标准

- 模式轨道显示顺序：拍照 → **人文** → 视频 → 文档。
- 进入人文模式后，preview zoom ratio 自动设为 1.3x（约 35mm 等效）。
- 人文模式仅显示 3 种风格：街头 Street、肖像 Portrait、生活 Life。
- 切换到拍照模式后 zoom 回到 1.0x。
- `./gradlew :app:assembleDebug` 编译通过。
- 现有单元测试不回归。

## 验证命令

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./gradlew --no-daemon :core:mode:test
rtk ./gradlew --no-daemon :core:settings:test
rtk ./gradlew --no-daemon :feature:mode-humanistic:test
rtk ./scripts/verify_stage_7_observability.sh
```

## 风险与备注

- **设备差异**: 1.3x zoom ratio 在不同设备上对应的等效焦距不同（取决于主摄物理焦距）。这是已知限制，后续可通过设备检测校准。
- **zoom 重置副作用**: 新增的 `resolvedModeSwitchDeviceGraph()` 会影响所有模式切换（不仅是人文模式）。由于当前所有其他模式的 `deviceGraph()` 返回 zoomRatio=1.0f（默认值），行为等价于重置 zoom。如果未来某个模式需要保留跨模式 zoom，需要重新评估此逻辑。
- **persisted 设置兼容**: 已有的 `defaultHumanisticFilterProfileId` 如果是被移除的 ID（`humanistic-original` 或 `humanistic-vivid`），`resolvedDefaultStyleIndex()` 会 fallback 到 index 0（即 `humanistic-street`），无需数据迁移。
