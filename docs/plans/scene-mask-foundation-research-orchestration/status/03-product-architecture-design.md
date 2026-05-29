# Package Status: 03-product-architecture-design

- **Agent**: Claude Code (background)
- **Status**: completed
- **Started**: 2026-05-27
- **Completed**: 2026-05-27

## Worktree

- Path: `.claude/worktrees/scene-mask-03-architecture`
- Branch: `worktree-scene-mask-03-architecture`

## Changes

- git status: no runtime code changes
- git diff --stat: (none)
- Changed files: this status file only

## Verification

- Commands run:
  - `grep -rn "SceneMask|PreviewSceneMask|Portrait|ColorLab|RenderRecipe|filterSpec|pipeline notes|supported|degraded|unsupported" app core docs/plans codex` — 180+ references confirmed across AppContainer, SessionUiRenderModel, PhotoAlgorithmPostProcessor, PortraitRenderPostProcessor, PreviewEffectModel, SceneMaskContracts, and mode plugins
  - `sed -n '1,220p' codex/Product-2.0-Standard.md` — Product 2.0 Standard read and analyzed; key principles: honest capability governance, preview/output consistency, four-layer architecture
- Test results: not applicable (research/design package)

## Product Architecture Proposal

### 1. Capability Taxonomy

#### 1.1 SceneMaskRole — Current State

| Role | Implemented | Backend | Used By |
|---|---|---|---|
| `PERSON_SUBJECT` | YES | ML Kit Selfie (STREAM + SINGLE_IMAGE) | Portrait bokeh, Color Lab subject protection, photo algorithm subject-aware filtering |
| `FOREGROUND` | NO | — | Future: general foreground detection |
| `BACKGROUND` | NO | — | Future: background-only effects |
| `DEPTH_APPROXIMATION` | NO | — | Explicitly forbidden: 2D mask is NOT depth |
| `SEMANTIC_REGION` | NO | — | Future: sky/animal/object segmentation |

**Decision**: Phase-1 uses only `PERSON_SUBJECT`. Other roles remain contract-only. No role should be declared `SUPPORTED` without a working backend and passing tests.

#### 1.2 Quality / Freshness

| Quality Level | Source | Behavior |
|---|---|---|
| `SAVED_PHOTO` | ML Kit SINGLE_IMAGE_MODE on saved bitmap | Highest confidence, full resolution, used for post-process rendering |
| `PREVIEW_APPROXIMATE` | ML Kit STREAM_MODE on preview frames | Lower confidence, 256x256, stale after 500ms, used only for preview color transform hint |
| `DEGRADED` | Stale preview mask or fallback path | Mask age > 500ms threshold; quality label downgraded, `isAvailable` set to false |
| `UNAVAILABLE` | NoOp or ML Kit init failure | No mask data; all mask-aware features fall back to global rendering |

**Freshness contract**: `PreviewSceneMaskSnapshot.toSnapshot()` applies a 500ms staleness threshold. Stale masks produce `DEGRADED` quality and `isAvailable = false`, which causes `PreviewEffectAdapter` to select `FALLBACK` color transform instead of `MASK_AWARE`.

#### 1.3 Backend Labels

| Backend | Phase | Preview | Saved Photo | Subject Scope | Network | Beta |
|---|---|---|---|---|---|---|
| ML Kit Selfie Segmentation | Phase-1 (default) | `SUPPORTED` (STREAM_MODE) | `SUPPORTED` (SINGLE_IMAGE_MODE) | Person only | No | Yes |
| ML Kit Subject Segmentation | Phase-2 candidate | `UNSUPPORTED` (no stream) | `DEGRADED` (requires Play Services download) | Multi-subject | Yes | Yes |
| MediaPipe Image Segmenter | Phase-2+ candidate | `SUPPORTED` (if integrated) | `SUPPORTED` (if integrated) | Multi-class semantic | No | No |
| Camera2 Depth | Future | `UNSUPPORTED` (coverage) | `UNSUPPORTED` (coverage) | True depth | No | No |

**Honesty rule**: Beta backends must always have a NoOp fallback path. The `SUPPORTED` label on ML Kit Selfie requires: (1) model init succeeds, (2) stream mode produces frames at >= 3fps sustained, (3) subject ratio >= 2%.

#### 1.4 Preview vs Saved Output

| Aspect | Preview | Saved Output |
|---|---|---|
| Mask source | `PreviewSceneMaskSource.latestMask()` | `SavedPhotoSceneMaskProvider.createSubjectMask()` |
| Quality | `PREVIEW_APPROXIMATE` (256x256, 8fps cap) | `SAVED_PHOTO` (raw-size, SINGLE_IMAGE_MODE) |
| Rendering | Global color transform hint only (`MASK_AWARE` vs `FALLBACK`) | Per-pixel subject-aware rendering with `smoothstep` boundary |
| Visual feedback | Tint overlay color shift (not mask visualization) | Actual blur, glow, vignette, warmth scaled by subject weight |
| Staleness | 500ms threshold; stale → degraded | No staleness (computed on-demand from saved bitmap) |

**Critical rule**: Preview mask must NEVER be used for saved output rendering. Saved output always re-segments from the full-resolution saved bitmap via `SavedPhotoSceneMaskProvider`.

#### 1.5 Product Feature Support Labels

| Product Feature | Mask Dependency | Phase-1 Label | Condition for `SUPPORTED` |
|---|---|---|---|
| **Portrait: subject protection** | Saved photo mask | `SUPPORTED` | Mask available + `MaskAwarePortraitRenderEditor` |
| **Portrait: background blur/style** | Saved photo mask | `DEGRADED` | 2D mask edge quality requires visual QA; not true depth bokeh |
| **Portrait: depth/blur strength slider** | Saved photo mask | `DEGRADED` | Slider controls `depthMultiplier` (0.5x-1.5x) on blur parameters; works but effect is 2D approximation |
| **Portrait: light spot / highlight shape** | Saved photo mask | `DEGRADED` | `applyLightSpotEffect()` renders bokeh spots in background region; quality depends on mask edge accuracy |
| **Portrait: beauty (smoothing/lift/saturation)** | Saved photo mask | `SUPPORTED` | Subject-weighted beauty applied to subject pixels only |
| **Humanistic: subject/background tonal separation** | Saved photo mask (via `PhotoAlgorithmPostProcessor`) | `DEGRADED` | Mask-aware path exists but Humanistic mode does NOT produce `PortraitEffect`; relies on `PhotoAlgorithmPostProcessor` which has writeback bug |
| **Humanistic: profile mapping (35mm/50mm/85mm)** | None | `SUPPORTED` | Style profiles are filter-only, no mask dependency |
| **Color Lab: subject protection** | Saved photo mask | `DEGRADED` | Mask-aware color rendering works in `AndroidPhotoAlgorithmEditor.applyStyleWithMask()` but bitmap changes are NOT written back to JPEG (confirmed bug) |
| **Color Lab: background color/tone changes** | Saved photo mask | `DEGRADED` | Same writeback bug applies |
| **Color Lab: preview approximation** | Preview mask | `DEGRADED` | `PreviewEffectAdapter` sets `MASK_AWARE` color transform when mask available, but this only changes global tint, not selective subject/background |
| **Depth slider (true depth)** | Hardware depth | `UNSUPPORTED` | 2D mask is not depth; `SceneMaskRole.DEPTH_APPROXIMATION` must not be used for ML Kit output |
| **General object/semantic segmentation** | MediaPipe or custom model | `UNSUPPORTED` | ML Kit Selfie is person-only |

### 2. Product Copy and Diagnostics Rules

#### 2.1 What UI May Show

| Context | Allowed Copy | Example |
|---|---|---|
| Portrait mode badge | "人像" (no depth claim) | Mode track label |
| Portrait Lab availability | `DEGRADED` label when mask-based | "效果近似" / availability badge |
| Depth strength slider | "虚化强度" (blur strength, NOT depth) | SeekBar label |
| Bokeh effect selector | "自然" / "奶油" / "梦幻" (style names) | Portrait Lab button |
| Color Lab subject protection | "主体保护：已启用" (when mask available) | Status indicator (future) |
| Mask status in dev log | Full pipeline notes | Dev panel: `scene-mask:saved=applied` |

#### 2.2 What Pipeline Notes Must Say

| Scenario | Pipeline Note | Meaning |
|---|---|---|
| Mask applied to saved portrait | `portrait-mask:saved=applied` | Mask-aware rendering used |
| Mask unavailable for portrait | `portrait-mask:saved=degraded:mask-unavailable` | Fallback to geometric focus blur |
| Mask applied to saved photo algorithm | `scene-mask:saved=applied` | Subject-aware color filtering used |
| Photo algorithm editor not mask-aware | `scene-mask:saved=degraded:editor-not-mask-aware` | Global rendering used despite mask availability |
| Preview mask stale | `scene-mask:stale:age=Xms:threshold=Yms` | Preview mask exceeded freshness threshold |
| ML Kit backend | `scene-mask:backend=mlkit-selfie` | Backend identification |
| Render path used | `portrait-render:applied:depth` or `portrait-render:fallback-focus` | Whether mask-based or geometric path |

#### 2.3 What Must Remain Hidden or Degraded

| Claim | Status | Rule |
|---|---|---|
| "深度虚化" (depth bokeh) | **HIDDEN** | 2D mask is not depth; use "背景虚化" (background blur) instead |
| "ZEISS/品牌联名 式虚化" | **HIDDEN** | Cannot claim vendor pipeline equivalence |
| "实时景深预览" | **HIDDEN** | Preview shows no bokeh effect; only saved output has blur |
| "多主体识别" | **HIDDEN** | ML Kit Selfie is person-only |
| "语义分割" | **HIDDEN** | No semantic class support in Phase-1 |
| "主体保护已应用" (when writeback broken) | **DEGRADED** | Pipeline notes must say `degraded` not `applied` when bitmap not persisted |

### 3. Architecture Placement

#### 3.1 Contracts (core/media)

`SceneMaskContracts.kt` — Pure domain types, no Android dependencies. This layer is **complete and tested**:
- `SceneMaskRole`, `SceneMaskQuality`, `SceneMaskSupport`
- `SceneMaskTransform`, `SceneMaskDescriptor`, `SceneMaskPayload`
- `SceneMaskCapability`, `SceneMaskPipelineNotes`

**No changes needed** for product architecture. Contracts are the stable foundation.

#### 3.2 Preview Source (app/camera)

`PreviewSceneMaskSource` interface + `MlKitSelfiePreviewSceneMaskSource` — Produces preview mask snapshots.

**Architecture rule**: Preview mask is a **hint only**. It influences:
- `PreviewColorTransform` selection (`MASK_AWARE` vs `FALLBACK`)
- `SubjectMaskPreviewDescriptor.isAvailable` for diagnostics
- Dev log panel status

Preview mask must NEVER:
- Be used for saved output rendering
- Be displayed as a visual overlay (no mask visualization in preview)
- Be treated as authoritative for subject boundaries

#### 3.3 Saved Photo Provider (app/camera)

`SavedPhotoSceneMaskProvider` interface + `MlKitSavedPhotoSceneMaskProvider` — Produces saved photo masks.

**Architecture rule**: Saved photo mask is the **authoritative mask** for all post-process rendering. It flows through:
1. `PhotoAlgorithmPostProcessor.resolveMask()` → `MaskAwarePhotoAlgorithmEditor.applyWithMask()`
2. `PortraitRenderPostProcessor.resolveMask()` → `MaskAwarePortraitRenderEditor.applyWithMask()`

Both post-processors share the same `savedMaskProvider` instance (injected via `AppContainer`).

#### 3.4 Render Recipe / Effect Model Integration

**Current flow**:
```
ModePlugin.buildEffectSpec()
  → EffectSpec (FilterEffect + PortraitEffect + ...)
  → EffectBridge.toMetadataTags() → shot custom tags
  → PostProcessor.decideWork() reads tags
  → PostProcessor.resolveMask() gets mask from provider
  → Editor.applyWithMask() or Editor.apply() (fallback)
```

**Architecture rule**: `EffectSpec` is the single source of truth for what effects to apply. `RenderRecipe` consolidates parameters. The mask is resolved **after** the effect spec is determined — it does not influence which effects are selected, only how they are rendered (mask-aware vs global).

**Gap identified**: `HumanisticModePlugin` does not produce a `PortraitEffect`, so humanistic capture never triggers `PortraitRenderPostProcessor`. Humanistic mask-aware rendering relies solely on `PhotoAlgorithmPostProcessor`, which has the writeback bug.

#### 3.5 Diagnostics

Diagnostics flow through three layers:
1. **Pipeline notes** (`SceneMaskPipelineNotes`) — structured strings in shot metadata
2. **Dev log panel** (`DevLogRenderModel`) — real-time event trace
3. **Portrait Lab availability** (`PortraitLabPageRenderModel.availability`) — user-visible degraded badge

**Architecture rule**: Every mask-related code path must write pipeline notes. The note format is: `scene-mask:<scope>=<state>[:<detail>]`. Scopes: `preview`, `saved`. States: `applied`, `degraded`, `unsupported`, `failed`.

### 4. Migration Path from Current Implementation

#### 4.1 Repair-First Items (Must Fix Before Claiming SUPPORTED)

| # | Issue | Location | Fix |
|---|---|---|---|
| R1 | **Photo algorithm mask writeback broken** | `AndroidPhotoAlgorithmEditor.applyWithMask()` | Add `writeEncodedBytes()` call after `applyStyleWithMask()`, mirroring `AndroidPortraitRenderEditor` pattern. Without this fix, `Color Lab subject protection` and `Humanistic subject-aware rendering` are falsely labeled. |
| R2 | **ImageProxy resource leak** | `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame()` | Add `image.close()` in all code paths (early returns + success + failure listeners). CameraX expects analyzer to close the proxy. |
| R3 | **Merge conflict blocks compilation** | `SessionPreviewRenderModel.kt:59-63` | Resolve HEAD vs b24d2bd conflict. This blocks all app-level SceneMask tests. |
| R4 | **Preview mask target resolution ignored** | `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame()` | Downscale bitmap to `config.targetWidth` x `config.targetHeight` before feeding to ML Kit segmenter. Currently processes at original resolution, wasting cycles. |

#### 4.2 Optional Future Items (Phase-2 Enhancements)

| # | Item | Trigger |
|---|---|---|
| F1 | **Mask visualization in preview** | User feedback wanting to see mask boundary before capture |
| F2 | **Portrait bokeh preview** | Performance budget allows real-time blur in preview |
| F3 | **Subject protection indicator in Color Lab** | User feedback wanting to know when mask-aware rendering is active |
| F4 | **Independent background style selector** | Product wants background treatment separate from bokeh effect |
| F5 | **Humanistic mode mask integration** | Product wants subject-aware color rendering in humanistic mode |
| F6 | **Mask quality/freshness UI indicator** | User feedback about stale or degraded mask quality |
| F7 | **Honesty badge on portrait mode** | Product wants prominent "approximate" indicator in mode track |
| F8 | **ML Kit Subject Segmentation backend** | Product needs multi-subject or non-person foreground masks |
| F9 | **MediaPipe Image Segmenter backend** | Product needs sky/animal/object semantic classes |
| F10 | **Max FPS throttle for preview mask** | Performance tuning needed on low-end devices |

#### 4.3 Explicitly Deferred Items

| # | Item | Reason |
|---|---|---|
| D1 | **True depth slider** | 2D mask is not depth; requires hardware depth sensor or ML-based depth estimation (MiDaS, etc.) |
| D2 | **ZEISS/品牌联名-style portrait** | Requires vendor ISP access; OpenCamera uses app-side processing only |
| D3 | **Real-time bokeh preview** | Performance too expensive for preview frames on most devices |
| D4 | **Multi-class semantic segmentation** | ML Kit Selfie is person-only; MediaPipe integration is Phase-2+ |
| D5 | **Video mask tracking** | ML Kit Selfie stream mode is for preview only; video mask requires temporal consistency |

### 5. Is "Scene Mask Segmentation" the Right Foundation?

**YES, with honesty limits.**

The Scene Mask infrastructure is the correct foundation for OpenCamera's subject-aware features because:

1. **Contracts are solid**: `SceneMaskContracts.kt` types are well-designed, tested, and extensible. The `SceneMaskRole` enum supports future expansion beyond `PERSON_SUBJECT`.
2. **Preview/saved separation is correct**: The architecture correctly separates preview-time hints from saved-photo authoritative masks.
3. **Fallback paths exist**: Every mask-aware code path has a `NoOp` fallback. The system degrades gracefully.
4. **Pipeline notes provide honesty**: The structured diagnostic notes prevent false claims about mask application.

**Honesty limits**:
- Phase-1 is **person-only** (ML Kit Selfie). No general subject or semantic segmentation.
- Phase-1 preview is **hint-only** (no bokeh preview, no mask visualization).
- Phase-1 saved output has **two confirmed bugs** (photo algorithm writeback, ImageProxy leak) that must be fixed before shipping.
- The "depth" slider is actually a "blur strength" slider — it does not control true depth.

### 6. Smallest Next Implementation Loop

If the user approves coding, the smallest repair loop is:

1. **Fix R1**: Photo algorithm mask writeback in `AndroidPhotoAlgorithmEditor.applyWithMask()` — add `writeEncodedBytes()` call.
2. **Fix R2**: ImageProxy leak in `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame()` — add `image.close()`.
3. **Fix R3**: Resolve merge conflict in `SessionPreviewRenderModel.kt:59-63`.
4. **Verify**: Run `SceneMaskContractsTest`, `PreviewSceneMaskSourceTest`, `SceneMaskPayloadTest`, `MaskAwarePortraitRenderMathTest`, `PhotoAlgorithmPostProcessorTest`.

This loop fixes the two confirmed bugs and unblocks compilation, making the mask infrastructure trustworthy for the features that depend on it.

## Self-Certification

- [x] Only touched allowed paths (this status file only)
- [x] Did not edit forbidden paths (no runtime code, tests, INDEX.md, or other status files)
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks

1. **Photo algorithm writeback bug**: Until R1 is fixed, Color Lab subject protection and Humanistic mask-aware rendering produce pipeline notes saying "applied" but the bitmap changes are discarded. This is a P0 honesty violation.
2. **ImageProxy leak**: Until R2 is fixed, preview mask source will cause CameraX frame starvation over extended use.
3. **Merge conflict**: Until R3 is fixed, app module cannot compile, blocking all app-level SceneMask tests.
4. **ML Kit Selfie beta lifecycle**: Google may change or deprecate the API. The NoOp fallback mitigates this but should be monitored.
5. **Edge quality unverified**: Math tests prove smoothstep and coordinate mapping work, but no test verifies actual pixel-level edge quality on rendered bitmaps. Visual QA on real device is required before claiming "portrait bokeh" as shipped quality.
6. **Humanistic mode gap**: Humanistic mode has zero mask integration. If the user expects subject-aware rendering in humanistic mode, it requires either: (a) adding `PortraitEffect` to humanistic's `EffectSpec`, or (b) fixing the `PhotoAlgorithmPostProcessor` writeback bug and relying on that path.
