# 01-public-exposure-inventory Status

## State

`completed`

## Evidence

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/public-release-safety-audit/01-public-exposure-inventory`
- Branch: `agent/public-release-safety-audit/01-public-exposure-inventory`
- Base commit: `7b10569bf01ebb275397d8cba013a1574f748b35`
- Commit hash: none (read-only audit, no code changes)
- Changed files: `output/01-public-exposure-inventory/report.md` (new)
- Verification:
  - `git -C public/teotis-camera log --all --format='%H %an <%ae> %cn <%ce> %s'` → 5 commit 全部暴露 <REDACTED_EMAIL>
  - `grep -rn 'vivo\|厂商\|xiaomi' public/teotis-camera/ --exclude-dir=.git` → 3 处 test fixture
  - `file public/teotis-camera/docs/assets/*.jpg` → EXIF software 字段含 PD2509 (Vivo)
  - `git -C public/teotis-camera config --list --show-origin` → 无泄露（仅 remote origin）
  - `find public/teotis-camera -name '*.env' -o -name '*.key' -o -name '*.pem'` → 无
  - `git -C public/teotis-camera ls-files '._*' '.gradle/'` → 未追踪
- Risks: P0 Git history identity leak requires force push after filter-repo; old forks may retain original identity
