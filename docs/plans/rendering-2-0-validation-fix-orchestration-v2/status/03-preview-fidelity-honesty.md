# Package Status: 03-preview-fidelity-honesty

- **Agent**: agent-render-v2-03-preview (background)
- **Status**: INTERRUPTED - investigation complete, implementation not started
- **Started**: 2026-05-26
- **Completed**: (interrupted by user)

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/agent-render-v2-03-preview`
- Branch: `worktree-agent-render-v2-03-preview` (clean, based on `bf87213`)

## Investigation Findings

### Problem Analysis (Complete)

The package doc references `PreviewOverlayView.applyColorTransformToPreview(...)` — this method **does not exist** in the current codebase. The actual situation:

1. **Current preview effect path**: `PreviewEffectAdapter.adapt()` → `PreviewEffectRenderModel` → `FilterOverlaySpec` (tintColor, tintAlpha, vignetteStrength, warmthShift) → `PreviewOverlayView.drawFilterOverlay()` draws a semi-transparent colored rectangle over the preview. This is a **visual approximation only** — not a real color matrix transform.

2. **Saved output path**: Uses real `ColorMatrix`/`ColorMatrixColorFilter` in `PhotoWatermarkPostProcessor.kt` (line 840-847).

3. **The gap**: The CameraX `PreviewView` (at `views.preview.previewView`) supports `View.setColorFilter(ColorMatrixColorFilter)` which would apply a real color matrix to the preview pixels, but this is never called. Only the overlay tint approximation is used.

4. **No `applyColorTransformToPreview` exists** — the audit's reference is to the *absence* of this functionality, not a commented-out method.

### Key Files Read

| File | Status | Key Finding |
|------|--------|-------------|
| `PreviewOverlayView.kt` | Read fully | No color matrix logic; only overlay tint via `drawFilterOverlay()` |
| `PreviewEffectAdapter.kt` (core/effect) | Read fully | Builds `FilterOverlaySpec` with tint approximation; no color matrix |
| `PreviewEffectModel.kt` (core/effect) | Read fully | `FilterOverlaySpec` has tintColor/tintAlpha/vignetteStrength/warmthShift; no colorMatrix field |
| `EffectSpec.kt` (core/effect) | Read fully | `FilterEffect` holds `FilterRenderSpec?` |
| `SettingsDataModels.kt` (core/settings) | Read fully | `FilterRenderSpec` has: brightnessShift, contrast, saturation, warmthShift, tintShift, monochromeMix, vignetteStrength, softGlowStrength, haloStrength, grainStrength, sharpnessBoost, highlightCompression, shadowLift, warmBoost, coolBoost |
| `SessionPreviewRenderModel.kt` | Read fully | `PreviewOverlayRenderModel` holds `effectModel: PreviewEffectRenderModel?`; `previewOverlayRenderModel()` builds it |
| `MainActivity.kt:284` | Read | `views.preview.overlayView.render(previewOverlayRenderModel(state, container.previewEffectAdapter))` — overlay only, no PreviewView color filter |
| `MainActivityViews.kt` | Read fully | `PreviewViews` contains `previewView: PreviewView` and `overlayView: PreviewOverlayView` |
| `PreviewEffectAdapterTest.kt` (core/effect) | Read fully | Existing tests cover overlay tint, no color matrix tests |
| `SessionPreviewRenderModelTest.kt` | Read fully | Tests grid/countdown visibility, no color transform tests |

### Allowed Paths (from package doc)

- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` ✅
- `app/src/main/java/com/opencamera/app/**PreviewColorTransform*` (new file to create)
- `app/src/main/java/com/opencamera/app/camera/PreviewEffectAdapter.kt` — NOTE: actual file is at `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`; may need coordination if touching core/effect
- `app/src/test/java/com/opencamera/app/**Preview*` ✅
- `app/src/test/java/com/opencamera/app/**ColorTransform*` (new test file)

### Forbidden Paths

- `CameraXCaptureAdapter.kt` — cannot wire color filter there
- `PhotoAlgorithmPostProcessor.kt`
- `core/effect/**` unless test-only fixture (coordinate)

## Planned Implementation (Not Started)

### 1. Create `PreviewColorTransform.kt`
- New file at `app/src/main/java/com/opencamera/app/PreviewColorTransform.kt`
- Pure-JVM helper: `FilterRenderSpec` → `FloatArray` (4x5 color matrix)
- Map: saturation → `ColorMatrix.setSaturation()`, contrast → scale+offset, brightness → offset, warmthShift → R/B channel bias, tintShift → G channel bias, monochromeMix → desaturation blend

### 2. Add `previewColorMatrix` to `PreviewEffectRenderModel`
- New field in `PreviewEffectRenderModel`: `val previewColorMatrix: FloatArray?`
- New enum: `PreviewFidelityState { EXACT, DEGRADED, DISABLED }`
- New field: `val fidelityState: PreviewFidelityState`

### 3. Update `PreviewEffectAdapter.adapt()`
- Call `PreviewColorTransform.buildMatrix(filter.renderSpec)` to produce the matrix
- Set fidelity state based on whether matrix is identity/degraded

### 4. Update `PreviewOverlayView`
- Accept `PreviewView` reference or expose `applyPreviewColorFilter(previewView: View, matrix: FloatArray?)`
- Apply `ColorMatrixColorFilter` to the PreviewView
- Clear filter when no effect active
- Keep overlay tint only for vignette (which can't be done via color matrix)

### 5. Wire in `MainActivity.kt` (render function)
- After `overlayView.render(...)`, call the color filter application
- NOTE: `MainActivity.kt` is not in the forbidden list, so this should be allowed

### 6. Tests
- `PreviewColorTransformTest.kt`: matrix correctness for various FilterRenderSpec values, identity matrix for null/defaults
- Update `SessionPreviewRenderModelTest.kt`: fidelity state assertions

## Changes

- git status: (no changes made)
- git diff --stat: (no changes made)
- Changed files: none

## Verification

- Commands planned but not run:
  ```
  rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformTest
  rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionPreviewRenderModelTest
  rtk ./gradlew --no-daemon :app:assembleDebug
  ```

## Delivery

- Commit hash: (none)
- PR link: (none)

## Self-Certification

- [x] Only read allowed paths during investigation
- [ ] Only touched allowed paths (no changes made yet)
- [ ] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files

## Unresolved Risks

1. **core/effect module ownership**: Adding `previewColorMatrix` to `PreviewEffectRenderModel` requires editing `core/effect/PreviewEffectModel.kt`, which is in the core/effect module. The package doc says `core/effect/**` is forbidden unless test-only. Need to decide: (a) add the matrix field in core/effect (coordinate with package 02), or (b) keep the matrix only in the app module and pass it separately.
2. **CameraX PreviewView compatibility**: Need to verify `View.setColorFilter()` works correctly with CameraX's `PreviewView` (it inherits from `FrameLayout`, so should work, but GPU-accelerated rendering may behave differently).
3. **Performance**: Applying a `ColorMatrixColorFilter` to the preview surface on every render frame — need to check if this causes jank. The overlay already invalidates per-frame, so the additional filter should be lightweight.
4. **Cockpit control isolation**: The package requires cockpit controls and panel UI not to be colored by preview-only effects. The color filter must be applied only to the `PreviewView`, not to sibling views.
