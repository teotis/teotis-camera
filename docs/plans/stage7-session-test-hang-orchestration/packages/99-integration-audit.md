# Package 99 - Integration Audit

## Package ID

`99-integration-audit`

## Goal

Audit the package 01 result and decide whether the Stage 7 session verification hang is fixed, partially mitigated, or still blocked.

## Required Checks

- Read `INDEX.md`, package 01, and both status files.
- Confirm package 01 stayed inside allowed paths.
- Confirm `DefaultCameraSessionTest` completed under isolated build conditions.
- Confirm `SessionDiagnosticsTest` still passes.
- Confirm any production code change is justified by a real session lifecycle root cause.
- Confirm no broad Stage transition or unrelated product change was introduced.

## Output

Write to `status/99-integration-audit.md`:

- verdict: PASS, PARTIAL, or FAIL
- evidence reviewed
- commands rerun by auditor, if any
- residual risks
- whether the mainline can accept the package
