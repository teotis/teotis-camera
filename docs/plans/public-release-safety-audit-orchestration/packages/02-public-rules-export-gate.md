# 02-public-rules-export-gate

## Goal

Turn the public-release rules into an enforceable local gate so future exports cannot silently publish personal identity, company identity, private paths, competitor-reference traces, or mismatched export behavior.

## Allowed Paths

- `/Volumes/Extreme_SSD/project/open_camera/scripts/PUBLIC_VERSION_RULES.md`
- `/Volumes/Extreme_SSD/project/open_camera/scripts/export_clean_repo.sh`
- `/Volumes/Extreme_SSD/project/open_camera/scripts/verify_public_release_safety.sh`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/output/02-public-rules-export-gate/**`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/02-public-rules-export-gate.md`

## Forbidden Paths And Actions

- Do not edit app/core/feature implementation files.
- Do not edit `public/teotis-camera` content in this package.
- Do not push or rewrite public history.

## Work

1. Reconcile rules and script behavior:
   - `export_clean_repo.sh` must target `public/teotis-camera` by default, preserve `.git`, and never initialize a fresh public Git repo unless explicitly requested for a staging directory.
   - If the script supports a staging target, it must label that path as staging and never confuse it with the canonical public repo.
2. Add or update `scripts/verify_public_release_safety.sh`:
   - scan `public/teotis-camera` tracked files, untracked files, public Git history, Git config, README/NOTICE/AUTHORS, and asset metadata fallbacks.
   - hard fail on personal/company identity, private paths, high-confidence secrets, forbidden internal directories, and unreviewed competitor-reference traces.
   - allow a small reviewed allowlist for neutral platform terms such as CameraX/Camera2.
3. Wire the verification script into the export flow so export stops before commit/push if the safety gate fails.
4. Keep the rule doc consistent with actual commands.

## Verification

Run and record:

```bash
rtk bash -n scripts/export_clean_repo.sh
rtk bash -n scripts/verify_public_release_safety.sh
rtk bash scripts/verify_public_release_safety.sh
```

If the safety script correctly fails because current public history still contains exposed identity, record that as an expected current-state failure and include the exact finding. Do not mark the package blocked solely because it detects a real pre-existing leak.

## Evidence Required

- changed files list
- shell syntax check results
- safety script result and explanation
- sample failure output proving the gate catches the known identity-history leak
- any allowlist entries and justification

## Unlock Condition

Completed when future export/push attempts have a deterministic local safety gate and the gate catches the current known exposure.
