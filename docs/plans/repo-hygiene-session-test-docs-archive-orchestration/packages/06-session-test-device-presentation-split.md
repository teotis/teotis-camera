# 06 - Session Test Device, Presentation, Document, Zoom, And Readiness Split

## Goal

Complete the first practical decomposition pass for `DefaultCameraSessionTest` by moving the remaining large domains into focused test files, leaving the original class either empty enough to delete or reduced to a tiny smoke/legacy holder.

## Package ID

`06-session-test-device-presentation-split`

## Dependencies

- Depends on `04-session-test-capture-mode-split`.

## Allowed Paths

- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTestFixtures.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionDeviceControlsTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionPresentationTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionDocumentBatchTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionZoomLensNodeTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionCaptureReadinessTest.kt`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/06-session-test-device-presentation-split/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/06-session-test-device-presentation-split.md`

## Forbidden Paths

- `core/session/src/main/**`
- app production code
- unrelated tests

## Implementation Scope

- Move device graph controls, still quality/resolution, unsupported mode, permission-loss, trace, capture feedback, metering, rotation, capability report, low-light prompt, document batch, lens-node, preview zoom ratio, and capture-readiness tests.
- Keep helper code centralized in `DefaultCameraSessionTestFixtures.kt`.
- Delete `DefaultCameraSessionTest.kt` only if it is truly empty after moves and no test runner references the class by name in scripts. Otherwise leave a small comment-free smoke holder with remaining tests.

## Steps

1. Verify package `04` branch/commit is present or work from integration containing it.
2. Move tests by domain into the listed focused classes.
3. Run focused tests for each new class.
4. Run full `:core:session:test` through isolated wrapper.
5. Record final line counts for all session test files.

## Acceptance Criteria

- `DefaultCameraSessionTest.kt` is no longer a 5000+ line multi-domain file.
- New domain test files compile and run.
- Full `:core:session:test` passes or any pre-existing failure is recorded with evidence from before the move.
- No production code changes.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionDeviceControlsTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionPresentationTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionDocumentBatchTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionZoomLensNodeTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionCaptureReadinessTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test
rtk wc -l core/session/src/test/kotlin/com/opencamera/core/session/*DefaultCameraSession*Test*.kt core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTestFixtures.kt
```

## Expected Evidence Pack

- Final line-count table.
- Full session test result.
- Changed files list.
- Commit hash.

## Risks And Notes

- If scripts or docs reference `DefaultCameraSessionTest` specifically, preserve a compatibility class or update the reference with a clear explanation.
