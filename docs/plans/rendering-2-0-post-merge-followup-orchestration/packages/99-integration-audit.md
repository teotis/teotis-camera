# 99 - Integration Audit

## Package ID

`99-integration-audit`

## Owner

Codex retained. Do not assign this to a background implementation agent unless the user explicitly asks.

## Goal

After packages 01 and 02 complete, verify that the accepted Rendering 2.0 positives remain intact, the app unit-test gate is no longer blocked by unrelated geometry test compilation, and the documentation ledger no longer overclaims or underclaims the state.

## Audit Steps

1. Read `INDEX.md` and all package docs in this follow-up orchestration.
2. Read all status files under `status/`.
3. Check current git status, diff, and recent commits.
4. Verify package 01 against every acceptance criterion.
5. Verify package 02 against every acceptance criterion.
6. Re-check the accepted positives in code:
   - `guardedPostProcess(...)` fallback exists and is tested.
   - `RenderRecipe.from(ShotResult)` exists and is used by `PhotoAlgorithmPostProcessor`.
   - Preview fidelity remains honest: approximate overlay/fidelity wording, no unsupported parity claim.
7. Run final focused verification commands.
8. Report one of `PASS`, `PARTIAL`, or `FAIL`.

## Final Verification Commands

Run through `rtk`:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.RenderRecipeTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.PreviewColorTransformTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:compileDebugKotlin
```

If a command fails, determine whether it is caused by the follow-up packages or by unrelated app-wide test compilation debt. Do not mark PASS if the target test still cannot execute.

## Acceptance Criteria

- [ ] Packages 01 and 02 have completed or honestly blocked status files.
- [ ] Accepted positives are still present in code.
- [ ] App target test executes, or the remaining blocker is documented as a new package-worthy issue.
- [ ] Docs are discoverable from `docs/plans/INDEX.md`.
- [ ] Ledger language avoids unsupported product-complete or real-device-validated claims.

## Output

Write audit evidence to `status/99-integration-audit.md` and provide a concise final recommendation:

- `PASS`: follow-up complete and no new blockers found.
- `PARTIAL`: mostly complete but named non-blocking or external QA remains.
- `FAIL`: blocking implementation, verification, or ledger issue remains.
