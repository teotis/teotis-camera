# Fifth Recording Hard10 Multimodal QA Report

> Date: 2026-05-22  
> Source recording: `/Users/dingren/Downloads/飞书20260522-162635.mp4`  
> Device: vivo X300 real-device recording  
> Recording facts: `540x1176`, about `157.93s`, portrait  
> Scope: high-difficulty 10% and multimodal-only review. This is not an implementation patch.

## Verdict

**NO GO for visual/product 2.0 acceptance.**

The local automated gate can pass, but the recording still shows a product experience below the camera 2.0 bar. The core issue is not one broken widget; it is that the current cockpit still feels like an engineering demo layered over a preview rather than a camera-first product surface.

## Evidence Frames

Extracted reference frames are stored outside the repo for review:

- Contact sheet: `/private/tmp/opencamera_video_review_162635/hard10_contact.jpg`
- Frames:
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_01.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_02.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_03.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_04.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_05.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_06.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_07.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_08.jpg`
  - `/private/tmp/opencamera_video_review_162635/hard10_frames/issue_09.jpg`

Approximate frame mapping:

| Frame | Approx time | Main evidence |
| --- | --- | --- |
| issue_01 | 00:05 | First-screen cockpit hierarchy, right rail, split bottom operation area |
| issue_02 | 00:11 | Settings panel engineering copy and large occlusion |
| issue_03 | 00:27 | Color Lab palette, advanced entry, tall panel occluding preview |
| issue_04 | 00:47 | Style panel raw render parameters leaking into product UI |
| issue_05 | 01:07 | Quick controls as floating buttons, frame-ratio information weak/unstable |
| issue_06 | 01:12 | Quick controls still floating; crop frame and controls compete |
| issue_07 | 01:21 | Dev panel dominates normal acceptance surface |
| issue_08 | 01:45 | Dev/raw diagnostics readability vs product UI |
| issue_09 | 02:10 | Thumbnail feedback, mode/zoom/bottom cockpit, frame geometry |

## P0 Findings

### P0-1 Color Lab direct manipulation is functionally broken

Evidence:

- User confirmed the palette snaps back to origin after tapping/dragging.
- Code inspection shows `FilterPaletteView` emits axes, but `MainActivity.handleFilterPaletteTouch()` writes custom filter render specs and requires `selectedProfileId`.
- Color Lab reticle is rendered from persisted `ColorLabSpec`, so the two state owners diverge.

Why this blocks 2.0:

- Color Lab is a first-class top action. If direct manipulation cannot persist, the most important 2.0 lab interaction is broken.

Required owner:

- Implement: `2026-05-22-fifth-color-lab-palette-persistence.md`
- Multimodal recheck: drag palette on vivo X300; reticle must stay where released.

### P0-2 Capture cockpit is visually fragmented

Evidence:

- Zoom chips float outside the shutter panel.
- Mode track sits separately and is hard to read over the preview.
- Shutter, thumbnail, lens, zoom, and mode do not read as one operational unit.

Why this blocks 2.0:

- Camera 2.0 acceptance depends on a confident first-screen cockpit. Current layout requires users to parse several unrelated floating regions.

Required owner:

- Implement: `2026-05-22-fifth-bottom-cockpit-zoom-mode-track.md`
- Multimodal recheck: bottom area must read as one translucent camera control group.

### P0-3 Quick frame-ratio interaction is not reliably visible

Evidence:

- Quick controls appear as floating stacked buttons.
- Frame ratio options are visually weak and can appear to disappear or lose context during use.
- User explicitly requested a panelized presentation.

Why this blocks 2.0:

- Frame ratio affects final composition. If options are not stable and scannable, output intent is unclear.

Required owner:

- Implement: `2026-05-22-fifth-quick-panel-frame-ratio-sheet.md`
- Multimodal recheck: `4:3 / 16:9 / 1:1` remain visible with selected state.

## P1 Findings

### P1-1 Top bar clipping and hierarchy are not acceptable

Evidence:

- User confirmed top Chinese text is partially obstructed.
- `色彩实验室` and `设置` share similar button shape, length, and visual weight.
- Bordered top buttons conflict with the rest of the cockpit.

Design judgment:

- The top bar should behave like a camera status/action strip: compact, calm, safe-area-aware. It should not look like two equal form buttons.

Required owner:

- Implement: `2026-05-22-fifth-top-bar-and-rail-ia-polish.md`
- Multimodal recheck: top bar fits under vivo status/recording overlay; no clipping.

### P1-2 Right rail label is semantically wrong

Evidence:

- User requested `色调` -> `镜头`.
- Recording shows the rail as a product-facing navigation surface, not an internal filter/tone label.

Design judgment:

- Use `镜头` for the right-rail product entry, while Color Lab remains top action. This better matches vivo/OPPO camera ergonomics and user expectation.

Required owner:

- Implement: `2026-05-22-fifth-top-bar-and-rail-ia-polish.md`

### P1-3 Product panels leak engineering language

Evidence:

- Settings panel shows `Supported`.
- Style panel exposes raw fields like `B 20 | C 0.92 | S 1.37 | W 24 | Mono...`.
- Dev log is allowed, but raw data outside Dev makes the product feel unfinished.

Design judgment:

- OpenCamera should preserve explicit capability semantics, but user panels need product copy. Raw values belong in Dev/diagnostics only.

Required owner:

- Implement: `2026-05-22-fifth-panel-copy-engineering-cleanup.md`

### P1-4 Dev is too dominant during normal product acceptance

Evidence:

- Dev entry remains highly visible in the right rail.
- Dev panel covers most of the screen and is visually similar in weight to product panels.

Design judgment:

- Dev can remain available because the project values diagnostics, but it should be visually subordinate during product acceptance. A long-press entry or less prominent treatment may be preferable after product approval.

Required owner:

- Product decision before implementation: keep Dev as visible rail item, hide behind gesture, or keep visible only for debug builds.

### P1-5 Preview and frame geometry still need visual arbitration

Evidence:

- 1:1 / 4:3 / 16:9 frames are visible, but the crop box, live content, and bottom controls compete.
- Black preview states appear in parts of the recording.

Design judgment:

- This may be a mixture of overlay geometry, preview state, and layout occlusion. It should not be judged solved by layout tests alone.

Required owner:

- Multimodal owner after layout packages land.
- If mismatch remains, create a dedicated geometry task rather than hiding it under style changes.

## Design Arbitration

### Recommended visual direction

Reference vivo, Apple, and OPPO for ergonomics, not for copying surface details.

- vivo/OPPO lesson: quick camera controls should be compact, panelized, and readable with one thumb.
- Apple lesson: the bottom capture cockpit should feel anchored and calm; photographic adjustments should be direct and reversible.
- OpenCamera philosophy: never fake capability. Degraded/unsupported states must remain explicit, but in product language rather than engineering dumps.

### Product hierarchy

The next APK should have this visible hierarchy:

1. Preview remains dominant.
2. Bottom capture cockpit is the main control unit.
3. Top bar is quiet and safe-area-aware.
4. Right rail has short product entries: `镜头 / 快捷 / Dev`.
5. Secondary panels are bounded sheets, not giant overlays.
6. Dev is available but visually subordinate.

## Recheck Protocol For New APK

After text-only implementation packages land:

1. Build APK:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

2. Install on vivo X300:

```bash
adb install -r -d "/Users/dingren/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk"
```

3. Record a new 2-minute portrait pass covering:

- first screen,
- top bar under status/recording overlay,
- Color Lab drag and release,
- right rail,
- Quick panel frame-ratio options,
- mode switching through photo/scenery/portrait/pro/video/document,
- one capture and thumbnail update,
- Dev open/close.

4. Extract key frames:

```bash
rtk ffmpeg -y -i "<new-recording.mp4>" -vf fps=1/5,scale=540:-1 /private/tmp/opencamera_video_review_next/key_%03d.jpg
```

5. Pass/fail criteria:

| Area | Pass condition |
| --- | --- |
| Top bar | No clipped text; top actions quiet and unequal in hierarchy |
| Color Lab | Reticle persists; no `进阶`; enough preview visible |
| Right rail | Shows `镜头 / 快捷 / Dev`; labels readable |
| Bottom cockpit | Zoom and shutter feel integrated; mode readable |
| Quick panel | Ratio options visible and selected state clear |
| Panels | No raw render params or `Supported` in user-facing panels |
| Capture feedback | Thumbnail clearly updates to latest capture |
| Frame geometry | Crop overlay aligns visually with preview content |

## Current Ownership Split

Codex/multimodal owner:

- This report.
- Final visual pass on new vivo X300 recording.
- Frame geometry visual arbitration.
- Color Lab live feel judgment.
- Capture feedback trust judgment.

Text-only implementation agents:

- Top bar / rail IA.
- Color Lab persistence.
- Bottom cockpit / mode track.
- Quick panel sheet.
- Panel copy cleanup.

## Final Judgment

Do not declare 2.0 visual/product GO from the current APK. The correct current state is:

**NO GO for visual/product 2.0 acceptance, with clear implementation packages ready.**

If the five text-only implementation packages land cleanly and the next vivo X300 recording satisfies the recheck protocol, the project can be reclassified to `CONDITIONAL GO pending saved-media output verification`.
