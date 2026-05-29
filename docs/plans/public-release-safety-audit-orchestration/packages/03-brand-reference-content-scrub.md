# 03-brand-reference-content-scrub

## Goal

Remove or neutralize public content that exposes competitor-learning traces, private design references, specific real-device testing context, or unnecessary brand/vendor names.

## Dependencies

- `01-public-exposure-inventory`

## Allowed Paths

Main-repo reporting:

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/output/03-brand-reference-content-scrub/**`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/03-brand-reference-content-scrub.md`

Public repo content, if edits are needed:

- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/README.md`
- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/README_EN.md`
- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/NOTICE`
- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/AUTHORS`
- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/docs/assets/**`
- `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/app/src/test/**`
- source comments only when they contain public-risk wording

## Forbidden Paths And Actions

- Do not change runtime behavior unless a test fixture literally encodes private or competitor-reference data.
- Do not delete public assets without replacing README references.
- Do not push to public remote.
- Do not rewrite public history.

## Work

1. Start from `output/01-public-exposure-inventory/report.md`.
2. For each competitor/reference hit, decide:
   - delete internal process wording
   - replace concrete brand/model fixture with neutral sample data
   - keep only if it is a neutral compatibility/API/platform term with written justification
3. Scrub README/README_EN and public-facing docs so they describe Teotis features directly, not who/what inspired them.
4. Replace test fixture values such as concrete vivo/X 系列 model names with neutral examples unless they are explicitly part of a public compatibility claim.
5. Check asset filenames, alt text, and metadata fallbacks.

## Verification

Run and record:

```bash
rtk rg -n --fixed-strings -e 'Apple' -e '参考设备' -e 'vivo' -e '厂商' -e '厂商系统' -e '参考相机应用' -e '品牌联名' -e '品牌联名' -e '行业' -e '参考' -e '学习' public/teotis-camera --glob '!public/teotis-camera/.git/objects/**'
rtk git -C public/teotis-camera status --short --branch
```

If allowed hits remain, list each one with file, line, and reason.

## Evidence Required

- `output/03-brand-reference-content-scrub/report.md`
- before/after hit table
- changed public repo files
- remaining allowed hits with justification

## Unlock Condition

Completed when public-facing content no longer contains unreviewed competitor-learning traces and remaining brand/vendor terms are explicitly justified.
