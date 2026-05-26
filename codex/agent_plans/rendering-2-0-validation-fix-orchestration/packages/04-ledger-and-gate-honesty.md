# Package 04 — Ledger And Gate Honesty

## Package ID

`04-ledger-and-gate-honesty`

## Goal

Repair the planning and verification record so it reflects the actual state of the Rendering 2.0 landing. The package index says implemented, but the top-level plan index still marks the same package as planned, and Stage 7 verification remains unstable around `DefaultCameraSessionTest`.

## Context

- Verified facts:
  - `codex/agent_plans/2026-05-25-rendering-2-0-approved-upgrade-index.md` says package status is `implemented`.
  - `codex/agent_plans/INDEX.md` still marks Rendering 2.0 entries as `planned`.
  - `scripts/verify_stage_7_observability.sh` can pass earlier media/device/mode segments, but local runs hang or time out in `:core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`.
  - A single `DefaultCameraSessionTest` method can pass, suggesting the issue is class-level runtime, scheduler, memory, or long-running test behavior rather than a simple compile failure.
- Relevant files:
  - `codex/agent_plans/INDEX.md`
  - `codex/agent_plans/2026-05-25-rendering-2-0-approved-upgrade-index.md`
  - `scripts/verify_stage_7_observability.sh`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- Non-goals:
  - Do not change product behavior.
  - Do not weaken Stage 7 verification by silently removing coverage.
  - Do not edit implementation files owned by packages 01-03.

## File Ownership

- Allowed paths:
  - `codex/agent_plans/INDEX.md`
  - `codex/agent_plans/2026-05-25-rendering-2-0-approved-upgrade-index.md`
  - `scripts/verify_stage_7_observability.sh`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
  - narrowly scoped test infrastructure files if the hang root cause requires it
- Forbidden paths:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `core/effect/**` except test references if needed
  - orchestration `INDEX.md`

## Implementation Scope

- Update plan ledger status to `implemented`, `validated`, `partial`, or `blocked` consistently based on actual verification.
- Isolate the `DefaultCameraSessionTest` hang:
  - identify the slow/hanging test or coroutine job;
  - fix the test if it leaks collectors/jobs or leaves virtual time work pending;
  - if it is genuinely environment-memory-related, document and split the Stage 7 script into smaller serial segments with clear failure output.
- Preserve Stage 7 coverage; do not simply delete the test from the gate.

## Acceptance Criteria

- Top-level `INDEX.md` and Rendering package index agree on package status.
- `verify_stage_7_observability.sh` no longer silently hangs in session tests on a normal local run, or it fails fast with a clear actionable reason.
- Any changed test still validates the original behavior.
- Documentation distinguishes local verification passed, blocked, and real-device-only risks.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest --tests com.opencamera.core.session.ThermalBudgetBridgeTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Expected Evidence Pack

- Exact root cause or best evidence for the session test hang.
- Updated ledger/status diff.
- Verification result, including whether Stage 7 full gate completed.

## Risks And Notes

- This package may uncover broader session-test debt. If fixing it requires refactoring session processors, stop and ask.

