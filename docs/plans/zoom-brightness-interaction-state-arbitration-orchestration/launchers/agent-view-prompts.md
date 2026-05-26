# Agent View Prompts

## Package: 01-zoom-state-arbitration-audit — Zoom State Arbitration Audit

Copy the block below into Claude Code Agent View.

---

**Mode**: package research executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/packages/01-zoom-state-arbitration-audit.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/01-zoom-state-arbitration-audit.md`

**File ownership**: you may edit only your status file. Runtime files are read-only.
**Dependencies**: none.
**Stop gates**: runtime code edits, tests edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths -> stop and ask.
**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, commands run, source references, root-cause hypothesis, recommended implementation split, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-brightness-state-arbitration-audit — Brightness State Arbitration Audit

Copy the block below into Claude Code Agent View.

---

**Mode**: package research executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/packages/02-brightness-state-arbitration-audit.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/02-brightness-state-arbitration-audit.md`

**File ownership**: you may edit only your status file. Runtime files are read-only.
**Dependencies**: none.
**Stop gates**: runtime code edits, tests edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths -> stop and ask.
**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git status, commands run, source references, root-cause hypothesis, recommended implementation split, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 03-shared-control-state-strategy — Shared Control State Strategy

Copy the block below into Claude Code Agent View after packages 01 and 02 complete.

---

**Mode**: package design executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/packages/03-shared-control-state-strategy.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/03-shared-control-state-strategy.md`

**File ownership**: you may edit only your status file. Runtime files are read-only.
**Dependencies**: wait for `status/01-zoom-state-arbitration-audit.md` and `status/02-brightness-state-arbitration-audit.md` to contain evidence.
**Stop gates**: runtime code edits, tests edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths -> stop and ask.
**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include summaries of packages 01/02, shared strategy, render arbitration rule, dispatch cadence rule, implementation split, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 04-verification-real-device-protocol — Verification And Real-Device Protocol

Copy the block below into Claude Code Agent View after package 03 completes.

---

**Mode**: package QA planning executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/packages/04-verification-real-device-protocol.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/04-verification-real-device-protocol.md`

**File ownership**: you may edit only your status file. Runtime files are read-only.
**Dependencies**: wait for `status/03-shared-control-state-strategy.md` to contain evidence.
**Stop gates**: runtime code edits, tests edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths -> stop and ask.
**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include local test matrix, trace/log expectations, real-device smoke protocol, pass/fail criteria, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 99-integration-audit — Codex Final Audit

Give this to Codex after packages 01-04 complete.

---

**Mode**: final integration auditor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
**Package docs**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/packages/*.md`
**Status files**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/*.md`
**Final prompt**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/validation/final-audit-prompt.md`

Run the final audit and write the result to `status/99-integration-audit.md`.

---
