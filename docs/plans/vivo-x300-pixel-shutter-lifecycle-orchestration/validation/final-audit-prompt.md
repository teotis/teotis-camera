# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/INDEX.md`
- Packages:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/01-pixel-capability-enumeration.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/02-quick-pixel-surface-design.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/03-shutter-lifecycle-contract.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/04-real-device-verification-protocol.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/99-integration-audit.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/*.md`

## Audit Steps

1. Read INDEX.md and all package docs.
2. Read all package status files.
3. Run:

```bash
rtk git status --short
rtk git diff --stat -- docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration
rtk rg -n "Status|Acceptance Criteria|Recommended decision|Unresolved Risks" docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status
```

4. For each package, check every acceptance criterion.
5. Check for file ownership conflicts:
   - Did any agent edit a file it was not assigned?
   - Did any package agent edit INDEX.md?
   - Did any package agent implement runtime changes despite research-only scope?
6. Check design consistency:
   - Does pixel capability use supported/degraded/unsupported semantics?
   - Does quick pixel UI avoid false 48MP/50MP claims?
   - Does shutter lifecycle separate frame acquisition from postprocess/save?
   - Does real-device QA catch the original vivo X300 symptoms?
7. Report: **PASS** / **PARTIAL** / **FAIL**.

## Evidence Required

- Per-package acceptance criteria status.
- Commands run and output summary.
- Cross-package conflict report.
- Final recommendation:
  - proceed to implementation handoff,
  - run another research pass,
  - or stop because capability cannot be supported honestly.

## Important Boundaries

- Do not fix runtime code during this audit.
- Do not claim real-device acceptance without actual vivo X300 evidence.
- Do not mark PASS if the design would allow unsafe repeated captures in night/high-pixel/multi-frame modes.
