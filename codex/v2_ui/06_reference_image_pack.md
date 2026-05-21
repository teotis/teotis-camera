# Reference Image Pack

## Purpose

Reference images provide a visual north star for humans and future multimodal agents. They are not the implementation source of truth for text-only agents. Every generated image must be paired with the wireframes and component specs in this folder.

Output directory:

```text
codex/v2_ui/reference_images/
```

## Required Images

| File | Purpose |
| --- | --- |
| `camera_cockpit_v2_main.png` | Main photo cockpit reference |
| `camera_cockpit_v2_recording.png` | Recording state reference |
| `tone_lab_v2_panel.png` | Tone Lab panel reference with palette |
| `lens_lab_v2_panel.png` | Lens Lab panel reference with grouped rows |
| `pro_controls_v2_panel.png` | Pro Controls panel reference |

Optional later:

- `watermark_lab_v2_panel.png`
- `portrait_lab_v2_panel.png`
- `quick_panel_v2.png`
- `dev_log_v2_panel.png`

## Prompt Rules

Use image generation for high-level UI mockup references only. Generated text inside images can be imperfect, so implementation agents must rely on Markdown specs for exact labels.

Shared constraints for all prompts:

- Use case: `ui-mockup`
- Android camera app
- vertical 9:16 phone screen
- Chinese labels
- dark translucent camera cockpit
- real preview placeholder or realistic camera preview background
- polished professional camera-product feel
- no marketing hero layout
- no decorative blobs/orbs
- no fake brand logos
- no unreadable tiny text
- no purple gradient-heavy theme
- no beige/brown dominant theme

## Prompt: Main Cockpit

```text
Use case: ui-mockup
Asset type: Android camera app reference mockup
Primary request: Create a polished vertical smartphone camera UI mockup for OpenCamera 2.0 main photo cockpit.
Style/medium: high-fidelity mobile UI mockup, dark translucent camera cockpit, Chinese labels, professional camera product.
Composition/framing: 9:16 phone screen, realistic camera preview placeholder, safe-area top status, right rail buttons, exact zoom row, horizontal mode row, compact bottom cockpit.
Text (verbatim): "OpenCamera · 拍照", "预览就绪", "镜头实验室", "色调", "快捷", "0.7x", "1x", "2x", "5x", "拍照", "文档", "人文", "人像", "专业", "视频", "快门".
Constraints: preview remains dominant, bottom controls balanced with thumbnail left and shutter center, no marketing hero layout, no decorative blobs, no unreadable tiny text, no fake logos.
Avoid: tall debug panels, vertical text wrapping, purple gradients, beige/brown palette, nested cards.
```

Save as:

```text
codex/v2_ui/reference_images/camera_cockpit_v2_main.png
```

## Prompt: Recording Cockpit

```text
Use case: ui-mockup
Asset type: Android camera app reference mockup
Primary request: Create a polished vertical smartphone camera UI mockup for OpenCamera 2.0 video recording state.
Style/medium: high-fidelity mobile UI mockup, dark translucent camera cockpit, Chinese labels, professional camera product.
Composition/framing: 9:16 phone screen, realistic camera preview placeholder, red recording feedback in top status, right rail compact buttons, zoom row, mode row with 视频 active, compact bottom cockpit.
Text (verbatim): "OpenCamera · 视频", "录制 00:12", "色调", "快捷", "0.7x", "1x", "2x", "拍照", "文档", "人文", "人像", "专业", "视频", "停止".
Constraints: recording state must be obvious, shutter becomes red stop control, disabled controls look intentional, preview remains dominant.
Avoid: modal dialogs, full-screen red overlay, marketing layout, decorative blobs, unreadable tiny text.
```

Save as:

```text
codex/v2_ui/reference_images/camera_cockpit_v2_recording.png
```

## Prompt: Tone Lab

```text
Use case: ui-mockup
Asset type: Android camera app panel reference mockup
Primary request: Create a polished OpenCamera 2.0 Tone Lab bottom sheet over a camera preview.
Style/medium: high-fidelity Android UI mockup, dark translucent panel, Chinese labels, professional camera product.
Composition/framing: 9:16 phone screen, preview visible behind dim scrim, bottom sheet covers lower 65 percent, panel header, tabs, selected filter row, visible two-axis color palette, advanced rows.
Text (verbatim): "色调", "当前色调 · 人文", "拍照", "人文", "人像", "视频", "滤镜", "调色板", "进阶", "自然", "已选", "保存为自定义".
Constraints: palette must be visually recognizable, rows structured with label/value/state, no nested cards, no tiny unreadable text.
Avoid: blank rectangle palette, colorful chaos, purple gradient theme, marketing layout.
```

Save as:

```text
codex/v2_ui/reference_images/tone_lab_v2_panel.png
```

## Prompt: Lens Lab

```text
Use case: ui-mockup
Asset type: Android camera app panel reference mockup
Primary request: Create a polished OpenCamera 2.0 Lens Lab bottom sheet over a camera preview.
Style/medium: high-fidelity Android UI mockup, dark translucent panel, Chinese labels, professional camera product.
Composition/framing: 9:16 phone screen, preview visible behind dim scrim, bottom sheet with header, segmented tabs, grouped settings rows, support/degraded badges.
Text (verbatim): "镜头实验室", "拍照", "录像", "通用", "比例", "4:3", "Live", "开启", "倒计时", "关闭", "水印", "当前默认", "设备能力", "支持".
Constraints: settings rows should look structured and easy to scan, no multi-line cramped buttons, no cards inside cards.
Avoid: debug-table look, tall full-screen panel, unreadable text, decorative blobs.
```

Save as:

```text
codex/v2_ui/reference_images/lens_lab_v2_panel.png
```

## Prompt: Pro Controls

```text
Use case: ui-mockup
Asset type: Android camera app panel reference mockup
Primary request: Create a polished OpenCamera 2.0 Pro Controls bottom sheet over a camera preview.
Style/medium: high-fidelity Android UI mockup, dark translucent panel, Chinese labels, professional camera product.
Composition/framing: 9:16 phone screen, preview visible behind dim scrim, bottom sheet with compact manual camera controls, sliders, toggles, state badges.
Text (verbatim): "专业控制", "当前设备能力", "RAW", "关闭", "ISO", "Auto", "S", "1/120", "EV", "0.0", "Focus", "Auto", "WB", "自动", "不支持".
Constraints: manual controls compact and technical but readable, disabled unsupported controls visible with reason, no fake brand logos.
Avoid: dense spreadsheet, nested cards, purple gradients, unreadable tiny labels.
```

Save as:

```text
codex/v2_ui/reference_images/pro_controls_v2_panel.png
```

## Usage Guidance For Implementation Agents

Text-only agents should:

- Read `01_camera_cockpit_wireframes.md` for layout.
- Read `02_visual_system.md` for exact visual tokens.
- Read `03_interaction_grammar.md` for behavior.
- Use reference image filenames only as supporting context in PR descriptions or human reviews.
- Never copy text from image pixels.
- Never infer dimensions from the generated picture when a dp spec exists.

Human reviewers and multimodal agents should:

- Compare screenshots against these images only at the level of composition, hierarchy, and obvious visual polish.
- Flag generated-image mistakes as reference limitations, not product requirements.
- Prefer Markdown specs when image and text disagree.

## Regeneration Checklist

When regenerating images:

- Keep filenames stable unless intentionally creating a versioned alternative.
- Do not overwrite existing images without checking whether they are referenced in review notes.
- Store final selected PNG files in `codex/v2_ui/reference_images/`.
- Update this document if a prompt changes.
- Add a short `reference_images/README.md` if multiple versions are kept.

## Current Pack Status

The five required reference PNG files currently exist under `codex/v2_ui/reference_images/`.

Because the current environment does not expose the built-in image generation tool, the checked-in images are deterministic PNG mockups generated by:

```text
codex/v2_ui/reference_images/generate_reference_images.py
```

These generated mockups intentionally prioritize reliable layout, Chinese labels, panel hierarchy, and control grouping over photorealistic polish. The prompts above remain the source for a later AI-generated visual pass if desired.
