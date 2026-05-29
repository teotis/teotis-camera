# Worktree Inventory - 2026-05-30

## Evidence Snapshot

| Metric | Value |
|---|---|
| Repository total (`.`) | 82G |
| `.worktrees` | 19G |
| `.claude/worktrees` | 51G |
| `docs/plans` | 1.2G |
| `public/teotis-camera` | 1.4G |
| Main checkout branch | `main` (ahead 93) |
| Main checkout commit | `1b953a43` |
| Inventory date | 2026-05-30 |

## Commands Executed

```
rtk git worktree list
rtk git status --short --branch
rtk du -sh . .worktrees .claude/worktrees docs/plans public/teotis-camera
rtk bash -lc 'find .claude/worktrees .worktrees -maxdepth 3 -type d -name .git -print'
rtk git branch --merged main
rtk git branch -a --merged main
# Per-worktree: git -C <path> status --short
```

## Classification Summary

| Classification | Count | Total Size (est.) |
|---|---|---|
| active | 6 | ~orchestration worktrees |
| candidate-merged (clean) | 43 | ~67G combined |
| candidate-stale (clean, not merged) | 11 | ~small |
| unknown (dirty) | 7 | ~small |
| protected | 1 | 1.4G |

---

## Active Worktrees

These are currently checked out, part of active orchestration, or the main checkout. Not cleanup candidates.

| # | Path | Branch | Commit | Dirty | Notes |
|---|---|---|---|---|---|
| 1 | `/Volumes/Extreme_SSD/project/open_camera` | `main` | `1b953a43` | M (status files) | Main checkout |
| 2 | `/private/tmp/open_camera-orchestration/repo-hygiene-session-test-docs-archive/01-worktree-inventory-and-safety-plan` | `agent/repo-hygiene-session-test-docs-archive/01-worktree-inventory-and-safety-plan` | `1b953a43` | — | Current session |
| 3 | `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/01-saved-photo-writeback-honesty` | `agent/scene-mask-honesty-repair/01-saved-photo-writeback-honesty` | `36d1867f` | — | Active orchestration |
| 4 | `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/02-preview-analysis-budget-contract` | `agent/scene-mask-honesty-repair/02-preview-analysis-budget-contract` | `d8e9b0e5` | — | Active orchestration |
| 5 | `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/03-scene-mask-verification-gate` | `agent/scene-mask-honesty-repair/03-scene-mask-verification-gate` | `8a9c2e13` | — | Active orchestration |
| 6 | `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/integration` | `agent/scene-mask-honesty-repair/integration` | `319768df` | — | Active orchestration |

---

## Unknown (Dirty) Worktrees

Any dirty worktree is classified `unknown` and NOT approved for cleanup. The user must review and decide.

| # | Path | Branch | Commit | Dirty | Notes |
|---|---|---|---|---|---|
| 1 | `.claude/worktrees/03-session-kernel-invariants` | `worktree-03-session-kernel-invariants` | `5f5f0b11` | DIRTY | Branch not merged |
| 2 | `.claude/worktrees/05-conversion-glue-detection` | `worktree-05-conversion-glue-detection` | `5f5f0b11` | DIRTY | Branch merged but dirty |
| 3 | `.claude/worktrees/color-lab-capture-save-regression` | `worktree-color-lab-capture-save-regression` | `bf87213b` | DIRTY | Branch merged but dirty |
| 4 | `.claude/worktrees/effect-preview-api-drift` | `worktree-effect-preview-api-drift` | `bb42921f` | DIRTY | Branch not merged |
| 5 | `.claude/worktrees/pkg-04-ledger-fix` | `worktree-pkg-04-ledger-fix` | `ea27ba24` | DIRTY | Branch merged but dirty |
| 6 | `.claude/worktrees/zoom-v2-03-session-recording-zoom-policy` | `worktree-zoom-v2-03-session-recording-zoom-policy` | `ab6b0103` | DIRTY | Branch merged but dirty |
| 7 | `.worktrees/real-device-ux-regression-20260527/01-zoom-threshold-lens-switch` | `agent/real-device-ux-regression-20260527/01-zoom-threshold-lens-switch` | `60b2f1c2` | DIRTY | Branch merged but dirty |

---

## Candidate-Merged (Clean) Worktrees

Branch merged to `main`, clean working tree. Safest cleanup candidates.

| # | Path | Branch | Commit | Dirty |
|---|---|---|---|---|
| 1 | `.claude/worktrees/01-app-unit-test-gate-cleanup` | `worktree-01-app-unit-test-gate-cleanup` | `d895f784` | CLEAN |
| 2 | `.claude/worktrees/01-effect-test-contract` | `fix/01-effect-test-contract` | `a4a2586c` | CLEAN |
| 3 | `.claude/worktrees/02-shutter-state-animation-v2` | `worktree-02-shutter-state-animation-v2` | `98ab517a` | CLEAN |
| 4 | `.claude/worktrees/02-slider-widget-productization` | `agent/zoom-cockpit-v2-productization/02-slider-widget-productization` | `67488a4a` | CLEAN |
| 5 | `.claude/worktrees/02-translation-audit` | `worktree-02-translation-audit` | `4c728e1c` | CLEAN |
| 6 | `.claude/worktrees/03-shared-control-state-strategy` | `worktree-03-shared-control-state-strategy` | `7eecada1` | CLEAN |
| 7 | `.claude/worktrees/04-verification-real-device-protocol` | `worktree-04-verification-real-device-protocol` | `155ef872` | CLEAN |
| 8 | `.claude/worktrees/07-synthesis-html-report` | `worktree-07-synthesis-html-report` | `5f5f0b11` | CLEAN |
| 9 | `.claude/worktrees/99-finalize` | `agent/ui-i18n-cleanup/99-finalize` | `499a5d9e` | CLEAN |
| 10 | `.claude/worktrees/agent-01-iq-gap-audit` | `worktree-agent-01-iq-gap-audit` | `2cff9aa9` | CLEAN |
| 11 | `.claude/worktrees/brightness-audit-02` | `worktree-brightness-audit-02` | `7e4efe33` | CLEAN |
| 12 | `.claude/worktrees/effect-preview-color-transform-fix` | `worktree-effect-preview-color-transform-fix` | `8e9c3e3a` | CLEAN |
| 13 | `.claude/worktrees/feat+color-lab-perceptual-strength` | `worktree-feat+color-lab-perceptual-strength` | `fcb70068` | CLEAN |
| 14 | `.claude/worktrees/feat+preview-analysis-fanout-stability` | `worktree-feat+preview-analysis-fanout-stability` | `17c1c161` | CLEAN |
| 15 | `.claude/worktrees/focal-slider-05` | `worktree-focal-slider-05` | `08b0054e` | CLEAN |
| 16 | `.claude/worktrees/pkg-01-preview-frame` | `worktree-pkg-01-preview-frame` | `868b0cf5` | CLEAN |
| 17 | `.claude/worktrees/pkg-01-watermark-recovery` | `worktree-pkg-01-watermark-recovery` | `74a07e6e` | CLEAN |
| 18 | `.claude/worktrees/pkg-02-ledger-rules` | `worktree-pkg-02-ledger-rules` | `283d9e85` | CLEAN |
| 19 | `.claude/worktrees/pkg-02-mask-audit` | `worktree-pkg-02-mask-audit` | `80f73720` | CLEAN |
| 20 | `.claude/worktrees/pkg-02-style-scorecard` | `worktree-pkg-02-style-scorecard` | `f966f7a8` | CLEAN |
| 21 | `.claude/worktrees/pkg-02-zoom-scaleend-recovery` | `worktree-pkg-02-zoom-scaleend-recovery` | `ed8f6a43` | CLEAN |
| 22 | `.claude/worktrees/pkg-03-rendering-pipeline` | `worktree-pkg-03-rendering-pipeline` | `e15e40c4` | CLEAN |
| 23 | `.claude/worktrees/pkg-04-ledger-v2` | `worktree-pkg-04-ledger-v2` | `42c91ef2` | CLEAN |
| 24 | `.claude/worktrees/pkg-04-zoom-semantics` | `worktree-pkg-04-zoom-semantics` | `1b254769` | CLEAN |
| 25 | `.claude/worktrees/postprocess-outer-guard` | `fix/postprocess-outer-guard` | `48c21ee9` | CLEAN |
| 26 | `.claude/worktrees/preview-fidelity-03` | `worktree-preview-fidelity-03` | `6f56fbaf` | CLEAN |
| 27 | `.claude/worktrees/preview-saved-mask-consistency` | `worktree-preview-saved-mask-consistency` | `6d410918` | CLEAN |
| 28 | `.claude/worktrees/real-device-ui-layout-watermark-20260528-01-preview-frame-containment` | `agent/real-device-ui-layout-watermark-20260528/01-preview-frame-containment` | `b131f810` | CLEAN |
| 29 | `.claude/worktrees/real-device-ui-layout-watermark-20260528-02-bottom-cockpit-density` | `agent/real-device-ui-layout-watermark-20260528/02-bottom-cockpit-density` | `4b76ed23` | CLEAN |
| 30 | `.claude/worktrees/real-device-ui-layout-watermark-20260528-03-quick-watermark-cycle` | `agent/real-device-ui-layout-watermark-20260528/03-quick-watermark-cycle` | `f966c5b2` | CLEAN |
| 31 | `.claude/worktrees/real-device-ui-layout-watermark-20260528-04-real-device-acceptance` | `agent/real-device-ui-layout-watermark-20260528/04-real-device-acceptance` | `46f44867` | CLEAN |
| 32 | `.claude/worktrees/real-device-ui-layout-watermark-20260528-integration` | `agent/real-device-ui-layout-watermark-20260528/integration` | `3798fd5b` | CLEAN |
| 33 | `.claude/worktrees/recipe-single-truth` | `recipe-single-truth` | `93b4c2a9` | CLEAN |
| 34 | `.claude/worktrees/replicated-tickling-bonbon` | `worktree-replicated-tickling-bonbon` | `38e04c1c` | CLEAN |
| 35 | `.claude/worktrees/scene-mask-research-01` | `worktree-scene-mask-research-01` | `a7348232` | CLEAN |
| 36 | `.claude/worktrees/shutter-data-boundary-v1` | `fix/shutter-data-boundary-v1` | `1275f006` | CLEAN |
| 37 | `.claude/worktrees/shutter-state-animation-v2` | `worktree-shutter-state-animation-v2` | `2d9d6244` | CLEAN |
| 38 | `.claude/worktrees/ui-v2-00-mode-order` | `worktree-ui-v2-00-mode-order` | `a53aac41` | CLEAN |
| 39 | `.claude/worktrees/ui-v2-01-focus-feedback` | `ui-v2-01-focus-feedback` | `f94b7550` | CLEAN |
| 40 | `.claude/worktrees/ui-v2-integration-audit` | `worktree-ui-v2-integration-audit` | `cf80340d` | CLEAN |
| 41 | `.claude/worktrees/wobbly-pondering-bear` | `worktree-wobbly-pondering-bear` | `aee60d9d` | CLEAN |
| 42 | `.claude/worktrees/zoom-arbitration-audit-01` | `worktree-zoom-arbitration-audit-01` | `20bdc355` | CLEAN |
| 43 | `.claude/worktrees/zoom-cockpit-v2` | `worktree-zoom-cockpit-v2` | `1c8aec36` | CLEAN |
| 44 | `.worktrees/dev-log-tag-system/01-dev-log-tag-system` | `agent/dev-log-tag-system/01-dev-log-tag-system` | `5065d0d6` | CLEAN |
| 45 | `.worktrees/full-clear-mode-v1/01-full-clear-product-definition` | `agent/full-clear-mode-v1/01-full-clear-product-definition` | `222c647a` | CLEAN |
| 46 | `.worktrees/preview-fitcenter-geometry` | `feature/preview-fitcenter-geometry` | `e9eb472a` | CLEAN |
| 47 | `.worktrees/public-release-safety-audit/01-public-exposure-inventory` | `agent/public-release-safety-audit/01-public-exposure-inventory` | `7b10569b` | CLEAN |
| 48 | `.worktrees/public-release-safety-audit/02-public-rules-export-gate` | `agent/public-release-safety-audit/02-public-rules-export-gate` | `fe337e6e` | CLEAN |
| 49 | `.worktrees/public-release-safety-audit/03-brand-reference-content-scrub` | `agent/public-release-safety-audit/03-brand-reference-content-scrub` | `fe337e6e` | CLEAN |
| 50 | `.worktrees/public-release-safety-audit/04-public-history-remediation-plan` | `agent/public-release-safety-audit/04-public-history-remediation-plan` | `fe337e6e` | CLEAN |
| 51 | `.worktrees/public-release-safety-audit/05-export-diff-release-verification` | `agent/public-release-safety-audit/05-export-diff-release-verification` | `fe337e6e` | CLEAN |
| 52 | `.worktrees/public-release-safety-audit/99-finalize` | `agent/public-release-safety-audit/integration` | `fe337e6e` | CLEAN |
| 53 | `.worktrees/real-device-ux-regression-20260527/05-integration-visual-smoke-protocol` | `agent/real-device-ux-regression-20260527/05-integration-visual-smoke-protocol` | `f6651e4b` | CLEAN |
| 54 | `.worktrees/real-device-ux-regression-20260527/99-finalize` | `agent/real-device-ux-regression-20260527/99-finalize` | `c633a4be` | CLEAN |

---

## Candidate-Stale (Clean, Not Merged) Worktrees

Branch NOT merged to `main`, but working tree is clean. Requires user review to determine if work was abandoned or superseded.

| # | Path | Branch | Commit | Dirty | Notes |
|---|---|---|---|---|---|
| 1 | `.claude/worktrees/research-01-backend-capability-matrix` | `worktree-research-01-backend-capability-matrix` | `a096b05c` | CLEAN | Research branch |
| 2 | `/private/tmp/open_camera-real-device-ux-finalize-verify` | detached HEAD | `d10609a3` | CLEAN | Detached, /private/tmp |
| 3 | `/private/tmp/open_camera_01_zoom_verify_20260527_01` | detached HEAD | `60b2f1c2` | CLEAN | Detached, /private/tmp |

---

## Protected Paths

Not cleanup targets under any circumstances.

| Path | Reason |
|---|---|
| `/Volumes/Extreme_SSD/project/open_camera` | Main checkout |
| `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera` | Protected public repo, governed by `scripts/PUBLIC_VERSION_RULES.md` |
| `/Volumes/Extreme_SSD/project/open_camera/docs/plans` | Canonical planning home, 1.2G, must remain discoverable |
| `/private/tmp/open_camera-orchestration/repo-hygiene-session-test-docs-archive/*` | Current orchestration worktrees (Wave 1) |
| `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/*` | Recent active orchestration |

---

## Explicit Statement

No destructive cleanup was performed. This inventory is read-only. No worktrees were deleted, moved, pruned, or removed.
