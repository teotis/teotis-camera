# Full Clear V2 - Competitive And Technical Research

## Source Refresh Status

**Refresh date**: 2026-05-29
**Method**: Web search (WebFetch blocked by network policy for all source domains; fallback to WebSearch for current information)

| Source | Original URL | Refresh Result | Access Date |
|--------|-------------|----------------|-------------|
| Apple Support - Macro | support.apple.com/en-ie/guide/iphone/iphfaacf2eb0/ios | Not directly accessible; refreshed via web search | 2026-05-29 |
| Apple iPhone 16 Pro | apple.com/iphone-16-pro/ | Not directly accessible; refreshed via web search | 2026-05-29 |
| vivo X300 Pro | vivo.com/en/products/x300pro | Refreshed via web search (launched Oct 2025) | 2026-05-29 |
| OPPO Find X8 Pro | oppo.com/en/smartphones/series-find-x/find-x8-pro/ | Refreshed via web search | 2026-05-29 |
| Android Camera2 API docs | developer.android.com/reference/... | Not directly accessible; refreshed via web search (StackOverflow, community) | 2026-05-29 |
| PubMed multi-focus survey | pubmed.ncbi.nlm.nih.gov/33974542/ | Not directly accessible; refreshed via web search (multiple 2024-2025 papers) | 2026-05-29 |

**Overall refresh verdict**: Direct page fetches unavailable due to network policy. Web search provided substantial current information for all research areas. Source list remains broadly accurate; no material contradiction found.

## Apple Lessons

### Confirmed Public Evidence

- iPhone 16 (all models) and iPhone 16 Pro support Macro mode with automatic switching from the standard wide lens to the Ultra Wide camera when the subject is close (~2 cm minimum focus distance).
- Macro Control is a user-facing Settings toggle (Settings > Camera > Macro Control) that displays a flower icon in the Camera app. Yellow = Macro active; tapping toggles it off to prevent unwanted automatic lens switching.
- iOS 26 (current as of 2025) introduces a redesigned Camera UI ("Liquid Glass") but retains the macro flower indicator and Macro Control behavior.
- iPhone 17 series (expected September 2025) is reported to extend macro capability to all models with a rumored 48MP Ultra Wide sensor on Pro models.
- The Ultra Wide camera achieves close focus via its inherently short minimum focus distance, not through a dedicated macro lens. This is an optical property of the wide-angle lens design, not a computational stacking technique.

### Inference And V2 Implications

- **Inference**: Apple's design philosophy treats macro as automatic scene adaptation, not a user-initiated capture mode. The only user-facing control is a veto ("don't switch lenses"), not a parameter ("focus at X diopters").
- **Inference**: Apple does not expose focus stacking or multi-focus fusion as a user feature. The product resolves close-up photography through lens choice (ultra-wide optics), not through computational depth-of-field extension.
- **V2 design lesson**: Full Clear V2 should default to automatic behavior (detect scene, choose strategy, execute) and expose user controls as intent-level overrides, not as technical parameters. The primary user-facing control should be a confidence/status indicator plus a "keep current lens" or "use alternative strategy" option -- not diopter sliders or fusion algorithm settings.
- **V2 design lesson**: The Apple pattern validates the approach of using inherent optical properties (wide-angle deep DOF) as a primary strategy rather than requiring computational fusion for every scene. Full Clear V2 should similarly prefer optical strategies where available and fall back to computational fusion only when needed.

## vivo Lessons

### Confirmed Public Evidence

- vivo X300 Pro launched October 2025 (China), with international rollout through early 2026.
- Rear camera system: 50MP main (Sony LYT-828, f/1.6, 24mm, 1/1.28"), 50MP ultra-wide (JN5, f/2.0, 15mm, 119 FOV), 200MP ZEISS APO periscope telephoto (Samsung ISOCELL HPB, f/2.67, 85mm, 1/1.4", CIPA 5.5 OIS).
- Telemacro capability: 2.7:1 magnification ratio using the 200MP periscope telephoto lens. Longer working distance than ultra-wide-based macro (avoids shadow-casting). Reviewers describe it as "more useful than ultra-wide macro modes."
- Dual-chip imaging pipeline: VS1 pre-processing chip + V3+ post-processing chip, plus MediaTek Dimensity 9500 with co-defined imaging NPU.
- ZEISS T* coating and APO certification on the telephoto lens, with fluorite-grade FCD100 glass for chromatic aberration suppression.
- Optional ZEISS 2.35x Telephoto Extender Kit for up to 8.5x optical zoom.

### Inference And V2 Implications

- **Inference**: vivo's macro strategy is opposite to Apple's: Apple uses ultra-wide (short working distance, deep DOF from wide-angle optics), vivo uses telephoto (long working distance, shallow DOF requiring precise focus). Both are valid optical paths for close-up photography, but they solve different problems.
- **Inference**: The dual-chip pipeline (pre-processing + post-processing) suggests vivo performs substantial in-camera ISP processing that third-party apps cannot replicate through public APIs. Full Clear V2 cannot assume equivalent processing quality.
- **Inference**: The telemacro approach's 2.7:1 magnification is impressive but comes from a dedicated periscope lens with OIS and ZEISS optics -- hardware that most Android devices lack.
- **V2 design lesson**: Full Clear V2 should support multiple optical strategies and auto-select based on available hardware: telemacro when a capable telephoto is present, ultra-wide/deep-DOF when available, main-camera focus bracket as a middle path, and computational fusion as fallback.
- **V2 design lesson**: The gap between vendor hardware+ISP quality and third-party app capability means V2 must declare what it can deliver on generic CameraX/Camera2 vs. what requires OEM-specific extensions. A capability matrix is essential.

## OPPO Lessons

### Confirmed Public Evidence

- OPPO Find X8 Pro features a quad 50MP setup: main (Sony LYT-808, f/1.6, 23mm, 1/1.4"), ultra-wide (JN5, f/2.0, 15mm, 120 FOV), 3x periscope telephoto (Sony LYT-600, f/2.6, 73mm, 43cm minimum focus), 6x periscope telephoto (Sony IMX858, f/4.3, 135mm, 35cm minimum focus).
- Built-in dedicated "macro mode" uses the ultra-wide or main lens with digital crop -- reviewers consistently rate it as subpar.
- Real macro capability comes from the 6x periscope telephoto (135mm) at its 35cm minimum focus distance. This is a "hidden" macro path, not the advertised macro mode.
- Hasselblad color tuning is a key differentiator in product messaging.
- Dual periscope telephoto design provides two distinct telephoto focal lengths (73mm and 135mm).

### Inference And V2 Implications

- **Inference**: OPPO's product demonstrates a common pattern: the advertised "macro mode" is often the weakest macro path. The best close-up results come from understanding which physical lens has the best combination of minimum focus distance, working distance, and optical quality -- and using that lens, not the branded mode.
- **Inference**: The dual-periscope design shows that high-end 2025 flagships are converging on multi-focal-length coverage as a competitive differentiator. This trend increases the variety of optical paths available to a Full Clear V2 implementation but also increases the complexity of auto-selecting the right path.
- **V2 design lesson**: Full Clear V2 should not blindly use whatever the device advertises as "macro." It should probe actual lens capabilities (minimum focus distance, field of view, aperture) and select the best available optical path, even if that means using the 6x telephoto for close-up work while ignoring the branded macro mode.
- **V2 design lesson**: The gap between "marketed feature" and "best actual behavior" reinforces that V2 must be evidence-driven at the Camera2 characteristics level, not at the product-marketing level.

## Android API Feasibility

### Confirmed Public Evidence

- `CaptureRequest.LENS_FOCUS_DISTANCE` uses **diopters** (1/meter) as its unit. 0.0 = infinity, higher values = closer focus. Conversion: `diopters = 100.0 / distanceInCm`.
- `LENS_INFO_FOCUS_DISTANCE_CALIBRATION` reports one of three levels:
  - **CALIBRATED (0)**: diopter values accurately map to real-world distances. Rare on consumer phones.
  - **APPROXIMATE (1)**: values are repeatable but not physically precise. Most common on consumer phones.
  - **UNCALIBRATED (2)**: only 0.0 (infinity) and MINIMUM_FOCUS_DISTANCE (closest) are meaningful. Intermediate values are unreliable.
- `LENS_INFO_MINIMUM_FOCUS_DISTANCE == 0` indicates a fixed-focus lens with no adjustable focus.
- Manual focus requires `CONTROL_AF_MODE_OFF`. Even then, AF may interfere unless previous triggers are fully cancelled.
- Many phone lenses use VCM (Voice Coil Motor) actuators without Hall sensor positional feedback, meaning the lens cannot know its absolute position. Gravity and temperature affect true focus position, especially near infinity.
- On many devices, setting `LENS_FOCUS_DISTANCE = 0.0` does NOT achieve true infinity focus -- a small positive diopter (e.g., 0.05--0.4) may be needed. This is device-specific and cannot be predicted from API metadata.
- The API defines no standardized step size. Each device quantizes focus positions differently.
- CameraX `Camera2Interop.Extender` can attach Camera2 capture request keys to CameraX use cases, but this operates at the builder level, not per-frame.
- Devices with `INFO_SUPPORTED_HARDWARE_LEVEL_FULL` are more likely to support manual focus control, but this is not guaranteed across OEMs.

### Evidence-Based Full Clear V2 Design Constraints

These are not inferences -- they are engineering conclusions derived directly from the API evidence above:

1. **Focus distance is approximate on virtually all consumer devices.** V2 must not assume accurate diopter-to-physical-distance mapping. It can rely on repeatable focus positions (APPROXIMATE guarantees this) but must treat focus values as opaque positions, not calibrated distances.
2. **Focus bracketing is feasible but device-specific.** A focus bracket sequence can step through available diopter values, but the number of useful steps, the actual depth-of-field at each step, and the reliability of each position vary by device. V2 needs a per-device calibration or auto-detection strategy.
3. **Lens switching (wide to ultra-wide to telephoto) during a bracket sequence is high-latency and not designed for per-frame toggling.** CameraX/Camera2 lens switching involves physical actuator movement, stream reconfiguration, and 3A convergence. A practical V2 must capture a bracket on a single lens before switching, not interleave lenses within a burst.
4. **CameraX builder-level interop is the practical ceiling for most devices.** Per-frame Camera2 overrides within a CameraX session are fragile across OEMs. V2 should design for builder-level configuration changes between bracket groups, not per-frame parameter injection.
5. **Fixed-focus and UNCALIBRATED devices cannot support focus bracketing.** V2 must detect these cases and fall back to single-frame capture with whatever optical DOF the lens provides.
6. **The gap between `0.0` diopters and true infinity focus means V2 cannot rely on "focus at infinity" as a precise far-field setting.** It should use the minimum available diopter value that achieves acceptable far-field sharpness, determined empirically.

## Algorithm Lessons

### Confirmed Public Evidence (from 2024-2025 literature)

- Multi-focus image fusion (MFF) is an active research area. The 2025 Neurocomputing survey by Luo et al. categorizes deep-learning MFF into six problem scenarios: lightweight networks, artifact/defocus-spread handling, information preservation, unified fusion networks, suboptimal decision maps, and challenging environments.
- Transformer-based methods dominate recent SOTA: SwinMFF (pure Swin Transformer, 2024), DAA-ViT (dual-scale attention + iterative refinement, 2025), CAViT-IMSFN (CNN+ViT hybrid with spatial attention, 2025), WDTEP (dual heterogeneous encoders with edge protection, 2024).
- The 2024 ICPR workshop survey by Bernardi et al. proposes a taxonomy distinguishing supervised, unsupervised, and task-driven fusion approaches by architecture type (CNN, GAN, Transformer).
- Key identified research gaps across multiple 2024-2025 surveys:
  - Real-world datasets are scarce -- most benchmarks use synthetic data with artificially blurred regions.
  - Lightweight architectures for real-time/embedded deployment are under-explored.
  - Robustness to noise, mis-registration, and real-world artifacts is insufficiently tested.
  - Defocus spread effects (transition zones between in-focus and out-of-focus regions) remain a challenge.
  - Domain-independent generalization (medical, surveillance, photography) is not achieved.
- The original PubMed survey (entry point, PMID 33974542) and newer 2025 EDMF benchmark (PMID 39599063) confirm that exposure differences between source images add a separate challenge beyond focus/depth differences.

### Inference And V2 Implications

- **Inference**: MFF research has matured to the point where algorithms can produce visually plausible all-in-focus images from pre-registered, well-exposed source images with controlled focus differences. However, the gap between this controlled condition and handheld smartphone capture is substantial.
- **Inference**: No published MFF method claims to handle all of: handheld misalignment, moving subjects, exposure variation, lens breathing (focal length change with focus), and real-time performance on mobile hardware -- simultaneously.
- **Inference**: The dominance of Transformer architectures in recent SOTA is a signal, not a requirement. Transformer models typically require more compute than mobile-friendly CNNs. For V2, a simpler method with explicit confidence scoring may be more appropriate than chasing SOTA metrics on synthetic benchmarks.
- **V2 algorithm stance** (confirmed by evidence):

  1. Local sharpness maps (Laplacian variance, gradient magnitude) are acceptable for initial fusion decision maps. They are computationally cheap, interpretable, and well-understood.
  2. Alignment confidence is required before replacing any source pixel with fused output. Misaligned regions must degrade gracefully to best-single-frame, not produce ghost artifacts.
  3. Moving-subject detection (frame-to-frame difference in overlapping regions) must gate fusion: if a region contains motion, use the best single frame for that region, not a fusion blend.
  4. Edge halos and defocus spread effects around high-contrast boundaries must be detected and suppressed -- these are the most visually objectionable fusion artifacts.
  5. Segmentation (semantic or instance) can improve fusion masks (e.g., preferentially fusing foreground subject over background) but cannot be a correctness requirement -- segmentation failures must not break the output.
  6. Diagnostics must record per-frame metadata: which frames contributed to which output regions, confidence scores, detected motion regions, and fallback decisions. This is essential for debugging and real-device validation.

## Full Clear V2 Actionable Principles

Synthesized from all vendor, API, and algorithm evidence:

### P1: Auto-Select Optical Strategy Before Computational Fusion

The first decision is which optical path to use:
- **Ultra-wide / deep-DOF**: If an ultra-wide camera is available and the scene is bright enough, use its inherently deep depth of field for close subjects. This is the Apple pattern.
- **Telephoto macro**: If a telephoto with close minimum focus distance is available, use it for close subjects with better working distance and background separation. This is the vivo/OPPO pattern.
- **Main camera focus bracket**: If neither ultra-wide nor telephoto is suitable, bracket focus on the main camera.
- **Single best frame**: If focus bracketing is unsupported (fixed-focus, UNCALIBRATED), capture one frame at the best available focus.

### P2: Capability Matrix Before Strategy Selection

Before choosing any strategy, probe the device:
- Per-lens: minimum focus distance, focus distance calibration level, hardware level, available AF modes, field of view, aperture.
- Per-session: current lighting, subject distance estimate, scene motion estimate.
- Decision: map probe results to supported strategies, select the highest-confidence path, declare fallback.

### P3: Intent-Level UX, Not Parameter-Level UX

User-facing controls should express intent, not technical parameters:
- "Keep everything sharp" (not "focus bracket with 5 steps at 0.1 diopter increments")
- "Focus on close subject" / "Focus on background" (not "LENS_FOCUS_DISTANCE = 2.5")
- Status indicators: "Capturing...", "Hold steady", "Processing...", "Ready"
- Override: "Keep current lens" (when auto-switching picks wrong), "Use alternative strategy"
- Diagnostics available but hidden behind a developer/advanced toggle

### P4: Fusion With Fallback, Not Fusion Or Nothing

Every fusion attempt must have a defined fallback:
- Alignment fails -> best single frame
- Motion detected in region -> best frame for that region
- Exposure mismatch between frames -> exposure-align or fall back
- Lens breathing causes FOV mismatch -> crop to common region or fall back
- Processing timeout -> deliver best available result with degraded indicator

### P5: Evidence-Graded Output

The output image should carry metadata about how it was produced:
- Strategy used (optical DOF / focus bracket fusion / single frame)
- Confidence score
- Which frames contributed
- Degraded regions (motion, alignment failure, edge artifact)
- Fallback reasons

This supports real-device validation: testers can see not just the result but the decision path.

## Evidence vs. Inference Separation

| Claim | Classification | Basis |
|-------|---------------|-------|
| iPhone macro uses ultra-wide lens auto-switching | Confirmed | Apple Support docs, multiple reviews |
| iPhone Macro Control is a user veto toggle | Confirmed | Apple Support docs, iOS Settings |
| Apple does not expose focus stacking as a feature | Confirmed | No public documentation or review mentions it |
| vivo X300 Pro uses 200MP periscope for telemacro at 2.7:1 | Confirmed | vivo product page, DXOMARK, multiple reviews |
| vivo dual-chip pipeline provides ISP processing not available to third-party apps | Inference | Public product specs describe hardware; API access to VS1/V3+ is not documented |
| OPPO Find X8 Pro "macro mode" is subpar; real macro is via 6x periscope | Confirmed | Multiple independent reviews |
| OPPO's best macro path requires ignoring the advertised mode | Confirmed | Reviewers explicitly recommend this |
| Android LENS_FOCUS_DISTANCE uses diopter units | Confirmed | Android API documentation, StackOverflow community |
| Most consumer phones are APPROXIMATE calibration | Confirmed | Android API documentation, developer reports |
| VCM lenses lack positional feedback on most phones | Confirmed | Hardware design analysis, multiple developer reports |
| Focus distance 0.0 may not achieve true infinity on many devices | Confirmed | Multiple developer reports across devices (Pixel, Nexus, etc.) |
| CameraX interop operates at builder level, not per-frame | Confirmed | Android CameraX documentation |
| Transformer-based MFF methods achieve SOTA on benchmarks | Confirmed | Multiple 2024-2025 publications with reproducible results |
| No published MFF method handles all handheld challenges simultaneously | Inference | Literature review; no paper claims this, and survey papers list these as open gaps |
| MFF benchmarks use predominantly synthetic data | Confirmed | Multiple 2024-2025 survey papers identify this as a key gap |
| A simpler method with confidence scoring may be more appropriate than SOTA Transformer for V2 | Inference | Engineering judgment based on mobile constraints and gap between benchmark conditions and handheld reality |

## Research Limitations

1. **No direct page access**: All web sources were accessed via search engine results, not direct page fetches. Page content may have changed since the search index was built. Specific technical claims (e.g., exact minimum focus distances) should be verified against manufacturer spec sheets before being used as implementation constraints.

2. **No real-device verification**: All vendor behavior descriptions (Apple macro switching, vivo telemacro, OPPO periscope macro) are based on published reviews and product pages. Actual device behavior may differ from documented behavior, especially across firmware versions.

3. **Algorithm research is lab-benchmark, not field-validated**: MFF methods are evaluated on standard datasets with pre-registered images and controlled focus differences. Performance on handheld smartphone captures with real misalignment, motion, exposure variation, and lens breathing is unknown and likely significantly lower than reported benchmark metrics.

4. **Android API behavior is OEM-dependent**: The Camera2 API defines contracts, but OEM implementations vary. Specific devices may not honor LENS_FOCUS_DISTANCE even when reporting APPROXIMATE calibration. Lens switching latency and 3A convergence behavior vary significantly across devices and are not documented in public APIs.

5. **Vendor product claims may change**: The vivo X300 Pro and OPPO Find X8 Pro are current as of this research date (May 2026). Product pages and specifications may be updated or removed. Apple's iOS 26 camera behavior is current but may change in point releases.

6. **No access to proprietary imaging pipelines**: vivo's VS1/V3+ chips, Apple's Neural Engine ISP, and OPPO's Hasselblad pipeline are black boxes. We can observe their outputs through reviews but cannot determine which processing steps are available to third-party apps via public APIs.

7. **No China-market-specific sources accessed**: vivo and OPPO China-market product pages (vivo.com.cn, oppo.cn) may contain more detailed specifications than international pages. These were not directly accessed.

8. **Research scope is bounded**: This research covers the sources listed in the source table above. It does not include Samsung, Xiaomi, Huawei, or other Android OEMs whose macro/close-up implementations may offer additional patterns. It also does not include academic literature beyond the MFF survey papers cited.
