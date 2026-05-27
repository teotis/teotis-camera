# 04-public-history-remediation-plan

## Goal

Prepare a safe, reviewable remediation plan for the already-pushed public Git identity history without performing destructive history rewrite or force-push.

## Dependencies

- `01-public-exposure-inventory`
- `02-public-rules-export-gate`

## Allowed Paths

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/output/04-public-history-remediation-plan/**`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/04-public-history-remediation-plan.md`

Read-only targets:

- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/**`

Temporary dry-run targets:

- `/private/tmp/teotis-camera-history-safety-dryrun-*`

## Forbidden Paths And Actions

- Do not force-push.
- Do not rewrite the live `public/teotis-camera` history.
- Do not delete remote branches/tags.
- Do not hard reset.
- Do not publish any dry-run branch.

## Work

1. Record current public branch, remote URL, head commit, and all author/committer identities.
2. Produce at least two remediation options:
   - history rewrite with neutral author/committer identity and force-with-lease after explicit approval
   - archive/recreate public repo if the user prefers a clean reset over rewrite
3. For the recommended option, provide exact commands, rollback/backup commands, and verification commands.
4. If `git filter-repo` is available locally, perform a dry-run on a temporary clone only. If unavailable, record the install/tooling gap; do not use `filter-branch` unless explicitly justified as fallback.
5. Define remote verification after cleanup:
   - fresh clone
   - `git log --all` identity scan
   - GitHub web-visible author check

## Verification

Run and record:

```bash
rtk git -C public/teotis-camera log --all --format='%H %an <%ae> %cn <%ce> %s'
rtk git -C public/teotis-camera remote -v
rtk git -C public/teotis-camera branch --show-current
```

Optional dry-run commands must run only in `/private/tmp/teotis-camera-history-safety-dryrun-*`.

## Evidence Required

- `output/04-public-history-remediation-plan/report.md`
- current exposure summary
- recommended remediation option
- exact commands requiring user approval
- rollback plan
- verification plan

## Unlock Condition

Completed when the user has a precise, non-executed history cleanup runbook and no package has performed the destructive step.
