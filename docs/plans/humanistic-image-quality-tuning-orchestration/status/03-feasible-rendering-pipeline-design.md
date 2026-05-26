# Package Status: 03-feasible-rendering-pipeline-design

- **Agent**: claude-code (foreground)
- **Status**: completed
- **Started**: 2026-05-27
- **Completed**: 2026-05-27

## Worktree

- Path: `.claude/worktrees/pkg-03-rendering-pipeline`
- Branch: `worktree-pkg-03-rendering-pipeline`

## Changes

- git status: clean before write; only `status/03-feasible-rendering-pipeline-design.md` modified
- git diff --stat: single file
- Changed files: `docs/plans/humanistic-image-quality-tuning-orchestration/status/03-feasible-rendering-pipeline-design.md`

## Verification

- Commands run:
  - `rg -n "ManualControlSupport|DeviceShotRequestTranslator|exposureCompensation|whiteBalance|PreviewColorTransform|RenderRecipe|PerceptualColorRecipe|PhotoAlgorithmPostProcessor|captureFeedbackPolicyFor|ShotCompleted|ShotFailed|diagnostic|RuntimeIssue" core app feature` — 250+ matches confirming full pipeline symbol coverage
  - `git status --short` — confirmed only target file touched
- Test results: N/A (design package, no code changes)

## Evidence

### Source / Doc References

| Source | Role in Design |
|---|---|
| `core/settings/FilterRenderSpec.kt` | 15-parameter render spec: brightnessShift, contrast, saturation, warmthShift, tintShift, monochromeMix, vignetteStrength, softGlowStrength, haloStrength, grainStrength, sharpnessBoost, highlightCompression, shadowLift, warmBoost, coolBoost |
| `core/settings/PerceptualColorRecipe.kt` | 10-field perceptual recipe: toneLift/toneDepth/chromaBoost/warmthBias/tintBias/shadowTint/highlightTint/neutralProtection/skinProtection/previewFidelity |
| `core/settings/StyleColorPipeline.kt` | 4 color sciences (NATURAL/TEXTURE/VIVID/MONOCHROME); TEXTURE split-grade with shadowLift + highlightCompression as primary dusk/night levers |
| `core/settings/ColorLabSpec.kt` | 2-axis palette (colorAxis, toneAxis) + strength → FilterRenderSpec + PerceptualColorRecipe |
| `core/effect/PreviewColorTransform.kt` | 4x5 color matrix builder from FilterRenderSpec: saturation, contrast, brightness, warmth, tint, shadowLift, highlightCompression encoded in matrix |
| `core/effect/PreviewEffectAdapter.kt` | Preview adaptation: spec → color matrix (preferred), recipe → tint overlay (fallback). Tint overlay cannot express per-pixel tone curve, chroma, highlight/shadow |
| `core/effect/RenderRecipe.kt` | Single-truth bridge: EffectSpec/ShotRequest/ShotResult → RenderRecipe with filterProfileId + FilterRenderSpec + PerceptualColorRecipe |
| `app/camera/PhotoAlgorithmPostProcessor.kt` | Saved JPEG per-pixel postprocess: applyHighlightShadow (fixed threshold 168/255), applyContrast (global), averagedChannel 3x3 unsharp mask, applyPerceptualAdjustments |
| `core/session/SessionContracts.kt:82-97` | CaptureFeedbackPolicy: SUPPRESS_UNTIL_SAVED_MEDIA for non-neutral recipes |
| `core/device/DeviceContracts.kt` | ManualControlCapabilityMatrix: exposure/WB support levels per device (APPLY/SAVED_ONLY/UNSUPPORTED) |
| `feature/mode-humanistic/HumanisticModePlugin.kt` | 5 humanistic styles, Pro variant, frame ratio, 1.3x zoom |
| `01-current-iq-gap-audit.md` | IQ gap classification: exposure=vendor-only, highlight=feasible, shadow-noise=vendor-only, local-contrast=feasible, sharpening=feasible, WB=implemented-unverified, color-atmosphere=implemented-unverified, preview/saved=feasible |
| `02-style-target-scorecard.md` | 4 dusk/night profiles (Neutral/Warm Deep/Cool Deep/Soft Dusk), parameter ranges, 8-dimension scorecard, 10 deterministic checks |

---

## Architecture Ownership Map

### §1 Layer Responsibility Matrix

| Layer | Current Responsibility | Proposed Addition for Dusk/Night |
|---|---|---|
| **core/settings** — Style/Recipe Semantics | `FilterRenderSpec` (15 fields), `PerceptualColorRecipe` (10 fields), `ColorLabSpec` (2-axis), `StyleColorPipeline` (4 sciences), 5 humanistic profiles | Dusk/night profile presets mapping to existing fields. No new axes or controls needed. |
| **core/effect** — Preview/Saved Recipe | `PreviewColorTransform.fromSpec()` builds 4x5 matrix; `PreviewEffectAdapter` prefers spec matrix, falls back to recipe tint overlay; `RenderRecipe` bridges metadata | Recipe-to-spec reverse-mapping for preview matrix path. |
| **feature/mode-humanistic** — Style Policy | `HumanisticModePlugin`: 5 styles, Pro variant, frame ratio, 1.3x zoom | Dusk/night style preset selection via existing profile mechanism. |
| **core/device** — Device Request/Capability | `DefaultDeviceShotRequestTranslator`: manual params → device request; `ManualControlSupport` per device | No change. Exposure/WB capabilities are per-device; dusk/night profiles use auto-exposure. |
| **app/camera** — CameraX/Media Integration | `PhotoAlgorithmPostProcessor`: per-pixel postprocess with recipe-aware adjustments; mask-aware variant | Adaptive highlight compression, luminance-only sharpening, local contrast pass. |
| **tests/verification** | Existing tests for PreviewColorTransform, PreviewEffectAdapter, RenderRecipe, CaptureFeedbackPolicy, DefaultDeviceShotRequestTranslator | Deterministic checks C1-C10 from scorecard. |

### §2 Data Flow: Style Selection → Saved JPEG

```
User selects style (e.g., Humanistic Warm Deep)
    ↓
HumanisticModePlugin resolves profile ID + FilterRenderSpec + ColorLabSpec
    ↓
StyleColorPipeline.render() → StyleColorPipelineResult
    ├── finalRenderSpec (FilterRenderSpec with all adjustments)
    ├── perceptualColorRecipe (PerceptualColorRecipe)
    └── notes: ["style-base:warm-deep", "color-lab:texture", "texture:split-grade"]
    ↓
EffectSpec = FilterEffect(profileId, renderSpec, recipe)
    ↓
    ├── Preview path: PreviewEffectAdapter.adapt()
    │   ├── buildColorTransform() → PreviewColorTransform.fromSpec(renderSpec) → 4x5 matrix
    │   └── OR buildRecipeColorTransform(recipe) → tint overlay (fallback)
    │
    └── Saved path: RenderRecipe.from(ShotRequest)
        → PhotoAlgorithmPostProcessor.processCapture()
            ├── applyHighlightShadow() per pixel
            ├── applyContrast() per pixel
            ├── applyPerceptualAdjustments() per pixel (warmth, tint, chroma, shadowTint, highlightTint, neutral/skin protection)
            └── averagedChannel() 3x3 unsharp mask
```

---

## Capability / Degradation Matrix

Every proposed improvement has a capability state per the design boundary contract.

| # | Improvement | Capability State | Rationale |
|---|---|---|---|
| P1 | Dusk/night profile presets (B/C/D from scorecard) | **supported** | Maps directly to existing FilterRenderSpec + PerceptualColorRecipe fields. No new axes needed. StyleColorPipeline already handles TEXTURE color science with split-grade. |
| P2 | Recipe-aware preview color matrix | **supported** | PreviewColorTransform.fromSpec() already builds a full 4x5 matrix from FilterRenderSpec. Need: reverse-map PerceptualColorRecipe → FilterRenderSpec for preview, then feed through existing matrix path. Replaces tint overlay with proper color matrix. |
| P3 | Adaptive highlight compression threshold | **supported** | Current threshold is fixed at 168/255. Can compute histogram of captured frame, find 95th percentile luminance, and use that as threshold. Pure CPU postprocess on already-captured frame. |
| P4 | Luminance-only sharpening | **supported** | Replace per-channel 3x3 unsharp mask with Y-channel-only sharpening in YUV space. Reduces color fringing and noise amplification. No new dependencies. |
| P5 | Large-radius local contrast (unsharp mask at 7-15px radius) | **degraded** | Feasible but slow on CPU at full resolution. Must operate on downsampled luminance channel or be limited to saved JPEG only (not preview). Performance impact needs measurement on target devices. |
| P6 | Exposure stability request policy | **degraded** | OpenCamera delegates to CameraX auto-exposure. Can request exposure compensation bias per profile (e.g., -1 EV for Cool Deep to preserve night density), but cannot control metering strategy. Device capability varies: ManualControlSupport.APPLY vs SAVED_ONLY. |
| P7 | Preview fidelity labeling | **supported** | PreviewColorFidelity enum already exists (NONE/GOOD/APPROXIMATE/MASK_AWARE/FALLBACK/DEGRADED). Need: honest labeling when preview uses tint overlay vs color matrix. Current code already sets APPROXIMATE for non-neutral recipes. |
| P8 | Pipeline diagnostics metadata | **supported** | Pipeline notes system exists in StyleColorPipelineResult.notes and PhotoAlgorithmPostProcessor. Add `color-lab:texture`, `style-base:<profileId>`, `preview:matrix`/`preview:tint-overlay` notes. |
| P9 | White balance scene bias | **unsupported** | No AWB scene analysis in OpenCamera. CameraX AWB is the only source. Can set whiteBalanceKelvin in ManualCaptureParams but this is a fixed override, not scene-adaptive. Per-device behavior varies (APPLY/SAVED_ONLY/UNSUPPORTED). |
| P10 | Multi-frame noise reduction | **unsupported** | OpenCamera captures single frames. Night mode multi-frame fusion is a separate pipeline. No path to apply recipe-aware denoise per-frame before fusion. |
| P11 | ISP-level localized tone mapping | **unsupported** | Vendor ISPs apply per-zone tone curves before the frame reaches the app. OpenCamera receives an already-processed JPEG/YUV frame. Cannot match vendor localized tone mapping. |
| P12 | Capture feedback during save | **supported** | CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA currently hides all feedback for non-neutral recipes. Can show low-fidelity tint overlay preview during save instead of suppressing entirely. SessionContracts change only. |

---

## Proposed Improvements — Staged Design

### Slice 1: Minimal First Implementation (Deterministic, No Device Dependency)

**Goal**: Wire existing profile presets through the full pipeline with honest preview labeling and diagnostics.

| Task | Owner Layer | Files | Description |
|---|---|---|---|
| 1a | core/settings | SettingsDefaults.kt, HumanisticStyleProfile | Add 3 dusk/night profile presets (Warm Deep, Cool Deep, Soft Dusk) to existing humanistic profile set. Each preset is a FilterRenderSpec + ColorLabSpec mapping per scorecard §1 profiles B/C/D. |
| 1b | core/effect | PreviewEffectAdapter.kt:65-97 | Recipe-to-spec reverse-mapping: build a FilterRenderSpec from PerceptualColorRecipe fields (warmthBias → warmthShift, chromaBoost → saturation, toneLift/toneDepth → brightnessShift/contrast, shadowTint → shadowLift, highlightTint → highlightCompression). Feed through PreviewColorTransform.fromSpec() instead of tint overlay. |
| 1c | core/effect | PreviewEffectAdapter.kt:86-90 | Set preview fidelity honestly: APPROXIMATE when using color matrix from recipe, GOOD when using spec directly, DEGRADED only when tint overlay is the only option. |
| 1d | core/effect | RenderRecipe.kt | Add pipeline notes to RenderRecipe: `color-lab:texture`, `style-base:<profileId>`, `preview:matrix` or `preview:tint-overlay`. |
| 1e | tests | PreviewEffectAdapterTest, StyleColorPipelineTest | Deterministic checks: C1 (recipe non-neutral), C4 (preview direction match), C7 (parameter bounds), C8 (pipeline notes), C9 (preview fidelity honest). |

**Verification**: Unit tests pass. Preview produces color matrix (not tint overlay) for non-neutral recipes. Pipeline notes present in RenderRecipe.

### Slice 2: Adaptive Highlight Compression

**Goal**: Replace fixed 168/255 highlight threshold with histogram-adaptive threshold.

| Task | Owner Layer | Files | Description |
|---|---|---|---|
| 2a | app/camera | PhotoAlgorithmPostProcessor.kt:1047-1063 | Compute frame luminance histogram before per-pixel loop. Find 95th percentile luminance. Use as highlight compression threshold instead of fixed 168. Clamp to [140, 220] range. |
| 2b | app/camera | PhotoAlgorithmPostProcessor.kt | Add `highlight-compression:adaptive:<threshold>` to pipeline notes. |
| 2c | tests | PhotoAlgorithmPostProcessorTest | Verify adaptive threshold produces different results than fixed 168 for high-key and low-key frames. C6 (no clipping), C10 (fallback order). |

**Verification**: Unit tests pass. High-key scene (bright sky) gets higher threshold; low-key scene (dark room with lamp) gets lower threshold.

### Slice 3: Luminance-Only Sharpening

**Goal**: Replace per-channel 3x3 unsharp mask with Y-channel-only sharpening to reduce color fringing.

| Task | Owner Layer | Files | Description |
|---|---|---|---|
| 3a | app/camera | PhotoAlgorithmPostProcessor.kt:432-436 | Convert RGB→YUV before sharpening. Apply unsharp mask to Y channel only. Convert back to RGB. Use existing 3x3 kernel radius. |
| 3b | app/camera | PhotoAlgorithmPostProcessor.kt | Add `sharpening:luminance-only` to pipeline notes. |
| 3c | tests | PhotoAlgorithmPostProcessorTest | Verify no color channel clipping after luminance-only sharpening. C6 (saturation guard). |

**Verification**: Unit tests pass. Sharpened image has no color fringing artifacts on high-contrast edges.

### Slice 4: Preview Fidelity Improvement (Deferred)

**Goal**: Show preview feedback during save instead of suppressing entirely.

| Task | Owner Layer | Files | Description |
|---|---|---|---|
| 4a | core/session | SessionContracts.kt:82-97 | Add CaptureFeedbackPolicy.SHOW_LOW_FIDELITY_PREVIEW. When active, show tint overlay during save, then replace with saved result. |
| 4b | app/camera | PreviewRecoverySessionProcessor.kt | Handle low-fidelity preview state: show tint overlay immediately, swap to saved bitmap when ready. |
| 4c | tests | CaptureFeedbackPolicyTest | Verify new policy activates for non-neutral recipes. |

**Verification**: User sees color direction immediately after capture, not blank screen during save.

### Slice 5: Local Contrast Pass (Deferred — Performance Risk)

**Goal**: Add large-radius unsharp mask for mid-frequency local contrast.

| Task | Owner Layer | Files | Description |
|---|---|---|---|
| 5a | app/camera | PhotoAlgorithmPostProcessor.kt | Add local contrast pass: downsample luminance to 1/4 resolution, apply 7x7 unsharp mask, upsample, blend as local contrast boost. |
| 5b | app/camera | PhotoAlgorithmPostProcessor.kt | Add `local-contrast:downsampled-4x` to pipeline notes. |
| 5c | tests | PhotoAlgorithmPostProcessorTest | Performance benchmark: must complete within 2x current postprocess time on target device. |

**Verification**: Local contrast pass completes within performance budget. No visible banding from downsample/upsample.

---

## No-Go List

These ideas are rejected as costly, dishonest, or architecturally violating:

| # | Idea | Reason |
|---|---|---|
| N1 | ML scene classifier for auto style selection | No ML runtime in OpenCamera. Adds model dependency, inference latency, and maintenance burden. Profiles are user-selected, not auto-detected. |
| N2 | Per-pixel noise estimation / adaptive denoise | Requires sensor-specific noise profiles (shot noise, read noise) that are not available from CameraX. Single-frame denoise without noise model produces worse results than accepting noise. |
| N3 | Multi-frame HDR fusion in post-process | OpenCamera receives single frames from CameraX. Night mode multi-frame fusion is a separate pipeline that operates before post-process. Cannot inject recipe into fusion pipeline. |
| N4 | GPU compute for real-time local tone mapping | Requires Vulkan/RenderScript/Metal compute setup. Not in current architecture. Adds GPU dependency and device compatibility risk. CPU post-process on saved JPEG is sufficient. |
| N5 | Scene-adaptive white balance | No AWB scene analysis capability. CameraX AWB is the only source. Manual WB override (ManualCaptureParams.whiteBalanceKelvin) is a fixed value, not scene-adaptive. |
| N6 | Preview pixel-match with saved output | Preview uses color matrix (4x5 linear transform) or tint overlay. Saved output uses per-pixel non-linear processing (tone curve, chroma, highlight/shadow, neutral/skin protection). Fundamentally different pipelines. Direction match is the honest target. |
| N7 | New UI controls for dusk/night | Scorecard §5 non-goal: "No new UI controls." Profiles map to existing Color Lab axes and Humanistic style presets. |
| N8 | Vendor ISP parity claims | Package 01 explicitly classifies exposure, shadow noise, and localized tone mapping as vendor-only. Do not promise or design toward vendor parity. |
| N9 | Hidden second session kernel | Design boundary: UI renders state and dispatches intents only. Session Kernel owns runtime state. No direct UI→runtime control bypass. |
| N10 | Deterministic pass/fail for taste judgments | Scorecard §3 is a 1-5 human review rubric. Sky layers, atmosphere, color tendency require human/Codex real-device review. Deterministic checks (C1-C10) cover binary invariants only. |

---

## Unresolved Technical Risks

| Risk | Impact | Mitigation |
|---|---|---|
| **TEXTURE color science may need dusk/night-specific tuning** | Package 02 risk #2: Current `applyTextureColorLab()` uses fixed multipliers. Profiles B/C/D may need slightly different shadow/highlight response curves. | Slice 1 uses existing TEXTURE path. If real-device review shows TEXTURE is insufficient, add a DUSK variant of TEXTURE in StyleColorPipeline as a follow-up. Not a blocker for first slice. |
| **Night mode multi-frame interaction undefined** | Package 02 risk #3: If user selects Humanistic Warm Deep while Night mode multi-frame fusion is active, recipe application order is unspecified. | Design decision: recipe applies AFTER fusion completes (on the fused output frame). This is consistent with current PhotoAlgorithmPostProcessor flow. Night mode produces a fused frame, then post-process applies recipe. |
| **Local contrast pass performance on low-end devices** | Slice 5 downsample/upsample approach may be too slow on older devices. | Gate behind device capability check. Skip local contrast on devices where postprocess exceeds time budget. Add `local-contrast:skipped-performance` diagnostic note. |
| **Preview recipe-to-spec reverse-mapping fidelity** | Slice 1b: Mapping PerceptualColorRecipe fields to FilterRenderSpec is an approximation. Some recipe fields (neutralProtection, skinProtection) have no direct spec equivalent. | Preview uses color matrix (which encodes saturation, contrast, brightness, warmth, tint, shadowLift, highlightCompression). Neutral/skin protection are per-pixel operations that only apply in saved path. Preview fidelity label must be APPROXIMATE, not GOOD. |
| **Cool Deep profile with mixed lighting** | Package 02 risk #4: Urban night scenes with cool sky + warm sodium lamps may produce inconsistent results with single cool-bias recipe. | Accept as known limitation. Profile targets dominant scene tone. Real-device sample review in package 04 will quantify severity. |

---

## Self-Certification

- [x] Only touched allowed paths (`docs/plans/humanistic-image-quality-tuning-orchestration/status/03-feasible-rendering-pipeline-design.md`)
- [x] Did not edit forbidden paths (runtime code, tests, resources, INDEX.md, package docs, other status files)
- [x] Did not edit `INDEX.md` or other status files
- [x] Did not claim vendor/system-camera parity
- [x] Design avoids hidden second session kernel
- [x] Design avoids UI-direct runtime control
- [x] Design separates saved JPEG rendering from preview approximation
- [x] Design includes minimal first implementation slice and later slices
- [x] Design does not implement any code changes
