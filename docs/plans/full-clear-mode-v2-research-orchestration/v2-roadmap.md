# Full Clear V2 - Roadmap

## Preconditions

- V1 mode surface and focus-bracket contracts have landed or are ready to merge.
- Stage 7 gates are stable enough to absorb a new product-mode implementation wave.
- Real-device owner can provide vivo X300 evidence after local implementation.

## Suggested Implementation Waves

### Wave 1: Core Contracts (no CameraX changes)

- Define `FullClearSceneAssessment`, route enum, and render model states.
- Define `FullClearDeviceCapabilities` with `from(DeviceCapabilities)` mapping.
- Define `FullClearCapturePlan` and `FullClearFallbackPolicy`.
- Define `FullClearFusionReport` with all per-stage fields.
- All pure functions, fully unit-testable without device.

**Gate**: All enum values and data classes compile. Route selection pure function passes unit tests (≥ 15 test cases covering supported/degraded/unsupported paths).

### Wave 2: Deep-DOF Route

- Implement ultra-wide lens selection through existing `DeviceGraphSpec` zoom ratio.
- Scene assessment for near-subject detection and deep-DOF suitability.
- User guidance states: READY, TOO_CLOSE, USE_WIDE, UNSUPPORTED.
- Diagnostics: route confidence, degradation reasons in pipeline notes.

**Gate**: Ultra-wide route produces correct lens selection. Guidance states transition correctly. Diagnostics tags (`fc:*`) written to pipeline notes.

### Wave 3: Focus Bracket Route

- Per-frame focus distance override in `DeviceShotRequestTranslator`.
- Build focus bracket steps (near→mid→far diopter interpolation).
- Pre-capture gyro stability check (shutter gate).
- Latency budget: 3-5 frame bracket within product latency target.
- Motion/stability guard: reduce confidence when gyro reports motion.

**Gate**: Per-frame focus values correctly propagated to Camera2 interop. Bracket frame count degrades under instability/thermal. Gyro stability gate prevents shutter when device is moving.

### Wave 4: Fusion Algorithm (Stages 0–7)

- **Stage 0**: Exposure normalization (YUV→RGB, histogram matching, reference selection).
- **Stage 1**: Coarse alignment (ORB + homography, inlier ratio gate).
- **Stage 2**: Alignment confidence gate (NCC grid, composite confidence).
- **Stage 3**: Lens breathing compensation (scale extraction, crop/scale, severity).
- **Stage 4**: Sharpness map generation (Laplacian variance on 8×8 blocks).
- **Stage 5**: Laplacian pyramid fusion (4-level pyramid, max-sharpness blend).
- **Stage 6**: Fusion confidence gate (edge consistency, ghost detection, artifact check).
- **Stage 7**: Output selection (decision tree: FUSED / DEGRADED / BEST_FRAME).

**Gate (synthetic)**: Each stage passes ≥ 5 synthetic test cases. Output selector decision tree produces correct `FullClearResultState` for all threshold regions. No stage crashes on edge cases (single frame, identical frames, extreme brightness).

**Gate (real-device, deferred)**: Bracket capture + fusion pipeline runs end-to-end on vivo X300 without crash. At least one scene produces `FUSED` result. At least one scene produces `BEST_FRAME` fallback (verifying fallback works).

### Wave 5: Diagnostics and QA Tooling

- `FullClearFusionReport.toPipelineNotes()` → all per-stage tags written.
- Sidecar JSON diagnostics (optional, for development QA): serialize full `FullClearFusionReport` to JSON alongside saved JPEG.
- Batch processing QA script: feed N bracket frame sets, produce diagnostics CSV.
- Degradation classification: each `fc:degraded=X` tag maps to a known degradation class.

**Gate**: Diagnostics provide enough information to diagnose why a fusion failed without re-running the shot. QA script can process a directory of bracket captures and produce a summary report.

### Wave 6: Real-Device QA

- Compare Photo, Scenery, Full Clear V1, and Full Clear V2 on vivo X300 scenes.
- Collect saved images, logs, route metadata, latency, and artifacts.
- Run algorithm pass rate matrix (tripod, handheld steady, handheld walking, breathing lens, subject motion, low light, macro near-far).
- Perceptual A/B testing: FUSED vs best single frame.
- Threshold calibration iteration based on real pass rates.

**Gate**: Pass rate meets or exceeds documented expectations. No crash or ANR in any scene type. Diagnostics complete for every shot.

### Wave 7: Integration and Polish

- Integrate `FullClearModePlugin` into the app's mode registry.
- User guidance UI strings finalized (Chinese).
- Thermal/resource gating integration verified.
- ShotGraph wiring for `FOCUS_BRACKET` and `DEEP_DOF_LENS` routes.

**Gate**: Full Clear V2 appears in mode selector on supported devices. Shutter-to-save < 3s for 3-frame bracket. Memory < 200MB peak.

## Algorithm Wave Dependencies

```
Wave 1 (Contracts) ──┬──> Wave 2 (Deep-DOF) ──┐
                     │                         │
                     ├──> Wave 3 (Bracket) ────┼──> Wave 6 (QA) ──> Wave 7 (Integration)
                     │                         │
                     └──> Wave 4 (Fusion) ─────┤
                                               │
                               Wave 5 (Diag) ──┘
```

Waves 2, 3, 4, and 5 can be developed in parallel after Wave 1 lands.

## Open Risks (Cannot Be Proven Locally)

These must be validated with real-device data before claiming V2 is production-ready:

| Risk | Impact | Validation Method |
|---|---|---|
| Handheld alignment pass rate too low (< 30%) | Users mostly get BEST_FRAME, not fusion | Real-device pass rate measurement on vivo X300 |
| Fusion quality worse than best single frame | Algorithm produces artifacts that look worse | Perceptual A/B testing with human judgment |
| ORB features unreliable on smartphone sensors | Alignment fails on low-texture or noisy scenes | ORB match rate on real bracket frames |
| Thresholds need significant retuning | Default thresholds produce wrong decisions | Calibration sweep on real bracket datasets |
| Pyramid fusion memory exceeds budget | OOM on low-RAM devices | Memory profiling on target device tier |
| Breathing compensation inadequate on some lenses | DEGRADED results have visible edge artifacts | Per-lens breathing characterization |
| Per-frame focus override unreliable on CameraX | Bracket frames have same focus → no fusion benefit | Camera2 interop verification per device |

## Go / No-Go

V2 should proceed to implementation only if the team accepts:

1. The first implementation may produce BEST_FRAME fallback more often than true fusion. This is still valuable if it saves users from the wrong lens/focus choice during hiking/travel captures.
2. Algorithm thresholds are estimates and will require real-device tuning (Wave 6).
3. Fusion quality on handheld shots cannot be guaranteed without real-device pass rate data.
4. Parallax, subject motion, and HDR are explicitly out of scope for V2.

