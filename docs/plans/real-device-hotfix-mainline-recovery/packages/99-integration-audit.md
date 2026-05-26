# Package 99 — Integration Audit

## Package ID
`99-integration-audit`

## Goal
Verify that watermark recovery, zoom ScaleEnd recovery, and shutter visual closure are present on current `main` and satisfy the original real-device hotfix requirements.

## Audit Steps
1. Read `INDEX.md`, all package docs, and all `status/*.md` files.
2. Check `git status --short`, `git diff --stat`, and recent `git log --oneline`.
3. For each package, check every acceptance criterion.
4. Verify current main code, not only side worktrees.
5. Run final integration verification commands.
6. Check file ownership violations and semantic conflicts.
7. Output PASS / PARTIAL / FAIL.

## Acceptance Criteria
- [ ] Package 01 watermark changes are present in current main and verified.
- [ ] Package 02 ScaleEnd fix is present in current main and verified.
- [ ] Package 03 shutter visual behavior is present in current main and verified.
- [ ] All implementation packages wrote evidence to their own status files.
- [ ] Final verification passes or remaining failures are classified as unrelated/pre-existing with evidence.

## Verification Commands
```bash
rtk git status --short
rtk git diff --stat
rtk git log --oneline -15
rtk rg -n "WatermarkEffect" feature/mode-humanistic feature/mode-portrait feature/mode-night feature/mode-pro feature/mode-document feature/mode-photo
rtk rg -n "ScaleEnd|resetZoomAccumulation|lastPinchTimestamp" app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```
