# Package 02 - V2 Product Capability Model

## Goal

Define the Full Clear V2 product promise, user-facing states, and support/degradation model.

## Allowed Paths

- `docs/plans/full-clear-mode-v2-research-orchestration/v2-product-definition.md`
- `docs/plans/full-clear-mode-v2-research-orchestration/status/02-v2-product-capability-model.md`

## Forbidden Paths

- Runtime source files.
- Build files.
- Other package status files.

## Required Work

1. Use package 01 research to refine V2 promise and non-promises.
2. Define user-visible guidance states.
3. Define support semantics for deep-DOF lens, focus bracket, lens-aware routing, alignment confidence, fusion confidence, and diagnostics.
4. Include V2 copywriting guidance in Chinese and English if useful.

## Acceptance Criteria

- Product promise is stronger than V1 but does not overclaim vendor parity.
- Every V2 feature has supported/degraded/unsupported semantics.
- User guidance favors intent and confidence rather than exposing raw focus distances.

## Verification Commands

```bash
rtk bash -lc 'rg -n "supported|degraded|unsupported|Full Clear|全清|V2" docs/plans/full-clear-mode-v2-research-orchestration/v2-product-definition.md'
```

## Expected Evidence

- Product model summary.
- Verification result.
- Remaining open product decisions.

