# 05-export-diff-release-verification

## Goal

Verify the final public-release path end to end without pushing: export behavior, public diff, safety gate, build viability, license/attribution, and remote-readiness.

## Dependencies

- `02-public-rules-export-gate`
- `03-brand-reference-content-scrub`
- `04-public-history-remediation-plan`

## Allowed Paths

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/output/05-export-diff-release-verification/**`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/05-export-diff-release-verification.md`

Read-only or generated verification targets:

- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/**`
- `/private/tmp/teotis-camera-public-export-check-*`

## Forbidden Paths And Actions

- Do not push to public remote.
- Do not rewrite history.
- Do not delete the canonical public repo.
- Do not mark public release ready if history cleanup still requires explicit user approval.

## Work

1. Re-run or dry-run export according to the updated rules.
2. Compare canonical public repo with a staging export when applicable.
3. Run the safety verifier and summarize all remaining findings.
4. Run a minimal public build/test check that does not rely on private build paths. Use public repo commands from `public/teotis-camera`; if Gradle is too slow or environment-dependent, record the blocker honestly.
5. Review README/README_EN, NOTICE, AUTHORS, LICENSE, and screenshot assets for consistency.
6. Produce a release-readiness report:
   - safe to push now
   - safe after history cleanup
   - blocked, with exact blockers

## Verification

Run and record:

```bash
rtk bash scripts/verify_public_release_safety.sh
rtk git -C public/teotis-camera status --short --branch
rtk git -C public/teotis-camera diff --stat
rtk git -C public/teotis-camera log --all --format='%h %an <%ae> %cn <%ce> %s' -n 20
```

For public Gradle verification, prefer:

```bash
rtk git -C public/teotis-camera status --short --branch
rtk bash -lc 'cd public/teotis-camera && ./gradlew --no-daemon :app:assembleDebug'
```

Only report this Gradle check as passed if it actually completes.

## Evidence Required

- `output/05-export-diff-release-verification/report.md`
- safety verifier output
- public diff summary
- build/test result or blocker
- final push readiness judgment

## Unlock Condition

Completed when the report clearly states whether public can be pushed now, only after approved history cleanup, or not yet.
