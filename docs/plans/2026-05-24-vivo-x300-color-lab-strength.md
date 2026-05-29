# 参考设备 Color Lab 效果强度修复方案

日期：2026-05-24

覆盖用户问题：9，“色彩实验室调色板，即便点选角落区域，预览和成片效果还是不太明显，应适当加大力度。”

## 目标

增强 Color Lab 二维调色板在边角区域的预览和成片可感知程度，同时保持中间区域自然、可控，不把照片推到过饱和或明显偏色。

## 现有代码入口

重点文件：

- `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/StyleColorPipelineTest.kt`
- `app/src/main/java/com/opencamera/app/FilterPaletteView.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`

当前强度线索：

- `ColorLabSpec.toMapping()` 中 edge 映射偏保守：
  - brightness：`+10 / -8`
  - contrast：`-0.12 / +0.16`
  - saturation：`colorMagnitude * 0.14 - abs(tone) * 0.02`
  - warmth：`color * 12`
  - tint：`-color * 3`
  - shadowLift：`airyTone * 0.18`
  - highlightCompression：`airyTone * 0.08 + deepTone * 0.12`
  - warm/cool boost：`0.18`
- `FilterPaletteView` 已正确把角落映射到 `colorAxis/toneAxis` 的 `-1..1`，问题不是点位无法到边界，而是映射力度偏弱。
- `MainActivity.handleFilterPaletteTouch()` 在 Color Lab route 中直接调用 `colorLabPaletteUpdateAction()`，预览和成片都依赖同一 `ColorLabSpec`，因此增强 core mapping 可同时影响两者。

## 推荐方案

只增强 `ColorLabSpec.toMapping()` 的映射曲线，不改触摸坐标、不改 UI reticle、不引入新设置项。

具体策略：

1. 使用轻微非线性曲线，让中间区仍温和，边角区更明显：
   - 对 `colorAxis` 和 `toneAxis` 使用 `signedCurve = sign(x) * abs(x).pow(0.85f)` 或等价 helper。
   - 若不想引入 `pow`，也可直接线性增强，但非线性更符合“角落更明显，中间不炸”的需求。
2. 提高边界映射上限：
   - brightness：由 `+10 / -8` 提到约 `+16 / -14`
   - contrast：由 `-0.12 / +0.16` 提到约 `-0.18 / +0.24`
   - saturation：由 `0.14` 提到约 `0.24`，tone 抑制从 `0.02` 维持或降到 `0.01`
   - warmth：由 `12` 提到约 `20`
   - tint：由 `3` 提到约 `6`
   - shadowLift：由 `0.18` 提到约 `0.26`
   - highlightCompression：由 `0.08/0.12` 提到约 `0.12/0.18`
   - warm/cool boost：由 `0.18` 提到约 `0.28`
3. 同步放宽 `ColorLabMapping.applyTo()` clamp：
   - brightness 可保持 `-24..32`，若上面增强后仍被截断不多，不必扩大。
   - contrast 从 `0.82..1.32` 可放到 `0.78..1.42`。
   - saturation 从 `0.72..1.38` 可放到 `0.68..1.52`。
   - warmth/tint 从 `-24..24` 可保持或放到 `-32..32`。建议放到 `-32..32` 以允许角落可见。
   - shadow/highlight/warm/cool boost 从 `0..0.38` 可放到 `0..0.48`。

二次审查补充：

- `pow(0.85)` 会让中等输入也变强，例如 `0.5` 会变成约 `0.55`。这符合增强方向，但如果真机中段过猛，可改用分段曲线：`abs(x) < 0.45` 时保持线性，超过后再加速。
- 不建议一开始同时大幅放宽所有 clamp。更稳的落地方式是两步：先增强 mapping 并保留 clamp，测试发现边角被 clamp 截断后再放宽对应字段。
- 方案里的阈值测试应保留少量余量，避免 roundToInt 和不同基础 spec 导致脆弱测试。比如 brightness/warmth 用 `>= 15` / `>= 18` 这种范围，不要断言精确值。
- 需要额外验证 `PersistedSettingsSerializerTest`，因为 `ColorLabSpec` 序列化包含 `strength/colorAxis/toneAxis`。虽然本方案不改字段，但增强 mapping 后老设置会立刻变强，属于有意行为，需在 release note 或 documentation 中说明。

## 具体实现提示

在 `ColorLabSpec.kt` 中新增私有 helper：

```kotlin
private fun signedPaletteCurve(value: Float): Float {
    val normalized = value.coerceIn(-1f, 1f)
    val magnitude = kotlin.math.abs(normalized)
    val curved = magnitude.pow(0.85f)
    return if (normalized < 0f) -curved else curved
}
```

然后在 `toMapping()` 中：

- `val color = signedPaletteCurve(spec.colorAxis) * spec.strength`
- `val tone = signedPaletteCurve(spec.toneAxis) * spec.strength`

如果 Kotlin import 需要：

```kotlin
import kotlin.math.pow
```

注意：`Float.pow(Float)` 是否可用取决于 Kotlin stdlib 版本；若不可用，用 `magnitude.toDouble().pow(0.85).toFloat()` 并 import `kotlin.math.pow`。

## 测试要求

更新 `ColorLabSpecTest`，不要只断言大于 0，要增加边界强度阈值，防止后续又被改弱：

1. `right color axis warms image without enabling cool boost`
   - 断言 `warmthShift >= 18`
   - 断言 `warmBoost >= 0.24f`
   - 断言 `saturation >= 1.20f`
2. `left color axis cools image without enabling warm boost`
   - 断言 `warmthShift <= -18`
   - 断言 `coolBoost >= 0.24f`
   - 断言 `saturation >= 1.20f`
3. `positive tone axis creates airy lifted tone`
   - 断言 `brightnessShift >= 14`
   - 断言 `contrast <= 0.84f`
   - 断言 `shadowLift >= 0.24f`
4. `negative tone axis creates deep contrast tone`
   - 断言 `brightnessShift <= -12`
   - 断言 `contrast >= 1.20f`
   - 断言 `highlightCompression >= 0.16f`
5. 增加组合角落测试：
   - `ColorLabSpec(colorAxis = 1f, toneAxis = -1f)` 应同时体现暖色、较高对比、明显饱和度。
   - `ColorLabSpec(colorAxis = -1f, toneAxis = 1f)` 应同时体现冷色、提亮、阴影抬升。

跑：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## 真机验收

安装新 APK 后：

```bash
rtk adb install -r -d <HOME>/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
```

手动路径：

1. 打开相机。
2. 进入 `色彩实验室`。
3. 点调色板四个角：
   - 右上：暖色 + 提亮，应明显。
   - 右下：暖色 + 深对比，应明显。
   - 左上：冷色 + 提亮，应明显。
   - 左下：冷色 + 深对比，应明显。
4. 拍照保存，打开缩略图确认成片与预览方向一致。

## 不做事项

- 不新增新的 Color Lab UI 控件。
- 不把 `strength` 默认值改到超过 `1f`，避免破坏序列化和旧设置语义。
- 不在 `PreviewOverlayView` 上单独增强 tint，预览和成片必须走同一 render spec 语义，避免“预览很强、成片不同”。
- 不把调色板坐标做“吸边”或“角落放大命中”来伪造强度；用户问题是效果力度，不是触点无法到达边界。
