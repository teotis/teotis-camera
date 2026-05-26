# 01 Stage Script Isolation

## Package ID

01-stage-script-isolation

## Goal

Make existing stage verification scripts safe for worktree/external-agent execution by ensuring cleanup and Gradle output use the same isolated build root. The user-visible outcome is fewer false compile/test failures caused by shared `~/.codex-build/OpenCamera` state.

## Context

- Root `build.gradle.kts` already supports `opencamera.buildRoot`, `OPENCAMERA_BUILD_ROOT`, and `CODEX_BUILD_ROOT`.
- `scripts/run_isolated_gradle.sh` already selects `OPENCAMERA_BUILD_ROOT="$HOME/.codex-build/OpenCamera-<workspace-hash>"`.
- `scripts/verify_stage_6b0_settings_foundation.sh`, `scripts/verify_stage_6b1_mode_catalog.sh`, and `scripts/verify_stage_6b2_filter_lab.sh` still default cleanup to `CODEX_BUILD_ROOT` or `~/.codex-build/OpenCamera`.
- `scripts/verify_stage_6b3_watermark_v2.sh` directly invokes `./gradlew`, so running it from a worktree can still use the shared default unless the caller manually exports a build root.

## File Ownership

Allowed paths:
- `scripts/run_isolated_gradle.sh`
- `scripts/verify_stage_6b0_settings_foundation.sh`
- `scripts/verify_stage_6b1_mode_catalog.sh`
- `scripts/verify_stage_6b2_filter_lab.sh`
- `scripts/verify_stage_6b3_watermark_v2.sh`

Forbidden paths:
- `AGENTS.md`
- `codex/documentation.md`
- `codex/agent_plans/**` except your assigned status file
- Runtime app/core Kotlin files unless a script verification bug cannot be tested otherwise; stop and ask first.

## Dependencies

None.

## Parallel Safety

Safe to run in parallel with package 02 because package 02 owns docs only. Do not edit docs to avoid conflicts.

## Implementation Scope

- Update stage verification scripts so cleanup root precedence matches Gradle root precedence:
  1. `OPENCAMERA_BUILD_ROOT`
  2. `CODEX_BUILD_ROOT`
  3. default `~/.codex-build/OpenCamera`
- Ensure each script's internal Gradle calls use the same selected build root, preferably by exporting `OPENCAMERA_BUILD_ROOT="${shared_build_root}"` in the subshell before `./gradlew`.
- If useful, extend `scripts/run_isolated_gradle.sh` narrowly so it remains a Gradle wrapper, but do not turn it into a broad arbitrary command runner unless you add clear guardrails and tests.
- Keep default main-workspace behavior compatible: direct `rtk ./scripts/verify_stage_6b3_watermark_v2.sh` may still use `~/.codex-build/OpenCamera`.

## Acceptance Criteria

- The three scripts with cleanup no longer ignore `OPENCAMERA_BUILD_ROOT`.
- Script cleanup and Gradle compile output point at the same selected root.
- `verify_stage_6b3_watermark_v2.sh` can be run with `OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-isolated` and writes Gradle outputs there.
- No script deletes or cleans another workspace's hash-derived build root when an isolated root is selected.
- No runtime camera behavior changes.

## Verification Commands

```bash
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b0-isolated ./scripts/verify_stage_6b0_settings_foundation.sh
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-isolated ./scripts/verify_stage_6b3_watermark_v2.sh
rtk ls -d /private/tmp/opencamera-stage6b0-isolated /private/tmp/opencamera-stage6b3-isolated
```

If full scripts are too slow locally, run the smallest representative script first and state exactly which commands were not run in your status file.

## Expected Evidence Pack

Write to `status/01-stage-script-isolation.md` only. Include:
- selected build roots printed or observed;
- exact scripts changed;
- verification commands and pass/fail summaries;
- proof that outputs exist under isolated roots;
- unresolved scripts not covered, if any.

## Risks And Notes

- Do not replace `rtk` project command policy. Users and agents should still invoke scripts through `rtk`.
- Do not use global `rm -rf ~/.codex-build/OpenCamera` as a fix.
- If a verification script fails for an existing product/test reason unrelated to isolation, preserve that finding and do not expand scope.

