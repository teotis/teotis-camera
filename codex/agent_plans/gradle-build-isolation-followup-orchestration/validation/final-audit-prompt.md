# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/codex/agent_plans/gradle-build-isolation-followup-orchestration/INDEX.md`
- Packages:
  - `/Volumes/Extreme_SSD/project/open_camera/codex/agent_plans/gradle-build-isolation-followup-orchestration/packages/01-stage-script-isolation.md`
  - `/Volumes/Extreme_SSD/project/open_camera/codex/agent_plans/gradle-build-isolation-followup-orchestration/packages/02-ledger-and-rules-restoration.md`
  - `/Volumes/Extreme_SSD/project/open_camera/codex/agent_plans/gradle-build-isolation-followup-orchestration/packages/99-integration-audit.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/codex/agent_plans/gradle-build-isolation-followup-orchestration/status/*.md`

## Audit Steps

1. Read `INDEX.md` and all package docs.
2. Read all `status/<package-id>.md` files.
3. Run:

```bash
rtk git status --short --untracked-files=all
rtk git diff --stat HEAD
rtk git log --oneline -5
```

4. For each package, check every acceptance criterion.
5. Run integration-level verification:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:media:test
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-final ./scripts/verify_stage_6b3_watermark_v2.sh
rtk rg -n "Gradle Isolation|build isolation|run_isolated_gradle|OPENCAMERA_BUILD_ROOT|implemented-with-follow-up" AGENTS.md codex/documentation.md codex/agent_plans/INDEX.md codex/agent_plans/2026-05-26-agent-handoff-gradle-isolation-index.md
```

6. Check for cross-package conflicts:
   - Did any agent edit a file it was not supposed to?
   - Are there duplicate implementations of the same isolation behavior?
   - Do cleanup roots and Gradle build roots match in every modified script?
   - Do docs overclaim that all scripts are isolated before verification proves it?
7. Report: PASS / PARTIAL / FAIL with specific evidence for each gap.

## Evidence Required

- Per-package acceptance criteria status: met / unmet / unverifiable.
- Integration test results.
- Cross-package conflict report.
- Final recommendation: merge / fix-then-merge / do-not-merge.

