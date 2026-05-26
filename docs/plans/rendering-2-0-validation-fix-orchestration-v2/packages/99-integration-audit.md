# Package 99 - Integration Audit

## Package ID

`99-integration-audit`

## Owner

Codex retained.

## Goal

Validate that packages 01-04 actually landed, that each acceptance criterion is met, that file ownership was respected, and that final verification is meaningful.

## Inputs

- `docs/plans/rendering-2-0-validation-fix-orchestration-v2/INDEX.md`
- `docs/plans/rendering-2-0-validation-fix-orchestration-v2/packages/*.md`
- `docs/plans/rendering-2-0-validation-fix-orchestration-v2/status/*.md`
- Current code and docs in the repository

## Audit Steps

1. Read the index and all package documents.
2. Read all package status files and verify each contains evidence, not just claims.
3. Check `git status`, `git diff --stat`, and recent commits/PR references.
4. For each package, compare code/docs against every acceptance criterion.
5. Check that no agent edited forbidden paths or another package status file.
6. Run focused verification for the touched areas.
7. Run the relevant Stage 7 verification only if focused gates and static checks pass.
8. Output one of: PASS, PARTIAL, or FAIL.

## Final Verification Candidates

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.CompositeMediaPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

Use isolated Gradle wrapper commands instead when auditing worktree output.

## Audit Constraints

- Fix only obvious, scope-contained audit omissions.
- Do not expand Rendering 2.0 scope into sharing, advanced Color Lab tools, or unrelated feature work.
- Do not cross project stage boundaries.
- Do not delete worktrees or use destructive git operations.

## Evidence Output

Write the final audit result to `status/99-integration-audit.md` and include:
- per-package result
- verification commands and results
- conflicts or file ownership violations
- unresolved real-device risks
- final recommendation: merge, fix-then-merge, or do-not-merge
