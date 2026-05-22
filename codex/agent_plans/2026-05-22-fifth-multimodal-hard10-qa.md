# Fifth Feedback: Multimodal QA And High-Difficulty Owner Tasks

> **Owner:** Codex with multimodal capability. Do not hand this file to a text-only implementation agent as a coding task.

## Purpose

This file captures the hardest 10% and all visual/multimodal work from the vivo X300 recording review. Text-only agents can implement the linked packages, but final acceptance requires this review loop.

The current baseline review has been completed and written to:

- [`../Fifth-Recording-Hard10-Multimodal-QA-Report.md`](../Fifth-Recording-Hard10-Multimodal-QA-Report.md)

Use that report as the authoritative P0/P1 issue ledger and recheck protocol for the next APK.

## Why This Is The Hard Part

The remaining 2.0 gaps are not only code correctness. They are about whether the app feels like a camera:

- safe-area and text clipping under real status/recording overlays,
- whether preview remains visually dominant,
- whether Color Lab direct manipulation feels immediate,
- whether mode/zoom/shutter read as one bottom cockpit,
- whether frame overlays match the real preview content,
- whether capture thumbnail feedback feels trustworthy,
- whether Dev remains available without ruining product acceptance.

These require screenshots/recordings and visual comparison, not just unit tests.

## Codex-Owned Work

### 1. Multimodal baseline extraction

- Extract contact sheets and key frames from `/Users/dingren/Downloads/飞书20260522-162635.mp4`.
- Mark issue timestamps:
  - top clipping,
  - Color Lab palette snap-back,
  - Quick panel ratio disappearance,
  - Dev overlay dominance,
  - bottom cockpit split,
  - mode text low contrast,
  - frame geometry black/white crop mismatch.

### 2. Design arbitration

After text-only implementation packages land, compare a new vivo X300 recording against:

- vivo-like quick panel ergonomics,
- Apple-like bottom cockpit restraint and photographic style direct manipulation,
- OPPO-like readable mode/zoom controls,
- OpenCamera-specific explicit capability/degraded semantics.

The judgment should be product-facing, not only "tests pass."

### 3. Frame geometry review

Validate visually:

- 1:1, 4:3, 16:9 overlays match the intended final capture crop.
- Overlay is not lower/higher than real preview content.
- Black preview states are not introduced by layout changes.
- Mode/zoom/cockpit do not cover the active crop.

If visual mismatch remains, create a separate geometry implementation task. Do not bury it under styling.

### 4. Color Lab live feel review

Validate:

- palette reticle stays where touched,
- preview/render feedback changes are visible or at least state feedback confirms applied value,
- no `进阶` pathway appears in Color Lab,
- panel height allows enough preview to remain visible.

### 5. Capture feedback review

Validate:

- tapping shutter gives immediate feedback,
- thumbnail updates to the latest capture,
- thumbnail is not overwritten by stale preview frame,
- saved result path and processed/degraded state are visible enough.

## Required Evidence For Final Pass

- New APK build path and commit/worktree state.
- vivo X300 portrait recording covering:
  - first screen,
  - top bar,
  - Color Lab drag,
  - right rail,
  - Quick panel frame ratio,
  - mode switching,
  - one photo capture,
  - thumbnail tap or saved-result check.
- At least one saved JPEG sample if output trust is part of the same pass.

## Output Format

Produce a final Markdown report with:

- before/after screenshots or frame references,
- issue-by-issue status: `Pass / Risk / Fail`,
- remaining blockers grouped as P0/P1/P2,
- explicit final verdict: `GO`, `CONDITIONAL GO`, or `NO GO`.

Baseline output already exists:

- `NO GO for visual/product 2.0 acceptance`
- P0: Color Lab persistence, fragmented bottom cockpit, unstable quick frame-ratio visibility
- P1: top-bar clipping/hierarchy, right-rail label, engineering copy leakage, Dev dominance, frame geometry arbitration

## Non-Goals

- Do not implement large runtime changes directly in this QA task.
- Do not accept "unit tests pass" as visual pass.
- Do not hide unsupported features to make screenshots cleaner unless product explicitly approves that behavior.
