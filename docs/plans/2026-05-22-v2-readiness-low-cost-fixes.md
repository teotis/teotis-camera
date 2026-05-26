# OpenCamera 2.0 Readiness Low-Cost Fixes

> 用途：可直接交给外部非多模态 agent 立即修复。  
> 范围：低解决成本、低架构风险、主要是文案、命名、测试、显式状态、轻量 UI 反馈。  
> 禁止：不要实现 RAW、真实多帧、Live motion、provider death 真机专项；这些属于高成本决策项。

## 1. 目标

清掉 2.0 准入中“成本低但影响观感和可信度”的问题，让下一轮审查不再被明显的 i18n、命名漂移、测试旧期望和提示缺口绊住。

## 2. 任务清单

### L1. 用户可见文案迁移到资源或 `AppTextResolver`

来源：`UI-Static-Audit.md` Finding 1、2、8。

问题：

- `MainActivity.kt`、`SessionUiRenderModel.kt` 中存在硬编码中文。
- `activity_main.xml` 中有 `android:text="Back"` 硬编码英文。
- `"Starting..."`、`"Recording"`、`"Saving..."`、`"Color: %.2f, Tone: %.2f"` 等用户可见英文/格式串未走资源。

要求：

- 所有用户可见文案进入 `app/src/main/res/values/strings.xml` 和 `values-en/strings.xml`，或现有 `AppTextResolver`。
- 保持默认中文。
- 添加或更新 render model 测试，覆盖至少：
  - recording/saving 状态文案；
  - frame ratio disabled reason；
  - color/tone 坐标展示；
  - back button 文案资源。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionStateRenderTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

### L2. 统一 `风格 / 色彩实验室` 内部命名或补显式映射测试

来源：`UI-Static-Audit.md` Finding 3、4；主控 P1。

问题：

- `buttonColorLabEntry` 点击打开 `CockpitPanelRoute.LensLab`，语义容易误导后续 agent。
- 内部 `FilterLab` 对应用户可见 `风格`，`LensLab` 对应用户可见 `色彩实验室`，映射隐式。

低成本可选方案：

1. 推荐：将内部 route 命名迁移为 `StyleLab / ColorLab`。
2. 保守：暂不改 route 名，但在 `CockpitPanelRouteTest` 或 `CameraCockpitRenderModelTest` 中显式断言：
   - `buttonFilterEntry` 打开用户语义 `风格`；
   - `buttonColorLabEntry` 打开用户语义 `色彩实验室`；
   - 文案和 route 映射不可混淆。

验收：

- 顶部按钮仍显示 `色彩实验室`。
- 侧栏按钮显示 `风格 / 快捷 / Dev`。
- 不把 `设置` 或 `色彩实验室` 放回侧栏。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

### L3. 清理侧栏遗留 `设置`/Lens rail 资源

来源：`UI-Static-Audit.md` Finding 5。

问题：

- `button_lens_rail` 字符串仍为 `设置`。
- `buttonLensLabEntry` 在 XML 中 hidden，但语义和资源仍容易误导。

要求：

- 删除或明确废弃隐藏的旧侧栏设置/镜头实验室入口。
- 确保侧栏仅包含 `风格 / 快捷 / Dev`。
- 若暂时不能删 XML 节点，需要注释说明迁移原因，并确保不会被渲染或测试当作有效入口。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

### L4. 修复 humanistic mode 两个旧期望测试

来源：`IO-Chain-Audit.md`、`Interaction-Flow-Audit.md`、`Stability-Observability-Audit.md`。

问题：

- `DefaultCameraSessionTest` 中两个 humanistic mode 测试期望仍是旧样式：
  - expected `humanistic-portrait`，actual `humanistic-vivid`
  - expected `photo-chasing-light-pro-assist`，actual `photo-original-pro-assist`

要求：

- 先确认当前实现的产品口径是否正确。
- 若当前实现正确，只更新测试期望。
- 若测试揭示真实产品回归，则不要硬改测试；输出阻断说明。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/verify_stage_7_observability.sh
```

### L5. 为 disabled/degraded 增加可持续状态提示，不只依赖 Toast

来源：`UI-Static-Audit.md` Finding 10。

问题：

- `captureConfigDisabledReason()` 的原因主要通过 `Toast` 呈现，用户可能错过。

要求：

- 在现有 render model/status strip/capture output 中加入最近一次 disabled reason。
- Toast 可保留，但不能是唯一反馈。
- 不新增 session owner；只做 UI 派生展示。

建议覆盖：

- 拍摄进行中无法更改设置。
- 当前模式不支持画幅。
- 倒计时中无法切换画幅。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

### L6. 权限永久拒绝后提供系统设置引导

来源：`Interaction-Flow-Audit.md` 权限恢复 Risk；主控 P1。

问题：

- `shouldShowRequestPermissionRationale` 与 else 分支逻辑相同，无法区分首次拒绝和永久拒绝。

要求：

- 当系统不再弹权限框时，给出用户可理解的设置引导。
- 可以先做最小 UI：状态文案 + 打开 App Settings 的按钮/动作。
- 不改变 session 权限状态 owner；权限变化仍通过现有 `PermissionsUpdated`。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

### L7. 补充低成本 render model 测试缺口

来源：`UI-Static-Audit.md` Test Gaps。

建议补充：

- `primaryStatusRenderModel()`：录制、保存、权限拒绝、preview error。
- `frameRatioControlRenderModel()`：unsupported、active shot、countdown disabled reason。
- 空 catalog 状态：无 filter/watermark template 时 UI 不崩溃且有合理空态。

建议验证：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## 3. 不要处理的事项

以下不属于低成本任务：

- 真实 RAW / DNG 输出。
- 真实夜景多帧合并算法。
- Live Photo ring buffer 和 motion mux。
- 视频帧级水印烧录。
- provider death 注入、thermal chamber、30 分钟长稳。

## 4. 交付物

- 修改代码和测试。
- 在 `codex/documentation.md` 追加一条最近有效闭环。
- 输出实际运行过的 `rtk` 验证命令和结果。
