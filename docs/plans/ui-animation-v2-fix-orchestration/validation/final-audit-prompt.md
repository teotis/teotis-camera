# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/INDEX.md`
- Packages: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/packages/*.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-animation-v2-fix-orchestration/status/*.md`

## Audit Steps

1. Read `INDEX.md` and all package docs.
2. Read every `status/<package-id>.md` file.
3. Run `rtk git status --short`, inspect changed files, and check recent local commits/branches if available.
4. For each implementation package, mark every acceptance criterion as met, unmet, waived by user, or unverifiable.
5. Check file ownership:
   - Did any package edit a file or file section it was not assigned?
   - Did multiple packages implement duplicate render state or duplicate gesture handling?
   - Did any UI package introduce direct CameraX/device/session-kernel ownership?
6. Run integration-level verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

7. Perform visual/device QA when available for timing, touch feel, animation interruption, and Quick Panel semantics.
8. Fix only scope-contained, low-risk, obvious omissions. Do not expand product scope.
9. Report one of:
   - **PASS**: all required criteria and gates pass.
   - **PARTIAL**: non-blocking criteria remain; list each one.
   - **FAIL**: blocking acceptance, ownership, or verification failures remain.

## Evidence Required

- Per-package acceptance criteria status.
- Integration test results with commands.
- Cross-package conflict report.
- Final recommendation: merge, fix-then-merge, or do-not-merge.
- Remaining real-device or visual QA risks.
