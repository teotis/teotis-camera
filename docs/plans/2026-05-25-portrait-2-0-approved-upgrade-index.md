# Portrait 2.0 Approved Upgrade Index

日期：2026-05-25

## Goal

把用户已认可的人像 2.0 升级范围沉淀成可交给非多模态 agent 的执行包：`Mask-aware` 人像成片、背景光斑/高光形态、景深/虚化强度滑杆、人像 Profile 体系重整。这个总包只规划执行边界，不改运行时代码。

## User Request

- 认可规划如下升级：`Mask-aware` 人像成片、背景光斑/高光形态、景深/虚化强度滑杆、人像 Profile 体系重整。
- 这些能力必须服从当前项目架构：UI 只渲染和分发意图；Mode Plugin 描述产品选择；Session Kernel 仍是唯一运行态 owner；Device Adapter 和 Media Pipeline 承接执行与后处理；硬件/算法相关能力必须有 `supported / degraded / unsupported` 语义。

## Package Documents

| Work Package | Owner | Status | Outcome / Next Action |
| --- | --- | --- | --- |
| [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md) | Text/code agent + Codex visual QA | blocked | 已有近期方案，纳入人像 2.0 首个实现闭环；当前验收发现 mask-aware app 测试失败且成功路径未写回输出，先执行 blocker 修复包。 |
| [Portrait 2.0 Profile System Realignment](./2026-05-25-portrait-2-0-profile-system-realignment.md) | Text/code agent | planned | 重整 `PortraitProfile`、beauty、bokeh/light-spot、filter/style 的职责边界，避免同一产品语义被多个模块重复解释。 |
| [Portrait 2.0 Depth Slider And Bokeh Strength](./2026-05-25-portrait-2-0-depth-slider-and-bokeh-strength.md) | Text/code agent | planned | 把当前离散 `PortraitBokehEffect` 和 style `bokehStrength` 升级为可持久化、可测试的虚化强度控制。 |
| [Portrait 2.0 Background Light Spot And Highlight Shape](./2026-05-25-portrait-2-0-background-light-spot-highlight.md) | Text/code agent + Codex visual QA | planned | 为背景区域增加保守的光斑/高光形态 spec 和渲染路径，不宣称真镜头级光学重建。 |
| [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md) | Text/code agent | planned | 作为上述三个新包的共享 contract 参考，确保 subject/background 语义集中到同一 layered spec。 |
| [Portrait Mask Visual QA And Acceptance](./2026-05-25-portrait-mask-visual-qa.md) | Codex/user QA | planned | 实现落地后用真机保存 JPEG 和预览录屏做最终视觉验收。 |
| [Portrait 2.0 Landing Validation Blockers](./2026-05-25-portrait-2-0-landing-validation-blockers.md) | Text/code agent | blocked | 修复当前验收发现的 mask-aware 输出写回、light-spot mask path notes、测试 setup 和 edge softness failures。 |

## Related Recent Plans Used

- [Portrait Reopen On Scene Mask Foundation Index](./2026-05-25-portrait-reopen-mask-foundation-index.md)
- [Preview Analysis Fanout Stability](./2026-05-25-portrait-preview-analysis-fanout-stability.md)
- [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md)
- [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md)
- [Core Chain Landing Validation Index](./2026-05-25-core-chain-landing-validation-index.md)
- [Scene Mask Diagnostic Honesty Repair](./2026-05-25-scene-mask-diagnostic-honesty-repair.md)

## Recommended Execution Order

1. **P0: Preview analysis fanout stability**
   - Execute [Preview Analysis Fanout Stability](./2026-05-25-portrait-preview-analysis-fanout-stability.md) before adding any preview-dependent portrait behavior.
   - This protects Live preview frames, preview scene mask, and future scene signals from `ImageProxy` ownership collisions.

2. **P1: Mask-aware saved portrait**
   - Execute [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md).
   - This makes saved JPEG output consume a real person subject mask before more product controls are exposed.

3. **P2: Profile system and layered effect contract**
   - Execute [Portrait 2.0 Profile System Realignment](./2026-05-25-portrait-2-0-profile-system-realignment.md) together with or immediately before [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md).
   - This stabilizes the product model for profile, beauty, style, bokeh, and light-spot decisions.

4. **P3: Depth slider**
   - Execute [Portrait 2.0 Depth Slider And Bokeh Strength](./2026-05-25-portrait-2-0-depth-slider-and-bokeh-strength.md).
   - The slider should feed the layered effect spec and existing capture metadata, not bypass the mode/session/media boundaries.

5. **P4: Background light spot and highlight shape**
   - Execute [Portrait 2.0 Background Light Spot And Highlight Shape](./2026-05-25-portrait-2-0-background-light-spot-highlight.md).
   - This depends on mask-aware rendering and the layered spec, because it must affect background regions only.

6. **P5: Visual acceptance**
   - Execute [Portrait Mask Visual QA And Acceptance](./2026-05-25-portrait-mask-visual-qa.md).
   - Codex/user retains final visual judgment for skin tone, edge halos, bokeh intensity, and light-spot taste.

## Delegation Model

- **Delegable to non-multimodal agents**
  - Settings enums, serializer/reducer tests, render model controls, metadata mapping, pure resolver tests.
  - `PortraitRenderPostProcessor` contract changes and synthetic mask bitmap tests.
  - Pipeline notes and degradation semantics.

- **Codex-retained work**
  - Final visual judgment for real portrait photos.
  - Deciding whether a profile/light-spot preset looks too artificial after implementation.
  - Confirming preview and saved JPEG remain product-consistent on real devices.

- **Blocked or deferred**
  - True optical depth estimation, hair-level matting, vendor beauty SDK integration, real-time video portrait, and Live dynamic-segment portrait effects.

## Global Acceptance Criteria

- Saved portrait photos can use a person subject mask to split subject and background rendering.
- Profile, beauty, depth, bokeh, light-spot, and filter choices flow through one explicit layered portrait spec.
- User-facing controls do not create hidden session state or direct CameraX calls.
- Missing/failed mask, weak confidence, unsupported depth, or thermal/resource pressure results in visible `degraded / unsupported` notes, not silent failure.
- Existing portrait capture, Live default behavior, watermark ordering, selfie mirror, frame ratio, and Stage 7 observability gate remain intact.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Final Conclusions

- The approved scope is valid and stage-local as a planned feature package, but implementation should not be mixed into the current Stage 7 stability exit unless the user explicitly authorizes feature work.
- The first useful implementation loop is `Mask-aware saved portrait`; the slider and light-spot controls should wait until foreground/background semantics are real.
- Non-multimodal agents can implement most deterministic code/test work, while Codex/user should retain final image-quality acceptance.

## Validation Result 2026-05-25

- Status: `blocked`.
- Passing evidence:
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest --tests com.opencamera.core.effect.PortraitLayeredEffectResolverTest`
- Failing evidence:
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest`
  - Failures: `available mask routes to mask-aware editor`, `mask-aware path preserves portrait mode metadata and downstream result`, `mask with edge softness has smooth transition`.
- Blocking conclusion:
  - Current app-layer landing cannot be accepted because mask-aware saved rendering does not pass focused tests and the production mask-aware path mutates a decoded bitmap without writing the changed pixels back to the output target.
  - Execute [Portrait 2.0 Landing Validation Blockers](./2026-05-25-portrait-2-0-landing-validation-blockers.md) before visual QA or product pass.
