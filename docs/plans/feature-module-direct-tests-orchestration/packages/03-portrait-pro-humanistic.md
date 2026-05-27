# Package 03 - Portrait Pro Humanistic Variant Direct Tests

## Package ID

`03-portrait-pro-humanistic`

## Goal

Add direct unit tests for `PortraitModePlugin`, `ProModePlugin`, and `HumanisticModePlugin`, focusing on variant-heavy mode behavior that will protect later large-file and effect-pipeline refactors.

## Assigned Branch And Worktree

- Branch: `agent/feature-module-direct-tests/03-portrait-pro-humanistic`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/feature-module-direct-tests-03-portrait-pro-humanistic`
- Base: current `main` unless the branch already exists.

## Allowed Paths

- `feature/mode-portrait/build.gradle.kts`
- `feature/mode-portrait/src/test/**`
- `feature/mode-pro/build.gradle.kts`
- `feature/mode-pro/src/test/**`
- `feature/mode-humanistic/build.gradle.kts`
- `feature/mode-humanistic/src/test/**`
- This package's coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/03-portrait-pro-humanistic.md`
- Shared state row in `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/state.tsv`

## Forbidden Paths

- Production Kotlin files under `feature/*/src/main/**`
- `app/**`
- `core/**`
- Other package status files
- `INDEX.md`
- Any generated build output

If a production seam appears necessary, stop and mark the package `blocked` with the exact seam needed. Do not change production behavior in this package.

## Expected Work

1. Add `testImplementation(kotlin("test"))` to the assigned feature module build files if missing.
2. Add direct JVM tests for `PortraitModePlugin`.
3. Add direct JVM tests for `ProModePlugin`.
4. Add direct JVM tests for `HumanisticModePlugin`.
5. Use a small fake `ModeContext` test harness rather than app/session integration.
6. Assert contract-level behavior:
   - `isSupported(...)` follows still-capture and relevant variant capability.
   - `onEnter()` emits mode enter events and effect specs.
   - Shutter handling returns the expected still capture strategy family.
   - Metadata includes mode, profile/variant, still resolution, pro/manual draft tags where applicable, and tags from `EffectBridge`.
   - `PostProcessSpec` carries expected algorithm/watermark choices for representative paths.
   - A representative photo `ModeSessionEvent` updates snapshot headline/detail.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-portrait:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-pro:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-humanistic:test
```

Optional cross-check from the main checkout after package commit:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :feature:mode-portrait:test :feature:mode-pro:test :feature:mode-humanistic:test
```

## Acceptance Criteria

- Direct tests exist under all three assigned modules.
- Tests fail meaningfully if variant metadata/effect/post-process contracts drift.
- No production code changed.
- Focused verification passes or the failure is recorded with exact output and diagnosis.
- Package branch has one local commit and the commit hash is recorded in status.

## Evidence To Record

- Worktree path
- Branch
- Base commit
- Commit hash
- Changed files
- Verification commands and pass/fail result
- Any residual risk or blocked seam
