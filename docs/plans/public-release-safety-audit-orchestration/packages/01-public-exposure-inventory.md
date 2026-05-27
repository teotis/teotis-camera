# 01-public-exposure-inventory

## Goal

Create a current, evidence-backed exposure inventory for the public repository before anyone cleans or pushes anything.

## Allowed Paths

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/output/01-public-exposure-inventory/**`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/01-public-exposure-inventory.md`

Read-only targets:

- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/**`
- `/Volumes/Extreme_SSD/project/open_camera/scripts/PUBLIC_VERSION_RULES.md`
- `/Volumes/Extreme_SSD/project/open_camera/scripts/export_clean_repo.sh`

## Forbidden Paths And Actions

- Do not edit `public/teotis-camera`.
- Do not rewrite Git history.
- Do not push to any remote.
- Do not delete files.

## Work

1. Inspect public repo identity and remote-visible history:
   - `git -C public/teotis-camera log --all --format='%H%x09%an%x09%ae%x09%cn%x09%ce%x09%s'`
   - `git -C public/teotis-camera config --list --show-origin`
   - `git -C public/teotis-camera status --short --branch`
2. Scan tracked, untracked, and hidden metadata for identity/path/company leakage:
   - personal names/usernames, company email/domain, local paths, disk names, generated temp paths.
3. Scan public-facing docs, README files, NOTICE/AUTHORS, commit messages, source comments, test fixtures, and asset names for competitor/reference traces:
   - Apple, iPhone, vivo, Xiaomi, MIUI, MiuiCamera, Leica, Hasselblad, X-series device names, competitor/reference/learning wording.
4. Inspect screenshot/assets metadata as far as local tools allow. If a metadata tool is missing, record that gap and use `file`, `strings`, and image dimensions as the minimum fallback.
5. Classify every finding:
   - `P0`: already public identity/company/secrets/history exposure
   - `P1`: public content implying competitor learning/reference or private design source
   - `P2`: public polish/legal/attribution ambiguity
   - `Allowed`: neutral platform/API/device compatibility usage with justification

## Verification

Run and record:

```bash
rtk git -C public/teotis-camera status --short --branch
rtk git -C public/teotis-camera log --all --format='%H %an <%ae> %cn <%ce> %s'
rtk rg -n --hidden --fixed-strings -e 'dingren' -e 'dingren@' -e '/Users/' -e '丁仁' public/teotis-camera --glob '!public/teotis-camera/.git/objects/**'
rtk rg -n --fixed-strings -e 'Apple' -e 'iPhone' -e 'vivo' -e 'Xiaomi' -e 'MIUI' -e 'MiuiCamera' -e 'Leica' -e 'Hasselblad' -e '竞品' -e '参考' -e '学习' public/teotis-camera --glob '!public/teotis-camera/.git/objects/**'
```

## Evidence Required

- `output/01-public-exposure-inventory/report.md`
- exact commands and outputs summary
- list of files/commits/metadata surfaces inspected
- severity table
- remediation recommendations by package

## Unlock Condition

Mark completed only when the report clearly separates confirmed leaks from allowed/ambiguous hits.
