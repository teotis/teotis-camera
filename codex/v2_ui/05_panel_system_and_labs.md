# Panel System And Labs

## Shared Panel Contract

All secondary panels use one shell:

```text
┌────────────────────────────┐
│ 标题                  关闭  │
│ 当前摘要 / 影响范围          │
├────────────────────────────┤
│ 分段 tabs / 分类 chips       │
├────────────────────────────┤
│ 名称            当前值 状态   │
│ 说明文字 / 支持情况          │
│ 名称            当前值 状态   │
│ 说明文字 / 支持情况          │
├────────────────────────────┤
│ 操作提示 / 降级提示          │
└────────────────────────────┘
```

Panel shell fields:

- `route`
- `title`
- `subtitle`
- `summary`
- `tabs`
- `sections`
- `footer`
- `closeContentDescription`

Control row fields:

- `id`
- `label`
- `value`
- `supportState`: supported, degraded, unsupported
- `stateBadge`
- `supportText`
- `enabled`
- `disabledReason`
- `action`

## Panel Behavior

- One active panel at a time.
- Outside tap closes.
- Back closes.
- Close button closes.
- Panel content scrolls internally.
- Preview gestures pause while a panel is open.
- Panels do not dispatch runtime camera actions directly; they dispatch existing session/settings/app-shell intents.

## Shared Section Types

### Segmented Tabs

Use for category switching:

- Lens Lab: `拍照`, `录像`, `通用`
- Tone Lab: `拍照`, `人文`, `人像`, `视频`
- Watermark Lab: `模板`, `样式`
- Dev Log: `摘要`, `事件`, `导出`

### Control Rows

Use for settings and capability rows.

Examples:

```text
Live              开启    支持
拍照时同时保存动态片段

RAW               关闭    不支持
当前设备未提供 RAW 输出能力
```

### Chip Rows

Use for short option sets:

- aspect ratio
- timer
- filter family
- video fps if short

### Palette

Tone Lab only:

- visible rectangular palette
- selected reticle
- short label for horizontal and vertical axes
- touch updates tone model

## Lens Lab

User-facing title: `镜头实验室`

Purpose:

- capture setup
- camera/lens capability summary
- mode-relevant defaults

Tabs:

- `拍照`
- `录像`
- `通用`

Photo section rows:

- `比例`: current aspect ratio
- `Live`: on/off/degraded
- `倒计时`: current timer
- `默认滤镜`: current default or link to Tone Lab
- `人像`: link to Portrait Lab when relevant
- `水印`: link to Watermark Lab
- `质量优先`: quality/latency preference if modeled

Video section rows:

- `规格`: resolution/fps
- `防抖`: support/degraded
- `录音`: on/off
- `视频色调`: current video filter or link to Tone Lab
- `水印`: video support/degraded summary

Common section rows:

- `镜头`: current lens/camera
- `网格`: on/off
- `水平仪`: on/off
- `闪光灯`: support state
- `设备能力`: opens capability summary if needed

Footer examples:

- `部分设置会重新配置预览。录制中暂不可调整。`
- `当前设备不支持的项目会保留显示，方便确认原因。`

## Tone Lab

User-facing title: `色调`

Purpose:

- filter selection
- palette tone/tint control
- advanced color controls
- save custom look

Tabs:

- `拍照`
- `人文`
- `人像`
- `视频`

Sections:

1. Current look
   - selected profile name
   - current render summary
   - support/degraded notes
2. Filter roster
   - filter chips or rows
   - selected item shows `已选`
   - selected item action opens adjustments
3. Palette
   - two-axis color/tone palette
   - horizontal: cool to warm or tint
   - vertical: low key to bright
   - selected reticle
4. Advanced
   - brightness
   - contrast
   - saturation
   - warmth
   - tint
   - fade
   - vignette
   - halo
5. Custom
   - save as custom
   - reset current adjustments

Required Chinese copy:

- `滤镜`
- `调色板`
- `进阶`
- `保存为自定义`
- `已选`
- `当前默认`
- `当前模式暂不可用`

Implementation note:

- If current code still has `Filter Lab`, keep internal names if needed but expose `色调` to users.

## Portrait Lab

User-facing title: `人像`

Purpose:

- portrait product tuning without confusing it with Humanistic mode

Rows:

- `人像方案`: profile
- `美颜方案`: preset
- `美颜强度`: strength
- `虚化效果`: bokeh
- `人像色调`: link to Tone Lab portrait tab

Support states:

- If portrait mode/plugin unsupported: show rows disabled with reason.
- If still capture unavailable: show read-only state.

Footer:

- `这些设置会应用到下一次人像拍摄。`

## Watermark Lab

User-facing title: `水印`

Selector route:

- title `水印`
- subtitle `选择默认模板`
- template rows with current/default state
- row action selects template
- secondary action opens detail

Template row:

```text
旅行拍立得          当前默认
包含 日期 / 地点 / 机型
```

Detail route:

- title `水印样式`
- subtitle selected template label
- rows:
  - `位置`
  - `文字大小`
  - `文字透明度`
  - `背景样式`

Footer:

- `水印样式按模板保存。`

Video support:

- If video watermark is sidecar-only, say `视频仅保存水印信息`.
- If still-only, say `仅照片支持`.

## Pro Controls

User-facing title: `专业控制`

Purpose:

- compact manual controls with explicit auto/manual ownership

Rows:

- `RAW`
- `ISO`
- `S`
- `EV`
- `Focus`
- `A`
- `WB`

Control details:

- RAW: toggle row.
- ISO: auto/manual plus slider/stepper.
- S: auto/manual plus value row.
- EV: slider, reset.
- Focus: auto/manual slider.
- A: supported only where modeled; otherwise disabled.
- WB: auto/preset/manual kelvin.

Support states:

- `支持`
- `降级`
- `不支持`
- `录制中锁定`

Footer:

- `专业参数按当前设备能力显示。`

## Quick Panel

User-facing title: `快捷`

Purpose:

- short-lived toggles and common settings

Recommended items:

- `倒计时`
- `比例`
- `网格`
- `水平仪`
- `闪光`
- `Live`
- `水印`
- `专业`

Rules:

- Maximum 8 items.
- Each item is short and tappable.
- It may link to full lab when the action needs more detail.

## Dev Log

User-facing title: `开发日志`

Purpose:

- readable diagnostics without losing raw trace value

Tabs:

- `摘要`
- `事件`
- `导出`

Summary rows:

- session state
- active mode
- camera/lens
- preview state
- last capture timing
- last runtime issue
- thumbnail source
- recording state

Event list:

- highlight key events from Stage 7 observability and capture timing.
- use monospace for raw event names.
- keep copy/export action visible.

Export:

- `复制`
- `导出`
- `清空显示过滤` if filtering exists

## Test Plan

Render-model tests:

- Every panel uses localized Chinese title by default.
- Every row includes label/value/support state/support text.
- Unsupported rows stay visible.
- Only one active panel route can be represented.
- Watermark detail route carries selected template id.
- Tone Lab includes palette model.
- Dev Log includes key timing events when present.

Interaction tests:

- Outside tap closes each route.
- Back closes each route.
- Disabled row shows reason.
- Panel open pauses preview gestures.

## Implementation Order

1. Create shared panel route/model.
2. Migrate current settings/filter/quick/dev booleans into route.
3. Create reusable row rendering helper in `MainActivity`.
4. Rebuild Lens Lab with rows and tabs.
5. Rename/reframe Filter Lab as Tone Lab at the user-facing layer.
6. Rebuild Portrait and Watermark as nested routes inside shared panel system.
7. Rebuild Pro Controls and Dev Log.

## Non-Goals

- Do not invent new camera capabilities.
- Do not move persisted settings ownership.
- Do not let panel state enter Session Kernel.
- Do not make UI visually depend on generated reference images.
