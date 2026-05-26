# Package 02 - Blur Border Settings Guard

## Package ID

`02-blur-border-settings-guard`

## Goal

Make `blur-four-border` invalid background protection complete by guarding direct persisted settings actions, not only UI cycling and render-time resolver fallback.

## Context

- User request: approve Watermark 2.0 upgrades, then check whether external implementation landing is appropriate.
- Validated good state:
  - UI only cycles blur-family backgrounds for `blur-four-border`.
  - `PhotoWatermarkTemplateResolverTest` verifies unsupported solid backgrounds clamp to `SOURCE_LIGHT_BLUR` at render resolution.
- Remaining blocker:
  - Direct `PersistedSettingsAction.UpdateWatermarkFrameBackground(templateId = "blur-four-border", background = WHITE/DARK)` can still store an invalid solid background.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`

## File Ownership

- Allowed paths:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`
  - narrowly related settings tests if needed
- Forbidden paths:
  - `core/effect/**`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` unless a test proves the resolver contract itself is wrong
  - `docs/plans/**` except your assigned status file
  - `codex/documentation.md`

## Implementation Scope

- Add or reuse a single settings-side rule for template-specific frame background support.
- Ensure direct persisted actions cannot store invalid solid backgrounds for `blur-four-border`.
- Preserve existing behavior for templates that legitimately support `WHITE` or `DARK`.
- Prefer normalizing invalid `blur-four-border` backgrounds to the default blur background used by current resolver behavior, expected to be `SOURCE_LIGHT_BLUR`.
- Add a settings-level regression test that dispatches `UpdateWatermarkFrameBackground` directly and proves `WHITE`/`DARK` do not persist for `blur-four-border`.
- Keep app resolver tests passing; they remain a second line of defense.

## Acceptance Criteria

- Direct persisted action with `templateId = "blur-four-border"` and `WHITE` stores a supported blur background instead.
- Direct persisted action with `templateId = "blur-four-border"` and `DARK` stores a supported blur background instead.
- Direct persisted action for templates that support solid backgrounds remains unchanged.
- Settings serializer/action tests pass.
- Watermark template resolver tests pass.
- The official Watermark V2 gate no longer reports this blocker after package 01 also lands.

## Verification Commands

External agents working in a worktree MUST use isolated Gradle:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.SessionUiRenderModelTest
```

If your worktree needs to run the official script, use an explicit build root:

```bash
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-watermark2-settings ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Expected Evidence Pack

- Diff summary of the settings guard and regression test.
- Before/after behavior for direct `UpdateWatermarkFrameBackground`.
- Test result summary for settings and app resolver/render-model commands.
- Confirmation that only allowed paths were touched.

## Risks And Notes

- This package should not tune the visual look of the blurred frame.
- Do not rely only on renderer clamping; the goal is to keep persisted settings internally valid.

