# Agent View Prompts

## Package: 01-session-test-hang-diagnosis-and-repair - Session Test Hang Diagnosis And Repair

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/packages/01-session-test-hang-diagnosis-and-repair.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/01-session-test-hang-diagnosis-and-repair.md`

**File ownership**: edit only the allowed paths listed in the package. Prefer `core/session` test/fixture repair unless production root cause is proven.
**Dependencies**: none.

**Stop gates**: broad session redesign, UI/product changes unrelated to the hang, destructive git operations, network/secrets, killing processes not owned by your run, touching forbidden paths, editing INDEX.md or another status file -> stop and ask.

**Required first command in worktree**:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, process evidence for any hang, source/code references, acceptance criteria status, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 99-integration-audit - Integration Audit

Copy the block below into Codex or a retained final audit agent after package 01 is complete.

---

**Mode**: integration auditor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/packages/99-integration-audit.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/99-integration-audit.md`
**Validation prompt**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/validation/final-audit-prompt.md`

Read INDEX.md, package docs, and status files. Check acceptance criteria, file ownership, root-cause quality, and verification honesty. Output PASS / PARTIAL / FAIL and write evidence to the 99 status file.

---
