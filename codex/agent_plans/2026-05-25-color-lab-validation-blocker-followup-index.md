# Color Lab Validation Blocker Follow-Up Index

日期：2026-05-25

## Goal

把外部 agent 已交付实现后的本地验收失败收敛成三个可执行修复包：先修 recipe 从 mode/effect 到 capture metadata/saved render 的断链，再修 preview 不消费 recipe 的一致性缺口，最后修后处理 fail-soft 与当前 app focused tests 失败。该包是 [`Color Lab Real-Device Follow-Up Index`](2026-05-25-color-lab-real-device-followup-index.md) 的验收阻断后续，不替代已批准的 Rendering 2.0 总包。

## User Request

- 用户最初真机反馈：
  - `7，色彩实验室最大效果依然偏淡。`
  - `8，选择了色彩实验室以后，点击拍照虽然有动画，但没有图片拍摄。`
- 用户随后要求：`文档已经交付外部agent完成，验证项目中当前的落地效果`。

## Validation Result

当前落地效果不通过原验收。仓内可验证缺口如下：

- `PerceptualColorRecipe` 已在 mode plugin 侧生成，但 `EffectBridge.toMetadataTags(...)` 未把 recipe 字段写入 capture metadata；`PhotoAlgorithmPostProcessor` 因而仍会读到中性 recipe。
- `PreviewEffectAdapter` 没有消费 `FilterEffect.recipe`，预览仍主要是 tint/overlay 近似，无法表达 Color Lab 最大效果。
- `PhotoAlgorithmPostProcessor` 的 mask source 读取仍有异常逃逸风险，且当前 app focused tests 已失败。
- `CameraSessionCoordinatorTest` 中 still quality/resolution bind rebind 断言失败，需要确认是否为同轮变更引入的 session/device bind 回归或测试预期未同步。

## Package Documents

| Work Package | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Color Lab Recipe Metadata Bridge Repair](2026-05-25-color-lab-recipe-metadata-bridge-repair.md) | Text/code agent | planned | 修复 non-neutral recipe 未进入 capture metadata/saved JPEG render 的断链。 |
| [Color Lab Preview Recipe Transform Repair](2026-05-25-color-lab-preview-recipe-transform-repair.md) | Text/code agent + Codex/user visual QA | planned | 让 preview 消费同一 recipe，至少给出 truthful approximate/degraded transform。 |
| [Color Lab Postprocess Fail-Soft And Test Repair](2026-05-25-color-lab-postprocess-failsoft-test-repair.md) | Text/code agent | planned | 修复 mask/后处理 fail-soft 缺口和当前 app focused test failures。 |

## Dependencies And Recommended Order

1. 先执行 `Color Lab Postprocess Fail-Soft And Test Repair`，因为“拍照有动画但无图片”是最高风险 P0，且当前 app focused tests 已红。
2. 再执行 `Color Lab Recipe Metadata Bridge Repair`，让保存链路能接到非中性的 Color Lab recipe。
3. 最后执行 `Color Lab Preview Recipe Transform Repair`，把预览追到同一 recipe 语义上，并保留 Codex/user 真机视觉验收。

如果实现 agent 只能串行处理一个包，优先级按上述顺序执行。若多 agent 并行，第二和第三包会触碰 `core/effect` 相关 contract，应由一个 integrator 最后合并并跑完整验证。

## Related Recent Plans Used As References

- [Rendering 2.0 Capture Save Reliability](2026-05-25-rendering-2-0-capture-save-reliability.md)
- [Rendering 2.0 Render Recipe Single Truth](2026-05-25-rendering-2-0-render-recipe-single-truth.md)
- [Rendering 2.0 Color Lab Perceptual Rendering](2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md)
- [Scene Mask Diagnostic Honesty Repair](2026-05-25-scene-mask-diagnostic-honesty-repair.md)
- [Saved Mask Production Wiring](2026-05-25-saved-mask-production-wiring.md)

## Package Acceptance Criteria

- Color Lab active capture either produces a saved image with postprocess notes or reports explicit degraded/failure state; no silent animation-only path remains.
- Non-neutral `PerceptualColorRecipe` is visible in capture metadata and is the recipe used by saved-photo postprocess.
- Preview consumes the same recipe contract and reports approximate/degraded fidelity honestly.
- Current focused app tests and relevant core settings/effect tests pass.
- Stage 7 gate is rerun only after focused failures are green.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.StyleColorPipelineTest --tests com.opencamera.core.settings.PerceptualColorRecipeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.EffectBridgeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.color lab filtered photo suppresses raw capture feedback"
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Codex-Retained Work

- Final vivo X300 or current target-device visual acceptance: preview, saved JPEG, and thumbnail/gallery must be compared on real media.
- Judgment of “最大效果明显但自然” stays with Codex/user, not a text-only implementation agent.

## Risks And Notes

- This is a blocker follow-up for failed validation, not a new stage transition.
- The Rendering 2.0 plans are broader; these docs intentionally scope down to the exact confirmed gaps from current validation.
