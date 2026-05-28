# Package 01 - Performance Timing Contract

## Package ID

`01-performance-timing-contract`

## Goal

Create the small reusable contract that lets core flows record performance/link timing consistently, then expose those records through existing session diagnostics/trace paths without creating a second runtime owner.

## Branch And Worktree

- Branch: `agent/performance-link-logs/01-performance-timing-contract`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/01-performance-timing-contract`

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/**`
- `core/session/src/test/kotlin/com/opencamera/core/session/**`
- `core/media/src/main/kotlin/com/opencamera/core/media/**`
- `core/media/src/test/kotlin/com/opencamera/core/media/**`

## Forbidden Paths

- `app/src/main/**`
- `feature*/**`
- `docs/plans/**` except the assigned status file
- build outputs, generated files, secrets, or unrelated formatting

## Required Design Constraints

- Use monotonic elapsed time for durations. Wall-clock time may be included for exported chronology, but it must not drive duration math.
- Include enough fields for later analysis: `flow`, `stage`, `status`, `correlationId`, start/end elapsed millis, duration millis, optional detail, and source.
- Keep the session kernel as the runtime owner. UI may render/export records later, but must not own timing state.
- Preserve existing `SessionTrace` behavior for current tests; do not remove existing event names such as `capture.timing` or `preview.first.frame`.
- Keep records compact and string-renderable so they can be included in Dev logs without raw frame/media data.

## Suggested Implementation Shape

- Add a small core model such as `PerformanceLinkEvent` / `PerformanceSpanSnapshot` / `PerformanceLinkRecorder` in `core/session` or a nearby existing diagnostics file.
- Add helper formatting that emits deterministic link-log lines, for example:
  `link flow=capture stage=device status=completed id=shot-1 startElapsed=100 endElapsed=245 duration=145ms detail=...`
- Extend `SessionDebugDump` or diagnostics helpers with recent performance/link records if that is the smallest fit.
- If `ShotTiming` needs a reusable conversion helper, add it in `core/media` without moving media ownership into session.

## Acceptance Criteria

- Unit tests prove duration math uses elapsed timestamps and handles missing endpoints as degraded/unavailable rather than crashing.
- Unit tests prove link event formatting includes flow, stage, status, correlation id, time points, and duration.
- Existing diagnostics tests continue to pass.
- No app/UI files are changed in this package.

## Verification

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:media:test
```

If `:core:media:test` is too broad due to unrelated failures, record the smallest relevant focused media tests and the blocker.

## Evidence To Record

- Changed files.
- Contract model summary.
- Example rendered link line.
- Verification commands and results.
- Any known migration risk for downstream app packages.

## Tail Step

After status and ledger are updated:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 01-performance-timing-contract
```
