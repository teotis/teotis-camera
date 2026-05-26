# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`
- Packages:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/00-mode-entry-visibility.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/01-style-copy-noise-cleanup.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/02-settings-third-level-navigation.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/03-quick-panel-outside-dismiss.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/04-persistence-reset-unification.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/05-dev-log-storage-governance.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/99-integration-audit.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/*.md`

## Audit Steps

1. Read `INDEX.md` and all package docs.
2. Read all `status/<package-id>.md` files.
3. Run `rtk git status --short --untracked-files=all`, `rtk git diff --stat`, and recent git log.
4. For each package, check every acceptance criterion.
5. Run integration-level verification if the workspace is merge-clean enough:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest --tests com.opencamera.core.mode.ModeProductDeclarationTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.DevLogRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

6. Run copy cleanup grep:

```bash
rtk rg -n "镜头|调整所选|打开可编辑的自定义副本" app/src/main app/src/test core/settings/src/main core/settings/src/test
```

7. Check for cross-package conflicts:
   - Did any agent edit a file it was not assigned?
   - Did multiple agents create separate reset defaults or panel routing state?
   - Did Quick outside-dismiss accidentally interfere with capture/focus/mode gestures?
   - Did Dev log cleanup delete outside the debug-log directory?
8. Report: `PASS`, `PARTIAL`, or `FAIL` with specific evidence for each gap.

## Evidence Required

- Per-package acceptance criteria status: met / unmet / unverifiable.
- Integration test results.
- Invalid-copy grep result.
- Cross-package conflict report.
- Real-device smoke checklist:
  - Humanistic and Portrait visible/tappable.
  - Style label reads as Style/风格.
  - Selected filter does not show meaningless copy.
  - Settings Portrait/Watermark enters third-level pages directly.
  - Quick dismisses on outside tap.
  - Reset appears and restores defaults on Settings/Style/Color Lab/Quick.
  - Dev logs cap at 20MB and cleanup by type works.
- Final recommendation: merge / fix-then-merge / do-not-merge.
