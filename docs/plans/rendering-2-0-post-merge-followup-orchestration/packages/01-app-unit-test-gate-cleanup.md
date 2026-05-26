# 01 - App Unit Test Gate Cleanup

## Package ID

`01-app-unit-test-gate-cleanup`

## Goal

Repair the app unit-test compile blockers that currently prevent the Rendering 2.0 preview/color-transform app test from running. This is a test/geometry gate cleanup, not a new rendering feature package.

## Current Evidence

The focused app command is blocked before reaching `PreviewColorTransformOverlayTest`:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
```

Observed blockers:
- `PreviewContentGeometryTest.kt` references `previewContentAspect`, `PreviewContentAspect`, and `previewRatioToContentAspect`, but the app test compile classpath does not currently resolve those symbols.
- `PreviewOverlayGeometryTest.kt` has parse errors around the later test blocks, consistent with an incomplete merge or malformed braces.

## Allowed Paths

- `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewColorTransformOverlayTest.kt` only if assertions need a narrow update after the gate compiles
- `app/src/main/java/com/opencamera/app/**Preview*Geometry*`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` only if the production helper already exists there and the compile fix must expose it safely

## Forbidden Paths

- `core/effect/**` unless Codex explicitly reassigns scope
- `app/src/main/java/com/opencamera/app/camera/**`
- `docs/plans/**` except your own status file
- Any Rendering 2.0 ledger/status files outside your assigned status file

## Implementation Notes

- Start by reading the failing test files and the production geometry helpers they are meant to exercise.
- Prefer restoring or aligning tests with existing helpers over inventing a second geometry model.
- If `PreviewContentAspect` or `previewRatioToContentAspect` was renamed or moved, update the tests to the current production API.
- If the tests represent obsolete behavior, remove only the obsolete expectation and keep coverage for the current invariant.
- Fix malformed braces or duplicated fragments in `PreviewOverlayGeometryTest.kt` with the smallest readable patch.
- Do not change runtime preview/rendering behavior unless a production helper is genuinely missing and the test documents a current product invariant.

## Required Verification

Run through `rtk`:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:compileDebugKotlin
```

If the second command exposes unrelated historical app tests, record the exact blocker and still report whether the first command now reaches and runs the target test.

## Acceptance Criteria

- [ ] `PreviewContentGeometryTest.kt` compiles.
- [ ] `PreviewOverlayGeometryTest.kt` has no parse/brace/merge-fragment errors.
- [ ] `PreviewColorTransformOverlayTest` can be executed by Gradle instead of being blocked by unrelated app test compilation.
- [ ] No runtime rendering behavior is changed unless required by an existing production helper contract.
- [ ] Evidence pack is written to `status/01-app-unit-test-gate-cleanup.md`.

## Expected Evidence Pack

Include:
- Worktree path and branch
- Changed files
- `git diff --stat`
- Verification command output summaries
- Any tests still blocked and why
- Self-certification that only allowed paths were touched
