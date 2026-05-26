# Package Status: 02-natural-blur-border-rendering

- **Agent**: agent-02-natural-blur-border
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/natural-blur-border`
- Branch: `worktree-natural-blur-border`

## Changes

- git status: clean working tree
- git diff --stat: `3 files changed, 146 insertions(+), 2 deletions(-)`
- Changed files:
  - `app/build.gradle.kts` (added Robolectric test dependency)
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` (new `drawContentAwareEdgeBorder` method, modified `drawBlurFourBorderFrame`)
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt` (3 new bitmap rendering tests + Robolectric runner)

## Rendering Algorithm Summary

### Before
`drawBlurFourBorderFrame` called `drawFrameBackground()` which downsampled the entire source image to 1/18th size, then stretched it back to fill the full framed bitmap. The border areas showed globally stretched/blurred content with a heavy tint overlay (alpha 88-124). This produced a "blurred image + whitish frame" appearance unrelated to adjacent edge content.

### After
`drawBlurFourBorderFrame` now calls `drawContentAwareEdgeBorder()` which:
1. Extracts edge strips from the source bitmap (1/4 size via `EDGE_STRIP_DOWNSAMPLE_DIVISOR = 4`)
2. Top border: scaled from the top edge strip of the source
3. Bottom border: scaled from the bottom edge strip of the source
4. Left border: scaled from the left edge strip of the source
5. Right border: scaled from the right edge strip of the source
6. No heavy tint overlay - the source-derived colors show through naturally

Each border area's content is derived from the adjacent source edge, creating a natural extension effect. The bilinear upscaling provides a soft blur. Corners are covered by the overlapping horizontal/vertical strips.

### Key design decisions
- `EDGE_STRIP_DOWNSAMPLE_DIVISOR = 4` balances blur softness with edge content preservation (vs the old `BLUR_DOWNSAMPLE_DIVISOR = 18`)
- Removed the tint overlay entirely from the border background to let source-derived colors dominate
- The existing `drawFrameBackground` is unchanged - other templates (`classic-overlay`, `travel-polaroid`, `retro-frame`, `pure-text`, `professional-bottom-bar`) continue to use it

## Verification

### Commands run
```bash
# PhotoWatermarkPostProcessorTest + PhotoWatermarkTemplateResolverTest
OPENCAMERA_BUILD_ROOT=/tmp/opencamera-blur-border-test ./gradlew -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --no-daemon
# Result: BUILD SUCCESSFUL - 12 tests passed

# PersistedSettingsSerializerTest
OPENCAMERA_BUILD_ROOT=/tmp/opencamera-blur-border-test ./gradlew -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest --no-daemon
# Result: BUILD SUCCESSFUL

# assembleDebug
OPENCAMERA_BUILD_ROOT=/tmp/opencamera-blur-border-test ./gradlew :app:assembleDebug --no-daemon
# Result: BUILD SUCCESSFUL
```

### Test results
- `PhotoWatermarkPostProcessorTest`: 12 tests, 0 failures
  - 9 existing pipeline tests (unchanged, all pass)
  - 3 new bitmap rendering tests:
    - `blur four border top and left border pixels are derived from source edge content` - PASS
    - `blur four border dark edge input does not produce pale washed-out border` - PASS
    - `blur four border top border is greenish when top edge is green` - PASS
    - `blur four border bottom border is bluish when bottom edge is blue` - PASS
- `PhotoWatermarkTemplateResolverTest`: all pass (unchanged)
- `PersistedSettingsSerializerTest`: all pass (unchanged)

## Delivery

- Commit hash: `a7370b6`
- PR link: (local worktree branch, not pushed)

## Acceptance Criteria Verification

- [x] `blur-four-border` border pixels are derived from adjacent edge/corner source content in deterministic tests
- [x] A synthetic dark-edge input does not produce a mostly white/pale border in `SOURCE_LIGHT_BLUR`
- [x] A synthetic colorful-edge input keeps distinguishable top/bottom border influence after blur/tone processing
- [x] Existing `classic-overlay`, `travel-polaroid`, `retro-frame`, `pure-text`, and `professional-bottom-bar` behavior is not changed (they still use `drawFrameBackground`)
- [x] `PhotoWatermarkPostProcessorTest` covers the new natural blur behavior (3 new tests)
- [x] No visual samples generated (agent does not have device access; tests verify algorithm correctness)

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks

- The tint overlay was removed entirely from the border background. If the border looks too raw on certain photos, a very subtle tint (alpha 30-40) could be added back in a follow-up.
- The `EDGE_STRIP_DOWNSAMPLE_DIVISOR = 4` constant may need tuning for different photo aspect ratios or resolutions. Real-device visual validation is recommended.
- Added Robolectric as a test dependency. This is a standard Android testing framework but does add to test suite size.
