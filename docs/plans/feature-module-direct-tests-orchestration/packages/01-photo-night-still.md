# Package 01 - Photo And Night Still Mode Direct Tests

## Package ID

`01-photo-night-still`

## Goal

Add direct unit tests for `PhotoModePlugin` and `NightModePlugin`, focusing on still-capture mode behavior that currently is only indirectly covered.

## Assigned Branch And Worktree

- Branch: `agent/feature-module-direct-tests/01-photo-night-still`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/feature-module-direct-tests-01-photo-night-still`
- Base: current `main` unless the branch already exists.

## Allowed Paths

- `feature/mode-photo/build.gradle.kts`
- `feature/mode-photo/src/test/**`
- `feature/mode-night/build.gradle.kts`
- `feature/mode-night/src/test/**`
- This package's coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/01-photo-night-still.md`
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
2. Add direct JVM tests for `PhotoModePlugin`.
3. Add direct JVM tests for `NightModePlugin`.
4. Use a small fake `ModeContext` test harness rather than app/session integration.
5. Assert contract-level behavior:
   - `isSupported(...)` follows relevant device capability.
   - `onEnter()` emits the expected enter event and calls `onEffectSpecChanged(...)`.
   - Shutter handling returns the expected `CaptureStrategy` family.
   - Metadata includes mode tags such as `mode=photo` or `mode=night`, still resolution, and tags from `EffectBridge`.
   - `PostProcessSpec` carries expected algorithm/watermark decisions for at least one representative path.
   - A representative `ModeSessionEvent` updates the snapshot headline/detail.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-photo:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-night:test
```

Optional cross-check from the main checkout after package commit:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :feature:mode-photo:test :feature:mode-night:test
```

## Acceptance Criteria

- Direct tests exist under both assigned modules.
- Tests fail meaningfully if mode metadata/effect/post-process contracts drift.
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
