# Package 01 — Current IQ Gap Audit: Evidence

## Package ID

`01-current-iq-gap-audit`

## Audit Date

2026-05-27

## Worktree / Branch

Worktree: `.claude/worktrees/01-current-iq-gap-audit` / Branch: `worktree-01-current-iq-gap-audit`

## Commands Run

```bash
rg -n "PerceptualColorRecipe|ColorLabSpec|StyleColorPipeline|PreviewColorTransform|PhotoAlgorithmPostProcessor|HumanisticModePlugin|exposure|whiteBalance|highlight|ShotCompleted|ShotFailed" core app feature docs/plans
grep -rn "CaptureFeedback\|SUPPRESS_UNTIL\|captureFeedback\|ALLOW_PREVIEW" core/
ls docs/plans/2026-05-25-*.md docs/plans/2026-05-24-vivo-x300-*.md docs/plans/2026-05-22-vivo-x300-*.md
```

Output: 400+ matches across core/settings, core/effect, core/session, core/device, feature/mode-humanistic, feature/mode-photo, app/src. No test execution required — all findings are code-path and doc-path analysis.

## Source / Doc References Used

| Reference | Role in Audit |
|---|---|
| `core/settings/src/main/kotlin/com/opencamera/core/settings/PerceptualColorRecipe.kt` | Recipe data model: toneLift/toneDepth/chromaBoost/warmthBias/tintBias/shadowTint/highlightTint + neutral/skin protection |
| `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt` | Two-axis palette mapping → FilterRenderSpec adjustments |
| `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt` | Style-aware pipeline: NATURAL/TEXTURE/VIVID/MONOCHROME secondary color lab |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt` | 4x5 color matrix builder from FilterRenderSpec for preview |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` | Preview adaptation: recipe → tint overlay (not color matrix) |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt` | Render recipe V2 single-truth bridge from ShotResult/ShotRequest/EffectSpec |
| `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` | Saved JPEG per-pixel postprocess with recipe-aware adjustments + mask-aware variant |
| `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt:77-97` | CaptureFeedbackPolicy: SUPPRESS_UNTIL_SAVED_MEDIA when recipe is non-neutral |
| `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` | ManualControlCapabilityMatrix: exposure/WB support levels per device |
| `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt` | Humanistic mode: 5 styles, Pro variant, frame ratio, 1.3x zoom |
| `docs/plans/2026-05-24-vivo-x300-real-device-feedback-index.md` | vivo X300 10-issue feedback index |
| `docs/plans/2026-05-24-vivo-x300-color-lab-strength.md` | Color Lab strength enhancement (implemented) |
| `docs/plans/2026-05-25-color-lab-real-device-followup-index.md` | Post-repair validation blockers and real-device status |
| `docs/plans/2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md` | Color Lab V2 perceptual rendering plan |
| `docs/plans/2026-05-25-humanistic-mode-reopen-35mm-styles.md` | Humanistic mode reopen with 35mm + 3 styles |
| `docs/plans/2026-05-22-vivo-x300-output-consistency-qa.md` | Output consistency QA checklist (TC-1 to TC-6) |

## IQ Gap Classification Table

| IQ Dimension | Classification | Evidence & Reasoning |
|---|---|---|
| **Exposure stability** | `vendor-only` | OpenCamera delegates exposure to CameraX/auto-exposure. `exposureCompensationSteps` exists (`SessionContracts.kt:320`, `DeviceContracts.kt:75`) but is a per-shot offset, not metering strategy. vivo ISP has scene-aware multi-zone metering, AE lock with subject tracking, and multi-frame exposure fusion. No realistic path to match vendor metering intelligence. |
| **Sky / highlight detail** | `feasible` | `highlightCompression` exists in `FilterRenderSpec` (`SettingsDataModels.kt:25`) and flows through `StyleColorPipeline` → `PhotoAlgorithmPostProcessor.applyHighlightShadow()` (`PhotoAlgorithmPostProcessor.kt:1047-1063`). Compression threshold is fixed at 168/255 with max ratio 0.45. **Feasible improvement**: adaptive threshold based on histogram, scene-aware sky detection, or luminance-zone-targeted compression. Multi-frame HDR is `vendor-only`. |
| **Lamp highlight control** | `feasible` | Same `highlightCompression` path as sky. Current implementation treats all highlights uniformly above 168/255. **Feasible improvement**: specular highlight detection (very bright + small area), localized rolloff curve, or highlight tint control via `PerceptualColorRecipe.highlightTint` (already modeled at `PerceptualColorRecipe.kt:23`). Cannot match vendor ISP localized tone mapping. |
| **Shadow noise** | `vendor-only` | `shadowLift` (`FilterRenderSpec`) lifts shadows but amplifies noise. `PhotoAlgorithmPostProcessor` applies uniform shadow lift per pixel. vivo ISP uses multi-frame temporal noise reduction, per-pixel noise profiling, and sensor-specific dark frame subtraction. Single-frame shadow lift without denoise will always be noisy in dusk/night. No denoise path exists in OpenCamera pipeline. |
| **Local contrast / microcontrast** | `feasible` | Current `contrast` is global only (`applyContrast` at `PhotoAlgorithmPostProcessor.kt:1043`). `sharpnessBoost` uses 3x3 unsharp mask (`averagedChannel` at line 1066). **Feasible improvement**: add a local contrast pass (e.g., unsharp mask at large radius for mid-frequency contrast, or bilateral-filter-based local tone mapping). The `softGlowStrength` and `haloStrength` fields exist but are vignette-adjacent, not true local contrast. |
| **Sharpening / texture** | `feasible` | `sharpnessBoost` exists as 3x3 unsharp mask (`PhotoAlgorithmPostProcessor.kt:432-436`). TEXTURE color science (`StyleColorScience.TEXTURE`) uses lower saturation + higher contrast split. **Feasible improvement**: edge-aware sharpening (guided filter or bilateral), radius control, luminance-only sharpening to avoid color fringing. Current implementation sharpens RGB channels independently. |
| **White balance** | `implemented-unverified` | `whiteBalanceKelvin` supported in `ManualCaptureParams` (`SettingsDataModels.kt:47`). Device capability is per-device: `ManualControlSupport.APPLY` or `.SAVED_ONLY` or `.UNSUPPORTED` (`DeviceContracts.kt:78`). `CameraXCaptureAdapterManualRequestTest` verifies WB translation. **Gap**: no AWB scene analysis or WB preset matching in OpenCamera; relies entirely on CameraX AWB. Per-device WB behavior needs real-device verification. |
| **Color atmosphere** | `implemented-unverified` | `PerceptualColorRecipe` controls warmth/tint/chroma/shadow tint/highlight tint. `StyleColorPipeline` produces style-aware recipes. `ColorLabSpec.toMapping()` was enhanced in 2026-05-24 plan. `PhotoAlgorithmPostProcessor.applyPerceptualAdjustments()` applies per-pixel recipe. **Gap**: vivo X300 feedback says "角落效果不明显" — enhanced mapping was implemented but real-device visual QA is incomplete (`2026-05-25-color-lab-real-device-followup-index.md:49`). |
| **Preview / saved consistency** | `feasible` | **Fundamental architectural gap**: `PreviewEffectAdapter.buildRecipeColorTransform()` (`PreviewEffectAdapter.kt:65-97`) produces a tint overlay (RGBA color + alpha), NOT a color matrix. `PreviewColorTransform.fromSpec()` builds a 4x5 matrix from `FilterRenderSpec` but `PreviewEffectAdapter` prefers the spec-based matrix when available (`line 53`), falling back to recipe tint. `PhotoAlgorithmPostProcessor` does full per-pixel processing with `applyPerceptualAdjustments()` (line 445-452). The preview path cannot express per-pixel tone curve, chroma, highlight/shadow, or neutral/skin protection. `CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA` hides raw preview for non-neutral recipes (`SessionContracts.kt:82-96`), so users see no feedback until save completes. **Feasible upgrade**: feed recipe into `PreviewColorTransform.fromSpec()` path (build a recipe-derived `FilterRenderSpec` for preview matrix) so preview at least uses a color matrix instead of a tint overlay. |
| **Capture / save reliability** | `implemented-unverified` | `PhotoAlgorithmPostProcessor` has full fail-soft: mask resolve failures fall back to maskless render (`MaskResolveResult.Fallback`), render exceptions caught at line 203, pipeline notes logged. `RenderRecipe.from(ShotResult)` bridges metadata to postprocess (`RenderRecipe.kt:84-108`). **Known blockers** from `2026-05-25-color-lab-real-device-followup-index.md:49-55`: `PhotoAlgorithmPostProcessorTest` failures, mask source/decode path exceptions, `CameraSessionCoordinatorTest` failures. Local deterministic repair was completed but real-device smoke is pending. |

## What OpenCamera Should NOT Promise

1. **Match vivo system camera ISP**: vivo uses proprietary BlueImage ISP with hardware-level multi-frame HDR, temporal noise reduction, scene-aware metering, and per-pixel tone mapping. OpenCamera operates on single CameraX frames with software-only post-processing. Do not claim parity.

2. **Multi-frame noise reduction**: OpenCamera captures single frames. Dusk/night shadow noise will always be worse than vendor multi-frame stacking. Do not promise clean shadows in low light.

3. **True scene-adaptive exposure**: OpenCamera delegates exposure to CameraX auto-exposure. Do not promise metering behavior that matches vendor scene recognition.

4. **Pixel-perfect preview/saved parity**: Preview uses a color matrix or tint overlay; saved output uses per-pixel processing. These are fundamentally different pipelines. Do not promise identical appearance.

5. **ISP-level highlight rolloff**: Vendor ISPs apply localized tone curves before the frame reaches the app. OpenCamera can only do global or semi-global post-processing on the received frame.

6. **Hardware-accelerated local tone mapping**: OpenCamera's per-pixel loop runs on CPU. Real-time local tone mapping at full resolution is not feasible without GPU compute.

## Highest-Leverage Future Implementation Surfaces

| # | Surface | Files | Leverage |
|---|---|---|---|
| 1 | **Recipe-aware preview color matrix** | `PreviewEffectAdapter.kt:65-97`, `PreviewColorTransform.kt:58-101` | Replace tint overlay with recipe-derived `FilterRenderSpec` → `PreviewColorTransform.fromSpec()` color matrix. Single change improves preview/saved direction consistency for ALL color science modes. |
| 2 | **Adaptive highlight compression** | `PhotoAlgorithmPostProcessor.kt:1047-1063`, `StyleColorPipeline.kt:124` | Make highlight compression threshold and ratio scene-adaptive (histogram-based or zone-based) instead of fixed 168/255 threshold. Improves sky detail and lamp highlights in dusk/night. |
| 3 | **Local contrast pass** | `PhotoAlgorithmPostProcessor.kt:366-478` | Add a large-radius unsharp mask or bilateral filter pass for mid-frequency local contrast. Currently only global `contrast` and small-radius `sharpnessBoost` exist. Separate texture from noise in low light. |
| 4 | **Edge-aware / luminance-only sharpening** | `PhotoAlgorithmPostProcessor.kt:432-436` | Replace per-channel 3x3 unsharp mask with luminance-only edge-aware sharpening (guided filter). Reduces color fringing and noise amplification in dusk/night. |
| 5 | **Capture feedback while saving** | `SessionContracts.kt:82-96`, `PreviewRecoverySessionProcessor.kt:215` | Show a low-fidelity preview (even tint overlay) during save instead of suppressing all feedback. User sees direction immediately, then gets saved result. Addresses "有动画但没有图片" perception. |
| 6 | **Perceptual recipe in preview matrix path** | `PreviewEffectAdapter.kt:99-101`, `PerceptualColorRecipe.kt:16-35` | Build a `FilterRenderSpec` from `PerceptualColorRecipe` (reverse-map recipe fields to spec fields) and feed it through `PreviewColorTransform.fromSpec()`. This gives preview a proper color matrix with shadow lift + highlight compression, not just tint. |

## Unresolved Risks And Sample Evidence Needed

| Risk | Evidence Needed |
|---|---|
| Color Lab "角落效果不明显" on real device | vivo X300 sample JPEGs with Color Lab at corners/edges, compared to neutral. Enhanced mapping was implemented but never visually verified on device. |
| Preview/saved direction mismatch | Side-by-side preview screenshot vs saved JPEG for same scene with non-neutral Color Lab. Currently impossible to verify without device. |
| Dusk/night shadow noise severity | Sample JPEGs from vivo X300 in dusk/night with Humanistic mode. Quantify noise gap vs system camera. |
| Highlight rolloff in mixed lighting | Sample JPEGs with bright lamps + dark surroundings. Compare lamp area clipping between OpenCamera and system camera. |
| White balance consistency across scenes | Sample pairs across daylight/tungsten/fluorescent/mixed. Verify CameraX AWB produces acceptable results on vivo X300. |
| Capture save reliability after repair | Smoke test: 10 consecutive shots with Color Lab active, verify all 10 produce saved JPEGs with correct metadata. Pending from `2026-05-25-color-lab-real-device-followup-index.md`. |

## Self-Certification

- Only `docs/plans/humanistic-image-quality-tuning-orchestration/status/01-current-iq-gap-audit.md` was written.
- No runtime code, tests, resources, Gradle files, `INDEX.md`, package docs, other status files, or `codex/documentation.md` were edited.
- All findings are based on read-only source and doc analysis.
