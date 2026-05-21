# Camera Function Convenience

## Goal

2.0 should make the camera feel easy to operate under one thumb. The main screen should serve the most common capture tasks. Labs should organize medium-frequency setup without forcing users to remember where a feature lives.

## Frequency Layers

### First Screen: High Frequency

Keep these visible without opening a panel:

- photo/video shutter
- current mode
- mode switch
- exact zoom presets
- pinch zoom
- lens switch
- latest saved media thumbnail
- status feedback
- Tone entry
- Quick entry
- Lens Lab primary entry

### Secondary Panels: Medium Frequency

Put these behind one tap:

- frame ratio
- timer
- live photo
- default photo/video spec
- filter selection
- tone palette
- portrait defaults
- watermark template/style
- pro controls
- debug/dev log

### Deferred/Advanced

Keep these out of the first screen unless explicitly promoted later:

- import/export filter profiles
- full manual curve editing
- per-device tuning matrix
- visual QA and screenshot comparison
- animation polish beyond simple View transitions

## Main Capture Flow

Photo:

```text
Open app -> preview ready -> select mode/zoom/lens if needed -> tap shutter -> saving feedback -> thumbnail updates -> tap thumbnail opens saved photo
```

Requirements:

- Shutter remains central and stable.
- Status says `保存中` while pipeline runs.
- Thumbnail updates only from saved-media result.
- Capture delay diagnosis events are visible in Dev Log, but the capture UI should not expose raw timing unless debug mode is active.

Video:

```text
Switch to 视频 -> tap record -> recording timer visible -> tap stop -> stopping/saving feedback -> thumbnail updates -> tap thumbnail opens saved video
```

Requirements:

- Start/stop feedback is immediate.
- Timer lives in top status or bottom compact status.
- Disabled lens/mode controls explain recording conflict.
- Saved video opens via thumbnail even if ImageView cannot show frame preview on all devices.

## Mode Convenience

Recommended first-level modes:

```text
拍照 / 文档 / 人文 / 人像 / 专业 / 视频
```

Rules:

- Mode names are user-facing; no internal plugin labels.
- Mode switching uses stable `ModeId`.
- Active mode is visible in both title and mode track.
- Mode track scroll position should keep active mode visible.
- Mode-specific unsupported features remain visible in labs with reason.

Mode summaries:

- `拍照`: default still capture, common filters, live photo, watermark.
- `文档`: document framing/crop related settings are summarized in Lens Lab or Quick.
- `人文`: color profile and street-style defaults emphasized through Tone Lab.
- `人像`: portrait profile, beauty, bokeh, portrait tone.
- `专业`: manual controls first, capture still remains central.
- `视频`: recording spec, timer, video tone, watermark sidecar where supported.

## Zoom And Lens Convenience

Zoom:

- Preset strip for exact ratios.
- Pinch for continuous ratio.
- Current ratio shown after pinch.
- Unsupported ratio chip can be hidden only if not meaningful; otherwise keep visible disabled with reason.

Lens:

- Bottom-right lens switch uses short copy: `镜头`.
- If multiple cameras exist, use a compact chooser in Lens Lab.
- Double tap preview can switch lens when safe.
- During recording, lens switch is disabled unless actual runtime supports it.

## Tone Convenience

Tone is a first-screen right-rail entry because it is part of the shooting feel.

Tone Lab should support:

- filter family tabs
- selected filter
- visible palette
- basic tone/tint adjustment
- advanced adjustment entry
- save as custom

User copy:

- `色调` instead of `Filter Lab` on first screen.
- Inside panel, sections can say `滤镜`, `调色板`, `进阶`.

Fast path:

```text
Tap 色调 -> tap filter chip -> optional palette drag -> outside tap closes
```

## Portrait Convenience

Portrait should not be confused with Humanistic:

- Mode hit targets must be stable and non-overlapping.
- Portrait Lab belongs under panel system and can also be linked from Lens Lab when in photo/portrait contexts.
- Portrait defaults should show profile, beauty preset, strength, and bokeh in rows.

Fast path:

```text
Tap 人像 mode -> tap shutter
Optional: Lens Lab or Portrait Lab -> choose profile/beauty/bokeh
```

## Watermark Convenience

Watermark is medium-frequency:

- Do not place multiple watermark controls on the main screen.
- Provide `水印` inside Lens Lab or Quick.
- Selector page chooses template.
- Detail page styles selected template.

Fast path:

```text
Lens Lab -> 水印 -> choose template -> optional style row -> outside tap closes
```

States:

- `当前默认`
- `已应用`
- `不可用：当前模式不支持`
- `只保存到照片`
- `视频仅保存侧车信息` where applicable

## Pro Convenience

Professional controls should be useful without turning the whole UI into a settings spreadsheet.

Entry rules:

- In `专业` mode, Pro Controls can be more prominent.
- In other modes, Pro Controls live in Lens Lab or right rail only if relevant.

Controls:

- RAW
- ISO
- S / shutter speed
- EV
- Focus
- Aperture if modeled or supported
- White balance

Control shape:

- Boolean: switch/toggle row.
- Enumerated: segmented row or chip row.
- Numeric: slider with value and reset/auto.
- Unsupported: row with unsupported badge and reason.

## Quick Panel Convenience

Quick panel is for short-lived choices:

- timer
- grid/level
- flash/torch if supported
- aspect ratio
- live photo
- recent setting shortcuts

Rules:

- Max 6-8 items.
- Uses icon/chip grid or compact rows.
- It should not duplicate full Lens Lab.
- It closes on outside tap.

## Dev Log Convenience

Dev Log remains debug-oriented, but it should be usable:

- Entry visible only in debug builds or dev mode.
- Title `开发日志`.
- Summary row: session state, camera id/lens, current mode, last capture timing, last issue.
- Key path events are highlighted:
  - `capture.photo`
  - `capture.saving`
  - `capture.saved`
  - `capture.timing`
  - `recording.requested`
  - `recording.started`
  - `recording.saved`
  - `recording.timing`
  - `preview.snapshot.ignored`
  - `preview.recovery.requested`
  - `preview.recovery.failed`
- Raw export remains available.

## Render Model Guidance

Add app-layer models that are convenient for UI rendering:

```kotlin
data class CameraCockpitRenderModel(
    val topStatus: TopStatusRenderModel,
    val rightRail: List<CockpitRailItemRenderModel>,
    val zoomStrip: ZoomStripRenderModel,
    val modeTrack: ModeTrackRenderModel,
    val bottomCockpit: BottomCockpitRenderModel,
    val activePanel: CockpitPanelRoute,
)
```

Each control should carry:

- display label
- current value
- enabled
- disabled reason
- support state
- action target, not direct camera runtime calls

## Convenience Acceptance Tests

Suggested render-model tests:

- Photo idle shows shutter, thumbnail command, zoom strip, mode track, Tone/Quick entries.
- Recording shows timer and stop state.
- Saving disables conflicting controls with `正在保存上一张`.
- Video recording disables mode/lens where unsupported.
- Pro mode promotes Pro Controls.
- Tone Lab entry label is Chinese.
- Watermark unsupported state remains visible with reason.

## Acceptance Checklist

- A user can take a photo without opening any panel.
- A user can start and stop video with obvious feedback.
- A user can pick exact zoom without cycling surprises.
- A user can reach Tone, Quick, and Lens Lab without searching.
- Medium-frequency features are grouped by user goal.
- Unsupported hardware features are explainable, not invisible mysteries.
