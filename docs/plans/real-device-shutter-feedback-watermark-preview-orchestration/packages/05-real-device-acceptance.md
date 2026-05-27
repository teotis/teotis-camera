# Package 05 - Real Device Acceptance

## Package ID

`05-real-device-acceptance`

## Goal

Produce and, if a device is available, run the final vivo X300 real-device acceptance protocol for shutter recovery feel and watermark template preview. This package must not replace device evidence with desktop tests.

## Dependencies

- Wait for `02-shutter-fast-feedback-runtime`.
- Wait for `03-shutter-visual-state-and-qa`.
- Wait for `04-watermark-template-preview`.

## Branch And Worktree

- Branch: `agent/shutter-feedback-watermark/05-real-device-acceptance`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-05-real-device-acceptance`
- Base: latest `main` plus implementation package results, or final integration branch if provided.

## Allowed Paths

- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/05-real-device-acceptance.md`
- Coordinator state file row for `05-real-device-acceptance`
- Optional QA notes under `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/`

## Forbidden Paths

- Runtime source edits
- Test source edits
- Other package status files
- `INDEX.md`
- Final PASS claims without real-device evidence

## Acceptance Protocol

### Build

Use the integrated result:

```bash
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

### Shutter Timing

Record with screen capture or high-frame-rate external camera if available:

- ordinary photo, watermark off,
- ordinary photo, selected overlay watermark,
- ordinary photo, selected expanded-frame watermark,
- rapid two-shot attempt after first button returns,
- Live or multi-frame capture if available, to confirm conservative blocking remains.

Record these observed times:

- tap to visible press feedback,
- tap to capture started feedback,
- tap to button visually ready,
- tap to second shot accepted,
- tap to saved thumbnail/update,
- any freeze, missed tap, or duplicate unsafe capture.

Suggested target:

- visual press feedback should be immediate enough to feel under 100 ms,
- ordinary visual re-ready should occur at data boundary, not final save,
- second ordinary tap should either be accepted cleanly or rejected with truthful reason; no silent dead tap.

### Watermark Preview

For every built-in template:

- `classic-overlay`
- `travel-polaroid`
- `retro-frame`
- `pure-text`
- `blur-four-border`
- `professional-bottom-bar`

Check:

- opening selector/detail shows template preview on live surface,
- changing placement moves the preview,
- changing opacity visibly changes preview strength,
- frame/four-border templates are visually distinct from simple text,
- saved photo broadly matches chosen template and placement,
- preview does not occlude critical controls or become unreadable.

## Acceptance Criteria

- [ ] Local build/install path is documented.
- [ ] Shutter timing table is filled or clearly marked device unavailable.
- [ ] Watermark preview checklist is filled for every built-in template or clearly marked device unavailable.
- [ ] Any remaining issue is classified as blocker, follow-up, or accepted limitation.
- [ ] No desktop-only evidence is described as final real-device PASS.

## Evidence Required

- Device model and OS/build if tested.
- APK/build command summary.
- Timing table.
- Watermark visual checklist.
- Screenshots/video paths if available.
- Final decision: `PASS`, `PARTIAL`, `BLOCKED`, or `NOT_RUN_DEVICE_UNAVAILABLE`.
