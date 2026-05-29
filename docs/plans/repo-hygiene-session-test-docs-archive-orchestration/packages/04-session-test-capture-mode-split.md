# 04 - Session Test Capture And Mode Split

## Goal

Continue the `DefaultCameraSessionTest` decomposition by moving capture lifecycle, recording, mode plugin behavior, degradation, and re-arm tests into focused files after the shared fixture package lands.

## Package ID

`04-session-test-capture-mode-split`

## Dependencies

- Depends on `02-session-test-fixture-recovery-split`.

## Allowed Paths

- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTestFixtures.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionCaptureLifecycleTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionModeBehaviorTest.kt`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/04-session-test-capture-mode-split/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/04-session-test-capture-mode-split.md`

## Forbidden Paths

- `core/session/src/main/**`
- app production code
- unrelated test files

## Implementation Scope

- Move ordinary photo shutter, countdown, video recording, shot failure, live photo completion, frame ratio, mode switch blocking, re-arm, and mode-specific capture strategy tests.
- Split them into `DefaultCameraSessionCaptureLifecycleTest` and `DefaultCameraSessionModeBehaviorTest`.
- Keep test behavior equivalent.

## Steps

1. Verify package `02` branch/commit is present or work from the integration branch that contains it.
2. Run current focused baseline for the session test classes.
3. Move tests by domain, preserving names and assertions.
4. Keep helper additions in `DefaultCameraSessionTestFixtures.kt`.
5. Run focused tests for new classes and remaining monolith.

## Acceptance Criteria

- Capture lifecycle and mode behavior tests live outside the monolithic class.
- Remaining `DefaultCameraSessionTest.kt` no longer contains the moved domains.
- New tests pass, or baseline-equivalent failures are documented with exact test names.
- No production code changes.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionCaptureLifecycleTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionModeBehaviorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Expected Evidence Pack

- Moved test count by new class.
- Baseline comparison.
- Verification result summary.
- Commit hash.

## Risks And Notes

- Do not refactor session runtime logic to make the split pass. This is a test organization package.
