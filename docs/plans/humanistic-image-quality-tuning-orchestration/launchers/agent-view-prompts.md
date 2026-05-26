# Agent View Prompts

## Package: 01-current-iq-gap-audit — Current IQ Gap Audit

Copy the block below into Claude Code Agent View.

---

**Mode**: research package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/packages/01-current-iq-gap-audit.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/status/01-current-iq-gap-audit.md`

**File ownership**: you may write only the status file above. Read relevant `core/**`, `feature/**`, `app/src/**`, `scripts/**`, `docs/plans/**`, and `codex/documentation.md`. Do NOT edit runtime code, tests, package docs, INDEX.md, other status files, Gradle files, or resources.
**Dependencies**: none.

**Stop gates**: runtime edits, test edits, vendor parity claims, stage transitions, destructive git operations, forbidden paths, or scope expansion -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, source/doc references, acceptance criteria status, IQ gap classification table, recommendations, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-style-target-scorecard — Style Target Scorecard

Copy the block below into Claude Code Agent View.

---

**Mode**: product/style research package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/packages/02-style-target-scorecard.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/status/02-style-target-scorecard.md`

**File ownership**: you may write only the status file above. Read relevant docs and source files. Do NOT edit runtime code, tests, package docs, INDEX.md, other status files, Gradle files, or resources.
**Dependencies**: none.

**Stop gates**: requiring new UI controls, vendor parity claims, stage transitions, destructive git operations, forbidden paths, or scope expansion -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, style target matrix, human scorecard, non-goals, unresolved product questions, and self-certification that you only touched allowed paths.

---

## Package: 03-feasible-rendering-pipeline-design — Feasible Rendering Pipeline Design

Copy the block below into Claude Code Agent View after packages 01 and 02 complete.

---

**Mode**: architecture research package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/packages/03-feasible-rendering-pipeline-design.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/status/03-feasible-rendering-pipeline-design.md`

**File ownership**: you may write only the status file above. Read package 01/02 status files and relevant source/docs. Do NOT edit runtime code, tests, package docs, INDEX.md, other status files, Gradle files, or resources.
**Dependencies**: wait for `status/01-current-iq-gap-audit.md` and `status/02-style-target-scorecard.md` to contain completed evidence.

**Stop gates**: runtime edits, new dependencies, private vendor APIs, architecture boundary violations, stage transitions, destructive git operations, forbidden paths, or scope expansion -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, architecture ownership map, capability/degradation matrix, first-slice recommendation, no-go list, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 04-real-device-capture-protocol — Real-Device Capture Protocol

Copy the block below into Claude Code Agent View after packages 01, 02, and 03 complete.

---

**Mode**: QA planning package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/packages/04-real-device-capture-protocol.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/status/04-real-device-capture-protocol.md`

**File ownership**: you may write only the status file above. Read package 01-03 status files and relevant docs/source. Do NOT edit runtime code, tests, package docs, INDEX.md, other status files, Gradle files, or resources.
**Dependencies**: wait for packages 01, 02, and 03 to complete.

**Stop gates**: cloud upload requirements, destructive device actions, runtime edits, stage transitions, destructive git operations, forbidden paths, or scope expansion -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, capture protocol, scorecard thresholds, evidence checklist, privacy guidance, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 05-implementation-roadmap — Implementation Roadmap

Copy the block below into Claude Code Agent View after packages 01-04 complete.

---

**Mode**: planning package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/packages/05-implementation-roadmap.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration/status/05-implementation-roadmap.md`

**File ownership**: you may write only the status file above. Read package 01-04 status files and relevant docs/source. Do NOT edit runtime code, tests, package docs, INDEX.md, other status files, Gradle files, resources, or `codex/documentation.md`.
**Dependencies**: wait for packages 01-04 to complete.

**Stop gates**: implementation edits, launching implementation, new stage claims, destructive git operations, forbidden paths, or scope expansion -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, git diff --stat, changed files, commands run, proposed implementation package list, rejected/deferred items, final recommendation, unresolved risks, and self-certification that you only touched allowed paths.

---
