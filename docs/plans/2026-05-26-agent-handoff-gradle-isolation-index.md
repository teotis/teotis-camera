# Gradle Build Isolation — Handoff Package

## Status

`validated`

Gradle build-root override, `scripts/run_isolated_gradle.sh`, and the legacy stage 6B verification scripts now use the same build-root precedence:

1. `OPENCAMERA_BUILD_ROOT`
2. `CODEX_BUILD_ROOT`
3. `~/.codex-build/OpenCamera`

This closes the build-directory pollution gap that appeared when external agents or worktrees ran Gradle against the shared default build root.

## Verified Facts

- Root `build.gradle.kts` supports `opencamera.buildRoot`, `OPENCAMERA_BUILD_ROOT`, and `CODEX_BUILD_ROOT`, in that precedence order.
- `scripts/run_isolated_gradle.sh` derives a hash-based default root per workspace, but now respects a caller-provided `OPENCAMERA_BUILD_ROOT` or `CODEX_BUILD_ROOT`.
- `scripts/verify_stage_6b0_settings_foundation.sh`, `scripts/verify_stage_6b1_mode_catalog.sh`, `scripts/verify_stage_6b2_filter_lab.sh`, and `scripts/verify_stage_6b3_watermark_v2.sh` select the same shared build root for cleanup and internal Gradle calls.
- Worktree or external-agent Gradle runs should still use `rtk ./scripts/run_isolated_gradle.sh <gradle-args>` unless a stage script is being invoked with an explicit isolated `OPENCAMERA_BUILD_ROOT`.

## Acceptance Policy

- Main workspace: direct `rtk ./gradlew ...` remains acceptable.
- Worktree or external agent: use `rtk ./scripts/run_isolated_gradle.sh ...`, or invoke legacy stage scripts with an explicit isolated `OPENCAMERA_BUILD_ROOT`.
- If a Gradle failure references source paths or metadata from another workspace, rerun the smallest failing command under an isolated root before classifying it as a product regression.

## Orchestration Record

- Follow-up kit: [`gradle-build-isolation-followup-orchestration/INDEX.md`](./gradle-build-isolation-followup-orchestration/INDEX.md)
- Package 01: legacy stage script isolation — landed on main.
- Package 02: ledger and rules restoration — landed on main.
- Package 99: Codex final audit — current result is PASS with a remaining recommendation to rerun full long gates serially when the machine is idle.
