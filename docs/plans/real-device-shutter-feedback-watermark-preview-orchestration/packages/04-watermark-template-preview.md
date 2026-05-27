# Package 04 - Watermark Template Preview

## Package ID

`04-watermark-template-preview`

## Goal

When the user chooses or edits a watermark template, the live preview should show the expected template at the corresponding position. The preview should be close enough for user expectation setting while remaining honest about approximate saved-output fidelity.

## Branch And Worktree

- Branch: `agent/shutter-feedback-watermark/04-watermark-template-preview`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-04-watermark-template-preview`
- Base: latest `main` unless `99-finalize` or the user says otherwise.

## Allowed Paths

- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt` only if selector/detail interaction must expose highlighted template preview state
- `app/src/main/java/com/opencamera/app/MainActivity.kt` only for plumbing selected watermark detail/template into preview render state
- `app/src/test/java/com/opencamera/app/SessionPreviewRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` only if geometry needs tests
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/04-watermark-template-preview.md`
- Coordinator state file row for `04-watermark-template-preview`

## Forbidden Paths

- Saved-photo watermark pixel renderer, unless a tiny shared template mapping is required and covered by tests
- Shutter/session runtime files
- Other package status files
- `INDEX.md`
- UI-local state as the source of capture metadata truth

## Implementation Guidance

1. Reuse existing `WatermarkHintSpec` and `WatermarkPreviewShape` where possible.
2. Extend the preview model only as needed to represent template:
   - placement,
   - opacity,
   - text scale,
   - frame/background family,
   - text-only, backed text, expanded frame, four-border, and professional bottom bar shapes.
3. Ensure the selected template from watermark selector/detail feeds preview while the panel is open.
4. Keep saved capture truth in persisted settings/session/media pipeline. The overlay is only expectation preview.
5. Do not claim exact parity for blur/four-border pixels if the preview is approximate.
6. Add focused tests proving:
   - selected template id reaches preview hint,
   - placement is respected,
   - four-border/professional/expanded-frame shapes are differentiated,
   - unsupported still capture does not show misleading enabled behavior.

## Acceptance Criteria

- [ ] Selecting a watermark template changes the preview hint template.
- [ ] Detail controls for placement/opacity/scale/background are reflected in the preview model or documented as intentionally approximate.
- [ ] Preview placement uses the active preview/capture frame, not the entire screen when a frame rect exists.
- [ ] Saved-photo watermark behavior remains driven by settings/media pipeline.
- [ ] Focused app/core tests pass.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

If overlay geometry is changed, add:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest
```

## Evidence Required

- Worktree path and branch.
- Base commit and final commit hash.
- Changed files and `git diff --stat`.
- Preview model mapping table for every built-in template.
- Test output summary.
- Residual visual QA risks.
