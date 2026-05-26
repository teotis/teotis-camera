# Package 99 — Integration Audit

## Package ID

`99-integration-audit`

## Goal

Codex reads all package evidence and decides whether the research/design package is acceptance-ready, partial, or failed, and whether it should be converted into a runtime implementation pass.

## Audit Steps

1. Re-read `INDEX.md` and all package docs.
2. Re-read all `status/*.md` files.
3. Check current `git status --short` and note unrelated dirty/conflicted files.
4. Compare every package delivery against its acceptance criteria.
5. Verify the final strategy:
   - zoom node numbers are display-only and not mixed with runtime ownership;
   - active drag state is ephemeral UI state only;
   - Session Kernel remains committed state owner;
   - brightness keeps request id stale filtering;
   - latest-wins / duplicate dispatch risk is addressed by evidence;
   - verification protocol includes real-device smoke.
6. Run only low-risk read/focused verification commands if useful. Do not modify runtime code unless the user separately authorizes implementation.
7. Output one of: `PASS`, `PARTIAL`, or `FAIL`.

## Acceptance Criteria

- PASS means all research packages produced concrete evidence and the shared strategy is implementable.
- PARTIAL means some evidence is missing but the remaining gaps are clearly listed.
- FAIL means the package lacks enough evidence to guide implementation safely.

## Allowed Paths

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/99-integration-audit.md`

## Forbidden Paths

- Runtime source/test files unless user separately authorizes implementation.
- Other package status files.

## Dependencies

- Depends on: packages 01-04 complete.

## Expected Evidence Pack

- [ ] package statuses read
- [ ] acceptance criteria matrix
- [ ] final PASS/PARTIAL/FAIL
- [ ] recommended next action
- [ ] unresolved risks
