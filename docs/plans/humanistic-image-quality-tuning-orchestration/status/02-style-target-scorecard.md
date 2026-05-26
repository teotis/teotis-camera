# Package Status: 02-style-target-scorecard

- **Agent**: claude-code (foreground)
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/pkg-02-style-scorecard
- Branch: worktree-pkg-02-style-scorecard

## Changes

- git status: clean before write; only `status/02-style-target-scorecard.md` modified
- git diff --stat: single file
- Changed files: `docs/plans/humanistic-image-quality-tuning-orchestration/status/02-style-target-scorecard.md`

## Verification

- Commands run:
  - `rg -n "Humanistic|Color Lab|ColorLab|PerceptualColorRecipe|tone|chroma|highlight|shadow|warm|cool" docs/plans/humanistic-image-quality-tuning-orchestration/ --count` — confirmed keyword density across plan docs
  - `rg -n "StyleColorScience|TEXTURE|NATURAL|VIVID|MONOCHROME" core/settings/ --count` — confirmed 4 color science modes and pipeline structure
  - `rg -n "night-fallback|night-multiframe|low-light" feature/ --count` — confirmed night mode fallback profiles exist but no scene-adaptive color logic
  - `git status --short` — confirmed only target file touched
- Test results: N/A (research package, no code changes)

## Evidence

### Source/doc references

| Source | Key Finding |
|---|---|
| `core/settings/SettingsDataModels.kt` (FilterRenderSpec) | 15 tone/color control parameters: brightnessShift, contrast, saturation, warmthShift, tintShift, monochromeMix, vignetteStrength, softGlowStrength, haloStrength, grainStrength, sharpnessBoost, highlightCompression, shadowLift, warmBoost, coolBoost |
| `core/settings/PerceptualColorRecipe.kt` | 10 perceptual-layer fields: toneLift, toneDepth, chromaBoost, warmthBias, tintBias, shadowTint, highlightTint, neutralProtection, skinProtection, previewFidelity |
| `core/settings/ColorLabSpec.kt` | 2-axis palette: colorAxis [-1,1] (cool-warm), toneAxis [-1,1] (deep-airy), strength [0,1] |
| `core/settings/StyleColorPipeline.kt` | 4 color sciences: NATURAL, TEXTURE (humanistic/street), VIVID (rich/vivid), MONOCHROME. TEXTURE uses split-grade with shadowLift and highlightCompression as primary dusk/night levers |
| `core/settings/SettingsDefaults.kt` | 5 humanistic profiles: original (near-neutral), vivid (saturated), street (cool+bright), portrait (warm+vignette), life (warm+contrast) |
| `docs/plans/2026-05-25-color-lab-perceptual-strength-and-consistency.md` | Corner semantics: warm airy=sunlight, warm deep=film/sunset, cool airy=freshness, cool deep=city/night density. Quality: no posterization, no neon saturation, no destroyed skin |
| `docs/plans/2026-05-24-vivo-x300-color-lab-strength.md` | Numeric boundary tuning: brightness +16/-14, contrast +0.24/-0.18, saturation 0.24, warmth 20, tint 6, shadowLift 0.26, highlightCompression 0.18, warmBoost 0.28 |
| `docs/plans/2026-05-25-humanistic-quick-snap-mode-ui.md` | Street/chasing-light, portrait/portrait-original, life/photo-rich algorithm mapping. No dusk/night style exists |
| `feature/night/NightModePlugin.kt` | Night fallback profiles: night-fallback-balanced, night-fallback-warm. Multi-frame fusion only — no scene-adaptive color |
| `docs/plans/2026-05-25-color-lab-preview-recipe-transform-repair.md` | Preview always weaker than saved; DEGRADED fidelity on unsupported devices. Preview/saved direction must match |

### Acceptance criteria status

| Criterion | Status | Evidence |
|---|---|---|
| Style target matrix with measurable criteria | DONE | See §1 below |
| Scorecard covers user-observed dimensions | DONE | 8-dimension rubric in §3 |
| Explicitly states acceptable even if vivo looks better | DONE | See §5 non-goals |
| Separates subjective taste from deterministic checks | DONE | §4 deterministic, §3 subjective |
| Consumable by packages 03/04 without further product decisions | DONE | Profiles map directly to FilterRenderSpec + PerceptualColorRecipe fields |

---

## Deliverable: Style Target Scorecard

### §1 Style Target Matrix — Dusk/Night Profiles

Four style targets for dusk and night shooting. Each maps to existing `FilterRenderSpec` + `PerceptualColorRecipe` parameters and the `StyleColorScience` pipeline. No new axes or controls are required.

#### Profile A: Neutral Baseline (Photo mode default, no Color Lab)

| Dimension | Target |
|---|---|
| Exposure intent | Preserve ambient mood; do not aggressively lift dark scenes. brightnessShift in [-2, +2] range for auto-exposed frames |
| Highlight rolloff | Mild highlightCompression (0.04-0.08) to retain lamp/sky gradient; no hard clipping |
| Shadow/noise | Minimal shadowLift (0.0-0.06); accept sensor noise as authentic |
| Local contrast/sharpening | contrast 1.00-1.04, sharpnessBoost 0.0-0.05; texture-forward, not smoothing |
| White balance/color bias | Device AWB as-is; no warmthShift override |
| Saturation/chroma | saturation 1.00-1.04; neutral chromaBoost |
| Skin/neutral protection | neutralProtection >= 0.8, skinProtection >= 0.8 (recipe defaults) |
| Preview/saved honesty | Preview fidelity APPROXIMATE or better; direction must match saved |

**Use case**: Reference baseline for side-by-side comparison. When user wants "no filter, just good capture."

#### Profile B: Humanistic Warm Deep (warm deep corner of Color Lab)

| Dimension | Target |
|---|---|
| Exposure intent | Slightly lifted shadows to reveal detail; brightnessShift +4 to +8. Not "bright daylight" — preserve dusk/night atmosphere |
| Highlight rolloff | highlightCompression 0.08-0.14; compress lamp halos and sky gradient into softer rolloff without flattening |
| Shadow/noise | shadowLift 0.12-0.22; open shadows gently but retain density. Noise acceptable if luminance noise, not chroma blob |
| Local contrast/sharpening | contrast 1.06-1.14; moderate depth without crunch. sharpnessBoost 0.02-0.06 |
| White balance/color bias | warmthShift +6 to +14; warmBoost 0.10-0.20. Tilt toward amber/golden, not orange |
| Saturation/chroma | saturation 1.04-1.12; chromaBoost moderate. Warm hues lifted slightly, cool hues not crushed |
| Skin/neutral protection | skinProtection >= 0.85; warm shift must not turn gray objects brown or skin orange. neutralProtection >= 0.75 |
| Preview/saved honesty | Preview shows warmth direction; saved may be slightly stronger. Gap <= 20% perceived intensity |

**Color Lab mapping**: colorAxis ~+0.5, toneAxis ~-0.3, strength 0.6-0.8, StyleColorScience.TEXTURE

**Atmosphere goal**: Film-like warmth at golden hour or under tungsten street lighting. Quiet, intimate, not dramatic.

#### Profile C: City Night Cool Deep (cool deep corner of Color Lab)

| Dimension | Target |
|---|---|
| Exposure intent | Preserve night density; do not lift to daylight. brightnessShift -2 to +2. Underexposure preferred over noisy lift |
| Highlight rolloff | highlightCompression 0.10-0.18; compress neon/sodium highlights into controlled rolloff. No blown-white halos |
| Shadow/noise | shadowLift 0.06-0.14; minimal. Shadows should stay deep but not crushed to pure black. Accept luminance noise |
| Local contrast/sharpening | contrast 1.08-1.18; city scenes benefit from moderate depth. sharpnessBoost 0.03-0.08 for architectural detail |
| White balance/color bias | coolBoost 0.10-0.20; slight blue-steel tint. warmthShift -4 to -10. Sodium-lamp areas may retain warm cast — do not fight the scene |
| Saturation/chroma | saturation 0.92-1.04; slightly restrained. Neon accents should read as color, not bleed. chromaBoost low |
| Skin/neutral protection | skinProtection >= 0.80; cool shift must not turn skin green-gray. neutralProtection >= 0.80; concrete/steel stay neutral |
| Preview/saved honesty | Preview shows cool direction; saved may be slightly deeper. Gap <= 20% perceived intensity |

**Color Lab mapping**: colorAxis ~-0.5, toneAxis ~-0.4, strength 0.6-0.8, StyleColorScience.TEXTURE

**Atmosphere goal**: Urban night density — blue-hour sky layers, controlled neon, quiet shadows. Not "cold and sterile" — just restrained warmth.

#### Profile D: Soft Dusk Airy/Warm (warm airy corner, lighter treatment)

| Dimension | Target |
|---|---|
| Exposure intent | Lifted and open; brightnessShift +6 to +12. Dusk scenes gain air and transparency without blowing highlights |
| Highlight rolloff | highlightCompression 0.04-0.10; keep sky gradient visible but soft |
| Shadow/noise | shadowLift 0.14-0.24; generous opening. Shadows should feel breathable, not flat |
| Local contrast/sharpening | contrast 0.96-1.06; lower than other profiles — airy means softer. sharpnessBoost 0.0-0.04 |
| White balance/color bias | warmthShift +4 to +10; warmBoost 0.06-0.14. Gentle warmth, like late afternoon light |
| Saturation/chroma | saturation 1.02-1.10; slightly lifted but not vivid. Pastel-leaning, not punchy |
| Skin/neutral protection | skinProtection >= 0.85; lifted exposure + warmth must not bleach or orange skin. neutralProtection >= 0.80 |
| Preview/saved honesty | Preview shows airy direction; saved may add slightly more lift. Gap <= 15% perceived intensity |

**Color Lab mapping**: colorAxis ~+0.4, toneAxis ~+0.4, strength 0.5-0.7, StyleColorScience.TEXTURE

**Atmosphere goal**: Twilight transparency — the moment after sunset when sky is still luminous. Hopeful, not heavy.

---

### §2 Pipeline Parameter Ranges (Implementation Reference)

These are the aggregate ranges across all four profiles. Package 03 should use these as clamping bounds.

| Parameter | Min | Max | Notes |
|---|---|---|---|
| brightnessShift | -2 | +12 | Neutral and Cool Deep stay near 0; Airy/Warm lifts most |
| contrast | 0.96 | 1.18 | Airy/Warm dips slightly; Cool Deep pushes highest |
| saturation | 0.92 | 1.12 | Cool Deep restrains; Warm Deep lifts slightly |
| warmthShift | -10 | +14 | Range from Cool Deep to Warm Deep |
| tintShift | -6 | +6 | Subtle; derived from colorAxis |
| shadowLift | 0.00 | 0.24 | Airy/Warm most generous; Neutral minimal |
| highlightCompression | 0.04 | 0.18 | Cool Deep most aggressive; Airy/Warm lightest |
| warmBoost | 0.00 | 0.20 | Warm Deep max; Cool Deep = 0 |
| coolBoost | 0.00 | 0.20 | Cool Deep max; others = 0 |
| sharpnessBoost | 0.00 | 0.08 | Cool Deep highest for architecture; Airy/Warm near 0 |
| neutralProtection | 0.75 | 1.00 | Never below 0.75 |
| skinProtection | 0.80 | 1.00 | Never below 0.80 |

---

### §3 Human Review Scorecard (1-5 Rubric)

A reviewer applies this to side-by-side samples (OpenCamera saved JPEG vs reference, or A/B style comparison). Each dimension scored 1-5.

| # | Dimension | 1 (Fail) | 2 (Weak) | 3 (Acceptable) | 4 (Good) | 5 (Excellent) |
|---|---|---|---|---|---|---|
| D1 | **Sky layers** | Flat single-tone sky; no gradient | Faint gradient visible but posterized | Smooth gradient, 2-3 tonal bands visible | Gradient with distinct warm/cool transition layers | Gradient reads as atmospheric depth; layer separation matches scene |
| D2 | **Lamp/highlight control** | Blown white halos around lights | Highlights compressed but still harsh | Soft rolloff; lamp shape preserved | Lamp glow retained with surrounding gradient | Lamp reads as light source with natural falloff into surrounding tones |
| D3 | **Shadow noise/detail** | Chroma blobs dominate shadows | Visible noise, some detail recovered | Luminance noise acceptable; detail present | Clean shadow detail with natural grain | Shadow reads as intentional texture; no distracting artifacts |
| D4 | **Local contrast** | Flat/muddy or crunchy/harsh | Slightly flat or slightly over-processed | Natural depth; scene reads dimensionally | Good separation between near/far planes | Scene has photographic depth without visible processing artifacts |
| D5 | **White balance** | Strongly wrong cast (green/purple) | Slightly off; noticeable in isolation | Neutral objects read neutral; scene-lit areas natural | WB feels intentional and scene-appropriate | WB enhances mood without drawing attention to itself |
| D6 | **Color tendency** | Neon/oversaturated or completely flat | Slightly too strong or too muted | Colors recognizable and restrained | Colors add mood without dominating | Colors feel like a deliberate palette; harmonious with scene |
| D7 | **Sharpness/texture** | Plastic/smooth or crunchy/haloed | Slightly soft or slightly over-sharpened | Detail preserved; no visible halos | Good micro-contrast; texture reads naturally | Fine detail (fabric, skin texture, foliage) reads without artifacts |
| D8 | **Overall atmosphere** | Looks like raw sensor output | Slightly processed but flat | Reads as a photograph with some style | Mood is clear; style supports the scene | The image evokes the intended feeling (warm intimate / cool urban / airy twilight) |

**Pass threshold**: Average score >= 3.0 across all 8 dimensions. No single dimension below 2.

**Strong pass**: Average >= 3.75 with no dimension below 3.

---

### §4 Deterministic Implementation Checks

These are binary pass/fail checks that do not require subjective judgment. Package 03/04 should implement these as automated validation.

| Check ID | Criterion | Pass Condition |
|---|---|---|
| C1 | Recipe non-neutral | Profile B/C/D: `perceptualColorRecipe.isNeutral == false` |
| C2 | Gray neutrality | 18% gray patch: deltaE (sRGB) < 3.0 after recipe application |
| C3 | Skin protection | Skin-tone patch: hue rotation < 10 degrees after recipe |
| C4 | Preview direction match | Preview transform direction (warm/cool, airy/deep) matches saved output direction |
| C5 | No posterization | 8-bit gradient (0-255) through full pipeline: no visible banding in 5% steps |
| C6 | Saturation guard | No channel exceeds 255 or drops below 0 after recipe (clipping check) |
| C7 | Parameter bounds | All FilterRenderSpec values within §2 ranges after recipe application |
| C8 | Pipeline notes present | Diagnostic output includes `color-lab:texture` and `style-base:<profileId>` notes |
| C9 | Preview fidelity honest | Device reports APPROXIMATE or better; DEGRADED only if genuinely unsupported |
| C10 | Fallback order | On render failure: mask-aware -> global recipe -> original captured. Never silent discard |

---

### §5 Non-Goals and Parity Disclaimers

1. **No vivo/system camera parity claim.** OpenCamera does not have access to vivo's ISP tuning, multi-frame HDR fusion, AIISP, or vendor-private RAW processing. These style targets define *achievable aesthetic direction*, not *equivalent output quality*.

2. **No RAW/HDR requirement.** All profiles work on single-frame JPEG postprocess. Multi-frame HDR (available in Night mode) is a bonus, not a prerequisite for these style targets.

3. **No scene auto-detection.** These profiles are user-selected or preset-mapped, not auto-detected from ambient light or scene content. Package 03 may propose lightweight heuristics (e.g., ISO threshold), but no ML scene classifier is assumed.

4. **Preview is inherently approximate.** The preview overlay cannot fully simulate tone curves, chroma shifts, or split tint. Preview/saved gap is expected; the target is directional consistency, not pixel-match fidelity.

5. **"Acceptable even if vivo looks better"** means: OpenCamera's dusk/night output should read as a deliberate photographic style with controlled exposure, honest noise, and recognizable color palette — even if the reference device produces cleaner shadows, smoother gradients, or more accurate white balance through hardware advantage.

6. **No new UI controls.** These profiles map to existing Color Lab axes and Humanistic style presets. No new sliders, toggles, or mode switches are required.

---

### §6 Style Language (Product Copy Reference)

For UI labels, tooltips, and marketing copy:

| Profile | Short Label | One-line Description |
|---|---|---|
| A: Neutral | 标准 | 忠实记录场景光线，不做额外风格处理 |
| B: Warm Deep | 暖调深沉 | 黄金时段的胶片质感，温暖而内敛 |
| C: Cool Deep | 冷调深邃 | 都市夜色的蓝调密度，安静而克制 |
| D: Soft Dusk | 柔光黄昏 | 日暮时分的通透空气感，轻盈而温暖 |

---

## Delivery

- Commit hash: (pending commit)
- PR link: N/A (research package, no PR)

## Self-Certification

- [x] Only touched allowed paths (`docs/plans/humanistic-image-quality-tuning-orchestration/status/02-style-target-scorecard.md`)
- [x] Did not edit forbidden paths (runtime code, tests, resources, INDEX.md, other status files)
- [x] Did not edit `INDEX.md` or other status files
- [x] Did not claim vendor/system-camera parity

## Unresolved Risks

1. **No real-device dusk/night samples reviewed.** Style targets are derived from plan documents and code analysis, not from actual side-by-side photo comparison. Package 04 should validate these targets against real vivo X300 samples.
2. **TEXTURE color science may need dusk/night-specific tuning.** The current `applyTextureColorLab()` uses fixed multipliers. Profiles B/C/D may need slightly different shadow/highlight response curves that TEXTURE doesn't currently provide — package 03 should evaluate whether a DUSK variant of TEXTURE is needed.
3. **Night mode multi-frame interaction is undefined.** If a user selects Humanistic Warm Deep while Night mode's multi-frame fusion is active, the recipe application order (fusion first, then recipe? or recipe per-frame then fuse?) is not specified. Package 03 should address this.
4. **Cool Deep profile conflicts with sodium-lamp scenes.** Urban night scenes with mixed lighting (cool sky + warm sodium lamps) may produce inconsistent results with a single cool-bias recipe. This is acceptable as a known limitation; the profile targets the dominant scene tone.
