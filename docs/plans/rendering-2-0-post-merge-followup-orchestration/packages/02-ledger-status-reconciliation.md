# 02 - Ledger Status Reconciliation

## Package ID

`02-ledger-status-reconciliation`

## Goal

Reconcile Rendering 2.0 documentation and status ledgers after Codex accepted the good parts of the external split-package work. The ledger must reflect reality: implementation positives may be accepted when evidence exists, but final product acceptance and real-device visual QA must remain explicitly pending where they have not happened.

## Current Evidence To Consider

Accepted positive implementation evidence now present on the main line includes:
- Capture save reliability outer guard: `CameraXCaptureAdapter.kt` calls `guardedPostProcess(...)`, and `PostprocessOuterGuardTest.kt` covers failure fallback behavior.
- Render Recipe V2 single-truth bridge: `RenderRecipe.from(ShotResult)` exists and `PhotoAlgorithmPostProcessor.kt` falls back to recipe-derived photo algorithm work.
- Color Lab preview honesty: `PreviewColorTransform`, app overlay wiring, and `PreviewColorFidelity` now model approximate/degraded preview rather than claiming true saved-output parity.

Known limits:
- Preview is still an approximate overlay/fidelity model, not a proven real `PreviewView` color pipeline parity path.
- App unit-test execution may still be blocked until package 01 lands.
- Final real-device save/visual QA remains outside local deterministic validation.

## Allowed Paths

- `docs/plans/INDEX.md`
- `docs/plans/rendering-2-0-validation-fix-orchestration-v2/**`
- `docs/plans/2026-05-25-rendering-2-0-approved-upgrade-index.md`
- `docs/plans/2026-05-25-rendering-2-0-capture-save-reliability.md`
- `docs/plans/2026-05-25-rendering-2-0-render-recipe-single-truth.md`
- `docs/plans/2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md`
- Your own status file: `docs/plans/rendering-2-0-post-merge-followup-orchestration/status/02-ledger-status-reconciliation.md`

## Forbidden Paths

- Runtime source files under `app/src/main/**`, `core/**`, or `feature/**`
- App/core tests
- Other package status files in this follow-up orchestration

## Implementation Notes

- Update the old V2 status/audit ledger so it no longer says packages 01/02/03 are simply absent if code evidence now exists.
- Use careful wording:
  - `accepted locally` or `merged positive result` is okay where code and focused verification evidence exist.
  - Do not write `product complete`, `real-device validated`, or `full gate passed` unless supported by evidence.
- Package 03 should be described as accepted for approximate preview/fidelity honesty, not as true saved-output preview parity.
- Keep any unresolved app-unit-test gate issue visible and link it to this follow-up package 01.
- Update `docs/plans/INDEX.md` so this follow-up package is discoverable.

## Required Verification

Run through `rtk`:

```bash
rtk rg -n "Rendering 2.0|rendering-2-0|post-merge|accepted|blocked|validated|planned" docs/plans/INDEX.md docs/plans/rendering-2-0-validation-fix-orchestration-v2 docs/plans/2026-05-25-rendering-2-0-*.md
rtk rg -n "product complete|real-device validated|full gate passed" docs/plans/rendering-2-0-validation-fix-orchestration-v2 docs/plans/2026-05-25-rendering-2-0-*.md
```

If the second command finds any phrase, inspect it and either remove overclaiming text or record why it is legitimate.

## Acceptance Criteria

- [ ] `docs/plans/INDEX.md` links this follow-up orchestration.
- [ ] Old V2 package statuses no longer contradict the accepted code state for packages 01/02/03.
- [ ] Remaining app-unit-test and real-device/visual QA limits are explicit.
- [ ] No runtime source or test files are changed.
- [ ] Evidence pack is written to `status/02-ledger-status-reconciliation.md`.

## Expected Evidence Pack

Include:
- Worktree path and branch
- Changed docs
- `git diff --stat`
- Verification command output summaries
- Remaining documentation risks
- Self-certification that only allowed paths were touched
