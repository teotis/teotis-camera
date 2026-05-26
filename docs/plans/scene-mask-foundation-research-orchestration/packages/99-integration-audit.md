# Package 99 — Integration Audit

## Package ID

`99-integration-audit`

## Goal

Codex reads all research evidence and decides whether the Scene Mask foundation design is ready to become an implementation repair package, remains partial, or should be rejected/deferred.

## Dependencies

- Wait for packages 01-04.

## Allowed Paths

- Read all repository and status files.
- Write only `docs/plans/scene-mask-foundation-research-orchestration/status/99-integration-audit.md`.

## Audit Steps

1. Re-read `INDEX.md` and all package docs.
2. Re-read all status files for packages 01-04.
3. Check current repo status and recent diffs.
4. Compare package evidence against every acceptance criterion.
5. Decide the final status:
   - `PASS`: design is coherent and ready for implementation planning.
   - `PARTIAL`: useful direction, but listed questions or evidence gaps remain.
   - `FAIL`: foundation is unsound or claims cannot be made honestly.
6. If `PASS` or `PARTIAL`, recommend the smallest next implementation loop.

## Acceptance Criteria

- Final judgment cites package evidence, not only opinions.
- Final judgment distinguishes `Scene Mask` from true depth and from full semantic segmentation.
- Final judgment says whether the existing implementation is safe to repair or should be redesigned.
- Final judgment explicitly addresses Portrait, Humanistic, and Color Lab.

## Verification Commands

```bash
rtk git status --short
rtk rg -n "SceneMask|PreviewSceneMask|MlKit|MaskAware|scene-mask" app core docs/plans codex
```

## Expected Evidence Pack

Use the standard status template and add `## Final Decision`.

