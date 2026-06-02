# Package 06 - Watermark Vivo Reference Polish

## Goal

Repair blur-four-border regression and use the vivo reference screenshots to improve watermark/frame affordance clarity without copying vendor branding.

The concrete user-facing requirements:

- `blur-four-border` must look like blurred content-derived borders, not a fixed non-blur frame.
- Watermark/frame selections should give a clear live preview or preview hint, similar in spirit to vivo's selected paper/watermark card and visible camera-surface affordance.
- Use localized user-facing labels; do not show raw English template ids.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` watermark/style preview sections only
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt` watermark sections only
- `core/settings/src/main/kotlin/com/opencamera/core/settings/**` watermark template/display-name sections only
- `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/SessionPreviewRenderModelTest.kt`
- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/06-watermark-vivo-reference-polish.md`
- package-local scratch path

## Forbidden Paths

- No vivo branding, copied proprietary layouts, or network-loaded assets.
- No fixed pale/solid frame pretending to be blur.
- No raw source image bytes in logs/status.
- No unrelated settings/i18n rewrite beyond watermark names needed for this package.

## Tasks

1. Inspect `blur-four-border` renderer/resolver history, especially prior plans that required content-aware edge extension.
2. Add or repair pixel tests proving borders derive from nearby image content and are blurred/extended rather than fixed.
3. Improve live preview hint for selected watermark/frame templates if current preview is too abstract.
4. Ensure watermark labels in Quick/Settings/preview hints are localized and user-facing.
5. Summarize vivo reference implications: selected state, visible preview, compact controls, and clear close/dismiss.

## Acceptance Criteria

- `blur-four-border` saved-render tests fail if the renderer uses a solid/fixed border.
- Selected watermark/frame preview affordance is visible enough for a user to anticipate the next shot's output.
- User-facing labels are localized; raw ids are not shown.
- No vendor copy/branding is introduced.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Evidence Required

- Pixel-test explanation for blur border.
- Preview hint/render-model evidence.
- vivo-reference notes translated into implementation choices.
- Tests/build and commit hash.

## Unlock Condition

Mark `completed` only after tests/build pass and final visual QA residuals are explicit.
