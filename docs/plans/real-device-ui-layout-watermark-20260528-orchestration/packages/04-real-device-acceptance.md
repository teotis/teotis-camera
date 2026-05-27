# Package 04 - Real Device Acceptance

## Package ID

`04-real-device-acceptance`

## Goal

Run the final acceptance protocol for the three latest real-device UI issues after implementation packages land. This package is manual/device-sensitive: local tests can support it, but screenshot/device judgment must stay explicit.

## Branch And Worktree

- Branch: `agent/real-device-ui-layout-watermark-20260528/04-real-device-acceptance`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-04-real-device-acceptance`

## Allowed Paths

- `docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/04-real-device-acceptance.md`
- `docs/plans/real-device-ui-layout-watermark-20260528-orchestration/scratch/04-real-device-acceptance/**`
- No production source changes unless the package records a blocker and the user explicitly approves a repair follow-up.

## Forbidden Paths

- Do not claim real-device visual acceptance from desktop/unit tests alone.
- Do not edit implementation files as part of QA unless explicitly approved after recording the blocker.
- Do not edit another package status file or `INDEX.md`.

## Dependencies

- `01-preview-frame-containment`
- `02-bottom-cockpit-density`
- `03-quick-watermark-cycle`

## Required Work

1. Inspect implementation package status files and commits.
2. Run or verify the integrated debug APK build path.
3. Record the install command and APK path if the build succeeds.
4. On device, verify:
   - frame guide is contained within live preview for 4:3, 16:9, and 1:1;
   - bottom deck/mode strip feels compact and no controls overlap;
   - Quick panel Watermark row cycles through the available templates and the live watermark hint/next capture reflect the selected template;
   - no regression in zoom slider, shutter, thumbnail, lens switch, right rail, top chips, or panel dismissal.
5. Attach screenshot paths or describe the concrete device evidence in the status file.

## Verification Commands

Run from the assigned worktree or main checkout as appropriate:

```bash
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

If installing on the connected device is available:

```bash
adb install -r -d /Users/dingren/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
```

If the APK path differs because the worktree uses isolated build output, record the actual path.

## Expected Evidence

- Worktree path, branch, base commit, commit hash if any.
- APK path and timestamp.
- Device model/Android version if available.
- Screenshot paths or concrete notes for each issue.
- Pass/fail judgment for issue 2, issue 3, and issue 4 separately.
- Residual risks or blocker details.

## Unlock Condition

`99-finalize` may start after this package records completed status or a user-approved waiver is explicitly recorded.
