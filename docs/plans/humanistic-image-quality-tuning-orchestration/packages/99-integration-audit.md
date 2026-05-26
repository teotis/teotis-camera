# Package 99 — Integration Audit

## Package ID

`99-integration-audit`

## Goal

Codex retained final audit for the Humanistic Image Quality Tuning orchestration. Read every package and status file, verify that the evidence supports the roadmap, and output `PASS`, `PARTIAL`, or `FAIL` before any implementation starts.

## Allowed Paths

- Read-only: entire repository.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/99-integration-audit.md` only, unless the user separately authorizes small plan-file corrections.

## Forbidden Paths

- Do not edit runtime code or tests.
- Do not edit package agents' status files.
- Do not declare visual acceptance without real-device samples.
- Do not mark Stage 7 complete.

## Dependencies

After packages 01-05 complete.

## Audit Steps

1. Re-read `INDEX.md` and all package docs.
2. Re-read all `status/*.md` files.
3. Check git status, changed files, and whether package agents touched only allowed files.
4. Compare each package result against its acceptance criteria.
5. Check for contradictions:
   - style target impossible under feasibility design;
   - roadmap depends on unverified or failing capture reliability;
   - real-device protocol asks for evidence unavailable locally;
   - any claim suggests parity with vivo system camera.
6. Run integration-level read-only checks:

```bash
rtk git status --short
rtk rg -n "vendor|vivo|system camera|parity|match|PerceptualColorRecipe|PreviewColorTransform|PhotoAlgorithmPostProcessor|Humanistic" docs/plans/humanistic-image-quality-tuning-orchestration docs/plans/2026-05-25-color-lab* docs/plans/2026-05-25-rendering-2-0* docs/plans/2026-05-25-humanistic*
```

7. Optional focused baseline tests if implementation readiness depends on current deterministic health:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

## Acceptance Criteria

- Audit records per-package acceptance status: met, unmet, or unverifiable.
- Audit identifies cross-package contradictions or confirms none.
- Audit identifies whether the roadmap is ready for a later implementation round.
- Audit names all remaining user/Codex real-device decisions.
- Audit outputs exactly one final state: `PASS`, `PARTIAL`, or `FAIL`.

## Expected Evidence Pack

- Worktree path and branch, if any.
- Commands run and short output summary.
- Per-package acceptance matrix.
- File ownership violation check.
- Cross-package contradiction report.
- Final recommendation: merge / fix-then-merge / do-not-merge / ready-for-user-approval.

## Stop Gates

Stop before fixing anything beyond obvious status-file typos. This audit is primarily judgment and integration, not implementation.
