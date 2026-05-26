# Package 99 — Integration Audit

## Goal

After package 01 lands, verify that the missing `PreviewColorMatrixBuilder` compile blocker is resolved and classify the result.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-final ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Output

Report `PASS`, `PARTIAL`, or `FAIL`.

- PASS: focused effect tests pass and 6B3 gate passes or only fails on a clearly unrelated downstream issue.
- PARTIAL: compile blocker fixed but a downstream product/test failure remains.
- FAIL: `PreviewColorMatrixBuilder` or equivalent effect compile blocker remains.
