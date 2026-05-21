# Multimodal Deferred Work Plan

> **For agentic workers:** This file is not for non-multimodal implementation agents. Use it only when an agent can inspect screenshots, visual references, or real-device screen recordings.

**Goal:** Isolate work that needs visual judgment or image/video understanding from the implementation plans that can be executed by text-only agents.

---

## Why This Is Separate

The five implementation plans can be executed from code and text specs. Some validation, however, requires looking at real screens:

- Whether the first-launch preview failure leaves a black preview, frozen preview, or UI-only blocked state.
- Whether the zoom strip feels narrow and camera-like.
- Whether the frame mask matches what the user believes will be captured.
- Whether the palette is visually obvious.
- Whether the transient thumbnail looks like helpful feedback or like a fake saved image.

Those checks should not block text-only implementation agents.

## Deferred Multimodal Tasks

### 1. First-Launch Visual State Classification

Inputs:

- Real-device screen recording from fresh install through permission grant.
- Exported dev log text.

Output:

- Classify the failed state as one of:
  - permission UI stale state
  - preview host detached
  - CameraX bind attempted but no first frame
  - preview is active but covered by UI
  - unknown
- Provide screenshot timestamps and matching trace events.

### 2. Thumbnail Feedback Visual QA

Inputs:

- Screen recording of shutter press through saved thumbnail update.

Output:

- Time from shutter tap to transient feedback visible.
- Time from shutter tap to saved-media thumbnail visible.
- Confirm transient feedback is visually distinct from gallery-ready saved media.
- Confirm thumbnail tap before save does not open a preview snapshot.

### 3. Zoom Strip Visual Comparison

Inputs:

- Screenshots after implementing the zoom strip on at least 1080x1920 and one smaller viewport.
- Existing references in `codex/v2_ui/reference_images`.

Output:

- Check that zoom points are readable, narrow, and not button-heavy.
- Check that mode track and zoom strip touch zones do not visually overlap.
- Check active state and disabled state.

### 4. Preview Frame And Aspect Ratio Visual QA

Inputs:

- Screenshots for `4:3`, `16:9`, and `1:1`.
- Saved output images for each ratio.

Output:

- Confirm preview frame and final output match.
- Confirm bottom controls are outside the perceived capture frame when possible.
- Confirm grid lines align to the framed area or are intentionally full-preview.

### 5. Tone Palette Discoverability Review

Inputs:

- Screenshot of first screen.
- Screenshot after tapping `色调`.
- Screenshot after dragging palette and after toggling `进阶`.

Output:

- Confirm `色调` entry is visible without reading dev text.
- Confirm `调色板` is visible immediately after opening.
- Confirm palette affordance is recognizable as a two-axis color/tone surface.
- Confirm Chinese copy is product-facing and not implementation jargon.

## Suggested Artifacts

Place outputs under:

```text
codex/v2_ui/visual_reviews/
```

Suggested filenames:

```text
2026-05-22-first-launch-permission-preview-review.md
2026-05-22-thumbnail-feedback-review.md
2026-05-22-zoom-strip-review.md
2026-05-22-preview-frame-review.md
2026-05-22-tone-palette-review.md
```

## Non-Goals

- Do not ask multimodal agents to implement code from this file.
- Do not let screenshot taste override architecture boundaries.
- Do not require image generation for the text-only implementation plans.
