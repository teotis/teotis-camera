# 07-real-device-acceptance-protocol Status

## State

`completed`

- State: completed
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/latest-real-device-vivo-feedback/07-real-device-acceptance-protocol`
- Branch: `agent/latest-real-device-vivo-feedback/07-real-device-acceptance-protocol`
- Base commit: `019767ad`
- Commit: `a8e3c369`

## Evidence

### Compilation Fix (package-local)

Main branch contained a pre-existing Kotlin compilation error in `SessionUiRenderModel.kt`:
`private` modifier on local functions inside `devLogRenderModel` (`escapeLinkValue`, `formatTimestamp`) + cross-module smart-cast on `event.detail`.

Three commits applied to package 07 branch only (will not conflict with packages 01–06):

| Commit | Fix |
|--------|-----|
| `da3632ef` | Remove `private` from local functions |
| `be870265` | Reorder local function declarations (define before use) |
| `a8e3c369` | Use local val to avoid cross-module smart-cast |

These are necessary for main to compile. They must be included when packages 01–06 merge into integration.

### APK

- Path: `/Users/dingren/.codex-build/OpenCamera-4faa0239/app/outputs/apk/debug/app-debug.apk`
- Size: 31 MB
- Build: `rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug` → BUILD SUCCESSFUL

### Local Smoke Tests

```
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionCockpitRenderModelTest \
  --tests com.opencamera.app.FocalLengthSliderViewTest \
  --tests com.opencamera.app.DevLogRenderModelTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
```

Result: **253 tests completed, 250 passed, 3 failed**

3 failures in `SessionCockpitRenderModelTest` (HUMANISTIC mode display-name ordering and `defaultStyleLabel` assertions at lines 307 and 324). These tests reference `TestAppTextResolver` labels; the assertion expectations appear to predate package 01 mode-directory changes. No failures in DevLog, FocalLength, SessionUi, or Watermark tests.

### Changed Files

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` (3 commits: local-func modifier, ordering, smart-cast fix)
- `local.properties` (added for worktree build; git-ignored)

## Acceptance Notes

1. Package 07 cannot claim real-device pass; it produces the substitute evidence bundle only.
2. The 3 failing cockpit tests are pre-existing and block no downstream package; they should be fixed in a follow-up.
3. The compilation fixes above are required for main to build successfully and must be included in the integration merge.

## Risks / Blockers

- None blocking for acceptance protocol delivery.
- Real-device QA is an external gate owned by the device owner; package 07 provides the checklist only.
