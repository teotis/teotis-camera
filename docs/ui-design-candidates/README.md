# OpenCamera UI Design Candidates

These are visual candidates for the next UI direction. They are intentionally static mockups, not implementation files.

## Candidate A: A Cockpit

File: `a-cockpit.svg`

- Preview-first cockpit.
- Fixed mode track above the bottom control band.
- Thumbnail, shutter, lens and zoom stay stable.
- `DEV` is a dedicated debug tool entry, not a normal camera feature.

## Candidate B: vivo / OPPO Efficiency

File: `vivo-oppo-efficiency.svg`

- More direct quick controls for one-hand shooting.
- Larger zoom selector and stronger selected states.
- Keeps common toggles visible without reopening a big panel.
- Good if the priority is speed and discoverability.

## Candidate C: Apple Minimal

File: `apple-minimal.svg`

- Most restrained visual language.
- Preview is dominant, text is minimal.
- Secondary controls become small neutral chips.
- Good if the priority is clean hierarchy and reduced distraction.

## Selection Workflow

After one candidate is selected, save it as the canonical reference under:

`docs/ui-reference/`

The final reference should include:

- Selected SVG.
- Decision notes.
- UI implementation checklist.
- Explicit list of controls that must stay out of the main shooting surface.
