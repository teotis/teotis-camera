# Package 99 — Integration Audit

## Package ID

`99-integration-audit`

## Goal

Validate the complete Zoom Cockpit V2 productization against the orchestration contract, package evidence, focused tests, Stage 7 boundaries, and real-device product expectations.

## Context

- Codex-retained work: final cross-package audit, product taste, real-device smoke guidance, and GO/PARTIAL/FAIL recommendation.
- Relevant docs:
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md`
  - all package docs under `packages/`
  - all package evidence under `status/`
- Non-goals:
  - Do not expand scope into preview-vs-capture saved crop semantics unless a package explicitly changed it.
  - Do not turn this into a broad UI Animation V2 audit.

## Audit Scope

1. Re-read the index and all package docs.
2. Re-read all `status/*.md` evidence files.
3. Check git status, diff, and recent commits/PRs.
4. Compare delivery against every acceptance criterion.
5. Check file ownership violations.
6. Run final focused verification and Stage 7 gate if feasible.
7. Perform product audit:
   - preset dots are exact and readable;
   - continuous slider has one-decimal current feedback;
   - capability boundary is honest;
   - recording restriction is clear;
   - UI does not claim optical zoom/focal hardware facts without evidence.

## Acceptance Criteria

- Every package acceptance criterion is met or explicitly marked unverifiable with evidence.
- Focused app/session tests pass.
- `:app:assembleDebug` passes.
- No package wrote outside allowed paths without explanation.
- Final UX state matches the user goal: points + slider + one-decimal focal/zoom label + capability boundary + recording restriction.
- Residual real-device risks are listed rather than hidden.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Expected Output

Return one of:

- `PASS` — all local criteria pass, with only real-device smoke remaining.
- `PARTIAL` — implementation is directionally correct but specific criteria are unmet or unverifiable.
- `FAIL` — blocking correctness, ownership, or verification issues remain.

## File Ownership

- `99-integration-audit` owns:
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/status/99-integration-audit.md`
  - optional final report under the same orchestration directory
- Package agents must not edit audit files.

## Allowed Paths

- Read all project files.
- Write only:
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/status/99-integration-audit.md`
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/FINAL_REPORT.md` if useful

## Forbidden Paths

- Runtime code edits unless they are obvious, low-risk, and strictly scope-contained omissions found during audit.
- Destructive git operations.

## Dependencies

- Depends on: all implementation packages complete.

## Parallel Safety

- unsafe
- Reason: final audit must run after all package evidence is available.

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status captured
- [ ] git diff --stat captured
- [ ] per-package acceptance status recorded
- [ ] verification commands run
- [ ] test results summarized
- [ ] final PASS/PARTIAL/FAIL recommendation
