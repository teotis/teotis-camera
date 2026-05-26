# Final Integration Audit

## Context
- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`
- Packages:
  - `01-watermark-mainline-recovery`
  - `02-zoom-scaleend-mainline-recovery`
  - `03-shutter-visual-closure`
  - `99-integration-audit`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-mainline-recovery/status/*.md`

## Audit Steps
1. Read `INDEX.md` and all package docs.
2. Read all package status files.
3. Run:
   ```bash
   rtk git status --short
   rtk git diff --stat
   rtk git log --oneline -15
   ```
4. For each package, check every acceptance criterion.
5. Confirm current main code, not only side worktrees:
   ```bash
   rtk rg -n "WatermarkEffect" feature/mode-humanistic feature/mode-portrait feature/mode-night feature/mode-pro feature/mode-document feature/mode-photo
   rtk rg -n "ScaleEnd|resetZoomAccumulation|lastPinchTimestamp" app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt
   ```
6. Run integration-level verification:
   ```bash
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
   rtk ./gradlew --no-daemon :app:assembleDebug
   ```
7. Check cross-package conflicts:
   - Did any agent edit a file it was not supposed to?
   - Did the package update only its own status file?
   - Are there duplicate or reverted implementations?
8. Report PASS / PARTIAL / FAIL with concrete evidence.

## Evidence Required
- Per-package acceptance status.
- Integration verification output summary.
- Cross-package conflict report.
- Final recommendation: merge / fix-then-merge / do-not-merge.
