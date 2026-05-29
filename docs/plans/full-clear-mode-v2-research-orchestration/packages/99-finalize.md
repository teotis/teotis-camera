# Package 99 - Finalize Full Clear V2 Research

## Goal

Verify package evidence, merge functional package branches into `agent/full-clear-mode-v2-research/integration`, run docs verification, merge back to `main` only if allowed, and write the final report.

## Required Work

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh verify-finalize
```

3. Verify every functional package:
   - acceptance criteria addressed,
   - changed files are within allowed paths,
   - evidence pack complete,
   - branch, worktree, base commit, commit hash recorded,
   - verification commands passed or failures are explicitly justified.
4. Check Capability Preflight. Real-device QA is external-assist and final product confidence only unless the user explicitly changes it to release-blocking.
5. Create or update `agent/full-clear-mode-v2-research/integration`.
6. Merge packages in the declared Merge Strategy order.
7. Stop and record conflicts without cleaning anything.
8. Run docs verification:

```bash
rtk bash -lc 'rg -n "Full Clear|V2|supported|degraded|unsupported|external-assist" docs/plans/full-clear-mode-v2-research-orchestration'
```

9. Merge integration branch back to `main` only after verification passes.
10. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
11. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion.
- Also update the coordinator ledger with `mark-state 99-finalize blocked ...`.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

