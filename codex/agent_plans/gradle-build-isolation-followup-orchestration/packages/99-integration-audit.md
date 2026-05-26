# 99 Integration Audit

## Package ID

99-integration-audit

## Goal

Codex validates that the build-isolation follow-up is complete enough to accept: Gradle override works, wrapper works, stage scripts no longer bypass isolated roots, and docs truthfully describe the current state.

## Codex-Retained Scope

- Re-read the orchestration index and both implementation packages.
- Read all status files.
- Check current git status and diffs.
- Compare delivery against every acceptance criterion.
- Run final verification commands.
- Report PASS, PARTIAL, or FAIL.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:media:test
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-final ./scripts/verify_stage_6b3_watermark_v2.sh
rtk rg -n "Gradle Isolation|build isolation|run_isolated_gradle|OPENCAMERA_BUILD_ROOT|implemented-with-follow-up" AGENTS.md codex/documentation.md codex/agent_plans/INDEX.md codex/agent_plans/2026-05-26-agent-handoff-gradle-isolation-index.md
```

## Acceptance Criteria

- Package 01 evidence proves scripts and Gradle output share the selected isolated root.
- Package 02 evidence proves the missing handoff package is restored and docs do not overclaim.
- No package touched forbidden paths.
- Final focused Gradle checks pass or any failures are classified with evidence as unrelated to build isolation.
- Codex can state whether the external-agent build-isolation repair is accepted, accepted with follow-up, or rejected.

