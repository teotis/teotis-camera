# Final Integration Audit

## Context
- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/INDEX.md`
- Packages:
  - `01-shutter-data-boundary`
  - `02-watermark-mainline`
  - `03-zoom-scaleend-sync`
  - `99-integration-audit`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-hotfix-rework/status/*.md`

## Audit Steps
1. Read `INDEX.md` and all package docs.
2. Read all `status/<package-id>.md` files.
3. Run:
   ```bash
   rtk git status --short
   rtk git diff --stat
   rtk git log --oneline -15
   ```
4. For each package, check every acceptance criterion.
5. Run integration-level verification:
   ```bash
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
   rtk ./gradlew --no-daemon :app:assembleDebug
   ```
6. Check cross-package conflicts:
   - Did any agent edit a file it was not supposed to?
   - Are there duplicate implementations of the same behavior?
   - Do shutter rendering, watermark effects, and zoom UI changes conflict semantically?
7. Report: PASS / PARTIAL / FAIL with specific evidence for each gap.

## Evidence Required
- Per-package acceptance criteria status: met / unmet / unverifiable.
- Integration test results.
- Cross-package conflict report.
- Final recommendation: merge / fix-then-merge / do-not-merge.
