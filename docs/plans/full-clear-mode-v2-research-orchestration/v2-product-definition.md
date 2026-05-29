# Full Clear V2 - Product Definition

## Product Promise

Full Clear V2 is a travel-object and landscape camera mode. It helps users capture scenes where both a close subject and distant background matter — a flower in front of mountains, a plate against a skyline, a detail with context. V2 selects the best available optical and computational path automatically, guided by subject distance, lens capability, and motion stability, and it tells the user what it chose and how confident it is.

The product language is intent-first, not exposure-first. Users understand "keep the flower and the mountain clear," not "near diopter 0.5 m, far infinity, fusion confidence 0.73." V2 translates technical capability into confidence-level guidance.

### What V2 Promises

- V2 will try the best available optical or computational path for each scene.
- V2 will tell the user what path it chose, in plain product language.
- V2 will show confidence as a simple level, not a raw score.
- V2 will fall back to a single best-frame capture when fusion is not safe.
- V2 will save diagnostics so the user or an agent can audit what happened.

### What V2 Does NOT Promise

- V2 does not guarantee both near and far planes are equally sharp.
- V2 does not promise focus-stack quality comparable to a tripod or dedicated macro rig.
- V2 does not claim to match vendor-proprietary multi-frame ISP fusion (Apple Deep Fusion, vivo ZEISS stack, OPPO Hasselblad pipeline).
- V2 does not expose raw focus distances, diopter values, or per-frame sharpness maps in primary UI.
- V2 does not work on every device — it degrades gracefully to a best-frame capture or wide/deep-DOF advice on unsupported hardware.

### Relationship to V1

V1 Full Clear is a single-capture mode with fixed focus guidance. V2 upgrades the promise from "try one good frame" to "pick the best available path and be honest about confidence." V2 maintains V1's simplicity but adds path selection, fusion, and degradation awareness.

---

## V2 User-Visible Guidance States

Each state is a product-level label shown to the user during capture. States are designed to guide intent and confidence, not to surface technical parameters.

### Capture-Ready States

| State | English Label | 中文标签 | Meaning | Trigger |
|---|---|---|---|---|
| `ready` | Ready | 就绪 | Scene looks good for Full Clear; close subject and background are both plausible. | Scene analysis passes minimum distance and contrast checks. |
| `too_close` | Move back | 请稍退后 | Subject is too close for the current lens to focus. | Measured or estimated subject distance is below lens minimum. |
| `use_wide` | Switch to Ultra Wide | 请切换超广角 | Ultra-wide or deep-DOF lens is predicted to give a better result than focus bracketing. | Scene depth range fits within ultra-wide DOF; bracketing would be less reliable. |
| `hold_steady` | Hold steady | 请保持稳定 | Device motion is above bracket/fusion threshold; user should stabilize. | Gyro/accelerometer motion exceeds configured stability threshold. |
| `unsupported` | Wide mode suggested | 建议使用广角模式 | Current device cannot provide enough lens/focus control for Full Clear V2. | Capability detection reports focus-distance control and multi-lens access as unavailable. |

### Capture-In-Progress States

| State | English Label | 中文标签 | Meaning | Trigger |
|---|---|---|---|---|
| `capturing` | Capturing | 拍摄中 | Frames are being acquired; keep the device steady. | Bracket or single-frame capture is in flight. |
| `processing` | Processing | 处理中 | Fusion or best-frame selection is running. | Post-capture pipeline is active. |

### Result States

| State | English Label | 中文标签 | Meaning | Trigger |
|---|---|---|---|---|
| `fused` | Full Clear | 全清 | V2 produced a fused result with acceptable alignment and fusion confidence. | Both alignment confidence and fusion confidence exceed their respective thresholds. |
| `best_frame` | Best frame saved | 已保存最佳帧 | V2 fell back to a single best frame; fusion was not safe or not attempted. | Alignment or fusion confidence was below threshold, or fusion path was not available. |
| `degraded` | Single frame saved | 已保存单帧 | V2 was unable to run its full path; a standard single capture was used instead. | Lens/focus capability was degraded at capture time; no bracket was possible. |

### Post-Capture/Diagnostic States

| State | English Label | 中文标签 | Meaning |
|---|---|---|---|
| `diagnostics_available` | Capture details available | 可查看拍摄详情 | Saved diagnostics can be inspected to understand what V2 did and why. |

---

## V2 Capability Matrix — Detailed Support Semantics

Each V2 capability has three tiers: **supported** (full path), **degraded** (fallback path), and **unsupported** (disabled or best-effort default). Diagnostics record the tier used for every capture.

### 1. Deep-DOF Lens Path

Deep-DOF path uses a lens with naturally large depth of field (typically ultra-wide) to cover both near subject and far background in a single capture, avoiding focus bracketing and fusion entirely.

| Semantic | Definition | Product Behavior |
|---|---|---|
| **supported** | An ultra-wide or equivalent deep-DOF physical camera is available, and its minimum focus distance can reach the subject. | V2 may route to deep-DOF path automatically. UI shows "Switch to Ultra Wide / 请切换超广角" if the current lens is not the deep-DOF lens. |
| **degraded** | A wide-DOF lens exists but has limited close-focus ability, or only the main camera is available with acceptable DOF at the current aperture. | V2 attempts deep-DOF capture but warns if the subject may be near the near-focus limit. Result may show softness at the near plane. |
| **unsupported** | No ultra-wide camera exists, or the only available lenses have shallow DOF (e.g., telephoto-only or fixed-aperture main with narrow DOF). | Deep-DOF path is not offered. V2 routes to focus bracket or best-frame path automatically. User never sees the ultra-wide suggestion. |

**Detection**: Query Camera2 `CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS` and physical camera list. Map minimum focal length to DOF estimate at expected subject distances. Cross-reference with `LENS_INFO_MINIMUM_FOCUS_DISTANCE`.

**Degradation triggers**: Lens switch latency timeout, ultra-wide camera temporarily unavailable (e.g., thermal throttle, concurrent usage), or subject distance below ultra-wide minimum focus distance.

### 2. Focus Bracket Path

Focus bracket path captures multiple frames at different focus distances (near subject, mid, far/infinity) and fuses them into one all-in-focus result.

| Semantic | Definition | Product Behavior |
|---|---|---|
| **supported** | `LENS_FOCUS_DISTANCE` is writable via Camera2 interop, and the device can complete a bracket sequence (≥3 frames) within the stability window without excessive latency. | V2 runs full bracket → fusion pipeline. UI shows "Capturing / 拍摄中" during bracket and "Processing / 处理中" during fusion. |
| **degraded-rebind** | Focus distance is writable only with a per-frame rebind or session restart, introducing latency that limits bracket size. | V2 runs a reduced bracket (2 frames: near + far, or near + infinity) with longer inter-frame gaps. Fusion is attempted but with lower confidence ceiling. UI warns "Hold steady / 请保持稳定" for longer. |
| **unsupported** | Focus distance is read-only, AF-only, or writing it produces no measurable effect. | Focus bracket path is disabled. V2 falls back to deep-DOF path (if supported) or best-frame path. User sees appropriate fallback state. |

**Detection**: Write a test focus-distance value via Camera2Interop, read back, and verify the lens moved. Measure per-frame latency for a 3-frame bracket. Classify as `supported` if write+verify succeeds and per-frame latency is under a configured threshold (suggested: 500ms).

**Degradation triggers**: Per-frame latency exceeds threshold mid-capture, focus-distance write fails on one frame, or gyro detects motion that would cause excessive alignment error.

### 3. Lens-Aware Routing

Lens-aware routing selects which physical camera (wide, ultra-wide, main, telephoto) to use based on subject distance, desired background framing, and each lens's optical characteristics.

| Semantic | Definition | Product Behavior |
|---|---|---|
| **supported** | Multiple physical cameras are exposed via Camera2/CameraX, and lens switching is reliable within a session. | V2 evaluates all available lenses and picks the best route (deep-DOF vs bracket vs hybrid). Route choice is recorded in diagnostics. |
| **degraded** | Multiple lenses exist but switching incurs significant latency, causes preview disruption, or some lenses have focus-distance write limitations. | V2 prefers the current lens if it can achieve an acceptable result. Lens switching is only suggested when the current lens path is clearly inferior. UI may show "Switch to Ultra Wide / 请切换超广角" as user guidance rather than automatic switch. |
| **unsupported** | Only one physical camera is available, or lens switching is not exposed to the app. | V2 uses the single available lens. If that lens supports focus bracket, the bracket path is used. Otherwise, deep-DOF advice or best-frame fallback applies. |

**Detection**: Enumerate physical cameras via `Camera2CameraInfo.getCameraCharacteristics()` and check `LENS_FACING` + focal length ranges. Verify lens switching latency by timing a switch-and-capture cycle.

**Degradation triggers**: Lens switch fails mid-capture, switch latency exceeds 1s, or the target lens reports a different minimum focus distance than expected at capture time.

### 4. Alignment Confidence

Alignment confidence measures whether multiple focus-bracket frames can be registered accurately enough for safe fusion. It must reject handheld motion, lens breathing, and subject movement before fusion commits.

| Semantic | Definition | Product Behavior |
|---|---|---|
| **required** | Alignment confidence is computed on every bracket capture. It gates whether fusion proceeds or the result degrades to best-frame. | If alignment confidence is below threshold after bracket capture, V2 saves the sharpest single frame (by local sharpness map) instead of attempting fusion. Result state shows "Best frame saved / 已保存最佳帧." |
| **degraded** | Alignment confidence is computed but uses a simpler, faster metric (e.g., global homography error only, no per-region check). | V2 proceeds with fusion but applies conservative blending (wider transition zones, more weight on the reference frame). Result may still be "Full Clear / 全清" but diagnostics record the degraded check. |
| **unsupported** | No alignment metric is available (e.g., single-frame deep-DOF path, or bracket frames are too few for registration). | Alignment check is skipped. This is only valid when the deep-DOF path was used — in that case there are no frames to align. For bracket paths, alignment is always required; "unsupported" alignment on a bracket path means "degrade to best-frame." |

**Required for fused output**: Alignment confidence must meet threshold for any "Full Clear / 全清" result. No fusion without alignment verification.

**Metrics**: Global homography error, per-region NCC (normalized cross-correlation) or MI (mutual information), edge-map consistency. Configurable threshold. Diagnostics must record which metric was used and its value.

### 5. Fusion Confidence

Fusion confidence decides whether the fused output is acceptable for delivery, or whether a single best frame should be saved instead.

| Semantic | Definition | Product Behavior |
|---|---|---|
| **required** | Fusion confidence is computed on the blended output. It gates whether the fused result is saved as "Full Clear / 全清" or the best single frame is saved as "Best frame saved / 已保存最佳帧." | Post-fusion check evaluates output quality. Result state depends on this check. |
| **degraded** | Fusion confidence is computed but uses a simpler metric, or the fusion itself is a simpler algorithm (e.g., Laplacian pyramid blend with fewer levels). | V2 saves the fused output but labels it with a "degraded" diagnostic flag. User-facing result may still say "Full Clear / 全清" if the degraded confidence still clears a lower threshold, or "Best frame saved / 已保存最佳帧" if it does not. |
| **unsupported** | Fusion algorithm cannot run (e.g., only one frame captured, or alignment failed entirely). | No fusion is attempted. Result is always "Best frame saved / 已保存最佳帧" or "Single frame saved / 已保存单帧." |

**Required for fused output**: Fusion confidence must meet threshold for any "Full Clear / 全清" result.

**Metrics**: Local sharpness uniformity across the fused image, edge-preservation score vs reference frame, halo/ghost artifact detector output. Diagnostics must record metric values and threshold.

### 6. Saved Diagnostics

Diagnostics record why V2 made each decision, so the user or an agent can audit a capture afterward without guessing.

| Semantic | Definition | Product Behavior |
|---|---|---|
| **required** | Every V2 capture writes a diagnostics record containing: route chosen, lens used, bracket frame count, alignment confidence score and metric, fusion confidence score and metric, result state, and any degradation reasons. | Diagnostics are saved alongside the image (e.g., EXIF custom tags, sidecar JSON, or session trace). UI shows "Capture details available / 可查看拍摄详情" when diagnostics exist. |

**Diagnostics schema** (minimum fields):

```
v2_diagnostics:
  route: deep_dof | focus_bracket | best_frame
  lens_used: ultra_wide | wide | main | telephoto
  bracket_frame_count: N
  alignment_confidence: 0.0–1.0
  alignment_metric: homography_ncc | homography_only | none
  fusion_confidence: 0.0–1.0
  fusion_metric: laplacian_pyramid | simple_blend | none
  result_state: fused | best_frame | degraded
  degradation_reasons: []
  timestamp_ms: N
  session_id: string
```

Diagnostics are required for all V2 captures regardless of result state. A "best_frame" result still records why fusion was not attempted or not confident.

---

## User Guidance Model

V2 guidance follows these principles, derived from competitive research:

### Principle 1: Intent Over Exposure

User-facing labels describe what the user wants ("keep both clear") not what the camera is doing ("focus at 0.3m, 2m, infinity"). This mirrors Apple's Macro Control pattern: the user sees "Macro" not "minimum focus distance engaged."

### Principle 2: Confidence Over Precision

V2 shows confidence as a simple two-tier signal (fused vs best-frame) rather than a numeric score. This avoids the "how sharp is 0.73?" problem and keeps the product promise honest.

### Principle 3: Automatic With Override

The automatic route (lens selection, bracket decision, fusion gate) is the primary path. Manual override exists only when the automatic choice feels wrong — specifically, when the user wants to force a different lens or skip fusion. This follows the Apple pattern: automatic macro switching with a user-visible control to prevent unwanted switching.

### Principle 4: Honest Fallback

When V2 cannot deliver fusion, it says so clearly with a product reason ("Best frame saved / 已保存最佳帧"), not a technical excuse. The user should never see "alignment confidence below 0.4" in primary UI. Diagnostics carry the detail for those who want it.

### Principle 5: Capability-Adaptive

V2 does not assume every device has the same lens/focus capabilities. The product promise is "V2 tries the best available path," not "V2 does focus stacking on every device." This is the key lesson from vivo/OPPO research: vendor quality comes from hardware-plus-ISP, and a third-party app must be honest about what it can access.

---

## Copywriting Guidance

### Chinese (简体中文) — Primary

V2 的中文文案面向中国 Android 用户，语言应该自然、直接、不技术化。

**模式名称**: `全清` (继承 V1，不改名)

**指导原则**:
- 用动词驱动：保持、切换、查看，而非名词堆叠。
- 用信心词：已保存、可查看，而非技术词：融合成功、对齐通过。
- 避免英文缩写和算法术语出现在主 UI。
- 短标签不超过 6 个汉字，说明文字不超过 15 个汉字。

**状态文案**:

| 状态键 | 中文标签 | 中文说明（可选副行） |
|---|---|---|
| `ready` | 就绪 | 适合全清拍摄 |
| `too_close` | 请稍退后 | 拍摄对象距离太近 |
| `use_wide` | 请切换超广角 | 超广角效果更佳 |
| `hold_steady` | 请保持稳定 | 手机稳定后可拍摄 |
| `unsupported` | 建议广角模式 | 当前设备不支持全清 |
| `capturing` | 拍摄中 | 请保持手机稳定 |
| `processing` | 处理中 | 正在生成全清照片 |
| `fused` | 全清 | 远近皆清晰 |
| `best_frame` | 已保存最佳帧 | 本次未使用融合处理 |
| `degraded` | 已保存单帧 | 全清处理未能完成 |
| `diagnostics_available` | 可查看拍摄详情 | 了解本次拍摄过程 |

### English — Secondary

English labels should feel like natural camera UI, not engineering debug output. Tone: calm, helpful, brief.

**State copy**:

| State Key | English Label | English Subtitle (optional) |
|---|---|---|
| `ready` | Ready | Good for Full Clear |
| `too_close` | Move back | Subject too close for this lens |
| `use_wide` | Switch to Ultra Wide | Better depth for this scene |
| `hold_steady` | Hold steady | Stabilize before capture |
| `unsupported` | Wide mode suggested | Full Clear not available on this device |
| `capturing` | Capturing | Keep device steady |
| `processing` | Processing | Creating Full Clear photo |
| `fused` | Full Clear | Near and far in focus |
| `best_frame` | Best frame saved | Fusion not used for this capture |
| `degraded` | Single frame saved | Full Clear unable to complete |
| `diagnostics_available` | Capture details | See what happened in this capture |

### Tone Guidelines (Both Languages)

- **Do**: brief, calm, helpful, honest.
- **Don't**: technical, apologetic, marketing-hype, or defensive.
- **Fusion result**: Say what happened ("Near and far in focus / 远近皆清晰"), not how it happened.
- **Fallback result**: Say what was saved, not what failed. "Best frame saved / 已保存最佳帧" is better than "Fusion failed / 融合失败."

---

## V2 Product Boundary

### V2 IS

- A camera mode for travel objects, food, nature details, and landscapes where near+far clarity matters.
- An automatic optical/computational path selector.
- Honest about confidence and fallback.
- Capability-adaptive across Android devices.
- Diagnosable — every capture decision is recorded and auditable.

### V2 IS NOT

- A manual focus-stack tool for macro photographers.
- A replacement for tripod-based focus bracketing.
- A vendor-quality guarantee (V2 works with what Camera2/CameraX exposes).
- A real-time computational photography pipeline (V2 runs post-capture fusion, not live preview fusion).
- A mode that requires segmentation, depth sensors, or ML models to function (these may improve it but are not required).

### Explicit Non-Goals

- Live preview of fused result (preview shows one lens feed; fusion happens post-capture).
- Video Full Clear (V2 is stills-only; video focus stacking is a separate future problem).
- RAW/DNG fusion output (V2 outputs JPEG/HEIF; RAW fusion is a future consideration).
- Manual focus-distance slider in primary UI (advanced users can use Pro mode instead).

---

## Competitive Positioning

Based on package 01 research:

| Dimension | Apple (Macro Control) | vivo X 系列 | OPPO Find X 系列 | Full Clear V2 |
|---|---|---|---|---|
| Approach | Automatic close-up lens switch with manual override | Hardware + ZEISS ISP multi-frame | Hardware + Hasselblad color + multi-camera | App-layer path selection + optional fusion |
| User sees | Macro toggle | Scene optimization labels | Master mode / Hasselblad styling | Confidence-based result labels |
| Fusion promise | Deep Fusion (proprietary, ISP-level) | ZEISS multi-frame (proprietary) | Hasselblad pipeline (proprietary) | Laplacian/blend fusion (transparent, fallback-aware) |
| Device reach | iPhone only | vivo devices only | OPPO devices only | Any Android with Camera2 focus control |
| Degradation model | Feature unavailable on older devices | N/A (first-party) | N/A (first-party) | Explicit supported/degraded/unsupported per capability |

V2's competitive position: it is not "better than Apple/vivo/OPPO at fusion." It is "the best available full-clear capture on any Android device that exposes focus control, honest about its limits, and auditable."

---

## Open Product Decisions

These decisions are deferred and should be resolved before V2 implementation begins:

1. **Bracket frame count**: Is the default bracket 3 frames (near, mid, far) or configurable up to N? More frames improve fusion quality but increase capture time and motion risk. **Recommendation**: default 3, configurable 3–5 via advanced settings, not primary UI.

2. **Manual lens override UX**: When the user disagrees with automatic lens selection, should the override be a simple "use this lens" toggle, or a lens picker? **Recommendation**: single "Use Ultra Wide / 使用超广角" toggle, not a full lens picker. Advanced lens selection belongs in Pro mode.

3. **Fusion quality tiers**: Should there be a "high quality" fusion mode that takes more frames and longer processing? **Recommendation**: defer to post-V2. Ship with one fusion quality tier first.

4. **Diagnostics visibility**: Should diagnostics be visible in-app (a detail panel), or only accessible via export/agent? **Recommendation**: in-app detail panel accessible from capture review, plus exportable JSON.

5. **Chinese market-specific tuning**: Should the Chinese localization include market-specific guidance (e.g., QR-code sharing of Full Clear results, social-media aspect ratios)? **Recommendation**: not in V2 scope; revisit after initial release.

6. **Thermal/degraded handling**: When the device is thermally throttled, should V2 automatically route to deep-DOF or best-frame (skipping fusion), or ask the user? **Recommendation**: automatic degradation with a brief toast ("Device warm, using single-frame mode / 设备温度较高，已切换单帧模式"). No user decision required.
