# OpenCamera Floating Focus Decision

Status: approved implementation direction  
Date: 2026-05-21  
Primary visual reference: `floating-button-candidates/floating-focus.svg`  
Secondary interaction reference: `floating-button-candidates/quick-bubble.svg`

## Decision

Use **Candidate A: Floating Focus** as the primary UI direction and absorb **Candidate B: Quick Bubble** for secondary expansion behavior.

The main camera surface should stay clean and preview-first. Floating buttons replace the fixed side rail so the UI does not feel like a column of controls. The palette and `DEV` entry both remain explicit, but neither should compete with the shutter or mode rail.

## Why This Direction

- It preserves Apple-style restraint: fewer controls appear by default.
- It preserves vivo/OriginOS-like polish: floating surfaces can feel fluid, light, and photographic.
- It preserves OPPO-like speed: quick actions can expand near the user's thumb without sending them into a settings page.
- It gives OpenCamera a clear identity: palette is a visible image-style tool, not a buried settings form.

## Initial Main Screen

- Full-screen preview remains the visual hero.
- Top strip is light: current mode, compact preview/permission state, settings.
- Floating utility stack contains only:
  - Palette
  - Zoom / quick launcher
  - `DEV` in debug builds only
- Bottom deck contains only:
  - Thumbnail
  - Shutter
  - Lens switch
  - Compact mode rail

## Secondary Panel Behavior

Use Quick Bubble behavior for secondary actions:

- A tap on the floating quick launcher expands local bubbles for `Flash`, `Ratio`, `Timer`/`Live`, and optional mode-specific quick actions.
- A tap on Palette opens the Floating Focus palette panel.
- A tap on `DEV` opens the debug console overlay.
- Expanded controls must not push or resize the bottom capture deck.
- Tapping outside, pressing close, or selecting a terminal action collapses the expanded controls.

## Palette Panel

Palette is a camera tool, not a settings page.

- Show style swatches such as `Natural`, `Vivid`, `Film`, `Mono`, `Soft`, `Custom`.
- Show current style summary and one direct intensity control.
- Optional advanced controls can remain behind one reveal action.
- Keep the panel compact enough that the user still understands they are in the camera preview.

## DEV Entry

- `DEV` appears only when `BuildConfig.DEBUG == true`.
- Release builds must hide the entry and console completely.
- The button belongs with floating utilities, not the top strip.
- The console still provides `Key Log`, `Core Log`, `Error Log`, `All`, and `Export`.

## Controls That Must Leave The Main Surface

- Fixed side rail / side column
- Top-bar `DEV`
- Top-bar filter button as a normal camera control
- Persistent bottom `Flash` and `Ratio` buttons
- Still quality
- Still size
- Restart session
- Secondary / tertiary camera buttons
- Long diagnostics or debug text outside the console

## Visual Rules

- Use translucent dark glass only for floating controls and panels.
- Avoid large nested cards on the main preview.
- Keep text short; prefer icon-like or compact labels for floating tools.
- Use one vivid accent for selected state, preferably cyan/teal.
- Warm accent is reserved for capture/recording or palette warmth.
- Do not let the UI become a one-color blue/purple theme.

## Acceptance Criteria

- First glance reads as a camera, not a settings dashboard.
- Floating buttons feel intentional and do not form a rigid side column.
- Palette is discoverable from the first screen.
- Quick actions expand locally and collapse easily.
- Bottom deck remains stable across initial and expanded states.
- Debug-only UI is absent from release builds.
