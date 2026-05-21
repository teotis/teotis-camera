# Multimodal Deferred Visual Review

## Purpose

This document separates work that needs visual interpretation from work that text-only agents can implement. Non-multimodal agents should not block on this file. They should implement the Markdown specs in `00` through `06`.

## Deferred Items

### 1. Screenshot Annotation

Inputs:

- real device screenshots
- screen recordings
- generated reference images

Tasks:

- annotate overlap, wrapping, unsafe inset, and visual hierarchy issues
- mark touch target ambiguity
- compare current UI to 2.0 cockpit layout
- identify places where preview is obscured too much

Outputs:

- annotated PNGs under `codex/v2_ui/visual_review/`
- short Markdown finding list with file names and issue ids

### 2. Real Device Visual QA

Devices:

- small Android phone
- large Android phone
- device with display cutout
- device with gesture navigation
- at least one lower-end camera device if available

Scenarios:

- first launch with permission missing
- preview ready photo mode
- photo saving
- after saved thumbnail
- mode switch
- lens switch
- exact zoom chip taps
- Tone Lab open
- Lens Lab open
- recording active
- recording stopping
- Dev Log open in debug

Checks:

- safe-area correctness
- text wrapping
- tap target spacing
- panel outside-dismiss
- preview remains visible
- shutter state clarity
- thumbnail correctness

### 3. Reference Image Comparison

Compare implementation screenshots against:

- `reference_images/camera_cockpit_v2_main.png`
- `reference_images/camera_cockpit_v2_recording.png`
- `reference_images/tone_lab_v2_panel.png`
- `reference_images/lens_lab_v2_panel.png`
- `reference_images/pro_controls_v2_panel.png`

Compare only:

- composition
- visual hierarchy
- panel density
- control grouping
- obvious polish

Do not compare:

- exact generated text
- exact icon shapes
- fake preview content
- image-generator artifacts

### 4. Icon Review

If 2.0 adds icons:

- verify each icon is recognizable in context
- verify content descriptions exist
- verify active/disabled states remain understandable without color
- avoid custom-drawn icons when a local icon library exists

### 5. Animation Review

Review only after static UI is stable:

- panel open/close duration
- focus ring
- shutter press
- recording transition
- zoom feedback
- mode active transition

Constraints:

- no heavy decorative animations
- no motion that delays capture
- no animation that hides the recording state

## Suggested Visual Review Report Format

```text
# OpenCamera 2.0 Visual QA Report

## Build
- Commit/branch:
- Device:
- Date:

## Screenshots
- main:
- recording:
- tone:
- lens:
- pro:

## Findings
- VQA-001 [P0/P1/P2]: title
  - screen:
  - evidence:
  - expected:
  - implementation owner:

## Pass Criteria
- no P0 visual blockers
- no text overlap/wrapping in core controls
- recording state recognizable
- outside panel dismiss works
```

## Handoff To Text-Only Agents

When a visual issue is found, translate it into deterministic instructions:

- file or component
- current behavior
- desired behavior
- dp/sp/token values where possible
- exact text if copy changes
- test or manual scenario

Bad handoff:

```text
Make it look more premium.
```

Good handoff:

```text
In the bottom cockpit, reduce panel height from about 170dp to 112dp, keep thumbnail 56dp left, shutter 72dp centered, and move capture status to a single 13sp line above the controls.
```

## Non-Blocking Rule

Text-only implementation should proceed without this review as long as it follows the written specs. Multimodal review improves polish and catches visual regressions; it is not a prerequisite for model/architecture work.
