# Final Audit Prompt - Stage 7 Session Test Hang

You are the retained Codex integration auditor for:

`/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration`

Read:

- `INDEX.md`
- `packages/01-session-test-hang-diagnosis-and-repair.md`
- `packages/99-integration-audit.md`
- `status/01-session-test-hang-diagnosis-and-repair.md`

Audit requirements:

1. Confirm the package stayed inside allowed paths.
2. Confirm `DefaultCameraSessionTest` was rerun under isolated build conditions and completed without manual kill.
3. Confirm `SessionDiagnosticsTest` still passes.
4. Confirm any timeout or test-fixture change preserves meaningful assertions.
5. Confirm any production change is justified by root cause evidence.
6. Decide PASS, PARTIAL, or FAIL.

Write the verdict and evidence to `status/99-integration-audit.md`.
