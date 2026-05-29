# Protected Paths

These paths are explicitly out of scope for worktree cleanup. No package in this orchestration may delete, move, prune, or repair them.

| Path | Size | Reason |
|---|---|---|
| `/Volumes/Extreme_SSD/project/open_camera` | 82G | Main checkout, working repository root |
| `/Volumes/Extreme_SSD/project/open_camera/public/teotis-camera` | 1.4G | Protected public repository governed by `scripts/PUBLIC_VERSION_RULES.md` |
| `/Volumes/Extreme_SSD/project/open_camera/docs/plans` | 1.2G | Canonical planning home, must remain discoverable per AGENTS.md |
| `/private/tmp/open_camera-orchestration/repo-hygiene-session-test-docs-archive/*` | — | Current orchestration worktrees (this cleanup session, Wave 1) |
| `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/*` | — | Recent active orchestration worktrees |

## Notes

- The orchestration package worktrees under `/private/tmp/open_camera-orchestration/` are explicitly not cleanup targets, as stated in the package doc.
- `public/teotis-camera` has its own version rules and must not be touched by cleanup agents.
- `docs/plans/` contains active planning material and indexed links; it must not be deleted even though it is 1.2G. The `03-docs-plan-taxonomy-and-retention` package handles archive policy separately.
