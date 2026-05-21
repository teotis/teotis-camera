# Mode Track Legibility And Hit Targets Plan

> **For agentic workers:** This is a non-multimodal interaction handoff. Implement text size, contrast, hit-target, and touch arbitration changes with tests. Use `rtk` for shell commands.

**Goal:** Make the mode track easier to read and more reliable to tap, with fewer false positives during horizontal scrolling.

## Problem Statement

The real-device feedback says:

- mode text is not prominent enough,
- the mode bar click behavior is not sensitive/accurate,
- accidental touches happen frequently.

This points to two separate root causes:

- visual legibility of mode labels,
- touch handling arbitration between chip taps and horizontal scrolling.

## Current Code Facts

- `modeTrackScroll` is a `HorizontalScrollView`.
- Mode buttons use `Widget.OpenCamera.ModeTrackButton`, `wrap_content`, and `8dp` gaps.
- `renderModeTrack()` changes text color, typeface, alpha, and active background.
- `bindModeTrack()` installs a custom `OnTouchListener`.
- The listener uses `20dp` touch slop, tracks raw coordinates, returns `false` from `ACTION_MOVE`, and dispatches mode change on `ACTION_UP` if it did not scroll.

## Required Behavior

- Active mode label must be clearly distinguishable at a glance.
- Inactive labels must remain readable, not overly faded.
- Each mode chip must meet at least `48dp` effective height and a forgiving horizontal tap area.
- Horizontal scroll should not dispatch a mode switch.
- A tap should dispatch only when pointer down/up both target the same chip and movement is below slop.
- Touch logic should not fight the parent `HorizontalScrollView`.

## Implementation Plan

### Step 1: Strengthen mode label style

Update `Widget.OpenCamera.ModeTrackButton` or per-button attributes:

- min height: `48dp`
- min width: at least `64dp` for Chinese two-character labels, `72dp` if space permits
- text size: increase by 1-2sp from current if below readable camera UI size
- active state:
  - bold,
  - full alpha,
  - clearer background/underline/indicator
- inactive state:
  - alpha no lower than `0.82f` unless disabled,
  - stronger contrast than current `0.78f` if real-device feedback still says faint.

Keep labels short:

- `拍照`
- `文档`
- `风景`
- `人文`
- `人像`
- `专业`
- `视频`

### Step 2: Replace fragile touch listener with click + scroll guard

Preferred low-risk approach:

- Let each button use `setOnClickListener`.
- Track scroll state on `modeTrackScroll`.
- Suppress click only when a real scroll gesture occurred.

If keeping `OnTouchListener`, make it internally consistent:

- return `true` for the full down/move/up sequence once intercepted,
- call `v.parent.requestDisallowInterceptTouchEvent(true)` only while deciding tap,
- cancel tap once horizontal movement exceeds slop,
- do not dispatch if pointer exits the chip bounds by more than a small tolerance.

Avoid returning `false` on move after consuming down; this can create inconsistent parent/child arbitration.

### Step 3: Add larger invisible hit slop if needed

If visual chips must remain compact, wrap each button in a container with min `48dp` height and sufficient width. Do not make the text itself tiny to fit.

Acceptance:

- chip visual can stay camera-like,
- actual touch target is at least `48dp x 48dp`,
- adjacent chips have enough gap or non-overlapping hit rects.

### Step 4: Keep auto-scroll predictable

`renderModeTrack()` currently calls `modeTrackScroll.smoothScrollTo()` after each render. This can feel like the bar moves under the user's finger.

Adjust behavior:

- auto-scroll only when active mode changes,
- do not smooth-scroll while user is dragging,
- center the active chip when possible instead of always using `left - 48dp`.

### Step 5: Tests

Add or update tests for:

- render model item labels are short and stable,
- active mode produces active visual state data if style is modeled,
- touch policy function dispatches on tap below slop,
- touch policy does not dispatch after horizontal movement above slop,
- auto-scroll target is stable and clamped.

If touch policy is currently embedded in `MainActivity`, extract a pure helper:

```kotlin
internal data class ModeTrackTouchDecision(
    val shouldDispatchClick: Boolean,
    val shouldTreatAsScroll: Boolean
)
```

This makes the false-tap fix testable without instrumentation.

## Files To Inspect Or Modify

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/drawable/bg_mode_track_active.xml`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- Optional new test: `app/src/test/java/com/opencamera/app/ModeTrackTouchPolicyTest.kt`

## Non-Goals

- Do not change the mode catalog or mode plugin architecture.
- Do not implement gesture-based mode switching in this pass.
- Do not increase label verbosity.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

If a touch policy test is added:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ModeTrackTouchPolicyTest
```

Then:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```
