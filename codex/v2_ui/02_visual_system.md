# Visual System

## Direction

OpenCamera 2.0 should feel like a quiet professional camera product. The visual language is preview-first, dark translucent, compact, and consistent. The interface should not look like a debug panel stack.

Primary qualities:

- clean enough for daily shooting
- readable over real camera preview
- Chinese-first typography
- stable controls that do not resize on state changes
- restrained accent color used only for active state and feedback

## Color Tokens

Recommended tokens:

| Token | Value | Usage |
| --- | --- | --- |
| `oc_surface_scrim` | `#8C000000` | bottom cockpit, panel backgrounds over preview |
| `oc_surface_scrim_light` | `#52000000` | top status, right rail buttons |
| `oc_surface_panel` | `#E61B1F24` | secondary panels |
| `oc_surface_panel_alt` | `#CC242A30` | section bands, active rows |
| `oc_text_primary` | `#F8FAFC` | main labels |
| `oc_text_secondary` | `#CBD5E1` | support text |
| `oc_text_muted` | `#94A3B8` | disabled descriptions |
| `oc_accent` | `#55D6BE` | selected mode, focus, active chip |
| `oc_record` | `#FF3B30` | recording and stop state |
| `oc_warning` | `#FBBF24` | degraded state |
| `oc_error` | `#F87171` | unavailable/failure state |
| `oc_divider` | `#2FFFFFFF` | panel dividers |

Rules:

- Do not use a purple/blue-gradient dominated screen.
- Do not use beige/brown/orange as the dominant theme.
- Do not add decorative blobs, orbs, or bokeh backgrounds.
- Let the real preview be the visual asset.
- Use accent color for state, not decoration.

## Typography

Recommended Android sizes:

| Element | Size | Weight | Lines |
| --- | --- | --- | --- |
| Top title | 17sp | medium/semibold | 1 |
| Top status | 12-13sp | regular | 1 |
| Mode chip | 14-15sp | medium | 1 |
| Zoom chip | 13-14sp | medium | 1 |
| Right rail | 12-13sp | medium | 1-2 short Chinese chars |
| Panel title | 18sp | semibold | 1 |
| Panel section title | 15sp | medium | 1 |
| Control row label | 14sp | medium | 1 |
| Control row value | 13sp | regular/medium | 1 |
| Support text | 12sp | regular | 1-2 |
| Dev log text | 11-12sp | monospace if available | multiple |

Rules:

- Letter spacing stays at 0.
- Do not scale text by viewport width.
- Do not put long English labels inside circular or narrow controls.
- Use short Chinese labels on controls and put longer explanations in support text.

## Shape And Elevation

| Component | Radius | Notes |
| --- | --- | --- |
| Top status group | 0-12dp | Prefer no card; use scrim band or soft rounded background |
| Bottom cockpit | 18-24dp top corners | Must stay short and stable |
| Panel shell | 16dp top corners | Bottom sheet style |
| Panel row | 8-10dp | Only if row needs active/pressed background |
| Chips | 18-22dp | Not every element should be a pill |
| Thumbnail | 8-10dp | Square, not circle |
| Shutter | circle | Photo white, video red, stop high contrast |
| Small icon buttons | 12dp | stable 44dp touch target |

Elevation:

- Prefer translucent surfaces and subtle outlines over heavy shadows.
- Panels can have one subtle top shadow.
- Avoid stacked raised cards inside a panel.

## Spacing Tokens

Recommended dp scale:

- `space_2`: 2dp for hairline offsets.
- `space_4`: 4dp for dense row internals.
- `space_8`: 8dp for button gaps and row padding.
- `space_12`: 12dp for panel internal rhythm.
- `space_16`: 16dp for panel edge padding.
- `space_24`: 24dp for zone separation.

Touch target:

- Minimum target 44dp.
- Shutter target 72dp.
- Mode chip min width 58dp and min height 40dp.
- Zoom chip min width 48dp and min height 32dp.

## Component Specs

### Top Status

Visual:

- Transparent or `oc_surface_scrim_light`.
- Text left aligned.
- Lens Lab action right aligned.
- Safe-area top padding included.

States:

- Normal: `预览就绪`
- Busy: `保存中`, `相机恢复中`
- Recording: red dot plus `录制 00:12`
- Error: muted panel plus warning/error text

### Right Rail Button

Visual:

- 44dp target.
- 36-40dp visible body.
- Rounded rectangle 12dp.
- Surface `oc_surface_scrim_light`.
- Active outline `oc_accent`.

Content:

- Prefer icon + short label if icon library exists.
- Otherwise Chinese two-character label: `色调`, `快捷`, `开发`.
- Content descriptions required.

### Zoom Chip

Visual:

- Inactive: translucent black, white text.
- Active: light fill or accent border, accent text.
- Disabled: 40% opacity plus status feedback on tap.

Text:

- `0.7x`, `1x`, `2x`, `5x`.
- Avoid `1.0x` unless all chips use one decimal consistently.

### Mode Track

Visual:

- Inactive text `oc_text_secondary`.
- Active text `oc_text_primary` plus accent underline or active pill.
- Do not use tall rectangular buttons that create vertical text.

Text:

- `拍照`, `文档`, `人文`, `人像`, `专业`, `视频`.
- Optional `夜景` if kept in first-level mode track.

### Bottom Cockpit

Visual:

- Short translucent band, not a tall settings card.
- Three-column alignment: thumbnail, shutter, lens/zoom.
- Capture output/status is a compact single line.

Shutter:

- Photo: white circle, subtle ring.
- Video ready: red circle or red center.
- Recording: stop icon/label with red state.
- Stopping/saving: disabled with busy ring or status text.

### Control Row

Use rows instead of multi-line buttons for panels:

```text
┌──────────────────────────────┐
│ 名称              当前值  状态 │
│ 说明文字 / 支持情况           │
└──────────────────────────────┘
```

Row fields:

- `label`
- `value`
- `stateBadge`: supported/degraded/unsupported/active/locked
- `supportText`
- `enabled`
- `disabledReason`
- `action`

Visual:

- 48-64dp minimum height.
- Label/value one line.
- Support text one or two lines.
- Divider between rows.
- Active row may use `oc_surface_panel_alt`.

## Panel Visual Grammar

Panel shell:

```text
标题                         关闭
当前摘要 / 影响范围
--------------------------------
[Tab] [Tab] [Tab]
--------------------------------
Section
Row
Row
--------------------------------
Footer / degraded hint
```

Rules:

- One panel shell style for all labs.
- Tabs are segmented chips, not separate cards.
- Avoid nested MaterialCards inside panel.
- Use section headings and dividers for hierarchy.
- Footer is a muted text band, not another card.

## Motion And Feedback

Use simple Android View animations only:

- Panel open: translate from bottom + fade, 160-220ms.
- Panel close: reverse, 120-180ms.
- Mode switch: active underline/chip moves or fades, 120ms.
- Zoom chip: pressed scale 0.98 or alpha change, 80ms.
- Shutter: immediate pressed state; recording start changes color/text within one render.
- Focus: small accent focus ring fades out, 600-900ms.

Do not add heavy animated illustrations or decorative motion.

## Localization Rules

- Default Chinese labels should be natural product copy, not literal translations of internal names.
- Keep English fallbacks in `values-en`.
- No user-facing hard-coded English in `SessionUiRenderModel.kt` or dynamic `MainActivity.kt` button creation.
- Names:
  - `Tone Lab` -> `色调`
  - `Lens Lab` -> `镜头实验室`
  - `Portrait Lab` -> `人像`
  - `Watermark Lab` -> `水印`
  - `Pro Controls` -> `专业控制`
  - `Dev Log` -> `开发日志`

## Accessibility Baseline

- Every icon-only or short-label button has a content description.
- Active/disabled state must not rely on color only.
- Disabled tap gives a short reason in status strip or toast.
- Contrast should remain readable over bright preview; use scrim behind text where needed.
- Panel close button target at least 44dp.

## Implementation Notes For Text-Only Agents

- Start by defining drawables and styles with explicit dimensions and colors.
- Then migrate controls one zone at a time.
- Do not infer layout from reference images. Use the dp and row specs here.
- Keep all magic numbers named in styles where practical.

## Acceptance Checklist

- Main screen reads as one camera product, not separate debug controls.
- Text fits in Chinese and English.
- Bottom cockpit no longer dominates the screen.
- Panels share one visual grammar.
- Tone palette is visible and recognizable.
- Recording state is unmistakable.
