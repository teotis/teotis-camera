# Agent View Prompts

## Package: 01-pixel-capability-enumeration — Pixel Capability Enumeration

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/01-pixel-capability-enumeration.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/01-pixel-capability-enumeration.md`

**File ownership**: you may write only the status file above. Runtime code and tests are read-only.
**Dependencies**: none.

**Stop gates**: runtime/test edits, network/dependency additions, vendor-private API claims, force-push, hard reset, deleting worktrees, touching forbidden paths, editing INDEX.md or another status file -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, source/code references, acceptance criteria status, recommended design decision, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-quick-pixel-surface-design — Quick Pixel Surface Design

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/02-quick-pixel-surface-design.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/02-quick-pixel-surface-design.md`

**File ownership**: you may write only the status file above. Runtime code and tests are read-only.
**Dependencies**: wait for `01-pixel-capability-enumeration` to complete.

**Stop gates**: runtime/test edits, designing UI labels that claim unsupported 48MP/50MP support, force-push, hard reset, deleting worktrees, touching forbidden paths, editing INDEX.md or another status file -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, source/code references, acceptance criteria status, recommended design decision, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 03-shutter-lifecycle-contract — Shutter Lifecycle Contract

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/03-shutter-lifecycle-contract.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/03-shutter-lifecycle-contract.md`

**File ownership**: you may write only the status file above. Runtime code and tests are read-only.
**Dependencies**: none.

**Stop gates**: runtime/test edits, allowing unsafe overlapping captures, breaking recording stop semantics, force-push, hard reset, deleting worktrees, touching forbidden paths, editing INDEX.md or another status file -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, source/code references, acceptance criteria status, recommended design decision, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 04-real-device-verification-protocol — Real Device Verification Protocol

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/04-real-device-verification-protocol.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/04-real-device-verification-protocol.md`

**File ownership**: you may write only the status file above. Runtime code and tests are read-only.
**Dependencies**: wait for packages 01, 02, and 03 to complete.

**Stop gates**: runtime/test edits, claiming device PASS without vivo X300 evidence, force-push, hard reset, deleting worktrees, touching forbidden paths, editing INDEX.md or another status file -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, test results, source/code references, acceptance criteria status, recommended design decision, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 99-integration-audit — Integration Audit

Copy the block below into Codex or a retained final audit agent after all package status files are complete.

---

**Mode**: integration auditor
**Repository**: `/Volumes/Extreme_SSD/project/open_camera`
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/packages/99-integration-audit.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/99-integration-audit.md`
**Validation prompt**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/validation/final-audit-prompt.md`

Read INDEX.md, all package docs, and all status files. Check every acceptance criterion, file ownership compliance, evidence quality, and whether the combined design is ready for implementation. Output PASS / PARTIAL / FAIL and write evidence to the 99 status file.

---
