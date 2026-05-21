# Camera Cockpit Wireframes

## Main Portrait Layout

The first screen is a camera cockpit. The preview fills the screen; controls float in stable zones.

```text
┌────────────────────────────────────┐
│ safe area                           │
│ OpenCamera · 拍照        镜头实验室  │
│ 预览就绪 / 保存中 / 录制 00:12        │
├────────────────────────────────────┤
│                                    │
│              Preview               │
│                                    │
│                         色调        │
│                         快捷        │
│                         开发        │
│                                    │
│        0.7x   1x   2x   5x         │
│      拍照 文档 人文 人像 专业 视频     │
├────────────────────────────────────┤
│ 最近媒体         快门/停止       镜头 │
│ 缩略图                         变焦 │
└────────────────────────────────────┘
```

### Zone Contract

| Zone | Purpose | Behavior |
| --- | --- | --- |
| Top status | App identity, active mode, camera status, one primary lab entry | Safe-area aware; no preview-blocking tall panel |
| Preview | Camera feed, focus gesture, pinch zoom | No decorative overlay except focus/level/status feedback |
| Right rail | Secondary entry points | Icon or very short Chinese label only |
| Zoom strip | Exact preset zoom selection | Tap chip selects that chip's ratio |
| Mode track | Stable mode switching | Horizontal, no vertical text, no accidental switching while scrolling |
| Bottom cockpit | Recent media, shutter, lens/zoom controls | Short, balanced, one-thumb capture surface |

## Main Cockpit Dimensions

Recommended portrait dp targets:

- Top status: 72-96dp total including safe-area padding.
- Right rail button: 44dp touch target, 36dp visual body, 8dp vertical gap.
- Zoom strip: 34-40dp high, placed 8-12dp above mode track.
- Mode track: 42-48dp high, horizontal scroll when needed.
- Bottom cockpit: 96-124dp total, including capture output/status line.
- Thumbnail: 52-60dp square with 8-10dp radius.
- Shutter photo: 72dp circle with 58dp inner ring.
- Shutter video stop: 72dp circle or rounded square inside circle, red state.

Small-screen rule:

- Top status compresses before preview controls move.
- Right rail labels may become icons with tooltips/content descriptions.
- Mode track remains one row and scrolls horizontally.
- Zoom strip may show `0.7x 1x 2x` and let overflow scroll to `5x`; it must not wrap.
- Bottom cockpit keeps thumbnail, shutter, and lens control in one row.

## Top Status Wireframe

```text
┌────────────────────────────────────┐
│ OpenCamera · 拍照        [镜头实验室] │
│ 预览就绪                             │
└────────────────────────────────────┘
```

States:

- `预览就绪`
- `保存中`
- `录制 00:12`
- `停止录制中`
- `相机恢复中`
- `需要相机权限`
- `当前镜头不支持 5x，已降级到 2x`

Implementation notes:

- Use two text rows on the left and one compact action on the right.
- Title max 1 line.
- Status max 1 line, ellipsize end.
- Apply top window inset as padding or top margin.
- Avoid placing a full-width opaque card over the preview.

## Right Rail Wireframe

```text
        ┌────┐
        │色调│
        ├────┤
        │快捷│
        ├────┤
        │开发│
        └────┘
```

Rules:

- Use 2-4 entries only.
- Button labels are two Chinese characters when possible.
- Optional icon above text if icon set is already available.
- Only one panel can open at a time.
- Active panel entry uses accent outline or accent dot.

Recommended entries:

- `色调` opens Tone Lab.
- `快捷` opens Quick panel.
- `开发` opens Dev Log in debug builds only.
- `专业` can appear only in modes where pro controls are central; otherwise Pro Controls belongs in the mode or panel system.

## Zoom Strip Wireframe

```text
        [0.7x]   [1x]   [2x]   [5x]
```

Rules:

- The selected chip has filled light background or accent ring.
- Disabled zoom chip remains visible with low opacity and a short reason available in status feedback.
- Tapping a chip dispatches the exact chip ratio.
- Pinch zoom may select an in-between ratio and should update strip state to nearest displayed chip only when exact or within a small threshold.
- Existing bottom zoom toggle can remain as a quick cycle entry, but the strip is exact selection.

## Mode Track Wireframe

```text
拍照   文档   人文   人像   专业   视频
```

Rules:

- One horizontal row.
- Each item has at least 58dp min width and 40dp min height.
- Text is single line.
- Active mode uses accent underline or filled chip.
- Swipe scroll never triggers mode change.
- Tap-up inside the same item triggers mode change.

Recommended mode order:

```text
拍照 → 文档 → 人文 → 人像 → 专业 → 视频
```

If Night remains available, use one of these patterns:

- Put `夜景` between `拍照` and `文档` on devices where it is a first-class target.
- Keep Night inside Lens Lab if the current product wants a shorter main track.

The implementation must preserve actual `ModeId` identity and should not rely on index-only routing.

## Bottom Cockpit Wireframe

```text
┌────────────────────────────────────┐
│ 上次保存  IMG_160053.jpg            │
│ ┌──────┐        ┌──────┐       ┌───┐ │
│ │缩略图│        │ 快门 │       │镜头│ │
│ └──────┘        └──────┘       │切换│ │
└────────────────────────────────────┘
```

Photo idle:

- Left: latest saved thumbnail. Empty state uses subtle placeholder, not preview snapshot after a saved capture.
- Center: white circular shutter, no long text inside.
- Right: lens switch button plus optional compact current lens label.

Recording:

```text
┌────────────────────────────────────┐
│ 录制中 00:12                        │
│ ┌──────┐        ┌──────┐       ┌───┐ │
│ │缩略图│        │ 停止 │       │镜头│ │
│ └──────┘        └──────┘       │锁定│ │
└────────────────────────────────────┘
```

Recording rules:

- Shutter shows stop state immediately after recording starts.
- During stopping/saving, shutter is disabled and status says why.
- Lens switch and mode switch are disabled during active recording unless the Session Kernel explicitly supports it.

## Unified Panel Placement

Panel should appear as a bottom sheet or side/bottom hybrid depending on screen size.

Portrait default:

```text
┌────────────────────────────────────┐
│ Preview dim scrim outside panel     │
│                                    │
│                                    │
├────────────────────────────────────┤
│ 标题                         关闭   │
│ 当前摘要 / 影响范围                 │
│ [分段] [分段] [分段]                 │
│ Row label          value     state  │
│ support text                       │
│ Row label          value     state  │
│ support text                       │
└────────────────────────────────────┘
```

Rules:

- Panel max height about 62-72% of screen.
- Tapping outside closes.
- Drag-to-dismiss is optional for 2.0; outside tap and close button are required.
- Panel content scrolls inside panel, not the whole root.
- Do not put cards inside cards. Use rows, sections, and dividers.

## XML Migration Guidance

Suggested root order inside `activity_main.xml`:

1. `PreviewView`
2. preview gesture/focus overlay
3. top status group
4. right rail group
5. zoom strip group
6. mode track group
7. bottom cockpit group
8. panel dismiss scrim
9. active panel container(s)
10. transient status/toast overlay

The scrim must sit below the visible panel and above main cockpit controls. It should not intercept taps inside the panel.

## Acceptance Checklist

- Top area respects safe-area insets.
- No visible text wraps vertically.
- Zoom chips do not cycle when tapped.
- Portrait tap cannot activate Humanistic through overlap or index error.
- Bottom controls fit on a small phone.
- Thumbnail represents latest saved media.
- Recording state is visible without reading dev log.
