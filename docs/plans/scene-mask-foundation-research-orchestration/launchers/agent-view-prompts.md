# Agent View Prompts

## Package: 01-backend-capability-matrix — Backend Capability Matrix

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/01-backend-capability-matrix.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/status/01-backend-capability-matrix.md`

**File ownership**: you may read the repository and write only your status file. Do NOT touch runtime code, tests, `INDEX.md`, or another package status file.  
**Dependencies**: none.

**Task**: Research official backend facts for Scene Mask: ML Kit Selfie Segmentation, ML Kit Subject Segmentation, MediaPipe Image Segmenter, CameraX/ImageAnalysis constraints, Camera2 depth/extensions if relevant, and deferred self-managed models. Produce an evidence-backed matrix for preview mask, saved-photo mask, portrait bokeh, Color Lab subject protection, background tuning, and depth slider honesty. Use `rtk` for shell commands.

**Stop gates**: runtime edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or claim beta/unverified hardware capabilities as fully supported -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, changed files if any, commands run, official source links, capability matrix, recommendation, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-current-implementation-audit — Current Implementation Audit

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/02-current-implementation-audit.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/status/02-current-implementation-audit.md`

**File ownership**: you may read the repository and write only your status file. Do NOT touch runtime code, tests, `INDEX.md`, or another package status file.  
**Dependencies**: none.

**Task**: Audit current `SceneMask` code and tests. Determine what is real, partial, claim-only, blocked, or unknown. Focus on `SceneMaskContracts`, preview source, ML Kit preview provider, saved-photo provider, mask-aware photo/portrait editor interfaces, tests, diagnostics, image ownership, and supported/degraded/unsupported honesty. Run the package verification commands through `rtk` if feasible.

**Stop gates**: runtime edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or fix failing tests -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, commands run, test results, concrete file references, current implementation finding table, smallest repair loop, unresolved risks, and self-certification.

---

## Package: 03-product-architecture-design — Product Architecture Design

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/03-product-architecture-design.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/status/03-product-architecture-design.md`

**File ownership**: you may read the repository and write only your status file. Do NOT touch runtime code, tests, `INDEX.md`, or another package status file.  
**Dependencies**: wait for packages 01 and 02 to complete.

**Task**: Translate the Scene Mask foundation into OpenCamera product/architecture semantics for Portrait, Humanistic, and Color Lab. Consume package 01 backend facts and package 02 implementation audit. Produce taxonomy, capability labels, UI/diagnostics copy rules, architecture placement, migration path, and smallest next implementation loop. Keep Stage 7 boundaries and session/device/media ownership intact.

**Stop gates**: runtime edits, product ambiguity beyond the package, force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or claim true depth from 2D mask -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, commands run, source/code references, product architecture proposal, recommended next loop, unresolved risks, and self-certification.

---

## Package: 04-verification-real-device-protocol — Verification And Real-Device Protocol

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/packages/04-verification-real-device-protocol.md`  
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/status/04-verification-real-device-protocol.md`

**File ownership**: you may read the repository and write only your status file. Do NOT touch runtime code, tests, `INDEX.md`, or another package status file.  
**Dependencies**: wait for packages 02 and 03 to complete.

**Task**: Define local and real-device QA for Scene Mask claims. Include focused Gradle gates, Stage 7 gate, preview/saved consistency checks, subject protection, background change visibility, edge/halo checks, analyzer stall checks, and honest degradation checks. Make clear which judgments require Codex/user multimodal review.

**Stop gates**: runtime edits, force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or requiring non-multimodal agents to judge skin-tone taste -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file. Include worktree path, branch, git status, commands run, verification protocol, real-device script, pass/partial/fail criteria, unresolved risks, and self-certification.

---

