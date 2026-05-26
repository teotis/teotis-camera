# Package 99 — Integration Audit

## Package ID
`99-integration-audit`

## Goal
Validate the completed packages against the original real-device issues and ensure the merged implementation is coherent, verified, and within Stage 7 scope.

## Scope
- Re-read `INDEX.md`, all package docs, and all `status/*.md` files.
- Check current git status, recent commits, and file ownership violations.
- Compare each package delivery against every acceptance criterion.
- Run final integration verification.
- Fix only tiny, obvious, scope-contained integration omissions. Do not expand product scope.

## Acceptance Criteria
- [ ] All implementation packages have completed status files with evidence packs.
- [ ] Package 01 proves the shutter animation stops at a real data boundary or honestly documents a CameraX limitation.
- [ ] Package 02 proves watermark effect/UI wiring is present on current `main`.
- [ ] Package 03 proves consecutive pinch zoom uses the correct base and the bottom focal-length UI synchronizes.
- [ ] No package edited forbidden paths without explanation.
- [ ] Final verification commands pass or failures are clearly classified as pre-existing/unrelated.
- [ ] Final report returns PASS, PARTIAL, or FAIL with evidence.

## Verification Commands
```bash
rtk git status --short
rtk git log --oneline -15
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Output
- PASS if all acceptance criteria are met and verification passes.
- PARTIAL if one or more non-blocking criteria remain unmet or require real-device visual confirmation.
- FAIL if a package is missing, unverified, semantically wrong, or causes blocking regressions.
