# 02 - Session Test Fixture And Recovery Split

## Goal

Start the `DefaultCameraSessionTest` decomposition safely by establishing a baseline, extracting shared test fixtures, and moving recovery / preview-host / permission recovery tests into a focused test file.

## Package ID

`02-session-test-fixture-recovery-split`

## Allowed Paths

- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTestFixtures.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionRecoveryTest.kt`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/02-session-test-fixture-recovery-split/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/02-session-test-fixture-recovery-split.md`

## Forbidden Paths

- `core/session/src/main/**`
- app production code
- device/media/effect production modules
- unrelated test files

## Implementation Scope

- Record current focused baseline for `DefaultCameraSessionTest`.
- Move reusable helper functions and fake/test fixtures from `DefaultCameraSessionTest.kt` into `DefaultCameraSessionTestFixtures.kt` with package-private visibility.
- Move tests around boot, preview errors, recoverable/fatal runtime issues, preview host detach/reattach, foreground attach, and permission recovery into `DefaultCameraSessionRecoveryTest.kt`.
- Keep assertions and test names intact unless a name needs a clearer class-local context.

## Steps

1. Run and record baseline:
   ```bash
   rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
   ```
   If this fails, record exact failing tests and continue only if moved tests can preserve the same failure/pass set.
2. Extract only helpers needed by moved tests and later packages.
3. Move recovery/host tests roughly from the beginning of the file through first-launch permission recovery.
4. Run focused tests for old and new classes.
5. Commit the package branch.

## Acceptance Criteria

- New fixture file contains shared helpers; no production code changes.
- Recovery tests compile and run as `DefaultCameraSessionRecoveryTest`.
- `DefaultCameraSessionTest.kt` is smaller and still contains the remaining unsplit tests.
- No assertions are weakened.
- Baseline failures, if any, are documented instead of hidden.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionRecoveryTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Expected Evidence Pack

- Baseline result before moving tests.
- New file list and moved test count.
- Focused verification output summary.
- Commit hash.

## Risks And Notes

- If the old monolithic class has pre-existing failures, this package may still be accepted only when the moved recovery tests pass and remaining baseline is unchanged.
