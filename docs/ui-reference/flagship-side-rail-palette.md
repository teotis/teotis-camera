# OpenCamera Flagship Side Rail + Palette Reference

Status: approved design direction  
Date: 2026-05-21  
Reference mockup: `flagship-side-rail-palette.svg`

## Design Decision

Use a combined direction:

- **Right side rail owns shooting efficiency.**
- **Palette surface owns image style and tone control.**
- **Bottom area stays calm and camera-like: thumbnail, shutter, lens switch, and mode rail only.**

This replaces the earlier A/B/C split. The new direction keeps the preview dominant like Apple Camera, gives one-hand tools the immediacy expected from vivo and OPPO flagship camera apps, and lets OpenCamera develop its own visual identity through a clean color/style palette.

## External Reference Principles

- **Apple iOS 26 Camera:** default camera surface is visually quieter, with Photo/Video emphasized and secondary controls hidden until needed.
- **vivo X300 Pro / OriginOS 6:** flagship camera UI should feel fluid, modern, style-forward, and fast to operate without crowding the preview.
- **OPPO Find X9 Pro / ColorOS 16:** side/quick controls and immediate capture actions are part of the flagship ergonomics story.

Do not copy any brand-specific icons, colors, layouts, text, or proprietary visual assets. Use these only as interaction and hierarchy references.

## Primary Layout

The camera screen is a full-preview cockpit with four stable zones.

1. **Top status strip**
   - Keep only compact status: current mode, preview/permission state, settings entry.
   - No large title treatment.
   - No dense rows of feature buttons.

2. **Right side rail**
   - Fixed vertical rail, thumb reachable.
   - Contains the primary quick controls:
     - Zoom stack: `0.6`, `1x`, `2x`, `5x`
     - Palette entry
     - Ratio
     - Flash
     - Timer or Live, depending on active mode
     - `DEV` entry in debug builds only
   - Current selection uses a clear filled state.
   - Inactive controls are translucent and quiet.

3. **Bottom capture deck**
   - Left: thumbnail.
   - Center: shutter.
   - Right: lens switch.
   - Mode rail sits directly above or inside the top edge of the capture deck.
   - Keep mode labels short: `Photo`, `Doc`, `Scenery`, `Human`, `Port`, `Pro`, `Video`.
   - Do not put `Flash`, `Ratio`, `Timer`, quality, size, or session controls in the bottom deck.

4. **Palette panel**
   - Opens from the side rail as a bottom sheet or side-floating panel.
   - Should feel like a photographic style tool, not a settings page.
   - Includes:
     - Style chips or swatches: `Natural`, `Vivid`, `Film`, `Mono`, `Soft`, `Custom`
     - Current style preview strip
     - Strength slider or 2D palette surface
     - Optional advanced reveal for exposure, warmth, tint, grain, highlights, shadows
   - Must be dismissible by tapping outside or returning to capture.

## DEV Console Placement

The development log entry belongs in the right side rail, not the top bar.

- Debug build: show compact `DEV` control at the bottom of the side rail.
- Release build: completely hidden.
- Tapping `DEV` opens a near-full-height overlay that avoids covering the shutter.
- Console tabs remain:
  - `Key Log`
  - `Core Log`
  - `Error Log`
  - `All`
- Console content must scroll vertically.
- Export writes to app private external debug log directory.

## Visual Style

The interface should feel simple, fluid, and friendly.

- Palette: neutral dark glass base, white primary text, one vivid accent.
- Suggested accent: cyan/teal for active tools, warm amber only for capture/recording emphasis.
- Avoid a one-note blue/purple UI.
- Avoid large cards on the main camera surface.
- Use rounded controls sparingly: small circular or capsule tools are fine; large nested cards are not.
- Preview must remain the visual hero.

## Interaction Rules

- One tap on side rail controls should either toggle or open a compact control surface.
- Zoom choices should be directly tappable; no hidden zoom button as the only path.
- Palette opens quickly and can be closed without losing camera context.
- Settings is for deeper configuration, not common shooting actions.
- Error/permission/save messages appear as short toast-like strips, not persistent paragraphs.
- Mode changes should keep side rail and capture deck positions stable.

## Controls To Remove From Main Surface

These controls must not appear as always-visible bottom or top buttons:

- Still quality
- Still size
- Restart session
- Secondary camera
- Tertiary camera
- Pro variant
- Debug dumps outside the `DEV` console
- Long diagnostics text

They belong in Settings, More, or the DEV console depending on audience.

## Implementation Checklist

- Move `DEV` entry from top strip to right side rail.
- Move `Flash`, `Ratio`, `Timer`/`Live`, and palette entry into right side rail.
- Keep zoom as a vertical side stack, not a horizontal bottom capsule.
- Simplify top strip to status + settings only.
- Keep bottom deck to thumbnail, shutter, lens switch, and mode rail.
- Convert current filter/palette work into a dedicated palette panel reachable from side rail.
- Preserve existing View/XML stack.
- Do not introduce Compose or a new session owner.
- Ensure UI only dispatches intents and renders state.
- Keep release builds free of debug-only entries.

## Acceptance Criteria

- A first-glance screenshot reads as a camera preview, not a settings dashboard.
- The right side rail visibly carries high-frequency controls.
- The palette feature is visually distinct and easy to discover.
- The bottom deck is stable, uncluttered, and one-hand friendly.
- No development-only UI appears in release builds.
- `DEV` console is discoverable in debug builds and does not compete with normal shooting controls.

## Source Links

- Apple Camera user guide for iOS 26: https://support.apple.com/guide/iphone/camera-basics-iph263472f78/26/ios/26
- Apple iOS 26 Camera redesign coverage: https://www.macrumors.com/guide/ios-26-camera-app/
- vivo X300 Pro product page: https://www.vivo.com/in/products/x300-pro
- OPPO Find X9 Pro product page: https://www.oppo.com/en/smartphones/series-find-x/find-x9-pro/
