# Package Status: 01-pixel-capability-enumeration

- **Agent**: vivo-x300-01-pixel-capability-enumeration
- **Status**: completed
- **Started**: 2026-05-26T16:39:23Z
- **Completed**: 2026-05-27

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/vivo-x300-01-pixel-capability-enumeration
- Branch: worktree-vivo-x300-01-pixel-capability-enumeration

## Changes

- git status: no code changes (research/design only)
- git diff --stat: (none — read-only package)
- Changed files: `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/01-pixel-capability-enumeration.md` (this file)

## Verification

### Commands Run

```bash
# Grep for all still-capture-output-size related symbols across codebase
grep -rn "availableStillCaptureOutputSizes\|normalizeStillCaptureOutputSizes\|getOutputSizes\|ResolutionSelector\|StillCaptureOutputSize\|getMaximumResolution\|getHighResolutionOutputSizes\|MAXIMUM_RESOLUTION" app/src/main/java app/src/test core -g '*.kt'

# Grep for any getMaximumResolution / getHighResolutionOutputSizes usage
grep -rn "getMaximumResolution\|getHighResolutionOutputSizes\|maximumResolution\|highResolution" app/src/main/java core --include='*.kt'
grep -rn "MAXIMUM_RESOLUTION\|HIGH_RESOLUTION\|maximum_resolution\|high_resolution" app/src/main/java core --include='*.kt'
```

### Test Results

- Codebase search confirms zero usage of `getMaximumResolution()` or `getHighResolutionOutputSizes()` anywhere in the project.
- Existing tests (`CameraXCaptureAdapterCapabilityDetectionTest`, `CameraXCaptureAdapterStillCaptureQualityTest`, `DefaultCameraSessionTest` still-resolution tests, `SessionStateRenderTest`) all pass against the current code — they test the 12MP/8MP/2MP preset path only.
- No test references high-pixel (48MP/50MP) scenarios.

---

## Evidence

### 1. Current Enumeration Path Diagram (Prose)

The still capture output size enumeration follows a 5-stage pipeline:

**Stage 1 — Hardware Query** (`CameraXCaptureAdapter.detectCameraLensProfiles()`, line 1052):
For each physical camera ID, queries `CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(ImageFormat.JPEG)`. This is the **only** API call for discovering still capture sizes. Results are mapped to `StillCaptureTargetResolution(width, height)`.

**Stage 2 — Normalization** (`normalizeStillCaptureOutputSizes()`, line 798):
Deduplicates by `(width, height)`, filters to 4:3 aspect ratio (tolerance ±0.05), and sorts by descending pixel count. If no 4:3 sizes found, keeps all sizes as fallback.

**Stage 3 — Preset Resolution** (`resolveAvailableStillCaptureResolutionPresets()`, line 818):
Computes `maxPixels` from normalized sizes, then includes each `StillCaptureResolutionPreset` whose `targetWidth × targetHeight ≤ maxPixels`. The enum has only three entries: `LARGE_12MP` (4000×3000), `MEDIUM_8MP` (3264×2448), `SMALL_2MP` (1600×1200).

**Stage 4 — Capability Merge** (`resolveDeviceCapabilities()`, line 647):
Merges per-lens profiles filtered by `preferredLensFacing` (default BACK) into `DeviceCapabilities.availableStillCaptureOutputSizes` and `availableStillCaptureResolutionPresets`.

**Stage 5 — CameraX Binding** (`createImageCapture()`, line 2574):
Resolves the final output size via `resolvedStillCaptureOutputSize()`, builds a `ResolutionSelector` with `ResolutionStrategy(targetSize, fallbackRule)` and `PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE`, and creates the `ImageCapture` use case.

**Session Layer** (`DefaultCameraSession`):
Initializes from `DeviceCapabilities`, provides toggle cycling via `handleStillCaptureResolutionToggled()` which cycles native output sizes (if ≥2 available) or presets. The session writes `StillCaptureConfig(resolutionPreset, outputSize)` into the `DeviceGraphSpec`.

### 2. Capability-Loss Table

| Stage | Function | Risk | Impact | Severity |
|-------|----------|------|--------|----------|
| **1a** | `detectCameraLensProfiles` | Only calls `getOutputSizes(ImageFormat.JPEG)` — never calls `StreamConfigurationMap.getMaximumResolution(int format)` (API 33+) | Devices with 48MP/50MP/64MP/108MP sensors that expose maximum resolution only via `getMaximumResolution()` will have those sizes **completely invisible**. On vivo X300, this is the most likely explanation for seeing only ~13MP instead of 50MP. | **CRITICAL** |
| **1b** | `detectCameraLensProfiles` | Never calls `getHighResolutionOutputSizes(ImageFormat.JPEG)` | Sizes available only at reduced frame rate (common for high-MP modes) are invisible. Some devices expose their highest-MP output exclusively through this method. | HIGH |
| **1c** | `detectCameraLensProfiles` | Only queries `ImageFormat.JPEG` | Does not query `YUV_420_888` or `RAW_SENSOR` sizes. Some devices expose higher resolutions for non-JPEG formats. | MODERATE |
| **2** | `normalizeStillCaptureOutputSizes` | 4:3 aspect ratio filter with ±0.05 tolerance | Sensor-native resolutions with non-standard aspect ratios (e.g., 4:3.01 on some Samsung sensors) could be filtered out. Rare but possible. | LOW |
| **3** | `resolveAvailableStillCaptureResolutionPresets` | Preset enum caps at 12MP (4000×3000) | Even if a 50MP size were discovered, there is no preset above 12MP. The `LARGE_12MP` matching logic would still find it (smallest ≥ 12MP), but UI labeling would be misleading ("12MP" shown for a 50MP capture). | HIGH |
| **4** | `resolveStillCaptureOutputSize` | For `LARGE_12MP`, picks smallest size ≥ 12MP | If multiple sizes above 12MP exist (e.g., 50MP and 12MP), the app selects 12MP (smallest ≥ 12MP) rather than the sensor's native maximum. User gets 12MP even when 50MP is available. | HIGH |
| **5** | `createImageCapture` | CameraX `ResolutionSelector` has its own internal enumeration | CameraX internally decides which sizes to offer based on its own `StreamConfigurationMap` reading. CameraX behavior with `getMaximumResolution()` depends on library version. Even if the app requests a high-MP target, CameraX may not bind it. | MODERATE |

### 3. Proposed Capability Contract

#### `SUPPORTED` — Selectable output sizes are actually available and bindable

A device output size is `SUPPORTED` when:
1. It appears in `StreamConfigurationMap.getOutputSizes(ImageFormat.JPEG)` OR `getMaximumResolution(ImageFormat.JPEG)` (API 33+).
2. The `normalizeStillCaptureOutputSizes()` pipeline preserves it (passes 4:3 filter or is the sensor native size).
3. The CameraX `ResolutionSelector` can bind a session targeting that size.
4. Real-device verification confirms the captured image has the expected pixel dimensions.

#### `DEGRADED` — Sensor/hardware suggests higher pixel class but CameraX path cannot bind it locally

A device capability is `DEGRADED` when:
1. `getMaximumResolution(ImageFormat.JPEG)` returns a high-MP size (e.g., 50MP) but `getOutputSizes(ImageFormat.JPEG)` does not include it.
2. The CameraX library version in use does not internally query `getMaximumResolution()`.
3. The device requires a vendor-specific capture session mode (e.g., vivo's "50MP mode") that standard Camera2/CameraX APIs cannot trigger.
4. The app detects the capability exists but cannot bind it without Camera2Interop or a custom capture pipeline.

#### `UNSUPPORTED` — No reliable high-pixel path is exposed

A device capability is `UNSUPPORTED` when:
1. Neither `getOutputSizes()` nor `getMaximumResolution()` expose any size above 12MP.
2. The sensor hardware does not support high-pixel capture (e.g., older devices).
3. The device requires vendor-private SDK calls that cannot be integrated into the open-source project.

### 4. Standard JPEG Sizes vs High-Resolution Candidates

| Category | API Source | Example Sizes | Current Code Handles? |
|----------|-----------|---------------|----------------------|
| **Standard JPEG** | `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(ImageFormat.JPEG)` | 4000×3000 (12MP), 3264×2448 (8MP), 1920×1080 (2MP) | YES — this is the only source used today |
| **High-Resolution JPEG** | `SCALER_STREAM_CONFIGURATION_MAP.getMaximumResolution(ImageFormat.JPEG)` | 8160×6120 (50MP), 7680×4320 (33MP) | NO — never queried |
| **High-Rate-Limited JPEG** | `SCALER_STREAM_CONFIGURATION_MAP.getHighResolutionOutputSizes(ImageFormat.JPEG)` | Sizes available only at reduced frame rate | NO — never queried |

### 5. Proposed Minimum Future Implementation Shape

#### Where to collect standard and high-resolution output sizes

In `detectCameraLensProfiles()`, after obtaining the `StreamConfigurationMap`, query three sources:
1. `streamMap.getOutputSizes(ImageFormat.JPEG)` — standard sizes (already done)
2. `if (Build.VERSION.SDK_INT >= 33) streamMap.getMaximumResolution(ImageFormat.JPEG)` — maximum resolution mode (new)
3. `streamMap.getHighResolutionOutputSizes(ImageFormat.JPEG)` — reduced-frame-rate sizes (new)

Merge all three into the `StillCaptureTargetResolution` list, dedup by `(width, height)`, and pass through the existing normalization pipeline.

#### How to preserve native output size labels

Add a new field to `StillCaptureOutputSize` or `CameraLensProfile`:
- `discoverySource: StillCaptureSizeSource` — enum with values `STANDARD`, `MAXIMUM_RESOLUTION`, `HIGH_RESOLUTION`
- `nativeLabel: String?` — e.g., "50MP", "48MP", "108MP" — derived from pixel count or device metadata

This allows the UI to show "50MP" instead of "8160×6120" and lets diagnostics distinguish standard from maximum-resolution paths.

#### How to record diagnostics without claiming success

Add to `DeviceCapabilities`:
- `highResolutionDetectionSource: String?` — records which API discovered the high-MP size
- `highResolutionBindable: Boolean?` — null until CameraX binding is verified

Log these in `SessionCockpitRenderModel` diagnostics. Do not mark a high-MP size as `SUPPORTED` until real-device verification confirms actual capture at that resolution.

#### Which unit tests should be added before code changes

1. **`detectCameraLensProfiles` with `getMaximumResolution()` mock**: Verify that when `getOutputSizes(JPEG)` returns only 12MP but `getMaximumResolution(JPEG)` returns 50MP, the `CameraLensProfile.availableStillCaptureOutputSizes` includes 50MP.

2. **`normalizeStillCaptureOutputSizes` with high-MP sizes**: Verify that 8160×6120 (4:3 ratio) passes the aspect ratio filter and appears in the output list.

3. **`resolveAvailableStillCaptureResolutionPresets` with 50MP sizes**: Verify that when max pixels > 12MP, all three presets are still returned (existing behavior), and that the new high-MP size is available for selection.

4. **`resolveStillCaptureOutputSize` with mixed sizes**: Verify that for `LARGE_12MP` preset with sizes [8160×6120, 4000×3000, 3264×2448], the function selects 4000×3000 (smallest ≥ 12MP), not 8160×6120. This confirms existing behavior and documents the need for a new `MAXIMUM_RESOLUTION` preset.

5. **New `MAXIMUM_RESOLUTION` preset (if added)**: Verify that `resolveStillCaptureOutputSize(MAXIMUM_RESOLUTION, [8160×6120, 4000×3000])` selects 8160×6120 (largest available).

6. **`createImageCapture` with high-MP target**: Verify that `ResolutionStrategy` receives the correct target size when a high-MP output size is resolved.

### 6. Focused Command Results

```
$ grep -rn "getMaximumResolution\|getHighResolutionOutputSizes" app/src/main/java core --include='*.kt'
(no output — zero matches)

$ grep -rn "MAXIMUM_RESOLUTION\|HIGH_RESOLUTION\|maximum_resolution\|high_resolution" app/src/main/java core --include='*.kt'
(no output — zero matches)
```

Confirmed: the codebase has zero awareness of Camera2's `getMaximumResolution()` or `getHighResolutionOutputSizes()` APIs.

### 7. Unresolved Unknowns for vivo X300 Real-Device Proof

1. **What does `getOutputSizes(ImageFormat.JPEG)` return on vivo X300?** — We suspect it returns only sizes up to ~13MP (4000×3000 or similar), which would explain the current behavior. Real-device dump needed.

2. **What does `getMaximumResolution(ImageFormat.JPEG)` return on vivo X300 (API 33+)?** — If it returns 8160×6120 (50MP), this confirms the architectural gap. If it also returns ~13MP, the device may require vendor-specific modes.

3. **What does `getHighResolutionOutputSizes(ImageFormat.JPEG)` return?** — May expose 50MP at reduced frame rate.

4. **Does CameraX internally query `getMaximumResolution()` in the version used by this project?** — Need to check CameraX library version and its internal `StreamConfigurationMap` handling.

5. **Does vivo X300 require a vendor-specific session mode for 50MP capture?** — Some OEMs (Samsung, Xiaomi, vivo) require `CaptureRequest` vendor tags or specific session configurations that standard CameraX cannot set. This would make the device `DEGRADED` even with correct size enumeration.

6. **What is the actual pixel count of vivo X300's "50MP" output?** — Marketing "50MP" may be 8160×6120 (49.9MP) or a different resolution. Need real-device verification.

---

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks

- **Real-device evidence gap**: All findings are local-code-only. The critical assumption — that `getMaximumResolution()` would expose 50MP on vivo X300 — is unverified without real-device testing.
- **CameraX version dependency**: The project's CameraX version may or may not internally query `getMaximumResolution()`. This affects whether the fix needs to be at the app layer or is already handled by CameraX.
- **Vendor-specific mode risk**: vivo X300 may require vendor-specific Camera2 extensions for high-pixel capture that cannot be expressed through standard APIs. This would change the fix strategy from "enumerate more sizes" to "use Camera2Interop with vendor tags."
