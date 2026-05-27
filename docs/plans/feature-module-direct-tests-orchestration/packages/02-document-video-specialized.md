# Package 02 - Document And Video Specialized Mode Direct Tests

## Package ID

`02-document-video-specialized`

## Goal

Add direct unit tests for `DocumentModePlugin` and `VideoModePlugin`, covering specialized mode behavior that is easy to regress during later capture/effect pipeline cleanup.

## Assigned Branch And Worktree

- Branch: `agent/feature-module-direct-tests/02-document-video-specialized`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/feature-module-direct-tests-02-document-video-specialized`
- Base: current `main` unless the branch already exists.

## Allowed Paths

- `feature/mode-document/build.gradle.kts`
- `feature/mode-document/src/test/**`
- `feature/mode-video/build.gradle.kts`
- `feature/mode-video/src/test/**`
- This package's coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/02-document-video-specialized.md`
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
2. Add direct JVM tests for `DocumentModePlugin`.
3. Add direct JVM tests for `VideoModePlugin`.
4. Use a small fake `ModeContext` test harness rather than app/session integration.
5. Assert contract-level behavior:
   - `isSupported(...)` follows still/video device capability.
   - `DocumentModePlugin` emits document effect metadata, document save path/prefix, scan mode/profile tags, and document post-process/exif choices.
   - `VideoModePlugin` emits video metadata for requested/resolved video spec, dynamic FPS/audio profile, and mode/video tags.
   - Video `ShotStarted`, `ShotCompleted`, and `ShotFailed` events affect only video snapshots and ignore photo events.
   - Unsupported frame-ratio actions return hints instead of capture mutations.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-document:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-video:test
```

Optional cross-check from the main checkout after package commit:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :feature:mode-document:test :feature:mode-video:test
```

## Acceptance Criteria

- Direct tests exist under both assigned modules.
- Tests fail meaningfully if document/video metadata or capture strategy contracts drift.
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
