# Package 01 - Vendor And Literature Research

## Goal

Turn the current Apple/vivo/OPPO/Android/research evidence into a concise Full Clear V2 competitive and technical research note.

## Allowed Paths

- `docs/plans/full-clear-mode-v2-research-orchestration/competitive-research.md`
- `docs/plans/full-clear-mode-v2-research-orchestration/status/01-vendor-and-literature-research.md`

## Forbidden Paths

- Runtime source files.
- Build files.
- Other package status files.

## Required Work

1. Review the source list in `competitive-research.md`.
2. If web access is available, refresh Apple/vivo/OPPO/Android source notes and record access dates.
3. If web access is not available, use the existing source list and mark refresh as not performed.
4. Translate vendor lessons into actionable Full Clear V2 principles.
5. Separate confirmed public evidence from inference.

## Acceptance Criteria

- Research note cites source URLs.
- Apple/vivo/OPPO lessons are product/architecture lessons, not copied marketing prose.
- Android API feasibility is mapped to concrete project implications.
- Research limitations are explicit.

## Verification Commands

```bash
rtk bash -lc 'rg -n "Apple|vivo|OPPO|Android|Research|V2" docs/plans/full-clear-mode-v2-research-orchestration/competitive-research.md'
```

## Expected Evidence

- Source refresh status.
- Summary of material changes.
- Verification result.

