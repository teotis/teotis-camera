# 2026-05-22 Fifth Real-Device Recording Review Index

> **For agentic workers:** This is the master handoff for the 参考设备 recording `<HOME>/Downloads/飞书20260522-162635.mp4`. Pick one linked plan, keep edits scoped, and run the listed verification. Use `rtk` for every shell command.

## Source Evidence

- Device/video: 参考设备 real-device screen recording.
- File: `<HOME>/Downloads/飞书20260522-162635.mp4`
- Video facts: `540x1176`, about `157.93s`, portrait.
- Multimodal review frames: settings, color lab, style panel, quick panel, dev panel, photo capture thumbnail, mode switching, 1:1 / 16:9 / 4:3 frame overlays.

## User-Confirmed Problems

1. Top-bar Chinese text is partially obstructed.
2. `设置` and `色彩实验室` top buttons are visually the same length and weight.
3. The color palette jumps back to origin after tapping/dragging; the adjustment does not persist.
4. Remove the `进阶` button from Color Lab.
5. Move the top bar slightly upward.
6. Rename the right-side `色调` entry to `镜头`.
7. The bottom operation area feels split; zoom should be merged into the translucent rounded shutter cockpit.
8. Mode-track text is hard to see during use; increase contrast and add a translucent background.
9. Top-bar action buttons have borders that conflict with the rest of the style.
10. Quick expanded secondary panel loses frame-ratio option information; make it a panel, not floating buttons.

## Additional Multimodal Findings

1. The first screen still reads like a technical demo: too many bordered text pills, weak hierarchy, and preview loses dominance.
2. Settings panel exposes engineering/capability language such as `Supported`; this is not user-facing 2.0 copy.
3. Color Lab visually points in the right direction, but its panel is too tall and covers the preview while editing.
4. Style panel exposes internal render parameters such as `B 20 | C 0.92 | S 1.37 | W 24`, which breaks product trust.
5. Quick controls are currently a vertical floating stack over the preview; this is hard to scan and easy to confuse with persistent right-rail actions.
6. Frame/preview geometry feels unstable when switching 1:1 / 16:9 / 4:3; the white crop frame and real preview area are visually disconnected.
7. Mode labels can be clipped at screen edges and lack enough active-state contrast.
8. The always-visible Dev entry and full-screen Dev log strongly reduce product feeling during normal acceptance.
9. Thumbnail feedback after capture is still not sufficiently trustworthy; it is hard to tell whether the thumbnail is the latest processed result.

## 2.0 Acceptance Gap Summary

| Domain | Current gap | 2.0 standard |
| --- | --- | --- |
| Top cockpit | Text overlap, equal-weight actions, bordered buttons | Safe-area-respecting, compact, hierarchy clear, no text clipping |
| Color Lab | Palette does not persist, advanced path remains, preview covered | Direct persistent two-axis color control, no advanced button, live feedback |
| Right rail | `色调` naming no longer matches product intent | Right rail is shooting-surface entry: `镜头 / 快捷 / Dev` |
| Bottom cockpit | Zoom, mode, shutter, thumbnail are visually split | One grounded translucent capture cockpit with zoom integrated |
| Quick panel | Floating buttons and missing ratio info | Compact panelized controls with stable labels and selected state |
| Mode track | Weak contrast and edge clipping | Readable over preview, semi-transparent backing, clear active state |
| Panel content | Engineering text and internal params leak | Product copy, concise status, no raw implementation fields |
| Output trust | Thumbnail not obviously latest processed capture | Immediate capture feedback plus saved/processed result handoff |

## Reference Direction

Use 参考厂商, Apple, and OPPO as ergonomic references, but preserve OpenCamera's lower-layer philosophy:

- Like 参考厂商/OPPO: camera-first visual density, concise mode rail, quick camera controls in one panel.
- Like Apple: restrained top actions, stable bottom capture cockpit, direct manipulation for photographic style controls.
- Preserve OpenCamera: explicit supported/degraded/unsupported semantics, no fake capability, no hidden second session kernel, diagnostics available but not front-stage product UI.

## Work Packages

1. [Top Bar And Rail IA Polish](./2026-05-22-fifth-top-bar-and-rail-ia-polish.md)
   - Fix top text obstruction, unequal hierarchy, border style, safe-area offset, and right-rail `色调 -> 镜头`.

2. [Color Lab Palette Persistence And Simplification](./2026-05-22-fifth-color-lab-palette-persistence.md)
   - Fix palette snapping back to origin, remove advanced button, route Color Lab to persisted `ColorLabSpec`.

3. [Bottom Cockpit Zoom And Mode Track Integration](./2026-05-22-fifth-bottom-cockpit-zoom-mode-track.md)
   - Merge zoom into the bottom rounded cockpit; strengthen mode-track contrast and active state.

4. [Quick Panel Frame Ratio Sheet](./2026-05-22-fifth-quick-panel-frame-ratio-sheet.md)
   - Replace floating quick buttons with a compact panelized sheet; restore stable frame-ratio option display.

5. [Panel Copy And Engineering Text Cleanup](./2026-05-22-fifth-panel-copy-engineering-cleanup.md)
   - Remove `Supported` and raw render parameter strings from user-facing panels; keep dev detail in Dev only.

6. [Multimodal QA And High-Difficulty Owner Tasks](./2026-05-22-fifth-multimodal-hard10-qa.md)
   - Covers the hardest 10% and all visual judgment work: frame geometry, top-bar safe area, palette live feel, capture thumbnail trust, final 参考设备 visual pass.

## Recommended Dependency Order

1. Color Lab persistence first, because it is a real functional defect.
2. Top bar/rail IA next, because it affects every screen and is low-to-medium risk.
3. Bottom cockpit + mode track next, because it changes layout density and requires careful integration.
4. Quick panel sheet next, because it overlaps the same main XML but can be bounded.
5. Panel copy cleanup can run in parallel if it avoids `MainActivity.kt` conflicts.
6. Multimodal QA last on a new APK.

## Integration Warning

Several packages touch `activity_main.xml` and `MainActivity.kt`. Do not let multiple agents edit those files blindly in parallel. Prefer one integrator for layout changes, with other agents preparing render-model tests, string resources, and isolated Kotlin helpers.

## Global Verification

After each package:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.gesture.GestureGuardTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

After integration:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Final acceptance still requires a new 参考设备 recording.
