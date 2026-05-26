# Git Worktree Hygiene And Status Repair

## Goal

Restore reliable git inspection for the main `open_camera` workspace so `rtk git status --short --branch` and `rtk git submodule status` do not fail because of stale nested worktrees or old gitdir pointers.

## Context

- User request: project may be confused after many external agents, especially git/test/build; inspect and package issues.
- Verified facts:
  - `rtk git status --short --branch` fails with `fatal: not a git repository: /Volumes/Extreme_SSD/project/codex_camera/.git/worktrees/orientation-adaptive-camera-ui`.
  - `rtk git status --short --branch --ignore-submodules` succeeds and shows many tracked/untracked changes, so the main repository is still readable.
  - `.claude/worktrees/orientation-adaptive-camera-ui/.git` contains `gitdir: /Volumes/Extreme_SSD/project/codex_camera/.git/worktrees/orientation-adaptive-camera-ui`.
  - Other `.claude/worktrees/*/.git` files point to `/Volumes/Extreme_SSD/project/open_camera/.git/worktrees/*` and are less suspicious.
  - `rtk git submodule status` fails with `fatal: no submodule mapping found in .gitmodules for path '.claude/worktrees/orientation-adaptive-camera-ui'`.
  - `.gitignore` ignores `.worktrees/` but not `.claude/worktrees/`.
- Relevant files:
  - `.gitignore`
  - `.claude/worktrees/orientation-adaptive-camera-ui/.git`
  - `.claude/worktrees/`
  - `.git/worktrees/`
- Non-goals:
  - Do not delete worktree directories silently.
  - Do not discard tracked or untracked user/agent changes.
  - Do not fix Kotlin test failures in this package.

## Implementation Scope

- Classify each nested worktree under `.claude/worktrees/` as active, stale, or archive-only.
- Decide the least destructive cleanup path for `orientation-adaptive-camera-ui`.
- Prevent nested agent worktrees from breaking main-repo status again.
- Update ignore rules or workspace convention only after verifying no needed handoff artifact becomes undiscoverable.

## Steps

1. Inspect nested worktrees:
   - `rtk find .claude/worktrees -maxdepth 2 -name .git`
   - `rtk git worktree list`
   - open each `.claude/worktrees/*/.git` and record whether the `gitdir` points inside current `open_camera` or old `codex_camera`.
2. For `.claude/worktrees/orientation-adaptive-camera-ui`:
   - if the directory is only a stale copy, ask the user before deleting or moving it;
   - if it contains needed work, convert/export it outside the main repo or repair it as a real worktree registered under current `.git/worktrees`.
3. Add `.claude/worktrees/` to `.gitignore` if the project intends agent worktrees to remain local-only.
4. If a `.gitmodules` file is introduced by any agent, make sure it is intentional; otherwise do not use submodules for local agent worktrees.
5. Re-run git inspection without `--ignore-submodules`.

## Acceptance Criteria

- `rtk git status --short --branch` runs without fatal errors.
- `rtk git submodule status` either reports no submodules cleanly or lists only intentional mappings from `.gitmodules`.
- Main-repo status no longer treats `.claude/worktrees/*` as accidental submodules.
- Existing user/agent changes are not deleted or reverted.
- Future external agents have a clear rule: use ignored worktree roots or registered worktrees, not nested stale `.git` pointers.

## Verification Commands

```bash
rtk git status --short --branch
rtk git submodule status
rtk git worktree list
rtk git status --short --branch --ignore-submodules
```

## Risks And Notes

- The directory `.claude/worktrees/orientation-adaptive-camera-ui` may contain real unmerged work. Treat cleanup as potentially destructive until inspected and approved.
- `git status --ignore-submodules` is only a diagnostic workaround, not the desired steady state.
- This should be fixed before additional implementation agents start merging test/build repairs, because normal status visibility is the guardrail for not overwriting each other.
