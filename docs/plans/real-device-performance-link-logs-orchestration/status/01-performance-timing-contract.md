# Status - 01-performance-timing-contract

## State

`completed`

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/01-performance-timing-contract`
- Branch: `agent/performance-link-logs/01-performance-timing-contract`
- Base commit: `a340a89`
- Commit hash: `12ec859`

## Changed Files

- `core/session/src/main/kotlin/com/opencamera/core/session/PerformanceLinkEvent.kt` (new)
- `core/session/src/test/kotlin/com/opencamera/core/session/PerformanceLinkEventTest.kt` (new)
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt` (modified — added `recentLinkEvents` field and `linkEvents` parameter)

## Contract Model Summary

- **`LinkEventStatus`** enum: STARTED, COMPLETED, DEGRADED, FAILED, CANCELLED, UNAVAILABLE
- **`PerformanceLinkEvent`** data class: immutable timing record with flow, stage, status, correlationId, startElapsedMillis, endElapsedMillis, durationMillis, detail, source
- **`PerformanceSpanSnapshot`** data class: in-progress span with toEvent() conversion; duration = max(0, end - start)
- **`PerformanceLinkRecorder`** interface: startSpan, completeSpan, recordEvent, snapshot
- **`InMemoryPerformanceLinkRecorder`**: ring-buffer using System.nanoTime() / 1_000_000 for monotonic elapsed time; 100 max events
- **`toLinkLogLine()`** extension: `link flow=X stage=Y status=Z id=ID startElapsed=S endElapsed=E duration=Dms detail=... source=SRC`
- `SessionDebugDump.recentLinkEvents` and `buildSessionDebugDump(linkEvents=)` added with empty defaults

## Example Rendered Link Line

```
link flow=capture stage=device status=completed id=shot-1 startElapsed=1234567890123 endElapsed=1234567890268 duration=145ms detail=exposure_16ms source=CaptureSessionProcessor
```

## Verification

- `:core:session:test --tests PerformanceLinkEventTest` — 21 tests, all passed
- `:core:session:test --tests SessionDiagnosticsTest` — all passed
- `:core:session:test --tests DefaultCameraSessionTest` — all passed
- `:core:media:test` — all passed

## Risks

- No app/UI files modified; backward-compatible additions only
- Duration math uses System.nanoTime() which is monotonic on JVM (arbitrary origin, valid differences)
- Downstream packages will use PerformanceLinkRecorder for actual instrumentation
