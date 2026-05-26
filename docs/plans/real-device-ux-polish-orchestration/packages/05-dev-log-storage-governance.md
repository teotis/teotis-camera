# 05 Dev Log Storage Governance

## Package ID

`05-dev-log-storage-governance`

## Goal

Add Dev log lifecycle governance: cap stored debug logs at about 20MB and provide one-tap cleanup by type.

## Problem Statement

The current Dev surface renders key/core/error/all trace content and `DevLogExporter` writes timestamped files under `debug-logs`. This is useful for Stage 7 diagnostics, but without a cap or cleanup affordance it can grow indefinitely and make the dev panel feel unmanaged.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/DevLogExporter.kt`
- `app/src/main/java/com/opencamera/app/DevLogRenderModel.kt`
- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- Dev console sections of `MainActivityActionBinder.kt`, `MainActivityActionCallbacks.kt`, `MainActivity.kt`, `MainActivityViews.kt`
- Dev console layout/resources
- `app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt`
- new focused tests under `app/src/test/java/com/opencamera/app/`
- package status file `status/05-dev-log-storage-governance.md`

## Forbidden Paths

- core session diagnostics semantics unless a field is only needed for type classification and remains backward compatible
- camera runtime behavior
- settings/style/quick/mode package files
- any other package status file

## Dependencies

None, but package 00 should ideally finish first if app tests are currently blocked by mode visibility.

## Parallel Safety

Can run with package 03.

## Implementation Notes

- Keep this as diagnostic lifecycle management, not a change to session trace ownership.
- Suggested cap: 20MB total under the Dev log directory. Enforce after export and on dev panel open if cheap.
- Cleanup by type should map to user-facing categories: Key, Core, Error, All. If exported files are not currently typed, add a small manifest or filename/tagging scheme; do not parse huge files on every render if avoidable.
- “Clear All” must be explicit and non-destructive outside the debug-log directory.
- Do not block camera startup on log pruning.

## Acceptance Criteria

- Exported Dev logs are bounded to 20MB total, with oldest eligible files pruned first.
- Dev panel exposes one-tap cleanup for Key, Core, Error, and All or a clearly equivalent type grouping.
- Cleanup never deletes files outside the app debug-log directory.
- Render model shows storage summary or cleanup availability enough for tests to assert behavior.
- File IO errors fail soft and surface a dev-panel diagnostic instead of crashing.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogExporterTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

If `DevLogExporterTest` is newly added, run the fully qualified test name selected by the implementation. In an isolated worktree, replace `rtk ./gradlew` with `rtk ./scripts/run_isolated_gradle.sh`.

## Expected Evidence Pack

- Storage cap algorithm summary.
- Cleanup categories and file safety proof.
- Focused test summaries.
- Residual risk note for Android filesystem differences if only JVM tests were run.
