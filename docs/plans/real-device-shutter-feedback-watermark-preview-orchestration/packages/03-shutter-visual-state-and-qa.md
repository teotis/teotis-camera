# Package 03 - Shutter Visual State And QA

## Package ID

`03-shutter-visual-state-and-qa`

## Goal

Make the shutter button feel fast and truthful after package 02's runtime semantics. This package owns visual feedback, render model behavior, and local QA checks, not device capture execution.

## Dependencies

- Wait for `01-shutter-latency-reference-diagnosis`.
- Wait for `02-shutter-fast-feedback-runtime`.

## Branch And Worktree

- Branch: `agent/shutter-feedback-watermark/03-shutter-visual-state-and-qa`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-03-shutter-visual-state-and-qa`
- Base: latest `main` plus package 02 result, or let `99-finalize` merge in order.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt` only if render plumbing requires it
- `app/src/main/res/drawable/**` only for shutter visual state assets
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/03-shutter-visual-state-and-qa.md`
- Coordinator state file row for `03-shutter-visual-state-and-qa`

## Forbidden Paths

- CameraX adapter runtime unless a tiny compile fix is required after package 02
- Watermark preview files
- Other package status files
- `INDEX.md`
- Purely decorative redesign that does not improve the slow-feel symptom

## Implementation Guidance

1. Separate visual feedback states:
   - immediate press/tap acknowledgment,
   - capture in progress before data boundary,
   - ready for next ordinary capture,
   - background save/postprocess indicator,
   - blocked special capture or recovery.
2. Avoid using a long disabled-looking state for background save/postprocess once session is ready.
3. Keep button color language consistent with prior user preference: avoid pure white large fill; use a slightly gray, low-weight selected/ready language.
4. If adding a transient press animation, make it visual-only and interruptible.
5. Add tests for render states. Do not require visual inspection as the only local proof.

## Acceptance Criteria

- [ ] Render tests prove ordinary still becomes visually ready at the runtime-safe boundary.
- [ ] Render tests prove background save/postprocess is represented without disabling the shutter.
- [ ] Render tests prove special/risky capture kinds remain disabled or blocked-looking.
- [ ] Active recording shutter remains visually and functionally stop-recording.
- [ ] Evidence includes a real-device smoke checklist for package 05.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Evidence Required

- Worktree path and branch.
- Base commit and final commit hash.
- Changed files and `git diff --stat`.
- Render-state before/after explanation.
- Test output summary.
- Statement that this is not final real-device PASS unless package 05 records device evidence.
