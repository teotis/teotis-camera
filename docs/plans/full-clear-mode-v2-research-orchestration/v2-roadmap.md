# Full Clear V2 - Roadmap

## Preconditions

### V1 Stability Gates (Must Pass Before V2 Implementation Starts)

| Gate | Verification | Owner |
|---|---|---|
| V1 mode surface is deployed | `FullClearModePlugin` exists with `ModeId.FULL_CLEAR` in mode registry | CI |
| V1 shutter-to-save latency | < 2s for single-frame capture on reference device | CI + real-device |
| V1 focus-bracket contracts compile | `FocusBracketStep`, `FocusBracketPlan` data classes exist in `core/device` | CI |
| V1 session kernel handles multi-frame | `DefaultCameraSession` processes `CaptureStrategy.MultiFrame` without crash or ANR | CI + real-device |
| V1 pipeline notes are written | `ShotResult.pipelineNotes` populated with `fc:v1:*` tags for all V1 captures | CI |
| V1 device capability detection stable | `DeviceCapabilities.manualControlCapabilities` and `zoomRatioCapability` populated on ≥ 3 test devices | real-device |

V2 implementation must not begin until all V1 stability gates pass. This ensures V2 builds on a reliable foundation rather than chasing V1 bugs during V2 development.

### Infrastructure Prerequisites

- Stage 7 observability pipeline is stable (pipeline notes, sidecar JSON, QA batch processing).
- Real-device owner can provide vivo X300 evidence after local implementation.
- Synthetic test image generation tooling exists (geometric transforms, blur, exposure, noise injection).
- CI runs unit tests for `core/device`, `core/media`, and `core/mode` on every commit.

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

## Autonomous vs External-Assist Gate Separation

V2 governance separates gates into two categories. **Autonomous gates** can be verified by CI, unit tests, or synthetic data without human involvement. **External-assist gates** require a human with a physical device, visual judgment, or domain expertise. External-assist evidence is release/product-confidence oriented — it gates the decision to ship V2 to users, not the decision to merge implementation code.

### Autonomous Gates (CI / Unit Tests / Synthetic Data)

These gates run automatically. A failure blocks the next implementation wave.

| Gate | Verification Method | Blocked Wave |
|---|---|---|
| All enum values and data classes compile | Kotlin compilation | Wave 1 → Wave 2/3/4/5 |
| Route selection pure function ≥ 15 test cases | JUnit, synthetic `FullClearDeviceCapabilities` fixtures | Wave 1 → Wave 2/3/4/5 |
| `FullClearDeviceCapabilities.from()` maps correctly | JUnit, `DeviceCapabilities` test fixtures | Wave 1 → Wave 2/3/4/5 |
| `FullClearFusionReport.toPipelineNotes()` produces correct tags | JUnit, constructed `FullClearFusionReport` instances | Wave 1 → Wave 5 |
| `selectFullClearRoute` returns correct route per device profile | JUnit, ≥ 20 test cases covering all supported/degraded/unsupported combos | Wave 1 gate |
| `buildFullClearCapturePlan` produces correct bracket steps | JUnit, diopter interpolation verification | Wave 1 gate |
| `buildFocusBracketSteps` interpolation correctness | JUnit, known near/far pairs | Wave 1 gate |
| Camera2 interop per-frame focus values propagated correctly | JUnit, `DeviceShotRequestTranslator` with `perFrameFocusOverrides` | Wave 3 gate |
| Gyro stability gate prevents shutter when device is moving | JUnit, mock gyro with injected motion | Wave 3 gate |
| Bracket frame count degrades under injected instability/thermal | JUnit, mock thermal state + gyro instability | Wave 3 gate |
| Each algorithm stage (0–7) passes ≥ 5 synthetic test cases | JUnit, programmatically generated frame pairs | Wave 4 gate (synthetic) |
| Output selector decision tree correct for all threshold regions | JUnit, all combinations of alignment/fusion/breathing scores | Wave 4 gate (synthetic) |
| Degradation classification maps every `fc:degraded=X` tag | JUnit, enum completeness check | Wave 5 gate |
| QA batch script processes directory and produces CSV summary | Shell script, synthetic frame directories | Wave 5 gate |
| `FullClearModePlugin` integrates into mode registry without crash | JUnit + instrumentation test | Wave 7 gate |
| Shutter-to-save < 3s for 3-frame bracket | Instrumented timing test (synthetic frames) | Wave 7 gate |
| Memory < 200MB peak during fusion | JUnit heap profiling (synthetic frames) | Wave 7 gate |

### External-Assist Gates (Real Device / Human Judgment)

These gates require a human with a physical device. They gate **release/product confidence**, not code merge. Merging code without passing these gates means the feature is implemented but not validated for production.

| Gate | Verification Method | Who | Evidence Required | Blocks |
|---|---|---|---|---|
| Per-frame focus override works on real CameraX/Camera2 | Write LENS_FOCUS_DISTANCE, capture, read EXIF | Device owner | EXIF distance within 10% of set value | Release confidence |
| Bracket capture + fusion runs end-to-end without crash | Real-device test on vivo X300 | Device owner | Logcat trace, saved images | Release confidence |
| At least one scene produces `FUSED` result | Real-device test, textured static scene | Device owner | `FullClearFusionReport` with resultState=FUSED | Release confidence |
| At least one scene produces `BEST_FRAME` fallback | Real-device test, induced motion during bracket | Device owner | `FullClearFusionReport` with resultState=BEST_FRAME | Release confidence |
| Handheld alignment pass rate measured | N≥30 handheld bracket captures on vivo X300 | Device owner | Pass rate table per scene type | Release confidence |
| Perceptual A/B: FUSED vs best single frame | Human side-by-side comparison on 10+ scenes | Device owner + 1 other viewer | Per-scene judgment (Better/Same/Worse) | Release confidence |
| Threshold calibration iteration | Adjust thresholds based on real pass rates | Device owner + developer | Updated threshold table with rationale | Release confidence |
| All 7 scene types pass rate measured | Tripod, handheld steady, walking, breathing lens, subject motion, low light, macro near-far | Device owner | Per-scene pass rate matrix | Release confidence |
| vivo X300 comparison: Photo, Scenery, V1, V2 | Same scene, all 4 modes, saved images + logs | Device owner | Comparison image set + route metadata | Product confidence |
| Chinese UI strings reviewed | Native speaker review of all 10 guidance states | Chinese-speaking reviewer | Approved string list | Release confidence |

### Gate Governance Rule

- **Autonomous gates** are embedded in CI and block merge of the corresponding implementation wave.
- **External-assist gates** are documented as release checklist items. They do NOT block code merge. They block the decision to enable V2 in production builds or mark it as non-experimental.
- If an external-assist gate fails, the corresponding implementation code remains merged but the feature stays behind a flag (e.g., `FULL_CLEAR_V2_ENABLED = false`) until re-validation passes.

## Real-Device QA Failure Triage Rules

When a real-device QA test fails or produces unexpected results, follow these triage rules before modifying code or thresholds. Each rule maps a failure symptom to the most probable root cause and the recommended action.

### Triage Table

| Failure Symptom | Probable Root Cause | Triage Action | Recovery Criterion |
|---|---|---|---|
| **FUSED rate < 20% on tripod** | ORB features insufficient on sensor, or alignment threshold too strict | 1. Export ORB match visualizations from diagnostic sidecar. 2. Check inlier ratios per frame pair. 3. If inlier ratios > 0.5 but NCC < 0.6, lower ALIGNMENT_THRESHOLD to 0.5. 4. If inlier ratios < 0.3 on most frames, try AKAZE or increase ORB feature count to 1000. | FUSED rate ≥ 50% on tripod retest |
| **FUSED rate < 30% on handheld-steady** | Inter-frame motion exceeds alignment tolerance | 1. Check per-frame gyro magnitude in diagnostics. 2. If gyro > 0.02 rad/s during bracket, tighten pre-capture stability gate. 3. If gyro < 0.01 but alignment still fails, alignment is too sensitive — lower NCC_GRID_SIZE to 8×8 (coarser check). 4. If ghost artifacts dominate, increase FUSION_THRESHOLD to 0.6. | FUSED rate ≥ 30% on handheld-steady retest, or BEST_FRAME rate honestly accepted |
| **All handheld-walking results are BEST_FRAME** | Expected behavior — walking motion exceeds alignment capability | 1. Verify that gyro stability gate correctly prevents shutter during walking. 2. Verify that BEST_FRAME result saves sharpest frame (not random frame). 3. Document walking pass rate as "expected BEST_FRAME" — this is not a defect. | BEST_FRAME rate ≥ 90% on walking; no crashes; sharpest frame correctly selected |
| **DEGRADED rate > 30%** | Breathing severity over-estimated or common on this lens | 1. Check per-frame scale deviations in diagnostics. 2. If scale deviations < 5% but severity > 0.3, the severity formula needs recalibration. 3. If scale deviations are truly > 5%, the lens breathes significantly — document this lens as "breathing-prone." 4. Consider raising BREATHING_SEVERITY_THRESHOLD to 0.4 for this device class. | DEGRADED rate < 20%, or lens documented as breathing-prone with user guidance |
| **Fusion output looks worse than best single frame (perceptual A/B)** | Pyramid fusion creating artifacts not caught by confidence gates | 1. Check edge consistency score — if > 0.8 but output looks bad, gate is not catching the artifact type. 2. Examine ghost detection output — if ghostRejectionApplied = false but double edges visible, ghost detector needs tuning. 3. Check per-frame sharpness maps — if wrong frame selected as reference, exposure normalization may have picked a suboptimal reference. | On next A/B retest: FUSED rated "Better" or "Same" in ≥ 70% of comparisons |
| **Shutter-to-save > 3s for 3-frame bracket** | Focus latency per frame too high, or fusion too slow | 1. Profile per-frame focus latency. If > 500ms/frame, reduce bracket to 2 frames. 2. Profile fusion time per stage (exposure norm, alignment, pyramid, confidence). 3. If pyramid fusion > 1s at 12MP, reduce pyramid levels from 4 to 3 or downscale input to 8MP. 4. If alignment > 500ms, reduce ORB feature count to 300. | Shutter-to-save < 3s for 3-frame bracket after optimization |
| **Memory > 200MB peak during fusion** | Pyramid buffers or alignment intermediates not released | 1. Profile heap: track per-stage allocation. 2. Check if RGBFrame instances are released after warp. 3. Ensure pyramid levels reuse buffers instead of allocating new ones at each level. 4. If still over budget, process frames at half resolution for fusion (output still at full resolution via pyramid collapse). | Peak memory < 200MB on target device tier |
| **Per-frame focus override has no effect (EXIF shows same focus distance)** | Camera2 LENS_FOCUS_DISTANCE not writable on this device, or CameraX interop blocking per-frame changes | 1. Try writing focus distance via raw Camera2 CaptureRequest.Builder (bypass CameraX interop). 2. If still no effect, mark device as `focusDistanceWritable = false`. 3. Route all captures through DEEP_DOF_LENS (if ultra-wide available) or BEST_FRAME_ONLY. 4. Document device model in unsupported list. | Route selection correctly disables FOCUS_BRACKET on this device |
| **ORB match rate < 30% on real frames** | Sensor noise or ISP processing (denoising, sharpening) degrading feature quality | 1. Save raw YUV frames from bracket (no ISP post-processing if possible). 2. Try AKAZE detector (more robust to noise but slower). 3. Reduce ORB threshold for feature detection (more features, lower quality). 4. If still < 30%, alignment is not viable — FOCUS_BRACKET route degrades to BEST_FRAME_ONLY. | Either match rate > 50% after tuning, or route disabled with documented reason |
| **Ghost artifacts visible but ghostRejectionApplied = false** | Ghost detector sensitivity too low | 1. Save fused output and source frames for the failing scene. 2. Check ghost detector parameters: double-edge search radius, edge match threshold. 3. Increase ghost detector sensitivity (reduce edge match distance threshold from 2px to 3px). 4. Re-run on saved frames to verify detection fires. | ghostRejectionApplied = true on the same scene retest |
| **Thermal: 20 brackets in 2 minutes causes crash or ANR** | Fusion pipeline not releasing resources, or thermal throttle not reducing frame count | 1. Check if `ResourceAdmissionPolicy.admitMultiFrame()` correctly reduces frame count under thermal stress. 2. Verify that `FullClearCapturePlan.maxTotalLatencyMs` is respected by the shot executor. 3. Add cooldown between brackets if thermal state is WARM or HOT. 4. If crash is OOM, see Memory triage above. | 20 brackets in 2 minutes complete without crash; frame count degrades but does not crash |

### Triage Process

1. **Reproduce**: Re-run the failing test on the same device and scene. Confirm it's reproducible, not a one-off.
2. **Capture evidence**: Save all diagnostic artifacts (images, logs, sidecar JSON, FullClearFusionReport) for the failing case.
3. **Classify**: Use the triage table above to identify the most probable root cause.
4. **Apply triage action**: Follow the recommended action from the table.
5. **Re-test**: Run the same test again. If it passes, record the fix. If it still fails, escalate to the next probable cause.
6. **Document**: Record the failure, root cause, fix, and retest result in the real-device QA log.

### Expected (Non-Failure) Outcomes

Some outcomes are not failures even though they produce BEST_FRAME or DEGRADED:

| Scenario | Expected Result State | Why Not a Failure |
|---|---|---|
| Walking while capturing | BEST_FRAME | Motion exceeds alignment capability — fallback is correct behavior |
| Low-light scene (ISO > 800) | BEST_FRAME or DEGRADED | Noise dominates sharpness; fusion cannot improve on single frame |
| Subject motion (wind-blown leaves, people walking) | BEST_FRAME or DEGRADED | Ghost artifacts would make fusion worse than best frame |
| Single-lens device without focus control | BEST_FRAME_ONLY (route) | Hardware limitation — route is correctly disabled |
| Ultra-wide not available + focus not writable | UNSUPPORTED (route) | Hardware limitation — V2 correctly reports unsupported |

## Open Risks (Cannot Be Proven Locally)

These must be validated with real-device data before claiming V2 is production-ready.

| Risk | Impact | Validation Method | Triage Reference |
|---|---|---|---|
| Handheld alignment pass rate too low (< 30%) | Users mostly get BEST_FRAME, not fusion | Real-device pass rate measurement on vivo X300 | See triage: "FUSED rate < 30% on handheld-steady" |
| Fusion quality worse than best single frame | Algorithm produces artifacts that look worse | Perceptual A/B testing with human judgment | See triage: "Fusion output looks worse than best single frame" |
| ORB features unreliable on smartphone sensors | Alignment fails on low-texture or noisy scenes | ORB match rate on real bracket frames | See triage: "ORB match rate < 30% on real frames" |
| Thresholds need significant retuning | Default thresholds produce wrong decisions | Calibration sweep on real bracket datasets | See triage: "FUSED rate < 20% on tripod" |
| Pyramid fusion memory exceeds budget | OOM on low-RAM devices | Memory profiling on target device tier | See triage: "Memory > 200MB peak" |
| Breathing compensation inadequate on some lenses | DEGRADED results have visible edge artifacts | Per-lens breathing characterization | See triage: "DEGRADED rate > 30%" |
| Per-frame focus override unreliable on CameraX | Bracket frames have same focus → no fusion benefit | Camera2 interop verification per device | See triage: "Per-frame focus override has no effect" |

## Go / No-Go Decision Framework

V2 implementation proceeds through sequential gates. Each gate is a decision point where the team evaluates evidence and decides to proceed, pause, or stop.

### Per-Wave Go/No-Go Gates

#### Wave 1 Go/No-Go (Core Contracts)

**Go criteria (all must pass):**
- All V1 stability gates pass (see Preconditions).
- All enum values and data classes compile in `core/device` and `core/media`.
- Route selection pure function passes ≥ 15 unit test cases.
- `FullClearDeviceCapabilities.from()` correctly maps `DeviceCapabilities`.
- Design review confirms no CameraX/Camera2 imports in mode-layer code.

**No-Go triggers (any):**
- V1 gates not all passing → finish V1 stabilization first.
- Route selection logic depends on runtime I/O (CameraX, network, file).
- `FullClearFusionReport` missing required fields from product definition.

**If No-Go**: Stop. Fix V1 stability issues or contract design issues. Do not start Wave 2/3/4/5.

#### Wave 2 Go/No-Go (Deep-DOF Route)

**Go criteria:**
- Wave 1 Go criteria passed.
- Ultra-wide lens selection correctly identified via `DeviceGraphSpec` zoom ratio for ≥ 3 device profiles.
- All 5 user guidance states render correct Chinese labels (unit test + manual review).
- Diagnostics tags written for every state transition (unit test).

**No-Go triggers:**
- Lens selection depends on hardcoded lens IDs (must use `lensNodeMap` with zoom ratio).
- Guidance states bypass `ModeState` pipeline (must use `headline/detail`).

#### Wave 3 Go/No-Go (Focus Bracket Route)

**Go criteria:**
- Waves 1 Go criteria passed (Wave 2 optional but recommended).
- Per-frame focus values correctly propagated to `DeviceShotRequest.perFrameFocusOverrides` (unit test ≥ 5 cases).
- Bracket step interpolation produces correct diopter sequence for 3, 5, and 6 frames.
- Pre-capture gyro gate prevents shutter when injected motion exceeds threshold.

**No-Go triggers:**
- Per-frame focus requires CameraX API changes (must use Camera2 interop).
- Bracket step count doesn't degrade under thermal stress.
- Gyro gate depends on continuous sensor polling instead of on-shutter check.

#### Wave 4 Go/No-Go (Fusion Algorithm)

**Go criteria (synthetic gate only — real-device gate is Wave 6):**
- Waves 1 Go criteria passed (Waves 2/3 optional).
- Each fusion stage (0–7) passes ≥ 5 synthetic test cases.
- Output selector decision tree produces correct `FullClearResultState` for all threshold region combinations.
- No stage crashes on edge cases (single frame, identical frames, extreme brightness).
- Best-frame fallback correctly selects sharpest frame (verified by synthetic test: blurred vs sharp).

**No-Go triggers:**
- Any stage depends on real camera frames (must be pure function on pixel buffers).
- Fusion confidence gate can't distinguish between perfectly fused and randomly blended frames (synthetic test).
- Pyramid fusion produces visible seams on complementary-sharpness synthetic test.

**Note on real-device gate**: Wave 4's real-device gate is deferred to Wave 6. Wave 4 code can merge without real-device fusion quality validation, but must not be enabled in production builds until Wave 6 passes.

#### Wave 5 Go/No-Go (Diagnostics and QA Tooling)

**Go criteria:**
- `FullClearFusionReport.toPipelineNotes()` produces all required `fc:*` tags.
- Sidecar JSON contains all `FullClearFusionReport` fields (round-trip test: serialize → deserialize → compare).
- QA batch script processes N synthetic frame directories and produces correct summary CSV.

**No-Go triggers:**
- Diagnostics tags miss required fields for any result state (FUSED, BEST_FRAME, DEGRADED).
- Batch script fails on empty directory, single frame, or corrupted frame.

#### Wave 6 Go/No-Go (Real-Device QA) — External Assist

**Go criteria (all must pass for production enablement):**
- Bracket capture + fusion runs end-to-end on vivo X300 without crash.
- At least one scene produces `FUSED` result.
- At least one scene produces `BEST_FRAME` fallback.
- Diagnostics complete for every shot (no missing `fc:*` tags).
- Shutter-to-save < 3s for 3-frame bracket.
- All 7 scene type pass rates measured and documented.
- Perceptual A/B: FUSED rated "Better" or "Same" in ≥ 70% of comparisons.

**No-Go triggers (any):**
- Crash or ANR in any scene type.
- 0% FUSED rate even on tripod → alignment fundamentally broken on this device.
- Fusion output consistently rated "Worse" in perceptual A/B.
- Diagnostics missing or incomplete for any shot.

**If No-Go on real-device gate**: Keep V2 behind feature flag. Iterate on thresholds/algorithms. Re-run Wave 6. Do NOT enable in production.

#### Wave 7 Go/No-Go (Integration and Polish)

**Go criteria:**
- Waves 1–5 Go criteria passed.
- Wave 6 Go criteria passed OR V2 remains behind feature flag with documented limitations.
- `FullClearModePlugin` integrates into mode registry and appears in mode selector.
- Shutter-to-save < 3s for 3-frame bracket (instrumented test + real-device measurement).
- Memory < 200MB peak (heap profiling).
- Chinese UI strings reviewed by native speaker.

**No-Go triggers:**
- Mode plugin crashes on unsupported devices (must gracefully show UNSUPPORTED state).
- Feature flag `FULL_CLEAR_V2_ENABLED` not implemented (must be able to disable without code revert).
- Any guidance string missing or has incorrect Chinese.

### V2 Production Enablement Decision

Even after all waves complete, V2 should not be enabled in production until:

1. Wave 6 real-device QA passes on at least one reference device.
2. The team accepts the documented product promise: "V2 does not guarantee both near and far planes are equally sharp."
3. Feature flag `FULL_CLEAR_V2_ENABLED = false` is in place as a kill switch.
4. Degradation telemetry is wired up so the team can monitor FUSED/BEST_FRAME/DEGRADED ratios in the field.

### Deferred V2 Decision

The acceptance criteria for this research package explicitly allow:

> V2 implementation can be deferred until V1 is stable.

This means:
- All above Go/No-Go gates assume V1 is stable first.
- If V1 stability issues are found during V2 development, V2 work pauses — V1 fixes take priority.
- The V2 code can remain on a feature branch behind a flag indefinitely without blocking V1 releases.
- V2 implementation waves can be spread across multiple release cycles (e.g., Waves 1–3 in one cycle, Waves 4–7 in the next).

