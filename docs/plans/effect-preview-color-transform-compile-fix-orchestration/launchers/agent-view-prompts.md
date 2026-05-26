# Agent View Prompts

## pkg-01

Implement `docs/plans/effect-preview-color-transform-compile-fix-orchestration/packages/01-preview-color-transform-test-api.md`.

Read `AGENTS.md` first. Create or reuse an isolated worktree. Use `rtk` for shell commands. When running Gradle from a worktree, use `rtk ./scripts/run_isolated_gradle.sh`; for the stage script verification, pass an explicit isolated `OPENCAMERA_BUILD_ROOT`.

Edit only the allowed paths listed in the package. Do not touch unrelated dirty files. Do not change app UI or camera runtime behavior. Commit locally when done and write the evidence pack to `docs/plans/effect-preview-color-transform-compile-fix-orchestration/status/01-preview-color-transform-test-api.md`.
