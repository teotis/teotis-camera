# Package 03 - V2 Android Architecture Design

## Goal

Design how Full Clear V2 fits this Android/Kotlin camera architecture without moving ownership across layers.

## Allowed Paths

- `docs/plans/full-clear-mode-v2-research-orchestration/v2-implementation-design.md`
- `docs/plans/full-clear-mode-v2-research-orchestration/status/03-v2-android-architecture-design.md`

## Forbidden Paths

- Runtime source files.
- Build files.
- Other package status files.

## Required Work

1. Inspect current V1 docs and current code shape if needed.
2. Define architecture placement across Mode Plugin, Session Kernel, Device Adapter, and Media Pipeline.
3. Design `FullClearSceneAssessment`, `FullClearRoute`, and `FullClearCapturePlan`.
4. Map Android/CameraX/Camera2 constraints to route decisions.
5. Identify implementation risks and tests for future code work.

## Acceptance Criteria

- No UI-owned CameraX execution.
- No second hidden session kernel.
- Device route decisions remain testable without physical device where possible.
- Real-device-only behavior is explicitly gated.

## Verification Commands

```bash
rtk bash -lc 'rg -n "Mode Plugin|Session Kernel|Device Adapter|Media Pipeline|FullClearRoute|FullClearCapturePlan" docs/plans/full-clear-mode-v2-research-orchestration/v2-implementation-design.md'
```

## Expected Evidence

- Architecture summary.
- Verification result.
- Future implementation package candidates.

