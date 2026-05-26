# Package Status: 02-quick-pixel-surface-design

- **Agent**: vivo-x300-02-quick-pixel-surface-design
- **Status**: completed
- **Started**: 2026-05-26T16:49:56Z
- **Completed**: 2026-05-27

## Worktree

- Path: main workspace (research/design only, no code changes)
- Branch: main

## Changes

- git status: no code changes (research/design only)
- git diff --stat: (none — read-only package)
- Changed files: `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/02-quick-pixel-surface-design.md` (this file)

## Verification

### Commands Run

```bash
# Grep for all still-capture-output-size related symbols
grep -rn "displayedStillCaptureOutputSize|isStillResolutionToggleEnabled|StillCaptureResolutionToggled|quickMegapixelLabel|availableStillCaptureOutputSizes" app/src/main/java app/src/test core/session/src/main -g '*.kt'

# Grep for quick resolution rendering
grep -rn "stillResolutionQuickLabel\|quickMegapixelLabel\|resolutionRow" app/src/main/java/ -g '*.kt'
```

### Test Results

- Read-only code inspection confirms the current pipeline as documented by package 01.
- No runtime changes made; no tests to run.

---

## Evidence

### 1. Current Quick Pixel Row Behavior (Source-Proven)

**Rendering** (`SessionCockpitRenderModel.kt:740-752`):

`stillResolutionQuickLabel(state, strings)` produces the quick panel label:
1. If no native output size is resolved → returns `preset.label` (e.g., "12MP", "8MP", "2MP").
2. If `outputSize` is explicitly null AND preset is `LARGE_12MP` → returns `preset.label` ("12MP").
3. Otherwise → returns `native.quickMegapixelLabel()` which computes `${round(pixelCount / 1_000_000)}MP`.

Key insight: The label already derives from actual pixel count, not a hardcoded string. If a 50MP (8160×6120) output size were available, the label would show "50MP" automatically. **The labeling code is already correct for high-pixel sizes.** The problem is upstream: package 01 proved that 50MP sizes are never discovered.

**Toggle enabled** (`SessionStateRender.kt:31-37`):

`isStillResolutionToggleEnabled(state)` returns true when:
- Active template is `STILL_CAPTURE`, AND
- `availableStillCaptureOutputSizes.size > 1` OR `availableStillCaptureResolutionPresets.size > 1`.

On a vivo X300 where only ~13MP sizes are discovered, there may still be multiple sizes (e.g., 4000×3000, 3264×2448, 1600×1200), so the toggle stays enabled. But it cycles between 12MP/8MP/2MP presets, never reaching 50MP.

**Toggle dispatch** (`MainActivityActionBinder.kt:127-129`):

`views.quickPanel.resolution.setOnClickListener` dispatches `SessionIntent.StillCaptureResolutionToggled`.

**Session handler** (`DefaultCameraSession.kt:746-778`):

`handleStillCaptureResolutionToggled()` is currently a stub (TODO: re-enable). The actual cycle logic at lines 1506-1518 (`nextStillCaptureOutputSize`) cycles through available sizes sorted by descending pixel count.

### 2. Decision Table: Quick Pixel Row Labels and Affordance

Based on package 01's `SUPPORTED / DEGRADED / UNSUPPORTED` capability contract:

| Capability State | availableStillCaptureOutputSizes | Quick Label | Toggle Enabled | Tap Behavior | Badge/Note |
|---|---|---|---|---|---|
| **SUPPORTED — multiple high-pixel sizes** | e.g., [8160×6120, 4000×3000, 3264×2448, 1600×1200] | Shows current: "50MP", "12MP", "8MP", "2MP" | YES | Cycle through native sizes (descending pixel count) | None needed |
| **SUPPORTED — single high-pixel size** | e.g., [8160×6120] only | "50MP" | NO (size count = 1, preset count = 1) | Disabled; no valid toggle target | None — truthful single option |
| **SUPPORTED — standard sizes only** | e.g., [4000×3000, 3264×2448, 1600×1200] | "12MP" (current LARGE_12MP default) | YES | Cycle presets: 12MP → 8MP → 2MP → 12MP | None — current behavior preserved |
| **DEGRADED — sensor has high-MP but CameraX cannot bind** | e.g., [8160×6120] discovered but CameraX binding fails | "50MP (不可用)" or "50MP*" | NO or DISABLED with reason | Disabled tap shows degraded reason toast | "高像素模式需要设备支持" |
| **DEGRADED — high-MP available but mode-restricted** | Available in PHOTO but not in NIGHT/PORTRAIT | Mode-aware: "50MP" in PHOTO, "12MP" in NIGHT | Mode-aware | Cycle in supported modes; disabled with reason in others | Per-mode label |
| **UNSUPPORTED — no high-pixel path** | Only standard sizes ≤ 12MP | Current behavior: "12MP" | YES | Cycle presets as today | None |
| **UNSUPPORTED — no still capture** | Template != STILL_CAPTURE | Row hidden or disabled | NO | N/A | N/A |

### 3. Proposed State and Persistence Semantics

#### New fields needed in `DeviceCapabilities` (from package 01):

```
highPixelCapabilityStatus: HighPixelCapabilityStatus  // SUPPORTED | DEGRADED | UNSUPPORTED
highPixelDegradedReason: String?                       // human-readable explanation for DEGRADED
```

#### New field in `StillCaptureOutputSize` or discovery metadata:

```
discoverySource: StillCaptureSizeSource  // STANDARD | MAXIMUM_RESOLUTION | HIGH_RESOLUTION
```

This does NOT change the quick pixel row's rendering logic directly — it's upstream metadata for diagnostics and future filtering.

#### Persistence model:

| What to Persist | Where | Rationale |
|---|---|---|
| Selected native output size (`StillCaptureOutputSize`) | `DeviceGraphSpec.stillCapture.outputSize` (already exists) | User's explicit choice of capture resolution |
| Resolution preset (`StillCaptureResolutionPreset`) | `DeviceGraphSpec.stillCapture.resolutionPreset` (already exists) | Legacy preset path; kept for backward compat |
| Best-effort high-pixel preference | New: `PersistedSettings.highPixelPreference: Boolean` | Remembers that user wanted high-pixel even if device changes |
| Per-lens selection | NOT in v1 | Adds complexity; most users have one preferred rear lens. Defer to future iteration. |
| Per-mode selection | NOT in v1 | Mode plugins can already filter capabilities; session can re-resolve on mode switch. |

#### State resolution order on session start:

1. Load `outputSize` from persisted `DeviceGraphSpec`.
2. If null, load `resolutionPreset` and resolve to nearest available size.
3. If `highPixelPreference == true` AND capability is `SUPPORTED`, prefer the largest available size.
4. Clamp to device's actual `availableStillCaptureOutputSizes`.

### 4. UI Truthfulness Rules

1. **No 50MP label unless discoverable**: The label must derive from `StillCaptureOutputSize.pixelCount`, not from marketing claims. Current code already does this via `quickMegapixelLabel()`.

2. **No switch affordance when only one output exists**: `isStillResolutionToggleEnabled` already checks `size > 1 || presets > 1`. If package 01's implementation discovers only one size, toggle is correctly disabled.

3. **No hiding high-MP behind preset labels**: If a 50MP size is available as a native output size, the quick label must show "50MP", not "12MP". Current code already does this — `stillResolutionQuickLabel` returns the native label when `outputSize` is explicitly set.

4. **No false "available" when degraded**: When `highPixelCapabilityStatus == DEGRADED`, the row must show the degraded label and be disabled or show a toast on tap. Do NOT show "50MP" in a clickable/active state.

5. **Mode-aware truth**: In modes that cannot use high-pixel (e.g., NIGHT requires multi-frame), the label should reflect the actual available resolution for that mode, not the global maximum.

### 5. Future Code Touchpoints

| File | Change | Purpose |
|---|---|---|
| `core/device/.../DeviceContracts.kt` | Add `HighPixelCapabilityStatus` enum; add fields to `DeviceCapabilities` | Upstream capability truth |
| `core/device/.../DeviceContracts.kt` | Add `discoverySource` to `StillCaptureOutputSize` or `CameraLensProfile` | Track how size was discovered |
| `app/.../CameraXCaptureAdapter.kt` | Query `getMaximumResolution()` and `getHighResolutionOutputSizes()` in `detectCameraLensProfiles()` | Discover high-MP sizes |
| `app/.../CameraXCaptureAdapter.kt` | Set `highPixelCapabilityStatus` in `resolveDeviceCapabilities()` | Record bindability |
| `app/.../SessionCockpitRenderModel.kt` | Update `stillResolutionQuickLabel()` to handle DEGRADED state | Show degraded badge |
| `app/.../SessionCockpitRenderModel.kt` | Update `quickPanelSheetRenderModel()` resolution row for capability-aware enabled/disabled | Correct toggle state |
| `app/.../SessionStateRender.kt` | Update `isStillResolutionToggleEnabled()` to respect DEGRADED | Prevent false toggle |
| `core/session/.../DefaultCameraSession.kt` | Complete `handleStillCaptureResolutionToggled()` stub | Actual toggle logic |
| `core/settings/.../PersistedSettings.kt` | Add `highPixelPreference` field | Persist user choice |
| `core/session/.../DefaultCameraSession.kt` | Add `highPixelPreference`-aware initialization | Honor preference on restart |

### 6. Future Test List

| Test | Module | Purpose |
|---|---|---|
| `SessionCockpitRenderModelTest` — label shows "50MP" when 50MP output is available | `:app` | Verify correct megapixel label |
| `SessionCockpitRenderModelTest` — label shows "12MP" when only standard sizes available | `:app` | Preserve current behavior |
| `SessionCockpitRenderModelTest` — toggle disabled when DEGRADED | `:app` | Prevent false affordance |
| `SessionCockpitRenderModelTest` — toggle disabled when only one size available | `:app` | Single-size devices |
| `SessionCockpitRenderModelTest` — mode-aware label: 50MP in PHOTO, 12MP in NIGHT | `:app` | Per-mode truth |
| `SessionStateRenderTest` — `isStillResolutionToggleEnabled` returns false for DEGRADED | `:app` | Guard toggle logic |
| `SessionStateRenderTest` — `displayedStillCaptureOutputSize` returns correct size for SUPPORTED | `:app` | Size display correctness |
| `DefaultCameraSessionTest` — toggle cycles through 50MP → 12MP → 8MP → 2MP | `:core:session` | Cycle correctness with high-MP |
| `DefaultCameraSessionTest` — toggle blocked during countdown/capture | `:core:session` | Preserve existing guards |
| `DefaultCameraSessionTest` — highPixelPreference initializes to largest size | `:core:session` | Preference persistence |
| `CameraXCaptureAdapterCapabilityDetectionTest` — getMaximumResolution discovery | `:app` | Upstream size discovery (package 01 scope) |

### 7. Risks and Product Questions

| # | Risk / Question | Owner | Status |
|---|---|---|---|
| R1 | **Real-device verification gap**: All decisions are based on code analysis. The actual vivo X300 behavior (what `getOutputSizes` and `getMaximumResolution` return) is unknown. Design assumes package 01's hypothesis is correct. | User / QA | Pending (package 04) |
| R2 | **CameraX binding**: Even if 50MP is discovered, CameraX `ResolutionSelector` may refuse to bind it. The DEGRADED path handles this, but the exact CameraX behavior per version is unverified. | Package 04 | Pending |
| R3 | **Performance impact**: High-pixel capture (50MP) may have significantly longer capture latency. The shutter lifecycle design (package 03) must account for this. | Package 03 | Pending |
| R4 | **Storage impact**: 50MP JPEG files are ~15-25MB vs ~3-5MB for 12MP. Should the UI warn about storage? | Product decision | Needs user input |
| R5 | **Mode compatibility**: Not all modes can use high-pixel (NIGHT multi-frame, VIDEO). The design says "mode-aware" but the exact per-mode filtering rules need mode plugin owners to confirm. | Mode plugin owners | Needs confirmation |
| R6 | **Per-lens behavior**: Some devices have different max resolutions per lens (wide vs ultrawide vs tele). The design defers per-lens selection to v1 but this may be a common real-world case. | Product decision | Deferred |
| R7 | **DEGRADED UX**: When high-pixel is DEGRADED, should the row be hidden, disabled-with-toast, or visible-with-badge? The design suggests disabled-with-toast but this is a product choice. | Product decision | Needs user input |
| R8 | **HighPixel preference on device change**: If user enables high-pixel on vivo X300 then switches to a device without it, the preference should be silently ignored. | Implementation detail | Addressed in design |

### 8. Architecture Boundary Preservation

The design preserves all architecture boundaries from AGENTS.md:

- **UI renders state and dispatches intents only**: The quick panel row reads from `SessionCockpitRenderModel` and dispatches `SessionIntent.StillCaptureResolutionToggled`. No direct camera calls.
- **Session Kernel owns state**: `DefaultCameraSession` owns `sessionStillCaptureOutputSize` and `sessionStillCaptureResolutionPreset`. Toggle logic lives in `handleStillCaptureResolutionToggled()`.
- **Device adapter owns hardware truth**: `CameraXCaptureAdapter` discovers sizes and sets `highPixelCapabilityStatus`. Session reads from `DeviceCapabilities`.
- **No second session kernel**: No new state management in UI, coordinators, or adapters.

---

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks

- **Real-device evidence gap**: All design decisions are based on code analysis and package 01's hypothesis. vivo X300 real-device verification (package 04) is critical before implementation.
- **handleStillCaptureResolutionToggled is a stub**: The session handler is currently `TODO: re-enable`. The design assumes it will be completed as part of implementation, not as this research package.
- **CameraX version dependency**: Whether the project's CameraX version internally queries `getMaximumResolution()` affects whether the fix is app-layer-only or needs CameraX upgrade.
- **Product decisions deferred**: High-pixel storage warning UX, DEGRADED row visual treatment, and per-lens selection strategy all need product input before implementation.
