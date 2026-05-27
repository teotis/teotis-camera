# 05-real-device-acceptance Status

- **Status**: completed
- **Agent**: shutter-feedback-watermark-05-real-device-acceptance
- **Branch**: `agent/shutter-feedback-watermark/05-real-device-acceptance`
- **Worktree**: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-05-real-device-acceptance`
- **Base commit**: `a8d621fb80e808254e9c301429571278309434ee`
- **Commit hash**: (no code changes — QA-only package)
- **Changed files**: none
- **Verification**: `./scripts/run_isolated_gradle.sh :app:assembleDebug` BUILD SUCCESSFUL; `:app:testDebugUnitTest` 838 passed, 2 pre-existing failures (MaskAwarePortraitRenderMathTest mask alpha precision, PhotoAlgorithmPostProcessorTest unsupported profile handling)
- **Evidence**:
  - **Device**: none connected (`adb devices` empty)
  - **APK**: `/Users/dingren/.codex-build/OpenCamera-d73f9ae3/app/outputs/apk/debug/app-debug.apk` (30.4 MB)
  - **Build command**: `rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug`
  - **Final decision**: `NOT_RUN_DEVICE_UNAVAILABLE`
  - **QA protocol**: see below
- **Risks / follow-up**:
  - Two pre-existing unit test failures unrelated to packages 02/03/04 (mask alpha math, photo algorithm unsupported profile)
  - Real-device acceptance cannot be completed without a connected vivo X300 or equivalent device

## QA Protocol (for when a device is available)

### Shutter Timing

| Scenario | Tap→Visual Feedback | Tap→Capture Started | Tap→Button Ready | Tap→Second Shot Accepted | Tap→Saved Thumbnail | Freeze/Missed Tap | Duplicate Capture |
|---|---|---|---|---|---|---|---|
| Ordinary photo, watermark off | | | | | | | |
| Ordinary photo, selected overlay watermark | | | | | | | |
| Ordinary photo, selected expanded-frame watermark | | | | | | | |
| Rapid two-shot after first button returns | | | | | | | |
| Live/multi-frame capture (conservative blocking) | | | | | | | |

**Target**: visual press feedback < 100ms, ordinary re-ready at data boundary (not final save), second tap accepted cleanly or rejected with truthful reason.

### Watermark Preview Checklist

| Template | Selector Shows Preview | Placement Change Moves Preview | Opacity Change Visible | Frame/Border Distinct | Saved Photo Matches | Preview Readable |
|---|---|---|---|---|---|---|
| classic-overlay | | | | N/A | | |
| travel-polaroid | | | | N/A | | |
| retro-frame | | | | Yes | | |
| pure-text | | | | N/A | | |
| blur-four-border | | | | Yes | | |
| professional-bottom-bar | | | | N/A | | |

### Build/Install Path

```bash
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
# APK: ~/.codex-build/OpenCamera-<hash>/app/outputs/apk/debug/app-debug.apk
rtk adb install -r <apk-path>
```
