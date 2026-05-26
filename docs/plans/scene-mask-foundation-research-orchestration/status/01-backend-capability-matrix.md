# Package Status: 01-backend-capability-matrix

- **Agent**: Claude Code (research agent)
- **Status**: completed
- **Started**: 2026-05-27
- **Completed**: 2026-05-27

## Worktree

- Path: `.claude/worktrees/scene-mask-research-01`
- Branch: `worktree-scene-mask-research-01`

## Changes

- git status: no runtime code changes
- git diff --stat: (none)
- Changed files: `docs/plans/scene-mask-foundation-research-orchestration/status/01-backend-capability-matrix.md` only

## Verification

- Commands run:
  - `grep -rn "SceneMask|MlKit|SubjectSegmentation|Segmentation|getClient|ImageAnalysis" app core`
  - `grep -rn "mlkit|mediapipe|segmentation|tflite|onnx" app/build.gradle.kts`
  - `grep -rn "minSdk|compileSdk|targetSdk" app/build.gradle.kts`
  - `find . -name "*SceneMask*" -o -name "*Segmentation*"`
- Test results: not applicable (research package, no code changes)

## Evidence

### Source Links

| Source | URL |
|---|---|
| ML Kit Selfie Segmentation Android | https://developers.google.com/ml-kit/vision/selfie-segmentation/android |
| ML Kit Subject Segmentation Android | https://developers.google.com/ml-kit/vision/subject-segmentation/android |
| MediaPipe Image Segmenter Android | https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android |
| CameraX ImageAnalysis | https://developer.android.com/reference/androidx/camera/core/ImageAnalysis |
| CameraX Extensions | https://developer.android.com/media/camera/camerax/extensions |
| Camera2 DEPTH_OUTPUT | https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT |
| OPPO Portrait (Reno 11) | https://www.oppo.com/en/newsroom/stories/oppo-reno11-series-portrait-expert-lofficiel/ |
| OPPO Portrait (Reno 15) | https://www.oppo.com/en/newsroom/press/oppo-launches-reno15-series/ |
| vivo X300 ZEISS Portrait | https://www.vivo.com/en/products/activity/x300 |
| TFLite Segmentation Overview | https://www.tensorflow.org/lite/examples/segmentation/overview |
| ONNX Runtime Mobile | https://onnxruntime.ai/docs/tutorials/mobile/ |

### Code References

| File | Relevance |
|---|---|
| `app/build.gradle.kts:80` | Current dependency: `com.google.mlkit:segmentation-selfie:16.0.0-beta6` |
| `app/build.gradle.kts:12` | `minSdk = 26` (meets all ML Kit requirements) |
| `app/src/main/java/.../MlKitSelfiePreviewSceneMaskSource.kt` | Working ML Kit Selfie preview implementation using `STREAM_MODE` |
| `app/src/main/java/.../MlKitSavedPhotoSceneMaskProvider.kt` | Working ML Kit Selfie saved-photo implementation using `SINGLE_IMAGE_MODE` |
| `app/src/main/java/.../NoOpPreviewSceneMaskSource.kt` | Fallback for unsupported/degraded paths |
| `app/src/main/java/.../SavedPhotoSceneMaskProvider.kt` | Interface + NoOp + coordinate mapper |
| `core/media/.../SceneMaskContracts.kt` | Full contract layer: roles, quality, support enum, capability, pipeline notes |
| `app/src/main/java/.../CameraXCaptureAdapter.kt:2736-2738` | `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` already wired |
| `app/src/main/java/.../AppContainer.kt:74-77` | Saved-photo mask provider: try ML Kit, fallback NoOp |
| `app/src/main/java/.../AppContainer.kt:141-144` | Preview mask source: try ML Kit, fallback NoOp |
| `core/effect/.../PreviewEffectModel.kt:71` | `PreviewSceneMaskSnapshot` data class with UNAVAILABLE sentinel |

## Capability Matrix

### Backend Comparison

| Dimension | ML Kit Selfie Segmentation | ML Kit Subject Segmentation | MediaPipe Image Segmenter | Camera2 Depth / CameraX Extensions | Self-managed TFLite / ONNX |
|---|---|---|---|---|---|
| **Status in codebase** | **Integrated and wired** | Not integrated | Not integrated | `ImageAnalysis` used for preview frames only; depth not used | Not integrated |
| **Dependency** | `com.google.mlkit:segmentation-selfie:16.0.0-beta6` (bundled) | Subject variant (unbundled) | MediaPipe Tasks SDK | Android platform / CameraX Extensions | Custom TFLite / ONNX Runtime |
| **Beta status** | Beta | Beta | Stable (Tasks API) | Stable | N/A |
| **Min API** | API 23+ | API 24+ | API 24+ (MediaPipe Tasks) | Varies by capability | N/A |
| **Google Play Services** | Not required (bundled ~4.5MB) | **Required** (unbundled ~200KB, model downloaded at runtime) | Not required (bundled model) | Not required | Not required |
| **Network requirement** | None after install | **Initial model download** | None after bundling | None | None |
| **Segmentation scope** | Person/selfie only | Person/subject foreground (multi-subject) | Category mask or confidence mask (multi-class) | Hardware depth map (if available) | Depends on model |
| **Stream / real-time** | Yes (`STREAM_MODE`) | **No** — static images only (per official docs) | Yes | Yes (if hardware supports) | Depends on model |
| **Latency** | ~25-65ms (Pixel 4, official) | ~200ms (Pixel 7 Pro, official) | Varies by model/device | Hardware-dependent | Varies |
| **Output type** | Per-pixel confidence mask (float buffer) | Per-pixel foreground/multi-subject mask | Category mask or confidence mask | DEPTH16 / point cloud | Depends on model |
| **Semantic classes** | Person only | Person/subject foreground | Sky, person, animal, etc. (model-dependent) | Depth only | Depends on model |
| **Mask quality** | Good for person; edges may flicker in stream | Higher quality for still images | Varies by model; generally good | True depth (hardware-dependent) | Depends on model |

### Capability Labels by Use Case

| Use Case | ML Kit Selfie | ML Kit Subject | MediaPipe | Camera2 Depth | TFLite/ONNX |
|---|---|---|---|---|---|
| **Preview person mask** | `SUPPORTED` | `UNSUPPORTED` (no stream mode) | `SUPPORTED` (if integrated) | `UNSUPPORTED` (coverage) | `UNSUPPORTED` (not integrated) |
| **Saved photo person mask** | `SUPPORTED` | `SUPPORTED` (if Play Services available) | `SUPPORTED` (if integrated) | `UNSUPPORTED` (coverage) | `UNSUPPORTED` (not integrated) |
| **General subject/object foreground** | `UNSUPPORTED` | `DEGRADED` (beta, requires download) | `SUPPORTED` (if integrated) | `UNSUPPORTED` | `UNSUPPORTED` (not integrated) |
| **Portrait bokeh** | `DEGRADED` (2D mask, not true depth) | `DEGRADED` (2D mask, not true depth) | `DEGRADED` (2D mask, not true depth) | `SUPPORTED` (if hardware) | `UNSUPPORTED` |
| **Color Lab subject protection** | `SUPPORTED` | `SUPPORTED` (if Play Services) | `SUPPORTED` (if integrated) | `UNSUPPORTED` | `UNSUPPORTED` |
| **Background tuning** | `SUPPORTED` | `SUPPORTED` (if Play Services) | `SUPPORTED` (if integrated) | `UNSUPPORTED` | `UNSUPPORTED` |
| **Depth slider** | `UNSUPPORTED` (2D mask ≠ depth) | `UNSUPPORTED` (2D mask ≠ depth) | `UNSUPPORTED` (2D mask ≠ depth) | `DEGRADED` (hardware-dependent) | `UNSUPPORTED` |
| **Sky/background/object semantic regions** | `UNSUPPORTED` | `UNSUPPORTED` | `DEGRADED` (model-dependent) | `UNSUPPORTED` | `UNSUPPORTED` |

### Condition Table: What Flips Labels

| Backend | Condition | From | To |
|---|---|---|---|
| ML Kit Selfie | Device lacks bundled model or init fails | `SUPPORTED` | `UNSUPPORTED` |
| ML Kit Selfie | Stream mode frame rate too low (<3 fps sustained) | `SUPPORTED` | `DEGRADED` |
| ML Kit Selfie | Subject ratio < 2% (no person detected) | `SUPPORTED` | `UNSUPPORTED` (per current code: `SceneMaskResult.Unavailable("no-person-detected")`) |
| ML Kit Subject | Google Play Services unavailable | `SUPPORTED` | `UNSUPPORTED` |
| ML Kit Subject | Model download fails or times out | `SUPPORTED` | `UNSUPPORTED` |
| ML Kit Subject | Device offline at capture time | `SUPPORTED` | `UNSUPPORTED` |
| ML Kit Subject | Latency > 500ms on low-end device | `SUPPORTED` | `DEGRADED` |
| MediaPipe | Model not bundled in APK | `SUPPORTED` | `UNSUPPORTED` |
| MediaPipe | Wrong model selected (e.g., selfie-only model) | `SUPPORTED` | `DEGRADED` |
| Camera2 Depth | `DEPTH_OUTPUT` not in `REQUEST_AVAILABLE_CAPABILITIES` | `DEGRADED` | `UNSUPPORTED` |
| Camera2 Depth | Depth frame rate < 5 fps | `DEGRADED` | `UNSUPPORTED` |
| Any backend | `ImageAnalysis` analyzer backlog (frames dropped) | `SUPPORTED` | `DEGRADED` |
| Any backend | Segmentation exception during processing | `SUPPORTED` | `DEGRADED` (current code catches and returns `Failed`) |

### Key Constraints and Warnings

1. **Beta disclaimer**: Both ML Kit Selfie and ML Kit Subject are explicitly beta. The codebase must treat them as `SUPPORTED` only with a `DEGRADED` fallback path always available.

2. **2D mask is NOT true depth**: All ML Kit and MediaPipe outputs are per-pixel confidence masks indicating "is this pixel part of a person/subject?". They do NOT provide metric depth. The codebase must not:
   - Claim depth slider capability from a 2D mask.
   - Use `SceneMaskRole.DEPTH_APPROXIMATION` for any ML Kit output.
   - Imply that mask-based bokeh is equivalent to hardware depth bokeh.

3. **ML Kit Subject requires Google Play Services**: This violates OpenCamera's no-network/no-secret client assumption for the default path. ML Kit Subject can only be `SUPPORTED` when:
   - Device has Google Play Services.
   - Model has been downloaded.
   - Network was available at least once after app install.
   - Otherwise it is `UNSUPPORTED`.

4. **ML Kit Subject only supports static images**: The official documentation states "static images" only. It cannot be used for real-time preview segmentation. For preview, only ML Kit Selfie or MediaPipe are viable.

5. **Bundled vs unbundled model size**:
   - ML Kit Selfie: ~4.5MB bundled in APK.
   - ML Kit Subject: ~200KB in APK but model downloaded at runtime (size varies).
   - MediaPipe: depends on model choice; can be bundled.

6. **OPPO/vivo product semantics**: OPPO's "scene layers / subject separation / skin protection / adjustable bokeh" and vivo's "ZEISS Multifocal Portrait" use hardware depth + ISP-level processing. OpenCamera can borrow product language (profile, subject/background style, depth strength) but must NOT claim equivalence to vendor pipelines. The honest framing is "mask-aware color and background treatment" not "portrait mode like OPPO/vivo".

7. **Existing code already uses ML Kit Selfie for both paths**: `MlKitSelfiePreviewSceneMaskSource` (STREAM_MODE) and `MlKitSavedPhotoSceneMaskProvider` (SINGLE_IMAGE_MODE) are both wired in `AppContainer.kt` with NoOp fallbacks. No ML Kit Subject or MediaPipe code exists yet.

## Recommended Phase-1 Backend Decision

### Default: ML Kit Selfie Segmentation (already integrated)

**Rationale:**
- Already implemented and wired for both preview (STREAM_MODE) and saved-photo (SINGLE_IMAGE_MODE).
- Bundled dependency — works offline, no Google Play Services required.
- Fits OpenCamera's no-network client assumption.
- Person mask is sufficient for the primary use cases: Color Lab subject protection, background tuning, and basic portrait style.
- Latency is acceptable for preview (~25-65ms) and saved-photo (~100-200ms).

**Honest labels for ML Kit Selfie (phase-1):**

| Capability | Label | Reason |
|---|---|---|
| Preview person mask | `SUPPORTED` | Working in codebase with STREAM_MODE |
| Saved photo person mask | `SUPPORTED` | Working in codebase with SINGLE_IMAGE_MODE |
| Portrait bokeh | `DEGRADED` | 2D mask only, not true depth; edge quality requires visual QA |
| Color Lab subject protection | `SUPPORTED` | Mask-aware rendering implemented in `PhotoAlgorithmPostProcessor` |
| Background tuning | `SUPPORTED` | Mask provides subject/background separation |
| Depth slider | `UNSUPPORTED` | 2D confidence mask is not depth |
| General object/semantic segmentation | `UNSUPPORTED` | Person-only model |

### Alternatives (Phase-2+)

| Backend | When to adopt | Trigger |
|---|---|---|
| ML Kit Subject Segmentation | When product needs multi-subject or non-person foreground masks AND can accept Play Services dependency | User feedback that person-only mask is insufficient; Color Lab needs to protect non-person subjects |
| MediaPipe Image Segmenter | When product needs sky/animal/object semantic classes for advanced features | Product roadmap requires "sky replacement" or "object-aware effects"; ML Kit Subject quality insufficient |
| Self-managed TFLite/ONNX | When ML Kit beta instability becomes a shipping blocker or when need for full model control | ML Kit deprecation, quality regression, or need for custom fine-tuned model |

## Delivery

- Commit hash: (pure research, no code changes)
- PR link: (none)

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks

1. **Real-device latency validation needed**: Official latency numbers (Pixel 4 for Selfie, Pixel 7 Pro for Subject) may not reflect mid-range or older device behavior. Phase-1 should include a latency smoke test on at least one non-Pixel device.

2. **ML Kit Selfie edge quality**: STREAM_MODE mask edges may flicker or have halos. Visual QA on real saved JPEG is required before claiming "portrait bokeh" as shipped quality.

3. **ML Kit Selfie beta lifecycle**: Google may change the API, deprecate it, or move it out of beta with breaking changes. The NoOp fallback path mitigates this but should be monitored.

4. **No general object/semantic segmentation yet**: If user requests "protect the sky" or "protect the dog", ML Kit Selfie cannot help. This is an explicit UNSUPPORTED gap for phase-1.

5. **OpenCamera's no-network assumption**: ML Kit Subject's unbundled/download requirement is fundamentally incompatible with offline-first. If Subject Segmentation is ever adopted, the UI must clearly communicate the network dependency and graceful degradation.
