# 02-bottom-cockpit-density Status

## State

`completed`

## Evidence

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-02-bottom-cockpit-density`
- Branch: `agent/real-device-ui-layout-watermark-20260528/02-bottom-cockpit-density`
- Base commit: b131f81
- Commit hash: 4b76ed2
- Changed files: app/src/main/res/values/dimens.xml, app/src/main/res/layout/activity_main.xml
- Verification: SessionCockpitRenderModelTest: passed; SessionUiRenderModelTest: passed; assembleDebug: BUILD SUCCESSFUL

## Changes Summary

Before → After:
- Mode track vertical padding: 2dp → 1dp
- Bottom sheet top padding: 2dp → 1dp
- Bottom sheet bottom padding: 8dp → 4dp
- Focal slider / recording indicator margin: 4dp → 2dp
- Main control row top margin: 2dp → 1dp
- Mode chip min height: 32dp → 28dp
- New dimen `space_1` (1dp) added

Total estimated vertical saving: ~10dp off the bottom deck.

## Notes

- Depends on `01-preview-frame-containment` so layout density work does not undo preview/frame containment.
- Preview/overlay bottom constraints still point to `modeTrackScroll` — unchanged by this package.
- Residual real-device visual QA remains required.
