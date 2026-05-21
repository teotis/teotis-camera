# Floating Button UI Candidates

These reference images explore replacing the fixed side rail with floating controls. Each SVG shows two states:

- left: initial main camera screen;
- right: secondary panel opened.

## Candidate A: Floating Focus

File: `floating-focus.svg`

- Recommended baseline.
- Uses 2-3 floating buttons instead of a fixed control rail.
- Palette opens as a compact photographic style panel.
- Bottom capture deck remains stable.
- `DEV` remains visible only in debug builds and sits with floating utilities.

Best when the goal is a flagship-feeling camera UI that stays clean but still makes style tools discoverable.

## Candidate B: Quick Bubble

File: `quick-bubble.svg`

- Most OPPO-inspired interaction model.
- A single floating launcher expands into quick bubbles.
- Zoom remains a direct bottom capsule.
- Works well for one-handed shooting.

Best when the priority is fast access and minimal default chrome. Risk: discoverability depends heavily on icon quality and motion.

## Candidate C: Apple-Clean Panel

File: `apple-clean-panel.svg`

- Most restrained and Apple-like.
- Default state exposes almost nothing beyond Photo/Video and capture controls.
- Secondary controls appear as a top translucent tool strip and a bottom style strip.

Best when the priority is maximum preview cleanliness. Risk: Android flagship users may find useful tools too hidden.

## Candidate D: Palette First

File: `palette-first.svg`

- Most OpenCamera-branded and style-forward.
- Palette is the primary floating action.
- Secondary panel feels like a small creative studio.

Best when image style and tone control should become the project signature. Risk: less neutral for simple point-and-shoot use.

## Recommendation

Use **Candidate A: Floating Focus** as the primary direction, with Candidate B's quick-bubble behavior available for secondary controls. It best balances:

- Apple-style restraint;
- vivo/OriginOS-like fluid, modern visual polish;
- OPPO-like fast access to common shooting tools;
- OpenCamera's own palette-driven image identity.

Once selected, copy the winning SVG and decision notes into `docs/ui-reference/` as the canonical implementation reference.
