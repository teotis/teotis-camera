# Package 05 - Full Clear QA And Product Docs

## Goal

Produce the final local evidence pack, APK path, install command, real-device smoke checklist, and updated product documentation for Full Clear V1.

## Allowed Paths

- `docs/plans/full-clear-mode-v1-orchestration/**`
- `docs/plans/INDEX.md`
- `codex/documentation.md`
- `scripts/**` only if a tiny verification helper is necessary and approved by existing patterns

## Forbidden Paths

- Runtime code changes except trivial doc-link compile fixes requested by prior packages.
- Coordinator files outside `status/05-full-clear-qa-and-product-docs.md`.

## Required Work

1. Update `product-definition.md` and `v1-implementation-design.md` with the final landed model names, statuses, and caveats.
2. Produce a real-device QA checklist for vivo X300 close foreground + distant background scenes.
3. Build or reference the debug APK path and provide `adb install` command.
4. Summarize local verification commands and exact pass/fail status.
5. Update `codex/documentation.md` with a concise, honest Full Clear V1 status note.
6. Do not mark product acceptance complete without external device evidence.

## Acceptance Criteria

- Product docs match code behavior and diagnostics.
- APK path is real or the package records why it could not build.
- Checklist separates local merge readiness from real-device product confidence.
- `docs/plans/INDEX.md` links this orchestration.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-fullclear:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- APK path and install command.
- Checklist path.
- Documentation changes.
- Verification command results.

