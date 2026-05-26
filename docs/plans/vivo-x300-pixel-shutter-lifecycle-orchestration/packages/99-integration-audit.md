# Package 99 — Integration Audit

## Package ID

`99-integration-audit`

## Owner

Codex retained.

## Purpose

Read all package evidence and decide whether the research/design package is ready to become an implementation handoff, needs more research, or should be stopped because the required behavior cannot be supported honestly on vivo X300 with the current platform stack.

## Inputs

- `INDEX.md`
- `packages/01-pixel-capability-enumeration.md`
- `packages/02-quick-pixel-surface-design.md`
- `packages/03-shutter-lifecycle-contract.md`
- `packages/04-real-device-verification-protocol.md`
- `status/01-pixel-capability-enumeration.md`
- `status/02-quick-pixel-surface-design.md`
- `status/03-shutter-lifecycle-contract.md`
- `status/04-real-device-verification-protocol.md`

## Audit Steps

1. Re-read the original user request and this orchestration index.
2. Re-read every package doc and status file.
3. Check whether package 01 proves a plausible high-pixel capability contract without false claims.
4. Check whether package 02 maps capability truth into quick pixel UI/session semantics without hiding unsupported/degraded cases.
5. Check whether package 03 separates frame acquisition from postprocess/save/UI feedback without allowing unsafe overlapping captures.
6. Check whether package 04 defines real-device evidence strong enough for vivo X300 acceptance.
7. Check file ownership compliance: package agents should have edited only their own status file.
8. Run only low-risk read-only inspections unless the user authorizes implementation.
9. Output **PASS**, **PARTIAL**, or **FAIL**.

## PASS Criteria

- All four research/design packages completed their status evidence.
- No package implemented runtime changes.
- The combined design has clear supported/degraded/unsupported semantics for pixel capability.
- The shutter re-arm policy is conservative by capture kind and does not add a hidden second session kernel.
- The real-device protocol can catch the original two symptoms.

## PARTIAL Criteria

- One package is incomplete but enough evidence exists to write a narrower follow-up.
- High-pixel support remains uncertain but the degraded/unsupported path is honest.
- Shutter lifecycle design is plausible but needs one more code audit before implementation.

## FAIL Criteria

- Evidence conflicts across packages.
- A package edited forbidden files or claimed real-device success without device evidence.
- The proposed design would make unsafe repeated capture possible in special modes.
- The pixel capability design would show 48MP/50MP without bindable/saved-output proof.
