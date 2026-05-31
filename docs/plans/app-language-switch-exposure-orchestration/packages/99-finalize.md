# Package: 99-finalize - App Language Switch Integration

## Mission

Integrate the completed package branches, verify the final behavior, and decide the task-level outcome using the INDEX Landing Strategy.

## Allowed Paths

- `docs/plans/app-language-switch-exposure-orchestration/FINAL_REPORT.md`
- `docs/plans/app-language-switch-exposure-orchestration/status/99-finalize.md`
- Coordinator status/state files for this plan through `launchers/orchestrate.sh`

Runtime source changes are forbidden in finalize except conflict resolution during merges.

## Required Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all package status files, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh verify-finalize
```

3. Verify each functional package:
   - state is `completed`
   - branch and commit hash are recorded
   - changed files are within allowed paths
   - verification evidence is complete
   - worktree is clean or dirty state is recorded as a blocker
4. Check Capability Preflight and Landing Strategy.
5. Create/update integration branch `agent/app-language-switch-exposure/integration`.
6. Merge package branches in order:
   - `01-language-persistence-contract`
   - `02-settings-language-entry`
   - `03-language-switch-verification`
7. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionUiRenderContractsTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

8. If integration verification passes and no release-blocking external gate remains, merge the integration branch back to `main`.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
10. Mark `99-finalize` as `finalized` only after all merge, verification, report, and cleanup steps succeed.

## External Gate Handling

`real-device-language-switch-qa` is release-confidence only. If local verification passes but no physical-device evidence exists, the task outcome may still be `landed`; the final report must say real-device QA remains pending and include the APK/install/checklist produced by package 03.

## Failure Rules

- Any merge conflict, missing evidence, forbidden path change, failed verification, or status mismatch sets this package to `blocked`.
- Preserve branches/worktrees on failure.
- Do not merge anything if the outcome is `failed-no-merge`.
- If only `01-language-persistence-contract` is valid as an independent merge candidate, list it as `failed-with-candidate-independent-fixes` and do not merge unless the INDEX criteria are satisfied.
