# Multimodal Follow-Up Visual And Output QA Plan

> **For multimodal-capable agents only.** Do not assign this document to a text-only implementation agent. This plan requires screenshots, screen recordings, or saved-image visual comparison.

**Goal:** Validate the visual/UI and saved-photo results that cannot be confidently judged from code and logs alone.

---

## Why This Is Separate

The user explicitly provided screenshots and real-device observations. Several fixes can be implemented by a non-multimodal agent, but final acceptance needs visual inspection:

- whether Chinese labels are actually readable on the target phone,
- whether right rail and quick panel labels still wrap,
- whether the selected filter visibly changes the saved JPEG,
- whether the thumbnail appears to roll back during real-device timing,
- whether panel hierarchy feels less mixed after IA cleanup.

This document should be executed after the three non-multimodal implementation plans:

- `2026-05-22-media-output-filter-thumbnail-stability.md`
- `2026-05-22-panel-entry-information-architecture.md`
- `2026-05-22-localization-and-narrow-layout-cleanup.md`

## Required Inputs

Collect:

- APK build hash or timestamp.
- Real-device model and Android version.
- Screen recording from app launch through:
  - open settings,
  - open quick,
  - open tone,
  - open watermark selector/detail,
  - take two photos with a strong filter,
  - switch mode/lens after capture.
- Saved original/final JPEGs if available.
- Fresh debug log after the run.

## Visual QA Checklist

### 1. Right Rail And Top Area

Screenshots:

- first screen idle,
- settings open,
- quick open,
- dev open in debug build.

Pass criteria:

- Right rail labels are horizontal and readable.
- No label is split vertically.
- `设置` appears instead of `镜头` for the settings route.
- `镜头实验室` is not duplicated with `设置`.
- Top title does not collide with status icons or the settings pill.

### 2. Quick Panel

Screenshots:

- quick panel open,
- quick panel after toggling each visible item.

Pass criteria:

- Each item label is single-line or intentionally split into label/value with enough spacing.
- No Chinese word is broken into an awkward second line.
- Quick panel does not duplicate full settings pages.

### 3. Secondary Panel Localization

Screenshots:

- settings root,
- watermark selector,
- watermark detail,
- tone panel,
- portrait page if reachable.

Pass criteria:

- Default Chinese UI does not show normal user-facing English such as `Back`, `Default`, `Open Style Page`, `Placement`, `Scale`, `Opacity`, `Current default`, `templates staged`.
- Template names are localized or paired with localized names.
- Buttons do not contain three stacked lines of mixed Chinese/English.

### 4. Saved Photo Filter Result

Procedure:

1. Select a very strong custom tone/filter, for example high saturation and warm/cool shift.
2. Capture one photo with Live off.
3. Capture one photo with Live on.
4. Compare saved JPEG against preview/neutral photo.
5. Review debug output for:
   - `algorithm-render:applied:<profile-id>`
   - no `Primary directory Pictures not allowed for content://media/external/file`

Pass criteria:

- Saved JPEG visibly reflects the selected filter.
- Live-on capture no longer fails due to sidecar directory.
- If the visual difference is subtle, log must still show `algorithm-render:applied`, and the next engineering pass can tune filter intensity.

### 5. Thumbnail Timing And Rollback

Screen recording:

- capture photo A,
- wait for saved thumbnail,
- capture photo B,
- switch mode/lens,
- open/close panels.

Pass criteria:

- Thumbnail may show transient capture feedback during saving.
- After save completion, thumbnail ends on latest saved media.
- No later preview snapshot replaces the saved-media thumbnail.
- Tapping thumbnail after save opens the saved media, not a feedback/preview cache image.
- On capture failure, previous saved-media thumbnail remains.

## Report Template

Save the multimodal report as:

```text
codex/agent_plans/2026-05-22-multimodal-qa-report.md
```

Use this structure:

```markdown
# 2026-05-22 Multimodal QA Report

## Device And Build

- Device:
- Android:
- APK:
- Time:

## Pass/Fail Summary

- Right rail/top:
- Quick panel:
- Secondary localization:
- Saved photo filter:
- Thumbnail timing:

## Evidence

- Screenshots:
- Screen recordings:
- Debug log:
- Saved JPEGs:

## Findings

1. Record each observed issue with the screen, timestamp, and expected behavior.

## Follow-Up Engineering Tasks

1. Record each follow-up as an actionable engineering task with the owning plan or file area.
```

## Non-Goals

- Do not implement code changes in this QA pass unless the user explicitly asks.
- Do not judge color science quality beyond confirming that the selected effect is applied.
- Do not require vivo/iPhone pixel-perfect matching.
