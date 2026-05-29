# Package 04 - V2 Algorithm And Media Pipeline Design

## Goal

Design the V2 focus fusion, confidence, fallback, and saved-output pipeline.

## Allowed Paths

- `docs/plans/full-clear-mode-v2-research-orchestration/v2-implementation-design.md`
- `docs/plans/full-clear-mode-v2-research-orchestration/v2-roadmap.md`
- `docs/plans/full-clear-mode-v2-research-orchestration/status/04-v2-algorithm-and-media-pipeline-design.md`

## Forbidden Paths

- Runtime source files.
- Build files.
- Other package status files.

## Required Work

1. Define fusion stages: exposure normalization, alignment, lens-breathing compensation, sharpness maps, confidence, output selection.
2. Define `FullClearFusionReport`.
3. Make best-frame fallback a first-class degraded success.
4. Define synthetic tests and real-device tests for future implementation.
5. Call out what cannot be proven locally.

## Acceptance Criteria

- Fusion never overwrites output without confidence.
- Edge halos, motion, and lens-breathing are explicit risk classes.
- Saved metadata/pipeline notes are sufficient for later QA.

## Verification Commands

```bash
rtk bash -lc 'rg -n "alignment|confidence|fallback|FullClearFusionReport|best-frame|lens-breathing" docs/plans/full-clear-mode-v2-research-orchestration/v2-implementation-design.md docs/plans/full-clear-mode-v2-research-orchestration/v2-roadmap.md'
```

## Expected Evidence

- Algorithm design summary.
- Verification result.
- Open risks.

