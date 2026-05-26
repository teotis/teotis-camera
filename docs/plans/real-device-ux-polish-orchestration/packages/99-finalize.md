# 99 Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Real Device UX Polish orchestration by validating package evidence, merging functional package branches into an integration branch, running integration verification, merging verified integration back to `main`, writing `FINAL_REPORT.md`, and cleaning up only local branches/worktrees recorded by this orchestration after every prior step succeeds.

## Owner

Finalization agent launched by `launchers/orchestrate.sh finalize` or by `advance` after all functional packages are complete.

## Dependencies

All functional packages:

- `00-mode-entry-visibility`
- `03-quick-panel-outside-dismiss`
- `05-dev-log-storage-governance`
- `01-style-copy-noise-cleanup`
- `02-settings-third-level-navigation`
- `04-persistence-reset-unification`

## Authorization

`99-finalize` is authorized to:

- Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
- Inspect package branches, worktrees, commits, and diffs.
- Create or update integration branch `agent/real-device-ux-polish/integration`.
- Merge functional package branches in the order defined by `INDEX.md`.
- Run integration verification.
- Merge verified integration back to `main` by local non-force merge.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Mark `status/state.tsv` row for `99-finalize` as `finalizing`, then `finalized` on success or `blocked` on failure.
- Delete only local package branches/worktrees recorded in `package-graph.tsv` and `state.tsv`, and only after integration verification and mainline merge succeed.

## Forbidden Without Explicit User Approval

- Force-push.
- Hard reset.
- Delete remote branches.
- Delete local branches/worktrees not recorded by this orchestration.
- Delete or overwrite unrelated dirty changes.
- Add secrets, credentials, network dependencies, or external API calls.
- Mark completion if real-device smoke remains required without recording it as residual risk.

## Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete
   - branch, worktree, base commit, commit hash recorded
   - verification commands passed or failure is explicitly justified
   - coordinator Markdown status and `state.tsv` agree
3. Stop if any functional package is not `completed`.
4. Create or update integration branch `agent/real-device-ux-polish/integration` from `main`.
5. Merge functional package branches in this order:
   - `00-mode-entry-visibility`
   - `03-quick-panel-outside-dismiss`
   - `05-dev-log-storage-governance`
   - `01-style-copy-noise-cleanup`
   - `02-settings-third-level-navigation`
   - `04-persistence-reset-unification`
6. Stop and record conflicts without cleaning anything if a merge conflict occurs.
7. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest --tests com.opencamera.core.mode.ModeProductDeclarationTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.DevLogRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

8. Run invalid-copy grep:

```bash
rtk rg -n "镜头|调整所选|打开可编辑的自定义副本" app/src/main app/src/test core/settings/src/main core/settings/src/test
```

9. Merge verified integration branch back to `main` by local non-force merge.
10. Write `FINAL_REPORT.md` with:
    - per-package acceptance status
    - merge summary
    - verification summary
    - invalid-copy grep result
    - cross-package conflict report
    - real-device smoke checklist
    - cleanup results
11. Write `status/99-finalize.md`.
12. Delete only recorded local package worktrees/branches after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

## Final Real-Device Smoke Checklist

- Humanistic and Portrait are visible and tappable.
- Style entry reads Style/风格, not Lens/镜头.
- Selected filter does not show meaningless copy.
- Settings Portrait/Watermark enters third-level pages directly.
- Quick dismisses on outside tap and does not trigger capture/focus/mode.
- Reset appears and restores defaults on Settings/Style/Color Lab/Quick.
- Dev logs cap at 20MB and cleanup by type works.
